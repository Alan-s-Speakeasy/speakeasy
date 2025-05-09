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
import java.util.concurrent.TimeUnit

class LoginHandler : RestHandler, PostRestHandler<UserSessionDetails> {

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
        // Should prevent to a certain extent brute force attacks
        // This is an additional, tighter rate limit as the global one in RestApi.kt.
        NaiveRateLimit.requestPerTimeUnit(ctx, 7, TimeUnit.MINUTES)

        val loginRequest = try {
            ctx.bodyAsClass(LoginRequest::class.java)
        } catch (e: BadRequestResponse) {
            throw ErrorStatusException(400, "Invalid parameters. This is a programmers error.", ctx)
        }
        val password = PlainPassword(loginRequest.password)

        val user = UserManager.getMatchingUser(loginRequest.username, password)
            ?: throw ErrorStatusException(401, "Invalid credentials. Please try again!", ctx)

        val sessionToken = ctx.getOrCreateSessionToken()

        val session = AccessManager.setUserForSession(sessionToken, user)

        //explicitly set cookie on login
        ctx.cookie(AccessManager.SESSION_COOKIE_NAME, sessionToken, AccessManager.SESSION_COOKIE_LIFETIME)

        return UserSessionDetails(session)

    }

    override val route = "login"
}