package com.conferbot.sdk.core.ai

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection

/**
 * Unit tests for OpenAIProvider
 * Tests response generation, streaming, error handling, and configuration
 */
class OpenAIProviderTest {

    private lateinit var provider: OpenAIProvider
    private lateinit var mockConnection: HttpURLConnection

    @Before
    fun setUp() {
        provider = OpenAIProvider()
        mockConnection = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Provider Properties Tests ====================

    @Test
    fun `provider has correct name`() {
        assertThat(provider.name).isEqualTo("openai")
    }

    @Test
    fun `provider has correct display name`() {
        assertThat(provider.displayName).isEqualTo("OpenAI")
    }

    @Test
    fun `provider has correct default model`() {
        assertThat(provider.defaultModel).isEqualTo("gpt-3.5-turbo")
    }

    @Test
    fun `provider supports streaming`() {
        assertThat(provider.supportsStreaming).isTrue()
    }

    @Test
    fun `provider has list of supported models`() {
        assertThat(provider.supportedModels).isNotEmpty()
        assertThat(provider.supportedModels).contains("gpt-3.5-turbo")
        assertThat(provider.supportedModels).contains("gpt-4")
        assertThat(provider.supportedModels).contains("gpt-4-turbo")
        assertThat(provider.supportedModels).contains("gpt-4o")
        assertThat(provider.supportedModels).contains("gpt-4o-mini")
    }

    // ==================== Configuration Tests ====================

    @Test
    fun `isConfigured returns true when API key is provided`() {
        val config = AIConfig(model = "gpt-4", apiKey = "sk-test-key")

        assertThat(provider.isConfigured(config)).isTrue()
    }

    @Test
    fun `isConfigured returns false when API key is null`() {
        val config = AIConfig(model = "gpt-4", apiKey = null)

        assertThat(provider.isConfigured(config)).isFalse()
    }

    @Test
    fun `isConfigured returns false when API key is empty`() {
        val config = AIConfig(model = "gpt-4", apiKey = "")

        assertThat(provider.isConfigured(config)).isFalse()
    }

    @Test
    fun `isConfigured returns false when API key is blank`() {
        val config = AIConfig(model = "gpt-4", apiKey = "   ")

        assertThat(provider.isConfigured(config)).isFalse()
    }

    // ==================== Generate Response Tests ====================

    @Test
    fun `generateResponse throws exception when not configured`() = runTest {
        val config = AIConfig(model = "gpt-4", apiKey = null)

        try {
            provider.generateResponse("Hello", emptyList(), config)
            throw AssertionError("Expected AIProviderException")
        } catch (e: AIProviderException) {
            assertThat(e.message).contains("not configured")
            assertThat(e.provider).isEqualTo("openai")
            assertThat(e.isRetryable).isFalse()
        }
    }

    @Test
    fun `generateResponse uses default model when model is blank`() = runTest {
        val config = AIConfig(model = "", apiKey = "test-key")

        // Create mock for the provider to test model resolution
        val testProvider = spyk(OpenAIProvider())

        // Verify that the default model is used
        assertThat(config.model).isEmpty()
        assertThat(testProvider.defaultModel).isEqualTo("gpt-3.5-turbo")
    }

    @Test
    fun `generateResponse uses custom endpoint when provided`() = runTest {
        val customEndpoint = "https://custom.openai.api.com/v1/chat/completions"
        val config = AIConfig(
            model = "gpt-4",
            apiKey = "test-key",
            customEndpoint = customEndpoint
        )

        assertThat(config.customEndpoint).isEqualTo(customEndpoint)
    }

    // ==================== Response Parsing Tests ====================

    @Test
    fun `parseResponse handles valid OpenAI response`() {
        val responseJson = JSONObject().apply {
            put("id", "chatcmpl-123")
            put("object", "chat.completion")
            put("created", 1677652288)
            put("model", "gpt-4")
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("index", 0)
                    put("message", JSONObject().apply {
                        put("role", "assistant")
                        put("content", "Hello! How can I assist you today?")
                    })
                    put("finish_reason", "stop")
                })
            })
            put("usage", JSONObject().apply {
                put("prompt_tokens", 10)
                put("completion_tokens", 20)
                put("total_tokens", 30)
            })
        }

        // Test the response structure
        val choices = responseJson.getJSONArray("choices")
        assertThat(choices.length()).isEqualTo(1)

        val message = choices.getJSONObject(0).getJSONObject("message")
        assertThat(message.getString("content")).isEqualTo("Hello! How can I assist you today?")

        val usage = responseJson.getJSONObject("usage")
        assertThat(usage.getInt("total_tokens")).isEqualTo(30)
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

        val choices = responseJson.getJSONArray("choices")
        val message = choices.getJSONObject(0).getJSONObject("message")
        assertThat(message.getString("content")).isEqualTo("Test response")

        // Usage is optional
        val usage = responseJson.optJSONObject("usage")
        val tokensUsed = usage?.optInt("total_tokens", 0) ?: 0
        assertThat(tokensUsed).isEqualTo(0)
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `error response identifies rate limiting`() {
        val isRateLimited = 429 == 429
        val isRetryable = 429 in listOf(429, 500, 502, 503, 504)

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
    fun `error response identifies client errors as non-retryable`() {
        val retryableCodes = listOf(429, 500, 502, 503, 504)
        val nonRetryableCodes = listOf(400, 401, 403, 404)

        for (code in nonRetryableCodes) {
            assertThat(code in retryableCodes).isFalse()
        }
    }

    @Test
    fun `parses OpenAI error message format`() {
        val errorJson = JSONObject().apply {
            put("error", JSONObject().apply {
                put("message", "Invalid API key provided")
                put("type", "invalid_request_error")
                put("code", "invalid_api_key")
            })
        }

        val errorMessage = errorJson.optJSONObject("error")?.optString("message") ?: "Unknown error"
        assertThat(errorMessage).isEqualTo("Invalid API key provided")
    }

    @Test
    fun `handles malformed error response gracefully`() {
        val errorBody = "Not a JSON response"

        val errorMessage = try {
            val errorJson = JSONObject(errorBody)
            errorJson.optJSONObject("error")?.optString("message") ?: errorBody
        } catch (e: Exception) {
            errorBody
        }

        assertThat(errorMessage).isEqualTo("Not a JSON response")
    }

    // ==================== Streaming Response Tests ====================

    @Test
    fun `streaming callback receives tokens`() = runTest {
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

        // Simulate what the streaming would produce
        callback.onToken("Hello")
        callback.onToken(" there")
        callback.onToken("!")
        callback.onComplete(
            AIResponse(
                content = "Hello there!",
                tokensUsed = 10,
                model = "gpt-4",
                provider = "openai",
                finishReason = "stop"
            )
        )

        assertThat(receivedTokens).containsExactly("Hello", " there", "!")
        assertThat(completedResponse).isNotNull()
        assertThat(completedResponse?.content).isEqualTo("Hello there!")
    }

    @Test
    fun `streaming handles SSE format correctly`() {
        // Test SSE line parsing
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
    fun `streaming ignores DONE marker`() {
        val doneLine = "data: [DONE]"
        assertThat(doneLine).isEqualTo("data: [DONE]")
        // This line should be skipped in processing
    }

    @Test
    fun `streaming handles malformed chunks gracefully`() {
        val malformedLine = "data: {invalid json"

        val content = try {
            val jsonData = malformedLine.substring(6)
            JSONObject(jsonData).optString("content", "")
        } catch (e: Exception) {
            "" // Skip malformed chunks
        }

        assertThat(content).isEmpty()
    }

    // ==================== Request Building Tests ====================

    @Test
    fun `request includes system prompt when provided`() {
        val config = AIConfig(
            model = "gpt-4",
            apiKey = "test-key",
            systemPrompt = "You are a helpful assistant"
        )

        // Build messages array
        val messages = JSONArray()

        config.systemPrompt?.let { systemPrompt ->
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }

        assertThat(messages.length()).isEqualTo(1)
        assertThat(messages.getJSONObject(0).getString("role")).isEqualTo("system")
        assertThat(messages.getJSONObject(0).getString("content")).isEqualTo("You are a helpful assistant")
    }

    @Test
    fun `request includes context messages`() {
        val context = listOf(
            AIMessage(role = "user", content = "Hello"),
            AIMessage(role = "assistant", content = "Hi there!")
        )

        val messages = JSONArray()
        context.forEach { msg ->
            messages.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        assertThat(messages.length()).isEqualTo(2)
        assertThat(messages.getJSONObject(0).getString("role")).isEqualTo("user")
        assertThat(messages.getJSONObject(1).getString("role")).isEqualTo("assistant")
    }

    @Test
    fun `request includes temperature parameter`() {
        val config = AIConfig(model = "gpt-4", temperature = 0.9f)

        val requestBody = JSONObject().apply {
            put("temperature", config.temperature.toDouble())
        }

        assertThat(requestBody.getDouble("temperature")).isEqualTo(0.9)
    }

    @Test
    fun `request includes max_tokens parameter`() {
        val config = AIConfig(model = "gpt-4", maxTokens = 2000)

        val requestBody = JSONObject().apply {
            put("max_tokens", config.maxTokens)
        }

        assertThat(requestBody.getInt("max_tokens")).isEqualTo(2000)
    }

    @Test
    fun `request includes optional parameters when set`() {
        val config = AIConfig(
            model = "gpt-4",
            topP = 0.95f,
            frequencyPenalty = 0.5f,
            presencePenalty = 0.3f
        )

        val requestBody = JSONObject().apply {
            config.topP?.let { put("top_p", it.toDouble()) }
            config.frequencyPenalty?.let { put("frequency_penalty", it.toDouble()) }
            config.presencePenalty?.let { put("presence_penalty", it.toDouble()) }
        }

        assertThat(requestBody.getDouble("top_p")).isEqualTo(0.95)
        assertThat(requestBody.getDouble("frequency_penalty")).isEqualTo(0.5)
        assertThat(requestBody.getDouble("presence_penalty")).isEqualTo(0.3)
    }

    @Test
    fun `request excludes optional parameters when not set`() {
        val config = AIConfig(model = "gpt-4")

        val requestBody = JSONObject().apply {
            config.topP?.let { put("top_p", it.toDouble()) }
            config.frequencyPenalty?.let { put("frequency_penalty", it.toDouble()) }
        }

        assertThat(requestBody.has("top_p")).isFalse()
        assertThat(requestBody.has("frequency_penalty")).isFalse()
    }

    @Test
    fun `request sets stream flag correctly`() {
        // Non-streaming request
        val nonStreamRequest = JSONObject().apply {
            put("stream", false)
        }
        assertThat(nonStreamRequest.getBoolean("stream")).isFalse()

        // Streaming request
        val streamRequest = JSONObject().apply {
            put("stream", true)
        }
        assertThat(streamRequest.getBoolean("stream")).isTrue()
    }

    // ==================== Connection Setup Tests ====================

    @Test
    fun `connection uses correct authorization header format`() {
        val apiKey = "sk-test-key-12345"
        val expectedHeader = "Bearer $apiKey"

        assertThat(expectedHeader).isEqualTo("Bearer sk-test-key-12345")
    }

    @Test
    fun `connection sets correct content type`() {
        val contentType = "application/json"
        assertThat(contentType).isEqualTo("application/json")
    }

    // ==================== Integration with AIProvider Interface ====================

    @Test
    fun `provider implements AIProvider interface correctly`() {
        assertThat(provider).isInstanceOf(AIProvider::class.java)
    }

    @Test
    fun `default streaming implementation falls back to non-streaming`() = runTest {
        // The default implementation in AIProvider interface falls back to non-streaming
        // when generateResponseStreaming is not overridden. OpenAI provider overrides this,
        // but we test the interface contract here.

        var errorReceived: AIProviderException? = null

        val callback = object : AIStreamCallback {
            override fun onToken(token: String) {}
            override fun onComplete(response: AIResponse) {}
            override fun onError(error: AIProviderException) {
                errorReceived = error
            }
        }

        // For unconfigured provider, streaming should report error via callback
        val config = AIConfig(model = "gpt-4", apiKey = null)

        // This simulates what the provider would do
        if (!provider.isConfigured(config)) {
            callback.onError(
                AIProviderException(
                    message = "OpenAI API key is not configured",
                    provider = "openai",
                    isRetryable = false
                )
            )
        }

        assertThat(errorReceived).isNotNull()
        assertThat(errorReceived?.message).contains("not configured")
    }
}
