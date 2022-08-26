package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.user.UserSession
import ch.ddis.speakeasy.util.UID
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ChatRoomManager {

    private val chatrooms = ConcurrentHashMap<ChatRoomId, ChatRoom>()
    private val basePath: File = File("chatlogs/") //TODO make configurable
    private val objectMapper = jacksonObjectMapper()


    fun init() {
        this.basePath.walk().filter { it.isFile }.forEach { file ->
            val lines = file.readLines(Charsets.UTF_8)
            val userIds: MutableSet<UserId> = objectMapper.readValue(lines[4])
            var sessions: MutableSet<UserSession> = objectMapper.readValue(lines[5])
            val messages: MutableList<ChatMessage> = mutableListOf()
            val reactions: MutableSet<ChatMessageReaction> = mutableSetOf()
            val assessedBy: MutableList<UserId> = mutableListOf()

            for (i in 6 until lines.size) {
                try {
                    val chatMessage: ChatMessage = objectMapper.readValue(lines[i])
                    messages.add(chatMessage)
                } catch (_: Exception) {}
                try {
                    sessions = objectMapper.readValue(lines[i])
                } catch (_: Exception) {}
                try {
                    val reaction: ChatMessageReaction = objectMapper.readValue(lines[i])
                    reactions.add(reaction)
                } catch (_: Exception) {}
                try {
                    val assessor: UserId = objectMapper.readValue(lines[i])
                    assessedBy.add(assessor)
                } catch (_: Exception) {}
            }

            val chatRoom = LoggingChatRoom(
                uid = UID(lines[0]),
                startTime = lines[1].toLong(),
                endTime = lines[2].toLongOrNull() ?: lines[1].toLong(),
                prompt = lines[3],
                basePath = basePath,
                userIds = userIds,
                sessions = sessions,
                messages = messages,
                reactions = reactions,
                assessedBy = assessedBy
            )
            chatrooms[chatRoom.uid] = chatRoom
        }
    }

    fun listActive(): List<ChatRoom> = this.chatrooms.values.filter { it.active }

    fun listAll(): List<ChatRoom> = this.chatrooms.values.toList()

    operator fun get(id: ChatRoomId) = this.chatrooms[id]

    private fun getByUser(id: UserId): List<ChatRoom> =
        this.chatrooms.values.filter { it.userIds.contains(id) }

    fun getByUserSession(session: UserSession): List<ChatRoom> =
        this.chatrooms.values.filter { it.sessions.any { s -> s.sessionToken == session.sessionToken }
            && (((System.currentTimeMillis() - it.startTime) / 60_000) < 60) && !it.assessedBy.contains(session.user.id) }

    fun getAssessedRoomsByUserId(userId: UserId): List<ChatRoom> =
        this.chatrooms.values.filter { it.userIds.contains(userId) && it.assessedBy.contains(userId) }

    fun getChatPartner(id: UID, session: UserSession): UserId? {
        return this.chatrooms[id]?.sessions?.find { it.user.id != session.user.id }?.user?.id
    }

    fun join(session: UserSession) {
        getByUser(session.user.id).forEach {
            it.sessions.add(session)
            it.joinOrLeave()
        }
    }

    fun create(userIds: MutableSet<UserId>, log: Boolean = true, prompt: String, endTime: Long? = null): ChatRoom {
        val sessions = userIds.map { AccessManager.getSessionsForUserId(it) }.flatten().toMutableSet()
        val chatRoom = if (log) {
            LoggingChatRoom(userIds = userIds, sessions = sessions, basePath = basePath, endTime = endTime, prompt = prompt)
        } else {
            ChatRoom(userIds = userIds, sessions = sessions)
        }
        chatRoom.prompt = prompt
        chatrooms[chatRoom.uid] = chatRoom
        return chatRoom
    }

    fun markAsAssessed(session: UserSession, id: ChatRoomId) {
        this.chatrooms[id]?.addAssessor(session)
    }

    fun isAssessedBy(session: UserSession, id: ChatRoomId): Boolean {
        return this.chatrooms[id]!!.assessedBy.contains(session.user.id)
    }

}