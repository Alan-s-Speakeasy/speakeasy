package ch.ddis.speakeasy.feedback

import ch.ddis.speakeasy.api.handlers.FeedbackRequestList
import ch.ddis.speakeasy.api.handlers.FeedbackResponse
import ch.ddis.speakeasy.api.handlers.FeedbackResponseList
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
            val partnerId = ChatRoomManager.getChatPartner(roomId, userSession) ?: UserId("undefined")
            for (response in feedbackResponseList.responses) {
                val value = response.value.replace("\"", "\"\"")
                sessionWriter.println("${System.currentTimeMillis()},${userSession.user.id.string},${userSession.sessionId.string},\"${roomId.string}\",${partnerId.string},${response.id},\"${value}\"")
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

    fun readFeedbackHistoryPerUser(userId: UserId, author: Boolean): HashMap<Pair<String, String>, MutableList<FeedbackResponse>> = this.lock.read {

        var response: FeedbackResponse
        val responseMap: HashMap<Pair<String, String>, MutableList<FeedbackResponse>> = hashMapOf()

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

                    if ((room != null) && (user != null) && (partner != null) && (responseId != null) && (responseValue != null)) {
                        response = FeedbackResponse(responseId, responseValue)
                        // evaluations from a certain user
                        if (author && user == userId.string) {
                            val partnerUsername = UserManager.getUsernameFromId(UserId(partner)) ?: ""
                            if (!responseMap.containsKey(Pair(partnerUsername, room))) {
                                responseMap[Pair(partnerUsername, room)] = mutableListOf()
                            }
                            responseMap[Pair(partnerUsername, room)]?.add(response)
                        // evaluations for a certain user
                        } else if (!author && partner == userId.string) {
                            val authorUsername = UserManager.getUsernameFromId(UserId(user)) ?: ""
                            if (!responseMap.containsKey(Pair(authorUsername, room))) {
                                responseMap[Pair(authorUsername, room)] = mutableListOf()
                            }
                            responseMap[Pair(authorUsername, room)]?.add(response)
                        }
                    }
                }
            }
        } catch (e: MalformedCSVException) {
            with(e) { printStackTrace() }
        }

        return responseMap
    }

}