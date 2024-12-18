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
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.util.MalformedCSVException
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.concurrent.locks.StampedLock

object FeedbackManager {
    private var sessionWriters: HashMap<String, PrintWriter> = hashMapOf() // formName -> feedback PrintWriter

    private lateinit var formsPath: File

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
            .filter { it.isFile }
            .forEach { file ->
                val feedbackForm: FeedbackForm = kMapper.readValue(file)
                if (this.forms.none{ it.formName == feedbackForm.formName }) {
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
                    Charsets.UTF_8)
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
        return forms.find { it.formName ==  formName}!!  // throw NullPointerException
    }

    fun isValidFormName(formName: String): Boolean {
        if (formName == "") { return true }
        return this.forms.find { it.formName ==  formName} != null
    }

    fun readFeedbackFromList(): MutableList<FeedbackForm> = forms

    fun logFeedback(userSession: UserSession, roomId: UID, feedbackResponseList: FeedbackResponseList): Unit =
        writerLock.write {
            val formName = ChatRoomManager.getFeedbackFormReference(roomId) ?: return
            val partnerId = ChatRoomManager.getChatPartner(roomId, userSession.user.id.UID()) ?: UserId("undefined")
            for (response in feedbackResponseList.responses) {
                val value = response.value.replace("\"", "\"\"")
                sessionWriters[formName]?.println("${System.currentTimeMillis()},${userSession.user.id.toString()},${userSession.sessionId.string},\"${roomId.string}\",${partnerId.string},${response.id},\"${value}\"")
            }
            sessionWriters[formName]?.flush()
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

    fun readFeedbackHistory(assignment: Boolean = false, formName: String): MutableList<FeedbackResponseItem> = this.lock.read {
        var response: FeedbackResponse
        val responseMap: HashMap<Triple<String, String, String>, MutableList<FeedbackResponse>> = hashMapOf()
        val responseList: MutableList<FeedbackResponseItem> = mutableListOf()

        if (this.feedbackFiles[formName] == null) { return responseList } // no such form -> return empty list

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

        return responseList
    }

    /**
     * Do the equivalent of this SQL query:
     * ```sql
     * SELECT recipient, COUNT(*), AVG(responseValue) FROM feedback WHERE author = author GROUP BY recipient
     *```
     * In other word, it gets all feedback entries the author filled in a chatrom with any other user, compute its average
     * and count and return the average feedback for each other user.
     *
     * @param author The author of the feedback
     * @param assignment If true, only return feedback that were filled in an assignment chatroom
     * @param formName The name of the feedback form
     *
     * @return List of FeedbackResponseAverageItem with the average feedback for each user, as stated above.
     */
    fun readFeedbackHistoryPerUser(author: Boolean, assignment: Boolean = false, formName: String): List<FeedbackResponseAverageItem> {
        val allFeedbackResponses = readFeedbackHistory(assignment = assignment, formName = formName)
        val responsesPerUser: HashMap<String, MutableList<FeedbackResponse>> = hashMapOf()
        val feedbackCountPerUser: HashMap<String, Int> = hashMapOf()

        allFeedbackResponses.forEach {
            val key = if (author) it.author else it.recipient
            if (!responsesPerUser.containsKey(key)) {
                responsesPerUser[key] = mutableListOf()
                feedbackCountPerUser[key] = 1
            }
            else {
                feedbackCountPerUser[key] = feedbackCountPerUser[key]!! + 1
            }
            it.responses.forEach { fr -> responsesPerUser[key]?.add(fr) }
        }
        return responsesPerUser.map {
            FeedbackResponseAverageItem(it.key, feedbackCountPerUser[it.key] ?: 0, computeFeedbackAverage(it.value, formName))
        }
    }

    /**
     * Given a list of feedback responses from a given formName, compute the average for each response id.
     *
     * @param responses List of feedback responses
     * @param formName Name of the feedback form
     *
     * @return List of feedback responses with the average value for each response id
     */
    fun computeFeedbackAverage(responses: List<FeedbackResponse>, formName: String): List<FeedbackResponse> {
        val requests = this.forms.find { it.formName == formName }!!.requests
        val averages = requests.associateTo(mutableMapOf()) { it.id to 0 }
        val count = requests.associateTo(mutableMapOf()) { it.id to 0 }

        responses.forEach { fr ->
            val value = fr.value.toIntOrNull() ?: 0
            averages[fr.id] = value + averages[fr.id]!!
            if (value != 0) {
                count[fr.id] = count[fr.id]!! + 1
            }
        }

        averages.forEach {
            if (count[it.key]!! > 0) {
                averages[it.key] = it.value / count[it.key]!!
            }
        }
        return averages.map { FeedbackResponse(it.key, it.value.toString()) }
    }
}