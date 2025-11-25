# API Reference

Complete API documentation for the Conferbot Android SDK.

## Table of Contents

- [Conferbot Object](#conferbot-object)
- [Models](#models)
- [Event Listener](#event-listener)
- [StateFlow Properties](#stateflow-properties)
- [Configuration](#configuration)

---

## Conferbot Object

The main SDK singleton for all operations.

### Methods

#### `initialize()`

Initialize the Conferbot SDK.

```kotlin
fun initialize(
    context: Context,
    apiKey: String,
    botId: String,
    config: ConferBotConfig = ConferBotConfig(),
    customization: ConferBotCustomization? = null,
    user: ConferBotUser? = null,
    baseUrl: String? = null,
    socketUrl: String? = null
)
```

**Parameters**:
- `context`: Application context
- `apiKey`: Your Conferbot API key (starts with `conf_sk_`)
- `botId`: Your bot ID
- `config`: SDK configuration options
- `customization`: UI customization options
- `user`: Pre-identified user (optional)
- `baseUrl`: Custom API base URL (optional)
- `socketUrl`: Custom socket URL (optional)

**Example**:
```kotlin
Conferbot.initialize(
    context = this,
    apiKey = "conf_sk_your_key_here",
    botId = "bot_abc123",
    config = ConferBotConfig(
        enableNotifications = true,
        autoConnect = true
    )
)
```

#### `openChat()`

Open the chat UI in a new Activity.

```kotlin
fun openChat(context: Context)
```

**Parameters**:
- `context`: Current context (Activity or Application)

**Example**:
```kotlin
Conferbot.openChat(this)
```

#### `initializeSession()`

Initialize a new chat session. Called automatically by `openChat()`.

```kotlin
suspend fun initializeSession(): Boolean
```

**Returns**: `Boolean` - Success status

**Example**:
```kotlin
lifecycleScope.launch {
    val success = Conferbot.initializeSession()
    if (success) {
        Log.d("Chat", "Session initialized")
    }
}
```

#### `sendMessage()`

Send a text message.

```kotlin
fun sendMessage(text: String)
```

**Parameters**:
- `text`: Message text

**Example**:
```kotlin
Conferbot.sendMessage("Hello, I need help!")
```

#### `identify()`

Identify the current user.

```kotlin
fun identify(user: ConferBotUser)
```

**Parameters**:
- `user`: User identification data

**Example**:
```kotlin
val user = ConferBotUser(
    id = "user-123",
    name = "John Doe",
    email = "john@example.com"
)
Conferbot.identify(user)
```

#### `registerPushToken()`

Register an FCM token for push notifications.

```kotlin
fun registerPushToken(token: String)
```

**Parameters**:
- `token`: FCM registration token

**Example**:
```kotlin
FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
    if (task.isSuccessful) {
        Conferbot.registerPushToken(task.result)
    }
}
```

#### `handlePushNotification()`

Check if a push notification is from Conferbot.

```kotlin
fun handlePushNotification(data: Map<String, String>): Boolean
```

**Parameters**:
- `data`: Notification data payload

**Returns**: `Boolean` - True if notification was from Conferbot

**Example**:
```kotlin
override fun onMessageReceived(remoteMessage: RemoteMessage) {
    if (Conferbot.handlePushNotification(remoteMessage.data)) {
        // Handled by Conferbot
        return
    }
    // Handle other notifications
}
```

#### `sendTypingStatus()`

Send typing indicator status.

```kotlin
fun sendTypingStatus(isTyping: Boolean)
```

**Parameters**:
- `isTyping`: Whether user is typing

**Example**:
```kotlin
editText.addTextChangedListener { text ->
    Conferbot.sendTypingStatus(text.isNotEmpty())
}
```

#### `initiateHandover()`

Request live agent handover.

```kotlin
fun initiateHandover(message: String? = null)
```

**Parameters**:
- `message`: Optional message to agent

**Example**:
```kotlin
Conferbot.initiateHandover("I need to speak with a human")
```

#### `endChat()`

End the current chat session.

```kotlin
fun endChat()
```

**Example**:
```kotlin
Conferbot.endChat()
```

#### `clearHistory()`

Clear local chat history and state.

```kotlin
fun clearHistory()
```

**Example**:
```kotlin
Conferbot.clearHistory()
```

#### `setEventListener()`

Set a listener for SDK events.

```kotlin
fun setEventListener(listener: ConferBotEventListener)
```

**Parameters**:
- `listener`: Event listener implementation

**Example**:
```kotlin
Conferbot.setEventListener(object : ConferBotEventListener {
    override fun onMessageReceived(message: RecordItem) {
        showNotification(message)
    }
})
```

#### `disconnect()`

Disconnect the socket connection.

```kotlin
fun disconnect()
```

**Example**:
```kotlin
override fun onDestroy() {
    super.onDestroy()
    Conferbot.disconnect()
}
```

#### `getCustomization()`

Get current customization settings.

```kotlin
fun getCustomization(): ConferBotCustomization?
```

**Returns**: `ConferBotCustomization?` - Current customization or null

#### `on()` / `off()`

Listen to custom socket events.

```kotlin
fun on(event: String, callback: Emitter.Listener)
fun off(event: String, callback: Emitter.Listener? = null)
```

**Example**:
```kotlin
Conferbot.on("custom-event") { data ->
    Log.d("Chat", "Custom event: $data")
}
```

---

## StateFlow Properties

Reactive state properties using Kotlin Flow.

### `isInitialized`

```kotlin
val isInitialized: StateFlow<Boolean>
```

SDK initialization status.

**Example**:
```kotlin
lifecycleScope.launch {
    Conferbot.isInitialized.collect { initialized ->
        if (initialized) {
            enableChatButton()
        }
    }
}
```

### `isConnected`

```kotlin
val isConnected: StateFlow<Boolean>
```

Socket connection status.

**Example**:
```kotlin
val isConnected by Conferbot.isConnected.collectAsState()
if (isConnected) {
    Text("Connected", color = Color.Green)
}
```

### `chatSessionId`

```kotlin
val chatSessionId: StateFlow<String?>
```

Current chat session ID (null if no session).

### `record`

```kotlin
val record: StateFlow<List<RecordItem>>
```

List of all messages in current session.

**Example**:
```kotlin
val messages by Conferbot.record.collectAsState()
LazyColumn {
    items(messages) { message ->
        MessageBubble(message)
    }
}
```

### `currentAgent`

```kotlin
val currentAgent: StateFlow<Agent?>
```

Currently connected agent (null if bot mode).

**Example**:
```kotlin
val agent by Conferbot.currentAgent.collectAsState()
agent?.let {
    Text("Chatting with ${it.name}")
}
```

### `unreadCount`

```kotlin
val unreadCount: StateFlow<Int>
```

Number of unread messages.

**Example**:
```kotlin
val unread by Conferbot.unreadCount.collectAsState()
Badge { Text("$unread") }
```

### `isAgentTyping`

```kotlin
val isAgentTyping: StateFlow<Boolean>
```

Whether agent is currently typing.

**Example**:
```kotlin
val isTyping by Conferbot.isAgentTyping.collectAsState()
if (isTyping) {
    TypingIndicator()
}
```

---

## Models

### ConferBotUser

User identification model.

```kotlin
data class ConferBotUser(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val metadata: Map<String, Any>? = null
)
```

**Example**:
```kotlin
val user = ConferBotUser(
    id = "user-123",
    name = "John Doe",
    email = "john@example.com",
    metadata = mapOf(
        "plan" to "premium",
        "userId" to 12345
    )
)
```

### ConferBotConfig

SDK configuration options.

```kotlin
data class ConferBotConfig(
    val enableNotifications: Boolean = true,
    val enableOfflineMode: Boolean = true,
    val autoConnect: Boolean = true,
    val reconnectionAttempts: Int? = null,
    val reconnectionDelay: Int? = null
)
```

**Parameters**:
- `enableNotifications`: Enable push notifications
- `enableOfflineMode`: Queue messages when offline
- `autoConnect`: Auto-connect socket on initialization
- `reconnectionAttempts`: Custom reconnection attempts (default: 5)
- `reconnectionDelay`: Custom reconnection delay in ms (default: 1000)

### ConferBotCustomization

UI customization options.

```kotlin
data class ConferBotCustomization(
    val primaryColor: Int? = null,
    val fontFamily: Int? = null,
    val bubbleRadius: Float? = null,
    val headerTitle: String? = null,
    val enableAvatar: Boolean? = null,
    val avatarUrl: String? = null,
    val botBubbleColor: Int? = null,
    val userBubbleColor: Int? = null
)
```

**Example**:
```kotlin
val customization = ConferBotCustomization(
    primaryColor = Color.parseColor("#FF6B6B"),
    headerTitle = "Customer Support",
    botBubbleColor = Color.parseColor("#0100EC"),
    userBubbleColor = Color.parseColor("#EDEDED"),
    bubbleRadius = 16f
)
```

### Agent

Agent model.

```kotlin
data class Agent(
    val id: String,
    val name: String,
    val email: String? = null,
    val avatar: String? = null,
    val title: String? = null,
    val status: String? = null
)
```

### RecordItem

Sealed class for different message types.

```kotlin
sealed class RecordItem {
    abstract val id: String
    abstract val type: MessageType
    abstract val time: Date

    data class UserMessage(...)
    data class BotMessage(...)
    data class AgentMessage(...)
    data class AgentMessageFile(...)
    data class AgentMessageAudio(...)
    data class AgentJoinedMessage(...)
    data class SystemMessage(...)
}
```

**Subtypes**:

#### UserMessage
```kotlin
data class UserMessage(
    val id: String,
    val time: Date,
    val text: String,
    val metadata: Map<String, Any>? = null
)
```

#### BotMessage
```kotlin
data class BotMessage(
    val id: String,
    val time: Date,
    val text: String? = null,
    val nodeData: Map<String, Any>? = null
)
```

#### AgentMessage
```kotlin
data class AgentMessage(
    val id: String,
    val time: Date,
    val text: String,
    val agentDetails: AgentDetails
)
```

### ChatSession

Chat session model.

```kotlin
data class ChatSession(
    val id: String,
    val chatSessionId: String,
    val botId: String,
    val visitorId: String? = null,
    val record: List<RecordItem> = emptyList(),
    val chatDate: Date? = null,
    val visitorMeta: Map<String, Any>? = null,
    val isActive: Boolean = true
)
```

---

## Event Listener

Interface for handling SDK events.

### ConferBotEventListener

```kotlin
interface ConferBotEventListener {
    fun onMessageReceived(message: RecordItem) {}
    fun onMessageSent(message: RecordItem) {}
    fun onAgentJoined(agent: Agent) {}
    fun onAgentLeft(agent: Agent) {}
    fun onSessionStarted(sessionId: String) {}
    fun onSessionEnded(sessionId: String) {}
    fun onTypingIndicator(isTyping: Boolean) {}
    fun onUnreadCountChanged(count: Int) {}
}
```

**Example Implementation**:
```kotlin
class ChatEventHandler : ConferBotEventListener {
    override fun onMessageReceived(message: RecordItem) {
        when (message) {
            is RecordItem.BotMessage -> {
                Log.d("Chat", "Bot: ${message.text}")
            }
            is RecordItem.AgentMessage -> {
                Log.d("Chat", "Agent: ${message.text}")
                showNotification(message)
            }
        }
    }

    override fun onAgentJoined(agent: Agent) {
        Toast.makeText(context, "${agent.name} joined", Toast.LENGTH_SHORT).show()
    }

    override fun onSessionStarted(sessionId: String) {
        analytics.track("chat_started", mapOf("sessionId" to sessionId))
    }
}

// Register
Conferbot.setEventListener(ChatEventHandler())
```

---

## Socket Events

Constants for socket events (read-only).

### Client → Server Events

```kotlin
SocketEvents.MOBILE_INIT
SocketEvents.SEND_VISITOR_MESSAGE
SocketEvents.VISITOR_TYPING
SocketEvents.INITIATE_HANDOVER
SocketEvents.END_CHAT
```

### Server → Client Events

```kotlin
SocketEvents.BOT_RESPONSE
SocketEvents.AGENT_MESSAGE
SocketEvents.AGENT_ACCEPTED
SocketEvents.AGENT_LEFT
SocketEvents.AGENT_TYPING_STATUS
SocketEvents.CHAT_ENDED
```

### Connection Events

```kotlin
SocketEvents.CONNECT
SocketEvents.DISCONNECT
SocketEvents.CONNECT_ERROR
SocketEvents.RECONNECT
```

---

## Constants

SDK constants (read-only).

```kotlin
Constants.DEFAULT_API_BASE_URL = "https://embed.conferbot.com/api/v1/mobile"
Constants.DEFAULT_SOCKET_URL = "https://embed.conferbot.com"
Constants.API_TIMEOUT = 30000L
Constants.MAX_MESSAGE_LENGTH = 5000
Constants.MAX_FILE_SIZE = 10485760
```

---

**Last Updated**: November 25, 2025
