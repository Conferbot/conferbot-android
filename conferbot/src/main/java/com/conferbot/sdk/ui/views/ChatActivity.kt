package com.conferbot.sdk.ui.views

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.conferbot.sdk.R
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.databinding.ActivityChatBinding
import com.conferbot.sdk.notifications.ConferbotNotificationManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Chat Activity for XML Views implementation
 * Supports deep linking from notifications
 */
class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatActivity"
    }

    private lateinit var binding: ActivityChatBinding
    private lateinit var messageAdapter: MessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupObservers()
        handleIntent(intent)
        ensureSessionInitialized()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    /**
     * Handle deep link intents from notifications
     */
    private fun handleIntent(intent: Intent) {
        // Check if launched from notification
        val action = intent.action
        val chatSessionId = intent.getStringExtra(ConferbotNotificationManager.EXTRA_CHAT_SESSION_ID)
        val notificationData = intent.getSerializableExtra(ConferbotNotificationManager.EXTRA_NOTIFICATION_DATA) as? HashMap<String, String>

        Log.d(TAG, "Handling intent - action: $action, sessionId: $chatSessionId")

        when (action) {
            ConferbotNotificationManager.ACTION_OPEN_CHAT -> {
                // Opened from notification tap
                Log.d(TAG, "Opened from notification with session: $chatSessionId")

                // Cancel related notifications
                Conferbot.cancelAllNotifications()

                // If we have notification data, let Conferbot handle it
                notificationData?.let {
                    Conferbot.handleNotificationTap(it)
                }
            }
            Intent.ACTION_VIEW -> {
                // Handle deep link scheme (conferbot://chat)
                intent.data?.let { uri ->
                    Log.d(TAG, "Deep link received: $uri")
                    // Extract session ID from URI if present
                    uri.getQueryParameter("sessionId")?.let { sessionId ->
                        // Could restore specific session here
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register this activity for in-app notification banners
        Conferbot.setCurrentActivity(this)
        // Clear unread count when chat is visible
        // Note: unreadCount is a private field, handled internally
    }

    override fun onPause() {
        super.onPause()
        // Unregister from in-app banners
        Conferbot.setCurrentActivity(null)
    }

    private fun setupUI() {
        // Setup RecyclerView
        messageAdapter = MessageAdapter()
        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }

        // Back button
        binding.chatHeader.backButton.setOnClickListener {
            finish()
        }

        // Send button
        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        // Message input
        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrEmpty()
                binding.sendButton.isEnabled = hasText
                if (hasText) {
                    Conferbot.sendTypingStatus(true)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Apply customization
        val customization = Conferbot.getCustomization()
        customization?.headerTitle?.let {
            binding.chatHeader.titleText.text = it
        }
    }

    private fun setupObservers() {
        // Observe messages
        Conferbot.record
            .onEach { messages ->
                messageAdapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    binding.messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
            .launchIn(lifecycleScope)

        // Observe connection status
        Conferbot.isConnected
            .onEach { isConnected ->
                binding.connectionStatus.root.visibility = if (isConnected) View.GONE else View.VISIBLE
                binding.connectionStatus.statusText.text = if (isConnected) "Connected" else "Reconnecting..."
            }
            .launchIn(lifecycleScope)

        // Observe typing indicator (show for both agent typing and flow engine processing)
        Conferbot.isAgentTyping
            .onEach { isTyping ->
                val isProcessing = Conferbot.isProcessingNode.value
                binding.typingIndicator.root.visibility =
                    if (isTyping || isProcessing) View.VISIBLE else View.GONE
            }
            .launchIn(lifecycleScope)

        // Also observe flow engine processing state for typing indicator
        Conferbot.isProcessingNode
            .onEach { isProcessing ->
                val isAgentTyping = Conferbot.isAgentTyping.value
                binding.typingIndicator.root.visibility =
                    if (isProcessing || isAgentTyping) View.VISIBLE else View.GONE
            }
            .launchIn(lifecycleScope)

        // Observe current agent
        Conferbot.currentAgent
            .onEach { agent ->
                if (agent != null) {
                    binding.chatHeader.titleText.text = agent.name
                    // Load avatar if available
                    // You can use Coil or Glide here
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun ensureSessionInitialized() {
        lifecycleScope.launch {
            Log.d(TAG, "Ensuring session initialized, current sessionId: ${Conferbot.chatSessionId.value}")
            if (Conferbot.chatSessionId.value == null) {
                Log.d(TAG, "No session, initializing...")
                val result = Conferbot.initializeSession()
                Log.d(TAG, "Session init result: $result")
            } else {
                Log.d(TAG, "Session already exists: ${Conferbot.chatSessionId.value}")
            }
        }
    }

    private fun sendMessage() {
        val text = binding.messageInput.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return

        // If the flow engine is waiting for text input, submit to it
        val currentUIState = Conferbot.currentUIState.value
        if (currentUIState is com.conferbot.sdk.core.nodes.NodeUIState.TextInput) {
            Conferbot.submitNodeResponse(text)
        } else {
            // Regular message send (agent chat or free-form)
            Conferbot.sendMessage(text)
        }

        binding.messageInput.text?.clear()
        Conferbot.sendTypingStatus(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        Conferbot.sendTypingStatus(false)
    }
}
