package ch.ddis.speakeasy.chat;

interface ChatEventListener {
    /**
     * Fired when a new chat room is created for the user/bot that has the ownership of this listener.
     */
    fun onNewRoom(chatRoom: ChatRoom)

    /**
     * Fired when a new message is sent in a chat room by the user/bot that has the ownership of this listener.
     */
    fun onMessage(chatMessage: ChatMessage, chatRoom: ChatRoom)

    /**
     * Fired when a reaction is added to a message in a chat room by the user/bot that has the ownership of this listener.
     */
    fun onReaction(chatMessageReaction: ChatMessageReaction, chatRoom: ChatRoom)

    val isActive: Boolean

}

