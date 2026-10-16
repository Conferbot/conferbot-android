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
    val metadata: Map<String, Any>? = null,
    /**
     * Identity verification: HMAC-SHA256 of the user's id (or email when there
     * is no id) with your workspace's identity secret, hex, made on your
     * server. With it the chat is filed under the verified user.
     */
    val hash: String? = null
) {
    /** The user as the web widget's visitorContext: the app's own data, then id, name, email, phone, hash. */
    fun toVisitorContext(): Map<String, Any?> = buildMap {
        metadata?.let { putAll(it) }
        put("id", id)
        name?.takeIf { it.isNotBlank() }?.let { put("name", it) }
        email?.takeIf { it.isNotBlank() }?.let { put("email", it) }
        phone?.takeIf { it.isNotBlank() }?.let { put("phone", it) }
        hash?.takeIf { it.isNotBlank() }?.let { put("hash", it) }
    }
}

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
    val aiSettings: AISettings = AISettings(),
    /**
     * The language the SDK draws its own words in: "en", "nl", "fr", "de",
     * "es", or "auto" (the phone's languages). Null keeps the owner's
     * `widgetLanguage` setting; a value here wins over it, as the web's
     * host-page customizations do.
     */
    val widgetLanguage: String? = null,
    /**
     * The owner's post-chat redirect (Customize > Conversations > Post-chat
     * redirect), asked first once a conversation ended and its rating is
     * done: return true when the app handled the URL (or wants none), and
     * the SDK shows no countdown. Anything else: "Taking you to example.com
     * in 10s" with Cancel, then the system browser.
     */
    val onPostChatRedirect: ((url: String) -> Boolean)? = null,
    /** Never follow the owner's post-chat redirect. */
    val disablePostChatRedirect: Boolean = false
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
