package com.conferbot.sdk.core.livechat

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Live chat around a person or the AI, as the web widget shows it: where the
 * visitor stands in the queue, the "Sent / Delivered / Seen" line under their
 * latest message, an agent's translated message, the cards that keep a
 * conversation going when nobody can join (leave a message, an email for the
 * reply), and the post-chat rating. The shapes are the embed-server's
 * (queueService, readReceipts, liveTranslation, aiFallback, handoverHandler).
 * Pure: works on the maps the socket JSON is converted to, no Android types.
 */

data class QueueStatus(
    val status: String,
    val position: Int?,
    val ahead: Int?,
    val agentsOnline: Int?,
    val estimateLabel: String?
)

data class HandoverWaiting(
    val offline: Boolean,
    val reason: String,
    val backAt: String?,
    val askEmail: Boolean,
    val email: String?
)

data class AgentTranslation(val from: String, val to: String, val original: String)

data class ReceiptMarks(val deliveredMessageId: String = "", val seenMessageId: String = "")

enum class ReceiptState { SENT, DELIVERED, SEEN }

data class VisitorReceipt(val id: String, val state: ReceiptState)

/** Post-chat rating: from the bot's csatConfig; on only with enabled = true. */
data class CsatConfig(
    val ratingType: String,
    val titleText: String,
    val subtitleText: String,
    val thankYouText: String,
    val showFeedbackInput: Boolean,
    val feedbackPlaceholder: String
)

/** A record entry as the receipt logic needs it. */
data class LiveEntry(val id: String, val kind: String)

object LiveChat {

    private fun num(v: Any?): Int? = when (v) {
        is Int -> v
        is Long -> v.toInt()
        is Double -> if (v.isFinite()) v.toInt() else null
        is Number -> v.toInt()
        else -> null
    }

    private fun str(v: Any?): String? = (v as? String)?.trim()?.takeIf { it.isNotEmpty() }

    /** queue-status as the visitor sees it, or null when they are not waiting. */
    fun queueStatusOf(raw: Map<String, Any?>?): QueueStatus? {
        raw ?: return null
        val status = (raw["status"] as? String)?.takeIf { it == "waiting" || it == "busy" } ?: return null
        val estimate = raw["estimate"] as? Map<*, *>
        return QueueStatus(status, num(raw["position"]), num(raw["ahead"]), num(raw["agentsOnline"]), str(estimate?.get("label")))
    }

    /** "1st", "2nd", "3rd", "11th" */
    fun ordinal(n: Int): String {
        val mod100 = n % 100
        if (mod100 in 11..13) return "${n}th"
        return n.toString() + when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    }

    /** "You're next in line", "You're 3rd in line", "Connecting you to the team". */
    fun queueLine(q: QueueStatus?): String {
        val p = q?.position
        return when {
            p == 1 -> "You're next in line"
            p != null && p > 1 -> "You're ${ordinal(p)} in line"
            else -> "Connecting you to the team"
        }
    }

    // The server's label is already a phrase ("Under a minute", "About 2 min").
    fun queueSubline(q: QueueStatus?): String = q?.estimateLabel ?: "Our team will be with you soon"

    /** handover-waiting: nobody could take the chat (the AI took it back). */
    fun handoverWaitingOf(raw: Map<String, Any?>?): HandoverWaiting? {
        raw ?: return null
        return HandoverWaiting(
            offline = raw["offline"] == true,
            reason = if (raw["reason"] == "offline") "offline" else "busy",
            backAt = raw["backAt"] as? String,
            askEmail = raw["askEmail"] == true,
            email = str(raw["email"])
        )
    }

    /** The line on that card. */
    fun handoverWaitingLine(w: HandoverWaiting, now: Date = Date(), tz: TimeZone = TimeZone.getDefault()): String {
        val back = backAtLabel(w.backAt, now, tz)
        val first = if (w.offline) "The team is offline right now${back?.let { ", back around $it" } ?: ""}." else "Everyone's busy right now."
        val second = w.email?.let { "They'll reply to $it." } ?: "Leave your email and they will reply."
        return "$first $second"
    }

    /** An agent message sent in the visitor's language: the original kept. */
    fun agentTranslationOf(data: Map<String, Any?>?): AgentTranslation? {
        val t = data?.get("translation") as? Map<*, *> ?: return null
        val original = t["original"] as? String ?: return null
        val to = t["to"] as? String ?: return null
        if (original.isBlank()) return null
        return AgentTranslation((t["from"] as? String).orEmpty(), to, original)
    }

    fun receiptMarksOf(raw: Map<String, Any?>?): ReceiptMarks =
        ReceiptMarks((raw?.get("deliveredMessageId") as? String).orEmpty(), (raw?.get("seenMessageId") as? String).orEmpty())

    private val VISITOR_KINDS = setOf("user-live-message", "user-live-message-file")
    private val AGENT_KINDS = setOf("agent-message", "agent-message-file", "agent-message-audio", "agent-message-rich")

    /**
     * The line under the visitor's latest live message while it is the newest
     * thing in the chat (an agent reply takes it away), or null. Only while a
     * person is on the other end - an AI answers at once.
     */
    fun visitorReceiptOf(record: List<LiveEntry>, marks: ReceiptMarks): VisitorReceipt? {
        for (i in record.indices.reversed()) {
            val r = record[i]
            if (r.kind in AGENT_KINDS) return null
            if (r.kind in VISITOR_KINDS) {
                if (r.id.isEmpty()) return null
                val seenAt = if (marks.seenMessageId.isNotEmpty()) record.indexOfFirst { it.id == marks.seenMessageId } else -1
                val deliveredAt = if (marks.deliveredMessageId.isNotEmpty()) record.indexOfFirst { it.id == marks.deliveredMessageId } else -1
                val state = when {
                    seenAt >= i -> ReceiptState.SEEN
                    deliveredAt >= i -> ReceiptState.DELIVERED
                    else -> ReceiptState.SENT
                }
                return VisitorReceipt(r.id, state)
            }
        }
        return null
    }

    fun receiptLabel(state: ReceiptState): String = when (state) {
        ReceiptState.SEEN -> "Seen"
        ReceiptState.DELIVERED -> "Delivered"
        ReceiptState.SENT -> "Sent"
    }

    /** The newest agent message id: what the visitor reports delivered / seen. */
    fun latestAgentMessageId(record: List<LiveEntry>): String =
        record.lastOrNull { it.kind in AGENT_KINDS }?.id.orEmpty()

    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
    fun isEmail(v: String?): Boolean = EMAIL.matches(v.orEmpty().trim())

    /** "9:00 AM" (same day) or "Friday 9:00 AM" from an ISO time; null when unknown. */
    fun backAtLabel(iso: String?, now: Date = Date(), tz: TimeZone = TimeZone.getDefault()): String? {
        iso ?: return null
        val date = parseIso(iso) ?: return null
        val time = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).apply { timeZone = tz }.format(date)
        val a = Calendar.getInstance(tz).apply { this.time = date }
        val b = Calendar.getInstance(tz).apply { this.time = now }
        val sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return time
        val day = SimpleDateFormat("EEEE", Locale.getDefault()).apply { timeZone = tz }.format(date)
        return "$day $time"
    }

    private fun parseIso(iso: String): Date? {
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
        for (p in patterns) {
            try {
                return SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** Visitor uploads in a live chat: on unless the owner turned them off. */
    fun visitorUploadsOn(customizations: Map<String, Any?>?): Boolean = customizations?.get("enableVisitorFileUpload") != false

    fun csatConfigOf(raw: Map<String, Any?>?): CsatConfig? {
        raw ?: return null
        if (raw["enabled"] != true) return null
        val type = (raw["ratingType"] as? String)?.takeIf { it in setOf("stars", "thumbs", "emoji", "nps") } ?: "stars"
        fun s(key: String, d: String) = str(raw[key]) ?: d
        return CsatConfig(
            ratingType = type,
            titleText = s("titleText", "How was your experience?"),
            subtitleText = s("subtitleText", "Your feedback helps us improve"),
            thankYouText = s("thankYouText", "Thank you for your feedback!"),
            showFeedbackInput = raw["showFeedbackInput"] != false,
            feedbackPlaceholder = s("feedbackPlaceholder", "Tell us more...")
        )
    }

    /** submit-chat-rating payload for a rating of the given type (value: Int, or Boolean for thumbs). */
    /**
     * handover-timeout, sent once the handover step's max wait passes with
     * nobody joined: the server then decides (no-agents-available), as the
     * web widget's maxWaitTimer does.
     */
    fun handoverTimeoutPayload(chatSessionId: String, nodeData: Map<String, Any?>?, workspaceId: String?): Map<String, Any?> = mapOf(
        "chatSessionId" to chatSessionId,
        "assignmentData" to mapOf(
            "assignmentType" to nodeData?.get("assignmentType"),
            "assignmentStrategy" to nodeData?.get("agentAssignmentStrategy"),
            "assignedAgents" to nodeData?.get("assignedAgents")
        ),
        "workspaceId" to workspaceId
    )

    /**
     * The handover step's own post-chat survey as submit-chat-rating (the
     * server has no post-chat-survey-response): a rating out of more than 5
     * is an NPS 0-10, otherwise a 1-5 score. null without a rating.
     */
    fun surveyRatingPayload(chatSessionId: String, ratingMax: Int?, rating: Int?, feedback: String? = null): Map<String, Any>? {
        if (rating == null || chatSessionId.isEmpty()) return null
        return if ((ratingMax ?: 5) > 5) ratingPayload(chatSessionId, "nps", rating.coerceIn(0, 10), feedback)
        else ratingPayload(chatSessionId, "stars", rating.coerceIn(1, 5), feedback)
    }

    fun ratingPayload(chatSessionId: String, type: String, value: Any, feedback: String? = null): Map<String, Any> {
        val out = linkedMapOf<String, Any>("chatSessionId" to chatSessionId, "source" to "post_chat_survey")
        when (type) {
            "thumbs" -> out["thumbsUp"] = value == true
            "nps" -> out["npsScore"] = (value as? Number)?.toInt() ?: 0
            else -> out["csatScore"] = (value as? Number)?.toInt() ?: 0
        }
        feedback?.trim()?.takeIf { it.isNotEmpty() }?.let { out["feedback"] = it.take(1000) }
        return out
    }

    // "See original" / "See translation" in the visitor's language (the web
    // widget's list, ts/chat/translatedMessage.ts).
    private val TRANSLATION_LABELS = mapOf(
        "en" to ("See original" to "See translation"),
        "es" to ("Ver original" to "Ver traducción"),
        "fr" to ("Voir l'original" to "Voir la traduction"),
        "de" to ("Original anzeigen" to "Übersetzung anzeigen"),
        "pt" to ("Ver original" to "Ver tradução"),
        "it" to ("Vedi originale" to "Vedi traduzione"),
        "nl" to ("Origineel bekijken" to "Vertaling bekijken"),
        "pl" to ("Zobacz oryginał" to "Zobacz tłumaczenie"),
        "tr" to ("Orijinali gör" to "Çeviriyi gör"),
        "ru" to ("Показать оригинал" to "Показать перевод"),
        "uk" to ("Показати оригінал" to "Показати переклад"),
        "ar" to ("عرض النص الأصلي" to "عرض الترجمة"),
        "hi" to ("मूल देखें" to "अनुवाद देखें"),
        "ja" to ("原文を表示" to "翻訳を表示"),
        "ko" to ("원문 보기" to "번역 보기"),
        "zh" to ("查看原文" to "查看翻译"),
        "id" to ("Lihat asli" to "Lihat terjemahan"),
        "vi" to ("Xem bản gốc" to "Xem bản dịch"),
        "th" to ("ดูต้นฉบับ" to "ดูคำแปล"),
        "sv" to ("Visa original" to "Visa översättning")
    )

    fun translationLabels(lang: String?): Pair<String, String> {
        val key = lang.orEmpty().lowercase().split('-', '_').first()
        return TRANSLATION_LABELS[key] ?: TRANSLATION_LABELS.getValue("en")
    }

    /** A file URL ready to open: the relative /media proxy on the API origin; never script / data URLs. */
    fun resolveMediaUrl(url: String?, origin: String): String? {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (Regex("^(javascript|vbscript|data):", RegexOption.IGNORE_CASE).containsMatchIn(raw.replace(Regex("\\s"), ""))) return null
        if (raw.startsWith("/")) return origin.trimEnd('/') + raw
        return raw
    }

    fun isImageUrl(url: String?, mimeType: String? = null): Boolean =
        mimeType.orEmpty().startsWith("image/") ||
            Regex("\\.(png|jpe?g|gif|webp|heic|bmp)(\\?|#|$)", RegexOption.IGNORE_CASE).containsMatchIn(url.orEmpty())

    /** The name on a file row: the record's own, or the URL's last part. */
    fun fileNameOf(own: String?, url: String): String {
        own?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val last = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        return try { java.net.URLDecoder.decode(last, "UTF-8") } catch (_: Exception) { last }.ifEmpty { "File" }
    }

    /** The visitor's email for leave a message: their own, else the latest email they answered. */
    fun knownEmail(visitorEmail: String?, answerValues: List<Any?>): String {
        if (isEmail(visitorEmail)) return visitorEmail!!.trim()
        return answerValues.lastOrNull { isEmail(it?.toString()) }?.toString()?.trim().orEmpty()
    }

    /** What they last asked in their own words: the newest typed visitor message, not a button tap. */
    fun lastQuestion(record: List<Map<String, Any?>>): String {
        val kinds = setOf("user-live-message", "user-input", "user-message", "ask-question-answer")
        for (r in record.asReversed()) {
            val kind = (r["shape"] ?: r["type"])?.toString().orEmpty()
            if (kind in kinds) {
                val text = (r["text"] ?: r["userInput"] ?: r["value"])?.toString().orEmpty()
                    .replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
                if (text.isNotEmpty() && !isEmail(text)) return text.take(2000)
            }
        }
        return ""
    }

    const val TALK_TO_PERSON_ASK = "I'd like to talk to a person, please."
    const val QUEUE_POLL_MS = 20_000L
}
