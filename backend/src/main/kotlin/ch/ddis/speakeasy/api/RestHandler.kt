package ch.ddis.speakeasy.api

import ch.ddis.speakeasy.util.errorResponse
import ch.ddis.speakeasy.util.sessionToken
import io.javalin.security.RouteRole
import io.javalin.http.Context
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory

private fun logRestError(ctx: Context, e: Exception) {
    LoggerFactory.getLogger("ch.ddis.speakeasy.api.RestApi")
        .error(MarkerFactory.getMarker("ERROR"), "Exception processing ${ctx.method()} ${ctx.path()}", e)
}


interface RestHandler {

    val route: String

}

/**
 * A RestHandler that can be used to handle GET requests.
 *
 * @param T The type of the object that is returned by the handler.
 */
interface GetRestHandler<T: Any> : RestHandler {
    /**
    * Specifies if the handler already returns json.
    * Prevents it from being wrapped in a json object.
     * Straightforward solution, would be better to use a more OO method.
     */
    val parseAsJson: Boolean
        get() = true

    fun get(ctx: Context) {
        AccessManager.updateLastAccess(ctx.sessionToken())
        try {
            // Calls the doGet method and returns the result.
            // If parseAsJson is true, the result is directly returned as json.
            val result = doGet(ctx)
            if (parseAsJson) {
                ctx.json(result)
            }
        } catch (e: Exception) {
            logRestError(ctx, e)
            if (e is ErrorStatusException) {
                ctx.errorResponse(e)
            } else {
                ctx.errorResponse(500, e.message ?: "Internal Server Error")
            }
        }
    }

    fun doGet(ctx: Context): T

}

interface PostRestHandler<T: Any> : RestHandler {

    fun post(ctx: Context) {
        AccessManager.updateLastAccess(ctx.sessionToken())
        try {
            ctx.json(doPost(ctx))
        } catch (e: Exception) {
            logRestError(ctx, e)
            if (e is ErrorStatusException) {
                ctx.errorResponse(e)
            } else {
                ctx.errorResponse(500, e.message ?: "Internal Server Error")
            }
        }
    }

    fun doPost(ctx: Context): T

}

interface PatchRestHandler<T: Any> : RestHandler {

    fun patch(ctx: Context) {
        AccessManager.updateLastAccess(ctx.sessionToken())
        try {
            ctx.json(doPatch(ctx))
        } catch (e: Exception) {
            logRestError(ctx, e)
            if (e is ErrorStatusException) {
                ctx.errorResponse(e)
            } else {
                ctx.errorResponse(500, e.message ?: "Internal Server Error")
            }
        }
    }

    fun doPatch(ctx: Context): T

}

interface DeleteRestHandler<T: Any> : RestHandler {

    fun delete(ctx: Context) {
        AccessManager.updateLastAccess(ctx.sessionToken())
        try {
            ctx.json(doDelete(ctx))
        } catch (e: Exception) {
            logRestError(ctx, e)
            if (e is ErrorStatusException) {
                ctx.errorResponse(e)
            } else {
                ctx.errorResponse(500, e.message ?: "Internal Server Error")
            }
        }
    }

    fun doDelete(ctx: Context): T

}

interface PutRestHandler<T: Any> : RestHandler {

    fun put(ctx: Context) {
        AccessManager.updateLastAccess(ctx.sessionToken())
        try {
            ctx.json(doPut(ctx))
        } catch (e: Exception) {
            logRestError(ctx, e)
            if (e is ErrorStatusException) {
                ctx.errorResponse(e)
            } else {
                ctx.errorResponse(500, e.message ?: "Internal Server Error")
            }
        }
    }

    fun doPut(ctx: Context): T

}

interface AccessManagedRestHandler : RestHandler {

    val permittedRoles: Set<RouteRole>

}