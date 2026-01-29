package com.conferbot.sdk.core.ai

import kotlinx.coroutines.delay

/**
 * Factory for creating and managing AI providers
 * Supports fallback chains and provider selection
 */
object AIProviderFactory {

    // Registered providers
    private val providers = mutableMapOf<String, AIProvider>()

    // Default fallback chain order
    private val defaultFallbackOrder = listOf("openai", "anthropic", "deepseek")

    init {
        // Register built-in providers
        registerProvider(OpenAIProvider())
        registerProvider(AnthropicProvider())
        registerProvider(DeepSeekProvider())
    }

    /**
     * Register a custom AI provider
     */
    fun registerProvider(provider: AIProvider) {
        providers[provider.name.lowercase()] = provider
    }

    /**
     * Get a provider by type/name
     * @param type Provider name (e.g., "openai", "anthropic", "deepseek")
     * @return The requested provider or null if not found
     */
    fun getProvider(type: String): AIProvider? {
        return providers[type.lowercase()]
    }

    /**
     * Get the default provider (OpenAI)
     */
    fun getDefaultProvider(): AIProvider {
        return providers["openai"] ?: OpenAIProvider()
    }

    /**
     * Get the fallback chain of providers
     * @param preferredOrder Optional custom order of provider names
     * @return List of providers in fallback order
     */
    fun getFallbackChain(preferredOrder: List<String>? = null): List<AIProvider> {
        val order = preferredOrder ?: defaultFallbackOrder
        return order.mapNotNull { providers[it.lowercase()] }
    }

    /**
     * Get all registered providers
     */
    fun getAllProviders(): List<AIProvider> {
        return providers.values.toList()
    }

    /**
     * Get provider names
     */
    fun getProviderNames(): List<String> {
        return providers.keys.toList()
    }

    /**
     * Determine provider type from model name
     * Useful for auto-detecting provider when only model is specified
     */
    fun getProviderForModel(model: String): AIProvider? {
        val modelLower = model.lowercase()

        return when {
            modelLower.startsWith("gpt-") || modelLower.contains("gpt") -> providers["openai"]
            modelLower.startsWith("claude") -> providers["anthropic"]
            modelLower.startsWith("deepseek") -> providers["deepseek"]
            else -> {
                // Check each provider's supported models
                providers.values.find { provider ->
                    provider.supportedModels.any { it.lowercase() == modelLower }
                }
            }
        }
    }

    /**
     * Execute with fallback chain
     * Tries each provider in the chain until one succeeds
     *
     * @param prompt The prompt to send
     * @param context Conversation context
     * @param configs Map of provider name to config (each provider may have different API keys)
     * @param preferredProvider Optional preferred provider to try first
     * @param maxRetries Maximum retries per provider for retryable errors
     * @param retryDelayMs Delay between retries
     * @return AIResponse from the first successful provider
     * @throws AIProviderException if all providers fail
     */
    suspend fun executeWithFallback(
        prompt: String,
        context: List<AIMessage>,
        configs: Map<String, AIConfig>,
        preferredProvider: String? = null,
        maxRetries: Int = 2,
        retryDelayMs: Long = 1000
    ): AIResponse {
        val errors = mutableListOf<AIProviderException>()

        // Build provider chain: preferred first, then default chain
        val chain = buildList {
            preferredProvider?.let { pref ->
                providers[pref.lowercase()]?.let { add(it) }
            }
            addAll(getFallbackChain().filter { it.name != preferredProvider?.lowercase() })
        }

        if (chain.isEmpty()) {
            throw AIProviderException(
                message = "No AI providers available",
                provider = "factory",
                isRetryable = false
            )
        }

        for (provider in chain) {
            val config = configs[provider.name] ?: configs["default"]

            if (config == null) {
                errors.add(AIProviderException(
                    message = "No configuration found for ${provider.displayName}",
                    provider = provider.name,
                    isRetryable = false
                ))
                continue
            }

            if (!provider.isConfigured(config)) {
                errors.add(AIProviderException(
                    message = "${provider.displayName} is not properly configured (missing API key)",
                    provider = provider.name,
                    isRetryable = false
                ))
                continue
            }

            // Try this provider with retries
            var lastError: AIProviderException? = null
            for (attempt in 1..maxRetries) {
                try {
                    return provider.generateResponse(prompt, context, config)
                } catch (e: AIProviderException) {
                    lastError = e
                    if (!e.isRetryable || attempt == maxRetries) {
                        break
                    }
                    // Exponential backoff for rate limiting
                    val delayMultiplier = if (e.isRateLimited) attempt * 2L else 1L
                    delay(retryDelayMs * delayMultiplier)
                }
            }

            lastError?.let { errors.add(it) }
        }

        // All providers failed
        val errorMessages = errors.joinToString("; ") { "${it.provider}: ${it.message}" }
        throw AIProviderException(
            message = "All AI providers failed: $errorMessages",
            provider = "factory",
            isRetryable = false
        )
    }

    /**
     * Execute with fallback chain and streaming support
     *
     * @param prompt The prompt to send
     * @param context Conversation context
     * @param configs Map of provider name to config
     * @param callback Streaming callback
     * @param preferredProvider Optional preferred provider to try first
     * @param enableStreaming Whether to use streaming if available
     */
    suspend fun executeWithFallbackStreaming(
        prompt: String,
        context: List<AIMessage>,
        configs: Map<String, AIConfig>,
        callback: AIStreamCallback,
        preferredProvider: String? = null,
        enableStreaming: Boolean = true
    ) {
        val errors = mutableListOf<AIProviderException>()

        // Build provider chain
        val chain = buildList {
            preferredProvider?.let { pref ->
                providers[pref.lowercase()]?.let { add(it) }
            }
            addAll(getFallbackChain().filter { it.name != preferredProvider?.lowercase() })
        }

        if (chain.isEmpty()) {
            callback.onError(AIProviderException(
                message = "No AI providers available",
                provider = "factory",
                isRetryable = false
            ))
            return
        }

        for (provider in chain) {
            val config = configs[provider.name] ?: configs["default"]

            if (config == null || !provider.isConfigured(config)) {
                continue
            }

            try {
                if (enableStreaming && provider.supportsStreaming) {
                    // Use streaming wrapper to detect errors
                    var succeeded = false
                    var streamError: AIProviderException? = null

                    provider.generateResponseStreaming(prompt, context, config, object : AIStreamCallback {
                        override fun onToken(token: String) {
                            callback.onToken(token)
                        }

                        override fun onComplete(response: AIResponse) {
                            succeeded = true
                            callback.onComplete(response)
                        }

                        override fun onError(error: AIProviderException) {
                            streamError = error
                        }
                    })

                    if (succeeded) return
                    streamError?.let { errors.add(it) }
                } else {
                    // Use non-streaming
                    val response = provider.generateResponse(prompt, context, config)
                    callback.onToken(response.content)
                    callback.onComplete(response)
                    return
                }
            } catch (e: AIProviderException) {
                errors.add(e)
            }
        }

        // All providers failed
        val errorMessages = errors.joinToString("; ") { "${it.provider}: ${it.message}" }
        callback.onError(AIProviderException(
            message = "All AI providers failed: $errorMessages",
            provider = "factory",
            isRetryable = false
        ))
    }
}

/**
 * Builder for creating AI configurations
 */
class AIConfigBuilder {
    private var model: String = ""
    private var temperature: Float = 0.7f
    private var maxTokens: Int = 1000
    private var apiKey: String? = null
    private var customEndpoint: String? = null
    private var systemPrompt: String? = null
    private var topP: Float? = null
    private var frequencyPenalty: Float? = null
    private var presencePenalty: Float? = null

    fun model(model: String) = apply { this.model = model }
    fun temperature(temperature: Float) = apply { this.temperature = temperature }
    fun maxTokens(maxTokens: Int) = apply { this.maxTokens = maxTokens }
    fun apiKey(apiKey: String?) = apply { this.apiKey = apiKey }
    fun customEndpoint(endpoint: String?) = apply { this.customEndpoint = endpoint }
    fun systemPrompt(prompt: String?) = apply { this.systemPrompt = prompt }
    fun topP(topP: Float?) = apply { this.topP = topP }
    fun frequencyPenalty(penalty: Float?) = apply { this.frequencyPenalty = penalty }
    fun presencePenalty(penalty: Float?) = apply { this.presencePenalty = penalty }

    fun build() = AIConfig(
        model = model,
        temperature = temperature,
        maxTokens = maxTokens,
        apiKey = apiKey,
        customEndpoint = customEndpoint,
        systemPrompt = systemPrompt,
        topP = topP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty
    )
}

/**
 * DSL helper for building AI config
 */
fun aiConfig(block: AIConfigBuilder.() -> Unit): AIConfig {
    return AIConfigBuilder().apply(block).build()
}
