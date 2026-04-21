package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.ai.*
import com.conferbot.sdk.core.nodes.*
import com.conferbot.sdk.models.AISettings
import com.conferbot.sdk.services.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Handler for webhook-node
 * Makes HTTP requests to external APIs with proper thread safety,
 * token caching, retry logic, and rate limiting.
 */
class WebhookNodeHandler(
    private val webhookClient: WebhookClient = createDefaultWebhookClient()
) : BaseNodeHandler() {
    override val nodeType = NodeTypes.WEBHOOK

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val DEFAULT_MAX_RETRIES = 3

        /**
         * Create default webhook client with standard configuration
         */
        fun createDefaultWebhookClient(): WebhookClient {
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

            val rateLimiter = RateLimiter(
                maxRequests = 100,
                perMillis = 60_000 // 100 requests per minute
            )

            return WebhookClient(httpClient, rateLimiter)
        }
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val url = getString(nodeData, "url", "")
        val method = getString(nodeData, "method", "POST").uppercase()
        val headers = getMap(nodeData, "headers")
        val body = nodeData["body"]
        val includeAnswerVariables = getBoolean(nodeData, "includeAnswerVariables", false)
        val answerVariable = nodeData["answerVariable"]?.toString()
        val timeout = getInt(nodeData, "timeout", (DEFAULT_TIMEOUT_MS / 1000).toInt()) * 1000L
        val maxRetries = getInt(nodeData, "maxRetries", DEFAULT_MAX_RETRIES)

        if (url.isEmpty()) {
            return NodeResult.Proceed()  // Skip if no URL
        }

        try {
            // Build request body
            val requestBody = buildRequestBody(body, includeAnswerVariables)

            // Parse authentication configuration
            val authentication = parseAuthentication(getMap(nodeData, "authentication"))

            // Build headers map with proper typing
            val headersMap = headers.mapValues { it.value?.toString() ?: "" }

            // Create webhook request
            val webhookRequest = WebhookRequest(
                url = url,
                method = method,
                headers = headersMap,
                body = requestBody,
                authentication = authentication,
                timeoutMs = timeout,
                maxRetries = maxRetries
            )

            // Execute request using the thread-safe WebhookClient
            val response = withContext(Dispatchers.IO) {
                webhookClient.execute(webhookRequest)
            }

            // Handle response
            if (response.success) {
                // Store response if answer variable specified
                if (!answerVariable.isNullOrEmpty() && response.body != null) {
                    state.setAnswerVariableByKey(answerVariable, response.body)
                }

                // Record successful webhook call
                recordResponse(
                    nodeId = nodeId,
                    shape = "webhook-success",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "statusCode" to response.statusCode,
                        "retryCount" to response.retryCount
                    )
                )
            } else {
                // Record error but continue flow
                recordResponse(
                    nodeId = nodeId,
                    shape = "webhook-error",
                    text = response.error,
                    type = nodeType,
                    additionalData = mapOf(
                        "statusCode" to response.statusCode,
                        "retryCount" to response.retryCount,
                        "error" to (response.error ?: "Unknown error")
                    )
                )
            }

        } catch (e: WebhookException) {
            // Handle webhook-specific exceptions
            recordResponse(
                nodeId = nodeId,
                shape = "webhook-error",
                text = e.message,
                type = nodeType,
                additionalData = mapOf(
                    "statusCode" to (e.statusCode ?: -1),
                    "isRetryable" to e.isRetryable
                )
            )
        } catch (e: Exception) {
            // Log error but continue flow
            recordResponse(
                nodeId = nodeId,
                shape = "webhook-error",
                text = e.message,
                type = nodeType
            )
        }

        return NodeResult.Proceed()
    }

    /**
     * Parse authentication configuration from node data
     */
    private fun parseAuthentication(auth: Map<String, Any?>): WebhookAuthentication? {
        if (auth.isEmpty()) return null

        val authType = auth["type"]?.toString()?.lowercase() ?: detectAuthType(auth)

        return when (authType) {
            "bearer" -> {
                val token = auth["token"]?.toString() ?: return null
                WebhookAuthentication.Bearer(token)
            }

            "basic" -> {
                val username = auth["username"]?.toString() ?: return null
                // FIX 6: Use password immediately and avoid caching credentials
                val passwordChars = (auth["password"]?.toString() ?: "").toCharArray()
                val result = WebhookAuthentication.Basic(username, String(passwordChars))
                // Clear password char array after use (best-effort for JVM)
                passwordChars.fill('\u0000')
                result
            }

            "apikey", "api_key", "api-key" -> {
                val key = auth["key"]?.toString() ?: auth["headerName"]?.toString() ?: "X-API-Key"
                val value = auth["value"]?.toString() ?: auth["apiKey"]?.toString() ?: return null
                val location = when (auth["location"]?.toString()?.lowercase()) {
                    "query" -> WebhookAuthentication.ApiKey.ApiKeyLocation.QUERY
                    else -> WebhookAuthentication.ApiKey.ApiKeyLocation.HEADER
                }
                WebhookAuthentication.ApiKey(key, value, location)
            }

            "oauth2", "oauth" -> {
                val tokenUrl = auth["tokenUrl"]?.toString() ?: return null
                val clientId = auth["clientId"]?.toString() ?: return null
                val clientSecret = auth["clientSecret"]?.toString() ?: return null
                val scope = auth["scope"]?.toString()
                val grantType = auth["grantType"]?.toString() ?: "client_credentials"
                WebhookAuthentication.OAuth2(tokenUrl, clientId, clientSecret, scope, grantType)
            }

            "custom", "token" -> {
                val tokenUrl = auth["tokenUrl"]?.toString() ?: return null
                val username = auth["username"]?.toString() ?: return null
                // FIX 6: Use password immediately and avoid caching credentials
                val passwordChars = (auth["password"]?.toString() ?: "").toCharArray()
                val tokenPath = auth["tokenPath"]?.toString() ?: "access_token"
                val expiresInPath = auth["expiresInPath"]?.toString() ?: "expires_in"
                val result = WebhookAuthentication.CustomToken(tokenUrl, username, String(passwordChars), tokenPath, expiresInPath)
                // Clear password char array after use (best-effort for JVM)
                passwordChars.fill('\u0000')
                result
            }

            else -> {
                // Try to detect based on available fields (legacy support)
                detectAndCreateAuthentication(auth)
            }
        }
    }

    /**
     * Detect authentication type based on available fields
     */
    private fun detectAuthType(auth: Map<String, Any?>): String {
        return when {
            auth.containsKey("token") && !auth.containsKey("tokenUrl") -> "bearer"
            auth.containsKey("apiKey") || auth.containsKey("headerName") -> "apikey"
            auth.containsKey("clientId") && auth.containsKey("clientSecret") -> "oauth2"
            auth.containsKey("tokenUrl") && auth.containsKey("username") -> "custom"
            auth.containsKey("username") && auth.containsKey("password") && !auth.containsKey("tokenUrl") -> "basic"
            else -> "unknown"
        }
    }

    /**
     * Create authentication from fields (legacy support)
     */
    private fun detectAndCreateAuthentication(auth: Map<String, Any?>): WebhookAuthentication? {
        // Legacy format: tokenUrl + username + password
        val tokenUrl = auth["tokenUrl"]?.toString()
        val username = auth["username"]?.toString()
        // FIX 6: Use password via char array and clear after use (best-effort for JVM)
        val passwordChars = (auth["password"]?.toString() ?: "").toCharArray()
        val password = String(passwordChars)

        if (!tokenUrl.isNullOrEmpty() && !username.isNullOrEmpty()) {
            val result = WebhookAuthentication.CustomToken(
                tokenUrl = tokenUrl,
                username = username,
                password = password,
                tokenPath = auth["tokenPath"]?.toString() ?: "access",
                expiresInPath = auth["expiresInPath"]?.toString() ?: "expires_in"
            )
            passwordChars.fill('\u0000')
            return result
        }

        // Legacy format: just username + password (basic auth)
        if (!username.isNullOrEmpty() && password.isNotEmpty()) {
            val result = WebhookAuthentication.Basic(username, password)
            passwordChars.fill('\u0000')
            return result
        }

        passwordChars.fill('\u0000')
        return null
    }

    /**
     * Build request body from node configuration
     */
    private fun buildRequestBody(body: Any?, includeAnswerVariables: Boolean): String? {
        val json = when (body) {
            is String -> {
                try {
                    JSONObject(body)
                } catch (e: Exception) {
                    JSONObject().put("data", body)
                }
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                JSONObject(body as Map<String, Any?>)
            }
            else -> JSONObject()
        }

        if (includeAnswerVariables) {
            val vars = state.getAnswerVariablesMap()
            json.put("answerVariables", JSONObject(vars))
        }

        return json.toString()
    }
}

/**
 * Handler for gpt-node
 * Sends conversation context to AI providers (OpenAI, Anthropic, DeepSeek)
 * Supports multiple providers with automatic fallback chain
 */
class GptNodeHandler(
    private val aiSettings: AISettings = AISettings()
) : BaseNodeHandler() {
    override val nodeType = NodeTypes.GPT

    companion object {
        // Provider name mapping from node configuration
        private val PROVIDER_ALIASES = mapOf(
            "gpt" to "openai",
            "openai" to "openai",
            "gpt-3.5" to "openai",
            "gpt-4" to "openai",
            "claude" to "anthropic",
            "anthropic" to "anthropic",
            "deepseek" to "deepseek"
        )
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        // Get configuration from node data
        val apiKey = getString(nodeData, "apiKey", "")
        val model = getString(nodeData, "selectedModel", "")
        val systemContext = nodeData["context"]?.toString()
        val providerType = getString(nodeData, "provider", "").lowercase()
        val temperature = getFloat(nodeData, "temperature", aiSettings.defaultTemperature)
        val maxTokens = getInt(nodeData, "maxTokens", aiSettings.defaultMaxTokens)
        val enableFallback = getBoolean(nodeData, "enableFallback", aiSettings.enableFallback)
        val customEndpoint = nodeData["customEndpoint"]?.toString() ?: aiSettings.customEndpoint

        // Determine which provider to use
        val resolvedProvider = resolveProvider(providerType, model)
        val resolvedModel = resolveModel(model, resolvedProvider)

        // Build provider configurations
        val configs = buildProviderConfigs(
            apiKey = apiKey,
            model = resolvedModel,
            temperature = temperature,
            maxTokens = maxTokens,
            systemPrompt = systemContext,
            customEndpoint = customEndpoint
        )

        // Check if we have any valid configuration
        if (configs.isEmpty() || configs.values.none { it.apiKey?.isNotBlank() == true }) {
            recordResponse(
                nodeId = nodeId,
                shape = "gpt-error",
                text = "No API key configured for AI provider",
                type = nodeType,
                additionalData = mapOf("provider" to resolvedProvider)
            )
            return NodeResult.Proceed()
        }

        try {
            // Build conversation context from transcript
            val context = buildConversationContext()

            // Get the most recent user message as the prompt
            val prompt = getLatestUserMessage()

            if (prompt.isBlank()) {
                return NodeResult.Proceed()  // No user message to respond to
            }

            // Execute AI request with fallback support
            val response = if (enableFallback) {
                executeWithFallback(prompt, context, configs, resolvedProvider)
            } else {
                executeSingleProvider(prompt, context, configs, resolvedProvider)
            }

            // Add response to transcript
            state.addToTranscript("bot", response.content)

            // Record the response with provider metadata
            recordResponse(
                nodeId = nodeId,
                shape = "gpt-response",
                text = response.content,
                type = nodeType,
                additionalData = mapOf(
                    "provider" to response.provider,
                    "model" to response.model,
                    "tokensUsed" to response.tokensUsed,
                    "finishReason" to (response.finishReason ?: "")
                )
            )

            // Display the message
            return NodeResult.DisplayUI(
                NodeUIState.Message(
                    text = response.content,
                    nodeId = nodeId
                )
            )

        } catch (e: AIProviderException) {
            recordResponse(
                nodeId = nodeId,
                shape = "gpt-error",
                text = e.message,
                type = nodeType,
                additionalData = mapOf(
                    "provider" to e.provider,
                    "statusCode" to (e.statusCode ?: -1),
                    "isRateLimited" to e.isRateLimited
                )
            )
        } catch (e: Exception) {
            recordResponse(
                nodeId = nodeId,
                shape = "gpt-error",
                text = e.message ?: "Unknown error occurred",
                type = nodeType
            )
        }

        return NodeResult.Proceed()
    }

    /**
     * Resolve provider name from aliases and model detection
     */
    private fun resolveProvider(providerType: String, model: String): String {
        // First check explicit provider type
        if (providerType.isNotBlank()) {
            val mapped = PROVIDER_ALIASES[providerType.lowercase()]
            if (mapped != null) return mapped
        }

        // Try to detect from model name
        val modelLower = model.lowercase()
        return when {
            modelLower.startsWith("gpt-") || modelLower.contains("gpt") -> "openai"
            modelLower.startsWith("claude") -> "anthropic"
            modelLower.startsWith("deepseek") -> "deepseek"
            else -> aiSettings.defaultProvider
        }
    }

    /**
     * Resolve model name based on provider
     */
    private fun resolveModel(model: String, provider: String): String {
        if (model.isNotBlank()) return model

        // Check SDK settings for default model
        aiSettings.getDefaultModel(provider)?.let { return it }

        // Use provider's default model
        return AIProviderFactory.getProvider(provider)?.defaultModel ?: "gpt-3.5-turbo"
    }

    /**
     * Build configuration for each provider
     */
    private fun buildProviderConfigs(
        apiKey: String,
        model: String,
        temperature: Float,
        maxTokens: Int,
        systemPrompt: String?,
        customEndpoint: String?
    ): Map<String, AIConfig> {
        val configs = mutableMapOf<String, AIConfig>()

        // Build base config
        val baseConfig = AIConfig(
            model = model,
            temperature = temperature,
            maxTokens = maxTokens,
            apiKey = apiKey.takeIf { it.isNotBlank() },
            customEndpoint = customEndpoint,
            systemPrompt = systemPrompt
        )

        // Add config for each provider with appropriate API key
        for (providerName in listOf("openai", "anthropic", "deepseek")) {
            val providerApiKey = when {
                apiKey.isNotBlank() -> apiKey
                else -> aiSettings.getApiKey(providerName)
            }

            if (!providerApiKey.isNullOrBlank()) {
                configs[providerName] = baseConfig.copy(
                    apiKey = providerApiKey,
                    model = aiSettings.getDefaultModel(providerName) ?: model
                )
            }
        }

        // Also add a "default" config for fallback lookup
        if (apiKey.isNotBlank()) {
            configs["default"] = baseConfig
        }

        return configs
    }

    /**
     * Build conversation context from transcript
     */
    private fun buildConversationContext(): List<AIMessage> {
        return state.getTranscriptForGPT().map { msg ->
            AIMessage(
                role = msg["role"] ?: "user",
                content = msg["content"] ?: ""
            )
        }.dropLast(1)  // Remove the latest user message (will be used as prompt)
    }

    /**
     * Get the latest user message from transcript
     */
    private fun getLatestUserMessage(): String {
        val transcript = state.getTranscriptForGPT()
        return transcript.lastOrNull { it["role"] == "user" }?.get("content") ?: ""
    }

    /**
     * Execute with fallback chain
     */
    private suspend fun executeWithFallback(
        prompt: String,
        context: List<AIMessage>,
        configs: Map<String, AIConfig>,
        preferredProvider: String
    ): AIResponse = withContext(Dispatchers.IO) {
        AIProviderFactory.executeWithFallback(
            prompt = prompt,
            context = context,
            configs = configs,
            preferredProvider = preferredProvider,
            maxRetries = aiSettings.maxRetries,
            retryDelayMs = aiSettings.retryDelayMs
        )
    }

    /**
     * Execute with single provider (no fallback)
     */
    private suspend fun executeSingleProvider(
        prompt: String,
        context: List<AIMessage>,
        configs: Map<String, AIConfig>,
        providerName: String
    ): AIResponse = withContext(Dispatchers.IO) {
        val provider = AIProviderFactory.getProvider(providerName)
            ?: throw AIProviderException(
                message = "Unknown provider: $providerName",
                provider = providerName,
                isRetryable = false
            )

        val config = configs[providerName] ?: configs["default"]
            ?: throw AIProviderException(
                message = "No configuration found for provider: $providerName",
                provider = providerName,
                isRetryable = false
            )

        provider.generateResponse(prompt, context, config)
    }

    /**
     * Get float value from node data
     */
    private fun getFloat(nodeData: Map<String, Any?>, key: String, default: Float): Float {
        return when (val value = nodeData[key]) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull() ?: default
            else -> default
        }
    }
}

/**
 * Handler for email-node
 * Sends email via server-side socket event (fire-and-forget)
 */
class EmailNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.EMAIL

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        // Execute email send via server-side integration
        return NodeResult.ExecuteIntegration(
            nodeType = "email-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                recordResponse(
                    nodeId = nodeId,
                    shape = "email-triggered",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "to" to nodeData["to"],
                        "subject" to nodeData["subject"],
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for zapier-node
 * Triggers Zapier webhook via server-side socket event (fire-and-forget)
 */
class ZapierNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ZAPIER

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        // Execute Zapier trigger via server-side integration
        return NodeResult.ExecuteIntegration(
            nodeType = "zapier-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                recordResponse(
                    nodeId = nodeId,
                    shape = "zapier-triggered",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "answerVariables" to state.getAnswerVariablesMap(),
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for google-sheets-node
 * Reads/writes to Google Sheets via server-side integration
 */
class GoogleSheetsNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.GOOGLE_SHEETS

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "write")

        return NodeResult.ExecuteIntegration(
            nodeType = "google-sheets-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                // Store answer variable if server returned data (e.g., read operations)
                if (result.success && result.answerVariable != null && result.answerValue != null) {
                    state.setAnswerVariableByKey(result.answerVariable, result.answerValue)
                }

                recordResponse(
                    nodeId = nodeId,
                    shape = "google-sheets-$operation",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "operation" to operation,
                        "spreadsheetId" to nodeData["spreadsheetId"],
                        "sheetName" to nodeData["sheetName"],
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for gmail-node
 * Sends email via Gmail through server-side integration
 */
class GmailNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.GMAIL

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        return NodeResult.ExecuteIntegration(
            nodeType = "gmail-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                recordResponse(
                    nodeId = nodeId,
                    shape = "gmail-triggered",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "to" to nodeData["to"],
                        "subject" to nodeData["subject"],
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for google-calendar-node
 * Books calendar appointments via server-side integration
 */
class GoogleCalendarNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.GOOGLE_CALENDAR

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "book")

        if (operation == "book") {
            // Display calendar booking UI
            val timezone = getString(nodeData, "timeZone", java.util.TimeZone.getDefault().id)
            val collectEmail = getBoolean(nodeData, "collectAttendeeEmail", false)
            val answerKey = getString(nodeData, "answerVariable", "calendar_booking")

            state.addAnswerVariable(nodeId, answerKey)

            return NodeResult.DisplayUI(
                NodeUIState.Calendar(
                    questionText = "Select a date and time",
                    showTimeSelection = true,
                    timezone = timezone,
                    nodeId = nodeId,
                    answerKey = answerKey
                )
            )
        }

        // For non-book operations, execute via server
        return NodeResult.ExecuteIntegration(
            nodeType = "google-calendar-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                recordResponse(
                    nodeId = nodeId,
                    shape = "google-calendar-$operation",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "operation" to operation,
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        @Suppress("UNCHECKED_CAST")
        val responseMap = when (response) {
            is Map<*, *> -> response as Map<String, Any?>
            else -> mapOf("date" to response.toString())
        }

        val date = responseMap["date"]?.toString() ?: ""
        val time = responseMap["time"]?.toString() ?: ""
        val email = responseMap["email"]?.toString()

        state.setAnswerVariable(nodeId, "$date $time")
        state.addToTranscript("user", "Booked: $date at $time")

        // Execute the booking via server-side integration
        val bookingData = nodeData.toMutableMap().apply {
            put("selectedDate", date)
            put("selectedTime", time)
            email?.let { put("attendeeEmail", it) }
        }

        return NodeResult.ExecuteIntegration(
            nodeType = "google-calendar-node",
            nodeId = nodeId,
            nodeData = bookingData,
            onResult = { result ->
                recordResponse(
                    nodeId = nodeId,
                    shape = "google-calendar-booking",
                    text = "$date $time",
                    type = nodeType,
                    additionalData = mapOf(
                        "date" to date,
                        "time" to time,
                        "attendeeEmail" to email,
                        "timezone" to nodeData["timeZone"],
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for google-meet-node
 * Creates Google Meet meetings via server-side integration
 */
class GoogleMeetNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.GOOGLE_MEET

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "book")

        if (operation == "book") {
            val timezone = getString(nodeData, "timeZone", java.util.TimeZone.getDefault().id)
            val answerKey = getString(nodeData, "answerVariable", "meet_booking")

            state.addAnswerVariable(nodeId, answerKey)

            return NodeResult.DisplayUI(
                NodeUIState.Calendar(
                    questionText = "Select a time for your meeting",
                    showTimeSelection = true,
                    timezone = timezone,
                    nodeId = nodeId,
                    answerKey = answerKey
                )
            )
        }

        // For non-book operations, execute via server
        return NodeResult.ExecuteIntegration(
            nodeType = "google-meet-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                recordResponse(
                    nodeId = nodeId,
                    shape = "google-meet-$operation",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "operation" to operation,
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        @Suppress("UNCHECKED_CAST")
        val responseMap = when (response) {
            is Map<*, *> -> response as Map<String, Any?>
            else -> mapOf("date" to response.toString())
        }

        val date = responseMap["date"]?.toString() ?: ""
        val time = responseMap["time"]?.toString() ?: ""

        // Execute the Meet booking via server-side integration
        val bookingData = nodeData.toMutableMap().apply {
            put("selectedDate", date)
            put("selectedTime", time)
        }

        return NodeResult.ExecuteIntegration(
            nodeType = "google-meet-node",
            nodeId = nodeId,
            nodeData = bookingData,
            onResult = { result ->
                recordResponse(
                    nodeId = nodeId,
                    shape = "google-meet-booking",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "date" to date,
                        "time" to time,
                        "meetLink" to result.data?.get("meetLink"),
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for google-drive-node
 * Uploads/downloads from Google Drive via server-side integration
 */
class GoogleDriveNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.GOOGLE_DRIVE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "upload")

        return NodeResult.ExecuteIntegration(
            nodeType = "google-drive-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                if (result.success && result.answerVariable != null && result.answerValue != null) {
                    state.setAnswerVariableByKey(result.answerVariable, result.answerValue)
                }

                recordResponse(
                    nodeId = nodeId,
                    shape = "google-drive-$operation",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "operation" to operation,
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for google-docs-node
 * Creates/updates Google Docs via server-side integration
 */
class GoogleDocsNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.GOOGLE_DOCS

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "create")

        return NodeResult.ExecuteIntegration(
            nodeType = "google-docs-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                if (result.success && result.answerVariable != null && result.answerValue != null) {
                    state.setAnswerVariableByKey(result.answerVariable, result.answerValue)
                }

                recordResponse(
                    nodeId = nodeId,
                    shape = "google-docs-$operation",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "operation" to operation,
                        "title" to nodeData["title"],
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for slack-node
 * Sends messages to Slack via server-side integration
 */
class SlackNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.SLACK

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        return NodeResult.ExecuteIntegration(
            nodeType = "slack-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                recordResponse(
                    nodeId = nodeId,
                    shape = "slack-message",
                    text = nodeData["message"]?.toString(),
                    type = nodeType,
                    additionalData = mapOf(
                        "channel" to nodeData["channel"],
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for discord-node
 * Sends messages to Discord via server-side integration
 */
class DiscordNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.DISCORD

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        return NodeResult.ExecuteIntegration(
            nodeType = "discord-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                recordResponse(
                    nodeId = nodeId,
                    shape = "discord-message",
                    text = nodeData["message"]?.toString(),
                    type = nodeType,
                    additionalData = mapOf(
                        "channel" to nodeData["channel"],
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for airtable-node
 * CRUD operations on Airtable via server-side integration
 */
class AirtableNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.AIRTABLE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "create")

        return NodeResult.ExecuteIntegration(
            nodeType = "airtable-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                if (result.success && result.answerVariable != null && result.answerValue != null) {
                    state.setAnswerVariableByKey(result.answerVariable, result.answerValue)
                }

                recordResponse(
                    nodeId = nodeId,
                    shape = "airtable-$operation",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "baseId" to nodeData["baseId"],
                        "tableName" to nodeData["tableName"],
                        "operation" to operation,
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for hubspot-node
 * Creates/updates HubSpot contacts via server-side integration
 */
class HubspotNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.HUBSPOT

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "createContact")

        return NodeResult.ExecuteIntegration(
            nodeType = "hubspot-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                if (result.success && result.answerVariable != null && result.answerValue != null) {
                    state.setAnswerVariableByKey(result.answerVariable, result.answerValue)
                }

                recordResponse(
                    nodeId = nodeId,
                    shape = "hubspot-$operation",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "operation" to operation,
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for notion-node
 * Creates/updates Notion pages via server-side integration
 */
class NotionNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.NOTION

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "createPage")

        return NodeResult.ExecuteIntegration(
            nodeType = "notion-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                if (result.success && result.answerVariable != null && result.answerValue != null) {
                    state.setAnswerVariableByKey(result.answerVariable, result.answerValue)
                }

                recordResponse(
                    nodeId = nodeId,
                    shape = "notion-$operation",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "databaseId" to nodeData["databaseId"],
                        "operation" to operation,
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for zohocrm-node
 * Creates/updates Zoho CRM records via server-side integration
 */
class ZohoCrmNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ZOHO_CRM

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "create")
        val module = getString(nodeData, "module", "Contacts")

        return NodeResult.ExecuteIntegration(
            nodeType = "zohocrm-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                if (result.success && result.answerVariable != null && result.answerValue != null) {
                    state.setAnswerVariableByKey(result.answerVariable, result.answerValue)
                }

                recordResponse(
                    nodeId = nodeId,
                    shape = "zohocrm-$operation",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "module" to module,
                        "operation" to operation,
                        "success" to result.success,
                        "error" to result.error
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}

/**
 * Handler for stripe-node
 * Creates payment links/sessions via server-side integration
 */
class StripeNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.STRIPE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "createPaymentLink")

        // For payment operations, execute via server and display payment UI
        if (operation in listOf("createPaymentLink", "createCheckoutSession")) {
            val amount = nodeData["customAmount"]
            val currency = nodeData["currency"]?.toString() ?: "USD"
            val description = nodeData["description"]?.toString()

            // Execute integration via socket and wait for payment URL from server
            return NodeResult.ExecuteIntegration(
                nodeType = "stripe-node",
                nodeId = nodeId,
                nodeData = nodeData,
                onResult = { result ->
                    // Record the response
                    recordResponse(
                        nodeId = nodeId,
                        shape = "stripe-payment",
                        text = null,
                        type = nodeType,
                        additionalData = mapOf(
                            "operation" to operation,
                            "amount" to amount,
                            "currency" to currency,
                            "url" to result.data?.get("url"),
                            "success" to result.success,
                            "error" to result.error
                        )
                    )

                    if (result.success && result.data != null) {
                        val paymentUrl = result.data["url"]?.toString() ?: ""

                        if (paymentUrl.isNotEmpty()) {
                            // Display payment UI with the actual URL from server
                            NodeResult.DisplayUI(
                                NodeUIState.Payment(
                                    paymentUrl = paymentUrl,
                                    amount = when (amount) {
                                        is Number -> amount.toDouble()
                                        is String -> amount.toDoubleOrNull()
                                        else -> null
                                    },
                                    currency = currency,
                                    description = result.message ?: description,
                                    nodeId = nodeId
                                )
                            )
                        } else {
                            // No URL returned, proceed with error
                            NodeResult.Error(
                                message = "Payment URL not received from server",
                                shouldProceed = true
                            )
                        }
                    } else {
                        // Integration failed, log error and proceed
                        NodeResult.Error(
                            message = result.error ?: "Failed to create payment link",
                            shouldProceed = true
                        )
                    }
                }
            )
        }

        // For non-payment operations (createCustomer, listProducts, etc.)
        // Execute via server but don't display payment UI
        return NodeResult.ExecuteIntegration(
            nodeType = "stripe-node",
            nodeId = nodeId,
            nodeData = nodeData,
            onResult = { result ->
                recordResponse(
                    nodeId = nodeId,
                    shape = "stripe-$operation",
                    text = null,
                    type = nodeType,
                    additionalData = mapOf(
                        "operation" to operation,
                        "success" to result.success,
                        "error" to result.error,
                        "data" to result.data
                    )
                )
                NodeResult.Proceed()
            }
        )
    }
}
