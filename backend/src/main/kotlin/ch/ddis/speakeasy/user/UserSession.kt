package ch.ddis.speakeasy.user

import ch.ddis.speakeasy.util.SessionAliasGenerator
import ch.ddis.speakeasy.util.UID

typealias SessionId = UID

data class UserSession(
    val user: User,
    val sessionToken: String,
    val sessionId: SessionId = UID(),
    val startTime: Long = System.currentTimeMillis()
)

data class UserSessionDetails(
    val userDetails: UserDetails,
    val sessionToken: String,
    val sessionId: String,
    val startTime: Long
) {
    constructor(session: UserSession) : this(
        UserDetails.of(session.user),
        session.sessionToken,
        session.sessionId.string,
        session.startTime
    )
}