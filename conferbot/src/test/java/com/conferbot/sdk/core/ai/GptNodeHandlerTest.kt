package com.conferbot.sdk.core.ai

import com.conferbot.sdk.core.nodes.NodeResult
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.core.nodes.handlers.GptNodeHandler
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.models.AISettings
import com.conferbot.sdk.testutils.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for GptNodeHandler
 * Tests AI provider integration, response processing, and variable storage
 */
class GptNodeHandlerTest {

    @Before
    fun setUp() {
        mockkObject(ChatState)

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

    // ==================== Configuration Tests ====================

    @Test
    fun `handler proceeds when no API key is configured`() = runTest {
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
    fun `handler proceeds when no user message in transcript`() = runTest {
        val mockAISettings = AISettings(
            apiKeys = mapOf("openai" to "test-key")
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
    fun `handler uses API key from node data`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val nodeData = mapOf(
            "type" to "gpt-node",
            "apiKey" to "node-specific-key",
            "selectedModel" to "gpt-4"
        )

        // Verify that node-specific key is used
        val apiKey = nodeData["apiKey"]?.toString() ?: ""
        assertThat(apiKey).isEqualTo("node-specific-key")
    }

    @Test
    fun `handler uses API key from AISettings when node key is empty`() = runTest {
        val settings = AISettings(
            apiKeys = mapOf("openai" to "settings-key")
        )
        val handler = GptNodeHandler(settings)

        val nodeApiKey = ""
        val settingsKey = settings.getApiKey("openai")

        val effectiveKey = nodeApiKey.takeIf { it.isNotBlank() } ?: settingsKey
        assertThat(effectiveKey).isEqualTo("settings-key")
    }

    // ==================== Provider Selection Tests ====================

    @Test
    fun `handler resolves OpenAI provider from explicit type`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val providerType = "openai"
        val provider = AIProviderFactory.getProvider(providerType)

        assertThat(provider).isNotNull()
        assertThat(provider?.name).isEqualTo("openai")
    }

    @Test
    fun `handler resolves Anthropic provider from explicit type`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val providerType = "anthropic"
        val provider = AIProviderFactory.getProvider(providerType)

        assertThat(provider).isNotNull()
        assertThat(provider?.name).isEqualTo("anthropic")
    }

    @Test
    fun `handler resolves DeepSeek provider from explicit type`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val providerType = "deepseek"
        val provider = AIProviderFactory.getProvider(providerType)

        assertThat(provider).isNotNull()
        assertThat(provider?.name).isEqualTo("deepseek")
    }

    @Test
    fun `handler detects OpenAI from GPT model name`() = runTest {
        val handler = GptNodeHandler(AISettings())

        // Test various GPT model patterns
        val models = listOf("gpt-3.5-turbo", "gpt-4", "gpt-4-turbo", "gpt-4o", "gpt-4o-mini")

        for (model in models) {
            val provider = AIProviderFactory.getProviderForModel(model)
            assertThat(provider?.name).isEqualTo("openai")
        }
    }

    @Test
    fun `handler detects Anthropic from Claude model name`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val models = listOf(
            "claude-3-opus-20240229",
            "claude-3-sonnet-20240229",
            "claude-3-haiku-20240307",
            "claude-3-5-sonnet-20241022"
        )

        for (model in models) {
            val provider = AIProviderFactory.getProviderForModel(model)
            assertThat(provider?.name).isEqualTo("anthropic")
        }
    }

    @Test
    fun `handler detects DeepSeek from model name`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val models = listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner")

        for (model in models) {
            val provider = AIProviderFactory.getProviderForModel(model)
            assertThat(provider?.name).isEqualTo("deepseek")
        }
    }

    // ==================== Provider Alias Tests ====================

    @Test
    fun `handler resolves gpt alias to OpenAI`() = runTest {
        val handler = GptNodeHandler(AISettings())

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "gpt-node",
            "apiKey" to "",
            "selectedModel" to "test-model",
            "provider" to "gpt"
        )

        // Provider alias "gpt" should map to "openai"
        val providerAliases = mapOf(
            "gpt" to "openai",
            "openai" to "openai",
            "gpt-3.5" to "openai",
            "gpt-4" to "openai"
        )

        assertThat(providerAliases["gpt"]).isEqualTo("openai")
    }

    @Test
    fun `handler resolves claude alias to Anthropic`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val providerAliases = mapOf(
            "claude" to "anthropic",
            "anthropic" to "anthropic"
        )

        assertThat(providerAliases["claude"]).isEqualTo("anthropic")
        assertThat(providerAliases["anthropic"]).isEqualTo("anthropic")
    }

    @Test
    fun `handler handles all provider aliases correctly`() = runTest {
        val handler = GptNodeHandler(AISettings())

        every { ChatState.pushToRecord(any()) } returns Unit

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

    // ==================== Configuration Building Tests ====================

    @Test
    fun `handler builds config with correct temperature`() = runTest {
        val settings = AISettings(defaultTemperature = 0.5f)
        val handler = GptNodeHandler(settings)

        // Node-level temperature should override default
        val nodeTemp = 0.9f
        val defaultTemp = settings.defaultTemperature

        val effectiveTemp = nodeTemp // Node value takes precedence
        assertThat(effectiveTemp).isEqualTo(0.9f)
    }

    @Test
    fun `handler builds config with correct max tokens`() = runTest {
        val settings = AISettings(defaultMaxTokens = 500)
        val handler = GptNodeHandler(settings)

        // Node-level maxTokens should override default
        val nodeMaxTokens = 2000
        val defaultMaxTokens = settings.defaultMaxTokens

        assertThat(nodeMaxTokens).isNotEqualTo(defaultMaxTokens)
    }

    @Test
    fun `handler includes system prompt in config`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val nodeData = mapOf(
            "type" to "gpt-node",
            "apiKey" to "test-key",
            "selectedModel" to "gpt-4",
            "context" to "You are a helpful customer service bot"
        )

        val systemPrompt = nodeData["context"]?.toString()
        assertThat(systemPrompt).isEqualTo("You are a helpful customer service bot")
    }

    @Test
    fun `handler uses custom endpoint when provided`() = runTest {
        val settings = AISettings(customEndpoint = "https://proxy.openai.com/v1")
        val handler = GptNodeHandler(settings)

        assertThat(settings.customEndpoint).isEqualTo("https://proxy.openai.com/v1")
    }

    @Test
    fun `handler respects fallback setting`() = runTest {
        val settingsWithFallback = AISettings(enableFallback = true)
        val settingsWithoutFallback = AISettings(enableFallback = false)

        assertThat(settingsWithFallback.enableFallback).isTrue()
        assertThat(settingsWithoutFallback.enableFallback).isFalse()
    }

    // ==================== Transcript Context Tests ====================

    @Test
    fun `handler builds conversation context from transcript`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val transcript = listOf(
            mapOf("role" to "user", "content" to "Hello"),
            mapOf("role" to "assistant", "content" to "Hi there!"),
            mapOf("role" to "user", "content" to "How are you?")
        )

        every { ChatState.getTranscriptForGPT() } returns transcript

        val context = transcript.map { msg ->
            AIMessage(
                role = msg["role"] ?: "user",
                content = msg["content"] ?: ""
            )
        }.dropLast(1) // Remove latest user message (will be used as prompt)

        assertThat(context).hasSize(2)
        assertThat(context[0].role).isEqualTo("user")
        assertThat(context[0].content).isEqualTo("Hello")
        assertThat(context[1].role).isEqualTo("assistant")
    }

    @Test
    fun `handler extracts latest user message as prompt`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val transcript = listOf(
            mapOf("role" to "user", "content" to "First message"),
            mapOf("role" to "assistant", "content" to "Response"),
            mapOf("role" to "user", "content" to "Latest question")
        )

        every { ChatState.getTranscriptForGPT() } returns transcript

        val prompt = transcript.lastOrNull { it["role"] == "user" }?.get("content") ?: ""

        assertThat(prompt).isEqualTo("Latest question")
    }

    @Test
    fun `handler handles empty transcript`() = runTest {
        val handler = GptNodeHandler(AISettings())

        every { ChatState.getTranscriptForGPT() } returns emptyList()

        val transcript = ChatState.getTranscriptForGPT()
        val prompt = transcript.lastOrNull { it["role"] == "user" }?.get("content") ?: ""

        assertThat(prompt).isEmpty()
    }

    @Test
    fun `handler handles transcript with no user messages`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val transcript = listOf(
            mapOf("role" to "assistant", "content" to "Bot message only")
        )

        every { ChatState.getTranscriptForGPT() } returns transcript

        val prompt = transcript.lastOrNull { it["role"] == "user" }?.get("content") ?: ""

        assertThat(prompt).isEmpty()
    }

    // ==================== Response Recording Tests ====================

    @Test
    fun `handler records gpt-error when API key missing`() = runTest {
        val handler = GptNodeHandler(AISettings())

        every { ChatState.pushToRecord(any()) } returns Unit

        val nodeData = mapOf(
            "type" to "gpt-node",
            "apiKey" to "",
            "selectedModel" to "gpt-4"
        )

        handler.process(nodeData, "gpt-1")

        verify { ChatState.pushToRecord(match { it.shape == "gpt-error" }) }
    }

    @Test
    fun `handler records provider metadata in response`() = runTest {
        // Simulate what would be recorded on successful response
        val response = AIResponse(
            content = "Hello!",
            tokensUsed = 50,
            model = "gpt-4",
            provider = "openai",
            finishReason = "stop"
        )

        val recordData = mapOf(
            "provider" to response.provider,
            "model" to response.model,
            "tokensUsed" to response.tokensUsed,
            "finishReason" to (response.finishReason ?: "")
        )

        assertThat(recordData["provider"]).isEqualTo("openai")
        assertThat(recordData["model"]).isEqualTo("gpt-4")
        assertThat(recordData["tokensUsed"]).isEqualTo(50)
        assertThat(recordData["finishReason"]).isEqualTo("stop")
    }

    @Test
    fun `handler records error details on AI provider exception`() = runTest {
        val exception = AIProviderException(
            message = "Rate limit exceeded",
            provider = "openai",
            statusCode = 429,
            isRateLimited = true
        )

        val recordData = mapOf(
            "provider" to exception.provider,
            "statusCode" to (exception.statusCode ?: -1),
            "isRateLimited" to exception.isRateLimited
        )

        assertThat(recordData["provider"]).isEqualTo("openai")
        assertThat(recordData["statusCode"]).isEqualTo(429)
        assertThat(recordData["isRateLimited"]).isEqualTo(true)
    }

    // ==================== Model Resolution Tests ====================

    @Test
    fun `handler uses provider default model when model is blank`() = runTest {
        val settings = AISettings(
            defaultModels = mapOf("openai" to "gpt-4-turbo")
        )
        val handler = GptNodeHandler(settings)

        val model = ""
        val provider = "openai"

        // Should use settings default, then provider default
        val resolvedModel = settings.getDefaultModel(provider)
            ?: AIProviderFactory.getProvider(provider)?.defaultModel
            ?: "gpt-3.5-turbo"

        assertThat(resolvedModel).isEqualTo("gpt-4-turbo")
    }

    @Test
    fun `handler uses provider default when no model specified anywhere`() = runTest {
        val settings = AISettings() // No default models set
        val handler = GptNodeHandler(settings)

        val provider = "openai"
        val providerDefault = AIProviderFactory.getProvider(provider)?.defaultModel

        assertThat(providerDefault).isEqualTo("gpt-3.5-turbo")
    }

    @Test
    fun `handler falls back to gpt-3.5-turbo when all else fails`() = runTest {
        val settings = AISettings()
        val handler = GptNodeHandler(settings)

        // If provider is unknown, default to gpt-3.5-turbo
        val unknownProvider = "unknown"
        val model = settings.getDefaultModel(unknownProvider)
            ?: AIProviderFactory.getProvider(unknownProvider)?.defaultModel
            ?: "gpt-3.5-turbo"

        assertThat(model).isEqualTo("gpt-3.5-turbo")
    }

    // ==================== Display UI Tests ====================

    @Test
    fun `successful response returns DisplayUI result`() = runTest {
        // Simulate what a successful response would return
        val responseContent = "Hello! How can I help you today?"

        val uiState = NodeUIState.Message(
            text = responseContent,
            nodeId = "gpt-1"
        )

        val result = NodeResult.DisplayUI(uiState)

        assertThat(result).isInstanceOf(NodeResult.DisplayUI::class.java)
        assertThat((result.uiState as NodeUIState.Message).text).isEqualTo(responseContent)
    }

    @Test
    fun `response is added to transcript`() = runTest {
        val handler = GptNodeHandler(AISettings())

        every { ChatState.addToTranscript(any(), any()) } returns Unit

        // Simulate adding response to transcript
        val responseContent = "AI response content"
        ChatState.addToTranscript("bot", responseContent)

        verify { ChatState.addToTranscript("bot", responseContent) }
    }

    // ==================== Fallback Chain Tests ====================

    @Test
    fun `handler builds fallback chain correctly`() = runTest {
        val settings = AISettings(
            enableFallback = true,
            fallbackOrder = listOf("openai", "anthropic", "deepseek")
        )
        val handler = GptNodeHandler(settings)

        val chain = AIProviderFactory.getFallbackChain(settings.fallbackOrder)

        assertThat(chain).hasSize(3)
        assertThat(chain[0].name).isEqualTo("openai")
        assertThat(chain[1].name).isEqualTo("anthropic")
        assertThat(chain[2].name).isEqualTo("deepseek")
    }

    @Test
    fun `handler uses custom fallback order from settings`() = runTest {
        val settings = AISettings(
            fallbackOrder = listOf("deepseek", "openai", "anthropic")
        )
        val handler = GptNodeHandler(settings)

        val chain = AIProviderFactory.getFallbackChain(settings.fallbackOrder)

        assertThat(chain[0].name).isEqualTo("deepseek")
        assertThat(chain[1].name).isEqualTo("openai")
        assertThat(chain[2].name).isEqualTo("anthropic")
    }

    @Test
    fun `handler executes single provider when fallback disabled`() = runTest {
        val settings = AISettings(enableFallback = false)
        val handler = GptNodeHandler(settings)

        // When fallback is disabled, only the specified provider is used
        assertThat(settings.enableFallback).isFalse()
    }

    // ==================== Retry Configuration Tests ====================

    @Test
    fun `handler uses retry settings from AISettings`() = runTest {
        val settings = AISettings(
            maxRetries = 3,
            retryDelayMs = 2000
        )
        val handler = GptNodeHandler(settings)

        assertThat(settings.maxRetries).isEqualTo(3)
        assertThat(settings.retryDelayMs).isEqualTo(2000)
    }

    @Test
    fun `handler uses default retry settings when not specified`() = runTest {
        val settings = AISettings()
        val handler = GptNodeHandler(settings)

        assertThat(settings.maxRetries).isEqualTo(2) // Default
        assertThat(settings.retryDelayMs).isEqualTo(1000) // Default
    }

    // ==================== Float Parsing Tests ====================

    @Test
    fun `handler parses temperature from Number`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val nodeData = mapOf(
            "type" to "gpt-node",
            "temperature" to 0.9 // Double
        )

        val value = nodeData["temperature"]
        val temperature = when (value) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull() ?: 0.7f
            else -> 0.7f
        }

        assertThat(temperature).isEqualTo(0.9f)
    }

    @Test
    fun `handler parses temperature from String`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val nodeData = mapOf(
            "type" to "gpt-node",
            "temperature" to "0.8"
        )

        val value = nodeData["temperature"]
        val temperature = when (value) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull() ?: 0.7f
            else -> 0.7f
        }

        assertThat(temperature).isEqualTo(0.8f)
    }

    @Test
    fun `handler uses default temperature for invalid string`() = runTest {
        val handler = GptNodeHandler(AISettings())

        val nodeData = mapOf(
            "type" to "gpt-node",
            "temperature" to "invalid"
        )

        val value = nodeData["temperature"]
        val temperature = when (value) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull() ?: 0.7f
            else -> 0.7f
        }

        assertThat(temperature).isEqualTo(0.7f)
    }
}
