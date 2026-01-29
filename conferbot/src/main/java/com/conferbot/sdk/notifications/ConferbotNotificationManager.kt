package com.conferbot.sdk.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.conferbot.sdk.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manager class for creating and displaying Conferbot notifications.
 * Handles notification channels, building notifications, and displaying them.
 */
class ConferbotNotificationManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "ConferbotNotificationMgr"

        // Channel IDs
        const val CHANNEL_ID_MESSAGES = "conferbot_messages"
        const val CHANNEL_ID_AGENT_UPDATES = "conferbot_agent_updates"
        const val CHANNEL_ID_SYSTEM = "conferbot_system"

        // Notification IDs
        private const val BASE_NOTIFICATION_ID = 100000
        private val notificationIdCounter = AtomicInteger(BASE_NOTIFICATION_ID)

        // Intent extras
        const val EXTRA_NOTIFICATION_TYPE = "conferbot_notification_type"
        const val EXTRA_CHAT_SESSION_ID = "conferbot_chat_session_id"
        const val EXTRA_NOTIFICATION_DATA = "conferbot_notification_data"

        // Action constants
        const val ACTION_OPEN_CHAT = "com.conferbot.ACTION_OPEN_CHAT"
        const val ACTION_DISMISS = "com.conferbot.ACTION_DISMISS"
        const val ACTION_REPLY = "com.conferbot.ACTION_REPLY"

        @Volatile
        private var instance: ConferbotNotificationManager? = null

        fun getInstance(context: Context): ConferbotNotificationManager {
            return instance ?: synchronized(this) {
                instance ?: ConferbotNotificationManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private val systemNotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var settings: NotificationSettings = NotificationSettings.load(context)

    // Activity class to launch for deep linking
    private var chatActivityClass: Class<*>? = null

    // Custom small icon
    @DrawableRes
    private var smallIconRes: Int = R.drawable.ic_send // Default fallback

    init {
        createNotificationChannels()
    }

    /**
     * Create all required notification channels
     */
    fun createNotificationChannel() {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Messages channel
            val messagesChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new chat messages"
                enableVibration(settings.vibrationEnabled)
                enableLights(true)
                settings.ledColor?.let { lightColor = it }
                if (!settings.soundEnabled) {
                    setSound(null, null)
                }
            }

            // Agent updates channel
            val agentChannel = NotificationChannel(
                CHANNEL_ID_AGENT_UPDATES,
                "Agent Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for agent status changes"
                enableVibration(settings.vibrationEnabled)
            }

            // System channel
            val systemChannel = NotificationChannel(
                CHANNEL_ID_SYSTEM,
                "System Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "System notifications from Conferbot"
            }

            systemNotificationManager.createNotificationChannels(
                listOf(messagesChannel, agentChannel, systemChannel)
            )

            Log.d(TAG, "Notification channels created")
        }
    }

    /**
     * Set the activity class to launch when notification is tapped
     */
    fun setChatActivityClass(activityClass: Class<*>) {
        chatActivityClass = activityClass
    }

    /**
     * Set custom small icon for notifications
     */
    fun setSmallIcon(@DrawableRes iconRes: Int) {
        smallIconRes = iconRes
    }

    /**
     * Update notification settings
     */
    fun updateSettings(newSettings: NotificationSettings) {
        settings = newSettings
        settings.save(context)

        // Recreate channels if needed (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannels()
        }
    }

    /**
     * Get current notification settings
     */
    fun getSettings(): NotificationSettings = settings

    /**
     * Show a notification for a new message
     */
    fun showMessageNotification(
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        if (!settings.enabled || !settings.showNewMessages) return

        val chatSessionId = data["chatSessionId"]
        val displayBody = if (settings.showPreview) body else "New message"

        val notification = buildNotification(
            channelId = CHANNEL_ID_MESSAGES,
            title = title,
            body = displayBody,
            chatSessionId = chatSessionId,
            data = data,
            priority = NotificationCompat.PRIORITY_HIGH
        )

        val notificationId = getNotificationId()
        showNotification(notificationId, notification)
    }

    /**
     * Show a notification for an agent joining the chat
     */
    fun showAgentNotification(
        agentName: String,
        message: String
    ) {
        if (!settings.enabled || !settings.showAgentJoined) return

        val notification = buildNotification(
            channelId = CHANNEL_ID_AGENT_UPDATES,
            title = agentName,
            body = message,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        val notificationId = getNotificationId()
        showNotification(notificationId, notification)
    }

    /**
     * Show a notification for handover queue
     */
    fun showHandoverNotification(
        estimatedWait: Int
    ) {
        if (!settings.enabled || !settings.showQueueUpdates) return

        val message = if (estimatedWait > 0) {
            "Estimated wait time: $estimatedWait minutes"
        } else {
            "You are in queue for an agent"
        }

        val notification = buildNotification(
            channelId = CHANNEL_ID_AGENT_UPDATES,
            title = "Waiting for Agent",
            body = message,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        val notificationId = getNotificationId()
        showNotification(notificationId, notification)
    }

    /**
     * Show notification based on ConferbotNotification type
     */
    fun showNotification(conferbotNotification: ConferbotNotification) {
        if (!settings.shouldShow(conferbotNotification)) return

        when (conferbotNotification) {
            is ConferbotNotification.NewMessage -> {
                showNewMessageNotification(conferbotNotification)
            }
            is ConferbotNotification.AgentJoined -> {
                showAgentJoinedNotification(conferbotNotification)
            }
            is ConferbotNotification.AgentLeft -> {
                showAgentLeftNotification(conferbotNotification)
            }
            is ConferbotNotification.ChatEnded -> {
                showChatEndedNotification(conferbotNotification)
            }
            is ConferbotNotification.HandoverQueued -> {
                showHandoverQueuedNotification(conferbotNotification)
            }
            is ConferbotNotification.QueuePositionUpdate -> {
                showQueuePositionUpdateNotification(conferbotNotification)
            }
            is ConferbotNotification.SystemNotification -> {
                showSystemNotification(conferbotNotification)
            }
        }
    }

    private fun showNewMessageNotification(notification: ConferbotNotification.NewMessage) {
        val title = notification.from
        val body = if (settings.showPreview) notification.content else "New message"

        val builder = buildNotificationBuilder(
            channelId = CHANNEL_ID_MESSAGES,
            title = title,
            body = body,
            chatSessionId = notification.chatSessionId,
            priority = NotificationCompat.PRIORITY_HIGH
        )

        // Load avatar if available
        notification.avatarUrl?.let { avatarUrl ->
            scope.launch {
                val bitmap = loadBitmapFromUrl(avatarUrl)
                bitmap?.let {
                    builder.setLargeIcon(it)
                    showNotification(getNotificationId(), builder.build())
                }
            }
        } ?: run {
            showNotification(getNotificationId(), builder.build())
        }
    }

    private fun showAgentJoinedNotification(notification: ConferbotNotification.AgentJoined) {
        val builder = buildNotificationBuilder(
            channelId = CHANNEL_ID_AGENT_UPDATES,
            title = "Agent Joined",
            body = "${notification.agentName} has joined the chat",
            chatSessionId = notification.chatSessionId,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        // Load avatar if available
        notification.agentAvatarUrl?.let { avatarUrl ->
            scope.launch {
                val bitmap = loadBitmapFromUrl(avatarUrl)
                bitmap?.let {
                    builder.setLargeIcon(it)
                    showNotification(getNotificationId(), builder.build())
                }
            }
        } ?: run {
            showNotification(getNotificationId(), builder.build())
        }
    }

    private fun showAgentLeftNotification(notification: ConferbotNotification.AgentLeft) {
        val systemNotification = buildNotification(
            channelId = CHANNEL_ID_AGENT_UPDATES,
            title = "Agent Left",
            body = "${notification.agentName} has left the chat",
            chatSessionId = notification.chatSessionId,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        showNotification(getNotificationId(), systemNotification)
    }

    private fun showChatEndedNotification(notification: ConferbotNotification.ChatEnded) {
        val systemNotification = buildNotification(
            channelId = CHANNEL_ID_SYSTEM,
            title = "Chat Ended",
            body = notification.reason,
            chatSessionId = notification.chatSessionId,
            priority = NotificationCompat.PRIORITY_LOW
        )

        showNotification(getNotificationId(), systemNotification)
    }

    private fun showHandoverQueuedNotification(notification: ConferbotNotification.HandoverQueued) {
        val message = buildString {
            append("You are #${notification.position} in queue")
            notification.estimatedWaitMinutes?.let {
                append(". Estimated wait: $it minutes")
            }
        }

        val systemNotification = buildNotification(
            channelId = CHANNEL_ID_AGENT_UPDATES,
            title = "Waiting for Agent",
            body = message,
            chatSessionId = notification.chatSessionId,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )

        showNotification(getNotificationId(), systemNotification)
    }

    private fun showQueuePositionUpdateNotification(notification: ConferbotNotification.QueuePositionUpdate) {
        val message = buildString {
            append("Your position: #${notification.newPosition}")
            notification.estimatedWaitMinutes?.let {
                append(". Estimated wait: $it minutes")
            }
        }

        val systemNotification = buildNotification(
            channelId = CHANNEL_ID_AGENT_UPDATES,
            title = "Queue Update",
            body = message,
            chatSessionId = notification.chatSessionId,
            priority = NotificationCompat.PRIORITY_LOW
        )

        showNotification(getNotificationId(), systemNotification)
    }

    private fun showSystemNotification(notification: ConferbotNotification.SystemNotification) {
        val systemNotification = buildNotification(
            channelId = CHANNEL_ID_SYSTEM,
            title = notification.title,
            body = notification.message,
            data = notification.data,
            priority = NotificationCompat.PRIORITY_LOW
        )

        showNotification(getNotificationId(), systemNotification)
    }

    private fun buildNotificationBuilder(
        channelId: String,
        title: String,
        body: String,
        chatSessionId: String? = null,
        data: Map<String, String> = emptyMap(),
        priority: Int = NotificationCompat.PRIORITY_DEFAULT
    ): NotificationCompat.Builder {
        val pendingIntent = createPendingIntent(chatSessionId, data)

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIconRes)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .apply {
                if (settings.vibrationEnabled) {
                    setVibrate(longArrayOf(0, 250, 250, 250))
                }
            }
    }

    private fun buildNotification(
        channelId: String,
        title: String,
        body: String,
        chatSessionId: String? = null,
        data: Map<String, String> = emptyMap(),
        priority: Int = NotificationCompat.PRIORITY_DEFAULT
    ): android.app.Notification {
        return buildNotificationBuilder(channelId, title, body, chatSessionId, data, priority).build()
    }

    private fun createPendingIntent(
        chatSessionId: String?,
        data: Map<String, String> = emptyMap()
    ): PendingIntent {
        val intent = if (chatActivityClass != null) {
            Intent(context, chatActivityClass).apply {
                action = ACTION_OPEN_CHAT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                chatSessionId?.let { putExtra(EXTRA_CHAT_SESSION_ID, it) }
                putExtra(EXTRA_NOTIFICATION_DATA, HashMap(data))
            }
        } else {
            // Use launcher intent as fallback
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                action = ACTION_OPEN_CHAT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                chatSessionId?.let { putExtra(EXTRA_CHAT_SESSION_ID, it) }
                putExtra(EXTRA_NOTIFICATION_DATA, HashMap(data))
            } ?: Intent()
        }

        return PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showNotification(id: Int, notification: android.app.Notification) {
        try {
            notificationManager.notify(id, notification)
            Log.d(TAG, "Notification shown with ID: $id")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to show notification", e)
        }
    }

    /**
     * Cancel all Conferbot notifications
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
        Log.d(TAG, "All notifications cancelled")
    }

    /**
     * Cancel a specific notification by ID
     */
    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
        Log.d(TAG, "Notification cancelled: $id")
    }

    /**
     * Cancel notifications for a specific chat session
     */
    fun cancelNotificationsForSession(chatSessionId: String) {
        // For Android N+, we could use notification groups
        // For now, just log
        Log.d(TAG, "Cancelling notifications for session: $chatSessionId")
    }

    /**
     * Check if notifications are enabled at the system level
     */
    fun areNotificationsEnabled(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }

    /**
     * Get a unique notification ID
     */
    private fun getNotificationId(): Int {
        return notificationIdCounter.incrementAndGet()
    }

    /**
     * Load bitmap from URL for notification large icon
     */
    private suspend fun loadBitmapFromUrl(url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                val inputStream = connection.getInputStream()
                BitmapFactory.decodeStream(inputStream)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bitmap from URL: $url", e)
                null
            }
        }
    }
}
