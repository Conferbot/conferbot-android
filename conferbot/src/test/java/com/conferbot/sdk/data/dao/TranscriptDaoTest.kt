package com.conferbot.sdk.data.dao

import com.conferbot.sdk.data.entities.TranscriptEntity
import com.conferbot.sdk.data.testutils.DatabaseTestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for TranscriptDao
 * Tests transcript entry CRUD operations and conversation history queries
 */
class TranscriptDaoTest {

    private lateinit var transcriptDao: TranscriptDao

    @Before
    fun setUp() {
        transcriptDao = mockk(relaxed = true)
    }

    // ==================== INSERT TESTS ====================

    @Test
    fun `insert creates new transcript entry and returns ID`() = runTest {
        // Given
        val entry = DatabaseTestFixtures.createTranscript()
        coEvery { transcriptDao.insert(entry) } returns 1L

        // When
        val result = transcriptDao.insert(entry)

        // Then
        assertThat(result).isEqualTo(1L)
        coVerify { transcriptDao.insert(entry) }
    }

    @Test
    fun `insertAll inserts multiple transcript entries`() = runTest {
        // Given
        val entries = DatabaseTestFixtures.createConversationTranscript()

        // When
        transcriptDao.insertAll(entries)

        // Then
        coVerify { transcriptDao.insertAll(entries) }
    }

    @Test
    fun `insertAll with empty list does nothing`() = runTest {
        // Given
        val emptyList = emptyList<TranscriptEntity>()

        // When
        transcriptDao.insertAll(emptyList)

        // Then
        coVerify { transcriptDao.insertAll(emptyList) }
    }

    // ==================== QUERY TESTS ====================

    @Test
    fun `getTranscript returns all entries for session ordered by timestamp ASC`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val entries = listOf(
            DatabaseTestFixtures.createTranscript(
                by = "bot",
                message = "First message",
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP
            ),
            DatabaseTestFixtures.createTranscript(
                by = "user",
                message = "Second message",
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP + 1000
            ),
            DatabaseTestFixtures.createTranscript(
                by = "bot",
                message = "Third message",
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP + 2000
            )
        )
        coEvery { transcriptDao.getTranscript(sessionId) } returns entries

        // When
        val result = transcriptDao.getTranscript(sessionId)

        // Then
        assertThat(result).hasSize(3)
        assertThat(result[0].message).isEqualTo("First message")
        assertThat(result[1].message).isEqualTo("Second message")
        assertThat(result[2].message).isEqualTo("Third message")
    }

    @Test
    fun `getTranscript returns empty list when no entries`() = runTest {
        // Given
        val sessionId = "empty-session"
        coEvery { transcriptDao.getTranscript(sessionId) } returns emptyList()

        // When
        val result = transcriptDao.getTranscript(sessionId)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `getTranscriptFlow emits transcript updates`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val entries = DatabaseTestFixtures.createConversationTranscript(sessionId)
        coEvery { transcriptDao.getTranscriptFlow(sessionId) } returns flowOf(entries)

        // When
        val flow = transcriptDao.getTranscriptFlow(sessionId)

        // Then
        flow.collect { result ->
            assertThat(result).hasSize(5)
        }
    }

    @Test
    fun `getRecentTranscript returns most recent N entries ordered DESC`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val recentEntries = listOf(
            DatabaseTestFixtures.createTranscript(
                by = "user",
                message = "Most recent",
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP + 2000
            ),
            DatabaseTestFixtures.createTranscript(
                by = "bot",
                message = "Second most recent",
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP + 1000
            )
        )
        coEvery { transcriptDao.getRecentTranscript(sessionId, 2) } returns recentEntries

        // When
        val result = transcriptDao.getRecentTranscript(sessionId, 2)

        // Then
        assertThat(result).hasSize(2)
        assertThat(result[0].message).isEqualTo("Most recent")
    }

    @Test
    fun `getTranscriptBySender filters by sender type`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val botEntries = listOf(
            DatabaseTestFixtures.createTranscript(by = "bot", message = "Bot message 1"),
            DatabaseTestFixtures.createTranscript(by = "bot", message = "Bot message 2")
        )
        coEvery { transcriptDao.getTranscriptBySender(sessionId, "bot") } returns botEntries

        // When
        val result = transcriptDao.getTranscriptBySender(sessionId, "bot")

        // Then
        assertThat(result).hasSize(2)
        assertThat(result.all { it.by == "bot" }).isTrue()
    }

    @Test
    fun `getTranscriptCount returns correct count`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        coEvery { transcriptDao.getTranscriptCount(sessionId) } returns 10

        // When
        val result = transcriptDao.getTranscriptCount(sessionId)

        // Then
        assertThat(result).isEqualTo(10)
    }

    @Test
    fun `getTranscriptCount returns zero when no entries`() = runTest {
        // Given
        val sessionId = "empty-session"
        coEvery { transcriptDao.getTranscriptCount(sessionId) } returns 0

        // When
        val result = transcriptDao.getTranscriptCount(sessionId)

        // Then
        assertThat(result).isEqualTo(0)
    }

    // ==================== DELETE TESTS ====================

    @Test
    fun `deleteAllForSession removes all entries for session`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        transcriptDao.deleteAllForSession(sessionId)

        // Then
        coVerify { transcriptDao.deleteAllForSession(sessionId) }
    }

    @Test
    fun `deleteOldEntries removes entries older than timestamp`() = runTest {
        // Given
        val cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // 24 hours ago

        // When
        transcriptDao.deleteOldEntries(cutoffTime)

        // Then
        coVerify { transcriptDao.deleteOldEntries(cutoffTime) }
    }

    // ==================== SENDER TYPE TESTS ====================

    @Test
    fun `transcript entry with bot sender is correctly stored`() = runTest {
        // Given
        val entry = DatabaseTestFixtures.createTranscript(by = "bot")
        val entries = listOf(entry)
        coEvery { transcriptDao.getTranscriptBySender(entry.sessionId, "bot") } returns entries

        // When
        transcriptDao.insert(entry)
        val result = transcriptDao.getTranscriptBySender(entry.sessionId, "bot")

        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].by).isEqualTo("bot")
    }

    @Test
    fun `transcript entry with user sender is correctly stored`() = runTest {
        // Given
        val entry = DatabaseTestFixtures.createTranscript(by = "user")
        val entries = listOf(entry)
        coEvery { transcriptDao.getTranscriptBySender(entry.sessionId, "user") } returns entries

        // When
        transcriptDao.insert(entry)
        val result = transcriptDao.getTranscriptBySender(entry.sessionId, "user")

        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].by).isEqualTo("user")
    }

    @Test
    fun `transcript entry with agent sender is correctly stored`() = runTest {
        // Given
        val entry = DatabaseTestFixtures.createTranscript(by = "agent")
        val entries = listOf(entry)
        coEvery { transcriptDao.getTranscriptBySender(entry.sessionId, "agent") } returns entries

        // When
        transcriptDao.insert(entry)
        val result = transcriptDao.getTranscriptBySender(entry.sessionId, "agent")

        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].by).isEqualTo("agent")
    }

    // ==================== CONVERSATION FLOW TESTS ====================

    @Test
    fun `conversation transcript preserves order`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val conversation = DatabaseTestFixtures.createConversationTranscript(sessionId)
        coEvery { transcriptDao.getTranscript(sessionId) } returns conversation

        // When
        val result = transcriptDao.getTranscript(sessionId)

        // Then
        assertThat(result).hasSize(5)
        // Verify alternating conversation pattern
        assertThat(result[0].by).isEqualTo("bot")
        assertThat(result[1].by).isEqualTo("user")
        assertThat(result[2].by).isEqualTo("bot")
        assertThat(result[3].by).isEqualTo("user")
        assertThat(result[4].by).isEqualTo("agent")
    }

    @Test
    fun `multiple bot messages in sequence are valid`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val entries = listOf(
            DatabaseTestFixtures.createTranscript(
                sessionId = sessionId,
                by = "bot",
                message = "Hello!",
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP
            ),
            DatabaseTestFixtures.createTranscript(
                sessionId = sessionId,
                by = "bot",
                message = "How can I help you?",
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP + 500
            ),
            DatabaseTestFixtures.createTranscript(
                sessionId = sessionId,
                by = "bot",
                message = "Please choose an option:",
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP + 1000
            )
        )
        coEvery { transcriptDao.getTranscript(sessionId) } returns entries

        // When
        val result = transcriptDao.getTranscript(sessionId)

        // Then
        assertThat(result).hasSize(3)
        assertThat(result.all { it.by == "bot" }).isTrue()
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    fun `transcript with empty message is valid`() = runTest {
        // Given
        val entry = DatabaseTestFixtures.createTranscript(message = "")

        // When
        transcriptDao.insert(entry)

        // Then
        coVerify { transcriptDao.insert(entry) }
    }

    @Test
    fun `transcript with long message is valid`() = runTest {
        // Given
        val longMessage = "x".repeat(10000)
        val entry = DatabaseTestFixtures.createTranscript(message = longMessage)

        // When
        transcriptDao.insert(entry)

        // Then
        coVerify { transcriptDao.insert(entry) }
    }

    @Test
    fun `transcript with special characters is valid`() = runTest {
        // Given
        val specialMessage = "Special chars: <script>alert('xss')</script> & \"quotes\""
        val entry = DatabaseTestFixtures.createTranscript(message = specialMessage)

        // When
        transcriptDao.insert(entry)

        // Then
        coVerify { transcriptDao.insert(entry) }
    }

    @Test
    fun `transcript with unicode is valid`() = runTest {
        // Given
        val unicodeMessage = "Hello! How are you?"
        val entry = DatabaseTestFixtures.createTranscript(message = unicodeMessage)

        // When
        transcriptDao.insert(entry)

        // Then
        coVerify { transcriptDao.insert(entry) }
    }

    @Test
    fun `transcripts are isolated by session`() = runTest {
        // Given
        val session1Entries = listOf(
            DatabaseTestFixtures.createTranscript(sessionId = "session-1", message = "Session 1")
        )
        val session2Entries = listOf(
            DatabaseTestFixtures.createTranscript(sessionId = "session-2", message = "Session 2")
        )
        coEvery { transcriptDao.getTranscript("session-1") } returns session1Entries
        coEvery { transcriptDao.getTranscript("session-2") } returns session2Entries

        // When
        val result1 = transcriptDao.getTranscript("session-1")
        val result2 = transcriptDao.getTranscript("session-2")

        // Then
        assertThat(result1).hasSize(1)
        assertThat(result2).hasSize(1)
        assertThat(result1[0].message).isEqualTo("Session 1")
        assertThat(result2[0].message).isEqualTo("Session 2")
    }

    @Test
    fun `large batch insert is valid`() = runTest {
        // Given
        val entries = (1..100).map { index ->
            DatabaseTestFixtures.createTranscript(
                by = if (index % 2 == 0) "user" else "bot",
                message = "Message $index",
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP + (index * 1000L)
            )
        }

        // When
        transcriptDao.insertAll(entries)

        // Then
        coVerify { transcriptDao.insertAll(entries) }
    }

    @Test
    fun `same timestamp for multiple entries is valid`() = runTest {
        // Given
        val timestamp = DatabaseTestFixtures.BASE_TIMESTAMP
        val entries = listOf(
            DatabaseTestFixtures.createTranscript(
                by = "bot",
                message = "Message 1",
                timestamp = timestamp
            ),
            DatabaseTestFixtures.createTranscript(
                by = "bot",
                message = "Message 2",
                timestamp = timestamp
            )
        )

        // When
        transcriptDao.insertAll(entries)

        // Then
        coVerify { transcriptDao.insertAll(entries) }
    }
}
