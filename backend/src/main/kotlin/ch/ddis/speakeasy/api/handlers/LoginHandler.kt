package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.user.PlainPassword
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserSessionDetails
import ch.ddis.speakeasy.util.getOrCreateSessionToken
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.util.NaiveRateLimit
import io.javalin.openapi.*
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory
import java.util.concurrent.TimeUnit

class LoginHandler : RestHandler, PostRestHandler<UserSessionDetails> {
    
    companion object {
        private val logger = LoggerFactory.getLogger(LoginHandler::class.java)
        private val accessLogger by lazy {  LoggerFactory.getLogger("ch.ddis.speakeasy.access")}
        private val ACCESS_MARKER = MarkerFactory.getMarker("ACCESS")
    }

    data class LoginRequest(var username: String, var password: String)

    @OpenApi(
        summary = "Sets roles for session based on user account and returns a session cookie.",
        path = "/api/login",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        tags = ["User"],
        requestBody = OpenApiRequestBody([OpenApiContent(LoginRequest::class)]),
        responses = [
            OpenApiResponse("200", [OpenApiContent(UserSessionDetails::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): UserSessionDetails {
        val requestId = ctx.attribute<String>("requestId") ?: "unknown"

        val loginRequest = try {
            ctx.bodyAsClass(LoginRequest::class.java)
        } catch (e: BadRequestResponse) {
            accessLogger.warn(ACCESS_MARKER, 
                "[{}] Invalid login request format from {}", 
                requestId, 
                ctx.ip()
            )
            throw ErrorStatusException(400, "Invalid parameters. This is a programmers error.", ctx)
        }


        accessLogger.info(ACCESS_MARKER, 
            "[{}] Login attempt for user '{}' from {}", 
            requestId, 
            loginRequest.username, 
            ctx.ip()
        )


        val password = PlainPassword(loginRequest.password)

        val user = UserManager.getMatchingUser(loginRequest.username, password)
        if (user == null) {
            accessLogger.warn(ACCESS_MARKER, 
                "[{}] Failed login attempt for user '{}' from {} - Invalid credentials", 
                requestId, 
                loginRequest.username, 
                ctx.ip()
            )
            throw ErrorStatusException(401, "Invalid credentials. Please try again!", ctx)
        }

        val sessionToken = ctx.getOrCreateSessionToken()
        val session = AccessManager.setUserForSession(sessionToken, user)

        //explicitly set cookie on login
        ctx.cookie(AccessManager.SESSION_COOKIE_NAME, sessionToken, AccessManager.SESSION_COOKIE_LIFETIME)

        accessLogger.info(ACCESS_MARKER, 
            "[{}] Successful login for user '{}' (ID: {}) from {} - Session: {}", 
            requestId, 
            user.name,
            user.id, 
            ctx.ip(),
            sessionToken.take(8)
        )

        return UserSessionDetails(session)

    }

    override val route = "login"
}