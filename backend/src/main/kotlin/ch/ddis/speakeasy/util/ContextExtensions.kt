package ch.ddis.speakeasy.util

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.api.ErrorStatus
import ch.ddis.speakeasy.api.ErrorStatusException
import io.javalin.http.Context
import kotlin.random.Random

fun Context.errorResponse(status: Int, errorMessage: String) {
    this.status(status)
    this.json(ErrorStatus(errorMessage))
}

fun Context.errorResponse(error: ErrorStatusException) {
    this.status(error.statusCode)
    this.json(error.errorStatus)
}

fun Context.sessionToken(): String? = this.attribute<String>("session")

fun Context.getOrCreateSessionToken(): String {
    val attributeId = this.attribute<String>("session")
    if (attributeId != null) {
        return attributeId
    }

    val random = Random(System.nanoTime())
    val id = List(AccessManager.SESSION_TOKEN_LENGTH) { AccessManager.SESSION_TOKEN_CHAR_POOL.random(random) }.joinToString("")

    this.attribute("session", id)

    return id

}