package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.feedback.FeedbackManager
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.sessionToken
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.*

data class FeedbackAnswerOption(val name: String, val value: Int)
data class FeedbackRequest(val id: String, val type: String, val name: String, val shortname: String, val options: List<FeedbackAnswerOption>)
data class FeedbackRequestList(val requests: List<FeedbackRequest>)
data class FeedbackResponse(val id: String, val value: String)
data class FeedbackResponseList(val responses: List<FeedbackResponse>)

class GetFeedbackRequestListHandler : GetRestHandler<FeedbackRequestList>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.USER)

    override val route: String = "feedback/:roomId"

    @OpenApi(
        summary = "Gets the list of feedback requests for a Chatroom.",
        path = "/api/feedback/:roomId",
        method = HttpMethod.GET,
        tags = ["Feedback"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom"),
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(FeedbackRequestList::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): FeedbackRequestList {


        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Unauthorized",
            ctx
        )
        val roomId = (ctx.pathParamMap().getOrElse("roomId") {
            throw ErrorStatusException(400, "Parameter 'roomId' is missing!'", ctx)
        }).UID()


        val feedbackRequestList = FeedbackManager.readFeedbackRequests()

        return feedbackRequestList
    }

}


class PostFeedbackHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.USER)

    override val route: String = "feedback/:roomId"

    @OpenApi(
        summary = "Returns Feedback for a Chatroom.",
        path = "/api/feedback/:roomId",
        method = HttpMethod.POST,
        requestBody = OpenApiRequestBody([OpenApiContent(FeedbackResponseList::class)]),
        tags = ["Feedback"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom"),
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
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

        if (ChatRoomManager.isAssessedBy(session, roomId)) {
            throw ErrorStatusException(409, "Chatroom already assessed.", ctx)
        }

        FeedbackManager.logFeedback(session, roomId, feedback)
        ChatRoomManager.markAsAssessed(session, roomId)
        return SuccessStatus("Feedback received")
    }

}

class GetFeedbackHistoryHandler : GetRestHandler<FeedbackResponseList>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.USER)

    override val route: String = "feedbackhistory/:roomId"

    @OpenApi(
        summary = "Gets the list of feedback responses for a Chatroom.",
        path = "/api/feedbackhistory/:roomId",
        method = HttpMethod.GET,
        tags = ["Feedback"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom"),
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

        return FeedbackManager.readFeedbackHistoryPerRoom(session.user.id, roomId)
    }

}
