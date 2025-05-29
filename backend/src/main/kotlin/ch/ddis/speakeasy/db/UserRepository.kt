package ch.ddis.speakeasy.db

import ch.ddis.speakeasy.api.handlers.FeedbackResponse
import ch.ddis.speakeasy.api.handlers.FeedbackResponseOfChatroom
import ch.ddis.speakeasy.feedback.FormId
import ch.ddis.speakeasy.util.UID
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*
import org.jetbrains.exposed.sql.and

typealias UserId = UID

fun EntityID<UUID>.string(): String = this.toString()
class UserEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserEntity>(Users)

    var name by Users.username
    var role by Users.role
    var password by Users.password
    val chatRooms by ChatRoomEntity referrersOn ChatroomParticipants
    val feedbackResponses by FeedbackResponseEntity referrersOn FeedbackResponses.author

    override fun equals(other: Any?): Boolean {
        // Probably just to avoid comparing passwords.
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserEntity

        if (id != other.id) return false
        if (name != other.name) return false
        if (role != other.role) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + role.hashCode()
        return result
    }

}


object UserRepository {
    /**
     * Get all the feedback responses the user UserId did with the specific formId.
     */
    fun getFeedbackResponsesOfUser(userId: UserId, formId: FormId, assignment: Boolean): FeedbackResponseOfChatroom =
        DatabaseHandler.dbQuery {
            UserEntity.findById(userId.toUUID())
                ?: throw IllegalArgumentException("User id ${userId.toUUID()} does not exist.")

            (FeedbackResponses innerJoin ChatRooms)
                .select(FeedbackResponses.columns)
                .where {
                    (FeedbackResponses.author eq userId.toUUID()) and
                            (FeedbackResponses.form eq formId.toUUID()) and
                            (ChatRooms.assignment eq assignment)
                }
                .groupBy { resultRow ->
                    Triple(
                        resultRow[FeedbackResponses.room].UID(),
                        resultRow[FeedbackResponses.recipient].UID(),
                        userId
                    )
                }
                .map { (key, rows) ->
                    FeedbackResponseOfChatroom(
                        author = key.third,
                        recipient = key.second,
                        room = key.first,
                        responses = rows.map { row ->
                            FeedbackResponse(
                                id = row[FeedbackResponses.requestId].toString(),
                                value = row[FeedbackResponses.value]
                            )
                        }
                    )
                }
            // Should be only one result per design of the primary key
        }.single()

    /**
     * Does what you think it does.
     */
    fun getUsernameFromId(userId: UserId): String? =
        DatabaseHandler.dbQuery {
            UserEntity.findById(userId.toUUID())?.name
        }
}
