package ch.ddis.speakeasy.assignment

import ch.ddis.speakeasy.user.User
import ch.ddis.speakeasy.util.CyclicList

/**
 * Generates a round-robin-style assignment of humans and bots with a fixed number of bots per human.
 * Does not assign humans as bots.
 */
class ShuffledChatAssignmentGenerator(users: List<User>, prompts: List<String>, private val botsPerHuman: Int) : ChatAssignmentGenerator {

    private val humans = users.filter { it.role.isHuman() }.shuffled()
    private val bots = CyclicList(
        users.filter { it.role.isBot() }.shuffled()
    )
    private val cyclicPrompts = CyclicList(prompts)

    init {
        check(bots.size >= botsPerHuman) {"Number of bots (${bots.size}) is smaller than the number of bots per human ($botsPerHuman)"}
        check(prompts.isNotEmpty()) {"List of prompts cannot be empty"}
    }

    override fun generateAssignments(): List<ChatAssignment> = humans.flatMap { human ->
        val prompt = cyclicPrompts.next()
        (0..botsPerHuman).map {
            ChatAssignment(
                human,
                bots.next(),
                prompt
            )
        }
    }


}