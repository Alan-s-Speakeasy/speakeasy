package ch.ddis.speakeasy.api.sse

import ch.ddis.speakeasy.api.AccessManagedRestHandler
import ch.ddis.speakeasy.api.RestApiRole
import ch.ddis.speakeasy.api.RestHandler
import ch.ddis.speakeasy.api.handlers.ListChatRoomsHandler
import ch.ddis.speakeasy.chat.SseChatEventListener
import ch.ddis.speakeasy.user.UserId
import io.javalin.http.sse.SseClient
import io.javalin.security.RouteRole
import java.util.function.Consumer

object SseRoomHandler: Consumer<SseClient> , RestHandler, AccessManagedRestHandler {
    override val route = "sse"
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.USER)  // TODO: not accept bots
    private val sseChatService: SseChatService = SseChatService()

    internal val listChatRoomsHandler = ListChatRoomsHandler()


    override fun accept(client: SseClient) { // on open ...
        client.keepAlive()
        sseChatService.createWorker(client)
    }

    fun getChatListeners(userIds: List<UserId>) : List<SseChatEventListener> {
        return sseChatService.getWorkersByUserIds(userIds)
    }






//    private fun sendRooms() {
//        clients.forEach { client ->
//            // todo: extend session
//            // TODO: trigger, only send SseRooms to relevant users

//            val data = listChatRoomsHandler.doGet(client.ctx()) // todo: handle exception
//            client.sendEvent("SseRooms", data=data)
//        }
//    }

//    fun closeClient(sessionToken: String?) {
//        println("Closed $sessionToken")
//        clients.find {it.ctx().sessionToken() == sessionToken}?.close()
//    }

}
