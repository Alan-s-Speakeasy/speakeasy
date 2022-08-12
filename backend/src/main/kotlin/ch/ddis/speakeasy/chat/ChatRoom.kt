package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.user.UserSession
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.read
import ch.ddis.speakeasy.util.write
import java.util.concurrent.locks.StampedLock
import kotlin.math.max

typealias ChatRoomId = UID

open class ChatRoom(
    val uid: ChatRoomId = UID(),
    val sessions: MutableSet<UserSession>,
    val userIds: MutableSet<UserId>,
    val startTime: Long = System.currentTimeMillis(),
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val reactions: MutableSet<ChatMessageReaction> = mutableSetOf(),
    val assessedBy: MutableList<UserId> = mutableListOf()
) {
    var prompt: String = ""
    internal var endTime: Long? = null

    val active: Boolean
        get() = startTime <= System.currentTimeMillis() && remainingTime > 0

    val remainingTime: Long
        get() = max((endTime ?: Long.MAX_VALUE) - System.currentTimeMillis(), 0)

    private val lock: StampedLock = StampedLock()

    val nextMessageOrdinal: Int
        get() = this.lock.read {
            messages.size
        }


    fun getAllMessages(): List<ChatMessage> = this.lock.read {
        this.messages.toList()
    }

    fun getAllReactions(): List<ChatMessageReaction> = this.lock.read {
        this.reactions.toList()
    }

    /**
     * @return all [ChatMessage]s since a specified timestamp
     */
    fun getMessagesSince(since: Long): List<ChatMessage> = this.lock.read {
        this.messages.filter { it.time >= since }
    }

    open fun joinOrLeave() {}

    open fun addMessage(message: ChatMessage): Unit = this.lock.write {
        require(this.active) { "Chatroom ${this.uid.string} is not active" }
        require(message.sessionId in this.sessions.map { it.sessionId }) { "User session '${message.sessionId.string}' does not belong to Chatroom ${this.uid.string}" }
        this.messages.add(message)
        return@write //actively return nothing
    }

    open fun addReaction(reaction: ChatMessageReaction): Unit = this.lock.write {
        require(this.active) { "Chatroom ${this.uid.string} is not active" }
        require(reaction.messageOrdinal < this.messages.size) { "Reaction ordinal out of bounds" }
        this.reactions.add(reaction)
        return@write
    }

    open fun addAssessor(session: UserSession): Unit = this.lock.write {
        require(this.active) { "Chatroom ${this.uid.string} is not active" }
        this.assessedBy.add(session.user.id)
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
