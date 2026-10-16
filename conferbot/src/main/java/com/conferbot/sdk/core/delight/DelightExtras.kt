package com.conferbot.sdk.core.delight

import com.conferbot.sdk.core.livechat.SignalTimer
import com.conferbot.sdk.core.nodes.NodeUIState
import com.conferbot.sdk.models.RecordItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a visitor's answer bubble answered: the question's step, its type, and its text as a score where it is one ("4/5"). */
data class AnswerOf(val nodeId: String, val type: String, val text: String)

/** The touches an answer got: the bot's tapback, and a burst while [celebrate] is set (a new value restarts it). */
data class AnswerTouch(val reaction: String? = null, val celebrate: Long? = null)

/** The engine's side of "Edit" (NodeFlowEngine): its question points, what ran since one, going back to one. */
interface ChangeAnswerEngine {
    /** (point id, node id, step type) of each question asked, oldest first. */
    fun points(): List<Triple<Int, String, String>>
    fun typesSince(pointId: Int): List<String>
    fun rewindTo(pointId: Int): Boolean
}

/**
 * The conversation's human touches, wired (core/delight/Delight.kt; RN
 * context/hooks/useDelight.ts; web ts/chat/delight/index.ts):
 *
 *   - the bot reacts to an answer that deserves it, 550 ms after it lands;
 *   - bookings, payments and perfect scores celebrate (not with reduced motion);
 *   - a question that waits a long while gets a gentle nudge;
 *   - the latest answer can be changed with one tap ("Edit"), which takes the
 *     conversation back to that question ([ChangeAnswerEngine.rewindTo]).
 *
 * An answer is a [RecordItem.UserInputResponse] stamped with [AnswerOf]. Each
 * touch follows the owner's setting (all on unless false). Screen-only:
 * nothing here is saved or sent.
 */
class DelightExtras(
    private val customizations: () -> Map<String, Any?>,
    private val engine: () -> ChangeAnswerEngine?,
    private val record: () -> List<RecordItem>,
    private val setRecord: ((List<RecordItem>) -> List<RecordItem>) -> Unit,
    /** A person or the AI agent is in the conversation, or it is over: no going back. */
    private val locked: () -> Boolean,
    /** The chat open, on the conversation, and the app in front. */
    private val chatVisible: () -> Boolean,
    private val reducedMotion: () -> Boolean,
    private val timer: SignalTimer,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val _touches = MutableStateFlow<Map<String, AnswerTouch>>(emptyMap())
    val touches: StateFlow<Map<String, AnswerTouch>> = _touches.asStateFlow()

    private val _changeRowId = MutableStateFlow<String?>(null)
    /** The answer row that shows "Edit" under it. */
    val changeRowId: StateFlow<String?> = _changeRowId.asStateFlow()

    private val _stallHint = MutableStateFlow<String?>(null)
    /** "No rush - ..." under a question that has waited. */
    val stallHint: StateFlow<String?> = _stallHint.asStateFlow()

    private var composer = ""
    private val stall = Delight.StallWatch(
        isVisible = chatVisible,
        composerValue = { composer },
        show = { _stallHint.value = it },
        hide = { _stallHint.value = null },
        timer = timer,
        now = now
    )

    // ---- The nudge ----

    private var armedFor: String? = null

    /** The flow's step on screen changed: a new question (re)starts the wait. */
    fun onUiState(ui: NodeUIState?) {
        val step = ui?.step
        val key = if (step != null && step.nodeId.isNotEmpty()) "${step.nodeId}:${step.type}:${step.visit}" else null
        if (key != armedFor) {
            armedFor = key
            stall.disarm()
            if (step != null && key != null && Delight.touchOn(customizations(), Delight.STALL_NUDGES) && Delight.nudgesWhileWaiting(step.type)) stall.arm(step.type)
        }
        refreshChange()
    }

    /** The visitor touched, scrolled or typed in the chat. */
    fun noteActivity() = stall.touch()

    /** What is in the composer (a nudge waits while something is typed). */
    fun setComposerValue(v: String) {
        composer = v
        stall.touch()
    }

    // ---- Reactions and celebrations ----

    private var seen: Set<String> = emptySet()
    private var primed = false
    private var busy = false

    /** The conversation changed: answers given just now get their touches a beat later. */
    fun onRecord(items: List<RecordItem>) {
        val ids = items.mapTo(HashSet()) { it.id }
        // What was on screen first (restored) gets nothing.
        if (!primed) {
            primed = true
            seen = ids
            refreshChange()
            return
        }
        val t = now()
        val fresh = items.filterIsInstance<RecordItem.UserInputResponse>()
            .filter { it.answerOf != null && it.id !in seen && t - it.time.time <= 30_000 }
        seen = ids
        for (row in fresh) {
            stall.disarm()
            val of = row.answerOf ?: continue
            timer.set(Delight.REACT_AFTER_MS) {
                val touch = Delight.touchesFor(customizations(), of.type, of.text.ifEmpty { row.text }, reducedMotion())
                if (touch.reaction != null || touch.celebrate) {
                    _touches.value = _touches.value + (row.id to AnswerTouch(touch.reaction, if (touch.celebrate) now() else null))
                }
            }
        }
        refreshChange()
    }

    /** The burst finished. */
    fun clearTouch(id: String) {
        val cur = _touches.value[id] ?: return
        if (cur.celebrate != null) _touches.value = _touches.value + (id to cur.copy(celebrate = null))
    }

    /** The bot started or stopped working on a reply. */
    fun onBusy(isBusy: Boolean) {
        if (busy == isBusy) return
        busy = isBusy
        refreshChange()
    }

    // ---- "Edit" on the latest answer, once the bot has finished replying ----

    private var changeTimer: Any? = null

    private fun latestAnswer(items: List<RecordItem>): RecordItem.UserInputResponse? =
        items.lastOrNull { it is RecordItem.UserInputResponse || it is RecordItem.UserMessage } as? RecordItem.UserInputResponse

    private fun pointFor(of: AnswerOf): Triple<Int, String, String>? = engine()?.points()?.lastOrNull { it.second == of.nodeId }

    fun refreshChange() {
        _changeRowId.value = null
        changeTimer?.let { timer.clear(it) }
        changeTimer = timer.set(Delight.QUIET_BEFORE_CHANGE_MS) {
            changeTimer = null
            val e = engine() ?: return@set
            val last = latestAnswer(record()) ?: return@set
            val of = last.answerOf ?: return@set
            val point = pointFor(of) ?: return@set
            val show = Delight.showChangeFor(customizations(), locked(), busy, true, point.third, e.typesSince(point.first))
            if (show) _changeRowId.value = last.id
        }
    }

    /** "Edit": cut the conversation at the question and ask it again. */
    fun changeAnswer() {
        val e = engine() ?: return
        val items = record()
        val index = items.indexOfFirst { it.id == _changeRowId.value }
        val row = items.getOrNull(index) as? RecordItem.UserInputResponse ?: return
        val of = row.answerOf ?: return
        val point = pointFor(of) ?: return
        val cut = cutIndexFor(items, index, of.nodeId)
        _changeRowId.value = null
        stall.disarm()
        armedFor = null
        setRecord { prev -> prev.take(minOf(cut, prev.size)) }
        e.rewindTo(point.first)
    }

    /** A new conversation: what is on screen next is counted from nothing. */
    fun reset() {
        primed = false
        seen = emptySet()
        armedFor = null
        stall.disarm()
        _touches.value = emptyMap()
        _changeRowId.value = null
    }

    companion object {
        /** The step a row belongs to: a question's bubble or its frozen choices, an answer. */
        fun nodeIdOf(item: RecordItem): String? = when (item) {
            is RecordItem.BotMessage -> item.nodeId
            is RecordItem.UserInputResponse -> item.answerOf?.nodeId
            else -> null
        }

        /**
         * Where "Edit" cuts the conversation: the question's own rows just above
         * the answer (its bubble, its frozen choices), else the answer itself.
         */
        fun cutIndexFor(items: List<RecordItem>, answerIndex: Int, nodeId: String): Int {
            var cut = answerIndex
            for (i in answerIndex - 1 downTo 0) {
                if (nodeIdOf(items[i]) != nodeId) break
                cut = i
            }
            return cut
        }
    }
}
