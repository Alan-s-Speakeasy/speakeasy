package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.util.sessionToken
import io.javalin.http.Context
import io.javalin.openapi.*

class LogoutHandler : RestHandler, GetRestHandler<SuccessStatus> {

    @OpenApi(
        summary = "Clears all user roles of the current session.",
        path = "/api/logout",
        operationId = OpenApiOperation.AUTO_GENERATE,
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
        return if( AccessManager.clearUserSession(ctx.sessionToken()) ){
            SuccessStatus("Logged out")
        } else {
            SuccessStatus("Not logged in")
        }

    }

    override val route = "logout"
}