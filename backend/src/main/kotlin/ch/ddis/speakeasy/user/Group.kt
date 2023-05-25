package ch.ddis.speakeasy.user

import ch.ddis.speakeasy.util.UID

typealias GroupId = UID

class Group(var id: GroupId, var name: String, var users: MutableList<User> = mutableListOf()) {

    fun size(): Int {
        return users.size
    }

    fun isEmpty(): Boolean {
        return users.isEmpty()
    }

    fun addUser(user: User) {
        users.add(user)
    }

    fun removeUser(user: User): Boolean {
        return users.remove(user)
    }

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