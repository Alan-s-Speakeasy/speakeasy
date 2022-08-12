package ch.ddis.speakeasy.assignment

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.api.handlers.AssignmentGeneratorObject
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
    private var prompts = emptyList<String>()
    private var botsPerHuman = 0
    private var duration = 0
    private var endTime = 0L
    private var round = 0

    fun init() {
        humans = UserManager.list().filter { it.role.isHuman() }
        bots = UserManager.list().filter { it.role.isBot() }
        botsPerHuman = 3
        duration = 10
        round = 1
    }

    fun clear() {
        humans = emptyList()
        bots = emptyList()
        prompts = emptyList()
        botsPerHuman = 0
        duration = 0
        endTime = 0L
        round = 0
    }

    fun getStatus(): AssignmentGeneratorObject {
        val timeLeft = if (endTime == 0L) {0L} else {endTime - System.currentTimeMillis()}
        if (timeLeft < 0) {
            endTime = 0L
        }
        return AssignmentGeneratorObject(
            humans.map { it.name },
            bots.map { it.name },
            humans.filter { AccessManager.hasUserIdActiveSessions(it.id) }.map { it.name } +
                bots.filter { AccessManager.hasUserIdActiveSessions(it.id) }.map { it.name },
            prompts,
            botsPerHuman,
            duration,
            timeLeft,
            round
        )
    }

    fun newRound(assignment: NewAssignmentObject): Long {
        round += 1
        prompts = assignment.prompts
        botsPerHuman = assignment.botsPerHuman
        duration = assignment.duration

        val humans = assignment.humans.mapNotNull { UserManager.getUserFromUsername(it) }.shuffled()
        val bots = CyclicList(
            assignment.bots.mapNotNull { UserManager.getUserFromUsername(it) }.shuffled()
        )

        val cyclicPrompts = CyclicList(prompts)
        endTime = System.currentTimeMillis() + (1000 * 60 * duration)

        humans.flatMap { human ->
            val prompt = cyclicPrompts.next()
            (1..botsPerHuman).map {
                ChatRoomManager.create(
                    AccessManager.getSessionsForUser(human) + AccessManager.getSessionsForUser(bots.next()),
                    true, prompt
                ).also { it.setEndTime(endTime) }
            }
        }

        return (1000L * 60 * duration)
    }
}