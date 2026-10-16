package com.conferbot.sdk.ui.compose.mascot

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.conferbot.sdk.core.customization.FxValues
import com.conferbot.sdk.core.customization.Keyframes
import com.conferbot.sdk.core.customization.MascotDirector
import com.conferbot.sdk.core.customization.MascotMotion
import com.conferbot.sdk.core.customization.MascotStage
import com.conferbot.sdk.core.customization.MascotTimers
import com.conferbot.sdk.core.customization.Track
import kotlin.math.cos
import kotlin.math.sin

/** The director's timers on the main thread. */
internal class MainMascotTimers : MascotTimers {
    private val handler = Handler(Looper.getMainLooper())
    override fun set(ms: Long, fn: () -> Unit): Any = Runnable { fn() }.also { handler.postDelayed(it, if (ms < 0) 0 else ms) }
    override fun clear(id: Any) = handler.removeCallbacks(id as Runnable)
}

/** A track playing from `at` (ms on the card's clock). */
internal class Playing(val track: Track, val at: Long, val until: Long? = null) {
    fun values(t: Long, base: Map<String, Double>? = null): FxValues {
        val x = (t - at).toDouble()
        val lap = if (track.options.iterations == Double.POSITIVE_INFINITY && until != null) minOf(x, (until - at).toDouble() - 0.001) else x
        return Keyframes.evaluate(listOf(track), lap, base)
    }

    fun over(t: Long): Boolean = until?.let { t >= it } ?: (track.options.iterations != Double.POSITIVE_INFINITY && t - at >= Keyframes.tracksEnd(listOf(track)))
}

/** A box moved by `v` in graphicsLayer: a CSS transform list's own order ("rts": the translation turned with it), px as dp. */
internal fun GraphicsLayerScope.move(v: FxValues, order: String, origin: TransformOrigin = TransformOrigin.Center) {
    var a = 0.0
    var sx = 1.0
    var sy = 1.0
    var tx = 0.0
    var ty = 0.0
    for (op in order) {
        when (op) {
            // The lists rotate before they scale: a translation after them is turned and scaled with them.
            't' -> {
                val x = v.tx * sx
                val y = v.ty * sy
                val r = Math.toRadians(a)
                tx += x * cos(r) - y * sin(r)
                ty += x * sin(r) + y * cos(r)
            }
            'r' -> a += v.r
            's' -> {
                sx *= v.sx
                sy *= v.sy
            }
        }
    }
    transformOrigin = origin
    translationX = tx.toFloat() * density
    translationY = ty.toFloat() * density
    rotationZ = a.toFloat()
    scaleX = sx.toFloat()
    scaleY = sy.toFloat()
    alpha = v.o.toFloat().coerceIn(0f, 1f)
}

/**
 * The greeting card alive (RN useGreetingMotion.ts on core MascotMotion):
 * the web's director on this card, its stage on one frame clock. The card
 * springs up (lively) or comes in as always; the figure pops (or rises),
 * waves, nods, breathes and gives at most three small gestures, then stands
 * still; the "Online" dot pings once; the words fade up. When the card goes
 * ([leave]) the figure hands off toward the launcher (opened) or bows (the
 * x) as the card collapses or fades. A voice note talks while its waveform
 * plays. Nothing while the app is in the background; reduced motion: a fade.
 * Every value is read in a graphicsLayer (redraws, no recomposition).
 */
@Stable
internal class GreetingMotion(
    val mode: String,
    val style: String,
    val text: String,
    /** mascot, avatar or null (none on the card). */
    val figure: String?,
    val reduced: Boolean,
) : MascotStage {
    /** When the card showed (System.nanoTime in ms, the frame clock's base). */
    val origin = System.nanoTime() / 1_000_000
    private fun clock(): Long = System.nanoTime() / 1_000_000 - origin

    /** The card's clock (ms since it showed), advanced on frames while something moves. */
    var time by mutableLongStateOf(0L)
    var ticking by mutableStateOf(true)

    val amp = MascotMotion.figureAmp(figure)
    private val moving = mode != "off" && !reduced
    val enter: Track? = MascotMotion.cardEnterTrack(mode, style, reduced)
    var leave by mutableStateOf<Playing?>(null)
        private set

    // The figure's first move, held from the first frame so it never shows before its pop.
    var gesture by mutableStateOf(
        if (moving && figure != null) Playing(if (mode == "lively") MascotMotion.gestureTrack("pop", amp, 140.0) else MascotMotion.gestureTrack("rise", amp, 80.0), 0L) else null,
    )
        private set
    var breath by mutableStateOf<Playing?>(null)
        private set
    var talk by mutableStateOf<Playing?>(null)
        private set
    var ping by mutableStateOf<Long?>(null)
        private set
    var wordsAt by mutableStateOf<Long?>(null)
        private set
    val words: MascotMotion.WordFade? = MascotMotion.wordFade(text)

    /** Where the launcher's centre is from the figure's (the hand-off), when known (dp). */
    var toward: (() -> Pair<Double, Double>?)? = null

    val director = MascotDirector(
        mode = mode,
        stage = this,
        timers = MainMascotTimers(),
        reducedOf = { reduced },
        ampOf = { MascotMotion.figureAmp(figure) },
        ownText = { MascotMotion.ownsText(style) },
    )

    private fun wake() {
        time = clock()
        ticking = true
    }

    override fun play(g: String, amp: Double, delay: Long) {
        if (figure == null) return
        val tr = MascotMotion.gestureTrack(g, amp, delay.toDouble(), if (g == "handoff") toward?.invoke() else null)
        talk = null
        gesture = Playing(tr, clock())
        wake()
    }

    override fun breathe(on: Boolean) {
        val now = clock()
        if (on) breath = Playing(MascotMotion.BREATH, now)
        else breath?.let { b -> breath = Playing(b.track, b.at, lapEnd(b, now)) }
        wake()
    }

    override fun talk(on: Boolean, amp: Double) {
        val now = clock()
        if (on) {
            gesture = null
            talk = Playing(MascotMotion.talkTrack(amp), now)
        } else talk?.let { k -> talk = Playing(k.track, k.at, lapEnd(k, now)) }
        wake()
    }

    /** A loop runs on to the end of its lap (an exhale, a syllable), then stops there. */
    private fun lapEnd(p: Playing, now: Long): Long {
        val d = p.track.options.duration.toLong()
        val laps = (now - p.at) / d + 1
        return p.at + laps * d
    }

    // A touch screen has no pointer to lean toward.
    override fun lean(x: Double, amp: Double) {}

    override fun ping(on: Boolean) {
        if (!on) return
        ping = clock()
        wake()
    }

    override fun words() {
        if (words == null) return
        wordsAt = clock()
        wake()
    }

    override fun reset() {
        gesture = null
        breath = null
        talk = null
        ping = null
    }

    /** The card is going: the director's hand-off or bow, the card's collapse or fade. When it is all over (ms from now). */
    fun leave(reason: String): Long {
        director.hide(reason)
        val now = clock()
        val track = MascotMotion.cardLeaveTrack(mode, reason, reduced)
        leave = track?.let { Playing(it, now) }
        wake()
        val figureEnd = if (moving && figure != null) (if (reason == "close") 300L else 280L) else 0L
        val cardEnd = track?.let { MascotMotion.endOf(it).toLong() } ?: 0L
        return maxOf(figureEnd, cardEnd) + 16
    }

    /** Anything still moving at [t] (else the clock stops until the next move). */
    fun active(t: Long): Boolean {
        if (enter != null && t < MascotMotion.endOf(enter)) return true
        if (leave?.over(t) == false) return true
        if (gesture?.over(t) == false || breath?.over(t) == false || talk?.over(t) == false) return true
        if (ping?.let { t - it < MascotMotion.endOf(MascotMotion.Ping.ring) } == true) return true
        if (wordsAt?.let { at -> t - at < Keyframes.tracksEnd(words!!.tracks) } == true) return true
        return director.state == "entering"
    }

    // ---- What each piece reads ----

    fun cardValues(t: Long): FxValues? {
        leave?.let { return it.values(t) }
        return enter?.let { Keyframes.evaluate(listOf(it), t.toDouble()) }
    }

    val cardOrder: String get() = (leave?.track ?: enter)?.let { Keyframes.transformOrder(it) } ?: "trs"
}

/** The card's own moves (its spring or plain entrance; its collapse or fade), about its centre. */
internal fun Modifier.greetingCardMotion(m: GreetingMotion): Modifier = graphicsLayer {
    val t = m.time
    val v = m.cardValues(t) ?: return@graphicsLayer
    move(v, m.cardOrder)
}

/** The figure's breath (outermost), gesture and talk, each about its feet. */
internal fun Modifier.figureBreath(m: GreetingMotion): Modifier = graphicsLayer {
    val b = m.breath ?: return@graphicsLayer
    move(b.values(m.time), "trs", TransformOrigin(0.5f, 1f))
}

internal fun Modifier.figureGesture(m: GreetingMotion): Modifier = graphicsLayer {
    val g = m.gesture ?: return@graphicsLayer
    move(g.values(m.time), Keyframes.transformOrder(g.track), TransformOrigin(0.5f, 1f))
}

internal fun Modifier.figureTalk(m: GreetingMotion): Modifier = graphicsLayer {
    val k = m.talk ?: return@graphicsLayer
    move(k.values(m.time), "trs", TransformOrigin(0.5f, 1f))
}

/** The "Online" dot's swell (dot) or its ring. */
internal fun Modifier.pingMotion(m: GreetingMotion, ring: Boolean): Modifier = graphicsLayer {
    val at = m.ping
    if (at == null) {
        if (ring) alpha = 0f
        return@graphicsLayer
    }
    val track = if (ring) MascotMotion.Ping.ring else MascotMotion.Ping.dot
    move(Keyframes.evaluate(listOf(track), (m.time - at).toDouble()), "trs")
    if (ring && m.time - at >= MascotMotion.endOf(track)) alpha = 0f
}

/** Word [i] fading up. */
internal fun Modifier.wordMotion(m: GreetingMotion, i: Int): Modifier = graphicsLayer {
    val at = m.wordsAt ?: return@graphicsLayer
    val tr = m.words?.tracks?.getOrNull(i) ?: return@graphicsLayer
    move(Keyframes.evaluate(listOf(tr), (m.time - at).toDouble()), "trs")
}

/**
 * The card's motion for one show: the director started, the frame clock run
 * while anything moves, the app's background seen, a voice note talking.
 * [leaving] (open / close) plays the leaving moves, then [onGone].
 */
@Composable
internal fun rememberGreetingMotion(
    mode: String,
    style: String,
    text: String,
    figure: String?,
    reduced: Boolean,
    leaving: String?,
    onGone: () -> Unit,
): GreetingMotion {
    val m = remember { GreetingMotion(mode, style, text, figure, reduced) }
    val gone by rememberUpdatedState(onGone)
    DisposableEffect(m) {
        m.director.show()
        if (style == "voice" && !reduced) m.director.talkFor(MascotMotion.voicePlayMs())
        onDispose { m.director.destroy() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, m) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) m.director.pageHidden(true)
            if (e == Lifecycle.Event.ON_START) m.director.pageHidden(false)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(m, m.ticking) {
        if (!m.ticking) return@LaunchedEffect
        while (true) {
            val t = maxOf(m.time, withFrameNanos { it / 1_000_000 } - m.origin)
            m.time = t
            if (!m.active(t)) {
                m.ticking = false
                break
            }
        }
    }
    LaunchedEffect(m, leaving) {
        if (leaving == null) return@LaunchedEffect
        val end = m.leave(leaving)
        kotlinx.coroutines.delay(end)
        gone()
    }
    return m
}
