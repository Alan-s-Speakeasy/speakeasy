package ch.ddis.speakeasy.api

import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.user.*
import ch.ddis.speakeasy.util.SessionAliasGenerator
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.sessionToken
import ch.ddis.speakeasy.util.write
import io.javalin.core.security.Role
import io.javalin.http.Context
import io.javalin.http.Handler
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.StampedLock
import kotlin.concurrent.timerTask

object AccessManager {

    private val sessionTokenUserSessionMap = ConcurrentHashMap<String, UserSession>(1000)
    private val userIdUserSessionMap = ConcurrentHashMap<UserId, ArrayList<UserSession>>(1000)
    private val sessionTokenLastAccessMap = ConcurrentHashMap<String, Long>(1000)

    private val sessionFile = File("data/sessions.csv")//TODO see where we actually want to store this
    private val sessionWriter = PrintWriter(
        FileWriter(
            sessionFile,
            Charsets.UTF_8,
            true
        ),
        true
    )

    fun manage(handler: Handler, ctx: Context, permittedRoles: Set<Role>) {
        when {
            permittedRoles.isEmpty() -> handler.handle(ctx) //fallback in case no roles are set, none are required
            permittedRoles.contains(RestApiRole.ANYONE) -> handler.handle(ctx)
            rolesOfSession(ctx.sessionToken()).any { it in permittedRoles } -> handler.handle(ctx)
            else -> ctx.status(401)
        }
    }

    fun init() {

        if (!sessionFile.exists() || sessionFile.length() == 0L) {
            sessionFile.writeText("timestamp,sessionid,sessiontoken,userid,username,sessionalias\n", Charsets.UTF_8)
        }

        val expiredSessionCleanupTimer = 10
        Timer().scheduleAtFixedRate(timerTask {
            clearExpiredSessions()
        }, expiredSessionCleanupTimer * 1000L, expiredSessionCleanupTimer * 1000L)
    }

    private val writerLock = StampedLock()

    private fun logSession(userSession: UserSession) = writerLock.write {
        sessionWriter.println("${System.currentTimeMillis()},${userSession.sessionId.string},${userSession.sessionToken},${userSession.user.id.string},${userSession.user.name},${userSession.userSessionAlias}")
        sessionWriter.flush()
    }

    private fun userRoleToApiRole(userRole: UserRole): Set<RestApiRole> = when (userRole) {
        UserRole.HUMAN -> setOf(RestApiRole.ANYONE, RestApiRole.USER, RestApiRole.HUMAN)
        UserRole.BOT -> setOf(RestApiRole.ANYONE, RestApiRole.USER)
        UserRole.ADMIN -> setOf(RestApiRole.ANYONE, RestApiRole.USER, RestApiRole.HUMAN, RestApiRole.ADMIN)
    }

    fun setUserForSession(sessionToken: String, user: User): UserSession {

        if (sessionTokenUserSessionMap.containsKey(sessionToken)) {
            return sessionTokenUserSessionMap[sessionToken]!!
        }

        val sessionId: SessionId
        val alias: String

        if (userIdUserSessionMap.containsKey(user.id) && userIdUserSessionMap[user.id]!!.size > 0) {
            val userSessions = userIdUserSessionMap[user.id]!!
            val sessions = userSessions.filter { it.sessionToken == sessionToken }
            if (sessions.size == 1) {
                sessionId = sessions[0].sessionId
                alias = sessions[0].userSessionAlias
            } else {
                sessionId = UID()
                alias = SessionAliasGenerator.getRandomName()
            }

            if (user.role == UserRole.BOT) { //in case of login, invalidate all other session of the same bot
                userIdUserSessionMap[user.id]!!.clear()
            }

        } else {
            sessionId = UID()
            alias = SessionAliasGenerator.getRandomName()
        }

        val session = UserSession(user, sessionToken, sessionId, userSessionAlias = alias)
        addSessionToMaps(session, user)
        ChatRoomManager.join(session)

        logSession(session)

        return session
    }

    private fun addSessionToMaps(session: UserSession, user: User) {
        sessionTokenUserSessionMap[session.sessionToken] = session

        if (!userIdUserSessionMap.containsKey(user.id)) {
            userIdUserSessionMap[user.id] = ArrayList()
        }
        userIdUserSessionMap[user.id]?.add(session)
        updateLastAccess(session.sessionToken)
    }

    fun updateLastAccess(sessionToken: String) {
        sessionTokenLastAccessMap[sessionToken] = System.currentTimeMillis()
    }

    fun getUserSessionForSessionToken(sessionToken: String): UserSession? {
        updateLastAccess(sessionToken)
        return sessionTokenUserSessionMap[sessionToken]
    }

    fun getSessionsForUserId(userId: UserId): ArrayList<UserSession> {
        return userIdUserSessionMap[userId] ?: arrayListOf()
    }

    fun hasUserIdActiveSessions(userId: UserId): Boolean {
        return userIdUserSessionMap[userId]?.isNotEmpty() == true
    }

    fun clearUserSession(sessionToken: String) {
        val session = sessionTokenUserSessionMap.remove(sessionToken)
        if (session != null) {
            sessionTokenLastAccessMap.remove(sessionToken)
            userIdUserSessionMap[session.user.id]!!.remove(session)
        }
    }

    fun forceClearUserId(userId: UserId) {
        userIdUserSessionMap.remove(userId)?.forEach {
            sessionTokenLastAccessMap.remove(it.sessionToken)
            sessionTokenUserSessionMap.remove(it.sessionToken)
        }
    }

    private fun clearExpiredSessions() {
        val sessionExpiryDate = 5 * 60
        for (sessionToken in sessionTokenLastAccessMap.keys()) {
            val lastAccess = sessionTokenLastAccessMap[sessionToken]!!
            if (System.currentTimeMillis() - lastAccess > sessionExpiryDate * 1000) {
                clearUserSession(sessionToken)
            }
        }
    }

    private fun rolesOfSession(sessionToken: String): Set<RestApiRole> =
        sessionTokenUserSessionMap[sessionToken]?.user?.role?.let { userRoleToApiRole(it) } ?: emptySet()

    fun listSessions(): List<UserSession> = sessionTokenUserSessionMap.values.toList()
}

enum class RestApiRole : Role { ANYONE, USER, HUMAN, ADMIN }