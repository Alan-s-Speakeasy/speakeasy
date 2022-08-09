package ch.ddis.speakeasy.assignment

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.api.handlers.NewAssignmentObject
import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.user.User
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.util.CyclicList

/**
 * Generates a round-robin-style assignment of humans and bots with a fixed number of bots per human.
 * Does not assign humans as bots.
 */
object UIChatAssignmentGenerator {

    private var humans = emptyList<User>()
    private var bots = emptyList<User>()

    private var round = 0

    fun init() {
        humans = UserManager.list().filter { it.role.isHuman() }
        bots = UserManager.list().filter { it.role.isBot() }
        round = 1
    }

    fun clear() {
        humans = emptyList()
        bots = emptyList()
        round = 0
    }

    fun getHumans(): List<String> = humans.map { it.name }

    fun getActiveHumans(): List<String> = humans.filter { AccessManager.hasUserIdActiveSessions(it.id) }.map { it.name }

    fun getBots(): List<String> = bots.map { it.name }

    fun getActiveBots(): List<String> = bots.filter { AccessManager.hasUserIdActiveSessions(it.id) }.map { it.name }

    fun getRound(): Int = round

    fun newRound(assignment: NewAssignmentObject): Long {
        round += 1

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