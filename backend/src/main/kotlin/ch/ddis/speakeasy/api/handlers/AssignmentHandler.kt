package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.assignment.UIChatAssignmentGenerator
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.*

data class AssignmentGeneratorObject(val humans: List<String>, val bots: List<String>)
data class NewAssignmentObject(val humans: List<String>, val bots: List<String>, val prompts: List<String>, val botsPerHuman: Int, val duration: Int)
data class RoundStarted(val remainingTime: Long)

class GetAssignmentGeneratorHandler : GetRestHandler<AssignmentGeneratorObject>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route: String = "assignment"

    @OpenApi(
        summary = "Get the status of the current assignment generator.",
        path = "/api/assignment",
        method = HttpMethod.GET,
        tags = ["Assignment"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(AssignmentGeneratorObject::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): AssignmentGeneratorObject {

        return AssignmentGeneratorObject(
            UIChatAssignmentGenerator.getHumans(),
            UIChatAssignmentGenerator.getBots()
        )
    }
}

class PostNextAssignmentHandler : PostRestHandler<RoundStarted>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route: String = "assignment"

    @OpenApi(
        summary = "Start a new round of assignments.",
        path = "/api/assignment/new",
        method = HttpMethod.POST,
        tags = ["Assignment"],
        requestBody = OpenApiRequestBody([OpenApiContent(NewAssignmentObject::class)]),
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): RoundStarted {

        val newAssignment = try {
            ctx.bodyAsClass(NewAssignmentObject::class.java)
        } catch (e: BadRequestResponse) {
            throw ErrorStatusException(400, "Invalid parameters. This is a programmers error.", ctx)
        }

        val remainingTime = UIChatAssignmentGenerator.newRound(newAssignment)

        return RoundStarted(remainingTime)
    }
}