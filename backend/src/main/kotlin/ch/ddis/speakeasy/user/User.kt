package ch.ddis.speakeasy.user

import ch.ddis.speakeasy.util.UID

typealias UserId = UID

class User(val id: UserId, val name: String, val role: UserRole, pass: Password) {

    val password: HashedPassword = when (pass) {
        is HashedPassword -> pass
        is PlainPassword -> pass.hash()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (id != other.id) return false
        if (name != other.name) return false
        if (role != other.role) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + role.hashCode()
        return result
    }


}