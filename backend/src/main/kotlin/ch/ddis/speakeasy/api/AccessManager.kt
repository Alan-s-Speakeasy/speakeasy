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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.StampedLock

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
    private val userIdUserSessionMap = ConcurrentHashMap<UserId, UserSession>(1000)
    private val sessionIdUserSessionMap = ConcurrentHashMap<SessionId, UserSession>(1000)

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

            val sess = userIdUserSessionMap[user.id]!!
            sessionId = sess.sessionId
            alias = sess.userSessionAlias

            if (user.role == UserRole.BOT) { //in case of login, invalidate all other session of the same bot
                val oldTokens = sessionTokenUserSessionMap.filter { it.value.user == user }.map { it.key }
                oldTokens.forEach { clearUserSession(it) }
            }

        } else {
            sessionId = UID()
            alias = SessionAliasGenerator.getRandomName()
        }

        val session = UserSession(user, sessionToken, sessionId, userSessionAlias = alias)
        sessionTokenUserSessionMap[sessionToken] = session
        userIdUserSessionMap[user.id] = session
        sessionIdUserSessionMap[session.sessionId] = session
        logSession(session)

        return session
    }

    fun getUserSessionForSessionToken(sessionToken: String): UserSession? = sessionTokenUserSessionMap[sessionToken]

    fun getUserSessionForSessionId(sessionId: SessionId): UserSession? = sessionIdUserSessionMap[sessionId]

    fun clearUserSession(sessionToken: String) {
        val session = sessionTokenUserSessionMap.remove(sessionToken)
        if (session != null) {
            userIdUserSessionMap.remove(session.user.id)
            sessionIdUserSessionMap.remove(session.sessionId)
        }

    }


    private fun rolesOfSession(sessionToken: String): Set<RestApiRole> =
        sessionTokenUserSessionMap[sessionToken]?.user?.role?.let { userRoleToApiRole(it) } ?: emptySet()

    fun listSessions(): List<UserSession> = sessionTokenUserSessionMap.values.toList()

}

enum class RestApiRole : Role { ANYONE, USER, HUMAN, ADMIN }