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

        fun toSseChatMessage(chatRoom: ChatRoom, chatMessage: ChatMessage): SseChatMessage =
            SseChatMessage(chatRoom.uid.toString(), chatRoom.remainingTime, chatMessage.time,
                chatMessage.authorAlias, chatMessage.ordinal, chatMessage.message)
    }

}

data class RestChatMessage(val timeStamp: Long, val authorAlias: String, val ordinal: Int, val message: String)
data class SseChatMessage(val roomId:String, val remainingTime: Long, val timeStamp: Long,
                          val authorAlias: String, val ordinal: Int, val message: String)