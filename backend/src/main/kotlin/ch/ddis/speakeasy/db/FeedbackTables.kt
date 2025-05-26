package ch.ddis.speakeasy.db

import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Represents the feedback forms in the database.
 * Maps form UIDs to their file paths and form names.
 */
object FeedbackForms : UUIDTable() {
    val formName = varchar("form_name", 100).uniqueIndex()
    val fileName = varchar("file_name", 255)
}

/**
 * Represents a complete feedback submission event
 *
 * Is this even useful ? TODO Remove once rooms are databased
 */
@Deprecated("")
object FeedbackSubmissions : IntIdTable() {
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val author = reference("author", Users)
    val room = varchar("chatroom", 64).nullable()
    val form = reference("form", FeedbackForms)
}

/**
 * Represents individual question responses within a submission
 */
object FeedbackResponses : CompositeIdTable() {
    val submission = reference("submission", FeedbackSubmissions).nullable()
    val form = reference("form", FeedbackForms)
    val room = reference("room", ChatRooms)
    val author = reference("author", Users)
    val recipient = reference("recipient", Users)
    // The question id
    val requestId = integer("request_id").entityId()
    val value = text("request_answer")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    init {
        // The primary key is {room, form, author, requestId}
        addIdColumn(room)
        addIdColumn(form)
        addIdColumn(author)
    }
}
