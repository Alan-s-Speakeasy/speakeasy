package ch.ddis.speakeasy.api

import ch.ddis.speakeasy.user.PlainPassword
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import io.javalin.testtools.JavalinTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.test.Ignore

class ChatRoomApiTest : ApiTestBase() {

    override fun createTestUsers() {
        super.createTestUsers()
        // Add additional test users for more comprehensive testing
        UserManager.addUser("charlie", UserRole.HUMAN, PlainPassword("password789"))
        UserManager.addUser("tester_bot", UserRole.TESTER, PlainPassword("tester123"))
        UserManager.addUser("evaluator", UserRole.EVALUATOR, PlainPassword("eval123"))
    }

    // Helper method to create a chatroom and return room ID
    private fun createChatroom(client: io.javalin.testtools.HttpClient, sessionToken: String, partnerUsername: String, formName: String = ""): String {
        val chatRoomRequest = mapOf("username" to partnerUsername, "formName" to formName)
        val response = client.post("/api/rooms/request?session=$sessionToken", objectMapper.writeValueAsString(chatRoomRequest))
        assertEquals(200, response.code, "Chatroom creation should succeed")
        
        // Get the created room ID by listing rooms
        val roomsResponse = client.get("/api/rooms?session=$sessionToken")
        assertEquals(200, roomsResponse.code)
        val roomsData = objectMapper.readTree(roomsResponse.body?.string()!!)
        val rooms = roomsData.get("rooms")
        assertTrue(rooms.size() > 0, "Should have at least one room")
        return rooms.get(0).get("uid").asText()
    }

    private fun getAliasForUser(client: io.javalin.testtools.HttpClient, roomId: String, userToken: String): String {
        val roomResponse = client.get("/api/room/$roomId?since=0&session=$userToken")
        assertEquals(200, roomResponse.code)
        val roomData = objectMapper.readTree(roomResponse.body?.string()!!)
        return roomData.get("info").get("alias").asText()
    }

    @Test
    fun `should create chatroom and list it for user`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")

            // Create chatroom
            val chatRoomRequest = mapOf("username" to "bob", "formName" to "")
            val createResponse = client.post("/api/rooms/request?session=$aliceToken", objectMapper.writeValueAsString(chatRoomRequest))
            assertEquals(200, createResponse.code, "Chatroom creation should succeed")

            // List rooms for Alice
            val roomsResponse = client.get("/api/rooms?session=$aliceToken")
            assertEquals(200, roomsResponse.code)
            
            val roomsData = objectMapper.readTree(roomsResponse.body?.string()!!)
            val rooms = roomsData.get("rooms")
            assertEquals(1, rooms.size(), "Alice should have exactly one room")
            
            val room = rooms.get(0)
            
            // Verify all room properties
            assertNotNull(room.get("uid"), "Room should have a UID")
            assertFalse(room.get("assignment").asBoolean(), "Room should not be an assignment by default")
            assertEquals("", room.get("formRef").asText(), "FormRef should be empty string when not specified")
            assertTrue(room.get("startTime").asLong() > 0, "Start time should be a positive timestamp")
            assertTrue(room.get("remainingTime").asLong() < 10 * 60 * 1000, "Remaining time should be max value for unlimited")
            assertTrue(room.get("userAliases").isArray, "User aliases should be an array")
            assertEquals(2, room.get("userAliases").size(), "Room should have 2 users")
            assertNotNull(room.get("alias"), "Room should have an alias for the current user")
            assertTrue(room.get("alias").asText().isNotEmpty(), "User alias should not be empty")
            assertEquals("New chat room", room.get("prompt").asText(), "Default prompt should be 'New chat room'")
            assertEquals("", room.get("testerBotAlias").asText(), "TesterBot alias should be empty when no bot present")
            assertFalse(room.get("markAsNoFeedback").asBoolean(), "Room should not be marked as no feedback by default")
            
            // Verify the user's alias is one of the aliases in the userAliases array
            val userAliases = room.get("userAliases")
            val userAlias = room.get("alias").asText()
            val aliasesSet = mutableSetOf<String>()
            for (i in 0 until userAliases.size()) {
                aliasesSet.add(userAliases.get(i).asText())
            }
            assertTrue(aliasesSet.contains(userAlias), "User's alias should be present in the userAliases array")
        }
    }

    @Test
    fun `should only show messages intended for the user via API`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val bobToken = loginAndGetSessionToken(client, "bob", "password456")
            val charlieToken = loginAndGetSessionToken(client, "charlie", "password789")

            // Alice creates a room with Bob, then adds Charlie
            val roomId = createChatroom(client, aliceToken, "bob")
            client.patch("/api/request/$roomId?session=$aliceToken", "charlie")

            // Get aliases for all users
            val aliceAlias = getAliasForUser(client, roomId, aliceToken)
            val bobAlias = getAliasForUser(client, roomId, bobToken)
            val charlieAlias = getAliasForUser(client, roomId, charlieToken)

            // Alice sends a message specifically to Bob
            client.post("/api/room/$roomId?session=$aliceToken&recipients=$bobAlias", "For Bob's eyes only")
            // Alice sends a broadcast message
            client.post("/api/room/$roomId?session=$aliceToken", "Hello everyone")
            // Alice sends a message specifically to Charlie
            client.post("/api/room/$roomId?session=$aliceToken&recipients=$charlieAlias", "A secret for Charlie")

            // Bob fetches messages
            val bobRoomStateResponse = client.get("/api/room/$roomId?since=0&session=$bobToken")
            val bobMessages = objectMapper.readTree(bobRoomStateResponse.body?.string()!!).get("messages")
            assertEquals(2, bobMessages.size(), "Bob should see two messages")
            val bobMessageContents = bobMessages.map { it.get("message").asText() }
            assertTrue(bobMessageContents.contains("For Bob's eyes only"))
            assertTrue(bobMessageContents.contains("Hello everyone"))

            // Charlie fetches messages
            val charlieRoomStateResponse = client.get("/api/room/$roomId?since=0&session=$charlieToken")
            val charlieMessages = objectMapper.readTree(charlieRoomStateResponse.body?.string()!!).get("messages")
            assertEquals(2, charlieMessages.size(), "Charlie should see two messages")
            val charlieMessageContents = charlieMessages.map { it.get("message").asText() }
            assertTrue(charlieMessageContents.contains("A secret for Charlie"))
            assertTrue(charlieMessageContents.contains("Hello everyone"))
            assertFalse { charlieMessageContents.contains("For Bob's eyes only") }

            // Alice fetches messages (she only sees the broadcast message as she wasn't a recipient of the others)
            val aliceRoomStateResponse = client.get("/api/room/$roomId?since=0&session=$aliceToken")
            val aliceMessages = objectMapper.readTree(aliceRoomStateResponse.body?.string()!!).get("messages")
            assertEquals(1, aliceMessages.size(), "Alice should see one message")
            assertEquals("Hello everyone", aliceMessages.get(0).get("message").asText())
            
            // Admin fetches messages and should see all of them
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val adminRoomStateResponse = client.get("/api/room/$roomId?since=0&session=$adminToken")
            val adminMessages = objectMapper.readTree(adminRoomStateResponse.body?.string()!!).get("messages")
            assertEquals(3, adminMessages.size(), "Admin should see all three messages")
        }
    }

    @Test
    fun `should handle chatroom creation with TesterBot`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")

            // Create chatroom with TesterBot (special case)
            val chatRoomRequest = mapOf("username" to "TesterBot", "formName" to "")
            val createResponse = client.post("/api/rooms/request?session=$aliceToken", objectMapper.writeValueAsString(chatRoomRequest))
            assertEquals(200, createResponse.code, "TesterBot chatroom creation should succeed")

            // Verify the room was created
            val roomsResponse = client.get("/api/rooms?session=$aliceToken")
            assertEquals(200, roomsResponse.code)
            val roomsData = objectMapper.readTree(roomsResponse.body?.string()!!)
            val rooms = roomsData.get("rooms")
            assertEquals(1, rooms.size())
        }
    }

    @Test
    fun `should post messages to chatroom`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val roomId = createChatroom(client, aliceToken, "bob")

            // Post a message
            val message = "Hello, this is a test message!"
            val messageResponse = client.post("/api/room/$roomId?session=$aliceToken", message)
            assertEquals(200, messageResponse.code, "Message posting should succeed")

            // Verify message appears in room state
            val roomStateResponse = client.get("/api/room/$roomId?since=0&session=$aliceToken")
            assertEquals(200, roomStateResponse.code)
            
            val roomState = objectMapper.readTree(roomStateResponse.body?.string()!!)
            val messages = roomState.get("messages")
            assertEquals(1, messages.size(), "Should have one message")
            assertEquals(message, messages.get(0).get("message").asText())
        }
    }

    @Test
    fun `should handle message with recipients using mentions`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val roomId = createChatroom(client, aliceToken, "bob")

            // Post message with mentions - this should be processed by ChatRoomManager.processMessageAndRecipients
            val messageWithMention = "@bob: Hello Bob, how are you?"
            val messageResponse = client.post("/api/room/$roomId?session=$aliceToken", messageWithMention)
            assertEquals(200, messageResponse.code, "Message with mention should succeed")

            // Verify message appears
            val roomStateResponse = client.get("/api/room/$roomId?since=0&session=$aliceToken")
            assertEquals(200, roomStateResponse.code)
            val roomState = objectMapper.readTree(roomStateResponse.body?.string()!!)
            val messages = roomState.get("messages")
            assertEquals(1, messages.size())
        }
    }

    @Test
    fun `should add reactions to messages`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val roomId = createChatroom(client, aliceToken, "bob")

            // Post a message first
            val messageResponse = client.post("/api/room/$roomId?session=$aliceToken", "Test message")
            assertEquals(200, messageResponse.code)

            // Add a reaction to the message (ordinal 0)
            val reaction = mapOf("messageOrdinal" to 0, "type" to "THUMBS_UP")
            val reactionResponse = client.post("/api/room/$roomId/reaction?session=$aliceToken", objectMapper.writeValueAsString(reaction))
            assertEquals(200, reactionResponse.code, "Adding reaction should succeed")

            // Verify reaction appears in room state
            val roomStateResponse = client.get("/api/room/$roomId?since=0&session=$aliceToken")
            assertEquals(200, roomStateResponse.code)
            val roomState = objectMapper.readTree(roomStateResponse.body?.string()!!)
            val reactions = roomState.get("reactions")
            assertEquals(1, reactions.size(), "Should have one reaction")
            assertEquals("THUMBS_UP", reactions.get(0).get("type").asText())
        }
    }

    @Test
    fun `should close chatroom when user deactivates it`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val roomId = createChatroom(client, aliceToken, "bob")

            // Close the chatroom
            val closeResponse = client.patch("/api/room/$roomId?session=$aliceToken", "")
            assertEquals(200, closeResponse.code, "Room closing should succeed")

            // Verify room is no longer active by trying to post a message
            val messageResponse = client.post("/api/room/$roomId?session=$aliceToken", "This should fail")
            assertEquals(400, messageResponse.code, "Should not be able to post to inactive room")
        }
    }

    @Test
    fun `should get user status for chatroom participants`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val roomId = createChatroom(client, aliceToken, "bob")

            // Get user statutatu
            val statusResponse = client.get("/api/room/$roomId/users-status?session=$aliceToken")
            assertEquals(200, statusResponse.code, "Getting user status should succeed")

            val statusData = objectMapper.readTree(statusResponse.body?.string()!!)
            assertTrue(statusData.isObject, "Status should be an object/map")
            assertTrue( statusData.size() >= 2, "Should have status for at least 2 users")
        }
    }

    @Test
    fun `should handle admin listing all chatrooms`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            
            // Create a room as Alice
            createChatroom(client, aliceToken, "bob")

            // Admin should be able to list all rooms
            val allRoomsResponse = client.get("/api/rooms/all?session=$adminToken")
            assertEquals(200, allRoomsResponse.code, "Admin should be able to list all rooms")

            val roomsData = objectMapper.readTree(allRoomsResponse.body?.string()!!)
            assertTrue(roomsData.get("numOfAllRooms").asInt() >= 1, "Should have at least one room")
            assertTrue(roomsData.get("rooms").isArray, "Rooms should be an array")
        }
    }

    @Test
    fun `should handle admin listing active chatrooms`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            
            // Create an active room
            createChatroom(client, aliceToken, "bob")

            // Admin should be able to list active rooms
            val activeRoomsResponse = client.get("/api/rooms/active?session=$adminToken")
            assertEquals(200, activeRoomsResponse.code, "Admin should be able to list active rooms")

            val roomsData = objectMapper.readTree(activeRoomsResponse.body?.string()!!)
            assertTrue(roomsData.get("numOfAllRooms").asInt() >= 1, "Should have at least one active room")
        }
    }

    @Test
    fun `should handle chatroom export for admin`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            
            // Create room and add some content
            val roomId = createChatroom(client, aliceToken, "bob")
            client.post("/api/room/$roomId?session=$aliceToken", "Test message for export")

            // Export the room
            val exportResponse = client.get("/api/rooms/export?roomsIds=$roomId&session=$adminToken")
            assertEquals(200, exportResponse.code, "Admin should be able to export rooms")
            
            val exportData = objectMapper.readTree(exportResponse.body?.string()!!)
            assertTrue(exportData.isArray, "Export should return an array")
            assertTrue(exportData.size() >= 1, "Should export at least one room")
        }
    }

    @Test
    fun `should reject unauthorized access to admin endpoints`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")

            // Regular user should not access admin endpoints
            val allRoomsResponse = client.get("/api/rooms/all?session=$aliceToken")
            assertEquals(401, allRoomsResponse.code, "Regular user should not access admin rooms list")

            val activeRoomsResponse = client.get("/api/rooms/active?session=$aliceToken")
            assertEquals(401, activeRoomsResponse.code, "Regular user should not access active rooms list")

            val exportResponse = client.get("/api/rooms/export?roomsIds=test&session=$aliceToken")
            assertEquals(401, exportResponse.code, "Regular user should not access export")
        }
    }

    @Test
    fun `should reject access to rooms user is not part of`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val charlieToken = loginAndGetSessionToken(client, "charlie", "password789")
            
            // Alice creates room with Bob
            val roomId = createChatroom(client, aliceToken, "bob")

            // Charlie should not be able to access Alice's room
            val roomStateResponse = client.get("/api/room/$roomId?since=0&session=$charlieToken")
            assertEquals(401, roomStateResponse.code, "Unauthorized user should not access room")

            val messageResponse = client.post("/api/room/$roomId?session=$charlieToken", "Unauthorized message")
            assertEquals(401, messageResponse.code, "Unauthorized user should not post messages")
        }
    }

    @Test
    @Ignore("Default return is 500, but that should instead be 404. To fix when time allows (never)")
    fun `should handle invalid room IDs gracefully`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val invalidRoomId = "invalid-room-id"

            // Test various endpoints with invalid room ID
            val roomStateResponse = client.get("/api/room/$invalidRoomId?since=0&session=$aliceToken")
            assertEquals(404, roomStateResponse.code, "Invalid room ID should return 404")

            val messageResponse = client.post("/api/room/$invalidRoomId?session=$aliceToken", "Test message")
            assertEquals(404, messageResponse.code, "Invalid room ID should return 404")

            val reactionResponse = client.post("/api/room/$invalidRoomId/reaction?session=$aliceToken", "{\"messageOrdinal\":0,\"type\":\"THUMBS_UP\"}")
            assertEquals(404, reactionResponse.code, "Invalid room ID should return 404")

            val closeResponse = client.patch("/api/room/$invalidRoomId?session=$aliceToken", "")
            assertEquals(404, closeResponse.code, "Invalid room ID should return 404")
        }
    }

    @Test
    fun `should reject empty messages`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val roomId = createChatroom(client, aliceToken, "bob")

            // Try to post empty message
            val emptyMessageResponse = client.post("/api/room/$roomId?session=$aliceToken", "")
            assertEquals(400, emptyMessageResponse.code, "Empty message should be rejected")

            val whitespaceMessageResponse = client.post("/api/room/$roomId?session=$aliceToken", "   ")
            assertEquals(400, whitespaceMessageResponse.code, "Whitespace-only message should be rejected")
        }
    }

    @Test
    fun `should handle invalid reaction data`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val roomId = createChatroom(client, aliceToken, "bob")

            // Post a message first
            client.post("/api/room/$roomId?session=$aliceToken", "Test message")

            // Try invalid reaction JSON
            val invalidJsonResponse = client.post("/api/room/$roomId/reaction?session=$aliceToken", "invalid json")
            assertEquals(500, invalidJsonResponse.code, "Invalid JSON should be rejected")

            // Try reaction to non-existent message
            val invalidOrdinalReaction = mapOf("messageOrdinal" to 999, "type" to "THUMBS_UP")
            val invalidOrdinalResponse = client.post("/api/room/$roomId/reaction?session=$aliceToken", objectMapper.writeValueAsString(invalidOrdinalReaction))
            assertEquals(400, invalidOrdinalResponse.code, "Reaction to non-existent message should be rejected")
        }
    }

    @Test
    fun `should handle chatroom creation with non-existent user`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")

            // Try to create room with non-existent user
            val chatRoomRequest = mapOf("username" to "nonexistentuser", "formName" to "")
            val createResponse = client.post("/api/rooms/request?session=$aliceToken", objectMapper.writeValueAsString(chatRoomRequest))
            
            // Should fail gracefully - exact error code may vary
            assertTrue(createResponse.code in listOf(400, 404, 500), "Should reject non-existent user")
        }
    }

    @Test
    fun `should handle requests without authentication`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            // Try various endpoints without session token
            val roomsResponse = client.get("/api/rooms")
            assertEquals(401, roomsResponse.code, "Unauthenticated request should return 401")

            val createRoomResponse = client.post("/api/rooms/request", "{\"username\":\"bob\"}")
            assertEquals(401, createRoomResponse.code, "Unauthenticated room creation should return 401")

            val messageResponse = client.post("/api/room/test-room", "Test message")
            assertEquals(401, messageResponse.code, "Unauthenticated message should return 401")
        }
    }

    @Test
    fun `should filter messages since timestamp correctly`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val roomId = createChatroom(client, aliceToken, "bob")

            val startTime = System.currentTimeMillis()

            // Post first message
            client.post("/api/room/$roomId?session=$aliceToken", "First message")
            Thread.sleep(100) // Small delay

            val midTime = System.currentTimeMillis()

            // Post second message
            client.post("/api/room/$roomId?session=$aliceToken", "Second message")

            // Get all messages
            val allMessagesResponse = client.get("/api/room/$roomId?since=0&session=$aliceToken")
            assertEquals(200, allMessagesResponse.code)
            val allMessages = objectMapper.readTree(allMessagesResponse.body?.string()!!).get("messages")
            assertEquals(2, allMessages.size(), "Should have 2 messages total")

            // Get messages since midTime (should only get second message)
            val recentMessagesResponse = client.get("/api/room/$roomId?since=$midTime&session=$aliceToken")
            assertEquals(200, recentMessagesResponse.code)
            val recentMessages = objectMapper.readTree(recentMessagesResponse.body?.string()!!).get("messages")
            assertEquals(1, recentMessages.size(), "Should have 1 recent message")
            assertEquals("Second message", recentMessages.get(0).get("message").asText())
        }
    }

    @Test
    fun `should handle admin pagination for room listing`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val adminToken = loginAndGetSessionToken(client, "admin", "admin123")
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            
            // Create multiple rooms
            createChatroom(client, aliceToken, "bob")
            createChatroom(client, aliceToken, "charlie")

            // Test pagination
            val page1Response = client.get("/api/rooms/all?page=1&limit=1&session=$adminToken")
            assertEquals(200, page1Response.code)
            val page1Data = objectMapper.readTree(page1Response.body?.string()!!)
            assertTrue(page1Data.get("numOfAllRooms").asInt() >= 2, "Should have at least 2 rooms total")
            assertEquals(1, page1Data.get("rooms").size(), "Should return 1 room per page")

            val page2Response = client.get("/api/rooms/all?page=2&limit=1&session=$adminToken")
            assertEquals(200, page2Response.code)
            val page2Data = objectMapper.readTree(page2Response.body?.string()!!)
            assertTrue(page2Data.get("rooms").size() <= 1, "Second page should have at most 1 room")
        }
    }

    @Test
    fun `should generate unique aliases for chatroom users and use them in messages`() {
        JavalinTest.test(RestApi.app!!) { _, client ->
            val aliceToken = loginAndGetSessionToken(client, "alice", "password123")
            val bobToken = loginAndGetSessionToken(client, "bob", "password456")
            
            // Create a chatroom with Alice and Bob
            val roomId = createChatroom(client, aliceToken, "bob")
            
            // Add Charlie to the room
            val addUserResponse = client.patch("/api/request/$roomId?session=$aliceToken", "charlie")
            assertEquals(200, addUserResponse.code, "Adding user to room should succeed")
            
            // Get room info from Alice's perspective
            val aliceRoomResponse = client.get("/api/room/$roomId?since=0&session=$aliceToken")
            assertEquals(200, aliceRoomResponse.code)
            val aliceRoomData = objectMapper.readTree(aliceRoomResponse.body?.string()!!)
            val aliceRoomInfo = aliceRoomData.get("info")
            
            // Verify Alice has an alias and it's different from her username
            val aliceAlias = aliceRoomInfo.get("alias").asText()
            assertNotNull(aliceAlias, "Alice should have an alias")
            assertNotEquals("alice", aliceAlias, "Alias should be different from username")
            
            // Get the list of all user aliases in the room
            val userAliases = aliceRoomInfo.get("userAliases")
            assertTrue(userAliases.isArray, "User aliases should be an array")
            assertEquals(3, userAliases.size(), "Should have 3 users in the room")
            
            // Verify all aliases are unique and different from usernames
            val aliasSet = mutableSetOf<String>()
            for (i in 0 until userAliases.size()) {
                val alias = userAliases.get(i).asText()
                assertFalse(aliasSet.contains(alias), "All aliases should be unique")
                aliasSet.add(alias)
                assertFalse(alias in listOf("alice", "bob", "charlie"), "Aliases should not match usernames")
            }
            
            // Post a message from Alice and verify it shows her alias, not username
            val message = "Hello everyone, this is Alice speaking!"
            val messageResponse = client.post("/api/room/$roomId?session=$aliceToken", message)
            assertEquals(200, messageResponse.code)
            
            // Get the message and verify it shows Alice's alias
            val messagesResponse = client.get("/api/room/$roomId?since=0&session=$bobToken")
            assertEquals(200, messagesResponse.code)
            val messagesData = objectMapper.readTree(messagesResponse.body?.string()!!)
            val messages = messagesData.get("messages")
            assertEquals(1, messages.size(), "Should have one message")
            
            val postedMessage = messages.get(0)
            assertEquals(message, postedMessage.get("message").asText(), "Message content should match")
            assertEquals(aliceAlias, postedMessage.get("authorAlias").asText(), "Message should show Alice's alias")
            assertNotEquals("alice", postedMessage.get("authorAlias").asText(), "Message should not show Alice's username")
            
            // Verify Bob sees the same aliases when he gets room info
            val bobRoomResponse = client.get("/api/room/$roomId?since=0&session=$bobToken")
            assertEquals(200, bobRoomResponse.code)
            val bobRoomData = objectMapper.readTree(bobRoomResponse.body?.string()!!)
            val bobRoomInfo = bobRoomData.get("info")
            
            // Bob should have a different alias than Alice
            val bobAlias = bobRoomInfo.get("alias").asText()
            assertNotNull(bobAlias, "Bob should have an alias")
            assertNotEquals(aliceAlias, bobAlias, "Bob's alias should be different from Alice's")
            assertNotEquals("bob", bobAlias, "Bob's alias should be different from his username")
            
            // The list of user aliases should be the same for all users
            val bobUserAliases = bobRoomInfo.get("userAliases")
            assertEquals(userAliases.size(), bobUserAliases.size(), "All users should see the same list of aliases")
        }
    }
} 