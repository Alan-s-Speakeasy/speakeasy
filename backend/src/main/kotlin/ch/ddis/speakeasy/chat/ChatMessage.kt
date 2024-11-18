package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.SessionId
import kotlinx.serialization.Serializable

data class ChatMessage(
    val message: String,
    val authorAlias: String,
    val authorSessionId: SessionId,
    val ordinal: Int,
    val recipients: Set<String> = mutableSetOf(),
    val isRead : Boolean = false,
    val time: Long = System.currentTimeMillis(),

    ) : ChatItemContainer() {

    companion object {
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
data class RestChatMessage(val timeStamp: Long, val authorAlias: String, val ordinal: Int, val message: String, val recipients: Set<String>, val isRead: Boolean)
// TODO: what's the meaning of recipients and isRead? It seems isRead is useless, through
data class SseChatMessage(val roomId:String, val timeStamp: Long, val authorAlias: String, val ordinal: Int,
                          val message: String, val recipients: Set<String>)
