package com.conferbot.sdk.notifications

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.conferbot.sdk.R
import com.conferbot.sdk.core.Conferbot
import java.lang.ref.WeakReference

/**
 * Handler for Conferbot notifications.
 * Manages notification display based on app state (foreground/background).
 * Handles notification taps and deep linking.
 */
class NotificationHandler(
    private val context: Context
) {
    companion object {
        private const val TAG = "ConferbotNotificationHandler"

        // In-app banner display duration
        private const val BANNER_DISPLAY_DURATION_MS = 4000L
        private const val BANNER_ANIMATION_DURATION_MS = 300L

        @Volatile
        private var instance: NotificationHandler? = null

        fun getInstance(context: Context): NotificationHandler {
            return instance ?: synchronized(this) {
                instance ?: NotificationHandler(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val notificationManager = ConferbotNotificationManager.getInstance(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Track app foreground state
    private var isAppInForeground = false

    // Current activity reference (weak to prevent leaks)
    private var currentActivityRef: WeakReference<Activity>? = null

    // Notification callback listeners
    private val notificationListeners = mutableListOf<NotificationListener>()

    // Chat activity class for deep linking
    private var chatActivityClass: Class<*>? = null

    init {
        observeAppLifecycle()
    }

    /**
     * Observe app lifecycle to track foreground/background state
     */
    private fun observeAppLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        isAppInForeground = true
                        Log.d(TAG, "App in foreground")
                    }
                    Lifecycle.Event.ON_STOP -> {
                        isAppInForeground = false
                        Log.d(TAG, "App in background")
                    }
                    else -> {}
                }
            }
        })
    }

    /**
     * Set the current activity for in-app banner display
     */
    fun setCurrentActivity(activity: Activity?) {
        currentActivityRef = activity?.let { WeakReference(it) }
    }

    /**
     * Set the chat activity class for deep linking
     */
    fun setChatActivityClass(activityClass: Class<*>) {
        chatActivityClass = activityClass
        notificationManager.setChatActivityClass(activityClass)
    }

    /**
     * Add a notification listener
     */
    fun addNotificationListener(listener: NotificationListener) {
        if (!notificationListeners.contains(listener)) {
            notificationListeners.add(listener)
        }
    }

    /**
     * Remove a notification listener
     */
    fun removeNotificationListener(listener: NotificationListener) {
        notificationListeners.remove(listener)
    }

    /**
     * Handle a received notification
     * Decides whether to show in-app banner or system notification
     */
    fun handleNotification(notification: ConferbotNotification) {
        Log.d(TAG, "Handling notification: ${notification::class.simpleName}")

        // Notify listeners first
        notifyListeners(notification)

        // Check notification settings
        val settings = notificationManager.getSettings()
        if (!settings.shouldShow(notification)) {
            Log.d(TAG, "Notification filtered by settings")
            return
        }

        if (isAppInForeground) {
            handleForegroundNotification(notification, settings)
        } else {
            handleBackgroundNotification(notification)
        }
    }

    /**
     * Handle notification when app is in foreground
     */
    private fun handleForegroundNotification(
        notification: ConferbotNotification,
        settings: NotificationSettings
    ) {
        Log.d(TAG, "App in foreground, handling notification")

        // Check if we should show in foreground
        if (!settings.showInForeground) {
            Log.d(TAG, "Foreground notifications disabled")
            return
        }

        // Try to show in-app banner
        val activity = currentActivityRef?.get()
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            mainHandler.post {
                showInAppBanner(activity, notification)
            }
        } else {
            // Fallback to system notification if no activity available
            notificationManager.showNotification(notification)
        }
    }

    /**
     * Handle notification when app is in background or killed
     */
    private fun handleBackgroundNotification(notification: ConferbotNotification) {
        Log.d(TAG, "App in background, showing system notification")
        notificationManager.showNotification(notification)
    }

    /**
     * Show in-app banner notification
     */
    private fun showInAppBanner(activity: Activity, notification: ConferbotNotification) {
        try {
            val decorView = activity.window?.decorView as? ViewGroup ?: return
            val contentView = decorView.findViewById<FrameLayout>(android.R.id.content) ?: return

            // Create banner view
            val bannerView = createBannerView(activity, notification)

            // Add banner to content view
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                topMargin = getStatusBarHeight(activity)
            }

            bannerView.translationY = -200f
            contentView.addView(bannerView, params)

            // Animate in
            bannerView.animate()
                .translationY(0f)
                .setDuration(BANNER_ANIMATION_DURATION_MS)
                .start()

            // Set click listener to open chat
            bannerView.setOnClickListener {
                dismissBanner(bannerView, contentView)
                handleNotificationTap(notification.toDataMap())
            }

            // Auto-dismiss after delay
            mainHandler.postDelayed({
                dismissBanner(bannerView, contentView)
            }, BANNER_DISPLAY_DURATION_MS)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to show in-app banner", e)
            // Fallback to system notification
            notificationManager.showNotification(notification)
        }
    }

    /**
     * Create banner view for in-app notification
     */
    private fun createBannerView(activity: Activity, notification: ConferbotNotification): View {
        val inflater = LayoutInflater.from(activity)

        // Try to use custom layout, fallback to simple view
        val bannerView = try {
            inflater.inflate(R.layout.view_notification_banner, null)
        } catch (e: Exception) {
            // Create simple banner programmatically if layout doesn't exist
            createSimpleBannerView(activity, notification)
        }

        // Set content
        when (notification) {
            is ConferbotNotification.NewMessage -> {
                bannerView.findViewById<TextView>(R.id.bannerTitle)?.text = notification.from
                bannerView.findViewById<TextView>(R.id.bannerMessage)?.text = notification.content
            }
            is ConferbotNotification.AgentJoined -> {
                bannerView.findViewById<TextView>(R.id.bannerTitle)?.text = "Agent Joined"
                bannerView.findViewById<TextView>(R.id.bannerMessage)?.text =
                    "${notification.agentName} has joined the chat"
            }
            is ConferbotNotification.AgentLeft -> {
                bannerView.findViewById<TextView>(R.id.bannerTitle)?.text = "Agent Left"
                bannerView.findViewById<TextView>(R.id.bannerMessage)?.text =
                    "${notification.agentName} has left the chat"
            }
            is ConferbotNotification.ChatEnded -> {
                bannerView.findViewById<TextView>(R.id.bannerTitle)?.text = "Chat Ended"
                bannerView.findViewById<TextView>(R.id.bannerMessage)?.text = notification.reason
            }
            is ConferbotNotification.HandoverQueued -> {
                bannerView.findViewById<TextView>(R.id.bannerTitle)?.text = "Waiting for Agent"
                bannerView.findViewById<TextView>(R.id.bannerMessage)?.text =
                    "You are #${notification.position} in queue"
            }
            is ConferbotNotification.QueuePositionUpdate -> {
                bannerView.findViewById<TextView>(R.id.bannerTitle)?.text = "Queue Update"
                bannerView.findViewById<TextView>(R.id.bannerMessage)?.text =
                    "Your position: #${notification.newPosition}"
            }
            is ConferbotNotification.SystemNotification -> {
                bannerView.findViewById<TextView>(R.id.bannerTitle)?.text = notification.title
                bannerView.findViewById<TextView>(R.id.bannerMessage)?.text = notification.message
            }
        }

        return bannerView
    }

    /**
     * Create a simple banner view programmatically
     */
    private fun createSimpleBannerView(activity: Activity, notification: ConferbotNotification): View {
        return FrameLayout(activity).apply {
            setBackgroundColor(0xFFFFFFFF.toInt())
            elevation = 8f
            setPadding(48, 32, 48, 32)

            val textView = TextView(activity).apply {
                text = when (notification) {
                    is ConferbotNotification.NewMessage ->
                        "${notification.from}: ${notification.content}"
                    is ConferbotNotification.AgentJoined ->
                        "${notification.agentName} has joined the chat"
                    is ConferbotNotification.AgentLeft ->
                        "${notification.agentName} has left the chat"
                    is ConferbotNotification.ChatEnded ->
                        "Chat ended: ${notification.reason}"
                    is ConferbotNotification.HandoverQueued ->
                        "You are #${notification.position} in queue"
                    is ConferbotNotification.QueuePositionUpdate ->
                        "Queue position: #${notification.newPosition}"
                    is ConferbotNotification.SystemNotification ->
                        "${notification.title}: ${notification.message}"
                }
                textSize = 14f
                maxLines = 2
            }

            addView(textView)
        }
    }

    /**
     * Dismiss in-app banner with animation
     */
    private fun dismissBanner(bannerView: View, parent: ViewGroup) {
        bannerView.animate()
            .translationY(-200f)
            .setDuration(BANNER_ANIMATION_DURATION_MS)
            .withEndAction {
                parent.removeView(bannerView)
            }
            .start()
    }

    /**
     * Get status bar height for banner positioning
     */
    private fun getStatusBarHeight(activity: Activity): Int {
        var result = 0
        val resourceId = activity.resources.getIdentifier(
            "status_bar_height", "dimen", "android"
        )
        if (resourceId > 0) {
            result = activity.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    /**
     * Handle notification tap (from system notification or in-app banner)
     */
    fun handleNotificationTap(data: Map<String, String>) {
        Log.d(TAG, "Notification tapped with data: $data")

        val chatSessionId = data["chatSessionId"]

        // Open chat activity
        if (chatActivityClass != null) {
            openChatActivity(chatSessionId, data)
        } else {
            // Use Conferbot.openChat as fallback
            val activity = currentActivityRef?.get()
            if (activity != null) {
                Conferbot.openChat(activity)
            } else {
                // Try to launch using application context
                openChatActivity(chatSessionId, data)
            }
        }

        // Cancel related notifications
        chatSessionId?.let {
            notificationManager.cancelNotificationsForSession(it)
        }
    }

    /**
     * Open chat activity with deep link data
     */
    private fun openChatActivity(chatSessionId: String?, data: Map<String, String>) {
        val targetClass = chatActivityClass ?: return

        try {
            val intent = Intent(context, targetClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                chatSessionId?.let {
                    putExtra(ConferbotNotificationManager.EXTRA_CHAT_SESSION_ID, it)
                }
                putExtra(ConferbotNotificationManager.EXTRA_NOTIFICATION_DATA, HashMap(data))
            }

            context.startActivity(intent)
            Log.d(TAG, "Opened chat activity")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open chat activity", e)
        }
    }

    /**
     * Notify all listeners about the received notification
     */
    private fun notifyListeners(notification: ConferbotNotification) {
        notificationListeners.forEach { listener ->
            try {
                listener.onNotificationReceived(notification)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying listener", e)
            }
        }
    }

    /**
     * Check if app is currently in foreground
     */
    fun isInForeground(): Boolean = isAppInForeground

    /**
     * Convert ConferbotNotification to data map
     */
    private fun ConferbotNotification.toDataMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()

        when (this) {
            is ConferbotNotification.NewMessage -> {
                map["type"] = "new_message"
                map["from"] = from
                map["content"] = content
                chatSessionId?.let { map["chatSessionId"] = it }
            }
            is ConferbotNotification.AgentJoined -> {
                map["type"] = "agent_joined"
                map["agentName"] = agentName
                chatSessionId?.let { map["chatSessionId"] = it }
            }
            is ConferbotNotification.AgentLeft -> {
                map["type"] = "agent_left"
                map["agentName"] = agentName
                chatSessionId?.let { map["chatSessionId"] = it }
            }
            is ConferbotNotification.ChatEnded -> {
                map["type"] = "chat_ended"
                map["reason"] = reason
                chatSessionId?.let { map["chatSessionId"] = it }
            }
            is ConferbotNotification.HandoverQueued -> {
                map["type"] = "handover_queued"
                map["position"] = position.toString()
                chatSessionId?.let { map["chatSessionId"] = it }
            }
            is ConferbotNotification.QueuePositionUpdate -> {
                map["type"] = "queue_position_update"
                map["position"] = newPosition.toString()
                chatSessionId?.let { map["chatSessionId"] = it }
            }
            is ConferbotNotification.SystemNotification -> {
                map["type"] = "system"
                map["title"] = title
                map["message"] = message
                map.putAll(data)
            }
        }

        return map
    }
}

/**
 * Listener interface for notification events
 */
interface NotificationListener {
    /**
     * Called when a notification is received
     */
    fun onNotificationReceived(notification: ConferbotNotification)

    /**
     * Called when a notification is tapped
     */
    fun onNotificationTapped(notification: ConferbotNotification) {}

    /**
     * Called when a notification is dismissed
     */
    fun onNotificationDismissed(notification: ConferbotNotification) {}
}
