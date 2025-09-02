package ch.ddis.speakeasy.api

import io.javalin.http.Context
import org.slf4j.LoggerFactory

/**
 * Exception that can be thrown in a RestHandler to return a specific error status and message. Will be shown as it is to the user.
 *
 * @param statusCode The HTTP status code to return.
 * @param status The error message to return.
 * @param ctx The Javalin context of the request that caused the exception.
 * @param doNotLog If true, the exception will not be logged. Default is false.
 */
data class ErrorStatusException(val statusCode: Int, val status: String, private val ctx: Context, val doNotLog: Boolean = false) : Exception(status) {

    companion object {
        // Correspond to the error logger
        private val logger = LoggerFactory.getLogger(RestApi::class.java)
    }

    init {
        if(!doNotLog){
            logger.error("ErrorStatusException with code $statusCode and message '$status' thrown by ${stackTrace.first()} for request ${ctx.req().requestedSessionId} from ${ctx.req().remoteAddr}")
        }
    }

    val errorStatus: ErrorStatus
        get() = ErrorStatus(status)
}