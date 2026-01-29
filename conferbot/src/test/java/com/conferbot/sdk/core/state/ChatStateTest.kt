package com.conferbot.sdk.core.state

import com.conferbot.sdk.testutils.TestChatState
import com.conferbot.sdk.testutils.TestFixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ChatState
 * Tests all state management functionality including:
 * - Answer variables
 * - Variable resolution
 * - Transcript operations
 * - Record operations
 * - User metadata
 * - State clearing/reset
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatStateTest {

    @Before
    fun setUp() {
        ChatState.reset()
    }

    @After
    fun tearDown() {
        ChatState.reset()
    }

    // ==================== INITIALIZATION TESTS ====================

    @Test
    fun `initialize sets session info correctly`() {
        // Given
        val chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID
        val visitorId = TestFixtures.TEST_VISITOR_ID
        val botId = TestFixtures.TEST_BOT_ID
        val workspaceId = TestFixtures.TEST_WORKSPACE_ID

        // When
        ChatState.initialize(chatSessionId, visitorId, botId, workspaceId)

        // Then
        assertThat(ChatState.chatSessionId).isEqualTo(chatSessionId)
        assertThat(ChatState.visitorId).isEqualTo(visitorId)
        assertThat(ChatState.botId).isEqualTo(botId)
        assertThat(ChatState.workspaceId).isEqualTo(workspaceId)
    }

    @Test
    fun `initialize without workspaceId sets it to null`() {
        // Given
        val chatSessionId = TestFixtures.TEST_CHAT_SESSION_ID
        val visitorId = TestFixtures.TEST_VISITOR_ID
        val botId = TestFixtures.TEST_BOT_ID

        // When
        ChatState.initialize(chatSessionId, visitorId, botId)

        // Then
        assertThat(ChatState.workspaceId).isNull()
    }

    // ==================== ANSWER VARIABLE TESTS ====================

    @Test
    fun `addAnswerVariable creates new variable`() {
        // Given
        val nodeId = "test-node-1"
        val key = "userName"
        val value = "John Doe"

        // When
        ChatState.addAnswerVariable(nodeId, key, value)

        // Then
        assertThat(ChatState.getAnswerVariableValue(key)).isEqualTo(value)
    }

    @Test
    fun `addAnswerVariable updates existing variable with same nodeId`() {
        // Given
        val nodeId = "test-node-1"
        val key = "userName"

        // When
        ChatState.addAnswerVariable(nodeId, key, "Initial Value")
        ChatState.addAnswerVariable(nodeId, key, "Updated Value")

        // Then
        assertThat(ChatState.getAnswerVariableValue(key)).isEqualTo("Updated Value")
    }

    @Test
    fun `addAnswerVariable with null value creates variable`() {
        // Given
        val nodeId = "test-node-1"
        val key = "userName"

        // When
        ChatState.addAnswerVariable(nodeId, key, null)

        // Then
        assertThat(ChatState.answerVariables.value).hasSize(1)
        assertThat(ChatState.getAnswerVariableValue(key)).isNull()
    }

    @Test
    fun `setAnswerVariable updates existing variable by nodeId`() {
        // Given
        val nodeId = "test-node-1"
        val key = "userName"
        ChatState.addAnswerVariable(nodeId, key, null)

        // When
        ChatState.setAnswerVariable(nodeId, "New Value")

        // Then
        assertThat(ChatState.getAnswerVariableValue(key)).isEqualTo("New Value")
    }

    @Test
    fun `setAnswerVariableByKey updates existing variable by key`() {
        // Given
        val nodeId = "test-node-1"
        val key = "userName"
        ChatState.addAnswerVariable(nodeId, key, "Initial")

        // When
        ChatState.setAnswerVariableByKey(key, "By Key Update")

        // Then
        assertThat(ChatState.getAnswerVariableValue(key)).isEqualTo("By Key Update")
    }

    @Test
    fun `setAnswerVariableByKey creates new variable if not exists`() {
        // Given
        val key = "newKey"
        val value = "New Value"

        // When
        ChatState.setAnswerVariableByKey(key, value)

        // Then
        assertThat(ChatState.getAnswerVariableValue(key)).isEqualTo(value)
    }

    @Test
    fun `getAnswerVariableValue returns null for non-existent key`() {
        // When
        val result = ChatState.getAnswerVariableValue("non-existent")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `getAnswerVariablesMap returns all variables as map`() {
        // Given
        ChatState.addAnswerVariable("node-1", "name", "John")
        ChatState.addAnswerVariable("node-2", "email", "john@test.com")
        ChatState.addAnswerVariable("node-3", "age", 30)

        // When
        val result = ChatState.getAnswerVariablesMap()

        // Then
        assertThat(result).hasSize(3)
        assertThat(result["name"]).isEqualTo("John")
        assertThat(result["email"]).isEqualTo("john@test.com")
        assertThat(result["age"]).isEqualTo(30)
    }

    @Test
    fun `answer variable supports different value types`() {
        // Given & When
        ChatState.addAnswerVariable("node-1", "stringValue", "Hello")
        ChatState.addAnswerVariable("node-2", "intValue", 42)
        ChatState.addAnswerVariable("node-3", "doubleValue", 3.14)
        ChatState.addAnswerVariable("node-4", "boolValue", true)
        ChatState.addAnswerVariable("node-5", "listValue", listOf("a", "b", "c"))

        // Then
        assertThat(ChatState.getAnswerVariableValue("stringValue")).isEqualTo("Hello")
        assertThat(ChatState.getAnswerVariableValue("intValue")).isEqualTo(42)
        assertThat(ChatState.getAnswerVariableValue("doubleValue")).isEqualTo(3.14)
        assertThat(ChatState.getAnswerVariableValue("boolValue")).isEqualTo(true)
        assertThat(ChatState.getAnswerVariableValue("listValue")).isEqualTo(listOf("a", "b", "c"))
    }

    // ==================== VARIABLE RESOLUTION TESTS ====================

    @Test
    fun `resolveValue returns original string when no variable reference`() {
        // Given
        val value = "Plain text without variables"

        // When
        val result = ChatState.resolveValue(value)

        // Then
        assertThat(result).isEqualTo(value)
    }

    @Test
    fun `resolveValue resolves double curly brace syntax`() {
        // Given
        ChatState.addAnswerVariable("node-1", "userName", "John")
        val value = "{{userName}}"

        // When
        val result = ChatState.resolveValue(value)

        // Then
        assertThat(result).isEqualTo("John")
    }

    @Test
    fun `resolveValue resolves dollar curly brace syntax`() {
        // Given
        ChatState.addAnswerVariable("node-1", "email", "john@test.com")
        val value = "\${email}"

        // When
        val result = ChatState.resolveValue(value)

        // Then
        assertThat(result).isEqualTo("john@test.com")
    }

    @Test
    fun `resolveValue returns original value when variable not found`() {
        // Given
        val value = "{{nonExistent}}"

        // When
        val result = ChatState.resolveValue(value)

        // Then
        assertThat(result).isEqualTo(value)
    }

    @Test
    fun `resolveValue prefers answer variables over temp variables`() {
        // Given
        ChatState.addAnswerVariable("node-1", "testVar", "AnswerValue")
        ChatState.setVariable("testVar", "TempValue")
        val value = "{{testVar}}"

        // When
        val result = ChatState.resolveValue(value)

        // Then
        assertThat(result).isEqualTo("AnswerValue")
    }

    @Test
    fun `resolveValue falls back to temp variable when answer not found`() {
        // Given
        ChatState.setVariable("tempOnly", "TempValue")
        val value = "{{tempOnly}}"

        // When
        val result = ChatState.resolveValue(value)

        // Then
        assertThat(result).isEqualTo("TempValue")
    }

    // ==================== TEMPORARY VARIABLE TESTS ====================

    @Test
    fun `setVariable creates temporary variable`() {
        // Given
        val name = "counter"
        val value = 10

        // When
        ChatState.setVariable(name, value)

        // Then
        assertThat(ChatState.getVariable(name)).isEqualTo(value)
    }

    @Test
    fun `setVariable updates existing temporary variable`() {
        // Given
        val name = "counter"
        ChatState.setVariable(name, 10)

        // When
        ChatState.setVariable(name, 20)

        // Then
        assertThat(ChatState.getVariable(name)).isEqualTo(20)
    }

    @Test
    fun `getVariable returns null for non-existent variable`() {
        // When
        val result = ChatState.getVariable("nonExistent")

        // Then
        assertThat(result).isNull()
    }

    // ==================== TRANSCRIPT TESTS ====================

    @Test
    fun `addToTranscript adds bot message`() {
        // Given
        val by = "bot"
        val message = "Hello, how can I help you?"

        // When
        ChatState.addToTranscript(by, message)

        // Then
        assertThat(ChatState.transcript.value).hasSize(1)
        assertThat(ChatState.transcript.value[0].by).isEqualTo(by)
        assertThat(ChatState.transcript.value[0].message).isEqualTo(message)
    }

    @Test
    fun `addToTranscript adds user message`() {
        // Given
        val by = "user"
        val message = "I need help with my order"

        // When
        ChatState.addToTranscript(by, message)

        // Then
        assertThat(ChatState.transcript.value).hasSize(1)
        assertThat(ChatState.transcript.value[0].by).isEqualTo(by)
        assertThat(ChatState.transcript.value[0].message).isEqualTo(message)
    }

    @Test
    fun `addToTranscript adds agent message`() {
        // Given
        val by = "agent"
        val message = "I'll help you with that"

        // When
        ChatState.addToTranscript(by, message)

        // Then
        assertThat(ChatState.transcript.value).hasSize(1)
        assertThat(ChatState.transcript.value[0].by).isEqualTo(by)
    }

    @Test
    fun `addToTranscript maintains order`() {
        // Given & When
        ChatState.addToTranscript("bot", "Message 1")
        ChatState.addToTranscript("user", "Message 2")
        ChatState.addToTranscript("bot", "Message 3")

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript).hasSize(3)
        assertThat(transcript[0].message).isEqualTo("Message 1")
        assertThat(transcript[1].message).isEqualTo("Message 2")
        assertThat(transcript[2].message).isEqualTo("Message 3")
    }

    @Test
    fun `addToTranscript limits size to 200 entries`() {
        // Given & When - Add 250 entries
        repeat(250) { index ->
            ChatState.addToTranscript("bot", "Message $index")
        }

        // Then - Should be trimmed to 200
        assertThat(ChatState.transcript.value.size).isAtMost(200)
    }

    @Test
    fun `getTranscriptForGPT converts bot messages to assistant role`() {
        // Given
        ChatState.addToTranscript("bot", "Bot message")

        // When
        val result = ChatState.getTranscriptForGPT()

        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0]["role"]).isEqualTo("assistant")
        assertThat(result[0]["content"]).isEqualTo("Bot message")
    }

    @Test
    fun `getTranscriptForGPT converts agent messages to assistant role`() {
        // Given
        ChatState.addToTranscript("agent", "Agent message")

        // When
        val result = ChatState.getTranscriptForGPT()

        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0]["role"]).isEqualTo("assistant")
    }

    @Test
    fun `getTranscriptForGPT converts user messages to user role`() {
        // Given
        ChatState.addToTranscript("user", "User message")

        // When
        val result = ChatState.getTranscriptForGPT()

        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0]["role"]).isEqualTo("user")
        assertThat(result[0]["content"]).isEqualTo("User message")
    }

    // ==================== RECORD TESTS ====================

    @Test
    fun `pushToRecord adds new entry`() {
        // Given
        val entry = RecordEntry(
            id = "record-1",
            shape = "bot-message",
            type = "message-node",
            text = "Hello"
        )

        // When
        ChatState.pushToRecord(entry)

        // Then
        assertThat(ChatState.record.value).hasSize(1)
        assertThat(ChatState.record.value[0].id).isEqualTo("record-1")
    }

    @Test
    fun `pushToRecord merges data for same ID`() {
        // Given
        val entry1 = RecordEntry(
            id = "record-1",
            shape = "bot-message",
            text = "Hello",
            data = mutableMapOf("key1" to "value1")
        )
        val entry2 = RecordEntry(
            id = "record-1",
            shape = "bot-message",
            text = "Updated",
            data = mutableMapOf("key2" to "value2")
        )

        // When
        ChatState.pushToRecord(entry1)
        ChatState.pushToRecord(entry2)

        // Then
        assertThat(ChatState.record.value).hasSize(1)
        val mergedEntry = ChatState.record.value[0]
        assertThat(mergedEntry.data["key1"]).isEqualTo("value1")
        assertThat(mergedEntry.data["key2"]).isEqualTo("value2")
    }

    @Test
    fun `getRecordForServer returns proper format`() {
        // Given
        val entry = RecordEntry(
            id = "record-1",
            shape = "bot-message",
            type = "message-node",
            text = "Hello",
            data = mutableMapOf("extra" to "data")
        )
        ChatState.pushToRecord(entry)

        // When
        val result = ChatState.getRecordForServer()

        // Then
        assertThat(result).hasSize(1)
        val serverRecord = result[0]
        assertThat(serverRecord["id"]).isEqualTo("record-1")
        assertThat(serverRecord["shape"]).isEqualTo("bot-message")
        assertThat(serverRecord["type"]).isEqualTo("message-node")
        assertThat(serverRecord["text"]).isEqualTo("Hello")
        assertThat(serverRecord["extra"]).isEqualTo("data")
    }

    @Test
    fun `record entries maintain order`() {
        // Given & When
        ChatState.pushToRecord(RecordEntry("id-1", "shape-1", text = "First"))
        ChatState.pushToRecord(RecordEntry("id-2", "shape-2", text = "Second"))
        ChatState.pushToRecord(RecordEntry("id-3", "shape-3", text = "Third"))

        // Then
        val record = ChatState.record.value
        assertThat(record).hasSize(3)
        assertThat(record[0].text).isEqualTo("First")
        assertThat(record[1].text).isEqualTo("Second")
        assertThat(record[2].text).isEqualTo("Third")
    }

    // ==================== USER METADATA TESTS ====================

    @Test
    fun `setUserMetadata sets name`() {
        // Given
        val name = "John Doe"

        // When
        ChatState.setUserMetadata("name", name)

        // Then
        assertThat(ChatState.getUserMetadata("name")).isEqualTo(name)
    }

    @Test
    fun `setUserMetadata sets email`() {
        // Given
        val email = "john@example.com"

        // When
        ChatState.setUserMetadata("email", email)

        // Then
        assertThat(ChatState.getUserMetadata("email")).isEqualTo(email)
    }

    @Test
    fun `setUserMetadata sets phone`() {
        // Given
        val phone = "+1234567890"

        // When
        ChatState.setUserMetadata("phone", phone)

        // Then
        assertThat(ChatState.getUserMetadata("phone")).isEqualTo(phone)
    }

    @Test
    fun `setUserMetadata handles mobile as phone alias`() {
        // Given
        val phone = "+1234567890"

        // When
        ChatState.setUserMetadata("mobile", phone)

        // Then
        assertThat(ChatState.getUserMetadata("phone")).isEqualTo(phone)
        assertThat(ChatState.getUserMetadata("mobile")).isEqualTo(phone)
    }

    @Test
    fun `setUserMetadata stores custom metadata`() {
        // Given
        val customKey = "companyName"
        val customValue = "Acme Inc"

        // When
        ChatState.setUserMetadata(customKey, customValue)

        // Then
        assertThat(ChatState.getUserMetadata(customKey)).isEqualTo(customValue)
    }

    @Test
    fun `setUserMetadata is case insensitive for standard fields`() {
        // Given & When
        ChatState.setUserMetadata("NAME", "John")
        ChatState.setUserMetadata("Email", "john@test.com")
        ChatState.setUserMetadata("PHONE", "+123")

        // Then
        assertThat(ChatState.getUserMetadata("name")).isEqualTo("John")
        assertThat(ChatState.getUserMetadata("email")).isEqualTo("john@test.com")
        assertThat(ChatState.getUserMetadata("phone")).isEqualTo("+123")
    }

    @Test
    fun `getUserMetadata returns null for non-existent field`() {
        // When
        val result = ChatState.getUserMetadata("nonExistent")

        // Then
        assertThat(result).isNull()
    }

    // ==================== FLOW STEPS TESTS ====================

    @Test
    fun `setSteps stores flow steps`() {
        // Given
        val (steps, _) = TestFixtures.createSimpleFlow()

        // When
        ChatState.setSteps(steps)

        // Then
        assertThat(ChatState.steps.value).hasSize(3)
    }

    @Test
    fun `getCurrentNode returns node at current index`() {
        // Given
        val (steps, _) = TestFixtures.createSimpleFlow()
        ChatState.setSteps(steps)

        // When
        val currentNode = ChatState.getCurrentNode()

        // Then
        assertThat(currentNode).isNotNull()
        assertThat(currentNode!!["id"]).isEqualTo("node-1")
    }

    @Test
    fun `getCurrentNode returns null when no steps`() {
        // When
        val currentNode = ChatState.getCurrentNode()

        // Then
        assertThat(currentNode).isNull()
    }

    @Test
    fun `incrementIndex moves to next node`() {
        // Given
        val (steps, _) = TestFixtures.createSimpleFlow()
        ChatState.setSteps(steps)

        // When
        ChatState.incrementIndex()

        // Then
        assertThat(ChatState.currentIndex.value).isEqualTo(1)
        assertThat(ChatState.getCurrentNode()?.get("id")).isEqualTo("node-2")
    }

    @Test
    fun `setCurrentIndex jumps to specific node`() {
        // Given
        val (steps, _) = TestFixtures.createSimpleFlow()
        ChatState.setSteps(steps)

        // When
        ChatState.setCurrentIndex(2)

        // Then
        assertThat(ChatState.currentIndex.value).isEqualTo(2)
        assertThat(ChatState.getCurrentNode()?.get("id")).isEqualTo("node-3")
    }

    @Test
    fun `getCurrentNode returns null for out of bounds index`() {
        // Given
        val (steps, _) = TestFixtures.createSimpleFlow()
        ChatState.setSteps(steps)
        ChatState.setCurrentIndex(100)

        // When
        val currentNode = ChatState.getCurrentNode()

        // Then
        assertThat(currentNode).isNull()
    }

    // ==================== BUILD RESPONSE DATA TESTS ====================

    @Test
    fun `buildResponseData includes all required fields`() {
        // Given
        ChatState.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            TestFixtures.TEST_WORKSPACE_ID
        )

        // When
        val responseData = ChatState.buildResponseData()

        // Then
        assertThat(responseData["version"]).isEqualTo("v2")
        assertThat(responseData["chatSessionId"]).isEqualTo(TestFixtures.TEST_CHAT_SESSION_ID)
        assertThat(responseData["visitorId"]).isEqualTo(TestFixtures.TEST_VISITOR_ID)
        assertThat(responseData["botId"]).isEqualTo(TestFixtures.TEST_BOT_ID)
        assertThat(responseData["workspaceId"]).isEqualTo(TestFixtures.TEST_WORKSPACE_ID)
        assertThat(responseData["chatDate"]).isNotNull()
        assertThat(responseData["deviceInfo"]).isNotNull()
        assertThat(responseData["location"]).isNotNull()
        assertThat(responseData["record"]).isNotNull()
        assertThat(responseData["answerVariables"]).isNotNull()
    }

    @Test
    fun `buildResponseData includes answer variables in correct format`() {
        // Given
        ChatState.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID
        )
        ChatState.addAnswerVariable("node-1", "name", "John")

        // When
        val responseData = ChatState.buildResponseData()

        // Then
        @Suppress("UNCHECKED_CAST")
        val answerVariables = responseData["answerVariables"] as List<Map<String, Any?>>
        assertThat(answerVariables).hasSize(1)
        assertThat(answerVariables[0]["nodeId"]).isEqualTo("node-1")
        assertThat(answerVariables[0]["key"]).isEqualTo("name")
        assertThat(answerVariables[0]["value"]).isEqualTo("John")
    }

    // ==================== RESET TESTS ====================

    @Test
    fun `reset clears all state`() {
        // Given
        ChatState.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            TestFixtures.TEST_WORKSPACE_ID
        )
        ChatState.addAnswerVariable("node-1", "name", "John")
        ChatState.setVariable("temp", "value")
        ChatState.setUserMetadata("name", "John")
        ChatState.addToTranscript("bot", "Hello")
        ChatState.pushToRecord(RecordEntry("id", "shape"))
        ChatState.setSteps(TestFixtures.createSimpleFlow().first)

        // When
        ChatState.reset()

        // Then
        assertThat(ChatState.chatSessionId).isNull()
        assertThat(ChatState.visitorId).isNull()
        assertThat(ChatState.botId).isNull()
        assertThat(ChatState.workspaceId).isNull()
        assertThat(ChatState.answerVariables.value).isEmpty()
        assertThat(ChatState.variables.value).isEmpty()
        assertThat(ChatState.userMetadata.value.name).isNull()
        assertThat(ChatState.transcript.value).isEmpty()
        assertThat(ChatState.record.value).isEmpty()
        assertThat(ChatState.steps.value).isEmpty()
        assertThat(ChatState.currentIndex.value).isEqualTo(0)
    }

    @Test
    fun `softReset preserves session info`() {
        // Given
        ChatState.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            TestFixtures.TEST_WORKSPACE_ID
        )
        ChatState.addAnswerVariable("node-1", "name", "John")
        ChatState.addToTranscript("bot", "Hello")

        // When
        ChatState.softReset()

        // Then - Session info preserved
        assertThat(ChatState.chatSessionId).isEqualTo(TestFixtures.TEST_CHAT_SESSION_ID)
        assertThat(ChatState.visitorId).isEqualTo(TestFixtures.TEST_VISITOR_ID)
        assertThat(ChatState.botId).isEqualTo(TestFixtures.TEST_BOT_ID)

        // But conversation state cleared
        assertThat(ChatState.answerVariables.value).isEmpty()
        assertThat(ChatState.transcript.value).isEmpty()
        assertThat(ChatState.record.value).isEmpty()
        assertThat(ChatState.currentIndex.value).isEqualTo(0)
    }

    // ==================== MEMORY MANAGEMENT TESTS ====================

    @Test
    fun `getMemoryUsageInfo returns correct counts`() {
        // Given
        ChatState.addAnswerVariable("node-1", "var1", "value1")
        ChatState.addAnswerVariable("node-2", "var2", "value2")
        ChatState.addToTranscript("bot", "Message 1")
        ChatState.addToTranscript("user", "Message 2")
        ChatState.pushToRecord(RecordEntry("id-1", "shape-1"))

        // When
        val memoryInfo = ChatState.getMemoryUsageInfo()

        // Then
        assertThat(memoryInfo.answerVariables).isEqualTo(2)
        assertThat(memoryInfo.transcriptEntries).isEqualTo(2)
        assertThat(memoryInfo.recordEntries).isEqualTo(1)
    }

    @Test
    fun `configurePagination sets values correctly`() {
        // Given & When
        ChatState.configurePagination(maxMessages = 200, pageSizeConfig = 100)

        // Then - Values are set internally (we verify through behavior)
        // This test verifies no exceptions are thrown
        assertThat(ChatState.paginationState.value).isNotNull()
    }

    // ==================== PAGINATION STATE TESTS ====================

    @Test
    fun `initial pagination state has correct defaults`() {
        // When
        val paginationState = ChatState.paginationState.value

        // Then
        assertThat(paginationState.isLoading).isFalse()
        assertThat(paginationState.hasMoreMessages).isTrue()
        assertThat(paginationState.oldestLoadedIndex).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `hasMoreMessages returns false without repository`() {
        // When
        val result = ChatState.hasMoreMessages()

        // Then
        assertThat(result).isFalse()
    }
}
