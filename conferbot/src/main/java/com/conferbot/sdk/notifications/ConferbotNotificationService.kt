package com.conferbot.sdk.notifications

import android.util.Log
import com.conferbot.sdk.core.Conferbot
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Messaging Service for handling Conferbot push notifications.
 *
 * This service receives push notifications from Firebase Cloud Messaging and:
 * 1. Handles incoming notification messages
 * 2. Manages FCM token refresh
 * 3. Delegates to NotificationHandler for processing
 *
 * Add to AndroidManifest.xml:
 * ```xml
 * <service
 *     android:name="com.conferbot.sdk.notifications.ConferbotNotificationService"
 *     android:exported="false">
 *     <intent-filter>
 *         <action android:name="com.google.firebase.MESSAGING_EVENT" />
 *     </intent-filter>
 * </service>
 * ```
 */
class ConferbotNotificationService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "ConferbotFCMService"

        // Conferbot notification type key in data payload
        private const val KEY_NOTIFICATION_TYPE = "type"
        private const val KEY_CONFERBOT_SOURCE = "source"
        private const val CONFERBOT_SOURCE = "conferbot"
    }

    private val notificationManager: ConferbotNotificationManager by lazy {
        ConferbotNotificationManager.getInstance(applicationContext)
    }

    private val notificationHandler: NotificationHandler by lazy {
        NotificationHandler.getInstance(applicationContext)
    }

    /**
     * Called when a new FCM message is received.
     *
     * This is invoked when:
     * - App is in foreground: Always called
     * - App is in background with data payload: Called
     * - App is in background with notification payload: Not called (system handles)
     *
     * @param message The received FCM message
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "FCM message received from: ${message.from}")
        Log.d(TAG, "Message data: ${message.data}")

        // Check if this is a Conferbot notification
        if (!isConferbotNotification(message)) {
            Log.d(TAG, "Not a Conferbot notification, ignoring")
            return
        }

        // Parse the notification from data payload
        val data = message.data
        val conferbotNotification = ConferbotNotification.fromPushData(data)

        if (conferbotNotification != null) {
            // Handle the notification through NotificationHandler
            notificationHandler.handleNotification(conferbotNotification)
        } else {
            // Fallback: Use notification payload if available
            message.notification?.let { notification ->
                handleNotificationPayload(notification, data)
            }
        }
    }

    /**
     * Called when FCM token is refreshed.
     *
     * This happens when:
     * - App is installed for the first time
     * - User uninstalls/reinstalls the app
     * - User clears app data
     * - Token is rotated by Firebase
     *
     * @param token The new FCM registration token
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.d(TAG, "New FCM token received")

        // Register the new token with Conferbot
        registerTokenWithConferbot(token)

        // Notify any listeners about token refresh
        NotificationTokenListener.onTokenRefreshed(token)
    }

    /**
     * Check if the received message is from Conferbot
     */
    private fun isConferbotNotification(message: RemoteMessage): Boolean {
        val data = message.data

        // Check for explicit Conferbot source
        if (data[KEY_CONFERBOT_SOURCE] == CONFERBOT_SOURCE) {
            return true
        }

        // Check for Conferbot notification types
        val type = data[KEY_NOTIFICATION_TYPE]
        return type != null && isConferbotNotificationType(type)
    }

    /**
     * Check if the notification type is a known Conferbot type
     */
    private fun isConferbotNotificationType(type: String): Boolean {
        return type in listOf(
            "conferbot_message",
            "new_message",
            "agent_joined",
            "agent_left",
            "chat_ended",
            "handover_queued",
            "queue_position_update",
            "system"
        )
    }

    /**
     * Handle notification payload (when notification body is present)
     */
    private fun handleNotificationPayload(
        notification: RemoteMessage.Notification,
        data: Map<String, String>
    ) {
        val title = notification.title ?: "Conferbot"
        val body = notification.body ?: ""

        Log.d(TAG, "Handling notification payload: $title - $body")

        // Show the notification
        notificationManager.showMessageNotification(
            title = title,
            body = body,
            data = data
        )
    }

    /**
     * Register the FCM token with Conferbot server
     */
    private fun registerTokenWithConferbot(token: String) {
        try {
            // Use the Conferbot SDK to register the token
            Conferbot.registerPushToken(token)
            Log.d(TAG, "Push token registered with Conferbot")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register push token with Conferbot", e)
        }
    }

    /**
     * Called when message delivery was unsuccessful.
     * Can be used for analytics or retry logic.
     */
    override fun onDeletedMessages() {
        super.onDeletedMessages()
        Log.d(TAG, "FCM messages deleted (too many pending)")
    }

    /**
     * Called when there is an error sending upstream message.
     */
    override fun onSendError(msgId: String, exception: Exception) {
        super.onSendError(msgId, exception)
        Log.e(TAG, "FCM send error for message: $msgId", exception)
    }

    /**
     * Called when upstream message is successfully sent.
     */
    override fun onMessageSent(msgId: String) {
        super.onMessageSent(msgId)
        Log.d(TAG, "FCM message sent successfully: $msgId")
    }
}

/**
 * Listener interface for FCM token refresh events.
 * Implement this to get notified when the FCM token changes.
 */
object NotificationTokenListener {
    private val listeners = mutableListOf<(String) -> Unit>()

    /**
     * Add a listener for token refresh events
     */
    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    /**
     * Remove a listener
     */
    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Called when token is refreshed
     */
    internal fun onTokenRefreshed(token: String) {
        listeners.forEach { it(token) }
    }

    /**
     * Clear all listeners
     */
    fun clearListeners() {
        listeners.clear()
    }
}
