package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.db.UserId
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.read
import ch.ddis.speakeasy.util.write
import com.opencsv.ICSVWriter
import kotlinx.serialization.Serializable
import java.util.*
import java.util.concurrent.locks.StampedLock
import kotlin.collections.HashMap
import kotlin.math.max

typealias ChatRoomId = UID

open class ChatRoom(
    val assignment: Boolean = false,
    val formRef: String,
    val uid: ChatRoomId = UID(),
    val users: MutableMap<UserId, String>, //UserId --> UserAlias
    val startTime: Long = System.currentTimeMillis(),
    var prompt: String = "",
    private val messages: MutableList<ChatMessage> = mutableListOf(),
    private val reactions: HashMap<Int, ChatMessageReaction> = hashMapOf(),
    val assessedBy: MutableList<Assessor> = mutableListOf(),
    var testerBotAlias: String = "",
    var markAsNoFeedback: Boolean = false,
    var active : Boolean = true, // This is used to determine if the chat room is active or not.
    var remainingTime: Long = 0L, // This is used to determine the remaining time for the chat room.
) {
    internal var endTime: Long? = null
    val aliasToUserId = users.entries.associateBy({ it.value }) { it.key }

    private val lock: StampedLock = StampedLock()

    private val listeners = mutableListOf<ChatEventListener>()

    @Deprecated("Directly handled by the database now. See addMessageTo in ChatRoomRepository")
    val nextMessageOrdinal: Int
        get() = this.lock.read {
            messages.size
        }
    companion object {
        /**
         * Export a given chatRoom.
         *
         * @param chatRoom The ChatRoom object to be serialized.
         * @return A SerializedChatRoom object containing the serialized data of the given ChatRoom.
         */
        fun export(chatRoom: ChatRoom): ExportableChatRoom {
            val usernames = chatRoom.users.keys.map { UserManager.getUsernameFromId(it)!!}
            var exportedChatMessages = ChatMessage.toExportableMessages(chatRoom.messages)
            // Merge the messages with the reactions (as of now, they are stored in separate lists)
            exportedChatMessages = exportedChatMessages.map { message ->
                message.copy(
                    reaction = chatRoom.reactions[message.ordinal]?.type
                )
            }

            // NOTE : on earlier versions of Speakeasy, the authorUserId was not present in the ChatMessage object.
            // In that case, ExportableMessage will have an empty string as the author. This is purely for backward compatibility.
            val updatedExportedMessages = exportedChatMessages.map { message ->
                if (message.authorUserName != ExportableMessage.UNKNOWN_USERNAME) {
                    return@map message
                }
                val userNameFromAlias = chatRoom.aliasToUserId[message.authorAlias]?.let { userId ->
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
                chatRoom.assignment,
                chatRoom.formRef,
                usernames,
                chatRoom.startTime,
                chatRoom.prompt,
                updatedExportedMessages,
                chatRoom.endTime
            )
        }
    }
    fun addListener(listener: ChatEventListener, alert: Boolean = true) = this.lock.write {
        this.listeners.add(listener)
        if (alert) {
            listener.onNewRoom(this)
        }
    }

    fun getAllMessages(): List<ChatMessage> = this.lock.read {
        this.messages.toList()
    }

    @Deprecated("handled by the database now. Use getAllReactions() instead.",
        replaceWith = ReplaceWith("ChatRoomManager.getReactionsFor(chatRoom.uid, chatMessage.)"))
    fun getAllReactions(): List<ChatMessageReaction> = this.lock.read {
        this.reactions.values.toList()
    }

    /**
     * @return all [ChatMessage]s since a specified timestamp
     */
    fun getMessagesSince(since: Long, userId: UserId): List<ChatMessage> = this.lock.read {
        val currentUserRole = UserManager.getUserRoleByUserID(userId)
        if (currentUserRole == UserRole.ADMIN) { return this.messages.filter { it.time >= since } }

        val currentUser = this.users[userId]
        return this.messages.filter { it.time >= since && it.recipients.contains(currentUser) }
    }

    @Deprecated("Directly handled by the database now.", replaceWith =
    ReplaceWith("ChatRoomManager.addMessageTo(chatRoom, message)"))
    open fun addMessage(message: ChatMessage): Unit = this.lock.write {
        require(ChatRoomManager.isChatRoomActive(this.uid)) { "Chatroom ${this.uid.string} is not active" }
        this.messages.add(message)
        listeners.removeIf { listener -> //check state of listener, update if active, remove if not
            if (listener.isActive) {
                listener.onMessage(message, this)
                false
            } else {
                true
            }
        }
        return@write //actively return nothing
    }

    @Deprecated("Directly handled by the database now.",
        replaceWith = ReplaceWith("ChatRoomManager.addReactionTo(chatRoom, reaction)"))
    open fun addReaction(reaction: ChatMessageReaction): Unit = this.lock.write {
        require(ChatRoomManager.isChatRoomActive(this.uid)) { "Chatroom ${this.uid.string} is not active" }
        require(reaction.messageOrdinal < this.messages.size) { "Reaction ordinal out of bounds" }
        this.reactions[reaction.messageOrdinal] = reaction
        listeners.removeIf { listener -> //check state of listener, update if active, remove if not
            if (listener.isActive) {
                listener.onReaction(reaction, this)
                false
            } else {
                true
            }
        }
        return@write
    }

    open fun addAssessor(assessor: Assessor): Unit = this.lock.write {
        TODO("Not implemented")
        this.assessedBy.add(assessor)
        return@write
    }

    open fun addMarkAsNoFeedback(noFeedback: NoFeedback): Unit = this.lock.write {
        this.markAsNoFeedback = noFeedback.mark
        return@write
    }

    fun setEndTime(endTime: Long) = this.lock.write {
        this.endTime = endTime
    }

    /**
     * Deactivates the chat room by setting the end time to the current time.
     * This method should be called when the chat room is no longer active.
     */
    @Deprecated("Use setEndTime im chatroommanager instead to set a specific end time.")
    fun deactivate() = this.lock.write {
        if (ChatRoomManager.isChatRoomActive(this.uid)) {
            this.endTime = System.currentTimeMillis()
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
