package com.conferbot.sdk.core.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.conferbot.sdk.core.sound.MessageSound.Cue
import com.conferbot.sdk.core.sound.MessageSound.Event
import com.conferbot.sdk.models.RecordItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The chat's sounds on the phone (core/sound/MessageSound.kt; RN
 * useChatSounds.ts and utils/chatSounds.ts): a message arriving, the chat
 * opening and closing, the visitor sending, under the owner's
 * messageSound, soundPack and launcher sound, and the visitor's Sound on /
 * off kept in the SDK's SharedPreferences under the web's key.
 *
 * Tones are rendered once per sound into 16-bit PCM and played with a
 * static [AudioTrack]; the owner's launcher file with [MediaPlayer]. Both
 * are media sounds (USAGE_MEDIA, CONTENT_TYPE_SONIFICATION): they follow
 * the media volume, and nothing plays while it is at 0. The visitor's own
 * Sound off is checked before every sound. Failures are silent: a sound
 * never breaks the chat.
 */
class ChatSounds(
    private val prefs: () -> android.content.SharedPreferences?,
    private val context: () -> Context?,
    private val customizations: () -> Map<String, Any?>,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val gate = MessageSound.SoundGate()
    private val _soundOn = MutableStateFlow(true)
    /** The visitor's switch (true unless they turned sounds off). */
    val soundOn: StateFlow<Boolean> = _soundOn.asStateFlow()
    private var loaded = false

    /** Read the visitor's switch as they left it. */
    fun load() {
        if (loaded) return
        loaded = true
        _soundOn.value = MessageSound.soundEnabledFrom(runCatching { prefs()?.getString(MessageSound.SOUND_ENABLED_KEY, null) }.getOrNull())
    }

    /** Whether the chat menu offers Sound on / off (the bot has a sound). */
    fun hasSounds(): Boolean = MessageSound.hasAnySound(customizations())

    /** The chat menu's Sound on / off. */
    fun toggle() {
        load()
        val next = !_soundOn.value
        _soundOn.value = next
        runCatching { prefs()?.edit()?.putString(MessageSound.SOUND_ENABLED_KEY, next.toString())?.apply() }
    }

    /** Play `event`'s sound if the owner set one, the visitor has sounds on and the gap allows it. */
    fun play(event: Event): Boolean {
        load()
        if (!_soundOn.value) return false
        val c = customizations()
        val cue = MessageSound.cueFor(c, event) ?: return false
        if (!gate.allow(c, event, now())) return false
        val ctx = context() ?: return false
        return runCatching { player.play(ctx, cue); true }.getOrElse {
            Log.d(TAG, "Sound not played: ${it.message}")
            false
        }
    }

    // ---- A message arriving ----

    private var seen: Set<String> = emptySet()
    private var primed = false

    /**
     * The conversation changed: a bot step's bubble or a person's / the AI's
     * message just added plays the message sound. The first list seen (a
     * restored chat) and a bulk restore are not news.
     */
    fun onRecord(items: List<RecordItem>, restoring: Boolean = false) {
        val rows = items.map { MessageSound.Row(it.id, isArriving(it)) }
        val arrived = primed && !restoring && MessageSound.messageArrived(seen, rows)
        seen = rows.mapTo(HashSet()) { it.id }
        primed = true
        if (arrived) play(Event.MESSAGE)
    }

    /** A new conversation: the next rows are counted from nothing. */
    fun resetSeen() {
        seen = emptySet()
    }

    companion object {
        private const val TAG = "ChatSounds"

        /**
         * A row that is a message arriving: a bot step's bubble (not the
         * frozen copy of a choice question) or a person's / the AI's message.
         */
        fun isArriving(item: RecordItem): Boolean = when (item) {
            is RecordItem.BotMessage -> item.nodeData?.containsKey("choices") != true
            is RecordItem.AgentMessage, is RecordItem.AgentRichMessage, is RecordItem.AgentMessageFile, is RecordItem.AgentMessageAudio -> true
            else -> false
        }

        private val player = Player()
    }

    /** Plays cues: rendered tones cached by key, files streamed. */
    private class Player {
        private val pcm = HashMap<String, ShortArray>()
        private val main = Handler(Looper.getMainLooper())

        private val attributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        private fun muted(ctx: Context): Boolean {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            return am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }

        fun play(ctx: Context, cue: Cue) {
            if (muted(ctx)) return
            when (cue) {
                is Cue.Tone -> playTone(cue)
                is Cue.Url -> playUrl(cue.url)
            }
        }

        private fun playTone(cue: Cue.Tone) {
            val samples = synchronized(pcm) { pcm.getOrPut(cue.key) { MessageSound.renderPcm(cue.steps) } }
            if (samples.isEmpty()) return
            val format = AudioFormat.Builder()
                .setSampleRate(MessageSound.SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            @Suppress("DEPRECATION") // AudioTrack.Builder is API 23; this constructor is API 21.
            val track = AudioTrack(attributes, format, samples.size * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE)
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                return
            }
            track.write(samples, 0, samples.size)
            track.play()
            // Released once it has played (its length, and a little more).
            val ms = samples.size * 1000L / MessageSound.SAMPLE_RATE + 200
            main.postDelayed({ runCatching { track.stop() }; track.release() }, ms)
        }

        private fun playUrl(url: String) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(attributes)
                mp.setDataSource(url)
                mp.setOnPreparedListener { it.start() }
                mp.setOnCompletionListener { it.release() }
                mp.setOnErrorListener { p, _, _ -> p.release(); true }
                mp.prepareAsync()
            } catch (e: Exception) {
                mp.release()
                throw e
            }
        }
    }
}
