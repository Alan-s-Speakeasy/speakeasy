package ch.ddis.speakeasy.user

import org.jetbrains.exposed.sql.transactions.transaction


data class GroupDetails(val id: String, val name: String, val users: List<UserDetails>) {

    companion object {
        fun of(group: Group): GroupDetails {
            val usersEager = transaction {// eager loading
                 group.users.toList()
            }
            return GroupDetails(group.id.string(), group.name, usersEager.map(UserDetails.Companion::of))

        }
    }
}