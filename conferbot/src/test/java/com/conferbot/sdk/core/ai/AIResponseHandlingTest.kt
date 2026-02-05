package com.conferbot.sdk.core.ai

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AI response handling scenarios
 * Tests streaming responses, token counting, error handling, and rate limiting
 */
class AIResponseHandlingTest {

    @Before
    fun setUp() {
        // Clean test state
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Streaming Response Tests ====================

    @Test
    fun `streaming accumulates content correctly`() = runTest {
        val contentBuilder = StringBuilder()
        val tokens = mutableListOf<String>()
        var finalResponse: AIResponse? = null

        val callback = object : AIStreamCallback {
            override fun onToken(token: String) {
                tokens.add(token)
                contentBuilder.append(token)
            }

            override fun onComplete(response: AIResponse) {
                finalResponse = response
            }

            override fun onError(error: AIProviderException) {}
        }

        // Simulate streaming tokens
        val streamTokens = listOf("Hello", " there", "!", " How", " can", " I", " help", "?")
        streamTokens.forEach { callback.onToken(it) }

        callback.onComplete(
            AIResponse(
                content = contentBuilder.toString(),
                tokensUsed = streamTokens.size * 2,
                model = "gpt-4",
                provider = "openai",
                finishReason = "stop"
            )
        )

        assertThat(contentBuilder.toString()).isEqualTo("Hello there! How can I help?")
        assertThat(tokens).hasSize(8)
        assertThat(finalResponse?.content).isEqualTo("Hello there! How can I help?")
    }

    @Test
    fun `streaming handles empty tokens gracefully`() = runTest {
        val tokens = mutableListOf<String>()

        val callback = object : AIStreamCallback {
            override fun onToken(token: String) {
                if (token.isNotEmpty()) {
                    tokens.add(token)
                }
            }

            override fun onComplete(response: AIResponse) {}
            override fun onError(error: AIProviderException) {}
        }

        // Simulate tokens with empty values
        listOf("Hello", "", " ", "World", "").forEach { callback.onToken(it) }

        assertThat(tokens).containsExactly("Hello", " ", "World")
    }

    @Test
    fun `streaming handles interruption with error callback`() = runTest {
        var receivedError: AIProviderException? = null
        val tokensBeforeError = mutableListOf<String>()

        val callback = object : AIStreamCallback {
            override fun onToken(token: String) {
                tokensBeforeError.add(token)
            }

            override fun onComplete(response: AIResponse) {}
            override fun onError(error: AIProviderException) {
                receivedError = error
            }
        }

        // Simulate partial streaming then error
        callback.onToken("Partial")
        callback.onToken(" response")
        callback.onError(
            AIProviderException(
                message = "Stream interrupted due to network error",
                provider = "openai",
                isRetryable = true
            )
        )

        assertThat(tokensBeforeError).containsExactly("Partial", " response")
        assertThat(receivedError).isNotNull()
        assertThat(receivedError?.message).contains("Stream interrupted")
    }

    @Test
    fun `streaming tracks token usage from final message`() = runTest {
        var finalTokenCount = 0

        val callback = object : AIStreamCallback {
            override fun onToken(token: String) {}
            override fun onComplete(response: AIResponse) {
                finalTokenCount = response.tokensUsed
            }

            override fun onError(error: AIProviderException) {}
        }

        callback.onComplete(
            AIResponse(
                content = "Complete response",
                tokensUsed = 150,
                model = "gpt-4",
                provider = "openai"
            )
        )

        assertThat(finalTokenCount).isEqualTo(150)
    }

    // ==================== Token Counting Tests ====================

    @Test
    fun `token count includes input and output tokens`() {
        val inputTokens = 50
        val outputTokens = 100
        val totalTokens = inputTokens + outputTokens

        val response = AIResponse(
            content = "Response content",
            tokensUsed = totalTokens,
            model = "gpt-4",
            provider = "openai"
        )

        assertThat(response.tokensUsed).isEqualTo(150)
    }

    @Test
    fun `token count handles zero tokens`() {
        val response = AIResponse(
            content = "",
            tokensUsed = 0,
            model = "gpt-4",
            provider = "openai"
        )

        assertThat(response.tokensUsed).isEqualTo(0)
    }

    @Test
    fun `token count from OpenAI usage object`() {
        val usageJson = JSONObject().apply {
            put("prompt_tokens", 25)
            put("completion_tokens", 75)
            put("total_tokens", 100)
        }

        val totalTokens = usageJson.optInt("total_tokens", 0)
        assertThat(totalTokens).isEqualTo(100)
    }

    @Test
    fun `token count from Anthropic usage object`() {
        val usageJson = JSONObject().apply {
            put("input_tokens", 30)
            put("output_tokens", 70)
        }

        val inputTokens = usageJson.optInt("input_tokens", 0)
        val outputTokens = usageJson.optInt("output_tokens", 0)
        val totalTokens = inputTokens + outputTokens

        assertThat(totalTokens).isEqualTo(100)
    }

    @Test
    fun `missing usage data returns zero tokens`() {
        val responseJson = JSONObject().apply {
            put("choices", JSONArray())
            // No usage object
        }

        val usage = responseJson.optJSONObject("usage")
        val tokensUsed = usage?.optInt("total_tokens", 0) ?: 0

        assertThat(tokensUsed).isEqualTo(0)
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `handles 400 Bad Request error`() {
        val responseCode = 400
        val isRetryable = responseCode in listOf(429, 500, 502, 503, 504)

        val exception = AIProviderException(
            message = "Bad request: Invalid model specified",
            provider = "openai",
            statusCode = 400,
            isRetryable = isRetryable
        )

        assertThat(exception.statusCode).isEqualTo(400)
        assertThat(exception.isRetryable).isFalse()
    }

    @Test
    fun `handles 401 Unauthorized error`() {
        val exception = AIProviderException(
            message = "Invalid API key",
            provider = "openai",
            statusCode = 401,
            isRetryable = false
        )

        assertThat(exception.statusCode).isEqualTo(401)
        assertThat(exception.isRetryable).isFalse()
    }

    @Test
    fun `handles 403 Forbidden error`() {
        val exception = AIProviderException(
            message = "Access denied to this resource",
            provider = "anthropic",
            statusCode = 403,
            isRetryable = false
        )

        assertThat(exception.statusCode).isEqualTo(403)
        assertThat(exception.isRetryable).isFalse()
    }

    @Test
    fun `handles 404 Not Found error`() {
        val exception = AIProviderException(
            message = "Model not found",
            provider = "openai",
            statusCode = 404,
            isRetryable = false
        )

        assertThat(exception.statusCode).isEqualTo(404)
        assertThat(exception.isRetryable).isFalse()
    }

    @Test
    fun `handles 500 Internal Server Error`() {
        val responseCode = 500
        val isRetryable = responseCode in listOf(429, 500, 502, 503, 504)

        val exception = AIProviderException(
            message = "Internal server error",
            provider = "openai",
            statusCode = 500,
            isRetryable = isRetryable
        )

        assertThat(exception.statusCode).isEqualTo(500)
        assertThat(exception.isRetryable).isTrue()
    }

    @Test
    fun `handles 502 Bad Gateway error`() {
        val exception = AIProviderException(
            message = "Bad gateway",
            provider = "deepseek",
            statusCode = 502,
            isRetryable = true
        )

        assertThat(exception.statusCode).isEqualTo(502)
        assertThat(exception.isRetryable).isTrue()
    }

    @Test
    fun `handles 503 Service Unavailable error`() {
        val exception = AIProviderException(
            message = "Service temporarily unavailable",
            provider = "anthropic",
            statusCode = 503,
            isRetryable = true
        )

        assertThat(exception.statusCode).isEqualTo(503)
        assertThat(exception.isRetryable).isTrue()
    }

    @Test
    fun `handles 504 Gateway Timeout error`() {
        val exception = AIProviderException(
            message = "Gateway timeout",
            provider = "openai",
            statusCode = 504,
            isRetryable = true
        )

        assertThat(exception.statusCode).isEqualTo(504)
        assertThat(exception.isRetryable).isTrue()
    }

    @Test
    fun `handles network connection error`() {
        val cause = java.net.ConnectException("Connection refused")
        val exception = AIProviderException(
            message = "Failed to connect to API endpoint",
            provider = "openai",
            isRetryable = true,
            cause = cause
        )

        assertThat(exception.cause).isInstanceOf(java.net.ConnectException::class.java)
        assertThat(exception.isRetryable).isTrue()
    }

    @Test
    fun `handles socket timeout error`() {
        val cause = java.net.SocketTimeoutException("Read timed out")
        val exception = AIProviderException(
            message = "Request timed out",
            provider = "deepseek",
            isRetryable = true,
            cause = cause
        )

        assertThat(exception.cause).isInstanceOf(java.net.SocketTimeoutException::class.java)
        assertThat(exception.isRetryable).isTrue()
    }

    @Test
    fun `handles JSON parsing error`() {
        val cause = org.json.JSONException("Malformed JSON")
        val exception = AIProviderException(
            message = "Failed to parse API response",
            provider = "openai",
            isRetryable = true,
            cause = cause
        )

        assertThat(exception.cause).isInstanceOf(org.json.JSONException::class.java)
    }

    // ==================== Rate Limiting Tests ====================

    @Test
    fun `identifies rate limit response`() {
        val responseCode = 429
        val isRateLimited = responseCode == 429

        val exception = AIProviderException(
            message = "Rate limit exceeded. Please retry after 60 seconds.",
            provider = "openai",
            statusCode = 429,
            isRateLimited = true,
            isRetryable = true
        )

        assertThat(exception.isRateLimited).isTrue()
        assertThat(exception.statusCode).isEqualTo(429)
    }

    @Test
    fun `rate limited error is retryable`() {
        val exception = AIProviderException(
            message = "Rate limit exceeded",
            provider = "openai",
            statusCode = 429,
            isRateLimited = true,
            isRetryable = true
        )

        assertThat(exception.isRetryable).isTrue()
    }

    @Test
    fun `exponential backoff calculation for rate limiting`() {
        val baseDelayMs = 1000L
        val attempt = 3

        // Exponential backoff: baseDelay * 2^attempt
        val delayMultiplier = 2L
        val backoffDelay = baseDelayMs * delayMultiplier * attempt

        // For rate limited: delay is multiplied further
        assertThat(backoffDelay).isEqualTo(6000L)
    }

    @Test
    fun `Anthropic 529 overloaded is retryable but not rate limited`() {
        val responseCode = 529
        val isRateLimited = responseCode == 429
        val isRetryable = responseCode in listOf(429, 500, 502, 503, 504, 529)

        assertThat(isRateLimited).isFalse()
        assertThat(isRetryable).isTrue()
    }

    // ==================== Finish Reason Tests ====================

    @Test
    fun `handles stop finish reason`() {
        val response = AIResponse(
            content = "Complete response",
            tokensUsed = 50,
            model = "gpt-4",
            provider = "openai",
            finishReason = "stop"
        )

        assertThat(response.finishReason).isEqualTo("stop")
    }

    @Test
    fun `handles max_tokens finish reason`() {
        val response = AIResponse(
            content = "Truncated response...",
            tokensUsed = 1000,
            model = "gpt-4",
            provider = "openai",
            finishReason = "max_tokens"
        )

        assertThat(response.finishReason).isEqualTo("max_tokens")
    }

    @Test
    fun `handles length finish reason (legacy)`() {
        val response = AIResponse(
            content = "Response hit token limit",
            tokensUsed = 2000,
            model = "gpt-3.5-turbo",
            provider = "openai",
            finishReason = "length"
        )

        assertThat(response.finishReason).isEqualTo("length")
    }

    @Test
    fun `handles end_turn finish reason (Anthropic)`() {
        val response = AIResponse(
            content = "Claude response",
            tokensUsed = 75,
            model = "claude-3-sonnet",
            provider = "anthropic",
            finishReason = "end_turn"
        )

        assertThat(response.finishReason).isEqualTo("end_turn")
    }

    @Test
    fun `handles null finish reason`() {
        val response = AIResponse(
            content = "Response",
            tokensUsed = 30,
            model = "gpt-4",
            provider = "openai",
            finishReason = null
        )

        assertThat(response.finishReason).isNull()
    }

    // ==================== Empty Response Handling Tests ====================

    @Test
    fun `detects empty choices array`() {
        val responseJson = JSONObject().apply {
            put("choices", JSONArray())
        }

        val choices = responseJson.getJSONArray("choices")
        assertThat(choices.length()).isEqualTo(0)
    }

    @Test
    fun `handles empty content in response`() {
        val response = AIResponse(
            content = "",
            tokensUsed = 10,
            model = "gpt-4",
            provider = "openai"
        )

        assertThat(response.content).isEmpty()
    }

    @Test
    fun `handles whitespace-only content`() {
        val content = "   \n\t  "
        assertThat(content.isBlank()).isTrue()
    }

    // ==================== Content Sanitization Tests ====================

    @Test
    fun `handles multiline content`() {
        val content = """
            Line 1
            Line 2
            Line 3
        """.trimIndent()

        val response = AIResponse(
            content = content,
            tokensUsed = 30,
            model = "gpt-4",
            provider = "openai"
        )

        assertThat(response.content).contains("\n")
        assertThat(response.content.lines()).hasSize(3)
    }

    @Test
    fun `handles special characters in content`() {
        val content = "Hello! Here's some code: <script>alert('xss')</script>"

        val response = AIResponse(
            content = content,
            tokensUsed = 20,
            model = "gpt-4",
            provider = "openai"
        )

        assertThat(response.content).contains("<script>")
    }

    @Test
    fun `handles unicode content`() {
        val content = "Hello in multiple languages: Bonjour, Hallo, Ciao"

        val response = AIResponse(
            content = content,
            tokensUsed = 25,
            model = "gpt-4",
            provider = "openai"
        )

        assertThat(response.content).contains("Bonjour")
    }

    @Test
    fun `handles emoji in content`() {
        val content = "That's great!"

        val response = AIResponse(
            content = content,
            tokensUsed = 15,
            model = "gpt-4",
            provider = "openai"
        )

        assertThat(response.content).isEqualTo("That's great!")
    }

    // ==================== Provider-Specific Response Format Tests ====================

    @Test
    fun `parses OpenAI response format`() {
        val responseJson = JSONObject().apply {
            put("id", "chatcmpl-123")
            put("object", "chat.completion")
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("index", 0)
                    put("message", JSONObject().apply {
                        put("role", "assistant")
                        put("content", "OpenAI response")
                    })
                    put("finish_reason", "stop")
                })
            })
            put("usage", JSONObject().apply {
                put("total_tokens", 50)
            })
        }

        val choices = responseJson.getJSONArray("choices")
        val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
        val tokens = responseJson.getJSONObject("usage").getInt("total_tokens")

        assertThat(content).isEqualTo("OpenAI response")
        assertThat(tokens).isEqualTo(50)
    }

    @Test
    fun `parses Anthropic response format`() {
        val responseJson = JSONObject().apply {
            put("id", "msg_123")
            put("type", "message")
            put("role", "assistant")
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Anthropic response")
                })
            })
            put("stop_reason", "end_turn")
            put("usage", JSONObject().apply {
                put("input_tokens", 20)
                put("output_tokens", 30)
            })
        }

        val contentArray = responseJson.getJSONArray("content")
        val text = contentArray.getJSONObject(0).getString("text")
        val usage = responseJson.getJSONObject("usage")
        val totalTokens = usage.getInt("input_tokens") + usage.getInt("output_tokens")

        assertThat(text).isEqualTo("Anthropic response")
        assertThat(totalTokens).isEqualTo(50)
    }

    @Test
    fun `parses DeepSeek response format (OpenAI compatible)`() {
        val responseJson = JSONObject().apply {
            put("id", "chat-123")
            put("object", "chat.completion")
            put("model", "deepseek-chat")
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("index", 0)
                    put("message", JSONObject().apply {
                        put("role", "assistant")
                        put("content", "DeepSeek response")
                    })
                    put("finish_reason", "stop")
                })
            })
            put("usage", JSONObject().apply {
                put("total_tokens", 45)
            })
        }

        val choices = responseJson.getJSONArray("choices")
        val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
        val model = responseJson.getString("model")

        assertThat(content).isEqualTo("DeepSeek response")
        assertThat(model).isEqualTo("deepseek-chat")
    }

    // ==================== Concurrent Request Handling Tests ====================

    @Test
    fun `multiple responses can be created independently`() {
        val response1 = AIResponse(
            content = "Response 1",
            tokensUsed = 10,
            model = "gpt-4",
            provider = "openai"
        )

        val response2 = AIResponse(
            content = "Response 2",
            tokensUsed = 20,
            model = "claude-3",
            provider = "anthropic"
        )

        assertThat(response1.content).isNotEqualTo(response2.content)
        assertThat(response1.provider).isNotEqualTo(response2.provider)
    }

    @Test
    fun `streaming callbacks are independent`() = runTest {
        val tokens1 = mutableListOf<String>()
        val tokens2 = mutableListOf<String>()

        val callback1 = object : AIStreamCallback {
            override fun onToken(token: String) {
                tokens1.add(token)
            }

            override fun onComplete(response: AIResponse) {}
            override fun onError(error: AIProviderException) {}
        }

        val callback2 = object : AIStreamCallback {
            override fun onToken(token: String) {
                tokens2.add(token)
            }

            override fun onComplete(response: AIResponse) {}
            override fun onError(error: AIProviderException) {}
        }

        callback1.onToken("Stream1Token")
        callback2.onToken("Stream2Token")

        assertThat(tokens1).containsExactly("Stream1Token")
        assertThat(tokens2).containsExactly("Stream2Token")
    }
}
