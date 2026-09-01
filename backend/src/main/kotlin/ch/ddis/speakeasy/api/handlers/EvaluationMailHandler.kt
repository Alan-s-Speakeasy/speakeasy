package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.mail.EvaluationMailStatus
import ch.ddis.speakeasy.mail.EvaluationMailer
import io.javalin.http.Context
import io.javalin.openapi.*
import io.javalin.security.RouteRole

data class EvaluationEmailTranscript(
    var username: String = "",
    var content: String = "",
    var recipients: Array<String> = emptyArray()
)

data class EvaluationEmailSendRequest(
    var transcripts: Array<EvaluationEmailTranscript> = emptyArray()
)

data class EvaluationEmailSendResult(
    val username: String,
    val email: String,
    val sent: Boolean,
    val error: String? = null
)

data class EvaluationEmailSendResponse(
    val sent: Int,
    val failed: Int,
    val results: List<EvaluationEmailSendResult>
)

private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private const val MAX_TRANSCRIPTS = 50
private const val MAX_RECIPIENTS_PER_CHAT = 20
private const val MAX_TOTAL_SENDS = 300
private const val MAX_CONTENT_CHARS = 200_000
private const val SEND_GAP_MS = 150L

class GetAutomatedEvaluationEmailStatusHandler : GetRestHandler<EvaluationMailStatus>, AccessManagedRestHandler {
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.ADMIN)
    override val route = "automated-evaluation/email/status"

    @OpenApi(
        summary = "Returns whether evaluation SMTP is configured. Does not expose secrets.",
        path = "/api/automated-evaluation/email/status",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Evaluation"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(EvaluationMailStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): EvaluationMailStatus {
        return EvaluationMailer.status()
    }
}

class PostAutomatedEvaluationEmailsHandler : PostRestHandler<EvaluationEmailSendResponse>, AccessManagedRestHandler {
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.ADMIN)
    override val route = "automated-evaluation/emails"

    @OpenApi(
        summary = "Sends automated evaluation chat transcripts to the listed addresses.",
        path = "/api/automated-evaluation/emails",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        tags = ["Evaluation"],
        requestBody = OpenApiRequestBody([OpenApiContent(EvaluationEmailSendRequest::class)]),
        responses = [
            OpenApiResponse("200", [OpenApiContent(EvaluationEmailSendResponse::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("503", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): EvaluationEmailSendResponse {
        if (EvaluationMailer.loadSettings() == null) {
            throw ErrorStatusException(
                503,
                "SMTP is not configured. Copy data/smtp.properties.example to data/smtp.properties or set SPEAKEASY_SMTP_* environment variables.",
                ctx
            )
        }

        val request = try {
            ctx.bodyAsClass(EvaluationEmailSendRequest::class.java)
        } catch (e: Exception) {
            throw ErrorStatusException(400, "Invalid parameters.", ctx)
        }

        if (request.transcripts.isEmpty()) {
            throw ErrorStatusException(400, "No transcripts to send.", ctx)
        }
        if (request.transcripts.size > MAX_TRANSCRIPTS) {
            throw ErrorStatusException(400, "At most $MAX_TRANSCRIPTS transcripts can be sent at once.", ctx)
        }

        var totalRecipients = 0
        request.transcripts.forEach { transcript ->
            val username = transcript.username.trim()
            if (username.isEmpty()) {
                throw ErrorStatusException(400, "Each transcript needs a username.", ctx)
            }
            if (transcript.content.length > MAX_CONTENT_CHARS) {
                throw ErrorStatusException(400, "Transcript for $username is too large.", ctx)
            }
            val recipients = transcript.recipients.map { it.trim() }.filter { it.isNotEmpty() }
            if (recipients.isEmpty()) {
                throw ErrorStatusException(400, "Transcript for $username has no recipients.", ctx)
            }
            if (recipients.size > MAX_RECIPIENTS_PER_CHAT) {
                throw ErrorStatusException(400, "Transcript for $username has too many recipients.", ctx)
            }
            recipients.forEach { email ->
                if (!EMAIL_PATTERN.matches(email)) {
                    throw ErrorStatusException(400, "Invalid email address: $email", ctx)
                }
            }
            totalRecipients += recipients.size
        }
        if (totalRecipients > MAX_TOTAL_SENDS) {
            throw ErrorStatusException(400, "At most $MAX_TOTAL_SENDS emails can be sent at once.", ctx)
        }

        val results = mutableListOf<EvaluationEmailSendResult>()
        request.transcripts.forEach { transcript ->
            val username = transcript.username.trim()
            transcript.recipients.map { it.trim() }.filter { it.isNotEmpty() }.forEach { email ->
                if (results.isNotEmpty()) {
                    Thread.sleep(SEND_GAP_MS)
                }
                try {
                    EvaluationMailer.sendTranscript(username, email, transcript.content)
                    results += EvaluationEmailSendResult(username, email, true)
                } catch (error: Exception) {
                    results += EvaluationEmailSendResult(
                        username,
                        email,
                        false,
                        error.message ?: "SMTP send failed"
                    )
                }
            }
        }

        return EvaluationEmailSendResponse(
            sent = results.count { it.sent },
            failed = results.count { !it.sent },
            results = results
        )
    }
}
