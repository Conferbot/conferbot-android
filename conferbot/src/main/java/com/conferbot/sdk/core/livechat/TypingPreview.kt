package com.conferbot.sdk.core.livechat

/**
 * Typing preview ("sneak peek"): while a person is chatting with the
 * visitor, the agent sees what the visitor is typing before it is sent.
 * Ported from the React Native SDK's src/core/typingPreview.ts
 * (conferbot-widget src/ts/chat/typingPreview.ts cleanDraft,
 * createDraftSender, and src/index.ts updateVisitorDraft: off when the owner
 * set visitorDraftPreview to false, never for a secret field, only while a
 * person is in the chat).
 *
 * The composer calls [DraftSender.update] on every change; drafts go out as
 * `visitor-draft {chatSessionId, draft}` at most every [THROTTLE_MS] (the
 * latest text always follows, trailing), trimmed and capped at [MAX].
 * [DraftSender.clear] sends one empty draft on send, or when the field is
 * emptied, so the agent's preview goes at once. The server relays drafts
 * only to the conversation's agents and never keeps them. Pure.
 */
object TypingPreview {
    const val MAX = 500
    const val THROTTLE_MS = 300L

    fun cleanDraft(value: Any?): String = (value as? String)?.trim()?.take(MAX) ?: ""

    /**
     * Never preview what looks like a secret or card data: a password field
     * (inputType), or autofill hints for passwords, one-time codes or cards.
     */
    fun isSensitiveField(password: Boolean = false, autofillHints: List<String> = emptyList()): Boolean {
        if (password) return true
        return autofillHints.any {
            val h = it.lowercase()
            h.contains("password") || h.startsWith("creditcard") || h.startsWith("cc-") ||
                h == "smsotpcode" || h == "one-time-code" || h == "sms-otp" || h.startsWith("2fa") || h.contains("otp")
        }
    }

    /** Whether drafts go to the agent at all: the owner allows it and a person is in the chat. */
    fun on(customizations: Map<String, Any?>?, chattingWithPerson: Boolean): Boolean =
        customizations?.get("visitorDraftPreview") != false && chattingWithPerson

    /** Sends the composer's drafts (see [TypingPreview]). */
    class DraftSender(
        private val send: (String) -> Unit,
        private val timer: SignalTimer,
        private val now: () -> Long = { System.currentTimeMillis() },
        private val intervalMs: Long = THROTTLE_MS
    ) {
        private var lastSent = ""
        private var lastSentAt = Long.MIN_VALUE / 2
        private var pending: String? = null
        private var handle: Any? = null

        private fun flush() {
            handle = null
            val draft = pending ?: return
            pending = null
            if (draft == lastSent) return
            lastSent = draft
            lastSentAt = now()
            send(draft)
        }

        fun update(text: String) {
            val draft = cleanDraft(text)
            if (draft.isEmpty()) return clear()
            pending = draft
            if (handle != null) return
            val wait = intervalMs - (now() - lastSentAt)
            if (wait <= 0) flush() else handle = timer.set(wait) { flush() }
        }

        fun clear() {
            handle?.let { timer.clear(it) }
            handle = null
            pending = null
            if (lastSent.isEmpty()) return
            lastSent = ""
            lastSentAt = now()
            send("")
        }
    }
}
