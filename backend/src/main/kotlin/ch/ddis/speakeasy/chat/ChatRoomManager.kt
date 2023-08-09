package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.feedback.FeedbackManager.DEFAULT_FORM_NAME
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
            val users: Map<UserId, String> = objectMapper.readValue(lines[6])
            val messages: MutableList<ChatMessage> = mutableListOf()
            val reactions: HashMap<Int, ChatMessageReaction> = hashMapOf()
            val assessedBy: MutableList<Assessor> = mutableListOf()
            var markAsNoFeedback: Boolean = false

            for (i in 8 until lines.size) {
                when (val chatItem: ChatItemContainer = objectMapper.readValue(lines[i])) {
                    is ChatMessage -> messages.add(chatItem)
                    is ChatMessageReactionContainer -> reactions[chatItem.reaction.messageOrdinal] = chatItem.reaction
                    is Assessor -> assessedBy.add(chatItem)
                    is NoFeedback -> markAsNoFeedback = true  // If this kind of record exists, then it must be true
                }
            }

            val chatRoom = LoggingChatRoom(
                assignment = lines[0].toBoolean(),
                formRef = lines[1],
                uid = UID(lines[2]),
                startTime = lines[3].toLong(),
                endTime = lines[4].toLongOrNull() ?: lines[3].toLong(),
                prompt = lines[5],
                basePath = basePath,
                users = users,
                messages = messages,
                reactions = reactions,
                assessedBy = assessedBy,
                markAsNoFeedback = markAsNoFeedback,
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
            false -> this.chatrooms.values.filter { it.users.contains(userId)
                    && !it.markAsNoFeedback
                    && !it.assessedBy.contains(Assessor(userId))
                    && (((System.currentTimeMillis() - it.startTime) / 60_000) < 60) }
                .sortedBy { it.startTime }
        }

    fun getAssessedOrMarkedRoomsByUserId(userId: UserId): List<ChatRoom> =
        this.chatrooms.values.filter { it.users.contains(userId)
                && (it.assessedBy.contains(Assessor(userId)) || it.markAsNoFeedback)}
            .sortedBy { it.startTime }

    fun getChatPartner(roomId: UID, userId: UserId): UserId? {
        val userIds = this.chatrooms[roomId]?.users?.keys
        return userIds?.find { it != userId }
    }

    fun getFeedbackFormReference(roomId: UID): String? {
        val formRef = this.chatrooms[roomId]?.formRef
        return if (formRef == "") null else formRef
    }

    fun create(userIds: List<UserId>,
//               formRef: String = DEFAULT_FORM_NAME,
               formRef: String,
               log: Boolean = true,
               prompt: String?,
               endTime: Long? = null,
               assignment: Boolean=false): ChatRoom {
        val users = userIds.associateWith { SessionAliasGenerator.getRandomName() }
        val roomPrompt = prompt ?: "Chatroom requested by ${users[userIds[0]]}"
        val chatRoom = if (log) {
            LoggingChatRoom(assignment = assignment, formRef = formRef, users = users, basePath = basePath, endTime = endTime, prompt = roomPrompt)
        } else {
            ChatRoom(assignment = assignment, formRef = formRef, users = users, prompt = roomPrompt)
        }

        chatRoom.prompt = prompt ?: "Chatroom requested by ${users[userIds[0]]}"
        chatrooms[chatRoom.uid] = chatRoom
        return chatRoom
    }

    fun markAsAssessed(session: UserSession, id: ChatRoomId) {
        this.chatrooms[id]?.addAssessor(Assessor(session.user.id.UID()))
    }

    fun isAssessedBy(session: UserSession, id: ChatRoomId): Boolean {
        return this.chatrooms[id]!!.assessedBy.contains(Assessor(session.user.id.UID()))
    }

    fun markAsNoFeedback(id: ChatRoomId) {
        this.chatrooms[id]?.addMarkAsNoFeedback(NoFeedback(mark = true))
    }

    fun isAssignment(id: ChatRoomId): Boolean {
        return this.chatrooms[id]?.assignment ?: false
    }

}