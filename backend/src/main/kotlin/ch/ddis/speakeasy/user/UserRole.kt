package ch.ddis.speakeasy.user

enum class UserRole {

    HUMAN,
    BOT,
    ADMIN;

    fun isHuman() = (this == HUMAN || this == ADMIN)

    fun isBot() = this == BOT
}