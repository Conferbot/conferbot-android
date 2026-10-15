package com.conferbot.sdk.core.livechat

/**
 * Waiting for a person, and the signals app code can listen to. Ported from
 * the React Native SDK's src/core/handoverWaiting.ts, itself from the web
 * widget's src/ts/chat/handoverWaiting.ts (createConnectingSignal, the
 * signal names and details). The web dispatches the signals on its window;
 * here they go through [SignalBus], exposed as Conferbot.onWidgetSignal.
 *
 *   cb:waiting-for-human  the visitor now waits for a person
 *                         ([WidgetSignal.WaitingForHuman]). From the
 *                         server's handover-waiting, or the SDK's own
 *                         connecting wait after [CONNECTING_SIGNAL_MS].
 *   cb:human-joined       a person (never the AI) joined.
 *   cb:wait-over          the wait ended without a person (unavailable,
 *                         cancelled or ended).
 *
 * No personal data (the visitor's email) is ever put in a signal. Pure.
 */
object HandoverSignals {
    const val WAITING_FOR_HUMAN_EVENT = "cb:waiting-for-human"
    const val HUMAN_JOINED_EVENT = "cb:human-joined"
    const val WAIT_OVER_EVENT = "cb:wait-over"

    /** A connecting wait with nobody yet after this long counts as waiting for a person. */
    const val CONNECTING_SIGNAL_MS = 20_000L

    /** Chat closed when it was due: checked again this often until it opens. */
    const val CONNECTING_RECHECK_MS = 2_000L

    /** The server's handover-waiting payload as a signal. */
    fun waitingDetail(data: Map<String, Any?>?, emailCaptured: Boolean = false): WidgetSignal.WaitingForHuman =
        WidgetSignal.WaitingForHuman(
            offline = data?.get("offline") == true,
            reason = if (data?.get("offline") == true || data?.get("reason") == "offline") "offline" else "busy",
            backAt = data?.get("backAt") as? String,
            emailCaptured = emailCaptured || (data?.get("email") as? String).orEmpty().isNotEmpty()
        )
}

/** A widget signal and its detail. */
sealed class WidgetSignal(val name: String) {
    data class WaitingForHuman(val offline: Boolean, val reason: String, val backAt: String?, val emailCaptured: Boolean) :
        WidgetSignal(HandoverSignals.WAITING_FOR_HUMAN_EVENT)

    data class HumanJoined(val agentName: String?) : WidgetSignal(HandoverSignals.HUMAN_JOINED_EVENT)

    /** reason: unavailable, cancelled or ended. */
    data class WaitOver(val reason: String) : WidgetSignal(HandoverSignals.WAIT_OVER_EVENT)
}

/** A timer the signal can set and clear (a coroutine in the SDK, a fake clock in tests). */
interface SignalTimer {
    fun set(ms: Long, fn: () -> Unit): Any
    fun clear(handle: Any)
}

/**
 * The connecting wait's own cb:waiting-for-human, once per wait.
 *
 *   start()  a wait for a person began (or a restore re-entered one): fire
 *            after [HandoverSignals.CONNECTING_SIGNAL_MS] unless it ends
 *            first. A second start() during the same wait changes nothing.
 *   end()    a person joined, nobody could, the visitor left the queue, the
 *            chat ended, or the server sent its own handover-waiting: never
 *            fire for this wait.
 *
 * Due while the chat is closed ([canFire] false), it waits for the chat to
 * open rather than offering something nobody can see.
 */
class ConnectingSignal(
    private val timer: SignalTimer,
    private val canFire: () -> Boolean = { true },
    private val fire: () -> Unit
) {
    private var handle: Any? = null
    private var active = false

    private fun cancel() {
        handle?.let(timer::clear)
        handle = null
    }

    private fun due() {
        handle = null
        if (!active) return
        if (!canFire()) {
            handle = timer.set(HandoverSignals.CONNECTING_RECHECK_MS, ::due)
            return
        }
        active = false
        fire()
    }

    @Synchronized
    fun start() {
        // Already counting for this wait: once per wait, not once per event.
        if (active) return
        active = true
        cancel()
        handle = timer.set(HandoverSignals.CONNECTING_SIGNAL_MS, ::due)
    }

    @Synchronized
    fun end() {
        active = false
        cancel()
    }

    val pending: Boolean get() = active
}

/** A listener bus for the widget signals. emit never throws. */
class SignalBus {
    private val listeners = mutableMapOf<String, MutableSet<(WidgetSignal) -> Unit>>()

    /** Listen to one signal by name; returns the unsubscribe. */
    @Synchronized
    fun on(name: String, fn: (WidgetSignal) -> Unit): () -> Unit {
        listeners.getOrPut(name) { linkedSetOf() }.add(fn)
        return { synchronized(this) { listeners[name]?.remove(fn) } }
    }

    /** The number of listeners that took it. */
    fun emit(signal: WidgetSignal): Int {
        val fns = synchronized(this) { listeners[signal.name]?.toList().orEmpty() }
        var n = 0
        fns.forEach {
            try {
                it(signal)
                n++
            } catch (_: Throwable) {
                // A listener's error is its own.
            }
        }
        return n
    }
}
