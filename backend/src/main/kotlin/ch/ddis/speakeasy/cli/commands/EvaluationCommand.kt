package ch.ddis.speakeasy.cli.commands

import ch.ddis.speakeasy.api.handlers.FeedbackRequestList
import ch.ddis.speakeasy.api.handlers.FeedbackResponseAverageItem
import ch.ddis.speakeasy.api.handlers.FeedbackResponseItem
import ch.ddis.speakeasy.feedback.FeedbackManager
import ch.ddis.speakeasy.user.UserManager
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
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

    private fun getFeedbackNameForValue(requests: FeedbackRequestList, id: String, value: String, csv: Boolean = false): String {
        requests.requests.forEach { if (it.id == id) {
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
        header: FeedbackRequestList,
        responses: List<FeedbackResponseItem>,
        output: String?,
        author: Boolean
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
                            FeedbackManager.computeFeedbackAverage(responses.flatMap { it.responses }).forEach {
                                cell(getFeedbackNameForValue(header, it.id, it.value))
                            }
                        }
                    }
                }
            )
        }
    }

    fun printAllEvaluations(
        header: FeedbackRequestList,
        allResponses: List<FeedbackResponseAverageItem>,
        output: String?,
        author: Boolean
    ) {
        if (output != null) {
            val fileWriter = createOutputFile(output)
            val firstHeader = if (author) "Author" else "Recipient"
            fileWriter.println(firstHeader + ",count," + header.requests.joinToString(",") { it.shortname })
            allResponses.forEach { response ->
                val parts: MutableList<String> = mutableListOf()
                parts.add(response.username)
                parts.add(response.count.toString())
                FeedbackManager.computeFeedbackAverage(response.responses).forEach {
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
                                FeedbackManager.computeFeedbackAverage(response.responses).forEach {
                                    cell(getFeedbackNameForValue(header, it.id, it.value))
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    fun printSummary(
        header: FeedbackRequestList,
        responses: MutableList<FeedbackResponseItem>,
        output: String?
    ) {
        if (output != null) {
            val fileWriter = createOutputFile(output)
            fileWriter.println(header.requests.joinToString(",") { it.shortname })
            val parts: MutableList<String> = mutableListOf()
            FeedbackManager.computeFeedbackAverage(responses.flatMap { it.responses }).forEach {
                parts.add(getFeedbackNameForValue(header, it.id, it.value, csv = true))
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
                            FeedbackManager.computeFeedbackAverage(responses.flatMap { it.responses }).forEach {
                                cell(getFeedbackNameForValue(header, it.id, it.value))
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

        override fun run() {
            val user = UserManager.list().find { it.name == username }
            if (user == null) {
                println("no user found")
                return
            }
            val header = FeedbackManager.readFeedbackRequests()
            val allFeedbackResponses = FeedbackManager.readFeedbackHistory()
            val userResponses = allFeedbackResponses.filter { it.author == user.name }

            if (userResponses.isEmpty()) {
                println("no evaluations found")
                return
            }
            EvaluationCommand().printEvaluationPerUser(header, userResponses, output, true)
        }
    }

    inner class ForRating : CliktCommand(name = "for", help = "Lists ratings received by a user") {
        private val username: String by argument()
        private val output: String? by option("-o", "--output")

        override fun run() {
            val user = UserManager.list().find { it.name == username }
            if (user == null) {
                println("no user found")
                return
            }
            val header = FeedbackManager.readFeedbackRequests()
            val allFeedbackResponses = FeedbackManager.readFeedbackHistory()
            val userResponses = allFeedbackResponses.filter { it.recipient == user.name }

            if (userResponses.isEmpty()) {
                println("no evaluations found")
                return
            }
            EvaluationCommand().printEvaluationPerUser(header, userResponses, output, false)
        }
    }

    inner class AllRatings : CliktCommand(name = "all", help = "Lists rating averages per author or recipient") {

        private val author: Boolean by option("-a", "--author").flag()
        private val recipient: Boolean by option("-r", "--recipient").flag()
        private val output: String? by option("-o", "--output")

        override fun run() {

            if ((author && recipient) || (!author && !recipient)) {
                println("please specify either author or recipient")
                println(getFormattedHelp())
                return
            }

            val header = FeedbackManager.readFeedbackRequests()
            val responsesPerUser = FeedbackManager.readFeedbackHistoryPerUser(author)

            EvaluationCommand().printAllEvaluations(header, responsesPerUser, output, author = author)
        }
    }

    inner class Summary : CliktCommand(name = "summary", help = "Lists rating averages over all evaluations") {

        private val output: String? by option("-o", "--output")

        override fun run() {
            val header = FeedbackManager.readFeedbackRequests()
            val allFeedbackResponses = FeedbackManager.readFeedbackHistory()
            EvaluationCommand().printSummary(header, allFeedbackResponses, output)
        }
    }
}