package ch.ddis.speakeasy.api.sse

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.api.ErrorStatusException
import ch.ddis.speakeasy.api.handlers.ChatRoomInfo
import ch.ddis.speakeasy.api.handlers.ChatRoomList
import ch.ddis.speakeasy.chat.*
import ch.ddis.speakeasy.user.SessionId
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.user.UserSession
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.sessionToken
import io.javalin.http.sse.SseClient
import java.util.*
import java.util.concurrent.ConcurrentHashMap

enum class ChatEventType {
    ROOMS,
    MESSAGE,
    REACTION;
}

class SseClientWorker(private val client: SseClient,
                      private val sseChatService: SseChatService): SseChatEventListener {

    val workerId = UUID.randomUUID().toString()
    private val rooms = ConcurrentHashMap<ChatRoomId, ChatRoom>()
    private var sessionId: SessionId
    var sessionToken: String
        private set
    var userId: UID
        private set

    override var isActive: Boolean = true
        private set

    init {
        val userSession: UserSession = AccessManager.getUserSessionForSessionToken(client.ctx().sessionToken())
            ?: throw ErrorStatusException( 401,  "Unauthorized", client.ctx() )

        if (userSession.user.role == UserRole.BOT) {
            throw ErrorStatusException( 401,  "Unauthorized", client.ctx() )
        } // not accept BOT

        sessionToken = client.ctx().sessionToken()!!
        sessionId = userSession.sessionId
        userId = userSession.user.id.UID()

        client.onClose {
            isActive = false
            sseChatService.removeWorker(this) // This is not necessary, but helps to reduce unnecessary workers saved in the hash table.
        }
        this.onOpen()
    }

    private fun send(eventType: ChatEventType, data: Any) {
        client.sendEvent(event=eventType.toString(), data=data)
    }

    private fun onOpen() {  // when the sse connection opens successfully
        // Handling Special Cases: Send all information when opening (refreshing page) to avoid missing information in frontend.
        // Suppose a worker, w1, disconnects from the SSE connection and reconnects as w2.
        // At this point, w1 and w2 have the same userId, but w2 is not added to the listeners of the related rooms.
        // We need to fix w2's listening for these rooms, as well as to send all existing and related rooms to w2.
        val rooms = ChatRoomManager.getByUser(userId)
        if (rooms.isEmpty()) { // if no rooms, send an empty ChatRoomList
            this.send(eventType=ChatEventType.ROOMS, data=ChatRoomList(rooms=emptyList()))
        } else {
            rooms.forEach { it.addListener(this) } // will trigger onNewRoom => send all existing and related rooms
        }
        // TODO: send all existing massages and reactions.
        //  Currently, the massages are sent one by one.
        //  If a user refreshes the frontend, the previous massages will be lost
    }

    override fun onNewRoom(chatRoom: ChatRoom) { // TODO: When assigning many rooms, backend will send it many times almost simultaneously ...
        if (!this.rooms.keys.contains(chatRoom.uid)) {
            //sanity check, is the user even part of this room
            if (!chatRoom.users.keys.contains(userId)) { return }
            this.rooms[chatRoom.uid] = chatRoom

            // TODO: send all rooms when there is a new room?
            //  I think the following is the most convenient way to do this, but am not sure it is best practice.
            //  Messages can be sent one by one because they are monotonically increasing, unlike rooms.
            //  If we also want to send rooms one by one, it would require a lot of additional logic to detect
            //  the disappearance and changes of rooms, and so on (e.g., to trigger an event that some room has not
            //  been active?).

            val rooms = ChatRoomList(
                ChatRoomManager.getByUser(userId, bot=false).map { ChatRoomInfo(it, userId) }
            )
            this.send(eventType=ChatEventType.ROOMS, data=rooms)
        }
    }

    override fun onMessage(chatMessage: ChatMessage, chatRoom: ChatRoom) {
        if (!chatRoom.users.keys.contains(userId)) { return }
        val sseChatMessage = ChatMessage.toSseChatMessage(chatRoom, chatMessage)
        this.send(
            eventType = ChatEventType.MESSAGE,
            data = sseChatMessage
        )
    }

    override fun onReaction(chatMessageReaction: ChatMessageReaction, chatRoom: ChatRoom) {
        if (!chatRoom.users.keys.contains(userId)) { return }
        val sseChatReaction = ChatMessageReaction.toSseChatReaction(chatRoom, chatMessageReaction)
        this.send(
            eventType = ChatEventType.REACTION,
            data = sseChatReaction
        )
    }

    fun close() {
        client.close()
    }

}
