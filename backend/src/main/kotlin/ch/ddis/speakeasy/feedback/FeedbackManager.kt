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
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.util.MalformedCSVException
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.concurrent.locks.StampedLock

object FeedbackManager {

    private lateinit var sessionWriter: PrintWriter

    private lateinit var feedbackRequestsFile: File
    private lateinit var feedbackFile: File

    private val kMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())
    private lateinit var requests: FeedbackRequestList

    private val lock: StampedLock = StampedLock()

    fun init(config: Config) {

        // INIT Reading Feedback Requests

        this.feedbackRequestsFile = File(File(config.dataPath), "feedbackrequests.json")
        if (!this.feedbackRequestsFile.exists()) {
            return
        }

        this.requests = kMapper.readValue(this.feedbackRequestsFile)

        // INIT Writing Feedback Responses
        this.feedbackFile = File(File(config.dataPath), "sessionfeedback.csv")

        if (!this.feedbackFile.exists() || this.feedbackFile.length() == 0L) {
            this.feedbackFile.writeText("timestamp,user,session,room,partner,responseid,responsevalue\n", Charsets.UTF_8)
        }

        this.sessionWriter = PrintWriter(
            FileWriter(
                this.feedbackFile,
                Charsets.UTF_8,
                true
            ),
            true
        )

    }


    private val writerLock = StampedLock()

    fun readFeedbackRequests(): FeedbackRequestList = requests

    fun logFeedback(userSession: UserSession, roomId: UID, feedbackResponseList: FeedbackResponseList) =
        writerLock.write {
            val partnerId = ChatRoomManager.getChatPartner(roomId, userSession.user.id.UID()) ?: UserId("undefined")
            for (response in feedbackResponseList.responses) {
                val value = response.value.replace("\"", "\"\"")
                sessionWriter.println("${System.currentTimeMillis()},${userSession.user.id.toString()},${userSession.sessionId.string},\"${roomId.string}\",${partnerId.string},${response.id},\"${value}\"")
            }
            sessionWriter.flush()
        }

    fun readFeedbackHistoryPerRoom(userId: UserId, roomId: UID): FeedbackResponseList = this.lock.read {

        var response: FeedbackResponse
        val responses: MutableList<FeedbackResponse> = mutableListOf()

        //read all CSV lines with the given userid and roomid

        try {
            csvReader().open(this.feedbackFile) {
                readAllWithHeader().forEach { row ->
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
        }

        return FeedbackResponseList(responses)
    }

    fun readFeedbackHistory(assignment: Boolean = false): MutableList<FeedbackResponseItem> = this.lock.read {
        var response: FeedbackResponse
        val responseMap: HashMap<Triple<String, String, String>, MutableList<FeedbackResponse>> = hashMapOf()
        val responseList: MutableList<FeedbackResponseItem> = mutableListOf()

        //read all CSV lines with the given userid

        try {
            csvReader().open(this.feedbackFile) {
                readAllWithHeader().forEach { row ->
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
            if (responses.size > this.requests.requests.size) {
                res = responses.take(this.requests.requests.size) as MutableList<FeedbackResponse>
            }
            responseList.add(FeedbackResponseItem(triple.first, triple.second, triple.third, res))
        }

        return responseList
    }

    fun readFeedbackHistoryPerUser(author: Boolean, assignment: Boolean = false): List<FeedbackResponseAverageItem> {
        val allFeedbackResponses = readFeedbackHistory(assignment = assignment)
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
            FeedbackResponseAverageItem(it.key, feedbackCountPerUser[it.key] ?: 0, computeFeedbackAverage(it.value))
        }
    }

    fun computeFeedbackAverage(responses: List<FeedbackResponse>): List<FeedbackResponse> {
        val averages = requests.requests.map { it.id to 0 }.toMap(mutableMapOf())
        val count = requests.requests.map { it.id to 0 }.toMap(mutableMapOf())

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