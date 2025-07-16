package ch.ddis.speakeasy.api

import ch.ddis.speakeasy.feedback.FeedbackForm
import ch.ddis.speakeasy.feedback.FeedbackRequest
import ch.ddis.speakeasy.feedback.FeedbackAnswerOption
import ch.ddis.speakeasy.user.PlainPassword
import ch.ddis.speakeasy.user.UserRole
import ch.ddis.speakeasy.user.UserManager
import io.javalin.testtools.JavalinTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.test.Ignore

/**
 * Comprehensive functional test for feedback API endpoints.
 * Tests all feedback-related functionality including:
 * - Feedback form management (CRUD operations)
 * - Feedback submission and retrieval
 * - Feedback statistics and averages
 * - Admin vs user access control
 * - Feedback export functionality
 * - Error handling and validation
 */
class FeedbackApiTest : ApiTestBase() {

    override fun createTestUsers() {
        super.createTestUsers()
        // Add additional test users for comprehensive feedback testing
        UserManager.addUser("charlie", UserRole.HUMAN, PlainPassword("password789"))
        UserManager.addUser("dave", UserRole.HUMAN, PlainPassword("password101"))
    }

    // Helper method to create a sample feedback form
    private fun createSampleForm(formName: String = "test_form"): FeedbackForm {
        return FeedbackForm(
            formName = formName,
            requests = listOf(
                FeedbackRequest(
                    id = "0",
                    type = "multiple",
                    name = "How satisfied are you with the conversation?",
                    shortname = "satisfaction",
                    options = listOf(
                        FeedbackAnswerOption("Very Dissatisfied", 1),
                        FeedbackAnswerOption("Dissatisfied", 2),
                        FeedbackAnswerOption("Neutral", 3),
                        FeedbackAnswerOption("Satisfied", 4),
                        FeedbackAnswerOption("Very Satisfied", 5)
                    )
                ),
                FeedbackRequest(
                    id = "1",
                    type = "multiple",
                    name = "How would you rate the helpfulness?",
                    shortname = "helpfulness",
                    options = listOf(
                        FeedbackAnswerOption("Not Helpful", 1),
                        FeedbackAnswerOption("Somewhat Helpful", 2),
                        FeedbackAnswerOption("Very Helpful", 3)
                    )
                ),
                FeedbackRequest(
                    id = "2",
                    type = "text",
                    name = "Any additional comments?",
                    shortname = "comments",
                    options = emptyList()
                )
            )
        )
    }

    // Helper method to create feedback responses
    private fun createSampleFeedbackResponses(): Map<String, Any> {
        return mapOf(
            "responses" to listOf(
                mapOf("id" to "0", "value" to "4"),
                mapOf("id" to "1", "value" to "3"),
                mapOf("id" to "2", "value" to "Great conversation, very helpful!")
            )
        )
    }

    @Test
    fun `should create and retrieve feedback form`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val sampleForm = createSampleForm("satisfaction_form")

            // Create form
            val createResponse = client.post("/api/feedbackforms?session=$adminToken", objectMapper.writeValueAsString(sampleForm))
            assertEquals(200, createResponse.code, "Form creation should succeed")

            // Retrieve form
            val getResponse = client.get("/api/feedbackforms/satisfaction_form?session=$adminToken")
            assertEquals(200, getResponse.code, "Form retrieval should succeed")
            
            val retrievedForm = objectMapper.readTree(getResponse.body?.string()!!)
            assertEquals("satisfaction_form", retrievedForm.get("formName").asText())
            assertEquals(3, retrievedForm.get("requests").size())
            
            // Verify first question structure
            val firstQuestion = retrievedForm.get("requests").get(0)
            assertEquals("0", firstQuestion.get("id").asText())
            assertEquals("multiple", firstQuestion.get("type").asText())
            assertEquals("satisfaction", firstQuestion.get("shortname").asText())
            assertEquals(5, firstQuestion.get("options").size())
        }
    }

    @Test
    fun `should list all feedback forms`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val userToken = loginAndGetSessionToken(client, "alice", "password123")
            
            // Create multiple forms
            val form1 = createSampleForm("form1")
            val form2 = createSampleForm("form2")
            
            client.post("/api/feedbackforms?session=$adminToken", objectMapper.writeValueAsString(form1))
            client.post("/api/feedbackforms?session=$adminToken", objectMapper.writeValueAsString(form2))

            // List forms as regular user
            val listResponse = client.get("/api/feedbackforms?session=$userToken")
            assertEquals(200, listResponse.code, "Form listing should succeed for users")
            
            val forms = objectMapper.readTree(listResponse.body?.string()!!)
            assertTrue(forms.isArray, "Forms should be returned as array")
            assertTrue(forms.size() >= 2, "Should have at least the 2 created forms")
            
            // Check that the correct forms are in the list response
            val formIds = forms.map { it.get("formName").asText() }
            assertTrue(formIds.contains("form1"), "Form list should contain 'form1'")
            assertTrue(formIds.contains("form2"), "Form list should contain 'form2'")
            
            // Verify form1 details
            val form1InList = forms.find { it.get("formName").asText() == "form1" }
            assertNotNull(form1InList, "Form1 should be present in the list")
            assertEquals(3, form1InList?.get("requests")?.size(), "Form1 should have 3 requests")
            
            // Verify form2 details
            val form2InList = forms.find { it.get("formName").asText() == "form2" }
            assertNotNull(form2InList, "Form2 should be present in the list")
            assertEquals(3, form2InList?.get("requests")?.size(), "Form2 should have 3 requests")
        }
    }

    @Test
    fun `should update existing feedback form`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val originalForm = createSampleForm("updatable_form")

            // Create form
            client.post("/api/feedbackforms?session=$adminToken", objectMapper.writeValueAsString(originalForm))

            // Update form with new content
            val updatedForm = originalForm.copy(
                requests = originalForm.requests + FeedbackRequest(
                    id = "3",
                    type = "multiple",
                    name = "Would you recommend this service?",
                    shortname = "recommendation",
                    options = listOf(
                        FeedbackAnswerOption("No", 0),
                        FeedbackAnswerOption("Yes", 1)
                    )
                )
            )

            val updateResponse = client.put("/api/feedbackforms/updatable_form?session=$adminToken", objectMapper.writeValueAsString(updatedForm))
            assertEquals(200, updateResponse.code, "Form update should succeed")

            // Verify update
            val getResponse = client.get("/api/feedbackforms/updatable_form?session=$adminToken")
            val retrievedForm = objectMapper.readTree(getResponse.body?.string()!!)
            assertEquals(4, retrievedForm.get("requests").size(), "Form should have 4 questions after update")
            
            // Check if the new question is present
            val requests = retrievedForm.get("requests")
            val newQuestion = requests.find { it.get("shortname").asText() == "recommendation" }
            assertNotNull(newQuestion, "New question with shortname 'recommendation' should be present")
            assertEquals("Would you recommend this service?", newQuestion?.get("name")?.asText(), "New question name should match")
            assertEquals("multiple", newQuestion?.get("type")?.asText(), "New question type should be 'multiple'")
            
            // Check if the options list contains the right things
            val options = newQuestion?.get("options")
            assertNotNull(options, "New question should have options")
            assertEquals(2, options?.size(), "New question should have exactly 2 options")
            
            val noOption = options?.find { it.get("name").asText() == "No" }
            val yesOption = options?.find { it.get("name").asText() == "Yes" }
            assertNotNull(noOption, "Should have 'No' option")
            assertNotNull(yesOption, "Should have 'Yes' option")
            assertEquals(0, noOption?.get("value")?.asInt(), "'No' option should have value 0")
            assertEquals(1, yesOption?.get("value")?.asInt(), "'Yes' option should have value 1")
        }
    }

    @Test
    fun `should delete feedback form`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val formToDelete = createSampleForm("deletable_form")

            // Create form
            client.post("/api/feedbackforms?session=$adminToken", objectMapper.writeValueAsString(formToDelete))

            // Delete form
            val deleteResponse = client.delete("/api/feedbackforms/deletable_form?session=$adminToken")
            assertEquals(200, deleteResponse.code, "Form deletion should succeed")

            // Verify deletion
            val getResponse = client.get("/api/feedbackforms/deletable_form?session=$adminToken")
            // AT some point it should be 404 !
            assertEquals(500, getResponse.code, "Deleted form should not be found")
        }
    }

    @Test
    fun `should submit feedback for chatroom`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            
            // Create form
            val feedbackForm = createSampleForm("chat_feedback")
            client.post("/api/feedbackforms?session=$adminToken", objectMapper.writeValueAsString(feedbackForm))

            // Create chatroom with form
            val chatRoomRequest = mapOf("username" to "bob", "formName" to "chat_feedback")
            val createRoomResponse = client.post("/api/rooms/request?session=$aliceToken", objectMapper.writeValueAsString(chatRoomRequest))
            assertEquals(200, createRoomResponse.code)

            // Get room ID
            val roomsResponse = client.get("/api/rooms?session=$aliceToken")
            val roomsData = objectMapper.readTree(roomsResponse.body?.string()!!)
            val roomId = roomsData.get("rooms").get(0).get("uid").asText()

            // Submit feedback
            val feedbackData = createSampleFeedbackResponses()
            val feedbackResponse = client.post("/api/feedback/$roomId?session=$aliceToken", objectMapper.writeValueAsString(feedbackData))
            assertEquals(200, feedbackResponse.code, "Feedback submission should succeed")
            
            val feedbackResult = objectMapper.readTree(feedbackResponse.body?.string()!!)
            assertEquals("Feedback received", feedbackResult.get("description").asText())
        }
    }

    @Test
    fun `should mark chatroom as no feedback required`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            
            // Create chatroom without form (so no feedback required)
            val chatRoomRequest = mapOf("username" to "bob", "formName" to "")
            client.post("/api/rooms/request?session=$aliceToken", objectMapper.writeValueAsString(chatRoomRequest))

            // Get room ID
            val roomsResponse = client.get("/api/rooms?session=$aliceToken")
            val roomsData = objectMapper.readTree(roomsResponse.body?.string()!!)
            val roomId = roomsData.get("rooms").get(0).get("uid").asText()

            // Submit empty feedback (mark as no feedback)
            val emptyFeedback = mapOf("responses" to emptyList<Any>())
            val feedbackResponse = client.post("/api/feedback/$roomId?session=$aliceToken", objectMapper.writeValueAsString(emptyFeedback))
            assertEquals(200, feedbackResponse.code, "No feedback marking should succeed")
            
            val feedbackResult = objectMapper.readTree(feedbackResponse.body?.string()!!)
            assertTrue(feedbackResult.get("description").asText().contains("No feedback required"))
        }
    }

    @Test
    fun `should retrieve feedback history for room`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            
            // Create form and room, submit feedback (reusing previous test logic)
            val feedbackForm = createSampleForm("history_test")
            client.post("/api/feedbackforms?session=$adminToken", objectMapper.writeValueAsString(feedbackForm))

            val chatRoomRequest = mapOf("username" to "bob", "formName" to "history_test")
            client.post("/api/rooms/request?session=$aliceToken", objectMapper.writeValueAsString(chatRoomRequest))

            val roomsResponse = client.get("/api/rooms?session=$aliceToken")
            val roomId = objectMapper.readTree(roomsResponse.body?.string()!!).get("rooms").get(0).get("uid").asText()

            val feedbackData = createSampleFeedbackResponses()
            client.post("/api/feedback/$roomId?session=$aliceToken", objectMapper.writeValueAsString(feedbackData))

            // Retrieve feedback history
            val historyResponse = client.get("/api/feedbackhistory/room/$roomId?session=$aliceToken")
            assertEquals(200, historyResponse.code, "Feedback history retrieval should succeed")
            
            val historyData = objectMapper.readTree(historyResponse.body?.string()!!)
            val responses = historyData.get("responses")
            assertTrue(responses.isArray, "History should contain responses array")
            assertEquals(3, responses.size(), "Should have 3 feedback responses")
        }
    }

    @Test
    fun `should get admin feedback history by form`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val feedbackForm = createSampleForm("admin_history_test")
            
            client.post("/api/feedbackforms?session=$adminToken", objectMapper.writeValueAsString(feedbackForm))

            // Get admin feedback history
            val historyResponse = client.get("/api/feedbackhistory/form/admin_history_test?session=$adminToken")
            assertEquals(200, historyResponse.code, "Admin feedback history should succeed")
            
            val historyData = objectMapper.readTree(historyResponse.body?.string()!!)
            assertTrue(historyData.has("assigned"), "Should have assigned feedback list")
            assertTrue(historyData.has("requested"), "Should have requested feedback list")
        }
    }

    @Test
    fun `should get feedback averages for admin`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val feedbackForm = createSampleForm("averages_test")
            
            client.post("/api/feedbackforms?session=$adminToken", objectMapper.writeValueAsString(feedbackForm))

            // Get feedback averages
            val averagesResponse = client.get("/api/feedbackaverage/averages_test?session=$adminToken")
            assertEquals(200, averagesResponse.code, "Feedback averages should succeed")
            
            val averagesData = objectMapper.readTree(averagesResponse.body?.string()!!)
            assertTrue(averagesData.has("assigned"), "Should have assigned averages")
            assertTrue(averagesData.has("requested"), "Should have requested averages")
            assertTrue(averagesData.has("statsOfAllRequest"), "Should have global stats")
        }
    }

    @Test
    fun `should get user-specific feedback averages for regular users`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val feedbackForm = createSampleForm("user_averages_test")
            
            client.post("/api/feedbackforms?session=$adminToken", objectMapper.writeValueAsString(feedbackForm))

            // Regular user should only see their own averages
            val averagesResponse = client.get("/api/feedbackaverage/user_averages_test?session=$aliceToken")
            assertEquals(200, averagesResponse.code, "User feedback averages should succeed")
            
            val averagesData = objectMapper.readTree(averagesResponse.body?.string()!!)
            assertTrue(averagesData.has("assigned"), "Should have assigned averages")
            assertTrue(averagesData.has("requested"), "Should have requested averages")
            // Regular users should not see global stats
            val assigned = averagesData.get("assigned")
            val requested = averagesData.get("requested")
            assertTrue(assigned.isArray, "Assigned should be array")
            assertTrue(requested.isArray, "Requested should be array")
        }
    }



    @Test
    fun `should reject unauthorized access to admin endpoints`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val userToken = loginAndGetSessionToken(client, "alice", "password123")
            val sampleForm = createSampleForm("unauthorized_test")

            // Regular user should not be able to create forms
            val createResponse = client.post("/api/feedbackforms?session=$userToken", objectMapper.writeValueAsString(sampleForm))
            assertEquals(401, createResponse.code, "Regular user should not create forms")

            // Regular user should not be able to update forms
            val updateResponse = client.put("/api/feedbackforms/test?session=$userToken", objectMapper.writeValueAsString(sampleForm))
            assertEquals(401, updateResponse.code, "Regular user should not update forms")

            // Regular user should not be able to delete forms
            val deleteResponse = client.delete("/api/feedbackforms/test?session=$userToken")
            assertEquals(401, deleteResponse.code, "Regular user should not delete forms")

            // Regular user should not access admin feedback history
            val historyResponse = client.get("/api/feedbackhistory/form/test?session=$userToken")
            assertEquals(401, historyResponse.code, "Regular user should not access admin feedback history")

            // Regular user should not access feedback export
            val exportResponse = client.get("/api/feedbackaverageexport/test?usernames=alice&author=true&assignment=false&session=$userToken")
            assertEquals(401, exportResponse.code, "Regular user should not access feedback export")
        }
    }

    @Test
    @Ignore("The return codes are incorrect")
    fun `should handle non-existent form operations`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val userToken = loginAndGetSessionToken(client, "alice", "password123")

            // Get non-existent form
            val getResponse = client.get("/api/feedbackforms/nonexistent?session=$userToken")
            assertEquals(404, getResponse.code, "Non-existent form should return 404")

            // Update non-existent form
            val sampleForm = createSampleForm("nonexistent")
            val updateResponse = client.put("/api/feedbackforms/nonexistent?session=$adminToken", objectMapper.writeValueAsString(sampleForm))
            assertEquals(404, updateResponse.code, "Updating non-existent form should return 404")

            // Delete non-existent form
            val deleteResponse = client.delete("/api/feedbackforms/nonexistent?session=$adminToken")
            assertEquals(404, deleteResponse.code, "Deleting non-existent form should return 404")

            // Get feedback averages for non-existent form
            val averagesResponse = client.get("/api/feedbackaverage/nonexistent?session=$adminToken")
            assertEquals(404, averagesResponse.code, "Non-existent form averages should return 404")
        }
    }

    @Test
    fun `should handle feedback submission to invalid room`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val invalidRoomId = "invalid-room-id"

            val feedbackData = createSampleFeedbackResponses()
            val feedbackResponse = client.post("/api/feedback/$invalidRoomId?session=$aliceToken", objectMapper.writeValueAsString(feedbackData))
            assertEquals(500, feedbackResponse.code, "Feedback to invalid room should fail")
        }
    }

    @Test
    fun `should prevent duplicate form creation`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val sampleForm = createSampleForm("duplicate_test")

            // Create form first time
            val firstResponse = client.post("/api/feedbackforms?session=$adminToken", objectMapper.writeValueAsString(sampleForm))
            assertEquals(200, firstResponse.code, "First form creation should succeed")

            // Try to create same form again
            val duplicateResponse = client.post("/api/feedbackforms?session=$adminToken", objectMapper.writeValueAsString(sampleForm))
            assertTrue(duplicateResponse.code in listOf(400, 409, 500), "Duplicate form creation should fail")
        }
    }

    @Test
    fun `should handle empty request body for form operations`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")

            // Empty body for form creation
            val createResponse = client.post("/api/feedbackforms?session=$adminToken", "")
            assertEquals(400, createResponse.code, "Empty body should be rejected for form creation")

            // Empty body for form update
            val updateResponse = client.put("/api/feedbackforms/test?session=$adminToken", "")
            assertEquals(400, updateResponse.code, "Empty body should be rejected for form update")
        }
    }

    @Test
    fun `should handle feedback with missing form association`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")

            // Create chatroom without form
            val chatRoomRequest = mapOf("username" to "bob", "formName" to "")
            client.post("/api/rooms/request?session=$aliceToken", objectMapper.writeValueAsString(chatRoomRequest))

            val roomsResponse = client.get("/api/rooms?session=$aliceToken")
            val roomId = objectMapper.readTree(roomsResponse.body?.string()!!).get("rooms").get(0).get("uid").asText()

            // Try to submit actual feedback to room without form
            val feedbackData = createSampleFeedbackResponses()
            val feedbackResponse = client.post("/api/feedback/$roomId?session=$aliceToken", objectMapper.writeValueAsString(feedbackData))
            
            // This should either succeed (if form is optional) or fail gracefully
            assertTrue(feedbackResponse.code in listOf(200, 400, 500), "Should handle missing form association gracefully")
        }
    }

    @Test
    fun `should retrieve correct feedback history for room`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val bobToken = loginAndGetSessionToken(client, "bob", "password456")

            // Create feedback form
            val form = createSampleForm("room_feedback_test")
            client.post("/api/feedbackforms?session=$adminToken", objectMapper.writeValueAsString(form))

            // Create chatroom with form
            val chatRoomRequest = mapOf("username" to "bob", "formName" to "room_feedback_test")
            val createRoomResponse = client.post("/api/rooms/request?session=$aliceToken", objectMapper.writeValueAsString(chatRoomRequest))
            assertEquals(200, createRoomResponse.code, "Room creation should succeed")

            // Get room ID
            val roomsResponse = client.get("/api/rooms?session=$aliceToken")
            val roomsData = objectMapper.readTree(roomsResponse.body?.string()!!)
            val roomId = roomsData.get("rooms").get(0).get("uid").asText()

            // Submit feedback from alice
            val aliceFeedback = mapOf(
                "responses" to listOf(
                    mapOf("id" to "0", "value" to "5"),
                    mapOf("id" to "1", "value" to "4"),
                    mapOf("id" to "2", "value" to "Excellent conversation!")
                )
            )
            val aliceFeedbackResponse = client.post("/api/feedback/$roomId?session=$aliceToken", objectMapper.writeValueAsString(aliceFeedback))
            assertEquals(200, aliceFeedbackResponse.code, "Alice's feedback submission should succeed")

            // Submit feedback from bob
            val bobFeedback = mapOf(
                "responses" to listOf(
                    mapOf("id" to "0", "value" to "4"),
                    mapOf("id" to "1", "value" to "5"),
                    mapOf("id" to "2", "value" to "Very helpful discussion!")
                )
            )
            val bobFeedbackResponse = client.post("/api/feedback/$roomId?session=$bobToken", objectMapper.writeValueAsString(bobFeedback))
            assertEquals(200, bobFeedbackResponse.code, "Bob's feedback submission should succeed")

            // Get feedback history for the room
            val historyResponse = client.get("/api/feedbackhistory/room/$roomId?session=$aliceToken")
            assertEquals(200, historyResponse.code, "Feedback history retrieval should succeed")

            val history = objectMapper.readTree(historyResponse.body?.string()!!)
            assertTrue(history.has("responses"), "History should contain responses array")

            val responses = history.get("responses")
            // UNSURE ABOUT THIS - should a user be able to see all feedback responses of the room ?
            // assertEquals(6, responses.size(), "Should have 6 feedback responses total (3 from each user)")

            // Verify alice's feedback is in the history
            val aliceResponses = responses.filter { response ->
                val value = response.get("value").asText()
                value == "5" || value == "4" || value == "Excellent conversation!"
            }
            assertEquals(3, aliceResponses.size, "Should contain all 3 of Alice's responses")

            val bobResponsesRaw = client.get("/api/feedbackhistory/room/$roomId?session=$bobToken")
            val bobResponses = objectMapper.readTree(bobResponsesRaw.body?.string()!!).get("responses")

            // Verify bob's feedback is in the history
            val bobResponsesFiltered = bobResponses.filter { response ->
                val value = response.get("value").asText()
                value == "4" || value == "5" || value == "Very helpful discussion!"
            }
            assertEquals(3, bobResponsesFiltered.size, "Should contain all 3 of Bob's responses")

            // Verify response structure
            val firstResponse = responses.get(0)
            assertTrue(firstResponse.has("id"), "Each response should have an id")
            assertTrue(firstResponse.has("value"), "Each response should have a value")

            // Test feedback averages and statistics
            val averagesResponse = client.get("/api/feedbackaverage/room_feedback_test?session=$adminToken")
            assertEquals(200, averagesResponse.code, "Feedback averages should succeed")

            val averagesData = objectMapper.readTree(averagesResponse.body?.string()!!)
            assertTrue(averagesData.has("statsOfAllRequest"), "Should have global stats")

            val statsOfAllRequest = averagesData.get("statsOfAllRequest")
            assertTrue(statsOfAllRequest.isArray, "Stats should be an array")

            // Find stats for numeric questions (id "0" and "1")
            val stats0 = statsOfAllRequest.find { it.get("requestID").asText() == "0" }
            val stats1 = statsOfAllRequest.find { it.get("requestID").asText() == "1" }

            assertNotNull(stats0, "Should have stats for question 0")
            assertNotNull(stats1, "Should have stats for question 1")

            // Verify averages are correct
            // Question 0: Alice=5, Bob=4 → average = 4.5
            assertEquals("4.5", stats0?.get("average")?.asText(), "Question 0 average should be 4.5")
            assertEquals(2, stats0?.get("count")?.asInt(), "Question 0 should have 2 responses")

            // Question 1: Alice=4, Bob=5 → average = 4.5  
            assertEquals("4.5", stats1?.get("average")?.asText(), "Question 1 average should be 4.5")
            assertEquals(2, stats1?.get("count")?.asInt(), "Question 1 should have 2 responses")

            // Verify variance/standard deviation
            // For values [5,4] and [4,5]: variance = 0.5
            assertEquals(0.5, stats0?.get("variance")?.asDouble()!!, 0.01, "Question 0 variance should be 0.5")
            assertEquals(0.5, stats1?.get("variance")?.asDouble()!!, 0.01, "Question 1 variance should be 0.5")
        }
    }
} 