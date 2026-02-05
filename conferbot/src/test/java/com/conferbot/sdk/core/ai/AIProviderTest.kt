package com.conferbot.sdk.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for AIProvider interface and data classes
 */
class AIProviderTest {

    // ==================== AIConfig Tests ====================

    @Test
    fun `AIConfig - creates with default values`() {
        val config = AIConfig(model = "gpt-4")

        assertThat(config.model).isEqualTo("gpt-4")
        assertThat(config.temperature).isEqualTo(0.7f)
        assertThat(config.maxTokens).isEqualTo(1000)
        assertThat(config.apiKey).isNull()
        assertThat(config.customEndpoint).isNull()
        assertThat(config.systemPrompt).isNull()
        assertThat(config.topP).isNull()
        assertThat(config.frequencyPenalty).isNull()
        assertThat(config.presencePenalty).isNull()
    }

    @Test
    fun `AIConfig - creates with all parameters`() {
        val config = AIConfig(
            model = "gpt-4-turbo",
            temperature = 0.9f,
            maxTokens = 2000,
            apiKey = "test-api-key",
            customEndpoint = "https://custom.api.com/v1",
            systemPrompt = "You are a helpful assistant",
            topP = 0.95f,
            frequencyPenalty = 0.5f,
            presencePenalty = 0.3f
        )

        assertThat(config.model).isEqualTo("gpt-4-turbo")
        assertThat(config.temperature).isEqualTo(0.9f)
        assertThat(config.maxTokens).isEqualTo(2000)
        assertThat(config.apiKey).isEqualTo("test-api-key")
        assertThat(config.customEndpoint).isEqualTo("https://custom.api.com/v1")
        assertThat(config.systemPrompt).isEqualTo("You are a helpful assistant")
        assertThat(config.topP).isEqualTo(0.95f)
        assertThat(config.frequencyPenalty).isEqualTo(0.5f)
        assertThat(config.presencePenalty).isEqualTo(0.3f)
    }

    @Test
    fun `AIConfig - copy creates new instance with modified values`() {
        val original = AIConfig(
            model = "gpt-3.5-turbo",
            temperature = 0.7f,
            apiKey = "original-key"
        )

        val modified = original.copy(
            model = "gpt-4",
            apiKey = "new-key"
        )

        assertThat(modified.model).isEqualTo("gpt-4")
        assertThat(modified.apiKey).isEqualTo("new-key")
        assertThat(modified.temperature).isEqualTo(0.7f) // Unchanged
        assertThat(original.model).isEqualTo("gpt-3.5-turbo") // Original unchanged
    }

    // ==================== AIResponse Tests ====================

    @Test
    fun `AIResponse - creates with required values`() {
        val response = AIResponse(
            content = "Hello, how can I help you?",
            tokensUsed = 50,
            model = "gpt-4",
            provider = "openai"
        )

        assertThat(response.content).isEqualTo("Hello, how can I help you?")
        assertThat(response.tokensUsed).isEqualTo(50)
        assertThat(response.model).isEqualTo("gpt-4")
        assertThat(response.provider).isEqualTo("openai")
        assertThat(response.finishReason).isNull()
    }

    @Test
    fun `AIResponse - creates with finish reason`() {
        val response = AIResponse(
            content = "Complete response",
            tokensUsed = 100,
            model = "claude-3-sonnet",
            provider = "anthropic",
            finishReason = "stop"
        )

        assertThat(response.finishReason).isEqualTo("stop")
    }

    @Test
    fun `AIResponse - handles empty content`() {
        val response = AIResponse(
            content = "",
            tokensUsed = 0,
            model = "gpt-4",
            provider = "openai"
        )

        assertThat(response.content).isEmpty()
        assertThat(response.tokensUsed).isEqualTo(0)
    }

    @Test
    fun `AIResponse - handles multiline content`() {
        val multilineContent = """
            Line 1
            Line 2
            Line 3
        """.trimIndent()

        val response = AIResponse(
            content = multilineContent,
            tokensUsed = 30,
            model = "gpt-4",
            provider = "openai"
        )

        assertThat(response.content).contains("Line 1")
        assertThat(response.content).contains("Line 2")
        assertThat(response.content).contains("Line 3")
    }

    // ==================== AIMessage Tests ====================

    @Test
    fun `AIMessage - creates user message`() {
        val message = AIMessage(role = "user", content = "Hello")

        assertThat(message.role).isEqualTo("user")
        assertThat(message.content).isEqualTo("Hello")
    }

    @Test
    fun `AIMessage - creates assistant message`() {
        val message = AIMessage(role = "assistant", content = "How can I help?")

        assertThat(message.role).isEqualTo("assistant")
        assertThat(message.content).isEqualTo("How can I help?")
    }

    @Test
    fun `AIMessage - creates system message`() {
        val message = AIMessage(role = "system", content = "You are a helpful assistant")

        assertThat(message.role).isEqualTo("system")
        assertThat(message.content).isEqualTo("You are a helpful assistant")
    }

    // ==================== AIProviderException Tests ====================

    @Test
    fun `AIProviderException - creates with message and provider`() {
        val exception = AIProviderException(
            message = "API key is invalid",
            provider = "openai"
        )

        assertThat(exception.message).isEqualTo("API key is invalid")
        assertThat(exception.provider).isEqualTo("openai")
        assertThat(exception.statusCode).isNull()
        assertThat(exception.isRateLimited).isFalse()
        assertThat(exception.isRetryable).isTrue()
    }

    @Test
    fun `AIProviderException - creates with status code`() {
        val exception = AIProviderException(
            message = "Not found",
            provider = "anthropic",
            statusCode = 404,
            isRetryable = false
        )

        assertThat(exception.statusCode).isEqualTo(404)
        assertThat(exception.isRetryable).isFalse()
    }

    @Test
    fun `AIProviderException - creates rate limited exception`() {
        val exception = AIProviderException(
            message = "Rate limit exceeded",
            provider = "openai",
            statusCode = 429,
            isRateLimited = true,
            isRetryable = true
        )

        assertThat(exception.isRateLimited).isTrue()
        assertThat(exception.isRetryable).isTrue()
        assertThat(exception.statusCode).isEqualTo(429)
    }

    @Test
    fun `AIProviderException - wraps cause exception`() {
        val cause = RuntimeException("Connection failed")
        val exception = AIProviderException(
            message = "Network error",
            provider = "deepseek",
            cause = cause
        )

        assertThat(exception.cause).isEqualTo(cause)
        assertThat(exception.cause?.message).isEqualTo("Connection failed")
    }

    @Test
    fun `AIProviderException - creates non-retryable exception`() {
        val exception = AIProviderException(
            message = "Invalid API key",
            provider = "openai",
            statusCode = 401,
            isRetryable = false
        )

        assertThat(exception.isRetryable).isFalse()
    }

    // ==================== AIStreamCallback Tests ====================

    @Test
    fun `AIStreamCallback - can be implemented`() {
        val tokens = mutableListOf<String>()
        var completedResponse: AIResponse? = null
        var receivedError: AIProviderException? = null

        val callback = object : AIStreamCallback {
            override fun onToken(token: String) {
                tokens.add(token)
            }

            override fun onComplete(response: AIResponse) {
                completedResponse = response
            }

            override fun onError(error: AIProviderException) {
                receivedError = error
            }
        }

        // Simulate streaming
        callback.onToken("Hello")
        callback.onToken(" World")
        callback.onComplete(
            AIResponse(
                content = "Hello World",
                tokensUsed = 5,
                model = "gpt-4",
                provider = "openai"
            )
        )

        assertThat(tokens).containsExactly("Hello", " World")
        assertThat(completedResponse).isNotNull()
        assertThat(completedResponse?.content).isEqualTo("Hello World")
        assertThat(receivedError).isNull()
    }

    @Test
    fun `AIStreamCallback - handles error callback`() {
        var receivedError: AIProviderException? = null

        val callback = object : AIStreamCallback {
            override fun onToken(token: String) {}
            override fun onComplete(response: AIResponse) {}
            override fun onError(error: AIProviderException) {
                receivedError = error
            }
        }

        callback.onError(
            AIProviderException(
                message = "Stream interrupted",
                provider = "openai",
                isRetryable = true
            )
        )

        assertThat(receivedError).isNotNull()
        assertThat(receivedError?.message).isEqualTo("Stream interrupted")
    }
}
