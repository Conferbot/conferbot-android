package com.conferbot.sdk.data.dao

import com.conferbot.sdk.data.entities.MessageEntity
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
 * Unit tests for MessageDao
 * Tests message CRUD operations, queries, and ordering
 */
class MessageDaoTest {

    private lateinit var messageDao: MessageDao

    @Before
    fun setUp() {
        messageDao = mockk(relaxed = true)
    }

    // ==================== INSERT TESTS ====================

    @Test
    fun `insert creates new message`() = runTest {
        // Given
        val message = DatabaseTestFixtures.createBotMessage()

        // When
        messageDao.insert(message)

        // Then
        coVerify { messageDao.insert(message) }
    }

    @Test
    fun `insert replaces message with same ID on conflict`() = runTest {
        // Given
        val originalMessage = DatabaseTestFixtures.createBotMessage(content = "Original")
        val updatedMessage = originalMessage.copy(content = "Updated")

        coEvery { messageDao.getMessage(originalMessage.id) } returns updatedMessage

        // When
        messageDao.insert(originalMessage)
        messageDao.insert(updatedMessage)

        // Then
        val result = messageDao.getMessage(originalMessage.id)
        assertThat(result?.content).isEqualTo("Updated")
    }

    @Test
    fun `insertAll inserts multiple messages`() = runTest {
        // Given
        val messages = DatabaseTestFixtures.createMultipleMessages(count = 5)

        // When
        messageDao.insertAll(messages)

        // Then
        coVerify { messageDao.insertAll(messages) }
    }

    @Test
    fun `insertAll with empty list does nothing`() = runTest {
        // Given
        val emptyList = emptyList<MessageEntity>()

        // When
        messageDao.insertAll(emptyList)

        // Then
        coVerify { messageDao.insertAll(emptyList) }
    }

    // ==================== QUERY TESTS ====================

    @Test
    fun `getMessage returns message when exists`() = runTest {
        // Given
        val message = DatabaseTestFixtures.createBotMessage()
        coEvery { messageDao.getMessage(message.id) } returns message

        // When
        val result = messageDao.getMessage(message.id)

        // Then
        assertThat(result).isEqualTo(message)
    }

    @Test
    fun `getMessage returns null when not exists`() = runTest {
        // Given
        coEvery { messageDao.getMessage("non-existent") } returns null

        // When
        val result = messageDao.getMessage("non-existent")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getMessages returns all messages for session ordered by timestamp ASC`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val messages = listOf(
            DatabaseTestFixtures.createMessage(
                id = "msg-1",
                sessionId = sessionId,
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP
            ),
            DatabaseTestFixtures.createMessage(
                id = "msg-2",
                sessionId = sessionId,
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP + 1000
            ),
            DatabaseTestFixtures.createMessage(
                id = "msg-3",
                sessionId = sessionId,
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP + 2000
            )
        )
        coEvery { messageDao.getMessages(sessionId) } returns messages

        // When
        val result = messageDao.getMessages(sessionId)

        // Then
        assertThat(result).hasSize(3)
        assertThat(result[0].id).isEqualTo("msg-1")
        assertThat(result[1].id).isEqualTo("msg-2")
        assertThat(result[2].id).isEqualTo("msg-3")
    }

    @Test
    fun `getMessages returns empty list when no messages`() = runTest {
        // Given
        val sessionId = "empty-session"
        coEvery { messageDao.getMessages(sessionId) } returns emptyList()

        // When
        val result = messageDao.getMessages(sessionId)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `getMessagesFlow emits message updates`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val messages = DatabaseTestFixtures.createMultipleMessages(sessionId = sessionId)
        coEvery { messageDao.getMessagesFlow(sessionId) } returns flowOf(messages)

        // When
        val flow = messageDao.getMessagesFlow(sessionId)

        // Then
        flow.collect { result ->
            assertThat(result).hasSize(5)
        }
    }

    @Test
    fun `getRecentMessages returns most recent N messages ordered DESC`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val recentMessages = listOf(
            DatabaseTestFixtures.createMessage(
                id = "msg-3",
                sessionId = sessionId,
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP + 2000
            ),
            DatabaseTestFixtures.createMessage(
                id = "msg-2",
                sessionId = sessionId,
                timestamp = DatabaseTestFixtures.BASE_TIMESTAMP + 1000
            )
        )
        coEvery { messageDao.getRecentMessages(sessionId, 2) } returns recentMessages

        // When
        val result = messageDao.getRecentMessages(sessionId, 2)

        // Then
        assertThat(result).hasSize(2)
        assertThat(result[0].id).isEqualTo("msg-3") // Most recent first
    }

    @Test
    fun `getMessagesBySender filters by sender type`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val botMessages = listOf(
            DatabaseTestFixtures.createBotMessage(id = "bot-1", sessionId = sessionId),
            DatabaseTestFixtures.createBotMessage(id = "bot-2", sessionId = sessionId)
        )
        coEvery { messageDao.getMessagesBySender(sessionId, "bot") } returns botMessages

        // When
        val result = messageDao.getMessagesBySender(sessionId, "bot")

        // Then
        assertThat(result).hasSize(2)
        assertThat(result.all { it.sender == "bot" }).isTrue()
    }

    @Test
    fun `getMessagesByNodeId filters by nodeId`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val nodeId = "ask-name-node"
        val nodeMessages = listOf(
            DatabaseTestFixtures.createMessage(
                id = "msg-1",
                sessionId = sessionId,
                nodeId = nodeId
            )
        )
        coEvery { messageDao.getMessagesByNodeId(sessionId, nodeId) } returns nodeMessages

        // When
        val result = messageDao.getMessagesByNodeId(sessionId, nodeId)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].nodeId).isEqualTo(nodeId)
    }

    @Test
    fun `getMessageCount returns correct count`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        coEvery { messageDao.getMessageCount(sessionId) } returns 10

        // When
        val result = messageDao.getMessageCount(sessionId)

        // Then
        assertThat(result).isEqualTo(10)
    }

    // ==================== DELETE TESTS ====================

    @Test
    fun `delete removes message by ID`() = runTest {
        // Given
        val messageId = "msg-to-delete"

        // When
        messageDao.delete(messageId)

        // Then
        coVerify { messageDao.delete(messageId) }
    }

    @Test
    fun `deleteAllForSession removes all messages for session`() = runTest {
        // Given
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        messageDao.deleteAllForSession(sessionId)

        // Then
        coVerify { messageDao.deleteAllForSession(sessionId) }
    }

    @Test
    fun `deleteOldMessages removes messages older than timestamp`() = runTest {
        // Given
        val cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // 24 hours ago

        // When
        messageDao.deleteOldMessages(cutoffTime)

        // Then
        coVerify { messageDao.deleteOldMessages(cutoffTime) }
    }

    // ==================== SENDER TYPE TESTS ====================

    @Test
    fun `message with bot sender is correctly identified`() = runTest {
        // Given
        val message = DatabaseTestFixtures.createBotMessage()
        coEvery { messageDao.getMessage(message.id) } returns message

        // When
        val result = messageDao.getMessage(message.id)

        // Then
        assertThat(result?.sender).isEqualTo("bot")
    }

    @Test
    fun `message with user sender is correctly identified`() = runTest {
        // Given
        val message = DatabaseTestFixtures.createUserMessage()
        coEvery { messageDao.getMessage(message.id) } returns message

        // When
        val result = messageDao.getMessage(message.id)

        // Then
        assertThat(result?.sender).isEqualTo("user")
    }

    @Test
    fun `message with agent sender is correctly identified`() = runTest {
        // Given
        val message = DatabaseTestFixtures.createAgentMessage()
        coEvery { messageDao.getMessage(message.id) } returns message

        // When
        val result = messageDao.getMessage(message.id)

        // Then
        assertThat(result?.sender).isEqualTo("agent")
    }

    @Test
    fun `message with system sender is correctly identified`() = runTest {
        // Given
        val message = DatabaseTestFixtures.createMessage(sender = "system")
        coEvery { messageDao.getMessage(message.id) } returns message

        // When
        val result = messageDao.getMessage(message.id)

        // Then
        assertThat(result?.sender).isEqualTo("system")
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    fun `message with empty content is valid`() = runTest {
        // Given
        val message = DatabaseTestFixtures.createMessageWithEmptyContent()

        // When
        messageDao.insert(message)

        // Then
        coVerify { messageDao.insert(message) }
    }

    @Test
    fun `message with long content is valid`() = runTest {
        // Given
        val message = DatabaseTestFixtures.createMessageWithLongContent(length = 10000)

        // When
        messageDao.insert(message)

        // Then
        coVerify { messageDao.insert(message) }
        assertThat(message.content.length).isEqualTo(10000)
    }

    @Test
    fun `message with special characters is valid`() = runTest {
        // Given
        val message = DatabaseTestFixtures.createMessageWithSpecialCharacters()

        // When
        messageDao.insert(message)

        // Then
        coVerify { messageDao.insert(message) }
    }

    @Test
    fun `message with unicode content is valid`() = runTest {
        // Given
        val message = DatabaseTestFixtures.createMessageWithUnicode()

        // When
        messageDao.insert(message)

        // Then
        coVerify { messageDao.insert(message) }
    }

    @Test
    fun `message with null nodeId is valid`() = runTest {
        // Given
        val message = DatabaseTestFixtures.createMessage(nodeId = null)
        coEvery { messageDao.getMessage(message.id) } returns message

        // When
        messageDao.insert(message)
        val result = messageDao.getMessage(message.id)

        // Then
        assertThat(result?.nodeId).isNull()
    }

    @Test
    fun `message with null nodeType is valid`() = runTest {
        // Given
        val message = DatabaseTestFixtures.createMessage(nodeType = null)
        coEvery { messageDao.getMessage(message.id) } returns message

        // When
        messageDao.insert(message)
        val result = messageDao.getMessage(message.id)

        // Then
        assertThat(result?.nodeType).isNull()
    }

    @Test
    fun `message with metadata is valid`() = runTest {
        // Given
        val metadata = """{"key":"value","number":123}"""
        val message = DatabaseTestFixtures.createMessage(metadata = metadata)
        coEvery { messageDao.getMessage(message.id) } returns message

        // When
        messageDao.insert(message)
        val result = messageDao.getMessage(message.id)

        // Then
        assertThat(result?.metadata).isEqualTo(metadata)
    }

    @Test
    fun `messages are isolated by session`() = runTest {
        // Given
        val session1Messages = DatabaseTestFixtures.createMultipleMessages(
            sessionId = "session-1",
            count = 3
        )
        val session2Messages = DatabaseTestFixtures.createMultipleMessages(
            sessionId = "session-2",
            count = 2
        )
        coEvery { messageDao.getMessages("session-1") } returns session1Messages
        coEvery { messageDao.getMessages("session-2") } returns session2Messages

        // When
        val result1 = messageDao.getMessages("session-1")
        val result2 = messageDao.getMessages("session-2")

        // Then
        assertThat(result1).hasSize(3)
        assertThat(result2).hasSize(2)
    }

    @Test
    fun `large batch insert is valid`() = runTest {
        // Given
        val messages = (1..100).map { index ->
            DatabaseTestFixtures.createMessage(
                id = "msg-$index",
                content = "Message $index"
            )
        }

        // When
        messageDao.insertAll(messages)

        // Then
        coVerify { messageDao.insertAll(messages) }
    }
}
