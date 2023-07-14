package ch.ddis.speakeasy.api

import com.fasterxml.jackson.databind.node.ObjectNode
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.OpenApiPluginConfiguration

class ClientOpenApiPlugin : OpenApiPlugin(OpenApiPluginConfiguration()
    .withDocumentationPath("/client-specs")
    .withDefinitionConfiguration { _, cfg ->
        cfg.withOpenApiInfo { info ->
            info.title = "Alan's Speakeasy"
            info.version = "0.1"
            info.description = "Client API for Alan's Speakeasy, Version 0.1"
        }
        cfg.withDefinitionProcessor { doc ->
            val blacklist = setOf(
                "/api/user/list",
                "/api/user/sessions",
                "/api/user/add",
                "/api/user/remove",
                "/api/user/password",
                "/api/rooms/all",
                "/api/rooms/active",
                "/api/rooms/assessed-and-marked",
                "/api/rooms/request",
                "/api/feedback",
                "/api/feedback/*",
                "/api/feedbackaverage",
                "/api/feedbackhistory",
                "/api/feedbackhistory/*",
                "/api/assignment",
                "/api/assignment/*",
                "/api/group",
                "/api/group/*"
            )
            val relevantRoutes =
                doc["paths"].fields().asSequence().filter { blacklist.none { b -> it.key.contains(b) } }.map { it.key }
                    .toList()

            (doc["paths"] as ObjectNode).retain(relevantRoutes)

            doc.toPrettyString()

        }
    }

)