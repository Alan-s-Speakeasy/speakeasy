package ch.ddis.speakeasy.api.sse

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.api.ErrorStatusException
import ch.ddis.speakeasy.api.sse.SseRoomHandler.listChatRoomsHandler
import ch.ddis.speakeasy.chat.*
import ch.ddis.speakeasy.user.SessionId
import ch.ddis.speakeasy.user.UserSession
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.sessionToken
import io.javalin.http.sse.SseClient
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SseClientWorker(private val client: SseClient,
                      private val sseChatService: SseChatService): SseChatEventListener {

    val workerId = UUID.randomUUID().toString()
    private val rooms = ConcurrentHashMap<ChatRoomId, ChatRoom>()

    private var sessionId: SessionId
    private var sessionToken: String
    var userId: UID
        private set

    override var isActive: Boolean = true
        private set

    init {
        val userSession: UserSession = AccessManager.getUserSessionForSessionToken(client.ctx().sessionToken())
            ?: throw ErrorStatusException(
            401,  "Unauthorized", client.ctx()
            ) // TODO: it seems no need to throw Exceptions here?
        sessionToken = client.ctx().sessionToken()!!
        sessionId = userSession.sessionId
        userId = userSession.user.id.UID()

        client.onClose {
            isActive = false
            sseChatService.removeWorker(this)  // TODO: check if it is necessary?
        }
        this.onOpen()
    }

    private fun onOpen() {  // when the sse connection opens successfully
        // TODO: should send all information when opening (refreshing page) to avoid missing information in frontend.
    }

    override fun onNewRoom(chatRoom: ChatRoom) {
        if (!this.rooms.keys.contains(chatRoom.uid)) {
            //sanity check, is the user even part of this room
            if (!chatRoom.users.keys.contains(userId)) {
                //TODO log
                return
            }
            this.rooms[chatRoom.uid] = chatRoom

            // TODO: not send all rooms? Just send a new room?
            val data = listChatRoomsHandler.doGet(client.ctx()) // todo: handle exception
            client.sendEvent("SseRooms", data=data)
        }
    }

    override fun onMessage(chatMessage: ChatMessage, chatRoom: ChatRoom) {
        TODO("Not yet implemented")
    }

    override fun onReaction(chatMessageReaction: ChatMessageReaction, chatRoom: ChatRoom) {
        TODO("Not yet implemented")
    }

    fun close() {
        client.close()
    }

}
