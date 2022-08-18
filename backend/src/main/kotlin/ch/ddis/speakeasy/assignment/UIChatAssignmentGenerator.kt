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
    private val assignmentHistory = hashMapOf<String, MutableSet<String>>().withDefault { mutableSetOf() }

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
        nextRound.clear()
        assignmentHistory.clear()
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

    fun generateNewRound(assignment: NewAssignmentObject): Pair<List<GeneratedAssignment>, Boolean> {
        nextRound = mutableListOf()

        prompts = assignment.prompts
        botsPerHuman = assignment.botsPerHuman
        duration = assignment.duration

        selected.humans = assignment.humans
        selected.bots = assignment.bots
        selected.admins = assignment.admins

        selected.humans.forEach { human ->
            assignmentHistory.putIfAbsent(human, mutableSetOf())
        }

        val cyclicPrompts = CyclicList(prompts.shuffled())

        (1..botsPerHuman).map {
            val selectedBots = mutableSetOf<String>()
            assignment.humans.forEach { human ->
                val bots = (assignment.bots + assignment.admins).shuffled()
                loop@ for (bot in bots) {
                    if (selectedBots.size == bots.size) {
                        selectedBots.clear()
                    }

                    if (!selectedBots.contains(bot) &&
                        !assignmentHistory[human]!!.contains(bot) &&
                        nextRound.none { it.human == human && it.bot == bot })
                    {
                        selectedBots.add(bot)
                        nextRound.add(GeneratedAssignment(human, bot, cyclicPrompts.next()))
                        break@loop
                    }
                }
            }
        }

        return Pair(nextRound, nextRound.size / selected.humans.size == botsPerHuman)
    }

    fun startNewRound(): Long {
        endTime = System.currentTimeMillis() + (1000 * 60 * duration)

        nextRound.forEach { a ->
            assignmentHistory[a.human]?.add(a.bot)
            ChatRoomManager.create(
                mutableSetOf(UserManager.getUserIdFromUsername(a.human)!!, UserManager.getUserIdFromUsername(a.bot)!!),
                true, a.prompt, endTime
            )
        }

        round += 1
        return (1000L * 60 * duration)
    }
}