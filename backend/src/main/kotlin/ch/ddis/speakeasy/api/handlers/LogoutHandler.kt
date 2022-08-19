package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.util.sessionToken
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.OpenApi
import io.javalin.plugin.openapi.annotations.OpenApiContent
import io.javalin.plugin.openapi.annotations.OpenApiParam
import io.javalin.plugin.openapi.annotations.OpenApiResponse

class LogoutHandler : RestHandler, GetRestHandler<SuccessStatus> {

    @OpenApi(
        summary = "Clears all user roles of the current session.", path = "/api/logout",
        tags = ["User"],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): SuccessStatus {
        AccessManager.clearUserSession(ctx.sessionToken())
        return SuccessStatus("Logged out")

    }

    override val route = "logout"
}