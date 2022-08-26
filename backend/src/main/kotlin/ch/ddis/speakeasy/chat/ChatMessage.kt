package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.SessionId

data class ChatMessage(val message: String, val sessionId: SessionId, val ordinal: Int, val time: Long = System.currentTimeMillis()) {

    companion object {
        private fun toRestMessage(chatMessage: ChatMessage): RestChatMessage? {
            return RestChatMessage(chatMessage.time, chatMessage.sessionId.string, chatMessage.ordinal, chatMessage.message)
        }

        fun toRestMessages(chatMessages: List<ChatMessage>):
            List<RestChatMessage> = chatMessages.mapNotNull { toRestMessage(it) }

    }

}

data class RestChatMessage(val timeStamp: Long, val session: String, val ordinal: Int, val message: String)