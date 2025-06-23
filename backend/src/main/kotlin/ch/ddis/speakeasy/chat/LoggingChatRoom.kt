package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.db.UserId
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.write
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.concurrent.locks.StampedLock

@Deprecated("Should be merged with Chatroom")
class LoggingChatRoom(
    assignment: Boolean = false,
    formRef: String,
    uid: UID = UID(),
    users: MutableMap<UserId, String>,
    startTime: Long = System.currentTimeMillis(),
    basePath: File,
    endTime: Long? = null,
    prompt: String,
    messages: MutableList<ChatMessage> = mutableListOf(),
    reactions: HashMap<Int, ChatMessageReaction> = hashMapOf(),
    assessedBy: MutableList<Assessor> = mutableListOf(),
    testerBotAlias: String,
    markAsNoFeedback: Boolean = false,
) : ChatRoom(assignment, formRef, uid, users, startTime, prompt, messages, reactions, assessedBy, testerBotAlias, markAsNoFeedback) {

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
            writer.println(this.assignment)
            writer.println(this.formRef)
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
    }

    override fun addReaction(reaction: ChatMessageReaction) {
    }

    override fun addAssessor(assessor: Assessor) {
        val exception =
            this.writerLock.write {
                try {
                    super.addAssessor(assessor)
                    writer.println(objectMapper.writeValueAsString(assessor))
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