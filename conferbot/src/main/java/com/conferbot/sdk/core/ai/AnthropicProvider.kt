package com.conferbot.sdk.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Anthropic Claude provider implementation
 * Supports Claude 3 (Opus, Sonnet, Haiku) and Claude 2 models
 */
class AnthropicProvider : AIProvider {

    override val name: String = "anthropic"
    override val displayName: String = "Anthropic Claude"
    override val defaultModel: String = "claude-3-sonnet-20240229"

    override val supportedModels: List<String> = listOf(
        "claude-3-opus-20240229",
        "claude-3-sonnet-20240229",
        "claude-3-haiku-20240307",
        "claude-3-5-sonnet-20240620",
        "claude-3-5-sonnet-20241022",
        "claude-3-5-haiku-20241022",
        "claude-2.1",
        "claude-2.0",
        "claude-instant-1.2"
    )

    override val supportsStreaming: Boolean = true

    companion object {
        private const val DEFAULT_ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private const val TIMEOUT_MS = 60000
    }

    override fun isConfigured(config: AIConfig): Boolean {
        return !config.apiKey.isNullOrBlank()
    }

    override suspend fun generateResponse(
        prompt: String,
        context: List<AIMessage>,
        config: AIConfig
    ): AIResponse = withContext(Dispatchers.IO) {
        if (!isConfigured(config)) {
            throw AIProviderException(
                message = "Anthropic API key is not configured",
                provider = name,
                isRetryable = false
            )
        }

        val endpoint = config.customEndpoint ?: DEFAULT_ENDPOINT
        val model = config.model.ifBlank { defaultModel }

        try {
            val connection = createConnection(endpoint, config.apiKey!!)
            val requestBody = buildRequestBody(prompt, context, config, model, stream = false)

            // Write request
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            // Handle response
            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().readText()
                parseResponse(responseText, model)
            } else {
                handleErrorResponse(connection, responseCode)
            }
        } catch (e: AIProviderException) {
            throw e
        } catch (e: Exception) {
            throw AIProviderException(
                message = "Anthropic request failed: ${e.message}",
                provider = name,
                isRetryable = true,
                cause = e
            )
        }
    }

    override suspend fun generateResponseStreaming(
        prompt: String,
        context: List<AIMessage>,
        config: AIConfig,
        callback: AIStreamCallback
    ) = withContext(Dispatchers.IO) {
        if (!isConfigured(config)) {
            callback.onError(AIProviderException(
                message = "Anthropic API key is not configured",
                provider = name,
                isRetryable = false
            ))
            return@withContext
        }

        val endpoint = config.customEndpoint ?: DEFAULT_ENDPOINT
        val model = config.model.ifBlank { defaultModel }

        try {
            val connection = createConnection(endpoint, config.apiKey!!)
            val requestBody = buildRequestBody(prompt, context, config, model, stream = true)

            // Write request
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(connection.inputStream.reader())
                val contentBuilder = StringBuilder()
                var inputTokens = 0
                var outputTokens = 0
                var stopReason: String? = null

                reader.forEachLine { line ->
                    if (line.startsWith("data: ")) {
                        try {
                            val jsonData = line.substring(6)
                            val json = JSONObject(jsonData)
                            val eventType = json.optString("type", "")

                            when (eventType) {
                                "content_block_delta" -> {
                                    val delta = json.optJSONObject("delta")
                                    val text = delta?.optString("text", "") ?: ""
                                    if (text.isNotEmpty()) {
                                        contentBuilder.append(text)
                                        callback.onToken(text)
                                    }
                                }
                                "message_start" -> {
                                    val message = json.optJSONObject("message")
                                    val usage = message?.optJSONObject("usage")
                                    inputTokens = usage?.optInt("input_tokens", 0) ?: 0
                                }
                                "message_delta" -> {
                                    stopReason = json.optJSONObject("delta")?.optString("stop_reason")
                                    val usage = json.optJSONObject("usage")
                                    outputTokens = usage?.optInt("output_tokens", 0) ?: 0
                                }
                            }
                        } catch (e: Exception) {
                            // Skip malformed chunks
                        }
                    }
                }
                reader.close()

                val response = AIResponse(
                    content = contentBuilder.toString(),
                    tokensUsed = inputTokens + outputTokens,
                    model = model,
                    provider = name,
                    finishReason = stopReason
                )
                callback.onComplete(response)
            } else {
                try {
                    handleErrorResponse(connection, responseCode)
                } catch (e: AIProviderException) {
                    callback.onError(e)
                }
            }
        } catch (e: AIProviderException) {
            callback.onError(e)
        } catch (e: Exception) {
            callback.onError(AIProviderException(
                message = "Anthropic streaming request failed: ${e.message}",
                provider = name,
                isRetryable = true,
                cause = e
            ))
        }
    }

    private fun createConnection(endpoint: String, apiKey: String): HttpURLConnection {
        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("x-api-key", apiKey)
        connection.setRequestProperty("anthropic-version", API_VERSION)
        connection.doOutput = true
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        return connection
    }

    private fun buildRequestBody(
        prompt: String,
        context: List<AIMessage>,
        config: AIConfig,
        model: String,
        stream: Boolean
    ): JSONObject {
        val messages = JSONArray()

        // Anthropic handles system separately, filter it out of messages
        val systemPrompt = config.systemPrompt ?: context
            .filter { it.role == "system" }
            .joinToString("\n") { it.content }
            .takeIf { it.isNotBlank() }

        // Add non-system context messages
        context.filter { it.role != "system" }.forEach { msg ->
            messages.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        // Add current prompt as user message
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        return JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", config.maxTokens)
            put("stream", stream)

            // Add system prompt if present
            if (!systemPrompt.isNullOrBlank()) {
                put("system", systemPrompt)
            }

            // Anthropic uses different parameter name for temperature
            if (config.temperature != 0.7f) {
                put("temperature", config.temperature.toDouble())
            }

            config.topP?.let { put("top_p", it.toDouble()) }
        }
    }

    private fun parseResponse(responseText: String, model: String): AIResponse {
        val json = JSONObject(responseText)

        // Get content from content array
        val contentArray = json.getJSONArray("content")
        val contentBuilder = StringBuilder()

        for (i in 0 until contentArray.length()) {
            val block = contentArray.getJSONObject(i)
            if (block.getString("type") == "text") {
                contentBuilder.append(block.getString("text"))
            }
        }

        val content = contentBuilder.toString()

        if (content.isBlank()) {
            throw AIProviderException(
                message = "Anthropic returned empty response",
                provider = name,
                isRetryable = true
            )
        }

        val stopReason = json.optString("stop_reason", null)

        val usage = json.optJSONObject("usage")
        val inputTokens = usage?.optInt("input_tokens", 0) ?: 0
        val outputTokens = usage?.optInt("output_tokens", 0) ?: 0

        return AIResponse(
            content = content,
            tokensUsed = inputTokens + outputTokens,
            model = model,
            provider = name,
            finishReason = stopReason
        )
    }

    private fun handleErrorResponse(connection: HttpURLConnection, responseCode: Int): Nothing {
        val errorBody = try {
            connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
        } catch (e: Exception) {
            "Failed to read error response"
        }

        val errorMessage = try {
            val errorJson = JSONObject(errorBody)
            errorJson.optJSONObject("error")?.optString("message") ?: errorBody
        } catch (e: Exception) {
            errorBody
        }

        val isRateLimited = responseCode == 429
        val isRetryable = responseCode in listOf(429, 500, 502, 503, 504, 529) // 529 is Anthropic overloaded

        throw AIProviderException(
            message = "Anthropic API error ($responseCode): $errorMessage",
            provider = name,
            statusCode = responseCode,
            isRateLimited = isRateLimited,
            isRetryable = isRetryable
        )
    }
}
