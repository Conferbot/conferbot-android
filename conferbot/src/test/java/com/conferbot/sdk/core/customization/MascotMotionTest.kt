package com.conferbot.sdk.core.customization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mascot's moods (art3d MASCOT_ANIM_CSS), the typing mascot and pencil,
 * the greeting card's director on a fake clock (home/mascotMotion.ts) and the
 * card's own moves (polish-home.css), as the React Native SDK's
 * __tests__/core/mascot.test.ts checks them. Their tracks against Chrome are
 * in LauncherFxGoldenTest.
 */
class MascotMotionTest {
    // ---------- The moods ----------

    @Test
    fun blinksAlwaysAndAddsTheMoodsOwnMoves() {
        assertEquals(listOf("lid"), MascotMoods.moodPlan("idle").parts.keys.toList())
        assertEquals(listOf("arm", "lid"), MascotMoods.moodPlan("wave", arm = "wave").parts.keys.sorted())
        assertEquals(listOf("body", "brow", "lid", "look"), MascotMoods.moodPlan("think").parts.keys.sorted())
        assertEquals(listOf("arm", "body", "lid", "shadow"), MascotMoods.moodPlan("happy", arm = "down").parts.keys.sorted())
        assertTrue(MascotMoods.moodPlan("happy").grin)
        // The bust (launcher, typing) has no arm.
        assertNull(MascotMoods.moodPlan("happy", bust = true).parts["arm"])
    }

    @Test
    fun liftsARestingArmBeforeItWavesAndWavesASetNumberOfTimes() {
        val up = MascotMoods.moodPlan("wave", arm = "down").parts.getValue("arm")[0]
        assertEquals(3200.0, up.options.duration, 0.0)
        assertEquals(-132.0, Keyframes.evaluate(listOf(up), 3200 * 0.24).r, 1e-4)
        val two = MascotMoods.moodPlan("wave", arm = "wave", waveCount = 2.0)
        assertEquals(5600.0, Keyframes.tracksEnd(two.parts.getValue("arm")), 0.0)
        // The loop starts on a whole blink after the two waves.
        assertEquals(6400.0, two.settle, 0.0)
        assertEquals(6400.0, two.period, 0.0)
    }

    @Test
    fun hopsTwiceWhenHappyThenBlinksOn() {
        val p = MascotMoods.moodPlan("happy", arm = "down")
        assertEquals(-8.0, Keyframes.evaluate(p.parts.getValue("body"), 900 * 0.45).ty, 1e-4)
        assertEquals(1800.0, Keyframes.tracksEnd(p.parts.getValue("body")), 0.0)
        assertEquals(19200.0, MascotMoods.moodPlan("think").period, 0.0)
    }

    @Test
    fun keepsTheSmileAndNothingElseUnderReducedMotion() {
        assertEquals(MascotMoods.MoodPlan(emptyMap(), true, 0.0, 0.0), MascotMoods.moodPlan("happy", reduced = true))
    }

    @Test
    fun typesSwayingAbout50By90BlinkingGlancingThreeDots() {
        val t = MascotMoods.Typing
        assertEquals(16.0 to 28.8, t.ORIGIN)
        assertEquals(4.0, Keyframes.evaluate(listOf(t.sway), 1200.0).r, 1e-6)
        assertEquals(listOf(0.0, 200.0, 400.0), t.dots.map { it.track.options.delay })
        assertEquals(0.0, t.PERIOD % 2400, 0.0)
        assertEquals(0.0, t.PERIOD % 3400, 0.0)
        assertTrue(MascotMoods.KEYFRAMES.getValue("cb-typing-blink").contains("95%"))
        assertEquals("Pip is typing", MascotMoods.typingLabel("mascot", "Pip"))
        assertEquals("Typing", MascotMoods.typingLabel("dots", "Pip"))
        assertEquals("Typing", MascotMoods.typingLabel("mascot", ""))
        // The pencil writes its line in 3/4 of the lap, then fades.
        val p = MascotMoods.Pencil
        assertEquals(60.0, Keyframes.evaluate(listOf(p.write), 0.0, mapOf("dash" to 60.0)).dash, 1e-9)
        assertEquals(0.0, Keyframes.evaluate(listOf(p.write), 1500.0, mapOf("dash" to 60.0)).dash, 1e-9)
        assertEquals(36.0, Keyframes.evaluate(listOf(p.pencil), 1600.0).tx, 1e-9)
    }

    @Test
    fun readsCssKeyframesAndTransformListsAsChromePlaysThem() {
        val k = Keyframes.cssKeyframes("0%,100%{transform:scaleY(0)}50%{transform:scaleY(1);opacity:.5}", "ease-in-out")
        assertEquals(listOf(0.0, 0.5, 1.0), k.map { it["offset"] })
        assertTrue(k.all { it["easing"] == "ease-in-out" })
        assertEquals("scaleY(1)", k[1]["transform"])
        assertEquals(".5", k[1]["opacity"])
        val tr = Track(listOf(mapOf("transform" to "none"), mapOf("transform" to "rotate(10deg) translateX(-2px)")), TrackOptions(100.0))
        val v = Keyframes.evaluate(listOf(tr), 50.0)
        assertEquals(5.0, v.r, 1e-9)
        assertEquals(-1.0, v.tx, 1e-9)
        assertEquals("rts", Keyframes.transformOrder(tr))
        assertEquals("tsr", Keyframes.transformOrder(Track(listOf(mapOf("transform" to "scale(2)"), mapOf("transform" to "translateY(-6px) scale(1)")), TrackOptions(1.0))))
        val m = Keyframes.transformMatrix(FxValues.rest(mapOf("ty" to 1.0, "r" to 90.0)), "rts")
        assertEquals(listOf(0.0, 1.0, -1.0, 0.0, -1.0, 0.0), m.map { Math.round(it * 1e6) / 1e6 + 0.0 })
        // The eyelids' plain scaleY stays the scale's y alone.
        val lid = Track(listOf(mapOf("transform" to "scaleY(0)"), mapOf("transform" to "scaleY(1)")), TrackOptions(100.0))
        val l = Keyframes.evaluate(listOf(lid), 50.0, mapOf("tx" to 3.0))
        assertEquals(0.5, l.sy, 1e-9)
        assertEquals(3.0, l.tx, 1e-9)
    }

    // ---------- The greeting card's figure, on a fake clock ----------

    private class Clock : MascotTimers {
        var t = 0L
        private var next = 1
        val queue = LinkedHashMap<Int, Pair<Long, () -> Unit>>()
        override fun set(ms: Long, fn: () -> Unit): Any = (next++).also { queue[it] = (t + ms) to fn }
        override fun clear(id: Any) { queue.remove(id) }
        fun advance(ms: Long) {
            val end = t + ms
            while (true) {
                val e = queue.entries.filter { it.value.first <= end }.minByOrNull { it.value.first } ?: break
                queue.remove(e.key)
                t = e.value.first
                e.value.second()
            }
            t = end
        }
    }

    private class Rig(mode: String = "lively", reduced: Boolean = false, alive: (() -> Boolean)? = null) {
        val c = Clock()
        val log = mutableListOf<List<Any>>()
        val stage = object : MascotStage {
            override fun play(g: String, amp: Double, delay: Long) { log.add(listOf("play:$g", amp, delay)) }
            override fun breathe(on: Boolean) { log.add(listOf(if (on) "breathe" else "exhale")) }
            override fun talk(on: Boolean, amp: Double) { log.add(listOf(if (on) "talk" else "quiet", amp)) }
            override fun lean(x: Double, amp: Double) { log.add(listOf("lean", x, amp)) }
            override fun ping(on: Boolean) { log.add(listOf(if (on) "ping" else "ping-off")) }
            override fun words() { log.add(listOf("words")) }
            override fun reset() { log.add(listOf("reset")) }
        }
        val d = MascotDirector(mode, stage, c, reducedOf = { reduced }, random = { 0.5 }, now = { c.t }, alive = alive)
        fun names() = log.map { it[0] as String }
    }

    private val idleGap = MascotMotion.IDLE_GAP_MIN + MascotMotion.IDLE_GAP_SPREAD / 2

    @Test
    fun isLivelyUnlessTheOwnerChoseSubtleOrOff() {
        assertEquals("lively", MascotMotion.normalize(null))
        assertEquals("lively", MascotMotion.normalize("wild"))
        assertEquals("subtle", MascotMotion.normalize("subtle"))
        assertEquals("off", MascotMotion.normalize("off"))
    }

    @Test
    fun livelyWordsPopWavePingNodThenABreath() {
        val r = Rig()
        val L = MascotMotion.Lively
        r.d.show()
        assertEquals(listOf("reset", "words", "play:pop"), r.names())
        assertEquals(L.POP, r.log[2][2])
        r.c.advance(L.WAVE)
        assertEquals("play:wave", r.names().last())
        r.c.advance(L.PING - L.WAVE)
        assertEquals("ping", r.names().last())
        r.c.advance(L.NOD - L.PING)
        assertEquals("play:nod", r.names().last())
        r.c.advance(L.IDLE - L.NOD)
        assertEquals("living", r.d.state)
        assertTrue(r.names().contains("breathe"))
        r.c.advance(MascotMotion.PING_MS)
        assertTrue(r.names().contains("ping-off"))
    }

    @Test
    fun threeIdleGesturesAtMostThenStillWithNoTimerLeft() {
        val r = Rig()
        r.d.show()
        r.c.advance(MascotMotion.Lively.IDLE)
        repeat(6) { r.c.advance(idleGap) }
        assertEquals(3, r.d.gestures)
        r.c.advance(MascotMotion.SETTLE_AFTER)
        assertEquals("settled", r.d.state)
        assertEquals("exhale", r.names().last())
        assertEquals(0, r.c.queue.size)
        r.c.advance(120000)
        assertEquals(3, r.d.gestures)
    }

    @Test
    fun subtleARiseThePingOneTiltNoWordsNoWave() {
        val r = Rig("subtle")
        r.d.show()
        r.c.advance(MascotMotion.Subtle.IDLE)
        repeat(4) { r.c.advance(idleGap) }
        r.c.advance(MascotMotion.SETTLE_AFTER)
        val n = r.names()
        assertTrue(n.containsAll(listOf("play:rise", "ping", "play:tilt")))
        assertFalse(n.contains("words"))
        assertFalse(n.contains("play:wave"))
        assertEquals("settled", r.d.state)
    }

    @Test
    fun reducedMotionAFadeNothingElseOffNothingAtAll() {
        val r = Rig(reduced = true)
        r.d.show()
        r.d.hide("open")
        r.c.advance(60000)
        assertEquals(listOf("reset", "play:fadeIn", "reset"), r.names())
        val off = Rig("off")
        off.d.show()
        off.c.advance(60000)
        assertEquals(listOf("reset"), off.names())
    }

    @Test
    fun aVoiceNoteTalksInPlaceOfTheWaveThenQuiet() {
        val r = Rig()
        r.d.show()
        r.d.talkFor(3600)
        r.c.advance(MascotMotion.Lively.WAVE)
        assertEquals("talk", r.names().last())
        r.c.advance(3600 - MascotMotion.Lively.WAVE)
        assertEquals("quiet", r.names().last())
    }

    @Test
    fun handsOffWhenOpenedFromTheCardBowsAtTheX() {
        val open = Rig()
        open.d.show()
        open.d.hide("open")
        assertEquals("play:handoff", open.names().last())
        assertEquals(0, open.c.queue.size)
        val close = Rig("subtle")
        close.d.show()
        close.d.hide("close")
        assertEquals(listOf<Any>("play:bow", 0.6, 0L), close.log.last())
    }

    @Test
    fun waitsInTheBackgroundAndGivesWayToTheCardLeaving() {
        val r = Rig()
        r.d.show()
        r.c.advance(MascotMotion.Lively.IDLE)
        r.d.pageHidden(true)
        r.c.advance(60000)
        assertEquals(0, r.d.gestures)
        r.d.pageHidden(false)
        r.c.advance(idleGap)
        assertEquals(1, r.d.gestures)
        var alive = true
        val gone = Rig(alive = { alive })
        gone.d.show()
        alive = false
        gone.c.advance(MascotMotion.Lively.WAVE)
        assertEquals("destroyed", gone.d.state)
        assertEquals(0, gone.c.queue.size)
    }

    @Test
    fun movesAMascotAllTheWayAPhotoAndInitialsLess() {
        assertEquals(listOf(1.0, 0.75, 0.55, 1.0), listOf("mascot", "avatar", "initials", null).map { MascotMotion.figureAmp(it) })
        assertTrue(MascotMotion.ownsText("typewriter") && MascotMotion.ownsText("imessage") && !MascotMotion.ownsText("card"))
    }

    // ---------- The card's own moves ----------

    @Test
    fun springsUpWhenLivelyComesInAsAlwaysOtherwise() {
        assertEquals(620.0, MascotMotion.cardEnterTrack("lively", "card")!!.options.duration, 0.0)
        assertEquals(450.0, MascotMotion.cardEnterTrack("subtle", "card")!!.options.duration, 0.0)
        assertEquals(450.0, MascotMotion.cardEnterTrack("off", "note")!!.options.duration, 0.0)
        assertNull(MascotMotion.cardEnterTrack("lively", "notification"))
        assertNull(MascotMotion.cardEnterTrack("lively", "neon"))
        assertNull(MascotMotion.cardEnterTrack("lively", "card", true))
        val spring = MascotMotion.cardEnterTrack("lively", "card")!!
        val a = Keyframes.evaluate(listOf(spring), 0.0)
        assertEquals(listOf(0.0, 14.0, 0.94), listOf(a.o, a.ty, a.sx))
        val b = Keyframes.evaluate(listOf(spring), 620.0)
        assertEquals(listOf(1.0, 0.0, 1.0), listOf(b.o, b.ty, b.sx))
    }

    @Test
    fun collapsesTowardTheLauncherWhenOpenedFadesWhenClosed() {
        val c = MascotMotion.cardLeaveTrack("lively", "open")!!.options
        assertEquals(240.0 to 40.0, c.duration to c.delay)
        assertEquals(220.0, MascotMotion.cardLeaveTrack("off", "open")!!.options.duration, 0.0)
        assertEquals(220.0, MascotMotion.cardLeaveTrack("subtle", "close")!!.options.duration, 0.0)
        assertNull(MascotMotion.cardLeaveTrack("lively", "open", true))
    }

    @Test
    fun fadesTheWordsUpWithinASecond() {
        assertNull(MascotMotion.wordFade("Hi"))
        val f = MascotMotion.wordFade("Hi there, how can we help you today?")!!
        assertEquals(8, f.words.size)
        assertEquals(55, f.step)
        val long = MascotMotion.wordFade((0 until 40).joinToString(" ") { "w$it" })!!
        assertEquals(13, long.step)
        assertTrue(Keyframes.tracksEnd(listOf(long.tracks[39])) < 1250)
        assertEquals(3660L, MascotMotion.voicePlayMs())
    }

    @Test
    fun playsEachGestureAsTheWebDoesTheLeavingOnesHoldTheirEnd() {
        assertEquals("forwards", MascotMotion.gestureTrack("handoff", 1.0).options.fill)
        val pop = MascotMotion.gestureTrack("pop", 1.0, 140.0)
        assertEquals(140.0 to "backwards", pop.options.delay to pop.options.fill)
        val v = Keyframes.evaluate(listOf(pop), 0.0)
        assertEquals(0.0 to 0.3, v.o to v.sx)
    }

    // ---------- The settings, resolved ----------

    @Test
    fun readsTheMascotSettingsIntoTheCustomizationsAndTheLauncher() {
        val c = mapOf("mascotShape" to "owl", "mascotInGreeting" to true, "mascotMotion" to "subtle", "mascotLauncherScale" to 130, "widgetIconBgColor" to "#123456", "launcherStyle" to "mascot")
        val r = Customizations.resolve(c)
        assertEquals("owl", r.mascot.shape)
        assertTrue(r.mascotInGreeting)
        assertEquals("subtle", r.mascotMotion)
        assertEquals(130, r.mascotLauncherScale)
        assertEquals("#123456", r.mascotAccent)
        val d = Customizations.resolve(emptyMap())
        assertEquals(listOf<Any>(false, "lively", 112), listOf(d.mascotInGreeting, d.mascotMotion, d.mascotLauncherScale))
        val l = Launcher.resolve(c, 390f)
        assertEquals("mascot", l.style)
        assertEquals(130, l.mascot.scale)
        assertEquals("#123456", l.mascot.accent)
        assertEquals(MascotArt.mixColor("#123456", 14, "#ffffff"), l.mascot.light)
        assertEquals("owl", l.mascot.look.shape)
        // An uploaded launcher image wins over the mascot.
        assertEquals("icon", Launcher.resolve(c + mapOf("widgetIconType" to "Image", "widgetIconImage" to "https://x/y.png"), 390f).style)
    }
}
