package ch.ddis.speakeasy.api

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
import kotlin.collections.ArrayList
import kotlin.concurrent.timerTask

object AccessManager {

    fun manage(handler: Handler, ctx: Context, permittedRoles: Set<Role>) {
        when {
            permittedRoles.isEmpty() -> handler.handle(ctx) //fallback in case no roles are set, none are required
            permittedRoles.contains(RestApiRole.ANYONE) -> handler.handle(ctx)
            rolesOfSession(ctx.sessionToken()).any { it in permittedRoles } -> handler.handle(ctx)
            else -> ctx.status(401)
        }
    }

    private val sessionWriter: PrintWriter

    init {
        val sessionFile = File("data/sessions.csv")//TODO see where we actually want to store this

        if (!sessionFile.exists() || sessionFile.length() == 0L) {
            sessionFile.writeText("timestamp,sessionid,sessiontoken,userid,username,sessionalias\n", Charsets.UTF_8)
        }

        val expiredSessionCleanupTimer = 10
        Timer().scheduleAtFixedRate(timerTask {
            clearExpiredSessions()
        }, expiredSessionCleanupTimer * 1000L, expiredSessionCleanupTimer * 1000L)

        sessionWriter = PrintWriter(
            FileWriter(
                sessionFile,
                Charsets.UTF_8,
                true
            ),
            true
        )
    }

    private val writerLock = StampedLock()

    private fun logSession(userSession: UserSession) = writerLock.write {
        sessionWriter.println("${System.currentTimeMillis()},${userSession.sessionId},${userSession.sessionToken},${userSession.user.id},${userSession.user.name},${userSession.userSessionAlias}")
        sessionWriter.flush()
    }

    private val sessionTokenUserSessionMap = ConcurrentHashMap<String, UserSession>(1000)
    private val userIdUserSessionMap = ConcurrentHashMap<UserId, ArrayList<UserSession>>(1000)
    private val sessionIdUserSessionMap = ConcurrentHashMap<SessionId, UserSession>(1000)
    private val sessionTokenLastAccessMap = ConcurrentHashMap<String, Long>(1000)

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

        if (userIdUserSessionMap.containsKey(user.id)) {
            val sessions = userIdUserSessionMap[user.id]!!.filter { it.sessionToken == sessionToken }
            if (sessions.size == 1) {
                sessionId = sessions[0].sessionId
                alias = sessions[0].userSessionAlias
            }
            else {
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
        sessionTokenUserSessionMap[sessionToken] = session
        sessionIdUserSessionMap[session.sessionId] = session

        if (!userIdUserSessionMap.containsKey(user.id)) {
            userIdUserSessionMap.put(user.id, ArrayList())
        }
        userIdUserSessionMap.get(user.id)?.add(session)

        logSession(session)

        return session
    }

    fun getUserSessionForSessionToken(sessionToken: String): UserSession? {
        sessionTokenLastAccessMap.put(sessionToken, System.currentTimeMillis())
        return sessionTokenUserSessionMap[sessionToken]
    }

    fun getUserSessionForSessionId(sessionId: SessionId): UserSession? = sessionIdUserSessionMap[sessionId]

    fun hasUserIdActiveSessions(userId: UserId): Boolean {
        return userIdUserSessionMap[userId]?.isNotEmpty() == true
    }

    fun clearUserSession(sessionToken: String) {
        sessionTokenUserSessionMap.remove(sessionToken)
    }

    private fun clearExpiredSessions() {
        val sessionExpiryDate = 10
        for (sessionToken in sessionTokenLastAccessMap.keys()) {
            val lastAccess = sessionTokenLastAccessMap.get(sessionToken)!!
            if (System.currentTimeMillis() - lastAccess > sessionExpiryDate * 1000) {
                val session = sessionTokenUserSessionMap.remove(sessionToken)
                if (session != null) {
                    sessionTokenLastAccessMap.remove(sessionToken)
                    userIdUserSessionMap[session.user.id]!!.remove(session)
                    sessionIdUserSessionMap.remove(session.sessionId)
                }
            }
        }
    }


    private fun rolesOfSession(sessionToken: String): Set<RestApiRole> =
        sessionTokenUserSessionMap[sessionToken]?.user?.role?.let { userRoleToApiRole(it) } ?: emptySet()

    fun listSessions(): List<UserSession> = sessionTokenUserSessionMap.values.toList()

}

enum class RestApiRole : Role { ANYONE, USER, HUMAN, ADMIN }