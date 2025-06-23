package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.api.sse.SseRoomHandler
import ch.ddis.speakeasy.db.ChatRepository
import ch.ddis.speakeasy.db.UserId
import ch.ddis.speakeasy.feedback.FormId
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.Config
import ch.ddis.speakeasy.util.SessionAliasGenerator
import ch.ddis.speakeasy.util.UID
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ChatRoomManager {

    @Deprecated("Should be replaced by a cache")
    private val chatrooms = ConcurrentHashMap<ChatRoomId, ChatRoom>()
    private lateinit var chatsFolder: File
    private val objectMapper = jacksonObjectMapper()
    private var indexTesterBot = -1
    private var indexAssistantBot = -1
    private var indexEvaluatorBot = -1
    private val constantTester = "tester"
    private val constantAssistant = "assistant"
    private val constantUserBot = "bot"

    fun init(config: Config) {
        // Recreates all chatrooms from the log files.
        // This should defintely be encapsulated into a static method of Chatroom/logging room.
        this.chatsFolder = File(config.dataPath, "chatlogs")
        if (!this.chatsFolder.exists() || this.chatsFolder.listFiles()?.isEmpty() != false) {
            println(
                "WARNING: No chatlogs found. No chatrooms will be loaded. " +
                        "The chatlogs files need to be loaded into datapath/chatlogs directory !"
            )
        }
        if (!this.chatsFolder.exists()) {
            this.chatsFolder.mkdirs()
        }
        print("Loading chatrooms from ${this.chatsFolder.normalize().absolutePath} ... ")
        this.chatsFolder.walk().filter { it.isFile }.forEach { file ->
            val lines = file.readLines(Charsets.UTF_8)
            val users: MutableMap<UserId, String> = objectMapper.readValue(lines[6])
            val messages: MutableList<ChatMessage> = mutableListOf()
            val reactions: HashMap<Int, ChatMessageReaction> = hashMapOf()
            val assessedBy: MutableList<Assessor> = mutableListOf()
            var markAsNoFeedback: Boolean = false

            // Parse .log files in order to populate the chatrooms with messages, reactions, etc.
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
                basePath = chatsFolder,
                users = users,
                messages = messages,
                reactions = reactions,
                assessedBy = assessedBy,
                testerBotAlias = "",
                markAsNoFeedback = markAsNoFeedback,
            )
            chatrooms[chatRoom.uid] = chatRoom
        }.also {
            println("Loaded ${chatrooms.size} chatrooms.")
        }
    }

    fun listActive(): List<ChatRoom> = ChatRepository.listActiveChatRooms()

    fun listAll(): List<ChatRoom> = ChatRepository.listChatRooms()

    // Not sure about that. Should we lazy load the chatrooms?
    operator fun get(id: ChatRoomId): ChatRoom? {
        return ChatRepository.findChatRoomById(id)
    }

    fun getByUser(userId: UserId, bot: Boolean = false): List<ChatRoom> =
        when (bot) {
            // TODO: also filter out assessed rooms for bot?
            true -> this.chatrooms.values.filter {
                it.users.contains(userId)
                        && (((System.currentTimeMillis() - it.startTime) / 60_000) < 60)
            }.sortedBy { it.startTime }

            false -> this.chatrooms.values.filter {
                it.users.contains(userId)
                        && !isRoomNoFeedback(it.uid)
                        && !it.assessedBy.contains(Assessor(userId))
                        && (((System.currentTimeMillis() - it.startTime) / 60_000) < 60)
            }
                .sortedBy { it.startTime }
        }

    fun getAssessedOrMarkedRoomsByUserId(userId: UserId): List<ChatRoom> =
        this.chatrooms.values.filter {
            it.users.contains(userId)
                    && (it.assessedBy.contains(Assessor(userId)) || isRoomNoFeedback(it.uid))
        }
            .sortedBy { it.startTime }



    /**
     * Returns the reference to the feedback form for a given chatroom.
     *
     * @throws IllegalArgumentException if the chatroom does not exist.
     */
    fun getFeedbackFormReference(roomId: UID): FormId? {
        return ChatRepository.getFormForChatRoom(roomId)
    }

    /**
     * Handles the creation of a new chatroom.
     *
     * @param userIds List of user ids that should be in the chatroom
     * @param formRef Reference to the form that should be used for the chatroom
     * @param log If the chatroom should be logged
     * @param prompt The prompt for the chatroom
     * @param endTime The end time of the chatroom
     * @param assignment If the chatroom is an assignment
     * @return The created chatroom
     */
    fun create(
        userIds: List<UserId>,
//               formRef: String = DEFAULT_FORM_NAME,
        formRef: String,
        log: Boolean = true, // Shoudl be removed
        prompt: String?,
        endTime: Long? = null,
        assignment: Boolean = false
    ): ChatRoom {

        val users = userIds.associateWith { SessionAliasGenerator.getRandomName() } as MutableMap<UserId, String>
        val roomPrompt = prompt ?: "Chatroom requested by ${users[userIds[0]]}"
        val chatRoom = if (log) {
            LoggingChatRoom(
                assignment = assignment,
                formRef = formRef,
                users = users,
                basePath = chatsFolder,
                endTime = endTime,
                testerBotAlias = "",
                prompt = roomPrompt
            )
        } else {
            ChatRoom(assignment = assignment, formRef = formRef, users = users, prompt = roomPrompt)
        }

        chatRoom.prompt = prompt ?: "Chatroom requested by ${users[userIds[0]]}"
        chatrooms[chatRoom.uid] = chatRoom

        for (userId in userIds) {
            val role = UserManager.getUserRoleByUserID(userId)
            if (role == UserRole.TESTER || role == UserRole.ASSISTANT) {
                val testerBots = UserManager.getUsersIDsFromUserRole(role)
                for (testerBot in testerBots) {
                    if (testerBot in users.keys) {
                        chatRoom.testerBotAlias = users[testerBot]!!
                    }
                }
            } else if (role == UserRole.EVALUATOR) {
                if (endTime != null) {
                    chatRoom.endTime = endTime + 1000 * 60 * 60
                }
            }
        }

        //add listeners
        val listeners = SseRoomHandler.getChatListeners(userIds)
        listeners.forEach {
            chatRoom.addListener(it)
        }
        ChatRepository.createChatRoom(chatRoom, userIds)
        return chatRoom
    }

    /**
     * Adds a message to the given ChatRoom.
     *
     * @param room The ChatRoom to which the message will be added.
     * @param message The ChatMessage to be added.
     */
    fun addMessageTo(room: ChatRoom, message: ChatMessage) {
        ChatRepository.addMessageTo(room.uid, message)
    }

    /**
     * Retrieves all messages for a given ChatRoom.
     *
     * @param room The ChatRoom for which messages are requested.
     * @param since Optional parameter to filter messages since a specific timestamp.
     * @return A list of ChatMessage objects for the specified ChatRoom.
     *
     * @throws IllegalArgumentException if the chat room ID is not found.
     */
    fun getMessagesFor(room: ChatRoomId, since : Long = -1): List<ChatMessage> {
        return ChatRepository.getMessagesFor(room, since)
    }

    /**
     * Adds a reaction to a message in the given ChatRoom.
     *
     * @param room The ChatRoom where the reaction will be added.
     * @param reaction The ChatMessageReaction to be added.
     */
    fun addReactionTo(room: ChatRoom, reaction: ChatMessageReaction) {
        ChatRepository.addReactionToMessage(room.uid, reaction.messageOrdinal, reaction.type)
    }

    /**
     * Gets the reactions for a specific message in a chat room.
     *
     * @param id The ID of the chat room.
     * @param ordinal The ordinal of the message for which reactions are requested.
     * @throws IllegalArgumentException if the chat room ID is not found.x
     */
    fun getReactionsForMessage(id: ChatRoomId, ordinal : Int): List<ChatMessageReactionType> {
        if (ordinal >= ChatRepository.getMessagesCountFor(id)) {
            throw IllegalArgumentException("Message ordinal $ordinal is out of bounds for chat room $id")
        }
        return ChatRepository.getReactionsForMessage(id, ordinal)
    }

    /**
     * Retrieves all reactions for each message in a chat room.
     *
     * @throws IllegalArgumentException if the chat room ID is not found.
     */
    fun getReactionsForChatRoom(id: ChatRoomId): List<ChatMessageReaction> {
        // Returns a list for each ordinal a ChatMessageReaction with the type and ordinal
        return (0.. ChatRepository.getMessagesCountFor(id)).map { ordinal ->
            ordinal to ChatRepository.getReactionsForMessage(id, ordinal)
        }.filter { it.second.isNotEmpty() }. map { ChatMessageReaction(it.first, it.second.last()) }
        // NOTE : Only unique reaction is supported. The latest is returned
    }

    fun addUser(newUserId: UserId, id: ChatRoomId) {
        val newUser = newUserId to SessionAliasGenerator.getRandomName()
        ChatRepository.addUserTo(id, newUser.first, newUser.second)
        this.chatrooms[id]?.users?.put(newUser.first, newUser.second)
    }

    fun getUsersIDofARoom(id: ChatRoomId): List<UserId> {
       return ChatRepository.getParticipants(id)
    }

    /**
     * Processes a received message to identify recipients mentioned using the `@` symbol and extracts the message content.
     *
     * It parses the message to find usernames, identifies corresponding users in the chat room,
     * and compiles a list of recipients along with the actual message content.
     *
     * @param receivedMessage The complete message received from a user, potentially containing mentions and message content.
     *                        Expected format: "@username1 @username2: message content".
     * @param room The `ChatRoom` object representing the current chat room.
     * @param userAlias The alias of the user who sent the message.
     * @return A `Pair` containing:
     *         - A `MutableSet<String>` of recipient aliases identified from the message.
     *         - The extracted message content as a `String`.
     *         Returns `null` if no usernames are found or the message is empty.
     */
    fun processMessageAndRecipients(
        receivedMessage: String,
        room: ChatRoom,
        userAlias: String
    ): Pair<MutableSet<String>, String>? {

        val regex = Regex("@[a-zA-Z0-9_]+")
        val usernameMatches = regex.findAll(receivedMessage)
        val usernames = usernameMatches.map { it.value.drop(1) }.toList()
        val message = receivedMessage.substringAfter(":").trim()
        val recipientsSet = mutableSetOf<String>()

        if (usernames.isNotEmpty() && message.isNotEmpty()) {
            for (user in usernames) {
                // This seems to manually add users ?
                if (user == this.constantTester || user == this.constantAssistant) {
                    val botRole = if (user == this.constantTester) UserRole.TESTER else UserRole.ASSISTANT
                    val testerBots = UserManager.getUsersIDsFromUserRole(botRole)
                    for (testerBot in testerBots) {
                        if (testerBot in room.users.keys) {
                            recipientsSet += room.users[testerBot]!!
                        }
                    }
                }
                // What is this
                if (user == this.constantUserBot) {
                    for (users in room.users.keys) {
                        if (UserManager.getUserRoleByUserID(users) == UserRole.BOT) {
                            recipientsSet += room.users[users]!!
                        }
                    }
                }
                if (UserManager.getUserIdFromUsername(user) in room.users.keys) {
                    recipientsSet += room.users[UserManager.getUserIdFromUsername(user)]!!
                }
            }
            recipientsSet += userAlias
            return Pair(recipientsSet, message)
        } else {
            return Pair(mutableSetOf(), receivedMessage)
        }
    }

    /**
     * Implements a round-robin selection strategy for bots based on their role.
     * Each call returns the next bot in rotation for the specified role,
     * ensuring even distribution of work across all available bots.
     *
     * @param userRole The role of the bot to select (TESTER, EVALUATOR, or ASSISTANT)
     * @return The username of the selected bot
     * @throws IndexOutOfBoundsException if no active bots of the specified role are available
     */
    fun getBot(userRole: UserRole): String {

        val activeBots = UserManager.listOfActiveUsersByRole(userRole)
        var botToSend = ""

        if (userRole == UserRole.TESTER) {
            indexTesterBot = (indexTesterBot + 1) % activeBots.size
            botToSend = activeBots[indexTesterBot].name
        } else if (userRole == UserRole.EVALUATOR) {
            indexEvaluatorBot = (indexEvaluatorBot + 1) % activeBots.size
            botToSend = activeBots[indexEvaluatorBot].name
        } else if (userRole == UserRole.ASSISTANT) {
            indexAssistantBot = (indexAssistantBot + 1) % activeBots.size
            botToSend = activeBots[indexAssistantBot].name
        }
        return botToSend
    }

    fun markAsNoFeedback(id: ChatRoomId) {
        ChatRepository.changeFeedbackStatus(false, id)
    }

    fun isRoomNoFeedback(id: ChatRoomId): Boolean {
        return !ChatRepository.isFeedbackWantedForRoom(id)
    }


    /**
     * Checks if the given chatroom id is an assignment.
     *
     * @param id The chatroom id to check
     * @throws IllegalArgumentException if the chatroom id is not found
     * @return true if the chatroom is an assignment, false otherwise
     */
    fun isAssignment(id: ChatRoomId): Boolean {
        return ChatRepository.isChatroomAssignment(id)
    }

    /**
     * Deactivates the chat room by setting the end time to the current time.
     *
     * @param id The chat room id to deactivate
     */
    fun deactivateChatRoom(id: ChatRoomId) {
        ChatRepository.setEndTimeToChatRoom(id, System.currentTimeMillis())
    }

    /**
     * A room is defined as active if it has a start time and end time, and the current time is within that range.
     */
    fun isChatRoomActive(id: ChatRoomId): Boolean {
        val (startTime, endTime) = ChatRepository.getTimeBoundsForChatRoom(id)
        return endTime == null || (System.currentTimeMillis() in startTime..endTime)
    }

    /**
     * Gets the remaining time for a chat room. If end time is null, it returns Long.MAX_VALUE.
     *
     * @return List of active chatrooms
     * @throws IllegalArgumentException if the chatroom id is not found
     */
    fun getRemainingTimeForChatRoom(id: ChatRoomId): Long {
        val (startTime, endTime) = ChatRepository.getTimeBoundsForChatRoom(id)
        return if (endTime != null) {
            endTime - System.currentTimeMillis()
        } else {
            Long.MAX_VALUE
        }
    }

    /**
     * Exports selected chatrooms to a list of ExportableChatrooms objects.
     *
     * @param chatRoomIds List of chatroom ids to export
     * @return List of SerializedChatRoom objects
     * @throws IllegalArgumentException if a chatroom id is not found
     */
    fun exportChatrooms(chatRoomIds: List<ChatRoomId>): List<ExportableChatRoom> {
        return chatRoomIds.map {
            ChatRoom.export(this.chatrooms.getOrElse(it) {
                throw IllegalArgumentException("Chatroom with id $it not found")
            })
        }
    }
}