# Conferbot Android SDK - Example App

Demonstrates how to integrate the Conferbot Android SDK using both XML Views and Jetpack Compose.

## Prerequisites

- **Android Studio** Hedgehog (2023.1) or later
- **JDK 17**
- **Android SDK** API 34
- A Conferbot **API Key** and **Bot ID** from your dashboard

## Setup

1. Open the root `conferbot-android/` project in Android Studio (not just the `example/` folder).
2. Wait for Gradle sync to complete.
3. Open `example/src/main/java/com/conferbot/example/ExampleApplication.kt`.
4. Replace the placeholder values:

```kotlin
Conferbot.initialize(
    context = this,
    apiKey = "YOUR_API_KEY",   // Replace with your API key
    botId = "YOUR_BOT_ID",    // Replace with your bot ID
)
```

5. Select the `example` run configuration in the toolbar.
6. Click **Run** (or press Shift+F10).

## What's Inside

The example app includes two activities:

### MainActivity (XML Views)
The default launcher activity. Demonstrates:
- Opening the chat via `Conferbot.openChat(context)`
- Observing SDK state (connection status, unread count, current agent)
- Sending messages programmatically
- Requesting live agent handover
- Clearing chat history

### ComposeActivity (Jetpack Compose)
Demonstrates the Compose integration:
- `ConferBotChatScreen` as a full-screen overlay
- Real-time state observation via `collectAsState()`
- Action buttons for all SDK operations

Switch between them from the app's UI.

## Push Notifications (Optional)

To enable FCM push notifications:
1. Add your `google-services.json` to `example/`.
2. Uncomment `id 'com.google.gms.google-services'` in `example/build.gradle`.
3. Rebuild the project.

Without this, the app works normally but won't receive push notifications for agent replies.

## Project Structure

```
example/
  src/main/
    java/com/conferbot/example/
      ExampleApplication.kt        -- SDK initialization
      ui/xml/MainActivity.kt       -- XML Views demo
      ui/compose/ComposeActivity.kt -- Jetpack Compose demo
    res/
      layout/activity_main.xml     -- XML layout
      values/                      -- Strings, themes, colors
    AndroidManifest.xml
  build.gradle
```
