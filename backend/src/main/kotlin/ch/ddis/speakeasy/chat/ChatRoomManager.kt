package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.api.sse.SseRoomHandler
import ch.ddis.speakeasy.db.ChatRepository
import ch.ddis.speakeasy.db.UserId
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.Config
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

object ChatRoomManager {

    private var indexTesterBot = -1
    private var indexAssistantBot = -1
    private var indexEvaluatorBot = -1
    private val constantTester = "tester"
    private val constantAssistant = "assistant"
    private val constantUserBot = "bot"

    fun init(config: Config) {
    }

    fun listActive(): List<ChatRoom> = ChatRepository.listActiveChatRooms()

    fun listAll(): List<ChatRoom> = ChatRepository.listChatRooms()

    /**
     * Retrieves a chat room by its ID.
     *
     * @param id The ID of the chat room to retrieve.
     * @throws IllegalArgumentException if the chat room ID is not found.
     * @return The ChatRoom instance associated with the given ID, or null if not found.
     */
    fun getFromId(id: ChatRoomId): ChatRoom? {
        return ChatRepository.findChatRoomById(id)
    }

    /**
     * Retrives a list of chatRooms instances that the user is part of.
     *
     * @param userId The ID of the user for whom to retrieve chat rooms.
     * @param bot If true, retrieves chat rooms that are only for bots.
     * @throws IllegalArgumentException if the userId is not found
     * @throws IllegalStateException if a chat room associated with the user is not found
     * @return A list of chat room IDs that the user is part of.
     */
    fun getByUser(userId: UserId, bot: Boolean = false): List<ChatRoom> {
        return ChatRepository.getChatRoomsForUser(userId)
            .map {
                ChatRoomManager.getFromId(it) ?: throw IllegalStateException("Chat room with id $it not found")
            }
    }
//        when (bot) {
//            // TODO: also filter out assessed rooms for bot?
//            true -> this.chatrooms.values.filter {
//                it.users.contains(userId)
//                        && (((System.currentTimeMillis() - it.startTime) / 60_000) < 60)
//            }.sortedBy { it.startTime }
//
//            // Returns if the room has is feedbackable, is not assessed by the user, and is not older than 60 minutes.
//            false -> this.chatrooms.values.filter {
//                it.users.contains(userId)
//                        && !isRoomNoFeedback(it.uid)
//                        && !it.assessedBy.contains(Assessor(userId))
//                        && (((System.currentTimeMillis() - it.startTime) / 60_000) < 60)
//            }
//                .sortedBy { it.startTime }
//        }

    fun getAssessedOrMarkedRoomsByUserId(userId: UserId): List<ChatRoom> {
        TODO()
    }


    /**
     * Handles the creation of a new chatroom.
     *
     * @param userIds List of user ids that should be in the chatroom
     * @param formRef Reference to the form that should be used for the chatroom
     * @param prompt The prompt for the chatroom
     * @param endTime The end time of the chatroom
     * @param assignment If the chatroom is an assignment
     * @return The created chatroom
     */
    fun create(
        userIds: List<UserId>,
//               formRef: String = DEFAULT_FORM_NAME,
        formRef: String = "",
        prompt: String?,
        endTime: Long? = null,
        assignment: Boolean = false
    ): ChatRoom {
        val chatRoom = ChatRepository.createChatRoom(userIds, assignment, prompt = prompt)
        val users = ChatRepository.getParticipantAliases(chatRoom.uid)

        for (userId in userIds) {
            val role = UserManager.getUserRoleByUserID(userId)
            if (role == UserRole.TESTER || role == UserRole.ASSISTANT) {
                val testerBots = UserManager.getUsersIDsFromUserRole(role)
                for (testerBot in testerBots) {
                    chatRoom.testerBotAlias = users[testerBot] ?: continue
                }
            } else if (role == UserRole.EVALUATOR) {
                if (endTime != null) {
                    chatRoom.setEndTime(endTime + 1000 * 60 * 60)
                }
            }
        }

        //add listeners
        val listeners = SseRoomHandler.getChatListeners(userIds)
        listeners.forEach {
            chatRoom.addListener(it)
        }
        return chatRoom
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
    fun getMessagesFor(room: ChatRoomId, since: Long = -1): List<ChatMessage> {
        return ChatRepository.getMessagesFor(room, since)
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
        return chatRoomIds.mapNotNull {
            ChatRepository.findChatRoomById(it)?.export()
        }
    }
}