package com.conferbot.sdk.core.customization

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherMotionTest {

    @Test
    fun pacesIdleMomentsLikeTheWeb() {
        assertEquals(LauncherMotion.FIRST_IDLE_MS, LauncherMotion.nextIdleDelay("subtle", 0))
        assertEquals(30000L, LauncherMotion.nextIdleDelay("subtle", 1))
        assertNull(LauncherMotion.nextIdleDelay("subtle", 4))
        assertEquals(24000L, LauncherMotion.nextIdleDelay("lively", 3) { 0.5 })
        assertNull(LauncherMotion.nextIdleDelay("lively", 10))
        assertNull(LauncherMotion.nextIdleDelay("off", 0))
    }

    @Test
    fun solvesCssCubicBezier() {
        assertEquals(0.5, LauncherMotion.cubicBezier(listOf(0.42, 0.0, 0.58, 1.0), 0.5), 1e-3)
        assertEquals(0.3, LauncherMotion.cubicBezier(listOf(0.0, 0.0, 1.0, 1.0), 0.3), 1e-3)
        assertTrue(LauncherMotion.cubicBezier(listOf(0.2, 1.4, 0.4, 1.0), 0.5) > 1)
    }

    @Test
    fun ripplesTwoRingsThenStops() {
        assertNull(LauncherMotion.rippleRing(2.0, 0))
        val a = LauncherMotion.rippleRing(3.4, 0)
        assertNotNull(a)
        assertTrue(a!!.scale > 1 && a.opacity < 0.45)
        assertNull(LauncherMotion.rippleRing(2.7, 1))
        assertNull(LauncherMotion.rippleRing(2.5 + 1.8 * 3 + 0.1, 0))
    }

    @Test
    fun restsAfterTheMoment() {
        assertTrue(LauncherMotion.iconFrame(LauncherIcons.byId("WidgetBubbleIcon40"), "idle", 9.0).all { it == null })
    }

    /** The same frames as the RN SDK's evaluator, which is checked against Chrome's own animation of the web icons. */
    @Test
    fun matchesTheVerifiedEvaluator() {
        val text = javaClass.classLoader!!.getResource("launcher-motion-golden.json")!!.readText()
        @Suppress("UNCHECKED_CAST")
        val cases = Gson().fromJson(text, List::class.java) as List<Map<String, Any?>>
        for (case in cases) {
            val id = case["id"] as String
            val frame = LauncherMotion.iconFrame(LauncherIcons.byId(id), case["st"] as String, (case["t"] as Number).toDouble())
            @Suppress("UNCHECKED_CAST")
            val ops = case["ops"] as List<Map<String, Any?>?>
            assertEquals(id, ops.size, frame.size)
            ops.forEachIndexed { i, want ->
                val got = frame[i]
                if (want == null) { assertNull("$id op $i", got); return@forEachIndexed }
                assertNotNull("$id op $i", got)
                @Suppress("UNCHECKED_CAST")
                val m = want["m"] as List<Number>
                m.forEachIndexed { k, v -> assertEquals("$id op $i m[$k]", v.toDouble(), got!!.m[k], 1e-3) }
                @Suppress("UNCHECKED_CAST")
                val layers = want["layers"] as List<List<Number>>
                assertEquals("$id op $i layers", layers.size, got!!.layers.size)
                layers.forEachIndexed { k, l ->
                    assertEquals(l[0].toInt(), got.layers[k].first)
                    assertEquals(l[1].toDouble(), got.layers[k].second, 1e-3)
                }
            }
        }
    }
}
