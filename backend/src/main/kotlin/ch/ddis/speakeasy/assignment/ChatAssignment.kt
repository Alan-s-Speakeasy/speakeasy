package ch.ddis.speakeasy.assignment

import ch.ddis.speakeasy.db.UserEntity

data class ChatAssignment(
    /** The [UserEntity] of the human 'assessor' */
    val human: UserEntity,
    /** The [UserEntity] of the agent under test, either a BOT or a HUMAN acting as one*/
    val bot: UserEntity,
    /** The prompt for the human assessor to start the conversation with*/
    val prompt: String
    )
