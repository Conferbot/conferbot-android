package com.conferbot.sdk.core.customization

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The launcher entrance, attention and idle call against Chrome: the React
 * Native SDK's golden (scripts/parity/launcherfx/golden.json, copied to
 * src/test/resources/launcherfx-golden.json) holds Chrome's own values of
 * every track, paused at each sample. Each sample keeps only the channels
 * away from rest; iterations -1 is infinity. The mascot's transform lists
 * (`matrix` cases: its moods, the typing mascot, the greeting card, the
 * figure's gestures) are compared as Chrome's matrix (ma..mf).
 */
@Suppress("UNCHECKED_CAST")
class LauncherFxGoldenTest {
    private val golden: Map<String, Any?> by lazy {
        Gson().fromJson(javaClass.classLoader!!.getResource("launcherfx-golden.json")!!.readText(), Map::class.java) as Map<String, Any?>
    }
    private val cases get() = golden["cases"] as List<Map<String, Any?>>

    private fun num(m: Map<String, Any?>, k: String) = (m[k] as Number).toDouble()
    private fun doubles(m: Any?): Map<String, Double> = ((m as Map<String, Any?>?) ?: emptyMap()).mapValues { (it.value as Number).toDouble() }

    /** Keyframes and options in one comparable form: options with their defaults filled in. */
    private fun norm(tracks: List<Map<String, Any?>>): List<Map<String, Any?>> = tracks.map { j ->
        val o = j["options"] as Map<String, Any?>
        val out = LinkedHashMap<String, Any?>()
        if (j["part"] != null) out["part"] = j["part"]
        out["keyframes"] = (j["keyframes"] as List<Map<String, Any?>>).map { k -> k.filterValues { it != null } }
        out["options"] = mapOf(
            "duration" to o["duration"],
            "delay" to (o["delay"] ?: 0),
            "iterations" to (o["iterations"] ?: 1),
            "easing" to (o["easing"] ?: "linear"),
            "fill" to (o["fill"] ?: "none"),
        )
        out
    }

    private val number = Regex("-?\\d+(?:\\.\\d+)?(?:e[-+]?\\d+)?")

    /** Deep equality, numbers (also inside strings such as "12.5px 3px") within a hair: the JVM's sin / cos can differ from V8's in the last bit. */
    private fun same(a: Any?, b: Any?): Boolean {
        if (a is Number && b is Number) return abs(a.toDouble() - b.toDouble()) <= 1e-9 * max(1.0, abs(a.toDouble()))
        if (a is String && b is String) {
            if (a.replace(number, "#") != b.replace(number, "#")) return false
            val x = number.findAll(a).map { it.value.toDouble() }.toList()
            val y = number.findAll(b).map { it.value.toDouble() }.toList()
            return same(x, y)
        }
        if (a is List<*> && b is List<*>) return a.size == b.size && a.indices.all { same(a[it], b[it]) }
        if (a is Map<*, *> && b is Map<*, *>) return a.size == b.size && a.keys.all { b.containsKey(it) && same(a[it], b[it]) }
        return a == b
    }

    @Test
    fun evaluatesEveryGoldenCaseWithinAHairOfChrome() {
        val worst = HashMap<String, Double>()
        var samples = 0
        val m = listOf("ma", "mb", "mc", "md", "me", "mf")
        val identity = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        for (c in cases) {
            val tracks = (c["tracks"] as List<Map<String, Any?>>).map { Track.fromJson(it) }
            val base = doubles(c["base"])
            val box = c["box"] as Map<String, Any?>
            for (s in c["samples"] as List<Map<String, Any?>>) {
                val v = s["v"] as Map<String, Any?>
                val want = FxValues.rest(doubles(v).filterKeys { it in Keyframes.CHANNELS })
                val t = num(s, "t")
                val got = Keyframes.evaluate(tracks, t, base)
                if (c["matrix"] == true) {
                    // A transform list: its matrix, in the order the list draws it.
                    val mx = Keyframes.transformMatrix(got, Keyframes.transformOrder(tracks[0]))
                    val wv = doubles(v)
                    m.forEachIndexed { i, ch -> worst["matrix"] = max(worst["matrix"] ?: 0.0, abs(mx[i] - (wv[ch] ?: identity[i]))) }
                    for (ch in listOf("o", "dash")) worst[ch] = max(worst[ch] ?: 0.0, abs(got[ch] - want[ch]))
                    samples += 1
                    continue
                }
                for (ch in Keyframes.CHANNELS) worst[ch] = max(worst[ch] ?: 0.0, abs(got[ch] - want[ch]))
                if (c["path"] != null && v["px"] != null) {
                    val x = num(box, "x")
                    val y = num(box, "y")
                    val w = num(box, "w")
                    val h = num(box, "h")
                    val f = LauncherEntrance.spriteFrame(FxSprite(x = x, y = y, w = w, h = h, tracks = tracks, path = c["path"] as String), t, got)
                    val ex = x + w / 2 + f.tx - (v["px"] as Number).toDouble()
                    val ey = y + h / 2 + f.ty - (v["py"] as Number).toDouble()
                    worst["path"] = max(worst["path"] ?: 0.0, sqrt(ex * ex + ey * ey))
                }
                samples += 1
            }
        }
        println("launcherfx golden: ${cases.size} cases, $samples samples; max error " + worst.toSortedMap().mapValues { "%.5f".format(it.value) })
        assertEquals(499, cases.size)
        assertEquals(22123, samples)
        for (ch in listOf("tx", "ty", "txp", "typ", "r", "rx", "ry", "od", "dash", "clip")) assertTrue("$ch ${worst[ch]}", worst.getValue(ch) < 0.01)
        for (ch in listOf("sx", "sy", "o")) assertTrue("$ch ${worst[ch]}", worst.getValue(ch) < 0.001)
        // Chrome lays `left` out on a 1/64 px grid; the motion path is flattened.
        // The mascot's transform lists, as matrices.
        assertTrue("matrix ${worst["matrix"]}", worst.getValue("matrix") < 0.001)
        assertTrue("left ${worst["left"]}", worst.getValue("left") < 0.05)
        assertTrue("path ${worst["path"]}", worst.getValue("path") < 0.6)
    }

    @Test
    fun buildsTheSameTracksTheGoldenWasSampledFrom() {
        val want = cases.associate { it["src"] as String to it["tracks"] as List<Map<String, Any?>> }
        val geometry = golden["geometry"] as Map<String, Map<String, Any?>>
        val g = geometry.getValue("right")
        val seed = (golden["seed"] as Number).toInt()
        var checked = 0
        val gson = Gson()
        fun check(src: String, tracks: List<Track>) {
            assertTrue(src, want.containsKey(src))
            // Through JSON, as the golden was read.
            val ours = norm(gson.fromJson(gson.toJson(tracks.map { Keyframes.toJson(it) }), List::class.java) as List<Map<String, Any?>>)
            val chrome = norm(want.getValue(src))
            assertTrue("$src\nours:   ${gson.toJson(ours)}\nchrome: ${gson.toJson(chrome)}", same(ours, chrome))
            checked += 1
        }

        for (m in LauncherAttention.MODES) {
            if (m == "off") continue
            for (t in LauncherAttention.tracks(m)) check("attention:$m:${t.part}", listOf(t))
        }
        for (t in LauncherAttention.tracks("pulse", clipped = true)) check("attention:pulse-clipped:${t.part}", listOf(t))
        for (t in LauncherAttention.tracks("pulse", wide = true)) check("attention:pulse-wide:${t.part}", listOf(t))
        for (pos in listOf("right", "left")) {
            for (card in listOf(false, true)) {
                for (m in LauncherIdleCall.MODES) {
                    if (m == "off") continue
                    if ((pos == "left" || card) && m != "peek" && m != "knock") continue
                    val p = LauncherIdleCall.performance(m, LauncherIdleCall.geometry(num(g, "width"), num(g, "height"), pos, card))
                    val tag = "idle:$m@$pos${if (card) "+card" else ""}"
                    check("$tag:self", p.self)
                    p.parts.forEach { (part, tracks) -> check("$tag:$part", tracks) }
                    p.launcher.forEachIndexed { i, t -> check("$tag:launcher$i", listOf(t)) }
                    p.ripples.forEachIndexed { i, r -> check("$tag:ripple$i", listOf(r.track)) }
                    check("$tag:bubble", listOf(p.bubble))
                }
            }
        }
        for (label in listOf("right", "left")) {
            val geo = geometry.getValue(label)
            val stage = LauncherEntrance.stage(
                width = num(geo, "width"),
                height = num(geo, "height"),
                position = geo["position"] as String,
                screenWidth = num(geo, "screenWidth"),
                screenHeight = num(geo, "screenHeight"),
                side = num(geo, "side"),
                bottom = num(geo, "bottom"),
                accent = geo["accent"] as String,
            )
            for (m in LauncherEntrance.MODES) {
                if (m == "classic") continue
                if (label == "left" && m !in listOf("ufo", "confetti", "magic", "plane", "meteor")) continue
                val p = LauncherEntrance.plan(m, stage, seed)!!
                check("entrance:$m@$label:arrive", listOf(p.arrive.withDelay(p.revealAt)))
                fun walk(sp: FxSprite, at: String) {
                    if (sp.tracks.isNotEmpty()) check("entrance:$m@$label:$at", sp.tracks)
                    sp.children.forEachIndexed { i, k -> walk(k, "$at.$i") }
                }
                p.sprites.forEachIndexed { i, sp -> walk(sp, "s$i") }
            }
        }
        check("entrance:settle", listOf(LauncherEntrance.SETTLE_REVEAL))
        // The mascot: its moods, the typing mascot and pencil, the card's moves and the figure's gestures.
        val moods = listOf(
            Triple("wave", "wave", 2.0),
            Triple("wave", "down", null),
            Triple("think", null, null),
            Triple("happy", "down", null),
        )
        for ((mood, arm, waves) in moods) {
            val plan = MascotMoods.moodPlan(mood, arm = arm, waveCount = waves)
            for ((part, tracks) in plan.parts) {
                if (part == "lid" && mood != "wave") continue
                check("mascot:mood-$mood${if (arm != null) "@$arm" else ""}:$part", tracks)
            }
        }
        val typing = MascotMoods.Typing
        check("mascot:typing:sway", listOf(typing.sway))
        check("mascot:typing:blink", listOf(typing.blink))
        check("mascot:typing:glance", listOf(typing.glance))
        typing.dots.forEachIndexed { i, d -> check("mascot:typing:dot$i", listOf(d.track)) }
        check("mascot:pencil:line", listOf(MascotMoods.Pencil.write))
        check("mascot:pencil:pencil", listOf(MascotMoods.Pencil.pencil))
        check("mascot:card:spring", listOf(MascotMotion.cardEnterTrack("lively", "card")!!))
        check("mascot:card:in", listOf(MascotMotion.cardEnterTrack("subtle", "card")!!))
        check("mascot:card:collapse", listOf(MascotMotion.cardLeaveTrack("lively", "open")!!))
        check("mascot:card:out", listOf(MascotMotion.cardLeaveTrack("off", "close")!!))
        check("mascot:card:ping-dot", listOf(MascotMotion.Ping.dot))
        check("mascot:card:ping-ring", listOf(MascotMotion.Ping.ring))
        check("mascot:card:word3", listOf(MascotMotion.wordFade("Hi there, how can we help you today?")!!.tracks[3]))
        for (amp in listOf(1.0, 0.75, 0.55)) {
            val a = Keyframes.jsNum(amp)
            for (g in listOf("pop", "rise", "fadeIn", "wave", "tilt", "nod", "hop", "handoff", "bow")) {
                if (amp != 1.0 && (g == "rise" || g == "fadeIn")) continue
                val delay = when (g) { "pop" -> 140.0; "rise" -> 80.0; else -> 0.0 }
                check("mascot:gesture:$g@$a", listOf(MascotMotion.gestureTrack(g, if (g == "fadeIn") 0.0 else amp, delay, if (g == "handoff") -40.0 to 60.0 else null)))
            }
            check("mascot:gesture:talk@$a", listOf(MascotMotion.talkTrack(amp)))
        }
        check("mascot:gesture:breath", listOf(MascotMotion.BREATH))
        // Every case in the golden is one this SDK builds.
        assertEquals(cases.size, checked)
    }

    @Test
    fun readsTheOwnersSettingsAsTheWebDoes() {
        val off = Launcher.resolve(mapOf("widgetIconBgColor" to "#000000"))
        assertEquals("classic", off.entrance)
        assertEquals("off", off.attention)
        assertEquals("off", off.idleCall)
        assertEquals(12, off.idleCallDelay)
        val on = Launcher.resolve(mapOf(
            "launcherEntrance" to "rocket",
            "launcherAttention" to "orbit",
            "launcherIdleCall" to "knock",
            "launcherIdleCallDelay" to "2.5",
            "launcherIdleCallText" to "  Hello   there, anybody home today?  ",
        ))
        assertEquals("rocket", on.entrance)
        assertEquals("orbit", on.attention)
        assertEquals("knock", on.idleCall)
        assertEquals(5, on.idleCallDelay)
        assertEquals("Hello there, anybody hom", on.idleCallText)
        assertEquals("classic", LauncherEntrance.normalize("warp"))
        assertEquals(120, LauncherIdleCall.normalizeDelay(999))
        assertEquals(13, LauncherIdleCall.normalizeDelay(12.5))
        assertEquals("Knock knock!", LauncherIdleCall.line("knock", "   "))
        assertEquals("greeting", LauncherAttention.target("float"))
        assertEquals("launcher", LauncherAttention.target("pulse"))
    }
}
