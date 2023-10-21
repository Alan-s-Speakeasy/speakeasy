package ch.ddis.speakeasy.api.sse

import ch.ddis.speakeasy.chat.SseChatEventListener
import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.util.UID
import io.javalin.http.sse.SseClient
import java.util.concurrent.ConcurrentHashMap

class SseChatService {

    private val workers =  ConcurrentHashMap<String, SseClientWorker>()

    fun createWorker(client: SseClient) {
        val worker = SseClientWorker(client, this) // TODO: handle exception
        workers[worker.workerId] = worker
    }

    fun removeWorker(worker: SseClientWorker) {
        workers.remove(worker.workerId, worker)
    }
    fun getWorkersByUserIds(userIds: List<UserId>) : List<SseClientWorker> {
        return workers.values.filter { userIds.contains(it.userId) }
    }

    fun close() {
        workers.values.forEach {
            it.close()
        }
    }

}