package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.read
import ch.ddis.speakeasy.util.write
import kotlinx.serialization.Serializable
import java.util.concurrent.locks.StampedLock
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
) {
    internal var endTime: Long? = null
    val aliasToUserId = users.entries.associateBy({ it.value }) { it.key }

    val active: Boolean
        get() = startTime <= System.currentTimeMillis() && remainingTime > 0

    val remainingTime: Long
        get() = max((endTime ?: Long.MAX_VALUE) - System.currentTimeMillis(), 0)

    private val lock: StampedLock = StampedLock()

    private val listeners = mutableListOf<ChatEventListener>()

    val nextMessageOrdinal: Int
        get() = this.lock.read {
            messages.size
        }
    companion object {
        /**
         * Serializes a given ChatRoom object into a SerializedChatRoom object.
         *
         * @param chatRoom The ChatRoom object to be serialized.
         * @return A SerializedChatRoom object containing the serialized data of the given ChatRoom.
         */
        fun exportSerialized(chatRoom: ChatRoom): SerializedChatRoom {
            return SerializedChatRoom(
                chatRoom.assignment,
                chatRoom.formRef,
                chatRoom.users.values.toList(),
                chatRoom.startTime,
                chatRoom.prompt,
                ChatMessage.toRestMessages(chatRoom.messages),
                chatRoom.endTime
            );
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

    open fun addMessage(message: ChatMessage): Unit = this.lock.write {
        require(this.active) { "Chatroom ${this.uid.string} is not active" }
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

    open fun addReaction(reaction: ChatMessageReaction): Unit = this.lock.write {
        require(this.active) { "Chatroom ${this.uid.string} is not active" }
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

    fun deactivate() = this.lock.write {
        if (active) {
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
data class SerializedChatRoom(
    val assignment: Boolean,
    val formRef: String,
    // Users aliases.
    val users: List<String>,
    val startTime: Long,
    val prompt: String,
    val messages: List<RestChatMessage>,
    val endTime: Long?,
)
