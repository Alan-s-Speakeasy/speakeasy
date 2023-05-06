package ch.ddis.speakeasy.api

import com.fasterxml.jackson.databind.node.ObjectNode
import io.javalin.openapi.plugin.OpenApiConfiguration
import io.javalin.openapi.plugin.OpenApiPlugin

class ClientOpenApiPlugin : OpenApiPlugin(OpenApiConfiguration().apply {
    this.info.title = "Alan's Speakeasy"
    this.info.version = "0.1"
    this.info.description = "Client API for Alan's Speakeasy, Version 0.1"
    this.documentationPath = "/client-specs"
    this.documentProcessor = {doc ->
        val blacklist = setOf(
            "/api/user/list",
            "/api/user/sessions",
            "/api/user/add",
            "/api/user/remove",
            "/api/user/password",
            "/api/rooms/all",
            "/api/rooms/active",
            "/api/rooms/assessed",
            "/api/rooms/request",
            "/api/feedback",
            "/api/feedback/*",
            "/api/feedbackaverage",
            "/api/feedbackhistory",
            "/api/feedbackhistory/*",
            "/api/assignment",
            "/api/assignment/*",
        )

        val relevantRoutes = doc["paths"].fields()
            .asSequence()
            .filter {
                blacklist.none { b -> it.key.contains(b) }
            }
            .map { it.key }.toList()

        (doc["paths"] as ObjectNode).retain(relevantRoutes)

        doc.toPrettyString()

    }
}) {
}