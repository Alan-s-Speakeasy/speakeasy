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
    var users: Map<UserId, String>, //UserId --> UserAlias
    val startTime: Long = System.currentTimeMillis(),
    var prompt: String = "",
    private val messages: MutableList<ChatMessage> = mutableListOf(),
    private val reactions: HashMap<Int, ChatMessageReaction> = hashMapOf(),
    val assessedBy: MutableList<Assessor> = mutableListOf(),
    var isDevelopment: Boolean = false,
    var isEvaluation: Boolean = false,
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

    val nextMessageOrdinal: Int
        get() = this.lock.read {
            messages.size
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
        this.messages.filter { it.time >= since && it.recipients.contains(currentUser)}
    }

//    fun getMessagesSince(since: Long, userId: UserId): List<ChatMessage> {
//        val userRole = UserManager.getUserRoleByUserID(userId)
//        return if (userRole == UserRole.BOT) {
//            this.lock.read {
//                this.messages.filter { it.time >= since && it.recipients.contains(UserManager.getUsernameFromId(userId))}
//            }
//        } else {
//            this.lock.read {
//                this.messages.filter { it.time >= since }
//            }
//        }
//    }

    open fun addMessage(message: ChatMessage): Unit = this.lock.write {
        require(this.active) { "Chatroom ${this.uid.string} is not active" }
        this.messages.add(message)
        return@write //actively return nothing
    }

    open fun addReaction(reaction: ChatMessageReaction): Unit = this.lock.write {
        require(this.active) { "Chatroom ${this.uid.string} is not active" }
        require(reaction.messageOrdinal < this.messages.size) { "Reaction ordinal out of bounds" }
        this.reactions[reaction.messageOrdinal] = reaction
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
