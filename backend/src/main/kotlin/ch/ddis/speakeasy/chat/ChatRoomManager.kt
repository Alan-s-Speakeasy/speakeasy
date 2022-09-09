package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.user.UserSession
import ch.ddis.speakeasy.util.SessionAliasGenerator
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
            val users: Map<UserId, String> = objectMapper.readValue(lines[4])
            val messages: MutableList<ChatMessage> = mutableListOf()
            val reactions: HashMap<Int, ChatMessageReaction> = hashMapOf()
            val assessedBy: MutableList<Assessor> = mutableListOf()

            for (i in 6 until lines.size) {
                when (val chatItem: ChatItemContainer = objectMapper.readValue(lines[i])) {
                    is ChatMessage -> messages.add(chatItem)
                    is ChatMessageReaction -> reactions[chatItem.messageOrdinal] = chatItem
                    is Assessor -> assessedBy.add(chatItem)
                }
            }

            val chatRoom = LoggingChatRoom(
                uid = UID(lines[0]),
                startTime = lines[1].toLong(),
                endTime = lines[2].toLongOrNull() ?: lines[1].toLong(),
                prompt = lines[3],
                basePath = basePath,
                users = users,
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

    fun getByUser(userId: UserId, bot: Boolean = false): List<ChatRoom> =
        when (bot) {
            true -> this.chatrooms.values.filter { it.users.contains(userId)
                    && (((System.currentTimeMillis() - it.startTime) / 60_000) < 60) }.sortedBy { it.startTime }
            false -> this.chatrooms.values.filter { it.users.contains(userId) && !it.assessedBy.contains(Assessor(userId))
                    && (((System.currentTimeMillis() - it.startTime) / 60_000) < 60) }.sortedBy { it.startTime }
        }

    fun getAssessedRoomsByUserId(userId: UserId): List<ChatRoom> =
        this.chatrooms.values.filter { it.users.contains(userId) && it.assessedBy.contains(Assessor(userId)) }.sortedBy { it.startTime }

    fun getChatPartner(roomId: UID, userId: UserId): UserId? {
        val userIds = this.chatrooms[roomId]?.users?.keys
        return userIds?.find { it != userId }
    }

    fun create(userIds: List<UserId>, log: Boolean = true, prompt: String?, endTime: Long? = null): ChatRoom {
        val users = userIds.associateWith { SessionAliasGenerator.getRandomName() }
        val roomPrompt = prompt ?: "Chatroom requested by ${users[userIds[0]]}"
        val chatRoom = if (log) {
            LoggingChatRoom(users = users, basePath = basePath, endTime = endTime, prompt = roomPrompt)
        } else {
            ChatRoom(users = users, prompt = roomPrompt)
        }

        chatRoom.prompt = prompt ?: "Chatroom requested by ${users[userIds[0]]}"
        chatrooms[chatRoom.uid] = chatRoom
        return chatRoom
    }

    fun markAsAssessed(session: UserSession, id: ChatRoomId) {
        this.chatrooms[id]?.addAssessor(Assessor(session.user.id))
    }

    fun isAssessedBy(session: UserSession, id: ChatRoomId): Boolean {
        return this.chatrooms[id]!!.assessedBy.contains(Assessor(session.user.id))
    }

}