package ch.ddis.speakeasy.assignment

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.api.handlers.AssignmentGeneratorObject
import ch.ddis.speakeasy.api.handlers.GeneratedAssignment
import ch.ddis.speakeasy.api.handlers.NewAssignmentObject
import ch.ddis.speakeasy.api.handlers.SelectedUsers
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
    private var admins = emptyList<User>()
    private var selected = SelectedUsers(mutableListOf(), mutableListOf(), mutableListOf())
    private var prompts = emptyList<String>()
    private var botsPerHuman = 0
    private var duration = 0
    private var endTime = 0L
    private var round = 0

    private var nextRound: MutableList<GeneratedAssignment> = mutableListOf()

    fun init() {
        humans = UserManager.list().filter { it.role.isHuman() }
        bots = UserManager.list().filter { it.role.isBot() }
        admins = UserManager.list().filter { it.role.isAdmin() }
        botsPerHuman = 3
        duration = 10
        round = 1
    }

    fun clear() {
        humans = emptyList()
        bots = emptyList()
        admins = emptyList()
        selected = SelectedUsers(mutableListOf(), mutableListOf(), mutableListOf())
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
            admins.map { it.name },
            humans.filter { AccessManager.hasUserIdActiveSessions(it.id) }.map { it.name } +
                bots.filter { AccessManager.hasUserIdActiveSessions(it.id) }.map { it.name },
            selected,
            nextRound,
            prompts,
            botsPerHuman,
            duration,
            timeLeft,
            round
        )
    }

    fun generateNewRound(assignment: NewAssignmentObject): List<GeneratedAssignment> {
        nextRound = mutableListOf()

        prompts = assignment.prompts
        botsPerHuman = assignment.botsPerHuman
        duration = assignment.duration

        selected = SelectedUsers(mutableListOf(), mutableListOf(), mutableListOf())
        selected.humans.addAll(assignment.humans)
        selected.bots.addAll(assignment.bots)
        selected.admins.addAll(assignment.admins)

        val humans = assignment.humans.mapNotNull { UserManager.getUserFromUsername(it) }.shuffled()
        val bots = CyclicList(
            assignment.bots.mapNotNull { UserManager.getUserFromUsername(it) }
                    + assignment.admins.mapNotNull { UserManager.getUserFromUsername(it) }
        )
        val cyclicPrompts = CyclicList(prompts.shuffled())

        humans.flatMap { human ->
            (1..botsPerHuman).map {
                nextRound.add(GeneratedAssignment(human.name, bots.next().name, cyclicPrompts.next()))
            }
        }
        return nextRound
    }

    fun startNewRound(): Long {
        endTime = System.currentTimeMillis() + (1000 * 60 * duration)

        nextRound.forEach { a ->
            ChatRoomManager.create(
                mutableSetOf(UserManager.getUserIdFromUsername(a.human)!!, UserManager.getUserIdFromUsername(a.bot)!!),
                true, a.prompt, endTime
            )
        }

        round += 1
        return (1000L * 60 * duration)
    }
}