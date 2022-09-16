package ch.ddis.speakeasy.chat

import com.fasterxml.jackson.annotation.JsonTypeInfo

enum class ChatMessageReactionType {
    THUMBS_UP,
    THUMBS_DOWN,
    STAR
}

data class ChatMessageReactionContainer(val reaction: ChatMessageReaction) : ChatItemContainer()

data class ChatMessageReaction(val messageOrdinal: Int, val type: ChatMessageReactionType) //TODO we could also explicitly store who reacted