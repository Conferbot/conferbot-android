package com.conferbot.sdk.ui.views

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.remember
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.notifications.ConferbotNotificationManager
import com.conferbot.sdk.ui.compose.ConferBotChatScreen
import com.conferbot.sdk.ui.theme.ConferbotThemeProvider
import com.conferbot.sdk.ui.theme.DarkTheme
import com.conferbot.sdk.ui.theme.LightTheme

/**
 * The full-screen chat for XML Views apps, launched by
 * [Conferbot.openChat], a notification tap (`com.conferbot.ACTION_OPEN_CHAT`)
 * or the `conferbot://chat` deep link.
 *
 * It hosts the same Compose chat as the Compose UI ([ConferBotChatScreen]),
 * so an XML app gets every feature from one implementation: the home
 * screen, the help centre, "Your requests", the wait cards and games, the
 * step cards, the typing styles and mascots, the chat menu (Sound on / off,
 * Email me this chat), the human touches, the owner's font and the post-chat
 * redirect. The app's [com.conferbot.sdk.models.ConferBotCustomization]
 * (primary colour, bubble colours and radius, font, header title) themes it
 * until the bot's own settings arrive, as before. The Compose UI library
 * comes with the SDK: the host app needs no Compose setup of its own.
 */
class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The chat opened: the launcher sound, or the pack's open (core/sound).
        if (savedInstanceState == null) Conferbot.sounds.play(com.conferbot.sdk.core.sound.MessageSound.Event.OPEN)
        // A theme set to "auto" follows the phone's light / dark setting.
        Conferbot.setSystemDarkMode(
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        )

        setContent {
            // The app's customization until the bot's settings arrive; the
            // chat draws in the bot's theme from then on.
            val customization = Conferbot.getCustomization()
            val light = remember(customization) { XmlChatBridge.theme(customization, LightTheme) }
            val dark = remember(customization) { XmlChatBridge.theme(customization, DarkTheme) }
            ConferbotThemeProvider(lightTheme = light, darkTheme = dark) {
                ConferBotChatScreen(onDismiss = { finish() })
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    /**
     * Deep link intents: a notification tap (its notifications go, the tap
     * is handled) or `conferbot://chat`.
     */
    private fun handleIntent(intent: Intent) {
        when (XmlChatBridge.launchOf(intent.action)) {
            XmlChatBridge.Launch.NOTIFICATION -> {
                Log.d(TAG, "Opened from notification with session: ${intent.getStringExtra(ConferbotNotificationManager.EXTRA_CHAT_SESSION_ID)}")
                Conferbot.cancelAllNotifications()
                @Suppress("UNCHECKED_CAST", "DEPRECATION")
                (intent.getSerializableExtra(ConferbotNotificationManager.EXTRA_NOTIFICATION_DATA) as? HashMap<String, String>)?.let {
                    Conferbot.handleNotificationTap(it)
                }
            }
            XmlChatBridge.Launch.DEEP_LINK -> Log.d(TAG, "Deep link received: ${intent.data}")
            XmlChatBridge.Launch.OPEN -> {}
        }
    }

    override fun onResume() {
        super.onResume()
        // Register this activity for in-app notification banners
        Conferbot.setCurrentActivity(this)
        // On screen: the waiting signal, receipts and the redirect count the chat as open.
        Conferbot.setChatVisible(true)
        Conferbot.resetUnreadCount()
    }

    override fun onPause() {
        super.onPause()
        Conferbot.setCurrentActivity(null)
        Conferbot.setChatVisible(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            Conferbot.sounds.play(com.conferbot.sdk.core.sound.MessageSound.Event.CLOSE)
            // A close blocks auto-open for 2 minutes (core/customization/AutoOpen.kt).
            com.conferbot.sdk.core.customization.AutoOpen.markClosed(Conferbot.activeBotId ?: "bot")
        }
        Conferbot.sendTypingStatus(false)
    }
}
