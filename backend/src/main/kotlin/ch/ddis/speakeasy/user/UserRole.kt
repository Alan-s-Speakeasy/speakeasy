package ch.ddis.speakeasy.user

enum class UserRole {

    HUMAN,
    BOT,
    ADMIN,
    EVALUATOR,
    ASSISTANT,
    TESTER;

    companion object {
        fun fromInt(value: Int): UserRole = entries.find { it.ordinal == value } ?: HUMAN
        fun toInt(userRole: UserRole): Int = userRole.ordinal
    }

    fun isHuman() = (this == HUMAN || this == ADMIN)

    fun isBot() = this == BOT

    fun isEvaluator() = this == EVALUATOR

    fun isAdmin() = this == ADMIN

    fun isAssistant() = this == ASSISTANT

    fun isTester() = this == TESTER
}