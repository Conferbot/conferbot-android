# Conferbot Android SDK: example app

A small app that shows the SDK in an XML Views screen and a Jetpack Compose screen. It builds against the SDK in this repository (`implementation project(':conferbot')`), so it always runs the code on your checkout.

## Prerequisites

- Android Studio Hedgehog (2023.1) or later, or the command line with JDK 17
- Android SDK platform 34
- An emulator or a phone with Android 5.0 (API 21) or later

## Run it against Conferbot's cloud

This is the default: the app talks to `https://wdt.conferbot.com`.

1. Open the root `conferbot-android/` folder in Android Studio (not `example/` on its own) and let Gradle sync.
2. The app starts with the public demo bot, which needs no account. To use your own bot, open `example/src/main/java/com/conferbot/example/ExampleApplication.kt` and change the bot ID (from the dashboard's **Share** tab, **Mobile apps**; publish the bot first):

   ```kotlin
   Conferbot.initialize(
       context = this,
       apiKey = "test_key",                  // any non-empty value
       botId = "691c970890527a0468f9b2c9",   // the demo bot; put your bot ID here
       ...
   )
   ```

3. Pick the `example` run configuration and press **Run** (Shift+F10).

From the command line:

```bash
sh ./gradlew :example:installDebug
adb shell am start -n com.conferbot.example/.ui.xml.MainActivity
```

## Run it against a local server

For Conferbot developers running the embed-server on their machine.

1. Start the embed-server on port 8001 (in the `embed-server` repository, with its `.env` set up):

   ```bash
   PORT=8001 npm run dev
   ```

2. Point the app at it. The Android emulator reaches your computer's `localhost` at `10.0.2.2`. Add `baseUrl` and `socketUrl` to the `initialize()` call in `ExampleApplication.kt`:

   ```kotlin
   Conferbot.initialize(
       context = this,
       apiKey = "test_key",
       botId = "YOUR_LOCAL_BOT_ID",
       baseUrl = "http://10.0.2.2:8001/api/v1/mobile/",
       socketUrl = "http://10.0.2.2:8001",
       ...
   )
   ```

   - Pass them to `initialize()`, not `ConferBotEndpoints.setApiBaseUrl()` / `setSocketUrl()`, which accept only `https://` URLs.
   - Plain `http` works because the example's manifest has `android:usesCleartextTraffic="true"`. Your own app would need the same (or a network security config) for a local server; never ship it.
   - If the emulator cannot reach `10.0.2.2`, forward the port and use `http://127.0.0.1:8001` instead:

     ```bash
     adb reverse tcp:8001 tcp:8001
     ```

   - On a phone, use your computer's address on the same Wi-Fi (`http://192.168.x.y:8001`), or `adb reverse` over USB as above.

3. Use a bot that exists in your local database, and publish it.
4. Run the app. Force-stop it before launching again (`adb shell am force-stop com.conferbot.example`), or it resumes the chat that was open.

Revert the URL changes before committing.

## What's inside

| Screen | What it shows |
|---|---|
| `MainActivity` (XML Views, the launcher activity) | **Open Chat (XML Views)** (`Conferbot.openChat`), **Open Chat (Compose)**, **Identify Test User** (`identify`), **Send Test Message**, **Request Agent** (`initiateHandover`), **Clear History**; the connection, agent and message count from the SDK's `StateFlow`s |
| `ComposeActivity` | `ConferBotChatScreen` over a Compose screen, the state with `collectAsState()`, the same actions |
| `ExampleApplication` | `initialize()`, an event listener, a notification listener and `NotificationSettings`, and the FCM token |

"Identify Test User" signs the visitor in, so the launcher's sales greeting no longer shows for them.

## Push notifications (optional)

The example depends on `firebase-messaging-ktx` and asks Firebase for a token once a conversation starts. To try push:

1. Add your Firebase project's `google-services.json` to `example/`.
2. Uncomment `id 'com.google.gms.google-services'` in `example/build.gradle`.
3. Declare the SDK's service in `example/src/main/AndroidManifest.xml` (see the main [README](../README.md#push-notifications)).

Without them the app works as before; it simply gets no pushes. The server sends pushes only when push is enabled on it (see the main README).

## Project structure

```
example/
  src/main/
    java/com/conferbot/example/
      ExampleApplication.kt          SDK initialization
      ui/xml/MainActivity.kt         XML Views demo
      ui/compose/ComposeActivity.kt  Jetpack Compose demo
      ui/compose/theme/Theme.kt
    res/layout/activity_main.xml
    AndroidManifest.xml
  build.gradle
```

More on testing on a device, and the unit tests, in [docs/PARITY.md](../docs/PARITY.md#how-to-test).
