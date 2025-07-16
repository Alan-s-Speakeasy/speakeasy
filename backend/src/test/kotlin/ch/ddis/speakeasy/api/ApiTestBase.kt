package ch.ddis.speakeasy.api

import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.db.DatabaseHandler
import ch.ddis.speakeasy.feedback.FormManager
import ch.ddis.speakeasy.user.PlainPassword
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.Config
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.javalin.testtools.HttpClient
import okhttp3.MediaType.Companion.toMediaType
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.File
import java.nio.file.Files

/**
 * Abstract base class for functional tests that provides common setup and cleanup logic.
 * This class handles:
 * - Database initialization and cleanup
 * - Test data directory management
 * - Manager initialization (UserManager, ChatRoomManager, AccessManager)
 * - RestApi initialization
 * - Test user creation
 */
abstract class ApiTestBase {

    protected lateinit var testDatabase: Database
    protected lateinit var testDataDir: File
    protected lateinit var testConfig: Config
    protected val objectMapper = jacksonObjectMapper()
    protected val jsonMediaType = "application/json".toMediaType()

    @BeforeEach
    fun setup() {
        // Create temporary test data directory
        testDataDir = Files.createTempDirectory("speakeasy_functional_test").toFile()
        testDataDir.deleteOnExit()

        // Create required subdirectories
        File(testDataDir, "chatlogs").mkdirs()
        File(testDataDir, "feedbackforms").mkdirs()
        File(testDataDir, "feedbackresults").mkdirs()

        // Create test database file
        val dbFile = File(testDataDir, "test_database.db")
        testDatabase = Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        )

        // Initialize database with test database
        DatabaseHandler.initWithDatabase(testDatabase)

        // Create test config
        testConfig = createTestConfig()

        // Initialize all managers
        UserManager.init(testConfig)
        ChatRoomManager.init(testConfig)
        AccessManager.init(testConfig)
        FormManager.init(testConfig)

        // Create test users
        createTestUsers()

        // Initialize RestApi
        RestApi.init(testConfig)
    }

    @AfterEach
    fun cleanup() {
        // Stop the API if it was started
        try {
            RestApi.stop()
        } catch (e: Exception) {
            // Ignore if already stopped
        }

        // Stop AccessManager
        try {
            AccessManager.stop()
        } catch (e: Exception) {
            // Ignore if already stopped
        }

        // Reset database
        DatabaseHandler.reset()

        // Clean up test data directory
        testDataDir.deleteRecursively()
    }

    /**
     * Creates the test configuration. Can be overridden by subclasses to customize config.
     */
    protected open fun createTestConfig(): Config {
        return Config(
            dataPath = testDataDir.absolutePath,
            httpPort = 0, // Use random port for testing
            enableSsl = false,
            rateLimit = 1000, // High rate limit for testing
            rateLimitUnit = java.util.concurrent.TimeUnit.MINUTES,
            rateLimitLogin = 1000 // Higher login rate limit for testing
        )
    }

    /**
     * Creates test users. Can be overridden by subclasses to customize users.
     */
    protected open fun createTestUsers() {
        UserManager.addUser("alice", UserRole.HUMAN, PlainPassword("password123"))
        UserManager.addUser("bob", UserRole.HUMAN, PlainPassword("password456"))
        UserManager.addUser("admin", UserRole.ADMIN, PlainPassword("admin123"))
    }

    // Helper method to login and get session token
    protected fun loginAndGetSessionToken(client: HttpClient, username: String, password: String): String {
        val loginRequest = mapOf("username" to username, "password" to password)
        val loginResponse = client.post("/api/login", objectMapper.writeValueAsString(loginRequest))
        val loginData = objectMapper.readTree(loginResponse.body?.string()!!)
        return loginData.get("sessionToken").asText()
    }
} 