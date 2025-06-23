package ch.ddis.speakeasy

import ch.ddis.speakeasy.chat.*
import ch.ddis.speakeasy.db.ChatRepository
import ch.ddis.speakeasy.db.DatabaseHandler
import ch.ddis.speakeasy.db.UserId
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
    fun `should handle message reactions`() {
        // Create test user
        UserManager.addUser("testuser", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("testuser")!!
        val chatRoom = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = "test-form",
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

        ChatRoomManager.addMessageTo(chatRoom, message)

        // Add a reaction to the message
        val reaction = ChatMessageReaction(
            messageOrdinal = 0,
            type = ChatMessageReactionType.THUMBS_UP
        )

        ChatRoomManager.addReactionTo(chatRoom, reaction)

        // Verify reaction was added
        val allReactions = ChatRoomManager.getReactionsForMessage(chatRoom.uid, 0)
        assertEquals(1, allReactions.size)
        assertEquals(ChatMessageReactionType.THUMBS_UP, allReactions[0])

        assertFailsWith<IllegalArgumentException> {
            ChatRoomManager.addReactionTo(chatRoom, ChatMessageReaction(1, ChatMessageReactionType.THUMBS_UP))
        }
        assertFailsWith<IllegalArgumentException> {
            ChatRoomManager.getReactionsForMessage(chatRoom.uid, 1)
        }

        // Add a few more messages and reactions and test the behavior
        val message2 = ChatMessage(
            message = "Another great message!",
            authorUserId = userId,
            authorAlias = chatRoom.users[userId]!!,
            authorSessionId = SessionId.INVALID,
            ordinal = 1,
            recipients = setOf(chatRoom.users[userId]!!)
        )

        ChatRoomManager.addMessageTo(chatRoom, message2)

        val message3 = ChatMessage(
            message = "Third message for testing",
            authorUserId = userId,
            authorAlias = chatRoom.users[userId]!!,
            authorSessionId = SessionId.INVALID,
            ordinal = 2,
            recipients = setOf(chatRoom.users[userId]!!)
        )

        ChatRoomManager.addMessageTo(chatRoom, message3)

        // Add reactions to different messages
        val reaction2 = ChatMessageReaction(
            messageOrdinal = 1,
            type = ChatMessageReactionType.THUMBS_DOWN
        )

        val reaction3 = ChatMessageReaction(
            messageOrdinal = 2,
            type = ChatMessageReactionType.STAR
        )

        ChatRoomManager.addReactionTo(chatRoom, reaction2)
        ChatRoomManager.addReactionTo(chatRoom, reaction3)

        // Verify each message has its correct reaction
        val reactions0 = ChatRoomManager.getReactionsForMessage(chatRoom.uid, 0)
        val reactions1 = ChatRoomManager.getReactionsForMessage(chatRoom.uid, 1)
        val reactions2 = ChatRoomManager.getReactionsForMessage(chatRoom.uid, 2)

        assertEquals(1, reactions0.size)
        assertEquals(ChatMessageReactionType.THUMBS_UP, reactions0[0])

        assertEquals(1, reactions1.size)
        assertEquals(ChatMessageReactionType.THUMBS_DOWN, reactions1[0])

        assertEquals(1, reactions2.size)
        assertEquals(ChatMessageReactionType.STAR, reactions2[0])

        // Test overwriting a reaction (should replace the previous one if the same)
        val newReaction1 = ChatMessageReaction(
            messageOrdinal = 1,
            type = ChatMessageReactionType.STAR
        )

        ChatRoomManager.addReactionTo(chatRoom, newReaction1)

        val updatedReactions1 = ChatRoomManager.getReactionsForMessage(chatRoom.uid, 1)
        assertEquals(2, updatedReactions1.size)
        // The order should be depedent on time of the reactiob
        assertEquals(ChatMessageReactionType.THUMBS_DOWN, updatedReactions1[0])
        assertEquals(ChatMessageReactionType.STAR, updatedReactions1[1])

        // Add another thumbs down reaction to the same message
        // The new thumbs down should be in last position in the array
        ChatRoomManager.addReactionTo(chatRoom, reaction2)
        val finalReactions1 = ChatRoomManager.getReactionsForMessage(chatRoom.uid, 1)

        // Should not change
        assertEquals(2, finalReactions1.size)
        assertContentEquals(listOf(ChatMessageReactionType.STAR, ChatMessageReactionType.THUMBS_DOWN), finalReactions1)

        // Test getReactionsForChatRoom for the current chatroom
        val allReactionsOfRoom = ChatRoomManager.getReactionsForChatRoom(chatRoom.uid)
        assertEquals(3, allReactionsOfRoom.size) // 1 for message 0, 2 for message 1, 1 for message 2

        // Verify specific reactions exist
        assertTrue(allReactionsOfRoom.any { it.messageOrdinal == 0 && it.type == ChatMessageReactionType.THUMBS_UP })
        assertTrue(allReactionsOfRoom.any { it.messageOrdinal == 1 && it.type == ChatMessageReactionType.THUMBS_DOWN })
        assertTrue(allReactionsOfRoom.any { it.messageOrdinal == 2 && it.type == ChatMessageReactionType.STAR })
    }

    @Test
    fun `should filter messages by time for users`() {
        // Create test users
        UserManager.addUser("user1", UserRole.HUMAN, PlainPassword("password1"))
        UserManager.addUser("user2", UserRole.HUMAN, PlainPassword("password2"))

        val user1Id = UserManager.getUserIdFromUsername("user1")!!
        val user2Id = UserManager.getUserIdFromUsername("user2")!!

        val chatRoom = ChatRoomManager.create(
            userIds = listOf(user1Id, user2Id),
            formRef = "test-form",
            prompt = "Test message filtering"
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
        ChatRoomManager.addMessageTo(chatRoom, message1)

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
        ChatRoomManager.addMessageTo(chatRoom, message2)

        // Get messages since timestamp between the two messages
        val messagesSince = ChatRoomManager.getMessagesFor(chatRoom.uid, timestamp1 + 5)

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
        val chatRoom = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = "test-form",
            prompt = "Test listener room"
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

        ChatRoomManager.addMessageTo(chatRoom, message)
        assertTrue(messageReceived, "Listener should receive message notification")
    }

    @Test
    fun `should handle multiple listeners with proper lifecycle management`() {
        // Create test user
        UserManager.addUser("listeneruser", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("listeneruser")!!

        // Track events for different listeners
        var activeListener1Events = mutableListOf<String>()
        var activeListener2Events = mutableListOf<String>()
        var inactiveListenerEvents = mutableListOf<String>()

        // Create multiple listeners with different states
        val activeListener1 = object : ChatEventListener {
            override val isActive = true

            override fun onNewRoom(chatRoom: ChatRoom) {
                activeListener1Events.add("newRoom:${chatRoom.uid.string}")
            }

            override fun onMessage(chatMessage: ChatMessage, chatRoom: ChatRoom) {
                activeListener1Events.add("message:${chatMessage.message}")
            }

            override fun onReaction(chatMessageReaction: ChatMessageReaction, chatRoom: ChatRoom) {
                activeListener1Events.add("reaction:${chatMessageReaction.type}")
            }
        }

        val activeListener2 = object : ChatEventListener {
            override val isActive = true

            override fun onNewRoom(chatRoom: ChatRoom) {
                activeListener2Events.add("newRoom:${chatRoom.uid.string}")
            }

            override fun onMessage(chatMessage: ChatMessage, chatRoom: ChatRoom) {
                activeListener2Events.add("message:${chatMessage.message}")
            }

            override fun onReaction(chatMessageReaction: ChatMessageReaction, chatRoom: ChatRoom) {
                activeListener2Events.add("reaction:${chatMessageReaction.type}")
            }
        }

        val inactiveListener = object : ChatEventListener {
            override val isActive = false

            override fun onNewRoom(chatRoom: ChatRoom) {
                inactiveListenerEvents.add("newRoom:${chatRoom.uid.string}")
            }

            override fun onMessage(chatMessage: ChatMessage, chatRoom: ChatRoom) {
                inactiveListenerEvents.add("message:${chatMessage.message}")
            }

            override fun onReaction(chatMessageReaction: ChatMessageReaction, chatRoom: ChatRoom) {
                inactiveListenerEvents.add("reaction:${chatMessageReaction.type}")
            }
        }

        // Create chat room
        val chatRoom = ChatRoom(
            assignment = false,
            formRef = "listener-test",
            users = mutableMapOf(userId to "ListenerUser"),
            prompt = "Test multiple listeners"
        )

        // Add all listeners
        chatRoom.addListener(activeListener1)
        chatRoom.addListener(activeListener2)
        chatRoom.addListener(inactiveListener)

        // Verify only active listeners received new room event
        assertEquals(1, activeListener1Events.size)
        assertEquals(1, activeListener2Events.size)
        assertEquals(0, inactiveListenerEvents.size)
        assertTrue(activeListener1Events[0].startsWith("newRoom:"))
        assertTrue(activeListener2Events[0].startsWith("newRoom:"))

        // Clear events to test message handling
        activeListener1Events.clear()
        activeListener2Events.clear()

        // Add a message
        val message = ChatMessage(
            message = "Hello listeners!",
            authorUserId = userId,
            authorAlias = "ListenerUser",
            authorSessionId = SessionId.INVALID,
            ordinal = 0,
            recipients = setOf("ListenerUser")
        )

        ChatRoomManager.addMessageTo(chatRoom, message)

        // Verify only active listeners received message event
        assertEquals(1, activeListener1Events.size)
        assertEquals(1, activeListener2Events.size)
        assertEquals(0, inactiveListenerEvents.size)
        assertEquals("message:Hello listeners!", activeListener1Events[0])
        assertEquals("message:Hello listeners!", activeListener2Events[0])

        // Clear events to test reaction handling
        activeListener1Events.clear()
        activeListener2Events.clear()

        // Add a reaction
        val reaction = ChatMessageReaction(
            messageOrdinal = 0,
            type = ChatMessageReactionType.THUMBS_UP
        )

        ChatRoomManager.addReactionTo(chatRoom, reaction)

        // Verify only active listeners received reaction event
        assertEquals(1, activeListener1Events.size)
        assertEquals(1, activeListener2Events.size)
        assertEquals(0, inactiveListenerEvents.size)
        assertEquals("reaction:THUMBS_UP", activeListener1Events[0])
        assertEquals("reaction:THUMBS_UP", activeListener2Events[0])

        // Verify inactive listener never received any events
        assertEquals(0, inactiveListenerEvents.size)
    }

    @Test
    fun `should automatically remove inactive listeners during event processing`() {
        // Create test user
        UserManager.addUser("cleanupuser", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("cleanupuser")!!

        // Create a listener that becomes inactive
        var listenerActive = true
        var eventsReceived = 0

        val dynamicListener = object : ChatEventListener {
            override val isActive: Boolean
                get() = listenerActive

            override fun onNewRoom(chatRoom: ChatRoom) {
                eventsReceived++
            }

            override fun onMessage(chatMessage: ChatMessage, chatRoom: ChatRoom) {
                eventsReceived++
            }

            override fun onReaction(chatMessageReaction: ChatMessageReaction, chatRoom: ChatRoom) {
                eventsReceived++
            }
        }

        // Create chat room and add listener
        var chatRoom = ChatRoom(
            assignment = false,
            formRef = "cleanup-test",
            users = mutableMapOf(userId to "CleanupUser"),
            prompt = "Test listener cleanup"
        )
        chatRoom = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = chatRoom.formRef,
            prompt = chatRoom.prompt,
        )

        chatRoom.addListener(dynamicListener)

        // Verify listener received new room event
        assertEquals(1, eventsReceived)

        // Add a message while listener is active
        val message1 = ChatMessage(
            message = "First message",
            authorUserId = userId,
            authorAlias = "CleanupUser",
            authorSessionId = SessionId.INVALID,
            ordinal = 0,
            recipients = setOf("CleanupUser")
        )

        ChatRoomManager.addMessageTo(chatRoom, message1)
        assertEquals(2, eventsReceived)

        // Make listener inactive
        listenerActive = false

        // Add another message - listener should be removed and not receive event
        val message2 = ChatMessage(
            message = "Second message",
            authorUserId = userId,
            authorAlias = "CleanupUser",
            authorSessionId = SessionId.INVALID,
            ordinal = 1,
            recipients = setOf("CleanupUser")
        )

        ChatRoomManager.addMessageTo(chatRoom, message2)

        // Events should still be 2 (listener was removed)
        assertEquals(2, eventsReceived)

        // Add a third message to confirm listener is permanently removed
        val message3 = ChatMessage(
            message = "Third message",
            authorUserId = userId,
            authorAlias = "CleanupUser",
            authorSessionId = SessionId.INVALID,
            ordinal = 2,
            recipients = setOf("CleanupUser")
        )

        // Make listener active again, but it shouldn't receive events since it was removed
        listenerActive = true
        ChatRoomManager.addMessageTo(chatRoom, message3)

        // Events should still be 2 (listener was removed and won't be re-added automatically)
        assertEquals(2, eventsReceived)
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
        assertTrue(ChatRoomManager.isChatRoomActive(chatRoom.uid))
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
        val messages = ChatRoomManager.getMessagesFor(chatRoom.uid)
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

    // @Test
//    fun `ChatRoomManager should handle feedback form references`() {
//        // Create test user
//        UserManager.addUser("formuser", UserRole.HUMAN, PlainPassword("password"))
//        val userId = UserManager.getUserIdFromUsername("formuser")!!
//
//        // Create chat room with form reference
//        val chatRoom = ChatRoomManager.create(
//            userIds = listOf(userId),
//            formRef = "feedback-form-test",
//            log = false,
//            prompt = "Form test room"
//        )
//
//        // Test getting feedback form reference
//        val formRef = ChatRoomManager.getFeedbackFormReference(chatRoom.uid)
//        assertEquals("feedback-form-test", formRef)
//
//        // Test with empty form reference
//        val chatRoomNoForm = ChatRoomManager.create(
//            userIds = listOf(userId),
//            formRef = "",
//            log = false,
//            prompt = "No form room"
//        )
//
//        val noFormRef = ChatRoomManager.getFeedbackFormReference(chatRoomNoForm.uid)
//        assertNull(noFormRef)
//    }

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
        assertFalse(ChatRoomManager.isRoomNoFeedback(chatRoom.uid))

        // Mark as no feedback
        ChatRoomManager.markAsNoFeedback(chatRoom.uid)

        // Should now be marked as no feedback
        assertTrue(ChatRoomManager.isRoomNoFeedback(chatRoom.uid))
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
        ChatRoomManager.deactivateChatRoom(activeRoom.uid)

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
    fun `ChatRoom should handle assignements`() {
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
        assertTrue(ChatRoomManager.isAssignment(chatRoom.uid), "Chat room should be recognized as an assignment")

    }

    @Test
    fun `ChatRoom should handle deactivation and reactivation correctly`() {
        // Create test user
        UserManager.addUser("deactivateuser", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("deactivateuser")!!

        // Create chat room
        val chatRoom = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = "deactivate-test",
            log = false,
            prompt = "Deactivation test room"
        )

        // Initially should be active
        assertTrue(ChatRoomManager.isChatRoomActive(chatRoom.uid))
        assertTrue(ChatRoomManager.listActive().any { it.uid == chatRoom.uid })

        // Deactivate room
        ChatRoomManager.deactivateChatRoom(chatRoom.uid)
        assertFalse(ChatRoomManager.isChatRoomActive(chatRoom.uid))
        assertFalse(ChatRoomManager.listActive().any { it.uid == chatRoom.uid })

        // Room should still be in all rooms list
        assertTrue(ChatRoomManager.listAll().any { it.uid == chatRoom.uid })
    }

    @Test
    fun `ChatRoomManager should handle non-existent room operations gracefully`() {
        // Create test user
        UserManager.addUser("nonexistentuser", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("nonexistentuser")!!

        val fakeRoomUid = ChatRoomId()

        // Test operations on non-existent room
        assertFailsWith<IllegalArgumentException> {
            ChatRoomManager.getFeedbackFormReference(fakeRoomUid)
        }
        assertFailsWith<IllegalArgumentException> {
            ChatRoomManager.isAssignment(fakeRoomUid)
        }

        assertFailsWith<IllegalArgumentException> {
            ChatRoomManager.getUsersIDofARoom(fakeRoomUid)
        }
    }

    @Test
    fun `ChatRoom should handle empty and special messages correctly`() {
        // Create test user
        UserManager.addUser("specialuser", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("specialuser")!!

        val chatRoom = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = "special-test",
            log = false,
            prompt = "Special messages test"
        )

        val userAlias = chatRoom.users[userId]!!

        // Test empty message
        val emptyMessage = ChatMessage(
            message = "",
            authorUserId = userId,
            authorAlias = userAlias,
            authorSessionId = SessionId.INVALID,
            recipients = setOf(userAlias)
        )
        ChatRoomManager.addMessageTo(chatRoom, emptyMessage)

        // Test message with special characters
        val specialMessage = ChatMessage(
            message = "Hello! 🎉 @user #hashtag & symbols: <>&\"'",
            authorUserId = userId,
            authorAlias = userAlias,
            authorSessionId = SessionId.INVALID,
            recipients = setOf(userAlias)
        )
        ChatRoomManager.addMessageTo(chatRoom, specialMessage)

        // Test very long message
        val longMessage = ChatMessage(
            message = "A".repeat(10000),
            authorUserId = userId,
            authorAlias = userAlias,
            authorSessionId = SessionId.INVALID,
            recipients = setOf(userAlias)
        )
        ChatRoomManager.addMessageTo(chatRoom, longMessage)

        val messages = ChatRoomManager.getMessagesFor(chatRoom.uid)
        assertEquals(3, messages.size)
        assertEquals("", messages[0].message)
        assertEquals("Hello! \uD83C\uDF89 @user #hashtag & symbols: <>&\"'", messages[1].message)
        assertEquals(10000, messages[2].message.length)
    }

    @Test
    fun `ChatRoomManager should handle multiple users in large chat room`() {
        // Create many test users
        val userIds = mutableListOf<UserId>()
        for (i in 1..10) {
            UserManager.addUser("user$i", UserRole.HUMAN, PlainPassword("password$i"))
            userIds.add(UserManager.getUserIdFromUsername("user$i")!!)
        }

        // Create chat room with all users
        val chatRoom = ChatRoomManager.create(
            userIds = userIds,
            formRef = "large-room-test",
            log = false,
            prompt = "Large room test with many users"
        )

        // Verify all users are present
        assertEquals(10, chatRoom.users.size)
        userIds.forEach { userId ->
            assertTrue(chatRoom.users.containsKey(userId))
        }

        // Add messages from different users
        userIds.forEachIndexed { index, userId ->
            val message = ChatMessage(
                message = "Message from user ${index + 1}",
                authorUserId = userId,
                authorAlias = chatRoom.users[userId]!!,
                authorSessionId = SessionId.INVALID,
                recipients = chatRoom.users.values.toSet()
            )
            ChatRoomManager.addMessageTo(chatRoom, message)
        }

        // Verify all messages were added
        val messages = ChatRoomManager.getMessagesFor(chatRoom.uid)
        assertEquals(10, messages.size)

        // Test each user can see the room
        userIds.forEach { userId ->
            val userRooms = ChatRoomManager.getByUser(userId)
            assertTrue(userRooms.any { it.uid == chatRoom.uid })
        }
    }

    @Test
    fun `ChatRoom should maintain message order with different timestamps`() {
        // Create test users
        UserManager.addUser("orderuser1", UserRole.HUMAN, PlainPassword("password1"))
        UserManager.addUser("orderuser2", UserRole.HUMAN, PlainPassword("password2"))

        val user1Id = UserManager.getUserIdFromUsername("orderuser1")!!
        val user2Id = UserManager.getUserIdFromUsername("orderuser2")!!

        val chatRoom = ChatRoomManager.create(
            userIds = listOf(user1Id, user2Id),
            formRef = "order-test",
            log = false,
            prompt = "Message order test"
        )

        val baseTime = System.currentTimeMillis()

        // Add messages with timestamps out of order
        val message1 = ChatMessage(
            message = "Third message chronologically",
            authorUserId = user1Id,
            authorAlias = chatRoom.users[user1Id]!!,
            authorSessionId = SessionId.INVALID,
            ordinal = 0,
            recipients = chatRoom.users.values.toSet(),
            time = baseTime + 2000
        )

        val message2 = ChatMessage(
            message = "First message chronologically",
            authorUserId = user2Id,
            authorAlias = chatRoom.users[user2Id]!!,
            authorSessionId = SessionId.INVALID,
            ordinal = 1,
            recipients = chatRoom.users.values.toSet(),
            time = baseTime
        )

        val message3 = ChatMessage(
            message = "Second message chronologically",
            authorUserId = user1Id,
            authorAlias = chatRoom.users[user1Id]!!,
            authorSessionId = SessionId.INVALID,
            ordinal = 2,
            recipients = chatRoom.users.values.toSet(),
            time = baseTime + 1000
        )

        // Add messages in order they were created (by ordinal)
        ChatRoomManager.addMessageTo(chatRoom, message1)
        ChatRoomManager.addMessageTo(chatRoom, message2)
        ChatRoomManager.addMessageTo(chatRoom, message3)

        // Messages should be retrieved in the order they were added (by ordinal)
        val messages = ChatRoomManager.getMessagesFor(chatRoom.uid)
        assertEquals(3, messages.size)
        assertEquals("Third message chronologically", messages[2].message)
        assertEquals("Second message chronologically", messages[1].message)
        assertEquals("First message chronologically", messages[0].message)
    }

    @Test
    fun `ChatRoomRepository should handle concurrent message additions`() {
        // Create test users
        UserManager.addUser("concurrent1", UserRole.HUMAN, PlainPassword("password1"))
        UserManager.addUser("concurrent2", UserRole.HUMAN, PlainPassword("password2"))

        val user1Id = UserManager.getUserIdFromUsername("concurrent1")!!
        val user2Id = UserManager.getUserIdFromUsername("concurrent2")!!

        val chatRoom = ChatRoomManager.create(
            userIds = listOf(user1Id, user2Id),
            formRef = "concurrent-test",
            log = false,
            prompt = "Concurrent access test"
        )

        // Simulate concurrent message additions
        val threads = mutableListOf<Thread>()
        val messageCount = 10

        // User 1 thread
        val thread1 = Thread {
            repeat(messageCount) { i ->
                val message = ChatMessage(
                    message = "Message from user1: $i",
                    authorUserId = user1Id,
                    authorAlias = chatRoom.users[user1Id]!!,
                    authorSessionId = SessionId.INVALID,
                    ordinal = -1,
                    recipients = chatRoom.users.values.toSet()
                )
                ChatRepository.addMessageTo(chatRoom.uid, message)
                Thread.sleep(1) // Small delay to simulate real usage
            }
        }

        // User 2 thread
        val thread2 = Thread {
            repeat(messageCount) { i ->
                val message = ChatMessage(
                    message = "Message from user2: $i",
                    authorUserId = user2Id,
                    authorAlias = chatRoom.users[user2Id]!!,
                    authorSessionId = SessionId.INVALID,
                    ordinal = -1, // Let the system assign the ordinal
                    recipients = chatRoom.users.values.toSet()
                )
                ChatRepository.addMessageTo(chatRoom.uid, message)
                Thread.sleep(1) // Small delay to simulate real usage
            }
        }

        threads.add(thread1)
        threads.add(thread2)

        // Start all threads
        threads.forEach { it.start() }

        // Wait for all threads to complete
        threads.forEach { it.join() }

        // Verify all messages were added
        val messages = ChatRepository.getChatMessages(chatRoom.uid)
        assertEquals(messageCount * 2, messages.size)

        // Verify ordinals are unique and sequential
        val ordinals = messages.map { it.ordinal }.sorted()
        assertEquals((0 until messageCount * 2).toList(), ordinals)

        // Verify both users' messages are present
        val user1Messages = messages.filter { it.authorUserId == user1Id }
        val user2Messages = messages.filter { it.authorUserId == user2Id }
        assertEquals(messageCount, user1Messages.size)
        assertEquals(messageCount, user2Messages.size)
    }

    @Test
    fun `ChatRoomManager should handle invalid user operations`() {
        // Test with non-existent user
        val fakeUserId = UserId()

        // Should handle non-existent user gracefully
        val rooms = ChatRoomManager.getByUser(fakeUserId)
        assertTrue(rooms.isEmpty())

        // Create valid room and test adding non-existent user
        UserManager.addUser("validuser", UserRole.HUMAN, PlainPassword("password"))
        val validUserId = UserManager.getUserIdFromUsername("validuser")!!

        val chatRoom = ChatRoomManager.create(
            userIds = listOf(validUserId),
            formRef = "invalid-user-test",
            log = false,
            prompt = "Invalid user test"
        )

        // Test adding non-existent user to room
        assertFailsWith<IllegalArgumentException> {
            ChatRoomManager.addUser(fakeUserId, chatRoom.uid)
        }
        // Room should still only have the original user
        assertEquals(1, chatRoom.users.size)
        assertTrue(chatRoom.users.containsKey(validUserId))
        assertFalse(chatRoom.users.containsKey(fakeUserId))
    }

    @Test
    fun `ChatRoom should handle prompt and form reference updates`() {
        // Create test user
        UserManager.addUser("updateuser", UserRole.HUMAN, PlainPassword("password"))
        val userId = UserManager.getUserIdFromUsername("updateuser")!!

        val chatRoom = ChatRoomManager.create(
            userIds = listOf(userId),
            formRef = "original-form",
            log = false,
            prompt = "Original prompt"
        )

        // Verify initial values
        assertEquals("original-form", chatRoom.formRef)
        assertEquals("Original prompt", chatRoom.prompt)
        // assertEquals("original-form", ChatRoomManager.getFeedbackFormReference(chatRoom.uid))

        // Test that the chat room maintains its properties consistently
        val retrievedRooms = ChatRoomManager.getByUser(userId)
        val retrievedRoom = retrievedRooms.find { it.uid == chatRoom.uid }
        assertNotNull(retrievedRoom)
        assertEquals("original-form", retrievedRoom.formRef)
        assertEquals("Original prompt", retrievedRoom.prompt)
    }
} 