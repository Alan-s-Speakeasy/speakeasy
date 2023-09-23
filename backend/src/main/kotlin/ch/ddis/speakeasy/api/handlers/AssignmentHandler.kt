package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.assignment.UIChatAssignmentGenerator
import ch.ddis.speakeasy.feedback.FeedbackManager
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.openapi.*

data class SelectedUsers(var humans: List<String>, var bots: List<String>, var admins: List<String>, var evaluator: List<String>)
data class AssignmentGeneratorObject(val humans: List<String>, val bots: List<String>, val admins: List<String>, val evaluator: List<String>, val active: List<String>, val selected: SelectedUsers, val assignments: List<GeneratedAssignment>, val prompts: List<String>, val formName: String, val botsPerHuman: Int, val duration: Int, val round: Int, val remainingTime: Long, val rooms: List<ChatRoomAdminInfo>)
data class NewAssignmentObject(val humans: List<String>, val bots: List<String>, val admins: List<String>, val prompts: List<String>, val botsPerHuman: Int, val duration: Int, val formName: String)
data class GeneratedAssignment(val human: String, val bot: String, val prompt: String, val formName: String)
data class RoundStarted(val remainingTime: Long)

class PostAssignmentGeneratorHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route: String = "assignment/new"

    @OpenApi(
        summary = "Initialize a new assignment generator.",
        path = "/api/assignment/new",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        tags = ["Assignment"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
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
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Assignment"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(AssignmentGeneratorObject::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): AssignmentGeneratorObject {
        return UIChatAssignmentGenerator.getStatus()
    }
}

class PostGenerateAssignmentHandler : PostRestHandler<List<GeneratedAssignment>>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route: String = "assignment/round"

    @OpenApi(
        summary = "Generate a new assignment round",
        path = "/api/assignment/round",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        tags = ["Assignment"],
        requestBody = OpenApiRequestBody([OpenApiContent(NewAssignmentObject::class)]),
        responses = [
            OpenApiResponse("200", [OpenApiContent(List::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): List<GeneratedAssignment> {

        val newAssignment = try {
            ctx.bodyAsClass(NewAssignmentObject::class.java)
        } catch (e: BadRequestResponse) {
            throw ErrorStatusException(400, "Invalid parameters. This is a programmers error.", ctx)
        }

        if ( !FeedbackManager.isValidFormName(newAssignment.formName) ) {
            throw ErrorStatusException(404, "The feedback form name is not valid", ctx)
        }

        if (newAssignment.humans.isEmpty() || (newAssignment.bots.isEmpty() && newAssignment.admins.isEmpty())) {
            throw ErrorStatusException(404, "A number of humans and bots need to be selected.", ctx)
        }
        if (newAssignment.prompts.isEmpty()) {
            throw ErrorStatusException(404, "A number of prompts need to be provided.", ctx)
        }

        var assignment = emptyList<GeneratedAssignment>()
        (1..3).map {
            val round = UIChatAssignmentGenerator.generateNewRound(newAssignment)
            if (round.second) {
                return round.first
            }
            assignment = round.first
        }

        if (assignment.isEmpty()) {
            throw ErrorStatusException(400, "No assignment could be created.", ctx)
        }
        return assignment
    }
}

class PatchStartAssignmentHandler : PatchRestHandler<RoundStarted>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route: String = "assignment/round"

    @OpenApi(
        summary = "Start the generated assignment round",
        path = "/api/assignment/round",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.PATCH],
        requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
        tags = ["Assignment"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(RoundStarted::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPatch(ctx: Context): RoundStarted {

        var evaluatorSelected = false

        if(ctx.body() == "true"){
            evaluatorSelected = true
        }

        val remainingTime = UIChatAssignmentGenerator.startNewRound(evaluatorSelected)

        return RoundStarted(remainingTime)
    }
}

class DeleteAssignmentGeneratorHandler : DeleteRestHandler<SuccessStatus>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route: String = "assignment"

    @OpenApi(
        summary = "Delete the active assignment generator.",
        path = "/api/assignment",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.DELETE],
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