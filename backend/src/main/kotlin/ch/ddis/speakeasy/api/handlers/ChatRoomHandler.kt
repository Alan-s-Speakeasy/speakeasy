package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.chat.*
import ch.ddis.speakeasy.cli.Cli
import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.sessionToken
import io.javalin.security.RouteRole
import io.javalin.http.Context
import io.javalin.openapi.*

data class ChatRoomUserAdminInfo(val alias: String, val username: String)

data class ChatRoomInfo(
    val uid: String,
    val startTime: Long?,
    val remainingTime: Long,
    val userAliases: List<String>,
    val alias: String?,
    val prompt: String
) {
    constructor(room: ChatRoom, userId: UserId) : this(
        room.uid.string,
        room.startTime,
        room.remainingTime,
        room.users.values.toList(),
        room.users[userId],
        room.prompt
    )
}

data class ChatRoomAdminInfo(
    val uid: String,
    val startTime: Long?,
    val remainingTime: Long,
    val users: List<ChatRoomUserAdminInfo>,
    val prompt: String
) {
    constructor(room: ChatRoom) : this(
        room.uid.string,
        room.startTime,
        room.remainingTime,
        room.users.map { ChatRoomUserAdminInfo(it.value, UserManager.getUsernameFromId(it.key) ?: "n/a") },
        room.prompt
    )
}

data class ChatRoomList(val rooms: List<ChatRoomInfo>)
data class ChatRoomAdminList(val rooms: List<ChatRoomAdminInfo>)

class ListChatRoomsHandler : GetRestHandler<ChatRoomList>, AccessManagedRestHandler {
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.USER)
    override val route = "rooms"

    @OpenApi(
        summary = "Lists all Chatrooms for current user",
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
            ChatRoomManager.getByUser(session.user.id.UID(), session.user.role == UserRole.BOT).map { ChatRoomInfo(it, session.user.id.UID()) }
        )
    }
}

class ListAssessedChatRoomsHandler : GetRestHandler<ChatRoomList>, AccessManagedRestHandler {
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.USER)
    override val route = "rooms/assessed"

    @OpenApi(
        summary = "Lists all assessed chatrooms for current user",
        path = "/api/rooms/assessed",
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
            ChatRoomManager.getAssessedRoomsByUserId(session.user.id.UID()).map { ChatRoomInfo(it, session.user.id.UID()) }
        )
    }
}

class ListAllChatRoomsHandler : GetRestHandler<ChatRoomAdminList>, AccessManagedRestHandler {
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.ADMIN)
    override val route = "rooms/all"

    @OpenApi(
        summary = "Lists all Chatrooms",
        path = "/api/rooms/all",
        tags = ["Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(ChatRoomAdminList::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): ChatRoomAdminList {
        return ChatRoomAdminList(
            ChatRoomManager.listAll().map { ChatRoomAdminInfo(it) }
        )
    }
}

class ListAllActiveChatRoomsHandler : GetRestHandler<ChatRoomAdminList>, AccessManagedRestHandler {
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.ADMIN)
    override val route = "rooms/active"

    @OpenApi(
        summary = "Lists all active Chatrooms",
        path = "/api/rooms/active",
        tags = ["Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(ChatRoomAdminList::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): ChatRoomAdminList {
        return ChatRoomAdminList(
            ChatRoomManager.listActive().map { ChatRoomAdminInfo(it) }
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
        //ChatMessage.toRestMessages(room.getMessagesSince(since)),
        ChatMessage.toRestMessages(room.getMessagesSince(since, userId)),
        room.getAllReactions()
    )
}

class GetChatRoomHandler : GetRestHandler<ChatRoomState>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER, RestApiRole.ADMIN, RestApiRole.EVALUATOR)
    override val route = "room/{roomId}/{since}"

    @OpenApi(
        summary = "Get state and all messages for a chat room since a specified time",
        path = "/api/room/{roomId}/{since}",
        tags = ["Chat"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom"),
            OpenApiParam("since", Long::class, "Timestamp for new messages"),
        ],
        queryParams = [
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

        val since = ctx.pathParamMap().getOrDefault("since", "0").toLongOrNull() ?: 0

        val room = ChatRoomManager[roomId] ?: throw ErrorStatusException(404, "Room ${roomId.string} not found", ctx)

        if (session.user.role != UserRole.ADMIN) {
            if (!room.users.containsKey(session.user.id.UID())) {
                throw ErrorStatusException(401, "Unauthorized", ctx)
            }
        }

        return ChatRoomState(room, since, session.user.id.UID())

    }
}

class PostChatMessageHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER, RestApiRole.BOT, RestApiRole.EVALUATOR)
    override val route = "room/{roomId}"

    @OpenApi(
        summary = "Post a message to a Chatroom.",
        path = "/api/room/{roomId}",
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
        tags = ["Chat"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom"),
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token"),
            OpenApiParam("private", String::class, "Private Message"),
            OpenApiParam("isDisplayed", String::class, "Displayed Message"),
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

        val userAlias = room.users[session.user.id.UID()] ?: throw ErrorStatusException(401, "Unauthorized", ctx)

        if (!room.active) {
            throw ErrorStatusException(400, "Chatroom not active", ctx)
        }

        val message = ctx.body()
        if (message.isBlank()) {
            throw ErrorStatusException(400, "Message cannot be empty", ctx)
        }
        var privateMessage = false


        if(room.isEvaluation){
            if(session.user.role == UserRole.HUMAN){
                privateMessage = true
            }
            if(session.user.role == UserRole.EVALUATOR){
                privateMessage = ctx.queryParam("private") == "true"
            }
        }

        var isDisplayed = true

        if (ctx.queryParam("isDisplayed") == "false") {
            isDisplayed = false
        }

        val isRead = false

        room.addMessage(ChatMessage(message, userAlias, session.sessionId,
            room.nextMessageOrdinal, privateMessage, isRead, isDisplayed))

        return SuccessStatus("Message received")

    }
}

class PostChatMessageReactionHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "room/{roomId}/reaction"

    @OpenApi(
        summary = "Post a chat message reaction to a Chatroom.",
        path = "/api/room/{roomId}/reaction",
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody([OpenApiContent(ChatMessageReaction::class)]),
        tags = ["Chat"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom"),
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

        if (!room.active) {
            throw ErrorStatusException(400, "Chatroom not active", ctx)
        }

//        val reaction = ctx.body<ChatMessageReaction>() // todo
        val reaction = ctx.bodyAsClass(ChatMessageReaction::class.java)

        try {
            room.addReaction(reaction)
            return SuccessStatus("Message received")
        } catch (e: IllegalArgumentException) {
            throw ErrorStatusException(400, e.localizedMessage, ctx)
        }

    }

}

data class ChatRequest(val username: String, val evaluation: Boolean)

class RequestChatRoomHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "rooms/request"

    @OpenApi(
        summary = "Creates a Chatroom with another user.",
        path = "/api/rooms/request",
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

//        val request = ctx.body<ChatRequest>() // todo
        val request = ctx.bodyAsClass(ChatRequest::class.java)

        val requestedSessions = AccessManager.listSessions().filter { it.user.name == request.username }

        if (requestedSessions.isEmpty()) {
            throw ErrorStatusException(
                404,
                "No session found for user ${request.username}",
                ctx
            )
        }

//        if (session.user.role != UserRole.ADMIN && requestedSessions.any { it.user.role != UserRole.BOT }) {
//            throw ErrorStatusException(403, "Cannot establish a chat with that user", ctx)
//        }

        ChatRoomManager.create(
            listOf(session.user.id.UID(), UserManager.getUserIdFromUsername(request.username)!!), true,
            null, System.currentTimeMillis() + 10 * 1000 * 60, request.evaluation)

        return SuccessStatus("Chatroom created")

    }

}

class PostNewUserHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "request/{roomId}"

    @OpenApi(
        summary = "Post a new user to a Chatroom.",
        path = "/api/request/{roomId}",
        methods = [HttpMethod.POST],
        requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
        tags = ["Chat"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom"),
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

        if (!room.active) {
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
        methods = [HttpMethod.PATCH],
        requestBody = OpenApiRequestBody([OpenApiContent(String::class)]),
        tags = ["Chat"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom"),
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

