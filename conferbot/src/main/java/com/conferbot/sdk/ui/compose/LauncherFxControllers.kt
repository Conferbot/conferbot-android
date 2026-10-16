package com.conferbot.sdk.ui.compose

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.conferbot.sdk.core.customization.EntrancePlan
import com.conferbot.sdk.core.customization.EntranceStage
import com.conferbot.sdk.core.customization.FxSprite
import com.conferbot.sdk.core.customization.FxValues
import com.conferbot.sdk.core.customization.Keyframes
import com.conferbot.sdk.core.customization.LauncherAttention
import com.conferbot.sdk.core.customization.LauncherEntrance
import com.conferbot.sdk.core.customization.LauncherIdleCall
import com.conferbot.sdk.core.customization.LauncherMotion
import com.conferbot.sdk.core.customization.Track
import org.json.JSONObject
import kotlin.math.ceil

/*
 * The rules round the launcher's entrance, attention loop and idle call
 * (core/customization/LauncherEntrance.kt, LauncherAttention.kt,
 * LauncherIdleCall.kt), as the React Native SDK's hooks keep them
 * (FloatingWidget/useLauncherEntrance.ts, useLauncherAttention.ts,
 * useLauncherIdleCall.ts). Each controller owns its clock and timers on an
 * [FxScheduler] (a fake one in tests) and reports what to draw at a time;
 * the launcher (ConferBotWidget) feeds them the chat's state and draws.
 * Plain Kotlin: nothing here touches Compose.
 */

/** A cancellable timer. */
interface FxTimer {
    fun cancel()
}

/** The clock and timers the controllers run on. */
interface FxScheduler {
    /** Milliseconds since the epoch. */
    fun now(): Long
    fun after(ms: Long, fn: () -> Unit): FxTimer
}

/** The wall clock and main-thread timers. */
object RealFxScheduler : FxScheduler {
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    override fun now(): Long = System.currentTimeMillis()
    override fun after(ms: Long, fn: () -> Unit): FxTimer {
        val r = Runnable { fn() }
        handler.postDelayed(r, if (ms < 0) 0 else ms)
        return object : FxTimer {
            override fun cancel() = handler.removeCallbacks(r)
        }
    }
}

/** What the launcher remembers for the app session (the web's sessionStorage). */
object LauncherFxSession {
    /** When the chat was last opened (0: not yet). Idle moments, attention and calls rest after it. */
    var openedAt: Long
        get() = LauncherOpened.at
        set(v) { LauncherOpened.at = v }

    /** The entrance each bot played this session (a different one plays once more). */
    val entrancePlayed = HashMap<String, String>()

    /** The calls each bot made this session. */
    val visits = HashMap<String, LauncherIdleCall.VisitState>()

    /** The clock the launcher's controllers run on (a test can set its own). */
    @Volatile var scheduler: FxScheduler = RealFxScheduler

    fun openedRecently(now: Long) = openedAt != 0L && now - openedAt < LauncherMotion.OPENED_REST_MS

    internal fun reset() {
        openedAt = 0
        entrancePlayed.clear()
        visits.clear()
    }
}

/** What the launcher and the greeting look like at a moment of the entrance. */
data class EntranceLook(
    /** The launcher's opacity and transform (about its centre), and how much of it shows from the top (the ufo's beam: 1 = all). */
    val o: Double = 1.0,
    val tx: Double = 0.0,
    val ty: Double = 0.0,
    val r: Double = 0.0,
    val sx: Double = 1.0,
    val sy: Double = 1.0,
    val shown: Double = 1.0,
    /** The greeting card's opacity and drop. */
    val greetingO: Double = 1.0,
    val greetingTy: Double = 0.0,
)

/**
 * How the launcher arrives: decided when the bot's settings arrive, once
 * per app session per bot and entrance, never over an open chat or with
 * reduced motion. The launcher is held invisible until the effect reveals
 * it; the greeting stays hidden until it has landed, then fades up 150ms
 * later. Opening the chat ends it at once. [busy] (it has not landed yet)
 * holds the attention loop and the idle call.
 */
class LauncherEntranceController(private val clock: FxScheduler = LauncherFxSession.scheduler) {
    /** Called whenever what it draws changes (not every frame). */
    var onChange: (() -> Unit)? = null

    /** none, hold, play, done. */
    var phase = "none"
        private set
    var plan: EntrancePlan? = null
        private set
    var busy = false
        private set
    private var startedAt = 0L
    private var decided = false
    private var laying = false
    private val timers = mutableListOf<FxTimer>()
    private var mode = "classic"
    private var botId = ""

    /** Once, when the settings arrive. */
    fun decide(mode: String, botId: String, reduced: Boolean, chatOpen: Boolean) {
        if (decided) return
        decided = true
        this.mode = mode
        this.botId = botId.ifEmpty { "bot" }
        if (!LauncherEntrance.shouldPlay(mode, reduced, chatOpen, LauncherFxSession.entrancePlayed[this.botId] == mode)) return
        phase = "hold"
        busy = true
        timers.add(clock.after(REVEAL_FAILSAFE_MS) { if (phase == "hold") finish() })
        onChange?.invoke()
    }

    /** The launcher is laid out (its size and place): the stage, the effect, the phases. */
    fun laidOut(stage: () -> EntranceStage) {
        if (phase != "hold" || laying) return
        laying = true
        timers.add(clock.after(LauncherEntrance.START_MS) {
            if (phase != "hold") return@after
            val p = LauncherEntrance.plan(mode, stage()) ?: return@after finish()
            LauncherFxSession.entrancePlayed[botId] = mode
            val tl = LauncherEntrance.timeline(p)
            plan = p
            phase = "play"
            startedAt = clock.now()
            timers.add(clock.after(ceil(tl.endAt).toLong()) { finish() })
            timers.add(clock.after(ceil(tl.settleAt).toLong()) {
                busy = false
                onChange?.invoke()
            })
            onChange?.invoke()
        })
    }

    /** Opening the chat by any route ends it at once. */
    fun chatOpened() {
        if (phase == "hold" || phase == "play") finish()
    }

    fun finish() {
        timers.forEach { it.cancel() }
        timers.clear()
        val was = phase
        busy = false
        phase = "done"
        if (was != "done") onChange?.invoke()
    }

    val active get() = phase == "hold" || phase == "play"

    /** Milliseconds into the effect. */
    val elapsed: Double get() = if (phase == "play") (clock.now() - startedAt).toDouble() else 0.0

    /** The launcher and the card at [t] ms (null: nothing to change). */
    fun lookAt(t: Double): EntranceLook? {
        if (phase == "hold") return EntranceLook(o = 0.0, greetingO = 0.0)
        val p = plan
        if (phase != "play" || p == null) return null
        val tl = LauncherEntrance.timeline(p)
        val settle = Keyframes.evaluate(listOf(LauncherEntrance.SETTLE_REVEAL.withDelay(tl.settleAt)), t)
        if (t < p.revealAt) return EntranceLook(o = 0.0, greetingO = settle.o, greetingTy = settle.ty)
        val arrive = p.arrive.withDelay(p.revealAt)
        val v = Keyframes.evaluate(listOf(arrive), t)
        val f = LauncherEntrance.spriteFrame(FxSprite(x = 0.0, y = 0.0, w = 0.0, h = 0.0, tracks = listOf(arrive)), t, v)
        return EntranceLook(o = f.o, tx = f.tx, ty = f.ty, r = f.r, sx = f.sx, sy = f.sy, shown = 1 - v.clip / 100, greetingO = settle.o, greetingTy = settle.ty)
    }

    fun dispose() {
        timers.forEach { it.cancel() }
        timers.clear()
        onChange = null
    }

    companion object {
        /** If the effect has not started by then, the launcher simply appears. */
        const val REVEAL_FAILSAFE_MS = 6000L
    }
}

/**
 * The opt-in "look here" loop: the first beat 1.5s after the launcher (or
 * the greeting card) shows, then a beat and stillness per lap while the
 * chat stays closed; paused while the launcher plays an idle or message
 * moment; not while an entrance lands; over once the chat is opened (and
 * not started when it was opened in the last 30 minutes); nothing with
 * reduced motion.
 */
class LauncherAttentionController(private val clock: FxScheduler = LauncherFxSession.scheduler) {
    var onChange: (() -> Unit)? = null

    var mode = "off"
        private set
    private var enabled = false
    private var reduced = false
    private var cardShown = false
    private var moment = false
    private var entranceBusy = false
    private var hasSize = false
    private var wide = false
    private var over = LauncherFxSession.openedRecently(clock.now())
    private var tracks: List<Track> = emptyList()

    // The clock: ms run so far, and since when it has been running (null: stopped or paused).
    private var ran = 0.0
    private var since: Long? = null

    val target get() = LauncherAttention.target(mode)
    val active get() = enabled && target != null && !reduced && !over

    /** Moving the launcher: its idle moments hold off and the load ripples give way. */
    val onLauncher get() = active && target == "launcher"
    val running get() = active && !entranceBusy && hasSize && (target == "launcher" || cardShown)

    fun update(mode: String, enabled: Boolean, reduced: Boolean, chatOpen: Boolean, cardShown: Boolean, moment: Boolean, entranceBusy: Boolean, hasSize: Boolean, wide: Boolean) {
        val wasRunning = running
        val wasActive = active
        val changed = mode != this.mode || wide != this.wide
        if (changed) {
            this.mode = mode
            this.wide = wide
            tracks = LauncherAttention.tracks(mode, false, wide)
        }
        this.enabled = enabled
        this.reduced = reduced
        if (chatOpen) over = true
        this.cardShown = cardShown
        this.entranceBusy = entranceBusy
        this.hasSize = hasSize
        val now = clock.now()
        if (running && (!wasRunning || changed)) {
            // Run (again, from the start: the card showing again restarts it).
            ran = 0.0
            since = if (moment) null else now
        } else if (!running && wasRunning) {
            ran = 0.0
            since = null
        } else if (running && moment != this.moment) {
            // A launcher moment (idle / message) pauses it.
            val s = since
            if (moment && s != null) {
                ran += now - s
                since = null
            } else if (!moment && s == null) {
                since = now
            }
        }
        this.moment = moment
        if (running != wasRunning || active != wasActive) onChange?.invoke()
    }

    /** Milliseconds on the loop's clock: the intro, then lap after lap. */
    val elapsed: Double
        get() {
            if (!running || tracks.isEmpty()) return 0.0
            val t = ran + (since?.let { clock.now() - it } ?: 0L)
            val intro = tracks.maxOf { it.options.delay }
            val period = tracks.first().options.duration
            return if (t < intro) t else intro + (t - intro) % period
        }

    /** A part's values at [t] ms (null when the mode has no such part or it is not running). */
    fun part(name: String, t: Double): FxValues? {
        if (!running) return null
        val tr = tracks.firstOrNull { it.part == name } ?: return null
        return Keyframes.evaluate(listOf(tr), t, LauncherAttention.BASE[name])
    }

    fun has(name: String) = running && tracks.any { it.part == name }
}

/** Where the 3-day rest is kept. */
interface IdleCallRestStore {
    fun read(botId: String): Long
    fun write(botId: String, until: Long)
}

/** The rest in the app's SharedPreferences, per bot, as `{"until": ms}`. */
class PrefsIdleCallRestStore(private val prefs: SharedPreferences?) : IdleCallRestStore {
    override fun read(botId: String): Long = try {
        prefs?.getString(key(botId), null)?.let { JSONObject(it).optLong("until", 0L) } ?: 0L
    } catch (_: Exception) {
        0L
    }

    override fun write(botId: String, until: Long) {
        try {
            prefs?.edit()?.putString(key(botId), JSONObject().put("until", until).toString())?.apply()
        } catch (_: Exception) {
            // nothing kept without storage
        }
    }

    companion object {
        fun key(botId: String) = "conferbot_idlecall_rest_${botId.ifEmpty { "default" }}"
    }
}

/**
 * The idle call: after the owner's delay, if the visitor has not opened the
 * chat, the mascot slips out from behind the launcher and calls them over,
 * with a speech bubble that opens the chat. One call per screen, two per
 * app session 3 minutes apart, then 3 days' rest (also after an opening);
 * closing the greeting means none for the session. A call waits while the
 * chat is open, the app is in the background, the keyboard is up, an
 * entrance lands, or the greeting has had less than 15s alone.
 */
class LauncherIdleCallController(
    private val clock: FxScheduler = LauncherFxSession.scheduler,
    private val store: IdleCallRestStore,
) {
    var onChange: (() -> Unit)? = null

    // What the launcher reports.
    var chatOpen = false
    var appActive = true
    var keyboard = false
    var entranceBusy = false
    var width: Double? = null
    var height: Double? = null
    var position = "right"
    private var cardSince = 0L
    var cardShown = false
        set(v) {
            if (v && !field) cardSince = clock.now()
            if (!v) cardSince = 0
            field = v
        }

    var perf: LauncherIdleCall.Performance? = null
        private set
    private var startedAt = 0L
    private var started = false
    private var destroyed = false
    private var playing = false
    private var mode = "off"
    private var botId = ""
    private var restUntil = 0L
    private val timers = mutableListOf<FxTimer>()

    private fun later(ms: Long, fn: () -> Unit) { timers.add(clock.after(ms, fn)) }

    private val visit get() = LauncherFxSession.visits[botId] ?: LauncherIdleCall.VisitState()
    private fun allowed() = !LauncherFxSession.openedRecently(clock.now()) && LauncherIdleCall.callAllowed(visit, restUntil, clock.now())
    private fun blocked(): Boolean {
        val greetingHasTheFloor = cardShown && clock.now() - cardSince < LauncherIdleCall.GREETING_FIRST_MS
        return chatOpen || !appActive || keyboard || greetingHasTheFloor || entranceBusy || width == null || height == null
    }

    /** Once, when the settings arrive. */
    fun start(mode: String, delay: Int, botId: String, reduced: Boolean) {
        if (started) return
        started = true
        this.mode = mode
        this.botId = botId
        if (mode == "off" || reduced) return
        restUntil = store.read(botId)
        if (destroyed || !allowed()) return
        later(delay * 1000L) { attempt() }
    }

    private fun attempt() {
        if (destroyed) return
        if (!allowed()) return destroy()
        if (playing || blocked()) return later(LauncherIdleCall.RETRY_MS) { attempt() }
        val now = clock.now()
        val (next, rest) = LauncherIdleCall.afterCall(visit, now)
        LauncherFxSession.visits[botId] = next
        // After the last call this session, rest for a few days.
        if (rest) store.write(botId, now + LauncherIdleCall.REST_MS)
        val p = LauncherIdleCall.performance(mode, LauncherIdleCall.geometry(width!!, height!!, position, cardShown))
        playing = true
        perf = p
        startedAt = now
        later(ceil(p.total).toLong() + 80) { stop() }
        onChange?.invoke()
    }

    /** The call over (or cut short by a tap on its bubble). */
    fun stop() {
        playing = false
        if (perf != null) {
            perf = null
            onChange?.invoke()
        }
    }

    private fun destroy() {
        destroyed = true
        timers.forEach { it.cancel() }
        timers.clear()
        stop()
    }

    /** Opening the chat: the call ends, and they are left alone for a few days. */
    fun chatOpened() {
        chatOpen = true
        if (destroyed || mode == "off" || !started) return
        store.write(botId, clock.now() + LauncherIdleCall.REST_MS)
        destroy()
    }

    /** The greeting was closed ("not now"): no more calls this session. */
    fun greetingClosed() {
        if (mode == "off") return
        LauncherFxSession.visits[botId] = visit.copy(quiet = true)
        destroy()
    }

    /** Milliseconds into the call. */
    val elapsed: Double get() = if (perf == null) 0.0 else (clock.now() - startedAt).toDouble()

    fun dispose() {
        destroyed = true
        timers.forEach { it.cancel() }
        timers.clear()
        onChange = null
    }
}
