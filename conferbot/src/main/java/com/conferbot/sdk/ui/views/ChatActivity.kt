package com.conferbot.sdk.ui.views

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.conferbot.sdk.R
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.databinding.ActivityChatBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Chat Activity for XML Views implementation
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var messageAdapter: MessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupObservers()
        ensureSessionInitialized()
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

        // Observe typing indicator
        Conferbot.isAgentTyping
            .onEach { isTyping ->
                binding.typingIndicator.root.visibility = if (isTyping) View.VISIBLE else View.GONE
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
        lifecycleScope.launchWhenCreated {
            if (Conferbot.chatSessionId.value == null) {
                Conferbot.initializeSession()
            }
        }
    }

    private fun sendMessage() {
        val text = binding.messageInput.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return

        Conferbot.sendMessage(text)
        binding.messageInput.text?.clear()
        Conferbot.sendTypingStatus(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        Conferbot.sendTypingStatus(false)
    }
}
