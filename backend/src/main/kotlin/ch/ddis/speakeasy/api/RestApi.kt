package ch.ddis.speakeasy.api

import ch.ddis.speakeasy.api.handlers.*
import ch.ddis.speakeasy.util.Config
import com.fasterxml.jackson.databind.SerializationFeature
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.core.security.SecurityUtil
import io.javalin.plugin.openapi.OpenApiOptions
import io.javalin.plugin.openapi.OpenApiPlugin
import io.javalin.plugin.openapi.jackson.JacksonToJsonMapper
import io.javalin.plugin.openapi.ui.SwaggerOptions
import io.swagger.v3.oas.models.info.Info
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory
import org.eclipse.jetty.http.HttpCookie
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.session.DefaultSessionCache
import org.eclipse.jetty.server.session.FileSessionDataStore
import org.eclipse.jetty.server.session.SessionHandler
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.QueuedThreadPool
import java.io.File

object RestApi {

    private var javalin: Javalin? = null


    fun init(config: Config) {

        val apiRestHandlers = listOf(

            LoginHandler(),
            LogoutHandler(),
            GetCurrentUserHandler(),
            ListUsersHandler(),
            ListUserSessionsHandler(),
            AddUserHandler(),
            RemoveUserHandler(),
            ChangePasswordHandler(),

            ListChatRoomsHandler(),
            ListAllChatRoomsHandler(),
            ListAllActiveChatRoomsHandler(),
            ListAssessedChatRoomsHandler(),
            RequestChatRoomHandler(),

            GetChatRoomHandler(),
            PostChatMessageHandler(),
            PostChatMessageReactionHandler(),

            GetFeedbackRequestListHandler(),
            PostFeedbackHandler(),
            GetFeedbackHistoryHandler(),

            GetAdminFeedbackHistoryHandler(),
            GetAdminFeedbackAverageHandler(),

            PostAssignmentGeneratorHandler(),
            GetAssignmentGeneratorHandler(),
            PostGenerateAssignmentHandler(),
            PatchStartAssignmentHandler(),
            DeleteAssignmentGeneratorHandler()
        )

        javalin = Javalin.create {
            it.enableCorsForAllOrigins()
            it.server { setupHttpServer(config) }
            it.registerPlugin(
                OpenApiPlugin(
                    OpenApiOptions(
                        Info().apply {
                            title("Alan's Speakeasy")
                            version("0.1")
                            description("Full API for Alan's Speakeasy, Version 0.1")
                        }
                    ).apply {
                        path("/swagger-docs")
                        swagger(SwaggerOptions("/swagger-ui"))
                        activateAnnotationScanningFor("ch.ddis.speakeasy.api.handlers")
                        toJsonMapper(JacksonToJsonMapper(jacksonMapper.enable(SerializationFeature.INDENT_OUTPUT)))
                    },
                    OpenApiOptions(
                        Info().apply {
                            title("Alan's Speakeasy")
                            version("0.1")
                            description("Client API for Alan's Speakeasy, Version 0.1")
                        }
                    ).apply {
                        path("/client-specs")
                        swagger(SwaggerOptions("/client-swagger"))
                        activateAnnotationScanningFor("ch.ddis.speakeasy.api.handlers")
                        toJsonMapper(JacksonToJsonMapper(jacksonMapper.enable(SerializationFeature.INDENT_OUTPUT)))
                        listOf(
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
                        ).forEach {
                            ignorePath(it)
                        }
                    },
                )
            )
            it.defaultContentType = "application/json"
            it.prefer405over404 = true
            it.sessionHandler { fileSessionHandler(config) }
            it.accessManager(AccessManager::manage)
            it.enforceSsl = config.enableSsl
            it.addStaticFiles("html")
            it.addSinglePageRoot("/", "html/index.html")
        }.routes {

            path("api") {
                apiRestHandlers.forEach { handler ->
                    path(handler.route) {


                        val permittedRoles = if (handler is AccessManagedRestHandler) {
                            handler.permittedRoles
                        } else {
                            SecurityUtil.roles(RestApiRole.ANYONE)
                        }

                        if (handler is GetRestHandler<*>) {
                            ApiBuilder.get(handler::get, permittedRoles)
                        }

                        if (handler is PostRestHandler<*>) {
                            ApiBuilder.post(handler::post, permittedRoles)
                        }

                        if (handler is PatchRestHandler<*>) {
                            ApiBuilder.patch(handler::patch, permittedRoles)
                        }

                        if (handler is DeleteRestHandler<*>) {
                            ApiBuilder.delete(handler::delete, permittedRoles)
                        }

                    }
                }
            }


        }.error(401) {
            it.json(ErrorStatus("Unauthorized request!"))
        }.exception(Exception::class.java) { e, ctx ->
            ctx.status(500).json(ErrorStatus("Internal server error!"))
        }.start(config.httpPort)

    }


    private fun fileSessionHandler(config: Config) = SessionHandler().apply {
        sessionCache = DefaultSessionCache(this).apply {
            sessionDataStore = FileSessionDataStore().apply {
                val baseDir = File(".")
                this.storeDir = File(baseDir, "session-store").apply { mkdir() }
            }
        }

        if (config.enableSsl) {
            sameSite = HttpCookie.SameSite.NONE
            sessionCookieConfig.isSecure = true
            isSecureRequestOnly = true
        }

    }

    private fun setupHttpServer(config: Config): Server {

        val httpConfig = HttpConfiguration().apply {
            sendServerVersion = false
            sendXPoweredBy = false
            if (config.enableSsl) {
                secureScheme = "https"
                securePort = config.httpsPort
            }
        }

        if (config.enableSsl) {
            val httpsConfig = HttpConfiguration(httpConfig).apply {
                addCustomizer(SecureRequestCustomizer())
            }

            val alpn = ALPNServerConnectionFactory().apply {
                defaultProtocol = "http/1.1"
            }

            val sslContextFactory = SslContextFactory.Server().apply {
                keyStorePath = config.keystorePath
                setKeyStorePassword(config.keystorePassword)
                //cipherComparator = HTTP2Cipher.COMPARATOR
                provider = "Conscrypt"
            }

            val ssl = SslConnectionFactory(sslContextFactory, alpn.protocol)

            val http2 = HTTP2ServerConnectionFactory(httpsConfig)

            val fallback = HttpConnectionFactory(httpsConfig)


            return Server(QueuedThreadPool(10_000)).apply {
                //HTTP Connector
                addConnector(
                    ServerConnector(
                        server,
                        HttpConnectionFactory(httpConfig),
                        HTTP2ServerConnectionFactory(httpConfig)
                    ).apply {
                        port = config.httpPort
                    })
                // HTTPS Connector
                addConnector(ServerConnector(server, ssl, alpn, http2, fallback).apply {
                    port = config.httpsPort
                })
            }
        } else {
            return Server(QueuedThreadPool(10_000)).apply {
                //HTTP Connector
                addConnector(
                    ServerConnector(
                        server,
                        HttpConnectionFactory(httpConfig),
                        HTTP2ServerConnectionFactory(httpConfig)
                    ).apply {
                        port = config.httpPort
                    })

            }
        }
    }

    fun stop() {
        javalin?.stop()
        javalin = null
    }

}