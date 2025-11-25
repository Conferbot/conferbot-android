# UI Components Reference

Complete reference for all UI components in the Conferbot Android SDK.

## Table of Contents

- [XML Views Components](#xml-views-components)
- [Jetpack Compose Components](#jetpack-compose-components)
- [Layouts](#layouts)
- [Customization](#customization)

---

## XML Views Components

### ChatActivity

Full-screen chat Activity with RecyclerView, input field, and header.

**Usage**:
```kotlin
Conferbot.openChat(context)
```

**Features**:
- RecyclerView for messages
- Auto-scroll to latest message
- Typing indicator
- Connection status banner
- Send button with enabled/disabled state
- Back button

**Customization**:
Apply via `ConferBotCustomization`:
```kotlin
Conferbot.initialize(
    context = this,
    customization = ConferBotCustomization(
        headerTitle = "Custom Title",
        primaryColor = Color.parseColor("#FF6B6B")
    )
)
```

### MessageAdapter

RecyclerView adapter for displaying messages with multiple view types.

**View Types**:
1. User Message (right-aligned)
2. Bot Message (left-aligned, gray bubble)
3. Agent Message (left-aligned, green bubble with agent name)
4. System Message (centered text)

**Usage**:
```kotlin
val adapter = MessageAdapter()
recyclerView.adapter = adapter

// Observe messages
Conferbot.record.onEach { messages ->
    adapter.submitList(messages)
}.launchIn(lifecycleScope)
```

**ViewHolders**:
- `UserMessageViewHolder` - User messages
- `BotMessageViewHolder` - Bot messages
- `AgentMessageViewHolder` - Agent messages with avatar
- `SystemMessageViewHolder` - System notifications

### Custom RecyclerView Implementation

Create your own message list:

```kotlin
class CustomChatFragment : Fragment() {
    private lateinit var adapter: CustomMessageAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.messagesRecyclerView)
        adapter = CustomMessageAdapter()

        recyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = this@CustomChatFragment.adapter
        }

        // Observe messages
        lifecycleScope.launch {
            Conferbot.record.collect { messages ->
                adapter.submitList(messages)
            }
        }
    }
}
```

---

## Jetpack Compose Components

### ConferBotChatScreen

Main chat screen with full UI (header, messages, input).

**Signature**:
```kotlin
@Composable
fun ConferBotChatScreen(
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
)
```

**Usage**:
```kotlin
var showChat by remember { mutableStateOf(false) }

if (showChat) {
    ConferBotChatScreen(
        onDismiss = { showChat = false }
    )
}
```

**Features**:
- Scaffold with TopAppBar
- Connection status banner
- Message list with LazyColumn
- Typing indicator
- Chat input with send button
- Auto-scroll to latest message

### ConferBotChatView

Embeddable chat view without scaffold (for custom layouts).

**Signature**:
```kotlin
@Composable
fun ConferBotChatView(
    modifier: Modifier = Modifier,
    onMessageSent: ((String) -> Unit)? = null,
    onMessageReceived: ((String) -> Unit)? = null,
    onAgentJoined: ((String) -> Unit)? = null
)
```

**Usage**:
```kotlin
@Composable
fun SupportScreen() {
    Scaffold(
        topBar = { CustomTopBar() }
    ) {
        ConferBotChatView(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            onMessageSent = { text ->
                Log.d("Chat", "Sent: $text")
            }
        )
    }
}
```

### ChatHeader

Customizable chat header with title and back button.

**Signature**:
```kotlin
@Composable
fun ChatHeader(
    title: String,
    onBackClick: () -> Unit,
    isConnected: Boolean,
    modifier: Modifier = Modifier
)
```

**Usage**:
```kotlin
ChatHeader(
    title = "Support",
    onBackClick = { finish() },
    isConnected = true
)
```

### MessageBubble

Message bubble component with support for all message types.

**Signature**:
```kotlin
@Composable
fun MessageBubble(
    message: RecordItem,
    modifier: Modifier = Modifier
)
```

**Types**:
```kotlin
when (message) {
    is RecordItem.UserMessage -> UserMessageBubble(message)
    is RecordItem.BotMessage -> BotMessageBubble(message)
    is RecordItem.AgentMessage -> AgentMessageBubble(message)
    is RecordItem.SystemMessage -> SystemMessageBubble(message)
}
```

**Custom Styling**:
```kotlin
@Composable
fun CustomMessageBubble(message: RecordItem.UserMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            color = Color(0xFF0100EC),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    color = Color.White
                )
                Text(
                    text = formatTime(message.time),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
```

### MessageList

LazyColumn-based message list.

**Signature**:
```kotlin
@Composable
fun MessageList(
    messages: List<RecordItem>,
    isAgentTyping: Boolean,
    modifier: Modifier = Modifier
)
```

**Usage**:
```kotlin
val messages by Conferbot.record.collectAsState()
val isAgentTyping by Conferbot.isAgentTyping.collectAsState()

MessageList(
    messages = messages,
    isAgentTyping = isAgentTyping,
    modifier = Modifier.weight(1f)
)
```

### ChatInput

Text input field with send button.

**Signature**:
```kotlin
@Composable
fun ChatInput(
    onSendMessage: (String) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
)
```

**Usage**:
```kotlin
ChatInput(
    onSendMessage = { text ->
        Conferbot.sendMessage(text)
    },
    onTypingChanged = { isTyping ->
        Conferbot.sendTypingStatus(isTyping)
    }
)
```

**Custom Implementation**:
```kotlin
@Composable
fun CustomChatInput() {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                Conferbot.sendTypingStatus(newText.isNotEmpty())
            },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a message...") },
            shape = RoundedCornerShape(24.dp)
        )

        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    Conferbot.sendMessage(text.trim())
                    text = ""
                }
            },
            enabled = text.isNotBlank()
        ) {
            Icon(Icons.Default.Send, "Send")
        }
    }
}
```

### TypingIndicator

Animated typing indicator (three dots).

**Signature**:
```kotlin
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
)
```

**Usage**:
```kotlin
val isAgentTyping by Conferbot.isAgentTyping.collectAsState()

if (isAgentTyping) {
    TypingIndicator()
}
```

### ConnectionStatusBanner

Banner shown when connection is lost.

**Signature**:
```kotlin
@Composable
fun ConnectionStatusBanner(
    modifier: Modifier = Modifier
)
```

**Usage**:
```kotlin
val isConnected by Conferbot.isConnected.collectAsState()

if (!isConnected) {
    ConnectionStatusBanner()
}
```

---

## Layouts

### activity_chat.xml

Main chat layout for XML Views.

**Structure**:
```xml
<ConstraintLayout>
    <ChatHeader />
    <ConnectionStatus />
    <RecyclerView /> <!-- Messages -->
    <TypingIndicator />
    <LinearLayout> <!-- Input container -->
        <EditText /> <!-- Message input -->
        <ImageButton /> <!-- Send button -->
    </LinearLayout>
</ConstraintLayout>
```

### item_message_user.xml

User message bubble layout (right-aligned).

**Structure**:
```xml
<LinearLayout gravity="end">
    <LinearLayout background="@drawable/bg_user_bubble">
        <TextView id="messageText" />
        <TextView id="timeText" />
    </LinearLayout>
</LinearLayout>
```

### item_message_bot.xml

Bot message bubble layout (left-aligned).

**Structure**:
```xml
<LinearLayout gravity="start">
    <ImageView id="avatarImage" />
    <LinearLayout background="@drawable/bg_bot_bubble">
        <TextView id="messageText" />
        <TextView id="timeText" />
    </LinearLayout>
</LinearLayout>
```

### item_message_agent.xml

Agent message bubble layout with agent info.

**Structure**:
```xml
<LinearLayout gravity="start">
    <ImageView id="avatarImage" />
    <LinearLayout background="@drawable/bg_agent_bubble">
        <TextView id="agentName" />
        <TextView id="messageText" />
        <TextView id="timeText" />
    </LinearLayout>
</LinearLayout>
```

---

## Customization

### Colors

Customize colors in `values/colors.xml`:

```xml
<resources>
    <color name="primary">#0100EC</color>
    <color name="bot_bubble">#F5F5F5</color>
    <color name="user_bubble">#0100EC</color>
    <color name="agent_bubble">#E8F5E9</color>
</resources>
```

Or programmatically:

```kotlin
Conferbot.initialize(
    context = this,
    customization = ConferBotCustomization(
        primaryColor = Color.parseColor("#FF6B6B"),
        botBubbleColor = Color.parseColor("#F0F0F0"),
        userBubbleColor = Color.parseColor("#0100EC")
    )
)
```

### Shapes

Customize bubble shapes in `drawable/`:

**bg_user_bubble.xml**:
```xml
<shape android:shape="rectangle">
    <solid android:color="@color/user_bubble" />
    <corners
        android:radius="16dp"
        android:topRightRadius="4dp" />
</shape>
```

### Typography

Set custom font:

```kotlin
ConferBotCustomization(
    fontFamily = R.font.inter_regular
)
```

### Compose Theming

Wrap in custom theme:

```kotlin
@Composable
fun CustomChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFFF6B6B),
            surface = Color(0xFFF5F5F5)
        )
    ) {
        content()
    }
}

@Composable
fun MyApp() {
    CustomChatTheme {
        ConferBotChatScreen(onDismiss = {})
    }
}
```

### Custom Message Renderer

Create custom message UI:

```kotlin
@Composable
fun CustomMessageRenderer(messages: List<RecordItem>) {
    LazyColumn {
        items(messages, key = { it.id }) { message ->
            when (message) {
                is RecordItem.UserMessage -> {
                    // Custom user bubble
                    MyCustomUserBubble(message)
                }
                is RecordItem.BotMessage -> {
                    // Custom bot bubble
                    MyCustomBotBubble(message)
                }
                // ... other types
            }
        }
    }
}
```

### Headless Integration

Use SDK state without UI:

```kotlin
@Composable
fun HeadlessChat() {
    val messages by Conferbot.record.collectAsState()
    var input by remember { mutableStateOf("") }

    Column {
        // Your custom message list
        LazyColumn {
            items(messages) { message ->
                YourCustomMessageView(message)
            }
        }

        // Your custom input
        YourCustomInput(
            value = input,
            onValueChange = { input = it },
            onSend = {
                Conferbot.sendMessage(input)
                input = ""
            }
        )
    }
}
```

---

## Advanced Components

### With ViewModel

```kotlin
class ChatViewModel : ViewModel() {
    val messages = Conferbot.record
    val isConnected = Conferbot.isConnected
    val currentAgent = Conferbot.currentAgent

    fun sendMessage(text: String) {
        Conferbot.sendMessage(text)
    }
}

@Composable
fun ChatScreenWithViewModel(viewModel: ChatViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    ChatUI(
        messages = messages,
        isConnected = isConnected,
        onSendMessage = viewModel::sendMessage
    )
}
```

### With Navigation

```kotlin
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController, "home") {
        composable("home") {
            HomeScreen {
                navController.navigate("chat")
            }
        }
        composable("chat") {
            ConferBotChatScreen {
                navController.popBackStack()
            }
        }
    }
}
```

### Floating Chat Button

```kotlin
@Composable
fun MainScreen() {
    var showChat by remember { mutableStateOf(false) }
    val unreadCount by Conferbot.unreadCount.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showChat = true }) {
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
        // Main content
    }

    if (showChat) {
        ConferBotChatScreen(onDismiss = { showChat = false })
    }
}
```

---

**Last Updated**: November 25, 2025
