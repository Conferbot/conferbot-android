package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.ai.*
import com.conferbot.sdk.core.nodes.NodeResult
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.models.AISettings
import com.conferbot.sdk.services.*
import com.conferbot.sdk.testutils.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Integration Node Handlers
 * Tests WebhookNodeHandler, GptNodeHandler, EmailNodeHandler, StripeNodeHandler,
 * and other integration nodes
 */
class IntegrationNodeHandlersTest {

    private lateinit var mockWebhookClient: WebhookClient

    @Before
    fun setUp() {
        mockkObject(ChatState)
        mockWebhookClient = mockk(relaxed = true)

        // Initialize ChatState for tests
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
        unmockkObject(ChatState)
        unmockkAll()
    }

    // ==================== WebhookNodeHandler Tests ====================

    @Test
    fun `WebhookNodeHandler - proceeds when URL is empty`() = runTest {
        val handler = WebhookNodeHandler(mockWebhookClient)

        val nodeData = mapOf(
            "type" to "webhook-node",
            "url" to ""
        )

        val result = handler.process(nodeData, "webhook-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        coVerify(exactly = 0) { mockWebhookClient.execute(any()) }
    }

    @Test
    fun `WebhookNodeHandler - successful POST request`() = runTest {
        val handler = WebhookNodeHandler(mockWebhookClient)

        coEvery { mockWebhookClient.execute(any()) } returns WebhookResponse(
            success = true,
            statusCode = 200,
            body = """{"result": "success"}""",
            retryCount = 0
        )

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "webhook-node",
            "url" to "https://api.example.com/webhook",
            "method" to "POST",
            "body" to mapOf("data" to "test")
        )

        val result = handler.process(nodeData, "webhook-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        coVerify { mockWebhookClient.execute(match {
            it.url == "https://api.example.com/webhook" &&
            it.method == "POST"
        }) }
    }

    @Test
    fun `WebhookNodeHandler - stores response in answer variable`() = runTest {
        val handler = WebhookNodeHandler(mockWebhookClient)

        coEvery { mockWebhookClient.execute(any()) } returns WebhookResponse(
            success = true,
            statusCode = 200,
            body = """{"result": "data"}""",
            retryCount = 0
        )

        every { ChatState.pushToRecord(any()) } returns Unit
        every { ChatState.setAnswerVariableByKey(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "webhook-node",
            "url" to "https://api.example.com/webhook",
            "method" to "GET",
            "answerVariable" to "webhookResponse"
        )

        val result = handler.process(nodeData, "webhook-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.setAnswerVariableByKey("webhookResponse", """{"result": "data"}""") }
    }

    @Test
    fun `WebhookNodeHandler - includes answer variables when configured`() = runTest {
        val handler = WebhookNodeHandler(mockWebhookClient)

        coEvery { mockWebhookClient.execute(any()) } returns WebhookResponse(
            success = true,
            statusCode = 200,
            body = null,
            retryCount = 0
        )

        every { ChatState.getAnswerVariablesMap() } returns mapOf(
            "name" to "John",
            "email" to "john@example.com"
        )
        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "webhook-node",
            "url" to "https://api.example.com/webhook",
            "method" to "POST",
            "includeAnswerVariables" to true
        )

        val result = handler.process(nodeData, "webhook-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        coVerify { mockWebhookClient.execute(match { request ->
            request.body?.contains("answerVariables") == true
        }) }
    }

    @Test
    fun `WebhookNodeHandler - handles error response`() = runTest {
        val handler = WebhookNodeHandler(mockWebhookClient)

        coEvery { mockWebhookClient.execute(any()) } returns WebhookResponse(
            success = false,
            statusCode = 500,
            body = null,
            error = "Internal Server Error",
            retryCount = 3
        )

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "webhook-node",
            "url" to "https://api.example.com/webhook",
            "method" to "POST"
        )

        val result = handler.process(nodeData, "webhook-1")

        // Should still proceed even on error
        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { it.shape == "webhook-error" }) }
    }

    @Test
    fun `WebhookNodeHandler - handles network exception`() = runTest {
        val handler = WebhookNodeHandler(mockWebhookClient)

        coEvery { mockWebhookClient.execute(any()) } throws WebhookException(
            message = "Connection timeout",
            statusCode = null,
            isRetryable = true
        )

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "webhook-node",
            "url" to "https://api.example.com/webhook",
            "method" to "POST"
        )

        val result = handler.process(nodeData, "webhook-1")

        // Should proceed even on exception
        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
    }

    @Test
    fun `WebhookNodeHandler - parses bearer authentication`() = runTest {
        val handler = WebhookNodeHandler(mockWebhookClient)

        coEvery { mockWebhookClient.execute(any()) } returns WebhookResponse(
            success = true,
            statusCode = 200,
            body = null,
            retryCount = 0
        )

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "webhook-node",
            "url" to "https://api.example.com/webhook",
            "method" to "GET",
            "authentication" to mapOf(
                "type" to "bearer",
                "token" to "my-secret-token"
            )
        )

        val result = handler.process(nodeData, "webhook-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        coVerify { mockWebhookClient.execute(match { request ->
            request.authentication is WebhookAuthentication.Bearer &&
            (request.authentication as WebhookAuthentication.Bearer).token == "my-secret-token"
        }) }
    }

    @Test
    fun `WebhookNodeHandler - parses basic authentication`() = runTest {
        val handler = WebhookNodeHandler(mockWebhookClient)

        coEvery { mockWebhookClient.execute(any()) } returns WebhookResponse(
            success = true,
            statusCode = 200,
            body = null,
            retryCount = 0
        )

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "webhook-node",
            "url" to "https://api.example.com/webhook",
            "method" to "GET",
            "authentication" to mapOf(
                "type" to "basic",
                "username" to "user",
                "password" to "pass"
            )
        )

        val result = handler.process(nodeData, "webhook-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        coVerify { mockWebhookClient.execute(match { request ->
            request.authentication is WebhookAuthentication.Basic &&
            (request.authentication as WebhookAuthentication.Basic).username == "user"
        }) }
    }

    @Test
    fun `WebhookNodeHandler - parses API key authentication`() = runTest {
        val handler = WebhookNodeHandler(mockWebhookClient)

        coEvery { mockWebhookClient.execute(any()) } returns WebhookResponse(
            success = true,
            statusCode = 200,
            body = null,
            retryCount = 0
        )

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "webhook-node",
            "url" to "https://api.example.com/webhook",
            "method" to "GET",
            "authentication" to mapOf(
                "type" to "apikey",
                "key" to "X-API-Key",
                "value" to "my-api-key",
                "location" to "header"
            )
        )

        val result = handler.process(nodeData, "webhook-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        coVerify { mockWebhookClient.execute(match { request ->
            request.authentication is WebhookAuthentication.ApiKey &&
            (request.authentication as WebhookAuthentication.ApiKey).key == "X-API-Key"
        }) }
    }

    @Test
    fun `WebhookNodeHandler - parses OAuth2 authentication`() = runTest {
        val handler = WebhookNodeHandler(mockWebhookClient)

        coEvery { mockWebhookClient.execute(any()) } returns WebhookResponse(
            success = true,
            statusCode = 200,
            body = null,
            retryCount = 0
        )

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "webhook-node",
            "url" to "https://api.example.com/webhook",
            "method" to "GET",
            "authentication" to mapOf(
                "type" to "oauth2",
                "tokenUrl" to "https://auth.example.com/token",
                "clientId" to "client-123",
                "clientSecret" to "secret-456",
                "scope" to "read write"
            )
        )

        val result = handler.process(nodeData, "webhook-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        coVerify { mockWebhookClient.execute(match { request ->
            request.authentication is WebhookAuthentication.OAuth2 &&
            (request.authentication as WebhookAuthentication.OAuth2).clientId == "client-123"
        }) }
    }

    @Test
    fun `WebhookNodeHandler - uses custom timeout`() = runTest {
        val handler = WebhookNodeHandler(mockWebhookClient)

        coEvery { mockWebhookClient.execute(any()) } returns WebhookResponse(
            success = true,
            statusCode = 200,
            body = null,
            retryCount = 0
        )

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "webhook-node",
            "url" to "https://api.example.com/webhook",
            "method" to "GET",
            "timeout" to 60  // 60 seconds
        )

        val result = handler.process(nodeData, "webhook-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        coVerify { mockWebhookClient.execute(match { request ->
            request.timeoutMs == 60_000L
        }) }
    }

    @Test
    fun `WebhookNodeHandler - uses custom retry count`() = runTest {
        val handler = WebhookNodeHandler(mockWebhookClient)

        coEvery { mockWebhookClient.execute(any()) } returns WebhookResponse(
            success = true,
            statusCode = 200,
            body = null,
            retryCount = 0
        )

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "webhook-node",
            "url" to "https://api.example.com/webhook",
            "method" to "GET",
            "maxRetries" to 5
        )

        val result = handler.process(nodeData, "webhook-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        coVerify { mockWebhookClient.execute(match { request ->
            request.maxRetries == 5
        }) }
    }

    @Test
    fun `WebhookNodeHandler - handles headers`() = runTest {
        val handler = WebhookNodeHandler(mockWebhookClient)

        coEvery { mockWebhookClient.execute(any()) } returns WebhookResponse(
            success = true,
            statusCode = 200,
            body = null,
            retryCount = 0
        )

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "webhook-node",
            "url" to "https://api.example.com/webhook",
            "method" to "GET",
            "headers" to mapOf(
                "Content-Type" to "application/json",
                "X-Custom-Header" to "custom-value"
            )
        )

        val result = handler.process(nodeData, "webhook-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        coVerify { mockWebhookClient.execute(match { request ->
            request.headers["Content-Type"] == "application/json" &&
            request.headers["X-Custom-Header"] == "custom-value"
        }) }
    }

    // ==================== GptNodeHandler Tests ====================

    @Test
    fun `GptNodeHandler - proceeds when no API key`() = runTest {
        val handler = GptNodeHandler(AISettings())

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "gpt-node",
            "apiKey" to "",
            "selectedModel" to "gpt-3.5-turbo"
        )

        val result = handler.process(nodeData, "gpt-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { it.shape == "gpt-error" }) }
    }

    @Test
    fun `GptNodeHandler - proceeds when no user message`() = runTest {
        val mockAISettings = AISettings(
            openAIApiKey = "test-key"
        )
        val handler = GptNodeHandler(mockAISettings)

        every { ChatState.getTranscriptForGPT() } returns emptyList()

        val nodeData = mapOf(
            "type" to "gpt-node",
            "apiKey" to "test-key",
            "selectedModel" to "gpt-3.5-turbo"
        )

        val result = handler.process(nodeData, "gpt-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
    }

    @Test
    fun `GptNodeHandler - resolves provider from model name`() = runTest {
        val handler = GptNodeHandler(AISettings())

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "gpt-node",
            "apiKey" to "",
            "selectedModel" to "claude-3-opus",  // Anthropic model
            "provider" to ""  // Empty provider, should detect from model
        )

        val result = handler.process(nodeData, "gpt-1")

        // Should detect anthropic from model name
        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
    }

    @Test
    fun `GptNodeHandler - handles provider aliases`() = runTest {
        val handler = GptNodeHandler(AISettings())

        every { ChatState.pushToRecord(any()) } returns Unit

        // Test various provider aliases
        val aliases = listOf("gpt", "openai", "gpt-3.5", "gpt-4", "claude", "anthropic", "deepseek")

        for (alias in aliases) {
            val nodeData = mapOf(
                "type" to "gpt-node",
                "apiKey" to "",
                "selectedModel" to "test-model",
                "provider" to alias
            )

            val result = handler.process(nodeData, "gpt-1")
            assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        }
    }

    // ==================== EmailNodeHandler Tests ====================

    @Test
    fun `EmailNodeHandler - records email trigger and proceeds`() = runTest {
        val handler = EmailNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "email-node",
            "to" to "recipient@example.com",
            "subject" to "Test Email"
        )

        val result = handler.process(nodeData, "email-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "email-triggered" &&
            entry.data["to"] == "recipient@example.com" &&
            entry.data["subject"] == "Test Email"
        }) }
    }

    // ==================== ZapierNodeHandler Tests ====================

    @Test
    fun `ZapierNodeHandler - records trigger and proceeds`() = runTest {
        val handler = ZapierNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit
        every { ChatState.getAnswerVariablesMap() } returns mapOf("key" to "value")

        val nodeData = mapOf(
            "type" to "zapier-node"
        )

        val result = handler.process(nodeData, "zapier-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { it.shape == "zapier-triggered" }) }
    }

    // ==================== GoogleSheetsNodeHandler Tests ====================

    @Test
    fun `GoogleSheetsNodeHandler - records operation and proceeds`() = runTest {
        val handler = GoogleSheetsNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "google-sheets-node",
            "operation" to "write",
            "spreadsheetId" to "sheet-123",
            "sheetName" to "Sheet1"
        )

        val result = handler.process(nodeData, "sheets-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "google-sheets-write" &&
            entry.data["spreadsheetId"] == "sheet-123"
        }) }
    }

    @Test
    fun `GoogleSheetsNodeHandler - defaults to write operation`() = runTest {
        val handler = GoogleSheetsNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "google-sheets-node",
            "spreadsheetId" to "sheet-123"
        )

        val result = handler.process(nodeData, "sheets-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "google-sheets-write"
        }) }
    }

    // ==================== GmailNodeHandler Tests ====================

    @Test
    fun `GmailNodeHandler - records and proceeds`() = runTest {
        val handler = GmailNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "gmail-node",
            "to" to "test@example.com",
            "subject" to "Test Subject"
        )

        val result = handler.process(nodeData, "gmail-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "gmail-triggered"
        }) }
    }

    // ==================== GoogleCalendarNodeHandler Tests ====================

    @Test
    fun `GoogleCalendarNodeHandler - displays calendar UI for booking`() = runTest {
        val handler = GoogleCalendarNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "google-calendar-node",
            "operation" to "book",
            "timeZone" to "America/New_York",
            "answerVariable" to "booking"
        )

        val result = handler.process(nodeData, "calendar-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.Calendar::class.java)
        assertThat((uiState as NodeUIState.Calendar).timezone).isEqualTo("America/New_York")
    }

    @Test
    fun `GoogleCalendarNodeHandler - handles booking response`() = runTest {
        val handler = GoogleCalendarNodeHandler()

        every { ChatState.setAnswerVariable(any(), any()) } returns Unit
        every { ChatState.addToTranscript(any(), any()) } returns Unit
        every { ChatState.pushToRecord(any()) } returns Unit

        val response = mapOf(
            "date" to "2024-03-15",
            "time" to "10:00",
            "email" to "user@example.com"
        )

        val nodeData = mapOf(
            "type" to "google-calendar-node",
            "operation" to "book",
            "timeZone" to "UTC"
        )

        val result = handler.handleResponse(response, nodeData, "calendar-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.setAnswerVariable("calendar-1", "2024-03-15 10:00") }
    }

    // ==================== GoogleMeetNodeHandler Tests ====================

    @Test
    fun `GoogleMeetNodeHandler - displays calendar UI for booking`() = runTest {
        val handler = GoogleMeetNodeHandler()

        every { ChatState.addAnswerVariable(any(), any()) } returns Unit

        val nodeData = mapOf(
            "type" to "google-meet-node",
            "operation" to "book",
            "timeZone" to "Europe/London",
            "answerVariable" to "meeting"
        )

        val result = handler.process(nodeData, "meet-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
    }

    // ==================== GoogleDriveNodeHandler Tests ====================

    @Test
    fun `GoogleDriveNodeHandler - records operation and proceeds`() = runTest {
        val handler = GoogleDriveNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "google-drive-node",
            "operation" to "upload"
        )

        val result = handler.process(nodeData, "drive-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "google-drive-upload"
        }) }
    }

    // ==================== GoogleDocsNodeHandler Tests ====================

    @Test
    fun `GoogleDocsNodeHandler - records operation and proceeds`() = runTest {
        val handler = GoogleDocsNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "google-docs-node",
            "operation" to "create",
            "title" to "New Document"
        )

        val result = handler.process(nodeData, "docs-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "google-docs-create"
        }) }
    }

    // ==================== SlackNodeHandler Tests ====================

    @Test
    fun `SlackNodeHandler - records message and proceeds`() = runTest {
        val handler = SlackNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "slack-node",
            "message" to "Hello Slack!",
            "channel" to "#general"
        )

        val result = handler.process(nodeData, "slack-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "slack-message" &&
            entry.text == "Hello Slack!" &&
            entry.data["channel"] == "#general"
        }) }
    }

    // ==================== DiscordNodeHandler Tests ====================

    @Test
    fun `DiscordNodeHandler - records message and proceeds`() = runTest {
        val handler = DiscordNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "discord-node",
            "message" to "Hello Discord!",
            "channel" to "bot-channel"
        )

        val result = handler.process(nodeData, "discord-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "discord-message"
        }) }
    }

    // ==================== AirtableNodeHandler Tests ====================

    @Test
    fun `AirtableNodeHandler - records operation and proceeds`() = runTest {
        val handler = AirtableNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "airtable-node",
            "operation" to "create",
            "baseId" to "base-123",
            "tableName" to "Contacts"
        )

        val result = handler.process(nodeData, "airtable-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "airtable-create" &&
            entry.data["baseId"] == "base-123"
        }) }
    }

    // ==================== HubspotNodeHandler Tests ====================

    @Test
    fun `HubspotNodeHandler - records operation and proceeds`() = runTest {
        val handler = HubspotNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "hubspot-node",
            "operation" to "createContact"
        )

        val result = handler.process(nodeData, "hubspot-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "hubspot-createContact"
        }) }
    }

    // ==================== NotionNodeHandler Tests ====================

    @Test
    fun `NotionNodeHandler - records operation and proceeds`() = runTest {
        val handler = NotionNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "notion-node",
            "operation" to "createPage",
            "databaseId" to "db-123"
        )

        val result = handler.process(nodeData, "notion-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "notion-createPage" &&
            entry.data["databaseId"] == "db-123"
        }) }
    }

    // ==================== ZohoCrmNodeHandler Tests ====================

    @Test
    fun `ZohoCrmNodeHandler - records operation and proceeds`() = runTest {
        val handler = ZohoCrmNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "zohocrm-node",
            "operation" to "create",
            "module" to "Leads"
        )

        val result = handler.process(nodeData, "zoho-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
        verify { ChatState.pushToRecord(match { entry ->
            entry.shape == "zohocrm-create" &&
            entry.data["module"] == "Leads"
        }) }
    }

    // ==================== StripeNodeHandler Tests ====================

    @Test
    fun `StripeNodeHandler - displays payment UI for createPaymentLink`() = runTest {
        val handler = StripeNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "stripe-node",
            "operation" to "createPaymentLink",
            "customAmount" to 99.99,
            "currency" to "USD",
            "description" to "Product Purchase"
        )

        val result = handler.process(nodeData, "stripe-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.Payment::class.java)

        val paymentState = uiState as NodeUIState.Payment
        assertThat(paymentState.amount).isEqualTo(99.99)
        assertThat(paymentState.currency).isEqualTo("USD")
        assertThat(paymentState.description).isEqualTo("Product Purchase")
    }

    @Test
    fun `StripeNodeHandler - displays payment UI for createCheckoutSession`() = runTest {
        val handler = StripeNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "stripe-node",
            "operation" to "createCheckoutSession",
            "customAmount" to "49.99",  // String amount
            "currency" to "EUR"
        )

        val result = handler.process(nodeData, "stripe-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState
        assertThat(uiState).isInstanceOf(NodeUIState.Payment::class.java)

        val paymentState = uiState as NodeUIState.Payment
        assertThat(paymentState.amount).isEqualTo(49.99)
        assertThat(paymentState.currency).isEqualTo("EUR")
    }

    @Test
    fun `StripeNodeHandler - proceeds for non-payment operations`() = runTest {
        val handler = StripeNodeHandler()

        val nodeData = mapOf(
            "type" to "stripe-node",
            "operation" to "getCustomer"  // Not a payment operation
        )

        val result = handler.process(nodeData, "stripe-1")

        assertThat(result).isInstanceOf(NodeResult.Proceed::class.java)
    }

    @Test
    fun `StripeNodeHandler - handles null amount`() = runTest {
        val handler = StripeNodeHandler()

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "stripe-node",
            "operation" to "createPaymentLink",
            "currency" to "USD"
            // No amount specified
        )

        val result = handler.process(nodeData, "stripe-1")

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        val uiState = (result as NodeResult.DisplayUI).uiState as NodeUIState.Payment
        assertThat(uiState.amount).isNull()
    }
}
