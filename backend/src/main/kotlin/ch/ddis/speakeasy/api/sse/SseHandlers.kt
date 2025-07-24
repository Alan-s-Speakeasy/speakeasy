package ch.ddis.speakeasy.api.sse

import ch.ddis.speakeasy.api.AccessManagedRestHandler
import ch.ddis.speakeasy.api.RestApiRole
import ch.ddis.speakeasy.api.RestHandler
import ch.ddis.speakeasy.chat.ChatEventListener
import ch.ddis.speakeasy.db.UserId
import io.javalin.http.sse.SseClient
import io.javalin.security.RouteRole
import java.util.function.Consumer

object SseRoomHandler: Consumer<SseClient> , RestHandler, AccessManagedRestHandler {
    override val route = "rooms"
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.HUMAN)  // not accept bots

    override fun accept(client: SseClient) {
        client.keepAlive()
        SseChatService.createWorker(client)
    }

    fun getChatListeners(userIds: List<UserId>) : List<ChatEventListener> {
        return SseChatService.getWorkersByUserIds(userIds)
    }
}


