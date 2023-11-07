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
    private val constantTester = "tester"
    private val constantAssistant = "assistant"
    private val constantUserBot = "bot"

    fun init() {
        this.basePath.walk().filter { it.isFile }.forEach { file ->
            val lines = file.readLines(Charsets.UTF_8)
            val users: MutableMap<UserId, String> = objectMapper.readValue(lines[6])
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
                testerBotAlias = "",
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

        val users = userIds.associateWith { SessionAliasGenerator.getRandomName() } as MutableMap<UserId, String>
        val roomPrompt = prompt ?: "Chatroom requested by ${users[userIds[0]]}"
        val chatRoom = if (log) {
            LoggingChatRoom(assignment = assignment, formRef = formRef, users = users, basePath = basePath, endTime = endTime, testerBotAlias = "", prompt = roomPrompt)
        } else {
            ChatRoom(assignment = assignment, formRef = formRef, users = users , prompt = roomPrompt)
        }

        chatRoom.prompt = prompt ?: "Chatroom requested by ${users[userIds[0]]}"
        chatrooms[chatRoom.uid] = chatRoom

        for (userId in userIds) {
            val role = UserManager.getUserRoleByUserID(userId)
            if (role == UserRole.TESTER || role == UserRole.ASSISTANT) {
                val testerBots = UserManager.getUsersIDsFromUserRole(role)
                for(testerBot in testerBots){
                    if(testerBot in users.keys){
                        chatRoom.testerBotAlias = users[testerBot]!!
                    }
                }
            }
            else if (role == UserRole.EVALUATOR){
                if (endTime != null) {
                    chatRoom.endTime = endTime + 1000 * 60 * 60
                }
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
        this.chatrooms[id]?.users?.put(newUSer.first, newUSer.second)
    }

    fun getUsersIDofARoom(id: ChatRoomId): List<UserId> {
        return this.chatrooms[id]?.users?.keys?.toList() ?: listOf()
    }

    fun processMessageAndRecipients(receivedMessage: String, room: ChatRoom, userAlias: String): Pair<MutableSet<String>, String>? {

            val regex = Regex("@[a-zA-Z0-9_]+")
            val usernameMatches = regex.findAll(receivedMessage)
            val usernames = usernameMatches.map { it.value.drop(1) }.toList()
            val message = receivedMessage.substringAfter(":").trim()
            val recipientsSet = mutableSetOf<String>()

            if (usernames.isNotEmpty() && message.isNotEmpty()) {
                for (user in usernames) {
                    if(user == this.constantTester || user == this.constantAssistant){
                        val botRole = if(user == this.constantTester) UserRole.TESTER else UserRole.ASSISTANT
                        val testerBots = UserManager.getUsersIDsFromUserRole(botRole)
                        for(testerBot in testerBots){
                            if(testerBot in room.users.keys){
                                recipientsSet += room.users[testerBot]!!
                            }
                        }
                    }
                    if(user == this.constantUserBot){
                        for (users in room.users.keys) {
                            if(UserManager.getUserRoleByUserID(users) == UserRole.BOT){
                                recipientsSet += room.users[users]!!
                            }
                        }
                    }
                    if(UserManager.getUserIdFromUsername(user) in room.users.keys) {
                        recipientsSet += room.users[UserManager.getUserIdFromUsername(user)]!!
                    }
                }
                recipientsSet += userAlias
                return Pair(recipientsSet, message)
            }
            else{
                return Pair(mutableSetOf(), receivedMessage)
                }
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