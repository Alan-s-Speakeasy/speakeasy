package ch.ddis.speakeasy.assignment

import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.user.UserSession
import ch.ddis.speakeasy.util.CyclicList

/**
 * Generates a round-robin-style assignment of humans and bots with a fixed number of bots per human.
 * Does not assign humans as bots.
 */
class ShuffledChatAssignmentGenerator(sessions: List<UserSession>, prompts: List<String>, private val botsPerHuman: Int) : ChatAssignmentGenerator {

    private val humanSessions = sessions.filter { it.user.role.isHuman() }.shuffled()
    private val botSessions = CyclicList(
        sessions.filter { it.user.role == UserRole.BOT }.shuffled()
    )
    private val cyclicPrompts = CyclicList(prompts)

    init {
        check(botSessions.size >= botsPerHuman) {"Number of bot sessions (${botSessions.size}) is smaller than the number of bots per human ($botsPerHuman)"}
        check(prompts.isNotEmpty()) {"List of prompts cannot be empty"}
    }

    override fun generateAssignments(): List<ChatAssignment> = humanSessions.flatMap { humanSession ->
        val prompt = cyclicPrompts.next()
        (0..botsPerHuman).map {
            ChatAssignment(
                humanSession,
                botSessions.next(),
                prompt
            )
        }
    }


}