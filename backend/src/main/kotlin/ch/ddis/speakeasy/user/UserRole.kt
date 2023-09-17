package ch.ddis.speakeasy.user

enum class UserRole {

    HUMAN,
    BOT,
    ADMIN,
    EVALUATOR;

    companion object {
        fun fromInt(value: Int): UserRole = values().find { it.ordinal == value } ?: HUMAN
        fun toInt(userRole: UserRole): Int = userRole.ordinal
    }

    fun isHuman() = (this == HUMAN || this == ADMIN)

    fun isBot() = this == BOT

    fun isEvaluator() = this == EVALUATOR

    fun isAdmin() = this == ADMIN
}