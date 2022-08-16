package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.assignment.UIChatAssignmentGenerator
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.*

data class SelectedUsers(val humans: MutableList<String>, val bots: MutableList<String>, val admins: MutableList<String>)
data class AssignmentGeneratorObject(val humans: List<String>, val bots: List<String>, val admins: List<String>, val active: List<String>, val selected: SelectedUsers, val assignments: List<GeneratedAssignment>, val prompts: List<String>, val botsPerHuman: Int, val duration: Int, val remainingTime: Long, val round: Int)
data class NewAssignmentObject(val humans: List<String>, val bots: List<String>, val admins: List<String>, val prompts: List<String>, val botsPerHuman: Int, val duration: Int)
data class GeneratedAssignment(val human: String, val bot: String, val prompt: String)
data class RoundStarted(val remainingTime: Long)

class PostAssignmentGeneratorHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route: String = "assignment/new"

    @OpenApi(
        summary = "Initialize a new assignment generator.",
        path = "/api/assignment/new",
        method = HttpMethod.POST,
        tags = ["Assignment"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {

        AccessManager.updateLastAccess(ctx.req.session.id)
        UIChatAssignmentGenerator.init()
        return SuccessStatus("Assignment generator created")
    }
}

class GetAssignmentGeneratorHandler : GetRestHandler<AssignmentGeneratorObject>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route: String = "assignment"

    @OpenApi(
        summary = "Get the status of the current assignment generator",
        path = "/api/assignment",
        method = HttpMethod.GET,
        tags = ["Assignment"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(AssignmentGeneratorObject::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): AssignmentGeneratorObject {

        AccessManager.updateLastAccess(ctx.req.session.id)
        return UIChatAssignmentGenerator.getStatus()
    }
}

class PostGenerateAssignmentHandler : PostRestHandler<List<GeneratedAssignment>>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route: String = "assignment/round"

    @OpenApi(
        summary = "Generate a new assignment round",
        path = "/api/assignment/round",
        method = HttpMethod.POST,
        tags = ["Assignment"],
        requestBody = OpenApiRequestBody([OpenApiContent(NewAssignmentObject::class)]),
        responses = [
            OpenApiResponse("200", [OpenApiContent(List::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): List<GeneratedAssignment> {

        AccessManager.updateLastAccess(ctx.req.session.id)

        val newAssignment = try {
            ctx.bodyAsClass(NewAssignmentObject::class.java)
        } catch (e: BadRequestResponse) {
            throw ErrorStatusException(400, "Invalid parameters. This is a programmers error.", ctx)
        }

        if (newAssignment.humans.isEmpty() || newAssignment.bots.isEmpty()) {
            throw ErrorStatusException(404, "A number of humans and bots need to be selected.", ctx)
        }
        if (newAssignment.prompts.isEmpty()) {
            throw ErrorStatusException(404, "A number of prompts need to be provided.", ctx)
        }

        return UIChatAssignmentGenerator.generateNewRound(newAssignment)
    }
}

class PatchStartAssignmentHandler : PatchRestHandler<RoundStarted>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route: String = "assignment/round"

    @OpenApi(
        summary = "Start the generated assignment round",
        path = "/api/assignment/round",
        method = HttpMethod.PATCH,
        tags = ["Assignment"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(RoundStarted::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPatch(ctx: Context): RoundStarted {

        val remainingTime = UIChatAssignmentGenerator.startNewRound()

        return RoundStarted(remainingTime)
    }
}

class DeleteAssignmentGeneratorHandler : DeleteRestHandler<SuccessStatus>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route: String = "assignment"

    @OpenApi(
        summary = "Delete the active assignment generator.",
        path = "/api/assignment",
        method = HttpMethod.DELETE,
        tags = ["Assignment"],
        requestBody = OpenApiRequestBody([OpenApiContent(NewAssignmentObject::class)]),
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doDelete(ctx: Context): SuccessStatus {

        UIChatAssignmentGenerator.clear()
        return SuccessStatus("Assignment generator deleted")
    }
}