package ch.ddis.speakeasy.db

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
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
 */
object FeedbackSubmissions : UUIDTable() {
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val author = reference("user_id", Users)
    val room = varchar("room_id", 64) // Later: reference to Rooms
    val form = reference("form_id", FeedbackForms)

    init {
        // Each user submits feedback for a room+form combination once
        index(true, author, room, form)
    }
}

/**
 * Represents individual question responses within a submission
 */
object FeedbackAnswers : Table() {
    val submission = reference("submission_id", FeedbackSubmissions)
    val requestId = integer("request_id")
    val value = text("request_answer")
    override val primaryKey = PrimaryKey(submission, requestId)
}
