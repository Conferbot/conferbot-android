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
import kotlinx.coroutines.flow.combine
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import com.conferbot.sdk.models.RecordItem
import com.conferbot.sdk.core.i18n.I18n
import com.conferbot.sdk.core.i18n.t

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

    // The system file picker for a live chat file: no extra library needed.
    private val pickFile = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) Conferbot.sendLiveFile(uri) { error ->
            if (error != null) android.widget.Toast.makeText(this, error, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The chat opened: the launcher sound, or the pack's open (core/sound).
        if (savedInstanceState == null) Conferbot.sounds.play(com.conferbot.sdk.core.sound.MessageSound.Event.OPEN)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // A theme set to "auto" follows the phone's light / dark setting.
        Conferbot.setSystemDarkMode(
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        )

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
        // On screen: the waiting signal and receipts count the chat as open.
        Conferbot.setChatVisible(true)
        // Clear unread count when chat is visible
        // Note: unreadCount is a private field, handled internally
    }

    override fun onPause() {
        super.onPause()
        // Unregister from in-app banners
        Conferbot.setCurrentActivity(null)
        Conferbot.setChatVisible(false)
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

        applyWords()

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

        // AI agent bots: the reply as it is written and the one-tap
        // questions, drawn with the Compose pieces in the server theme.
        // The bot's chat background (core/customization ChatBackground), as on the web.
        binding.chatBackground.setContent {
            val map by Conferbot.serverCustomizationMap.collectAsState()
            val look by Conferbot.customizations.collectAsState()
            if (map.isNotEmpty()) {
                // A widget theme paints it when the owner kept the default white.
                val background = androidx.compose.runtime.remember(map, look.surface) {
                    look.surface?.let { com.conferbot.sdk.core.customization.ChatBackground.Solid(it) }
                        ?: com.conferbot.sdk.core.customization.ChatBackgrounds.resolve(map)
                }
                com.conferbot.sdk.ui.compose.chat.ChatBackgroundLayer(background)
            }
        }

        // "Powered by Conferbot", or the owner's own text, or nothing (hideBrand).
        binding.brandFooter.setContent {
            val serverCustomization by Conferbot.serverCustomization.collectAsState()
            val language = I18n.languageFlow.collectAsState().value
            androidx.compose.runtime.key(language) {
                com.conferbot.sdk.ui.compose.chat.PoweredByFooter(serverCustomization = serverCustomization)
            }
        }

        binding.agentStrip.setContent {
            // Drawn again, whole, in a new language.
            val language = I18n.languageFlow.collectAsState().value
            androidx.compose.runtime.key(language) {
                val serverTheme by Conferbot.serverTheme.collectAsState()
                val stream by Conferbot.agentStream.collectAsState()
                val suggestions by Conferbot.suggestions.collectAsState()
                com.conferbot.sdk.ui.theme.ConferbotThemeProvider(theme = serverTheme ?: com.conferbot.sdk.ui.theme.LightTheme) {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        stream?.let {
                            com.conferbot.sdk.ui.compose.messages.MessageBubble(
                                message = RecordItem.AgentMessage(id = "stream-${it.id}", time = java.util.Date(), text = it.text, agentDetails = it.agentDetails),
                                animate = false
                            )
                        }
                        if (stream == null) {
                            com.conferbot.sdk.ui.compose.messages.SuggestionChips(items = suggestions, onPick = { Conferbot.sendMessage(it) })
                        }
                        // Live chat: "Sent / Seen" for the visitor's latest message,
                        // the cards (leave a message, file request, lead, rating),
                        // locked / connection lines and the owner's quick actions.
                        val record by Conferbot.record.collectAsState()
                        val marks by Conferbot.liveChat.receiptMarks.collectAsState()
                        val agent by Conferbot.currentAgent.collectAsState()
                        val lockedBy by Conferbot.liveChat.lockedBy.collectAsState()
                        val agentLost by Conferbot.liveChat.agentConnectionLost.collectAsState()
                        val receipt = if (agent?.id?.startsWith("ai-agent-") == false)
                            com.conferbot.sdk.core.livechat.LiveChat.visitorReceiptOf(com.conferbot.sdk.core.livechat.LiveChatExtras.entriesOf(record), marks) else null
                        receipt?.let {
                            androidx.compose.material3.Text(
                                com.conferbot.sdk.core.livechat.LiveChat.receiptLabel(it.state),
                                fontSize = 12.sp,
                                color = androidx.compose.ui.graphics.Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                            )
                        }
                        // The handover step while it waits: the compact queue card
                        // (its Cancel leaves the queue on the server).
                        val ui by Conferbot.currentUIState.collectAsState()
                        (ui as? com.conferbot.sdk.core.nodes.NodeUIState.HumanHandover)
                            ?.takeIf { it.state == com.conferbot.sdk.core.nodes.NodeUIState.HumanHandover.HandoverState.WAITING_FOR_AGENT }
                            ?.let { com.conferbot.sdk.ui.compose.chat.HandoverWaitCard(message = it.handoverMessage) }
                        val closed by remember { closedState() }.collectAsState(initial = null)
                        com.conferbot.sdk.ui.compose.chat.LiveChatFooterCards(onUpload = { pickFile.launch("*/*") }, closed = closed)
                        lockedBy?.let { com.conferbot.sdk.ui.compose.chat.SystemLine(t("system.locked")) }
                        agentLost?.let { com.conferbot.sdk.ui.compose.chat.SystemLine(t("agent.connectionLost", "name" to it)) }
                        com.conferbot.sdk.ui.compose.chat.QuickActionsStrip()
                    }
                }
            }
        }

        // Apply customization
        val customization = Conferbot.getCustomization()
        customization?.headerTitle?.let {
            binding.chatHeader.titleText.text = it
        }
    }

    /** The words the screen draws itself, in the widget's language (core/i18n). */
    private fun applyWords() {
        binding.attachButton.contentDescription = t("chat.composer.attach")
        binding.sendButton.contentDescription = t("sdk.composer.sendMessage")
        binding.chatHeader.backButton.contentDescription = t("common.back")
        binding.chatHeader.avatarImage.contentDescription = t("sdk.message.botAvatar")
        if (binding.messageInput.isEnabled) binding.messageInput.hint = t("chat.composer.placeholder")
        if (binding.chatHeader.titleText.text.isNullOrEmpty()) binding.chatHeader.titleText.text = t("sdk.header.supportChat")
    }

    /** The chat's closed state: blocked, or a live chat ended with nothing going on. */
    private fun closedState() = combine(
        com.conferbot.sdk.core.state.ChatState.record,
        Conferbot.liveChat.blocked,
        Conferbot.isLiveChatMode,
        Conferbot.currentAgent
    ) { record, blocked, live, agent ->
        com.conferbot.sdk.core.livechat.LiveEvents.closedStateOf(
            com.conferbot.sdk.core.livechat.LiveEvents.kindsOf(record),
            blocked = blocked,
            stepWaiting = Conferbot.currentUIState.value != null || Conferbot.isProcessingNode.value,
            live = live && agent != null
        )
    }

    private fun setupObservers() {
        // The widget's language changed (the bot's settings arrived): the
        // screen's own words and every row again.
        I18n.languageFlow
            .onEach {
                applyWords()
                messageAdapter.notifyDataSetChanged()
            }
            .launchIn(lifecycleScope)

        // Live chat: the paperclip, and the composer while the agent turned
        // replies off or locked the conversation.
        binding.attachButton.setOnClickListener { pickFile.launch("*/*") }
        combine(
            Conferbot.isLiveChatMode,
            Conferbot.liveChat.fileRequestedBy,
            Conferbot.serverCustomizationMap,
            Conferbot.liveChat.lockedBy,
            Conferbot.liveChat.visitorInputEnabled
        ) { live, requested, custom, locked, inputOn ->
            val attach = live && (com.conferbot.sdk.core.livechat.LiveChat.visitorUploadsOn(custom) || requested != null)
            attach to (locked != null || !inputOn)
        }
            // Blocked, or ended with nothing going on: the composer is off
            // with the closed line as its hint (core/livechat/LiveEvents.kt).
            .combine(closedState()) { (attach, repliesOff), closed -> Triple(attach, repliesOff || closed?.inputOff == true, closed) }
            // ...and again in a new language.
            .combine(I18n.languageFlow) { v, _ -> v }
            .onEach { (attach, repliesOff, closed) ->
                binding.attachButton.visibility = if (attach && closed == null) View.VISIBLE else View.GONE
                binding.messageInput.isEnabled = !repliesOff
                binding.messageInput.hint = when {
                    closed?.inputOff == true -> closed.text
                    repliesOff -> t("sdk.chat.repliesOff")
                    else -> t("chat.composer.placeholder")
                }
                binding.sendButton.isEnabled = !repliesOff && !binding.messageInput.text.isNullOrBlank()
            }
            .launchIn(lifecycleScope)

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
            .combine(I18n.languageFlow) { connected, _ -> connected }
            .onEach { isConnected ->
                binding.connectionStatus.root.visibility = if (isConnected) View.GONE else View.VISIBLE
                binding.connectionStatus.statusText.text = if (isConnected) t("sdk.status.connected") else t("sdk.status.reconnecting")
            }
            .launchIn(lifecycleScope)

        // Observe typing indicator: agent typing, flow engine processing, or
        // waiting on an AI reply (none once the reply is being written)
        combine(Conferbot.isAgentTyping, Conferbot.isProcessingNode, Conferbot.awaitingReply, Conferbot.agentStream) { typing, processing, awaiting, stream ->
            (typing || processing || awaiting) && stream == null
        }
            .onEach { show ->
                binding.typingIndicator.root.visibility = if (show) View.VISIBLE else View.GONE
                // The dots sit over the list's end: make room so they never cover the last message.
                val room = if (show) (52 * resources.displayMetrics.density).toInt() else (8 * resources.displayMetrics.density).toInt()
                binding.messagesRecyclerView.clipToPadding = false
                binding.messagesRecyclerView.setPadding(
                    binding.messagesRecyclerView.paddingLeft, binding.messagesRecyclerView.paddingTop,
                    binding.messagesRecyclerView.paddingRight, room
                )
                if (show && messageAdapter.itemCount > 0) binding.messagesRecyclerView.smoothScrollToPosition(messageAdapter.itemCount - 1)
            }
            .launchIn(lifecycleScope)

        // The bot's look (core/customization) and who is in the chat: the
        // header, background, send button and typing dots, as on the web.
        combine(Conferbot.customizations, Conferbot.serverCustomizationMap, Conferbot.currentAgent, Conferbot.record, Conferbot.agentBot) { look, map, agent, record, agentBot ->
            val personInChat = com.conferbot.sdk.core.livechat.HandoverState.phase(
                com.conferbot.sdk.core.livechat.HandoverState.entriesOfItems(record)
            ) == com.conferbot.sdk.core.livechat.HandoverPhase.JOINED ||
                (agent != null && !agent.id.startsWith("ai-agent-") && agentBot == null)
            val status = com.conferbot.sdk.core.livechat.ReplyTime.headerStatus(
                com.conferbot.sdk.core.livechat.ReplyTime.of(map["replyTime"]),
                personInChat = personInChat,
                agentBot = agentBot != null
            ) ?: com.conferbot.sdk.core.livechat.ReplyTime.ONLINE
            Triple(look, map, status)
        }
            .combine(I18n.languageFlow) { v, _ -> v }
            .onEach { (look, map, status) -> applyLook(look, map, status) }
            .launchIn(lifecycleScope)
    }

    private fun applyLook(
        look: com.conferbot.sdk.core.customization.ResolvedCustomizations,
        map: Map<String, Any?>,
        /** "Online", or the team's reply time (core/livechat/ReplyTime.kt). */
        status: String
    ) {
        val header = binding.chatHeader
        val loaded = map.isNotEmpty()
        if (loaded) {
            val bg = XmlLook.headerBg(android.graphics.Color.parseColor("#0100EC"))
            val ink = XmlLook.headerText(android.graphics.Color.WHITE)
            header.root.setBackgroundColor(bg)
            header.titleText.setTextColor(ink)
            header.subtitleText.setTextColor(ink)
            header.backButton.imageTintList = android.content.res.ColorStateList.valueOf(ink)
            binding.sendButton.imageTintList = android.content.res.ColorStateList.valueOf(XmlLook.sendColor(bg))
            XmlLook.composer(binding.root, binding.inputContainer, binding.brandFooter, binding.messageInput)
            // The chat background (gradient, image, pattern) is drawn behind a see-through list.
            binding.messagesRecyclerView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            val dots = (0 until (binding.typingIndicator.root as android.view.ViewGroup).childCount).map { (binding.typingIndicator.root as android.view.ViewGroup).getChildAt(it) }
            XmlLook.typing(binding.typingIndicator.root, dots)
        }
        // The header keeps the bot (never the agent), as the web widget does.
        header.titleText.text = look.title.takeIf { loaded }
            ?: Conferbot.getCustomization()?.headerTitle
            ?: t("sdk.header.supportChat")
        // Under the title: the tagline, else "Online" (or the reply time) with the green dot.
        val subtitle: CharSequence? = when {
            !loaded -> null
            look.tagline != null -> look.tagline
            look.showOnline -> android.text.SpannableString("\u25CF  $status").apply {
                setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#22C55E")), 0, 1, 0)
            }
            else -> null
        }
        header.subtitleText.text = subtitle
        header.subtitleText.visibility = if (subtitle != null) View.VISIBLE else View.GONE
        // The bot's logo (none when the owner hid it); a person joining does not change it.
        val image = if (loaded && !look.hideLogo) look.logo else null
        header.avatarImage.visibility = if (image != null) View.VISIBLE else View.GONE
        if (image != null) {
            header.avatarImage.clipToOutline = true
            header.avatarImage.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            coil.Coil.imageLoader(this).enqueue(
                coil.request.ImageRequest.Builder(this).data(image).target(header.avatarImage).build()
            )
        }
        messageAdapter.notifyDataSetChanged()
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
        if (isFinishing) Conferbot.sounds.play(com.conferbot.sdk.core.sound.MessageSound.Event.CLOSE)
        Conferbot.sendTypingStatus(false)
    }
}
