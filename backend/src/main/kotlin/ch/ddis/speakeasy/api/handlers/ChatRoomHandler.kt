package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.chat.*
import ch.ddis.speakeasy.cli.Cli
import ch.ddis.speakeasy.db.ChatRepository
import ch.ddis.speakeasy.db.UserId
import ch.ddis.speakeasy.feedback.FormManager
import ch.ddis.speakeasy.user.SessionId
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.sessionToken
import com.opencsv.CSVWriterBuilder
import io.javalin.http.Context
import io.javalin.json.jsonMapper
import io.javalin.json.toJsonString
import io.javalin.openapi.*
import io.javalin.security.RouteRole
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ChatRoomUserAdminInfo(val alias: String, val username: String)

data class ChatRoomInfo(
    val assignment: Boolean,
    val formRef: String,
    val uid: String,
    val startTime: Long,
    val remainingTime: Long,
    val userAliases: List<String>,
    val alias: String?,
    val prompt: String,
    val testerBotAlias: String,
    val markAsNoFeedback: Boolean
) {
    constructor(room: ChatRoom, userId: UserId) : this(
        room.assignment,
        room.formRef,
        room.uid.string,
        room.startTime,
        room.remainingTime,
        room.users.values.toList(),
        room.users[userId],
        room.prompt,
        room.testerBotAlias,
        room.markAsNoFeedback
    )
}

data class ChatRoomAdminInfo(
    val assignment: Boolean,
    val formRef: String,
    val uid: String,
    val startTime: Long,
    val remainingTime: Long,
    val users: List<ChatRoomUserAdminInfo>,
    val prompt: String,
    val markAsNoFeedback: Boolean
) {
    constructor(room: ChatRoom) : this(
        room.assignment,
        room.formRef,
        room.uid.string,
        room.startTime,
        room.remainingTime,
        room.users.map { ChatRoomUserAdminInfo(it.value, UserManager.getUsernameFromId(it.key) ?: "n/a") },
        room.prompt,
        room.markAsNoFeedback
    )
}

data class ChatRoomList(val rooms: List<ChatRoomInfo>)
data class ChatRoomAdminList(val numOfAllRooms: Int, val rooms: List<ChatRoomAdminInfo>)

class ListChatRoomsHandler : GetRestHandler<ChatRoomList>, AccessManagedRestHandler {
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.USER)
    override val route = "rooms"

    @OpenApi(
        summary = "Lists all Chatrooms for current user",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        path = "/api/rooms",
        tags = ["Chat"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(ChatRoomList::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ]
    )
    override fun doGet(ctx: Context): ChatRoomList {

        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Unauthorized",
            ctx
        )

        return ChatRoomList(
            ChatRoomManager.getByUser(session.user.id.UID(), session.user.role == UserRole.BOT)
                .map { ChatRoomInfo(it, session.user.id.UID()) }
        )
    }
}

class ListAssessedChatRoomsHandler : GetRestHandler<ChatRoomList>, AccessManagedRestHandler {
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.USER)
    override val route = "rooms/assessed"

    @OpenApi(
        summary = "Lists all assessed chatrooms for current user (including chatrooms marked as no need for assessment)",
        path = "/api/rooms/assessed",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Chat"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(ChatRoomList::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ]
    )
    override fun doGet(ctx: Context): ChatRoomList {

        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Unauthorized",
            ctx
        )

        return ChatRoomList(
            ChatRoomManager.getAssessedOrMarkedRoomsByUserId(session.user.id.UID())
                .map { ChatRoomInfo(it, session.user.id.UID()) }
        )
    }
}

class ListAllChatRoomsHandler : GetRestHandler<ChatRoomAdminList>, AccessManagedRestHandler {
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.ADMIN)
    override val route = "rooms/all"

    @OpenApi(
        summary = "Lists all Chatrooms with pagination, ordered by descending startTime, filtered by users and time range.",
        path = "/api/rooms/all",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Admin"],
        queryParams = [
            OpenApiParam("page", Int::class, "page number for pagination. Defaults to 1."),
            OpenApiParam(
                "limit",
                Int::class,
                "number of rooms to return per page. If not specified, there is no limit."
            ),
            OpenApiParam(
                "timeRange",
                String::class,
                "Comma-separated list of two timestamps UNIX MILLISECONDS to filter rooms by STARTING time range. " +
                        "If not specified, all rooms are returned."
            ),
            OpenApiParam(
                name = "userTuples",
                type = Array<String>::class,
                description = "List of tuple in the format 'userId1,userId2' passed as repeated query parameters." +
                        "This parameter contains the list of 2 tuples users that should be present in the chatroom. " +
                        "If not specified, all rooms are returned." +
                        "If one userId is blank, it is considered as a wildcard",
                required = false
            )
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(ChatRoomAdminList::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): ChatRoomAdminList {
        val page = ctx.queryParam("page")?.toIntOrNull() ?: 1
        val limit = ctx.queryParam("limit")?.toIntOrNull()
        // Retrieve 'timeRange' and convert to List<Long> or null if missing
        val timeRange = ctx.queryParam("timeRange")?.split(",")?.mapNotNull { it.trim().toLongOrNull() }
        if (timeRange != null && timeRange.size != 2) {
            throw IllegalArgumentException("timeRange must contain exactly two timestamps.")
        }
        // If one id string is empty, UserId will be of type Invalid. This is used as a wildcard (all chatrooms with the other user, regardless of the other user)
        val userTuples = ctx.queryParams("userTuples").map { it.split(",").map { UserId(it.trim()) } }

        // Fetch and sort all rooms
        val allRooms: List<ChatRoom> = ChatRoomManager.listAll().sortedByDescending { it.startTime }

        // Filter rooms based on provided parameters.
        // This typically should be done in a proper database at some point.
        val filteredRooms = allRooms.filter { room ->
            // Filter by time range if timeRange is provided
            (timeRange == null || (room.startTime in timeRange[0]..timeRange[1])) &&
                    // Equivalent to usertuple[i] \subseteq room.users. If none of userIds are invalid, this is equivalent to
                    // check subset equality. (an "invalidID" here Is used a wildcard)
                    // This should be a proper SQL/else command instead of doing O(nm) stuff, but whatever.
                    (userTuples.isEmpty() || userTuples.any { tuple ->
                        tuple.all { userId ->
                            UserId.isInvalid(userId) || room.users.containsKey(
                                userId
                            )
                        }
                    })
        }
        if (filteredRooms.isEmpty()) {
            return ChatRoomAdminList(0, emptyList())
        }

        // Apply pagination to allRooms based on page and limit
        val startIndex = (page - 1) * (limit ?: filteredRooms.size)
        val endIndex = startIndex + (limit ?: filteredRooms.size)
        val paginatedRooms = filteredRooms.subList(
            startIndex.coerceAtLeast(0),
            endIndex.coerceAtMost(filteredRooms.size)
        )

        return ChatRoomAdminList(
            numOfAllRooms = filteredRooms.size,
            rooms = paginatedRooms.map { ChatRoomAdminInfo(it) }
        )
    }
}

class ListAllActiveChatRoomsHandler : GetRestHandler<ChatRoomAdminList>, AccessManagedRestHandler {
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.ADMIN)
    override val route = "rooms/active"

    @OpenApi(
        summary = "Lists all active Chatrooms",
        path = "/api/rooms/active",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(ChatRoomAdminList::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): ChatRoomAdminList {
        val rooms = ChatRoomManager.listActive().map { ChatRoomAdminInfo(it) }
        return ChatRoomAdminList(
            rooms.size,
            rooms
        )
    }
}

data class ChatRoomState(
    val info: ChatRoomInfo,
    val messages: List<RestChatMessage>,
    val reactions: List<ChatMessageReaction>
) {
    constructor(room: ChatRoom, since: Long, userId: UserId) : this(
        ChatRoomInfo(room, userId),
        ChatMessage.toRestMessages(room.getMessagesSince(since, userId)),
        ChatRoomManager.getReactionsForChatRoom(room.uid)
    )
}

class GetChatRoomHandler : GetRestHandler<ChatRoomState>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER, RestApiRole.ADMIN, RestApiRole.EVALUATOR)
    override val route = "room/{roomId}"

    @OpenApi(
        summary = "Get state and all messages for a chat room since a specified time",
        path = "/api/room/{roomId}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Chat"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom", required = true),
        ],
        queryParams = [
            OpenApiParam("since", Long::class, "Timestamp for new messages", required = true),
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(ChatRoomState::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): ChatRoomState {

        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Unauthorized",
            ctx
        )

        val roomId = (ctx.pathParamMap().getOrElse("roomId") {
            throw ErrorStatusException(400, "Parameter 'roomId' is missing!'", ctx)
        }).UID()

        val since = ctx.queryParam("since")?.toLongOrNull()
            ?: throw ErrorStatusException(400, "Parameter 'since' is missing!/ill formatted", ctx)

        val room = ChatRoomManager[roomId] ?: throw ErrorStatusException(404, "Room ${roomId.string} not found", ctx)

        if (session.user.role != UserRole.ADMIN) {
            if (!room.users.containsKey(session.user.id.UID())) {
                throw ErrorStatusException(401, "Unauthorized", ctx)
            }
        }

        return ChatRoomState(room, since, session.user.id.UID())

    }
}

class ExportChatRoomsHandler : GetRestHandler<Unit>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.ADMIN)
    override val route = "rooms/export"

    override val parseAsJson = false


    @OpenApi(
        summary = "Export specified chatrooms as JSON or CSV. In case of CSV, a ZIP file is returned.",
        path = "/api/rooms/export",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Admin"],
        queryParams = [
            OpenApiParam("roomsIds", String::class, "Comma-separated list of roomIds to export", required = true),
            OpenApiParam("format", String::class, "Format of the export (json or csv, default JSON)", required = false),
        ],

        responses = [
            OpenApiResponse(
                "200",
                // NOTE : Although the application type is set to zip, it also works with json files as both are treated by
                // blobs by openAPI generator.
                [OpenApiContent(ByteArray::class, "application/zip")],
            ),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context) {
        // Get list of UID of the chatrooms :
        val roomIDs = ctx.queryParam("roomsIds")?.split(",")?.map { it.UID() }
            ?: throw ErrorStatusException(400, "Parameter 'roomsIds' is missing!/ill formatted", ctx)
        val serializedChatRooms = ChatRoomManager.exportChatrooms(roomIDs)
        if (serializedChatRooms.isEmpty()) {
            throw ErrorStatusException(404, "No chatrooms found with the provided roomIds", ctx)
        }
        if (ctx.queryParam("format") == "csv") {
            exportCSVToContext(ctx, serializedChatRooms)
            return
        }
        exportJsonToContext(ctx, serializedChatRooms)
    }

    /**
     * Exports a set of chatrooms as JSON or CSV and directly write it back to the context.
     *
     * @param ctx the context of the request
     * @param serializedChatRooms the list of chatrooms to export
     * @return the exported chatrooms as a string, in JSON format
     */
    private fun exportJsonToContext(ctx: Context, serializedChatRooms: List<ExportableChatRoom>): Unit {
        ctx.header("Content-Type", "application/json")
        ctx.header("Content-Disposition", "attachment; filename=\"chatrooms.json\"")
        val output = ctx.jsonMapper().toJsonString(serializedChatRooms)
        ctx.result(output)
    }

    /**
     * Creates a ZIP containing the CSV files and writes it back to the context.
     *
     * @ctx the context of the request
     * @serializedChatRooms the list of chatrooms to export
     * @return the exported chatrooms zipped together.
     */
    private fun exportCSVToContext(ctx: Context, serializedChatRooms: List<ExportableChatRoom>): Unit {
        // Create a byte array output stream to hold the ZIP data
        val byteArrayOutputStream = ByteArrayOutputStream()
        ZipOutputStream(byteArrayOutputStream).use { zipOutputStream ->
            // Loop through each ChatRoom and create individual CSV entries in the ZIP file
            serializedChatRooms.forEach { chatRoom ->
                StringWriter().use { tempWriter ->
                    CSVWriterBuilder(tempWriter).withSeparator('\t').build().use { csvWriter ->
                        ExportableChatRoom.writeToCSV(csvWriter, chatRoom)
                    }
                    val zipEntry = ZipEntry("${chatRoom.startTime}.csv")
                    zipOutputStream.putNextEntry(zipEntry)
                    zipOutputStream.write(tempWriter.toString().toByteArray())
                    zipOutputStream.closeEntry()
                }
            }

        }
        // Prepare the ZIP for download
        ctx.header("Content-Type", "application/zip")
        ctx.header("Content-Disposition", "attachment; filename=\"chatrooms.zip\"")
        ctx.result(byteArrayOutputStream.toByteArray())
    }
}

class PostChatMessageHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER, RestApiRole.BOT, RestApiRole.EVALUATOR)
    override val route = "room/{roomId}"

    @OpenApi(
        summary = "Post a message to a Chatroom.",
        path = "/api/room/{roomId}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
        tags = ["Chat"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom", required = true),
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token"),
            OpenApiParam("recipients", String::class, "Recipients of Message"),
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
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

        //; val room = ChatRoomManager[roomId] ?: throw ErrorStatusException(404, "Room ${roomId.string} not found", ctx)
        val room = ChatRepository.findChatRoomById(roomId) ?: throw ErrorStatusException(
            404,
            "Room ${roomId.string} not found",
            ctx
        )

        // Means that the user is not a member of the chatroom
        val userAlias = room.users[session.user.id.UID()] ?: throw ErrorStatusException(401, "Unauthorized", ctx)

        if (!ChatRoomManager.isChatRoomActive(room.uid)) {
            throw ErrorStatusException(400, "Chatroom not active", ctx)
        }

        val message = ctx.body()
        if (message.isBlank()) {
            throw ErrorStatusException(400, "Message cannot be empty", ctx)
        }

        var recipients = ctx.queryParam("recipients")?.split(",")?.toMutableSet() ?: mutableSetOf()

        if (recipients.isEmpty() || recipients.first().isBlank()) {
            recipients.addAll(room.users.values)
        }

        val (recipientsList, finalMessage) = ChatRoomManager.processMessageAndRecipients(message, room, userAlias)
            ?: return SuccessStatus("Message not received")

        if (recipientsList.isNotEmpty()) {
            recipients = recipientsList
        }

        ChatRoomManager.addMessageTo(
            room, ChatMessage(
                finalMessage,
                session.user.id.UID(), userAlias, SessionId.INVALID, room.nextMessageOrdinal, recipients, isRead = false
            )
        )

        return SuccessStatus("Message received")

    }
}

class PostChatMessageReactionHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "room/{roomId}/reaction"

    @OpenApi(
        summary = "Post a chat message reaction to a Chatroom.",
        path = "/api/room/{roomId}/reaction",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody([OpenApiContent(ChatMessageReaction::class)]),
        tags = ["Chat"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom", required = true),
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
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

        val room = ChatRoomManager[roomId] ?: throw ErrorStatusException(404, "Room ${roomId.string} not found", ctx)

        if (!room.users.containsKey(session.user.id.UID())) {
            throw ErrorStatusException(401, "Unauthorized", ctx)
        }

        if (!ChatRoomManager.isChatRoomActive(room.uid)) {
            throw ErrorStatusException(400, "Chatroom not active", ctx)
        }

        val reaction = ctx.bodyAsClass(ChatMessageReaction::class.java)

        try {
            ChatRoomManager.addReactionTo(room, reaction)
            return SuccessStatus("Message received")
        } catch (e: IllegalArgumentException) {
            throw ErrorStatusException(400, e.localizedMessage, ctx)
        }

    }

}

data class ChatRequest(val username: String, val formName: String = "")

class RequestChatRoomHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "rooms/request"
    private val developmentBotUsername: String = "TesterBot"

    @OpenApi(
        summary = "Creates a Chatroom with another user. If formname is not provided, no form is used",
        path = "/api/rooms/request",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody([OpenApiContent(ChatRequest::class)]),
        tags = ["Chat"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("403", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {

        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401, "Unauthorized", ctx
        )

        if (Cli.assignmentGenerator != null) {
            throw ErrorStatusException(403, "Cannot establish a chat at this time", ctx)
        }

        val request = ctx.bodyAsClass(ChatRequest::class.java)

        // Check if the requested user is connected. This should actually be removed, as we want to allow users
        // to request chatrooms with offline users.
        val requestedSessions = AccessManager.listSessions().filter { it.user.name == request.username }
        if (false && requestedSessions.isEmpty()) {
            throw ErrorStatusException(
                403,
                "No session found for user ${request.username}",
                ctx
            )
        }

        var username = request.username
        var chatRoomTime = 10 * 60 * 1000

        if (username == developmentBotUsername) {
            val testerBotRole = UserRole.TESTER
            val testerBot = ChatRoomManager.getBot(testerBotRole)
            username = testerBot
            chatRoomTime = 60 * 60 * 1000
        }

        val formRef = request.formName
        if (formRef.isNotBlank() && !FormManager.isValidFormName(formRef)) {
            throw ErrorStatusException(404, "The feedback form name is not valid", ctx)
        }

        ChatRoomManager.create(
            userIds = listOf(session.user.id.UID(), UserManager.getUserIdFromUsername(username)!!),
            formRef = formRef,
            log = true,
            prompt = null,
            endTime = System.currentTimeMillis() + chatRoomTime
        )

        return SuccessStatus("Chatroom created")

    }

}

class PatchNewUserHandler : PatchRestHandler<SuccessStatus>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "request/{roomId}"

    @OpenApi(
        summary = "Add a user to an existing Chatroom.",
        path = "/api/request/{roomId}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.PATCH],
        requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
        tags = ["Chat"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom", required = true),
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPatch(ctx: Context): SuccessStatus {

        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Unauthorized",
            ctx
        )
        val roomId = (ctx.pathParamMap().getOrElse("roomId") {
            throw ErrorStatusException(400, "Parameter 'roomId' is missing!'", ctx)
        }).UID()

        val room = ChatRoomManager[roomId] ?: throw ErrorStatusException(404, "Room ${roomId.string} not found", ctx)

        if (!ChatRoomManager.isChatRoomActive(room.uid)) {
            throw ErrorStatusException(400, "Chatroom not active", ctx)
        }

        val newUser = UserManager.getUserIdFromUsername(ctx.body())!!

        ChatRoomManager.addUser(newUser, roomId)

        return SuccessStatus("User added")

    }
}

class CloseChatRoomHandler : PatchRestHandler<SuccessStatus>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "room/{roomId}"

    @OpenApi(
        summary = "Closes a Chatroom.",
        path = "/api/room/{roomId}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.PATCH],
        requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
        tags = ["Chat"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom", required = true),
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPatch(ctx: Context): SuccessStatus {

        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Unauthorized",
            ctx
        )
        val roomId = (ctx.pathParamMap().getOrElse("roomId") {
            throw ErrorStatusException(400, "Parameter 'roomId' is missing!'", ctx)
        }).UID()

        val room = ChatRoomManager[roomId] ?: throw ErrorStatusException(404, "Room ${roomId.string} not found", ctx)

        if (!room.users.containsKey(session.user.id.UID())) {
            throw ErrorStatusException(401, "Unauthorized", ctx)
        }

        room.deactivate()

        return SuccessStatus("Chatroom closed")

    }
}

class GetChatRoomUsersStatusHandler : GetRestHandler<Map<String, Boolean>>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "room/{roomId}/users-status"

    @OpenApi(
        summary = "Get users and their online status for a chat room",
        path = "/api/room/{roomId}/users-status",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Chat"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom", required = true),
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(Map::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): Map<String, Boolean> {

        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Unauthorized",
            ctx
        )
        val roomId = (ctx.pathParamMap().getOrElse("roomId") {
            throw ErrorStatusException(400, "Parameter 'roomId' is missing!'", ctx)
        }).UID()

        val room = ChatRoomManager[roomId] ?: throw ErrorStatusException(404, "Room ${roomId.string} not found", ctx)

        if (!room.users.containsKey(session.user.id.UID())) {
            throw ErrorStatusException(401, "Unauthorized", ctx)
        }

        // Get all active sessions to check which users are online
        val activeSessions = AccessManager.listSessions()
        // Create a map of user aliases to their online status
        return room.users.entries.associate { (userId, alias) ->
            val isOnline = activeSessions.any { it.user.id.UID() == userId }
            alias to isOnline
        }
    }
}

