package com.conferbot.sdk.ui.compose

import com.conferbot.sdk.core.customization.LauncherEntrance
import com.conferbot.sdk.core.customization.LauncherIdleCall
import com.conferbot.sdk.core.customization.LauncherMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.ceil

/** The rules round the launcher's entrance, attention loop and idle call (as the React Native SDK's hooks keep them), on a fake clock. */
class LauncherFxControllersTest {
    private class FakeTimer(val at: Long, val fn: () -> Unit) : FxTimer {
        var cancelled = false
        override fun cancel() { cancelled = true }
    }

    class FakeClock : FxScheduler {
        var t = 1_000_000_000_000L
        private val timers = mutableListOf<FakeTimer>()
        override fun now() = t
        override fun after(ms: Long, fn: () -> Unit): FxTimer = FakeTimer(t + ms, fn).also { timers.add(it) }

        /** Moves the clock on, firing the timers due in order. */
        fun advance(ms: Long) {
            val end = t + ms
            while (true) {
                val x = timers.filter { !it.cancelled && it.at <= end }.minByOrNull { it.at } ?: break
                timers.remove(x)
                t = x.at
                x.fn()
            }
            t = end
        }
    }

    class MemoryRest : IdleCallRestStore {
        val until = HashMap<String, Long>()
        override fun read(botId: String) = until[botId] ?: 0L
        override fun write(botId: String, until: Long) { this.until[botId] = until }
    }

    private fun stage() = LauncherEntrance.stage(50.0, 50.0, "right", 390.0, 844.0, 16.0, 16.0, "#4f46e5")

    @Before
    fun setUp() = LauncherFxSession.reset()

    // ---- Entrance ----

    @Test
    fun entranceHoldsPlaysAndKeepsTheCardHiddenUntilItLandsThenFadesItUp150msLater() {
        val clock = FakeClock()
        val c = LauncherEntranceController(clock)
        c.decide("rocket", "b1", reduced = false, chatOpen = false)
        assertEquals("hold", c.phase)
        assertTrue(c.busy)
        assertEquals(0.0, c.lookAt(0.0)!!.o, 0.0)
        assertEquals(0.0, c.lookAt(0.0)!!.greetingO, 0.0)
        c.laidOut(::stage)
        clock.advance(LauncherEntrance.START_MS)
        assertEquals("play", c.phase)
        val tl = LauncherEntrance.timeline(c.plan!!)
        // Before the reveal the launcher is still hidden; the card stays hidden until the landing settles.
        assertEquals(0.0, c.lookAt(tl.landAt - 1)!!.o, 0.0)
        assertEquals(0.0, c.lookAt(tl.landedAt)!!.greetingO, 0.0)
        assertEquals(0.0, c.lookAt(tl.settleAt - 1)!!.greetingO, 0.0)
        assertTrue(c.lookAt(tl.settleAt + 450)!!.greetingO > 0.5)
        clock.advance(ceil(tl.settleAt).toLong())
        assertFalse(c.busy)
        clock.advance(ceil(tl.endAt - tl.settleAt).toLong() + 1)
        assertEquals("done", c.phase)
        assertNull(c.lookAt(0.0))
    }

    @Test
    fun entrancePlaysOncePerAppSessionPerBotAndEntrance() {
        val clock = FakeClock()
        val a = LauncherEntranceController(clock).apply { decide("drop", "b1", false, false) }
        a.laidOut(::stage)
        clock.advance(5000)
        assertEquals("drop", LauncherFxSession.entrancePlayed["b1"])
        assertEquals("none", LauncherEntranceController(clock).apply { decide("drop", "b1", false, false) }.phase)
        assertEquals("hold", LauncherEntranceController(clock).apply { decide("drop", "b2", false, false) }.phase)
        assertEquals("hold", LauncherEntranceController(clock).apply { decide("coin", "b1", false, false) }.phase)
    }

    @Test
    fun entranceNeverOverAnOpenChatWithReducedMotionOrForClassicAndOpeningTheChatEndsIt() {
        val clock = FakeClock()
        assertEquals("none", LauncherEntranceController(clock).apply { decide("ufo", "x", reduced = false, chatOpen = true) }.phase)
        assertEquals("none", LauncherEntranceController(clock).apply { decide("ufo", "x", reduced = true, chatOpen = false) }.phase)
        assertEquals("none", LauncherEntranceController(clock).apply { decide("classic", "x", reduced = false, chatOpen = false) }.phase)
        val c = LauncherEntranceController(clock).apply { decide("ufo", "x", false, false) }
        c.laidOut(::stage)
        clock.advance(200)
        c.chatOpened()
        assertEquals("done", c.phase)
        assertFalse(c.busy)
    }

    @Test
    fun theLauncherSimplyAppearsIfItIsNeverLaidOut() {
        val clock = FakeClock()
        val c = LauncherEntranceController(clock).apply { decide("egg", "x", false, false) }
        clock.advance(LauncherEntranceController.REVEAL_FAILSAFE_MS)
        assertEquals("done", c.phase)
    }

    // ---- Attention ----

    private fun feed(
        c: LauncherAttentionController,
        mode: String = "heartbeat",
        chatOpen: Boolean = false,
        card: Boolean = false,
        moment: Boolean = false,
        busy: Boolean = false,
        size: Boolean = true,
        reduced: Boolean = false,
    ) = c.update(mode, true, reduced, chatOpen, card, moment, busy, size, false)

    @Test
    fun attentionWaitsForTheEntranceThenRunsItsLoopFromTheStart() {
        val clock = FakeClock()
        val c = LauncherAttentionController(clock)
        feed(c, busy = true)
        assertTrue(c.onLauncher)
        assertFalse(c.running)
        clock.advance(3000)
        feed(c)
        assertTrue(c.running)
        assertEquals(0.0, c.elapsed, 0.0)
        clock.advance(1600)
        assertEquals(1600.0, c.elapsed, 0.0)
        // Laps after the 1.5s intro.
        clock.advance(3000)
        assertEquals(1600.0, c.elapsed, 1e-9)
    }

    @Test
    fun attentionPausesForALauncherMomentAndResumesWhereItWas() {
        val clock = FakeClock()
        val c = LauncherAttentionController(clock)
        feed(c)
        clock.advance(500)
        feed(c, moment = true)
        clock.advance(2600)
        assertEquals(500.0, c.elapsed, 0.0)
        feed(c)
        clock.advance(100)
        assertEquals(600.0, c.elapsed, 0.0)
    }

    @Test
    fun attentionIsOverOnceTheChatOpensAndNotStartedWithin30MinutesOfAnOpening() {
        val clock = FakeClock()
        val c = LauncherAttentionController(clock)
        feed(c)
        feed(c, chatOpen = true)
        assertFalse(c.running)
        feed(c)
        assertFalse(c.running)
        LauncherFxSession.openedAt = clock.now()
        clock.advance(LauncherMotion.OPENED_REST_MS - 1)
        val d = LauncherAttentionController(clock)
        feed(d)
        assertFalse(d.active)
        clock.advance(2)
        val e = LauncherAttentionController(clock)
        feed(e)
        assertTrue(e.running)
    }

    @Test
    fun aCardModeRunsOnlyWhileTheCardShowsAndNothingWithReducedMotion() {
        val clock = FakeClock()
        val c = LauncherAttentionController(clock)
        feed(c, mode = "wave")
        assertEquals("greeting", c.target)
        assertFalse(c.onLauncher)
        assertFalse(c.running)
        feed(c, mode = "wave", card = true)
        assertTrue(c.running)
        assertTrue(c.has("hand"))
        val r = LauncherAttentionController(clock)
        feed(r, reduced = true)
        assertFalse(r.active)
    }

    // ---- Idle call ----

    private fun started(mode: String = "knock", delay: Int = 12, bot: String = "b1", clock: FakeClock = FakeClock(), rest: MemoryRest = MemoryRest()) =
        Triple(LauncherIdleCallController(clock, rest).apply {
            width = 50.0
            height = 50.0
            start(mode, delay, bot, reduced = false)
        }, clock, rest)

    @Test
    fun idleCallCallsAfterTheDelayAndIsOverAfterItsPerformance() {
        val (c, clock, _) = started()
        clock.advance(11999)
        assertNull(c.perf)
        clock.advance(1)
        assertNotNull(c.perf)
        assertTrue(c.perf!!.front)
        clock.advance(ceil(c.perf!!.total).toLong() + 80)
        assertNull(c.perf)
    }

    @Test
    fun idleCallWaits4sAtATimeWhileBlocked() {
        val (c, clock, _) = started()
        c.keyboard = true
        clock.advance(12000)
        assertNull(c.perf)
        c.keyboard = false
        c.appActive = false
        clock.advance(LauncherIdleCall.RETRY_MS)
        assertNull(c.perf)
        c.appActive = true
        c.entranceBusy = true
        clock.advance(LauncherIdleCall.RETRY_MS)
        assertNull(c.perf)
        c.entranceBusy = false
        c.chatOpen = true
        clock.advance(LauncherIdleCall.RETRY_MS)
        assertNull(c.perf)
        c.chatOpen = false
        c.cardShown = true
        clock.advance(LauncherIdleCall.RETRY_MS)
        assertNull(c.perf)
        // The card has had 15s alone: the mascot calls, without a bubble.
        clock.advance(12000)
        assertNotNull(c.perf)
        assertFalse(c.perf!!.talks)
    }

    @Test
    fun idleCallTwicePerSession3MinutesApartThen3DaysOfRest() {
        val clock = FakeClock()
        val rest = MemoryRest()
        val (a, _, _) = started(clock = clock, rest = rest)
        clock.advance(12000)
        assertNotNull(a.perf)
        assertNull(rest.until["b1"])
        // A second screen this session, too soon: nothing.
        val (b, _, _) = started(clock = clock, rest = rest)
        clock.advance(60000)
        assertNull(b.perf)
        clock.advance(LauncherIdleCall.MIN_GAP_MS)
        val (c, _, _) = started(clock = clock, rest = rest)
        clock.advance(12000)
        assertNotNull(c.perf)
        assertTrue(rest.until.getValue("b1") > clock.now() + LauncherIdleCall.REST_MS - 20000)
        // A third: the session's two are spent.
        clock.advance(LauncherIdleCall.MIN_GAP_MS * 2)
        val (d, _, _) = started(clock = clock, rest = rest)
        clock.advance(12000)
        assertNull(d.perf)
        // Next session (the rest stays in storage).
        LauncherFxSession.reset()
        val (e, _, _) = started(clock = clock, rest = rest)
        clock.advance(12000)
        assertNull(e.perf)
        clock.advance(LauncherIdleCall.REST_MS)
        LauncherFxSession.reset()
        val (f, _, _) = started(clock = clock, rest = rest)
        clock.advance(12000)
        assertNotNull(f.perf)
    }

    @Test
    fun openingTheChatEndsTheCallAndRests3DaysAndClosingTheGreetingStopsCallsForTheSession() {
        val (c, clock, rest) = started()
        clock.advance(12000)
        c.chatOpened()
        assertNull(c.perf)
        assertEquals(clock.now() + LauncherIdleCall.REST_MS, rest.until["b1"])
        val (g, clock2, _) = started(bot = "b2")
        g.greetingClosed()
        clock2.advance(60000)
        assertNull(g.perf)
        assertTrue(LauncherFxSession.visits.getValue("b2").quiet)
    }

    @Test
    fun idleCallNothingForOffOrWithReducedMotionNorWithin30MinutesOfAnOpening() {
        val (off, k1, _) = started(mode = "off")
        k1.advance(60000)
        assertNull(off.perf)
        val clock = FakeClock()
        val reduced = LauncherIdleCallController(clock, MemoryRest()).apply {
            width = 50.0
            height = 50.0
            start("wave", 5, "b3", reduced = true)
        }
        clock.advance(60000)
        assertNull(reduced.perf)
        LauncherFxSession.openedAt = clock.now()
        val (opened, _, _) = started(clock = clock, bot = "b4")
        clock.advance(60000)
        assertNull(opened.perf)
    }
}
