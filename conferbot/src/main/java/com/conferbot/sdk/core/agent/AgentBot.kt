package com.conferbot.sdk.core.agent

/**
 * AI agent bots (Agent Studio): bots with no flow of their own, answered by
 * an AI agent from the first message.
 *
 * The server marks one with `chatbotData.agentBot` on fetched-chatbot-data
 * (embed-server chatbotController getChatbotElementsSocket). Such a bot
 * still ships a one-node shell flow (welcome -> handover to the agent) for
 * older clients, but running it as-is stages a handover the visitor should
 * never see. With agentBot set the SDK opens straight into the agent chat
 * and reuses the handover socket path silently underneath (silentStart),
 * as the web widget does (conferbot-widget index.ts _startAgentBotChat,
 * ts/chat/agentShell.ts).
 *
 * Also here: the AI reply's extras as the server sends them on
 * agent-message (sources, follow-ups, action buttons, cards, an action to
 * confirm), cleaned the same way the web widget cleans them. Pure: works
 * on the maps the socket JSON is converted to, no Android types.
 */

data class AgentBot(
    val agentId: String,
    val displayName: String,
    val greeting: String,
    /** Suggested first questions, shown as chips under the greeting. */
    val starters: List<String>,
    val avatar: String?
) {
    /** The agent as the chat presents it - the shape the server sends as agentDetails. */
    val agentDetailsId: String get() = "ai-agent-$agentId"
}

data class AgentSource(val title: String, val url: String? = null, val articleId: String? = null, val snippet: String? = null)
data class AnswerAction(val label: String, val url: String)
data class AnswerCard(val title: String?, val rows: List<Pair<String, String>>, val note: String?, val url: String?)
data class ResultCard(
    val title: String,
    val status: String?,
    val tone: String,
    val rows: List<Pair<String, String>>,
    val linkUrl: String?,
    val linkLabel: String?
)
data class ActionSummaryLine(val label: String, val value: String)
data class ActionRequest(val token: String, val name: String, val summary: List<ActionSummaryLine>)

/** An article, quick replies or a card from the agent (agent-message-rich). */
sealed class RichEntry {
    data class Article(val articleId: String, val title: String, val snippet: String, val url: String?) : RichEntry()
    data class QuickReplies(val text: String, val options: List<String>) : RichEntry()
    data class Card(
        val title: String,
        val description: String,
        val imageUrl: String?,
        val buttonLabel: String?,
        val buttonUrl: String?
    ) : RichEntry()
}

/** What an AI reply carries besides its text. */
data class AIReplyExtras(
    val sources: List<AgentSource> = emptyList(),
    val actions: List<AnswerAction> = emptyList(),
    val card: AnswerCard? = null,
    val resultCard: ResultCard? = null,
    val actionRequest: ActionRequest? = null,
    val isFallback: Boolean = false
) {
    val isEmpty: Boolean
        get() = sources.isEmpty() && actions.isEmpty() && card == null && resultCard == null && actionRequest == null
}


object AgentBotParser {
    const val DEFAULT_AGENT_NAME = "AI Assistant"
    const val DEFAULT_GREETING = "Hi! How can I help you today?"

    /**
     * How long the "writing a reply" dots may wait for the AI (web
     * agentListeners.ts AWAITING_REPLY_MAX_MS): the AI path stays silent when
     * the provider fails twice, so without a ceiling they would run forever.
     */
    const val AWAIT_REPLY_MS = 45_000L

    /** Said when that ceiling is hit, so the dots do not just vanish (web chat.replyTimeout). */
    const val REPLY_TIMEOUT_TEXT = "That took too long. Try again, or ask for a human."

    /** The notice's id: an on-screen line, not part of the conversation's record (as on the web). */
    fun replyTimeoutId(now: Long = System.currentTimeMillis()) = "reply-timeout-$now"
    const val MAX_SOURCES = 3
    val FEEDBACK_REASONS = listOf("wrong" to "Wrong", "outdated" to "Outdated", "unanswered" to "Didn't answer", "other" to "Other")

    private val OBJECT_ID = Regex("^[a-f0-9]{24}$", RegexOption.IGNORE_CASE)
    private val HTTP_URL = Regex("^https?://[^\\s\"'<>]+$", RegexOption.IGNORE_CASE)
    private val HTTPS_URL = Regex("^https://[^\\s\"'<>]+$", RegexOption.IGNORE_CASE)

    private fun clean(v: Any?, max: Int): String = when (v) {
        is String -> v.replace(Regex("\\s+"), " ").trim().take(max)
        is Number -> {
            val d = v.toDouble()
            if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else v.toString()
        }
        else -> ""
    }

    private fun httpUrl(v: Any?): String? = (v as? String)?.trim()?.takeIf { HTTP_URL.matches(it) }
    private fun httpsUrl(v: Any?): String? = (v as? String)?.trim()?.takeIf { HTTPS_URL.matches(it) }

    @Suppress("UNCHECKED_CAST")
    private fun mapOf(v: Any?): Map<String, Any?>? = v as? Map<String, Any?>
    private fun listOf(v: Any?): List<Any?> = (v as? List<Any?>) ?: emptyList()

    /** The agent bot config from chatbotData, or null for every flow bot. */
    fun parseAgentBot(chatbotData: Map<String, Any?>?): AgentBot? {
        val a = mapOf(chatbotData?.get("agentBot")) ?: return null
        val agentId = a["agentId"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val seen = mutableSetOf<String>()
        val starters = listOf(a["starters"])
            .map { clean(if (it is String) it else mapOf(it)?.get("text"), 80) }
            .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
            .take(4)
        return AgentBot(
            agentId = agentId,
            displayName = clean(a["displayName"], 60).ifEmpty { clean(chatbotData?.get("name"), 60) }.ifEmpty { DEFAULT_AGENT_NAME },
            greeting = (a["greeting"] as? String)?.trim() ?: "",
            starters = starters,
            avatar = (a["avatar"] as? String)?.takeIf { it.isNotBlank() }
        )
    }

    /** The agentDetails map the server sends for this agent. */
    fun agentDetailsOf(bot: AgentBot): Map<String, Any?> = linkedMapOf(
        "_id" to bot.agentDetailsId,
        "name" to bot.displayName,
        "avatar" to (bot.avatar ?: ""),
        "isAIAgent" to true
    )

    /** Is this agentDetails an AI agent rather than a person? */
    fun isAIAgent(details: Map<String, Any?>?): Boolean {
        if (details == null) return false
        if (details["isAIAgent"] == true) return true
        return (details["_id"] as? String)?.startsWith("ai-agent-") == true
    }

    fun isAgentShellHandover(node: Map<String, Any?>, agentId: String): Boolean {
        if (node["type"] != "human-handover-node") return false
        val assigned = listOf(mapOf(node["data"])?.get("assignedAIAgents"))
        return assigned.map { it.toString() }.contains(agentId)
    }

    /**
     * The shell's welcome and handover nodes. A bot switched from a flow keeps
     * the owner's flow too, so only the handover assigned to this agent counts.
     */
    fun agentShellNodes(nodes: List<Map<String, Any?>>, agentId: String): Pair<Map<String, Any?>?, Map<String, Any?>?> {
        val handover = nodes.firstOrNull { isAgentShellHandover(it, agentId) } ?: return null to null
        val welcome = nodes.firstOrNull { it["type"] == "welcome-node" }
        return welcome to handover
    }

    /** Handover data for the agent chat: never a form, message or wait before it. */
    fun agentHandoverData(shellData: Map<String, Any?>?, agentId: String): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>("assignmentType" to "auto", "assignedAgents" to emptyList<String>())
        shellData?.let { out.putAll(it) }
        out["assignedAIAgents"] = kotlin.collections.listOf(agentId)
        out["handoverMessage"] = ""
        out["enablePreChatQuestions"] = false
        out["preChatQuestions"] = emptyList<Any>()
        return out
    }

    /**
     * Is the AI still answering? The latest word wins: an inactivity pause the
     * visitor came back from has the AI on the chat again; a person taking
     * over, the chat ending or an inactivity close mean it is not.
     */
    fun isAgentAIActive(recordTypes: List<String?>): Boolean {
        for (type in recordTypes.asReversed()) {
            if (type == "ai-agent-resumed") return true
            if (type == "agent-left-chat" || type == "agent-chat-ended" || type == "ai-agent-inactive-close") return false
        }
        return true
    }

    /** "https://www.conferbot.com/pricing" -> "conferbot.com" */
    fun sourceHost(url: String?): String {
        val m = Regex("^https?://([^/?#:]+)", RegexOption.IGNORE_CASE).find(url ?: "") ?: return ""
        return m.groupValues[1].lowercase().removePrefix("www.")
    }

    /** Sources an AI reply was grounded in: http(s) pages or help articles, at most three. */
    fun normalizeSources(raw: Any?): List<AgentSource> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<AgentSource>()
        for (item in listOf(raw)) {
            val m = mapOf(item) ?: continue
            val articleId = (m["articleId"] as? String)?.takeIf { OBJECT_ID.matches(it) }
            val url = (m["url"] ?: m["sourceUrl"])?.toString()?.trim() ?: ""
            val key = if (articleId != null) "article:$articleId" else url
            if ((articleId == null && !url.startsWith("http://", true) && !url.startsWith("https://", true)) || !seen.add(key)) continue
            val snippet = clean(m["snippet"] ?: m["description"] ?: m["excerpt"], 220).ifEmpty { null }
            out += if (articleId != null) {
                AgentSource(clean(m["title"], 160).ifEmpty { "Help article" }, httpUrl(url), articleId, snippet)
            } else {
                AgentSource(clean(m["title"], 160).ifEmpty { sourceHost(url) }, url, null, snippet)
            }
            if (out.size >= MAX_SOURCES) break
        }
        return out
    }

    /** The buttons under an answer: labelled, http(s), at most two, once each. */
    fun answerActionsOf(raw: Any?): List<AnswerAction> {
        val seen = mutableSetOf<String>()
        return listOf(raw).mapNotNull { a ->
            val m = mapOf(a) ?: return@mapNotNull null
            val label = clean(m["label"], 28)
            val url = httpUrl(m["url"]) ?: return@mapNotNull null
            if (label.isEmpty() || !seen.add(url)) null else AnswerAction(label, url)
        }.take(2)
    }

    private fun rowsOf(raw: Any?, max: Int): List<Pair<String, String>> =
        listOf(raw).mapNotNull { r ->
            val l = r as? List<*> ?: return@mapNotNull null
            if (l.size < 2) return@mapNotNull null
            val a = clean(l[0], 40)
            val b = clean(l[1], 60)
            if (a.isEmpty() || b.isEmpty()) null else a to b
        }.take(max)

    /** Facts from the knowledge as a card: a title, two to six rows, a note, a link. */
    fun answerCardOf(raw: Any?): AnswerCard? {
        val m = mapOf(raw) ?: return null
        val rows = rowsOf(m["rows"], 6)
        if (rows.size < 2) return null
        return AnswerCard(clean(m["title"], 60).ifEmpty { null }, rows, clean(m["note"], 120).ifEmpty { null }, httpsUrl(m["url"]))
    }

    /** What an action did ("Order #1042 · In transit"), as a card. */
    fun resultCardOf(raw: Any?): ResultCard? {
        val m = mapOf(raw) ?: return null
        val rows = rowsOf(m["rows"], 5)
        val status = clean(m["status"], 30).ifEmpty { null }
        if (rows.isEmpty() && status == null) return null
        val tone = (m["tone"] as? String)?.takeIf { it in setOf("done", "moving", "problem", "neutral") } ?: "neutral"
        val link = mapOf(m["link"])
        val url = httpsUrl(link?.get("url"))
        return ResultCard(
            title = clean(m["title"], 50).ifEmpty { "Done" },
            status = status,
            tone = tone,
            rows = rows,
            linkUrl = url,
            linkLabel = if (url != null) clean(link?.get("label"), 24).ifEmpty { "Open" } else null
        )
    }

    /** An action the AI is about to take, for the visitor to confirm. */
    fun actionRequestOf(raw: Any?): ActionRequest? {
        val m = mapOf(raw) ?: return null
        val token = (m["token"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        val name = m["name"] as? String ?: return null
        val summary = listOf(m["summary"]).mapNotNull { l ->
            val line = mapOf(l) ?: return@mapNotNull null
            val label = line["label"] as? String ?: return@mapNotNull null
            if (!line.containsKey("value")) return@mapNotNull null
            ActionSummaryLine(label.take(40), clean(line["value"], 120).ifEmpty { line["value"].toString().take(120) })
        }.take(8)
        return ActionRequest(token, name.take(80), summary)
    }

    /** What the visitor may ask next: at most three short questions. */
    fun followUpsOf(raw: Any?): List<String> =
        listOf(raw).filterIsInstance<String>().map { clean(it, 120) }.filter { it.isNotEmpty() }.take(3)

    /** An agent's rich message as it may be drawn, or null. */
    fun safeRich(raw: Any?): RichEntry? {
        val m = mapOf(raw) ?: return null
        return when (m["kind"]) {
            "article" -> {
                val title = clean(m["title"], 80)
                if (title.isEmpty()) null else RichEntry.Article(clean(m["articleId"], 40), title, clean(m["snippet"], 200), httpsUrl(m["url"]))
            }
            "quick_replies" -> {
                val options = listOf(m["options"]).map { clean(it, 40) }.filter { it.isNotEmpty() }.take(4)
                if (options.size < 2) null else RichEntry.QuickReplies(clean(m["text"], 300), options)
            }
            "card" -> {
                val title = clean(m["title"], 80)
                if (title.isEmpty()) return null
                val button = mapOf(m["button"])
                val label = clean(button?.get("label"), 30)
                val url = httpsUrl(button?.get("url"))
                RichEntry.Card(
                    title = title,
                    description = clean(m["description"], 300),
                    imageUrl = httpsUrl(m["imageUrl"]),
                    buttonLabel = if (label.isNotEmpty() && url != null) label else null,
                    buttonUrl = if (label.isNotEmpty() && url != null) url else null
                )
            }
            else -> null
        }
    }

    /** The owner's quick actions above the composer: labelled, safe links, at most three. */
    fun quickActionsOf(customizations: Map<String, Any?>?): List<AnswerAction> =
        listOf(customizations?.get("chatQuickActions")).mapNotNull { a ->
            val m = mapOf(a) ?: return@mapNotNull null
            val label = clean(m["label"], 30)
            val raw = m["url"]?.toString()?.trim() ?: ""
            val url = httpUrl(raw) ?: raw.takeIf { Regex("^(mailto|tel):", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            if (label.isEmpty() || url == null) null else AnswerAction(label, url)
        }.take(3)

    /** "Helpful?" under AI answers unless the owner turned it off. */
    fun answerFeedbackOn(customizations: Map<String, Any?>?): Boolean = customizations?.get("hideAnswerFeedback") != true

    /** Only an AI answer stored under its server id can be rated. */
    fun canRate(messageId: String?): Boolean = messageId != null && OBJECT_ID.matches(messageId)

    /** The conversion a link stands for, in the analytics vocabulary. */
    fun conversionEventOf(label: String, url: String = ""): String {
        val t = "$label $url".lowercase()
        return when {
            Regex("demo|book|meeting|call|calendly").containsMatchIn(t) -> "demo_booked"
            Regex("trial|sign ?up|register|get started|start free").containsMatchIn(t) -> "signup"
            Regex("buy|purchase|checkout|order|cart").containsMatchIn(t) -> "purchase"
            Regex("contact|email|quote").containsMatchIn(t) -> "contact_form"
            Regex("download").containsMatchIn(t) -> "download"
            else -> "custom"
        }
    }

    /** The extras of an agent-message payload; null for a person's message. */
    fun aiReplyExtrasOf(data: Map<String, Any?>): AIReplyExtras? {
        if (!isAIAgent(mapOf(data["agentDetails"]))) return null
        val sources = normalizeSources(data["sources"] ?: data["citations"])
        if (data["isFallback"] == true) return AIReplyExtras(sources = sources, isFallback = true)
        return AIReplyExtras(
            sources = sources,
            actions = answerActionsOf(data["actions"]),
            card = answerCardOf(data["card"]),
            resultCard = resultCardOf(data["resultCard"]),
            actionRequest = actionRequestOf(data["actionRequest"])
        )
    }
}
