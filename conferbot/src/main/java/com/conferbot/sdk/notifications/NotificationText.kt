package com.conferbot.sdk.notifications

import com.conferbot.sdk.core.i18n.t

/**
 * The words of the SDK's own notifications and in-app banners, in the
 * widget's language (core/i18n: `notify.*`, `system.*`, `agent.*` and the
 * SDK's own `sdk.notify.*`). A push that starts the app before the bot's
 * settings arrive is drawn in the app's language choice or the phone's.
 */
internal object NotificationText {
    val channelMessages: String get() = t("sdk.notify.channelMessages")
    val channelMessagesDesc: String get() = t("sdk.notify.channelMessagesDesc")
    val channelAgent: String get() = t("sdk.notify.channelAgent")
    val channelAgentDesc: String get() = t("sdk.notify.channelAgentDesc")
    val channelSystem: String get() = t("sdk.notify.channelSystem")
    val channelSystemDesc: String get() = t("sdk.notify.channelSystemDesc")

    val newMessage: String get() = t("notify.newMessage")
    val agent: String get() = t("agent.agent")
    val agentJoined: String get() = t("sdk.notify.agentJoined")
    val agentLeft: String get() = t("sdk.notify.agentLeft")
    val chatEnded: String get() = t("sdk.notify.chatEnded")
    val waiting: String get() = t("sdk.notify.waiting")
    val queueUpdate: String get() = t("sdk.notify.queueUpdate")
    val inQueue: String get() = t("sdk.notify.inQueue")
    val sessionEnded: String get() = t("sdk.notify.sessionEnded")

    fun hasJoined(name: String) = t("system.hasJoined", "name" to name)
    fun hasLeft(name: String) = t("system.hasLeft", "name" to name)
    fun estimatedWait(minutes: Int) = t("sdk.notify.estimatedWait", "n" to minutes)
    fun chatEndedReason(reason: String) = t("sdk.notify.chatEndedReason", "reason" to reason)
    fun queuePosition(n: Int) = t("sdk.notify.queuePosition", "n" to n)

    /** "You are #3 in queue", with the wait when known. */
    fun position(n: Int, waitMinutes: Int?) =
        if (waitMinutes != null) t("sdk.notify.positionWait", "n" to n, "min" to waitMinutes) else t("sdk.notify.position", "n" to n)

    /** "Your position: #2", with the wait when known. */
    fun newPosition(n: Int, waitMinutes: Int?) =
        if (waitMinutes != null) t("sdk.notify.newPositionWait", "n" to n, "min" to waitMinutes) else t("sdk.notify.newPosition", "n" to n)
}
