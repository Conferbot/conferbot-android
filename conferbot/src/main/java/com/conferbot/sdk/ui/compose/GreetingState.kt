package com.conferbot.sdk.ui.compose

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.conferbot.sdk.core.agent.AgentBot
import com.conferbot.sdk.core.customization.Customizations
import com.conferbot.sdk.core.customization.Greeting
import com.conferbot.sdk.core.customization.MascotArt
import com.conferbot.sdk.core.customization.MascotMotion
import com.conferbot.sdk.core.state.ChatState
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** What the greeting card shows (core/customization/Greeting dressed for this bot). */
data class GreetingCardData(
    val style: String,
    val text: String,
    val head: Greeting.Head,
    val starters: List<String>,
    val bubble: Greeting.Bubble,
    val accent: String,
    val font: String?,
    /** The phone text's typing moment plays once per app session. */
    val firstShow: Boolean,
    val click: String,
    val linkUrl: String,
    /** The owner's mascot greets in place of the avatar (mascotInGreeting), in the launcher's colour; null for the avatar. */
    val mascot: GreetingMascot? = null,
    /** How the card and its figure move (mascotMotion): lively (default), subtle or off. */
    val motion: String = MascotMotion.DEFAULT,
)

/** The mascot on the greeting card: the owner's look and the colour it is drawn in when they chose none. */
data class GreetingMascot(val look: MascotArt.Look, val accent: String)

/**
 * The greeting card's life, as the web runs it (ts/visibility/greeting.ts):
 * decided once the bot's settings arrive, shown after its delay unless the
 * chat is open or the frequency says no, gone for a while once closed or
 * used. A new visit counts once per app process.
 */
@Stable
class GreetingState internal constructor(private val prefs: android.content.SharedPreferences?, private val botId: String) {
    var card by mutableStateOf<GreetingCardData?>(null)
        internal set

    internal var stored: Stored = read()

    /** Seen state for this app session (the "session" frequency, the phone text's typing moment). */
    internal val session: SessionSeen = SESSION.getOrPut(botId) { SessionSeen() }

    internal data class Stored(
        val visits: Int = 0,
        val chatted: Boolean = false,
        val shows: Map<String, List<Long>> = emptyMap(),
        val dismissed: Map<String, Long> = emptyMap(),
        val shownAt: Long? = null,
        val closedAt: Long? = null,
        val last: String? = null,
    )

    internal class SessionSeen(var shownThisSession: Boolean = false, var typed: Boolean = false, var counted: Boolean = false)

    private fun key() = "greeting.$botId"

    private fun keysOf(o: JSONObject): List<String> = o.keys().asSequence().map { it.toString() }.toList()

    private fun read(): Stored {
        return try {
            val raw = prefs?.getString(key(), null) ?: return Stored()
            val o = JSONObject(raw)
            val shows = o.optJSONObject("shows")?.let { s ->
                keysOf(s).associateWith { k -> s.getJSONArray(k).let { a -> List(a.length()) { a.getLong(it) } } }
            } ?: emptyMap()
            val dismissed = o.optJSONObject("dismissed")?.let { d -> keysOf(d).associateWith { d.getLong(it) } } ?: emptyMap()
            Stored(
                visits = o.optInt("visits", 0),
                chatted = o.optBoolean("chatted", false),
                shows = shows,
                dismissed = dismissed,
                shownAt = if (o.has("shownAt")) o.getLong("shownAt") else null,
                closedAt = if (o.has("closedAt")) o.getLong("closedAt") else null,
                last = o.optString("last").ifEmpty { null },
            )
        } catch (_: Exception) {
            Stored()
        }
    }

    internal fun write(st: Stored) {
        stored = st
        try {
            val o = JSONObject()
                .put("visits", st.visits)
                .put("chatted", st.chatted)
                .put("shows", JSONObject().apply { st.shows.forEach { (k, v) -> put(k, JSONArray(v)) } })
                .put("dismissed", JSONObject().apply { st.dismissed.forEach { (k, v) -> put(k, v) } })
            st.shownAt?.let { o.put("shownAt", it) }
            st.closedAt?.let { o.put("closedAt", it) }
            st.last?.let { o.put("last", it) }
            prefs?.edit()?.putString(key(), o.toString())?.apply()
        } catch (_: Exception) {
            // nothing kept without storage
        }
    }

    /** Closed with its X: rests for a while, and its rule counts as dismissed. */
    fun dismiss() {
        val now = System.currentTimeMillis()
        val st = stored
        write(st.copy(closedAt = now, dismissed = st.last?.let { st.dismissed + (it to now) } ?: st.dismissed))
        card = null
    }

    /** Used (tapped): rests for a while like a close, but is not a dismissal of its rule. */
    fun closeForNow() {
        write(stored.copy(closedAt = System.currentTimeMillis()))
        card = null
    }

    internal companion object {
        val SESSION = mutableMapOf<String, SessionSeen>()
        private const val NINETY_DAYS = 90L * 86_400_000L
    }

    /** One show: counted against its rule's limits, and the card dressed. */
    internal fun show(c: Map<String, Any?>, g: Greeting.Resolved, agentBot: AgentBot?) {
        val now = System.currentTimeMillis()
        session.shownThisSession = true
        val shows = stored.shows.toMutableMap()
        shows[g.key] = ((shows[g.key] ?: emptyList()).filter { now - it < NINETY_DAYS } + now).takeLast(20)
        write(stored.copy(shownAt = now, shows = shows, last = g.key))
        val head0 = Greeting.head(c).let { h -> if (agentBot != null) h.copy(name = agentBot.displayName.ifEmpty { h.name }) else h }
        // The owner's mascot greets in place of the avatar, by its name (firstImpression.ts).
        val look = MascotArt.lookOf(c)
        val withMascot = c["mascotInGreeting"] == true
        card = GreetingCardData(
            style = Greeting.normalizeStyle(c["greetingStyle"]),
            text = g.text,
            head = if (withMascot) head0.copy(avatar = null, name = look.name.ifEmpty { head0.name }) else head0,
            starters = g.starters.ifEmpty { agentBot?.starters ?: emptyList() }.take(3),
            bubble = Greeting.bubble(c),
            accent = Greeting.accent(c),
            font = Greeting.font(c),
            firstShow = !session.typed,
            click = g.click,
            linkUrl = g.linkUrl,
            mascot = if (withMascot) GreetingMascot(look, Customizations.mascotAccent(c)) else null,
            motion = MascotMotion.normalize(c["mascotMotion"]),
        )
        session.typed = true
    }
}

@Composable
fun rememberGreeting(
    customizations: Map<String, Any?>,
    chatOpen: Boolean,
    botId: String,
    agentBot: AgentBot?,
    enabled: Boolean,
): GreetingState {
    val context = LocalContext.current
    val state = remember(botId) {
        GreetingState(context.applicationContext.getSharedPreferences("conferbot", Context.MODE_PRIVATE), botId)
    }

    // Chatted, for rules that ask; and opening the chat rests the card, as closing it does.
    LaunchedEffect(chatOpen) {
        if (!chatOpen) return@LaunchedEffect
        val st = state.stored
        state.write(st.copy(chatted = true, closedAt = if (state.card != null) System.currentTimeMillis() else st.closedAt))
        state.card = null
    }

    LaunchedEffect(customizations, chatOpen, botId, agentBot, enabled) {
        if (customizations.isEmpty() || !enabled || chatOpen) {
            state.card = null
            return@LaunchedEffect
        }
        // Decided once, as the web does: settings arriving again (the theme's
        // palette applied, a socket reconnect re-fetching the bot) leave a
        // showing card alone rather than counting and playing it again.
        if (state.card != null) return@LaunchedEffect
        if (!state.session.counted) {
            state.session.counted = true
            state.write(state.stored.copy(visits = state.stored.visits + 1))
        }
        val st = state.stored
        val ctx = ChatState.visitorContext
        val g = Greeting.decide(
            customizations,
            Greeting.Facts(
                returning = st.visits > 1,
                signedIn = ctx != null && (ctx["id"] != null || ctx["email"] != null),
                visits = maxOf(1, st.visits),
                chatted = st.chatted,
                shows = st.shows,
                dismissed = st.dismissed,
            ),
        ) ?: return@LaunchedEffect
        if (!Greeting.frequencyAllows(customizations, Greeting.Seen(st.shownAt, st.closedAt, state.session.shownThisSession))) return@LaunchedEffect
        delay(g.delay * 1000L)
        state.show(customizations, g, agentBot)
    }
    return state
}
