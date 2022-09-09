package ch.ddis.speakeasy.chat

import com.fasterxml.jackson.annotation.JsonTypeInfo

enum class ChatMessageReactionType {
    THUMBS_UP,
    THUMBS_DOWN,
    STAR
}

// Annotation needed here to be able to parse the body in /api/room/:roomId/reaction
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property ="class", defaultImpl = ChatMessageReaction::class)
data class ChatMessageReaction(val messageOrdinal: Int, val type: ChatMessageReactionType) : ChatItemContainer() //TODO we could also explicitly store who reacted