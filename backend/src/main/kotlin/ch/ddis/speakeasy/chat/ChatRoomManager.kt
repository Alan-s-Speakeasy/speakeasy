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
    private var indexAssistantBot = -1
    private var indexEvaluatorBot = -1


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

        for (userId in userIds) {
            val role = UserManager.getUserRoleByUserID(userId)
            if (role == UserRole.TESTER) {
                chatRoom.testerBotAlias = users[userId]!!
                chatRoom.testingSession = true
            }
            if (role == UserRole.ASSISTANT) {
                chatRoom.testerBotAlias = users[userId]!!
                chatRoom.assistantEvaluation = true
                }
            if (role == UserRole.EVALUATOR) {
                chatRoom.testerBotAlias = users[userId]!!
                chatRoom.automaticEvaluation = true
                println("Registered TesterBot for $userId")
                }
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

    fun getBot(userRole: UserRole): String {

        val activeBots = UserManager.listOfActiveUsersByRole(userRole)
        var botToSend = ""

        if (userRole == UserRole.TESTER) {
            indexTesterBot = (indexTesterBot + 1) % activeBots.size
            botToSend = activeBots[indexTesterBot].name
        }
        else if (userRole == UserRole.EVALUATOR) {
            indexEvaluatorBot = (indexEvaluatorBot + 1) % activeBots.size
            botToSend = activeBots[indexEvaluatorBot].name
        }
        else if (userRole == UserRole.ASSISTANT) {
            indexAssistantBot = (indexAssistantBot + 1) % activeBots.size
            botToSend = activeBots[indexAssistantBot].name
        }
        return botToSend
    }

    fun markAsNoFeedback(id: ChatRoomId) {
        this.chatrooms[id]?.addMarkAsNoFeedback(NoFeedback(mark = true))
    }

    fun isAssignment(id: ChatRoomId): Boolean {
        return this.chatrooms[id]?.assignment ?: false
    }

}