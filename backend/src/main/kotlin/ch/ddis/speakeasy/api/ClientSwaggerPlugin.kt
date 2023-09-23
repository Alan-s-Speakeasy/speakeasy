package ch.ddis.speakeasy.api

import io.javalin.Javalin
import io.javalin.openapi.plugin.swagger.SwaggerConfiguration
import io.javalin.openapi.plugin.swagger.SwaggerHandler
import io.javalin.plugin.Plugin

class ClientSwaggerPlugin : Plugin {
    override fun apply(app: Javalin) {
        val swaggerHandler = SwaggerHandler(
            title = "Alan's Speakeasy",
            documentationPath = "/client-specs",
            swaggerVersion = SwaggerConfiguration().version, // default version in Swagger UI Bundle
            validatorUrl = "https://validator.swagger.io/validator", // new
            routingPath = app.cfg.routing.contextPath,
            basePath = null,
            tagsSorter = "'alpha'",
            operationsSorter = "'alpha'",
            customJavaScriptFiles = emptyList(),
            customStylesheetFiles = emptyList()
        )

        app.get("/client-swagger", swaggerHandler)
    }

}