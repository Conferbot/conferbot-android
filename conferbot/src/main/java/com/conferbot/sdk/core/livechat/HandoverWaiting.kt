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

// ---------------------------------------------------------------------------
// The AI escalation's max wait (web handoverWaiting.ts armAIEscalationNudge,
// special.ts _handleAIEscalationWait; RN src/core/handoverWaiting.ts)
// ---------------------------------------------------------------------------

object EscalationWait {
    /** After an AI escalation's max wait, how much longer the server is given before it is nudged. */
    const val SERVER_GRACE_MS = 30_000L

    /** The handover step's max wait (minutes; 2 when unset, 0 or less) plus the grace, in ms. */
    fun nudgeMs(maxWaitTimeMinutes: Any?): Long {
        val minutes = minutesOf(maxWaitTimeMinutes)
        return Math.round(minutes * 60_000) + SERVER_GRACE_MS
    }

    /** A step's max wait in minutes: a number above 0 (or its text), else 2. */
    fun minutesOf(v: Any?): Double {
        val n = when (v) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            else -> null
        }
        return if (n != null && n > 0 && !n.isNaN() && !n.isInfinite()) n else 2.0
    }

    /**
     * Does this agent-left start the escalation's wait? Only an AI agent
     * handing the visitor on: a person leaving is not an escalation (nor is a
     * person's own handover), and an inactivity close ends the chat
     * (ai-agent-inactive-close).
     */
    fun starts(data: Map<String, Any?>?): Boolean =
        (data?.get("agentDetails") as? Map<*, *>)?.get("isAIAgent") == true && data["reason"] != "visitor_inactive"

    /**
     * The handover step an escalation waits on: the flow's current handover
     * step, else (an agent bot, whose silent handover is not a flow step) the
     * latest handover step in the record.
     */
    fun handoverData(stepData: Map<String, Any?>?, record: List<com.conferbot.sdk.core.state.RecordEntry>?): Map<String, Any?>? {
        if (stepData != null) return stepData
        val list = record.orEmpty()
        for (i in list.indices.reversed()) {
            val r = list[i]
            if ((r.type == "human-handover-node" || r.shape == "human-handover-node") && r.data.isNotEmpty()) return r.data
        }
        return null
    }
}

/**
 * After the AI escalates, the server owns the wait: it hands the chat back
 * to the AI and says so (handover-waiting), or sends no-agents-available.
 * This timer never draws anything; it only nudges (handover-timeout) a
 * server that lost its own timer, the step's max wait plus 30s after the AI
 * left.
 *
 *   arm(maxWait)  the AI escalated: (re)start the timer (one per chat).
 *   clear()       someone accepted, the server's handover-waiting or
 *                 no-agents-available came, the visitor left the queue, the
 *                 chat ended or closed for inactivity, the visitor was
 *                 blocked.
 */
class EscalationNudge(private val timer: SignalTimer, private val nudge: () -> Unit) {
    private var handle: Any? = null

    @Synchronized
    fun arm(maxWaitTimeMinutes: Any?) {
        clear()
        handle = timer.set(EscalationWait.nudgeMs(maxWaitTimeMinutes)) {
            synchronized(this) { handle = null }
            nudge()
        }
    }

    @Synchronized
    fun clear() {
        handle?.let(timer::clear)
        handle = null
    }

    val pending: Boolean @Synchronized get() = handle != null
}
