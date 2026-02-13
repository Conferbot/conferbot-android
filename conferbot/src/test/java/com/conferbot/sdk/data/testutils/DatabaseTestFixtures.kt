package com.conferbot.sdk.data.testutils

import com.conferbot.sdk.core.state.AnswerVariable
import com.conferbot.sdk.core.state.RecordEntry
import com.conferbot.sdk.core.state.TranscriptEntry
import com.conferbot.sdk.core.state.UserMetadata
import com.conferbot.sdk.data.entities.AnswerVariableEntity
import com.conferbot.sdk.data.entities.ChatSessionEntity
import com.conferbot.sdk.data.entities.MessageEntity
import com.conferbot.sdk.data.entities.RecordEntity
import com.conferbot.sdk.data.entities.TranscriptEntity
import com.conferbot.sdk.data.entities.UserMetadataEntity

/**
 * Test fixtures for database layer tests
 * Provides reusable test data for entities and DAOs
 */
object DatabaseTestFixtures {

    // ==================== Session Data ====================

    const val TEST_SESSION_ID = "test-session-123"
    const val TEST_SESSION_ID_2 = "test-session-456"
    const val TEST_VISITOR_ID = "visitor-abc"
    const val TEST_VISITOR_ID_2 = "visitor-def"
    const val TEST_BOT_ID = "bot-xyz"
    const val TEST_BOT_ID_2 = "bot-uvw"
    const val TEST_WORKSPACE_ID = "workspace-123"

    // Fixed timestamps for predictable testing
    val BASE_TIMESTAMP = 1700000000000L // Fixed base timestamp
    val RECENT_TIMESTAMP = BASE_TIMESTAMP + 1000L
    val OLD_TIMESTAMP = BASE_TIMESTAMP - (60 * 60 * 1000L) // 1 hour ago

    // ==================== Chat Session Entities ====================

    fun createChatSession(
        sessionId: String = TEST_SESSION_ID,
        visitorId: String = TEST_VISITOR_ID,
        botId: String = TEST_BOT_ID,
        workspaceId: String? = TEST_WORKSPACE_ID,
        currentIndex: Int = 0,
        isActive: Boolean = true,
        createdAt: Long = BASE_TIMESTAMP,
        updatedAt: Long = BASE_TIMESTAMP
    ): ChatSessionEntity {
        return ChatSessionEntity(
            sessionId = sessionId,
            visitorId = visitorId,
            botId = botId,
            workspaceId = workspaceId,
            currentIndex = currentIndex,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun createActiveSession(botId: String = TEST_BOT_ID): ChatSessionEntity {
        return createChatSession(
            sessionId = "active-session-${System.currentTimeMillis()}",
            botId = botId,
            isActive = true,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun createInactiveSession(botId: String = TEST_BOT_ID): ChatSessionEntity {
        return createChatSession(
            sessionId = "inactive-session-${System.currentTimeMillis()}",
            botId = botId,
            isActive = false
        )
    }

    fun createOldSession(botId: String = TEST_BOT_ID): ChatSessionEntity {
        return createChatSession(
            sessionId = "old-session-${System.currentTimeMillis()}",
            botId = botId,
            createdAt = OLD_TIMESTAMP,
            updatedAt = OLD_TIMESTAMP
        )
    }

    // ==================== Message Entities ====================

    fun createMessage(
        id: String = "msg-1",
        sessionId: String = TEST_SESSION_ID,
        content: String = "Test message content",
        sender: String = "bot",
        timestamp: Long = BASE_TIMESTAMP,
        nodeId: String? = "node-1",
        nodeType: String? = "message-node",
        metadata: String? = null
    ): MessageEntity {
        return MessageEntity(
            id = id,
            sessionId = sessionId,
            content = content,
            sender = sender,
            timestamp = timestamp,
            nodeId = nodeId,
            nodeType = nodeType,
            metadata = metadata
        )
    }

    fun createBotMessage(
        id: String = "bot-msg-1",
        sessionId: String = TEST_SESSION_ID,
        content: String = "Hello from bot!",
        timestamp: Long = BASE_TIMESTAMP
    ): MessageEntity {
        return createMessage(
            id = id,
            sessionId = sessionId,
            content = content,
            sender = "bot",
            timestamp = timestamp
        )
    }

    fun createUserMessage(
        id: String = "user-msg-1",
        sessionId: String = TEST_SESSION_ID,
        content: String = "Hello from user!",
        timestamp: Long = BASE_TIMESTAMP
    ): MessageEntity {
        return createMessage(
            id = id,
            sessionId = sessionId,
            content = content,
            sender = "user",
            timestamp = timestamp
        )
    }

    fun createAgentMessage(
        id: String = "agent-msg-1",
        sessionId: String = TEST_SESSION_ID,
        content: String = "Hello from agent!",
        timestamp: Long = BASE_TIMESTAMP
    ): MessageEntity {
        return createMessage(
            id = id,
            sessionId = sessionId,
            content = content,
            sender = "agent",
            timestamp = timestamp
        )
    }

    fun createMultipleMessages(
        sessionId: String = TEST_SESSION_ID,
        count: Int = 5
    ): List<MessageEntity> {
        return (1..count).map { index ->
            createMessage(
                id = "msg-$index",
                sessionId = sessionId,
                content = "Message $index",
                sender = if (index % 2 == 0) "user" else "bot",
                timestamp = BASE_TIMESTAMP + (index * 1000L)
            )
        }
    }

    // ==================== Answer Variable Entities ====================

    fun createAnswerVariable(
        id: Long = 0,
        sessionId: String = TEST_SESSION_ID,
        nodeId: String = "ask-name-node",
        key: String = "name",
        value: String? = "\"John Doe\"",
        createdAt: Long = BASE_TIMESTAMP
    ): AnswerVariableEntity {
        return AnswerVariableEntity(
            id = id,
            sessionId = sessionId,
            nodeId = nodeId,
            key = key,
            value = value,
            createdAt = createdAt
        )
    }

    fun createAnswerVariableData(
        nodeId: String = "ask-name-node",
        key: String = "name",
        value: Any? = "John Doe"
    ): AnswerVariable {
        return AnswerVariable(
            nodeId = nodeId,
            key = key,
            value = value
        )
    }

    fun createMultipleAnswerVariables(
        sessionId: String = TEST_SESSION_ID
    ): List<AnswerVariableEntity> {
        return listOf(
            createAnswerVariable(
                sessionId = sessionId,
                nodeId = "ask-name-node",
                key = "name",
                value = "\"John Doe\"",
                createdAt = BASE_TIMESTAMP
            ),
            createAnswerVariable(
                sessionId = sessionId,
                nodeId = "ask-email-node",
                key = "email",
                value = "\"john@example.com\"",
                createdAt = BASE_TIMESTAMP + 1000L
            ),
            createAnswerVariable(
                sessionId = sessionId,
                nodeId = "ask-phone-node",
                key = "phone",
                value = "\"+1234567890\"",
                createdAt = BASE_TIMESTAMP + 2000L
            ),
            createAnswerVariable(
                sessionId = sessionId,
                nodeId = "ask-age-node",
                key = "age",
                value = "30",
                createdAt = BASE_TIMESTAMP + 3000L
            )
        )
    }

    // ==================== User Metadata Entities ====================

    fun createUserMetadata(
        sessionId: String = TEST_SESSION_ID,
        name: String? = "John Doe",
        email: String? = "john@example.com",
        phone: String? = "+1234567890",
        customData: String? = null,
        updatedAt: Long = BASE_TIMESTAMP
    ): UserMetadataEntity {
        return UserMetadataEntity(
            sessionId = sessionId,
            name = name,
            email = email,
            phone = phone,
            customData = customData,
            updatedAt = updatedAt
        )
    }

    fun createUserMetadataWithCustomData(
        sessionId: String = TEST_SESSION_ID
    ): UserMetadataEntity {
        return createUserMetadata(
            sessionId = sessionId,
            customData = """{"company":"Acme Inc","role":"Developer","subscribed":true}"""
        )
    }

    fun createUserMetadataData(
        name: String? = "John Doe",
        email: String? = "john@example.com",
        phone: String? = "+1234567890",
        metadata: MutableMap<String, Any> = mutableMapOf()
    ): UserMetadata {
        return UserMetadata(
            name = name,
            email = email,
            phone = phone,
            metadata = metadata
        )
    }

    // ==================== Transcript Entities ====================

    fun createTranscript(
        id: Long = 0,
        sessionId: String = TEST_SESSION_ID,
        by: String = "bot",
        message: String = "Hello, how can I help you?",
        timestamp: Long = BASE_TIMESTAMP
    ): TranscriptEntity {
        return TranscriptEntity(
            id = id,
            sessionId = sessionId,
            by = by,
            message = message,
            timestamp = timestamp
        )
    }

    fun createTranscriptEntry(
        by: String = "bot",
        message: String = "Hello, how can I help you?",
        timestamp: Long = BASE_TIMESTAMP
    ): TranscriptEntry {
        return TranscriptEntry(
            by = by,
            message = message,
            timestamp = timestamp
        )
    }

    fun createConversationTranscript(
        sessionId: String = TEST_SESSION_ID
    ): List<TranscriptEntity> {
        return listOf(
            createTranscript(
                sessionId = sessionId,
                by = "bot",
                message = "Hello! Welcome to our chatbot.",
                timestamp = BASE_TIMESTAMP
            ),
            createTranscript(
                sessionId = sessionId,
                by = "user",
                message = "Hi, I need help with my order",
                timestamp = BASE_TIMESTAMP + 1000L
            ),
            createTranscript(
                sessionId = sessionId,
                by = "bot",
                message = "Sure! What's your order number?",
                timestamp = BASE_TIMESTAMP + 2000L
            ),
            createTranscript(
                sessionId = sessionId,
                by = "user",
                message = "Order #12345",
                timestamp = BASE_TIMESTAMP + 3000L
            ),
            createTranscript(
                sessionId = sessionId,
                by = "agent",
                message = "Let me look that up for you.",
                timestamp = BASE_TIMESTAMP + 4000L
            )
        )
    }

    // ==================== Record Entities ====================

    fun createRecord(
        id: Long = 0,
        sessionId: String = TEST_SESSION_ID,
        recordId: String = "record-1",
        shape: String = "bot-message",
        type: String? = "message-node",
        text: String? = "Hello!",
        time: String = "2024-01-01T12:00:00.000Z",
        data: String? = null
    ): RecordEntity {
        return RecordEntity(
            id = id,
            sessionId = sessionId,
            recordId = recordId,
            shape = shape,
            type = type,
            text = text,
            time = time,
            data = data
        )
    }

    fun createRecordEntry(
        id: String = "record-1",
        shape: String = "bot-message",
        type: String? = "message-node",
        text: String? = "Hello!",
        time: String = "2024-01-01T12:00:00.000Z",
        data: MutableMap<String, Any?> = mutableMapOf()
    ): RecordEntry {
        return RecordEntry(
            id = id,
            shape = shape,
            type = type,
            text = text,
            time = time,
            data = data
        )
    }

    fun createRecordWithData(
        sessionId: String = TEST_SESSION_ID,
        recordId: String = "record-with-data"
    ): RecordEntity {
        return createRecord(
            sessionId = sessionId,
            recordId = recordId,
            shape = "bot-response",
            type = "ask-name-node",
            text = "What is your name?",
            data = """{"nodeId":"ask-name-node","answerVariable":"name","response":"John Doe"}"""
        )
    }

    fun createMultipleRecords(
        sessionId: String = TEST_SESSION_ID,
        count: Int = 5
    ): List<RecordEntity> {
        return (1..count).map { index ->
            createRecord(
                sessionId = sessionId,
                recordId = "record-$index",
                shape = if (index % 2 == 0) "user-response" else "bot-message",
                type = "message-node",
                text = "Record $index content",
                time = "2024-01-01T12:0$index:00.000Z"
            )
        }
    }

    // ==================== Complex Scenarios ====================

    /**
     * Creates a complete session with all related data for testing cascading operations
     */
    data class CompleteSessionData(
        val session: ChatSessionEntity,
        val messages: List<MessageEntity>,
        val answerVariables: List<AnswerVariableEntity>,
        val userMetadata: UserMetadataEntity,
        val transcript: List<TranscriptEntity>,
        val records: List<RecordEntity>
    )

    fun createCompleteSession(
        sessionId: String = TEST_SESSION_ID
    ): CompleteSessionData {
        return CompleteSessionData(
            session = createChatSession(sessionId = sessionId),
            messages = createMultipleMessages(sessionId = sessionId),
            answerVariables = createMultipleAnswerVariables(sessionId = sessionId),
            userMetadata = createUserMetadata(sessionId = sessionId),
            transcript = createConversationTranscript(sessionId = sessionId),
            records = createMultipleRecords(sessionId = sessionId)
        )
    }

    // ==================== Edge Cases ====================

    fun createMessageWithEmptyContent(
        sessionId: String = TEST_SESSION_ID
    ): MessageEntity {
        return createMessage(
            id = "empty-msg",
            sessionId = sessionId,
            content = ""
        )
    }

    fun createMessageWithLongContent(
        sessionId: String = TEST_SESSION_ID,
        length: Int = 10000
    ): MessageEntity {
        return createMessage(
            id = "long-msg",
            sessionId = sessionId,
            content = "x".repeat(length)
        )
    }

    fun createMessageWithSpecialCharacters(
        sessionId: String = TEST_SESSION_ID
    ): MessageEntity {
        return createMessage(
            id = "special-msg",
            sessionId = sessionId,
            content = "Special chars: <script>alert('xss')</script> & \"quotes\" 'apostrophes'"
        )
    }

    fun createMessageWithUnicode(
        sessionId: String = TEST_SESSION_ID
    ): MessageEntity {
        return createMessage(
            id = "unicode-msg",
            sessionId = sessionId,
            content = "Unicode test: Hello World! Emoji test: Test passed!"
        )
    }

    fun createAnswerVariableWithNullValue(
        sessionId: String = TEST_SESSION_ID
    ): AnswerVariableEntity {
        return createAnswerVariable(
            sessionId = sessionId,
            nodeId = "optional-node",
            key = "optionalField",
            value = null
        )
    }

    fun createAnswerVariableWithComplexValue(
        sessionId: String = TEST_SESSION_ID
    ): AnswerVariableEntity {
        return createAnswerVariable(
            sessionId = sessionId,
            nodeId = "complex-node",
            key = "complexField",
            value = """{"nested":{"key":"value"},"array":[1,2,3],"boolean":true}"""
        )
    }
}
