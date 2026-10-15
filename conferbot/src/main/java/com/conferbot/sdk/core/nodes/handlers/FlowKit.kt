package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.flow.FlowCalendar
import com.conferbot.sdk.core.flow.FlowText
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.flow.flowContext
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.core.state.RecordEntry

/**
 * Small helpers the step handlers share (RN handlers/flowKit.ts): owner text
 * filled from the answers as the visitor sees it, record entries in the web's
 * shapes, bot and visitor lines on the transcript.
 */
internal object FlowKit {
    /** Owner HTML with the answers filled in (values escaped), as one native bubble's text. */
    fun shownText(html: Any?): String = FlowText.bubbleText(ChatState.resolveVariables(Js.str(html), html = true))

    /** Owner HTML filled in, as one line (labels of buttons and options). */
    fun shownLabel(html: Any?): String = FlowText.plainText(ChatState.resolveVariables(Js.str(html), html = true))

    /** What an option saves: its own text, HTML stripped and entities decoded (never filled). */
    fun savedText(html: Any?): String = FlowText.stripHtmlAndDecode(html)

    /** Now, on the flow's clock, as an ISO string. */
    suspend fun nowIso(): String = FlowCalendar.iso(flowContext().now())

    /**
     * The visitor's answer on the record (shape "user-..."), merged as the web
     * does. "id", "shape", "type" and "text" are the entry's own; the rest are
     * the values the web records at the top of the entry.
     */
    suspend fun pushAnswer(entry: Map<String, Any?>) {
        val fields = entry.filterKeys { it != "id" && it != "shape" && it != "type" && it != "text" }
        ChatState.addRecord(
            RecordEntry(
                id = Js.str(entry["id"]),
                shape = Js.str(entry["shape"]),
                type = entry["type"] as? String,
                text = entry["text"]?.let { Js.str(it) },
                time = nowIso(),
                fields = fields
            )
        )
    }

    /** A bot line on the transcript; [side] also shows it as its own bubble (not the step's card). */
    suspend fun botSay(text: String, side: Boolean = false) {
        if (text.isEmpty()) return
        ChatState.addToTranscript("bot", text)
        if (side) flowContext().say(text)
    }

    /** The visitor's line on the transcript (as typed: the transcript is never masked). */
    fun userSay(text: String) {
        ChatState.addToTranscript("user", text)
    }

    /** A typed answer, trimmed: from a string or the text input's {value} / {text}. */
    fun typedText(response: Any?): String {
        if (response is String) return response.trim()
        if (response is Map<*, *>) {
            val v = response["value"] ?: response["text"] ?: response["message"]
            if (v != null && v !is Map<*, *> && v !is List<*>) return Js.str(v).trim()
        }
        return ""
    }

    /** The id of the button / option the visitor picked (the SDK's choice cards send {id, text}). */
    fun pickedId(response: Any?): String? {
        if (response == null) return null
        if (response !is Map<*, *>) return Js.str(response)
        val id = response["buttonId"] ?: response["choiceId"] ?: response["optionId"] ?: response["id"] ?: response["selectedId"]
        return id?.let { Js.str(it) }
    }

    /** A map view of an answer. */
    @Suppress("UNCHECKED_CAST")
    fun asMap(response: Any?): Map<String, Any?> = (response as? Map<String, Any?>) ?: emptyMap()
}
