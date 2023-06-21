package ch.ddis.speakeasy.user

data class UserDetails(val id: String, val username: String, val role: UserRole) {
    companion object {
        fun of(user: User): UserDetails = UserDetails(user.id.string(), user.name, user.role)
    }
}