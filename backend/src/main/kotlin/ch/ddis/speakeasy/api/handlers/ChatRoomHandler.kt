package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.chat.*
import ch.ddis.speakeasy.cli.Cli
import ch.ddis.speakeasy.user.SessionId
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.sessionToken
import io.javalin.core.security.Role
import io.javalin.http.Context
import io.javalin.plugin.openapi.annotations.*

data class ChatRoomInfo(
    val uid: String,
    val startTime: Long?,
    val remainingTime: Long,
    val sessions: List<String>,
    val prompt: String
) {
    constructor(room: ChatRoom) : this(
        room.uid.string,
        room.startTime,
        room.remainingTime,
        room.sessions.map { it.sessionId.string },
        room.prompt
    )
}

data class ChatRoomList(val rooms: List<ChatRoomInfo>)

class ListChatRoomsHandler : GetRestHandler<ChatRoomList>, AccessManagedRestHandler {
    override val permittedRoles: Set<Role> = setOf(RestApiRole.USER)
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
            ChatRoomManager.getByUserSession(session.sessionToken).map { ChatRoomInfo(it) }
        )
    }
}

class ListAssessedChatRoomsHandler : GetRestHandler<ChatRoomList>, AccessManagedRestHandler {
    override val permittedRoles: Set<Role> = setOf(RestApiRole.USER)
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
            ChatRoomManager.getAssessedRoomsByUserSession(session.sessionToken).map { ChatRoomInfo(it) }
        )
    }
}

class ListAllChatRoomsHandler : GetRestHandler<ChatRoomList>, AccessManagedRestHandler {
    override val permittedRoles: Set<Role> = setOf(RestApiRole.ADMIN)
    override val route = "rooms/all"

    @OpenApi(
        summary = "Lists all Chatrooms",
        path = "/api/rooms/all",
        tags = ["Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(ChatRoomList::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): ChatRoomList {
        AccessManager.updateLastAccess(ctx.req.session.id)
        return ChatRoomList(
            ChatRoomManager.listAll().map { ChatRoomInfo(it) }
        )
    }
}

class ListAllActiveChatRoomsHandler : GetRestHandler<ChatRoomList>, AccessManagedRestHandler {
    override val permittedRoles: Set<Role> = setOf(RestApiRole.ADMIN)
    override val route = "rooms/active"

    @OpenApi(
        summary = "Lists all active Chatrooms",
        path = "/api/rooms/active",
        tags = ["Admin"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(ChatRoomList::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): ChatRoomList {
        AccessManager.updateLastAccess(ctx.req.session.id)
        return ChatRoomList(
            ChatRoomManager.listActive().map { ChatRoomInfo(it) }
        )
    }
}

data class ChatRoomState(
    val info: ChatRoomInfo,
    val messages: List<RestChatMessage>,
    val reactions: List<ChatMessageReaction>
) {
    constructor(room: ChatRoom, since: Long) : this(
        ChatRoomInfo(room),
        ChatMessage.toRestMessages(room.getMessagesSince(since), room.sessions),
        room.getAllReactions()
    )
}

class GetChatRoomHandler : GetRestHandler<ChatRoomState>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER, RestApiRole.ADMIN)
    override val route = "room/:roomId/:since"

    @OpenApi(
        summary = "Get state and all messages for a chat room since a specified time",
        path = "/api/room/:roomId/:since",
        tags = ["Chat", "Admin"],
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
            if (room.sessions.none { it.sessionToken == session.sessionToken }) {
                throw ErrorStatusException(401, "Unauthorized", ctx)
            }
        }

        return ChatRoomState(room, since)

    }
}

class PostChatMessageHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "room/:roomId"

    @OpenApi(
        summary = "Post a message to a Chatroom.",
        path = "/api/room/:roomId",
        method = HttpMethod.POST,
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

        if (room.sessions.none { it.sessionToken == session.sessionToken }) {
            throw ErrorStatusException(401, "Unauthorized", ctx)
        }

        if (!room.active) {
            throw ErrorStatusException(400, "Chatroom not active", ctx)
        }


        val message = ctx.body()

        if (message.isBlank()) {
            throw ErrorStatusException(400, "message cannot be empty", ctx)
        }

        room.addMessage(ChatMessage(message, session.sessionId, room.nextMessageOrdinal))

        return SuccessStatus("Message received")

    }
}

class PostChatMessageReactionHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "room/:roomId/reaction"

    @OpenApi(
        summary = "Post a chat message reaction to a Chatroom.",
        path = "/api/room/:roomId/reaction",
        method = HttpMethod.POST,
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

        if (room.sessions.none { it.sessionToken == session.sessionToken }) {
            throw ErrorStatusException(401, "Unauthorized", ctx)
        }

        if (!room.active) {
            throw ErrorStatusException(400, "Chatroom not active", ctx)
        }

        val reaction = ctx.body<ChatMessageReaction>()

        try {
            room.addReaction(reaction)
            return SuccessStatus("Message received")
        } catch (e: IllegalArgumentException) {
            throw ErrorStatusException(400, e.localizedMessage, ctx)
        }

    }

}

data class ChatRequest(val username: String)

class RequestChatRoomHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {

    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "rooms/request"

    @OpenApi(
        summary = "Creates a Chatroom with another user.",
        path = "/api/rooms/request",
        method = HttpMethod.POST,
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

        val request = ctx.body<ChatRequest>()

        val requestedSessions = AccessManager.listSessions().filter { it.user.name == request.username }

        if (requestedSessions.size == 0) {
            throw ErrorStatusException(
                404,
                "No session found for user ${request.username}",
                ctx
            )
        }

        if (session.user.role != UserRole.ADMIN && requestedSessions.any { it.user.role != UserRole.BOT }) {
            throw ErrorStatusException(403, "Cannot establish a chat with that user", ctx)
        }

        val relevantSessions = listOf(listOf(session), requestedSessions)

        ChatRoomManager.create(relevantSessions.flatten(), true, "Chatroom requested by ${session.userSessionAlias}")
            .also { it.setEndTime(System.currentTimeMillis() + 10 * 1000 * 60) }

        return SuccessStatus("Chatroom created")

    }

}

data class AliasPair(val session: String, val alias: String)
data class Aliases(val list: List<AliasPair>)

class GetAliasRoomHandler : GetRestHandler<Aliases>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route = "alias/:roomId"

    @OpenApi(
        summary = "Get alias of the other user in the chatroom",
        path = "/api/alias/:roomId",
        method = HttpMethod.GET,
        tags = ["Chat"],
        pathParams = [
            OpenApiParam("roomId", String::class, "Id of the Chatroom"),
        ],
        queryParams = [
            OpenApiParam("session", String::class, "Session Token")
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(Aliases::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): Aliases {

        val session = AccessManager.getUserSessionForSessionToken(ctx.sessionToken()) ?: throw ErrorStatusException(
            401,
            "Unauthorized",
            ctx
        )

        val roomId = (ctx.pathParamMap().getOrElse("roomId") {
            throw ErrorStatusException(400, "Parameter 'roomId' is missing!'", ctx)
        }).UID()

        val room = ChatRoomManager[roomId] ?: throw ErrorStatusException(404, "Room ${roomId.string} not found", ctx)

        if (room.sessions.none { it.sessionToken == session.sessionToken }) {
            throw ErrorStatusException(401, "Unauthorized", ctx)
        }

        return Aliases(
            room.sessions.map { AliasPair(it.sessionId.string, it.userSessionAlias) }
        )

    }
}