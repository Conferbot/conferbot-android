package com.conferbot.sdk.core.delight

import com.conferbot.sdk.core.i18n.t
import com.conferbot.sdk.core.livechat.SignalTimer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The conversation's small human touches, as the web widget judges them.
 * Ported from the React Native SDK's src/core/delight.ts (conferbot-widget
 * src/ts/chat/delight/rules.ts: which answers can be changed and when going
 * back is safe, the bot's tapback, the moments that earn a celebration, the
 * gentle nudge; delight/index.ts the timings and the owner's switches;
 * delight/stallNudge.ts when the nudge shows; delight/celebrate.ts the
 * confetti's fan; ratingFx.ts the rating's sparks).
 *
 * Restraint is the point: these fire on the moments a person would react
 * to, and each can be switched off by the owner: customizations
 * botReactions, celebrations, stallNudges, allowChangeAnswer (all on unless
 * false). Motion is skipped for visitors who ask for less. Pure.
 */
object Delight {

    // ------------------------------------------------------------------
    // The owner's switches
    // ------------------------------------------------------------------

    const val BOT_REACTIONS = "botReactions"
    const val CELEBRATIONS = "celebrations"
    const val STALL_NUDGES = "stallNudges"
    const val ALLOW_CHANGE_ANSWER = "allowChangeAnswer"

    /** On unless the owner set it to false (web delight/index.ts `on`). */
    fun touchOn(c: Map<String, Any?>?, key: String): Boolean = c?.get(key) != false

    /** The reaction and the burst wait this long after the answer (web REACT_AFTER_MS). */
    const val REACT_AFTER_MS = 550L
    /** "Edit" shows once the bot has been quiet this long (web QUIET_BEFORE_CHANGE_MS). */
    const val QUIET_BEFORE_CHANGE_MS = 900L
    /** Only the recent past can be changed (web keeps 30 question points). */
    const val MAX_QUESTION_POINTS = 30

    // ------------------------------------------------------------------
    // Changing an answer
    // ------------------------------------------------------------------

    /** Questions whose answer can be taken back and given again. */
    val CHANGEABLE_TYPES: Set<String> = setOf(
        // v2 questions
        "ask-name-node", "ask-email-node", "ask-phone-number-node", "ask-number-node", "ask-url-node", "ask-location-node",
        "ask-custom-question-node",
        // choices
        "n-choices-node", "n-select-option-node", "n-check-options-node", "yes-or-no-choice-node", "image-choice-node",
        "rating-choice-node", "opinion-scale-choice-node", "dynamic-choices-node",
        // rich inputs with no side effect
        "slider-node", "datetime-node", "ranking-node", "matrix-node", "address-node",
        // classic
        "two-choices-node", "three-choices-node", "select-option-node", "user-rating-node", "user-range-node", "user-input-node"
    )

    /**
     * Steps that show something but act on the world (a booking made, a code
     * sent, a payment, a file uploaded, a signature given, an AI call): once
     * one has run, going back would not undo it, so the answer before it stays.
     */
    private val ACTS_ON_THE_WORLD = setOf(
        "calendar-node", "payment-node", "otp-node", "ask-file-node", "signature-node", "ai-extract-node", "ai-route-node",
        "kb-answer-node", "quiz-node", "consent-node", "ask-multiple-questions-node"
    )

    /** The web's display steps (core/node-processors/nodeTypeCategories.ts DISPLAY_NODE_TYPES). */
    private val DISPLAY_TYPES = setOf(
        "two-choices-node", "three-choices-node", "select-option-node", "user-rating-node", "quiz-node", "user-input-node",
        "user-range-node", "message-node", "image-node", "video-node", "audio-node", "file-node", "ask-name-node",
        "ask-email-node", "ask-phone-number-node", "ask-number-node", "ask-url-node", "ask-file-node", "ask-location-node",
        "ask-custom-question-node", "ask-multiple-questions-node", "calendar-node", "n-select-option-node",
        "n-check-options-node", "n-choices-node", "image-choice-node", "carousel-node", "rating-choice-node",
        "yes-or-no-choice-node", "opinion-scale-choice-node", "user-redirect-node", "html-node", "navigate-node",
        "dynamic-choices-node", "consent-node", "product-card-node", "slider-node", "datetime-node", "ranking-node",
        "otp-node", "address-node", "matrix-node", "ai-extract-node", "ai-route-node", "kb-answer-node", "signature-node",
        "payment-node"
    )

    /** The web's logic steps (LOGIC_NODE_TYPES). */
    private val LOGIC_TYPES = setOf(
        "condition-node", "boolean-logic-node", "random-flow-node", "math-operation-node", "variable-node", "jump-to-node",
        "business-hours-node"
    )

    /** A step after the answer that going back can safely undo. */
    fun isSafeToUndo(type: String): Boolean =
        (type in DISPLAY_TYPES && type !in ACTS_ON_THE_WORLD) || type in LOGIC_TYPES || type == "delay-node"

    /**
     * Can the visitor change their answer to `questionType`, given the step
     * types that have run since it was asked (the question itself excluded)?
     * A person in the conversation since then counts as "agent" (not safe).
     */
    fun canChangeAnswer(questionType: String, typesSince: List<String>): Boolean =
        questionType in CHANGEABLE_TYPES && typesSince.all(::isSafeToUndo)

    /** Steps that wait for the visitor's answer: the ones a question point is kept for. */
    fun waitsForAnswer(questionType: String): Boolean = questionType in CHANGEABLE_TYPES || questionType in ACTS_ON_THE_WORLD

    /**
     * Whether "Edit" shows under the visitor's latest answer: the owner allows
     * it, nothing locks the conversation (a person in it, or over), the bot is
     * not mid-reply, the answer is the visitor's latest bubble, and nothing
     * irreversible ran since (web delight/index.ts refreshChange).
     */
    fun showChangeFor(
        customizations: Map<String, Any?>?,
        locked: Boolean,
        botBusy: Boolean,
        isLatestAnswer: Boolean,
        questionType: String,
        typesSince: List<String>
    ): Boolean = touchOn(customizations, ALLOW_CHANGE_ANSWER) && !locked && !botBusy && isLatestAnswer &&
        canChangeAnswer(questionType, typesSince)

    // ------------------------------------------------------------------
    // Reading an answer
    // ------------------------------------------------------------------

    private val FRACTION = Regex("^(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+)\\b")

    /**
     * A rating or scale answer as a share of the best (0 to 1), from the reply
     * text: "★★★★ Good" (stars out of 5), "8/10". null when not a score.
     */
    fun scoreShare(text: String?): Double? {
        val s = text.orEmpty().trim()
        FRACTION.find(s)?.let { m ->
            val maxV = m.groupValues[2].toDouble()
            return if (maxV > 0) max(0.0, min(1.0, m.groupValues[1].toDouble() / maxV)) else null
        }
        val stars = s.count { it == '★' || it == '⭐' }
        if (stars > 0) return min(1.0, stars / 5.0)
        return null
    }

    private val SCORE_TYPES = setOf("rating-choice-node", "opinion-scale-choice-node", "user-rating-node")

    /** The answer's text as the web reads it for a score: "n/max". */
    fun scoreText(questionType: String, value: Double?, max: Double?): String {
        if (questionType !in SCORE_TYPES || value == null || !value.isFinite() || max == null || max <= 0) return ""
        fun n(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()
        return "${n(value)}/${n(max)}"
    }

    // ------------------------------------------------------------------
    // The bot's reaction
    // ------------------------------------------------------------------

    const val WAVE = "👋"
    const val PARTY = "🎉"
    const val HEART = "❤️"
    const val THUMBS_UP = "👍"
    const val THANKS = "🙏"

    /**
     * The tapback the bot puts on the visitor's answer, or null for none.
     * Names get a wave, scores a heart or a thumbs up (a low score a quiet
     * thank-you, never a cheer), a booking or payment a party. Everything
     * else goes unremarked.
     */
    fun reactionFor(questionType: String, answerText: String?): String? {
        if (questionType == "ask-name-node") return WAVE
        if (questionType == "calendar-node" || questionType == "payment-node") return PARTY
        if (questionType in SCORE_TYPES) {
            val s = scoreShare(answerText) ?: return null
            return if (s >= 0.8) HEART else if (s >= 0.55) THUMBS_UP else THANKS
        }
        return null
    }

    private val REACTION_LABELS = mapOf(
        WAVE to "reaction.waved", PARTY to "reaction.celebrated", HEART to "reaction.loved",
        THUMBS_UP to "reaction.liked", THANKS to "reaction.thanked"
    )

    /** What a screen reader says for the badge (web reactions.ts LABELS). */
    fun reactionLabel(emoji: String): String = t(REACTION_LABELS[emoji] ?: "reaction.reacted")

    // ------------------------------------------------------------------
    // Celebrations
    // ------------------------------------------------------------------

    /** The moments that earn a burst: a booking made, a payment through, a top score. */
    fun celebrates(questionType: String, answerText: String?): Boolean {
        if (questionType == "calendar-node" || questionType == "payment-node") return true
        if (questionType in SCORE_TYPES) {
            val s = scoreShare(answerText)
            // The rating card already sparks on a pick; a perfect score adds the burst.
            return s != null && s >= 0.999 && questionType != "rating-choice-node"
        }
        return false
    }

    data class Touches(val reaction: String?, val celebrate: Boolean)

    /** What an answer gets, a beat after it lands: a tapback and / or a burst (web delight/index.ts onAnswer). */
    fun touchesFor(c: Map<String, Any?>?, questionType: String, answerText: String?, reducedMotion: Boolean): Touches = Touches(
        reaction = if (touchOn(c, BOT_REACTIONS)) reactionFor(questionType, answerText) else null,
        celebrate = !reducedMotion && touchOn(c, CELEBRATIONS) && celebrates(questionType, answerText)
    )

    /** Pieces in a burst (web celebrate.ts PIECES). */
    const val CONFETTI_PIECES = 28
    /** The burst is gone after this (web: the pieces removed at 1600 ms). */
    const val CONFETTI_MS = 1600L

    /** A confetti piece: where it flies (px), its fall, spin, start delay, colour (0-3) and shape. */
    data class ConfettiPiece(val dx: Int, val dy: Int, val fall: Int, val rot: Int, val delay: Int, val tone: Int, val round: Boolean)

    private fun jsRound(d: Double): Int = Math.floor(d + 0.5).toInt()

    /**
     * The burst's pieces: a fan that opens up, -160 to -20 degrees with a
     * little jitter, 56-116 px out, then a 26-56 px fall (web celebrate.ts).
     */
    fun confettiPieces(random: () -> Double = Math::random): List<ConfettiPiece> = (0 until CONFETTI_PIECES).map { i ->
        val angle = (-160 + (140.0 * i) / (CONFETTI_PIECES - 1) + (random() * 14 - 7)) * (PI / 180)
        val reach = 56 + random() * 60
        ConfettiPiece(
            dx = jsRound(cos(angle) * reach),
            dy = jsRound(sin(angle) * reach),
            fall = jsRound(26 + random() * 30),
            rot = jsRound(random() * 540 - 270),
            delay = jsRound(random() * 90),
            tone = i % 4,
            round = i % 3 == 0
        )
    }

    /** The rating's sparks on a pick: 8, or 12 for the top score, evenly round from 20 degrees (web ratingFx.ts burst). */
    fun ratingSparks(top: Boolean): List<Double> {
        val count = if (top) 12 else 8
        return (0 until count).map { (360.0 / count) * it + 20 }
    }

    /** The sparks are gone after this (web: removed at 1000 ms). */
    const val RATING_SPARKS_MS = 1000L

    // ------------------------------------------------------------------
    // A gentle nudge when a question waits
    // ------------------------------------------------------------------

    /** How long a question waits, with the chat open and nobody busy, before the nudge. */
    const val STALL_AFTER_MS = 40_000L
    /** Recent activity (typing, a touch, a scroll) in the chat postpones it this long. */
    const val STALL_QUIET_MS = 15_000L

    private val TYPED = setOf(
        "ask-name-node", "ask-email-node", "ask-phone-number-node", "ask-number-node", "ask-url-node", "ask-location-node",
        "ask-custom-question-node", "user-input-node", "user-range-node"
    )

    /** What the nudge says: picking or typing, never pushy. */
    fun stallHintFor(questionType: String): String = if (questionType in TYPED) t("nudge.stallType") else t("nudge.stallPick")

    /** Whether a question shows a nudge at all (not for steps that already wait on their own). */
    fun nudgesWhileWaiting(questionType: String): Boolean =
        questionType in CHANGEABLE_TYPES || questionType == "calendar-node" || questionType == "consent-node"

    /**
     * The nudge's timing (web delight/stallNudge.ts): [STALL_AFTER_MS] after
     * the question, unless the visitor was busy in the last [STALL_QUIET_MS],
     * the chat is not on screen, or something is typed; then it checks again
     * every [STALL_QUIET_MS]. Once per question.
     */
    class StallWatch(
        /** The chat open, on the conversation, and the app in front. */
        private val isVisible: () -> Boolean,
        private val composerValue: () -> String,
        private val show: (String) -> Unit,
        private val hide: () -> Unit,
        private val timer: SignalTimer,
        private val now: () -> Long = { System.currentTimeMillis() }
    ) {
        private var handle: Any? = null
        private var armedType = ""
        private var armed = false
        private var shown = false
        private var lastActivity = now()

        private fun clear() {
            handle?.let { timer.clear(it) }
            handle = null
        }

        private fun check() {
            handle = null
            if (shown || !armed) return
            val busy = now() - lastActivity < STALL_QUIET_MS
            if (busy || !isVisible() || composerValue().trim().isNotEmpty()) {
                handle = timer.set(STALL_QUIET_MS) { check() }
                return
            }
            shown = true
            show(stallHintFor(armedType))
        }

        /** Start (or restart) the wait for the question now on screen. */
        fun arm(questionType: String) {
            armedType = questionType
            armed = true
            if (shown) return
            clear()
            handle = timer.set(STALL_AFTER_MS) { check() }
        }

        /** They answered, or the question went away: the nudge goes. */
        fun disarm() {
            clear()
            val was = shown
            armed = false
            shown = false
            if (was) hide()
        }

        /** The visitor touched, scrolled or typed in the chat. */
        fun touch() {
            lastActivity = now()
        }
    }

    /** A colour (0xRRGGBB) mixed with white (CSS color-mix(in srgb, c share, #fff)). */
    fun mixWithWhite(rgb: Int, share: Double): Int {
        fun c(v: Int) = (v * share + 255 * (1 - share)).roundToInt()
        return (c((rgb shr 16) and 255) shl 16) or (c((rgb shr 8) and 255) shl 8) or c(rgb and 255)
    }
}
