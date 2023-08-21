package ch.ddis.speakeasy.chat

import ch.ddis.speakeasy.user.SessionId
import ch.ddis.speakeasy.user.UserRole

data class ChatMessage(
    val message: String,
    val authorAlias: String,
    val authorSessionId: SessionId,
    val ordinal: Int,
    var private : Boolean,
    val isRead : Boolean = false,
    val isDisplayed : Boolean = true,
    val time: Long = System.currentTimeMillis()

) : ChatItemContainer() {

    companion object {
        fun toRestMessages(chatMessages: List<ChatMessage>):
            List<RestChatMessage> = chatMessages.map {
            RestChatMessage(it.time, it.authorAlias, it.ordinal, it.message, it.private, it.isRead, it.isDisplayed)
        }
    }

}

data class RestChatMessage(val timeStamp: Long, val authorAlias: String, val ordinal: Int, val message: String, var private: Boolean, val isRead: Boolean, val isDisplayed: Boolean)