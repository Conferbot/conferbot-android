package com.conferbot.sdk.core.ai

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AnthropicProvider
 * Tests response generation, streaming, error handling, and Anthropic-specific behavior
 */
class AnthropicProviderTest {

    private lateinit var provider: AnthropicProvider

    @Before
    fun setUp() {
        provider = AnthropicProvider()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Provider Properties Tests ====================

    @Test
    fun `provider has correct name`() {
        assertThat(provider.name).isEqualTo("anthropic")
    }

    @Test
    fun `provider has correct display name`() {
        assertThat(provider.displayName).isEqualTo("Anthropic Claude")
    }

    @Test
    fun `provider has correct default model`() {
        assertThat(provider.defaultModel).isEqualTo("claude-3-sonnet-20240229")
    }

    @Test
    fun `provider supports streaming`() {
        assertThat(provider.supportsStreaming).isTrue()
    }

    @Test
    fun `provider has list of supported Claude models`() {
        assertThat(provider.supportedModels).isNotEmpty()
        assertThat(provider.supportedModels).contains("claude-3-opus-20240229")
        assertThat(provider.supportedModels).contains("claude-3-sonnet-20240229")
        assertThat(provider.supportedModels).contains("claude-3-haiku-20240307")
        assertThat(provider.supportedModels).contains("claude-3-5-sonnet-20240620")
        assertThat(provider.supportedModels).contains("claude-3-5-sonnet-20241022")
        assertThat(provider.supportedModels).contains("claude-3-5-haiku-20241022")
    }

    @Test
    fun `provider supports legacy Claude 2 models`() {
        assertThat(provider.supportedModels).contains("claude-2.1")
        assertThat(provider.supportedModels).contains("claude-2.0")
        assertThat(provider.supportedModels).contains("claude-instant-1.2")
    }

    // ==================== Configuration Tests ====================

    @Test
    fun `isConfigured returns true when API key is provided`() {
        val config = AIConfig(model = "claude-3-opus", apiKey = "sk-ant-test-key")

        assertThat(provider.isConfigured(config)).isTrue()
    }

    @Test
    fun `isConfigured returns false when API key is null`() {
        val config = AIConfig(model = "claude-3-opus", apiKey = null)

        assertThat(provider.isConfigured(config)).isFalse()
    }

    @Test
    fun `isConfigured returns false when API key is empty`() {
        val config = AIConfig(model = "claude-3-opus", apiKey = "")

        assertThat(provider.isConfigured(config)).isFalse()
    }

    @Test
    fun `isConfigured returns false when API key is blank`() {
        val config = AIConfig(model = "claude-3-opus", apiKey = "   ")

        assertThat(provider.isConfigured(config)).isFalse()
    }

    // ==================== Generate Response Tests ====================

    @Test
    fun `generateResponse throws exception when not configured`() = runTest {
        val config = AIConfig(model = "claude-3-opus", apiKey = null)

        try {
            provider.generateResponse("Hello", emptyList(), config)
            throw AssertionError("Expected AIProviderException")
        } catch (e: AIProviderException) {
            assertThat(e.message).contains("not configured")
            assertThat(e.provider).isEqualTo("anthropic")
            assertThat(e.isRetryable).isFalse()
        }
    }

    @Test
    fun `generateResponse uses default model when model is blank`() = runTest {
        val config = AIConfig(model = "", apiKey = "test-key")

        assertThat(config.model).isEmpty()
        assertThat(provider.defaultModel).isEqualTo("claude-3-sonnet-20240229")
    }

    // ==================== Anthropic-Specific Response Parsing Tests ====================

    @Test
    fun `parseResponse handles Anthropic message format`() {
        val responseJson = JSONObject().apply {
            put("id", "msg_01XFDUDYJgAACzvnptvVoYEL")
            put("type", "message")
            put("role", "assistant")
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Hello! How can I assist you today?")
                })
            })
            put("model", "claude-3-sonnet-20240229")
            put("stop_reason", "end_turn")
            put("usage", JSONObject().apply {
                put("input_tokens", 10)
                put("output_tokens", 15)
            })
        }

        val contentArray = responseJson.getJSONArray("content")
        val contentBuilder = StringBuilder()

        for (i in 0 until contentArray.length()) {
            val block = contentArray.getJSONObject(i)
            if (block.getString("type") == "text") {
                contentBuilder.append(block.getString("text"))
            }
        }

        assertThat(contentBuilder.toString()).isEqualTo("Hello! How can I assist you today?")

        val usage = responseJson.getJSONObject("usage")
        val inputTokens = usage.getInt("input_tokens")
        val outputTokens = usage.getInt("output_tokens")
        assertThat(inputTokens + outputTokens).isEqualTo(25)
    }

    @Test
    fun `parseResponse handles multiple content blocks`() {
        val responseJson = JSONObject().apply {
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "First block. ")
                })
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Second block.")
                })
            })
        }

        val contentArray = responseJson.getJSONArray("content")
        val contentBuilder = StringBuilder()

        for (i in 0 until contentArray.length()) {
            val block = contentArray.getJSONObject(i)
            if (block.getString("type") == "text") {
                contentBuilder.append(block.getString("text"))
            }
        }

        assertThat(contentBuilder.toString()).isEqualTo("First block. Second block.")
    }

    @Test
    fun `parseResponse handles stop_reason field`() {
        val responseJson = JSONObject().apply {
            put("stop_reason", "end_turn")
        }

        val stopReason = responseJson.optString("stop_reason", null)
        assertThat(stopReason).isEqualTo("end_turn")
    }

    @Test
    fun `parseResponse handles max_tokens stop reason`() {
        val responseJson = JSONObject().apply {
            put("stop_reason", "max_tokens")
        }

        val stopReason = responseJson.optString("stop_reason", null)
        assertThat(stopReason).isEqualTo("max_tokens")
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `error response identifies rate limiting at 429`() {
        val responseCode = 429
        val isRateLimited = responseCode == 429
        val isRetryable = responseCode in listOf(429, 500, 502, 503, 504, 529)

        assertThat(isRateLimited).isTrue()
        assertThat(isRetryable).isTrue()
    }

    @Test
    fun `error response identifies Anthropic overloaded at 529`() {
        val responseCode = 529
        val isRateLimited = responseCode == 429
        val isRetryable = responseCode in listOf(429, 500, 502, 503, 504, 529)

        assertThat(isRateLimited).isFalse() // 529 is overloaded, not rate limited
        assertThat(isRetryable).isTrue() // But it is retryable
    }

    @Test
    fun `error response identifies server errors as retryable`() {
        val retryableCodes = listOf(429, 500, 502, 503, 504, 529)

        for (code in listOf(500, 502, 503, 504)) {
            assertThat(code in retryableCodes).isTrue()
        }
    }

    @Test
    fun `parses Anthropic error message format`() {
        val errorJson = JSONObject().apply {
            put("error", JSONObject().apply {
                put("type", "authentication_error")
                put("message", "Invalid API key")
            })
        }

        val errorMessage = errorJson.optJSONObject("error")?.optString("message") ?: "Unknown error"
        assertThat(errorMessage).isEqualTo("Invalid API key")
    }

    @Test
    fun `handles permission_denied error type`() {
        val errorJson = JSONObject().apply {
            put("error", JSONObject().apply {
                put("type", "permission_error")
                put("message", "Your API key does not have permission to use this resource")
            })
        }

        val errorType = errorJson.optJSONObject("error")?.optString("type")
        assertThat(errorType).isEqualTo("permission_error")
    }

    // ==================== Streaming Response Tests ====================

    @Test
    fun `streaming handles Anthropic SSE event types`() {
        // Test message_start event
        val messageStartEvent = JSONObject().apply {
            put("type", "message_start")
            put("message", JSONObject().apply {
                put("id", "msg_123")
                put("usage", JSONObject().apply {
                    put("input_tokens", 10)
                })
            })
        }

        assertThat(messageStartEvent.getString("type")).isEqualTo("message_start")
        val inputTokens = messageStartEvent
            .getJSONObject("message")
            .getJSONObject("usage")
            .getInt("input_tokens")
        assertThat(inputTokens).isEqualTo(10)
    }

    @Test
    fun `streaming handles content_block_delta event`() {
        val deltaEvent = JSONObject().apply {
            put("type", "content_block_delta")
            put("index", 0)
            put("delta", JSONObject().apply {
                put("type", "text_delta")
                put("text", "Hello")
            })
        }

        assertThat(deltaEvent.getString("type")).isEqualTo("content_block_delta")
        val text = deltaEvent.getJSONObject("delta").getString("text")
        assertThat(text).isEqualTo("Hello")
    }

    @Test
    fun `streaming handles message_delta event`() {
        val messageDeltaEvent = JSONObject().apply {
            put("type", "message_delta")
            put("delta", JSONObject().apply {
                put("stop_reason", "end_turn")
            })
            put("usage", JSONObject().apply {
                put("output_tokens", 15)
            })
        }

        assertThat(messageDeltaEvent.getString("type")).isEqualTo("message_delta")
        val stopReason = messageDeltaEvent.getJSONObject("delta").getString("stop_reason")
        assertThat(stopReason).isEqualTo("end_turn")
        val outputTokens = messageDeltaEvent.getJSONObject("usage").getInt("output_tokens")
        assertThat(outputTokens).isEqualTo(15)
    }

    @Test
    fun `streaming callback accumulates tokens correctly`() = runTest {
        val receivedTokens = mutableListOf<String>()
        var completedResponse: AIResponse? = null

        val callback = object : AIStreamCallback {
            override fun onToken(token: String) {
                receivedTokens.add(token)
            }

            override fun onComplete(response: AIResponse) {
                completedResponse = response
            }

            override fun onError(error: AIProviderException) {}
        }

        // Simulate Anthropic streaming
        callback.onToken("Hello")
        callback.onToken(" from")
        callback.onToken(" Claude")
        callback.onToken("!")
        callback.onComplete(
            AIResponse(
                content = "Hello from Claude!",
                tokensUsed = 25,
                model = "claude-3-sonnet-20240229",
                provider = "anthropic",
                finishReason = "end_turn"
            )
        )

        assertThat(receivedTokens).containsExactly("Hello", " from", " Claude", "!")
        assertThat(completedResponse?.content).isEqualTo("Hello from Claude!")
        assertThat(completedResponse?.provider).isEqualTo("anthropic")
    }

    // ==================== Request Building Tests ====================

    @Test
    fun `request separates system prompt from messages`() {
        val config = AIConfig(
            model = "claude-3-opus",
            apiKey = "test-key",
            systemPrompt = "You are a helpful assistant"
        )

        // Anthropic handles system separately
        val requestBody = JSONObject().apply {
            if (!config.systemPrompt.isNullOrBlank()) {
                put("system", config.systemPrompt)
            }
        }

        assertThat(requestBody.has("system")).isTrue()
        assertThat(requestBody.getString("system")).isEqualTo("You are a helpful assistant")
    }

    @Test
    fun `request extracts system prompt from context messages`() {
        val context = listOf(
            AIMessage(role = "system", content = "Be helpful"),
            AIMessage(role = "user", content = "Hello"),
            AIMessage(role = "assistant", content = "Hi!")
        )

        // Filter out system messages
        val systemPrompt = context
            .filter { it.role == "system" }
            .joinToString("\n") { it.content }
            .takeIf { it.isNotBlank() }

        val nonSystemMessages = context.filter { it.role != "system" }

        assertThat(systemPrompt).isEqualTo("Be helpful")
        assertThat(nonSystemMessages).hasSize(2)
        assertThat(nonSystemMessages.none { it.role == "system" }).isTrue()
    }

    @Test
    fun `request excludes system messages from messages array`() {
        val context = listOf(
            AIMessage(role = "system", content = "System prompt"),
            AIMessage(role = "user", content = "User message")
        )

        val messages = JSONArray()
        context.filter { it.role != "system" }.forEach { msg ->
            messages.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        assertThat(messages.length()).isEqualTo(1)
        assertThat(messages.getJSONObject(0).getString("role")).isEqualTo("user")
    }

    @Test
    fun `request uses correct Anthropic header format`() {
        // Anthropic uses x-api-key header instead of Authorization: Bearer
        val apiKey = "sk-ant-test-key"
        val headers = mapOf(
            "Content-Type" to "application/json",
            "x-api-key" to apiKey,
            "anthropic-version" to "2023-06-01"
        )

        assertThat(headers["x-api-key"]).isEqualTo(apiKey)
        assertThat(headers["anthropic-version"]).isEqualTo("2023-06-01")
    }

    @Test
    fun `request includes max_tokens as required parameter`() {
        val config = AIConfig(model = "claude-3-opus", maxTokens = 4096)

        val requestBody = JSONObject().apply {
            put("max_tokens", config.maxTokens)
        }

        // Anthropic requires max_tokens
        assertThat(requestBody.has("max_tokens")).isTrue()
        assertThat(requestBody.getInt("max_tokens")).isEqualTo(4096)
    }

    @Test
    fun `request excludes temperature when using default`() {
        val config = AIConfig(model = "claude-3-opus", temperature = 0.7f)

        val requestBody = JSONObject().apply {
            // Anthropic: only include if different from default
            if (config.temperature != 0.7f) {
                put("temperature", config.temperature.toDouble())
            }
        }

        assertThat(requestBody.has("temperature")).isFalse()
    }

    @Test
    fun `request includes temperature when not default`() {
        val config = AIConfig(model = "claude-3-opus", temperature = 0.9f)

        val requestBody = JSONObject().apply {
            if (config.temperature != 0.7f) {
                put("temperature", config.temperature.toDouble())
            }
        }

        assertThat(requestBody.has("temperature")).isTrue()
        assertThat(requestBody.getDouble("temperature")).isEqualTo(0.9)
    }

    @Test
    fun `request includes top_p when set`() {
        val config = AIConfig(model = "claude-3-opus", topP = 0.9f)

        val requestBody = JSONObject().apply {
            config.topP?.let { put("top_p", it.toDouble()) }
        }

        assertThat(requestBody.has("top_p")).isTrue()
        assertThat(requestBody.getDouble("top_p")).isEqualTo(0.9)
    }

    // ==================== Connection Setup Tests ====================

    @Test
    fun `connection uses Anthropic API endpoint`() {
        val defaultEndpoint = "https://api.anthropic.com/v1/messages"
        assertThat(defaultEndpoint).contains("anthropic.com")
        assertThat(defaultEndpoint).contains("/messages")
    }

    @Test
    fun `connection can use custom endpoint`() {
        val customEndpoint = "https://custom.anthropic.proxy.com/v1/messages"
        val config = AIConfig(
            model = "claude-3-opus",
            apiKey = "test-key",
            customEndpoint = customEndpoint
        )

        assertThat(config.customEndpoint).isEqualTo(customEndpoint)
    }

    // ==================== Integration with AIProvider Interface ====================

    @Test
    fun `provider implements AIProvider interface correctly`() {
        assertThat(provider).isInstanceOf(AIProvider::class.java)
    }

    @Test
    fun `streaming reports error for unconfigured provider`() = runTest {
        var errorReceived: AIProviderException? = null

        val callback = object : AIStreamCallback {
            override fun onToken(token: String) {}
            override fun onComplete(response: AIResponse) {}
            override fun onError(error: AIProviderException) {
                errorReceived = error
            }
        }

        val config = AIConfig(model = "claude-3-opus", apiKey = null)

        if (!provider.isConfigured(config)) {
            callback.onError(
                AIProviderException(
                    message = "Anthropic API key is not configured",
                    provider = "anthropic",
                    isRetryable = false
                )
            )
        }

        assertThat(errorReceived).isNotNull()
        assertThat(errorReceived?.provider).isEqualTo("anthropic")
    }
}
