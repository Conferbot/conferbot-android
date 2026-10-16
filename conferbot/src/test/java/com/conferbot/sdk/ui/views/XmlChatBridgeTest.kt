package com.conferbot.sdk.ui.views

import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.models.ConferBotCustomization
import com.conferbot.sdk.ui.theme.DarkTheme
import com.conferbot.sdk.ui.theme.LightTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The XML ChatActivity on the Compose chat ([XmlChatBridge]): the app's
 * ConferBotCustomization as the Compose theme until the bot's settings
 * arrive, the header title, and what a launch intent asks for.
 */
class XmlChatBridgeTest {

    @Test
    fun `no customization keeps the SDK's theme`() {
        assertThat(XmlChatBridge.theme(null, LightTheme)).isSameInstanceAs(LightTheme)
        assertThat(XmlChatBridge.theme(ConferBotCustomization(), LightTheme)).isEqualTo(LightTheme)
        assertThat(XmlChatBridge.theme(ConferBotCustomization(headerTitle = "Help"), DarkTheme)).isEqualTo(DarkTheme)
    }

    @Test
    fun `the primary colour is the header, buttons and links, with ink that reads on it`() {
        val red = 0xFFFF6B6B.toInt()
        val t = XmlChatBridge.theme(ConferBotCustomization(primaryColor = red), LightTheme)
        val c = Color(red)
        assertThat(t.colors.primary).isEqualTo(c)
        assertThat(t.colors.headerBackground).isEqualTo(c)
        assertThat(t.colors.buttonBackground).isEqualTo(c)
        assertThat(t.colors.link).isEqualTo(c)
        assertThat(t.colors.inputBorderFocused).isEqualTo(c)
        assertThat(t.colors.headerText).isEqualTo(Color.White)
        assertThat(t.colors.onPrimary).isEqualTo(Color.White)
        // Untouched: the rest of the theme.
        assertThat(t.colors.botBubble).isEqualTo(LightTheme.colors.botBubble)
        assertThat(t.colors.background).isEqualTo(LightTheme.colors.background)
        assertThat(t.shapes).isEqualTo(LightTheme.shapes)
        // A light primary gets black ink.
        val yellow = XmlChatBridge.theme(ConferBotCustomization(primaryColor = 0xFFFFEB3B.toInt()), LightTheme)
        assertThat(yellow.colors.headerText).isEqualTo(Color.Black)
    }

    @Test
    fun `bubble colours, radius and font`() {
        val t = XmlChatBridge.theme(
            ConferBotCustomization(
                botBubbleColor = 0xFF0100EC.toInt(),
                userBubbleColor = 0xFFEDEDED.toInt(),
                bubbleRadius = 6f,
                fontFamily = 42
            ),
            LightTheme
        )
        assertThat(t.colors.botBubble).isEqualTo(Color(0xFF0100EC.toInt()))
        assertThat(t.colors.botBubbleText).isEqualTo(Color.White)
        assertThat(t.colors.userBubble).isEqualTo(Color(0xFFEDEDED.toInt()))
        assertThat(t.colors.userBubbleText).isEqualTo(Color.Black)
        assertThat(t.shapes.bubbleRadius).isEqualTo(6.dp)
        assertThat(t.typography.fontFamily).isNotEqualTo(FontFamily.Default)
        assertThat(t.typography.secondaryFontFamily).isEqualTo(t.typography.fontFamily)
        assertThat(t.colors.primary).isEqualTo(LightTheme.colors.primary)
        // No font resource (0) and a negative radius keep the theme's.
        val keep = XmlChatBridge.theme(ConferBotCustomization(bubbleRadius = -1f, fontFamily = 0), LightTheme)
        assertThat(keep.shapes).isEqualTo(LightTheme.shapes)
        assertThat(keep.typography).isEqualTo(LightTheme.typography)
    }

    @Test
    fun `the header title until the bot's settings arrive`() {
        assertThat(XmlChatBridge.headerTitle(null)).isNull()
        assertThat(XmlChatBridge.headerTitle(ConferBotCustomization(headerTitle = "  "))).isNull()
        assertThat(XmlChatBridge.headerTitle(ConferBotCustomization(headerTitle = "Customer Support"))).isEqualTo("Customer Support")
    }

    @Test
    fun `what a launch intent asks for`() {
        assertThat(XmlChatBridge.launchOf("com.conferbot.ACTION_OPEN_CHAT")).isEqualTo(XmlChatBridge.Launch.NOTIFICATION)
        assertThat(XmlChatBridge.launchOf(Intent.ACTION_VIEW)).isEqualTo(XmlChatBridge.Launch.DEEP_LINK)
        assertThat(XmlChatBridge.launchOf(null)).isEqualTo(XmlChatBridge.Launch.OPEN)
        assertThat(XmlChatBridge.launchOf(Intent.ACTION_MAIN)).isEqualTo(XmlChatBridge.Launch.OPEN)
    }
}
