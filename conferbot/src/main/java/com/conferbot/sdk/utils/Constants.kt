package com.conferbot.sdk.utils

/**
 * Conferbot SDK constants
 */
object Constants {
    // API Configuration
    const val DEFAULT_API_BASE_URL = "https://embed.conferbot.com/api/v1/mobile"
    const val DEFAULT_SOCKET_URL = "https://embed.conferbot.com"
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
