package ch.ddis.speakeasy.api

import ch.ddis.speakeasy.api.handlers.*
import ch.ddis.speakeasy.api.sse.SseRoomHandler
import ch.ddis.speakeasy.util.Config
import ch.ddis.speakeasy.util.clearLoggingContext
import ch.ddis.speakeasy.util.setupLoggingContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder
import io.javalin.apibuilder.ApiBuilder.before
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.http.staticfiles.Location
import ch.ddis.speakeasy.util.SessionTokenBasedNaiveRateLimit
import io.javalin.json.JavalinJackson
import io.javalin.openapi.CookieAuth
import io.javalin.openapi.OpenApiContact
import io.javalin.openapi.OpenApiLicense
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.OpenApiPluginConfiguration
import io.javalin.openapi.plugin.SecurityComponentConfiguration
import io.javalin.openapi.plugin.swagger.SwaggerConfiguration
import io.javalin.openapi.plugin.swagger.SwaggerPlugin
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory
import org.eclipse.jetty.server.*
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.util.thread.QueuedThreadPool
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory

object RestApi {

    private var javalin: Javalin? = null
    
    // Public property for testing access
    val app: Javalin? get() = javalin
    
    // Loggers for different aspects of the application
    private val logger = LoggerFactory.getLogger(RestApi::class.java)
    private val requestLogger = LoggerFactory.getLogger("ch.ddis.speakeasy.requests")
    private val accessLogger = LoggerFactory.getLogger("ch.ddis.speakeasy.access")
    
    // Markers for log filtering
    private val REST_MARKER = MarkerFactory.getMarker("REST")
    private val ACCESS_MARKER = MarkerFactory.getMarker("ACCESS")
    private val ERROR_MARKER = MarkerFactory.getMarker("ERROR")


    /**
     * Initializes javalin app. Does not start the server.
     */
    fun init(config: Config) {

        val apiRestHandlers = listOf(

            LoginHandler(),
            LogoutHandler(),
            GetCurrentUserHandler(),
            ListUsersHandler(),
            ListUserSessionsHandler(),
            CountUsersHandler(),
            AddUserHandler(),
            RemoveUserHandler(),
            ChangePasswordHandler(),
            CreateGroupHandler(),
            RemoveGroupHandler(),
            ListGroupsHandler(),
            UpdateGroupHandler(),
            RemoveAllGroupsHandler(),
            GetCurrentUserbyUsername(),

            ListChatRoomsHandler(),
            ListAllChatRoomsHandler(),
            ListAllActiveChatRoomsHandler(),
            ListAssessedChatRoomsHandler(),
            RequestChatRoomHandler(),
            ExportChatRoomsHandler(),
            CloseChatRoomHandler(),
            GetChatRoomHandler(),
            GetChatRoomUsersStatusHandler(),
            PostChatMessageHandler(),
            PostChatMessageReactionHandler(),
            PatchNewUserHandler(),

            PostFeedbackHandler(),
            GetFeedbackHistoryHandler(),
            ExportFeedbackHandler(),

            GetAdminFeedbackHistoryHandler(),
            GetAdminFeedbackAverageHandler(),

            PostAssignmentGeneratorHandler(),
            GetAssignmentGeneratorHandler(),
            PostGenerateAssignmentHandler(),
            PatchStartAssignmentHandler(),
            DeleteAssignmentGeneratorHandler(),

            GetFormListHandler(),
            GetFormHandler(),
            PostFormHandler(),
            PutFormHandler(),
            DeleteFormHandler(),
        )

        javalin = Javalin.create {
            it.plugins.enableCors { corsContainer ->
                corsContainer.add { cfg ->
//                    cfg.anyHost() // TODO: anyHost does not work for dev when using 4200 frontend port
                    cfg.reflectClientOrigin = true
                    cfg.allowCredentials = true
                }
            }
            it.jetty.server { setupHttpServer(config) }
            it.plugins.register(
                OpenApiPlugin(
                    OpenApiPluginConfiguration()
                        .withDocumentationPath("/swagger-docs")
                        .withDefinitionConfiguration { _, cfg ->
                            cfg.withOpenApiInfo { info ->
                                info.title = "Alan's Speakeasy"
                                info.version = "0.1"
                                info.description = "Full API for Alan's Speakeasy, Version 0.1"
                                val contact = OpenApiContact()
                                contact.url = "https://speakeasy.ifi.uzh.ch"
                                contact.name = "The Speakeasy Dev Team"
                                info.contact = contact
                                val license = OpenApiLicense()
                                license.name = "MIT"
                                info.license = license
                            }
                            cfg.withSecurity(
                                SecurityComponentConfiguration()
                                    .withSecurityScheme("CookieAuth", CookieAuth(AccessManager.SESSION_COOKIE_NAME))
                            )
                        }
                )
            )

            it.plugins.register(
                SwaggerPlugin(
                    SwaggerConfiguration().apply {
                        this.documentationPath = "/swagger-docs"
                        this.uiPath = "/swagger-ui"
                    }
                )
            )
            // client-side
            it.plugins.register(ClientOpenApiPlugin())
            it.plugins.register(ClientSwaggerPlugin())
            it.jsonMapper(
                JavalinJackson(
                    ObjectMapper()
                        .enable(SerializationFeature.INDENT_OUTPUT)
                        .registerModule(kotlinModule())
                )
            )
            it.http.defaultContentType = "application/json"
            it.http.prefer405over404 = true
            it.accessManager(AccessManager::manage)
            if (config.enableSsl) {
                it.plugins.enableSslRedirects()
            }
            it.staticFiles.add("html", Location.CLASSPATH)
            it.spaRoot.addFile("/", "html/index.html")
        }.events { event ->
            event.serverStarted {
                logger.info("Javalin server started successfully on port ${config.httpPort}")
                if (config.enableSsl) {
                    logger.info("SSL enabled on port ${config.httpsPort}")
                }
            }
            event.serverStopped {
                logger.info("Javalin server stopped")
            }
        }.before { ctx ->
            // Set up request tracking
            val startTime = System.currentTimeMillis()
            ctx.attribute("startTime", startTime)
            
            val requestId = java.util.UUID.randomUUID().toString().take(8)
            ctx.attribute("requestId", requestId)
            
            // Set up logging context for this request thread
            ctx.setupLoggingContext()
            
            // Log request details with REST marker for filtering
            requestLogger.info(REST_MARKER, 
                "[{}] {} {} from {} - User-Agent: {}", 
                requestId,
                ctx.method(),
                ctx.path(),
                ctx.ip(),
                ctx.userAgent() ?: "Unknown"
            )

            // Log query parameters if present
            if (ctx.queryParamMap().isNotEmpty()) {
                requestLogger.debug(REST_MARKER, 
                    "[{}] Query params: {}", 
                    requestId, 
                    ctx.queryParamMap()
                )
            }

            //check for session cookie
            val cookieId = ctx.cookie(AccessManager.SESSION_COOKIE_NAME)

            if (cookieId != null) {
                //update cookie lifetime
                ctx.cookie(AccessManager.SESSION_COOKIE_NAME, cookieId, AccessManager.SESSION_COOKIE_LIFETIME)
                //store id in attribute for later use
                ctx.attribute("session", cookieId)

            }

            //check for query parameter
            val paramId = ctx.queryParam("session")

            if (paramId != null) {
                //store id in attribute for later use
                ctx.attribute("session", paramId)
            }

        }.after { ctx ->
            // Log response details
            val startTime = ctx.attribute<Long>("startTime") ?: System.currentTimeMillis()
            val requestId = ctx.attribute<String>("requestId") ?: "unknown"
            val duration = System.currentTimeMillis() - startTime
            
            requestLogger.info(REST_MARKER,
                "[{}] Response {} - Duration: {}ms - Size: {}",
                requestId,
                ctx.status(),
                duration,
                ctx.result()?.length ?: 0
            )
            
            // Log slow requests as warnings
            if (duration > 1000) {
                requestLogger.warn(REST_MARKER,
                    "[{}] SLOW REQUEST: {} {} took {}ms",
                    requestId,
                    ctx.method(),
                    ctx.path(),
                    duration
                )
            }
            
            // Clear logging context when request is complete
            clearLoggingContext()
        }.routes {
            path("sse") {
                path(SseRoomHandler.route) { // "room"
                    ApiBuilder.sse(SseRoomHandler, *SseRoomHandler.permittedRoles.toTypedArray())
                }
            }
            path("api") {
                before { ctx ->
                    SessionTokenBasedNaiveRateLimit.requestPerTimeUnit(ctx, config.rateLimit, config.rateLimitUnit)
                }
                apiRestHandlers.forEach { handler ->
                    path(handler.route) {

                        val permittedRoles = if (handler is AccessManagedRestHandler) {
                            handler.permittedRoles
                        } else {
                            // fallback in case no roles are set, none are required
                            // TODO : AccessManager should be enforced in the handler so we avoid those flaws
                            setOf(RestApiRole.ANYONE)
                        }

                        if (handler is GetRestHandler<*>) {
                            ApiBuilder.get(handler::get, *permittedRoles.toTypedArray())
                        }

                        if (handler is PostRestHandler<*>) {
                            ApiBuilder.post(handler::post, *permittedRoles.toTypedArray())
                        }

                        if (handler is PatchRestHandler<*>) {
                            ApiBuilder.patch(handler::patch, *permittedRoles.toTypedArray())
                        }

                        if (handler is DeleteRestHandler<*>) {
                            ApiBuilder.delete(handler::delete, *permittedRoles.toTypedArray())
                        }

                        if (handler is PutRestHandler<*>) {
                            ApiBuilder.put(handler::put, *permittedRoles.toTypedArray())
                        }
                    }
                }
            }
            path("api/login") {
                before { ctx ->
                    // Should prevent to a certain extent brute force attacks
                    // This is an additional, tighter rate limit as the global one above
                    SessionTokenBasedNaiveRateLimit.requestPerTimeUnit(ctx, config.rateLimitLogin, config.rateLimitUnit)
                }
            }


        }.error(401) { ctx ->
            val requestId = ctx.attribute<String>("requestId") ?: "unknown"
            accessLogger.warn(ACCESS_MARKER, 
                "[{}] Unauthorized access attempt to {} from {}", 
                requestId, 
                ctx.path(), 
                ctx.ip()
            )
        }.error(404) { ctx ->
            val requestId = ctx.attribute<String>("requestId") ?: "unknown"
            requestLogger.info(REST_MARKER, 
                "[{}] Resource not found: {}", 
                requestId, 
                ctx.path()
            )
        }.error(429) { ctx ->
            val requestId = ctx.attribute<String>("requestId") ?: "unknown"
            accessLogger.warn(ACCESS_MARKER, 
                "[{}] Rate limit exceeded for {} from {}", 
                requestId, 
                ctx.path(), 
                ctx.ip()
            )
        }.exception(Exception::class.java) { e, ctx ->
            val requestId = ctx.attribute<String>("requestId") ?: "unknown"
            logger.error(ERROR_MARKER,
                "[{}] Unhandled exception for {} {} from {}: {}", 
                requestId,
                ctx.method(),
                ctx.path(), 
                ctx.ip(),
                e.message,
                e
            )
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

    fun start() {
        if (javalin == null) {
            throw IllegalStateException("Javalin has not been initialized. Call init() first.")
        }
        javalin!!.start()
    }

    fun stop() {
        javalin?.stop()
        javalin = null
    }

}