package ch.ddis.speakeasy.api

import ch.ddis.speakeasy.user.*
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.sessionToken
import ch.ddis.speakeasy.util.write
import io.javalin.security.RouteRole
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
    private val cleanupTimer = Timer()

    const val SESSION_COOKIE_NAME = "SESSIONID"
    const val SESSION_COOKIE_LIFETIME = 60 * 60 * 24 //a day

    val SESSION_TOKEN_CHAR_POOL : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '-' + '_'
    const val SESSION_TOKEN_LENGTH = 32

    fun manage(handler: Handler, ctx: Context, permittedRoles: Set<RouteRole>) {
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
        cleanupTimer.scheduleAtFixedRate(timerTask {
            clearExpiredSessions()
        }, expiredSessionCleanupTimer * 1000L, expiredSessionCleanupTimer * 1000L)
    }

    fun stop() {
        cleanupTimer.cancel()
    }

    private val writerLock = StampedLock()

    private fun logSession(userSession: UserSession) = writerLock.write {
        sessionWriter.println("${System.currentTimeMillis()},${userSession.sessionId.string},${userSession.sessionToken},${userSession.user.id.string()},${userSession.user.name}")
        sessionWriter.flush()
    }

    private fun userRoleToApiRole(userRole: UserRole): Set<RestApiRole> = when (userRole) {
        UserRole.HUMAN -> setOf(RestApiRole.ANYONE, RestApiRole.USER, RestApiRole.HUMAN)
        UserRole.BOT -> setOf(RestApiRole.ANYONE, RestApiRole.USER)
        UserRole.ADMIN -> setOf(RestApiRole.ANYONE, RestApiRole.USER, RestApiRole.HUMAN, RestApiRole.ADMIN)
        UserRole.EVALUATOR -> setOf(RestApiRole.ANYONE, RestApiRole.USER)
    }

    fun setUserForSession(sessionToken: String, user: User): UserSession {

        if (sessionTokenUserSessionMap.containsKey(sessionToken)
            // if the role in HashMap and the role of current user don't match (e.g. BOT == HUMAN), we need to create a new UserSession
            && sessionTokenUserSessionMap[sessionToken]!!.user.role == user.role) {
            return sessionTokenUserSessionMap[sessionToken]!!
        }

        val sessionId: SessionId

        if (userIdUserSessionMap.containsKey(user.id.UID()) && userIdUserSessionMap[user.id.UID()]!!.size > 0) {
            val userSessions = userIdUserSessionMap[user.id.UID()]!!
            val sessions = userSessions.filter { it.sessionToken == sessionToken }
            sessionId = if (sessions.size == 1) {
                sessions[0].sessionId
            } else {
                UID()
            }

            if (user.role == UserRole.BOT) { //in case of login, invalidate all other session of the same bot
                userIdUserSessionMap[user.id.UID()]!!.clear()
            }

        } else {
            sessionId = UID()
        }

        val session = UserSession(user, sessionToken, sessionId)
        addSessionToMaps(session, user)

        logSession(session)

        return session
    }

    private fun addSessionToMaps(session: UserSession, user: User) {
        sessionTokenUserSessionMap[session.sessionToken] = session

        if (!userIdUserSessionMap.containsKey(user.id.UID())) {
            userIdUserSessionMap[user.id.UID()] = ArrayList()
        }
        userIdUserSessionMap[user.id.UID()]?.add(session)
        updateLastAccess(session.sessionToken)
    }

    fun updateLastAccess(sessionToken: String?) {
        if (sessionToken == null) {
            return
        }
        sessionTokenLastAccessMap[sessionToken] = System.currentTimeMillis()
    }

    fun getUserSessionForSessionToken(sessionToken: String?): UserSession? {
        if (sessionToken == null) {
            return null
        }
        updateLastAccess(sessionToken)
        return sessionTokenUserSessionMap[sessionToken]
    }

    fun getSessionsForUserId(userId: UserId): ArrayList<UserSession> {
        return userIdUserSessionMap[userId] ?: arrayListOf()
    }

    fun hasUserIdActiveSessions(userId: UserId): Boolean {
        return userIdUserSessionMap[userId]?.isNotEmpty() == true
    }

    fun clearUserSession(sessionToken: String?): Boolean {
        if (sessionToken == null) {
            return false
        }
        val session = sessionTokenUserSessionMap.remove(sessionToken)
        if (session != null) {
            sessionTokenLastAccessMap.remove(sessionToken)
            userIdUserSessionMap[session.user.id.UID()]!!.remove(session)
            return true
        }
        return false
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

    private fun rolesOfSession(sessionToken: String?): Set<RestApiRole> =
        if (sessionToken != null)
            sessionTokenUserSessionMap[sessionToken]?.user?.role?.let { userRoleToApiRole(it) } ?: emptySet()
        else emptySet()

    fun listSessions(): List<UserSession> = sessionTokenUserSessionMap.values.toList()
}

enum class RestApiRole : RouteRole { ANYONE, USER, HUMAN, ADMIN, BOT, EVALUATOR, TESTER }