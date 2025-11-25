# Architecture

This document describes the architecture and design patterns of the Conferbot Android SDK.

## Overview

The SDK follows a clean architecture pattern with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────┐
│                     UI Layer                             │
│  ┌──────────────┐              ┌──────────────┐        │
│  │  XML Views   │              │   Compose    │        │
│  │              │              │              │        │
│  │ ChatActivity │              │ ChatScreen   │        │
│  │ MessageAdapter│             │ MessageBubble│        │
│  └──────────────┘              └──────────────┘        │
└─────────────────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                   Core Layer                             │
│                                                          │
│              ┌─────────────────┐                        │
│              │   Conferbot     │  (Singleton)           │
│              │   - StateFlow   │                        │
│              │   - Methods     │                        │
│              └─────────────────┘                        │
└─────────────────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                 Service Layer                            │
│  ┌──────────────┐              ┌──────────────┐        │
│  │  ApiClient   │              │ SocketClient │        │
│  │  (Retrofit)  │              │  (Socket.IO) │        │
│  └──────────────┘              └──────────────┘        │
└─────────────────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                  Data Layer                              │
│                                                          │
│  Agent, Message, ChatSession, SocketEvents              │
└─────────────────────────────────────────────────────────┘
```

## Layers

### 1. Data Layer (Models)

**Location**: `com.conferbot.sdk.models`

**Responsibilities**:
- Define data structures
- Handle JSON serialization/deserialization
- Model business entities

**Components**:
- `Agent.kt` - Agent and AgentDetails models
- `Message.kt` - Sealed class hierarchy for different message types
- `ChatSession.kt` - Chat session data
- `SocketEvents.kt` - Socket event constants
- `Configuration.kt` - SDK configuration models

**Design Patterns**:
- **Sealed Classes**: Message types use sealed classes for type-safe polymorphism
- **Data Classes**: Immutable data structures with copy() support
- **Builder Pattern**: Configuration classes support builder-style creation

### 2. Service Layer

**Location**: `com.conferbot.sdk.services`

**Responsibilities**:
- Handle network communication
- Manage API calls
- Maintain socket connections

**Components**:

#### ApiClient.kt
- REST API communication using Retrofit
- Coroutine-based async operations
- Automatic header injection (API key, Bot ID, Platform)
- Response parsing and error handling
- Endpoints:
  - `POST /session/init` - Initialize session
  - `GET /session/{id}` - Get session history
  - `POST /session/{id}/message` - Send message
  - `POST /push/register` - Register FCM token

#### SocketClient.kt
- Real-time communication using Socket.IO
- Connection management with auto-reconnect
- Event emission and listening
- Custom header support
- Events:
  - Client → Server: mobile-init, send-visitor-message, visitor-typing
  - Server → Client: bot-response, agent-message, agent-accepted

**Design Patterns**:
- **Repository Pattern**: ApiClient acts as repository for REST data
- **Observer Pattern**: SocketClient uses event listeners
- **Singleton**: Both clients are instantiated once in Conferbot

### 3. Core Layer

**Location**: `com.conferbot.sdk.core`

**Responsibilities**:
- Central SDK coordination
- State management
- Event routing
- Public API surface

**Components**:

#### Conferbot.kt
Main SDK singleton providing:

**State (StateFlow)**:
```kotlin
val isInitialized: StateFlow<Boolean>
val isConnected: StateFlow<Boolean>
val chatSessionId: StateFlow<String?>
val record: StateFlow<List<RecordItem>>
val currentAgent: StateFlow<Agent?>
val unreadCount: StateFlow<Int>
val isAgentTyping: StateFlow<Boolean>
```

**Methods**:
```kotlin
fun initialize(...)
fun openChat(context)
suspend fun initializeSession()
fun sendMessage(text)
fun identify(user)
fun registerPushToken(token)
fun setEventListener(listener)
fun disconnect()
```

**Design Patterns**:
- **Singleton Object**: Single instance across app
- **State Pattern**: StateFlow for reactive state
- **Observer Pattern**: Event listener interface
- **Facade Pattern**: Simplifies complex subsystem interactions

#### ConferBotEventListener Interface
```kotlin
interface ConferBotEventListener {
    fun onMessageReceived(message: RecordItem)
    fun onMessageSent(message: RecordItem)
    fun onAgentJoined(agent: Agent)
    fun onAgentLeft(agent: Agent)
    fun onSessionStarted(sessionId: String)
    fun onSessionEnded(sessionId: String)
    fun onTypingIndicator(isTyping: Boolean)
    fun onUnreadCountChanged(count: Int)
}
```

### 4. UI Layer

**Location**: `com.conferbot.sdk.ui`

**Responsibilities**:
- Render chat interface
- Handle user interactions
- Display messages and typing indicators

#### XML Views (`ui/views/`)

**ChatActivity.kt**
- Full-screen chat Activity
- RecyclerView for messages
- EditText + Send button
- Connection status banner
- Typing indicator

**MessageAdapter.kt**
- RecyclerView adapter
- Multiple view types (user, bot, agent, system)
- DiffUtil for efficient updates
- ViewHolder pattern

**Layout Files**:
- `activity_chat.xml` - Main chat layout
- `item_message_user.xml` - User message bubble
- `item_message_bot.xml` - Bot message bubble
- `item_message_agent.xml` - Agent message bubble
- `view_chat_header.xml` - Header with title/back
- `view_typing_indicator.xml` - Typing dots

#### Jetpack Compose (`ui/compose/`)

**ConferBotChatScreen.kt**
- Main chat Composable
- Scaffold with TopAppBar
- LazyColumn for messages
- ChatInput composable
- Connection status banner

**MessageBubble.kt**
- Message rendering logic
- Different bubbles for user/bot/agent
- Time formatting
- Styling

**ConferBotChatView.kt**
- Embeddable chat view
- No scaffold (for embedding)

**Design Patterns**:
- **MVC/MVVM**: Activity/Composable observes StateFlow (ViewModel pattern)
- **Adapter Pattern**: MessageAdapter adapts data to views
- **ViewHolder Pattern**: Efficient RecyclerView rendering
- **Composition**: Compose uses composition over inheritance

## State Management

### StateFlow Architecture

```kotlin
// Conferbot singleton manages state
private val _record = MutableStateFlow<List<RecordItem>>(emptyList())
val record: StateFlow<List<RecordItem>> = _record.asStateFlow()

// UI observes state
Conferbot.record
    .onEach { messages ->
        updateUI(messages)
    }
    .launchIn(lifecycleScope)
```

**Benefits**:
- Type-safe state
- Reactive updates
- Lifecycle-aware
- No memory leaks
- Testable

### State Flow

```
Socket Event → SocketClient → Conferbot → StateFlow → UI
                                           ↓
                                      Event Listener
```

## Threading Model

### Coroutines and Dispatchers

```kotlin
// Main thread (UI updates)
Dispatchers.Main

// IO thread (Network calls)
Dispatchers.IO (handled by Retrofit/OkHttp)

// Default thread (CPU-intensive work)
Dispatchers.Default
```

### Scope Hierarchy

```
Application Scope (Conferbot singleton)
    │
    ├─→ API calls (suspend functions)
    │
    └─→ Socket events (callbacks → StateFlow)

Activity Scope (ChatActivity)
    │
    └─→ UI updates (collect StateFlow)
```

## Data Flow

### Message Send Flow

```
User Types → ChatInput → Conferbot.sendMessage()
                            │
                            ├─→ Add to StateFlow (optimistic update)
                            │
                            └─→ SocketClient.sendVisitorMessage()
                                    │
                                    └─→ Server
```

### Message Receive Flow

```
Server → Socket Event → SocketClient listener
                            │
                            └─→ Conferbot.handleMessageReceived()
                                    │
                                    ├─→ Update StateFlow
                                    │
                                    └─→ Trigger EventListener
                                            │
                                            └─→ UI updates (automatically via StateFlow)
```

### Session Initialization Flow

```
App Start → Conferbot.initialize()
                │
                └─→ Create ApiClient, SocketClient
                        │
                        └─→ SocketClient.connect()

User Opens Chat → Conferbot.openChat()
                      │
                      └─→ initializeSession() (if needed)
                              │
                              ├─→ ApiClient.initSession()
                              │       │
                              │       └─→ POST /session/init
                              │
                              ├─→ SocketClient.joinChatRoom()
                              │
                              └─→ SocketClient.mobileInit()
```

## Error Handling

### Network Errors

```kotlin
try {
    val response = apiClient.initSession()
    if (response.success) {
        // Handle success
    } else {
        // Handle error from response.error
    }
} catch (e: Exception) {
    // Handle network exception
}
```

### Socket Errors

```kotlin
socketClient.on(SocketEvents.CONNECT_ERROR) { error ->
    _isConnected.value = false
    // Auto-reconnect handled by Socket.IO
}
```

## Testing Strategy

### Unit Tests
- Models: Serialization/deserialization
- ApiClient: Mock Retrofit responses
- SocketClient: Mock socket events
- Conferbot: State transitions

### Integration Tests
- Full message flow
- Session initialization
- Socket reconnection

### UI Tests
- ChatActivity interactions
- Compose screenshot tests
- Accessibility tests

## Performance Considerations

### Memory Management
- WeakReferences for listeners (avoided via lifecycle-aware StateFlow)
- RecyclerView view recycling
- Compose recomposition optimization
- Image caching with Coil

### Network Optimization
- OkHttp connection pooling
- Gzip compression
- Request debouncing for typing indicators
- Offline message queuing

### Battery Optimization
- Socket auto-disconnect when app backgrounded
- Doze mode compatibility
- WorkManager for background sync

## Security

### Network Security
- HTTPS for all API calls
- WSS for socket connections
- Certificate pinning (optional)
- No sensitive data in logs (production builds)

### Data Security
- No local storage of messages (by default)
- Encryption in transit
- API key obfuscation via ProGuard

## Extensibility

### Custom UI
```kotlin
// Use headless mode
val messages by Conferbot.record.collectAsState()
CustomMessageList(messages)
```

### Custom Events
```kotlin
Conferbot.on("custom-event") { data ->
    // Handle custom socket event
}
```

### Custom Configuration
```kotlin
ConferBotCustomization(
    primaryColor = customColor,
    headerTitle = customTitle
)
```

## Dependencies

### Core Dependencies
- Kotlin stdlib
- Kotlin Coroutines
- AndroidX Core

### Networking
- Retrofit (REST)
- OkHttp (HTTP client)
- Gson (JSON)
- Socket.IO-Client (WebSocket)

### UI
- Material Design 3
- RecyclerView
- Jetpack Compose
- Coil (image loading)

### Lifecycle
- AndroidX Lifecycle
- ViewModel
- LiveData/StateFlow

## Versioning

The SDK follows Semantic Versioning (SemVer):

- **Major**: Breaking API changes
- **Minor**: New features (backward compatible)
- **Patch**: Bug fixes

Example: `1.2.3`
- Major: 1
- Minor: 2
- Patch: 3

---

**Last Updated**: November 25, 2025
