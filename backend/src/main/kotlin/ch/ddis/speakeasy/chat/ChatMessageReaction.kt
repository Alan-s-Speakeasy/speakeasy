package ch.ddis.speakeasy.chat

import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlinx.serialization.Serializable

enum class ChatMessageReactionType {
    THUMBS_UP,
    THUMBS_DOWN,
    STAR
}

data class ChatMessageReactionContainer(val reaction: ChatMessageReaction) : ChatItemContainer()

@Serializable
data class ChatMessageReaction(val messageOrdinal: Int, val type: ChatMessageReactionType) { //TODO we could also explicitly store who reacted
    companion object {
        fun toSseChatReaction(chatRoom: ChatRoom, reaction: ChatMessageReaction):
            SseChatReaction = SseChatReaction(chatRoom.uid.toString(), reaction.messageOrdinal, reaction.type)
    }
}

data class SseChatReaction(val roomId:String, val messageOrdinal: Int, val type: ChatMessageReactionType)