package ch.ddis.speakeasy.api.sse

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.user.UserId
import io.javalin.http.sse.SseClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object SseChatService {

    private val workers =  ConcurrentHashMap<String, SseClientWorker>()
    private val updateSessionExecutor = Executors.newSingleThreadScheduledExecutor()
    init {
        // To avoid session expiration when using SSE (the frontend doesn't actively request to update the session)
        updateSessionExecutor.scheduleAtFixedRate(::updateSessionForActiveWorker, 1, 3, TimeUnit.MINUTES)
    }

    private fun updateSessionForActiveWorker() {
        workers.values.forEach {
            if (it.isActive) {
                AccessManager.updateLastAccess(it.sessionToken)
            }
        }
    }

    fun createWorker(client: SseClient){
        val worker = SseClientWorker(client) // TODO: handle exception ...
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