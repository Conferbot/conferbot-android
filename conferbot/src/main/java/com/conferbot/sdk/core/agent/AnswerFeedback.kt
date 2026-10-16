package com.conferbot.sdk.core.agent

/** A visitor's vote on one AI answer: a No may carry a reason and a comment. */
data class AnswerFeedback(val helpful: Boolean, val reason: String? = null, val comment: String? = null)

/**
 * "Helpful?" under an AI answer, as the web widget's ts/chat/answerFeedback.ts
 * (ported from the React Native SDK's src/core/answerFeedback.ts).
 *
 *   ask     "Helpful?" with a thumb up and a thumb down, on the newest
 *           answer only (an older unrated one steps aside).
 *   up      sent at once ({helpful:true}); "Thanks for letting us know!",
 *           then the quiet rated line.
 *   down    sent at once ({helpful:false}) - a No never waits for a reason.
 *           "Sorry about that. What went wrong?" with four reasons; a reason
 *           is sent too and opens an optional comment. "Talk to a person"
 *           (only while nobody is coming or here) and "Ask another way"
 *           stay right there.
 *   rated   both thumbs, the chosen one pressed, and the reason. Tapping the
 *           other thumb changes the vote. A restored answer draws this.
 *
 * Pure: [step] says what a tap does (the vote to send and the view next);
 * the caller sends it (`ai-answer-feedback`) and keeps it on the answer's
 * record entry so a restore draws it.
 */
object Helpful {

    enum class View { ASK, THANKS, DOWN, RATED }

    /** The reasons and their words (web FEEDBACK_REASONS, i18n en). */
    val REASONS = listOf("wrong" to "Wrong", "outdated" to "Outdated", "unanswered" to "Didn't answer", "other" to "Other")

    // The web's English strings (ts/i18n/en.ts helpful.*).
    const val ASK = "Helpful?"
    const val YES = "Yes, helpful"
    const val NO = "No, not helpful"
    const val RATED_UP = "Helpful"
    const val RATED_DOWN = "Not helpful"
    const val THANKS = "Thanks for letting us know!"
    const val SORRY = "Sorry about that. What went wrong?"
    const val COMMENT = "Add a comment (optional)"
    const val SEND_FEEDBACK = "Send feedback"
    const val RETRY = "Ask another way"
    const val THANKS_COMMENT = "Thanks, that helps us improve."
    const val TALK_TO_PERSON = "Talk to a person"
    const val TALK_TO_PERSON_ASK = "I'd like to talk to a person, please."

    /** How long "Thanks for letting us know!" shows before the rated line (web: 1800 + 350 ms fade). */
    const val THANKS_MS = 1800L

    fun isReason(r: Any?): Boolean = REASONS.any { it.first == r }
    fun reasonLabel(r: String?): String = REASONS.firstOrNull { it.first == r }?.second ?: ""

    /** A comment as sent: one line, trimmed, at most 500 characters. */
    fun cleanComment(text: Any?): String = (text?.toString() ?: "").replace(Regex("\\s+"), " ").trim().take(500)

    /** A stored vote, or null when it is not one (web normalizeFeedback). */
    fun normalize(raw: Any?): AnswerFeedback? {
        if (raw is AnswerFeedback) return normalize(mapOf("helpful" to raw.helpful, "reason" to raw.reason, "comment" to raw.comment))
        val m = raw as? Map<*, *> ?: return null
        val helpful = m["helpful"] as? Boolean ?: return null
        if (helpful) return AnswerFeedback(true)
        val reason = (m["reason"] as? String)?.takeIf { isReason(it) }
        val comment = (m["comment"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(500)
        return AnswerFeedback(false, reason, comment)
    }

    /** The rated line's words: "Helpful", or "Not helpful · Outdated". */
    fun ratedLabel(fb: AnswerFeedback?): String = when {
        fb == null -> ""
        fb.helpful -> RATED_UP
        else -> RATED_DOWN + (fb.reason?.let { " · ${reasonLabel(it)}" } ?: "")
    }

    data class State(
        val view: View,
        val current: AnswerFeedback?,
        /** The comment went: the field is replaced by "Thanks, that helps us improve." */
        val commented: Boolean = false
    )

    sealed class Tap {
        data class Vote(val helpful: Boolean) : Tap()
        data class Reason(val reason: String) : Tap()
        data class Comment(val text: String) : Tap()
        object Human : Tap()
        object Retry : Tap()
        /** The thanks line's time is up. */
        object Settle : Tap()
    }

    /** What a tap does: the next state, the vote to send, the words to send ("Talk to a person") or focus the composer. */
    data class Step(val state: State, val send: AnswerFeedback? = null, val ask: String? = null, val focus: Boolean = false)

    /** Where a row starts: rated when the answer already has a vote. */
    fun start(initial: Any? = null): State {
        val current = normalize(initial)
        return State(if (current != null) View.RATED else View.ASK, current)
    }

    fun step(s: State, tap: Tap): Step = when (tap) {
        is Tap.Vote -> {
            // The same thumb again: nothing new.
            if (s.current != null && s.current.helpful == tap.helpful) Step(s)
            else AnswerFeedback(tap.helpful).let { fb -> Step(State(if (tap.helpful) View.THANKS else View.DOWN, fb), send = fb) }
        }
        is Tap.Reason -> {
            if (s.view != View.DOWN || !isReason(tap.reason)) Step(s)
            else AnswerFeedback(false, tap.reason).let { fb -> Step(s.copy(current = fb), send = fb) }
        }
        is Tap.Comment -> {
            if (s.view != View.DOWN || s.commented) Step(s)
            else {
                val comment = cleanComment(tap.text)
                // An empty comment sends nothing, and the field still says thanks (web).
                val fb = if (comment.isEmpty()) null else AnswerFeedback(false, s.current?.reason, comment)
                Step(s.copy(current = fb ?: s.current, commented = true), send = fb)
            }
        }
        Tap.Human -> Step(s.copy(view = View.RATED), ask = TALK_TO_PERSON_ASK)
        Tap.Retry -> Step(s.copy(view = View.RATED), focus = true)
        Tap.Settle -> if (s.view == View.THANKS && s.current?.helpful == true) Step(s.copy(view = View.RATED)) else Step(s)
    }

    /** The vote as the record entry keeps it (web _sendAnswerFeedback: `entry.feedback = { ...feedback, at }`). */
    fun entryFeedback(fb: AnswerFeedback, at: String): Map<String, Any?> = buildMap {
        put("helpful", fb.helpful)
        fb.reason?.let { put("reason", it) }
        fb.comment?.let { put("comment", it) }
        put("at", at)
    }

    /** The `ai-answer-feedback` payload. */
    fun payload(chatSessionId: String?, agentMessageId: String, fb: AnswerFeedback): Map<String, Any?> = buildMap {
        put("chatSessionId", chatSessionId)
        put("agentMessageId", agentMessageId)
        put("helpful", fb.helpful)
        fb.reason?.let { put("reason", it) }
        fb.comment?.let { put("comment", it) }
    }

    /**
     * Whether an answer draws its row: a rated one keeps its line; an unrated
     * one asks only while it is the newest rateable answer (an older one
     * steps aside, web answerFeedback.ts).
     */
    fun showsRow(answerId: String, rated: Boolean, newestRateableId: String?): Boolean = rated || answerId == newestRateableId
}
