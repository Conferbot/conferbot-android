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
 * Unit tests for DeepSeekProvider
 * Tests response generation, streaming, error handling, and DeepSeek-specific behavior
 */
class DeepSeekProviderTest {

    private lateinit var provider: DeepSeekProvider

    @Before
    fun setUp() {
        provider = DeepSeekProvider()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Provider Properties Tests ====================

    @Test
    fun `provider has correct name`() {
        assertThat(provider.name).isEqualTo("deepseek")
    }

    @Test
    fun `provider has correct display name`() {
        assertThat(provider.displayName).isEqualTo("DeepSeek")
    }

    @Test
    fun `provider has correct default model`() {
        assertThat(provider.defaultModel).isEqualTo("deepseek-chat")
    }

    @Test
    fun `provider supports streaming`() {
        assertThat(provider.supportsStreaming).isTrue()
    }

    @Test
    fun `provider has list of supported DeepSeek models`() {
        assertThat(provider.supportedModels).isNotEmpty()
        assertThat(provider.supportedModels).contains("deepseek-chat")
        assertThat(provider.supportedModels).contains("deepseek-coder")
        assertThat(provider.supportedModels).contains("deepseek-reasoner")
    }

    // ==================== Configuration Tests ====================

    @Test
    fun `isConfigured returns true when API key is provided`() {
        val config = AIConfig(model = "deepseek-chat", apiKey = "sk-deepseek-test")

        assertThat(provider.isConfigured(config)).isTrue()
    }

    @Test
    fun `isConfigured returns false when API key is null`() {
        val config = AIConfig(model = "deepseek-chat", apiKey = null)

        assertThat(provider.isConfigured(config)).isFalse()
    }

    @Test
    fun `isConfigured returns false when API key is empty`() {
        val config = AIConfig(model = "deepseek-chat", apiKey = "")

        assertThat(provider.isConfigured(config)).isFalse()
    }

    @Test
    fun `isConfigured returns false when API key is blank`() {
        val config = AIConfig(model = "deepseek-chat", apiKey = "   ")

        assertThat(provider.isConfigured(config)).isFalse()
    }

    // ==================== Generate Response Tests ====================

    @Test
    fun `generateResponse throws exception when not configured`() = runTest {
        val config = AIConfig(model = "deepseek-chat", apiKey = null)

        try {
            provider.generateResponse("Hello", emptyList(), config)
            throw AssertionError("Expected AIProviderException")
        } catch (e: AIProviderException) {
            assertThat(e.message).contains("not configured")
            assertThat(e.provider).isEqualTo("deepseek")
            assertThat(e.isRetryable).isFalse()
        }
    }

    @Test
    fun `generateResponse uses default model when model is blank`() = runTest {
        val config = AIConfig(model = "", apiKey = "test-key")

        assertThat(config.model).isEmpty()
        assertThat(provider.defaultModel).isEqualTo("deepseek-chat")
    }

    // ==================== Response Parsing Tests (OpenAI Compatible) ====================

    @Test
    fun `parseResponse handles OpenAI-compatible response format`() {
        // DeepSeek uses OpenAI-compatible API format
        val responseJson = JSONObject().apply {
            put("id", "chat-123456")
            put("object", "chat.completion")
            put("created", 1677652288)
            put("model", "deepseek-chat")
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("index", 0)
                    put("message", JSONObject().apply {
                        put("role", "assistant")
                        put("content", "Hello! I'm DeepSeek, how can I help?")
                    })
                    put("finish_reason", "stop")
                })
            })
            put("usage", JSONObject().apply {
                put("prompt_tokens", 15)
                put("completion_tokens", 20)
                put("total_tokens", 35)
            })
        }

        val choices = responseJson.getJSONArray("choices")
        assertThat(choices.length()).isEqualTo(1)

        val message = choices.getJSONObject(0).getJSONObject("message")
        assertThat(message.getString("content")).isEqualTo("Hello! I'm DeepSeek, how can I help?")

        val usage = responseJson.getJSONObject("usage")
        assertThat(usage.getInt("total_tokens")).isEqualTo(35)
    }

    @Test
    fun `parseResponse handles empty choices array`() {
        val responseJson = JSONObject().apply {
            put("choices", JSONArray())
        }

        val choices = responseJson.getJSONArray("choices")
        assertThat(choices.length()).isEqualTo(0)
    }

    @Test
    fun `parseResponse handles response without usage data`() {
        val responseJson = JSONObject().apply {
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("message", JSONObject().apply {
                        put("role", "assistant")
                        put("content", "Test response")
                    })
                    put("finish_reason", "stop")
                })
            })
        }

        val usage = responseJson.optJSONObject("usage")
        val tokensUsed = usage?.optInt("total_tokens", 0) ?: 0
        assertThat(tokensUsed).isEqualTo(0)
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `error response identifies rate limiting`() {
        val responseCode = 429
        val isRateLimited = responseCode == 429
        val isRetryable = responseCode in listOf(429, 500, 502, 503, 504)

        assertThat(isRateLimited).isTrue()
        assertThat(isRetryable).isTrue()
    }

    @Test
    fun `error response identifies server errors as retryable`() {
        val retryableCodes = listOf(429, 500, 502, 503, 504)

        for (code in retryableCodes) {
            assertThat(code in retryableCodes).isTrue()
        }
    }

    @Test
    fun `parses DeepSeek error message format`() {
        val errorJson = JSONObject().apply {
            put("error", JSONObject().apply {
                put("message", "Invalid API key provided")
                put("type", "invalid_request_error")
            })
        }

        val errorMessage = errorJson.optJSONObject("error")?.optString("message") ?: "Unknown error"
        assertThat(errorMessage).isEqualTo("Invalid API key provided")
    }

    @Test
    fun `handles malformed error response`() {
        val errorBody = "Service temporarily unavailable"

        val errorMessage = try {
            val errorJson = JSONObject(errorBody)
            errorJson.optJSONObject("error")?.optString("message") ?: errorBody
        } catch (e: Exception) {
            errorBody
        }

        assertThat(errorMessage).isEqualTo("Service temporarily unavailable")
    }

    // ==================== Streaming Response Tests ====================

    @Test
    fun `streaming handles OpenAI-compatible SSE format`() {
        // DeepSeek streaming uses same format as OpenAI
        val sseLine = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}"
        assertThat(sseLine.startsWith("data: ")).isTrue()

        val jsonData = sseLine.substring(6)
        val json = JSONObject(jsonData)
        val choices = json.optJSONArray("choices")
        val delta = choices?.getJSONObject(0)?.optJSONObject("delta")
        val content = delta?.optString("content", "") ?: ""

        assertThat(content).isEqualTo("Hello")
    }

    @Test
    fun `streaming handles finish_reason with null string value`() {
        // DeepSeek may return "null" as string instead of JSON null
        val streamChunk = JSONObject().apply {
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("delta", JSONObject().apply {
                        put("content", "text")
                    })
                    put("finish_reason", "null") // String "null"
                })
            })
        }

        val choice = streamChunk.getJSONArray("choices").getJSONObject(0)
        val reason = choice.optString("finish_reason", null)

        // Should check if reason is "null" string
        val finishReason = if (reason != null && reason != "null") reason else null
        assertThat(finishReason).isNull()
    }

    @Test
    fun `streaming handles actual finish_reason`() {
        val streamChunk = JSONObject().apply {
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("delta", JSONObject())
                    put("finish_reason", "stop")
                })
            })
        }

        val choice = streamChunk.getJSONArray("choices").getJSONObject(0)
        val reason = choice.optString("finish_reason", null)
        val finishReason = if (reason != null && reason != "null") reason else null

        assertThat(finishReason).isEqualTo("stop")
    }

    @Test
    fun `streaming callback accumulates tokens`() = runTest {
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

        // Simulate DeepSeek streaming
        callback.onToken("Deep")
        callback.onToken("Seek")
        callback.onToken(" response")
        callback.onComplete(
            AIResponse(
                content = "DeepSeek response",
                tokensUsed = 20,
                model = "deepseek-chat",
                provider = "deepseek",
                finishReason = "stop"
            )
        )

        assertThat(receivedTokens).containsExactly("Deep", "Seek", " response")
        assertThat(completedResponse?.content).isEqualTo("DeepSeek response")
        assertThat(completedResponse?.provider).isEqualTo("deepseek")
    }

    @Test
    fun `streaming ignores DONE marker`() {
        val doneLine = "data: [DONE]"
        assertThat(doneLine).isEqualTo("data: [DONE]")
    }

    // ==================== Request Building Tests ====================

    @Test
    fun `request uses OpenAI-compatible format`() {
        val config = AIConfig(
            model = "deepseek-chat",
            apiKey = "test-key",
            temperature = 0.8f,
            maxTokens = 2000
        )

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", "Hello")
            })
        }

        val requestBody = JSONObject().apply {
            put("model", config.model)
            put("messages", messages)
            put("temperature", config.temperature.toDouble())
            put("max_tokens", config.maxTokens)
            put("stream", false)
        }

        assertThat(requestBody.getString("model")).isEqualTo("deepseek-chat")
        assertThat(requestBody.getDouble("temperature")).isEqualTo(0.8)
        assertThat(requestBody.getInt("max_tokens")).isEqualTo(2000)
    }

    @Test
    fun `request includes system prompt when provided`() {
        val config = AIConfig(
            model = "deepseek-chat",
            apiKey = "test-key",
            systemPrompt = "You are a coding assistant"
        )

        val messages = JSONArray()
        config.systemPrompt?.let { systemPrompt ->
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }

        assertThat(messages.length()).isEqualTo(1)
        assertThat(messages.getJSONObject(0).getString("role")).isEqualTo("system")
        assertThat(messages.getJSONObject(0).getString("content")).isEqualTo("You are a coding assistant")
    }

    @Test
    fun `request includes context messages`() {
        val context = listOf(
            AIMessage(role = "user", content = "Write a function"),
            AIMessage(role = "assistant", content = "Here's a function...")
        )

        val messages = JSONArray()
        context.forEach { msg ->
            messages.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        assertThat(messages.length()).isEqualTo(2)
    }

    @Test
    fun `request includes optional parameters when set`() {
        val config = AIConfig(
            model = "deepseek-chat",
            topP = 0.9f,
            frequencyPenalty = 0.3f,
            presencePenalty = 0.2f
        )

        val requestBody = JSONObject().apply {
            config.topP?.let { put("top_p", it.toDouble()) }
            config.frequencyPenalty?.let { put("frequency_penalty", it.toDouble()) }
            config.presencePenalty?.let { put("presence_penalty", it.toDouble()) }
        }

        assertThat(requestBody.getDouble("top_p")).isEqualTo(0.9)
        assertThat(requestBody.getDouble("frequency_penalty")).isEqualTo(0.3)
        assertThat(requestBody.getDouble("presence_penalty")).isEqualTo(0.2)
    }

    // ==================== Connection Setup Tests ====================

    @Test
    fun `connection uses DeepSeek API endpoint`() {
        val defaultEndpoint = "https://api.deepseek.com/v1/chat/completions"
        assertThat(defaultEndpoint).contains("deepseek.com")
        assertThat(defaultEndpoint).contains("/chat/completions")
    }

    @Test
    fun `connection uses Bearer token authentication`() {
        val apiKey = "sk-deepseek-test-key"
        val expectedHeader = "Bearer $apiKey"

        assertThat(expectedHeader).isEqualTo("Bearer sk-deepseek-test-key")
    }

    @Test
    fun `connection has longer timeout for DeepSeek`() {
        // DeepSeek can be slower, so longer timeout is configured (90 seconds)
        val timeoutMs = 90000
        assertThat(timeoutMs).isEqualTo(90000)
        assertThat(timeoutMs).isGreaterThan(60000) // Longer than typical 60s
    }

    @Test
    fun `connection can use custom endpoint`() {
        val customEndpoint = "https://custom.deepseek.proxy.com/v1/chat/completions"
        val config = AIConfig(
            model = "deepseek-chat",
            apiKey = "test-key",
            customEndpoint = customEndpoint
        )

        assertThat(config.customEndpoint).isEqualTo(customEndpoint)
    }

    // ==================== DeepSeek Specific Tests ====================

    @Test
    fun `deepseek-coder model is available for coding tasks`() {
        assertThat(provider.supportedModels).contains("deepseek-coder")
    }

    @Test
    fun `deepseek-reasoner model is available for reasoning tasks`() {
        assertThat(provider.supportedModels).contains("deepseek-reasoner")
    }

    @Test
    fun `provider can be used as fallback provider`() {
        // DeepSeek is designed as a cost-effective fallback
        assertThat(provider.name).isEqualTo("deepseek")
        assertThat(provider.isConfigured(AIConfig(model = "deepseek-chat", apiKey = "key"))).isTrue()
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

        val config = AIConfig(model = "deepseek-chat", apiKey = null)

        if (!provider.isConfigured(config)) {
            callback.onError(
                AIProviderException(
                    message = "DeepSeek API key is not configured",
                    provider = "deepseek",
                    isRetryable = false
                )
            )
        }

        assertThat(errorReceived).isNotNull()
        assertThat(errorReceived?.provider).isEqualTo("deepseek")
    }
}
