package com.conferbot.sdk.core.customization

/**
 * Auto-open: the owner's botAutoOpen / botAutoOpenDelay (seconds) open the
 * chat on its own, for a first visit only. Ported from the React Native
 * SDK's src/core/autoOpen.ts (conferbot-widget src/index.ts, the live_chat /
 * popup_chat auto-open, and src/core/customizations.ts autoOpen,
 * autoOpenDelay):
 *
 *   - only while the visitor has not started a chat (a conversation they
 *     already have is theirs to reopen: web `!chatStarted`);
 *   - not when they closed the chat in the last 2 minutes (the web's
 *     isChatOpen=false cookie lasts 120 s);
 *   - once per launch of the app (the web: once per page load);
 *   - when the delay is up and the chat is already open, nothing.
 *
 * Pure.
 */
object AutoOpen {
    /** A close blocks auto-open this long (web: the isChatOpen cookie's 2 minutes). */
    const val CLOSED_BLOCKS_MS = 120_000L

    /** The owner's delay in milliseconds (unset, junk or negative: at once). */
    fun delayOf(c: Map<String, Any?>?): Long {
        val s = when (val v = c?.get("botAutoOpenDelay")) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            else -> null
        } ?: return 0
        return if (s.isFinite() && s > 0) Math.round(s * 1000) else 0
    }

    private fun truthy(v: Any?): Boolean = when (v) {
        null, false -> false
        is Number -> v.toDouble() != 0.0
        is String -> v.isNotEmpty()
        else -> true
    }

    /** In how many ms to open the chat, or null for not at all. */
    fun plan(
        c: Map<String, Any?>?,
        chatStarted: Boolean,
        isOpen: Boolean,
        alreadyAutoOpened: Boolean,
        closedAt: Long? = null,
        now: Long = System.currentTimeMillis()
    ): Long? {
        if (!truthy(c?.get("botAutoOpen")) || chatStarted || isOpen || alreadyAutoOpened) return null
        if (closedAt != null && closedAt > 0 && now - closedAt < CLOSED_BLOCKS_MS) return null
        return delayOf(c)
    }

    // Once per launch of the app per bot; a close blocks it for 2 minutes.
    private val opened = HashSet<String>()
    private val closed = HashMap<String, Long>()

    fun alreadyOpened(bot: String): Boolean = synchronized(opened) { bot in opened }
    fun markOpened(bot: String) = synchronized(opened) { opened.add(bot) }
    fun closedAt(bot: String): Long? = synchronized(closed) { closed[bot] }
    fun markClosed(bot: String, at: Long = System.currentTimeMillis()) = synchronized(closed) { closed[bot] = at }
}
