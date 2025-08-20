package ch.ddis.speakeasy.db

import ch.ddis.speakeasy.chat.ChatMessageReactionType
import ch.ddis.speakeasy.chat.ChatRoomId
import ch.ddis.speakeasy.chat.DatabaseChatRoom
import ch.ddis.speakeasy.feedback.FormId
import ch.ddis.speakeasy.user.SessionId
import ch.ddis.speakeasy.util.UID
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import java.util.*
import ch.ddis.speakeasy.chat.ChatMessage as DomainChatMessage
import ch.ddis.speakeasy.chat.ChatRoom as DomainChatRoom

/**
 * Entity class for ChatRoom table
 */
class ChatRoomEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ChatRoomEntity>(ChatRooms)

    var assignment by ChatRooms.assignment
    var formId by ChatRooms.formId
    var startTime by ChatRooms.startTime
    var endTime by ChatRooms.endTime
    var prompt by ChatRooms.prompt
    val messages by ChatMessageEntity referrersOn ChatMessages.chatRoom
    var participants by UserEntity via ChatroomParticipants
    var testerBotAlias by ChatRooms.testerBotAlias
    var markAsNoFeedback by ChatRooms.markAsNoFeedback
    val feedbackResponses by FeedbackResponseEntity referrersOn FeedbackResponses.room

    /*
    * Convert the ChatRoomEntity to a regular ChatRoom object.
     */
    // NOTE : See https://github.com/JetBrains/Exposed/issues/656#issuecomment-542113164
    // on why we need to wrap the whole thing with dbQuery.
    fun toDomainModel(): DomainChatRoom = DatabaseHandler.dbQuery {
        // See comment below for why we do that. Ho boy I don't like Exposed.
        DatabaseChatRoom(
            uid = id.UID(),
        )
    }
}

/**
 * Entity class for ChatMessage table
 */
class ChatMessageEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<ChatMessageEntity>(ChatMessages)

    var chatroom by ChatRoomEntity referencedOn ChatMessages.chatRoom
    var sender by ChatMessages.sender
    var content by ChatMessages.content
    var timestamp by ChatMessages.timestamp
    var ordinal by ChatMessages.ordinal


    fun toDomainModel(): DomainChatMessage = DatabaseHandler.dbQuery {
        DomainChatMessage(
            authorUserId = sender.UID(),
            message = content,
            time = timestamp,
            ordinal = ordinal.value,
            // NOTE : See https://github.com/JetBrains/Exposed/issues/928
            // It is not possible to nicely get the alias from the many-to-many relationship database.
            authorAlias = ChatroomParticipants.select(ChatroomParticipants.alias).where {
                (ChatroomParticipants.chatRoom eq chatroom.id) and (ChatroomParticipants.user eq sender)
            }.firstOrNull()?.get(ChatroomParticipants.alias) ?: "",
            authorSessionId = SessionId.INVALID
        )
    }
}

class ChatMessageReactionEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<ChatMessageReactionEntity>(ChatReactions)

    var chatroom by ChatRoomEntity referencedOn ChatReactions.chatRoom
    var ordinal by ChatReactions.ordinal
    var reaction by ChatReactions.reaction
    var timestamp by ChatReactions.timestamp
}

/**
 * Repository for chat-related database operations
 */
object ChatRepository {

    /**
     * Finds a chat room by ID
     *
     * @param id The ID of the chat room to find
     * @return The chat room entity if found, null otherwise
     */
    fun findChatRoomById(id: ChatRoomId): DomainChatRoom? = DatabaseHandler.dbQuery {
        ChatRoomEntity.findById(id.toUUID())?.toDomainModel()
    }

    /**
     * Returns a list of all chat rooms in the database. Use carefully, as this can be a large list.
     */
    fun listChatRooms(): List<DomainChatRoom> = DatabaseHandler.dbQuery {
        ChatRoomEntity.all().map { it.toDomainModel() }.toList()
    }

    fun countChatRooms(): Int = DatabaseHandler.dbQuery {
        ChatRoomEntity.all().count().toInt()
    }

    /**
     * Returns a list of all active chat rooms.
     * An active chat room is defined as one that has started before now and either has no end time or an end time in the future.
     */
    fun listActiveChatRooms(): List<DomainChatRoom> = DatabaseHandler.dbQuery {
        ChatRoomEntity.find {
            // Started before now
            (ChatRooms.startTime lessEq System.currentTimeMillis()) and
                    // End time not defined or in the future
                    ((ChatRooms.endTime.isNull()) or (ChatRooms.endTime greater System.currentTimeMillis()))
        }
            .map { it.toDomainModel() }.toList()
    }

    /**
     * Get the formId associated with a chat room
     *
     * @param id The ID of the chat room
     */
    fun getFormForChatRoom(id: ChatRoomId): FormId? = DatabaseHandler.dbQuery {
        val chatRoom = ChatRoomEntity.findById(id.toUUID())
            ?: throw IllegalArgumentException("Chat room with ID ${id.string} not found")
        chatRoom.formId?.UID()
    }

    /**
     * Sets the end time for a chat room
     *
     * @throws IllegalArgumentException if the chatroom does not exist.
     */
    fun setEndTimeToChatRoom(id: ChatRoomId, endTime: Long): Unit = DatabaseHandler.dbQuery {
        val chatRoom = ChatRoomEntity.findById(id.toUUID())
            ?: throw IllegalArgumentException("Chat room with ID ${id.string} not found")
        chatRoom.endTime = endTime
    }

    /**
     * Gets the (startTime, endTime) bounds for a chat room.
     *
     * @param id The ID of the chat room
     * @return A pair containing the start time and end time (nullable) of the chat room
     */
    fun getTimeBoundsForChatRoom(id: ChatRoomId): Pair<Long, Long?> = DatabaseHandler.dbQuery {
        val chatRoom = ChatRoomEntity.findById(id.toUUID())
            ?: throw IllegalArgumentException("Chat room with ID ${id.string} not found")
        val startTime = chatRoom.startTime
        val endTime = chatRoom.endTime
        Pair(startTime, endTime)
    }


    /**
     * Returns true if the room already has feedback applied to.
     */
    fun isRoomAlreadyAssessed(id: ChatRoomId, formId: FormId, authorID: UserId): Boolean {
        // Basically check if there is a feedback response for the given room, form and author.
        return !DatabaseHandler.dbQuery {
            FeedbackResponseEntity.find {
                (FeedbackResponses.room eq id.toUUID()) and
                        (FeedbackResponses.form eq formId.toUUID()) and
                        (FeedbackResponses.author eq authorID.toUUID())
            }.empty()
        }
    }

    /**
     * Creates a new chat room and persists it to the database. Does not add any participants.
     *
     * @param assignment A boolean indicating if the chat room is an assignment.
     * @param markAsNoFeedback A boolean indicating if the chat room should be marked as having no feedback.
     * @param prompt An optional prompt for the chat room.
     * @return The newly created [DomainChatRoom].
     */
    fun createChatRoom(
        assignment: Boolean,
        markAsNoFeedback: Boolean = false,
        prompt: String? = null,
        formId: FormId?
    ): DomainChatRoom = DatabaseHandler.dbQuery {
        val chatRoom = DatabaseChatRoom()
        ChatRoomEntity.new(chatRoom.uid.toUUID()) {
            this.assignment = assignment
            this.formId = if (formId != null) EntityID(formId.toUUID(), FeedbackForms) else null
            this.startTime = System.currentTimeMillis()
            this.prompt = prompt ?: "New chat room"
            this.testerBotAlias = ""
            this.markAsNoFeedback = markAsNoFeedback
        }
        return@dbQuery chatRoom
    }

    /**
     * Adds a message to a chat room with auto-generated ordinal
     *
     * @param chatRoomId The ID of the chat room
     * @param message The message to add (ordinal will be auto-generated if -1)
     * @return The created message with the assigned ordinal
     */
    fun addMessageTo(chatRoomId: ChatRoomId, message: DomainChatMessage): DomainChatMessage = DatabaseHandler.dbQuery {
        val chatRoom_ = ChatRoomEntity.findById(chatRoomId.toUUID())
            ?: throw IllegalArgumentException("Chat room with ID ${chatRoomId.string} not found")

        val MAX_RETRIES = 3

        for (it in 0..MAX_RETRIES) {
            val nextOrdinal = nextMessageOrdinalFor(chatRoomId)
            try {
                // Try to insert the message with the next ordinal
                ChatMessages.insert {
                    it[chatRoom] = chatRoom_.id
                    it[sender] = EntityID(message.authorUserId.toUUID(), Users)
                    it[content] = message.message
                    it[timestamp] = message.time
                    it[ordinal] = nextOrdinal
                }
                message.ordinal = nextOrdinal
                // If successful, return the message with the assigned ordinal
                return@dbQuery message.copy(ordinal = nextOrdinal)
            } catch (e: ExposedSQLException) {
                // If there's a constraint violation on the ordinal, retry with a new ordinal.
                if (it == MAX_RETRIES - 1) throw e // If it's the last attempt, rethrow the exception
            }
        }
        // Should in theory never reach here
        throw IllegalStateException("Could not insert message to chat room $chatRoomId")
    }

    /**
     * Gets all messages for a chat room, sorted by timestamp in ascending order.
     *
     * @param chatRoomId The ID of the chat room
     * @return List of chat messages in the chat room
     * @throws IllegalArgumentException if the chatRoom does not exist
     */
    fun getMessagesFor(chatRoomId: ChatRoomId, since: Long = -1): List<DomainChatMessage> = DatabaseHandler.dbQuery {
        ChatRoomEntity.findById(chatRoomId.toUUID())
            ?: throw IllegalArgumentException("Chat room with ID ${chatRoomId.string} not found")
        if (since > 0) {
            // If a timestamp is provided, filter messages by that timestamp
            ChatMessageEntity.find {
                (ChatMessages.chatRoom eq EntityID(
                    chatRoomId.toUUID(),
                    ChatRooms
                )) and (ChatMessages.timestamp greaterEq since)
            }
                .orderBy(ChatMessages.timestamp to SortOrder.ASC).map { it.toDomainModel() }.toList()
        } else {
            // Otherwise, return all messages for the chat room
            ChatMessageEntity.find { ChatMessages.chatRoom eq EntityID(chatRoomId.toUUID(), ChatRooms) }
                .orderBy(ChatMessages.timestamp to SortOrder.ASC).map { it.toDomainModel() }.toList()
        }
    }

    /**
     * Adds a reaction to a message in a chat room
     *
     * @param chatRoomId The ID of the chat room
     * @param ordinal The ordinal of the message to react to
     * @param reaction The reaction type
     * @param sender The user ID of the sender of the reaction. Not supported yet
     * @throws IllegalArgumentException if the chat room does not exist
     */
    fun addReactionToMessage(
        chatRoomId: ChatRoomId,
        messageOrdinal: Int,
        reaction: ChatMessageReactionType,
        sender: UserId = UserId.INVALID
    ): Unit = DatabaseHandler.dbQuery {
        findChatRoomById(
            chatRoomId
        ) ?: throw IllegalArgumentException("Chat room with ID ${chatRoomId.string} not found")

        if (messageOrdinal < 0) {
            throw IllegalArgumentException("Message ordinal must be non-negative")
        }
        if (messageOrdinal >= getMessagesCountFor(chatRoomId)) {
            throw IllegalArgumentException("Message ordinal must be between 0 and $messageOrdinal")
        }

        ChatReactions.upsert() {
            it[chatRoom] = chatRoomId.toUUID()
            it[ordinal] = messageOrdinal
            // it[sender] = EntityID(reaction.)
            it[ChatReactions.reaction] = reaction
            it[timestamp] = System.currentTimeMillis()
        }
    }

    /**
     * Gets all reactions for a specific message in a chat room, sorted in descending order by timestamp.
     *
     * @param chatRoomId The ID of the chat room
     * @param ordinal The ordinal of the message to get reactions for
     * @return List of reaction types for the specified message
     */
    fun getReactionsForMessage(chatRoomId: ChatRoomId, ordinal: Int): List<ChatMessageReactionType> =
        DatabaseHandler.dbQuery {
            ChatReactions.select(ChatReactions.reaction).orderBy(ChatReactions.timestamp, SortOrder.ASC)
                .where { (ChatReactions.chatRoom eq chatRoomId.toUUID()) and (ChatReactions.ordinal eq ordinal) }
                .map { it[ChatReactions.reaction] }.toList()
        }


    /**
     * Gets the next message ordinal - i.e, next sequential index
     *
     * @param chatRoomId The ID of the chat room
     * @return The next ordinal number for messages in the chat room
     */
    private fun nextMessageOrdinalFor(chatRoomId: ChatRoomId): Int {
        val lastRow = ChatMessages
            .select(ChatMessages.ordinal)                     // we only need the ordinal column
            .where { ChatMessages.chatRoom eq chatRoomId.toUUID() } // restrict to this room
            .orderBy(ChatMessages.ordinal, SortOrder.DESC)   // highest ordinal first
            .limit(1)
            .firstOrNull()


        val current = lastRow?.get(ChatMessages.ordinal)?.value   // unwrap EntityID → Int
        return (current ?: -1) + 1
    }

    /**
     * Adds a user to a chat room
     *
     * @param chatRoomId The ID of the chat room
     * @param userId The ID of the user to add
     * @param alias The alias for the user in the chat room
     */
    fun addUserTo(chatRoomId: ChatRoomId, userId: UserId, alias: String) = DatabaseHandler.dbQuery {
        val chatRoom = ChatRoomEntity.findById(chatRoomId.toUUID())
            ?: throw IllegalArgumentException("Chat room with ID ${chatRoomId.string} not found")
        val user = UserEntity.findById(userId.toUUID())
            ?: throw IllegalArgumentException("User with ID ${userId.string} not found")
        // If the user is already a participant, throw an exception
        if (ChatroomParticipants.selectAll().where {
                (ChatroomParticipants.chatRoom eq chatRoom.id) and
                        (ChatroomParticipants.user eq user.id)
            }.any()) {
            throw IllegalArgumentException("User with ID ${userId.string} is already a participant in chat room ${chatRoomId.string}")
        }

        // Add the user to participants and set the alias in the join table
        ChatroomParticipants.insert {
            it[ChatroomParticipants.chatRoom] = chatRoom.id
            it[ChatroomParticipants.user] = user.id
            it[ChatroomParticipants.alias] = alias
        }
    }

    /**
     * Gets the participants of a chat room
     *
     * @param chatRoomId The ID of the chat room
     */
    fun getParticipants(chatRoomId: ChatRoomId): List<UserId> = DatabaseHandler.dbQuery {
        val chatRoom = ChatRoomEntity.findById(chatRoomId.toUUID())
            ?: throw IllegalArgumentException("Chat room with ID ${chatRoomId.string} not found")
        chatRoom.participants.map { it.id.UID() }.toList()
    }

    /**
     * Gets the mapping participant user IDs to their aliases in a chat room.
     *
     * @param chatRoomId The ID of the chat room
     * @return Map of user IDs to their aliases in the chat room
     * @throws IllegalArgumentException if the chat room does not exist
     */
    fun getParticipantAliases(chatRoomId: ChatRoomId): Map<UserId, String> = DatabaseHandler.dbQuery {
        val chatRoom = ChatRoomEntity.findById(chatRoomId.toUUID())
            ?: throw IllegalArgumentException("Chat room with ID ${chatRoomId.string} not found")
        ChatroomParticipants.selectAll()
            .where { ChatroomParticipants.chatRoom eq chatRoom.id }
            .associate {
                it[ChatroomParticipants.user].UID() to (it[ChatroomParticipants.alias] ?: "")
            }
    }

    /**
     * Changes the feedback status of a chat room
     *
     * @param feedbackWanted Whether the chat room should be marked as having no feedback
     * @param chatRoomId The ID of the chat room
     * @return True if the status was changed successfully, false otherwise
     */
    fun changeFeedbackStatusFor(chatRoomId: ChatRoomId, feedbackWanted: Boolean): Boolean = DatabaseHandler.dbQuery {
        val chatRoom = ChatRoomEntity.findById(chatRoomId.toUUID())
            ?: throw IllegalArgumentException("Chat room with ID ${chatRoomId.string} not found")
        chatRoom.markAsNoFeedback = !feedbackWanted
        true
    }

    fun isFeedbackWantedForRoom(roomId: ChatRoomId): Boolean = DatabaseHandler.dbQuery {
        val room = ChatRoomEntity.findById(roomId.toUUID())
            ?: throw IllegalArgumentException("Room with ID ${roomId.string} not found")

        !room.markAsNoFeedback
    }


    /**
     * Gets all messages for a chat room
     *
     * @param chatRoomId The ID of the chat room
     * @return List of chat message entities
     */
    fun getChatMessages(chatRoomId: ChatRoomId): List<DomainChatMessage> = DatabaseHandler.dbQuery {
        ChatMessageEntity.find { ChatMessages.chatRoom eq EntityID(chatRoomId.toUUID(), ChatRooms) }
            .orderBy(ChatMessages.timestamp to SortOrder.ASC).map { it.toDomainModel() }
            .toList()
    }

    /**
     * Gets the count of messages for a given chat room ID.
     *
     * @param id The ID of the chat room.
     * @return The number of messages in the chat room.
     */
    fun getMessagesCountFor(id: ChatRoomId): Int = DatabaseHandler.dbQuery {
        ChatMessages.selectAll().where { ChatMessages.chatRoom eq id.toUUID() }.count().toInt()
    }

    /**
     * Checks if a chat room is an assignment.
     *
     * @param chatRoomId The ID of the chat room
     * @return True if the chat room is an assignment, false otherwise
     */
    fun isChatroomAssignment(chatRoomId: ChatRoomId): Boolean = DatabaseHandler.dbQuery {
        ChatRoomEntity.findById(chatRoomId.toUUID())
            ?.assignment ?: throw IllegalArgumentException("Chat room with ID ${chatRoomId.string} not found")
    }

    /**
     * Gets chat rooms for a user
     *
     * @param userId The ID of the user
     * @return List of chat room entities
     * @throws IllegalArgumentException if the user does not exist
     */
    fun getChatRoomsForUser(userId: UserId): List<ChatRoomId> = DatabaseHandler.dbQuery {
        val user = UserEntity.findById(userId.toUUID())
            ?: throw IllegalArgumentException("User with ID ${userId.string} not found")
        ChatroomParticipants.select(ChatroomParticipants.chatRoom).where {
            ChatroomParticipants.user eq user.id
        }.map { it[ChatroomParticipants.chatRoom].UID() }.toList()
    }

    /**
     * Gets the prompt for a chat room
     *
     * @param chatRoomId The ID of the chat room
     * @return The prompt string for the chat room
     * @throws IllegalArgumentException if the chat room does not exist
     */
    fun getPromptForChatRoom(chatRoomId: ChatRoomId): String = DatabaseHandler.dbQuery {
        val chatRoom = ChatRoomEntity.findById(chatRoomId.toUUID())
            ?: throw IllegalArgumentException("Chat room with ID ${chatRoomId.string} not found")
        chatRoom.prompt
    }

    /**
     * Sets the prompt for a chat room
     *
     * @param chatRoomId The ID of the chat room
     * @param prompt The new prompt string for the chat room
     * @throws IllegalArgumentException if the chat room does not exist
     */
    fun setPromptForChatRoom(chatRoomId: ChatRoomId, prompt: String): Unit = DatabaseHandler.dbQuery {
        val chatRoom = ChatRoomEntity.findById(chatRoomId.toUUID())
            ?: throw IllegalArgumentException("Chat room with ID ${chatRoomId.string} not found")
        chatRoom.prompt = prompt
    }

    /**
     * Search the chat rooms based on various criteria.
     *
     * @param queryId Optional query ID to filter chat rooms by ID substring WARNING : ONLY PERFECT MATCH IS SUPPORTED
     * @param queryPrompt Optional query prompt to filter chat rooms by prompt substring
     * @param userIds List of user IDs to filter chat rooms. Returns all chat rooms if the list is empty.
     * @param startTime Start time of the range (inclusive)
     * @param endTime End time of the range (exclusive)
     * @return List of chat room IDs that match the criteria
     */
    // Should defintly be a builder this is real ugly
    fun search(
        queryId: String,
        queryPrompt: String,
        userIds: List<UserId>,
        startTime: Long,
        endTime: Long,
    ): List<ChatRoomId> = DatabaseHandler.dbQuery {

        // Get chat rooms that have participants in the specified user IDs and within the time range
        (ChatRooms innerJoin ChatroomParticipants).select(ChatRooms.id)
            .where {
                    var baseCondition = ((ChatRooms.startTime greaterEq startTime) and
                            (ChatRooms.endTime lessEq endTime))
                if (userIds.isNotEmpty()) {
                    baseCondition = baseCondition and (ChatroomParticipants.user inList userIds.map { it.toUUID() })
                }
                if (queryId.isNotEmpty()) {
                    // Only perfect match is supported for now !!!
                    baseCondition = baseCondition and (ChatRooms.id eq UID(queryId).toUUID()) // Assuming queryId is a valid UUID string
                }
                if (queryPrompt.isNotEmpty()) {
                    baseCondition = baseCondition and (ChatRooms.prompt like "%$queryPrompt%")}
                baseCondition
            }
            .orderBy(ChatRooms.startTime to SortOrder.DESC)
            .map { it[ChatRooms.id].UID() }
            .distinct()
            .toList()
    }


}