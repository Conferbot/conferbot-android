package com.conferbot.example

import android.app.Application
import android.util.Log
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.ConferBotEventListener
import com.conferbot.sdk.models.*
import com.conferbot.sdk.notifications.ConferbotNotification
import com.conferbot.sdk.notifications.NotificationListener
import com.conferbot.sdk.notifications.NotificationSettings
import com.google.firebase.messaging.FirebaseMessaging

class ExampleApplication : Application() {

    companion object {
        private const val TAG = "ConferBot"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Conferbot SDK
        Conferbot.initialize(
            context = this,
            apiKey = "test_key", // Replace with your API key
            botId = "69e8503cf33718a92ea792fe", // Replace with your bot ID
            config = ConferBotConfig(
                enableNotifications = true,
                enableOfflineMode = true,
                autoConnect = true
            ),
            customization = ConferBotCustomization(
                headerTitle = "Support Chat",
                enableAvatar = true
            )
        )

        // Set event listener for debugging
        Conferbot.setEventListener(object : ConferBotEventListener {
            override fun onMessageReceived(message: RecordItem) {
                Log.d(TAG, "Message received: $message")
            }

            override fun onMessageSent(message: RecordItem) {
                Log.d(TAG, "Message sent: $message")
            }

            override fun onAgentJoined(agent: Agent) {
                Log.d(TAG, "Agent joined: ${agent.name}")
            }

            override fun onAgentLeft(agent: Agent) {
                Log.d(TAG, "Agent left: ${agent.name}")
            }

            override fun onSessionStarted(sessionId: String) {
                Log.d(TAG, "Session started: $sessionId")
                // Good time to register push token after session is established
                try {
                    registerPushToken()
                } catch (e: Exception) {
                    Log.w(TAG, "Push token registration skipped: ${e.message}")
                }
            }

            override fun onSessionEnded(sessionId: String) {
                Log.d(TAG, "Session ended: $sessionId")
            }

            override fun onTypingIndicator(isTyping: Boolean) {
                Log.d(TAG, "Agent typing: $isTyping")
            }

            override fun onUnreadCountChanged(count: Int) {
                Log.d(TAG, "Unread count: $count")
            }
        })

        // Set up notification listener for custom handling
        Conferbot.addNotificationListener(object : NotificationListener {
            override fun onNotificationReceived(notification: ConferbotNotification) {
                Log.d(TAG, "Notification received: ${notification::class.simpleName}")
                when (notification) {
                    is ConferbotNotification.NewMessage -> {
                        Log.d(TAG, "New message from ${notification.from}: ${notification.content}")
                    }
                    is ConferbotNotification.AgentJoined -> {
                        Log.d(TAG, "Agent ${notification.agentName} joined")
                    }
                    is ConferbotNotification.AgentLeft -> {
                        Log.d(TAG, "Agent ${notification.agentName} left")
                    }
                    is ConferbotNotification.ChatEnded -> {
                        Log.d(TAG, "Chat ended: ${notification.reason}")
                    }
                    is ConferbotNotification.HandoverQueued -> {
                        Log.d(TAG, "Queued at position ${notification.position}")
                    }
                    is ConferbotNotification.QueuePositionUpdate -> {
                        Log.d(TAG, "Queue position updated to ${notification.newPosition}")
                    }
                    is ConferbotNotification.SystemNotification -> {
                        Log.d(TAG, "System: ${notification.title} - ${notification.message}")
                    }
                }
            }

            override fun onNotificationTapped(notification: ConferbotNotification) {
                Log.d(TAG, "Notification tapped: ${notification::class.simpleName}")
            }
        })

        // Configure notification settings (optional)
        val notificationSettings = NotificationSettings(
            enabled = true,
            soundEnabled = true,
            vibrationEnabled = true,
            showPreview = true,
            showInForeground = false, // Don't show system notifications when app is in foreground
            showNewMessages = true,
            showAgentJoined = true,
            showAgentLeft = true,
            showChatEnded = true,
            showQueueUpdates = true
        )
        Conferbot.updateNotificationSettings(notificationSettings)

        Log.d(TAG, "SDK initialized successfully with push notifications")
    }

    /**
     * Register FCM push token with Conferbot
     * Called after session is established
     */
    private fun registerPushToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d(TAG, "FCM Token: $token")

            // Register with Conferbot
            Conferbot.registerPushToken(token)
        }
    }
}
