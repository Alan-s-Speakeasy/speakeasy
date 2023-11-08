package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.SessionId

data class ChatMessage(
    val message: String,
    val authorAlias: String,
    val authorSessionId: SessionId,
    val ordinal: Int,
    val time: Long = System.currentTimeMillis()
) : ChatItemContainer() {

    companion object {
        fun toRestMessages(chatMessages: List<ChatMessage>):
            List<RestChatMessage> = chatMessages.map {
            RestChatMessage(it.time, it.authorAlias, it.ordinal, it.message)
        }

        fun toSseChatMessages(chatRoom: ChatRoom, chatMessages: List<ChatMessage>):
            List<SseChatMessage> = chatMessages.map {
                SseChatMessage(chatRoom.uid.toString(), it.time, it.authorAlias, it.ordinal, it.message)}
    }

}

data class RestChatMessage(val timeStamp: Long, val authorAlias: String, val ordinal: Int, val message: String)
data class SseChatMessage(val roomId:String, val timeStamp: Long,
                          val authorAlias: String, val ordinal: Int, val message: String)