package com.conferbot.sdk.core.agent

/**
 * Placeholder citation markers in AI answers, as embed-server
 * utils/citationMarkers.js stripPlaceholderMarkers.
 *
 * The server strips markers from new answers, but answers saved before that
 * can come back in restored history with an echoed placeholder ("[n]",
 * "[N]", "[1, n]") or a label ("[source]", "[web:2]"). Conservative on
 * purpose: a bracket goes only when every part is a number or one of the
 * placeholder words, and at least one part is not a plain number ("[2]"
 * could be the author's own). "[Klik hier]", a markdown link "[n](url)" and
 * a task-list "- [x]" stay as written.
 */
object CitationMarkers {
    // Words a model writes in place of (or in front of) a passage number.
    private val WORDS = listOf(
        "n", "x", "k", "#", "nr", "no", "num", "number",
        "source", "sources", "citation", "citations", "cite", "ref", "refs", "reference", "references",
        "passage", "passages", "knowledge", "kb", "faq", "context", "doc", "docs", "website", "web",
        "instruction", "instructions",
    ).joinToString("|") { it.replace("#", "\\#") }

    // One part of a marker: "2", "n", "source", "source 2", "web:2", "passage n".
    private val PART = "(?:\\d+|(?:$WORDS)(?:[ \\t]*[:#-]?[ \\t]*(?:\\d+|n))?)"

    // A whole marker, with the one space before it. Not a markdown link ("[n](url)").
    private val MARKER = Regex("[ \\t]?\\[[ \\t]*$PART(?:[ \\t]*[,;][ \\t]*$PART)*[ \\t]*\\](?!\\()", RegexOption.IGNORE_CASE)
    private val NUMERIC = Regex("^\\[\\s*\\d+(?:\\s*[,;]\\s*\\d+)*\\s*\\]$")
    private val CHECKBOX = Regex("^\\s*\\[\\s*x\\s*\\]$", RegexOption.IGNORE_CASE)
    private val LIST_START = Regex("(^|\\n)[ \\t]*[-*+][ \\t]*$")

    // What a removed marker leaves: "zit ." and "zit ,".
    private val SPACE_BEFORE_PUNCT = Regex("[ \\t]+([.,;:!?])")

    /** [text] without its placeholder markers; unchanged when it has none. */
    fun stripPlaceholderMarkers(text: String): String {
        var changed = false
        val out = MARKER.replace(text) { m ->
            val marker = m.value
            val before = text.substring(maxOf(0, m.range.first - 40), m.range.first)
            val isCheckbox = CHECKBOX.matches(marker) && LIST_START.containsMatchIn(before)
            if (isCheckbox || NUMERIC.matches(marker.trim())) {
                marker
            } else {
                changed = true
                ""
            }
        }
        return if (changed) SPACE_BEFORE_PUNCT.replace(out, "$1").trim() else text
    }
}
