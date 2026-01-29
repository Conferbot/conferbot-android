package com.conferbot.sdk.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Notification settings for Conferbot SDK.
 * Controls how notifications are displayed to the user.
 */
data class NotificationSettings(
    /**
     * Master switch for notifications
     */
    val enabled: Boolean = true,

    /**
     * Whether to play sound with notifications
     */
    val soundEnabled: Boolean = true,

    /**
     * Whether to vibrate with notifications
     */
    val vibrationEnabled: Boolean = true,

    /**
     * Whether to show message preview in notifications
     */
    val showPreview: Boolean = true,

    /**
     * Whether to show notifications when app is in foreground
     */
    val showInForeground: Boolean = false,

    /**
     * Show notifications for new messages
     */
    val showNewMessages: Boolean = true,

    /**
     * Show notifications when agent joins
     */
    val showAgentJoined: Boolean = true,

    /**
     * Show notifications when agent leaves
     */
    val showAgentLeft: Boolean = true,

    /**
     * Show notifications when chat ends
     */
    val showChatEnded: Boolean = true,

    /**
     * Show notifications for queue updates
     */
    val showQueueUpdates: Boolean = true,

    /**
     * Custom notification channel ID (if null, uses default)
     */
    val customChannelId: String? = null,

    /**
     * Custom notification sound URI (if null, uses default)
     */
    val customSoundUri: String? = null,

    /**
     * LED color for notifications (ARGB int, null for default)
     */
    val ledColor: Int? = null,

    /**
     * Priority level for notifications (0-4, where 4 is max)
     */
    val priority: Int = 3
) {
    companion object {
        private const val PREFS_NAME = "conferbot_notification_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_SHOW_PREVIEW = "show_preview"
        private const val KEY_SHOW_IN_FOREGROUND = "show_in_foreground"
        private const val KEY_SHOW_NEW_MESSAGES = "show_new_messages"
        private const val KEY_SHOW_AGENT_JOINED = "show_agent_joined"
        private const val KEY_SHOW_AGENT_LEFT = "show_agent_left"
        private const val KEY_SHOW_CHAT_ENDED = "show_chat_ended"
        private const val KEY_SHOW_QUEUE_UPDATES = "show_queue_updates"
        private const val KEY_CUSTOM_CHANNEL_ID = "custom_channel_id"
        private const val KEY_CUSTOM_SOUND_URI = "custom_sound_uri"
        private const val KEY_LED_COLOR = "led_color"
        private const val KEY_PRIORITY = "priority"

        /**
         * Load settings from SharedPreferences
         */
        fun load(context: Context): NotificationSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return NotificationSettings(
                enabled = prefs.getBoolean(KEY_ENABLED, true),
                soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true),
                vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true),
                showPreview = prefs.getBoolean(KEY_SHOW_PREVIEW, true),
                showInForeground = prefs.getBoolean(KEY_SHOW_IN_FOREGROUND, false),
                showNewMessages = prefs.getBoolean(KEY_SHOW_NEW_MESSAGES, true),
                showAgentJoined = prefs.getBoolean(KEY_SHOW_AGENT_JOINED, true),
                showAgentLeft = prefs.getBoolean(KEY_SHOW_AGENT_LEFT, true),
                showChatEnded = prefs.getBoolean(KEY_SHOW_CHAT_ENDED, true),
                showQueueUpdates = prefs.getBoolean(KEY_SHOW_QUEUE_UPDATES, true),
                customChannelId = prefs.getString(KEY_CUSTOM_CHANNEL_ID, null),
                customSoundUri = prefs.getString(KEY_CUSTOM_SOUND_URI, null),
                ledColor = if (prefs.contains(KEY_LED_COLOR)) prefs.getInt(KEY_LED_COLOR, 0) else null,
                priority = prefs.getInt(KEY_PRIORITY, 3)
            )
        }

        /**
         * Create default settings
         */
        fun default(): NotificationSettings = NotificationSettings()
    }

    /**
     * Save settings to SharedPreferences
     */
    fun save(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ENABLED, enabled)
            putBoolean(KEY_SOUND_ENABLED, soundEnabled)
            putBoolean(KEY_VIBRATION_ENABLED, vibrationEnabled)
            putBoolean(KEY_SHOW_PREVIEW, showPreview)
            putBoolean(KEY_SHOW_IN_FOREGROUND, showInForeground)
            putBoolean(KEY_SHOW_NEW_MESSAGES, showNewMessages)
            putBoolean(KEY_SHOW_AGENT_JOINED, showAgentJoined)
            putBoolean(KEY_SHOW_AGENT_LEFT, showAgentLeft)
            putBoolean(KEY_SHOW_CHAT_ENDED, showChatEnded)
            putBoolean(KEY_SHOW_QUEUE_UPDATES, showQueueUpdates)
            if (customChannelId != null) {
                putString(KEY_CUSTOM_CHANNEL_ID, customChannelId)
            } else {
                remove(KEY_CUSTOM_CHANNEL_ID)
            }
            if (customSoundUri != null) {
                putString(KEY_CUSTOM_SOUND_URI, customSoundUri)
            } else {
                remove(KEY_CUSTOM_SOUND_URI)
            }
            if (ledColor != null) {
                putInt(KEY_LED_COLOR, ledColor)
            } else {
                remove(KEY_LED_COLOR)
            }
            putInt(KEY_PRIORITY, priority)
        }
    }

    /**
     * Check if a specific notification type should be shown
     */
    fun shouldShow(notification: ConferbotNotification): Boolean {
        if (!enabled) return false

        return when (notification) {
            is ConferbotNotification.NewMessage -> showNewMessages
            is ConferbotNotification.AgentJoined -> showAgentJoined
            is ConferbotNotification.AgentLeft -> showAgentLeft
            is ConferbotNotification.ChatEnded -> showChatEnded
            is ConferbotNotification.HandoverQueued -> showQueueUpdates
            is ConferbotNotification.QueuePositionUpdate -> showQueueUpdates
            is ConferbotNotification.SystemNotification -> true
        }
    }

    /**
     * Create a copy with updated values
     */
    fun copyWithUpdates(
        enabled: Boolean? = null,
        soundEnabled: Boolean? = null,
        vibrationEnabled: Boolean? = null,
        showPreview: Boolean? = null,
        showInForeground: Boolean? = null
    ): NotificationSettings {
        return copy(
            enabled = enabled ?: this.enabled,
            soundEnabled = soundEnabled ?: this.soundEnabled,
            vibrationEnabled = vibrationEnabled ?: this.vibrationEnabled,
            showPreview = showPreview ?: this.showPreview,
            showInForeground = showInForeground ?: this.showInForeground
        )
    }
}
