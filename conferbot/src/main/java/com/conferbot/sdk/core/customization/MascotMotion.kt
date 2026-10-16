package com.conferbot.sdk.core.customization

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The greeting card's figure, alive (conferbot-widget src/ts/home/mascotMotion.ts
 * and css/polish-home.css .cb-mm): how the bot's figure on the greeting card
 * moves. The figure is the owner's mascot (mascotInGreeting), the bot's
 * avatar, or an agent's initials; everything here moves it as a whole.
 *
 * customizations.mascotMotion: "lively" (absent), "subtle" or "off".
 *
 * Lively, from the moment the card shows (it springs up, cb-greet-spring):
 *     0ms  the greeting's words start fading up, one after another (< 1s)
 *   140ms  the figure pops in: stretch, squash, settle (620ms)
 *   760ms  a friendly wave, rocking about its feet (900ms)
 *   900ms  the "Online" dot sends out one soft ping
 *  1660ms  a blink-like double squash (380ms)
 *  1900ms  idle: a slow breath (2px), and a small gesture every 8-12s,
 *          three at most; 3s after the last one the breath ends on its
 *          next exhale and the figure stands still for good.
 * Subtle: a gentle rise, the ping, the breath and a single head tilt; the
 * card comes in as it always did (cb-greet-in). Off: nothing moves.
 *
 * Leaving: opening the chat from the card hands off (the figure shrinks
 * toward the launcher as the card collapses, cb-greet-collapse); the x sends
 * it off with a small bow (the card cb-greet-out). A voice note makes it
 * talk (a rhythmic squash) while its waveform plays.
 *
 * Calm by design: no gesture while the app is in the background, nothing
 * that loops for ever, and under reduced motion just a fade. Hovering (the
 * lean toward the pointer, the hop) has no counterpart on a touch screen,
 * as on the web, where touch pointers are ignored.
 *
 * The director has no view: it calls a [MascotStage] (tested on a fake
 * clock). The gestures are Web Animations keyframes ([Keyframes] evaluates
 * them); the card's own moves are CSS @keyframes, as the web wrote them.
 * Ported from the React Native SDK's core/mascotMotion.ts. Pure.
 */
object MascotMotion {
    val MOTIONS = listOf("lively", "subtle", "off")
    const val DEFAULT = "lively"

    fun normalize(value: Any?): String = if (value is String && value in MOTIONS) value else DEFAULT

    /** When each part of the entrance plays, ms after the card shows. */
    object Lively {
        const val POP = 140L
        const val WAVE = 760L
        const val PING = 900L
        const val NOD = 1660L
        const val IDLE = 1900L
    }

    object Subtle {
        const val RISE = 80L
        const val PING = 600L
        const val IDLE = 900L
    }

    /** Idle gestures: one every 8-12s, at most this many, then still. */
    const val IDLE_GAP_MIN = 8000L
    const val IDLE_GAP_SPREAD = 4000L
    val IDLE_GESTURES = mapOf("lively" to listOf("tilt", "wave", "tilt"), "subtle" to listOf("tilt"))

    /** After the last idle gesture, the breath ends this much later. */
    const val SETTLE_AFTER = 3000L

    /** The "Online" ping's class stays this long (one ring). */
    const val PING_MS = 1300L

    /** Hover hops no closer together than this. */
    const val HOP_GAP = 1500L

    // ---------- The gestures: Web Animations on the figure ----------

    private fun sc(a: Double, x: Double) = Keyframes.fixed(1 + (x - 1) * a, 4)
    private fun dg(a: Double, x: Double) = "${Keyframes.fixed(x * a, 2)}deg"
    private fun px(a: Double, x: Double) = "${Keyframes.fixed(x * a, 2)}px"

    private fun kf(vararg p: Pair<String, Any?>): Keyframe = linkedMapOf(*p)

    /** A gesture's keyframes, duration and easing. */
    class Frames(val frames: List<Keyframe>, val ms: Double, val easing: String)

    /** Each gesture's keyframes at a given amplitude (1 = the mascot's full swing), as the web passes them to element.animate(). */
    fun gestureFrames(g: String, a: Double, toward: Pair<Double, Double> = 0.0 to 30.0): Frames = when (g) {
        "pop" -> Frames(
            listOf(
                kf("transform" to "translateY(${px(a, 6.0)}) scale(0.3)", "opacity" to 0, "offset" to 0),
                kf("transform" to "translateY(${px(a, -3.0)}) scale(${sc(a, 0.92)}, ${sc(a, 1.14)})", "opacity" to 1, "offset" to 0.42),
                kf("transform" to "translateY(0px) scale(${sc(a, 1.1)}, ${sc(a, 0.9)})", "offset" to 0.64),
                kf("transform" to "translateY(${px(a, -1.0)}) scale(${sc(a, 0.97)}, ${sc(a, 1.03)})", "offset" to 0.82),
                kf("transform" to "none", "opacity" to 1, "offset" to 1),
            ),
            620.0, "cubic-bezier(.3,.7,.4,1)",
        )
        "rise" -> Frames(
            listOf(kf("transform" to "translateY(4px) scale(0.85)", "opacity" to 0), kf("transform" to "none", "opacity" to 1)),
            420.0, "cubic-bezier(.22,1,.36,1)",
        )
        "fadeIn" -> Frames(listOf(kf("opacity" to 0), kf("opacity" to 1)), 220.0, "ease-out")
        "wave" -> Frames(
            listOf(
                kf("transform" to "rotate(0deg)", "offset" to 0),
                kf("transform" to "rotate(${dg(a, -10.0)})", "offset" to 0.2),
                kf("transform" to "rotate(${dg(a, 8.0)})", "offset" to 0.42),
                kf("transform" to "rotate(${dg(a, -6.0)})", "offset" to 0.62),
                kf("transform" to "rotate(${dg(a, 3.0)})", "offset" to 0.8),
                kf("transform" to "rotate(0deg)", "offset" to 1),
            ),
            900.0, "ease-in-out",
        )
        "tilt" -> Frames(
            listOf(
                kf("transform" to "none", "offset" to 0),
                kf("transform" to "rotate(${dg(a, -7.0)}) translateX(${px(a, -1.0)})", "offset" to 0.25),
                kf("transform" to "rotate(${dg(a, -6.0)}) translateX(${px(a, -1.0)})", "offset" to 0.7),
                kf("transform" to "none", "offset" to 1),
            ),
            1300.0, "cubic-bezier(.45,0,.25,1)",
        )
        "nod" -> Frames(
            listOf(
                kf("transform" to "none", "offset" to 0),
                kf("transform" to "scale(${sc(a, 1.03)}, ${sc(a, 0.9)})", "offset" to 0.3),
                kf("transform" to "none", "offset" to 0.55),
                kf("transform" to "scale(${sc(a, 1.02)}, ${sc(a, 0.93)})", "offset" to 0.75),
                kf("transform" to "none", "offset" to 1),
            ),
            380.0, "ease-in-out",
        )
        "hop" -> Frames(
            listOf(
                kf("transform" to "none", "offset" to 0),
                kf("transform" to "scale(${sc(a, 1.08)}, ${sc(a, 0.92)})", "offset" to 0.18),
                kf("transform" to "translateY(${px(a, -6.0)}) scale(${sc(a, 0.96)}, ${sc(a, 1.05)})", "offset" to 0.45),
                kf("transform" to "scale(${sc(a, 1.05)}, ${sc(a, 0.95)})", "offset" to 0.72),
                kf("transform" to "none", "offset" to 1),
            ),
            560.0, "cubic-bezier(.3,.7,.4,1)",
        )
        // Toward the launcher, shrinking, gone as the card collapses.
        "handoff" -> Frames(
            listOf(
                kf("transform" to "none", "opacity" to 1),
                kf("transform" to "translate(${px(a, toward.first * 0.2)}, ${px(a, toward.second * 0.12)}) scale(${sc(a, 1.08)}, ${sc(a, 0.94)})", "opacity" to 1, "offset" to 0.25),
                kf("transform" to "translate(${px(a, toward.first * 0.6)}, ${px(a, toward.second * 0.6)}) scale(0.25)", "opacity" to 0),
            ),
            280.0, "cubic-bezier(.5,0,.75,0)",
        )
        "bow" -> Frames(
            listOf(
                kf("transform" to "none", "opacity" to 1),
                kf("transform" to "rotate(${dg(a, 6.0)}) translateY(${px(a, 1.0)}) scale(${sc(a, 1.02)}, ${sc(a, 0.9)})", "opacity" to 1, "offset" to 0.4),
                kf("transform" to "rotate(${dg(a, 6.0)}) scale(0.5)", "opacity" to 0),
            ),
            300.0, "ease-in",
        )
        else -> throw IllegalArgumentException("no gesture $g")
    }

    val GESTURES = listOf("pop", "rise", "fadeIn", "wave", "tilt", "nod", "hop", "handoff", "bow")

    /** A gesture as the track the web plays (fill backwards; forwards for the two that leave). */
    fun gestureTrack(g: String, amp: Double, delay: Double = 0.0, toward: Pair<Double, Double>? = null): Track {
        val f = if (toward != null) gestureFrames(g, amp, toward) else gestureFrames(g, amp)
        val leaving = g == "handoff" || g == "bow"
        return Track(f.frames, TrackOptions(f.ms, delay, 1.0, f.easing, if (leaving) "forwards" else "backwards"))
    }

    const val TALK_MS = 640.0

    fun talkFrames(a: Double): List<Keyframe> = listOf(
        kf("transform" to "none", "offset" to 0),
        kf("transform" to "scale(${sc(a, 1.025)}, ${sc(a, 0.965)})", "offset" to 0.2),
        kf("transform" to "scale(${sc(a, 0.99)}, ${sc(a, 1.03)})", "offset" to 0.45),
        kf("transform" to "scale(${sc(a, 1.03)}, ${sc(a, 0.96)})", "offset" to 0.7),
        kf("transform" to "none", "offset" to 1),
    )

    /** The talk, one syllable per lap. */
    fun talkTrack(a: Double): Track = Track(talkFrames(a), TrackOptions(TALK_MS, 0.0, Double.POSITIVE_INFINITY, "ease-in-out"))

    const val BREATH_MS = 3400.0

    /** The slow breath, on the individual translate / scale (it sits under the gestures' transform). */
    val BREATH = Track(
        listOf(kf("translate" to "0 0", "scale" to "1"), kf("translate" to "0 -2px", "scale" to "1.012"), kf("translate" to "0 0", "scale" to "1")),
        TrackOptions(BREATH_MS, 0.0, Double.POSITIVE_INFINITY, "ease-in-out"),
    )

    /** How far the figure leans toward the pointer at full amplitude. */
    const val LEAN_DEG = 6.0

    /** How far each figure moves: the mascot all the way, a photo 0.75, initials 0.55. */
    fun figureAmp(figure: String?): Double = when (figure) {
        "initials" -> 0.55
        "avatar" -> 0.75
        else -> 1.0
    }

    /** The styles that reveal their own words (typewriter, the text message): no word fade. */
    fun ownsText(style: String): Boolean = style == "typewriter" || style == "imessage"

    // ---------- The card (polish-home.css), as the web wrote it ----------

    val CARD_KEYFRAMES: Map<String, String> = mapOf(
        "cb-greet-in" to "from { opacity: 0; transform: translateY(10px) scale(0.98); } to { opacity: 1; transform: none; }",
        "cb-greet-out" to "from { opacity: 1; transform: none; } to { opacity: 0; transform: translateY(6px) scale(0.98); }",
        "cb-greet-spring" to "0% { opacity: 0; transform: translateY(14px) scale(0.94); } 55% { opacity: 1; transform: translateY(-3px) scale(1.01); } 78% { transform: translateY(1px) scale(0.998); } 100% { opacity: 1; transform: none; }",
        "cb-greet-collapse" to "from { opacity: 1; transform: none; } to { opacity: 0; transform: translateY(16px) scale(0.86); }",
        "cb-mm-word" to "from { opacity: 0; transform: translateY(5px); filter: blur(2px); } to { opacity: 1; transform: none; filter: none; }",
        "cb-mm-dot" to "0%, 100% { transform: scale(1); } 40% { transform: scale(1.35); }",
        "cb-mm-ring" to "from { opacity: 0.55; transform: scale(1); } to { opacity: 0; transform: scale(3.2); }",
    )

    private fun cardTrack(name: String, duration: Double, timing: String, delay: Double = 0.0, fill: String = "forwards"): Track =
        Track(Keyframes.cssKeyframes(CARD_KEYFRAMES.getValue(name), timing), TrackOptions(duration, delay, 1.0, null, fill))

    /**
     * How the card comes in: lively springs up (cb-greet-spring 0.62s), the
     * others as always (cb-greet-in 0.45s). The notification and neon keep their
     * own entrance (the style's rule wins), as does reduced motion (none): null.
     */
    fun cardEnterTrack(mode: String, style: String, reduced: Boolean = false): Track? {
        if (reduced || style == "notification" || style == "neon") return null
        return if (mode == "lively") cardTrack("cb-greet-spring", 620.0, "cubic-bezier(0.2, 0.9, 0.3, 1)")
        else cardTrack("cb-greet-in", 450.0, "cubic-bezier(0.22, 1, 0.36, 1)")
    }

    /**
     * How the card goes: opened from the card with the figure moving, it
     * collapses toward the launcher (cb-greet-collapse 0.24s after 0.04s);
     * otherwise it fades down (cb-greet-out 0.22s). Reduced motion: at once.
     */
    fun cardLeaveTrack(mode: String, reason: String, reduced: Boolean = false): Track? {
        if (reduced) return null
        return if (reason == "open" && mode != "off") cardTrack("cb-greet-collapse", 240.0, "cubic-bezier(0.55, 0, 0.75, 0.2)", 40.0)
        else cardTrack("cb-greet-out", 220.0, "ease-in")
    }

    /** The "Online" dot's ping: the dot swells once (cb-mm-dot 0.5s) and one ring goes out (cb-mm-ring 1.1s). No ring on the notification and postcard. */
    object Ping {
        val dot = cardTrack("cb-mm-dot", 500.0, "cubic-bezier(0.3, 1.6, 0.5, 1)", 0.0, "none")
        val ring = cardTrack("cb-mm-ring", 1100.0, "cubic-bezier(0.2, 0.7, 0.3, 1)")
        fun ringed(style: String) = style != "notification" && style != "postcard"
    }

    /** The words, the step between them (ms) and each word's track. */
    class WordFade(val words: List<String>, val step: Int, val tracks: List<Track>)

    private val WHITESPACE = Regex("\\s+")

    /**
     * The greeting's words fading up (mascotMotion.ts stage.words): each word
     * 0.32s, from 380ms, a step apart that shrinks for a longer message so all
     * of it is up within about a second. Null for fewer than two words (left as
     * it is).
     */
    fun wordFade(text: String?): WordFade? {
        val words = (text ?: "").split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.size < 2) return null
        val step = min(55, floor(520.0 / (words.size - 1)).toInt())
        return WordFade(words, step, words.indices.map { i -> cardTrack("cb-mm-word", 320.0, "cubic-bezier(0.22, 1, 0.36, 1)", 380.0 + i * step, "both") })
    }

    /** How long a voice note "plays" as the card shows: 22 bars, each 1.2s twice, 60ms apart (polish-home.css cb-wave). */
    fun voicePlayMs(bars: Int = 22): Long = (bars - 1) * 60L + 1200L * 2

    /** When a track is over (one lap of a loop). */
    fun endOf(t: Track): Double =
        if (t.options.iterations == Double.POSITIVE_INFINITY) t.options.delay + t.options.duration else Keyframes.tracksEnd(listOf(t))
}

/** What the director asks of the view. Every call is safe without a figure. */
interface MascotStage {
    /** Play a one-off gesture on the figure (replacing one in flight). */
    fun play(g: String, amp: Double, delay: Long = 0)

    /** Start the slow breath, or let it end on its next exhale. */
    fun breathe(on: Boolean)

    /** Talk (a rhythmic squash) while a voice note plays. */
    fun talk(on: Boolean, amp: Double)

    /** Lean toward the pointer: -1 (left) .. 1 (right), 0 upright. */
    fun lean(x: Double, amp: Double)

    /** The "Online" dot's single ping. */
    fun ping(on: Boolean)

    /** The greeting's words fading up one by one. */
    fun words()

    /** Stop everything now, the figure as drawn. */
    fun reset()
}

/** The director's timers (the main thread's in an app, a fake clock in tests). */
interface MascotTimers {
    fun set(ms: Long, fn: () -> Unit): Any
    fun clear(id: Any)
}

/**
 * The web's director (mascotMotion.ts createMascotDirector), line for line.
 * States: hidden, entering, living, settled, destroyed. Hide reasons: open,
 * close, away.
 */
class MascotDirector(
    private val mode: String,
    private val stage: MascotStage,
    private val timers: MascotTimers,
    /** Reduced motion, read each time the card shows. */
    private val reducedOf: () -> Boolean = { false },
    /** How far the figure moves: 1 for a mascot, less for a photo / initials (figureAmp). */
    private val ampOf: () -> Double = { 1.0 },
    /** The style reveals its own text (typewriter, text message): no word fade. */
    private val ownText: () -> Boolean = { false },
    private val random: () -> Double = { Math.random() },
    private val now: () -> Long = { System.currentTimeMillis() },
    /** False once the card has gone: the director stops itself. */
    private val alive: (() -> Boolean)? = null,
) {
    private val live = LinkedHashSet<Any>()

    var state = "hidden"
        private set

    /** Idle gestures played since the card last showed. */
    var gestures = 0
        private set
    private var talking = false
    private var wantTalk = false
    private var reduced = false
    private var amp = 1.0
    private var lastHop = Long.MIN_VALUE
    private var hidden = false
    private var idleTimer: Any? = null
    private var talkTimer: Any? = null

    private val lively = mode == "lively"
    private fun moving() = mode != "off" && !reduced

    private fun after(ms: Long, fn: () -> Unit): Any {
        lateinit var id: Any
        id = timers.set(ms) {
            live.remove(id)
            if (alive != null && !alive.invoke()) {
                destroy()
                return@set
            }
            fn()
        }
        live.add(id)
        return id
    }

    private fun cancel(id: Any?) {
        if (id == null) return
        timers.clear(id)
        live.remove(id)
    }

    private fun cancelAll() {
        live.forEach { timers.clear(it) }
        live.clear()
        idleTimer = null
        talkTimer = null
    }

    private fun startTalking() {
        if (talking) return
        talking = true
        stage.talk(true, if (lively) amp else amp * 0.5)
    }

    private fun stopTalking() {
        if (!talking) return
        talking = false
        stage.talk(false, amp)
    }

    private fun gestureList() = MascotMotion.IDLE_GESTURES.getValue(if (lively) "lively" else "subtle")

    private fun scheduleIdle() {
        cancel(idleTimer)
        idleTimer = null
        if (state != "living" || hidden) return
        if (gestures >= gestureList().size) {
            idleTimer = after(MascotMotion.SETTLE_AFTER) {
                idleTimer = null
                settle()
            }
            return
        }
        idleTimer = after(MascotMotion.IDLE_GAP_MIN + floor(random() * MascotMotion.IDLE_GAP_SPREAD).toLong()) {
            idleTimer = null
            // Talking: the gesture waits for a quiet moment (not counted).
            if (!talking) {
                stage.play(gestureList()[gestures], amp)
                gestures += 1
            }
            scheduleIdle()
        }
    }

    private fun settle() {
        if (state != "living") return
        state = "settled"
        stage.breathe(false)
    }

    private fun goLive() {
        if (state != "entering") return
        state = "living"
        stage.breathe(true)
        if (wantTalk) startTalking()
        scheduleIdle()
    }

    fun show() {
        if (state == "destroyed") return
        cancelAll()
        stage.reset()
        talking = false
        gestures = 0
        lastHop = Long.MIN_VALUE
        reduced = reducedOf()
        amp = max(0.0, min(1.0, ampOf()))
        if (mode == "off") {
            state = "settled"
            return
        }
        if (reduced) {
            // A plain fade, and nothing after it.
            stage.play("fadeIn", 0.0)
            state = "settled"
            return
        }
        state = "entering"
        if (lively) {
            if (!ownText()) stage.words()
            stage.play("pop", amp, MascotMotion.Lively.POP)
            after(MascotMotion.Lively.WAVE) {
                if (wantTalk) startTalking() else stage.play("wave", amp)
            }
            after(MascotMotion.Lively.PING) {
                stage.ping(true)
                after(MascotMotion.PING_MS) { stage.ping(false) }
            }
            after(MascotMotion.Lively.NOD) {
                if (!talking) stage.play("nod", amp)
            }
            after(MascotMotion.Lively.IDLE) { goLive() }
        } else {
            stage.play("rise", amp, MascotMotion.Subtle.RISE)
            after(MascotMotion.Subtle.PING) {
                stage.ping(true)
                after(MascotMotion.PING_MS) { stage.ping(false) }
            }
            after(MascotMotion.Subtle.IDLE) { goLive() }
        }
    }

    fun hide(reason: String = "open") {
        if (state == "destroyed" || state == "hidden") return
        cancelAll()
        val wasMoving = moving()
        stage.reset()
        talking = false
        wantTalk = false
        state = "hidden"
        if (wasMoving && reason != "away") stage.play(if (reason == "close") "bow" else "handoff", if (lively) amp else amp * 0.6)
    }

    fun hover(on: Boolean) {
        if (!moving() || (state != "living" && state != "settled" && state != "entering")) return
        if (!on) {
            stage.lean(0.0, amp)
            return
        }
        if (lively && state != "entering" && !talking && (lastHop == Long.MIN_VALUE || now() - lastHop >= MascotMotion.HOP_GAP)) {
            lastHop = now()
            stage.play("hop", amp)
        }
    }

    fun point(x: Double) {
        if (!moving() || state == "hidden" || state == "destroyed") return
        val clamped = max(-1.0, min(1.0, if (x.isFinite()) x else 0.0))
        stage.lean(clamped, if (lively) amp else amp * 0.6)
    }

    fun talk(on: Boolean) {
        wantTalk = on
        if (!on) {
            cancel(talkTimer)
            talkTimer = null
            stopTalking()
            return
        }
        if (!moving() || state == "hidden" || state == "destroyed") return
        // During the entrance it waits for the pop to land.
        if (state == "entering") return
        startTalking()
    }

    /** Talk for [ms], then stop (a voice note of known length). */
    fun talkFor(ms: Long) {
        if (!moving() || state == "hidden" || state == "destroyed") return
        talk(true)
        cancel(talkTimer)
        talkTimer = after(max(0L, ms)) {
            talkTimer = null
            talk(false)
        }
    }

    /** The app went to the background / came back. */
    fun pageHidden(isHidden: Boolean) {
        if (hidden == isHidden) return
        hidden = isHidden
        if (state != "living") return
        if (hidden) {
            cancel(idleTimer)
            idleTimer = null
        } else scheduleIdle()
    }

    fun destroy() {
        if (state == "destroyed") return
        cancelAll()
        stage.reset()
        talking = false
        state = "destroyed"
    }

    /** Timers waiting (tests: none may outlive a hide / settle / destroy). */
    fun pending(): Int = live.size
}
