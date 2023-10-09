package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.AccessManagedRestHandler
import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.api.RestApiRole
import ch.ddis.speakeasy.api.RestHandler
import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.getOrCreateSessionToken
import ch.ddis.speakeasy.util.sessionToken
import io.javalin.http.sse.SseClient
import io.javalin.security.RouteRole
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

object SseAllHandler: Consumer<SseClient> , RestHandler, AccessManagedRestHandler {
    override val route = "sse"
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.USER)  // TODO: not accept bots
    private val clients = ConcurrentLinkedQueue<SseClient>()
    private val scheduler = Executors.newScheduledThreadPool(1)

    private val listChatRoomsHandler = ListChatRoomsHandler()

    init {
        scheduler.scheduleAtFixedRate(::sendRooms, 0, 2, TimeUnit.SECONDS)
    }

    override fun accept(client: SseClient) { // on open ...
        client.keepAlive()
        client.onClose { this.clients.remove(client) }
        this.clients.add(client)
    }

    private fun sendRooms() {
        // todo: Should it be sent periodically or in response to an event trigger (when someone Posts /Deletes rooms)?
        this.clients.forEach {client ->
            // todo: extend session
            val data = listChatRoomsHandler.doGet(client.ctx()) // todo: handle exception
            client.sendEvent("SseRooms", data=data)
        }
    }

}
