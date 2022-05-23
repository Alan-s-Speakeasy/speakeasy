package ch.ddis.speakeasy.cli.commands

import ch.ddis.speakeasy.api.handlers.FeedbackRequestList
import ch.ddis.speakeasy.api.handlers.FeedbackResponse
import ch.ddis.speakeasy.feedback.FeedbackManager
import ch.ddis.speakeasy.user.UserManager
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.jakewharton.picnic.table

class EvaluationCommand : NoOpCliktCommand(name = "evaluation") {

    init {
        this.subcommands(
            FromRating(),
            ForRating(),
            AllRatings()
        )
    }

    private fun getFeedbackNameForValue(requests: FeedbackRequestList, id: String, value: String): String {
        requests.requests.forEach { if (it.id == id) {
            it.options.forEach { o -> if (o.value.toString() == value) {return o.name} }
        } }
        return if (value == "0") {
            "---"
        } else {
            value
        }
    }

    private fun average(responses: HashMap<Pair<String, String>, MutableList<FeedbackResponse>>, requests: FeedbackRequestList) : MutableMap<String, Int> {
        val averages = requests.requests.map { it.id to 0 }.toMap(mutableMapOf())
        val count = requests.requests.map { it.id to 0 }.toMap(mutableMapOf())

        responses.values.forEach {
            it.forEach { fr ->
                val value = fr.value.toIntOrNull() ?: 0
                averages[fr.id] = value + averages[fr.id]!!
                if (value != 0) {
                    count[fr.id] = count[fr.id]!! + 1
                }
            }
        }

        averages.forEach {
            if (count[it.key]!! > 0) {
                averages[it.key] = it.value / count[it.key]!!
            }
        }
        return averages
    }

    fun printEvaluationPerUser(
        header: FeedbackRequestList,
        responses: HashMap<Pair<String, String>, MutableList<FeedbackResponse>>,
        author: Boolean
    ) {
        println(
            table {
                cellStyle {
                    border = true
                    paddingLeft = 1
                    paddingRight = 1
                }
                header {
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
                            cell(response.key.first)
                            response.value.forEach {
                                cell(getFeedbackNameForValue(header, it.id, it.value))
                            }
                        }
                    }
                    row {
                        cell("AVERAGE")
                        average(responses, header).forEach {
                            cell(getFeedbackNameForValue(header, it.key, it.value.toString()))
                        }
                    }
                }
            }
        )
    }

    fun printAllEvaluations(
        header: FeedbackRequestList,
        allResponses: HashMap<String, HashMap<Pair<String, String>, MutableList<FeedbackResponse>>>,
    ) {
        println(
            table {
                cellStyle {
                    border = true
                    paddingLeft = 1
                    paddingRight = 1
                }
                header {
                    row {
                        cell("Author")
                        header.requests.forEach {
                            cell(it.shortname)
                        }
                    }
                }
                body {
                    allResponses.forEach { response ->
                        row {
                            cell(response.key)
                            average(response.value, header).forEach {
                                cell(getFeedbackNameForValue(header, it.key, it.value.toString()))
                            }
                        }
                    }
                }
            }
        )
    }

    inner class FromRating : CliktCommand(name = "from", help = "Lists ratings made by a user") {
        private val username by argument()

        override fun run() {

            val user = UserManager.list().find { it.name == username }
            if (user == null) {
                println("no user found")
                return
            }

            val header = FeedbackManager.readFeedbackRequests()
            val responses = FeedbackManager.readFeedbackHistoryPerUser(user.id, true)

            if (responses.isEmpty()) {
                println("no evaluations found")
                return
            }

            EvaluationCommand().printEvaluationPerUser(header, responses, true)
        }
    }

    inner class ForRating : CliktCommand(name = "for", help = "Lists ratings received by a user") {
        private val username by argument()

        override fun run() {

            val user = UserManager.list().find { it.name == username }
            if (user == null) {
                println("no user found")
                return
            }

            val header = FeedbackManager.readFeedbackRequests()
            val responses = FeedbackManager.readFeedbackHistoryPerUser(user.id, false)

            if (responses.isEmpty()) {
                println("no evaluations found")
                return
            }

            EvaluationCommand().printEvaluationPerUser(header, responses, false)
        }
    }

    inner class AllRatings : CliktCommand(name = "all", help = "Lists rating averages for all authors") {

        override fun run() {
            val users = UserManager.list()
            val header = FeedbackManager.readFeedbackRequests()

            val allResponses = hashMapOf<String, HashMap<Pair<String, String>, MutableList<FeedbackResponse>>>()
            users.forEach {
                val responses = FeedbackManager.readFeedbackHistoryPerUser(it.id, true)
                if (responses.isNotEmpty()) {
                    allResponses[it.name] = responses
                }
            }
            EvaluationCommand().printAllEvaluations(header, allResponses)
        }
    }
}