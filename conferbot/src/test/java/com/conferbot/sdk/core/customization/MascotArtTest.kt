package com.conferbot.sdk.core.customization

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * The owner's mascot (art3d.ts mascot(), mascotLookOf, mascotScaleOf), as the
 * React Native SDK's core/mascotArt.ts draws it: src/test/resources/
 * mascot-art.json holds, for 367 looks (every character in every arm pose,
 * as a bust and in the owner's colour; every eye and accessory on five
 * characters; junk), the first 16 hex digits of each layer's SHA-256 as RN
 * drew it (scripts/mascot-art-fixture.sh). Every layer here must be the same
 * markup, character for character.
 */
@Suppress("UNCHECKED_CAST")
class MascotArtTest {
    private val fixture: Map<String, Any?> by lazy {
        Gson().fromJson(javaClass.classLoader!!.getResource("mascot-art.json")!!.readText(), Map::class.java) as Map<String, Any?>
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private fun lookOf(m: Map<String, Any?>?): MascotArt.Look? = m?.let {
        MascotArt.Look(
            shape = it["shape"] as String? ?: "blob",
            eyes = it["eyes"] as String? ?: "round",
            accessory = it["accessory"] as String? ?: "none",
            color = it["color"] as String? ?: "",
        )
    }

    /** Well-formed markup: every tag closed in order, attributes quoted, nothing between the tags. */
    private fun wellFormed(xml: String): Boolean {
        val stack = ArrayDeque<String>()
        val re = Regex("<(/?)([a-zA-Z]+)((?:\\s+[a-zA-Z0-9:-]+=\"[^\"<>]*\")*)\\s*(/?)>")
        var at = 0
        for (m in re.findAll(xml)) {
            if (xml.substring(at, m.range.first).isNotBlank()) return false
            at = m.range.last + 1
            if (m.groupValues[1].isNotEmpty()) {
                if (stack.removeLastOrNull() != m.groupValues[2]) return false
            } else if (m.groupValues[4].isEmpty()) stack.addLast(m.groupValues[2])
        }
        return at == xml.length && stack.isEmpty()
    }

    @Test
    fun drawsEveryLookAsTheReactNativeSdkDoes() {
        val cases = fixture["cases"] as List<Map<String, Any?>>
        val wrong = mutableListOf<String>()
        var layers = 0
        for (c in cases) {
            val l = MascotArt.layers(c["accent"] as String, c["arm"] as String, lookOf(c["look"] as Map<String, Any?>?), c["bust"] == true)
            val want = c["layers"] as Map<String, String?>
            for ((name, svg) in l.ordered) {
                val w = want[name]
                if ((svg == null) != (w == null) || (svg != null && hash(svg) != w)) wrong.add("${c["look"]} ${c["arm"]} bust=${c["bust"]}: $name")
                if (svg != null) {
                    layers += 1
                    assertTrue("$name not well formed", wellFormed(svg))
                    assertFalse(name, Regex("style=|class=|filter=|color-mix|undefined|NaN|null").containsMatchIn(svg))
                }
            }
        }
        assertEquals(367, cases.size)
        assertTrue("${wrong.size} layers differ, e.g. ${wrong.take(5)}", wrong.isEmpty())
        assertTrue(layers > 3500)
    }

    @Test
    fun turnsEachPartAboutTheJointsRnHas() {
        val origins = fixture["origins"] as Map<String, Map<String, List<Number>>>
        for (shape in MascotArt.SHAPES) {
            val o = MascotArt.origins(MascotArt.Look(shape = shape))
            for (part in listOf("body", "arm", "lid", "look", "brow")) {
                val want = origins.getValue(shape).getValue(part)
                val got = o[part]!!
                assertEquals("$shape $part x", want[0].toDouble(), got.first, 1e-9)
                assertEquals("$shape $part y", want[1].toDouble(), got.second, 1e-9)
            }
        }
        assertEquals(50.0 to 94.0, MascotArt.ORIGINS.body)
        assertEquals(50.0 to 39.0, MascotArt.ORIGINS.lid)
        // The car's eyes are high and small: eyeY 40, scale 0.62.
        assertEquals(40 - 13 * 0.62, MascotArt.origins(MascotArt.Look(shape = "car")).lid.second, 1e-9)
        // The bust's 92 x 96 viewBox fitted in a square box.
        assertEquals(2.0 to 0.0, MascotArt.point(4.0 to 0.0, 96.0, true))
        assertEquals(50.0 to 94.0, MascotArt.point(50.0 to 94.0, 100.0, false))
        assertEquals(1.0, MascotArt.unit(96.0, true), 1e-12)
    }

    @Test
    fun defaultsToTheClassicBlobAndKeepsOnlyKnownValues() {
        assertEquals(MascotArt.Look(), MascotArt.lookOf(null))
        assertEquals(
            MascotArt.Look(),
            MascotArt.lookOf(mapOf("mascotShape" to "dragon", "mascotEyes" to "laser", "mascotAccessory" to "cape", "mascotColor" to "red", "mascotName" to 42)),
        )
        assertEquals(
            MascotArt.Look("fox", "star", "crown", "#F97316", "Pip the fox, the fri"),
            MascotArt.lookOf(mapOf("mascotShape" to "fox", "mascotEyes" to "star", "mascotAccessory" to "crown", "mascotColor" to " #F97316 ", "mascotName" to "  Pip   the  fox, the friendliest one ")),
        )
    }

    @Test
    fun sizesTheLauncherMascot70To150PercentOfTheButton() {
        assertEquals(112, MascotArt.scaleOf(emptyMap()))
        assertEquals(112, MascotArt.scaleOf(mapOf("mascotLauncherScale" to null)))
        assertEquals(112, MascotArt.scaleOf(mapOf("mascotLauncherScale" to "")))
        assertEquals(112, MascotArt.scaleOf(mapOf("mascotLauncherScale" to "big")))
        assertEquals(70, MascotArt.scaleOf(mapOf("mascotLauncherScale" to 10)))
        assertEquals(150, MascotArt.scaleOf(mapOf("mascotLauncherScale" to 999)))
        assertEquals(120, MascotArt.scaleOf(mapOf("mascotLauncherScale" to "120.4")))
        assertEquals(130, MascotArt.scaleOf(mapOf("mascotLauncherScale" to 130.0)))
    }

    @Test
    fun hasTwentySevenCharactersEachWithItsOwnName() {
        assertEquals(27, MascotArt.SHAPES.size)
        for (s in MascotArt.SHAPES) assertTrue(s, MascotArt.DEFAULT_NAMES[s]!!.isNotEmpty())
    }

    @Test
    fun paintsTheOwnersColourCloudAndGhostPalerAToothPearly() {
        assertEquals("#ff0000", MascotArt.colorOf(MascotArt.Look(shape = "cat", color = "#ff0000"), "#0000ff"))
        assertEquals("#0000ff", MascotArt.colorOf(MascotArt.Look(shape = "cat"), "#0000ff"))
        assertEquals(MascotArt.mixColor("#000000", 45, "#ffffff"), MascotArt.colorOf(MascotArt.Look(shape = "cloud"), "#000000"))
        assertEquals(MascotArt.mixColor("#000000", 22, "#ffffff"), MascotArt.colorOf(MascotArt.Look(shape = "ghost"), "#000000"))
        assertEquals(MascotArt.mixColor("#000000", 12, "#ffffff"), MascotArt.colorOf(MascotArt.Look(shape = "tooth"), "#000000"))
        // The body in the owner's colour, the pupils' rim in the launcher's.
        val l = MascotArt.layers("#000000", "down", MascotArt.Look(color = "#ff0000"))
        assertTrue(l.body.contains("stop-color=\"#ff0000\""))
        assertTrue(l.look.contains(MascotArt.mixColor("#000000", 45, "#0b1020")))
    }

    @Test
    fun keepsTheArmBehindOrInFrontByItsPoseAndTheBustBare() {
        assertTrue(MascotArt.layers("#4f46e5", "knock").armFront!!.contains("<svg"))
        assertNull(MascotArt.layers("#4f46e5", "knock").armBack)
        assertTrue(MascotArt.layers("#4f46e5", "wave").armBack!!.contains("<svg"))
        val cat = MascotArt.layers("#4f46e5", "wave", MascotArt.Look(shape = "cat"))
        assertTrue(cat.tail!!.contains("<path"))
        val bust = MascotArt.layers("#4f46e5", "wave", MascotArt.Look(shape = "cat"), bust = true)
        assertEquals(listOf(null, null, null, null), listOf(bust.shadow, bust.tail, bust.armBack, bust.armFront))
        assertTrue(bust.body.contains("viewBox=\"4 0 92 96\""))
        assertTrue(cat.body.length > bust.body.length)
        // Sleepy lids, shades, glasses, a crown; a robot's LED eyes without whites; an industry prop in its own paints.
        assertTrue(MascotArt.layers("#4f46e5", "down", MascotArt.Look(eyes = "sleepy")).face!!.contains("<path"))
        assertNull(MascotArt.layers("#4f46e5", "down").face)
        assertTrue(MascotArt.layers("#4f46e5", "down", MascotArt.Look(eyes = "shades")).cover!!.contains("#0b1020"))
        assertTrue(MascotArt.layers("#4f46e5", "down", MascotArt.Look(accessory = "glasses")).top!!.contains("stroke=\"#1f2937\""))
        val robot = MascotArt.layers("#4f46e5", "down", MascotArt.Look(shape = "robot"))
        assertFalse(robot.body.contains("url(#me)"))
        assertNull(robot.cover)
        val medic = MascotArt.layers("#4f46e5", "down", MascotArt.Look(shape = "medic")).top!!
        assertTrue(medic.contains("url(#mr)") && medic.contains("id=\"mr\""))
    }
}
