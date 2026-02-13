package com.conferbot.sdk.core

import android.content.Context
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.data.ConferbotDatabase
import com.conferbot.sdk.data.SessionRepository
import com.conferbot.sdk.data.dao.AnswerVariableDao
import com.conferbot.sdk.data.dao.ChatSessionDao
import com.conferbot.sdk.data.dao.MessageDao
import com.conferbot.sdk.data.dao.RecordDao
import com.conferbot.sdk.data.dao.TranscriptDao
import com.conferbot.sdk.data.dao.UserMetadataDao
import com.conferbot.sdk.data.entities.ChatSessionEntity
import com.conferbot.sdk.data.testutils.DatabaseTestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SessionPersistenceManager
 * Tests session persistence, restoration, timeout handling, and data cleanup
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionPersistenceManagerTest {

    private lateinit var context: Context
    private lateinit var database: ConferbotDatabase
    private lateinit var chatSessionDao: ChatSessionDao
    private lateinit var messageDao: MessageDao
    private lateinit var answerVariableDao: AnswerVariableDao
    private lateinit var userMetadataDao: UserMetadataDao
    private lateinit var transcriptDao: TranscriptDao
    private lateinit var recordDao: RecordDao
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        database = mockk(relaxed = true)
        chatSessionDao = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        answerVariableDao = mockk(relaxed = true)
        userMetadataDao = mockk(relaxed = true)
        transcriptDao = mockk(relaxed = true)
        recordDao = mockk(relaxed = true)

        // Setup database to return mocked DAOs
        every { database.chatSessionDao() } returns chatSessionDao
        every { database.messageDao() } returns messageDao
        every { database.answerVariableDao() } returns answerVariableDao
        every { database.userMetadataDao() } returns userMetadataDao
        every { database.transcriptDao() } returns transcriptDao
        every { database.recordDao() } returns recordDao

        // Mock ConferbotDatabase singleton
        mockkObject(ConferbotDatabase)
        every { ConferbotDatabase.getInstance(any()) } returns database

        // Reset ChatState
        ChatState.reset()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(ConferbotDatabase)
        ChatState.reset()
    }

    // ==================== hasValidSession TESTS ====================

    @Test
    fun `hasValidSession returns true when valid session exists`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val validSession = DatabaseTestFixtures.createChatSession(
            botId = botId,
            isActive = true,
            updatedAt = System.currentTimeMillis() - 1000 // 1 second ago
        )
        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns validSession

        // When
        val result = SessionPersistenceManager.hasValidSession(context, botId)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `hasValidSession returns false when no session exists`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns null

        // When
        val result = SessionPersistenceManager.hasValidSession(context, botId)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `hasValidSession returns false when session is expired`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val expiredSession = DatabaseTestFixtures.createChatSession(
            botId = botId,
            isActive = true,
            updatedAt = System.currentTimeMillis() - (SessionRepository.SESSION_EXPIRY_MS + 1000)
        )
        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns expiredSession

        // When
        val result = SessionPersistenceManager.hasValidSession(context, botId)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `hasValidSession returns false on error`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        coEvery { chatSessionDao.getLatestActiveSession(botId) } throws Exception("Database error")

        // When
        val result = SessionPersistenceManager.hasValidSession(context, botId)

        // Then
        assertThat(result).isFalse()
    }

    // ==================== restoreSession TESTS ====================

    @Test
    fun `restoreSession returns failure when no valid session exists`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns null

        // When
        val result = SessionPersistenceManager.restoreSession(context, botId)

        // Then
        assertThat(result.success).isFalse()
        assertThat(result.reason).contains("No valid session found")
    }

    @Test
    fun `restoreSession returns success with session details when valid session exists`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val session = DatabaseTestFixtures.createChatSession(
            botId = botId,
            isActive = true,
            updatedAt = System.currentTimeMillis() - 1000
        )
        val messages = DatabaseTestFixtures.createMultipleMessages(sessionId = session.sessionId)
        val answerVariables = DatabaseTestFixtures.createMultipleAnswerVariables(sessionId = session.sessionId)

        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns session
        coEvery { chatSessionDao.getSession(session.sessionId) } returns session
        coEvery { messageDao.getMessages(session.sessionId) } returns messages
        coEvery { answerVariableDao.getVariables(session.sessionId) } returns answerVariables
        coEvery { userMetadataDao.getMetadata(session.sessionId) } returns null
        coEvery { transcriptDao.getTranscript(session.sessionId) } returns emptyList()
        coEvery { recordDao.getRecords(session.sessionId) } returns emptyList()

        // Mock ChatState.restoreSession
        mockkObject(ChatState)
        coEvery { ChatState.restoreSession(any(), any()) } returns true

        // When
        val result = SessionPersistenceManager.restoreSession(context, botId)

        // Then
        assertThat(result.success).isTrue()
        assertThat(result.sessionId).isEqualTo(session.sessionId)
        assertThat(result.visitorId).isEqualTo(session.visitorId)
        assertThat(result.messageCount).isEqualTo(messages.size)
        assertThat(result.answerVariableCount).isEqualTo(answerVariables.size)

        unmockkObject(ChatState)
    }

    @Test
    fun `restoreSession returns failure when ChatState restore fails`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val session = DatabaseTestFixtures.createChatSession(
            botId = botId,
            isActive = true,
            updatedAt = System.currentTimeMillis() - 1000
        )

        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns session
        coEvery { chatSessionDao.getSession(session.sessionId) } returns session
        coEvery { messageDao.getMessages(session.sessionId) } returns emptyList()
        coEvery { answerVariableDao.getVariables(session.sessionId) } returns emptyList()
        coEvery { userMetadataDao.getMetadata(session.sessionId) } returns null
        coEvery { transcriptDao.getTranscript(session.sessionId) } returns emptyList()
        coEvery { recordDao.getRecords(session.sessionId) } returns emptyList()

        // Mock ChatState.restoreSession to return false
        mockkObject(ChatState)
        coEvery { ChatState.restoreSession(any(), any()) } returns false

        // When
        val result = SessionPersistenceManager.restoreSession(context, botId)

        // Then
        assertThat(result.success).isFalse()
        assertThat(result.reason).contains("Failed to restore session")

        unmockkObject(ChatState)
    }

    @Test
    fun `restoreSession handles exceptions gracefully`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        coEvery { chatSessionDao.getLatestActiveSession(botId) } throws Exception("Database error")

        // When
        val result = SessionPersistenceManager.restoreSession(context, botId)

        // Then
        assertThat(result.success).isFalse()
        assertThat(result.reason).contains("Database error")
    }

    // ==================== clearExpiredSessions TESTS ====================

    @Test
    fun `clearExpiredSessions deletes old sessions`() = runTest {
        // Given
        val expiryMs = SessionRepository.SESSION_EXPIRY_MS

        // When
        SessionPersistenceManager.clearExpiredSessions(context, expiryMs)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { chatSessionDao.deleteOldSessions(any()) }
    }

    @Test
    fun `clearExpiredSessions uses default expiry when not specified`() = runTest {
        // When
        SessionPersistenceManager.clearExpiredSessions(context)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { chatSessionDao.deleteOldSessions(any()) }
    }

    @Test
    fun `clearExpiredSessions handles errors gracefully`() = runTest {
        // Given
        coEvery { chatSessionDao.deleteOldSessions(any()) } throws Exception("Delete error")

        // When - should not throw
        SessionPersistenceManager.clearExpiredSessions(context)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - no exception thrown
    }

    // ==================== getSessionInfo TESTS ====================

    @Test
    fun `getSessionInfo returns session info when valid session exists`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val session = DatabaseTestFixtures.createChatSession(
            botId = botId,
            isActive = true,
            updatedAt = System.currentTimeMillis() - 1000,
            currentIndex = 5
        )
        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns session

        // When
        val result = SessionPersistenceManager.getSessionInfo(context, botId)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.sessionId).isEqualTo(session.sessionId)
        assertThat(result?.visitorId).isEqualTo(session.visitorId)
        assertThat(result?.botId).isEqualTo(session.botId)
        assertThat(result?.currentIndex).isEqualTo(5)
        assertThat(result?.isValid).isTrue()
    }

    @Test
    fun `getSessionInfo returns null when no session exists`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns null

        // When
        val result = SessionPersistenceManager.getSessionInfo(context, botId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getSessionInfo returns info with isValid false for expired session`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val expiredSession = DatabaseTestFixtures.createChatSession(
            botId = botId,
            isActive = true,
            updatedAt = System.currentTimeMillis() - (SessionRepository.SESSION_EXPIRY_MS + 1000)
        )
        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns expiredSession

        // When
        val result = SessionPersistenceManager.getSessionInfo(context, botId)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.isValid).isFalse()
    }

    @Test
    fun `getSessionInfo handles errors gracefully`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        coEvery { chatSessionDao.getLatestActiveSession(botId) } throws Exception("Database error")

        // When
        val result = SessionPersistenceManager.getSessionInfo(context, botId)

        // Then
        assertThat(result).isNull()
    }

    // ==================== invalidateSession TESTS ====================

    @Test
    fun `invalidateSession deactivates session`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        SessionPersistenceManager.invalidateSession(context, sessionId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { chatSessionDao.updateActiveStatus(sessionId, false, any()) }
    }

    @Test
    fun `invalidateSession handles errors gracefully`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        coEvery { chatSessionDao.updateActiveStatus(any(), any(), any()) } throws Exception("Update error")

        // When - should not throw
        SessionPersistenceManager.invalidateSession(context, sessionId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - no exception thrown
    }

    // ==================== deleteSession TESTS ====================

    @Test
    fun `deleteSession removes session`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        SessionPersistenceManager.deleteSession(context, sessionId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { chatSessionDao.delete(sessionId) }
    }

    @Test
    fun `deleteSession handles errors gracefully`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        coEvery { chatSessionDao.delete(any()) } throws Exception("Delete error")

        // When - should not throw
        SessionPersistenceManager.deleteSession(context, sessionId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - no exception thrown
    }

    // ==================== SessionRestoreResult TESTS ====================

    @Test
    fun `SessionRestoreResult success case has all required fields`() {
        // Given/When
        val result = SessionRestoreResult(
            success = true,
            sessionId = "session-123",
            visitorId = "visitor-456",
            messageCount = 10,
            answerVariableCount = 5
        )

        // Then
        assertThat(result.success).isTrue()
        assertThat(result.sessionId).isEqualTo("session-123")
        assertThat(result.visitorId).isEqualTo("visitor-456")
        assertThat(result.messageCount).isEqualTo(10)
        assertThat(result.answerVariableCount).isEqualTo(5)
        assertThat(result.reason).isNull()
    }

    @Test
    fun `SessionRestoreResult failure case has reason`() {
        // Given/When
        val result = SessionRestoreResult(
            success = false,
            reason = "Session expired"
        )

        // Then
        assertThat(result.success).isFalse()
        assertThat(result.reason).isEqualTo("Session expired")
        assertThat(result.sessionId).isNull()
    }

    // ==================== SessionInfo TESTS ====================

    @Test
    fun `SessionInfo contains all session details`() {
        // Given/When
        val info = SessionInfo(
            sessionId = "session-123",
            visitorId = "visitor-456",
            botId = "bot-789",
            currentIndex = 10,
            createdAt = 1000L,
            updatedAt = 2000L,
            isValid = true
        )

        // Then
        assertThat(info.sessionId).isEqualTo("session-123")
        assertThat(info.visitorId).isEqualTo("visitor-456")
        assertThat(info.botId).isEqualTo("bot-789")
        assertThat(info.currentIndex).isEqualTo(10)
        assertThat(info.createdAt).isEqualTo(1000L)
        assertThat(info.updatedAt).isEqualTo(2000L)
        assertThat(info.isValid).isTrue()
    }

    // ==================== TIMEOUT HANDLING TESTS ====================

    @Test
    fun `session at expiry boundary is still valid`() = runTest {
        // Given - Session updated exactly at expiry time minus 1 ms
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val borderlineSession = DatabaseTestFixtures.createChatSession(
            botId = botId,
            isActive = true,
            updatedAt = System.currentTimeMillis() - SessionRepository.SESSION_EXPIRY_MS + 1
        )
        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns borderlineSession

        // When
        val result = SessionPersistenceManager.hasValidSession(context, botId)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `session just past expiry is invalid`() = runTest {
        // Given - Session updated just past expiry time
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val expiredSession = DatabaseTestFixtures.createChatSession(
            botId = botId,
            isActive = true,
            updatedAt = System.currentTimeMillis() - SessionRepository.SESSION_EXPIRY_MS - 1
        )
        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns expiredSession

        // When
        val result = SessionPersistenceManager.hasValidSession(context, botId)

        // Then
        assertThat(result).isFalse()
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    fun `handles empty bot ID`() = runTest {
        // Given
        val emptyBotId = ""
        coEvery { chatSessionDao.getLatestActiveSession(emptyBotId) } returns null

        // When
        val result = SessionPersistenceManager.hasValidSession(context, emptyBotId)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `handles whitespace-only bot ID`() = runTest {
        // Given
        val whitespaceBotId = "   "
        coEvery { chatSessionDao.getLatestActiveSession(whitespaceBotId) } returns null

        // When
        val result = SessionPersistenceManager.hasValidSession(context, whitespaceBotId)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `handles very long session ID`() = runTest {
        // Given
        val longSessionId = "x".repeat(1000)

        // When
        SessionPersistenceManager.invalidateSession(context, longSessionId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { chatSessionDao.updateActiveStatus(longSessionId, false, any()) }
    }
}
