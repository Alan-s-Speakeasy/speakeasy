package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.user.UserSession
import ch.ddis.speakeasy.util.UID
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

    fun getByUserSession(session: UserSession): List<ChatRoom> =
        this.chatrooms.values.filter { it.sessions.any { s -> s.sessionToken == session.sessionToken }
            && (((System.currentTimeMillis() - it.startTime) / 60_000) < 60) && !it.assessedBy.contains(session.user.id) }

    fun getAssessedRoomsByUserSession(session: UserSession): List<ChatRoom> =
        this.chatrooms.values.filter { it.sessions.any { s -> s.sessionToken == session.sessionToken } && it.assessedBy.contains(session.user.id) }

    fun getChatPartner(id: UID, session: UserSession): UserId? {
        return this.chatrooms[id]?.sessions?.find { it.user.id != session.user.id }?.user?.id
    }

    fun join(session: UserSession) {
        getByUser(session.user.id).forEach {
            it.sessions.add(session)
            it.join_or_leave()
        }
    }

    fun leave(session: UserSession) {
        getByUser(session.user.id).forEach {
            it.sessions.remove(session)
            it.join_or_leave()
        }
    }

    fun create(sessions: List<UserSession>, log: Boolean = true, prompt: String): ChatRoom {
        val chatRoom = if (log) {
            LoggingChatRoom(sessions = sessions.toMutableSet(), basePath = basePath)
        } else {
            ChatRoom(sessions = sessions.toMutableSet())
        }
        chatRoom.prompt = prompt
        chatrooms[chatRoom.uid] = chatRoom
        return chatRoom
    }

    fun markAsAssessed(session: UserSession, id: ChatRoomId) {
        this.chatrooms[id]?.assessedBy?.add(session.user.id)
    }

    fun isAssessedBy(session: UserSession, id: ChatRoomId): Boolean {
        return this.chatrooms[id]!!.assessedBy.contains(session.user.id);
    }

}