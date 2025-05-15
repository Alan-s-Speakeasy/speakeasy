package ch.ddis.speakeasy.api

import ch.ddis.speakeasy.util.errorResponse
import ch.ddis.speakeasy.util.sessionToken
import io.javalin.security.RouteRole
import io.javalin.http.Context


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
     * Straightforware solution, would be better to use a more OO method.
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
        } catch (e: ErrorStatusException) {
            ctx.errorResponse(e)
        } catch (e: Exception) {
            ctx.errorResponse(500, e.message ?: "")
        }
    }

    fun doGet(ctx: Context): T

}

interface PostRestHandler<T: Any> : RestHandler {

    fun post(ctx: Context) {
        AccessManager.updateLastAccess(ctx.sessionToken())
        try {
            ctx.json(doPost(ctx))
        } catch (e: ErrorStatusException) {
            ctx.errorResponse(e)
        } catch (e: Exception) {
            ctx.errorResponse(500,e.message ?: "")
        }
    }

    fun doPost(ctx: Context): T

}

interface PatchRestHandler<T: Any> : RestHandler {

    fun patch(ctx: Context) {
        AccessManager.updateLastAccess(ctx.sessionToken())
        try {
            ctx.json(doPatch(ctx))
        } catch (e: ErrorStatusException) {
            ctx.errorResponse(e)
        } catch (e: Exception) {
            ctx.errorResponse(500, e.message ?: "")
        }
    }

    fun doPatch(ctx: Context): T

}

interface DeleteRestHandler<T: Any> : RestHandler {

    fun delete(ctx: Context) {
        AccessManager.updateLastAccess(ctx.sessionToken())
        try {
            ctx.json(doDelete(ctx))
        } catch (e: ErrorStatusException) {
            ctx.errorResponse(e)
        } catch (e: Exception) {
            ctx.errorResponse(500, e.message ?: "")
        }
    }

    fun doDelete(ctx: Context): T

}

interface PutRestHandler<T: Any> : RestHandler {

    fun put(ctx: Context) {
        AccessManager.updateLastAccess(ctx.sessionToken())
        try {
            ctx.json(doPut(ctx))
        } catch (e: ErrorStatusException) {
            ctx.errorResponse(e)
        } catch (e: Exception) {
            ctx.errorResponse(500, e.message ?: "")
        }
    }

    fun doPut(ctx: Context): T

}

interface AccessManagedRestHandler : RestHandler {

    val permittedRoles: Set<RouteRole>

}