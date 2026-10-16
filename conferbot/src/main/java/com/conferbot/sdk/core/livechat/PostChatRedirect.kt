package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.i18n.t
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Post-chat redirect (Customize > Conversations > Post-chat redirect, the
 * bot's redirectUrl), ported from React Native's core/postChatRedirect.ts
 * (and iOS's Core/LiveChat/PostChatRedirect.swift), the web widget's
 * ts/chat/postChatRedirect.ts and docs/redirect-after-chat.md. When a
 * conversation ends - the flow reaches its end, or an agent ends a chat
 * they took over - and the post-chat rating is sent or put away (or there
 * is none), a quiet line counts down 10 seconds with Cancel, then the
 * owner's URL opens outside the chat (the system browser).
 *
 *   - only http(s) URLs with a host: javascript:, data:, mailto:, a bare
 *     domain and anything with a user:password@ part are ignored
 *   - the app's `onPostChatRedirect(url)` is asked first: true means the
 *     app handled it (or wants none), and no countdown is shown
 *   - once per conversation: remembered when the countdown starts, so a
 *     cancelled one, an agent ending the chat again or a restored ended
 *     chat does not count down a second time
 *   - it stops on Cancel, a new conversation, the chat closed, a block, a
 *     person back in it, the app in the background
 *   - when the system cannot open it, the line becomes "Continue to
 *     example.com" the visitor can tap
 */
object PostChatRedirect {
    /** Seconds the line counts down. */
    const val SECONDS = 10
    /** The rating shows 1.5 s after the end (web index.ts); the line waits for it. */
    const val SETTLE_MS = 1500L
    /** The device remembers an offered conversation under this + chatSessionId. */
    const val DONE_KEY_PREFIX = "conferbot_redirect_done_"
    internal const val MAX_URL_LENGTH = 2048

    private data class Parts(val scheme: String, val host: String, val port: String, val rest: String)

    private val URL = Regex("^(https?)://([^/?#\\\\]*)([\\s\\S]*)$", RegexOption.IGNORE_CASE)
    private val V6 = Regex("^(\\[[0-9a-fA-F:.]+\\])(?::(\\d*))?$")
    private val DIGITS = Regex("^\\d*$")
    private val DOTS = Regex("^\\.+$")
    // A host may not hold these (WHATWG forbidden host code points).
    private val FORBIDDEN_HOST = Regex("[\\s#%/:<>?@\\[\\\\\\]^|\\x00-\\x1f\\x7f]")
    private val CONTROLS = Regex("[\\x00-\\x1f\\x7f]")

    private fun parse(href: String): Parts? {
        val m = URL.find(href) ?: return null
        val scheme = m.groupValues[1].lowercase()
        val authority = m.groupValues[2]
        // A user:password@ (or any user@) part.
        if (authority.contains('@')) return null
        var host = authority
        var port = ""
        val v6 = V6.find(authority)
        if (v6 != null) {
            host = v6.groupValues[1]
            port = v6.groupValues[2]
        } else {
            val at = authority.lastIndexOf(':')
            if (at >= 0) {
                port = authority.substring(at + 1)
                host = authority.substring(0, at)
                if (!DIGITS.matches(port)) return null
            }
            if (host.isEmpty() || FORBIDDEN_HOST.containsMatchIn(host) || DOTS.matches(host)) return null
        }
        if (port.isNotEmpty() && (port.toBigIntegerOrNull() ?: return null) > 65535.toBigInteger()) return null
        host = host.lowercase()
        port = port.trimStart('0').ifEmpty { if (port.isEmpty()) "" else "0" }
        if (port == (if (scheme == "http") "80" else "443")) port = ""
        // The path, query and fragment as a browser writes them.
        var rest = m.groupValues[3].replace('\\', '/').replace(CONTROLS, "").replace(" ", "%20")
        if (!rest.startsWith("/")) rest = "/$rest"
        return Parts(scheme, host, port, rest)
    }

    /** The owner's URL when it is a plain http(s) address, else null. */
    fun safeUrl(raw: Any?): String? {
        val value = (raw as? String)?.trim() ?: return null
        if (value.isEmpty() || value.length > MAX_URL_LENGTH) return null
        val u = parse(value) ?: return null
        return "${u.scheme}://${u.host}${if (u.port.isEmpty()) "" else ":${u.port}"}${u.rest}"
    }

    /** "www.example.com" -> "example.com", for the line the visitor reads. */
    fun host(href: String): String {
        val u = parse(href.trim()) ?: return ""
        return if (u.host.startsWith("www.")) u.host.drop(4) else u.host
    }

    /** This conversation already counted down (or was cancelled, or the app took it). */
    fun alreadyOffered(chatSessionId: String, store: RedirectStore?): Boolean {
        if (chatSessionId.isEmpty() || store == null) return false
        return try { store.read(DONE_KEY_PREFIX + chatSessionId) == "1" } catch (e: Exception) { false }
    }
}

/** Where an offered conversation is remembered (the SDK's SharedPreferences; the web's localStorage). */
interface RedirectStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

/** The line under the conversation. */
data class RedirectLine(
    val href: String,
    val host: String,
    val phase: Phase,
    val secondsLeft: Int,
    /** What the line shows now. */
    val text: String,
    /** What a screen reader hears: the first sentence, said once (not every tick). */
    val said: String,
    /** The Cancel button's text and its accessible label. */
    val cancelText: String,
    val cancelLabel: String
) {
    enum class Phase {
        /** "Taking you to x in Ns" with Cancel. */
        COUNTING,
        /** "Taking you to x…": it opened. */
        GOING,
        /** "Continue to x": the system could not open it, the visitor can. */
        CONTINUE
    }
}

/**
 * One per chat: when to start, the countdown, the conversations already
 * offered, the line. The SDK calls [conversationEnded] at the flow's end and
 * when an agent ends a chat with no flow going on from it, [update] on every
 * change the hooks read, [cancel] when the app goes to the background and
 * [reset] on a new chat.
 */
class PostChatRedirectController(
    private val hooks: Hooks,
    private val timer: SignalTimer,
    seconds: Int = PostChatRedirect.SECONDS
) {
    enum class Start { NONE, HANDLED, COUNTING }

    class Hooks(
        /** The bot's redirectUrl (fetched-chatbot-data). */
        val redirectUrl: () -> Any?,
        val sessionId: () -> String?,
        /** The chat is on screen, on the conversation. */
        val chatVisible: () -> Boolean,
        /** The workspace blocked this visitor. */
        val blocked: () -> Boolean,
        /** A person (or a handover) is in the conversation. */
        val live: () -> Boolean,
        /** The post-chat rating is up (or about to be) and not answered yet. */
        val ratingPending: () -> Boolean,
        /** Opens the URL outside the chat; false when the system refused. */
        val open: (href: String) -> Boolean,
        /** The app's config: never redirect. */
        val disabled: () -> Boolean = { false },
        /** The app's hook, asked first: true = handled, no countdown. */
        val onPostChatRedirect: () -> ((String) -> Boolean)? = { null },
        val store: () -> RedirectStore? = { null },
        val appInForeground: () -> Boolean = { true },
        /** The countdown started: say [RedirectLine.said] once to a screen reader. */
        val announce: (String) -> Unit = {}
    )

    private val countdown = seconds.coerceAtLeast(1)
    private val _line = MutableStateFlow<RedirectLine?>(null)
    /** The line on screen now (null: none). */
    val line: StateFlow<RedirectLine?> = _line.asStateFlow()

    private val offeredHere = mutableSetOf<String>()
    // One try per end, as the web's _afterPostChat (an offered conversation
    // is never offered again: offeredHere and the store).
    private val tried = mutableSetOf<String>()
    private var ended: String? = null
    private var active: String? = null
    private var tick: Any? = null
    private var settle: Any? = null

    /** The conversation the countdown (or the line) is for. */
    val activeSession: String? get() = active

    private fun canGo(session: String): Boolean =
        ended == session && (hooks.sessionId() ?: "") == session && hooks.chatVisible() &&
            !hooks.blocked() && !hooks.live() && hooks.appInForeground()

    /** The conversation on screen ended (the flow's end, an agent ending it). */
    fun conversationEnded() {
        val session = hooks.sessionId() ?: ""
        if (session.isEmpty()) return
        ended = session
        tried.remove(session)
        settle?.let { timer.clear(it) }
        settle = null
        update()
    }

    /**
     * Anything the chat shows changed: wait for the rating, try once after
     * the settle; a countdown that can no longer go stops.
     */
    fun update() {
        active?.let { if (!canGo(it)) cancel() }
        val e = ended ?: return
        if ((hooks.sessionId() ?: "") != e) {
            ended = null
            settle?.let { timer.clear(it) }
            settle = null
            return
        }
        if (e in tried) return
        if (hooks.ratingPending()) {
            settle?.let { timer.clear(it) }
            settle = null
            return
        }
        if (settle != null) return
        settle = timer.set(PostChatRedirect.SETTLE_MS) {
            settle = null
            if (ended != e || hooks.ratingPending()) return@set
            tried += e
            start(e)
        }
    }

    /**
     * The app's hook, else the countdown. NONE when there is nothing to do
     * (no valid URL, turned off, already offered, not on screen).
     */
    fun start(session: String): Start {
        val href = PostChatRedirect.safeUrl(hooks.redirectUrl()) ?: return Start.NONE
        if (hooks.disabled()) return Start.NONE
        if (session in offeredHere || active != null) return Start.NONE
        val store = hooks.store()
        if (PostChatRedirect.alreadyOffered(session, store)) {
            offeredHere += session
            return Start.NONE
        }
        if (!canGo(session)) return Start.NONE
        offeredHere += session
        if (session.isNotEmpty()) try { store?.write(PostChatRedirect.DONE_KEY_PREFIX + session, "1") } catch (e: Exception) {
            // storage refused: the in-memory guard still holds while the app runs
        }

        val hook = hooks.onPostChatRedirect()
        if (hook != null && (try { hook(href) } catch (e: Exception) { false })) return Start.HANDLED

        val host = PostChatRedirect.host(href)
        val first = t("redirect.countdown", "host" to host, "n" to countdown)
        active = session
        _line.value = RedirectLine(
            href = href, host = host, phase = RedirectLine.Phase.COUNTING, secondsLeft = countdown,
            text = first, said = first,
            cancelText = t("common.cancel"),
            cancelLabel = t("redirect.cancel", "host" to host)
        )
        hooks.announce(first)
        scheduleTick(session, countdown)
        return Start.COUNTING
    }

    private fun scheduleTick(session: String, left: Int) {
        tick = timer.set(1000L) {
            val l = _line.value
            if (active != session || l == null) return@set
            if (!canGo(session)) return@set cancel()
            val now = left - 1
            if (now > 0) {
                _line.value = l.copy(secondsLeft = now, text = t("redirect.countdown", "host" to l.host, "n" to now))
                return@set scheduleTick(session, now)
            }
            tick = null
            go(l, session)
        }
    }

    private fun go(l: RedirectLine, session: String) {
        val opened = try { hooks.open(l.href) } catch (e: Exception) { false }
        val line = _line.value
        if (active != session || line == null) return
        val text = if (opened) t("redirect.going", "host" to l.host) else t("redirect.continue", "host" to l.host)
        _line.value = line.copy(
            phase = if (opened) RedirectLine.Phase.GOING else RedirectLine.Phase.CONTINUE,
            secondsLeft = 0, text = text, said = text
        )
    }

    /** "Continue to example.com" tapped: open it now. */
    fun continueNow(): Boolean {
        val l = _line.value ?: return false
        if (l.phase != RedirectLine.Phase.CONTINUE) return false
        val opened = try { hooks.open(l.href) } catch (e: Exception) { false }
        if (opened && _line.value == l) cancel()
        return opened
    }

    /** Stop a countdown and take the line away (Cancel, the chat closed, the app in the background). */
    fun cancel() {
        tick?.let { timer.clear(it) }
        tick = null
        active = null
        if (_line.value != null) _line.value = null
    }

    /** A restart: the countdown stops and the next conversation gets its own. */
    fun reset() {
        cancel()
        settle?.let { timer.clear(it) }
        settle = null
        ended = null
    }
}
