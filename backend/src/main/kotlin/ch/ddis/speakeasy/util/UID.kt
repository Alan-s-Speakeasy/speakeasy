package ch.ddis.speakeasy.util

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

fun String.UID(): UID = UID(UUID.fromString(this))
fun EntityID<UUID>.UID(): UID = UID(this.toString())

@Serializable
data class UID(val string: String) {
    constructor() : this(UUID.randomUUID().toString())

    constructor(uuid: UUID) : this(uuid.toString())

    override fun toString(): String {
        return this.string
    }

    fun toUUID(): UUID {
        return UUID.fromString(this.string)
    }


    companion object {
        /**
         * Invalid UID. Used e.g for default values.
         */
        val INVALID : UID = UID("00000000-0000-0000-0000-000000000000")

        /**
         * Checks if a given UID is invalid. An UID is invalid here if it is equal to the INVALID UID, this function does
         * not check if the UID is a valid UUID in a programming sense.
         */
        fun isInvalid(uid: UID): Boolean {
            return uid == INVALID
        }
    }
}