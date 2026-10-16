package com.conferbot.sdk.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.conferbot.sdk.core.customization.OwnerFont
import com.conferbot.sdk.core.customization.OwnerFont.FontPick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The chat's font at run time (core/customization/OwnerFont.kt; RN
 * utils/fontLoader.ts and hooks/useChatFont.ts): the owner's font, else the
 * theme's, as a Compose [FontFamily] for the body and for headings.
 *
 * A web font's Google Fonts stylesheet is fetched (Google sends TrueType to a
 * client that is not a browser), each upright face downloaded once into the
 * app's cache (`cacheDir/conferbot-fonts/`) and checked with
 * [Typeface.createFromFile]; a later launch uses the cached files without the
 * network. A system or unknown family is asked of the device by name
 * ([Typeface.create]), which gives the platform font when it has none. Any
 * failure keeps the platform font: a font never breaks the chat.
 */
object ChatFontLoader {
    private const val TAG = "ChatFontLoader"

    /** The loaded fonts: null where the platform font draws the text. */
    data class Loaded(
        val body: FontFamily? = null,
        val display: FontFamily? = null,
        /** The body's faces by weight, for views that take a [Typeface] (the XML chat). */
        val bodyTypefaces: Map<Int, Typeface> = emptyMap()
    )

    private val _fonts = MutableStateFlow(Loaded())
    val fonts: StateFlow<Loaded> = _fonts.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var current: OwnerFont.ChatFonts? = null
    private val http by lazy { OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build() }

    /** The bot's settings arrived (or changed): pick the fonts and load them. */
    fun apply(context: Context, ownerFont: Any?, themeFont: String?, themeFontRole: String?) {
        val picks = OwnerFont.chatFontsFor(ownerFont, themeFont, themeFontRole)
        if (picks == current) return
        current = picks
        job?.cancel()
        if (picks.body == null && picks.display == null) {
            _fonts.value = Loaded()
            return
        }
        val app = context.applicationContext
        job = scope.launch {
            val body = picks.body?.let { load(app, it) }
            val display = if (picks.display == picks.body) body else picks.display?.let { load(app, it) }
            _fonts.value = Loaded(body?.family, display?.family, body?.typefaces.orEmpty())
        }
    }

    private class Faces(val family: FontFamily, val typefaces: Map<Int, Typeface>)

    private fun load(context: Context, pick: FontPick): Faces? = try {
        if (pick.google == null) {
            val tf = Typeface.create(pick.family, Typeface.NORMAL)
            Faces(FontFamily(androidx.compose.ui.text.font.Typeface(tf)), mapOf(400 to tf))
        } else loadWeb(context, pick)
    } catch (e: Exception) {
        Log.d(TAG, "Font ${pick.family} not loaded: ${e.message}")
        null
    }

    private fun fileName(family: String, weight: Int) = family.replace(Regex("[^A-Za-z0-9]"), "_") + "-" + weight + ".ttf"

    private fun loadWeb(context: Context, pick: FontPick): Faces? {
        val dir = File(context.cacheDir, "conferbot-fonts").apply { mkdirs() }
        val index = File(dir, pick.family.replace(Regex("[^A-Za-z0-9]"), "_") + ".weights")
        // Cached from an earlier launch: no network.
        val cached = runCatching { index.readText().split(",").mapNotNull { it.trim().toIntOrNull() } }.getOrNull()
            ?.associateWith { File(dir, fileName(pick.family, it)) }
            ?.takeIf { m -> m.isNotEmpty() && m.values.all { it.length() > 0 } }
        val files: Map<Int, File> = cached ?: run {
            val css = get(OwnerFont.googleFontUrl(pick.google!!))?.toString(Charsets.UTF_8) ?: return null
            val faces = OwnerFont.parseFontFaces(css).filter { it.style == "normal" }.distinctBy { it.weight }
            val got = LinkedHashMap<Int, File>()
            for (f in faces) {
                val bytes = get(f.url) ?: continue
                val out = File(dir, fileName(pick.family, f.weight))
                val tmp = File(dir, out.name + ".part")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(out)) { out.writeBytes(bytes); tmp.delete() }
                got[f.weight] = out
            }
            if (got.isNotEmpty()) index.writeText(got.keys.joinToString(","))
            got
        }
        // Each face registered with the platform; one it refuses is left out.
        val typefaces = LinkedHashMap<Int, Typeface>()
        val fonts = files.mapNotNull { (w, file) ->
            runCatching { Typeface.createFromFile(file) }.getOrNull()?.let { tf ->
                typefaces[w] = tf
                Font(file, FontWeight(w.coerceIn(1, 1000)))
            }
        }
        if (fonts.isEmpty()) {
            index.delete()
            return null
        }
        return Faces(FontFamily(fonts), typefaces)
    }

    private fun get(url: String): ByteArray? = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (r.isSuccessful) r.body?.bytes() else null
    }

    /** [theme] with the chat's fonts: the body font for all text, the display font for headings. */
    fun withFonts(theme: ConferbotTheme, loaded: Loaded = _fonts.value): ConferbotTheme {
        if (loaded.body == null && loaded.display == null) return theme
        val t = theme.typography
        val body = loaded.body ?: t.fontFamily
        return theme.copy(typography = t.copy(fontFamily = body, secondaryFontFamily = loaded.display ?: body))
    }

    /** The XML chat's typeface for [weight] (the nearest loaded face), or null for the platform font. */
    fun typeface(weight: Int = 400): Typeface? {
        val faces = _fonts.value.bodyTypefaces
        return OwnerFont.nearestWeight(faces.keys, weight)?.let { faces[it] }
    }
}
