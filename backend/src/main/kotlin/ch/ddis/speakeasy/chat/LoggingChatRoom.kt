package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.UserSession
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.write
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.util.concurrent.locks.StampedLock

class LoggingChatRoom(
    uid: UID = UID(),
    sessions: List<UserSession>,
    startTime: Long = System.currentTimeMillis(),
    basePath: File,
    endTime: Long? = null
) : ChatRoom(uid, sessions, startTime) {

    init {
        if (!basePath.isDirectory) {
            basePath.mkdirs()
        }
        this.endTime = endTime
    }

    private val objectMapper = jacksonObjectMapper()
    private val writer = File(basePath, "${this.uid.string}.log").printWriter(Charsets.UTF_8)
    private val writerLock = StampedLock()

    init {
        writer.println(this.uid.string)
        writer.println(this.startTime)
        writer.println(this.endTime)
        writer.println(objectMapper.writeValueAsString(this.sessions))
        writer.println()
        writer.flush()
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
}