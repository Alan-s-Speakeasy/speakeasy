package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.feedback.FeedbackManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.sessionToken
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.openapi.*

data class FeedbackAnswerOption(val name: String, val value: Int)
data class FeedbackRequest(val id: String, val type: String, val name: String, val shortname: String, val options: List<FeedbackAnswerOption>)
data class FeedbackForm(val formName: String, val requests: List<FeedbackRequest>)
data class FeedbackFormList(val forms: MutableList<FeedbackForm>)
data class FeedbackResponse(val id: String, val value: String)
data class FeedbackResponseList(val responses: MutableList<FeedbackResponse>)
data class FeedbackResponseItem(var author: String, val recipient: String, val room: String, val responses: List<FeedbackResponse>)
data class FeedbackResponseMapList(val assigned: MutableList<FeedbackResponseItem>, val requested: MutableList<FeedbackResponseItem>)
data class FeedbackResponseAverageItem(val username: String, val count: Int, val responses: List<FeedbackResponse>)
data class FeedbackResponseAverageMapList(val assigned: List<FeedbackResponseAverageItem>, val requested: List<FeedbackResponseAverageItem>)

class GetFeedbackFormListHandler : GetRestHandler<FeedbackFormList>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route: String = "feedbackforms"
    @OpenApi(
        summary = "Gets the list of all feedback forms.",
        path = "/api/feedbackforms",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Feedback"],

        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(FeedbackFormList::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): FeedbackFormList {

        AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Unauthorized",
            ctx
        )

        return FeedbackFormList(forms = FeedbackManager.readFeedbackFromList())
    }
}

class GetFeedbackFormHandler : GetRestHandler<FeedbackForm>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.USER)

    override val route: String = "feedbackform/{formName}"
    @OpenApi(
        summary = "Gets the feedback form (with form name and questions).",
        path = "/api/feedbackform/{formName}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Feedback"],
        pathParams = [
            OpenApiParam("formName", String::class, "Name of the feedback form", required = true),
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(FeedbackForm::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): FeedbackForm {

        AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Unauthorized",
            ctx
        )

        val formName = (ctx.pathParamMap().getOrElse("formName") {
            throw ErrorStatusException(400, "Parameter 'formName' is missing!'", ctx)
        })

        val form = try {
            FeedbackManager.readFeedbackFrom(formName)
        } catch (e: NullPointerException) {
            throw ErrorStatusException(404, "Feedback form '$formName' not found!", ctx)
        }

        return form
    }
}

class PostFeedbackHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.USER)

    override val route: String = "feedback/{roomId}"

    @OpenApi(
        summary = "Posts Feedback for a Chatroom.",
        path = "/api/feedback/{roomId}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody([OpenApiContent(FeedbackResponseList::class)]),
        tags = ["Feedback"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom", required = true),
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("403", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("409", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {

        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Unauthorized",
            ctx
        )
        val roomId = (ctx.pathParamMap().getOrElse("roomId") {
            throw ErrorStatusException(400, "Parameter 'roomId' is missing!'", ctx)
        }).UID()

        val feedback = try {
            ctx.bodyAsClass(FeedbackResponseList::class.java)
        } catch (e: BadRequestResponse) {
            throw ErrorStatusException(400, "Invalid feedback.", ctx)
        }

        val isAssessed = try {
            ChatRoomManager.isAssessedBy(session, roomId)
        } catch (e : NullPointerException) {
            throw ErrorStatusException(404, "Chatroom ${roomId.string} not found.", ctx)
        }

        if (isAssessed) {
            throw ErrorStatusException(409, "Chatroom already assessed.", ctx)
        }

        // if user wants to mark this chat room as "no feedback"
        if (feedback.responses.isEmpty()) {
            if (ChatRoomManager.isAssignment(roomId)
                && ChatRoomManager.getFeedbackFormReference(roomId) != null) {
                throw ErrorStatusException(403, "You must fill-in the feedback form.", ctx)
            }
            if (session.user.role == UserRole.BOT) {
                throw ErrorStatusException(403, "Bot is not allowed to send this request.", ctx)
            }
            ChatRoomManager.markAsNoFeedback(roomId)
            return SuccessStatus("No feedback required for this chat now.")
        }

        FeedbackManager.logFeedback(session, roomId, feedback)
        ChatRoomManager.markAsAssessed(session, roomId)
        return SuccessStatus("Feedback received")
    }

}

class GetFeedbackHistoryHandler : GetRestHandler<FeedbackResponseList>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.USER)
    override val route: String = "feedbackhistory/room/{roomId}"

    @OpenApi(
        summary = "Gets the list of feedback responses for a Chatroom.",
        path = "/api/feedbackhistory/room/{roomId}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Feedback"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom", required = true),
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(FeedbackResponseList::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): FeedbackResponseList {

        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Unauthorized",
            ctx
        )
        val roomId = (ctx.pathParamMap().getOrElse("roomId") {
            throw ErrorStatusException(400, "Parameter 'roomId' is missing!'", ctx)
        }).UID()

        return FeedbackManager.readFeedbackHistoryPerRoom(session.user.id.UID(), roomId)
    }
}

class GetAdminFeedbackHistoryHandler : GetRestHandler<FeedbackResponseMapList>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route: String = "feedbackhistory/form/{formName}"

    @OpenApi(
        summary = "Gets two lists (assigned and requested) of feedback responses to this form",
        path = "/api/feedbackhistory/form/{formName}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Admin", "Feedback"],
        pathParams = [
            OpenApiParam("formName", String::class, "Name of the feedback form", required = true),
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(FeedbackResponseMapList::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): FeedbackResponseMapList {

        val formName = (ctx.pathParamMap().getOrElse("formName") {
            throw ErrorStatusException(400, "Parameter 'formName' is missing!'", ctx)
        })

        try {
            FeedbackManager.readFeedbackFrom(formName)
        } catch (e: NullPointerException) {
            throw ErrorStatusException(404, "Feedback form '$formName' not found!", ctx)
        }

        val assignedFeedbackResponses = FeedbackManager.readFeedbackHistory(assignment = true, formName = formName)
        val requestedFeedbackResponses = FeedbackManager.readFeedbackHistory(assignment = false, formName = formName)

        return FeedbackResponseMapList(
            assigned =  assignedFeedbackResponses,
            requested = requestedFeedbackResponses)
    }
}

class GetAdminFeedbackAverageHandler : GetRestHandler<FeedbackResponseAverageMapList>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN, RestApiRole.USER)

    override val route: String = "feedbackaverage/{formName}"

    @OpenApi(
        summary = "Gets the list of feedback averages (both assigned and requested) per user",
        path = "/api/feedbackaverage/{formName}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Admin", "Feedback"],
        pathParams = [
            OpenApiParam("formName", String::class, "Name of the feedback form", required = true),
        ],
        queryParams = [
            OpenApiParam("author", String::class, "author or recipient")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(FeedbackResponseAverageMapList::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): FeedbackResponseAverageMapList {

        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Unauthorized",
            ctx
        )
        val author = ctx.queryParam("author")?.toBooleanStrictOrNull() ?: true


        AccessManager.updateLastAccess(ctx.sessionToken())

        val formName = (ctx.pathParamMap().getOrElse("formName") {
            throw ErrorStatusException(400, "Parameter 'formName' is missing!'", ctx)
        })

        try {
            FeedbackManager.readFeedbackFrom(formName)
        } catch (e: NullPointerException) {
            throw ErrorStatusException(404, "Feedback form '$formName' not found!", ctx)
        }

        val feedbackResponsesPerUserAssigned = FeedbackManager.readFeedbackHistoryPerUser(author, assignment = true, formName = formName)
        val feedbackResponsesPerUserRequested = FeedbackManager.readFeedbackHistoryPerUser(author, assignment = false, formName = formName)

        // Return all averages to admin
        if (session.user.role == UserRole.ADMIN) {
            return FeedbackResponseAverageMapList(
                assigned = feedbackResponsesPerUserAssigned,
                requested = feedbackResponsesPerUserRequested
            )
        }
        // Return only the user's average to human
        else {
            val averageForUserAssigned = feedbackResponsesPerUserAssigned
                .find { it.username == session.user.name }
            val averageForUserRequested = feedbackResponsesPerUserRequested
                .find { it.username == session.user.name }

            return FeedbackResponseAverageMapList(
                assigned = listOfNotNull(averageForUserAssigned),
                requested = listOfNotNull(averageForUserRequested)
            )
        }
    }
}