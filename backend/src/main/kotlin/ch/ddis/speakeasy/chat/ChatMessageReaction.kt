package ch.ddis.speakeasy.chat

enum class ChatMessageReactionType {
    THUMBS_UP,
    THUMBS_DOWN,
    STAR
}

data class ChatMessageReaction(val messageOrdinal: Int, val type: ChatMessageReactionType) //TODO we could also explicitly store who reacted