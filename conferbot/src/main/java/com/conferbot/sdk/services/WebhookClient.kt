package com.conferbot.sdk.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Cached token with expiration
 */
data class CachedToken(
    val token: String,
    val expiresAt: Long
) {
    fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt

    fun isExpiringSoon(marginMs: Long = 60_000): Boolean =
        System.currentTimeMillis() >= (expiresAt - marginMs)
}

/**
 * Webhook request configuration
 */
data class WebhookRequest(
    val url: String,
    val method: String = "POST",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val authentication: WebhookAuthentication? = null,
    val timeoutMs: Long = 30_000,
    val maxRetries: Int = 3
)

/**
 * Authentication configuration for webhooks
 */
sealed class WebhookAuthentication {
    /**
     * Bearer token authentication
     */
    data class Bearer(val token: String) : WebhookAuthentication()

    /**
     * Basic authentication (username:password)
     */
    data class Basic(val username: String, val password: String) : WebhookAuthentication()

    /**
     * API Key authentication
     */
    data class ApiKey(
        val key: String,
        val value: String,
        val location: ApiKeyLocation = ApiKeyLocation.HEADER
    ) : WebhookAuthentication() {
        enum class ApiKeyLocation { HEADER, QUERY }
    }

    /**
     * OAuth2 authentication with token endpoint
     */
    data class OAuth2(
        val tokenUrl: String,
        val clientId: String,
        val clientSecret: String,
        val scope: String? = null,
        val grantType: String = "client_credentials"
    ) : WebhookAuthentication()

    /**
     * Custom OAuth/Token endpoint authentication
     */
    data class CustomToken(
        val tokenUrl: String,
        val username: String,
        val password: String,
        val tokenPath: String = "access_token",
        val expiresInPath: String = "expires_in"
    ) : WebhookAuthentication()
}

/**
 * Webhook response
 */
data class WebhookResponse(
    val success: Boolean,
    val statusCode: Int,
    val body: String?,
    val headers: Map<String, String> = emptyMap(),
    val error: String? = null,
    val retryCount: Int = 0
)

/**
 * Exception for webhook errors
 */
class WebhookException(
    message: String,
    val statusCode: Int? = null,
    val isRetryable: Boolean = false,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Thread-safe webhook client with token caching, retry logic, and rate limiting
 */
class WebhookClient(
    private val httpClient: OkHttpClient = createDefaultClient(),
    private val rateLimiter: RateLimiter = RateLimiter(maxRequests = 100, perMillis = 60_000)
) {
    // Thread-safe token cache
    private val tokenCache = ConcurrentHashMap<String, CachedToken>()

    // Mutex for token refresh operations to prevent concurrent token fetches
    private val tokenRefreshMutex = Mutex()

    // Request tracking for debugging
    private val requestCounter = AtomicLong(0)

    companion object {
        private const val TAG = "WebhookClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()

        // Retryable HTTP status codes
        private val RETRYABLE_STATUS_CODES = setOf(408, 429, 500, 502, 503, 504)

        fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    /**
     * Execute a webhook request with retry logic
     */
    suspend fun execute(request: WebhookRequest): WebhookResponse {
        return withContext(Dispatchers.IO) {
            // Acquire rate limit permit
            rateLimiter.acquire()

            executeWithRetry(request, request.maxRetries)
        }
    }

    /**
     * Execute request with exponential backoff retry logic
     */
    private suspend fun executeWithRetry(
        request: WebhookRequest,
        maxRetries: Int
    ): WebhookResponse {
        var lastException: Exception? = null
        var lastResponse: WebhookResponse? = null

        repeat(maxRetries) { attempt ->
            try {
                val response = executeOnce(request)

                // If successful, return immediately
                if (response.success) {
                    return response.copy(retryCount = attempt)
                }

                // Check if error is retryable
                if (!isRetryableStatus(response.statusCode)) {
                    return response.copy(retryCount = attempt)
                }

                lastResponse = response

                // Exponential backoff with jitter
                val backoffMs = calculateBackoff(attempt)
                delay(backoffMs)

            } catch (e: Exception) {
                lastException = e

                // Check if exception is retryable
                if (!isRetryableException(e)) {
                    throw e
                }

                // Exponential backoff with jitter
                val backoffMs = calculateBackoff(attempt)
                delay(backoffMs)
            }
        }

        // All retries exhausted
        return lastResponse?.copy(retryCount = maxRetries) ?: WebhookResponse(
            success = false,
            statusCode = -1,
            body = null,
            error = lastException?.message ?: "All retries exhausted"
        )
    }

    /**
     * Execute a single request
     */
    private suspend fun executeOnce(webhookRequest: WebhookRequest): WebhookResponse {
        val requestId = requestCounter.incrementAndGet()

        try {
            // Build the HTTP request
            val requestBuilder = Request.Builder()

            // Handle URL with potential query parameters for API key auth
            var url = webhookRequest.url
            if (webhookRequest.authentication is WebhookAuthentication.ApiKey) {
                val apiKeyAuth = webhookRequest.authentication
                if (apiKeyAuth.location == WebhookAuthentication.ApiKey.ApiKeyLocation.QUERY) {
                    url = appendQueryParam(url, apiKeyAuth.key, apiKeyAuth.value)
                }
            }

            requestBuilder.url(url)

            // Add headers
            webhookRequest.headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            // Add authentication header
            addAuthenticationHeader(requestBuilder, webhookRequest.authentication)

            // Set method and body
            val body = webhookRequest.body?.toRequestBody(JSON_MEDIA_TYPE)
            when (webhookRequest.method.uppercase()) {
                "GET" -> requestBuilder.get()
                "POST" -> requestBuilder.post(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
                "PUT" -> requestBuilder.put(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
                "PATCH" -> requestBuilder.patch(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
                "DELETE" -> {
                    if (body != null) {
                        requestBuilder.delete(body)
                    } else {
                        requestBuilder.delete()
                    }
                }
                else -> requestBuilder.post(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
            }

            // Create client with custom timeout if needed
            val client = if (webhookRequest.timeoutMs != 30_000L) {
                httpClient.newBuilder()
                    .connectTimeout(webhookRequest.timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(webhookRequest.timeoutMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(webhookRequest.timeoutMs, TimeUnit.MILLISECONDS)
                    .build()
            } else {
                httpClient
            }

            // Execute the request
            val response = client.newCall(requestBuilder.build()).execute()

            val responseBody = response.body?.string()
            val responseHeaders = response.headers.toMultimap()
                .mapValues { it.value.firstOrNull() ?: "" }

            return WebhookResponse(
                success = response.isSuccessful,
                statusCode = response.code,
                body = responseBody,
                headers = responseHeaders,
                error = if (!response.isSuccessful) "HTTP ${response.code}: ${response.message}" else null
            )

        } catch (e: IOException) {
            return WebhookResponse(
                success = false,
                statusCode = -1,
                body = null,
                error = "Network error: ${e.message}"
            )
        } catch (e: Exception) {
            return WebhookResponse(
                success = false,
                statusCode = -1,
                body = null,
                error = "Request error: ${e.message}"
            )
        }
    }

    /**
     * Add authentication header to request
     */
    private suspend fun addAuthenticationHeader(
        requestBuilder: Request.Builder,
        authentication: WebhookAuthentication?
    ) {
        when (authentication) {
            is WebhookAuthentication.Bearer -> {
                requestBuilder.addHeader("Authorization", "Bearer ${authentication.token}")
            }

            is WebhookAuthentication.Basic -> {
                val credentials = "${authentication.username}:${authentication.password}"
                val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
                requestBuilder.addHeader("Authorization", "Basic $encoded")
            }

            is WebhookAuthentication.ApiKey -> {
                if (authentication.location == WebhookAuthentication.ApiKey.ApiKeyLocation.HEADER) {
                    requestBuilder.addHeader(authentication.key, authentication.value)
                }
                // Query parameter is handled in URL building
            }

            is WebhookAuthentication.OAuth2 -> {
                val token = getOrFetchOAuth2Token(authentication)
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            is WebhookAuthentication.CustomToken -> {
                val token = getOrFetchCustomToken(authentication)
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            null -> { /* No authentication */ }
        }
    }

    /**
     * Get cached OAuth2 token or fetch a new one
     */
    private suspend fun getOrFetchOAuth2Token(auth: WebhookAuthentication.OAuth2): String {
        val cacheKey = "oauth2:${auth.tokenUrl}:${auth.clientId}"

        // Check cache first
        getCachedToken(cacheKey)?.let { return it }

        // Fetch new token with mutex to prevent concurrent fetches
        return tokenRefreshMutex.withLock {
            // Double-check after acquiring lock
            getCachedToken(cacheKey)?.let { return@withLock it }

            // Fetch new token
            val token = fetchOAuth2Token(auth)
            token
        }
    }

    /**
     * Fetch OAuth2 token from token endpoint
     */
    private fun fetchOAuth2Token(auth: WebhookAuthentication.OAuth2): String {
        val bodyBuilder = StringBuilder()
            .append("grant_type=${auth.grantType}")
            .append("&client_id=${auth.clientId}")
            .append("&client_secret=${auth.clientSecret}")

        auth.scope?.let { bodyBuilder.append("&scope=$it") }

        val request = Request.Builder()
            .url(auth.tokenUrl)
            .post(bodyBuilder.toString().toRequestBody(FORM_MEDIA_TYPE))
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw WebhookException(
                "Failed to fetch OAuth2 token: HTTP ${response.code}",
                statusCode = response.code
            )
        }

        val responseBody = response.body?.string()
            ?: throw WebhookException("Empty response from OAuth2 token endpoint")

        val json = JSONObject(responseBody)
        val token = json.optString("access_token")
            .takeIf { it.isNotEmpty() }
            ?: throw WebhookException("No access_token in OAuth2 response")

        val expiresIn = json.optLong("expires_in", 3600) // Default 1 hour

        // Cache the token
        cacheToken("oauth2:${auth.tokenUrl}:${auth.clientId}", token, expiresIn)

        return token
    }

    /**
     * Get cached custom token or fetch a new one
     */
    private suspend fun getOrFetchCustomToken(auth: WebhookAuthentication.CustomToken): String {
        val cacheKey = "custom:${auth.tokenUrl}:${auth.username}"

        // Check cache first
        getCachedToken(cacheKey)?.let { return it }

        // Fetch new token with mutex to prevent concurrent fetches
        return tokenRefreshMutex.withLock {
            // Double-check after acquiring lock
            getCachedToken(cacheKey)?.let { return@withLock it }

            // Fetch new token
            val token = fetchCustomToken(auth)
            token
        }
    }

    /**
     * Fetch custom token from token endpoint
     */
    private fun fetchCustomToken(auth: WebhookAuthentication.CustomToken): String {
        val requestBody = JSONObject().apply {
            put("username", auth.username)
            put("password", auth.password)
        }

        val request = Request.Builder()
            .url(auth.tokenUrl)
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .build()

        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw WebhookException(
                "Failed to fetch custom token: HTTP ${response.code}",
                statusCode = response.code
            )
        }

        val responseBody = response.body?.string()
            ?: throw WebhookException("Empty response from token endpoint")

        val json = JSONObject(responseBody)

        // Navigate to token using dot-notation path
        val token = getJsonValue(json, auth.tokenPath)
            ?: throw WebhookException("No token found at path: ${auth.tokenPath}")

        val expiresIn = getJsonValue(json, auth.expiresInPath)?.toLongOrNull() ?: 3600L

        // Cache the token
        cacheToken("custom:${auth.tokenUrl}:${auth.username}", token, expiresIn)

        return token
    }

    /**
     * Get cached token if not expired
     */
    private fun getCachedToken(key: String): String? {
        val cached = tokenCache[key] ?: return null

        if (cached.isExpired()) {
            tokenCache.remove(key)
            return null
        }

        return cached.token
    }

    /**
     * Cache a token with expiration
     */
    private fun cacheToken(key: String, token: String, expiresInSeconds: Long) {
        // Subtract 60 seconds as buffer to ensure we refresh before actual expiration
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000) - 60_000
        tokenCache[key] = CachedToken(token, expiresAt)
    }

    /**
     * Clear all cached tokens
     */
    fun clearTokenCache() {
        tokenCache.clear()
    }

    /**
     * Clear a specific cached token
     */
    fun clearCachedToken(key: String) {
        tokenCache.remove(key)
    }

    /**
     * Calculate exponential backoff with jitter
     */
    private fun calculateBackoff(attempt: Int): Long {
        val baseDelay = 1000L // 1 second
        val maxDelay = 30_000L // 30 seconds
        val exponentialDelay = baseDelay * (1L shl attempt.coerceAtMost(5))
        val jitter = (Math.random() * exponentialDelay * 0.1).toLong()
        return (exponentialDelay + jitter).coerceAtMost(maxDelay)
    }

    /**
     * Check if HTTP status code is retryable
     */
    private fun isRetryableStatus(statusCode: Int): Boolean {
        return statusCode in RETRYABLE_STATUS_CODES
    }

    /**
     * Check if exception is retryable
     */
    private fun isRetryableException(e: Exception): Boolean {
        return when (e) {
            is IOException -> true
            is WebhookException -> e.isRetryable
            else -> false
        }
    }

    /**
     * Append query parameter to URL
     */
    private fun appendQueryParam(url: String, key: String, value: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        return "$url$separator$key=$value"
    }

    /**
     * Get nested JSON value using dot notation
     */
    private fun getJsonValue(json: JSONObject, path: String): String? {
        val parts = path.split(".")
        var current: Any = json

        for (part in parts) {
            current = when (current) {
                is JSONObject -> current.opt(part) ?: return null
                else -> return null
            }
        }

        return current.toString().takeIf { it.isNotEmpty() && it != "null" }
    }
}
