package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.assignment.UIChatAssignmentGenerator
import ch.ddis.speakeasy.user.*
import ch.ddis.speakeasy.util.sessionToken
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.openapi.*

class ListUsersHandler : GetRestHandler<List<UserDetails>>, AccessManagedRestHandler {

    @OpenApi(
        summary = "Lists all available users.",
        path = "/api/user/list",
        operationId = OpenApiOperation.AUTO_GENERATE,
        tags = ["Admin"],
        responses = [OpenApiResponse("200", [OpenApiContent(Array<UserDetails>::class)])]
    )
    override fun doGet(ctx: Context): List<UserDetails> {
        return UserManager.list().map(UserDetails.Companion::of)
    }

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route = "user/list"
}

class ListUserSessionsHandler : GetRestHandler<List<UserSessionDetails>>, AccessManagedRestHandler {

    @OpenApi(
        summary = "Lists all current user sessions.",
        path = "/api/user/sessions",
        operationId = OpenApiOperation.AUTO_GENERATE,
        tags = ["Admin"],
        responses = [OpenApiResponse("200", [OpenApiContent(Array<UserSessionDetails>::class)])]
    )
    override fun doGet(ctx: Context): List<UserSessionDetails> {
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
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody([OpenApiContent(AddUserRequest::class)]),
        tags = ["Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("409", [OpenApiContent(ErrorStatus::class)]),
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val addUserRequest = try {
            ctx.bodyAsClass(AddUserRequest::class.java)
        } catch (e: BadRequestResponse) {
            throw ErrorStatusException(400, "Invalid parameters. This is a programmers error.", ctx)
        } catch (e: InvalidFormatException){
            throw ErrorStatusException(400, "Invalid request format.", ctx)
        }

        try {
            UserManager.addUser(addUserRequest.username, addUserRequest.role, PlainPassword(addUserRequest.password))
        } catch (e: UsernameConflictException) {
            throw ErrorStatusException(409, "Username already exists!", ctx)
        }

        return SuccessStatus("User added")
    }

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route = "user/add"
}

class RemoveUserHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {

    @OpenApi(
        summary = "Removes an existing user.",
        path = "/api/user/remove",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
        tags = ["Admin"],
        queryParams = [
            OpenApiParam("force", Boolean::class, "Ignore active user sessions.")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("403", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val username = ctx.body()

        val force = ctx.queryParam("force")?.toBooleanStrictOrNull() ?: false

        if (username.isBlank()) {
            throw ErrorStatusException(400, "username cannot be empty", ctx)
        }

        if (!UserManager.removeUser(username, force)) {
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
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.PATCH],
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
//            ctx.body<PasswordChangeRequest>()
            ctx.bodyAsClass(PasswordChangeRequest::class.java)
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
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
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

class CreateGroupHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {

    data class CreateGroupRequest(var name: String, var usernames: List<String>)

    @OpenApi(
        summary = "Creates a group with existing, non-duplicate and at least one users.",
        path = "/api/group/create",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody([OpenApiContent(CreateGroupRequest::class)]),
        tags = ["Admin", "Group"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("409", [OpenApiContent(ErrorStatus::class)]),
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val createGroupRequest = try {
            ctx.bodyAsClass(CreateGroupRequest::class.java)
        } catch (e: BadRequestResponse) {
            throw ErrorStatusException(400, "Invalid parameters. This is a programmers error.", ctx)
        } catch (e: InvalidFormatException){
            throw ErrorStatusException(400, "Invalid request format.", ctx)
        }
        if (createGroupRequest.usernames.isEmpty()) { throw ErrorStatusException(400, "usernames cannot be empty", ctx)}

        try {
            UserManager.createGroup(createGroupRequest.name, createGroupRequest.usernames)
        } catch (e: GroupNameConflictException) {
            throw ErrorStatusException(409, "Group name already exists!", ctx)
        } catch (e: UsernameNotFoundException) {
            throw ErrorStatusException(404, e.message!!, ctx)
        }

        return SuccessStatus("Group created")
    }


    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route = "group/create"
}

class RemoveGroupHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {

    @OpenApi(
        summary = "Deletes an existing group.",
        path = "/api/group/remove",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
        tags = ["Admin", "Group"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        val groupName = ctx.body()

        if (groupName.isBlank()) {
            throw ErrorStatusException(400, "Group name cannot be empty", ctx)
        }
        try {
            UserManager.removeGroup(groupName)
        } catch (e: GroupNameNotFoundException) {
            throw ErrorStatusException(404, e.message!!, ctx)
        }

        return SuccessStatus("Group removed")
    }

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route = "group/remove"
}


class ListGroupsHandler : GetRestHandler<List<GroupDetails>>, AccessManagedRestHandler {
    @OpenApi(
        summary = "Lists all groups with corresponding users.",
        path = "/api/group/list",
        operationId = OpenApiOperation.AUTO_GENERATE,
        tags = ["Admin", "Group"],
        responses = [OpenApiResponse("200", [OpenApiContent(Array<GroupDetails>::class)])]
    )
    override fun doGet(ctx: Context): List<GroupDetails> {

        return UserManager.listGroups().map(GroupDetails.Companion::of)
    }

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route = "group/list"
}

class RemoveAllGroupsHandler : DeleteRestHandler<SuccessStatus>, AccessManagedRestHandler {
    @OpenApi(
        summary = "Removes all existing groups.",
        path = "/api/group",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.DELETE],
        tags = ["Admin", "Group"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
        ]
    )
    override fun doDelete(ctx: Context):SuccessStatus {
        UserManager.removeAllGroups()
        return SuccessStatus("All groups removed")
    }

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route = "group"
}

class GetCurrentUserbyUsername : GetRestHandler<SuccessStatus>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "user/check"

    @OpenApi(
        summary = "Returns details for the current session.",
        path = "/api/user/check",
        methods = [HttpMethod.GET],
        tags = ["User"],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token"),
            OpenApiParam("username", String::class, "Username")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(UserSessionDetails::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): SuccessStatus {

        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Invalid user session",
            ctx
        )

        val username = ctx.queryParam("username") ?: throw ErrorStatusException(
            401,
            "Invalid user session",
            ctx
        )

        UserManager.getUserIdFromUsername(username) ?: return SuccessStatus("The username is incorrect")

        if(!UserManager.checkIfUserIsActive(username)){
            return SuccessStatus("The BOT is not active")
        }
        if(UserManager.getUserRoleByUserName(username) != UserRole.BOT){
            return SuccessStatus("The username doesn't belongs to a BOT")
        }

        return SuccessStatus("User found")
    }
}