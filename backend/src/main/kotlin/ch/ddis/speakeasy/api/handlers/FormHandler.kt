package ch.ddis.speakeasy.api.handlers

import ch.ddis.speakeasy.api.*
import ch.ddis.speakeasy.feedback.FeedbackForm
import ch.ddis.speakeasy.feedback.FormManager
import ch.ddis.speakeasy.feedback.InvalidFormException
import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import io.javalin.http.Context
import io.javalin.openapi.*

class GetFormListHandler : GetRestHandler<List<FeedbackForm>>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route: String = "feedbackforms"

    @OpenApi(
        summary = "Gets the list of all feedback forms",
        path = "/api/feedbackforms",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Form"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(Array<FeedbackForm>::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): List<FeedbackForm> {
        return FormManager.listForms()
    }
}

class GetFormHandler : GetRestHandler<FeedbackForm>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.USER)
    override val route: String = "feedbackforms/{formName}"

    @OpenApi(
        summary = "Gets a feedback form",
        path = "/api/feedbackforms/{formName}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.GET],
        tags = ["Form"],
        pathParams = [
            OpenApiParam("formName", String::class, "Name of the feedback form", required = true),
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(FeedbackForm::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doGet(ctx: Context): FeedbackForm {
        val formName = (ctx.pathParamMap().getOrElse("formName") {
            throw ErrorStatusException(400, "Parameter 'formName' is missing!'", ctx)
        })
        if (formName.isBlank()) {
            throw ErrorStatusException(400, "Parameter 'formName' is empty!", ctx)
        }
        return FormManager.getForm(formName)
    }
}

class PostFormHandler : PostRestHandler<SuccessStatus>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.ADMIN)
    override val route: String = "feedbackforms"

    @OpenApi(
        summary = "Creates a new feedback form",
        path = "/api/feedbackforms/",
        operationId = OpenApiOperation.AUTO_GENERATE,
        requestBody = OpenApiRequestBody([OpenApiContent(FeedbackForm::class)]),
        methods = [HttpMethod.POST],
        tags = ["Form"],
        responses = [
            OpenApiResponse("200", [OpenApiContent(FeedbackForm::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPost(ctx: Context): SuccessStatus {
        if (ctx.body().isBlank()) {
            throw ErrorStatusException(400, "Request body is empty!", ctx)
        }
        var form: FeedbackForm? = null
        try {
            form = ctx.bodyAsClass(FeedbackForm::class.java)
        } catch (e: ValueInstantiationException) {
            // We can show the whole error, in this specific case where the validation failed
            if (e.cause is InvalidFormException) {
                // Cast to InvalidFormException
                val invalidFormException = e.cause as InvalidFormException
                throw ErrorStatusException(400, invalidFormException.message ?: "Invalid form", ctx)
            }
        } catch (e: Exception) {
            // This is a generic error, we don't want to show the whole stack trace or whatever, I guess
            throw ErrorStatusException(400, "Invalid parameters. This is a programmers error.", ctx)
        }

        if (form != null) {
            FormManager.createNewForm(form)
            return SuccessStatus("Form '${form.formName}' created successfully!")
        }
        throw ErrorStatusException(400, "Invalid parameters. This is a programmers error.", ctx)
    }
}

class PutFormHandler : PutRestHandler<SuccessStatus>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.ADMIN)
    override val route: String = "feedbackforms/{formName}"

    @OpenApi(
        summary = "Updates a feedback form",
        path = "/api/feedbackforms/{formName}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.PUT],
        tags = ["Form"],
        requestBody = OpenApiRequestBody([OpenApiContent(FeedbackForm::class)]),
        pathParams = [
            OpenApiParam("formName", String::class, "Name of the feedback form", required = true),
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(FeedbackForm::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doPut(ctx: Context): SuccessStatus {
        val formName = (ctx.pathParamMap().getOrElse("formName") {
            throw ErrorStatusException(400, "Parameter 'formName' is missing!", ctx)
        })
        if (formName.isBlank()) {
            throw ErrorStatusException(400, "Parameter 'formName' is empty!", ctx)
        }
        if (ctx.body().isBlank()) {
            throw ErrorStatusException(400, "Request body is empty!", ctx)
        }
        val form = ctx.bodyAsClass(FeedbackForm::class.java)
        FormManager.updateForm(formName, form)
        return SuccessStatus("Form '${form.formName}' updated successfully!")
    }
}

class DeleteFormHandler : DeleteRestHandler<SuccessStatus>, AccessManagedRestHandler {
    override val permittedRoles = setOf(RestApiRole.ADMIN)
    override val route: String = "feedbackforms/{formName}"

    @OpenApi(
        summary = "Deletes a feedback form",
        path = "/api/feedbackforms/{formName}",
        operationId = OpenApiOperation.AUTO_GENERATE,
        methods = [HttpMethod.DELETE],
        tags = ["Form"],
        pathParams = [
            OpenApiParam("formName", String::class, "Name of the feedback form", required = true),
        ],
        responses = [
            OpenApiResponse("200", [OpenApiContent(SuccessStatus::class)]),
            OpenApiResponse("401", [OpenApiContent(ErrorStatus::class)]),
            OpenApiResponse("404", [OpenApiContent(ErrorStatus::class)])
        ]
    )
    override fun doDelete(ctx: Context): SuccessStatus {
        val formName = (ctx.pathParamMap().getOrElse("formName") {
            throw ErrorStatusException(400, "Parameter 'formName' is missing!", ctx)
        })
        if (formName.isBlank()) {
            throw ErrorStatusException(400, "Parameter 'formName' is empty!", ctx)
        }
        FormManager.deleteForm(formName)
        return SuccessStatus("Form '$formName' deleted successfully!")
    }
}
