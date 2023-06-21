package ch.ddis.speakeasy.user

enum class UserRole {

    HUMAN,
    BOT,
    ADMIN;

    companion object {
        fun fromInt(value: Int): UserRole = values().find { it.ordinal == value } ?: HUMAN
        fun toInt(userRole: UserRole): Int = userRole.ordinal
    }

    fun isHuman() = (this == HUMAN || this == ADMIN)

    fun isBot() = this == BOT

    fun isAdmin() = this == ADMIN
}