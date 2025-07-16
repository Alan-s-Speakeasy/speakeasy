package ch.ddis.speakeasy.api

import io.javalin.testtools.JavalinTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LoginAndChatRoomApiTest : ApiTestBase() {

    @Test
    fun `should login user and create chatroom via HTTP API`() {
        JavalinTest.test(RestApi.app!!) { server, client ->
            
            // Step 1: Login as Alice
            val loginRequest = mapOf(
                "username" to "alice",
                "password" to "password123"
            )
            val loginRequestBody = objectMapper.writeValueAsString(loginRequest)
            
            val loginResponse = client.post("/api/login", loginRequestBody)

            // Verify login was successful
            assertEquals(200, loginResponse.code, "Login should succeed - " + loginResponse.message)
            
            val loginResponseBody = loginResponse.body?.string()
            assertNotNull(loginResponseBody, "Login response body should not be null")
            assertTrue(loginResponseBody!!.isNotEmpty(), "Login response should have body")
            
            val loginData = objectMapper.readTree(loginResponseBody)
            val sessionToken = loginData.get("sessionToken").asText()
            assertNotNull(sessionToken, "Session token should be present")
            assertTrue(sessionToken.isNotEmpty(), "Session token should not be empty")

            // Step 2: Request a new chatroom with Bob
            val chatRoomRequest = mapOf(
                "username" to "bob",
                "formName" to ""
            )
            val chatRoomRequestBody = objectMapper.writeValueAsString(chatRoomRequest)

            val chatRoomResponse = client.post("/api/rooms/request?session=$sessionToken", chatRoomRequestBody)

            // Verify chatroom creation was successful
            assertEquals(200, chatRoomResponse.code, "Chatroom creation should succeed" + chatRoomResponse.message)
            
            val chatRoomResponseBody = chatRoomResponse.body?.string()
            assertNotNull(chatRoomResponseBody, "Chatroom response body should not be null")
            
            val chatRoomData = objectMapper.readTree(chatRoomResponseBody!!)
            assertEquals("Chatroom created", chatRoomData.get("description").asText(), "Success message should be returned")

            // Step 3: Verify the chatroom was actually created by listing user's rooms
            val roomsResponse = client.get("/api/rooms?session=$sessionToken")

            assertEquals(200, roomsResponse.code, "Should be able to list rooms")
            
            val roomsResponseBody = roomsResponse.body?.string()
            assertNotNull(roomsResponseBody, "Rooms response body should not be null")
            
            val roomsData = objectMapper.readTree(roomsResponseBody!!)
            val rooms = roomsData.get("rooms")
            assertTrue(rooms.isArray, "Rooms should be an array")
            assertEquals(1, rooms.size(), "Should have exactly one room")
            
            val room = rooms.get(0)
            assertNotNull(room.get("uid"), "Room should have a UID")
            assertTrue(room.get("uid").asText().isNotEmpty(), "Room UID should not be empty")
        }
    }

    @Test
    fun `should handle invalid login credentials`() {
        JavalinTest.test(RestApi.app!!) { server, client ->
            
            // Try to login with wrong password
            val invalidLoginRequest = mapOf(
                "username" to "alice",
                "password" to "wrongpassword"
            )
            val requestBody = objectMapper.writeValueAsString(invalidLoginRequest)
            
            val response = client.post("/api/login", requestBody)

            // Should return 401 Unauthorized
            assertEquals(401, response.code, "Invalid login should return 401")
            
            val responseBody = response.body?.string()
            assertNotNull(responseBody, "Error response should have body")
        }
    }

    @Test
    fun `should reject chatroom request without authentication`() {
        JavalinTest.test(RestApi.app!!) { server, client ->
            
            // Try to create chatroom without authentication
            val chatRoomRequest = mapOf(
                "username" to "bob",
                "formName" to ""
            )
            val requestBody = objectMapper.writeValueAsString(chatRoomRequest)

            val response = client.post("/api/rooms/request", requestBody)

            // Should return 401 Unauthorized
            assertEquals(401, response.code, "Unauthenticated request should return 401")
        }
    }

    @Test
    fun `should reject chatroom request with non-existent user`() {
        JavalinTest.test(RestApi.app!!) { server, client ->
            
            // Step 1: Login as Alice
            val loginRequest = mapOf(
                "username" to "alice",
                "password" to "password123"
            )
            val loginRequestBody = objectMapper.writeValueAsString(loginRequest)

            val loginResponse = client.post("/api/login", loginRequestBody)
            assertEquals(200, loginResponse.code, "Login should succeed")
            
            val loginResponseBody = loginResponse.body?.string()
            val loginData = objectMapper.readTree(loginResponseBody!!)
            val sessionToken = loginData.get("sessionToken").asText()

            // Step 2: Try to request chatroom with non-existent user
            val chatRoomRequest = mapOf(
                "username" to "nonexistentuser",
                "formName" to ""
            )
            val chatRoomRequestBody = objectMapper.writeValueAsString(chatRoomRequest)

            val chatRoomResponse = client.post("/api/rooms/request?session=$sessionToken", chatRoomRequestBody)

            assertTrue(
                chatRoomResponse.code in listOf(400, 404, 500),
                "Request with non-existent user should fail, got: ${chatRoomResponse.code}"
            )
        }
    }

    @Test
    fun `should allow admin to access admin endpoints`() {
        JavalinTest.test(RestApi.app!!) { server, client ->
            
            // Login as admin
            val loginRequest = mapOf(
                "username" to "admin",
                "password" to "admin123"
            )
            val loginRequestBody = objectMapper.writeValueAsString(loginRequest)
            
            val loginResponse = client.post("/api/login", loginRequestBody)
            assertEquals(200, loginResponse.code, "Admin login should succeed")
            
            val loginResponseBody = loginResponse.body?.string()
            val loginData = objectMapper.readTree(loginResponseBody!!)
            val sessionToken = loginData.get("sessionToken").asText()

            // Try to access admin endpoint (list all users)
            val usersResponse = client.get("/api/user/list?session=$sessionToken")

            assertEquals(200, usersResponse.code, "Admin should be able to list users")
            
            val usersResponseBody = usersResponse.body?.string()
            assertNotNull(usersResponseBody, "Users response should have body")
            
            val usersData = objectMapper.readTree(usersResponseBody!!)
            assertTrue(usersData.isArray, "Users response should be an array")
            assertTrue(usersData.size() >= 3, "Should have at least 3 test users")
        }
    }

    @Test
    fun `should reject regular user from admin endpoints`() {
        JavalinTest.test(RestApi.app!!) { server, client ->
            
            // Login as regular user (Alice)
            val loginRequest = mapOf(
                "username" to "alice",
                "password" to "password123"
            )
            val loginRequestBody = objectMapper.writeValueAsString(loginRequest)
            
            val loginResponse = client.post("/api/login", loginRequestBody)
            assertEquals(200, loginResponse.code, "User login should succeed")
            
            val loginResponseBody = loginResponse.body?.string()
            val loginData = objectMapper.readTree(loginResponseBody!!)
            val sessionToken = loginData.get("sessionToken").asText()

            // Try to access admin endpoint (list all users)
            val usersResponse = client.get("/api/user/list?session=$sessionToken")

            // Should return 401 Unauthorized
            assertEquals(401, usersResponse.code, "Regular user should not access admin endpoints")
        }
    }
} 