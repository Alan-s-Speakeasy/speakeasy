package ch.ddis.speakeasy

import ch.ddis.speakeasy.db.DatabaseHandler
import ch.ddis.speakeasy.user.PlainPassword
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import org.jetbrains.exposed.sql.Database
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/**
 * Simple end-to-end test that demonstrates database mocking.
 * This shows the basic pattern for testing your backend with a test database.
 */
class SimpleEndToEndTest {

    private lateinit var testDatabase: Database
    private lateinit var testDataDir: File

    @BeforeTest
    fun setup() {
        // Create temporary test data directory
        testDataDir = Files.createTempDirectory("speakeasy_simple_test").toFile()
        testDataDir.deleteOnExit()

        // Create test database file
        val dbFile = File(testDataDir, "test_database.db")
        testDatabase = Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        )

        // Initialize database with test database
        DatabaseHandler.initWithDatabase(testDatabase)

        // Create a test config
        val testConfig = ch.ddis.speakeasy.util.Config(
            dataPath = testDataDir.absolutePath
        )

        // Initialize UserManager with test config
        UserManager.init(testConfig)
    }

    @AfterTest
    fun cleanup() {
        DatabaseHandler.reset()
        
        // Clean up test data directory
        testDataDir.deleteRecursively()
    }

    @Test
    fun `should create users and verify database integration`() {
        // Add test users
        UserManager.addUser("testuser", UserRole.HUMAN, PlainPassword("testpass"))
        UserManager.addUser("admin", UserRole.ADMIN, PlainPassword("adminpass"))

        // Verify users were created
        val users = UserManager.list()
        assertEquals(2, users.size)
        
        val testUser = users.find { it.name == "testuser" }
        assertNotNull(testUser)
        assertEquals(UserRole.HUMAN, testUser.role)
        
        val adminUser = users.find { it.name == "admin" }
        assertNotNull(adminUser)
        assertEquals(UserRole.ADMIN, adminUser.role)

        // Verify authentication works
        val authenticatedUser = UserManager.getMatchingUser("testuser", PlainPassword("testpass"))
        assertNotNull(authenticatedUser)
        assertEquals("testuser", authenticatedUser.name)

        // Verify wrong password fails
        val wrongPasswordUser = UserManager.getMatchingUser("testuser", PlainPassword("wrongpass"))
        assertNull(wrongPasswordUser)
    }

    @Test
    fun `should handle user operations correctly`() {
        // Add a user
        UserManager.addUser("tempuser", UserRole.HUMAN, PlainPassword("temppass"))

        // Count users
        val userCount = UserManager.list().size
        assertEquals(1, userCount)

        // Remove user
        val removed = UserManager.removeUser("tempuser", force = true)
        assertTrue(removed)

        // Verify user is gone
        val finalCount = UserManager.list().size
        assertEquals(0, finalCount)
    }

    @Test
    fun `should handle group operations`() {
        // Add users first
        UserManager.addUser("user1", UserRole.HUMAN, PlainPassword("pass1"))
        UserManager.addUser("user2", UserRole.HUMAN, PlainPassword("pass2"))

        // Create a group with both users
        UserManager.createGroup("testgroup", listOf("user1", "user2"))

        // Verify group exists and has users
        val groups = UserManager.listGroups()
        assertEquals(1, groups.size)
        assertEquals("testgroup", groups[0].name)

        // Verify the users are in the same group
        val areInSameGroup = UserManager.areInSameGroup("user1", "user2")
        assertTrue(areInSameGroup)

        // Verify group details
        val groupDetails = ch.ddis.speakeasy.user.GroupDetails.of(groups[0])
        assertEquals(2, groupDetails.users.size)
        assertTrue(groupDetails.users.any { it.username == "user1" })
        assertTrue(groupDetails.users.any { it.username == "user2" })
    }
} 