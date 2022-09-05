package ch.ddis.speakeasy.chat

data class ChatMessage(val message: String, val authorAlias: String, val ordinal: Int, val time: Long = System.currentTimeMillis()) {

    companion object {
        fun toRestMessages(chatMessages: List<ChatMessage>):
            List<RestChatMessage> = chatMessages.map {
            RestChatMessage(it.time, it.authorAlias, it.ordinal, it.message)
        }
    }

}

data class RestChatMessage(val timeStamp: Long, val authorAlias: String, val ordinal: Int, val message: String)