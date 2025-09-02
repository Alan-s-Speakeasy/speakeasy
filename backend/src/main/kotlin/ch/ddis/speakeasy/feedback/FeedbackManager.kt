package ch.ddis.speakeasy.feedback

import ch.ddis.speakeasy.api.handlers.*
import ch.ddis.speakeasy.chat.ChatRoomId
import ch.ddis.speakeasy.db.*
import ch.ddis.speakeasy.db.UserId
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jetbrains.exposed.sql.*
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.concurrent.locks.StampedLock

object FeedbackManager {
    private var sessionWriters: HashMap<String, PrintWriter> = hashMapOf() // formName -> feedback PrintWriter

    private lateinit var formsPath: File
    private lateinit var feedbackResultsPath: File // Added for storing JSON results

    private var feedbackFiles: HashMap<String, File> = hashMapOf() // formName -> feedback results

    private val kMapper: ObjectMapper = jacksonObjectMapper()

    private var forms: MutableList<FeedbackForm> = mutableListOf()

    lateinit var DEFAULT_FORM_NAME: String // Use the name of the first form as the default value (or "").

    private val lock: StampedLock = StampedLock()


    fun init(config: Config) {

        // INIT Reading Feedback Forms
        this.formsPath = File(File(config.dataPath), "feedbackforms/")
        this.formsPath
            .walk()
            .filter { it.isFile && it.extension == "json" }
            .forEach { file ->
                println("Reading feedback form from ${file.name}")
                val feedbackForm: FeedbackForm = kMapper.readValue(file)
                if (this.forms.none { it.formName == feedbackForm.formName }) {
                    this.forms.add(feedbackForm)
                } else {
                    System.err.println("formNames in feedbackforms should be unique  -> ignored duplicates ${feedbackForm.formName}.")
                }
            }
        this.forms.sortBy { it.formName } // Ensure that after each initialization, the forms are sorted in ascending order by formName

        // Migrate forms to database
        DatabaseHandler.dbQuery {
            forms.forEach { form ->
                // Check if form already exists in database
                val existingForm = FeedbackForms.selectAll().where { FeedbackForms.formName eq form.formName }
                    .singleOrNull()

                if (existingForm == null) {
                    // Insert the form into the database
                    FeedbackForms.insert {
                        it[formName] = form.formName
                        it[fileName] = "${form.formName}.json" // Using the original filename
                    }
                    println("Migrated feedback form '${form.formName}' to database")
                }
            }
        }

        this.DEFAULT_FORM_NAME = this.forms.firstOrNull()?.formName ?: run {
            System.err.println("Not found any feedback forms when init.")
            return@run ""
        }

        val baseFolder = File(File(config.dataPath), "feedbackresults")
        baseFolder.mkdirs()

        // Initialize feedbackResultsPath
        this.feedbackResultsPath = baseFolder

        // INIT Writing Feedback Responses
        this.forms.forEach {
            val file = File(baseFolder, "${it.formName}.csv")
            this.feedbackFiles[it.formName] = file
            if (!file.exists() || file.length() == 0L) {
                file.writeText(
                    "timestamp,user,session,room,partner,responseid,responsevalue\n",
                    Charsets.UTF_8
                )
            }
            this.sessionWriters[it.formName] = PrintWriter(
                FileWriter(
                    this.feedbackFiles[it.formName],
                    Charsets.UTF_8,
                    true
                ),
                true
            )
        }
    }


    @Deprecated("Useless")
    private val writerLock = StampedLock()

    /**
     * Write the feedback responses to the CSV file and database.
     * Return if the feedback form is not found.
     *
     * @throws IllegalArgumentException if the chatroom is already assessed
     * @throws IllegalArgumentException if the chatroom does not have any form attached.
     */
    fun logFeedback(authorId: UserId, roomId: ChatRoomId, feedbackResponseList: FeedbackResponseList) =
        // The recipient of the feedback
        writerLock.write {
            // NOTE : As of now, we assume that the only case where there is more than 2 participants in a chatroom with
            // a special bot.
            val recipientId = ChatRepository.getParticipants(roomId).singleOrNull { it != authorId && UserManager.getUserRoleByUserID(it) == UserRole.BOT }
                ?: throw IllegalArgumentException("Giving feedback to a room with more than 2 bots is not supported !")
            val formId = ChatRepository.getFormForChatRoom(roomId)
                ?: throw IllegalArgumentException("Chatroom does not have any form attached !")
            if (ChatRepository.isRoomAlreadyAssessed(roomId, formId, authorId)) {
                throw IllegalArgumentException("Room already assessed!")
            }
            for (response in feedbackResponseList.responses) {
                // Should use batch write instead
                FeedbackRepository.saveFeedbackResponse(authorId, recipientId, roomId, formId, response)
            }
        }

    /**
     * Reads the feedback history for a specific user in a specific room.
     *
     * Only the feedback responses of the said user and of that room will be returned.
     */
    fun readFeedbackHistoryPerRoom(userId: UserId, roomId: UID): FeedbackResponseList = this.lock.read {
        return FeedbackResponseList(responses = FeedbackRepository.getFeedbackResponseForRoom(roomId, userId).toMutableList())
    }

    /**
     * Givem a set of authors, return the set of feedback responses those authors provided.
     *
     * @param authorIds The list of userIDs to get the feedback for. If empty, get all the feedback.
     * @param assignment If true, only return feedback that were filled in an assignment chatroom
     * @param formName The name of the feedback form
     *
     * @return List of FeedbackResponseItem with the feedback responses. Null if user did not provide any feedback.
     */
    fun readFeedbackHistory(
        authorIds: Set<UserId> = emptySet(),
        assignment: Boolean = false,
        formName: String
    ): MutableList<FeedbackResponseOfChatroom?> = this.lock.read {
        if (!FormManager.isValidFormName(formName)) {
            throw FormNotFoundException("Invalid form name")
        }
        val formId = FormManager.getFormIdByName(formName)
        if (authorIds.isEmpty()) {
            return FeedbackRepository.getAllFeedbackResponsesOfForm(
                formId,
                assignment = assignment
            ).toMutableList()
        }

        return authorIds.flatMap { userId ->
            FeedbackRepository.getFeedbackResponsesOfUser(userId, formId, assignment)
        }.toMutableList()

    }


    /**
     * Do the equivalent of this SQL query:
     * ```sql
     * SELECT recipient, question_id, COUNT(*) AS total_feedbacks, AVG(value) AS average_value
     * FROM feedback WHERE author = 'author'
     * GROUP BY recipient, question_id
     *```
     * Optionally, one can filter the statistics by selecting the usernames to get the statistics for. If empty, get all the statistics.
     *
     * In other word, it gets all feedback entries the author filled in a chatroom with any other user, compute its average
     * and count and return the average for each question (=request) of the said feedback.
     *
     * @param userIds The list of usernames to get the statistics for. If empty, get all the statistics.
     * @param author The author of the feedback
     * @param assignment If true, only return feedback that were filled in an assignment chatroom
     * @param formName The name of the feedback form
     *
     * @return List of FeedbackResponseAverageItem with the average feedback for each user, as stated above.
     */
    fun aggregateFeedbackStatisticsPerUser(
        userIds: Set<UserId> = emptySet<UserId>(),
        author: Boolean,
        assignment: Boolean = false,
        formName: String
    ): List<FeedbackResponseStatsItem> {
        val allFeedbackResponses =
            readFeedbackHistory(authorIds = userIds, assignment = assignment, formName = formName)
        val responsesPerUser: HashMap<String, MutableList<FeedbackResponse>> = hashMapOf()
        val feedbackCountPerUser: HashMap<String, Int> = hashMapOf()

        allFeedbackResponses.filterNotNull().forEach {
            val key = if (author) it.author else it.recipient
            if (!responsesPerUser.containsKey(key)) {
                responsesPerUser[key] = mutableListOf()
                feedbackCountPerUser[key] = 1
            } else {
                feedbackCountPerUser[key] = feedbackCountPerUser[key]!! + 1
            }
            it.responses.forEach { fr -> responsesPerUser[key]?.add(fr) }
        }
        // get the list of _all_ feedback responses and compute the average and variance from that.
        return responsesPerUser.map { (userName, responses) ->
            FeedbackResponseStatsItem(
                userName,
                feedbackCountPerUser[userName] ?: 0,
                computeStatsPerRequestOfFeedback(responses, formName)
            )
        }
    }

    /**
     * Compute statistics of a whole feedback request, or question. In other words, compute the average and variance across all the
     * feedback responses of each request (question) of the feedback form.
     *
     * @param formName The name of the feedback form
     *
     * @return List of FeedBackStatsOfRequest with the average and variance of each request.
     */
    fun aggregateFeedbackStatisticsGlobal(formName: String): List<FeedBackStatsOfRequest> {
        val allFeedbackResponses = readFeedbackHistory(formName = formName)
        return computeStatsPerRequestOfFeedback(allFeedbackResponses.filterNotNull().flatMap { it.responses }, formName)
    }

    /**
     * Given a list of feedback responses from a given formName, compute the average and variance of all the responses
     * for each request (question) of the feedback form.
     *
     * Ignore any request (question) that is a textual question.
     *
     * @param responses List of feedback responses
     * @param formName Name of the feedback form
     *
     * @return List of feedback responses with the average value for each response id. The average value is a float stringed.
     */
    fun computeStatsPerRequestOfFeedback(
        responses: List<FeedbackResponse>,
        formName: String
    ): List<FeedBackStatsOfRequest> {
        // Get the "questions" of the form (called requests here)
        val requests = FormManager.getFormByName(formName).requests
        val averagesPerRequest = requests.associateTo(mutableMapOf()) { it.id to 0f }
        val variancesPerRequest = requests.associateTo(mutableMapOf()) { it.id to 0f }
        val countPerRequest = requests.associateTo(mutableMapOf()) { it.id to 0 }

        // One pass variance and average computation
        for (request in requests) {
            if (request.options.isEmpty()) {
                // Special case : This is a textual question
                continue
            }
            val responsesOfRequest = responses.filter { it.id == request.id && it.value.isNotBlank() }
            val n = responsesOfRequest.size;
            var S1 = 0f;
            var S2 = 0f;
            for (response in responsesOfRequest) {
                S1 += response.value.toFloat()
                S2 += response.value.toFloat() * response.value.toFloat()
            }
            averagesPerRequest[request.id] = S1 / n
            variancesPerRequest[request.id] = (S2 - S1 * S1 / n) / (n - 1)
            countPerRequest[request.id] = n
        }
        return requests.map {
            FeedBackStatsOfRequest(
                it.id,
                averagesPerRequest[it.id]!!.toString(),
                variance = variancesPerRequest[it.id]!!,
                count = countPerRequest[it.id]!!
            )
        }
    }
}

