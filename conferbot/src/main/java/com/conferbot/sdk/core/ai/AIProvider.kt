package com.conferbot.sdk.core.ai

/**
 * Configuration for AI provider requests
 */
data class AIConfig(
    val model: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1000,
    val apiKey: String? = null,
    val customEndpoint: String? = null,
    val systemPrompt: String? = null,
    val topP: Float? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null
)

/**
 * Response from AI provider
 */
data class AIResponse(
    val content: String,
    val tokensUsed: Int,
    val model: String,
    val provider: String,
    val finishReason: String? = null
)

/**
 * Message format for AI conversation context
 */
data class AIMessage(
    val role: String,  // "system", "user", "assistant"
    val content: String
)

/**
 * Exception thrown when AI provider fails
 */
class AIProviderException(
    message: String,
    val provider: String,
    val statusCode: Int? = null,
    val isRateLimited: Boolean = false,
    val isRetryable: Boolean = true,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Streaming response callback
 */
interface AIStreamCallback {
    fun onToken(token: String)
    fun onComplete(response: AIResponse)
    fun onError(error: AIProviderException)
}

/**
 * Base interface for all AI providers
 * Supports multiple AI services like OpenAI, Anthropic, DeepSeek, etc.
 */
interface AIProvider {
    /**
     * Provider name identifier (e.g., "openai", "anthropic", "deepseek")
     */
    val name: String

    /**
     * Display name for logging/UI purposes
     */
    val displayName: String

    /**
     * Default model for this provider
     */
    val defaultModel: String

    /**
     * List of supported models
     */
    val supportedModels: List<String>

    /**
     * Check if provider is properly configured (has API key, etc.)
     */
    fun isConfigured(config: AIConfig): Boolean

    /**
     * Generate a response from the AI provider
     *
     * @param prompt The main user prompt/question
     * @param context Previous conversation messages for context
     * @param config Configuration including model, temperature, etc.
     * @return AIResponse with generated content
     * @throws AIProviderException if generation fails
     */
    suspend fun generateResponse(
        prompt: String,
        context: List<AIMessage>,
        config: AIConfig
    ): AIResponse

    /**
     * Generate a response with streaming support
     * Falls back to non-streaming if not supported
     *
     * @param prompt The main user prompt/question
     * @param context Previous conversation messages for context
     * @param config Configuration including model, temperature, etc.
     * @param callback Callback for streaming tokens
     */
    suspend fun generateResponseStreaming(
        prompt: String,
        context: List<AIMessage>,
        config: AIConfig,
        callback: AIStreamCallback
    ) {
        // Default implementation: fall back to non-streaming
        try {
            val response = generateResponse(prompt, context, config)
            callback.onToken(response.content)
            callback.onComplete(response)
        } catch (e: AIProviderException) {
            callback.onError(e)
        }
    }

    /**
     * Whether this provider supports streaming responses
     */
    val supportsStreaming: Boolean
        get() = false
}
