package ch.ddis.speakeasy

import ch.ddis.speakeasy.api.handlers.FeedbackResponse
import ch.ddis.speakeasy.api.handlers.FeedbackResponseList
import ch.ddis.speakeasy.chat.Assessor
import ch.ddis.speakeasy.chat.ChatRoomManager
import ch.ddis.speakeasy.db.ChatRepository
import ch.ddis.speakeasy.db.DatabaseHandler
import ch.ddis.speakeasy.db.UserId
import ch.ddis.speakeasy.feedback.*
import ch.ddis.speakeasy.user.PlainPassword
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.util.Config
import ch.ddis.speakeasy.util.FormNotFoundException
import ch.ddis.speakeasy.util.UID
import org.jetbrains.exposed.sql.Database
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/**
 * Test class for feedback functionality
 * Tests feedback submission, retrieval, and validation using a simplified approach
 */
class FeedbackTest {

    private lateinit var testDatabase: Database
    private lateinit var testDataDir: File
    private lateinit var aliceId: UID
    private lateinit var bobId: UID
    private lateinit var charlieId: UID

    private val DEFAULT_FORM_NAME = "test-form"

    @BeforeTest
    fun setup() {
        // Create temporary test data directory
        testDataDir = Files.createTempDirectory("speakeasy_feedback_test").toFile()
        testDataDir.deleteOnExit()

        // Create required subdirectories
        File(testDataDir, "feedbackforms").mkdirs()
        File(testDataDir, "feedbackresults").mkdirs()
        File(testDataDir, "chatlogs").mkdirs()

        // Create test database file
        val dbFile = File(testDataDir, "test_database.db")
        println("Using test database file: ${dbFile.absolutePath}")
        testDatabase = Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        )

        // Initialize database with test database
        DatabaseHandler.initWithDatabase(testDatabase)

        // Create a test config
        val testConfig = Config(
            dataPath = testDataDir.absolutePath
        )

        // Create a test feedback form file
        val feedbackForm = FeedbackForm(
            formName = DEFAULT_FORM_NAME,
            requests = listOf(
                FeedbackRequest(
                    id = "0",
                    type = "multiple",
                    name = "Rating",
                    shortname = "rating",
                    options = listOf(FeedbackAnswerOption("Good", 5), FeedbackAnswerOption("Bad", 1))
                ),
                FeedbackRequest(
                    id = "1",
                    type = "text",
                    name = "Comments",
                    shortname = "comments",
                    options = emptyList()
                )
            )
        )
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        File(File(testDataDir, "feedbackforms"), "test-form.json").writeText(mapper.writeValueAsString(feedbackForm))

        // Initialize only UserManager and ChatRoomManager to avoid singleton issues
        try {
            UserManager.init(testConfig)
        } catch (e: Exception) {
            // UserManager might already be initialized, ignore
        }

        try {
            ChatRoomManager.init(testConfig)
        } catch (e: Exception) {
            // ChatRoomManager might already be initialized, ignore
        }

        try {
            FeedbackManager.init(testConfig)
        } catch (e: Exception) {
            // FeedbackManager might already be initialized, ignore
        }
        FormManager.init(testConfig)
        ChatRoomManager.init(testConfig)

        // Create test users
        UserManager.addUser("alice", UserRole.HUMAN, PlainPassword("password1"))
        UserManager.addUser("bob", UserRole.HUMAN, PlainPassword("password2"))
        UserManager.addUser("charlie", UserRole.HUMAN, PlainPassword("password3"))

        aliceId = UserManager.getUserIdFromUsername("alice")!!
        bobId = UserManager.getUserIdFromUsername("bob")!!
        charlieId = UserManager.getUserIdFromUsername("charlie")!!
    }

    @AfterTest
    fun cleanup() {
        DatabaseHandler.reset()
        testDataDir.deleteRecursively()
    }

    @Test
    fun `should create and validate feedback form structure`() {
        // Test creating a basic feedback form structure
        val feedbackForm = FeedbackForm(
            formName = "test-form",
            requests = listOf(
                FeedbackRequest(
                    id = "0",
                    type = "multiple",
                    name = "How would you rate this conversation?",
                    shortname = "rating",
                    options = listOf(
                        FeedbackAnswerOption("Excellent", 5),
                        FeedbackAnswerOption("Good", 4),
                        FeedbackAnswerOption("Average", 3),
                        FeedbackAnswerOption("Poor", 2),
                        FeedbackAnswerOption("Very Poor", 1)
                    )
                ),
                FeedbackRequest(
                    id = "1",
                    type = "text",
                    name = "Additional comments",
                    shortname = "comments",
                    options = emptyList()
                )
            )
        )

        // Verify form structure
        assertEquals("test-form", feedbackForm.formName)
        assertEquals(2, feedbackForm.requests.size)
        
        val ratingQuestion = feedbackForm.requests[0]
        assertEquals("0", ratingQuestion.id)
        assertEquals("multiple", ratingQuestion.type)
        assertEquals(5, ratingQuestion.options.size)
        
        val commentsQuestion = feedbackForm.requests[1]
        assertEquals("1", commentsQuestion.id)
        assertEquals("text", commentsQuestion.type)
        assertTrue(commentsQuestion.options.isEmpty())
    }



    @Test
    fun `should test chat room feedback marking functionality`() {
        // Create a chat room
        val chatRoom = ChatRoomManager.create(
            formRef = "test-form",
            prompt = "Test conversation for feedback",
            userIds = listOf(aliceId, bobId),
        )

        // Initially not marked as no feedback
        assertFalse(chatRoom.isMarkedAsNoFeedback())

        // Mark as no feedback required
        chatRoom.markAsNoFeedback()
        assertTrue(chatRoom.isMarkedAsNoFeedback())

    }

    @Test
    fun `should raise something when chatroom has an invalid feedbackRef`() {
        // Create a chat room with an invalid form reference
        assertFailsWith<FormNotFoundException> {
            ChatRoomManager.create(
                formRef = "invalid-form",
                prompt = "Test conversation with invalid form",
                userIds = listOf(aliceId, bobId),
            )
        }
    }

    @Test
    fun `should test assessor functionality`() {
        // Create a chat room
        val chatRoom = ChatRoomManager.create(
            formRef = "test-form",
            prompt = "Test conversation with feedback",
            userIds = listOf(aliceId, bobId),
        )

        // Initially no assessors
        // assertTrue(chatRoom.assessedBy.isEmpty())

        // We can't test the addAssessor method because it's not implemented (TODO)
        // But we can test the data structure
        val assessor = Assessor(aliceId)
        assertEquals(aliceId, assessor.assessor)
    }

    @Test
    fun `should validate feedback form requirements`() {
        // Test that feedback forms have proper validation
        assertFailsWith<Exception> {
            FeedbackForm(
                formName = "", // Empty name should fail
                requests = listOf(
                    FeedbackRequest("0", "text", "Question", "q1", emptyList())
                )
            )
        }

        assertFailsWith<Exception> {
            FeedbackForm(
                formName = "valid-name",
                requests = emptyList() // Empty requests should fail
            )
        }

        // Test duplicate shortnames should fail
        assertFailsWith<Exception> {
            FeedbackForm(
                formName = "valid-name",
                requests = listOf(
                    FeedbackRequest("0", "text", "Question 1", "same-short", emptyList()),
                    FeedbackRequest("1", "text", "Question 2", "same-short", emptyList()) // Duplicate shortname
                )
            )
        }
    }


    @Test
    fun `should validate feedback request properties`() {
        // Test valid multiple choice question
        val validMultiple = FeedbackRequest(
            id = "0",
            type = "multiple",
            name = "Valid Question",
            shortname = "valid",
            options = listOf(FeedbackAnswerOption("Option 1", 1))
        )
        
        // This should not throw
        assertEquals("0", validMultiple.id)
        assertEquals("multiple", validMultiple.type)
        
        // Test valid text question
        val validText = FeedbackRequest(
            id = "1",
            type = "text",
            name = "Valid Text",
            shortname = "text",
            options = emptyList()
        )
        
        assertEquals("1", validText.id)
        assertEquals("text", validText.type)
        
        // Test invalid ID (non-numeric)
        assertFailsWith<Exception> {
            FeedbackRequest(
                id = "invalid",
                type = "text",
                name = "Question",
                shortname = "q",
                options = emptyList()
            )
        }
        
        // Test negative ID
        assertFailsWith<Exception> {
            FeedbackRequest(
                id = "-1",
                type = "text",
                name = "Question",
                shortname = "q",
                options = emptyList()
            )
        }
        
        // Test multiple choice without options
        assertFailsWith<Exception> {
            FeedbackRequest(
                id = "0",
                type = "multiple",
                name = "Question",
                shortname = "q",
                options = emptyList() // Should fail for multiple choice
            )
        }
    }



    @Test
    fun `should handle complex feedback form validation`() {
        // Test form with sequential IDs starting from 0
        val validForm = FeedbackForm(
            formName = "complex-form",
            requests = listOf(
                FeedbackRequest("0", "multiple", "Q1", "q1", 
                    listOf(FeedbackAnswerOption("A", 1))),
                FeedbackRequest("1", "text", "Q2", "q2", emptyList()),
                FeedbackRequest("2", "multiple", "Q3", "q3", 
                    listOf(FeedbackAnswerOption("B", 2), FeedbackAnswerOption("C", 3)))
            )
        )
        
        assertEquals("complex-form", validForm.formName)
        assertEquals(3, validForm.requests.size)
        
        // Test form with non-sorted IDs should fail
        assertFailsWith<Exception> {
            FeedbackForm(
                formName = "invalid-sequence",
                requests = listOf(
                    FeedbackRequest("1", "text", "Q1", "q1", emptyList()),
                    FeedbackRequest("0", "text", "Q2", "q2", emptyList()) // Not sorted
                )
            )
        }
        
        // Test form not starting from 0 should fail
        assertFailsWith<Exception> {
            FeedbackForm(
                formName = "invalid-start",
                requests = listOf(
                    FeedbackRequest("1", "text", "Q1", "q1", emptyList()) // Should start from 0
                )
            )
        }
    }

    @Test
    fun `should validate form name constraints`() {
        // Valid form names
        val validNames = listOf(
            "valid-form",
            "ValidForm123",
            "form_with_underscores",
            "form with spaces",
            "a1",
            "1a"
        )
        
        validNames.forEach { name ->
            val form = FeedbackForm(
                formName = name,
                requests = listOf(
                    FeedbackRequest("0", "text", "Question", "q", emptyList())
                )
            )
            assertEquals(name, form.formName)
        }
        
        // Invalid form names that should fail
        val invalidNames = listOf(
            "", // Empty
            "   ", // Blank
            "..", // Contains ..
            "form/with/slash", // Contains /
            "form\\with\\backslash", // Contains \
            "a".repeat(101) // Too long (>100 chars)
        )
        
        invalidNames.forEach { name ->
            assertFailsWith<Exception>("Form name '$name' should be invalid") {
                FeedbackForm(
                    formName = name,
                    requests = listOf(
                        FeedbackRequest("0", "text", "Question", "q", emptyList())
                    )
                )
            }
        }
    }

    @Test
    fun `should handle multiple feedback forms`() {
        // Test creating multiple different forms
        val form1 = FeedbackForm(
            formName = "satisfaction-survey",
            requests = listOf(
                FeedbackRequest("0", "multiple", "Satisfaction", "satisfaction", 
                    listOf(
                        FeedbackAnswerOption("Very Satisfied", 5),
                        FeedbackAnswerOption("Satisfied", 4),
                        FeedbackAnswerOption("Neutral", 3),
                        FeedbackAnswerOption("Dissatisfied", 2),
                        FeedbackAnswerOption("Very Dissatisfied", 1)
                    ))
            )
        )
        
        val form2 = FeedbackForm(
            formName = "quality-assessment",
            requests = listOf(
                FeedbackRequest("0", "multiple", "Quality", "quality", 
                    listOf(
                        FeedbackAnswerOption("Excellent", 10),
                        FeedbackAnswerOption("Good", 8),
                        FeedbackAnswerOption("Average", 6),
                        FeedbackAnswerOption("Poor", 4),
                        FeedbackAnswerOption("Very Poor", 2)
                    )),
                FeedbackRequest("1", "text", "Improvements", "improvements", emptyList())
            )
        )
        
        // Verify forms are different and valid
        assertNotEquals(form1.formName, form2.formName)
        assertEquals(1, form1.requests.size)
        assertEquals(2, form2.requests.size)
        
        // Verify option counts
        assertEquals(5, form1.requests[0].options.size)
        assertEquals(5, form2.requests[0].options.size)
        assertEquals(0, form2.requests[1].options.size)
    }

    @Test
    fun `should correctly detect valid forms`(){
        assertTrue (FormManager.isValidFormName("test-form"))
        assertFalse (FormManager.isValidFormName("") )
        assertFalse {   FormManager.isValidFormName("ergjkdgr") }
    }

    @Test
    fun `should have correct formIds`() {
        val formIdFromManager = FormManager.getFormIdByName(DEFAULT_FORM_NAME)
        assertNotNull(formIdFromManager, "Form ID should not be null for existing form")
        // Create a chatroom and check if it has the correct formId
        val chatRoom = ChatRoomManager.create(
            formRef = DEFAULT_FORM_NAME,
            prompt = "Test chat room for form ID",
            userIds = listOf(aliceId, bobId)
        )
        val formIdFromChatRoom = ChatRepository.getFormForChatRoom(chatRoom.uid)
        assertEquals(formIdFromChatRoom, formIdFromManager, "Form ID from chat room should match form ID from manager")
    }


    @Test
    fun `should log feedback and retrieve it with readFeedbackHistory`() {
        // Get user entities
        val aliceId = UserManager.getUserIdFromUsername("alice")!!
        val bobId = UserManager.getUserIdFromUsername("bob")!!

        // Create a chat room for feedback
        val chatRoom = ChatRoomManager.create(
            formRef = "test-form",
            prompt = "Test feedback submission",
            userIds = listOf(aliceId, bobId),
            assignment = true
        )

        // Create feedback response from Alice about Bob
        val feedbackToLog = FeedbackResponseList(
            mutableListOf(
                FeedbackResponse("0", "5"), // Rating
                FeedbackResponse("1", "A good conversation partner.") // Comment
            )
        )

        // Log the feedback
        FeedbackManager.logFeedback(aliceId, chatRoom.uid, feedbackToLog)

        // Read feedbackf for room

        val feedbackResponses = FeedbackManager.readFeedbackHistoryPerRoom(aliceId, chatRoom.uid)
        assertEquals(feedbackToLog, feedbackResponses)

        // Read the feedback history
        val history = FeedbackManager.readFeedbackHistory(
            authorIds = setOf(aliceId),
            assignment = true,
            formName = "test-form"
        ).filterNotNull().toMutableList()

        // Assertions
        assertEquals(1, history.size, "Should find one feedback entry for Alice")
        val feedbackEntry = history.first()

        assertEquals("alice", feedbackEntry.author)
        assertEquals("bob", feedbackEntry.recipient)

        assertEquals(2, feedbackEntry.responses.size, "Should contain two responses")

        val ratingResponse = feedbackEntry.responses.find { it.id == "0" }
        assertNotNull(ratingResponse)
        assertEquals("5", ratingResponse.value)

        val commentResponse = feedbackEntry.responses.find { it.id == "1" }
        assertNotNull(commentResponse)
        assertEquals("A good conversation partner.", commentResponse.value)

        // Check that the whole feedback for the form is read when users is empty in readFeedbackHistory
        val allFeedback = FeedbackManager.readFeedbackHistory(
            authorIds = setOf(),
            assignment = true,
            formName = "test-form"
        ).filterNotNull().toMutableList()
        assertEquals(1, allFeedback.size)
    }

    @Test
    fun `should prevent logging duplicate feedback`() {
        val aliceId = UserManager.getUserIdFromUsername("alice")!!
        val bobId = UserManager.getUserIdFromUsername("bob")!!

        val chatRoom = ChatRoomManager.create(
            formRef = "test-form",
            prompt = "Test duplicate feedback",
            userIds = listOf(aliceId, bobId)
        )

        val feedback = FeedbackResponseList(mutableListOf(FeedbackResponse("0", "5")))

        // First submission should succeed
        FeedbackManager.logFeedback(aliceId, chatRoom.uid, feedback)

        // Second submission should fail
        assertFailsWith<IllegalArgumentException>("Should not be able to submit feedback twice") {
            FeedbackManager.logFeedback(aliceId, chatRoom.uid, feedback)
        }
    }

    @Test
    fun `should filter feedback history by assignment`() {
        val alice = UserManager.getUserIdFromUsername("alice")!!
        val bobId = UserManager.getUserIdFromUsername("bob")!!

        // Create an assignment and a non-assignment chat room
        val assignmentRoom = ChatRoomManager.create(
            formRef = "test-form",
            prompt = "Assignment feedback",
            userIds = listOf(aliceId, bobId),
            assignment = true
        )
        val nonAssignmentRoom = ChatRoomManager.create(
            formRef = "test-form",
            prompt = "Non-assignment feedback",
            userIds = listOf(aliceId, bobId),
            assignment = false
        )

        val feedback = FeedbackResponseList(mutableListOf(FeedbackResponse("0", "5")))

        // Log feedback for both rooms
        FeedbackManager.logFeedback(aliceId, assignmentRoom.uid, feedback)
        FeedbackManager.logFeedback(aliceId, nonAssignmentRoom.uid, feedback)

        // Read history with assignment=true
        val assignmentHistory = FeedbackManager.readFeedbackHistory(
            authorIds = setOf(aliceId),
            assignment = true,
            formName = "test-form"
        )
        assertEquals(1, assignmentHistory.size)
        assertNotNull(assignmentHistory.first())
        // Read history with assignment=false
        val nonAssignmentHistory = FeedbackManager.readFeedbackHistory(
            authorIds = setOf(aliceId),
            assignment = false,
            formName = "test-form"
        ).filterNotNull().toMutableList()
        assertEquals(1, nonAssignmentHistory.size)
    }
    // TODO : Also test for multiple authorIds !


    @Test
    fun `should handle edge cases in feedback response values`() {
        // Test various edge cases for response values
        val edgeCaseResponses = listOf(
            FeedbackResponse("0", ""), // Empty value
            FeedbackResponse("1", "   "), // Whitespace only
            FeedbackResponse("2", "0"), // Zero value
            FeedbackResponse("3", "999999"), // Very large number
            FeedbackResponse("4", "Text with spaces and symbols!@#"), // Complex text
            FeedbackResponse("5", "Multi\nline\ntext"), // Multiline text
            FeedbackResponse("6", "Unicode: 你好 🌟"), // Unicode characters
        )
        
        val responseList = FeedbackResponseList(edgeCaseResponses.toMutableList())
        assertEquals(7, responseList.responses.size)
        
        // Verify all responses are stored correctly
        assertEquals("", responseList.responses[0].value)
        assertEquals("   ", responseList.responses[1].value)
        assertEquals("0", responseList.responses[2].value)
        assertEquals("999999", responseList.responses[3].value)
        assertTrue(responseList.responses[4].value.contains("!@#"))
        assertTrue(responseList.responses[5].value.contains("\n"))
        assertTrue(responseList.responses[6].value.contains("🌟"))
    }
}

// NEXT : Test logfeedback, aggreagate statistics, readFeedback etc - basically all methods from FeedbackManager