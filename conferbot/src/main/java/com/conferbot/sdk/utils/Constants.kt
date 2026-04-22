package com.conferbot.sdk.utils

import android.util.Log

/**
 * Configurable endpoint overrides for the Conferbot SDK.
 * Call setApiBaseUrl / setSocketUrl before Conferbot.initialize() to override defaults.
 * Only HTTPS URLs are accepted.
 */
object ConferBotEndpoints {
    private const val TAG = "ConferBotEndpoints"

    @Volatile
    private var _apiBaseUrl: String = Constants.DEFAULT_API_BASE_URL

    @Volatile
    private var _socketUrl: String = Constants.DEFAULT_SOCKET_URL

    /** Current API base URL (always HTTPS). */
    val apiBaseUrl: String get() = _apiBaseUrl

    /** Current Socket URL (always HTTPS). */
    val socketUrl: String get() = _socketUrl

    /**
     * Override the API base URL. Must use HTTPS.
     * @throws IllegalArgumentException if the URL does not start with https://
     */
    fun setApiBaseUrl(url: String) {
        require(url.startsWith("https://")) {
            "API base URL must use HTTPS: $url"
        }
        // Ensure trailing slash for Retrofit relative path resolution
        val normalizedUrl = if (url.endsWith("/")) url else "$url/"
        Log.d(TAG, "API base URL overridden to: $normalizedUrl")
        _apiBaseUrl = normalizedUrl
    }

    /**
     * Override the Socket URL. Must use HTTPS.
     * @throws IllegalArgumentException if the URL does not start with https://
     */
    fun setSocketUrl(url: String) {
        require(url.startsWith("https://")) {
            "Socket URL must use HTTPS: $url"
        }
        Log.d(TAG, "Socket URL overridden to: $url")
        _socketUrl = url
    }

    /** Reset both URLs to their defaults. */
    fun resetToDefaults() {
        _apiBaseUrl = Constants.DEFAULT_API_BASE_URL
        _socketUrl = Constants.DEFAULT_SOCKET_URL
    }
}

/**
 * Conferbot SDK constants
 */
object Constants {
    // API Configuration
    const val DEFAULT_API_BASE_URL = "http://10.0.2.2:8001/api/v1/mobile/"
    const val DEFAULT_SOCKET_URL = "http://10.0.2.2:8001"
    const val API_TIMEOUT = 30000L // 30 seconds
    const val SOCKET_TIMEOUT = 20000L // 20 seconds

    // Headers
    const val HEADER_API_KEY = "X-API-Key"
    const val HEADER_BOT_ID = "X-Bot-ID"
    const val HEADER_PLATFORM = "X-Platform"

    // Platform identifier
    const val PLATFORM_IDENTIFIER = "android"

    // Socket configuration
    const val SOCKET_RECONNECTION_ATTEMPTS = 5
    const val SOCKET_RECONNECTION_DELAY = 1000
    const val SOCKET_RECONNECTION_DELAY_MAX = 5000

    // Message limits
    const val MAX_MESSAGE_LENGTH = 5000
    const val MAX_FILE_SIZE = 10485760 // 10MB

    // UI Constants
    const val TYPING_INDICATOR_DURATION = 3000
    const val MESSAGE_ANIMATION_DURATION = 300
}

/**
 * Configurable network timeouts and retry policies.
 * Call configure() before Conferbot.initialize() to override defaults.
 */
object ConferBotNetworkConfig {
    var apiTimeout: Long = Constants.API_TIMEOUT
        private set
    var socketTimeout: Long = Constants.SOCKET_TIMEOUT
        private set
    var reconnectionAttempts: Int = Constants.SOCKET_RECONNECTION_ATTEMPTS
        private set
    var reconnectionDelay: Int = Constants.SOCKET_RECONNECTION_DELAY
        private set
    var reconnectionDelayMax: Int = Constants.SOCKET_RECONNECTION_DELAY_MAX
        private set

    fun configure(
        apiTimeout: Long? = null,
        socketTimeout: Long? = null,
        reconnectionAttempts: Int? = null,
        reconnectionDelay: Int? = null,
        reconnectionDelayMax: Int? = null
    ) {
        apiTimeout?.let { require(it > 0) { "API timeout must be positive" }; this.apiTimeout = it }
        socketTimeout?.let { require(it > 0) { "Socket timeout must be positive" }; this.socketTimeout = it }
        reconnectionAttempts?.let { require(it >= 0) { "Reconnection attempts must be non-negative" }; this.reconnectionAttempts = it }
        reconnectionDelay?.let { require(it > 0) { "Reconnection delay must be positive" }; this.reconnectionDelay = it }
        reconnectionDelayMax?.let { require(it > 0) { "Max delay must be positive" }; this.reconnectionDelayMax = it }
    }

    fun reset() {
        apiTimeout = Constants.API_TIMEOUT
        socketTimeout = Constants.SOCKET_TIMEOUT
        reconnectionAttempts = Constants.SOCKET_RECONNECTION_ATTEMPTS
        reconnectionDelay = Constants.SOCKET_RECONNECTION_DELAY
        reconnectionDelayMax = Constants.SOCKET_RECONNECTION_DELAY_MAX
    }
}
