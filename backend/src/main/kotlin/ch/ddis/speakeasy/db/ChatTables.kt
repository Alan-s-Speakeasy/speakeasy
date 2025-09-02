package ch.ddis.speakeasy.db

import ch.ddis.speakeasy.chat.ChatMessageReactionType
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table

object ChatRooms : UUIDTable() {
    val assignment = bool("assignment").default(false)
    val formId = reference("form_id", FeedbackForms).nullable() // Can be null if the room is not meant to be assessed
    val startTime = long("start_time")
    val endTime = long("end_time").nullable()
    val prompt = text("prompt").default("")
    val testerBotAlias = varchar("tester_bot_alias", 255).nullable()
    val markAsNoFeedback = bool("mark_as_no_feedback").default(false)
}

object ChatroomParticipants : CompositeIdTable() {
    val chatRoom = reference("chatroom", ChatRooms)
    val user = reference("user", Users)
    val alias = varchar("alias", 255).nullable()
    override val primaryKey = PrimaryKey(chatRoom, user) // Composite primary key
}

object ChatMessages : CompositeIdTable() {
    val chatRoom = reference("chatroom", ChatRooms)
    val ordinal = integer("ordinal").entityId()
    val sender = reference("sender", Users)
    val content = text("content")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(chatRoom, ordinal)

    init {
        addIdColumn(chatRoom)
        addIdColumn(ordinal)
    }
}

object ChatReactions : CompositeIdTable() {
    val chatRoom = reference("chatroom", ChatRooms)
    val ordinal = integer("ordinal").entityId()
    val reaction = enumeration<ChatMessageReactionType>("reaction")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(chatRoom, ordinal, reaction) // Composite primary key

    init {
        addIdColumn(chatRoom)
        addIdColumn(ordinal)
    }
}

object ChatMessageRecipients : Table() {
    val chatRoom = reference("chat_room_id", ChatRooms)
    val messageOrdinal = reference("message_ordinal", ChatMessages.ordinal)
    val recipientUser = reference("recipient_user_id", Users)

    override val primaryKey = PrimaryKey(chatRoom, messageOrdinal, recipientUser)
}

