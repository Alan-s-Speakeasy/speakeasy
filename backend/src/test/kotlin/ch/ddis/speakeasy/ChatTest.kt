package ch.ddis.speakeasy

import ch.ddis.speakeasy.chat.*
import ch.ddis.speakeasy.db.DatabaseHandler
import ch.ddis.speakeasy.user.PlainPassword
import ch.ddis.speakeasy.user.SessionId
import ch.ddis.speakeasy.user.UserManager
import ch.ddis.speakeasy.user.UserRole
import org.jetbrains.exposed.sql.Database
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/**
 * Simple test for chat message handling.
 * This demonstrates how to test chat rooms and message functionality.
 */
class ChatTest {

    private lateinit var testDatabase: Database
    private lateinit var testDataDir: File

    @BeforeTest
    fun setup() {
        // Create temporary test data directory
        testDataDir = Files.createTempDirectory("speakeasy_chat_test").toFile()
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

        // Initialize ChatRoomManager (this will create the chatlogs directory)
        ChatRoomManager.init(testConfig)
    }

    @AfterTest
    fun cleanup() {
        DatabaseHandler.reset()
        
        // Clean up test data directory
        testDataDir.deleteRecursively()
    }

    @Test
    fun `should create chat room and handle messages`() {
        // Create test users
        UserManager.addUser("alice", UserRole.HUMAN, PlainPassword("password1"))
        UserManager.addUser("bob", UserRole.HUMAN, PlainPassword("password2"))

        val aliceId = UserManager.getUserIdFromUsername("alice")!!
        val bobId = UserManager.getUserIdFromUsername("bob")!!

        // Create a simple chat room (not logged to avoid file dependencies)
        val chatRoom = ChatRoom(
            assignment = false,
            formRef = "test-form",
            users = mutableMapOf(
                aliceId to "Alice",
                bobId to "Bob"
            ),
            prompt = "Test conversation"
        )

        // Verify chat room is active
        assertTrue(chatRoom.active)
        assertEquals(2, chatRoom.users.size)
        assertEquals("Test conversation", chatRoom.prompt)

        // Add first message from Alice
        val message1 = ChatMessage(
            message = "Hello Bob!",
            authorUserId = aliceId,
            authorAlias = "Alice",
            authorSessionId = SessionId.INVALID,
            ordinal = chatRoom.nextMessageOrdinal,
            recipients = setOf("Bob", "Alice")
        )

        chatRoom.addMessage(message1)

        // Add second message from Bob
        val message2 = ChatMessage(
            message = "Hi Alice! How are you?",
            authorUserId = bobId,
            authorAlias = "Bob", 
            authorSessionId = SessionId.INVALID,
            ordinal = chatRoom.nextMessageOrdinal,
            recipients = setOf("Bob", "Alice")
        )

        chatRoom.addMessage(message2)

        // Verify messages were added
        val allMessages = chatRoom.getAllMessages()
        assertEquals(2, allMessages.size)

        val firstMessage = allMessages[0]
        assertEquals("Hello Bob!", firstMessage.message)
        assertEquals("Alice", firstMessage.authorAlias)
        assertEquals(aliceId, firstMessage.authorUserId)

        val secondMessage = allMessages[1]
        assertEquals("Hi Alice! How are you?", secondMessage.message)
        assertEquals("Bob", secondMessage.authorAlias)
        assertEquals(bobId, secondMessage.authorUserId)
    }

    @Test
    fun `should handle message reactions`() {
        // Create test user
        UserManager.addUser("testuser", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("testuser")!!

        // Create chat room
        val chatRoom = ChatRoom(
            assignment = false,
            formRef = "test-form",
            users = mutableMapOf(userId to "TestUser"),
            prompt = "Test reactions"
        )

        // Add a message
        val message = ChatMessage(
            message = "Great job!",
            authorUserId = userId,
            authorAlias = "TestUser",
            authorSessionId = SessionId.INVALID,
            ordinal = 0,
            recipients = setOf("TestUser")
        )

        chatRoom.addMessage(message)

        // Add a reaction to the message
        val reaction = ChatMessageReaction(
            messageOrdinal = 0,
            type = ChatMessageReactionType.THUMBS_UP
        )

        chatRoom.addReaction(reaction)

        // Verify reaction was added
        val allReactions = chatRoom.getAllReactions()
        assertEquals(1, allReactions.size)
        assertEquals(ChatMessageReactionType.THUMBS_UP, allReactions[0].type)
        assertEquals(0, allReactions[0].messageOrdinal)
    }

    @Test
    fun `should filter messages by time for users`() {
        // Create test users
        UserManager.addUser("user1", UserRole.HUMAN, PlainPassword("password1"))
        UserManager.addUser("user2", UserRole.HUMAN, PlainPassword("password2"))

        val user1Id = UserManager.getUserIdFromUsername("user1")!!
        val user2Id = UserManager.getUserIdFromUsername("user2")!!

        // Create chat room
        val chatRoom = ChatRoom(
            assignment = false,
            formRef = "test-form",
            users = mutableMapOf(
                user1Id to "User1",
                user2Id to "User2"
            )
        )

        val timestamp1 = System.currentTimeMillis()
        
        // Add first message
        val message1 = ChatMessage(
            message = "First message",
            authorUserId = user1Id,
            authorAlias = "User1",
            authorSessionId = SessionId.INVALID,
            ordinal = 0,
            recipients = setOf("User1", "User2"),
            time = timestamp1
        )
        chatRoom.addMessage(message1)

        // Wait a bit and add second message
        Thread.sleep(10)
        val timestamp2 = System.currentTimeMillis()
        
        val message2 = ChatMessage(
            message = "Second message", 
            authorUserId = user2Id,
            authorAlias = "User2",
            authorSessionId = SessionId.INVALID,
            ordinal = 1,
            recipients = setOf("User1", "User2"),
            time = timestamp2
        )
        chatRoom.addMessage(message2)

        // Get messages since timestamp between the two messages
        val messagesSince = chatRoom.getMessagesSince(timestamp1 + 5, user1Id)
        
        // Should only get the second message
        assertEquals(1, messagesSince.size)
        assertEquals("Second message", messagesSince[0].message)
    }

    @Test
    fun `should create and test simple event listener`() {
        // Create a simple test event listener
        var messageReceived = false
        var roomReceived = false

        val testListener = object : ChatEventListener {
            override val isActive = true

            override fun onNewRoom(chatRoom: ChatRoom) {
                roomReceived = true
            }

            override fun onMessage(chatMessage: ChatMessage, chatRoom: ChatRoom) {
                messageReceived = true
            }

            override fun onReaction(chatMessageReaction: ChatMessageReaction, chatRoom: ChatRoom) {
                // Not tested in this simple example
            }
        }

        // Create user and chat room
        UserManager.addUser("listenertest", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("listenertest")!!

        val chatRoom = ChatRoom(
            assignment = false,
            formRef = "test-form",
            users = mutableMapOf(userId to "ListenerTest")
        )

        // Add listener to chat room
        chatRoom.addListener(testListener)
        assertTrue(roomReceived, "Listener should receive room notification")

        // Add a message
        val message = ChatMessage(
            message = "Test message for listener",
            authorUserId = userId,
            authorAlias = "ListenerTest",
            authorSessionId = SessionId.INVALID,
            ordinal = 0,
            recipients = setOf("ListenerTest")
        )

        chatRoom.addMessage(message)
        assertTrue(messageReceived, "Listener should receive message notification")
    }

    @Test
    fun `ChatRoom should correctly increment ordinals`() {
        // Create test user
        UserManager.addUser("ordinaltest", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("ordinaltest")!!

        // Create chat room
        val chatRoom = ChatRoom(
            assignment = false,
            formRef = "ordinal-test-form",
            users = mutableMapOf(userId to "OrdinalTest")
        )

        // Verify initial ordinal is 0
        assertEquals(0, chatRoom.nextMessageOrdinal)

        // Add first message
        val message1 = ChatMessage(
            message = "First message",
            authorUserId = userId,
            authorAlias = "OrdinalTest",
            authorSessionId = SessionId.INVALID,
            ordinal = chatRoom.nextMessageOrdinal,
            recipients = setOf("OrdinalTest")
        )
        chatRoom.addMessage(message1)

        // Verify ordinal incremented
        assertEquals(1, chatRoom.nextMessageOrdinal)

        // Add second message
        val message2 = ChatMessage(
            message = "Second message",
            authorUserId = userId,
            authorAlias = "OrdinalTest",
            authorSessionId = SessionId.INVALID,
            ordinal = chatRoom.nextMessageOrdinal,
            recipients = setOf("OrdinalTest")
        )
        chatRoom.addMessage(message2)

        // Verify ordinal incremented again
        assertEquals(2, chatRoom.nextMessageOrdinal)
        val message3 = ChatMessage(
            message = "Third message",
            authorUserId = userId,
            authorAlias = "OrdinalTest",
            authorSessionId = SessionId.INVALID,
            ordinal = -1,
            recipients = setOf("OrdinalTest")
        )

        // Nothign should happen if ordinal is -1
        chatRoom.addMessage(message3)
        assertEquals(3, chatRoom.nextMessageOrdinal, "Ordinal should not increment for -1 ordinal")

    }

    @Test
    fun `ChatRoomManager should create chat room with specified users`() {
        // Create test users
        UserManager.addUser("user1", UserRole.HUMAN, PlainPassword("password1"))
        UserManager.addUser("user2", UserRole.HUMAN, PlainPassword("password2"))

        val user1Id = UserManager.getUserIdFromUsername("user1")!!
        val user2Id = UserManager.getUserIdFromUsername("user2")!!

        // Create chat room using ChatRoomManager
        val chatRoom = ChatRoomManager.create(
            userIds = listOf(user1Id, user2Id),
            formRef = "test-form",
            log = false,
            prompt = "Test prompt for chat room"
        )

        // Verify chat room was created correctly
        assertNotNull(chatRoom)
        assertTrue(chatRoom.active)
        assertEquals("test-form", chatRoom.formRef)
        assertEquals("Test prompt for chat room", chatRoom.prompt)
        assertEquals(2, chatRoom.users.size)
        assertTrue(chatRoom.users.containsKey(user1Id))
        assertTrue(chatRoom.users.containsKey(user2Id))

        // Verify it appears in the active list
        val activeChatRooms = ChatRoomManager.listActive()
        assertTrue(activeChatRooms.any { it.uid == chatRoom.uid })
    }

    @Test
    fun `ChatRoomManager should add messages to chat rooms`() {
        // Create test user and chat room
        UserManager.addUser("messageuser", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("messageuser")!!

        val chatRoom = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = "test-form",
            log = false,
            prompt = "Test message room"
        )

        // Create a message
        val message = ChatMessage(
            message = "Hello from ChatRoomManager test!",
            authorUserId = userId,
            authorAlias = chatRoom.users[userId]!!,
            authorSessionId = SessionId.INVALID,
            ordinal = 0,
            recipients = setOf(chatRoom.users[userId]!!)
        )

        // Add message using ChatRoomManager
        ChatRoomManager.addMessageTo(chatRoom, message)

        // Verify message was added
        val messages = chatRoom.getAllMessages()
        assertEquals(1, messages.size)
        assertEquals("Hello from ChatRoomManager test!", messages[0].message)
        assertEquals(userId, messages[0].authorUserId)
    }

    @Test
    fun `ChatRoomManager should retrieve chat rooms by user`() {
        // Create test users
        UserManager.addUser("alice", UserRole.HUMAN, PlainPassword("password1"))
        UserManager.addUser("bob", UserRole.HUMAN, PlainPassword("password2"))
        UserManager.addUser("charlie", UserRole.HUMAN, PlainPassword("password3"))

        val aliceId = UserManager.getUserIdFromUsername("alice")!!
        val bobId = UserManager.getUserIdFromUsername("bob")!!
        val charlieId = UserManager.getUserIdFromUsername("charlie")!!

        // Create chat rooms with different user combinations
        val room1 = ChatRoomManager.create(
            userIds = listOf(aliceId, bobId),
            formRef = "form1",
            log = false,
            prompt = "Alice and Bob chat"
        )

        val room2 = ChatRoomManager.create(
            userIds = listOf(aliceId, charlieId),
            formRef = "form2",
            log = false,
            prompt = "Alice and Charlie chat"
        )

        val room3 = ChatRoomManager.create(
            userIds = listOf(bobId, charlieId),
            formRef = "form3",
            log = false,
            prompt = "Bob and Charlie chat"
        )

        // Test getting rooms by user
        val aliceRooms = ChatRoomManager.getByUser(aliceId)
        val bobRooms = ChatRoomManager.getByUser(bobId)
        val charlieRooms = ChatRoomManager.getByUser(charlieId)

        // Alice should be in room1 and room2
        assertEquals(2, aliceRooms.size)
        assertTrue(aliceRooms.any { it.uid == room1.uid })
        assertTrue(aliceRooms.any { it.uid == room2.uid })

        // Bob should be in room1 and room3
        assertEquals(2, bobRooms.size)
        assertTrue(bobRooms.any { it.uid == room1.uid })
        assertTrue(bobRooms.any { it.uid == room3.uid })

        // Charlie should be in room2 and room3
        assertEquals(2, charlieRooms.size)
        assertTrue(charlieRooms.any { it.uid == room2.uid })
        assertTrue(charlieRooms.any { it.uid == room3.uid })
    }

    @Test
    fun `ChatRoomManager should handle chat partner retrieval`() {
        // Create test users
        UserManager.addUser("partner1", UserRole.HUMAN, PlainPassword("password1"))
        UserManager.addUser("partner2", UserRole.HUMAN, PlainPassword("password2"))

        val user1Id = UserManager.getUserIdFromUsername("partner1")!!
        val user2Id = UserManager.getUserIdFromUsername("partner2")!!

        // Create chat room
        val chatRoom = ChatRoomManager.create(
            userIds = listOf(user1Id, user2Id),
            formRef = "partner-test",
            log = false,
            prompt = "Partner test room"
        )

        // Test getting chat partner
        val partner1 = ChatRoomManager.getChatPartner(chatRoom.uid, user1Id)
        val partner2 = ChatRoomManager.getChatPartner(chatRoom.uid, user2Id)

        assertEquals(user2Id, partner1)
        assertEquals(user1Id, partner2)
    }

    @Test
    fun `ChatRoomManager should add users to existing chat rooms`() {
        // Create initial users and chat room
        UserManager.addUser("initial1", UserRole.HUMAN, PlainPassword("password1"))
        UserManager.addUser("initial2", UserRole.HUMAN, PlainPassword("password2"))
        UserManager.addUser("newcomer", UserRole.HUMAN, PlainPassword("password3"))

        val user1Id = UserManager.getUserIdFromUsername("initial1")!!
        val user2Id = UserManager.getUserIdFromUsername("initial2")!!
        val newcomerId = UserManager.getUserIdFromUsername("newcomer")!!

        val chatRoom = ChatRoomManager.create(
            userIds = listOf(user1Id, user2Id),
            formRef = "add-user-test",
            log = false,
            prompt = "Add user test room"
        )

        // Initially should have 2 users
        assertEquals(2, chatRoom.users.size)

        // Add new user
        ChatRoomManager.addUser(newcomerId, chatRoom.uid)

        // Should now have 3 users
        assertEquals(3, chatRoom.users.size)
        assertTrue(chatRoom.users.containsKey(newcomerId))

        // Verify user list method
        val userIds = ChatRoomManager.getUsersIDofARoom(chatRoom.uid)
        assertEquals(3, userIds.size)
        assertTrue(userIds.contains(user1Id))
        assertTrue(userIds.contains(user2Id))
        assertTrue(userIds.contains(newcomerId))
    }

    @Test
    fun `ChatRoomManager should handle feedback form references`() {
        // Create test user
        UserManager.addUser("formuser", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("formuser")!!

        // Create chat room with form reference
        val chatRoom = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = "feedback-form-test",
            log = false,
            prompt = "Form test room"
        )

        // Test getting feedback form reference
        val formRef = ChatRoomManager.getFeedbackFormReference(chatRoom.uid)
        assertEquals("feedback-form-test", formRef)

        // Test with empty form reference
        val chatRoomNoForm = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = "",
            log = false,
            prompt = "No form room"
        )

        val noFormRef = ChatRoomManager.getFeedbackFormReference(chatRoomNoForm.uid)
        assertNull(noFormRef)
    }

    @Test
    fun `ChatRoomManager should handle assignment flag`() {
        // Create test user
        UserManager.addUser("assignmentuser", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("assignmentuser")!!

        // Create regular chat room
        val regularRoom = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = "regular-form",
            log = false,
            prompt = "Regular room",
            assignment = false
        )

        // Create assignment chat room
        val assignmentRoom = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = "assignment-form",
            log = false,
            prompt = "Assignment room",
            assignment = true
        )

        // Test assignment detection
        assertFalse(ChatRoomManager.isAssignment(regularRoom.uid))
        assertTrue(ChatRoomManager.isAssignment(assignmentRoom.uid))
    }

    @Test
    fun `ChatRoomManager should handle no feedback marking`() {
        // Create test user
        UserManager.addUser("nofeedbackuser", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("nofeedbackuser")!!

        // Create chat room
        val chatRoom = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = "nofeedback-form",
            log = false,
            prompt = "No feedback test room"
        )

        // Initially should not be marked as no feedback
        assertFalse(chatRoom.markAsNoFeedback)

        // Mark as no feedback
        ChatRoomManager.markAsNoFeedback(chatRoom.uid)

        // Should now be marked as no feedback
        assertTrue(chatRoom.markAsNoFeedback)
    }

    @Test
    fun `ChatRoomManager should process messages with recipients correctly`() {
        // Create test users
        UserManager.addUser("sender", UserRole.HUMAN, PlainPassword("password"))
        UserManager.addUser("receiver", UserRole.HUMAN, PlainPassword("password"))

        val senderId = UserManager.getUserIdFromUsername("sender")!!
        val receiverId = UserManager.getUserIdFromUsername("receiver")!!

        // Create chat room with users
        val chatRoom = ChatRoomManager.create(
            userIds = listOf(senderId, receiverId),
            formRef = "recipient-test",
            log = false,
            prompt = "Recipient test room"
        )

        val senderAlias = chatRoom.users[senderId]!!

        // Test message with specific user mention
        val receiverUsername = UserManager.getUsernameFromId(receiverId)!!
        val messageWithUser = "@${receiverUsername}: Hello specific user!"
        
        val result1 = ChatRoomManager.processMessageAndRecipients(messageWithUser, chatRoom, senderAlias)
        assertNotNull(result1)
        
        // Test message without mentions
        val regularMessage = "Hello everyone!"
        val result2 = ChatRoomManager.processMessageAndRecipients(regularMessage, chatRoom, senderAlias)
        assertNotNull(result2)
        assertEquals("Hello everyone!", result2.second)
        assertTrue(result2.first.isEmpty())
    }

    @Test
    fun `ChatRoomManager should list all and active chat rooms`() {
        // Create test users
        UserManager.addUser("listuser1", UserRole.HUMAN, PlainPassword("password1"))
        UserManager.addUser("listuser2", UserRole.HUMAN, PlainPassword("password2"))

        val user1Id = UserManager.getUserIdFromUsername("listuser1")!!
        val user2Id = UserManager.getUserIdFromUsername("listuser2")!!

        // Create active chat room
        val activeRoom = ChatRoomManager.create(
            userIds = listOf(user1Id),
            formRef = "active-form",
            log = false,
            prompt = "Active room"
        )

        // Create another active room
        val anotherActiveRoom = ChatRoomManager.create(
            userIds = listOf(user2Id),
            formRef = "another-active-form", 
            log = false,
            prompt = "Another active room"
        )

        // Deactivate one room
        activeRoom.deactivate()

        // Test listing all rooms
        val allRooms = ChatRoomManager.listAll()
        assertTrue(allRooms.size >= 2)
        assertTrue(allRooms.any { it.uid == activeRoom.uid })
        assertTrue(allRooms.any { it.uid == anotherActiveRoom.uid })

        // Test listing only active rooms
        val activeRooms = ChatRoomManager.listActive()
        assertTrue(activeRooms.any { it.uid == anotherActiveRoom.uid })
        assertFalse(activeRooms.any { it.uid == activeRoom.uid })
    }

    @Test
    fun `ChatRoomManager should export chat rooms correctly`() {
        // Create test user
        UserManager.addUser("exportuser", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("exportuser")!!

        // Create chat room with some content
        val chatRoom = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = "export-form",
            log = false,
            prompt = "Export test room"
        )

        // Add a message
        val message = ChatMessage(
            message = "Export test message",
            authorUserId = userId,
            authorAlias = chatRoom.users[userId]!!,
            authorSessionId = SessionId.INVALID,
            ordinal = 0,
            recipients = setOf(chatRoom.users[userId]!!)
        )
        ChatRoomManager.addMessageTo(chatRoom, message)

        // Export the chat room
        val exportedRooms = ChatRoomManager.exportChatrooms(listOf(chatRoom.uid))
        
        assertEquals(1, exportedRooms.size)
        val exportedRoom = exportedRooms[0]
        assertEquals("export-form", exportedRoom.formRef)
        assertEquals("Export test room", exportedRoom.prompt)
        assertEquals(1, exportedRoom.messages.size)
        assertEquals("Export test message", exportedRoom.messages[0].message)
        assertEquals(1, exportedRoom.usernames.size)
        assertEquals("exportuser", exportedRoom.usernames[0])
    }

    @Test
    fun `ChatRoom should handle assignements`(){
        UserManager.addUser("alice", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("alice")!!
        val chatroom = ChatRoom(
            assignment = true,
            formRef = "assignment-form",
            users = mutableMapOf(),
            prompt = "Assignment test room"
        )

        assertTrue(chatroom.assignment, "Chat room should be an assignment")
        // Create chat room with some content
        val chatRoom = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = "export-form",
            log = false,
            prompt = "Export test room",
            assignment = true
        )
        assertTrue(chatRoom.assignment, "Chat room should be an assignment")
        assertTrue(ChatRoomManager.isAssignment(chatroom.uid), "Chat room should be recognized as an assignment")

    }
} 