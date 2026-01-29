package com.conferbot.sdk.models

import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * User identification model
 */
data class ConferBotUser(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val metadata: Map<String, Any>? = null
)

/**
 * AI provider settings for GPT/LLM nodes
 */
data class AISettings(
    /**
     * Default AI provider to use (openai, anthropic, deepseek)
     */
    val defaultProvider: String = "openai",

    /**
     * Whether to enable fallback to other providers when primary fails
     */
    val enableFallback: Boolean = true,

    /**
     * Custom endpoint URL for OpenAI-compatible APIs (e.g., Azure OpenAI, local LLM)
     */
    val customEndpoint: String? = null,

    /**
     * API keys for each provider (overrides node-level keys)
     */
    val apiKeys: Map<String, String> = emptyMap(),

    /**
     * Custom fallback order (provider names in order of preference)
     */
    val fallbackOrder: List<String>? = null,

    /**
     * Default model for each provider
     */
    val defaultModels: Map<String, String> = emptyMap(),

    /**
     * Maximum retries per provider before falling back
     */
    val maxRetries: Int = 2,

    /**
     * Retry delay in milliseconds
     */
    val retryDelayMs: Long = 1000,

    /**
     * Enable streaming responses if provider supports it
     */
    val enableStreaming: Boolean = false,

    /**
     * Default temperature for AI responses (0.0 - 1.0)
     */
    val defaultTemperature: Float = 0.7f,

    /**
     * Default max tokens for AI responses
     */
    val defaultMaxTokens: Int = 1000
) {
    /**
     * Get API key for a specific provider
     */
    fun getApiKey(provider: String): String? {
        return apiKeys[provider.lowercase()]
    }

    /**
     * Get default model for a provider, or the provider's default if not set
     */
    fun getDefaultModel(provider: String): String? {
        return defaultModels[provider.lowercase()]
    }

    companion object {
        /**
         * Create AISettings with a single API key for all providers
         */
        fun withUniversalKey(apiKey: String, provider: String = "openai"): AISettings {
            return AISettings(
                defaultProvider = provider,
                apiKeys = mapOf(provider to apiKey)
            )
        }
    }
}

/**
 * SDK configuration
 */
data class ConferBotConfig(
    val enableNotifications: Boolean = true,
    val enableOfflineMode: Boolean = true,
    val autoConnect: Boolean = true,
    val reconnectionAttempts: Int? = null,
    val reconnectionDelay: Int? = null,
    /**
     * AI provider settings for GPT/LLM nodes
     */
    val aiSettings: AISettings = AISettings()
)

/**
 * UI customization options
 */
data class ConferBotCustomization(
    @ColorInt val primaryColor: Int? = null,
    val fontFamily: Int? = null, // Font resource ID
    val bubbleRadius: Float? = null,
    val headerTitle: String? = null,
    val enableAvatar: Boolean? = null,
    val avatarUrl: String? = null,
    @ColorInt val botBubbleColor: Int? = null,
    @ColorInt val userBubbleColor: Int? = null
) {
    companion object {
        /**
         * Parse hex color string to ColorInt
         */
        fun parseColor(colorString: String): Int {
            return try {
                Color.parseColor(colorString)
            } catch (e: IllegalArgumentException) {
                Color.BLACK
            }
        }
    }
}
