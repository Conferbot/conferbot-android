package com.conferbot.sdk.core.livechat

/**
 * "Email me this chat" (web ts/chat/emailTranscript.ts, ported from the
 * React Native SDK's src/core/emailTranscript.ts): the visitor gets the
 * whole conversation in their inbox, and the address they give is kept on
 * the conversation like any email they leave.
 *
 * Sent by the embed server, only ever to the address typed here, and
 * rate-limited (5 per 15 minutes per IP, shared with the web route). The web
 * posts to /api/v1/chat/transcript-email with the botId in the body; an app
 * posts to the mobile base path, /api/v1/mobile/chat/transcript-email, and
 * the bot comes from its X-Bot-ID header (embed-server #193).
 *
 * Pure: the request's path and body, and how its answer reads.
 */
object EmailTranscript {

    /** The web's address check (emailTranscript.ts EMAIL_RE). */
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    fun isEmail(email: String?): Boolean = EMAIL.matches((email ?: "").trim())

    /** Under the mobile API base (".../api/v1/mobile/"). */
    const val PATH = "chat/transcript-email"

    fun body(chatSessionId: String, email: String): Map<String, String> = mapOf("chatSessionId" to chatSessionId, "email" to email.trim())

    // The web's English strings (i18n en: chat.menu.emailChat, mailchat.*, form.*).
    const val MENU = "Email me this chat"
    const val TITLE = "Email me this chat"
    const val SUB = "We'll send the whole conversation to your inbox."
    const val PLACEHOLDER = "you@company.com"
    const val LABEL = "Your email"
    const val SEND = "Send"
    const val SENDING = "Sending…"
    const val CANCEL = "Cancel"
    const val CHECK_EMAIL = "Please check the email address."
    const val SEND_FAILED = "We couldn't send it just now. Please try again."
    fun sentText(email: String) = "Sent! Check your inbox at $email"

    data class Result(val ok: Boolean, val message: String? = null)

    /**
     * The server's answer read as the web reads it: sent only on a 2xx with
     * `success: true`; otherwise its message, if it gave one (an invalid
     * address is a 400, an unknown session a 200 with success false, the
     * rate limit a 429 with `message`).
     */
    fun result(status: Int, data: Map<String, Any?>?): Result {
        val ok = status in 200..299 && data?.get("success") == true
        val message = (data?.get("message") as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
        return if (ok) Result(true) else Result(false, message)
    }

    /** What the card says when it did not go: the server's words (it words them in English, as the SDK is), else ours. */
    fun errorText(r: Result?): String = r?.takeIf { !it.ok }?.message ?: SEND_FAILED
}
