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
    MESSAGE,
    REACTION;
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
    private val roomsToSend: MutableList<ChatRoom> = mutableListOf()

    init {
        val userSession: UserSession = AccessManager.getUserSessionForSessionToken(client.ctx().sessionToken())
            ?: throw ErrorStatusException( 401,  "Unauthorized", client.ctx() )


        sessionToken = client.ctx().sessionToken()!!
        sessionId = userSession.sessionId
        userId = userSession.user.id.UID()

        client.onClose {
            isActive = false
            SseChatService.removeWorker(this) // This is not necessary, but helps to reduce unnecessary workers saved in the hash table.
        }
        this.onOpen()
    }

    private fun onOpen() {
        // Handling Special Cases: reconnect the worker to the related chat rooms if refreshing web page,
        // without re-sending them as new rooms
        val rooms = ChatRoomManager.getByUser(userId)
        rooms.forEach {
            if (it.isActive()) {
                // In theory any active room will be a ListenedChatRoom.
                (it as? ListenedChatRoom)?.addListener(this, alert = false) // not trigger `onNewRoom`
            }
        }
    }


    private fun send(eventType: ChatEventType, data: Any) {
        client.sendEvent(event=eventType.toString(), data=data)
    }

    override fun onNewRoom(chatRoom: ChatRoom) {
        if (!this.rooms.keys.contains(chatRoom.uid)) {
            //sanity check, is the user even part of this room
            if (!chatRoom.users.keys.contains(userId)) { return }
            this.rooms[chatRoom.uid] = chatRoom
            roomsToSend.add(chatRoom)
            // onNewRoom is triggered by a single new room. To avoid sending rooms too many times simultaneously when
            // assigning numerous chat rooms to multiple users, I introduced a delay mechanism here,
            // which can compress multiple room transmissions occurring in extremely short intervals, and only send
            // the latest new rooms.
            roomSendingJob?.cancel()
            roomSendingJob = CoroutineScope(Dispatchers.Default).launch {
                delay(sendingDelayInterval)
                sendLatestRooms()
            }
        }
    }

    private fun sendLatestRooms() {
        // send latest new rooms at one shot.
        this.send(eventType = ChatEventType.ROOMS, data = ChatRoomList(roomsToSend.map { ChatRoomInfo(it, userId) }))
        roomsToSend.clear()
    }

    override fun onMessage(chatMessage: ChatMessage, chatRoom: ChatRoom) {
        if (!chatRoom.users.keys.contains(userId)) { return }
        val userAliasInRoom = chatRoom.users[this.userId]
        if (chatMessage.recipients.isEmpty() || chatMessage.recipients.contains(userAliasInRoom)) {
            val sseChatMessage = ChatMessage.toSseChatMessage(chatRoom, chatMessage)
            this.send(
                eventType = ChatEventType.MESSAGE,
                data = sseChatMessage  // a single massage
            )
        }
    }

    override fun onReaction(chatMessageReaction: ChatMessageReaction, chatRoom: ChatRoom) {
        if (!chatRoom.users.keys.contains(userId)) { return }
        val sseChatReaction = ChatMessageReaction.toSseChatReaction(chatRoom, chatMessageReaction)
        this.send(
            eventType = ChatEventType.REACTION,
            data = sseChatReaction  // a single reaction
        )
    }

    fun close() {
        client.close()
    }

}
