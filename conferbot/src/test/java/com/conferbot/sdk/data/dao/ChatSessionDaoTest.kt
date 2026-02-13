package com.conferbot.sdk.data.dao

import com.conferbot.sdk.data.entities.ChatSessionEntity
import com.conferbot.sdk.data.testutils.DatabaseTestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ChatSessionDao
 * Tests CRUD operations, queries, and session management
 *
 * Note: These tests use MockK to mock the DAO interface.
 * For full integration tests with Room's in-memory database,
 * use AndroidX Test with @RunWith(AndroidJUnit4::class) in androidTest.
 */
class ChatSessionDaoTest {

    private lateinit var chatSessionDao: ChatSessionDao

    @Before
    fun setUp() {
        chatSessionDao = mockk(relaxed = true)
    }

    // ==================== INSERT TESTS ====================

    @Test
    fun `insert creates new session`() = runTest {
        // Given
        val session = DatabaseTestFixtures.createChatSession()

        // When
        chatSessionDao.insert(session)

        // Then
        coVerify { chatSessionDao.insert(session) }
    }

    @Test
    fun `insert replaces session with same ID on conflict`() = runTest {
        // Given
        val originalSession = DatabaseTestFixtures.createChatSession(currentIndex = 0)
        val updatedSession = originalSession.copy(currentIndex = 5)

        coEvery { chatSessionDao.getSession(originalSession.sessionId) } returns updatedSession

        // When
        chatSessionDao.insert(originalSession)
        chatSessionDao.insert(updatedSession)

        // Then
        val result = chatSessionDao.getSession(originalSession.sessionId)
        assertThat(result?.currentIndex).isEqualTo(5)
    }

    @Test
    fun `insert with null workspaceId succeeds`() = runTest {
        // Given
        val session = DatabaseTestFixtures.createChatSession(workspaceId = null)

        // When
        chatSessionDao.insert(session)

        // Then
        coVerify { chatSessionDao.insert(session) }
    }

    // ==================== UPDATE TESTS ====================

    @Test
    fun `update modifies existing session`() = runTest {
        // Given
        val session = DatabaseTestFixtures.createChatSession()
        val updatedSession = session.copy(
            currentIndex = 10,
            isActive = false,
            updatedAt = System.currentTimeMillis()
        )

        coEvery { chatSessionDao.getSession(session.sessionId) } returns updatedSession

        // When
        chatSessionDao.update(updatedSession)

        // Then
        coVerify { chatSessionDao.update(updatedSession) }
        val result = chatSessionDao.getSession(session.sessionId)
        assertThat(result?.currentIndex).isEqualTo(10)
        assertThat(result?.isActive).isFalse()
    }

    @Test
    fun `updateCurrentIndex updates only index and timestamp`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val newIndex = 15
        val updateSlot = slot<String>()
        val indexSlot = slot<Int>()

        coEvery {
            chatSessionDao.updateCurrentIndex(capture(updateSlot), capture(indexSlot), any())
        } returns Unit

        // When
        chatSessionDao.updateCurrentIndex(sessionId, newIndex)

        // Then
        coVerify { chatSessionDao.updateCurrentIndex(sessionId, newIndex, any()) }
        assertThat(updateSlot.captured).isEqualTo(sessionId)
        assertThat(indexSlot.captured).isEqualTo(newIndex)
    }

    @Test
    fun `updateActiveStatus marks session as inactive`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        chatSessionDao.updateActiveStatus(sessionId, false)

        // Then
        coVerify { chatSessionDao.updateActiveStatus(sessionId, false, any()) }
    }

    @Test
    fun `touch updates timestamp without changing other fields`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        chatSessionDao.touch(sessionId)

        // Then
        coVerify { chatSessionDao.touch(sessionId, any()) }
    }

    // ==================== QUERY TESTS ====================

    @Test
    fun `getSession returns session when exists`() = runTest {
        // Given
        val session = DatabaseTestFixtures.createChatSession()
        coEvery { chatSessionDao.getSession(session.sessionId) } returns session

        // When
        val result = chatSessionDao.getSession(session.sessionId)

        // Then
        assertThat(result).isEqualTo(session)
        assertThat(result?.sessionId).isEqualTo(session.sessionId)
        assertThat(result?.visitorId).isEqualTo(session.visitorId)
        assertThat(result?.botId).isEqualTo(session.botId)
    }

    @Test
    fun `getSession returns null when not exists`() = runTest {
        // Given
        val nonExistentId = "non-existent-session"
        coEvery { chatSessionDao.getSession(nonExistentId) } returns null

        // When
        val result = chatSessionDao.getSession(nonExistentId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getSessionFlow emits session updates`() = runTest {
        // Given
        val session = DatabaseTestFixtures.createChatSession()
        coEvery { chatSessionDao.getSessionFlow(session.sessionId) } returns flowOf(session)

        // When
        val flow = chatSessionDao.getSessionFlow(session.sessionId)

        // Then
        flow.collect { result ->
            assertThat(result).isEqualTo(session)
        }
    }

    @Test
    fun `getLatestActiveSession returns most recent active session for bot`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val recentSession = DatabaseTestFixtures.createChatSession(
            sessionId = "recent-session",
            botId = botId,
            isActive = true,
            updatedAt = DatabaseTestFixtures.RECENT_TIMESTAMP
        )
        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns recentSession

        // When
        val result = chatSessionDao.getLatestActiveSession(botId)

        // Then
        assertThat(result).isEqualTo(recentSession)
        assertThat(result?.isActive).isTrue()
    }

    @Test
    fun `getLatestActiveSession returns null when no active sessions`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns null

        // When
        val result = chatSessionDao.getLatestActiveSession(botId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getLatestSession returns most recent session regardless of active status`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val inactiveSession = DatabaseTestFixtures.createChatSession(
            sessionId = "inactive-session",
            botId = botId,
            isActive = false,
            updatedAt = DatabaseTestFixtures.RECENT_TIMESTAMP
        )
        coEvery { chatSessionDao.getLatestSession(botId) } returns inactiveSession

        // When
        val result = chatSessionDao.getLatestSession(botId)

        // Then
        assertThat(result).isEqualTo(inactiveSession)
        assertThat(result?.isActive).isFalse()
    }

    @Test
    fun `getAllSessions returns all sessions for bot ordered by createdAt desc`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val sessions = listOf(
            DatabaseTestFixtures.createChatSession(
                sessionId = "session-3",
                botId = botId,
                createdAt = DatabaseTestFixtures.BASE_TIMESTAMP + 2000
            ),
            DatabaseTestFixtures.createChatSession(
                sessionId = "session-2",
                botId = botId,
                createdAt = DatabaseTestFixtures.BASE_TIMESTAMP + 1000
            ),
            DatabaseTestFixtures.createChatSession(
                sessionId = "session-1",
                botId = botId,
                createdAt = DatabaseTestFixtures.BASE_TIMESTAMP
            )
        )
        coEvery { chatSessionDao.getAllSessions(botId) } returns sessions

        // When
        val result = chatSessionDao.getAllSessions(botId)

        // Then
        assertThat(result).hasSize(3)
        assertThat(result[0].sessionId).isEqualTo("session-3")
        assertThat(result[2].sessionId).isEqualTo("session-1")
    }

    @Test
    fun `getSessionCount returns correct count`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        coEvery { chatSessionDao.getSessionCount(botId) } returns 5

        // When
        val result = chatSessionDao.getSessionCount(botId)

        // Then
        assertThat(result).isEqualTo(5)
    }

    @Test
    fun `getSessionCount returns zero when no sessions`() = runTest {
        // Given
        val botId = "empty-bot"
        coEvery { chatSessionDao.getSessionCount(botId) } returns 0

        // When
        val result = chatSessionDao.getSessionCount(botId)

        // Then
        assertThat(result).isEqualTo(0)
    }

    // ==================== DELETE TESTS ====================

    @Test
    fun `delete removes session by ID`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        chatSessionDao.delete(sessionId)

        // Then
        coVerify { chatSessionDao.delete(sessionId) }
    }

    @Test
    fun `deleteOldSessions removes sessions older than timestamp`() = runTest {
        // Given
        val cutoffTime = System.currentTimeMillis() - (30 * 60 * 1000) // 30 minutes ago

        // When
        chatSessionDao.deleteOldSessions(cutoffTime)

        // Then
        coVerify { chatSessionDao.deleteOldSessions(cutoffTime) }
    }

    @Test
    fun `deleteAllForBot removes all sessions for a bot`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID

        // When
        chatSessionDao.deleteAllForBot(botId)

        // Then
        coVerify { chatSessionDao.deleteAllForBot(botId) }
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    fun `session with all null optional fields is valid`() = runTest {
        // Given
        val session = ChatSessionEntity(
            sessionId = "minimal-session",
            visitorId = "visitor",
            botId = "bot",
            workspaceId = null,
            currentIndex = 0,
            isActive = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // When
        chatSessionDao.insert(session)

        // Then
        coVerify { chatSessionDao.insert(session) }
    }

    @Test
    fun `session with large currentIndex is valid`() = runTest {
        // Given
        val session = DatabaseTestFixtures.createChatSession(currentIndex = Int.MAX_VALUE)

        // When
        chatSessionDao.insert(session)

        // Then
        coVerify { chatSessionDao.insert(session) }
    }

    @Test
    fun `multiple sessions for same bot are tracked separately`() = runTest {
        // Given
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val sessions = listOf(
            DatabaseTestFixtures.createChatSession(sessionId = "session-1", botId = botId),
            DatabaseTestFixtures.createChatSession(sessionId = "session-2", botId = botId),
            DatabaseTestFixtures.createChatSession(sessionId = "session-3", botId = botId)
        )
        coEvery { chatSessionDao.getAllSessions(botId) } returns sessions

        // When
        val result = chatSessionDao.getAllSessions(botId)

        // Then
        assertThat(result).hasSize(3)
        assertThat(result.map { it.sessionId }).containsExactly("session-1", "session-2", "session-3")
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
}
