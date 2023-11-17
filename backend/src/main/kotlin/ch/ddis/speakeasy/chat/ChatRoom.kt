package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.read
import ch.ddis.speakeasy.util.write
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

    fun addListener(listener: ChatEventListener) = this.lock.write {
        this.listeners.add(listener)
        listener.onNewRoom(this)
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
        val currentUser = this.users[userId]
        // TODO: BUG - Admin cannot spectate chat room messages if we keep `it.recipients.contains(currentUser)`.
        //  I'm not sure if it is necessary here. I just removed it for now.
//        this.messages.filter { it.time >= since && it.recipients.contains(currentUser) }
        this.messages.filter { it.time >= since }
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
