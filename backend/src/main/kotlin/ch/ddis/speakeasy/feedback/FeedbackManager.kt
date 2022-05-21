package ch.ddis.speakeasy.feedback

import ch.ddis.speakeasy.api.handlers.FeedbackRequestList
import ch.ddis.speakeasy.api.handlers.FeedbackResponse
import ch.ddis.speakeasy.api.handlers.FeedbackResponseList
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
            this.feedbackFile.writeText("timestamp,session,room,responseid,responsevalue\n", Charsets.UTF_8)
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
            for (response in feedbackResponseList.responses) {
                sessionWriter.println("${System.currentTimeMillis()},${userSession.sessionId.string},\"${roomId.string}\",${response.id},\"${response.value}\"")
            }
            sessionWriter.flush()
        }

    fun readFeedbackHistoryPerRoom(userId: UserId, roomId: UID): FeedbackResponseList = this.lock.read {

        var response: FeedbackResponse
        val responses: MutableList<FeedbackResponse> = mutableListOf()

        //read all CSV lines with the given usersession and roomid

        try {
            csvReader().open(this.feedbackFile) {
                readAllWithHeader().forEach { row ->
                    //in file CSV file: timestamp,userid,sessionid,room,responseid,responsevalue
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


}