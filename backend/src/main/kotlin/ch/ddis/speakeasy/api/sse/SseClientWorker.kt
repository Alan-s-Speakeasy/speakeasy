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
import kotlinx.coroutines.*

enum class ChatEventType {
    ROOMS,
    MESSAGES,
    REACTIONS;
}

class SseClientWorker(private val client: SseClient): ChatEventListener {

    val workerId = UUID.randomUUID().toString()
    private val rooms = ConcurrentHashMap<ChatRoomId, ChatRoom>()
    private var sessionId: SessionId
    var sessionToken: String
        private set
    var userId: UID
        private set

    override var isActive: Boolean = true
        private set

    private val sendingDelayInterval = 500L
    private var roomSendingJob: Job? = null

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
            SseChatService.removeWorker(this) // This is not necessary, but helps to reduce unnecessary workers saved in the hash table.
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
            return
        }
        rooms.forEach { room ->
            room.addListener(this) // it triggers onNewRoom => send all existing and related rooms

            // send all existing messages and reactions
            room.getAllMessages().takeIf { it.isNotEmpty() }?.let {
                this.send(eventType = ChatEventType.MESSAGES, data = ChatMessage.toSseChatMessages(room, it))
            }
            room.getAllReactions().takeIf { it.isNotEmpty() }?.let {
                this.send(eventType = ChatEventType.REACTIONS, data = ChatMessageReaction.toSseChatReactions(room, it))
            }

        }
    }

    override fun onNewRoom(chatRoom: ChatRoom) {
        if (!this.rooms.keys.contains(chatRoom.uid)) {
            //sanity check, is the user even part of this room
            if (!chatRoom.users.keys.contains(userId)) { return }
            this.rooms[chatRoom.uid] = chatRoom

            // onNewRoom is triggered by a single new room. To avoid sending rooms too many times simultaneously when
            // assigning numerous chat rooms to multiple users, I introduced a delay mechanism here,
            // which can compress multiple room transmissions occurring in extremely short intervals, and only send
            // the latest rooms.
            roomSendingJob?.cancel()
            roomSendingJob = CoroutineScope(Dispatchers.Default).launch {
                delay(sendingDelayInterval)
                sendLatestRooms()
            }
        }
    }

    private fun sendLatestRooms() {
        // send all rooms when there is a new room, because they are not monotonically increasing.
        val rooms = ChatRoomManager.getByUser(userId, bot=false)
        this.send(eventType = ChatEventType.ROOMS, data = ChatRoomList(rooms.map { ChatRoomInfo(it, userId) }))
    }

    override fun onMessage(chatMessage: ChatMessage, chatRoom: ChatRoom) {
        if (!chatRoom.users.keys.contains(userId)) { return }
        val sseChatMessages = ChatMessage.toSseChatMessages(chatRoom, listOf(chatMessage))
        this.send(
            eventType = ChatEventType.MESSAGES,
            data = sseChatMessages  // a list of a single massage
        )
    }

    override fun onReaction(chatMessageReaction: ChatMessageReaction, chatRoom: ChatRoom) {
        if (!chatRoom.users.keys.contains(userId)) { return }
        val sseChatReactions = ChatMessageReaction.toSseChatReactions(chatRoom, listOf(chatMessageReaction))
        this.send(
            eventType = ChatEventType.REACTIONS,
            data = sseChatReactions  // a list of a single reaction
        )
    }

    fun close() {
        client.close()
    }

}
