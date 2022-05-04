package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.user.SessionId
import ch.ddis.speakeasy.user.UserSession

data class ChatMessage(val message: String, val sessionId: SessionId, val ordinal: Int, val time: Long = System.currentTimeMillis()) {

    companion object {
        fun toRestMessage(chatMessage: ChatMessage, sessions: Collection<UserSession>, sessionId: SessionId): RestChatMessage? {
            val session = sessions.find { it.sessionId == chatMessage.sessionId } ?: return null
            val myMessage = AccessManager.doSessionsBelongToSameUser(session.sessionId, sessionId)
            return RestChatMessage(chatMessage.time, session.sessionId.string, myMessage, chatMessage.ordinal, chatMessage.message)
        }

        fun toRestMessages(chatMessages: List<ChatMessage>, sessions: Collection<UserSession>, sessionId: SessionId):
            List<RestChatMessage> = chatMessages.mapNotNull { toRestMessage(it, sessions, sessionId) }

    }

}

data class RestChatMessage(val timeStamp: Long, val session: String, val myMessage: Boolean, val ordinal: Int, val message: String)