package com.conferbot.example.ui.xml

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.conferbot.example.R
import com.conferbot.example.ui.compose.ComposeActivity
import com.conferbot.sdk.core.Conferbot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Main Activity demonstrating XML Views integration
 */
class MainActivity : AppCompatActivity() {

    private lateinit var unreadBadge: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUI()
        observeState()
    }

    private fun setupUI() {
        unreadBadge = findViewById(R.id.unreadBadge)

        // Open chat button (XML Views)
        findViewById<Button>(R.id.openChatButton).setOnClickListener {
            Conferbot.openChat(this)
        }

        // Open compose example
        findViewById<Button>(R.id.openComposeButton).setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }

        // Identify user button
        findViewById<Button>(R.id.identifyUserButton).setOnClickListener {
            identifyTestUser()
        }

        // Send test message
        findViewById<Button>(R.id.sendMessageButton).setOnClickListener {
            sendTestMessage()
        }

        // Request agent
        findViewById<Button>(R.id.requestAgentButton).setOnClickListener {
            Conferbot.initiateHandover("User requested agent from example app")
        }

        // Clear history
        findViewById<Button>(R.id.clearHistoryButton).setOnClickListener {
            Conferbot.clearHistory()
        }
    }

    private fun observeState() {
        // Observe unread count
        Conferbot.unreadCount
            .onEach { count ->
                unreadBadge.text = if (count > 0) count.toString() else ""
                unreadBadge.visibility = if (count > 0) TextView.VISIBLE else TextView.GONE
            }
            .launchIn(lifecycleScope)

        // Observe connection status
        Conferbot.isConnected
            .onEach { connected ->
                findViewById<TextView>(R.id.connectionStatus).text =
                    if (connected) "Connected" else "Disconnected"
            }
            .launchIn(lifecycleScope)

        // Observe current agent
        Conferbot.currentAgent
            .onEach { agent ->
                findViewById<TextView>(R.id.agentStatus).text =
                    agent?.let { "Agent: ${it.name}" } ?: "No agent"
            }
            .launchIn(lifecycleScope)

        // Observe message count
        Conferbot.record
            .onEach { messages ->
                findViewById<TextView>(R.id.messageCount).text =
                    "Messages: ${messages.size}"
            }
            .launchIn(lifecycleScope)
    }

    private fun identifyTestUser() {
        Conferbot.identify(
            com.conferbot.sdk.models.ConferBotUser(
                id = "test-user-123",
                name = "Test User",
                email = "test@example.com",
                phone = "+1234567890",
                metadata = mapOf(
                    "app" to "example-app",
                    "platform" to "android"
                )
            )
        )
    }

    private fun sendTestMessage() {
        lifecycleScope.launch {
            // Ensure session is initialized
            if (Conferbot.chatSessionId.value == null) {
                Conferbot.initializeSession()
            }
            // Send message
            Conferbot.sendMessage("Hello from example app!")
        }
    }
}
