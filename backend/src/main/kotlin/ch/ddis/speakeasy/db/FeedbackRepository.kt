package ch.ddis.speakeasy.db

import ch.ddis.speakeasy.api.ErrorStatus
import ch.ddis.speakeasy.api.handlers.FeedbackResponse
import ch.ddis.speakeasy.chat.ChatRoomId
import ch.ddis.speakeasy.db.FeedbackForms.formName
import ch.ddis.speakeasy.feedback.FormId
import ch.ddis.speakeasy.util.UID
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*
import kotlin.math.absoluteValue

/**
 * Entity class for FeedbackForm table
 */
class FeedbackFormEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FeedbackFormEntity>(FeedbackForms)

    var formName by FeedbackForms.formName
    var fileName by FeedbackForms.fileName
    val responses by FeedbackResponseEntity referrersOn FeedbackResponses.form
}

class FeedbackResponseEntity (id : EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<FeedbackResponseEntity>(FeedbackResponses)
    // Actually used FeedbackSubmission
    var author by UserEntity referencedOn FeedbackResponses.author
    var recipient by UserEntity referencedOn FeedbackResponses.recipient
    var form by FeedbackFormEntity referencedOn FeedbackResponses.form
    var value by FeedbackResponses.value
    var associatedRoom by ChatRoomEntity referencedOn FeedbackResponses.room
    // = Question ID
    var requestId by FeedbackResponses.requestId

    fun toFeedbackResponse(): FeedbackResponse = DatabaseHandler.dbQuery {
        FeedbackResponse(
            id = requestId.toString(),
            value = value,
        )
    }
}


/**
 * Repository for feedback-related database operations.
 * Also holds form entities.
 */
object FeedbackRepository {
    /**
     * Finds a feedback form by name
     *
     * @param formName The name of the form to find
     * @return The form entity if found, null otherwise
     */
    fun findFormByName(formName: String): FormId? = DatabaseHandler.dbQuery {
        FeedbackFormEntity.find { FeedbackForms.formName eq formName }.singleOrNull()?.id?.UID()
    }


    /**
     * Save a feedback response to the database.
     *
     * @throws IllegalArgumentException If the room was already assessed with the question specific question.
     */
    fun saveFeedbackResponse(authorId: UserId, recipientId : UserId, roomId: ChatRoomId, formId: FormId, feedbackResponse: FeedbackResponse) =
        DatabaseHandler.dbQuery {
            val key = CompositeID {
                it[FeedbackResponses.form] = formId.toUUID()
                it[FeedbackResponses.room] = roomId.toUUID()
                it[FeedbackResponses.author] = authorId.toUUID()
                it[FeedbackResponses.requestId] = feedbackResponse.id.toInt()
            }
            FeedbackResponseEntity.findById(key)?.let {throw IllegalArgumentException("Feedback response for this question already exists.")}

            FeedbackResponseEntity.new(key) {
                this.value = feedbackResponse.value
                this.recipient = UserEntity[recipientId.toUUID()]
            }
        }



    /**
     * Finds a feedback form by ID
     *
     * @param id The ID of the form to find
     * @return The form entity if found, null otherwise
     */
    fun findFormById(id: UUID): FeedbackFormEntity? = DatabaseHandler.dbQuery {
        FeedbackFormEntity.findById(id)
    }

    /**
     * Creates a new feedback form entry in the database
     *
     * @param formName The name of the form
     * @param fileName The file name where the form is stored
     * @return The created form entity
     */
    fun createForm(formName: String, fileName: String): FeedbackFormEntity = DatabaseHandler.dbQuery {
        FeedbackFormEntity.new {
            this.formName = formName
            this.fileName = fileName
        }
    }

}


