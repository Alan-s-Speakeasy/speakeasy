package ch.ddis.speakeasy.db

import ch.ddis.speakeasy.chat.ChatMessage
import ch.ddis.speakeasy.chat.ChatRoom
import ch.ddis.speakeasy.chat.ChatMessage as DomainChatMessage
import ch.ddis.speakeasy.chat.ChatRoom as DomainChatRoom
import ch.ddis.speakeasy.chat.ChatRoomId
import ch.ddis.speakeasy.user.SessionId
import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.util.UID
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

/**
 * Entity class for ChatRoom table
 */
class ChatRoomEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ChatRoomEntity>(ChatRooms)

    var assignment by ChatRooms.assignment
    var formId by ChatRooms.formId
    var startTime by ChatRooms.startTime
    var prompt by ChatRooms.prompt
    val messages by ChatMessageEntity referrersOn ChatMessages.chatRoom
    var participants by UserEntity via ChatroomParticipants
    var testerBotAlias by ChatRooms.testerBotAlias
    var markAsNoFeedback by ChatRooms.markAsNoFeedback

    /*
    * Convert the ChatRoomEntity to a regular ChatRoom object.
     */
    // NOTE : See https://github.com/JetBrains/Exposed/issues/656#issuecomment-542113164
    // Why we need to wrap the whole thing with dbQuery. What annoys me is that
    // There is no warning. Wasted 45 minutes figuring out why. Love Exposed !!!
    fun toDomainModel(): DomainChatRoom = DatabaseHandler.dbQuery {
        // See comment below for why we do that. Ho boy I don't like Exposed.
        val aliases =
            DatabaseHandler.dbQuery {
                ChatroomParticipants.selectAll().where(ChatroomParticipants.chatRoom eq id)
                    .associate {
                        (it[ChatroomParticipants.user].UID() to (it[ChatroomParticipants.alias] ?: ""))
                    }.toMutableMap()
            }
        DomainChatRoom(
            uid = id.UID(),
            assignment = assignment,
            formRef = formId.value.toString(),
            startTime = startTime,
            prompt = prompt,
            // Despite what the IDE could tell this.messages can be null!
            messages = this.messages.toList().map { it.toDomainModel() }.toMutableList(),
            users = aliases,
        )
    }
}

/**
 * Entity class for ChatMessage table
 */
class ChatMessageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ChatMessageEntity>(ChatMessages)

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
            ordinal = ordinal,
            // NOTE : See https://github.com/JetBrains/Exposed/issues/928
            // It is not possible to nicely get the alias from the many-to-many relationship database.
            authorAlias = ChatroomParticipants.select(ChatroomParticipants.alias).where {
                ChatroomParticipants.chatRoom eq chatroom.id
            }.firstOrNull()?.get(ChatroomParticipants.alias) ?: "",
            authorSessionId = SessionId.INVALID
        )
    }
}

/**
 * Repository for chat-related database operations
 */
object ChatRepository {
    private val objectMapper = jacksonObjectMapper()

    /**
     * Finds a chat room by ID
     *
     * @param id The ID of the chat room to find
     * @return The chat room entity if found, null otherwise
     */
    // TODO : This should return a DomainChatRoom object
    fun findChatRoomById(id: ChatRoomId): ChatRoom? = DatabaseHandler.dbQuery {
        ChatRoomEntity.findById(id.toUUID())?.toDomainModel()
    }

    /**
     * Creates a new chat room
     *
     * @param domainChatRoom The domain chat room object to create
     * @param participants List of participant user IDs
     * @return The created chat room
     */
    // TODO : This should return a DomainChatRoom object
    fun createChatRoom(domainChatRoom: DomainChatRoom, participants: List<UserId>): ChatRoom =
        DatabaseHandler.dbQuery {
            val chatRoom = ChatRoomEntity.new(domainChatRoom.uid.toUUID()) {
                assignment = domainChatRoom.assignment
                formId = EntityID(UUID.randomUUID(), FeedbackForms) // This is a placeholder TODO TODO TODO
                startTime = domainChatRoom.startTime
                prompt = domainChatRoom.prompt
                testerBotAlias = domainChatRoom.testerBotAlias
                markAsNoFeedback = domainChatRoom.markAsNoFeedback
                this.participants = UserEntity.find { Users.id inList participants.map { it.toUUID() } }
            }
            chatRoom.toDomainModel()
        }

    /**
     * Adds a message to a chat room
     *
     * @param chatRoomId The ID of the chat room
     * @param message The message to add
     * @return The created message
     */
    fun addMessageTo(chatRoomId: ChatRoomId, message: DomainChatMessage): ChatMessage = DatabaseHandler.dbQuery {
        val chatRoom = ChatRoomEntity.findById(chatRoomId.toUUID())
            ?: throw IllegalArgumentException("Chat room with ID ${chatRoomId.string} not found")

        ChatMessageEntity.new {
            this.chatroom = chatRoom
            this.sender = EntityID(message.authorUserId.toUUID(), Users)
            this.content = message.message
            this.timestamp = message.time
            this.ordinal = message.ordinal
        }.toDomainModel()
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

        // Add the user to participants and set the alias in the join table
        ChatroomParticipants.insert {
            it[ChatroomParticipants.chatRoom] = chatRoom.id
            it[ChatroomParticipants.user] = user.id
            it[ChatroomParticipants.alias] = alias
        }
    }

    /**
     * Changes the feedback status of a chat room
     *
     * @param feedbackWanted Whether the chat room should be marked as having no feedback
     * @param chatRoomId The ID of the chat room
     * @return True if the status was changed successfully, false otherwise
     */
    fun changeFeedbackStatus(feedbackWanted : Boolean, chatRoomId: ChatRoomId): Boolean = DatabaseHandler.dbQuery {
        val chatRoom = ChatRoomEntity.findById(chatRoomId.toUUID())
            ?: throw IllegalArgumentException("Chat room with ID ${chatRoomId.string} not found")
        chatRoom.markAsNoFeedback = !feedbackWanted
        true
    }

    /**
     * Gets all messages for a chat room
     *
     * @param chatRoomId The ID of the chat room
     * @return List of chat message entities
     */
    fun getChatMessages(chatRoomId: ChatRoomId): List<DomainChatMessage> = DatabaseHandler.dbQuery {
        ChatMessageEntity.find { ChatMessages.chatRoom eq chatRoomId.toUUID() }
            .orderBy(ChatMessages.timestamp to SortOrder.ASC).map { it.toDomainModel() }
            .toList()
    }

    /**
     * Gets chat rooms for a user
     *
     * @param userId The ID of the user
     * @return List of chat room entities
     */
    fun getChatRoomsForUser(userId: UserId): List<ChatRoomEntity> = DatabaseHandler.dbQuery {
        UserEntity.findById(userId.toUUID())?.chatRooms?.toList() ?: emptyList()
    }
}