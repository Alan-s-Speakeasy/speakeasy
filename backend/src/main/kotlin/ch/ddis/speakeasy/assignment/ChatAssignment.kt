package ch.ddis.speakeasy.assignment

import ch.ddis.speakeasy.user.User
import ch.ddis.speakeasy.user.UserSession

data class ChatAssignment(
    /** The [User] of the human 'assessor' */
    val human: User,
    /** The [User] of the agent under test, either a BOT or a HUMAN acting as one*/
    val bot: User,
    /** The prompt for the human assessor to start the conversation with*/
    val prompt: String
    )
