package com.conferbot.sdk.core.customization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherTest {

    @Test
    fun isASpeechBubbleSixteenFromTheCornerByDefault() {
        val l = Launcher.resolve(emptyMap())
        assertEquals("bubble", l.shape)
        assertEquals("right", l.position)
        assertEquals(50f, l.size)
        assertEquals(16f, l.bottom)
        assertEquals(16f, l.side)
        assertEquals(Corners(25f, 25f, 6f, 25f), l.corners)
        assertEquals(6f, Launcher.resolve(mapOf("widgetPosition" to "left")).corners.bottomLeft)
    }

    @Test
    fun keepsAnOwnerRoundedButtonRound() {
        assertEquals("circle", Launcher.normalizeShape(null, 12))
        assertEquals("bubble", Launcher.normalizeShape(null, 50))
        assertEquals("peek", Launcher.normalizeShape("peek", 12))
        assertEquals(12f, Launcher.resolve(mapOf("widgetBorderRadius" to 12, "widgetSize" to 60)).corners.topLeft)
    }

    @Test
    fun usesThePhoneSizeAndOffsetsClamped() {
        val l = Launcher.resolve(mapOf("widgetSize" to 70, "mobileWidgetSize" to 200, "mobileOffsetBottom" to 90, "mobileOffsetSide" to -5))
        assertEquals(80f, l.size)
        assertEquals(90f, l.bottom)
        assertEquals(0f, l.side)
        assertEquals(64f, Launcher.resolve(mapOf("widgetSize" to 64)).size)
    }

    @Test
    fun drawsSquircleTabAndPeek() {
        assertEquals(15f, Launcher.resolve(mapOf("launcherShape" to "squircle", "widgetSize" to 50)).corners.topLeft)
        val tab = Launcher.resolve(mapOf("launcherShape" to "tab", "widgetSize" to 50))
        assertEquals(41f, tab.width)
        assertEquals(0f, tab.side)
        assertEquals(Corners(15f, 0f, 0f, 15f), tab.corners)
        assertEquals(17f, Launcher.resolve(mapOf("launcherShape" to "peek", "widgetSize" to 50)).tucked)
    }

    @Test
    fun readsColoursWithDarkInkOnLightOnes() {
        assertEquals("#0F766E", Launcher.resolve(mapOf("widgetIconBgColor" to "#0F766E")).solid)
        assertEquals("#ffffff", Launcher.resolve(mapOf("widgetIconBgColor" to "#0F766E")).ink)
        assertEquals("#111827", Launcher.resolve(mapOf("widgetIconBgColor" to "#fef3c7")).ink)
        val g = Launcher.resolve(mapOf("widgetIconThemeType" to "Gradient", "widgetGradientBgOne" to "#7c3aed", "widgetGradientBgTwo" to "#0d9488"))
        assertEquals("#7c3aed" to "#0d9488", g.gradient)
        assertEquals("#7c3aed", g.solid)
        assertEquals("#123456", Launcher.resolve(mapOf("headerBgColor" to "#123456")).solid)
    }

    @Test
    fun fallsBackToTheIconForImageMissingAvatarAndLabelOnPhones() {
        assertEquals("avatar", Launcher.resolve(mapOf("launcherStyle" to "avatar", "avatar" to "https://x/a.png")).style)
        assertEquals("icon", Launcher.resolve(mapOf("launcherStyle" to "avatar")).style)
        assertEquals("icon", Launcher.resolve(mapOf("launcherStyle" to "avatar", "avatar" to "a", "widgetIconType" to "Image", "widgetIconImage" to "https://x/i.png")).style)
        assertEquals("icon", Launcher.resolve(mapOf("launcherStyle" to "label"), 400f).style)
        assertEquals("label", Launcher.resolve(mapOf("launcherStyle" to "label"), 800f).style)
    }

    @Test
    fun capsTheLabelAndShowsTheGreetingOnlyWhenOn() {
        assertEquals("Talk to the team about y", Launcher.normalizeLabel("  Talk   to the   team about your order today  "))
        assertEquals("Chat with us", Launcher.resolve(emptyMap()).label)
        assertNull(Launcher.resolve(mapOf("chatIconCtaText" to "Hi there")).ctaText)
        assertEquals("Hi there", Launcher.resolve(mapOf("chatIconCta" to true, "chatIconCtaText" to "Hi there")).ctaText)
    }

    @Test
    fun readsTheMotionSubtleByDefault() {
        assertEquals("subtle", Launcher.resolve(emptyMap()).animation)
        assertEquals("off", Launcher.resolve(mapOf("launcherAnimation" to "off")).animation)
        assertEquals("subtle", Launcher.resolve(mapOf("launcherAnimation" to "wild")).animation)
    }
}
