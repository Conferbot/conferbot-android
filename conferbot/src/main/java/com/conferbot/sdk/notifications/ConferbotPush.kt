package com.conferbot.sdk.notifications

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex

/**
 * Push notifications (embed-server #189, docs/mobile-push.md), as RN's
 * src/core/push.ts and Flutter's lib/src/core/push/push.dart.
 *
 * When an agent or the AI replies and the visitor has no live socket in that
 * chat, embed-server sends an FCM push to the tokens registered for that
 * conversation. Firebase is optional for the app: it gets the token, hands
 * it to `Conferbot.setPushToken`, and forwards what it receives or the
 * visitor taps to `Conferbot.handlePushNotification`.
 *
 * Pure: the payload's shape, the request bodies, how the server's answers
 * read, and the registrar that keeps one token registered with the current
 * conversation (again for each new one, unregistered on logout).
 */
object PushContract {
    /** `data.source` of every Conferbot push. */
    const val SOURCE = "conferbot"

    /** `data.type` of a new message. */
    const val TYPE_MESSAGE = "conferbot_message"

    /** The Android channel the server names (`android.notification.channel_id`). */
    const val CHANNEL_ID = ConferbotNotificationManager.CHANNEL_ID_MESSAGES

    /** Under the mobile API base (ConferBotEndpoints.apiBaseUrl, ".../api/v1/mobile/"). */
    const val REGISTER_PATH = "push/register"
    const val UNREGISTER_PATH = "push/unregister"

    private val APNS_HEX = Regex("^[0-9a-fA-F]{64}$")

    /** As the server decides when the SDK does not say: a 64-character hex token is APNs, anything else FCM. */
    fun tokenTypeOf(token: String, type: PushTokenType? = null): PushTokenType =
        type ?: if (APNS_HEX.matches(token)) PushTokenType.APNS else PushTokenType.FCM

    /** `{ token, chatSessionId, tokenType, deviceInfo? }`; never visitorId (the server takes the conversation's). */
    fun registerBody(
        token: String,
        chatSessionId: String,
        tokenType: PushTokenType,
        deviceInfo: PushDeviceInfo? = null
    ): Map<String, Any> {
        val body = linkedMapOf<String, Any>(
            "token" to token,
            "chatSessionId" to chatSessionId,
            "tokenType" to tokenType.wire
        )
        deviceInfo?.toMap()?.takeIf { it.isNotEmpty() }?.let { body["deviceInfo"] = it }
        return body
    }

    fun unregisterBody(token: String): Map<String, String> = mapOf("token" to token)

    /** Registered only on a 2xx with `success: true`; else the server's error code (a bare 404 is SESSION_NOT_FOUND). */
    fun registerResult(status: Int, data: Map<String, Any?>?): PushRegisterResult {
        if (status in 200..299 && data?.get("success") == true) return PushRegisterResult(true)
        val code = data?.get("error") as? String
        PushRegisterError.values().firstOrNull { it.server && it.name == code }?.let { return PushRegisterResult(false, it) }
        if (status == 404) return PushRegisterResult(false, PushRegisterError.SESSION_NOT_FOUND)
        return PushRegisterResult(false, PushRegisterError.SERVER)
    }

    /**
     * Worth trying again for the same token and conversation: the conversation
     * is not on the server yet (a new chat is stored when the visitor first
     * answers), or the network or server failed. A refused token or another
     * visitor's conversation is not.
     */
    fun retryable(error: PushRegisterError?): Boolean =
        error == PushRegisterError.SESSION_NOT_FOUND || error == PushRegisterError.NETWORK || error == PushRegisterError.SERVER

    /** Where a tap goes: the chat when it names the conversation on the device (or there is none), else that one in "Your requests". */
    fun targetOf(push: ConferbotPush, currentChatSessionId: String?): PushTarget =
        if (currentChatSessionId.isNullOrEmpty() || currentChatSessionId == push.chatSessionId) PushTarget.CHAT
        else PushTarget.REQUESTS
}

enum class PushTokenType(val wire: String) {
    FCM("fcm"),
    APNS("apns");
}

enum class PushTarget { CHAT, REQUESTS }

/** The server's refusals (embed-server REGISTER_PUSH_FAILURES), and NETWORK / SERVER of our own. */
enum class PushRegisterError(internal val server: Boolean) {
    MISSING_TOKEN(true),
    INVALID_TOKEN(true),
    MISSING_SESSION(true),
    SESSION_FORBIDDEN(true),
    SESSION_NOT_FOUND(true),
    NETWORK(false),
    SERVER(false);
}

data class PushRegisterResult(val ok: Boolean, val error: PushRegisterError? = null)

data class PushDeviceInfo(
    val model: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val sdkVersion: String? = null
) {
    fun toMap(): Map<String, String> = buildMap {
        model?.takeIf { it.isNotBlank() }?.let { put("model", it) }
        osVersion?.takeIf { it.isNotBlank() }?.let { put("osVersion", it) }
        appVersion?.takeIf { it.isNotBlank() }?.let { put("appVersion", it) }
        sdkVersion?.takeIf { it.isNotBlank() }?.let { put("sdkVersion", it) }
    }
}

/** A Conferbot notification's data (all strings, as the server sends them). */
data class ConferbotPush(
    val botId: String,
    val chatSessionId: String,
    /** The reply's id, may be "". */
    val messageId: String,
    /** "agent" (a person) or "bot" (the AI). */
    val senderType: String,
    val senderName: String,
    val body: String
) {
    companion object {
        /**
         * The Conferbot notification in [data] (FCM `RemoteMessage.data`, or a
         * tapped notification's Intent extras as a map), or null when it is
         * not one: another library's, another type, no bot or conversation.
         */
        @JvmStatic
        fun parse(data: Map<String, Any?>?): ConferbotPush? {
            if (data == null) return null
            fun str(key: String): String = data[key]?.toString() ?: ""
            if (str("source") != PushContract.SOURCE || str("type") != PushContract.TYPE_MESSAGE) return null
            val botId = str("botId").trim()
            val chatSessionId = str("chatSessionId").trim()
            if (botId.isEmpty() || chatSessionId.isEmpty()) return null
            return ConferbotPush(
                botId = botId,
                chatSessionId = chatSessionId,
                messageId = str("messageId"),
                senderType = if (str("senderType") == "agent") "agent" else "bot",
                senderName = str("senderName"),
                body = str("body")
            )
        }

        @JvmStatic
        fun isConferbotPush(data: Map<String, Any?>?): Boolean = parse(data) != null
    }
}

/**
 * One token registered with the current conversation: again for every new
 * conversation, once per (token, conversation) pair, and again on the next
 * [poke] while the server does not have the conversation yet. A pair the
 * server refused for good is not tried again; one request at a time.
 */
class PushRegistrar(
    private val register: suspend (Map<String, Any>) -> PushRegisterResult,
    private val unregister: suspend (String) -> Boolean,
    private val deviceInfo: () -> PushDeviceInfo? = { null }
) {
    data class Registered(val token: String, val chatSessionId: String)

    @Volatile private var token: String? = null
    @Volatile private var type: PushTokenType = PushTokenType.FCM
    @Volatile private var session: String? = null
    @Volatile private var done: Registered? = null
    // A pair the server refused for good (bad token, another visitor's chat).
    @Volatile private var refused: String? = null

    private val running = Mutex()
    private val again = AtomicBoolean(false)

    private fun key() = "$token\u0000$session"

    /** The token and conversation registered last, if any. */
    fun registered(): Registered? = done

    fun token(): String? = token

    private suspend fun attempt() {
        val t = token ?: return
        val s = session ?: return
        if (done == Registered(t, s)) return
        if (refused == key()) return
        val r = try {
            register(PushContract.registerBody(t, s, type, deviceInfo()))
        } catch (e: Exception) {
            PushRegisterResult(false, PushRegisterError.NETWORK)
        }
        if (t != token || s != session) return // moved on meanwhile: the next run has it
        if (r.ok) done = Registered(t, s)
        else if (!PushContract.retryable(r.error)) refused = key()
    }

    private suspend fun run() {
        again.set(true)
        // Already running: that run picks this one up.
        if (!running.tryLock()) return
        try {
            while (again.getAndSet(false)) attempt()
        } finally {
            running.unlock()
        }
        if (again.get()) run()
    }

    /** The app's token (null or blank clears it: unregistered with the server). */
    suspend fun setToken(next: String?, nextType: PushTokenType? = null) {
        val clean = next?.trim().orEmpty()
        if (clean.isEmpty()) {
            unregister()
            token = null
            return
        }
        val old = done?.token
        token = clean
        refused = null
        type = PushContract.tokenTypeOf(clean, nextType)
        // A refreshed token: the old one goes.
        if (old != null && old != clean) {
            done = null
            try { unregister(old) } catch (_: Exception) { /* best effort */ }
        }
        run()
    }

    /** The conversation now on the device (null: none yet). */
    suspend fun setSession(chatSessionId: String?) {
        session = chatSessionId?.takeIf { it.isNotBlank() }
        run()
    }

    /** Something happened in the conversation: a registration that waits for it to reach the server tries again. */
    suspend fun poke() = run()

    /** Logout / the visitor cleared: unregister, keep the token for the next conversation. */
    suspend fun unregister() {
        val t = done?.token ?: token
        done = null
        // Not registered with this conversation again until a new one or a new setToken.
        refused = if (token != null && session != null) key() else null
        if (t == null) return
        try { unregister(t) } catch (_: Exception) { /* best effort: the server deactivates dead tokens */ }
    }
}
