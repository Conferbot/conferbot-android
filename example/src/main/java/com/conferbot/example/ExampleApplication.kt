package com.conferbot.example

import android.app.Application
import android.util.Log
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.ConferBotEventListener
import com.conferbot.sdk.models.*

class ExampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Conferbot SDK
        Conferbot.initialize(
            context = this,
            apiKey = "conf_sk_your_api_key_here", // Replace with your API key
            botId = "your_bot_id_here", // Replace with your bot ID
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
                Log.d("ConferBot", "Message received: $message")
            }

            override fun onMessageSent(message: RecordItem) {
                Log.d("ConferBot", "Message sent: $message")
            }

            override fun onAgentJoined(agent: Agent) {
                Log.d("ConferBot", "Agent joined: ${agent.name}")
            }

            override fun onAgentLeft(agent: Agent) {
                Log.d("ConferBot", "Agent left: ${agent.name}")
            }

            override fun onSessionStarted(sessionId: String) {
                Log.d("ConferBot", "Session started: $sessionId")
            }

            override fun onSessionEnded(sessionId: String) {
                Log.d("ConferBot", "Session ended: $sessionId")
            }

            override fun onTypingIndicator(isTyping: Boolean) {
                Log.d("ConferBot", "Agent typing: $isTyping")
            }

            override fun onUnreadCountChanged(count: Int) {
                Log.d("ConferBot", "Unread count: $count")
            }
        })

        Log.d("ConferBot", "SDK initialized successfully")
    }
}
