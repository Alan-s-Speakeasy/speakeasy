package ch.ddis.speakeasy.assignment

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.api.handlers.*
import ch.ddis.speakeasy.chat.ChatRoom
import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.db.UserEntity
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.CyclicList
import ch.ddis.speakeasy.util.UID

/**
 * Generates a round-robin-style assignment of humans and bots with a fixed number of bots per human.
 * Does not assign humans as bots.
 */
object UIChatAssignmentGenerator {

    private var humans = emptyList<UserEntity>()
    private var bots = emptyList<UserEntity>()
    private var admins = emptyList<UserEntity>()
    private var evaluator = emptyList<UserEntity>()
    private var assistant = emptyList<UserEntity>()
    private var selected = SelectedUsers(mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())
    private var prompts = emptyList<String>()
    private var botsPerHuman = 0
    private var duration = 0
    private var endTime = 0L
    private var round = 0
    private var formName: String = ""
    private var nextRound: MutableList<GeneratedAssignment> = mutableListOf()
    private val humanAssignments = hashMapOf<String, MutableList<String>>().withDefault { mutableListOf() }
    private val chatRooms: MutableList<ChatRoom> = mutableListOf()

    fun init() {
        humans = UserManager.list().filter { it.role.isHuman() }
        bots = UserManager.list().filter { it.role.isBot() }
        admins = UserManager.list().filter { it.role.isAdmin() }
        evaluator = UserManager.list().filter { it.role.isEvaluator() }
        assistant = UserManager.list().filter { it.role.isAssistant() }
        botsPerHuman = 3
        duration = 10
        round = 1
    }

    fun clear() {
        humans = emptyList()
        bots = emptyList()
        admins = emptyList()
        evaluator = emptyList()
        assistant = emptyList()
        selected = SelectedUsers(mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())
        prompts = emptyList()
        botsPerHuman = 0
        duration = 0
        endTime = 0L
        round = 0
        formName = ""
        nextRound.clear()
        humanAssignments.clear()
        chatRooms.clear()
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
            evaluator.map { it.name },
            assistant.map { it.name },
            humans.filter { AccessManager.hasUserIdActiveSessions(it.id.UID()) }.map { it.name } +
                bots.filter { AccessManager.hasUserIdActiveSessions(it.id.UID()) }.map { it.name } +
                evaluator.filter { AccessManager.hasUserIdActiveSessions(it.id.UID()) }.map { it.name } +
                assistant.filter { AccessManager.hasUserIdActiveSessions(it.id.UID()) }.map { it.name },
            selected,
            nextRound,
            prompts,
            formName,
            botsPerHuman,
            duration,
            round,
            timeLeft,
            chatRooms.map { ChatRoomAdminInfo(it) }
        )
    }

    private fun wasNotAlreadySelected(human: String, bot: String, slack: Int): Boolean {
        val previous = humanAssignments[human]!!.count { it == bot }
        val current = nextRound.count { it.human == human && it.bot == bot }
        return current == 0 && previous <= slack
    }

    fun generateNewRound(assignment: NewAssignmentObject): Pair<List<GeneratedAssignment>, Boolean> {
        nextRound = mutableListOf()

        prompts = assignment.prompts
        botsPerHuman = assignment.botsPerHuman
        duration = assignment.duration
        formName = assignment.formName

        selected.humans = assignment.humans
        selected.bots = assignment.bots
        selected.admins = assignment.admins

        selected.humans.forEach { human ->
            humanAssignments.putIfAbsent(human, mutableListOf())
        }

        val cyclicPrompts = CyclicList(prompts.shuffled())
        val bots = (assignment.bots + assignment.admins).shuffled()
        var botIndex = 0

        (1..botsPerHuman).map {
            selected.humans.forEach { human ->
                var assigned = false
                var slack = 0
                while (!assigned) {
                    val bot = bots[botIndex]
                    if (slack < bots.size){ // Comply with group constraint
                        if (wasNotAlreadySelected(human, bot, slack) && !UserManager.areInSameGroup(human, bot)) {
                            nextRound.add(GeneratedAssignment(human, bot, cyclicPrompts.next(), formName))
                            assigned = true
                        } else {
                            botIndex = (botIndex + 1) % bots.size
                            if (botIndex == 0) { slack += 1 }
                        }
                    } else { // When the attempts are too many, we drop the group constraint (Just for some edge cases)
                        if (wasNotAlreadySelected(human, bot, slack)) {
                            nextRound.add(GeneratedAssignment(human, bot, cyclicPrompts.next(), formName))
                            assigned = true
                        } else {
                            botIndex = (botIndex + 1) % bots.size
                            if (botIndex == 0) { slack += 1 }
                        }
                    }
                }
                botIndex = (botIndex + 1) % bots.size
            }
        }

        return Pair(nextRound, nextRound.size / selected.humans.size == botsPerHuman)
    }

    fun generateNewAutomaticRound(assignment: NewAssignmentObject): Pair<List<GeneratedAssignment>, Boolean> {
        nextRound = mutableListOf()

        formName = assignment.formName
        prompts = assignment.prompts
        selected.bots = assignment.bots

        val cyclicPrompts = CyclicList(prompts.shuffled())
        val bots = assignment.bots

        for (bot in bots){
            val evaluatorUsername = ChatRoomManager.getBot(UserRole.EVALUATOR)
            nextRound.add(GeneratedAssignment(evaluatorUsername, bot, cyclicPrompts.next(), formName))
        }
        return Pair(nextRound, true)
    }

    fun startNewRound(assistantSelected: Boolean): Long {
        endTime = System.currentTimeMillis() + (1000 * 60 * duration)

        nextRound.forEach { a ->
            val humanId = UserManager.getUserIdFromUsername(a.human)!!
            val botId = UserManager.getUserIdFromUsername(a.bot)!!

            // Only create the chat room if both users are online
            if (AccessManager.hasUserIdActiveSessions(humanId) && AccessManager.hasUserIdActiveSessions(botId)) {
                humanAssignments.putIfAbsent(a.human, mutableListOf())
                humanAssignments[a.human]?.add(a.bot)

                if(assistantSelected){
                    val assistantUsername = UserManager.getUserIdFromUsername(ChatRoomManager.getBot(UserRole.ASSISTANT))!!
                    val chatRoom = ChatRoomManager.create(
                        userIds = listOf(humanId, botId, assistantUsername),
                        formRef = a.formName,
                        log = true,
                        prompt = a.prompt,
                        endTime = endTime,
                        assignment = true)

                    chatRooms.add(chatRoom)
                }else{
                    val chatRoom = ChatRoomManager.create(
                        userIds = listOf(humanId, botId),
                        formRef = a.formName,
                        log = true,
                        prompt = a.prompt,
                        endTime = endTime,
                        assignment = true)
                    chatRooms.add(chatRoom)
                }
            }
        }

        round += 1
        return (1000L * 60 * duration)
    }
}