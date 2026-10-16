# Changelog

All notable changes to the Conferbot Android SDK are documented in this file.
This is the only changelog for the SDK (the old copy in `docs/` was merged in here).

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.0] - 2026-10-16

Everything since 1.0.0: the SDK now behaves like the Conferbot web widget, from the
launcher to the AI agent, the handover and the help centre. `docs/PARITY.md` tracks each
feature with its issue and its files.

### Breaking changes & migration

1. **Firebase is optional, and the SDK no longer brings it.**
   `firebase-messaging-ktx` is `compileOnly` now, and the SDK's manifest declares no
   `FirebaseMessagingService` and no `com.google.firebase.messaging.*` meta-data
   (default channel, icon, colour). Push stays off until your app hands the SDK a token.
   To keep push notifications:
   - Add Firebase to the app yourself (`google-services.json`, the
     `com.google.gms.google-services` plugin,
     `implementation 'com.google.firebase:firebase-messaging-ktx'`).
   - Declare the SDK's service in your manifest if Conferbot is your only FCM sender:
     ```xml
     <service
         android:name="com.conferbot.sdk.notifications.ConferbotNotificationService"
         android:exported="false">
         <intent-filter>
             <action android:name="com.google.firebase.MESSAGING_EVENT" />
         </intent-filter>
     </service>
     ```
     Or, from your own `FirebaseMessagingService`, call
     `Conferbot.setPushToken(token, PushTokenType.FCM)` in `onNewToken` and
     `Conferbot.handlePushNotification(message.data, open = false)` in `onMessageReceived`.
   - Pass the token: `Conferbot.setPushToken(token, PushTokenType.FCM)`, and call
     `Conferbot.handlePushIntent(intent)` from your launcher activity's `onCreate` and
     `onNewIntent`.
   - Set `com.google.firebase.messaging.default_notification_icon` (and the channel /
     colour, if you relied on the SDK's) in your own manifest.
   - The SDK's consumer rules now have `-dontwarn com.google.firebase.**`, so R8 does not
     fail in an app without Firebase.
2. **The push token is registered over HTTP, not the socket.** The
   `register-push-token` / `unregister-push-token` socket emits are gone. The token goes
   to `POST /api/v1/mobile/push/register` with the conversation (`chatSessionId`) and
   `tokenType`, again for each new conversation, and is unregistered on sign-out,
   `clearHistory()` or `unregisterPush()`. `ApiClient.registerPushToken(token, chatSessionId)`
   was replaced by `registerPush(body)` / `unregisterPush(token)`. On the server, sending
   is off until the embed-server has `MOBILE_PUSH_ENABLED=true` and FCM credentials.
3. **`ChatActivity` hosts the Compose chat.** `Conferbot.openChat(context)`, the class
   name, its manifest entry, `com.conferbot.ACTION_OPEN_CHAT` and the `conferbot://chat`
   deep link are unchanged, but the Activity no longer inflates `activity_chat.xml`,
   `view_chat_header.xml`, `view_typing_indicator.xml` or `view_connection_status.xml`.
   - An app that overrode those layouts, or their ids, colours or drawables, to restyle
     the chat no longer sees its overrides. Use `ConferBotCustomization` (`primaryColor`,
     `botBubbleColor`, `userBubbleColor`, `bubbleRadius`, `fontFamily`, `headerTitle`
     apply until the bot's settings arrive), the dashboard's settings, or embed
     `ConferBotChatView` / `ConferBotThemedChatScreen` in your own Activity.
   - `MessageAdapter` and `NodeInteractionListener` are still public for an app that
     draws its own `RecyclerView` chat, but the built-in chat no longer uses them.
   - The Activity follows the phone's dark mode until the bot's theme arrives. Back from
     the conversation returns to the home screen when the owner has one.
   - Your app needs no Compose setup of its own: the SDK brings the Compose libraries.
4. **`Conferbot.identify(user: ConferBotUser?)`** takes a nullable user. `identify(null)`
   signs the user out: their "Your requests" list, request screens and draft reply go,
   an answer still on its way for them is ignored, and the device's push token is
   unregistered from the conversation. Existing `identify(user)` calls compile unchanged.
5. **Deprecated push calls.** `registerPushToken(token)` is now `setPushToken(token)`
   and `unregisterPushToken()` is now `unregisterPush()`. The old names still work, and
   are marked `@Deprecated` with a `ReplaceWith`.
6. **`handlePushNotification(data, open = true)`** gained `open`, and returns `true` only
   for a Conferbot push of this bot (data `source: "conferbot"`, its `botId`), so other
   libraries' messages are left alone. Kotlin and Java (`@JvmOverloads`) calls with one
   argument compile unchanged, but code compiled against 1.0.0 must be rebuilt.
7. **Flow engine types** (only for apps that run `NodeFlowEngine` or write node handlers):
   - `NodeResult.ExecuteIntegration` is gone (integrations run on Conferbot's server).
     `NodeResult.Invalid(message, uiState, inCard)` (a refused answer; the step waits) and
     `NodeResult.End` are new, and `Proceed` / `DelayedProceed` have a `fallbackPort`.
     An exhaustive `when` over `NodeResult` needs the new cases.
   - The primary `NodeFlowEngine` constructor is internal; the public
     `NodeFlowEngine(socketClient, ...)` constructor stays.
   - `ChatState.resolveVariables(text, html = false)` fills `{x}`, `${x}`, `{{x}}`,
     `${ANSWERS.x}` and `$${x}` as the web does. An unknown, null or empty variable now
     shows nothing (1.0.0 left the placeholder in the text).
   - `ConferBotConfig.aiSettings` is no longer read: GPT, AI and knowledge-base steps ask
     Conferbot's server, which holds the owner's AI key. The app never needs one.
8. **`KnowledgeBaseArticle.content` is `String?`** (null until the article's body is
   fetched when it opens; `hasBody` says whether it is there). `fetchKnowledgeBase()` is
   kept but does nothing: the help centre comes with the bot's data.
9. **English wording.** The SDK's own words come from the web widget's table, so some
   English texts changed, for example "Help Center" is "Help centre" (also the default
   `title` of `KnowledgeBaseScreen`), "Search for articles..." is "Search articles..." and
   "Related Articles" is "Related articles". UI tests that match the old English texts
   need updating. What the owner wrote in the dashboard is shown as written.

### Added

- **Dashboard customisation, as the web widget draws it:** the chat background (solid,
  gradients, image with mask, 13 patterns), all 27 widget themes with light / dark / auto
  and the theme palette, the header (logo, hide logo, online dot, reply time), bubble and
  option corners, the avatar and the footer, in Compose and the XML chat (#12-#16).
- **The floating launcher (`ConferBotWidget`):** the launcher shapes and styles, all 56
  icons, its motion, entrance effects (12), attention modes (8) and idle call (5), the
  27 mascots with their moods, and the greeting card with its rules, frequency and 11
  styles (#18-#20, #23, #50, #73, #81).
- **Flow engine at parity:** the shared flow-node spec's 156 cases pass. Every step the
  dashboard builds runs, with step cards in the chat for consent, OTP, address,
  signature, matrix, ranking, slider, date / time, calendar, payment, carousel, product
  card and navigate; loops and sub-flows; card numbers, ids and passwords masked in
  answers (`redactSensitive`); `Conferbot.answerStep(FlowStepAnswer)` for custom UIs
  (#29-#34, #48, #54, #64-#67).
- **Handover lifecycle:** "Connecting you with the team" and its four outcomes, the 20 s
  waiting signal (`Conferbot.onWidgetSignal`, `HandoverSignals`), `leaveQueue`, back in
  the queue, transfers, a restored live chat, the max-wait countdown and
  `handover-timeout`, the post-chat survey questions, `startNewChat()`, a blocked
  visitor's closed chat (#24-#28, #37, #38, #43, #44, #59-#62, #72).
- **While you wait:** help articles 6 s into a wait, the server's pick with "That solved
  it", and seven games (#40, #75).
- **AI agent:** formatted replies (tables, headings, quotes, code), "Helpful?" with
  reasons and comments (`sendAnswerFeedback`), the 45 s "took too long" notice, the
  inactivity close, cited articles that open in the app, and the phone's language on
  every record (#35, #39, #46, #47, #52, #90).
- **Home screen** (`enableInitialPage`): greeting, "Ask anything", "Talk to us" with the
  reply time, recent articles, customer logos; `Conferbot.showHome()`, `showChat()`,
  `showHelp()`, `homeView`, `HomeScreenView` (#77, #78).
- **"Your requests":** the visitor's conversations with the team, live replies, unread
  badge (`Conferbot.requests`, `requestsUnread`, `openRequests()`) (#41, #79).
- **Help centre:** follows `enableKnowledgeBase` / `hideRecentArticles`, Chat / Articles
  tabs, bodies fetched on open and drawn from HTML without a WebView, search inside
  articles, `openHelpArticle()`, `isHelpCentreOn` (#36, #45, #53, #57).
- **"Email me this chat"** in the chat menu and `liveChat.openEmailTranscript()` (#42).
- **Widget languages:** English, Dutch, French, German and Spanish, chosen as the web
  chooses; `ConferBotConfig.widgetLanguage` overrides the owner's choice (#83).
- **The owner's font** from Google Fonts, cached on the device (#49).
- **Sounds and sound packs, human touches** (tapbacks, confetti, "No rush" nudges,
  "Edit" on the latest answer), **auto-open** and the agent's **typing preview** (#51,
  #84, #85, #88).
- **Post-chat redirect** (`redirectUrl`): a 10 s countdown line with Cancel, then the
  system browser; `ConferBotConfig.onPostChatRedirect` and `disablePostChatRedirect`
  (#86). The post-chat rating at the flow's end, 1.5 s after it (#91).
- **Identity verification:** `ConferBotUser.hash` (HMAC-SHA256 made on your server); the
  user travels with every record as the visitor context (#21).
- **Push notifications:** `setPushToken(token, type)`, `unregisterPush()`,
  `handlePushNotification(data, open)`, `handlePushIntent(intent)`, `PushTokenType`,
  `ConferbotPush.parse()` / `isConferbotPush()`; one notification per conversation on
  the `conferbot_messages` channel; a tap opens its conversation (#92).

### Changed

- `ChatActivity` is the Compose chat, so the XML chat has every feature above (#58,
  #74, #76, #80, #82, #87). See the breaking changes.
- GPT, AI and knowledge-base steps call Conferbot's server instead of AI providers from
  the device (#29).
- The flow runs as the web runs it: a step without a branch moves on along its first
  line; a missing branch, a jump to a deleted step or the end of a path ends the chat; a
  step type the SDK does not know shows nothing and carries on (#30, #65).
- Typing dots last as long as on the web, longer for longer messages.
- `OfflineManager`, `PaginatedMessageManager`, `NetworkMonitor` and `RateLimiter` take
  their dependencies internally for tests; their public constructors are unchanged (#89).

### Fixed

- Agent bots open on the agent chat; a restore loads the conversation without replaying
  the flow; live messages, agent events and files show in both chats (#1-#4).
- The chat resizes for the keyboard in Compose and XML (#5).
- Handover: the accept, no-agents and chat-ended events read the current step; the wait
  time is sent as written; fallback messages are plain bot messages and the flow carries
  on (#7, #8, #10, #11).
- Greeting rule dates no longer use `java.time`, which API 21-25 lack (#68).
- Step texts, loop names and logic variables no longer crash on Android's regex engine,
  which refuses a bare `}` (#70).
- A message saved again replaces itself in memory and on disk; a restart draws every line
  once (#62, #63).
- Going back to an earlier question ("Edit") cuts the stored transcript, record and
  answers too (#88).
- Help-centre articles show as formatted HTML, not plain text; an article's body is
  fetched when it opens (#53, #57).
- `RateLimiter.acquire()` no longer spins on a timestamp exactly `perMillis` old (#89).
- `deviceInfo` falls back when the device model is unset (#22).
- After a reconnect the visitor is back in the chat's room, so agent messages sent after
  a network blip arrive; a waiting visitor's queue position is asked again at once (#94).

### Security

- Identity verification: with `ConferBotUser.hash` the server files the chat under the
  verified user; when the workspace requires it, a missing or wrong hash drops the
  claimed name, email and id. The secret never ships in the app (#21).
- Sign-out (`identify(null)`) and switching users drop the last user's requests and
  ignore answers still on their way for them, for shared devices (#79).
- Card numbers, ids and passwords a visitor types are masked in answers and records
  unless the bot's `redactSensitive` is off; the typing preview is never sent for a
  password field (#33, #51).
- The post-chat redirect follows only `http(s)` URLs with a host, at most 2048
  characters and without `user:password@`, and opens them in the system browser, never in
  the chat (#86).
- A push is handled only when it is Conferbot's and for this bot (#92).
- The socket handshake carries the visitor id, so a visitor the workspace blocked is
  refused at connect (#43).

## [1.0.0] - 2026-04-23

### Added
- Initial release of the Conferbot Android SDK
- Native Kotlin implementation with Coroutines and `StateFlow`
- XML Views (`ChatActivity`, `MessageAdapter`) and Jetpack Compose (Material 3) UIs
- Real-time messaging via Socket.IO, REST via Retrofit
- Node flow engine with 50+ node types
- Live agent handover support, typing indicators
- Message reactions and read receipts
- File and image uploads
- Knowledge base integration
- Session persistence (Room)
- Push notification support (FCM)
- User identification and metadata
- Full theming and customization
- Offline message queuing
- Analytics and event tracking
- ProGuard / R8 consumer rules
- Example app with XML and Compose screens

Supported: Android 5.0 (API 21) and higher, Kotlin 1.9+.

[Unreleased]: https://github.com/Conferbot/conferbot-android/compare/1.0.0...HEAD
[1.0.0]: https://github.com/Conferbot/conferbot-android/releases/tag/1.0.0
