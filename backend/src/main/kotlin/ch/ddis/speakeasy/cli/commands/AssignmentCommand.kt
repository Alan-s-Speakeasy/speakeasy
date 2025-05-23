package ch.ddis.speakeasy.cli.commands

import ch.ddis.speakeasy.api.AccessManager
import ch.ddis.speakeasy.assignment.ListChatAssignmentGenerator
import ch.ddis.speakeasy.assignment.ShuffledChatAssignmentGenerator
import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.cli.Cli
import ch.ddis.speakeasy.feedback.FeedbackManager
import ch.ddis.speakeasy.feedback.FormManager
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.util.UID
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import java.io.File

class AssignmentCommand : NoOpCliktCommand(name = "assignment") {

    init {
        this.subcommands(
            AssignmentStatusCommand(),
            NewAssignmentCommand(),
            NextAssignmentCommand()
        )
    }

    inner class AssignmentStatusCommand :
        CliktCommand(name = "status", help = "Shows the current status of an assignment") {

        override fun run() {
            if (Cli.assignmentGenerator == null) {
                println("No assignment generator active")
            } else {
                println("Active assignment generator: ${Cli.assignmentGenerator!!::class.simpleName}")
            }
        }

    }

    enum class AssignmentGeneratorType {
        SHUFFLED, LIST
    }

    inner class NewAssignmentCommand :
        CliktCommand(name = "new", help = "Initializes a new assignment generator", printHelpOnEmptyArgs = true) {

        private val type: AssignmentGeneratorType by option(
            "-t",
            "--type",
            help = "Which assignment generator to use"
        ).enum<AssignmentGeneratorType>().required()

        private val inputFile: String? by option(
            "-f",
            "--file",
            help = "Path to input file in case assignment generator needs one",
        )

        override fun run() {

            when (type) {
                AssignmentGeneratorType.SHUFFLED -> {
                    if (inputFile == null) {
                        println("no list with prompts provided")
                        return
                    }
                    val prompts = File(inputFile!!).readLines()

                    Cli.assignmentGenerator = ShuffledChatAssignmentGenerator(UserManager.list(), prompts, 3)

                }
                AssignmentGeneratorType.LIST -> {
                    if (inputFile == null) {
                        println("no list file provided")
                        return
                    }
                    Cli.assignmentGenerator = ListChatAssignmentGenerator(File(inputFile!!))

                }
            }
        }
    }

    inner class NextAssignmentCommand : CliktCommand(
        name = "next",
        help = "Starts the next round of chats as defined by current assignment generator"
    ) {

        private val duration: Int by option(
            "-d",
            "--duration",
            help = "duration of the next assignment in minutes (defaults to 10)"
        ).int().default(10)

        private val formName: String by option(
            "-f",
            "--form",
            help = "which feedback form to use: ${FormManager.listForms().map { it.formName }} (default no feedback)"
        ).default("")

        override fun run() {

            if (Cli.assignmentGenerator == null) {
                println("No assignment generator active")
                return
            }
            if (!FormManager.isValidFormName(formName)){
                println("You should leave it blank or choose an existing form: ${FormManager.listForms().map { it.formName }}")
                return
            }

            val next = Cli.assignmentGenerator!!.generateAssignments()

            val endTime = System.currentTimeMillis() + (1000 * 60 * duration)

            next.forEach { assignment ->
                ChatRoomManager.create(
                    userIds = listOf(assignment.human.id.UID(), assignment.bot.id.UID()),
                    log = true,
                    prompt = assignment.prompt,
                    assignment = true,
                    formRef = formName
                ).also { it.setEndTime(endTime) }
            }

            println("generated chatrooms based on ${next.size} assignments")

        }

    }

}