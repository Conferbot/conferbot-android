package com.conferbot.sdk.data

import android.content.Context
import com.conferbot.sdk.core.state.AnswerVariable
import com.conferbot.sdk.core.state.RecordEntry
import com.conferbot.sdk.core.state.TranscriptEntry
import com.conferbot.sdk.core.state.UserMetadata
import com.conferbot.sdk.data.dao.AnswerVariableDao
import com.conferbot.sdk.data.dao.ChatSessionDao
import com.conferbot.sdk.data.dao.MessageDao
import com.conferbot.sdk.data.dao.RecordDao
import com.conferbot.sdk.data.dao.TranscriptDao
import com.conferbot.sdk.data.dao.UserMetadataDao
import com.conferbot.sdk.data.entities.AnswerVariableEntity
import com.conferbot.sdk.data.entities.ChatSessionEntity
import com.conferbot.sdk.data.entities.MessageEntity
import com.conferbot.sdk.data.entities.RecordEntity
import com.conferbot.sdk.data.entities.TranscriptEntity
import com.conferbot.sdk.data.entities.UserMetadataEntity
import com.conferbot.sdk.data.testutils.DatabaseTestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SessionRepository
 * Tests high-level session persistence and restoration operations
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {

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
    }

    @After
    fun tearDown() {
        unmockkObject(ConferbotDatabase)
    }

    // ==================== SESSION OPERATIONS TESTS ====================

    @Test
    fun `saveSession creates new session when not exists`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        coEvery { chatSessionDao.getSession(sessionId) } returns null

        // When
        repository.saveSession(
            sessionId = sessionId,
            visitorId = DatabaseTestFixtures.TEST_VISITOR_ID,
            botId = DatabaseTestFixtures.TEST_BOT_ID,
            workspaceId = DatabaseTestFixtures.TEST_WORKSPACE_ID,
            currentIndex = 0,
            isActive = true
        )

        // Then
        coVerify { chatSessionDao.insert(any()) }
    }

    @Test
    fun `saveSession updates existing session`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val existingSession = DatabaseTestFixtures.createChatSession()
        coEvery { chatSessionDao.getSession(existingSession.sessionId) } returns existingSession

        // When
        repository.saveSession(
            sessionId = existingSession.sessionId,
            visitorId = existingSession.visitorId,
            botId = existingSession.botId,
            currentIndex = 5
        )

        // Then
        coVerify { chatSessionDao.update(any()) }
    }

    @Test
    fun `getSession returns session by ID`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val session = DatabaseTestFixtures.createChatSession()
        coEvery { chatSessionDao.getSession(session.sessionId) } returns session

        // When
        val result = repository.getSession(session.sessionId)

        // Then
        assertThat(result).isEqualTo(session)
    }

    @Test
    fun `getSession returns null when not found`() = runTest {
        // Given
        val repository = SessionRepository(context)
        coEvery { chatSessionDao.getSession("non-existent") } returns null

        // When
        val result = repository.getSession("non-existent")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getLatestValidSession returns valid active session`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val recentSession = DatabaseTestFixtures.createChatSession(
            isActive = true,
            updatedAt = System.currentTimeMillis() - 1000 // 1 second ago
        )
        coEvery { chatSessionDao.getLatestActiveSession(DatabaseTestFixtures.TEST_BOT_ID) } returns recentSession

        // When
        val result = repository.getLatestValidSession(DatabaseTestFixtures.TEST_BOT_ID)

        // Then
        assertThat(result).isEqualTo(recentSession)
    }

    @Test
    fun `getLatestValidSession returns null for expired session`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val expiredSession = DatabaseTestFixtures.createChatSession(
            isActive = true,
            updatedAt = System.currentTimeMillis() - (SessionRepository.SESSION_EXPIRY_MS + 1000)
        )
        coEvery { chatSessionDao.getLatestActiveSession(DatabaseTestFixtures.TEST_BOT_ID) } returns expiredSession

        // When
        val result = repository.getLatestValidSession(DatabaseTestFixtures.TEST_BOT_ID)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `isSessionValid returns true for active non-expired session`() {
        // Given
        val repository = SessionRepository(context)
        val validSession = DatabaseTestFixtures.createChatSession(
            isActive = true,
            updatedAt = System.currentTimeMillis() - 1000 // 1 second ago
        )

        // When
        val result = repository.isSessionValid(validSession)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `isSessionValid returns false for inactive session`() {
        // Given
        val repository = SessionRepository(context)
        val inactiveSession = DatabaseTestFixtures.createChatSession(
            isActive = false,
            updatedAt = System.currentTimeMillis() - 1000
        )

        // When
        val result = repository.isSessionValid(inactiveSession)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `isSessionValid returns false for expired session`() {
        // Given
        val repository = SessionRepository(context)
        val expiredSession = DatabaseTestFixtures.createChatSession(
            isActive = true,
            updatedAt = System.currentTimeMillis() - (SessionRepository.SESSION_EXPIRY_MS + 1000)
        )

        // When
        val result = repository.isSessionValid(expiredSession)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `updateCurrentIndex updates session index`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        repository.updateCurrentIndex(sessionId, 10)

        // Then
        coVerify { chatSessionDao.updateCurrentIndex(sessionId, 10) }
    }

    @Test
    fun `deactivateSession marks session as inactive`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        repository.deactivateSession(sessionId)

        // Then
        coVerify { chatSessionDao.updateActiveStatus(sessionId, false) }
    }

    @Test
    fun `touchSession updates session timestamp`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        repository.touchSession(sessionId)

        // Then
        coVerify { chatSessionDao.touch(sessionId) }
    }

    @Test
    fun `deleteSession removes session`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        repository.deleteSession(sessionId)

        // Then
        coVerify { chatSessionDao.delete(sessionId) }
    }

    // ==================== MESSAGE OPERATIONS TESTS ====================

    @Test
    fun `saveMessage inserts message and touches session`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        repository.saveMessage(
            id = "msg-1",
            sessionId = sessionId,
            content = "Hello",
            sender = "bot"
        )

        // Then
        coVerify { messageDao.insert(any()) }
        coVerify { chatSessionDao.touch(sessionId) }
    }

    @Test
    fun `saveMessage includes optional parameters`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val messageSlot = slot<MessageEntity>()
        coEvery { messageDao.insert(capture(messageSlot)) } returns Unit

        // When
        repository.saveMessage(
            id = "msg-1",
            sessionId = sessionId,
            content = "Hello",
            sender = "bot",
            nodeId = "node-1",
            nodeType = "message-node",
            metadata = mapOf("key" to "value")
        )

        // Then
        assertThat(messageSlot.captured.nodeId).isEqualTo("node-1")
        assertThat(messageSlot.captured.nodeType).isEqualTo("message-node")
        assertThat(messageSlot.captured.metadata).contains("key")
    }

    @Test
    fun `getMessages returns messages for session`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val messages = DatabaseTestFixtures.createMultipleMessages()
        coEvery { messageDao.getMessages(DatabaseTestFixtures.TEST_SESSION_ID) } returns messages

        // When
        val result = repository.getMessages(DatabaseTestFixtures.TEST_SESSION_ID)

        // Then
        assertThat(result).hasSize(5)
    }

    @Test
    fun `getMessagesFlow returns flow of messages`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val messages = DatabaseTestFixtures.createMultipleMessages()
        coEvery { messageDao.getMessagesFlow(DatabaseTestFixtures.TEST_SESSION_ID) } returns flowOf(messages)

        // When
        val flow = repository.getMessagesFlow(DatabaseTestFixtures.TEST_SESSION_ID)

        // Then
        flow.collect { result ->
            assertThat(result).hasSize(5)
        }
    }

    // ==================== ANSWER VARIABLE OPERATIONS TESTS ====================

    @Test
    fun `saveAnswerVariable creates new variable when not exists`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        coEvery { answerVariableDao.getVariableByNodeId(sessionId, "node-1") } returns null

        // When
        repository.saveAnswerVariable(sessionId, "node-1", "name", "John")

        // Then
        coVerify { answerVariableDao.insert(any()) }
    }

    @Test
    fun `saveAnswerVariable updates existing variable`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val existing = DatabaseTestFixtures.createAnswerVariable(sessionId = sessionId)
        coEvery { answerVariableDao.getVariableByNodeId(sessionId, existing.nodeId) } returns existing

        // When
        repository.saveAnswerVariable(sessionId, existing.nodeId, existing.key, "New Value")

        // Then
        coVerify { answerVariableDao.updateValueByNodeId(sessionId, existing.nodeId, any()) }
    }

    @Test
    fun `saveAnswerVariables saves multiple variables`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val variables = listOf(
            AnswerVariable("node-1", "name", "John"),
            AnswerVariable("node-2", "email", "john@example.com")
        )

        // When
        repository.saveAnswerVariables(sessionId, variables)

        // Then
        coVerify { answerVariableDao.insertAll(any()) }
    }

    @Test
    fun `getAnswerVariables returns converted variables`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val entities = DatabaseTestFixtures.createMultipleAnswerVariables()
        coEvery { answerVariableDao.getVariables(DatabaseTestFixtures.TEST_SESSION_ID) } returns entities

        // When
        val result = repository.getAnswerVariables(DatabaseTestFixtures.TEST_SESSION_ID)

        // Then
        assertThat(result).hasSize(4)
        assertThat(result[0].nodeId).isEqualTo("ask-name-node")
    }

    // ==================== USER METADATA OPERATIONS TESTS ====================

    @Test
    fun `saveUserMetadata inserts metadata`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        repository.saveUserMetadata(
            sessionId = sessionId,
            name = "John",
            email = "john@example.com",
            phone = "+1234567890"
        )

        // Then
        coVerify { userMetadataDao.insert(any()) }
    }

    @Test
    fun `saveUserMetadata from UserMetadata object`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val metadata = UserMetadata(
            name = "John",
            email = "john@example.com",
            phone = "+1234567890",
            metadata = mutableMapOf("company" to "Acme")
        )

        // When
        repository.saveUserMetadata(sessionId, metadata)

        // Then
        coVerify { userMetadataDao.insert(any()) }
    }

    @Test
    fun `updateUserMetadataField updates name`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        repository.updateUserMetadataField(sessionId, "name", "Jane")

        // Then
        coVerify { userMetadataDao.updateName(sessionId, "Jane") }
    }

    @Test
    fun `updateUserMetadataField updates email`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        repository.updateUserMetadataField(sessionId, "email", "jane@example.com")

        // Then
        coVerify { userMetadataDao.updateEmail(sessionId, "jane@example.com") }
    }

    @Test
    fun `updateUserMetadataField updates phone`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        repository.updateUserMetadataField(sessionId, "phone", "+9876543210")

        // Then
        coVerify { userMetadataDao.updatePhone(sessionId, "+9876543210") }
    }

    @Test
    fun `updateUserMetadataField updates custom field in JSON`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val existingMetadata = DatabaseTestFixtures.createUserMetadata(
            sessionId = sessionId,
            customData = """{"existing":"value"}"""
        )
        coEvery { userMetadataDao.getMetadata(sessionId) } returns existingMetadata

        // When
        repository.updateUserMetadataField(sessionId, "company", "Acme Inc")

        // Then
        coVerify { userMetadataDao.updateCustomData(sessionId, any()) }
    }

    @Test
    fun `getUserMetadata returns converted metadata`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val entity = DatabaseTestFixtures.createUserMetadataWithCustomData()
        coEvery { userMetadataDao.getMetadata(DatabaseTestFixtures.TEST_SESSION_ID) } returns entity

        // When
        val result = repository.getUserMetadata(DatabaseTestFixtures.TEST_SESSION_ID)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.name).isEqualTo("John Doe")
        assertThat(result?.metadata).isNotEmpty()
    }

    @Test
    fun `getUserMetadata returns null when not exists`() = runTest {
        // Given
        val repository = SessionRepository(context)
        coEvery { userMetadataDao.getMetadata("non-existent") } returns null

        // When
        val result = repository.getUserMetadata("non-existent")

        // Then
        assertThat(result).isNull()
    }

    // ==================== TRANSCRIPT OPERATIONS TESTS ====================

    @Test
    fun `saveTranscriptEntry inserts entry`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID

        // When
        repository.saveTranscriptEntry(sessionId, "bot", "Hello!")

        // Then
        coVerify { transcriptDao.insert(any()) }
    }

    @Test
    fun `saveTranscriptEntries saves multiple entries`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val entries = listOf(
            TranscriptEntry("bot", "Hello!", 1000L),
            TranscriptEntry("user", "Hi!", 2000L)
        )

        // When
        repository.saveTranscriptEntries(sessionId, entries)

        // Then
        coVerify { transcriptDao.insertAll(any()) }
    }

    @Test
    fun `getTranscript returns converted entries`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val entities = DatabaseTestFixtures.createConversationTranscript()
        coEvery { transcriptDao.getTranscript(DatabaseTestFixtures.TEST_SESSION_ID) } returns entities

        // When
        val result = repository.getTranscript(DatabaseTestFixtures.TEST_SESSION_ID)

        // Then
        assertThat(result).hasSize(5)
        assertThat(result[0].by).isEqualTo("bot")
    }

    // ==================== RECORD OPERATIONS TESTS ====================

    @Test
    fun `saveRecordEntry creates new entry when not exists`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val entry = RecordEntry(
            id = "record-1",
            shape = "bot-message",
            type = "message-node",
            text = "Hello"
        )
        coEvery { recordDao.getRecordById(sessionId, entry.id) } returns null

        // When
        repository.saveRecordEntry(sessionId, entry)

        // Then
        coVerify { recordDao.insert(any()) }
    }

    @Test
    fun `saveRecordEntry merges data for existing entry`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val existingEntity = DatabaseTestFixtures.createRecord(
            sessionId = sessionId,
            recordId = "record-1",
            data = """{"existingKey":"existingValue"}"""
        )
        coEvery { recordDao.getRecordById(sessionId, "record-1") } returns existingEntity

        val entry = RecordEntry(
            id = "record-1",
            shape = "bot-message",
            data = mutableMapOf("newKey" to "newValue")
        )

        // When
        repository.saveRecordEntry(sessionId, entry)

        // Then
        coVerify { recordDao.updateData(sessionId, "record-1", any()) }
    }

    @Test
    fun `getRecords returns converted entries`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val entities = DatabaseTestFixtures.createMultipleRecords()
        coEvery { recordDao.getRecords(DatabaseTestFixtures.TEST_SESSION_ID) } returns entities

        // When
        val result = repository.getRecords(DatabaseTestFixtures.TEST_SESSION_ID)

        // Then
        assertThat(result).hasSize(5)
        assertThat(result[0].id).isEqualTo("record-1")
    }

    // ==================== FULL SESSION RESTORE TESTS ====================

    @Test
    fun `restoreFullSession returns all session data`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val sessionId = DatabaseTestFixtures.TEST_SESSION_ID
        val session = DatabaseTestFixtures.createChatSession(
            sessionId = sessionId,
            updatedAt = System.currentTimeMillis() - 1000
        )
        val messages = DatabaseTestFixtures.createMultipleMessages(sessionId = sessionId)
        val answerVariables = DatabaseTestFixtures.createMultipleAnswerVariables(sessionId = sessionId)
        val userMetadata = DatabaseTestFixtures.createUserMetadata(sessionId = sessionId)
        val transcript = DatabaseTestFixtures.createConversationTranscript(sessionId = sessionId)
        val records = DatabaseTestFixtures.createMultipleRecords(sessionId = sessionId)

        coEvery { chatSessionDao.getSession(sessionId) } returns session
        coEvery { messageDao.getMessages(sessionId) } returns messages
        coEvery { answerVariableDao.getVariables(sessionId) } returns answerVariables
        coEvery { userMetadataDao.getMetadata(sessionId) } returns userMetadata
        coEvery { transcriptDao.getTranscript(sessionId) } returns transcript
        coEvery { recordDao.getRecords(sessionId) } returns records

        // When
        val result = repository.restoreFullSession(sessionId)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.session?.sessionId).isEqualTo(sessionId)
        assertThat(result?.messages).hasSize(5)
        assertThat(result?.answerVariables).hasSize(4)
        assertThat(result?.userMetadata).isNotNull()
        assertThat(result?.transcript).hasSize(5)
        assertThat(result?.records).hasSize(5)
    }

    @Test
    fun `restoreFullSession returns null for non-existent session`() = runTest {
        // Given
        val repository = SessionRepository(context)
        coEvery { chatSessionDao.getSession("non-existent") } returns null

        // When
        val result = repository.restoreFullSession("non-existent")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `restoreFullSession returns null for expired session`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val expiredSession = DatabaseTestFixtures.createChatSession(
            updatedAt = System.currentTimeMillis() - (SessionRepository.SESSION_EXPIRY_MS + 1000)
        )
        coEvery { chatSessionDao.getSession(expiredSession.sessionId) } returns expiredSession

        // When
        val result = repository.restoreFullSession(expiredSession.sessionId)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `restoreLatestSession returns latest valid session data`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val botId = DatabaseTestFixtures.TEST_BOT_ID
        val session = DatabaseTestFixtures.createChatSession(
            botId = botId,
            updatedAt = System.currentTimeMillis() - 1000
        )

        coEvery { chatSessionDao.getLatestActiveSession(botId) } returns session
        coEvery { chatSessionDao.getSession(session.sessionId) } returns session
        coEvery { messageDao.getMessages(session.sessionId) } returns emptyList()
        coEvery { answerVariableDao.getVariables(session.sessionId) } returns emptyList()
        coEvery { userMetadataDao.getMetadata(session.sessionId) } returns null
        coEvery { transcriptDao.getTranscript(session.sessionId) } returns emptyList()
        coEvery { recordDao.getRecords(session.sessionId) } returns emptyList()

        // When
        val result = repository.restoreLatestSession(botId)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.session?.botId).isEqualTo(botId)
    }

    // ==================== SESSION EXPIRY TESTS ====================

    @Test
    fun `SESSION_EXPIRY_MS is 30 minutes`() {
        // Then
        assertThat(SessionRepository.SESSION_EXPIRY_MS).isEqualTo(30 * 60 * 1000L)
    }

    @Test
    fun `clearOldSessions deletes old sessions`() = runTest {
        // Given
        val repository = SessionRepository(context)
        val olderThan = System.currentTimeMillis() - SessionRepository.SESSION_EXPIRY_MS

        // When
        repository.clearOldSessions(olderThan)

        // Then
        coVerify { chatSessionDao.deleteOldSessions(olderThan) }
    }
}
