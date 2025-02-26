package ch.ddis.speakeasy.feedback

import ch.ddis.speakeasy.api.handlers.*
import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.user.UserId
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserSession
import ch.ddis.speakeasy.util.Config
import ch.ddis.speakeasy.util.UID
import ch.ddis.speakeasy.util.read
import ch.ddis.speakeasy.util.write
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.util.MalformedCSVException
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.StampedLock

object FeedbackManager {
    private var sessionWriters: HashMap<String, PrintWriter> = hashMapOf() // formName -> feedback PrintWriter

    private lateinit var formsPath: File

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

        this.DEFAULT_FORM_NAME = this.forms.firstOrNull()?.formName ?: run {
            System.err.println("Not found any feedback forms when init.")
            return@run ""
        }

        val baseFolder = File(File(config.dataPath), "feedbackresults")
        baseFolder.mkdirs()

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


    private val writerLock = StampedLock()

    /**
     * Returns a matching FeedbackForm object for the given formName.
     *
     * @throws NullPointerException if the formName is not found.
     */
    fun readFeedbackFrom(formName: String): FeedbackForm {
        return forms.find { it.formName == formName }!!  // throw NullPointerException
    }

    fun isValidFormName(formName: String): Boolean {
        if (formName == "") {
            return true
        }
        return this.forms.find { it.formName == formName } != null
    }

    fun readFeedbackFromList(): MutableList<FeedbackForm> = forms

    /**
     * Write the feedback responses to the CSV file.
     *
     * @param userSession The user session that filled the feedback form.
     * @param roomId The room ID where the feedback was filled.
     * @param feedbackResponseList The list of feedback responses.
     */
    fun logFeedback(userSession: UserSession, roomId: UID, feedbackResponseList: FeedbackResponseList): Unit =
        writerLock.write {
            val formName = ChatRoomManager.getFeedbackFormReference(roomId) ?: return
            val partnerId = ChatRoomManager.getChatPartner(roomId, userSession.user.id.UID()) ?: UserId("undefined")
            for (response in feedbackResponseList.responses) {
                val value = response.value.replace("\"", "\"\"")
                sessionWriters[formName]?.println("${System.currentTimeMillis()},${userSession.user.id.toString()},${userSession.sessionId.string},\"${roomId.string}\",${partnerId.string},${response.id},\"${value}\"")
            }
            sessionWriters[formName]?.flush()
            this.FeedbackHistoryCacher.invalidate()
        }

    fun readFeedbackHistoryPerRoom(userId: UserId, roomId: UID): FeedbackResponseList = this.lock.read {
        var response: FeedbackResponse
        val responses: MutableList<FeedbackResponse> = mutableListOf()
        val formName = ChatRoomManager.getFeedbackFormReference(roomId) ?: return FeedbackResponseList(responses)

        //read all CSV lines with the given userid and roomid

        try {
            csvReader().open(this.feedbackFiles[formName]!!) {
                readAllWithHeaderAsSequence().forEach { row ->
                    //in file CSV file: timestamp,userid,sessionid,room,partnerid,responseid,responsevalue
                    val user = row["user"]
                    val room = row["room"]
                    val responseId = row["responseid"]
                    val responseValue = row["responsevalue"]

                    if ((user == userId.string) && (room == roomId.string) && (responseId != null) && (responseValue != null)) {
                        response = FeedbackResponse(responseId, responseValue)
                        responses.add(response)
                    }
                }
            }
        } catch (e: MalformedCSVException) {
            with(e) { printStackTrace() }
        } catch (e: NullPointerException) {
            System.err.println("$formName is NOT in feedback forms or results!")
        }

        return FeedbackResponseList(responses)
    }

    /**
     * Read all the feedback from a single CSV file.
     *
     * @param userIDs The list of userIDs to get the feedback for. If empty, get all the feedback.
     * @param assignment If true, only return feedback that were filled in an assignment chatroom
     * @param formName The name of the feedback form
     *
     * @return List of FeedbackResponseItem with the feedback responses.
     */
    fun readFeedbackHistory(userIDs: Set<UserId> = emptySet<UserId>(), assignment: Boolean = false, formName: String): MutableList<FeedbackResponseItem> = this.lock.read {

        var response: FeedbackResponse
        val responseMap: HashMap<Triple<String, String, String>, MutableList<FeedbackResponse>> = hashMapOf()
        val responseList: MutableList<FeedbackResponseItem> = mutableListOf()
        if (this.feedbackFiles[formName] == null) {
            return responseList
        } // no such form -> return empty list TODO : This should raise an exception


        // NOTE : Only cache the result for empty userIDs for now. This is because as of now,
        // there is no eviction policy for the cache, so the cache can grow theoretically indefinitely (in practice, it
        // still gets invalided).
        if (userIDs.isEmpty() && this.FeedbackHistoryCacher.isHit(userIDs, assignment, formName)) {
            return this.FeedbackHistoryCacher.get(userIDs, assignment, formName)
        }

        //read all CSV lines with the given userid

        try {
            csvReader().open(this.feedbackFiles[formName]!!) {
                readAllWithHeaderAsSequence().forEach { row ->
                    //in file CSV file: timestamp,userid,sessionid,room,partnerid,responseid,responsevalue
                    val user = row["user"]
                    val room = row["room"]
                    val partner = row["partner"]
                    val responseId = row["responseid"]
                    val responseValue = row["responsevalue"]
                    if ((room != null)
                        && ChatRoomManager.isAssignment(room.UID()) == assignment
                        && (user != null)
                        && (partner != null)
                        && (userIDs.isEmpty() || userIDs.contains(UserId(user)))
                        && (UserManager.checkUserIdExists(UserId(user)))
                        && (UserManager.checkUserIdExists(UserId(partner)))
                        && (responseId != null)
                        && (responseValue != null)) {
                        response = FeedbackResponse(responseId, responseValue)
                        val authorUsername = UserManager.getUsernameFromId(UserId(user)) ?: ""
                        val recipientUsername = UserManager.getUsernameFromId(UserId(partner)) ?: ""
                        if (!responseMap.containsKey(Triple(authorUsername, recipientUsername, room))) {
                            responseMap[Triple(authorUsername, recipientUsername, room)] = mutableListOf()
                        }
                        responseMap[Triple(authorUsername, recipientUsername, room)]?.add(response)
                    }
                }
            }
        } catch (e: MalformedCSVException) {
            with(e) { printStackTrace() }
        }

        responseMap.forEach { (triple, responses) ->
            var res = responses
            val requestsSize = this.forms.find { it.formName == formName }!!.requests.size
            if (responses.size > requestsSize) {
                res = responses.take(requestsSize) as MutableList<FeedbackResponse>
            }
            responseList.add(FeedbackResponseItem(triple.first, triple.second, triple.third, res))
        }

        // Cache the result, only for empty userIDs for now
        if (userIDs.isEmpty())
            this.FeedbackHistoryCacher.cache(userIDs, assignment, formName, responseList)

        return responseList
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
        val allFeedbackResponses = readFeedbackHistory(userIDs = userIds, assignment = assignment, formName = formName)
        val responsesPerUser: HashMap<String, MutableList<FeedbackResponse>> = hashMapOf()
        val feedbackCountPerUser: HashMap<String, Int> = hashMapOf()

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
        return responsesPerUser.map { (username, responses) ->
            FeedbackResponseStatsItem(
                username,
                feedbackCountPerUser[username] ?: 0,
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
class FeedbackHistoryCacher {
    private val cache = ConcurrentHashMap<String, Pair<Long, MutableList<FeedbackResponseItem>>>()
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
     * @return the list of cached [FeedbackResponseItem] if valid; otherwise, an empty list.
     */
    fun get(userIDs: Set<UserId> = emptySet(), assignment: Boolean = false, formName: String): MutableList<FeedbackResponseItem> {
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
     * @param feedback the list of [FeedbackResponseItem] to be cached.
     */
    fun cache(userIDs: Set<UserId> = emptySet(), assignment: Boolean = false, formName: String, feedback: MutableList<FeedbackResponseItem>) {
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