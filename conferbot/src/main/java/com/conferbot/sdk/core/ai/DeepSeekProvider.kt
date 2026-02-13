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
 * DeepSeek AI provider implementation
 * A cost-effective alternative/fallback provider
 * Uses OpenAI-compatible API format
 */
class DeepSeekProvider : AIProvider {

    override val name: String = "deepseek"
    override val displayName: String = "DeepSeek"
    override val defaultModel: String = "deepseek-chat"

    override val supportedModels: List<String> = listOf(
        "deepseek-chat",
        "deepseek-coder",
        "deepseek-reasoner"
    )

    override val supportsStreaming: Boolean = true

    companion object {
        private const val DEFAULT_ENDPOINT = "https://api.deepseek.com/v1/chat/completions"
        private const val TIMEOUT_MS = 90000 // DeepSeek can be slower, longer timeout
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
                message = "DeepSeek API key is not configured",
                provider = name,
                isRetryable = false
            )
        }

        val endpoint = config.customEndpoint ?: DEFAULT_ENDPOINT
        val model = config.model.ifBlank { defaultModel }

        try {
            val apiKey = config.apiKey ?: throw AIProviderException(
                message = "DeepSeek API key is null",
                provider = name,
                isRetryable = false
            )
            val connection = createConnection(endpoint, apiKey)
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
                message = "DeepSeek request failed: ${e.message}",
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
                message = "DeepSeek API key is not configured",
                provider = name,
                isRetryable = false
            ))
            return@withContext
        }

        val endpoint = config.customEndpoint ?: DEFAULT_ENDPOINT
        val model = config.model.ifBlank { defaultModel }

        try {
            val apiKey = config.apiKey ?: run {
                callback.onError(AIProviderException(
                    message = "DeepSeek API key is null",
                    provider = name,
                    isRetryable = false
                ))
                return@withContext
            }
            val connection = createConnection(endpoint, apiKey)
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
                var tokensUsed = 0
                var finishReason: String? = null

                reader.forEachLine { line ->
                    if (line.startsWith("data: ") && line != "data: [DONE]") {
                        try {
                            val jsonData = line.substring(6)
                            val json = JSONObject(jsonData)
                            val choices = json.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val choice = choices.getJSONObject(0)
                                val delta = choice.optJSONObject("delta")
                                val content = delta?.optString("content", "") ?: ""
                                if (content.isNotEmpty()) {
                                    contentBuilder.append(content)
                                    callback.onToken(content)
                                }
                                val reason = choice.optString("finish_reason", null)
                                if (reason != null && reason != "null") {
                                    finishReason = reason
                                }
                            }
                            // Try to get usage if available
                            val usage = json.optJSONObject("usage")
                            if (usage != null) {
                                tokensUsed = usage.optInt("total_tokens", 0)
                            }
                        } catch (e: Exception) {
                            // Skip malformed chunks
                        }
                    }
                }
                reader.close()

                val response = AIResponse(
                    content = contentBuilder.toString(),
                    tokensUsed = tokensUsed,
                    model = model,
                    provider = name,
                    finishReason = finishReason
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
                message = "DeepSeek streaming request failed: ${e.message}",
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
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
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

        // Add system prompt if provided
        config.systemPrompt?.let { systemPrompt ->
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }

        // Add context messages
        context.forEach { msg ->
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
            put("temperature", config.temperature.toDouble())
            put("max_tokens", config.maxTokens)
            put("stream", stream)

            config.topP?.let { put("top_p", it.toDouble()) }
            config.frequencyPenalty?.let { put("frequency_penalty", it.toDouble()) }
            config.presencePenalty?.let { put("presence_penalty", it.toDouble()) }
        }
    }

    private fun parseResponse(responseText: String, model: String): AIResponse {
        val json = JSONObject(responseText)
        val choices = json.getJSONArray("choices")

        if (choices.length() == 0) {
            throw AIProviderException(
                message = "DeepSeek returned empty response",
                provider = name,
                isRetryable = true
            )
        }

        val choice = choices.getJSONObject(0)
        val message = choice.getJSONObject("message")
        val content = message.getString("content")
        val finishReason = choice.optString("finish_reason", null)

        val usage = json.optJSONObject("usage")
        val tokensUsed = usage?.optInt("total_tokens", 0) ?: 0

        return AIResponse(
            content = content,
            tokensUsed = tokensUsed,
            model = model,
            provider = name,
            finishReason = finishReason
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
        val isRetryable = responseCode in listOf(429, 500, 502, 503, 504)

        throw AIProviderException(
            message = "DeepSeek API error ($responseCode): $errorMessage",
            provider = name,
            statusCode = responseCode,
            isRateLimited = isRateLimited,
            isRetryable = isRetryable
        )
    }
}
