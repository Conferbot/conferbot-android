# Conferbot Android SDK

[![Maven Central](https://img.shields.io/maven-central/v/com.conferbot/android-sdk)](https://central.sonatype.com/artifact/com.conferbot/android-sdk)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-5.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-blue.svg)](https://kotlinlang.org)

Native Android SDK for integrating Conferbot into your Kotlin/Java Android applications. Supports both XML Views and Jetpack Compose.

## Features

- **Native Android UI** - XML Views and Jetpack Compose support with Material Design 3
- **Real-time Chat** - Socket.IO powered instant messaging
- **Offline Support** - Messages queued when offline, sent when back online
- **Live Agent Handover** - Seamless transition from bot to human agent
- **File Uploads** - Support for images and documents
- **Push Notifications** - FCM integration for agent responses
- **Typing Indicators** - See when agent is typing
- **Dark Theme Support** - Automatic day/night theme adaptation
- **Customizable** - Full control over colors, fonts, and UI elements
- **Kotlin Coroutines** - Modern async/await pattern with Flow
- **StateFlow** - Reactive state management

## Installation

### Gradle Setup

**1. Add to project-level `build.gradle`:**

```gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**2. Add to app-level `build.gradle`:**

```gradle
dependencies {
    implementation 'com.conferbot:android-sdk:1.0.0'
}
```

**3. Sync Gradle files**

## Quick Start

### 1. Initialize SDK

In your `Application` class:

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

Update `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApplication"
    ...>
</application>
```

### 2. Open Chat (XML Views)

```kotlin
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.conferbot.sdk.core.Conferbot

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

### 3. Open Chat (Jetpack Compose)

```kotlin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.conferbot.sdk.ui.compose.ConferBotChatScreen

@Composable
fun HomeScreen() {
    var showChat by remember { mutableStateOf(false) }

    Button(onClick = { showChat = true }) {
        Text("Support Chat")
    }

    if (showChat) {
        ConferBotChatScreen(
            onDismiss = { showChat = false }
        )
    }
}
```

## Architecture

The SDK follows a clean architecture pattern:

```
conferbot/
├── models/          # Data models (Agent, Message, ChatSession)
├── services/        # API and Socket clients
├── core/            # Conferbot singleton
├── ui/
│   ├── views/       # XML Views (Activity, Adapter)
│   └── compose/     # Jetpack Compose components
└── utils/           # Constants and utilities
```

### Key Components

1. **Conferbot** - Main singleton for SDK operations
2. **ApiClient** - REST API client using Retrofit
3. **SocketClient** - Real-time Socket.IO client
4. **ChatActivity** - Full-screen chat Activity (XML)
5. **ConferBotChatScreen** - Chat screen Composable
6. **MessageAdapter** - RecyclerView adapter for messages

## Usage Patterns

### Pattern 1: Drop-in Widget (Activity)

Open a full-screen chat Activity:

```kotlin
Conferbot.openChat(context)
```

### Pattern 2: Embedded Chat (Compose)

Embed chat in your Composable:

```kotlin
@Composable
fun SupportScreen() {
    ConferBotChatView(
        modifier = Modifier.fillMaxSize(),
        onMessageSent = { message ->
            Log.d("Chat", "Sent: $message")
        }
    )
}
```

### Pattern 3: Headless (Custom UI)

Build your own UI using the SDK's state:

```kotlin
val messages by Conferbot.record.collectAsState()
val isConnected by Conferbot.isConnected.collectAsState()
val currentAgent by Conferbot.currentAgent.collectAsState()

// Your custom UI
LazyColumn {
    items(messages) { message ->
        CustomMessageBubble(message)
    }
}
```

## Configuration

### User Identification

```kotlin
import com.conferbot.sdk.models.ConferBotUser

val user = ConferBotUser(
    id = "user-123",
    name = "John Doe",
    email = "john@example.com",
    phone = "+1234567890",
    metadata = mapOf(
        "plan" to "premium",
        "signupDate" to "2024-01-15"
    )
)

Conferbot.identify(user)
```

### Customization

```kotlin
import com.conferbot.sdk.models.ConferBotCustomization
import android.graphics.Color

Conferbot.initialize(
    context = this,
    apiKey = "conf_sk_...",
    botId = "bot_...",
    customization = ConferBotCustomization(
        primaryColor = Color.parseColor("#FF6B6B"),
        headerTitle = "Customer Support",
        enableAvatar = true,
        botBubbleColor = Color.parseColor("#0100EC"),
        userBubbleColor = Color.parseColor("#EDEDED")
    )
)
```

### Event Handling

```kotlin
import com.conferbot.sdk.core.ConferBotEventListener
import com.conferbot.sdk.models.Agent
import com.conferbot.sdk.models.RecordItem

Conferbot.setEventListener(object : ConferBotEventListener {
    override fun onMessageReceived(message: RecordItem) {
        Log.d("Chat", "New message: $message")
    }

    override fun onAgentJoined(agent: Agent) {
        Log.d("Chat", "Agent joined: ${agent.name}")
    }

    override fun onSessionStarted(sessionId: String) {
        Log.d("Chat", "Session started: $sessionId")
    }
})
```

## State Management

The SDK uses Kotlin Flow for reactive state:

```kotlin
// Observe connection status
lifecycleScope.launch {
    Conferbot.isConnected.collect { isConnected ->
        updateConnectionUI(isConnected)
    }
}

// Observe messages
lifecycleScope.launch {
    Conferbot.record.collect { messages ->
        updateMessageList(messages)
    }
}

// Observe unread count
lifecycleScope.launch {
    Conferbot.unreadCount.collect { count ->
        updateBadge(count)
    }
}
```

## API Reference

### Conferbot Object

#### Methods

| Method | Description |
|--------|-------------|
| `initialize()` | Initialize SDK with config |
| `openChat(context)` | Open chat activity |
| `sendMessage(text)` | Send message programmatically |
| `identify(user)` | Identify current user |
| `registerPushToken(token)` | Register FCM token |
| `clearHistory()` | Clear chat history |
| `disconnect()` | Disconnect socket |
| `setEventListener(listener)` | Set event listener |

#### StateFlow Properties

| Property | Type | Description |
|----------|------|-------------|
| `isInitialized` | `StateFlow<Boolean>` | SDK initialization status |
| `isConnected` | `StateFlow<Boolean>` | Socket connection status |
| `chatSessionId` | `StateFlow<String?>` | Current session ID |
| `record` | `StateFlow<List<RecordItem>>` | Chat messages |
| `currentAgent` | `StateFlow<Agent?>` | Current agent |
| `unreadCount` | `StateFlow<Int>` | Unread message count |
| `isAgentTyping` | `StateFlow<Boolean>` | Agent typing status |

## Advanced Usage

### Push Notifications

See [docs/PUSH_NOTIFICATIONS.md](docs/PUSH_NOTIFICATIONS.md)

### Deep Linking

See [docs/DEEP_LINKING.md](docs/DEEP_LINKING.md)

### Custom Themes

See [docs/THEMING.md](docs/THEMING.md)

### ProGuard Rules

The SDK includes consumer ProGuard rules. If you encounter issues, add:

```proguard
-keep class com.conferbot.sdk.** { *; }
-keep class io.socket.** { *; }
```

## Examples

Check the `/example` directory for:
- XML Views implementation
- Jetpack Compose implementation
- Custom UI examples
- Push notification setup
- Deep linking examples

## Requirements

- Android 5.0 (API 21) or higher
- Kotlin 1.9 or higher
- Gradle 7.0 or higher

## Dependencies

- Retrofit 2.9.0
- OkHttp 4.11.0
- Socket.IO-Client 2.1.0
- Jetpack Compose BOM 2023.10.01
- Kotlin Coroutines 1.7.3

## Migration Guide

### From Web Widget to Android SDK

| Web Widget | Android SDK |
|------------|-------------|
| `<script>` tag | Gradle dependency |
| `window.Conferbot.open()` | `Conferbot.openChat(context)` |
| JavaScript callbacks | `ConferBotEventListener` |
| CSS customization | `ConferBotCustomization` |
| HTML embed | Native Views/Compose |

## Troubleshooting

### Common Issues

1. **"API Key Invalid" Error**
   - Verify API key in Conferbot dashboard

2. **Chat Not Appearing**
   - Ensure `initialize()` is called in `Application.onCreate()`
   - Check Logcat for errors

3. **Socket Connection Failing**
   - Add `<uses-permission android:name="android.permission.INTERNET" />` to manifest
   - Check network connectivity

4. **ProGuard Issues**
   - See consumer ProGuard rules above

## Support

- **Documentation**: [https://docs.conferbot.com/mobile/android](https://docs.conferbot.com/mobile/android)
- **GitHub Issues**: [https://github.com/conferbot/android-sdk/issues](https://github.com/conferbot/android-sdk/issues)
- **Email**: mobile-support@conferbot.com

## License

Proprietary - See LICENSE file for details

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

---

**Made with ❤️ by Conferbot Team**
