package ch.ddis.speakeasy.api.sse

import ch.ddis.speakeasy.api.AccessManagedRestHandler
import ch.ddis.speakeasy.api.RestApiRole
import ch.ddis.speakeasy.api.RestHandler
import ch.ddis.speakeasy.chat.SseChatEventListener
import ch.ddis.speakeasy.user.UserId
import io.javalin.http.sse.SseClient
import io.javalin.security.RouteRole
import java.util.function.Consumer

object SseRoomHandler: Consumer<SseClient> , RestHandler, AccessManagedRestHandler {
    override val route = "sse"
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.HUMAN)  // not accept bots
    private val sseChatService: SseChatService = SseChatService()

    override fun accept(client: SseClient) { // on open ...
        client.keepAlive()
        sseChatService.createWorker(client)
    }

    fun getChatListeners(userIds: List<UserId>) : List<SseChatEventListener> {
        return sseChatService.getWorkersByUserIds(userIds)
    }
}


