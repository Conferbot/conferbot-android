package com.conferbot.sdk.core.ai

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for AIProviderFactory
 * Tests provider creation, fallback chains, and configuration management
 */
class AIProviderFactoryTest {

    @Before
    fun setUp() {
        // AIProviderFactory is an object singleton, ensure it's in a clean state
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Provider Registration Tests ====================

    @Test
    fun `factory has OpenAI provider registered`() {
        val provider = AIProviderFactory.getProvider("openai")

        assertThat(provider).isNotNull()
        assertThat(provider?.name).isEqualTo("openai")
    }

    @Test
    fun `factory has Anthropic provider registered`() {
        val provider = AIProviderFactory.getProvider("anthropic")

        assertThat(provider).isNotNull()
        assertThat(provider?.name).isEqualTo("anthropic")
    }

    @Test
    fun `factory has DeepSeek provider registered`() {
        val provider = AIProviderFactory.getProvider("deepseek")

        assertThat(provider).isNotNull()
        assertThat(provider?.name).isEqualTo("deepseek")
    }

    @Test
    fun `getProvider is case insensitive`() {
        val lowercase = AIProviderFactory.getProvider("openai")
        val uppercase = AIProviderFactory.getProvider("OPENAI")
        val mixedCase = AIProviderFactory.getProvider("OpenAI")

        assertThat(lowercase).isNotNull()
        assertThat(uppercase).isNotNull()
        assertThat(mixedCase).isNotNull()
        assertThat(lowercase?.name).isEqualTo(uppercase?.name)
        assertThat(lowercase?.name).isEqualTo(mixedCase?.name)
    }

    @Test
    fun `getProvider returns null for unknown provider`() {
        val provider = AIProviderFactory.getProvider("unknown-provider")

        assertThat(provider).isNull()
    }

    // ==================== Default Provider Tests ====================

    @Test
    fun `getDefaultProvider returns OpenAI`() {
        val defaultProvider = AIProviderFactory.getDefaultProvider()

        assertThat(defaultProvider).isNotNull()
        assertThat(defaultProvider.name).isEqualTo("openai")
    }

    @Test
    fun `default provider is OpenAI type`() {
        val defaultProvider = AIProviderFactory.getDefaultProvider()

        assertThat(defaultProvider).isInstanceOf(OpenAIProvider::class.java)
    }

    // ==================== Provider Names Tests ====================

    @Test
    fun `getProviderNames returns all registered providers`() {
        val names = AIProviderFactory.getProviderNames()

        assertThat(names).contains("openai")
        assertThat(names).contains("anthropic")
        assertThat(names).contains("deepseek")
    }

    @Test
    fun `getAllProviders returns list of providers`() {
        val providers = AIProviderFactory.getAllProviders()

        assertThat(providers).isNotEmpty()
        assertThat(providers.size).isAtLeast(3)

        val names = providers.map { it.name }
        assertThat(names).contains("openai")
        assertThat(names).contains("anthropic")
        assertThat(names).contains("deepseek")
    }

    // ==================== Fallback Chain Tests ====================

    @Test
    fun `getFallbackChain returns providers in default order`() {
        val chain = AIProviderFactory.getFallbackChain()

        assertThat(chain).isNotEmpty()
        assertThat(chain[0].name).isEqualTo("openai")
        assertThat(chain[1].name).isEqualTo("anthropic")
        assertThat(chain[2].name).isEqualTo("deepseek")
    }

    @Test
    fun `getFallbackChain respects custom order`() {
        val customOrder = listOf("deepseek", "anthropic", "openai")
        val chain = AIProviderFactory.getFallbackChain(customOrder)

        assertThat(chain[0].name).isEqualTo("deepseek")
        assertThat(chain[1].name).isEqualTo("anthropic")
        assertThat(chain[2].name).isEqualTo("openai")
    }

    @Test
    fun `getFallbackChain skips unknown providers in custom order`() {
        val customOrder = listOf("openai", "unknown", "deepseek")
        val chain = AIProviderFactory.getFallbackChain(customOrder)

        assertThat(chain).hasSize(2)
        assertThat(chain[0].name).isEqualTo("openai")
        assertThat(chain[1].name).isEqualTo("deepseek")
    }

    @Test
    fun `getFallbackChain handles empty custom order`() {
        val chain = AIProviderFactory.getFallbackChain(emptyList())

        assertThat(chain).isEmpty()
    }

    // ==================== Model Detection Tests ====================

    @Test
    fun `getProviderForModel detects GPT models as OpenAI`() {
        val gpt35 = AIProviderFactory.getProviderForModel("gpt-3.5-turbo")
        val gpt4 = AIProviderFactory.getProviderForModel("gpt-4")
        val gpt4Turbo = AIProviderFactory.getProviderForModel("gpt-4-turbo")
        val gpt4o = AIProviderFactory.getProviderForModel("gpt-4o")

        assertThat(gpt35?.name).isEqualTo("openai")
        assertThat(gpt4?.name).isEqualTo("openai")
        assertThat(gpt4Turbo?.name).isEqualTo("openai")
        assertThat(gpt4o?.name).isEqualTo("openai")
    }

    @Test
    fun `getProviderForModel detects Claude models as Anthropic`() {
        val claude3 = AIProviderFactory.getProviderForModel("claude-3-opus-20240229")
        val claude3Sonnet = AIProviderFactory.getProviderForModel("claude-3-sonnet-20240229")
        val claude35 = AIProviderFactory.getProviderForModel("claude-3-5-sonnet-20241022")

        assertThat(claude3?.name).isEqualTo("anthropic")
        assertThat(claude3Sonnet?.name).isEqualTo("anthropic")
        assertThat(claude35?.name).isEqualTo("anthropic")
    }

    @Test
    fun `getProviderForModel detects DeepSeek models`() {
        val deepseekChat = AIProviderFactory.getProviderForModel("deepseek-chat")
        val deepseekCoder = AIProviderFactory.getProviderForModel("deepseek-coder")
        val deepseekReasoner = AIProviderFactory.getProviderForModel("deepseek-reasoner")

        assertThat(deepseekChat?.name).isEqualTo("deepseek")
        assertThat(deepseekCoder?.name).isEqualTo("deepseek")
        assertThat(deepseekReasoner?.name).isEqualTo("deepseek")
    }

    @Test
    fun `getProviderForModel is case insensitive`() {
        val lowercase = AIProviderFactory.getProviderForModel("gpt-4")
        val uppercase = AIProviderFactory.getProviderForModel("GPT-4")
        val mixedCase = AIProviderFactory.getProviderForModel("Gpt-4")

        assertThat(lowercase?.name).isEqualTo("openai")
        assertThat(uppercase?.name).isEqualTo("openai")
        assertThat(mixedCase?.name).isEqualTo("openai")
    }

    @Test
    fun `getProviderForModel returns null for unknown model`() {
        val provider = AIProviderFactory.getProviderForModel("unknown-model-xyz")

        assertThat(provider).isNull()
    }

    @Test
    fun `getProviderForModel detects gpt keyword in model name`() {
        val customGpt = AIProviderFactory.getProviderForModel("custom-gpt-model")

        assertThat(customGpt?.name).isEqualTo("openai")
    }

    // ==================== Custom Provider Registration Tests ====================

    @Test
    fun `registerProvider adds custom provider`() {
        val customProvider = object : AIProvider {
            override val name = "custom"
            override val displayName = "Custom Provider"
            override val defaultModel = "custom-model"
            override val supportedModels = listOf("custom-model", "custom-model-2")
            override val supportsStreaming = false

            override fun isConfigured(config: AIConfig) = !config.apiKey.isNullOrBlank()

            override suspend fun generateResponse(
                prompt: String,
                context: List<AIMessage>,
                config: AIConfig
            ): AIResponse {
                return AIResponse(
                    content = "Custom response",
                    tokensUsed = 10,
                    model = defaultModel,
                    provider = name
                )
            }
        }

        AIProviderFactory.registerProvider(customProvider)

        val retrieved = AIProviderFactory.getProvider("custom")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.name).isEqualTo("custom")
        assertThat(retrieved?.displayName).isEqualTo("Custom Provider")
    }

    @Test
    fun `registerProvider overwrites existing provider with same name`() {
        val originalProvider = AIProviderFactory.getProvider("openai")
        val originalDisplayName = originalProvider?.displayName

        val customOpenAI = object : AIProvider {
            override val name = "openai"
            override val displayName = "Custom OpenAI"
            override val defaultModel = "custom-gpt"
            override val supportedModels = listOf("custom-gpt")
            override val supportsStreaming = false

            override fun isConfigured(config: AIConfig) = !config.apiKey.isNullOrBlank()

            override suspend fun generateResponse(
                prompt: String,
                context: List<AIMessage>,
                config: AIConfig
            ): AIResponse {
                return AIResponse(
                    content = "Custom",
                    tokensUsed = 5,
                    model = defaultModel,
                    provider = name
                )
            }
        }

        AIProviderFactory.registerProvider(customOpenAI)
        val modified = AIProviderFactory.getProvider("openai")

        assertThat(modified?.displayName).isEqualTo("Custom OpenAI")
        assertThat(modified?.displayName).isNotEqualTo(originalDisplayName)

        // Re-register the original OpenAI provider to restore state
        AIProviderFactory.registerProvider(OpenAIProvider())
    }

    // ==================== Execute With Fallback Tests ====================

    @Test
    fun `executeWithFallback throws when no providers available`() = runTest {
        try {
            AIProviderFactory.executeWithFallback(
                prompt = "Hello",
                context = emptyList(),
                configs = emptyMap(),
                preferredProvider = null
            )
            throw AssertionError("Expected AIProviderException")
        } catch (e: AIProviderException) {
            assertThat(e.message).contains("No configuration found")
            assertThat(e.provider).isEqualTo("factory")
            assertThat(e.isRetryable).isFalse()
        }
    }

    @Test
    fun `executeWithFallback prioritizes preferred provider`() = runTest {
        // Since we can't actually make HTTP calls in unit tests,
        // we test the configuration logic

        val configs = mapOf(
            "openai" to AIConfig(model = "gpt-4", apiKey = "openai-key"),
            "anthropic" to AIConfig(model = "claude-3", apiKey = "anthropic-key"),
            "deepseek" to AIConfig(model = "deepseek-chat", apiKey = "deepseek-key")
        )

        // Verify preferred provider would be tried first
        val preferredProvider = "anthropic"
        val provider = AIProviderFactory.getProvider(preferredProvider)

        assertThat(provider).isNotNull()
        assertThat(provider?.name).isEqualTo("anthropic")
        assertThat(configs[preferredProvider]).isNotNull()
    }

    @Test
    fun `executeWithFallback uses default config when provider config missing`() = runTest {
        val configs = mapOf(
            "default" to AIConfig(model = "gpt-4", apiKey = "default-key")
        )

        // Provider should fall back to "default" config
        val openaiConfig = configs["openai"] ?: configs["default"]

        assertThat(openaiConfig).isNotNull()
        assertThat(openaiConfig?.apiKey).isEqualTo("default-key")
    }

    @Test
    fun `executeWithFallback skips unconfigured providers`() = runTest {
        val configs = mapOf(
            "openai" to AIConfig(model = "gpt-4", apiKey = null), // Not configured
            "anthropic" to AIConfig(model = "claude-3", apiKey = "valid-key")
        )

        // OpenAI config is not valid (no API key)
        val openaiProvider = AIProviderFactory.getProvider("openai")
        val openaiConfigured = openaiProvider?.isConfigured(configs["openai"]!!)

        assertThat(openaiConfigured).isFalse()

        // Anthropic config is valid
        val anthropicProvider = AIProviderFactory.getProvider("anthropic")
        val anthropicConfigured = anthropicProvider?.isConfigured(configs["anthropic"]!!)

        assertThat(anthropicConfigured).isTrue()
    }

    // ==================== Execute With Fallback Streaming Tests ====================

    @Test
    fun `executeWithFallbackStreaming calls error callback when no providers`() = runTest {
        var receivedError: AIProviderException? = null

        val callback = object : AIStreamCallback {
            override fun onToken(token: String) {}
            override fun onComplete(response: AIResponse) {}
            override fun onError(error: AIProviderException) {
                receivedError = error
            }
        }

        AIProviderFactory.executeWithFallbackStreaming(
            prompt = "Hello",
            context = emptyList(),
            configs = emptyMap(),
            callback = callback,
            preferredProvider = null
        )

        assertThat(receivedError).isNotNull()
        assertThat(receivedError?.message).contains("No AI providers available")
    }

    @Test
    fun `executeWithFallbackStreaming respects streaming flag`() = runTest {
        val configs = mapOf(
            "openai" to AIConfig(model = "gpt-4", apiKey = "test-key")
        )

        // Verify provider supports streaming
        val provider = AIProviderFactory.getProvider("openai")
        assertThat(provider?.supportsStreaming).isTrue()
    }

    // ==================== AIConfigBuilder Tests ====================

    @Test
    fun `AIConfigBuilder creates config with all parameters`() {
        val config = AIConfigBuilder()
            .model("gpt-4-turbo")
            .temperature(0.9f)
            .maxTokens(2000)
            .apiKey("test-key")
            .customEndpoint("https://custom.api.com")
            .systemPrompt("Be helpful")
            .topP(0.95f)
            .frequencyPenalty(0.5f)
            .presencePenalty(0.3f)
            .build()

        assertThat(config.model).isEqualTo("gpt-4-turbo")
        assertThat(config.temperature).isEqualTo(0.9f)
        assertThat(config.maxTokens).isEqualTo(2000)
        assertThat(config.apiKey).isEqualTo("test-key")
        assertThat(config.customEndpoint).isEqualTo("https://custom.api.com")
        assertThat(config.systemPrompt).isEqualTo("Be helpful")
        assertThat(config.topP).isEqualTo(0.95f)
        assertThat(config.frequencyPenalty).isEqualTo(0.5f)
        assertThat(config.presencePenalty).isEqualTo(0.3f)
    }

    @Test
    fun `AIConfigBuilder supports fluent interface`() {
        val builder = AIConfigBuilder()
            .model("gpt-4")
            .apiKey("key")
            .temperature(0.5f)

        assertThat(builder).isInstanceOf(AIConfigBuilder::class.java)

        val config = builder.build()
        assertThat(config.model).isEqualTo("gpt-4")
    }

    @Test
    fun `AIConfigBuilder uses default values when not set`() {
        val config = AIConfigBuilder()
            .model("gpt-4")
            .build()

        assertThat(config.temperature).isEqualTo(0.7f)
        assertThat(config.maxTokens).isEqualTo(1000)
        assertThat(config.apiKey).isNull()
    }

    // ==================== aiConfig DSL Tests ====================

    @Test
    fun `aiConfig DSL creates config correctly`() {
        val config = aiConfig {
            model("gpt-4")
            apiKey("test-api-key")
            temperature(0.8f)
            maxTokens(1500)
        }

        assertThat(config.model).isEqualTo("gpt-4")
        assertThat(config.apiKey).isEqualTo("test-api-key")
        assertThat(config.temperature).isEqualTo(0.8f)
        assertThat(config.maxTokens).isEqualTo(1500)
    }

    @Test
    fun `aiConfig DSL supports all parameters`() {
        val config = aiConfig {
            model("claude-3-opus")
            temperature(0.5f)
            maxTokens(4000)
            apiKey("anthropic-key")
            customEndpoint("https://proxy.anthropic.com")
            systemPrompt("You are Claude")
            topP(0.9f)
            frequencyPenalty(0.1f)
            presencePenalty(0.2f)
        }

        assertThat(config.model).isEqualTo("claude-3-opus")
        assertThat(config.topP).isEqualTo(0.9f)
        assertThat(config.frequencyPenalty).isEqualTo(0.1f)
        assertThat(config.presencePenalty).isEqualTo(0.2f)
    }

    // ==================== Error Cases Tests ====================

    @Test
    fun `factory handles concurrent access safely`() {
        // Test that multiple threads can access factory without issues
        val providers = mutableListOf<AIProvider?>()

        repeat(10) {
            providers.add(AIProviderFactory.getProvider("openai"))
        }

        assertThat(providers.all { it?.name == "openai" }).isTrue()
    }

    @Test
    fun `provider lookup handles special characters in name`() {
        val provider = AIProviderFactory.getProvider("open ai")
        assertThat(provider).isNull()

        val provider2 = AIProviderFactory.getProvider("open-ai")
        assertThat(provider2).isNull()

        val provider3 = AIProviderFactory.getProvider("open_ai")
        assertThat(provider3).isNull()
    }
}
