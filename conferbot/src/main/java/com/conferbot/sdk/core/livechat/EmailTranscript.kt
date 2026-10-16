package com.conferbot.sdk.core.livechat

import com.conferbot.sdk.core.i18n.t
import com.conferbot.sdk.core.i18n.I18n

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

    // The web's strings (ts/chat/emailTranscript.ts: chat.menu.emailChat, mailchat.*, form.*, common.*), read in the active language.
    val MENU: String get() = t("chat.menu.emailChat")
    val TITLE: String get() = t("chat.menu.emailChat")
    val SUB: String get() = t("mailchat.sub")
    val PLACEHOLDER: String get() = t("form.emailPlaceholder")
    val LABEL: String get() = t("form.yourEmail")
    val SEND: String get() = t("common.send")
    val SENDING: String get() = t("common.sendingEllipsis")
    val CANCEL: String get() = t("common.cancel")
    val CHECK_EMAIL: String get() = t("form.checkEmail")
    val SEND_FAILED: String get() = t("form.sendFailed")
    fun sentText(email: String) = t("mailchat.sent", "email" to email)

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

    /** What the card says when it did not go: the server's words (it words them in English, so only in English), else ours. */
    fun errorText(r: Result?): String = r?.takeIf { !it.ok && I18n.language == I18n.EN }?.message ?: SEND_FAILED
}
