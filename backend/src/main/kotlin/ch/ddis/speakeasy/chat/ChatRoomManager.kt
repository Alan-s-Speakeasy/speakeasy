package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
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
    private var indexTesterBot = -1


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
                    is ChatMessageReactionContainer -> reactions[chatItem.reaction.messageOrdinal] = chatItem.reaction
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

    fun create(userIds: List<UserId>, log: Boolean = true, prompt: String?, endTime: Long? = null, development: Boolean? = false, evaluation: Boolean? = false): ChatRoom {
        val users = userIds.associateWith { SessionAliasGenerator.getRandomName() }
        val roomPrompt = prompt ?: "Chatroom requested by ${users[userIds[0]]}"
        val chatRoom = if (log) {
            LoggingChatRoom(users = users, basePath = basePath, endTime = endTime, prompt = roomPrompt)
        } else {
            ChatRoom(users = users, prompt = roomPrompt)
        }

        chatRoom.prompt = prompt ?: "Chatroom requested by ${users[userIds[0]]}"
        chatrooms[chatRoom.uid] = chatRoom
        if (development != null) {
            val testerBotID = UserManager.getUserIdFromUsername("TesterBot")
            chatRoom.testerBotAlias = users[testerBotID]!!
            chatRoom.isDevelopment = development
        }
        if (evaluation != null) {
            val testerBotID = UserManager.getUserIdFromUsername("TesterBot")
            chatRoom.testerBotAlias = users[testerBotID]!!
            chatRoom.isEvaluation = evaluation
        }
        return chatRoom
    }

    fun markAsAssessed(session: UserSession, id: ChatRoomId) {
        this.chatrooms[id]?.addAssessor(Assessor(session.user.id.UID()))
    }

    fun isAssessedBy(session: UserSession, id: ChatRoomId): Boolean {
        return this.chatrooms[id]!!.assessedBy.contains(Assessor(session.user.id.UID()))
    }

    fun addUser(newUserId: UserId, id: ChatRoomId) {
        val newUSer = newUserId to SessionAliasGenerator.getRandomName()
        val currentUsers = this.chatrooms[id]?.users
        if (currentUsers != null) {
            this.chatrooms[id]?.users = currentUsers.plus(newUSer)
        }
    }

    fun getUsersIDofARoom(id: ChatRoomId): List<UserId> {
        return this.chatrooms[id]?.users?.keys?.toList() ?: listOf()
    }

    fun checkMessageRecipients(message: String): Boolean {
        return message.startsWith('@') && message.contains(":")
    }

    fun getRecipientsFromMessage(message: String, room: ChatRoom, userAlias: String): MutableList<String>{
        val listRecipients = mutableListOf<String>()
        val colonIndex = message.indexOf(":")

        val userSubstring = message.substring(0, colonIndex).trim()

        val userList = userSubstring.split(",").map { it.trim().removePrefix("@") }

        for (user in userList) {
            if(UserManager.getUserIdFromUsername(user) in room.users.keys) {
                listRecipients += room.users[UserManager.getUserIdFromUsername(user)]!!
            }
        }
        listRecipients += userAlias
        return listRecipients
    }

    fun getMessageToRecipients(message: String): String {

        val colonIndex = message.indexOf(":")

        return message.substring(colonIndex + 1).trim()

    }

    fun getTesterBot(): String {
        val testerBots = UserManager.listOfActiveUsersByRole(UserRole.EVALUATOR)
        indexTesterBot = (indexTesterBot + 1) % testerBots.size
        return testerBots[indexTesterBot].name

    }

}