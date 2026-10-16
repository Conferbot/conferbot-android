package com.conferbot.sdk.core.sound

import com.conferbot.sdk.core.sound.MessageSound.Event
import com.conferbot.sdk.core.sound.MessageSound.Row
import com.conferbot.sdk.core.sound.MessageSound.Wave
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * The chat's sounds (core/sound/MessageSound.kt): the cases of the React
 * Native SDK's __tests__/core/messageSound.test.ts (web
 * ts/chat/messageSound.ts, index.ts _playToggleSound).
 */
class MessageSoundTest {

    @Test
    fun `the web's three message sounds, off by default`() {
        assertThat(MessageSound.soundRecipe("off")).isEmpty()
        assertThat(MessageSound.soundRecipe(null)).isEmpty()
        assertThat(MessageSound.soundRecipe("ding")).isEmpty()
        assertThat(MessageSound.soundRecipe("pop")).containsExactly(MessageSound.ToneStep(0.0, 520.0, 880.0, 0.09, Wave.SINE, 0.18))
        assertThat(MessageSound.soundRecipe("chime").map { it.from }).containsExactly(880.0, 1318.5).inOrder()
        assertThat(MessageSound.soundRecipe("bubble").map { it.from to it.to }).containsExactly(380.0 to 760.0, 520.0 to 1040.0).inOrder()
        assertThat(MessageSound.normalizeMessageSound("chime")).isEqualTo("chime")
        assertThat(MessageSound.normalizeMessageSound("loud")).isEqualTo("off")
    }

    @Test
    fun `five packs with all four moments, quiet and short`() {
        assertThat(MessageSound.SOUND_PACKS).containsExactly("off", "soft", "playful", "retro", "bubbly", "premium").inOrder()
        assertThat(MessageSound.normalizeSoundPack("nope")).isEqualTo("off")
        for (pack in MessageSound.SOUND_PACKS.filter { it != "off" }) {
            for (ev in Event.values()) {
                val steps = MessageSound.packRecipe(pack, ev)
                assertThat(steps).isNotEmpty()
                steps.forEach { assertThat(it.gain).isAtMost(0.2) }
                assertThat(MessageSound.toneLength(steps)).isLessThan(0.5)
            }
        }
        assertThat(MessageSound.packRecipe("retro", Event.SEND)).containsExactly(MessageSound.ToneStep(0.0, 660.0, 660.0, 0.04, Wave.SQUARE, 0.04))
        assertThat(MessageSound.packRecipe("playful", Event.OPEN).map { it.at }).containsExactly(0.0, 0.065, 0.13).inOrder()
        assertThat(MessageSound.packRecipe("off", Event.OPEN)).isEmpty()
    }

    @Test
    fun `the launcher file for open and close, unless a pack plays them`() {
        val c = mapOf("enableWidgetToggleSound" to true, "checkedSoundTab" to "choose", "widgetToggleSound" to "https://cdn.conferbot.com/sounds/sound-1.mp3")
        assertThat(MessageSound.toggleSoundUrl(c)).isEqualTo("https://cdn.conferbot.com/sounds/sound-1.mp3")
        assertThat(MessageSound.toggleSoundUrl(c - "checkedSoundTab")).isEqualTo("https://cdn.conferbot.com/sounds/sound-1.mp3")
        assertThat(MessageSound.toggleSoundUrl(c + mapOf("checkedSoundTab" to "upload", "customWidgetSoundUrl" to "https://x.test/a.wav"))).isEqualTo("https://x.test/a.wav")
        assertThat(MessageSound.toggleSoundUrl(c + ("enableWidgetToggleSound" to false))).isEmpty()
        assertThat(MessageSound.toggleSoundUrl(c + ("soundPack" to "soft"))).isEmpty()
        assertThat(MessageSound.toggleSoundUrl(c + ("widgetToggleSound" to "javascript:alert(1)"))).isEmpty()
        assertThat(MessageSound.cueFor(c, Event.OPEN)).isEqualTo(MessageSound.Cue.Url("https://cdn.conferbot.com/sounds/sound-1.mp3"))
        assertThat(MessageSound.cueFor(c, Event.CLOSE)).isEqualTo(MessageSound.Cue.Url("https://cdn.conferbot.com/sounds/sound-1.mp3"))
        assertThat(MessageSound.cueFor(c, Event.SEND)).isNull()
        assertThat(MessageSound.cueFor(c, Event.MESSAGE)).isNull()
    }

    @Test
    fun `the pack over the single message sound`() {
        assertThat((MessageSound.cueFor(mapOf("messageSound" to "pop"), Event.MESSAGE) as MessageSound.Cue.Tone).key).isEqualTo("message.pop")
        assertThat((MessageSound.cueFor(mapOf("messageSound" to "pop", "soundPack" to "retro"), Event.MESSAGE) as MessageSound.Cue.Tone).key).isEqualTo("retro.message")
        assertThat((MessageSound.cueFor(mapOf("soundPack" to "retro"), Event.SEND) as MessageSound.Cue.Tone).key).isEqualTo("retro.send")
        assertThat(MessageSound.cueFor(emptyMap(), Event.MESSAGE)).isNull()
        assertThat(MessageSound.hasAnySound(emptyMap())).isFalse()
        assertThat(MessageSound.hasAnySound(mapOf("messageSound" to "chime"))).isTrue()
        assertThat(MessageSound.hasAnySound(mapOf("soundPack" to "soft"))).isTrue()
    }

    @Test
    fun `the web's gaps - a message at most every 800 ms, each pack moment its own`() {
        val gate = MessageSound.SoundGate()
        val c = mapOf("messageSound" to "pop")
        assertThat(gate.allow(c, Event.MESSAGE, 1000)).isTrue()
        assertThat(gate.allow(c, Event.MESSAGE, 1000 + MessageSound.MIN_GAP_MS - 1)).isFalse()
        assertThat(gate.allow(c, Event.MESSAGE, 1000 + MessageSound.MIN_GAP_MS)).isTrue()
        // The launcher file plays on every toggle.
        assertThat(gate.allow(c, Event.OPEN, 1000)).isTrue()
        assertThat(gate.allow(c, Event.OPEN, 1001)).isTrue()
        val p = mapOf("soundPack" to "soft")
        val g2 = MessageSound.SoundGate()
        assertThat(g2.allow(p, Event.SEND, 0)).isTrue()
        assertThat(g2.allow(p, Event.SEND, MessageSound.PACK_GAP_MS.getValue(Event.SEND) - 1)).isFalse()
        assertThat(g2.allow(p, Event.OPEN, 10)).isTrue()
        assertThat(g2.allow(p, Event.SEND, MessageSound.PACK_GAP_MS.getValue(Event.SEND))).isTrue()
    }

    @Test
    fun `the visitor's switch as the web reads it - only false is off`() {
        assertThat(MessageSound.soundEnabledFrom("false")).isFalse()
        assertThat(MessageSound.soundEnabledFrom("true")).isTrue()
        assertThat(MessageSound.soundEnabledFrom(null)).isTrue()
    }

    @Test
    fun `a tone as Web Audio renders it - exponential glides, a 12 ms attack, a quiet tail`() {
        val pop = MessageSound.soundRecipe("pop").first()
        assertThat(MessageSound.stepFrequency(pop, 0.0)).isWithin(1e-6).of(520.0)
        assertThat(MessageSound.stepFrequency(pop, pop.dur)).isWithin(1e-6).of(880.0)
        assertThat(MessageSound.stepFrequency(pop, pop.dur / 2)).isWithin(1e-6).of(sqrt(520.0 * 880.0))
        assertThat(MessageSound.stepGain(pop, 0.0)).isWithin(1e-6).of(0.0001)
        assertThat(MessageSound.stepGain(pop, 0.012)).isWithin(1e-6).of(0.18)
        assertThat(MessageSound.stepGain(pop, pop.dur)).isWithin(1e-6).of(0.0001)
        assertThat(MessageSound.stepGain(pop, pop.dur + 0.05)).isEqualTo(0.0)
        val samples = MessageSound.renderSamples(MessageSound.soundRecipe("pop"), 8000)
        assertThat(samples.size).isEqualTo(ceil(MessageSound.toneLength(MessageSound.soundRecipe("pop")) * 8000).toInt())
        val peak = MessageSound.peak(samples)
        assertThat(peak).isGreaterThan(0.1)
        assertThat(peak).isAtMost(0.18 + 1e-6)
    }

    @Test
    fun `a valid 16-bit mono WAV, under 25 KB for the longest sound`() {
        val wav = MessageSound.renderWav(MessageSound.packRecipe("premium", Event.OPEN))
        fun text(o: Int, n: Int) = String(wav.copyOfRange(o, o + n), Charsets.US_ASCII)
        fun u16(o: Int) = (wav[o].toInt() and 0xff) or ((wav[o + 1].toInt() and 0xff) shl 8)
        fun u32(o: Int) = u16(o) or (u16(o + 2) shl 16)
        assertThat(text(0, 4)).isEqualTo("RIFF")
        assertThat(text(8, 4)).isEqualTo("WAVE")
        assertThat(u16(22)).isEqualTo(1)
        assertThat(u32(24)).isEqualTo(22050)
        assertThat(u16(34)).isEqualTo(16)
        assertThat(u32(40)).isEqualTo(wav.size - 44)
        assertThat(wav.size).isLessThan(25_000)
        // The PCM is the WAV's data.
        val pcm = MessageSound.renderPcm(MessageSound.packRecipe("premium", Event.OPEN))
        assertThat(pcm.size * 2).isEqualTo(wav.size - 44)
    }

    @Test
    fun `a message arriving, not the visitor's own nor a restored conversation`() {
        val before = listOf(Row("a", true))
        val ids = before.map { it.id }.toSet()
        assertThat(MessageSound.messageArrived(ids, before + Row("b", true))).isTrue()
        assertThat(MessageSound.messageArrived(ids, before + Row("u", false))).isFalse()
        val restored = (0..MessageSound.BULK_ROWS).map { Row("r$it", true) }
        assertThat(MessageSound.messageArrived(emptySet(), restored)).isFalse()
        assertThat(MessageSound.messageArrived(emptySet(), listOf(Row("w", true)))).isTrue()
    }
}
