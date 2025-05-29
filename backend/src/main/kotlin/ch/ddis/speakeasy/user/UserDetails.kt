package ch.ddis.speakeasy.user

import ch.ddis.speakeasy.db.UserEntity

data class UserDetails(val id: String, val username: String, val role: UserRole) {
    companion object {
        fun of(user: UserEntity): UserDetails = UserDetails(user.id.toString(), user.name, user.role)
    }
}