package ch.ddis.speakeasy.util

import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

fun String.UID(): UID = UID(UUID.fromString(this))
fun EntityID<UUID>.UID(): UID = UID(this.toString())

data class UID(val string: String) {
    constructor() : this(UUID.randomUUID().toString())

    constructor(uuid: UUID) : this(uuid.toString())

    override fun toString(): String {
        return this.string
    }

    fun toUUID(): UUID {
        return UUID.fromString(this.string)
    }
}