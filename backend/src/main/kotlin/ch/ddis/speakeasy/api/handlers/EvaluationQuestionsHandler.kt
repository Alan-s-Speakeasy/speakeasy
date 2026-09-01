package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.user.PlainPassword
import ch.ddis.speakeasy.user.UserManager
import io.javalin.http.Context
import io.javalin.openapi.*
import io.javalin.security.RouteRole

data class EvaluationCredentialPair(
    var username: String = "",
    var password: String = ""
)

data class EvaluationCredentialCheckRequest(
    var credentials: Array<EvaluationCredentialPair> = emptyArray()
)

data class EvaluationCredentialCheckResult(
    val username: String,
    val ok: Boolean,
    val reason: String? = null
)

class PostAutomatedEvaluationCredentialsCheckHandler : PostRestHandler<List<EvaluationCredentialCheckResult>>, AccessManagedRestHandler {
    override val permittedRoles: Set<RouteRole> = setOf(RestApiRole.ADMIN)
    override val route = "automated-evaluation/credentials/check"

    @OpenApi(
        summary = "Checks automated evaluation bot credentials without changing the admin session.",
        path = "/api/automated-evaluation/credentials/check",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.POST],
        tags = ["Evaluation"],
        requestBody = OpenApiRequestBody([OpenApiContent(EvaluationCredentialCheckRequest::class)]),
        responses = [
            OpenApiResponse("200", [OpenApiContent(Array<EvaluationCredentialCheckResult>::class)]),
            OpenApiResponse("400", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): List<EvaluationCredentialCheckResult> {
        val request = try {
            ctx.bodyAsClass(EvaluationCredentialCheckRequest::class.java)
        } catch (e: Exception) {
            throw ErrorStatusException(400, "Invalid parameters.", ctx)
        }
        return request.credentials.map { pair ->
            val username = pair.username.trim()
            when {
                username.isEmpty() || !UserManager.checkUsernameExists(username) ->
                    EvaluationCredentialCheckResult(username, false, "does_not_exist")
                UserManager.getMatchingUser(username, PlainPassword(pair.password)) == null ->
                    EvaluationCredentialCheckResult(username, false, "wrong_password")
                !UserManager.checkIfUserIsActive(username) ->
                    EvaluationCredentialCheckResult(username, false, "not_logged_in")
                else -> EvaluationCredentialCheckResult(username, true)
            }
        }
    }
}
