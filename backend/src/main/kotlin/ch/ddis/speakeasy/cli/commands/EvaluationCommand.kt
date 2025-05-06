package ch.ddis.speakeasy.cli.commands

import ch.ddis.speakeasy.feedback.FeedbackForm
import ch.ddis.speakeasy.api.handlers.FeedbackResponseStatsItem
import ch.ddis.speakeasy.api.handlers.FeedbackResponseItem
import ch.ddis.speakeasy.feedback.FeedbackManager
import ch.ddis.speakeasy.feedback.FormManager
import ch.ddis.speakeasy.user.UserManager
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.jakewharton.picnic.BorderStyle
import com.jakewharton.picnic.TextAlignment
import com.jakewharton.picnic.table
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

class EvaluationCommand : NoOpCliktCommand(name = "evaluation") {

    init {
        this.subcommands(
            FromRating(),
            ForRating(),
            AllRatings(),
            Summary()
        )
    }

    private fun getFeedbackNameForValue(form: FeedbackForm, id: String, value: String, csv: Boolean = false): String {
        form.requests.forEach { if (it.id == id) {
            it.options.forEach { o -> if (o.value.toString() == value) {return o.name} }
        } }
        return if (value == "0") {
            "---"
        } else {
            if (csv) {
                "\"" + value.replace("\"", "\"\"") + "\""
            }
            else {
                value
            }
        }
    }

    private fun createOutputFile(filename: String): PrintWriter {
        val outputFile = File(filename)
        outputFile.writeText("", Charsets.UTF_8)
        return PrintWriter(
            FileWriter(
                outputFile,
                Charsets.UTF_8,
                true
            ),
            true
        )
    }

    fun printEvaluationPerUser(
        header: FeedbackForm,
        responses: List<FeedbackResponseItem>,
        output: String?,
        author: Boolean,
        formName: String,
    ) {
        if (output != null) {
            val fileWriter = createOutputFile(output)
            val firstHeader = if (author) "Recipient" else "Author"
            fileWriter.println(firstHeader + "," + header.requests.joinToString(",") { it.shortname })
            responses.forEach { response ->
                val parts: MutableList<String> = mutableListOf()
                parts.add(if (author) response.recipient else response.author)
                response.responses.forEach {
                    parts.add(getFeedbackNameForValue(header, it.id, it.value, csv = true))
                }
                fileWriter.println(parts.joinToString(","))
            }
        }
        else {
            println(
                table {
                    style {
                        borderStyle = BorderStyle.Hidden
                    }
                    cellStyle {
                        paddingLeft = 1
                        paddingRight = 1
                        borderLeft = true
                        borderRight = true
                    }
                    header {
                        cellStyle {
                            border = true
                            alignment = TextAlignment.MiddleLeft
                        }
                        row {
                            cell(if (author) "Recipient" else "Author")
                            header.requests.forEach {
                                cell(it.shortname)
                            }
                        }
                    }
                    body {
                        responses.forEach { response ->
                            row {
                                cell(if (author) response.recipient else response.author)
                                response.responses.forEach {
                                    cell(getFeedbackNameForValue(header, it.id, it.value))
                                }
                            }
                        }
                    }
                    footer {
                        cellStyle {
                            border = true
                        }
                        row {
                            cell("AVERAGE")
                            FeedbackManager.computeStatsPerRequestOfFeedback(responses.flatMap { it.responses }, formName).forEach {
                                cell(getFeedbackNameForValue(header, it.requestID, it.average))
                            }
                        }
                    }
                }
            )
        }
    }

    fun printAllEvaluations(
        header: FeedbackForm,
        allResponses: List<FeedbackResponseStatsItem>,
        output: String?,
        author: Boolean,
        formName: String
    ) {
        if (output != null) {
            val fileWriter = createOutputFile(output)
            val firstHeader = if (author) "Author" else "Recipient"
            fileWriter.println(firstHeader + ",count," + header.requests.joinToString(",") { it.shortname })
            allResponses.forEach { response ->
                val parts: MutableList<String> = mutableListOf()
                parts.add(response.username)
                parts.add(response.count.toString())
                response.statsOfResponsePerRequest.forEach {
                    parts.add(getFeedbackNameForValue(header, it.requestID, it.average, csv = true))
                }
                fileWriter.println(parts.joinToString(","))
            }
        }
        else {
            println(
                table {
                    style {
                        borderStyle = BorderStyle.Hidden
                    }
                    cellStyle {
                        paddingLeft = 1
                        paddingRight = 1
                        borderLeft = true
                        borderRight = true
                    }
                    header {
                        cellStyle {
                            border = true
                            alignment = TextAlignment.MiddleLeft
                        }
                        row {
                            cell(if (author) "Author" else "Recipient")
                            cell("Number of Evaluations")
                            header.requests.forEach {
                                cell(it.shortname)
                            }
                        }
                    }
                    body {
                        allResponses.forEach { response ->
                            row {
                                cell(response.username)
                                cell(response.count)
                                response.statsOfResponsePerRequest.forEach {
                                    cell(getFeedbackNameForValue(header, it.requestID, it.average))
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    fun printSummary(
        header: FeedbackForm,
        responses: MutableList<FeedbackResponseItem>,
        output: String?,
        formName: String
    ) {
        if (output != null) {
            val fileWriter = createOutputFile(output)
            fileWriter.println(header.requests.joinToString(",") { it.shortname })
            val parts: MutableList<String> = mutableListOf()
            FeedbackManager.computeStatsPerRequestOfFeedback(responses.flatMap { it.responses }, formName).forEach {
                parts.add(getFeedbackNameForValue(header, it.requestID, it.average, csv = true))
            }
            fileWriter.println(parts.joinToString(","))
        }
        else {
            println(
                table {
                    style {
                        borderStyle = BorderStyle.Hidden
                    }
                    cellStyle {
                        paddingLeft = 1
                        paddingRight = 1
                        borderLeft = true
                        borderRight = true
                    }
                    header {
                        cellStyle {
                            border = true
                            alignment = TextAlignment.MiddleLeft
                        }
                        row {
                            header.requests.forEach {
                                cell(it.shortname)
                            }
                        }
                    }
                    body {
                        row {
                            FeedbackManager.computeStatsPerRequestOfFeedback(responses.flatMap { it.responses }, formName).forEach {
                                cell(getFeedbackNameForValue(header, it.requestID, it.average))
                            }
                        }
                    }
                }
            )
        }
    }

    inner class FromRating : CliktCommand(name = "from", help = "Lists ratings made by a user") {
        private val username: String by argument()
        private val output: String? by option("-o", "--output")
        private val assigned: Boolean by option("--assigned",
            help = "Flag to list ratings only for chat rooms assigned by administrators.").flag()
        private val requested: Boolean by option("--requested",
            help = "Flag to list ratings only for chat rooms requested by students.").flag()

        private val formName: String by option(
            "-f", "--form",
            help = "Which form to review: ${FeedbackManager.readFeedbackFromList().map { it.formName }}").required()

        override fun run() {
            val user = UserManager.list().find { it.name == username }
            if (user == null) {
                println("no user found")
                return
            }
            if ((assigned && requested) || (!assigned && !requested)) {
                println("Assigned or requested flag required")
                println(getFormattedHelp())
                return
            }

            if (formName.isBlank() || !FeedbackManager.isValidFormName(formName)){
                println("You should choose an existing form: ${FeedbackManager.readFeedbackFromList().map { it.formName }}")
                return
            }

            val header = FormManager.getForm(formName)
            val allFeedbackResponses = FeedbackManager.readFeedbackHistory(assignment = assigned, formName = formName)
            val userResponses = allFeedbackResponses.filter { it.author == user.name }

            if (userResponses.isEmpty()) {
                println("no evaluations found")
                return
            }
            val supplement = if (assigned) "assigned by administrators" else "requested by students"
            println("filtered evaluations for chat rooms $supplement:")
            EvaluationCommand().printEvaluationPerUser(header, userResponses, output, true, formName)
        }
    }

    inner class ForRating : CliktCommand(name = "for", help = "Lists ratings received by a user") {
        private val username: String by argument()
        private val output: String? by option("-o", "--output")
        private val assigned: Boolean by option("--assigned",
            help = "Flag to list ratings only for chat rooms assigned by administrators.").flag()
        private val requested: Boolean by option("--requested",
            help = "Flag to list ratings only for chat rooms requested by students.").flag()
        private val formName: String by option(
            "-f", "--form",
            help = "Which form to review: ${FeedbackManager.readFeedbackFromList().map { it.formName }}").required()


        override fun run() {
            val user = UserManager.list().find { it.name == username }
            if (user == null) {
                println("no user found")
                return
            }
            if ((assigned && requested) || (!assigned && !requested)) {
                println("Assigned or requested flag required")
                println(getFormattedHelp())
                return
            }
            if (formName.isBlank() || !FeedbackManager.isValidFormName(formName)){
                println("You should choose an existing form: ${FeedbackManager.readFeedbackFromList().map { it.formName }}")
                return
            }

            val header = FormManager.getForm(formName)
            val allFeedbackResponses = FeedbackManager.readFeedbackHistory(assignment = assigned, formName = formName)
            val userResponses = allFeedbackResponses.filter { it.recipient == user.name }

            if (userResponses.isEmpty()) {
                println("no evaluations found")
                return
            }

            val supplement = if (assigned) "assigned by administrators" else "requested by students"
            println("filtered evaluations for chat rooms $supplement:")
            EvaluationCommand().printEvaluationPerUser(header, userResponses, output, false, formName)
        }
    }

    inner class AllRatings : CliktCommand(name = "all", help = "Lists rating averages per author or recipient") {

        private val author: Boolean by option("-a", "--author", help = "Flag to list averages for all authors").flag()
        private val recipient: Boolean by option("-r", "--recipient", help = "Flag to list averages for all recipients").flag()
        private val assigned: Boolean by option("--assigned",
            help = "Flag to list ratings only for chat rooms assigned by administrators.").flag()
        private val requested: Boolean by option("--requested",
            help = "Flag to list ratings only for chat rooms requested by students.").flag()
        private val output: String? by option("-o", "--output")
        private val formName: String by option(
            "-f", "--form",
            help = "Which form to review: ${FeedbackManager.readFeedbackFromList().map { it.formName }}").required()

        override fun run() {

            if ((author && recipient) || (!author && !recipient)) {
                println("Author or recipient flag required")
                println(getFormattedHelp())
                return
            }
            if ((assigned && requested) || (!assigned && !requested)) {
                println("Assigned or requested flag required")
                println(getFormattedHelp())
                return
            }
            if (formName.isBlank() || !FeedbackManager.isValidFormName(formName)){
                println("You should choose an existing form: ${FeedbackManager.readFeedbackFromList().map { it.formName }}")
                return
            }

            val header = FormManager.getForm(formName)
            val responsesPerUser = FeedbackManager.aggregateFeedbackStatisticsPerUser(
                author = author,
                assignment = assigned,
                formName = formName
            )

            val supplement = if (assigned) "assigned by administrators" else "requested by students"
            println("filtered evaluations for chat rooms $supplement:")
            EvaluationCommand().printAllEvaluations(header, responsesPerUser, output, author = author, formName)
        }
    }

    inner class Summary : CliktCommand(name = "summary", help = "Lists rating averages over all evaluations") {

        private val output: String? by option("-o", "--output")
        private val assigned: Boolean by option("--assigned",
            help = "Flag to list ratings only for chat rooms assigned by administrators.").flag()
        private val requested: Boolean by option("--requested",
            help = "Flag to list ratings only for chat rooms requested by students.").flag()
        private val formName: String by option(
            "-f", "--form",
            help = "Which form to review: ${FeedbackManager.readFeedbackFromList().map { it.formName }}").required()

        override fun run() {
            if ((assigned && requested) || (!assigned && !requested)) {
                println("Assigned or requested flag required")
                println(getFormattedHelp())
                return
            }
            if (formName.isBlank() || !FeedbackManager.isValidFormName(formName)){
                println("You should choose an existing form: ${FeedbackManager.readFeedbackFromList().map { it.formName }}")
                return
            }
            val header = FormManager.getForm(formName)
            val allFeedbackResponses = FeedbackManager.readFeedbackHistory(assignment = assigned, formName = formName)

            val supplement = if (assigned) "assigned by administrators" else "requested by students"
            println("filtered evaluations for chat rooms $supplement:")
            EvaluationCommand().printSummary(header, allFeedbackResponses, output, formName)
        }
    }
}