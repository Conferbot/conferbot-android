# Examples

Practical examples for integrating the Conferbot Android SDK.

## Table of Contents

- [Basic Setup](#basic-setup)
- [XML Views Examples](#xml-views-examples)
- [Jetpack Compose Examples](#jetpack-compose-examples)
- [Advanced Examples](#advanced-examples)
- [Real-World Use Cases](#real-world-use-cases)

---

## Basic Setup

### Application Class

```kotlin
import android.app.Application
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.models.ConferBotConfig

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Conferbot.initialize(
            context = this,
            apiKey = "conf_sk_your_api_key_here",
            botId = "your_bot_id_here",
            config = ConferBotConfig(
                enableNotifications = true,
                enableOfflineMode = true
            )
        )
    }
}
```

**AndroidManifest.xml**:
```xml
<application
    android:name=".MyApplication"
    ...>
</application>
```

---

## XML Views Examples

### Example 1: Simple Chat Button

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.chatButton).setOnClickListener {
            Conferbot.openChat(this)
        }
    }
}
```

### Example 2: Embedded Chat Fragment

```kotlin
class SupportFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_support, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Custom implementation using SDK state
        setupCustomChat()
    }

    private fun setupCustomChat() {
        lifecycleScope.launch {
            Conferbot.record.collect { messages ->
                updateMessageList(messages)
            }
        }

        lifecycleScope.launch {
            Conferbot.isConnected.collect { connected ->
                updateConnectionStatus(connected)
            }
        }

        view?.findViewById<EditText>(R.id.messageInput)?.let { input ->
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    Conferbot.sendTypingStatus(s?.isNotEmpty() == true)
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        view?.findViewById<Button>(R.id.sendButton)?.setOnClickListener {
            val text = view?.findViewById<EditText>(R.id.messageInput)?.text.toString()
            if (text.isNotBlank()) {
                Conferbot.sendMessage(text)
                view?.findViewById<EditText>(R.id.messageInput)?.text?.clear()
            }
        }
    }
}
```

### Example 3: With Event Listener

```kotlin
class ChatActivity : AppCompatActivity() {
    private val eventListener = object : ConferBotEventListener {
        override fun onMessageReceived(message: RecordItem) {
            when (message) {
                is RecordItem.BotMessage -> {
                    Log.d("Chat", "Bot message: ${message.text}")
                }
                is RecordItem.AgentMessage -> {
                    Log.d("Chat", "Agent message: ${message.text}")
                    showNotification("New message from ${message.agentDetails.name}")
                }
            }
        }

        override fun onAgentJoined(agent: Agent) {
            Toast.makeText(
                this@ChatActivity,
                "${agent.name} joined the chat",
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun onSessionStarted(sessionId: String) {
            analytics.track("chat_session_started", mapOf("sessionId" to sessionId))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        Conferbot.setEventListener(eventListener)
        Conferbot.openChat(this)
    }
}
```

### Example 4: Custom Message Adapter

```kotlin
class CustomMessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var messages: List<RecordItem> = emptyList()

    fun submitList(newMessages: List<RecordItem>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (messages[position]) {
            is RecordItem.UserMessage -> VIEW_TYPE_USER
            is RecordItem.BotMessage -> VIEW_TYPE_BOT
            is RecordItem.AgentMessage -> VIEW_TYPE_AGENT
            else -> VIEW_TYPE_SYSTEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> UserMessageViewHolder(
                inflater.inflate(R.layout.item_user_message, parent, false)
            )
            VIEW_TYPE_BOT -> BotMessageViewHolder(
                inflater.inflate(R.layout.item_bot_message, parent, false)
            )
            VIEW_TYPE_AGENT -> AgentMessageViewHolder(
                inflater.inflate(R.layout.item_agent_message, parent, false)
            )
            else -> SystemMessageViewHolder(
                inflater.inflate(R.layout.item_system_message, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message as RecordItem.UserMessage)
            is BotMessageViewHolder -> holder.bind(message as RecordItem.BotMessage)
            is AgentMessageViewHolder -> holder.bind(message as RecordItem.AgentMessage)
        }
    }

    override fun getItemCount() = messages.size

    companion object {
        const val VIEW_TYPE_USER = 1
        const val VIEW_TYPE_BOT = 2
        const val VIEW_TYPE_AGENT = 3
        const val VIEW_TYPE_SYSTEM = 4
    }
}
```

---

## Jetpack Compose Examples

### Example 1: Simple Chat Screen

```kotlin
@Composable
fun HomeScreen() {
    var showChat by remember { mutableStateOf(false) }

    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(onClick = { showChat = true }) {
                Text("Open Support Chat")
            }
        }
    }

    if (showChat) {
        ConferBotChatScreen(
            onDismiss = { showChat = false }
        )
    }
}
```

### Example 2: Embedded Chat View

```kotlin
@Composable
fun SupportScreen() {
    ConferBotChatView(
        modifier = Modifier.fillMaxSize(),
        onMessageSent = { text ->
            Log.d("Chat", "Sent: $text")
        },
        onAgentJoined = { agentName ->
            Log.d("Chat", "Agent joined: $agentName")
        }
    )
}
```

### Example 3: Custom UI with State

```kotlin
@Composable
fun CustomChatScreen() {
    val messages by Conferbot.record.collectAsState()
    val isConnected by Conferbot.isConnected.collectAsState()
    val currentAgent by Conferbot.currentAgent.collectAsState()
    val isAgentTyping by Conferbot.isAgentTyping.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(currentAgent?.name ?: "Support")
                        if (!isConnected) {
                            Text("Reconnecting...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Custom message list
            CustomMessageList(
                messages = messages,
                isAgentTyping = isAgentTyping,
                modifier = Modifier.weight(1f)
            )

            // Custom input
            CustomChatInput(
                onSendMessage = { text ->
                    Conferbot.sendMessage(text)
                }
            )
        }
    }
}
```

### Example 4: With Navigation

```kotlin
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenChat = { navController.navigate("chat") }
            )
        }
        composable("chat") {
            ConferBotChatScreen(
                onDismiss = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun HomeScreen(onOpenChat: () -> Unit) {
    val unreadCount by Conferbot.unreadCount.collectAsState()

    Button(onClick = onOpenChat) {
        Text("Chat")
        if (unreadCount > 0) {
            Badge { Text("$unreadCount") }
        }
    }
}
```

### Example 5: Custom Message Bubble

```kotlin
@Composable
fun CustomMessageBubble(message: RecordItem) {
    when (message) {
        is RecordItem.UserMessage -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF0100EC), Color(0xFF6366F1))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(text = message.text, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTime(message.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
        is RecordItem.BotMessage -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Image(
                    painter = painterResource(R.drawable.bot_avatar),
                    contentDescription = "Bot",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Text(text = message.text ?: "")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTime(message.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
```

---

## Advanced Examples

### Example 1: E-commerce Product Support

```kotlin
@Composable
fun ProductDetailScreen(product: Product) {
    var showChat by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            Button(
                onClick = {
                    // Pre-populate chat with product context
                    Conferbot.identify(
                        ConferBotUser(
                            id = getUserId(),
                            metadata = mapOf(
                                "productId" to product.id,
                                "productName" to product.name,
                                "price" to product.price
                            )
                        )
                    )
                    showChat = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ask about ${product.name}")
            }
        }
    ) {
        // Product details...
    }

    if (showChat) {
        ConferBotChatScreen(onDismiss = { showChat = false })
    }
}
```

### Example 2: Banking App with Biometric Auth

```kotlin
class SecureChatActivity : AppCompatActivity() {
    private lateinit var biometricPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupBiometricAuth()

        findViewById<Button>(R.id.secureChatButton).setOnClickListener {
            authenticateAndOpenChat()
        }
    }

    private fun setupBiometricAuth() {
        val executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    Conferbot.openChat(this@SecureChatActivity)
                }

                override fun onAuthenticationFailed() {
                    Toast.makeText(
                        this@SecureChatActivity,
                        "Authentication failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun authenticateAndOpenChat() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Secure Chat")
            .setSubtitle("Authenticate to access support")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
```

### Example 3: With ViewModel (MVVM)

```kotlin
class ChatViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        observeConferbot()
    }

    private fun observeConferbot() {
        viewModelScope.launch {
            Conferbot.record.collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }

        viewModelScope.launch {
            Conferbot.isConnected.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
            }
        }

        viewModelScope.launch {
            Conferbot.currentAgent.collect { agent ->
                _uiState.update { it.copy(currentAgent = agent) }
            }
        }

        viewModelScope.launch {
            Conferbot.unreadCount.collect { count ->
                _uiState.update { it.copy(unreadCount = count) }
            }
        }
    }

    fun sendMessage(text: String) {
        Conferbot.sendMessage(text)
    }

    fun requestAgent() {
        Conferbot.initiateHandover("User requested agent")
    }
}

data class ChatUiState(
    val messages: List<RecordItem> = emptyList(),
    val isConnected: Boolean = false,
    val currentAgent: Agent? = null,
    val unreadCount: Int = 0
)

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    ChatContent(
        messages = uiState.messages,
        isConnected = uiState.isConnected,
        currentAgent = uiState.currentAgent,
        onSendMessage = viewModel::sendMessage,
        onRequestAgent = viewModel::requestAgent
    )
}
```

### Example 4: Multi-language Support

```kotlin
@Composable
fun MultiLanguageChatScreen() {
    val context = LocalContext.current
    val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0]

    LaunchedEffect(Unit) {
        Conferbot.identify(
            ConferBotUser(
                id = getUserId(),
                metadata = mapOf(
                    "language" to locale?.language,
                    "country" to locale?.country
                )
            )
        )
    }

    ConferBotChatScreen(
        onDismiss = { /* ... */ }
    )
}
```

### Example 5: Analytics Integration

```kotlin
class AnalyticsEventListener(private val analytics: Analytics) : ConferBotEventListener {
    override fun onSessionStarted(sessionId: String) {
        analytics.track("chat_session_started", mapOf(
            "sessionId" to sessionId,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    override fun onMessageSent(message: RecordItem) {
        if (message is RecordItem.UserMessage) {
            analytics.track("chat_message_sent", mapOf(
                "messageLength" to message.text.length
            ))
        }
    }

    override fun onAgentJoined(agent: Agent) {
        analytics.track("chat_agent_joined", mapOf(
            "agentId" to agent.id,
            "agentName" to agent.name
        ))
    }

    override fun onSessionEnded(sessionId: String) {
        analytics.track("chat_session_ended", mapOf(
            "sessionId" to sessionId,
            "duration" to calculateSessionDuration()
        ))
    }
}

// Register
Conferbot.setEventListener(AnalyticsEventListener(FirebaseAnalytics.getInstance(context)))
```

---

## Real-World Use Cases

### Use Case 1: SaaS Dashboard

```kotlin
@Composable
fun DashboardScreen() {
    var showChat by remember { mutableStateOf(false) }
    val unreadCount by Conferbot.unreadCount.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showChat = true },
                containerColor = Color(0xFF0100EC)
            ) {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge { Text("$unreadCount") }
                        }
                    }
                ) {
                    Icon(Icons.Default.Chat, "Chat")
                }
            }
        }
    ) {
        // Dashboard content...
    }

    if (showChat) {
        ConferBotChatScreen(onDismiss = { showChat = false })
    }
}
```

### Use Case 2: Healthcare App

```kotlin
class TelehealthChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Identify patient
        Conferbot.identify(
            ConferBotUser(
                id = patient.id,
                name = patient.name,
                email = patient.email,
                metadata = mapOf(
                    "patientId" to patient.id,
                    "dateOfBirth" to patient.dob,
                    "insurance" to patient.insuranceProvider,
                    "appointmentId" to currentAppointment.id
                )
            )
        )

        // HIPAA compliance logging
        Conferbot.setEventListener(object : ConferBotEventListener {
            override fun onSessionStarted(sessionId: String) {
                auditLog.record(
                    event = "CHAT_SESSION_STARTED",
                    patientId = patient.id,
                    sessionId = sessionId
                )
            }
        })

        Conferbot.openChat(this)
    }
}
```

### Use Case 3: Ride-sharing App

```kotlin
@Composable
fun RideTrackingScreen(ride: Ride) {
    var showDriverChat by remember { mutableStateOf(false) }

    Column {
        Map(ride.location)

        Button(
            onClick = {
                Conferbot.identify(
                    ConferBotUser(
                        id = user.id,
                        metadata = mapOf(
                            "rideId" to ride.id,
                            "driverId" to ride.driver.id,
                            "driverName" to ride.driver.name,
                            "pickup" to ride.pickup,
                            "dropoff" to ride.dropoff,
                            "estimatedArrival" to ride.eta
                        )
                    )
                )
                showDriverChat = true
            }
        ) {
            Text("Contact Support")
        }
    }

    if (showDriverChat) {
        ConferBotChatScreen(onDismiss = { showDriverChat = false })
    }
}
```

---

**Last Updated**: November 25, 2025
