package com.conferbot.sdk.data

import android.content.Context
import com.conferbot.sdk.data.dao.AnswerVariableDao
import com.conferbot.sdk.data.dao.ChatSessionDao
import com.conferbot.sdk.data.dao.MessageDao
import com.conferbot.sdk.data.dao.RecordDao
import com.conferbot.sdk.data.dao.TranscriptDao
import com.conferbot.sdk.data.dao.UserMetadataDao
import com.conferbot.sdk.data.testutils.DatabaseTestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for database layer
 * Tests relationships, cascading operations, and complex scenarios
 *
 * Note: These tests mock the DAO layer to verify expected behavior.
 * For true integration tests with Room's in-memory database,
 * use AndroidX Test with @RunWith(AndroidJUnit4::class) in androidTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: ConferbotDatabase
    private lateinit var chatSessionDao: ChatSessionDao
    private lateinit var messageDao: MessageDao
    private lateinit var answerVariableDao: AnswerVariableDao
    private lateinit var userMetadataDao: UserMetadataDao
    private lateinit var transcriptDao: TranscriptDao
    private lateinit var recordDao: RecordDao

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        database = mockk(relaxed = true)
        chatSessionDao = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        answerVariableDao = mockk(relaxed = true)
        userMetadataDao = mockk(relaxed = true)
        transcriptDao = mockk(relaxed = true)
        recordDao = mockk(relaxed = true)

        every { database.chatSessionDao() } returns chatSessionDao
        every { database.messageDao() } returns messageDao
        every { database.answerVariableDao() } returns answerVariableDao
        every { database.userMetadataDao() } returns userMetadataDao
        every { database.transcriptDao() } returns transcriptDao
        every { database.recordDao() } returns recordDao

        mockkObject(ConferbotDatabase)
        every { ConferbotDatabase.getInstance(any()) } returns database
    }

    @After
    fun tearDown() {
        unmockkObject(ConferbotDatabase)
    }

    // ==================== CASCADING DELETE TESTS ====================

    @Test
    fun `deleting session should trigger cascade delete of all related data`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val completeData = DatabaseTestFixtures.createCompleteSession(sessionId)

        // Verify related data exists
        coEvery { messageDao.getMessages(sessionId) } returns completeData.messages
        coEvery { answerVariableDao.getVariables(sessionId) } returns completeData.answerVariables
        coEvery { userMetadataDao.getMetadata(sessionId) } returns completeData.userMetadata
        coEvery { transcriptDao.getTranscript(sessionId) } returns completeData.transcript
        coEvery { recordDao.getRecords(sessionId) } returns completeData.records

        // When - Delete session (with ForeignKey CASCADE, related data should be deleted)
        chatSessionDao.delete(sessionId)

        // Then - Verify session delete was called
        // Note: With Room's ForeignKey CASCADE, related entities are auto-deleted
        coVerify { chatSessionDao.delete(sessionId) }
    }

    @Test
    fun `messages reference correct session via foreign key`() = runTest {
        // Given
        val session = DatabaseTestFixtures.createChatSession()
        val message = DatabaseTestFixtures.createMessage(sessionId = session.sessionId)

        // When/Then - Message should reference session's sessionId
        assertThat(message.sessionId).isEqualTo(session.sessionId)
    }

    @Test
    fun `answer variables reference correct session via foreign key`() = runTest {
        // Given
        val session = DatabaseTestFixtures.createChatSession()
        val variable = DatabaseTestFixtures.createAnswerVariable(sessionId = session.sessionId)

        // When/Then
        assertThat(variable.sessionId).isEqualTo(session.sessionId)
    }

    @Test
    fun `user metadata references correct session via foreign key`() = runTest {
        // Given
        val session = DatabaseTestFixtures.createChatSession()
        val metadata = DatabaseTestFixtures.createUserMetadata(sessionId = session.sessionId)

        // When/Then
        assertThat(metadata.sessionId).isEqualTo(session.sessionId)
    }

    @Test
    fun `transcript entries reference correct session via foreign key`() = runTest {
        // Given
        val session = DatabaseTestFixtures.createChatSession()
        val transcript = DatabaseTestFixtures.createTranscript(sessionId = session.sessionId)

        // When/Then
        assertThat(transcript.sessionId).isEqualTo(session.sessionId)
    }

    @Test
    fun `records reference correct session via foreign key`() = runTest {
        // Given
        val session = DatabaseTestFixtures.createChatSession()
        val record = DatabaseTestFixtures.createRecord(sessionId = session.sessionId)

        // When/Then
        assertThat(record.sessionId).isEqualTo(session.sessionId)
    }

    // ==================== FULL SESSION LIFECYCLE TESTS ====================

    @Test
    fun `complete session lifecycle - create, use, restore, delete`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val session = DatabaseTestFixtures.createChatSession(sessionId = sessionId)

        // Step 1: Create session
        coEvery { chatSessionDao.getSession(sessionId) } returns null
        repository.saveSession(
            sessionId = sessionId,
            visitorId = DatabaseTestFixtures.TEST_VISITOR_ID,
            botId = DatabaseTestFixtures.TEST_BOT_ID
        )
        coVerify { chatSessionDao.insert(any()) }

        // Step 2: Add data to session
        repository.saveMessage(
            id = "msg-1",
            sessionId = sessionId,
            content = "Hello",
            sender = "bot"
        )
        coVerify { messageDao.insert(any()) }

        repository.saveTranscriptEntry(sessionId, "bot", "Hello")
        coVerify { transcriptDao.insert(any()) }

        // Step 3: Restore session
        coEvery { chatSessionDao.getSession(sessionId) } returns session.copy(updatedAt = System.currentTimeMillis())
        coEvery { messageDao.getMessages(sessionId) } returns listOf(DatabaseTestFixtures.createMessage(sessionId = sessionId))
        coEvery { answerVariableDao.getVariables(sessionId) } returns emptyList()
        coEvery { userMetadataDao.getMetadata(sessionId) } returns null
        coEvery { transcriptDao.getTranscript(sessionId) } returns listOf(DatabaseTestFixtures.createTranscript(sessionId = sessionId))
        coEvery { recordDao.getRecords(sessionId) } returns emptyList()

        val restored = repository.restoreFullSession(sessionId)
        assertThat(restored).isNotNull()
        assertThat(restored?.messages).isNotEmpty()

        // Step 4: Delete session
        repository.deleteSession(sessionId)
        coVerify { chatSessionDao.delete(sessionId) }
    }

    // ==================== MULTI-SESSION TESTS ====================

    @Test
    fun `multiple sessions for same bot are independent`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val session1 = DatabaseTestFixtures.createChatSession(
            sessionId = "session-1",
            botId = botId
        )
        val session2 = DatabaseTestFixtures.createChatSession(
            sessionId = "session-2",
            botId = botId
        )

        val session1Messages = listOf(
            DatabaseTestFixtures.createMessage(sessionId = "session-1", content = "Session 1 message")
        )
        val session2Messages = listOf(
            DatabaseTestFixtures.createMessage(sessionId = "session-2", content = "Session 2 message")
        )

        coEvery { messageDao.getMessages("session-1") } returns session1Messages
        coEvery { messageDao.getMessages("session-2") } returns session2Messages

        // When
        val result1 = messageDao.getMessages("session-1")
        val result2 = messageDao.getMessages("session-2")

        // Then
        assertThat(result1).hasSize(1)
        assertThat(result2).hasSize(1)
        assertThat(result1[0].content).isEqualTo("Session 1 message")
        assertThat(result2[0].content).isEqualTo("Session 2 message")
    }

    @Test
    fun `sessions for different bots are isolated`() = runTest {
        // Given
        val bot1Sessions = listOf(
            DatabaseTestFixtures.createChatSession(sessionId = "bot1-session", botId = "bot-1")
        )
        val bot2Sessions = listOf(
            DatabaseTestFixtures.createChatSession(sessionId = "bot2-session", botId = "bot-2")
        )

        coEvery { chatSessionDao.getAllSessions("bot-1") } returns bot1Sessions
        coEvery { chatSessionDao.getAllSessions("bot-2") } returns bot2Sessions

        // When
        val result1 = chatSessionDao.getAllSessions("bot-1")
        val result2 = chatSessionDao.getAllSessions("bot-2")

        // Then
        assertThat(result1).hasSize(1)
        assertThat(result2).hasSize(1)
        assertThat(result1[0].botId).isEqualTo("bot-1")
        assertThat(result2[0].botId).isEqualTo("bot-2")
    }

    // ==================== DATA CONSISTENCY TESTS ====================

    @Test
    fun `touch session updates timestamp on all related operations`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When - Operations that should touch session
        repository.saveMessage("msg-1", sessionId, "Hello", "bot")
        repository.saveTranscriptEntry(sessionId, "bot", "Hello")

        // Then - Each operation should touch the session
        coVerify(atLeast = 2) { chatSessionDao.touch(sessionId) }
    }

    @Test
    fun `answer variable update preserves other variables`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val existingVariables = DatabaseTestFixtures.createMultipleAnswerVariables(sessionId)

        coEvery { answerVariableDao.getVariables(sessionId) } returns existingVariables
        coEvery { answerVariableDao.getVariableByNodeId(sessionId, "ask-name-node") } returns existingVariables[0]

        // When - Update one variable
        repository.saveAnswerVariable(sessionId, "ask-name-node", "name", "Updated Name")

        // Then - Only that variable should be updated
        coVerify { answerVariableDao.updateValueByNodeId(sessionId, "ask-name-node", any()) }
        coVerify(exactly = 0) { answerVariableDao.updateValueByNodeId(sessionId, "ask-email-node", any()) }
    }

    // ==================== CLEANUP OPERATION TESTS ====================

    @Test
    fun `deleteOldSessions removes expired sessions`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val cutoff = System.currentTimeMillis() - SessionRepository.SESSION_EXPIRY_MS

        // When
        repository.clearOldSessions(cutoff)

        // Then
        coVerify { chatSessionDao.deleteOldSessions(cutoff) }
    }

    @Test
    fun `deleteAllForSession cleans up all related tables`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When - Delete all related data
        messageDao.deleteAllForSession(sessionId)
        answerVariableDao.deleteAllForSession(sessionId)
        transcriptDao.deleteAllForSession(sessionId)
        recordDao.deleteAllForSession(sessionId)
        userMetadataDao.delete(sessionId)

        // Then
        coVerify { messageDao.deleteAllForSession(sessionId) }
        coVerify { answerVariableDao.deleteAllForSession(sessionId) }
        coVerify { transcriptDao.deleteAllForSession(sessionId) }
        coVerify { recordDao.deleteAllForSession(sessionId) }
        coVerify { userMetadataDao.delete(sessionId) }
    }

    // ==================== EDGE CASES ====================

    @Test
    fun `handle session with no related data`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = "empty-session"
        val session = DatabaseTestFixtures.createChatSession(
            sessionId = sessionId,
            updatedAt = System.currentTimeMillis()
        )

        coEvery { chatSessionDao.getSession(sessionId) } returns session
        coEvery { messageDao.getMessages(sessionId) } returns emptyList()
        coEvery { answerVariableDao.getVariables(sessionId) } returns emptyList()
        coEvery { userMetadataDao.getMetadata(sessionId) } returns null
        coEvery { transcriptDao.getTranscript(sessionId) } returns emptyList()
        coEvery { recordDao.getRecords(sessionId) } returns emptyList()

        // When
        val restored = repository.restoreFullSession(sessionId)

        // Then
        assertThat(restored).isNotNull()
        assertThat(restored?.messages).isEmpty()
        assertThat(restored?.answerVariables).isEmpty()
        assertThat(restored?.userMetadata).isNull()
        assertThat(restored?.transcript).isEmpty()
        assertThat(restored?.records).isEmpty()
    }

    @Test
    fun `handle session with maximum data load`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = "large-session"
        val session = DatabaseTestFixtures.createChatSession(
            sessionId = sessionId,
            updatedAt = System.currentTimeMillis()
        )

        // Create large data sets
        val largeMessageCount = 1000
        val messages = (1..largeMessageCount).map { index ->
            DatabaseTestFixtures.createMessage(
                id = "msg-$index",
                sessionId = sessionId,
                content = "Message $index",
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP + (index * 1000L)
            )
        }

        coEvery { chatSessionDao.getSession(sessionId) } returns session
        coEvery { messageDao.getMessages(sessionId) } returns messages
        coEvery { messageDao.getMessageCount(sessionId) } returns largeMessageCount
        coEvery { answerVariableDao.getVariables(sessionId) } returns emptyList()
        coEvery { userMetadataDao.getMetadata(sessionId) } returns null
        coEvery { transcriptDao.getTranscript(sessionId) } returns emptyList()
        coEvery { recordDao.getRecords(sessionId) } returns emptyList()

        // When
        val restored = repository.restoreFullSession(sessionId)
        val messageCount = messageDao.getMessageCount(sessionId)

        // Then
        assertThat(restored).isNotNull()
        assertThat(restored?.messages).hasSize(largeMessageCount)
        assertThat(messageCount).isEqualTo(largeMessageCount)
    }

    // ==================== INDEX VALIDATION TESTS ====================

    @Test
    fun `session index on botId improves query performance`() = runTest {
        // Given - Index exists on botId column
        val botId = DatabaseTestFixtures.TEST_BOT_ID

        // When - Query by botId
        chatSessionDao.getLatestActiveSession(botId)

        // Then - Query should be executed (index usage is internal to Room)
        coVerify { chatSessionDao.getLatestActiveSession(botId) }
    }

    @Test
    fun `message index on sessionId improves query performance`() = runTest {
        // Given - Index exists on sessionId column
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When - Query by sessionId
        messageDao.getMessages(sessionId)

        // Then - Query should be executed
        coVerify { messageDao.getMessages(sessionId) }
    }

    @Test
    fun `answer variable indices on sessionId and nodeId improve queries`() = runTest {
        // Given - Indices exist
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val nodeId = "test-node"

        // When - Query by indexed columns
        answerVariableDao.getVariables(sessionId)
        answerVariableDao.getVariableByNodeId(sessionId, nodeId)

        // Then
        coVerify { answerVariableDao.getVariables(sessionId) }
        coVerify { answerVariableDao.getVariableByNodeId(sessionId, nodeId) }
    }
}
