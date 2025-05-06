package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.feedback.FeedbackForm
import ch.ddis.speakeasy.feedback.FeedbackManager
import ch.ddis.speakeasy.feedback.FormManager
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.sessionToken
import com.opencsv.CSVWriterBuilder
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.openapi.*


 // I really wonder the point of all of this when most of these are mapppings.

data class FeedbackFormList(val forms: MutableList<FeedbackForm>)
data class FeedbackResponse(val id: String, val value: String)
data class FeedbackResponseList(val responses: MutableList<FeedbackResponse>)
data class FeedbackResponseItem(var author: String, val recipient: String, val room: String, val responses: List<FeedbackResponse>)
data class FeedbackResponseMapList(val assigned: MutableList<FeedbackResponseItem>, val requested: MutableList<FeedbackResponseItem>)
// NOTE : a request is a question in the feedback form
data class FeedBackStatsOfRequest(val requestID : String, val average : String, val variance : Float, val count : Int)
data class FeedbackResponseStatsItem(val username: String, val count: Int, val statsOfResponsePerRequest: List<FeedBackStatsOfRequest>)
data class FeedbackResponseStatsMapList(
    val assigned: List<FeedbackResponseStatsItem>,
    val requested: List<FeedbackResponseStatsItem>,
    val statsOfAllRequest: List<FeedBackStatsOfRequest> = listOf()
)

/*
// TOOD : Remove
class GetFeedbackFormListHandler : GetRestHandler<FeedbackFormList>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route: String = "feedbackforms_v0"
    @OpenApi(
        summary = "Gets the list of all feedback forms. DEPRECATED.",
        path = "/api/feedbackforms_v0",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Feedback"],
        // DEPCATED :
        deprecated = true,
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
// TODO : Remove
class GetFeedbackFormHandler : GetRestHandler<FeedbackForm>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.USER)

    override val route: String = "feedbackform/{formName}"
    @OpenApi(
        summary = "Gets the feedback form (with form name and questions).",
        path = "/api/feedbackform_v0/{formName}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Feedback"],
        deprecated = true,
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
*/

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

        if (ChatRoomManager.getFeedbackFormReference(roomId) == null) {
            throw ErrorStatusException(403, "No feedback form assigned to this chat.", ctx)
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
            FormManager.isValidFormName(formName)
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

// NOTE : despite its name, this is also available to regular users.
// The difference is that normal user can only access their own feedback average
class GetAdminFeedbackAverageHandler : GetRestHandler<FeedbackResponseStatsMapList>, AccessManagedRestHandler {

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
            OpenApiParam("author", Boolean::class, "author or recipient")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(FeedbackResponseStatsMapList::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): FeedbackResponseStatsMapList {

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
            FormManager.getForm(formName)
        } catch (e: NullPointerException) {
            throw ErrorStatusException(404, "Feedback form '$formName' not found!", ctx)
        }

        val feedbackResponsesPerUserAssigned = FeedbackManager.aggregateFeedbackStatisticsPerUser(
            author = author,
            assignment = true,
            formName = formName
        )
        val feedbackResponsesPerUserRequested = FeedbackManager.aggregateFeedbackStatisticsPerUser(
            author = author,
            assignment = false,
            formName = formName
        )

        val statsOfAllRequest = FeedbackManager.aggregateFeedbackStatisticsGlobal(formName)

        // Return all averages to admin
        if (session.user.role == UserRole.ADMIN) {
            return FeedbackResponseStatsMapList(
                assigned = feedbackResponsesPerUserAssigned,
                requested = feedbackResponsesPerUserRequested,
                statsOfAllRequest = statsOfAllRequest
            )
        }
        // Return only the user's average to human
        else {
            val averageForUserAssigned = feedbackResponsesPerUserAssigned
                .find { it.username == session.user.name }
            val averageForUserRequested = feedbackResponsesPerUserRequested
                .find { it.username == session.user.name }

            return FeedbackResponseStatsMapList(
                assigned = listOfNotNull(averageForUserAssigned),
                requested = listOfNotNull(averageForUserRequested)
            )
        }
    }

}

class ExportFeedbackHandler : GetRestHandler<Unit>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.ADMIN)

    override val route: String = "feedbackaverageexport/{formName}"

    @OpenApi(
        summary = "Exports the feedback responses to a CSV file",
        path = "/api/feedbackaverageexport/{formName}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Admin", "Feedback"],
        pathParams = [
            OpenApiParam("formName", String::class, "Name of the feedback form", required = true),
        ],
        queryParams = [
            OpenApiParam("usernames", String::class, "Comma separated list of usernames to export", required = true),
            OpenApiParam("author", Boolean::class, "author or recipient"),
            OpenApiParam("assignment", Boolean::class, "assignment or request", required = true),
        ],
        responses = [
            OpenApiResponse(
                "200",
                [OpenApiContent(ByteArray::class, "text/csv")],
            ),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context) {
        AccessManager.updateLastAccess(ctx.sessionToken())
        if (ctx.queryParam("usernames") == null) {
            throw ErrorStatusException(400, "Parameter 'usernames' is missing!'", ctx)
        }
        val userIds = (ctx.queryParam("usernames") ?: "").split(",").map { username ->
            val userId = UserManager.getUserIdFromUsername(username.trim())
                ?: throw IllegalArgumentException("Username '$username' does not map to a UserId.")
            userId
        }.toSet()
        val author = ctx.queryParam("author")?.toBooleanStrictOrNull() ?: true
        val formName = (ctx.pathParamMap().getOrElse("formName") {
            throw ErrorStatusException(400, "Parameter 'formName' is missing!'", ctx)
        })
        val assignmentRequested = ctx.queryParam("assignment")?.toBooleanStrictOrNull()
            ?: throw ErrorStatusException(400, "Parameter 'assignment' is missing!'", ctx)

        val feedbackResponsesPerUser = FeedbackManager.aggregateFeedbackStatisticsPerUser(
            userIds = userIds,
            author = author,
            assignment = assignmentRequested,
            formName = formName
        )
        // This also filters out textual questions, so we don't include them in the CSV
        val requestIdToShortName = FormManager.getForm(formName).requests.filter { it.options.isNotEmpty() }.associateBy({ it.id }, { it.shortname })
        ctx.outputStream().use { outputStream ->
            // Structure of the csv file : username, N, request1, request2, ...
            val writer = CSVWriterBuilder(outputStream.writer()).build()
            // Header
            val header = mutableListOf("username")
            for (requestId in requestIdToShortName.keys) {
                header.add(requestIdToShortName[requestId] ?: "unknown")
            }
            writer.writeNext(header.toTypedArray())
            userIds.forEach { userId ->
                val user = UserManager.getUsernameFromId(userId) ?: "UNKNOWN"
                val responses = feedbackResponsesPerUser.find { it.username == user }
                val requestIdToAverage = responses?.statsOfResponsePerRequest?.associateBy({ it.requestID }, { it.average })

                val row = mutableListOf(user)
                for (requestId in requestIdToShortName.keys) {
                    row.add(requestIdToAverage?.get(requestId) ?: "")
                }
                writer.writeNext(row.toTypedArray())
            }
            writer.flush()
        }
        ctx.header("Content-Type", "text/csv")
        ctx.header("Content-Disposition", "attachment; filename=\"feedbacks.csv\"")
    }
}

class PutNewForm()