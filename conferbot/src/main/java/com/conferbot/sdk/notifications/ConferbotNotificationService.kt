package com.conferbot.sdk.notifications

import android.util.Log
import com.conferbot.sdk.core.Conferbot
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * A ready-made FirebaseMessagingService for apps whose only FCM pushes are
 * Conferbot's (embed-server #189, docs/mobile-push.md).
 *
 * Firebase is optional: the SDK only compiles against firebase-messaging,
 * so this class works when the app adds firebase-messaging itself, and the
 * SDK's manifest does not declare it. To use it, declare it in the app's
 * manifest:
 * ```xml
 * <service
 *     android:name="com.conferbot.sdk.notifications.ConferbotNotificationService"
 *     android:exported="false">
 *     <intent-filter>
 *         <action android:name="com.google.firebase.MESSAGING_EVENT" />
 *     </intent-filter>
 * </service>
 * ```
 * An app with its own FirebaseMessagingService (only one receives the
 * messages) forwards instead: `Conferbot.setPushToken(token, PushTokenType.FCM)`
 * in onNewToken, `Conferbot.handlePushNotification(message.data, open = false)`
 * in onMessageReceived.
 */
open class ConferbotNotificationService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "ConferbotFCMService"
    }

    /**
     * Called for a message while the app is in the foreground (in the
     * background the system shows the server's notification block itself;
     * its tap reaches the launcher activity: `Conferbot.handlePushIntent`).
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Shown on the conferbot_messages channel unless that conversation is on screen.
        if (!Conferbot.handlePushNotification(message.data, open = false)) {
            Log.d(TAG, "Not a Conferbot notification, ignoring")
        }
    }

    /** A new or rotated token: registered with the current conversation. */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        try {
            Conferbot.setPushToken(token, PushTokenType.FCM)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hand the push token to Conferbot", e)
        }
        NotificationTokenListener.onTokenRefreshed(token)
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
