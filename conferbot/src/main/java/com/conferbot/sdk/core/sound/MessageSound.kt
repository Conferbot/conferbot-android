package com.conferbot.sdk.core.sound

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The chat's sounds, as the web widget plays them. Ported from the React
 * Native SDK's src/core/messageSound.ts (conferbot-widget
 * src/ts/chat/messageSound.ts soundRecipe, packRecipe, MIN_GAP_MS,
 * PACK_GAP_MS; the launcher sound in src/index.ts _playToggleSound; and
 * src/ts/layout/ownerLayout.ts normalizeMessageSound).
 *
 *   messageSound   "off" (default) | "pop" | "chime" | "bubble": a message
 *                  arrived (the bot's step drawn, an agent's message)
 *   soundPack      "off" (default) | "soft" | "playful" | "retro" |
 *                  "bubbly" | "premium": open, message, send and close as a
 *                  set; a pack replaces the single message sound and the
 *                  launcher sound, so nothing doubles up
 *   launcher sound enableWidgetToggleSound with checkedSoundTab "choose"
 *                  (widgetToggleSound) or "upload" (customWidgetSoundUrl):
 *                  an audio file played when the chat opens or closes
 *
 * The visitor's "Sound off" in the chat menu silences all of them (kept
 * under the web's key "conferbot-sound-enabled" = "false").
 *
 * The web synthesises each tone with Web Audio; [renderSamples] computes the
 * same oscillator steps into 16-bit mono PCM (and [renderWav] a WAV file),
 * so no sound file ships. Pure: no audio, storage or clock of its own.
 */
object MessageSound {

    enum class Wave { SINE, TRIANGLE, SQUARE }

    enum class Event(val key: String) { OPEN("open"), MESSAGE("message"), SEND("send"), CLOSE("close") }

    /** One oscillator: when it starts, its glide, length, wave and peak gain. */
    data class ToneStep(val at: Double, val from: Double, val to: Double, val dur: Double, val type: Wave, val gain: Double)

    /** What to play for a moment: a tone (its cache key and notes) or a file. */
    sealed class Cue {
        data class Tone(val key: String, val steps: List<ToneStep>) : Cue()
        data class Url(val url: String) : Cue()
    }

    val MESSAGE_SOUNDS = listOf("off", "pop", "chime", "bubble")
    val SOUND_PACKS = listOf("off", "soft", "playful", "retro", "bubbly", "premium")

    /** The visitor's switch, under the web's own key. */
    const val SOUND_ENABLED_KEY = "conferbot-sound-enabled"

    /** At most one message sound this often. */
    const val MIN_GAP_MS = 800L

    /** A pack's own moments can follow each other quickly; each has its own gap. */
    val PACK_GAP_MS: Map<Event, Long> = mapOf(Event.OPEN to 400L, Event.MESSAGE to 800L, Event.SEND to 250L, Event.CLOSE to 400L)

    fun normalizeMessageSound(v: Any?): String = (v as? String)?.takeIf { it in MESSAGE_SOUNDS } ?: "off"

    fun normalizeSoundPack(v: Any?): String = (v as? String)?.takeIf { it in SOUND_PACKS } ?: "off"

    /** True when a pack (not "off") is chosen. */
    fun soundPackOn(c: Map<String, Any?>?): Boolean = normalizeSoundPack(c?.get("soundPack")) != "off"

    private fun note(at: Double, from: Double, dur: Double, type: Wave, gain: Double, to: Double = from) = ToneStep(at, from, to, dur, type, gain)

    private fun run(freqs: List<Double>, step: Double, dur: Double, type: Wave, gain: Double) =
        freqs.mapIndexed { i, f -> note(i * step, f, dur, type, gain) }

    /** The notes of each message sound. */
    fun soundRecipe(kind: Any?): List<ToneStep> = when (normalizeMessageSound(kind)) {
        "pop" -> listOf(ToneStep(0.0, 520.0, 880.0, 0.09, Wave.SINE, 0.18))
        "chime" -> listOf(
            ToneStep(0.0, 880.0, 880.0, 0.18, Wave.TRIANGLE, 0.12),
            ToneStep(0.09, 1318.5, 1318.5, 0.22, Wave.TRIANGLE, 0.1)
        )
        "bubble" -> listOf(
            ToneStep(0.0, 380.0, 760.0, 0.07, Wave.SINE, 0.16),
            ToneStep(0.08, 520.0, 1040.0, 0.07, Wave.SINE, 0.12)
        )
        else -> emptyList()
    }

    // C5 E5 G5 C6, and the rest of the notes the packs use.
    private const val C5 = 523.25
    private const val E5 = 659.25
    private const val G5 = 783.99
    private const val C6 = 1046.5
    private const val E6 = 1318.5
    private const val G6 = 1568.0
    private const val B5 = 987.77
    private const val A4 = 440.0
    private const val E4 = 329.63

    private val PACKS: Map<String, Map<Event, List<ToneStep>>> = mapOf(
        // Soft: low, rounded sines - barely there.
        "soft" to mapOf(
            Event.OPEN to listOf(note(0.0, A4, 0.16, Wave.SINE, 0.07, E5), note(0.08, E5, 0.16, Wave.SINE, 0.05)),
            Event.MESSAGE to listOf(note(0.0, E5, 0.16, Wave.SINE, 0.08), note(0.07, A4 * 2, 0.2, Wave.SINE, 0.05)),
            Event.SEND to listOf(note(0.0, C5, 0.08, Wave.SINE, 0.05, G5)),
            Event.CLOSE to listOf(note(0.0, E5, 0.16, Wave.SINE, 0.06, A4))
        ),
        // Playful: a bright arpeggio and a boing.
        "playful" to mapOf(
            Event.OPEN to run(listOf(C5, E5, G5), 0.065, 0.09, Wave.TRIANGLE, 0.1),
            Event.MESSAGE to listOf(note(0.0, 300.0, 0.14, Wave.SINE, 0.14, 900.0), note(0.13, 900.0, 0.06, Wave.SINE, 0.06)),
            Event.SEND to listOf(note(0.0, 600.0, 0.06, Wave.SINE, 0.12, 1050.0)),
            Event.CLOSE to run(listOf(G5, E5, C5), 0.06, 0.08, Wave.TRIANGLE, 0.08)
        ),
        // Retro: square-wave chiptune - a power-up and a coin.
        "retro" to mapOf(
            Event.OPEN to run(listOf(E4, A4, E5, A4 * 2), 0.045, 0.05, Wave.SQUARE, 0.045),
            Event.MESSAGE to listOf(note(0.0, B5, 0.07, Wave.SQUARE, 0.05), note(0.07, E6, 0.16, Wave.SQUARE, 0.045)),
            Event.SEND to listOf(note(0.0, 660.0, 0.04, Wave.SQUARE, 0.04)),
            Event.CLOSE to run(listOf(A4 * 2, E5, A4), 0.05, 0.05, Wave.SQUARE, 0.04)
        ),
        // Bubbly: rising bubbles.
        "bubbly" to mapOf(
            Event.OPEN to listOf(note(0.0, 400.0, 0.06, Wave.SINE, 0.13, 800.0), note(0.07, 500.0, 0.06, Wave.SINE, 0.11, 1000.0), note(0.14, 650.0, 0.07, Wave.SINE, 0.09, 1300.0)),
            Event.MESSAGE to listOf(note(0.0, 380.0, 0.07, Wave.SINE, 0.16, 760.0), note(0.08, 520.0, 0.07, Wave.SINE, 0.12, 1040.0)),
            Event.SEND to listOf(note(0.0, 700.0, 0.05, Wave.SINE, 0.1, 1400.0)),
            Event.CLOSE to listOf(note(0.0, 800.0, 0.09, Wave.SINE, 0.1, 400.0))
        ),
        // Premium: a soft bell - a fundamental and its octave, long and low.
        "premium" to mapOf(
            Event.OPEN to listOf(note(0.0, C5, 0.42, Wave.SINE, 0.07), note(0.0, C6, 0.34, Wave.SINE, 0.03), note(0.1, G5, 0.36, Wave.SINE, 0.04)),
            Event.MESSAGE to listOf(note(0.0, C6, 0.32, Wave.TRIANGLE, 0.07), note(0.09, G6, 0.28, Wave.SINE, 0.035)),
            Event.SEND to listOf(note(0.0, A4 * 2, 0.1, Wave.SINE, 0.045)),
            Event.CLOSE to listOf(note(0.0, G5, 0.26, Wave.SINE, 0.05, C5))
        )
    )

    /** The notes of `pack` for `event`. */
    fun packRecipe(pack: Any?, event: Event): List<ToneStep> {
        val p = normalizeSoundPack(pack)
        return if (p == "off") emptyList() else PACKS[p]?.get(event).orEmpty()
    }

    private val HTTP = Regex("^https?://", RegexOption.IGNORE_CASE)

    /** The launcher sound's file, or "" (none, or a pack plays open and close instead). */
    fun toggleSoundUrl(c: Map<String, Any?>?): String {
        if (c == null || !truthy(c["enableWidgetToggleSound"]) || soundPackOn(c)) return ""
        val tab = (c["checkedSoundTab"] as? String)?.takeIf { it.isNotEmpty() } ?: "choose"
        val url = when (tab) {
            "choose" -> c["widgetToggleSound"]
            "upload" -> c["customWidgetSoundUrl"]
            else -> null
        }
        val u = (url as? String)?.trim().orEmpty()
        return if (HTTP.containsMatchIn(u)) u else ""
    }

    private fun truthy(v: Any?): Boolean = when (v) {
        null, false -> false
        is Number -> v.toDouble() != 0.0 && !v.toDouble().isNaN()
        is String -> v.isNotEmpty()
        else -> true
    }

    /** Whether the bot has any sound at all (the chat menu offers Sound on / off only then). */
    fun hasAnySound(c: Map<String, Any?>?): Boolean =
        soundPackOn(c) || normalizeMessageSound(c?.get("messageSound")) != "off" || toggleSoundUrl(c).isNotEmpty()

    /** The visitor's stored switch: only "false" turns sounds off (nothing stored reads as on). */
    fun soundEnabledFrom(stored: String?): Boolean = stored != "false"

    /**
     * The cue for `event` under the owner's settings: the pack's sound when a
     * pack is on; else the message sound for MESSAGE and the launcher file
     * for OPEN / CLOSE; SEND has a sound only in a pack.
     */
    fun cueFor(c: Map<String, Any?>?, event: Event): Cue? {
        if (soundPackOn(c)) {
            val pack = normalizeSoundPack(c?.get("soundPack"))
            val steps = packRecipe(pack, event)
            return if (steps.isNotEmpty()) Cue.Tone("$pack.${event.key}", steps) else null
        }
        return when (event) {
            Event.MESSAGE -> {
                val kind = normalizeMessageSound(c?.get("messageSound"))
                val steps = soundRecipe(kind)
                if (steps.isNotEmpty()) Cue.Tone("message.$kind", steps) else null
            }
            Event.OPEN, Event.CLOSE -> toggleSoundUrl(c).takeIf { it.isNotEmpty() }?.let { Cue.Url(it) }
            Event.SEND -> null
        }
    }

    /**
     * The gaps between sounds, per moment: a message sound at most every
     * [MIN_GAP_MS] (a pack's message too), each pack moment its own gap. The
     * launcher file has no gap (the web plays it on every toggle).
     */
    class SoundGate {
        private val last = HashMap<Event, Long>()

        /** True (and noted) when `event` may play at `now`. */
        fun allow(c: Map<String, Any?>?, event: Event, now: Long): Boolean {
            val pack = soundPackOn(c)
            if (!pack && event != Event.MESSAGE) return true
            val gap = if (pack) PACK_GAP_MS.getValue(event) else MIN_GAP_MS
            val prev = last[event]
            if (prev != null && now - prev < gap) return false
            last[event] = now
            return true
        }
    }

    // ------------------------------------------------------------------
    // The tone as samples
    // ------------------------------------------------------------------

    /** Web Audio starts the notes this long after the call (t0 = currentTime + 0.01). */
    private const val LEAD_S = 0.01
    /** The gain's attack (exponentialRampToValueAtTime(gain, t + 0.012)). */
    private const val ATTACK_S = 0.012
    /** Each oscillator stops this long after its note (osc.stop(t + dur + 0.02)). */
    private const val TAIL_S = 0.02
    private const val FLOOR = 0.0001

    const val SAMPLE_RATE = 22050

    private fun wave(type: Wave, phase: Double): Double {
        val p = phase - floor(phase)
        return when (type) {
            Wave.SQUARE -> if (p < 0.5) 1.0 else -1.0
            Wave.TRIANGLE -> if (p < 0.25) 4 * p else if (p < 0.75) 2 - 4 * p else 4 * p - 4
            Wave.SINE -> sin(2 * PI * p)
        }
    }

    /** A value on an exponential ramp from a to b, at fraction f (Web Audio's exponentialRampToValueAtTime). */
    private fun expRamp(a: Double, b: Double, f: Double): Double = a * (b / a).pow(max(0.0, min(1.0, f)))

    /** The gain of a step `t` seconds into it: 0.0001, up to `gain` in 12 ms, down to 0.0001 at `dur`. */
    fun stepGain(s: ToneStep, t: Double): Double {
        if (t < 0 || t > s.dur + TAIL_S) return 0.0
        if (t <= ATTACK_S) return expRamp(FLOOR, s.gain, t / ATTACK_S)
        if (t <= s.dur) return expRamp(s.gain, FLOOR, (t - ATTACK_S) / max(1e-6, s.dur - ATTACK_S))
        return FLOOR
    }

    /** The step's frequency `t` seconds into it: an exponential glide from `from` to `to` over `dur`. */
    fun stepFrequency(s: ToneStep, t: Double): Double = expRamp(s.from, s.to, t / s.dur)

    /** Seconds the sound lasts, its lead-in and tails included. */
    fun toneLength(steps: List<ToneStep>): Double =
        if (steps.isEmpty()) 0.0 else LEAD_S + steps.maxOf { it.at + it.dur + TAIL_S }

    /** The steps as samples in [-1, 1]. */
    fun renderSamples(steps: List<ToneStep>, sampleRate: Int = SAMPLE_RATE): DoubleArray {
        val n = ceil(toneLength(steps) * sampleRate).toInt()
        val out = DoubleArray(n)
        for (s in steps) {
            val start = Math.round((LEAD_S + s.at) * sampleRate).toInt()
            val len = ceil((s.dur + TAIL_S) * sampleRate).toInt()
            var phase = 0.0
            var i = 0
            while (i < len && start + i < n) {
                val t = i.toDouble() / sampleRate
                out[start + i] += wave(s.type, phase) * stepGain(s, t)
                phase += stepFrequency(s, min(t, s.dur)) / sampleRate
                i++
            }
        }
        for (i in 0 until n) out[i] = max(-1.0, min(1.0, out[i]))
        return out
    }

    /** The steps as 16-bit PCM samples (what AudioTrack plays). */
    fun renderPcm(steps: List<ToneStep>, sampleRate: Int = SAMPLE_RATE): ShortArray {
        val samples = renderSamples(steps, sampleRate)
        return ShortArray(samples.size) { (samples[it] * 32767).roundToInt().toShort() }
    }

    /** The steps as a 16-bit mono PCM WAV file. */
    fun renderWav(steps: List<ToneStep>, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val pcm = renderPcm(steps, sampleRate)
        val data = pcm.size * 2
        val out = ByteArray(44 + data)
        fun str(o: Int, s: String) = s.forEachIndexed { i, ch -> out[o + i] = ch.code.toByte() }
        fun u32(o: Int, v: Int) { for (i in 0..3) out[o + i] = (v ushr (8 * i)).toByte() }
        fun u16(o: Int, v: Int) { out[o] = v.toByte(); out[o + 1] = (v ushr 8).toByte() }
        str(0, "RIFF"); u32(4, 36 + data); str(8, "WAVE"); str(12, "fmt ")
        u32(16, 16) // PCM chunk size
        u16(20, 1) // PCM
        u16(22, 1) // mono
        u32(24, sampleRate)
        u32(28, sampleRate * 2) // byte rate
        u16(32, 2) // block align
        u16(34, 16) // bits per sample
        str(36, "data"); u32(40, data)
        pcm.forEachIndexed { i, v -> u16(44 + i * 2, v.toInt()) }
        return out
    }

    /** The loudest sample (a test's check that a tone is there and stays quiet). */
    fun peak(samples: DoubleArray): Double = samples.maxOfOrNull { abs(it) } ?: 0.0

    // ------------------------------------------------------------------
    // Which new conversation rows are a message arriving
    // ------------------------------------------------------------------

    /** More new rows than this at once is a conversation put on screen whole (restored, history), not news. */
    const val BULK_ROWS = 3

    /** A row as the sound logic reads it: its id and kind ("bot", "agent", "visitor", "other"). */
    data class Row(val id: String, val arriving: Boolean)

    /** Whether the rows added since `prevIds` include an arriving message (and are not a bulk restore). */
    fun messageArrived(prevIds: Set<String>, next: List<Row>): Boolean {
        val added = next.filter { it.id !in prevIds }
        return added.isNotEmpty() && added.size <= BULK_ROWS && added.any { it.arriving }
    }
}
