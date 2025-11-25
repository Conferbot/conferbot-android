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
 * SDK configuration
 */
data class ConferBotConfig(
    val enableNotifications: Boolean = true,
    val enableOfflineMode: Boolean = true,
    val autoConnect: Boolean = true,
    val reconnectionAttempts: Int? = null,
    val reconnectionDelay: Int? = null
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
