package ch.ddis.speakeasy.api

import ch.ddis.speakeasy.util.errorResponse
import ch.ddis.speakeasy.util.sessionToken
import io.javalin.core.security.Role
import io.javalin.http.Context


interface RestHandler {

    val route: String

}

interface GetRestHandler<T: Any> : RestHandler {

    fun get(ctx: Context) {
        AccessManager.updateLastAccess(ctx.sessionToken())
        try {
            ctx.json(doGet(ctx))
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

interface AccessManagedRestHandler : RestHandler {

    val permittedRoles: Set<Role>

}