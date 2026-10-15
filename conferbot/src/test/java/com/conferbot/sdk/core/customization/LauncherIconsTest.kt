package com.conferbot.sdk.core.customization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconsTest {

    @Test
    fun hasTheWebWidgetsFiftySixIcons() {
        assertEquals(56, LauncherIcons.all.size)
        val ids = LauncherIcons.all.map { it.id }.toSet()
        for (n in 1..56) assertTrue("WidgetBubbleIcon$n", "WidgetBubbleIcon$n" in ids)
    }

    @Test
    fun fallsBackToTheGlossyBubble() {
        assertEquals("WidgetBubbleIcon40", LauncherIcons.DEFAULT_ICON)
        assertEquals("WidgetBubbleIcon40", LauncherIcons.byId("nope").id)
        assertEquals("WidgetBubbleIcon40", LauncherIcons.byId(null).id)
        assertEquals("WidgetBubbleIcon7", LauncherIcons.byId("WidgetBubbleIcon7").id)
    }

    @Test
    fun parsesPaintsGradientsAndHiddenParts() {
        val glossy = LauncherIcons.byId("WidgetBubbleIcon40")
        assertEquals(3, glossy.grads.size)
        assertEquals("ink", glossy.grads[0].stops[0].c)
        assertEquals(0.84, glossy.grads[0].stops[1].a, 1e-9)
        assertTrue(LauncherIcons.all.any { icon -> icon.ops.any { it.op == 0.0 } })
        for (icon in LauncherIcons.all) for (o in icon.ops) {
            assertTrue(o.d.length > 3)
            listOfNotNull(o.fill, o.stroke).forEach { p -> p.g?.let { assertNotNull(icon.grads.getOrNull(it)) } }
        }
    }

    @Test
    fun drawsTheIconAtSeventyPercent() {
        assertEquals(35f, Launcher.resolve(mapOf("widgetSize" to 50)).glyph)
        assertEquals(25f, Launcher.resolve(mapOf("widgetSize" to 50, "launcherShape" to "tab")).glyph)
    }
}
