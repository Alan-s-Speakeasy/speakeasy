package ch.ddis.speakeasy.assignment

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.api.handlers.NewAssignmentObject
import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.util.CyclicList

/**
 * Generates a round-robin-style assignment of humans and bots with a fixed number of bots per human.
 * Does not assign humans as bots.
 */
object UIChatAssignmentGenerator {

    init {
//        check(bots.size >= botsPerHuman) {"Number of bots (${bots.size}) is smaller than the number of bots per human ($botsPerHuman)"}
//        check(prompts.isNotEmpty()) {"List of prompts cannot be empty"}
    }

    fun getHumans(): List<String> = UserManager.list().filter { it.role.isHuman() }.map { it.name }
    fun getBots(): List<String> = UserManager.list().filter { it.role.isBot() }.map { it.name }

    fun newRound(assignment: NewAssignmentObject): Long {

        val humans = assignment.humans.mapNotNull { UserManager.getUserFromUsername(it) }.shuffled()
        val bots = CyclicList(
            assignment.bots.mapNotNull { UserManager.getUserFromUsername(it) }.shuffled()
        )

        val cyclicPrompts = CyclicList(assignment.prompts)
        val endTime = System.currentTimeMillis() + (1000 * 60 * assignment.duration)

        humans.flatMap { human ->
            val prompt = cyclicPrompts.next()
            (0..assignment.botsPerHuman).map {
                ChatRoomManager.create(
                    AccessManager.getSessionsForUser(human) + AccessManager.getSessionsForUser(bots.next()),
                    true, prompt
                ).also { it.setEndTime(endTime) }
            }
        }

        return endTime - System.currentTimeMillis()
    }
}