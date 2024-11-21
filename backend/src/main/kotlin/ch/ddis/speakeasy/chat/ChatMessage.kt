package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.SessionId
import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.util.UID
import kotlinx.serialization.Serializable

/**
 * A chat message that can be sent in a chat room. A simple dataclass.
 *
 * @param message The message that is sent.
 * @param authorUserId the UserID of the author.
 * @param authorAlias The alias of the author of the message. As of now, this parameter could be removed and replaced by authorUserId.
 * @param authorSessionId The session id of the author of the message.
 * @param ordinal The ordinal of the message in the chat room.
 * @param recipients The recipients of the message, but only their aliases.
 * @param isRead Whether the message has been read.
 * @param time The time the message was sent.
 */
data class ChatMessage(
    val message: String,
    // NOTE FOR BACKWARD COMPATIBILITY: authorUserId is not present in earlier versions of Speakeasy.
    // By default,
    val authorUserId: UserId = UserId.INVALID,
    val authorAlias: String,
    val authorSessionId: SessionId,
    val ordinal: Int,
    val recipients: Set<String> = mutableSetOf(),
    val isRead : Boolean = false,
    val time: Long = System.currentTimeMillis(),

    ) : ChatItemContainer() {

    companion object {
        /**
         * Converts a list of ChatMessages to a list of ExportableMessages.
         *
         * @param chatMessages The list of ChatMessages to be converted.
         */
        fun toExportableMessages(chatMessages: List<ChatMessage>): List<ExportableMessage> {
            return chatMessages.map {
                val username =
                // Two cases :
                // 1. FOr backward compatibility, if the authorUserId is invalid, the username is "unknown", as authorUserID
                // is not present in earlier versions of Speakeasy.
                    // 2. authorUSerID can come from another speakeasy instance, and is therefore not registered in the current instance.
                    if (UID.isInvalid(it.authorUserId)) ExportableMessage.UNKNOWN_USERNAME else UserManager.getUsernameFromId(it.authorUserId)
                        ?: ExportableMessage.NOT_REGISTED_USERNAME
                ExportableMessage(it.time, username, it.authorAlias, it.ordinal, it.message)
            }
        }
        fun toRestMessages(chatMessages: List<ChatMessage>):
            List<RestChatMessage> = chatMessages.map {
            RestChatMessage(it.time, it.authorAlias, it.ordinal, it.message, it.recipients, it.isRead)
        }

        fun toSseChatMessage(chatRoom: ChatRoom, chatMessage: ChatMessage):
            SseChatMessage = SseChatMessage(chatRoom.uid.toString(), chatMessage.time, chatMessage.authorAlias,
                    chatMessage.ordinal, chatMessage.message, chatMessage.recipients)
    }

}

@Serializable
data class ExportableMessage(val timeStamp: Long, val authorUserName: String, val authorAlias: String, val ordinal: Int, val message: String) {
    companion object {
        const val NOT_REGISTED_USERNAME = "not_registered"
        const val UNKNOWN_USERNAME = "unknown"
    }
}

data class RestChatMessage(val timeStamp: Long, val authorAlias: String, val ordinal: Int, val message: String, val recipients: Set<String>, val isRead: Boolean)
// TODO: what's the meaning of recipients and isRead? It seems isRead is useless, through
data class SseChatMessage(val roomId:String, val timeStamp: Long, val authorAlias: String, val ordinal: Int,
                          val message: String, val recipients: Set<String>)
