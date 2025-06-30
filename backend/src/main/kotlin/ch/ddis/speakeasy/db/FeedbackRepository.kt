package ch.ddis.speakeasy.db

import ch.ddis.speakeasy.api.handlers.FeedbackResponse
import ch.ddis.speakeasy.api.handlers.FeedbackResponseOfChatroom
import ch.ddis.speakeasy.chat.ChatRoomId
import ch.ddis.speakeasy.feedback.FormId
import ch.ddis.speakeasy.feedback.FormManager
import ch.ddis.speakeasy.util.ChatRoomNotFoundException
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.UserNotFoundException
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.mapLazy
import java.util.*

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

    // Convert it back to domain model
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
     * Gets all feedback responses for a chat room
     *
     * @param roomId The ID of the chat room
     * @return List of feedback responses for the chat room
     * @throws IllegalArgumentException if the chat room does not exist
     */
    fun getFeedbackResponseForRoom(roomId: ChatRoomId): List<FeedbackResponse> = DatabaseHandler.dbQuery {
        val room = ChatRoomEntity.findById(roomId.toUUID())
            ?: throw ChatRoomNotFoundException(roomId)
        room.feedbackResponses.map { it.toFeedbackResponse() }
    }

    /**
     * Get all the feedback responses the user UserId did with the specific formId.
     */
    fun getFeedbackResponsesOfUser(userId: UserId?, formId: FormId, assignment: Boolean): Iterable<FeedbackResponseOfChatroom> =
        DatabaseHandler.dbQuery {
            if (userId != null) {
                val user = UserEntity.findById(userId.toUUID())
                    ?: throw UserNotFoundException(userId)
            }

            (FeedbackResponses innerJoin ChatRooms)
                .select(FeedbackResponses.columns)
                .where {
                    val baseCondition =
                            (FeedbackResponses.form eq formId.toUUID()) and
                            (ChatRooms.assignment eq assignment)
                    if (userId != null) {
                        baseCondition and (FeedbackResponses.author eq userId.toUUID())
                    } else {
                        baseCondition
                    }

                }
                .groupBy { resultRow ->
                    Triple(
                        resultRow[FeedbackResponses.room].UID(),
                        resultRow[FeedbackResponses.recipient].UID(),
                        resultRow[FeedbackResponses.author].UID()
                    )
                }
                .map { (key, rows) ->
                    FeedbackResponseOfChatroom(
                        author = key.third,
                        roomId = key.first,
                        recipient = key.second,
                        responses = rows.map { row ->
                            FeedbackResponse(
                                id = row[FeedbackResponses.requestId].toString(),
                                value = row[FeedbackResponses.value]
                            )
                        }
                    )
                }
        }

    /**
     * Gets all feedback responses for a specific form.
     */
    fun getAllFeedbackResponsesOfForm(formId: FormId, assignment: Boolean): Iterable<FeedbackResponseOfChatroom> = DatabaseHandler.dbQuery {
        // TODO : ensure formid is valid
        getFeedbackResponsesOfUser(null, formId, assignment)
    }

    fun findFormByName(formName: String): FormId? = DatabaseHandler.dbQuery {
        return@dbQuery FeedbackFormEntity.find { FeedbackForms.formName eq formName }.singleOrNull()?.id?.UID()
    }

    fun getFormNameById(formId: FormId): String? = DatabaseHandler.dbQuery {
        FeedbackFormEntity.findById(formId.toUUID())?.formName
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


