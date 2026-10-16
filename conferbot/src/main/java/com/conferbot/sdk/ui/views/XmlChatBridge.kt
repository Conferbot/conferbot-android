package com.conferbot.sdk.ui.views

import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.models.ConferBotCustomization
import com.conferbot.sdk.notifications.ConferbotNotificationManager
import com.conferbot.sdk.ui.theme.ConferbotTheme

/**
 * What the XML [ChatActivity] keeps of its own API now that it hosts the
 * Compose chat (`ConferBotChatScreen`): the app's [ConferBotCustomization]
 * as the Compose theme until the bot's own settings arrive (they win then,
 * as they won over the XML look), and what a launch intent asks for.
 */
internal object XmlChatBridge {

    /** Black or white, whichever reads on [c] (as the XML chat's XmlLook.inkOn). */
    fun inkOn(c: Color): Color {
        val lum = 0.299 * c.red + 0.587 * c.green + 0.114 * c.blue
        return if (lum > 0.6) Color.Black else Color.White
    }

    /**
     * [base] with the app's colours, bubble radius and font. Fields left
     * null keep [base]'s; a null customization is [base] itself.
     */
    fun theme(c: ConferBotCustomization?, base: ConferbotTheme): ConferbotTheme {
        if (c == null) return base
        var colors = base.colors
        c.primaryColor?.let {
            val p = Color(it)
            val ink = inkOn(p)
            colors = colors.copy(
                primary = p, onPrimary = ink,
                headerBackground = p, headerText = ink, headerIcon = ink,
                buttonBackground = p, buttonText = ink,
                link = p, inputBorderFocused = p, sending = p
            )
        }
        c.botBubbleColor?.let { colors = colors.copy(botBubble = Color(it), botBubbleText = inkOn(Color(it))) }
        c.userBubbleColor?.let { colors = colors.copy(userBubble = Color(it), userBubbleText = inkOn(Color(it))) }
        val shapes = c.bubbleRadius?.takeIf { it >= 0f }?.let { base.shapes.copy(bubbleRadius = it.dp) } ?: base.shapes
        val typography = c.fontFamily?.takeIf { it != 0 }?.let {
            val family = FontFamily(Font(it))
            base.typography.copy(fontFamily = family, secondaryFontFamily = family)
        } ?: base.typography
        return base.copy(colors = colors, shapes = shapes, typography = typography)
    }

    /** The header's title until the bot's settings arrive: the app's, else null (the SDK's "Support Chat"). */
    fun headerTitle(c: ConferBotCustomization?): String? = c?.headerTitle?.takeIf { it.isNotBlank() }

    /** What a launch intent asks of the chat. */
    enum class Launch {
        /** A Conferbot notification was tapped: its notifications go, the tap is handled. */
        NOTIFICATION,
        /** conferbot://chat (an external link). */
        DEEP_LINK,
        /** Conferbot.openChat() or the app's own intent. */
        OPEN
    }

    fun launchOf(action: String?): Launch = when (action) {
        ConferbotNotificationManager.ACTION_OPEN_CHAT -> Launch.NOTIFICATION
        Intent.ACTION_VIEW -> Launch.DEEP_LINK
        else -> Launch.OPEN
    }
}
