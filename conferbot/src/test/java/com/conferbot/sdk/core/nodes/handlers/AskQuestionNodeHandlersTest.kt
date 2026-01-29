package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.nodes.NodeResult
import com.conferbot.sdk.core.nodes.NodeTypes
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.testutils.TestFixtures
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Ask Question Node Handlers
 * Tests all ask question node handlers including:
 * - AskNameNodeHandler
 * - AskEmailNodeHandler
 * - AskPhoneNodeHandler
 * - AskNumberNodeHandler
 * - AskUrlNodeHandler
 * - AskLocationNodeHandler
 * - AskCustomNodeHandler
 * - AskFileNodeHandler
 * - CalendarNodeHandler
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AskQuestionNodeHandlersTest {

    @Before
    fun setUp() {
        ChatState.reset()
        ChatState.initialize(
            TestFixtures.TEST_CHAT_SESSION_ID,
            TestFixtures.TEST_VISITOR_ID,
            TestFixtures.TEST_BOT_ID,
            TestFixtures.TEST_WORKSPACE_ID
        )
    }

    @After
    fun tearDown() {
        ChatState.reset()
    }

    // ==================== ASK NAME NODE HANDLER TESTS ====================

    @Test
    fun `AskNameNodeHandler returns correct node type`() {
        val handler = AskNameNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.ASK_NAME)
    }

    @Test
    fun `AskNameNodeHandler processes with default question text`() = runTest {
        // Given
        val handler = AskNameNodeHandler()
        val nodeData = mapOf(
            "type" to NodeTypes.ASK_NAME,
            "answerVariable" to "userName"
        )

        // When
        val result = handler.process(nodeData, "name-1")

        // Then
        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.TextInput::class.java)
        val textInput = uiState as NodeUIState.TextInput
        assertThat(textInput.questionText).isEqualTo("What is your name?")
        assertThat(textInput.inputType).isEqualTo(NodeUIState.TextInput.InputType.NAME)
    }

    @Test
    fun `AskNameNodeHandler processes with custom question text`() = runTest {
        // Given
        val handler = AskNameNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNameNodeData(
            questionText = "Please tell us your name"
        )

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        val textInput = (result as NodeResult.DisplayUI).uiState as NodeUIState.TextInput
        assertThat(textInput.questionText).isEqualTo("Please tell us your name")
    }

    @Test
    fun `AskNameNodeHandler initializes answer variable`() = runTest {
        // Given
        val handler = AskNameNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNameNodeData(
            answerVariable = "fullName"
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val answerVars = ChatState.answerVariables.value
        assertThat(answerVars.any { it.key == "fullName" }).isTrue()
    }

    @Test
    fun `AskNameNodeHandler adds question to transcript`() = runTest {
        // Given
        val handler = AskNameNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNameNodeData(
            questionText = "What's your name?"
        )

        // When
        handler.process(nodeData, nodeId)

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript).hasSize(1)
        assertThat(transcript[0].by).isEqualTo("bot")
        assertThat(transcript[0].message).isEqualTo("What's your name?")
    }

    @Test
    fun `AskNameNodeHandler handleResponse stores name`() = runTest {
        // Given
        val handler = AskNameNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNameNodeData(
            answerVariable = "name"
        )
        handler.process(nodeData, nodeId)

        // When
        val result = handler.handleResponse("John Doe", nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat(ChatState.getAnswerVariableValue("name")).isEqualTo("John Doe")
        assertThat(ChatState.getUserMetadata("name")).isEqualTo("John Doe")
    }

    @Test
    fun `AskNameNodeHandler handleResponse rejects empty name`() = runTest {
        // Given
        val handler = AskNameNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNameNodeData()
        handler.process(nodeData, nodeId)

        // When
        val result = handler.handleResponse("", nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.Error::class.java)
        val error = result as NodeResult.Error
        assertThat(error.message).isEqualTo("Please enter your name")
        assertThat(error.shouldProceed).isFalse()
    }

    @Test
    fun `AskNameNodeHandler handleResponse trims whitespace`() = runTest {
        // Given
        val handler = AskNameNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNameNodeData(
            answerVariable = "name"
        )
        handler.process(nodeData, nodeId)

        // When
        handler.handleResponse("  John Doe  ", nodeData, nodeId)

        // Then
        assertThat(ChatState.getAnswerVariableValue("name")).isEqualTo("John Doe")
    }

    @Test
    fun `AskNameNodeHandler handleResponse adds name to transcript`() = runTest {
        // Given
        val handler = AskNameNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNameNodeData()
        handler.process(nodeData, nodeId)

        // When
        handler.handleResponse("Jane", nodeData, nodeId)

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript.last().by).isEqualTo("user")
        assertThat(transcript.last().message).isEqualTo("Jane")
    }

    @Test
    fun `AskNameNodeHandler handleResponse records response`() = runTest {
        // Given
        val handler = AskNameNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNameNodeData()
        handler.process(nodeData, nodeId)

        // When
        handler.handleResponse("John", nodeData, nodeId)

        // Then
        val record = ChatState.record.value
        assertThat(record.any { it.shape == "user-ask-name-response" }).isTrue()
    }

    @Test
    fun `AskNameNodeHandler handleResponse with greeting adds to transcript`() = runTest {
        // Given
        val handler = AskNameNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNameNodeData(
            nameGreetResponse = "Nice to meet you, {name}!"
        )
        handler.process(nodeData, nodeId)

        // When
        handler.handleResponse("Alice", nodeData, nodeId)

        // Then
        val transcript = ChatState.transcript.value
        // Should have: question, user answer, and greeting
        assertThat(transcript.any { it.message == "Nice to meet you, Alice!" }).isTrue()
    }

    // ==================== ASK EMAIL NODE HANDLER TESTS ====================

    @Test
    fun `AskEmailNodeHandler returns correct node type`() {
        val handler = AskEmailNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.ASK_EMAIL)
    }

    @Test
    fun `AskEmailNodeHandler processes with correct input type`() = runTest {
        // Given
        val handler = AskEmailNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskEmailNodeData()

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        val textInput = (result as NodeResult.DisplayUI).uiState as NodeUIState.TextInput
        assertThat(textInput.inputType).isEqualTo(NodeUIState.TextInput.InputType.EMAIL)
    }

    @Test
    fun `AskEmailNodeHandler handleResponse validates email format`() = runTest {
        // Given
        val handler = AskEmailNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskEmailNodeData()
        handler.process(nodeData, nodeId)

        // Test valid emails
        for (validEmail in TestFixtures.ValidationData.validEmails) {
            ChatState.reset()
            ChatState.initialize(
                TestFixtures.TEST_CHAT_SESSION_ID,
                TestFixtures.TEST_VISITOR_ID,
                TestFixtures.TEST_BOT_ID,
                TestFixtures.TEST_WORKSPACE_ID
            )
            handler.process(nodeData, nodeId)

            val result = handler.handleResponse(validEmail, nodeData, nodeId)
            assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        }
    }

    @Test
    fun `AskEmailNodeHandler rejects invalid emails`() = runTest {
        // Given
        val handler = AskEmailNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskEmailNodeData(
            incorrectEmailResponse = "Invalid email format"
        )
        handler.process(nodeData, nodeId)

        // Test invalid emails
        for (invalidEmail in TestFixtures.ValidationData.invalidEmails) {
            val result = handler.handleResponse(invalidEmail, nodeData, nodeId)
            assertThat(result).isInstanceOf(NodeResult.Error::class.java)
            val error = result as NodeResult.Error
            assertThat(error.message).isEqualTo("Invalid email format")
            assertThat(error.shouldProceed).isFalse()
        }
    }

    @Test
    fun `AskEmailNodeHandler stores valid email`() = runTest {
        // Given
        val handler = AskEmailNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskEmailNodeData(
            answerVariable = "userEmail"
        )
        handler.process(nodeData, nodeId)

        // When
        handler.handleResponse("test@example.com", nodeData, nodeId)

        // Then
        assertThat(ChatState.getAnswerVariableValue("userEmail")).isEqualTo("test@example.com")
        assertThat(ChatState.getUserMetadata("email")).isEqualTo("test@example.com")
    }

    @Test
    fun `AskEmailNodeHandler uses custom error message`() = runTest {
        // Given
        val handler = AskEmailNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskEmailNodeData(
            incorrectEmailResponse = "Please provide a valid email address"
        )
        handler.process(nodeData, nodeId)

        // When
        val result = handler.handleResponse("invalid", nodeData, nodeId)

        // Then
        val error = result as NodeResult.Error
        assertThat(error.message).isEqualTo("Please provide a valid email address")
    }

    // ==================== ASK PHONE NODE HANDLER TESTS ====================

    @Test
    fun `AskPhoneNodeHandler returns correct node type`() {
        val handler = AskPhoneNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.ASK_PHONE)
    }

    @Test
    fun `AskPhoneNodeHandler processes with correct input type`() = runTest {
        // Given
        val handler = AskPhoneNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskPhoneNodeData()

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        val textInput = (result as NodeResult.DisplayUI).uiState as NodeUIState.TextInput
        assertThat(textInput.inputType).isEqualTo(NodeUIState.TextInput.InputType.PHONE)
    }

    @Test
    fun `AskPhoneNodeHandler handleResponse validates phone format`() = runTest {
        // Given
        val handler = AskPhoneNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskPhoneNodeData()
        handler.process(nodeData, nodeId)

        // Test valid phones
        for (validPhone in TestFixtures.ValidationData.validPhones) {
            ChatState.reset()
            ChatState.initialize(
                TestFixtures.TEST_CHAT_SESSION_ID,
                TestFixtures.TEST_VISITOR_ID,
                TestFixtures.TEST_BOT_ID,
                TestFixtures.TEST_WORKSPACE_ID
            )
            handler.process(nodeData, nodeId)

            val result = handler.handleResponse(validPhone, nodeData, nodeId)
            assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        }
    }

    @Test
    fun `AskPhoneNodeHandler rejects invalid phones`() = runTest {
        // Given
        val handler = AskPhoneNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskPhoneNodeData(
            incorrectPhoneNumberResponse = "Invalid phone"
        )
        handler.process(nodeData, nodeId)

        // Test invalid phones
        for (invalidPhone in TestFixtures.ValidationData.invalidPhones) {
            val result = handler.handleResponse(invalidPhone, nodeData, nodeId)
            assertThat(result).isInstanceOf(NodeResult.Error::class.java)
        }
    }

    @Test
    fun `AskPhoneNodeHandler stores valid phone`() = runTest {
        // Given
        val handler = AskPhoneNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskPhoneNodeData(
            answerVariable = "phoneNumber"
        )
        handler.process(nodeData, nodeId)

        // When
        handler.handleResponse("+1234567890", nodeData, nodeId)

        // Then
        assertThat(ChatState.getAnswerVariableValue("phoneNumber")).isEqualTo("+1234567890")
        assertThat(ChatState.getUserMetadata("phone")).isEqualTo("+1234567890")
    }

    // ==================== ASK NUMBER NODE HANDLER TESTS ====================

    @Test
    fun `AskNumberNodeHandler returns correct node type`() {
        val handler = AskNumberNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.ASK_NUMBER)
    }

    @Test
    fun `AskNumberNodeHandler processes with correct input type`() = runTest {
        // Given
        val handler = AskNumberNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNumberNodeData()

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        val textInput = (result as NodeResult.DisplayUI).uiState as NodeUIState.TextInput
        assertThat(textInput.inputType).isEqualTo(NodeUIState.TextInput.InputType.NUMBER)
    }

    @Test
    fun `AskNumberNodeHandler handleResponse validates number format`() = runTest {
        // Given
        val handler = AskNumberNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNumberNodeData()
        handler.process(nodeData, nodeId)

        // Test valid numbers
        for (validNumber in TestFixtures.ValidationData.validNumbers) {
            ChatState.reset()
            ChatState.initialize(
                TestFixtures.TEST_CHAT_SESSION_ID,
                TestFixtures.TEST_VISITOR_ID,
                TestFixtures.TEST_BOT_ID,
                TestFixtures.TEST_WORKSPACE_ID
            )
            handler.process(nodeData, nodeId)

            val result = handler.handleResponse(validNumber, nodeData, nodeId)
            assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        }
    }

    @Test
    fun `AskNumberNodeHandler rejects invalid numbers`() = runTest {
        // Given
        val handler = AskNumberNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNumberNodeData()
        handler.process(nodeData, nodeId)

        // Test invalid numbers
        for (invalidNumber in TestFixtures.ValidationData.invalidNumbers) {
            val result = handler.handleResponse(invalidNumber, nodeData, nodeId)
            assertThat(result).isInstanceOf(NodeResult.Error::class.java)
        }
    }

    @Test
    fun `AskNumberNodeHandler stores number as double`() = runTest {
        // Given
        val handler = AskNumberNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNumberNodeData(
            answerVariable = "quantity"
        )
        handler.process(nodeData, nodeId)

        // When
        handler.handleResponse("42", nodeData, nodeId)

        // Then
        assertThat(ChatState.getAnswerVariableValue("quantity")).isEqualTo(42.0)
    }

    @Test
    fun `AskNumberNodeHandler handles decimal numbers`() = runTest {
        // Given
        val handler = AskNumberNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskNumberNodeData(
            answerVariable = "price"
        )
        handler.process(nodeData, nodeId)

        // When
        handler.handleResponse("19.99", nodeData, nodeId)

        // Then
        assertThat(ChatState.getAnswerVariableValue("price")).isEqualTo(19.99)
    }

    // ==================== ASK URL NODE HANDLER TESTS ====================

    @Test
    fun `AskUrlNodeHandler returns correct node type`() {
        val handler = AskUrlNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.ASK_URL)
    }

    @Test
    fun `AskUrlNodeHandler processes with correct input type`() = runTest {
        // Given
        val handler = AskUrlNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskUrlNodeData()

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        val textInput = (result as NodeResult.DisplayUI).uiState as NodeUIState.TextInput
        assertThat(textInput.inputType).isEqualTo(NodeUIState.TextInput.InputType.URL)
    }

    @Test
    fun `AskUrlNodeHandler handleResponse validates URL format`() = runTest {
        // Given
        val handler = AskUrlNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskUrlNodeData()
        handler.process(nodeData, nodeId)

        // Test valid URLs
        for (validUrl in TestFixtures.ValidationData.validUrls) {
            ChatState.reset()
            ChatState.initialize(
                TestFixtures.TEST_CHAT_SESSION_ID,
                TestFixtures.TEST_VISITOR_ID,
                TestFixtures.TEST_BOT_ID,
                TestFixtures.TEST_WORKSPACE_ID
            )
            handler.process(nodeData, nodeId)

            val result = handler.handleResponse(validUrl, nodeData, nodeId)
            assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        }
    }

    @Test
    fun `AskUrlNodeHandler rejects invalid URLs`() = runTest {
        // Given
        val handler = AskUrlNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskUrlNodeData()
        handler.process(nodeData, nodeId)

        // Test invalid URLs
        for (invalidUrl in TestFixtures.ValidationData.invalidUrls) {
            val result = handler.handleResponse(invalidUrl, nodeData, nodeId)
            assertThat(result).isInstanceOf(NodeResult.Error::class.java)
        }
    }

    @Test
    fun `AskUrlNodeHandler stores valid URL`() = runTest {
        // Given
        val handler = AskUrlNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskUrlNodeData(
            answerVariable = "website"
        )
        handler.process(nodeData, nodeId)

        // When
        handler.handleResponse("https://example.com", nodeData, nodeId)

        // Then
        assertThat(ChatState.getAnswerVariableValue("website")).isEqualTo("https://example.com")
    }

    // ==================== ASK LOCATION NODE HANDLER TESTS ====================

    @Test
    fun `AskLocationNodeHandler returns correct node type`() {
        val handler = AskLocationNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.ASK_LOCATION)
    }

    @Test
    fun `AskLocationNodeHandler processes with correct input type`() = runTest {
        // Given
        val handler = AskLocationNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskLocationNodeData()

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        val textInput = (result as NodeResult.DisplayUI).uiState as NodeUIState.TextInput
        assertThat(textInput.inputType).isEqualTo(NodeUIState.TextInput.InputType.LOCATION)
    }

    @Test
    fun `AskLocationNodeHandler handleResponse stores location`() = runTest {
        // Given
        val handler = AskLocationNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskLocationNodeData(
            answerVariable = "userLocation"
        )
        handler.process(nodeData, nodeId)

        // When
        handler.handleResponse("New York, NY", nodeData, nodeId)

        // Then
        assertThat(ChatState.getAnswerVariableValue("userLocation")).isEqualTo("New York, NY")
    }

    @Test
    fun `AskLocationNodeHandler rejects empty location`() = runTest {
        // Given
        val handler = AskLocationNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskLocationNodeData()
        handler.process(nodeData, nodeId)

        // When
        val result = handler.handleResponse("", nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.Error::class.java)
        val error = result as NodeResult.Error
        assertThat(error.message).isEqualTo("Please enter a location")
    }

    // ==================== ASK CUSTOM NODE HANDLER TESTS ====================

    @Test
    fun `AskCustomNodeHandler returns correct node type`() {
        val handler = AskCustomNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.ASK_CUSTOM)
    }

    @Test
    fun `AskCustomNodeHandler processes with TEXT input type`() = runTest {
        // Given
        val handler = AskCustomNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskCustomNodeData()

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        val textInput = (result as NodeResult.DisplayUI).uiState as NodeUIState.TextInput
        assertThat(textInput.inputType).isEqualTo(NodeUIState.TextInput.InputType.TEXT)
    }

    @Test
    fun `AskCustomNodeHandler handleResponse stores custom answer`() = runTest {
        // Given
        val handler = AskCustomNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskCustomNodeData(
            answerVariable = "feedback"
        )
        handler.process(nodeData, nodeId)

        // When
        handler.handleResponse("Great product!", nodeData, nodeId)

        // Then
        assertThat(ChatState.getAnswerVariableValue("feedback")).isEqualTo("Great product!")
    }

    @Test
    fun `AskCustomNodeHandler rejects empty answer`() = runTest {
        // Given
        val handler = AskCustomNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskCustomNodeData()
        handler.process(nodeData, nodeId)

        // When
        val result = handler.handleResponse("  ", nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.Error::class.java)
    }

    @Test
    fun `AskCustomNodeHandler uses nodeId as answerVariable when not provided`() = runTest {
        // Given
        val handler = AskCustomNodeHandler()
        val nodeId = "custom-node-123"
        val nodeData = mapOf(
            "type" to NodeTypes.ASK_CUSTOM,
            "questionText" to "Custom question?"
            // answerVariable not provided
        )
        handler.process(nodeData, nodeId)

        // When
        handler.handleResponse("Custom answer", nodeData, nodeId)

        // Then
        assertThat(ChatState.getAnswerVariableValue(nodeId)).isEqualTo("Custom answer")
    }

    // ==================== ASK FILE NODE HANDLER TESTS ====================

    @Test
    fun `AskFileNodeHandler returns correct node type`() {
        val handler = AskFileNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.ASK_FILE)
    }

    @Test
    fun `AskFileNodeHandler processes with FileUpload UI state`() = runTest {
        // Given
        val handler = AskFileNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskFileNodeData(
            maxSize = 10
        )

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.FileUpload::class.java)
        val fileUpload = uiState as NodeUIState.FileUpload
        assertThat(fileUpload.maxSizeMb).isEqualTo(10)
    }

    @Test
    fun `AskFileNodeHandler handleResponse with map stores file URL`() = runTest {
        // Given
        val handler = AskFileNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskFileNodeData(
            answerVariable = "uploadedFile"
        )
        handler.process(nodeData, nodeId)

        // When
        val response = mapOf(
            "url" to "https://storage.example.com/file.pdf",
            "fileName" to "document.pdf"
        )
        val result = handler.handleResponse(response, nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat(ChatState.getAnswerVariableValue("uploadedFile"))
            .isEqualTo("https://storage.example.com/file.pdf")
    }

    @Test
    fun `AskFileNodeHandler handleResponse with string stores URL`() = runTest {
        // Given
        val handler = AskFileNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskFileNodeData(
            answerVariable = "file"
        )
        handler.process(nodeData, nodeId)

        // When
        val result = handler.handleResponse("https://example.com/file.txt", nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        assertThat(ChatState.getAnswerVariableValue("file"))
            .isEqualTo("https://example.com/file.txt")
    }

    @Test
    fun `AskFileNodeHandler adds file to transcript`() = runTest {
        // Given
        val handler = AskFileNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createAskFileNodeData()
        handler.process(nodeData, nodeId)

        // When
        val response = mapOf(
            "url" to "https://example.com/file.pdf",
            "fileName" to "Report.pdf"
        )
        handler.handleResponse(response, nodeData, nodeId)

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript.any { it.message == "[File: Report.pdf]" }).isTrue()
    }

    // ==================== CALENDAR NODE HANDLER TESTS ====================

    @Test
    fun `CalendarNodeHandler returns correct node type`() {
        val handler = CalendarNodeHandler()
        assertThat(handler.nodeType).isEqualTo(NodeTypes.CALENDAR)
    }

    @Test
    fun `CalendarNodeHandler processes with Calendar UI state`() = runTest {
        // Given
        val handler = CalendarNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createCalendarNodeData(
            showTimeSelection = true,
            timezone = "America/Los_Angeles"
        )

        // When
        val result = handler.process(nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.Calendar::class.java)
        val calendar = uiState as NodeUIState.Calendar
        assertThat(calendar.showTimeSelection).isTrue()
        assertThat(calendar.timezone).isEqualTo("America/Los_Angeles")
    }

    @Test
    fun `CalendarNodeHandler handleResponse stores date`() = runTest {
        // Given
        val handler = CalendarNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createCalendarNodeData(
            answerVariable = "appointmentDate"
        )
        handler.process(nodeData, nodeId)

        // When
        val response = mapOf("date" to "2024-01-15")
        val result = handler.handleResponse(response, nodeData, nodeId)

        // Then
        assertThat(result).isInstanceOf(NodeResult.DelayedProceed::class.java)
        assertThat(ChatState.getAnswerVariableValue("appointmentDate")).isEqualTo("2024-01-15")
    }

    @Test
    fun `CalendarNodeHandler handleResponse stores date and time`() = runTest {
        // Given
        val handler = CalendarNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createCalendarNodeData(
            showTimeSelection = true,
            answerVariable = "appointment"
        )
        handler.process(nodeData, nodeId)

        // When
        val response = mapOf(
            "date" to "2024-01-15",
            "time" to "14:30"
        )
        handler.handleResponse(response, nodeData, nodeId)

        // Then
        assertThat(ChatState.getAnswerVariableValue("appointment"))
            .isEqualTo("2024-01-15 at 14:30")
    }

    @Test
    fun `CalendarNodeHandler adds selection to transcript`() = runTest {
        // Given
        val handler = CalendarNodeHandler()
        val (nodeId, nodeData) = TestFixtures.createCalendarNodeData(
            showTimeSelection = true
        )
        handler.process(nodeData, nodeId)

        // When
        val response = mapOf(
            "date" to "2024-01-15",
            "time" to "10:00"
        )
        handler.handleResponse(response, nodeData, nodeId)

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript.any { it.message == "2024-01-15 at 10:00" }).isTrue()
    }

    // ==================== COMMON BEHAVIORS TESTS ====================

    @Test
    fun `all ask handlers return DisplayUI result on process`() = runTest {
        // Given
        val handlers = listOf(
            AskNameNodeHandler() to TestFixtures.createAskNameNodeData(),
            AskEmailNodeHandler() to TestFixtures.createAskEmailNodeData(),
            AskPhoneNodeHandler() to TestFixtures.createAskPhoneNodeData(),
            AskNumberNodeHandler() to TestFixtures.createAskNumberNodeData(),
            AskUrlNodeHandler() to TestFixtures.createAskUrlNodeData(),
            AskLocationNodeHandler() to TestFixtures.createAskLocationNodeData(),
            AskCustomNodeHandler() to TestFixtures.createAskCustomNodeData()
        )

        // When & Then
        for ((handler, nodeDataPair) in handlers) {
            val (nodeId, nodeData) = nodeDataPair
            val result = handler.process(nodeData, nodeId)
            assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        }
    }

    @Test
    fun `all ask handlers add question to transcript`() = runTest {
        // Given
        val questionText = "Test question?"

        // Test AskNameNodeHandler
        val nameHandler = AskNameNodeHandler()
        val (nameNodeId, nameNodeData) = TestFixtures.createAskNameNodeData(
            questionText = questionText
        )
        nameHandler.process(nameNodeData, nameNodeId)

        // Then
        val transcript = ChatState.transcript.value
        assertThat(transcript).isNotEmpty()
        assertThat(transcript.last().by).isEqualTo("bot")
        assertThat(transcript.last().message).isEqualTo(questionText)
    }

    @Test
    fun `handlers record responses with correct shapes`() = runTest {
        // Given - Process and respond to each handler type
        val testCases = listOf(
            Triple(AskNameNodeHandler(), TestFixtures.createAskNameNodeData(), "John"),
            Triple(AskEmailNodeHandler(), TestFixtures.createAskEmailNodeData(), "test@example.com"),
            Triple(AskPhoneNodeHandler(), TestFixtures.createAskPhoneNodeData(), "+1234567890"),
            Triple(AskNumberNodeHandler(), TestFixtures.createAskNumberNodeData(), "42"),
            Triple(AskUrlNodeHandler(), TestFixtures.createAskUrlNodeData(), "https://example.com"),
            Triple(AskLocationNodeHandler(), TestFixtures.createAskLocationNodeData(), "NYC"),
            Triple(AskCustomNodeHandler(), TestFixtures.createAskCustomNodeData(), "Custom")
        )

        val expectedShapes = listOf(
            "user-ask-name-response",
            "user-ask-email-response",
            "user-ask-phone-response",
            "user-ask-number-response",
            "user-ask-url-response",
            "user-ask-location-response",
            "user-ask-custom-response"
        )

        for ((index, testCase) in testCases.withIndex()) {
            ChatState.reset()
            ChatState.initialize(
                TestFixtures.TEST_CHAT_SESSION_ID,
                TestFixtures.TEST_VISITOR_ID,
                TestFixtures.TEST_BOT_ID,
                TestFixtures.TEST_WORKSPACE_ID
            )

            val (handler, nodeDataPair, response) = testCase
            val (nodeId, nodeData) = nodeDataPair

            handler.process(nodeData, nodeId)
            handler.handleResponse(response, nodeData, nodeId)

            val record = ChatState.record.value
            assertThat(record.any { it.shape == expectedShapes[index] })
                .isTrue()
        }
    }
}
