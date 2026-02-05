package com.conferbot.sdk.data.entities

import com.conferbot.sdk.data.testutils.DatabaseTestFixtures
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for all Entity classes
 * Tests entity creation, data class behavior, and field validation
 */
class EntitiesTest {

    // ==================== ChatSessionEntity Tests ====================

    @Test
    fun `ChatSessionEntity creation with all fields`() {
        // Given/When
        val session = ChatSessionEntity(
            sessionId = "session-123",
            visitorId = "visitor-456",
            botId = "bot-789",
            workspaceId = "workspace-abc",
            currentIndex = 5,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        // Then
        assertThat(session.sessionId).isEqualTo("session-123")
        assertThat(session.visitorId).isEqualTo("visitor-456")
        assertThat(session.botId).isEqualTo("bot-789")
        assertThat(session.workspaceId).isEqualTo("workspace-abc")
        assertThat(session.currentIndex).isEqualTo(5)
        assertThat(session.isActive).isTrue()
        assertThat(session.createdAt).isEqualTo(1000L)
        assertThat(session.updatedAt).isEqualTo(2000L)
    }

    @Test
    fun `ChatSessionEntity creation with default values`() {
        // Given/When
        val session = ChatSessionEntity(
            sessionId = "session-123",
            visitorId = "visitor-456",
            botId = "bot-789"
        )

        // Then
        assertThat(session.workspaceId).isNull()
        assertThat(session.currentIndex).isEqualTo(0)
        assertThat(session.isActive).isTrue()
        assertThat(session.createdAt).isGreaterThan(0L)
        assertThat(session.updatedAt).isGreaterThan(0L)
    }

    @Test
    fun `ChatSessionEntity copy creates independent instance`() {
        // Given
        val original = DatabaseTestFixtures.createChatSession()

        // When
        val copy = original.copy(currentIndex = 10)

        // Then
        assertThat(original.currentIndex).isEqualTo(0)
        assertThat(copy.currentIndex).isEqualTo(10)
        assertThat(original.sessionId).isEqualTo(copy.sessionId)
    }

    @Test
    fun `ChatSessionEntity equals and hashCode work correctly`() {
        // Given
        val session1 = DatabaseTestFixtures.createChatSession()
        val session2 = DatabaseTestFixtures.createChatSession()
        val session3 = DatabaseTestFixtures.createChatSession(sessionId = "different")

        // Then
        assertThat(session1).isEqualTo(session2)
        assertThat(session1.hashCode()).isEqualTo(session2.hashCode())
        assertThat(session1).isNotEqualTo(session3)
    }

    @Test
    fun `ChatSessionEntity with null workspaceId is valid`() {
        // Given/When
        val session = DatabaseTestFixtures.createChatSession(workspaceId = null)

        // Then
        assertThat(session.workspaceId).isNull()
    }

    // ==================== MessageEntity Tests ====================

    @Test
    fun `MessageEntity creation with all fields`() {
        // Given/When
        val message = MessageEntity(
            id = "msg-123",
            sessionId = "session-456",
            content = "Hello, world!",
            sender = "bot",
            timestamp = 1000L,
            nodeId = "node-789",
            nodeType = "message-node",
            metadata = """{"key":"value"}"""
        )

        // Then
        assertThat(message.id).isEqualTo("msg-123")
        assertThat(message.sessionId).isEqualTo("session-456")
        assertThat(message.content).isEqualTo("Hello, world!")
        assertThat(message.sender).isEqualTo("bot")
        assertThat(message.timestamp).isEqualTo(1000L)
        assertThat(message.nodeId).isEqualTo("node-789")
        assertThat(message.nodeType).isEqualTo("message-node")
        assertThat(message.metadata).isEqualTo("""{"key":"value"}""")
    }

    @Test
    fun `MessageEntity creation with default values`() {
        // Given/When
        val message = MessageEntity(
            id = "msg-123",
            sessionId = "session-456",
            content = "Hello",
            sender = "user"
        )

        // Then
        assertThat(message.timestamp).isGreaterThan(0L)
        assertThat(message.nodeId).isNull()
        assertThat(message.nodeType).isNull()
        assertThat(message.metadata).isNull()
    }

    @Test
    fun `MessageEntity supports all sender types`() {
        // Given/When
        val botMessage = DatabaseTestFixtures.createBotMessage()
        val userMessage = DatabaseTestFixtures.createUserMessage()
        val agentMessage = DatabaseTestFixtures.createAgentMessage()
        val systemMessage = DatabaseTestFixtures.createMessage(sender = "system")

        // Then
        assertThat(botMessage.sender).isEqualTo("bot")
        assertThat(userMessage.sender).isEqualTo("user")
        assertThat(agentMessage.sender).isEqualTo("agent")
        assertThat(systemMessage.sender).isEqualTo("system")
    }

    @Test
    fun `MessageEntity equals and hashCode work correctly`() {
        // Given
        val msg1 = DatabaseTestFixtures.createMessage(id = "msg-1")
        val msg2 = DatabaseTestFixtures.createMessage(id = "msg-1")
        val msg3 = DatabaseTestFixtures.createMessage(id = "msg-2")

        // Then
        assertThat(msg1).isEqualTo(msg2)
        assertThat(msg1.hashCode()).isEqualTo(msg2.hashCode())
        assertThat(msg1).isNotEqualTo(msg3)
    }

    // ==================== AnswerVariableEntity Tests ====================

    @Test
    fun `AnswerVariableEntity creation with all fields`() {
        // Given/When
        val variable = AnswerVariableEntity(
            id = 1,
            sessionId = "session-123",
            nodeId = "node-456",
            key = "name",
            value = "\"John Doe\"",
            createdAt = 1000L
        )

        // Then
        assertThat(variable.id).isEqualTo(1)
        assertThat(variable.sessionId).isEqualTo("session-123")
        assertThat(variable.nodeId).isEqualTo("node-456")
        assertThat(variable.key).isEqualTo("name")
        assertThat(variable.value).isEqualTo("\"John Doe\"")
        assertThat(variable.createdAt).isEqualTo(1000L)
    }

    @Test
    fun `AnswerVariableEntity creation with default values`() {
        // Given/When
        val variable = AnswerVariableEntity(
            sessionId = "session-123",
            nodeId = "node-456",
            key = "name"
        )

        // Then
        assertThat(variable.id).isEqualTo(0)
        assertThat(variable.value).isNull()
        assertThat(variable.createdAt).isGreaterThan(0L)
    }

    @Test
    fun `AnswerVariableEntity with null value is valid`() {
        // Given/When
        val variable = DatabaseTestFixtures.createAnswerVariableWithNullValue()

        // Then
        assertThat(variable.value).isNull()
    }

    @Test
    fun `AnswerVariableEntity with complex JSON value is valid`() {
        // Given/When
        val variable = DatabaseTestFixtures.createAnswerVariableWithComplexValue()

        // Then
        assertThat(variable.value).contains("nested")
        assertThat(variable.value).contains("array")
        assertThat(variable.value).contains("boolean")
    }

    @Test
    fun `AnswerVariableEntity supports various value types as JSON`() {
        // Given
        val stringVar = DatabaseTestFixtures.createAnswerVariable(value = "\"text\"")
        val numberVar = DatabaseTestFixtures.createAnswerVariable(value = "42")
        val boolVar = DatabaseTestFixtures.createAnswerVariable(value = "true")
        val arrayVar = DatabaseTestFixtures.createAnswerVariable(value = "[1,2,3]")
        val objectVar = DatabaseTestFixtures.createAnswerVariable(value = """{"key":"value"}""")

        // Then
        assertThat(stringVar.value).contains("text")
        assertThat(numberVar.value).isEqualTo("42")
        assertThat(boolVar.value).isEqualTo("true")
        assertThat(arrayVar.value).contains("[")
        assertThat(objectVar.value).contains("key")
    }

    // ==================== UserMetadataEntity Tests ====================

    @Test
    fun `UserMetadataEntity creation with all fields`() {
        // Given/When
        val metadata = UserMetadataEntity(
            sessionId = "session-123",
            name = "John Doe",
            email = "john@example.com",
            phone = "+1234567890",
            customData = """{"company":"Acme"}""",
            updatedAt = 1000L
        )

        // Then
        assertThat(metadata.sessionId).isEqualTo("session-123")
        assertThat(metadata.name).isEqualTo("John Doe")
        assertThat(metadata.email).isEqualTo("john@example.com")
        assertThat(metadata.phone).isEqualTo("+1234567890")
        assertThat(metadata.customData).contains("company")
        assertThat(metadata.updatedAt).isEqualTo(1000L)
    }

    @Test
    fun `UserMetadataEntity creation with default values`() {
        // Given/When
        val metadata = UserMetadataEntity(sessionId = "session-123")

        // Then
        assertThat(metadata.name).isNull()
        assertThat(metadata.email).isNull()
        assertThat(metadata.phone).isNull()
        assertThat(metadata.customData).isNull()
        assertThat(metadata.updatedAt).isGreaterThan(0L)
    }

    @Test
    fun `UserMetadataEntity with all null optional fields is valid`() {
        // Given/When
        val metadata = UserMetadataEntity(
            sessionId = "session-123",
            name = null,
            email = null,
            phone = null,
            customData = null
        )

        // Then
        assertThat(metadata.name).isNull()
        assertThat(metadata.email).isNull()
        assertThat(metadata.phone).isNull()
        assertThat(metadata.customData).isNull()
    }

    @Test
    fun `UserMetadataEntity uses sessionId as primary key`() {
        // Given
        val metadata1 = DatabaseTestFixtures.createUserMetadata(sessionId = "session-1")
        val metadata2 = DatabaseTestFixtures.createUserMetadata(sessionId = "session-1")

        // Then - same sessionId means same identity
        assertThat(metadata1.sessionId).isEqualTo(metadata2.sessionId)
    }

    // ==================== TranscriptEntity Tests ====================

    @Test
    fun `TranscriptEntity creation with all fields`() {
        // Given/When
        val transcript = TranscriptEntity(
            id = 1,
            sessionId = "session-123",
            by = "bot",
            message = "Hello!",
            timestamp = 1000L
        )

        // Then
        assertThat(transcript.id).isEqualTo(1)
        assertThat(transcript.sessionId).isEqualTo("session-123")
        assertThat(transcript.by).isEqualTo("bot")
        assertThat(transcript.message).isEqualTo("Hello!")
        assertThat(transcript.timestamp).isEqualTo(1000L)
    }

    @Test
    fun `TranscriptEntity creation with default values`() {
        // Given/When
        val transcript = TranscriptEntity(
            sessionId = "session-123",
            by = "user",
            message = "Hi"
        )

        // Then
        assertThat(transcript.id).isEqualTo(0)
        assertThat(transcript.timestamp).isGreaterThan(0L)
    }

    @Test
    fun `TranscriptEntity supports all sender types`() {
        // Given/When
        val botEntry = DatabaseTestFixtures.createTranscript(by = "bot")
        val userEntry = DatabaseTestFixtures.createTranscript(by = "user")
        val agentEntry = DatabaseTestFixtures.createTranscript(by = "agent")

        // Then
        assertThat(botEntry.by).isEqualTo("bot")
        assertThat(userEntry.by).isEqualTo("user")
        assertThat(agentEntry.by).isEqualTo("agent")
    }

    @Test
    fun `TranscriptEntity autoGenerates ID`() {
        // Given/When
        val transcript = TranscriptEntity(
            sessionId = "session-123",
            by = "bot",
            message = "Hello"
        )

        // Then - id should be 0 (auto-generated on insert)
        assertThat(transcript.id).isEqualTo(0)
    }

    // ==================== RecordEntity Tests ====================

    @Test
    fun `RecordEntity creation with all fields`() {
        // Given/When
        val record = RecordEntity(
            id = 1,
            sessionId = "session-123",
            recordId = "record-456",
            shape = "bot-message",
            type = "message-node",
            text = "Hello!",
            time = "2024-01-15T12:00:00.000Z",
            data = """{"key":"value"}"""
        )

        // Then
        assertThat(record.id).isEqualTo(1)
        assertThat(record.sessionId).isEqualTo("session-123")
        assertThat(record.recordId).isEqualTo("record-456")
        assertThat(record.shape).isEqualTo("bot-message")
        assertThat(record.type).isEqualTo("message-node")
        assertThat(record.text).isEqualTo("Hello!")
        assertThat(record.time).isEqualTo("2024-01-15T12:00:00.000Z")
        assertThat(record.data).contains("key")
    }

    @Test
    fun `RecordEntity creation with default values`() {
        // Given/When
        val record = RecordEntity(
            sessionId = "session-123",
            recordId = "record-456",
            shape = "bot-message",
            time = "2024-01-15T12:00:00.000Z"
        )

        // Then
        assertThat(record.id).isEqualTo(0)
        assertThat(record.type).isNull()
        assertThat(record.text).isNull()
        assertThat(record.data).isNull()
    }

    @Test
    fun `RecordEntity supports various shapes`() {
        // Given/When
        val botMessage = DatabaseTestFixtures.createRecord(shape = "bot-message")
        val userResponse = DatabaseTestFixtures.createRecord(shape = "user-response")
        val botResponse = DatabaseTestFixtures.createRecord(shape = "bot-response")

        // Then
        assertThat(botMessage.shape).isEqualTo("bot-message")
        assertThat(userResponse.shape).isEqualTo("user-response")
        assertThat(botResponse.shape).isEqualTo("bot-response")
    }

    @Test
    fun `RecordEntity with complex data is valid`() {
        // Given/When
        val record = DatabaseTestFixtures.createRecordWithData()

        // Then
        assertThat(record.data).isNotNull()
        assertThat(record.data).contains("nodeId")
        assertThat(record.data).contains("answerVariable")
    }

    @Test
    fun `RecordEntity autoGenerates ID`() {
        // Given/When
        val record = RecordEntity(
            sessionId = "session-123",
            recordId = "record-456",
            shape = "bot-message",
            time = "2024-01-15T12:00:00.000Z"
        )

        // Then - id should be 0 (auto-generated on insert)
        assertThat(record.id).isEqualTo(0)
    }

    // ==================== Foreign Key Relationship Tests ====================

    @Test
    fun `MessageEntity references ChatSessionEntity via sessionId`() {
        // Given
        val session = DatabaseTestFixtures.createChatSession(sessionId = "shared-session")
        val message = DatabaseTestFixtures.createMessage(sessionId = "shared-session")

        // Then
        assertThat(message.sessionId).isEqualTo(session.sessionId)
    }

    @Test
    fun `AnswerVariableEntity references ChatSessionEntity via sessionId`() {
        // Given
        val session = DatabaseTestFixtures.createChatSession(sessionId = "shared-session")
        val variable = DatabaseTestFixtures.createAnswerVariable(sessionId = "shared-session")

        // Then
        assertThat(variable.sessionId).isEqualTo(session.sessionId)
    }

    @Test
    fun `TranscriptEntity references ChatSessionEntity via sessionId`() {
        // Given
        val session = DatabaseTestFixtures.createChatSession(sessionId = "shared-session")
        val transcript = DatabaseTestFixtures.createTranscript(sessionId = "shared-session")

        // Then
        assertThat(transcript.sessionId).isEqualTo(session.sessionId)
    }

    @Test
    fun `RecordEntity references ChatSessionEntity via sessionId`() {
        // Given
        val session = DatabaseTestFixtures.createChatSession(sessionId = "shared-session")
        val record = DatabaseTestFixtures.createRecord(sessionId = "shared-session")

        // Then
        assertThat(record.sessionId).isEqualTo(session.sessionId)
    }

    @Test
    fun `UserMetadataEntity references ChatSessionEntity via sessionId`() {
        // Given
        val session = DatabaseTestFixtures.createChatSession(sessionId = "shared-session")
        val metadata = DatabaseTestFixtures.createUserMetadata(sessionId = "shared-session")

        // Then
        assertThat(metadata.sessionId).isEqualTo(session.sessionId)
    }

    // ==================== Complete Session Data Test ====================

    @Test
    fun `CompleteSessionData contains all related entities`() {
        // Given/When
        val completeData = DatabaseTestFixtures.createCompleteSession()

        // Then
        assertThat(completeData.session.sessionId).isEqualTo(DatabaseTestFixtures.TEST_SESSION_ID)
        assertThat(completeData.messages).isNotEmpty()
        assertThat(completeData.answerVariables).isNotEmpty()
        assertThat(completeData.userMetadata).isNotNull()
        assertThat(completeData.transcript).isNotEmpty()
        assertThat(completeData.records).isNotEmpty()

        // Verify all entities share the same sessionId
        assertThat(completeData.messages.all { it.sessionId == completeData.session.sessionId }).isTrue()
        assertThat(completeData.answerVariables.all { it.sessionId == completeData.session.sessionId }).isTrue()
        assertThat(completeData.userMetadata.sessionId).isEqualTo(completeData.session.sessionId)
        assertThat(completeData.transcript.all { it.sessionId == completeData.session.sessionId }).isTrue()
        assertThat(completeData.records.all { it.sessionId == completeData.session.sessionId }).isTrue()
    }
}
