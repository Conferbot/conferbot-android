# Conferbot Android SDK - Integration Tutorial

A step-by-step walkthrough for integrating the Conferbot Android SDK into a Kotlin app, from an empty project to a fully themed, push-enabled chat with offline support. Every snippet compiles against SDK 1.0.0.

For a quick overview see the [README](../README.md). For the method-by-method reference see [API.md](API.md).

## Contents

1. [Prerequisites](#1-prerequisites)
2. [Install the SDK](#2-install-the-sdk)
3. [Initialize in your Application class](#3-initialize-in-your-application-class)
4. [Add the floating chat bubble](#4-add-the-floating-chat-bubble)
5. [Alternative entry points](#5-alternative-entry-points)
6. [Identify your user](#6-identify-your-user)
7. [Theming](#7-theming)
8. [Listen to events](#8-listen-to-events)
9. [Build a custom (headless) UI](#9-build-a-custom-headless-ui)
10. [Offline mode and session persistence](#10-offline-mode-and-session-persistence)
11. [Push notifications with FCM](#11-push-notifications-with-fcm)
12. [Knowledge base](#12-knowledge-base)
13. [Analytics](#13-analytics)
14. [Testing against a non-production server](#14-testing-against-a-non-production-server)
15. [Release checklist](#15-release-checklist)

---

## 1. Prerequisites

- Android Studio with an app targeting **minSdk 21+**, compiled with **Java 17** and **Kotlin 1.9+**
- Jetpack Compose enabled in your app module if you want the Compose UI or the floating widget (the XML `ChatActivity` path works without Compose in your own code)
- A Conferbot bot ID and API key from [app.conferbot.com](https://app.conferbot.com), or the public demo bot ID `691c970890527a0468f9b2c9` for evaluation

## 2. Install the SDK

`settings.gradle` (or project `build.gradle`):

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

App module `build.gradle`:

```gradle
dependencies {
    implementation 'com.conferbot:android-sdk:1.0.0'
}
```

The SDK bundles Socket.IO, Retrofit/OkHttp, Room, Coil/Glide, Compose Material 3, and `firebase-messaging-ktx` as transitive dependencies. Make sure your app declares the Internet permission:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## 3. Initialize in your Application class

The SDK is a process-wide singleton (`object Conferbot`). Initialize it exactly once:

```kotlin
import android.app.Application
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.models.ConferBotConfig

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Conferbot.initialize(
            context = this,
            apiKey = "YOUR_API_KEY",
            botId = "YOUR_BOT_ID"
        )
    }
}
```

```xml
<application android:name=".MyApplication" ...>
```

What happens on initialize:

- The REST client and Socket.IO client are created against `https://wdt.conferbot.com`
- With `autoConnect = true` (default) the socket connects and requests the bot's flow, so the first open is instant
- The node flow engine, offline queue, notification components, and analytics are wired up
- A stable anonymous visitor ID is generated and stored in SharedPreferences

Validation rules to be aware of:

- `apiKey` must be non-blank and at least 8 characters
- `botId` must be non-blank
- Calling `initialize()` twice throws `IllegalStateException` - call `Conferbot.disconnect()` first if you need to re-initialize (e.g. switching bots after login)

The full signature, with everything optional after `botId`:

```kotlin
fun initialize(
    context: Context,
    apiKey: String,
    botId: String,
    config: ConferBotConfig = ConferBotConfig(),
    customization: ConferBotCustomization? = null,
    user: ConferBotUser? = null,
    baseUrl: String? = null,
    socketUrl: String? = null,
    paginationConfig: PaginationConfig = PaginationConfig()
)
```

`ConferBotConfig` defaults: `enableNotifications = true`, `enableOfflineMode = true`, `autoConnect = true`.

## 4. Add the floating chat bubble

This is the recommended pattern for most apps - it mirrors the Conferbot web widget: a bubble in the bottom-right corner that opens the chat as a bottom sheet.

```kotlin
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.conferbot.sdk.ui.compose.ConferBotWidgetScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyAppTheme {
                ConferBotWidgetScope {
                    // Your existing app UI
                    MyMainScreen()
                }
            }
        }
    }
}
```

`ConferBotWidgetScope` simply stacks `ConferBotWidget()` on top of your content in a `Box`. If you already have a root `Box`, place the widget yourself:

```kotlin
Box(Modifier.fillMaxSize()) {
    MyMainScreen()
    ConferBotWidget()
}
```

Out of the box you get:

- A FAB whose color, icon, size, corner radius, position (left/right), and edge offsets come from your dashboard's widget customizations
- An unread-count badge (suppressed while the chat is open)
- The CTA tooltip from the dashboard (`chatIconCtaText`), auto-shown after 2 seconds
- An animated bottom-sheet chat (88% screen height) with a tap-to-dismiss scrim
- Automatic unread reset and in-app notification suppression while the chat is visible

### Local fallbacks

`ConferBotWidgetConfig` provides defaults for anything the server does not set. Server values always take priority, attribute by attribute:

```kotlin
import com.conferbot.sdk.ui.compose.ConferBotWidget
import com.conferbot.sdk.ui.compose.ConferBotWidgetConfig
import com.conferbot.sdk.ui.compose.WidgetPosition

ConferBotWidget(
    config = ConferBotWidgetConfig(
        position = WidgetPosition.BOTTOM_LEFT,
        size = 56.dp,
        offsetX = 16.dp,
        offsetY = 24.dp,
        backgroundColor = Color(0xFF10B981),
        showUnreadBadge = true,
        borderRadius = 16.dp
    )
)
```

Note: `showUnreadBadge` is purely local (no server counterpart). Everything else can be overridden from the dashboard, in which case your config value is ignored for that attribute.

### XML apps

There is no View-based bubble. Either host the composable in a `ComposeView` inside your layout, or wire your own `FloatingActionButton` to:

```kotlin
Conferbot.openChat(this)
```

## 5. Alternative entry points

### Full-screen Activity (zero Compose required)

```kotlin
Conferbot.openChat(context)
```

Creates a session if needed and launches the SDK's `ChatActivity` (`com.conferbot.sdk.ui.views.ChatActivity`) as a new task. This Activity honors the `ConferBotCustomization` passed to `initialize()`.

### Embedded Compose screen

```kotlin
import com.conferbot.sdk.ui.compose.ConferBotChatScreen

ConferBotChatScreen(onDismiss = { navController.popBackStack() })
```

Use this when the chat is a destination in your own navigation graph. It initializes the session on first composition, renders the paginated message list, node interactions (choices, forms, calendars, surveys), status banners, and the input bar.

### Themed variants

```kotlin
import com.conferbot.sdk.ui.compose.ConferBotChatView
import com.conferbot.sdk.ui.compose.ConferBotThemedChatScreen

ConferBotChatView(theme = myTheme)                       // single fixed theme
ConferBotChatView(lightTheme = myLight, darkTheme = myDark, useDarkTheme = null)
ConferBotThemedChatScreen(onDismiss = { /* ... */ })     // modal with dismiss
```

## 6. Identify your user

By default visitors are anonymous (a generated visitor ID persisted on the device). To attach identity, pass a `ConferBotUser` at initialization or call `identify()` **before the first session is created**:

```kotlin
import com.conferbot.sdk.models.ConferBotUser

Conferbot.identify(
    ConferBotUser(
        id = "user-123",              // required
        name = "Jane Doe",
        email = "jane@example.com",
        phone = "+1234567890",
        metadata = mapOf("plan" to "premium")
    )
)
```

Timing matters: the user ID is sent when the session is created (the SDK calls `initSession(userId = user?.id)`), which happens the first time the chat is opened or when you call `initializeSession()` yourself. Identifying after a session already exists updates the SDK's in-memory user only; it does not re-tag the running session. A typical login flow:

```kotlin
fun onLoggedIn(user: MyUser) {
    Conferbot.identify(ConferBotUser(id = user.id, name = user.name, email = user.email))
}

fun onLoggedOut() {
    Conferbot.clearHistory()        // drop the previous user's local conversation
    Conferbot.unregisterPushToken() // stop routing pushes to this device
}
```

## 7. Theming

### 7.1 Server customizations (recommended)

Style the bot once in the dashboard flow builder and every platform - web, Android, iOS, Flutter - renders it consistently. When the SDK receives the bot data it parses `customizations` into:

- `Conferbot.serverCustomization: StateFlow<ServerChatbotCustomization?>` - raw values (bot name, avatar URL, logo, widget placement, branding, tagline, ...)
- `Conferbot.serverTheme: StateFlow<ConferbotTheme?>` - a complete theme built from the color values (header, bot/user bubbles, chat background incl. gradient and image backgrounds, bubble radius, font size)

You do not need to touch either; the built-in UI consumes them automatically.

### 7.2 Precedence: server beats local

This is verified behavior, not convention: `ConferBotChatScreen` collects `serverTheme` and, when it is non-null, wraps the whole chat in `ConferbotThemeProvider(theme = serverTheme)`. Because that provider sits *inside* any provider you add, **dashboard customizations override local themes** whenever the bot has a `customizations` object. Your local theme is the fallback for bots with no server customizations.

The floating widget resolves each attribute independently (see the table in the README), so a dashboard that only sets the widget color still uses your local size/position.

The XML `ChatActivity` reads the local `ConferBotCustomization`; header title precedence in the Compose UI is: live agent name > server `botName` > server `logoText` > "Support Chat".

### 7.3 Building a local theme

```kotlin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.ui.theme.ConferbotThemeBuilder
import com.conferbot.sdk.ui.theme.LightTheme

val brandTheme = ConferbotThemeBuilder.create()
    .baseTheme(LightTheme)                 // start from the SDK light theme
    .name("Brand")
    .primaryColor(Color(0xFF6366F1))
    .headerColors(background = Color(0xFF6366F1), text = Color.White)
    .botBubbleColors(background = Color(0xFFF3F4F6), text = Color(0xFF111827))
    .userBubbleColors(background = Color(0xFF6366F1), text = Color.White)
    .inputColors(
        background = Color.White,
        text = Color(0xFF111827),
        border = Color(0xFFE5E7EB)
    )
    .bubbleRadius(16.dp)
    .messageSize(15.sp)
    .build()
```

Apply it:

```kotlin
ConferBotChatView(theme = brandTheme)
```

Or provide it to any SDK composable via the provider:

```kotlin
import com.conferbot.sdk.ui.theme.ConferbotThemeProvider

ConferbotThemeProvider(theme = brandTheme) {
    ConferBotChatScreen(onDismiss = { /* ... */ })
}
```

(Remember 7.2: if the server sends customizations, they win inside `ConferBotChatScreen`.)

Built-in themes `LightTheme` and `DarkTheme` live in `com.conferbot.sdk.ui.theme`. `ConferBotChatView(lightTheme, darkTheme, useDarkTheme = null)` follows the system dark-mode setting.

## 8. Listen to events

One listener, all default no-ops - override what you need:

```kotlin
import com.conferbot.sdk.core.ConferBotEventListener
import com.conferbot.sdk.models.Agent
import com.conferbot.sdk.models.RecordItem

Conferbot.setEventListener(object : ConferBotEventListener {
    override fun onSessionStarted(sessionId: String) {
        // Good moment to register the push token (see section 11)
    }
    override fun onMessageReceived(message: RecordItem) { /* badge, sound, ... */ }
    override fun onAgentJoined(agent: Agent) { /* show "Talking to ${agent.name}" */ }
    override fun onUnreadCountChanged(count: Int) { /* update your own badge */ }
    override fun onQueueSynced(successCount: Int, failCount: Int) { /* offline sync done */ }
})
```

Full callback list: `onMessageReceived`, `onMessageSent`, `onAgentJoined`, `onAgentLeft`, `onSessionStarted`, `onSessionEnded`, `onTypingIndicator(Boolean)`, `onUnreadCountChanged(Int)`, `onOnlineStatusChanged(Boolean)`, `onMessageFailed(String)`, `onQueueSynced(Int, Int)`.

For anything not covered, subscribe to raw Socket.IO events:

```kotlin
import com.conferbot.sdk.models.SocketEvents
import io.socket.emitter.Emitter

val onBotResponse = Emitter.Listener { args -> /* JSONObject payload in args[0] */ }
Conferbot.on(SocketEvents.BOT_RESPONSE, onBotResponse)
// later
Conferbot.off(SocketEvents.BOT_RESPONSE, onBotResponse)
```

Useful constants: `BOT_RESPONSE`, `AGENT_MESSAGE`, `AGENT_ACCEPTED`, `AGENT_LEFT`, `AGENT_TYPING_STATUS`, `CHAT_ENDED`, `NO_AGENTS_AVAILABLE`, `CONNECT`, `DISCONNECT`, `RECONNECT`.

## 9. Build a custom (headless) UI

Everything the built-in UI does is available to your own screens.

### 9.1 Start a session

```kotlin
lifecycleScope.launch {
    if (Conferbot.chatSessionId.value == null) {
        val ok = Conferbot.initializeSession()   // suspend fun, returns Boolean
        if (!ok) { /* show error */ }
    }
}
```

### 9.2 Render state

```kotlin
@Composable
fun CustomChat() {
    val messages by Conferbot.record.collectAsState()
    val isAgentTyping by Conferbot.isAgentTyping.collectAsState()
    val isConnected by Conferbot.isConnected.collectAsState()
    val isOnline by Conferbot.isOnline.collectAsState()
    val isLoadingMore by Conferbot.isLoadingMore.collectAsState()

    Column {
        if (!isOnline) OfflineChip()
        LazyColumn(Modifier.weight(1f)) {
            items(messages) { item -> MyBubble(item) }
            if (isAgentTyping) item { MyTypingDots() }
        }
        MyInput(onSend = { Conferbot.sendMessage(it) })
    }
}
```

`record` is a `StateFlow<List<RecordItem>>`; `RecordItem` is a sealed hierarchy covering bot messages, user messages, user input responses, agent messages, and node payloads - render with a `when` over its subtypes.

### 9.3 Interactions

```kotlin
Conferbot.sendMessage("Hello")                       // visitor message
Conferbot.sendTypingStatus(true)                     // typing on/off
Conferbot.initiateHandover("Escalate to a human")    // request live agent
Conferbot.endChat()                                  // finish the conversation
Conferbot.resetUnreadCount()                         // mark as read
Conferbot.setChatVisible(true)                       // suppress in-app banners while your UI is open
```

### 9.4 Flow nodes (choices, forms, etc.)

Interactive nodes are exposed through the flow engine:

```kotlin
val flowEngine = Conferbot.flowEngine
val uiState by flowEngine!!.currentUIState.collectAsState()   // NodeUIState?
val isProcessing by flowEngine.isProcessing.collectAsState()

// When the user picks a choice / submits a form value:
flowEngine.submitResponse(response)
```

The built-in `PaginatedMessageList` + node components handle all 50+ node types; replicate only what your custom UI needs.

### 9.5 Pagination

```kotlin
// When the user scrolls near the top:
if (Conferbot.hasMoreMessages()) {
    Conferbot.loadMoreMessages()
}
```

Tune with `PaginationConfig(pageSize = 50, maxMemoryMessages = 100, backgroundMemoryLimit = 50, paginationThreshold = 10)` at initialize time.

## 10. Offline mode and session persistence

### Offline queue

Enabled by default (`enableOfflineMode = true`). Messages sent while offline are queued in Room and synced when connectivity returns.

```kotlin
val isOnline by Conferbot.isOnline.collectAsState()
val pending by Conferbot.pendingMessageCount.collectAsState()
val syncing by Conferbot.isSyncingQueue.collectAsState()

// Manual control
Conferbot.processOfflineQueue()
Conferbot.clearOfflineQueue()
Conferbot.clearCurrentSessionQueue()
Conferbot.isOfflineModeEnabled()
Conferbot.isCurrentlyOnline()
Conferbot.hasPendingMessages()
```

### Restoring a previous conversation

Sessions persist across app restarts. Before opening the chat, optionally restore:

```kotlin
lifecycleScope.launch {
    if (Conferbot.hasPersistedSession()) {
        val result = Conferbot.restorePersistedSession()
        if (result.success) {
            // History is back in Conferbot.record, socket room rejoined,
            // analytics resumed. onSessionStarted fires with the restored ID.
        }
    }
    Conferbot.openChat(this@MainActivity)
}
```

To start fresh instead: `Conferbot.clearHistory()`.

## 11. Push notifications with FCM

The SDK depends on `firebase-messaging-ktx`; you supply the Firebase project.

**Step 1 - Firebase setup.** Add `google-services.json` to your app module and apply the `com.google.gms.google-services` plugin.

**Step 2 - Register the token after the session starts.** Tokens are tied to a session, so the example app registers inside `onSessionStarted`:

```kotlin
Conferbot.setEventListener(object : ConferBotEventListener {
    override fun onSessionStarted(sessionId: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) Conferbot.registerPushToken(task.result)
        }
    }
})
```

Token refreshes are re-registered automatically once notifications are initialized.

**Step 3 - Forward payloads** from your `FirebaseMessagingService`:

```kotlin
class MyMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (Conferbot.handlePushNotification(message.data)) return
        // your own notifications
    }

    override fun onNewToken(token: String) {
        Conferbot.registerPushToken(token)
    }
}
```

`handlePushNotification` recognizes Conferbot payloads (`source == "conferbot"` or types like `new_message`, `agent_joined`, `chat_ended`, `handover_queued`) and returns `false` for everything else.

**Step 4 (optional) - customize behavior:**

```kotlin
import com.conferbot.sdk.notifications.NotificationSettings

Conferbot.updateNotificationSettings(
    NotificationSettings(
        enabled = true,
        soundEnabled = true,
        vibrationEnabled = true,
        showPreview = true,
        showInForeground = false,   // rely on in-app banners while foregrounded
        showNewMessages = true,
        showAgentJoined = true,
        showAgentLeft = true,
        showChatEnded = true,
        showQueueUpdates = true
    )
)

// In-app banners need to know the visible Activity:
override fun onResume() { super.onResume(); Conferbot.setCurrentActivity(this) }
override fun onPause() { Conferbot.setCurrentActivity(null); super.onPause() }

// Deep-link taps into your own chat screen instead of the SDK's:
Conferbot.setNotificationChatActivity(MyChatActivity::class.java)

// Observe notifications programmatically:
Conferbot.addNotificationListener(object : NotificationListener {
    override fun onNotificationReceived(notification: ConferbotNotification) { }
    override fun onNotificationTapped(notification: ConferbotNotification) { }
})
```

On logout call `Conferbot.unregisterPushToken()`.

## 12. Knowledge base

If the bot has a knowledge base attached, present the built-in help center:

```kotlin
import com.conferbot.sdk.ui.compose.knowledgebase.KnowledgeBaseScreen

val kbService = Conferbot.initKnowledgeBaseService()
if (kbService != null) {
    Conferbot.fetchKnowledgeBase()
    KnowledgeBaseScreen(
        knowledgeBaseService = kbService,
        onDismiss = { showKb = false },
        primaryColor = Color(0xFF0100EC),
        title = "Help Center"
    )
}
```

The screen includes category tabs, article lists, full-text search, and article detail views. Headless equivalents on `Conferbot`:

```kotlin
Conferbot.searchKnowledgeBase("refund policy")
Conferbot.trackKnowledgeBaseArticleView(article)
Conferbot.startKnowledgeBaseArticleEngagement(articleId)
Conferbot.updateKnowledgeBaseScrollDepth(80)
Conferbot.rateKnowledgeBaseArticle(articleId, helpful = true)
Conferbot.hasRatedKnowledgeBaseArticle(articleId)
```

## 13. Analytics

Session start, node visits, engagement, and drop-off are tracked automatically. Optional enrichment:

```kotlin
// Attribution (call before/around session start)
Conferbot.setUtmParameters(
    utmSource = "push",
    utmMedium = "mobile",
    utmCampaign = "reactivation"
)

// Custom interactions
Conferbot.trackInteraction("linksClicked", mapOf("url" to "https://example.com/pricing"))

// Conversions
Conferbot.trackGoalCompletion("goal-signup", conversionEvent = "signup", conversionValue = 0.0)

// Ratings (the built-in post-chat survey calls this for you)
Conferbot.submitChatRating(csatScore = 5, feedback = "Solved instantly", thumbsUp = true)

// Lifecycle hint for drop-off tracking
Conferbot.onAppBackgrounded()
```

Results appear in the Conferbot dashboard's analytics.

## 14. Testing against a non-production server

All endpoints are HTTPS-only and default to production (`https://wdt.conferbot.com`). To point at a staging deployment, configure **before** `initialize()`:

```kotlin
import com.conferbot.sdk.utils.ConferBotEndpoints

ConferBotEndpoints.setApiBaseUrl("https://staging.example.com/api/v1/mobile/")
ConferBotEndpoints.setSocketUrl("https://staging.example.com")
// ConferBotEndpoints.resetToDefaults() to go back
```

`http://` URLs are rejected with `IllegalArgumentException`.

## 15. Release checklist

- [ ] Real `apiKey` and `botId` (not `test_key` / the demo bot)
- [ ] Bot **published** in the dashboard
- [ ] User identified before first chat open (if your app has accounts)
- [ ] Push: `google-services.json` present, token registered in `onSessionStarted`, `handlePushNotification` wired in your messaging service
- [ ] Logout path calls `unregisterPushToken()` and (optionally) `clearHistory()`
- [ ] Minified build tested; if needed add `-keep class com.conferbot.sdk.** { *; }` and `-keep class io.socket.** { *; }`
- [ ] Verified on API 21 (minSdk) and your target SDK

---

## Where to go next

- [README](../README.md) - feature overview, API reference table, troubleshooting
- [API.md](API.md) - full method and property reference
- [COMPONENTS.md](COMPONENTS.md) - UI component catalog
- [EXAMPLES.md](EXAMPLES.md) - additional integration patterns
- [`example/`](../example/) - runnable sample app (XML + Compose + push)
