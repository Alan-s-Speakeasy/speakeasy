package ch.ddis.speakeasy.user

import ch.ddis.speakeasy.util.UID
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

typealias GroupId = UID

class Group(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Group>(Groups)

    var name by Groups.name
    var users by User via GroupUsers

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Group

        if (id != other.id) return false
        if (name != other.name) return false
        if (users != other.users) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + users.hashCode()
        return result
    }
}