package ch.ddis.speakeasy.assignment

import ch.ddis.speakeasy.user.UserSession

data class ChatAssignment(
    /** The [UserSession] of the human 'assessor' */
    val humanSession: UserSession,
    /** The [UserSession] of the agent under test, either a BOT or a HUMAN acting as one*/
    val botSession: UserSession,
    /** The prompt for the human assessor to start the conversation with*/
    val prompt: String
    )
