package ch.ddis.speakeasy.feedback

import ch.ddis.speakeasy.api.handlers.*
import ch.ddis.speakeasy.chat.ChatRoomId
import ch.ddis.speakeasy.db.*
import ch.ddis.speakeasy.util.Config
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.read
import ch.ddis.speakeasy.util.write
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jetbrains.exposed.sql.*
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
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

    private val FeedbackHistoryCacher = FeedbackHistoryCacher()

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
    fun logFeedback(author: UserEntity, roomId: ChatRoomId, feedbackResponseList: FeedbackResponseList) =
        // The recipient of the feedback
        writerLock.write {
            val recipientId = ChatRepository.getParticipants(roomId).single { it != author.id.UID() }
            val formId = ChatRepository.getFormForChatRoom(roomId)
                ?: throw IllegalArgumentException("Chatroom does not have any form attached !")
            if (ChatRepository.isRoomAlreadyAssessed(roomId, formId, author.id.UID())) {
                throw IllegalArgumentException("Room alreadu assessed!")
            }
            for (response in feedbackResponseList.responses) {
                FeedbackRepository.saveFeedbackResponse(author.id.UID(), recipientId, roomId, formId, response)
            }
            this.FeedbackHistoryCacher.invalidate()
        }

    fun readFeedbackHistoryPerRoom(userId: UserId, roomId: UID): FeedbackResponseList = this.lock.read {
        return FeedbackResponseList(responses = ChatRepository.getFeedbackResponseForRoom(roomId).toMutableList())
    }

    /**
     * Givem a set of authors, return the set of feedback responses those authors provided.
     *
     * @param authorIds The list of userIDs to get the feedback for. If empty, get all the feedback.
     * @param assignment If true, only return feedback that were filled in an assignment chatroom
     * @param formName The name of the feedback form
     *
     * @return List of FeedbackResponseItem with the feedback responses.
     */
    fun readFeedbackHistory(
        authorIds: Set<UserId> = emptySet(),
        assignment: Boolean = false,
        formName: String
    ): MutableList<FeedbackResponseOfChatroom> = this.lock.read {
        var response: FeedbackResponse
        val responseMap: HashMap<Triple<String, String, String>, MutableList<FeedbackResponse>> = hashMapOf()
        val responseList: MutableList<FeedbackResponseOfChatroom> = mutableListOf()
        if (!FormManager.isValidFormName(formName)) {
            throw IllegalStateException("Invalid form name")
        }


        val formId = FeedbackRepository.findFormByName(formName)?: throw IllegalArgumentException("Form name not found")
        if (authorIds.isEmpty()){
            TODO("Investigate if this is needed. We should avoid loading all the chatrooms in the memory as much " +
                    "as possible. ")
        }

        return authorIds.map {
            UserRepository.getFeedbackResponsesOfUser(it, formId, assignment)
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
        val allFeedbackResponses = readFeedbackHistory(authorIds = userIds, assignment = assignment, formName = formName)
        val responsesPerUser: HashMap<UserId, MutableList<FeedbackResponse>> = hashMapOf()
        val feedbackCountPerUser: HashMap<UserId, Int> = hashMapOf()

        allFeedbackResponses.forEach {
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
        return responsesPerUser.map { (userId, responses) ->
            FeedbackResponseStatsItem(
                UserRepository.getUsernameFromId(userId)?:"",
                feedbackCountPerUser[userId] ?: 0,
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
        return computeStatsPerRequestOfFeedback(allFeedbackResponses.flatMap { it.responses }, formName)
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
        val requests = this.forms.find { it.formName == formName }!!.requests
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

/**
 * A very simple cache/memoization system for the feedback history.
 *
 * This is to "hide" the painly slow reading of the CSV files, and should be removed when a database is used.
 *
 * NOTE : There is no eviction policy, so the cache can grow indefinitely. This is mitigated by only caching the
 * results for empty userIDs, which is the most common use case. Therefore, the cache size should be at maximum the number of different formNames * 2.
 * NOTE : This is NOT thread safe, but it is solely used with the write/read locks of the FeedbackManager
 */
@Deprecated("We use a database now !")
class FeedbackHistoryCacher {
    private val cache = ConcurrentHashMap<String, Pair<Long, MutableList<FeedbackResponseOfChatroom>>>()
    private val TTLCACHE_ms = 1000L * 60 * 10 // 10 minutes

    /**
     * Retrieves the cached feedback responses for the specified parameters.
     *
     * If the cache entry exists and is still within its TTL, it returns the cached data.
     * Otherwise, the expired entry is removed and an empty list is returned.
     *
     * @param userIDs the set of user IDs used to generate the cache key (default is an empty set).
     * @param assignment a flag indicating whether the feedback is for an assignment chat room (default is false).
     * @param formName the name of the feedback form.
     * @return the list of cached [FeedbackResponseOfChatroom] if valid; otherwise, an empty list.
     */
    fun get(
        userIDs: Set<UserId> = emptySet(),
        assignment: Boolean = false,
        formName: String
    ): MutableList<FeedbackResponseOfChatroom> {
        val key = "${userIDs.hashCode()}-$assignment-$formName"
        return cache[key]?.let { (timestamp, data) ->
            if (System.currentTimeMillis() - timestamp < TTLCACHE_ms) {
                data
            } else {
                cache.remove(key)
                mutableListOf()
            }
        } ?: mutableListOf()
    }

    /**
     * Checks if there is a valid (non-expired) cache entry for the specified parameters.
     *
     * If the cache entry exists and has not expired, the method returns true.
     * Otherwise, it returns false and removes the expired entry if present.
     *
     * @param userIDs the set of user IDs used to generate the cache key (default is an empty set).
     * @param assignment a flag indicating whether the feedback is for an assignment chat room (default is false).
     * @param formName the name of the feedback form.
     * @return true if a valid cache entry exists; false otherwise.
     */
    fun isHit(userIDs: Set<UserId> = emptySet(), assignment: Boolean = false, formName: String): Boolean {
        val key = "${userIDs.hashCode()}-$assignment-$formName"
        return cache[key]?.let { (timestamp, _) ->
            if (System.currentTimeMillis() - timestamp < TTLCACHE_ms) {
                true
            } else {
                cache.remove(key)
                false
            }
        } ?: false
    }

    /**
     * Caches the provided list of feedback responses for the specified parameters.
     *
     * The current system time is recorded to enforce the TTL.
     *
     * @param userIDs the set of user IDs used to generate the cache key (default is an empty set).
     * @param assignment a flag indicating whether the feedback is for an assignment chat room (default is false).
     * @param formName the name of the feedback form.
     * @param feedback the list of [FeedbackResponseOfChatroom] to be cached.
     */
    fun cache(
        userIDs: Set<UserId> = emptySet(),
        assignment: Boolean = false,
        formName: String,
        feedback: MutableList<FeedbackResponseOfChatroom>
    ) {
        val key = "${userIDs.hashCode()}-$assignment-$formName"
        cache[key] = System.currentTimeMillis() to feedback
    }

    /**
     * Invalidates the whole cache.
     */
    fun invalidate() {
        cache.clear()
    }

}

