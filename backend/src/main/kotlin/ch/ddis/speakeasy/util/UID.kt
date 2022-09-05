package ch.ddis.speakeasy.util

import java.util.*

fun String.UID(): UID = UID(UUID.fromString(this))

data class UID(val string: String) {
    constructor() : this(UUID.randomUUID().toString())

    constructor(uuid: UUID) : this(uuid.toString())

    override fun toString(): String {
        return this.string
    }
}