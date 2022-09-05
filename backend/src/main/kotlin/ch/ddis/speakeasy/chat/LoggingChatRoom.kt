package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.user.UserSession
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.write
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.concurrent.locks.StampedLock

class LoggingChatRoom(
    uid: UID = UID(),
    users: Map<UserId, String>,
    startTime: Long = System.currentTimeMillis(),
    basePath: File,
    endTime: Long? = null,
    prompt: String,
    messages: MutableList<ChatMessage> = mutableListOf(),
    reactions: MutableSet<ChatMessageReaction> = mutableSetOf(),
    assessedBy: MutableList<UserId> = mutableListOf()
) : ChatRoom(uid, users, startTime, prompt, messages, reactions, assessedBy) {

    init {
        if (!basePath.isDirectory) {
            basePath.mkdirs()
        }
        this.endTime = endTime
    }

    private val objectMapper = jacksonObjectMapper()
    private val file = File(basePath, "${this.uid.string}.log")
    private val writer = PrintWriter(
        FileWriter(
            File(basePath, "${this.uid.string}.log"),
            Charsets.UTF_8,
            true
        )
    )
    private val writerLock = StampedLock()

    init {
        if (!file.exists() || file.length() == 0L) {
            writer.println(this.uid.string)
            writer.println(this.startTime.toString())
            writer.println(this.endTime.toString())
            writer.println(this.prompt)
            writer.println(objectMapper.writeValueAsString(this.users))
            writer.println()
            writer.flush()
        }
    }

    override fun addMessage(message: ChatMessage) {
        val exception =
            this.writerLock.write {
                try {
                    super.addMessage(message)
                    writer.println(objectMapper.writeValueAsString(message))
                    writer.flush()
                    null
                } catch (e: IllegalArgumentException) {
                    e
                }
            }
        if (exception != null) {
            throw exception
        }
    }

    override fun addReaction(reaction: ChatMessageReaction) {
        val exception =
            this.writerLock.write {
                try {
                    super.addReaction(reaction)
                    writer.println(objectMapper.writeValueAsString(reaction))
                    writer.flush()
                    null
                } catch (e: IllegalArgumentException) {
                    e
                }
            }
        if (exception != null) {
            throw exception
        }
    }

    override fun addAssessor(session: UserSession) {
        val exception =
            this.writerLock.write {
                try {
                    super.addAssessor(session)
                    writer.println(objectMapper.writeValueAsString(session.user.id))
                    writer.flush()
                    null
                } catch (e: IllegalArgumentException) {
                    e
                }
            }
        if (exception != null) {
            throw exception
        }
    }
}