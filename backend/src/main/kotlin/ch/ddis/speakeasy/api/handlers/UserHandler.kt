package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.user.*
import ch.ddis.speakeasy.util.sessionToken
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.*

class ListUsersHandler : GetRestHandler<List<UserDetails>>, AccessManagedRestHandler {

    @OpenApi(
        summary = "Lists all available users.",
        path = "/api/user/list",
        tags = ["Admin"],
        responses = [OpenApiResponse("200", [OpenApiContent(Array<UserDetails>::class)])]
    )
    override fun doGet(ctx: Context): List<UserDetails> {
        AccessManager.updateLastAccess(ctx.req.session.id)
        return UserManager.list().map(UserDetails.Companion::of)
    }

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route = "user/list"
}

class ListUserSessionsHandler : GetRestHandler<List<UserSessionDetails>>, AccessManagedRestHandler {

    @OpenApi(
        summary = "Lists all current user sessions.",
        path = "/api/user/sessions",
        tags = ["Admin"],
        responses = [OpenApiResponse("200", [OpenApiContent(Array<UserSessionDetails>::class)])]
    )
    override fun doGet(ctx: Context): List<UserSessionDetails> {
        AccessManager.updateLastAccess(ctx.req.session.id)
        return AccessManager.listSessions().map { UserSessionDetails(it) }
    }

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route = "user/sessions"
}

class AddUserHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {

    data class AddUserRequest(var username: String, var role: UserRole, var password: String)

    @OpenApi(
        summary = "Adds a new user.",
        path = "/api/user/add",
        method = HttpMethod.POST,
        requestBody = OpenApiRequestBody([OpenApiContent(AddUserRequest::class)]),
        tags = ["Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val addUserRequest = try {
            ctx.bodyAsClass(AddUserRequest::class.java)
        } catch (e: BadRequestResponse) {
            throw ErrorStatusException(400, "Invalid parameters. This is a programmers error.", ctx)
        }

        UserManager.addUser(addUserRequest.username, addUserRequest.role, PlainPassword(addUserRequest.password))

        return SuccessStatus("User added")
    }

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route = "user/add"
}

class RemoveUserHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {

    @OpenApi(
        summary = "Removes an existing user.",
        path = "/api/user/remove",
        method = HttpMethod.POST,
        requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
        tags = ["Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("403", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val username = ctx.body()

        if (username.isBlank()) {
            throw ErrorStatusException(400, "username cannot be empty", ctx)
        }

        if (!UserManager.removeUser(username, false)) {
            throw ErrorStatusException(403, "user has active sessions", ctx)
        }

        return SuccessStatus("User removed")
    }

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route = "user/remove"
}

data class PasswordChangeRequest(val currentPassword: String, val newPassword: String)

class ChangePasswordHandler : PatchRestHandler<SuccessStatus>, AccessManagedRestHandler {

    override val route = "user/password"

    override val permittedRoles = setOf(RestApiRole.USER)

    @OpenApi(
        summary = "Changes the password for a given user.",
        path = "/api/user/password",
        method = HttpMethod.PATCH,
        requestBody = OpenApiRequestBody([OpenApiContent(PasswordChangeRequest::class)]),
        tags = ["User"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],

        )
    override fun doPatch(ctx: Context): SuccessStatus {

        val changeRequest = try {
            ctx.body<PasswordChangeRequest>()
        } catch (e: BadRequestResponse) {
            throw ErrorStatusException(400, "Invalid change request", ctx)
        }

        val currentPassword = PlainPassword(changeRequest.currentPassword)

        val userSession = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Invalid user session",
            ctx
        )

        val user = UserManager.getMatchingUser(userSession.user.name, currentPassword)
            ?: throw ErrorStatusException(401, "Invalid credentials.", ctx)

        if (user != userSession.user) {
            throw ErrorStatusException(401, "Invalid credentials.", ctx) //should never happen, checking just to be sure
        }

        try {
            UserManager.updatePassword(user.id, PlainPassword(changeRequest.newPassword))
        } catch (e: IllegalArgumentException) {
            throw ErrorStatusException(400, "Could not change password", ctx)
        }

        return SuccessStatus("Password changed.")
    }

}

class GetCurrentUserHandler : GetRestHandler<UserSessionDetails>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "user/current"

    @OpenApi(
        summary = "Returns details for the current session.",
        path = "/api/user/current",
        method = HttpMethod.GET,
        tags = ["User"],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(UserSessionDetails::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): UserSessionDetails {

        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Invalid user session",
            ctx
        )

        return UserSessionDetails(session)
    }

}