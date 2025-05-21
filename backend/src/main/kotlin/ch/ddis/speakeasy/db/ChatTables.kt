package ch.ddis.speakeasy.db

import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.UUIDTable

object ChatRooms : UUIDTable() {
    val assignment = bool("assignment").default(false)
    val formId = reference("form_id", FeedbackForms)
    val startTime = long("start_time")
    val prompt = text("prompt").default("")
    val testerBotAlias = varchar("tester_bot_alias", 255).nullable()
    val markAsNoFeedback = bool("mark_as_no_feedback").default(false)
}

// TODO : Once Kotlin is updated, this should be a CompositeIdTable so
// We maintain normalization !
object ChatroomParticipants : CompositeIdTable() {
    val chatRoom = reference("chatroom", ChatRooms)
    val user = reference("user", Users)
    val alias = varchar("alias", 255).nullable()
    override val primaryKey = PrimaryKey(chatRoom, user) // Composite primary key
}

object ChatMessages : UUIDTable() {
    val chatRoom = reference("chatroom", ChatRooms)
    val sender = reference("sender", Users)
    val content = text("content")
    val timestamp = long("timestamp")
    val ordinal = integer("ordinal")
}

