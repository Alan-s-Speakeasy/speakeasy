package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.SessionId

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
    }

}

data class RestChatMessage(val timeStamp: Long, val authorAlias: String, val ordinal: Int, val message: String, val recipients: Set<String>, val isRead: Boolean)