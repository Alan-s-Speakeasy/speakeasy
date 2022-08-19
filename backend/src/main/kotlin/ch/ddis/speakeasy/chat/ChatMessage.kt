package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.SessionId
import ch.ddis.speakeasy.user.UserSession

data class ChatMessage(val message: String, val sessionId: SessionId, val ordinal: Int, val time: Long = System.currentTimeMillis()) {

    companion object {
        private fun toRestMessage(chatMessage: ChatMessage, sessions: Collection<UserSession>): RestChatMessage? {
            val session = sessions.find { it.sessionId == chatMessage.sessionId } ?: return null
            return RestChatMessage(chatMessage.time, session.sessionId.string, chatMessage.ordinal, chatMessage.message)
        }

        fun toRestMessages(chatMessages: List<ChatMessage>, sessions: Collection<UserSession>):
            List<RestChatMessage> = chatMessages.mapNotNull { toRestMessage(it, sessions) }

    }

}

data class RestChatMessage(val timeStamp: Long, val session: String, val ordinal: Int, val message: String)