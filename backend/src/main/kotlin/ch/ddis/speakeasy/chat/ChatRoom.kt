package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.db.ChatRepository
import ch.ddis.speakeasy.db.UserId
import ch.ddis.speakeasy.feedback.FormId
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.util.SessionAliasGenerator
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.read
import ch.ddis.speakeasy.util.write
import com.opencsv.ICSVWriter
import kotlinx.serialization.Serializable
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.StampedLock

typealias ChatRoomId = UID

/**
 * An interface for any chatRoom.
 *
 * This interface defines the basic operations that can be performed on a chat room, such as adding messages, reactions, and users, as well as retrieving messages and reactions.
 */
interface ChatRoom {
    val uid: ChatRoomId
    var testerBotAlias: String
    val users: Map<UserId, String>
    val aliasToUserId: Map<String, UserId>
    val assignment: Boolean
    val formRef: String
    val startTime: Long
    val endTime: Long?
    var prompt: String
    fun getMessages(): List<ChatMessage>
    fun computeRemainingTime(): Long
    fun getMessagesSince(since: Long, userId: UserId): List<ChatMessage>
    fun addMessage(message: ChatMessage)
    fun addReaction(reaction: ChatMessageReaction)
    fun getReactionsForMessage(messageOrdinal: Int): List<ChatMessageReactionType>
    fun getReactions(): List<ChatMessageReaction>
    fun addUser(newUserId: UserId)
    fun getFeedbackForm(): FormId?
    fun markAsNoFeedback()
    fun isMarkedAsNoFeedback(): Boolean
    fun addAssessor(assessor: Assessor)
    fun isAssessedBy(userId: UserId): Boolean
    fun setEndTime(endTime: Long)
    fun deactivate()
    fun isActive(): Boolean
    fun export(): ExportableChatRoom
}

/**
 * Represents a chat room where users can exchange messages and reactions. Is not intended to
 *
 * @param uid Unique identifier for this chat room
 */
internal class DatabaseChatRoom(
    override val uid: ChatRoomId = UID(),
    override var testerBotAlias: String = "", // No idea what this is for
) : ChatRoom {

    // A map of user IDs to their aliases in this chat room.
    override val users: Map<UserId, String>
        get() = ChatRepository.getParticipantAliases(this.uid)

    // A map of user aliases to their user IDs in this chat room.
    override val aliasToUserId: Map<String, UserId>
        get() = this.users.entries.associateBy({ it.value }) { it.key }

    override val assignment: Boolean
        get() = ChatRepository.isChatroomAssignment(this.uid)

    // Getters with database access
    override val formRef: String
        get() = "TODO"

    override val startTime: Long
        get() = ChatRepository.getTimeBoundsForChatRoom(this.uid).first

    override val endTime: Long?
        get() = ChatRepository.getTimeBoundsForChatRoom(this.uid).second
    override var prompt: String
        get() = ChatRepository.getPromptForChatRoom(this.uid)
        set(value) {
            ChatRepository.setPromptForChatRoom(this.uid, value)
        }

    override fun getMessages(): List<ChatMessage> {
        return ChatRepository.getMessagesFor(this.uid)
    }

    /**
     * Export a given chatRoom.
     *
     * @return A SerializedChatRoom object containing the serialized data of the given ChatRoom.
     */
    override fun export(): ExportableChatRoom {
        val usernames = this.users.keys.map { UserManager.getUsernameFromId(it)!! }
        val exportedChatMessages = ChatMessage.toExportableMessages(this.getMessages())

        // NOTE : on earlier versions of Speakeasy, the authorUserId was not present in the ChatMessage object.
        // In that case, ExportableMessage will have an empty string as the author. This is purely for backward compatibility.
        val updatedExportedMessages = exportedChatMessages.map { message ->
            if (message.authorUserName != ExportableMessage.UNKNOWN_USERNAME) {
                return@map message
            }
            val userNameFromAlias = this.aliasToUserId[message.authorAlias]?.let { userId ->
                UserManager.getUsernameFromId(userId)
            }
            if (userNameFromAlias != null) {
                // Found the username !
                message.copy(authorUserName = userNameFromAlias)
            } else {
                message.copy(authorUserName = ExportableMessage.NOT_REGISTED_USERNAME)
            }
        }
        return ExportableChatRoom(
            this.assignment,
            this.formRef,
            usernames,
            this.startTime,
            this.prompt,
            updatedExportedMessages,
            this.endTime,
        )
    }

    /**
     * Computes the remaining time for this chat room session.
     *
     * @return The remaining time in milliseconds, or Long.MAX_VALUE if there's no end time set
     */
    override fun computeRemainingTime(): Long {
        val (_, endTime) = ChatRepository.getTimeBoundsForChatRoom(this.uid)
        return if (endTime != null) {
            endTime - System.currentTimeMillis()
        } else {
            Long.MAX_VALUE
        }
    }

    /**
     * @return all [ChatMessage]s since a specified timestamp
     *
     * @userId The recipients. Seems to be useless now.
     */
    override fun getMessagesSince(since: Long, userId: UserId): List<ChatMessage> {
        return ChatRepository.getMessagesFor(this.uid, since)
    }

    /**
     * Adds a new message to the chat room and notifies all active listeners.
     *
     * @param message The chat message to be added.
     * @throws IllegalArgumentException if the chat room is not active/not found or the message is invalid.
     */
    override fun addMessage(message: ChatMessage) {
        require(this.isActive()) { "Chatroom ${this.uid.string} is not active" }
        ChatRepository.addMessageTo(this.uid, message)
    }

    /**
     * Adds a reaction to a specific message in the chat room and notifies all active listeners.
     *
     * @param reaction The reaction to be added.
     * @throws IllegalArgumentException if the chat room is not active/not found or the reaction is invalid.
     */
    override fun addReaction(reaction: ChatMessageReaction) {
        require(this.isActive()) { "Chatroom ${this.uid.string} is not active" }
        ChatRepository.addReactionToMessage(this.uid, reaction.messageOrdinal, reaction.type)
    }

    /**
     * Gets the reactions for a specific message in a chat room.
     *
     * @param messageOrdinal The ordinal of the message for which reactions are requested.
     * @throws IllegalArgumentException if the chat room ID is not found.x
     */
    override fun getReactionsForMessage(messageOrdinal: Int): List<ChatMessageReactionType> {
        if (messageOrdinal >= ChatRepository.getMessagesCountFor(this.uid)) {
            throw IllegalArgumentException("Message ordinal $messageOrdinal is out of bounds for chat room ${this.uid}")
        }
        return ChatRepository.getReactionsForMessage(this.uid, messageOrdinal)
    }

    /**
     * Gets all reactions for the chat room.
     *
     * @return A list of ChatMessageReaction objects containing the ordinal and type of each reaction.
     * @throws IllegalArgumentException if the chat room ID is not found.
     */
    override fun getReactions(): List<ChatMessageReaction> {
        // Returns a list for each ordinal a ChatMessageReaction with the type and ordinal
        return (0..ChatRepository.getMessagesCountFor(this.uid)).map { ordinal ->
            ordinal to ChatRepository.getReactionsForMessage(this.uid, ordinal)
        }.filter { it.second.isNotEmpty() }.map { ChatMessageReaction(it.first, it.second.last()) }
        // NOTE : Only unique reaction is supported. The latest is returned
    }

    /**
     * Adds a user to the chat room while also generating a random alias for them.
     *
     * @throws IllegalArgumentException if the user is already in the chat room or if the user ID is invalid.
     */
    override fun addUser(newUserId: UserId) {
        val newUser = newUserId to SessionAliasGenerator.getRandomName()
        ChatRepository.addUserTo(this.uid, newUser.first, newUser.second)
    }

    /**
     * Gets the feedback form associated with this chat room.
     *
     * @return The FormId of the feedback form, or null if no form is associated.
     * @throws IllegalArgumentException if the chat room ID is not found.
     */
    // Could also be a property
    override fun getFeedbackForm(): FormId? {
        return ChatRepository.getFormForChatRoom(this.uid)
    }

    /**
     * Marks the chat room as NOT requiring feedback. Idempotent (no effect if already marked).
     *
     * @throws IllegalArgumentException if the chat room ID is not found.
     */
    override fun markAsNoFeedback() {
        ChatRepository.changeFeedbackStatusFor(this.uid, false)
    }

    /**
     * Self-explanatory.
     *
     * @throws IllegalArgumentException if the chat room ID is not found.
     */
    override fun isMarkedAsNoFeedback(): Boolean {
        return !ChatRepository.isFeedbackWantedForRoom(this.uid)
    }

    override fun addAssessor(assessor: Assessor) {
        TODO("Not implemented")
    }

    /**
     * Checks if this chat room has been assessed by the specified user.
     *
     * @param userId The ID of the user to check for assessment
     * @return true if the chat room has feedback associated with the given user as author, false otherwise
     */
    override fun isAssessedBy(userId: UserId): Boolean {
        TODO()
    }


    override fun setEndTime(endTime: Long) =
        ChatRepository.setEndTimeToChatRoom(this.uid, endTime)


    /**
     * Closes the chat room, preventing any further messages or reactions from being added.
     */
    override fun deactivate() =
        setEndTime(System.currentTimeMillis())

    /**
     * Determines whether the chat room is currently active.
     * A chat room is considered active if its start time has passed and its end time has not yet been reached.
     *
     * @return `true` if the chat room is active, `false` otherwise.
     */
    override fun isActive(): Boolean {
        val (startTime, endTime) = ChatRepository.getTimeBoundsForChatRoom(this.uid)
        val now = System.currentTimeMillis()
        return now >= startTime && (endTime == null || now < endTime)
    }
}

/**
 * A decorator for a [ChatRoom] that adds support for listeners.
 *
 * This class wraps an existing [ChatRoom] instance and provides functionality for adding, removing, and notifying
 * [ChatEventListener]s of events such as new messages and reactions. It follows the Decorator pattern to separate
 * the core chat room logic from the event notification mechanism.
 *
 * @property decorated The underlying [ChatRoom] instance that this class decorates.
 */
class ListenedChatRoom(private val decorated: ChatRoom) : ChatRoom by decorated {

    private val lock = StampedLock()

    // Using a CopyOnWriteArraySet to allow concurrent modifications while iterating over listeners.
    // Simpler and makes the whole thing thread-safe.
    private val listeners = CopyOnWriteArraySet<ChatEventListener>()

    /**
     * Returns a list of all listeners currently registered to this chat room.
     *
     * @return A list of [ChatEventListener]s.
     */
    fun getListeners(): List<ChatEventListener> =
        listeners.toList()

    /**
     * Adds a listener to this chat room.
     *
     * @param listener The listener to add.
     * @param alert If true, the listener will be immediately notified of the new room.
     */
    fun addListener(listener: ChatEventListener, alert: Boolean = true) : Unit  {
        this.listeners.add(listener)
        if (alert) {
            listener.onNewRoom(this)
        }
    }

    /**
     * Adds a collection of listeners to this chat room.
     */
    fun addListeners(listeners: Collection<ChatEventListener>, alert: Boolean = true): Unit =
        listeners.forEach {
            addListener(it, alert)
        }

    /**
     * Removes a listener from this chat room.
     *
     * @param listener The listener to remove.
     */
    fun removeListener(listener: ChatEventListener)  {
        listeners.remove(listener)
    }

    override fun addMessage(message: ChatMessage) {
        decorated.addMessage(message)
        listeners.removeIf { listener ->
            if (listener.isActive) {
                listener.onMessage(message, this)
                false
            } else {
                true
            }
        }
    }

    override fun addReaction(reaction: ChatMessageReaction) {
        decorated.addReaction(reaction)
        listeners.removeIf { listener ->
            if (listener.isActive) {
                listener.onReaction(reaction, this)
                false
            } else {
                true
            }
        }
    }
}

/**
 * A serialized data class version of chatroom.
 *
 * Used to export a chatroom into a format supposedly ready to be exported to JSON or something else.
 */
@Serializable
data class ExportableChatRoom(
    val assignment: Boolean,
    val formRef: String,
    val usernames: List<String>,
    val startTime: Long,
    val prompt: String,
    val messages: List<ExportableMessage>,
    val endTime: Long?,
) {

    companion object {
        private val dateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

        /**
         * Writes a list of ExportableChatRoom objects to an already opened writer object, used for exporting chatrooms.
         *
         * @param write The ICSVWriter object to write to.
         * @param chatRooms The list of ExportableChatRoom objects to write to the CSV file.
         *
         * @return Unit
         */
        fun writeToCSV(writer: ICSVWriter, chatRoom: ExportableChatRoom) {
            writer.writeNext(
                arrayOf(
                    "Timestamp",
                    "AuthorUserName",
                    "AuthorAlias",
                    "Message",
                    "Reaction"
                )
            )
            chatRoom.messages.forEach { message ->
                writer.writeNext(
                    arrayOf(
                        dateFormatter.format(Date(message.timeStamp)),
                        message.authorUserName,
                        message.authorAlias,
                        message.message,
                        message.reaction?.name ?: ""
                    )
                )
            }
        }
    }
}
