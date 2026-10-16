package com.conferbot.sdk.core.requests

import com.conferbot.sdk.core.flow.FlowCalendar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

/**
 * "Your requests" (web conferbot-widget src/ts/home/requests/): a visitor's
 * own conversations with the team, where each one stands in plain words,
 * and a reply box that reaches the team. A reply to a solved request opens
 * it again. Ported from the React Native SDK's src/core/requests.ts.
 *
 * Pure: the words (web words.ts), what the device remembers (web store.ts,
 * as values in and out; [RequestsExtras] keeps them in SharedPreferences),
 * which visitor is asking (web api.ts `who`) and the socket calls with an
 * answer (web api.ts, embed-server socket/handlers/visitorRequestsHandler.js
 * and services/chatbot/visitorRequests.js). The screens are [RequestsPortal].
 *
 * Whose requests: the server lists a conversation only when its id is one
 * this device started AND its visitor id matches (the ids are random and
 * shown nowhere else), or when the app signed its user in (`user.hash`,
 * identity verification) - then their verified conversations on this bot
 * from any device. Only conversations a person on the team handles are
 * requests; a chat the AI answered alone is not one.
 *
 * English only for now (the web's i18n `requests.*`, `when.*` keys).
 */

// ------------------------------------------------------------ types

/** As the server sends them (visitorRequests.js `summary`). */
data class RequestSummary(
    val id: String,
    val title: String,
    /** "open" or "solved". */
    val status: String,
    val startedAt: String? = null,
    val lastAt: String? = null,
    /** "you" or "team". */
    val lastFrom: String,
    val lastText: String = "",
    val teamName: String = ""
)

/** visitorRequests.js `messagesOf`. */
data class RequestMessage(
    val id: String,
    /** "you" or "team". */
    val from: String,
    val name: String,
    val text: String,
    val at: String?
)

/** One request with its conversation (visitorRequests.js `open`). */
data class RequestDetail(val summary: RequestSummary, val messages: List<RequestMessage>) {
    val id: String get() = summary.id
}

enum class RequestState { REPLIED, WAITING, SOLVED }

/** A state in a few plain words, never a ticket status. */
data class StateWords(val label: String, val detail: String)

/** The app's signed-in user, only what the server checks (ConferBotUser). */
data class RequestsUser(val id: String? = null, val email: String? = null, val hash: String? = null)

/** The ids of the chats this device started (newest first) and when each request was last read here. */
data class RequestMemory(
    val sessions: List<String> = emptyList(),
    /** Request id -> when it was last read here (ms), oldest mark first. */
    val seen: Map<String, Long> = emptyMap()
)

/** What a reply came back with: the request as it is now, or why not (locked, not_found, empty, error, offline). */
data class ReplyResult(val ok: Boolean, val request: RequestDetail? = null, val reason: String? = null)

/** What the SDK's socket offers (services/SocketClient.kt). */
interface RequestsSocket {
    /** Emit and wait for the answer; null when offline or after [timeoutMs]. */
    fun ask(event: String, data: Map<String, Any?>, timeoutMs: Long, onAnswer: (Map<String, Any?>?) -> Unit)
    fun emit(event: String, data: Map<String, Any?>)
    /** Listen; returns the unsubscribe. */
    fun on(event: String, fn: (Map<String, Any?>?) -> Unit): () -> Unit
}

/** The calls (web api.ts requestsApi). */
interface RequestsApi {
    fun list(onDone: (List<RequestSummary>?) -> Unit)
    fun open(id: String, onDone: (RequestDetail?) -> Unit)
    /** No more live updates for it. */
    fun close(id: String)
    fun reply(id: String, text: String, entryId: String, onDone: (ReplyResult) -> Unit)
    /** A request changed on the server (a team reply, solved). Returns the unsubscribe. */
    fun onUpdate(fn: (String) -> Unit): () -> Unit
}

object Requests {

    // ------------------------------------------------------------ the words

    // The web's English strings (i18n en `requests.*`, `common.*`, `transcript.you`).
    const val TITLE = "Your requests"
    const val ONE = "Your request"
    const val BACK = "Back"
    const val LOADING = "Loading..."
    const val LOAD_FAILED = "We couldn't load this just now."
    const val TRY_AGAIN = "Try again"
    const val EMPTY = "Nothing here yet. When you ask us something, you can follow it here."
    const val YOU = "You"
    const val SUPPORT_TEAM = "Support team"
    const val REPLY_PLACEHOLDER = "Write your reply..."
    const val REOPEN_PLACEHOLDER = "Write here to open it again..."
    const val YOUR_REPLY = "Your reply"
    const val SEND = "Send"
    const val SENDING = "Sending..."
    const val NOT_SENT = "Not sent."
    const val LOCKED = "This conversation is closed for replies."
    const val NEW_REPLY = "New reply"
    fun newReplyFrom(name: String) = "New reply from $name"
    fun seeAll(n: Int) = "See all ($n)"

    /** The chat menu's item (RN; the web has the home card instead). */
    fun menu(unread: Int) = if (unread > 0) "$TITLE · $NEW_REPLY" else TITLE

    /** Each state in a few plain words, never a ticket status (web words.ts STATE_WORDS). */
    val STATE_WORDS: Map<RequestState, StateWords> = mapOf(
        RequestState.REPLIED to StateWords("We replied", "Read it below. If you need anything else, just write back."),
        RequestState.WAITING to StateWords("We're on it", "We'll reply right here. You can close this - your message is safe with us."),
        RequestState.SOLVED to StateWords("Solved", "Still need help with this? Write below and we'll pick it up again.")
    )

    /** Milliseconds of an ISO time (or a number); 0 when it does not read. */
    fun ms(v: Any?): Long = when (v) {
        null -> 0L
        is Number -> v.toLong()
        else -> FlowCalendar.parseIso(v.toString()) ?: 0L
    }

    /** A reply from the team on an open request, not read on this device yet (web isNewReply). */
    fun isNewReply(r: RequestSummary, seen: Map<String, Long>): Boolean =
        // A solved one is done: its last word is "Solved", not a reply to read.
        r.status != "solved" && r.lastFrom == "team" && ms(r.lastAt) > (seen[r.id] ?: 0L)

    fun stateOf(r: RequestSummary): RequestState = when {
        r.status == "solved" -> RequestState.SOLVED
        r.lastFrom == "team" -> RequestState.REPLIED
        else -> RequestState.WAITING
    }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val DAYS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    /** "Just now", "5 min ago", "3 hours ago", "Yesterday", "Monday", "3 Oct" (web whenWords, English, the phone's time zone). */
    fun whenWords(v: Any?, now: Long = System.currentTimeMillis()): String {
        val t = ms(v)
        if (t == 0L) return ""
        val diff = maxOf(0L, now - t)
        if (diff < 60_000L) return "Just now"
        if (diff < 3_600_000L) return "${diff / 60_000L} min ago"
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val startOfToday = (today.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (t >= startOfToday) {
            val h = diff / 3_600_000L
            return if (h <= 1) "1 hour ago" else "$h hours ago"
        }
        if (t >= startOfToday - 86_400_000L) return "Yesterday"
        val d = Calendar.getInstance().apply { timeInMillis = t }
        if (t >= startOfToday - 6 * 86_400_000L) return DAYS[d.get(Calendar.DAY_OF_WEEK) - 1]
        val year = d.get(Calendar.YEAR)
        return "${d.get(Calendar.DAY_OF_MONTH)} ${MONTHS[d.get(Calendar.MONTH)]}" + if (year != today.get(Calendar.YEAR)) " $year" else ""
    }

    /** The line under a request's title (web rowLine). */
    fun rowLine(r: RequestSummary, seen: Map<String, Long>, now: Long = System.currentTimeMillis()): String {
        val whenText = whenWords(r.lastAt, now)
        val head = if (isNewReply(r, seen)) {
            if (r.teamName.isNotEmpty()) newReplyFrom(r.teamName) else NEW_REPLY
        } else STATE_WORDS.getValue(stateOf(r)).label
        return if (whenText.isNotEmpty()) "$head · $whenText" else head
    }

    /** Newest replies first, then whatever moved last (web ordered). */
    fun ordered(list: List<RequestSummary>, seen: Map<String, Long>): List<RequestSummary> =
        list.sortedWith(
            compareByDescending<RequestSummary> { isNewReply(it, seen) }.thenByDescending { ms(it.lastAt) }
        )

    /** How many requests have a team reply not read here: the app's badge (RN). */
    fun unreadCount(list: List<RequestSummary>?, seen: Map<String, Long>): Int = list.orEmpty().count { isNewReply(it, seen) }

    /** Read up to the newest message, by the server's clock too (web view.ts seenAt). */
    fun seenAt(lastAt: String?, now: Long = System.currentTimeMillis()): Long = maxOf(now, ms(lastAt))

    /** The home card shows two; "See all" when there are more (web card.ts ON_HOME). */
    const val ON_HOME = 2
    /** The list is read again at most this often when nothing forces it (web card.ts). */
    const val REFRESH_GAP_MS = 15_000L
    /** The reply box's limit (web view.ts maxLength, the server's MAX_REPLY). */
    const val MAX_REPLY = 4000

    // ------------------------------------------------------------ links in a message

    // The web's pattern; nothing in it that ICU (Android) reads differently from the JVM.
    private val URL_RE = Regex("\\bhttps?://[^\\s<>\"']+[^\\s<>\"'.,;:!?)\\]]", RegexOption.IGNORE_CASE)

    /** A run of a message: plain text, or a link. */
    data class Run(val text: String, val url: String? = null)

    /** Text with its links as their own runs, everything else text (web view.ts appendText). */
    fun linkRuns(text: String?): List<Run> {
        val s = text.orEmpty()
        val out = mutableListOf<Run>()
        var at = 0
        URL_RE.findAll(s).forEach { m ->
            if (m.range.first > at) out += Run(s.substring(at, m.range.first))
            out += Run(m.value, m.value)
            at = m.range.last + 1
        }
        if (at < s.length) out += Run(s.substring(at))
        return out
    }

    // ------------------------------------------------------------ what the device remembers

    const val MAX_SESSIONS = 30
    /** A conversation id the server accepts (visitorRequests.js SESSION). */
    val SESSION_RE = Regex("^[\\w-]{6,64}$")
    private const val SEEN_KEPT = 60

    /** Per bot (the web keeps `cb_req_<botId>` in localStorage, RN `@conferbot:requests:<botId>`). */
    fun storageKey(botId: String?) = "conferbot_requests_${botId?.takeIf { it.isNotEmpty() } ?: "default"}"

    private val gson = Gson()

    /** What was stored, cleaned (web readRequests). Anything unreadable is nothing. */
    fun parseMemory(raw: String?): RequestMemory = try {
        val m: Map<String, Any?>? = gson.fromJson(raw ?: "null", object : TypeToken<Map<String, Any?>>() {}.type)
        if (m == null) RequestMemory() else {
            val sessions = (m["sessions"] as? List<*>).orEmpty()
                .filterIsInstance<String>()
                .filter { SESSION_RE.matches(it) }
                .take(MAX_SESSIONS)
            val seen = linkedMapOf<String, Long>()
            (m["seen"] as? Map<*, *>)?.forEach { (k, v) ->
                val key = k as? String ?: return@forEach
                if (!SESSION_RE.matches(key)) return@forEach
                val t = (v as? Number)?.toDouble() ?: return@forEach
                if (t.isFinite()) seen[key] = t.toLong()
            }
            RequestMemory(sessions, seen)
        }
    } catch (e: Exception) {
        RequestMemory()
    }

    /** What to store (web store.ts write): 30 sessions, the last 60 read marks. */
    fun serializeMemory(m: RequestMemory): String {
        val keys = m.seen.keys.toList().takeLast(SEEN_KEPT)
        val seen = linkedMapOf<String, Long>().apply { keys.forEach { put(it, m.seen.getValue(it)) } }
        return gson.toJson(mapOf("sessions" to m.sessions.take(MAX_SESSIONS), "seen" to seen))
    }

    /** A chat on this device was saved: it may become a request (web rememberSession). Null when nothing changes. */
    fun withSession(m: RequestMemory, session: String?): RequestMemory? {
        val s = session.orEmpty()
        if (!SESSION_RE.matches(s) || m.sessions.firstOrNull() == s) return null
        return m.copy(sessions = (listOf(s) + m.sessions.filter { it != s }).take(MAX_SESSIONS))
    }

    /** They read this request up to [at] (web markSeen). Null when nothing changes. */
    fun withSeen(m: RequestMemory, id: String, at: Long): RequestMemory? {
        if (id.isEmpty() || (m.seen[id] ?: 0L) >= at) return null
        // Put back last, so the newest read marks are the ones kept.
        val seen = LinkedHashMap(m.seen).apply { remove(id); put(id, at) }
        return m.copy(seen = seen)
    }

    /**
     * The visitor wrote in this chat: remember it (web index.ts
     * `_sendResponse`, a `user-` entry in the record). [entries]: each
     * record entry's (type, shape), as LiveEvents.kindsOf gives them.
     */
    fun hasVisitorEntry(entries: List<Pair<String?, String?>>?): Boolean =
        entries.orEmpty().any { (type, shape) -> (shape?.takeIf { it.isNotEmpty() } ?: type.orEmpty()).startsWith("user-") }

    // ------------------------------------------------------------ who is asking

    /** Every call says which visitor this is (web api.ts `who`): only what the server checks. */
    fun who(visitorId: String?, memory: RequestMemory, user: RequestsUser?): Map<String, Any?> = buildMap {
        put("visitorId", visitorId.orEmpty())
        put("sessions", memory.sessions)
        if (user != null) put("user", buildMap {
            user.id?.let { put("id", it) }
            user.email?.let { put("email", it) }
            user.hash?.let { put("hash", it) }
        })
    }

    /** Nothing on this device and nobody signed in: nothing to ask about (web card.ts refresh). */
    fun canAsk(memory: RequestMemory, user: RequestsUser?): Boolean =
        memory.sessions.isNotEmpty() || !user?.hash.isNullOrEmpty()

    // ------------------------------------------------------------ the server

    /** web api.ts TIMEOUT_MS */
    const val TIMEOUT_MS = 10_000L

    /** The socket events (embed-server visitorRequestsHandler.js). */
    const val EVENT_LIST = "my-requests"
    const val EVENT_OPEN = "my-request-open"
    const val EVENT_CLOSE = "my-request-close"
    const val EVENT_REPLY = "my-request-reply"
    const val EVENT_UPDATE = "my-request-update"

    private fun str(v: Any?): String = v as? String ?: v?.toString().orEmpty()

    /** A request as the server sends it; null when it has no id. */
    fun summaryOf(m: Map<*, *>?): RequestSummary? {
        m ?: return null
        val id = str(m["id"]).ifEmpty { return null }
        return RequestSummary(
            id = id,
            title = str(m["title"]),
            status = if (m["status"] == "solved") "solved" else "open",
            startedAt = m["startedAt"]?.let(::str),
            lastAt = m["lastAt"]?.let(::str),
            lastFrom = if (m["lastFrom"] == "team") "team" else "you",
            lastText = str(m["lastText"]),
            teamName = str(m["teamName"])
        )
    }

    fun detailOf(m: Map<*, *>?): RequestDetail? {
        val s = summaryOf(m) ?: return null
        val messages = (m?.get("messages") as? List<*>).orEmpty().mapIndexedNotNull { i, e ->
            val x = e as? Map<*, *> ?: return@mapIndexedNotNull null
            RequestMessage(
                id = str(x["id"]).ifEmpty { "m$i" },
                from = if (x["from"] == "team") "team" else "you",
                name = str(x["name"]),
                text = str(x["text"]),
                at = x["at"]?.let(::str)
            )
        }
        return RequestDetail(s, messages)
    }

    /**
     * The calls (web api.ts requestsApi) on [socket]; `who` is read at each
     * call. The socket's 'connect' fires on every connect: the server
     * forgets a request's live updates when the connection drops, so the
     * open one is read (and joined) again. Answers come through [post].
     */
    fun api(socket: () -> RequestsSocket?, who: () -> Map<String, Any?>, post: (() -> Unit) -> Unit = { it() }): RequestsApiClient =
        RequestsApiClient(socket, who, post)

    /** A reply's id, so a retry is one message on the server (web view.ts; the server keeps `request-reply-[\w-]{1,60}`). */
    fun replyEntryId(now: Long = System.currentTimeMillis(), rand: () -> Double = Math::random): String =
        "request-reply-${now.toString(36)}${fraction36(rand(), 6)}"

    /** The first [digits] base-36 digits after the point, as JavaScript's toString(36).slice(2). */
    private fun fraction36(x: Double, digits: Int): String {
        val sb = StringBuilder()
        var f = x - Math.floor(x)
        while (sb.length < digits && f > 0.0) {
            f *= 36
            val d = f.toInt()
            sb.append(Character.forDigit(d, 36))
            f -= d
        }
        return sb.toString()
    }

    // ------------------------------------------------------------ the look

    private val HEX = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

    private fun hex(v: Any?): String? = (v as? String)?.trim()?.takeIf { HEX.matches(it) }

    private fun lightness(h: String): Double {
        var s = h.substring(1)
        if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
        val (r, g, b) = listOf(0, 2, 4).map { i ->
            val c = s.substring(i, i + 2).toInt(16) / 255.0
            if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * The accent for the Back link, the visitor's bubbles and Send (web
     * firstImpression.ts accentFor, brand/brand.ts brandColorOf): the brand
     * colour (not with a theme palette), the header colour, the launcher's,
     * unless too light to read (homeUtils.ts isLight, luminance over 0.55),
     * then indigo.
     */
    fun accent(c: Map<String, Any?>?): String {
        val palette = c?.get("_themePalette").let { it != null && it != false }
        val brand = (if (!palette) hex(c?.get("brandColor")) else null) ?: c?.get("headerBgColor")
        val one = c?.get("widgetGradientBgOne")
        val two = c?.get("widgetGradientBgTwo")
        val launcher = if (c?.get("widgetIconThemeType") == "Gradient" && one != null && two != null) listOf(one, two)
        else listOf(c?.get("widgetIconBgColor") ?: c?.get("headerBgColor"))
        for (v in listOf(brand, c?.get("headerBgColor")) + launcher) {
            val h = hex(v)
            if (h != null && lightness(h) <= 0.55) return h
        }
        return "#4F46E5"
    }
}

/** [RequestsApi] on the SDK's socket (web api.ts requestsApi). */
class RequestsApiClient(
    private val socket: () -> RequestsSocket?,
    private val who: () -> Map<String, Any?>,
    private val post: (() -> Unit) -> Unit = { it() }
) : RequestsApi {
    @Volatile private var current: String? = null

    private fun call(event: String, payload: Map<String, Any?>, onAnswer: (Map<String, Any?>?) -> Unit) {
        val s = socket()
        if (s == null) { post { onAnswer(null) }; return }
        try {
            s.ask(event, payload, Requests.TIMEOUT_MS) { a -> post { onAnswer(a) } }
        } catch (e: Exception) {
            post { onAnswer(null) }
        }
    }

    override fun list(onDone: (List<RequestSummary>?) -> Unit) = call(Requests.EVENT_LIST, who()) { a ->
        val list = a?.get("requests") as? List<*>
        onDone(if (a?.get("ok") == true && list != null) list.mapNotNull { Requests.summaryOf(it as? Map<*, *>) } else null)
    }

    override fun open(id: String, onDone: (RequestDetail?) -> Unit) {
        current = id
        call(Requests.EVENT_OPEN, who() + ("chatSessionId" to id)) { a ->
            onDone(if (a?.get("ok") == true) Requests.detailOf(a["request"] as? Map<*, *>) else null)
        }
    }

    override fun close(id: String) {
        if (current == id) current = null
        try {
            socket()?.emit(Requests.EVENT_CLOSE, mapOf("chatSessionId" to id))
        } catch (e: Exception) {
            // nothing to stop
        }
    }

    override fun reply(id: String, text: String, entryId: String, onDone: (ReplyResult) -> Unit) =
        call(Requests.EVENT_REPLY, who() + mapOf("chatSessionId" to id, "text" to text, "entryId" to entryId)) { a ->
            onDone(
                if (a == null) ReplyResult(false, reason = "offline")
                else ReplyResult(a["ok"] == true, Requests.detailOf(a["request"] as? Map<*, *>), a["reason"] as? String)
            )
        }

    override fun onUpdate(fn: (String) -> Unit): () -> Unit {
        val s = socket() ?: return {}
        val offUpdate = s.on(Requests.EVENT_UPDATE) { data ->
            val id = data?.get("chatSessionId") as? String
            if (id != null) post { fn(id) }
        }
        val offConnect = s.on("connect") {
            val id = current
            if (id != null) post { fn(id) }
        }
        return { offUpdate(); offConnect() }
    }
}
