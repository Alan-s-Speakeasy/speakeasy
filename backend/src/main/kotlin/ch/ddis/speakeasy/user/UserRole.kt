package ch.ddis.speakeasy.user

enum class UserRole {

    HUMAN,
    BOT,
    ADMIN,
    EVALUATOR,
    TESTER;

    companion object {
        fun fromInt(value: Int): UserRole = values().find { it.ordinal == value } ?: HUMAN
        fun toInt(userRole: UserRole): Int = userRole.ordinal
    }

    fun isHuman() = (this == HUMAN || this == ADMIN)

    fun isBot() = this == BOT

    fun isEvaluator() = this == EVALUATOR

    fun isTester() = this == TESTER

    fun isAdmin() = this == ADMIN
}