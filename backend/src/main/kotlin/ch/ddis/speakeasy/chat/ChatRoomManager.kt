package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.SessionId
import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.user.UserSession
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ChatRoomManager {

    private val chatrooms = ConcurrentHashMap<ChatRoomId, ChatRoom>()
    private val basePath: File = File("chatlogs/") //TODO make configurable

    fun listActive(): List<ChatRoom> = this.chatrooms.values.filter { it.active }

    fun listAll(): List<ChatRoom> = this.chatrooms.values.toList()

    operator fun get(id: ChatRoomId) = this.chatrooms[id]

    fun getByUser(id: UserId): List<ChatRoom> =
        this.chatrooms.values.filter { it.sessions.any { s -> s.user.id == id } }

    fun getByUserSession(sessionId: SessionId): List<ChatRoom> =
        this.chatrooms.values.filter { it.sessions.any { s -> s.sessionId == sessionId } && (((System.currentTimeMillis() - it.startTime) / 60_000) < 60) && !it.assessed }

    fun getAssessedRoomsByUserSession(sessionId: SessionId): List<ChatRoom> =
        this.chatrooms.values.filter { it.sessions.any { s -> s.sessionId == sessionId } && it.assessed }


    fun create(sessions: List<UserSession>, log: Boolean = true, prompt: String): ChatRoom {
        val chatRoom = if (log) {
            LoggingChatRoom(sessions = sessions, basePath = basePath)
        } else {
            ChatRoom(sessions = sessions)
        }
        chatRoom.prompt = prompt
        chatrooms[chatRoom.uid] = chatRoom
        return chatRoom
    }

    fun markAsAssessed(id: ChatRoomId) {
        this.chatrooms[id]?.assessed = true
    }

}