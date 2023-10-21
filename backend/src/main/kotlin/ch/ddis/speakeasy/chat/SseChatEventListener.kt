package ch.ddis.speakeasy.chat;

interface SseChatEventListener {
    fun onNewRoom(chatRoom: ChatRoom)
    fun onMessage(chatMessage: ChatMessage, chatRoom: ChatRoom)
    fun onReaction(chatMessageReaction: ChatMessageReaction, chatRoom: ChatRoom)

    val isActive: Boolean

}

