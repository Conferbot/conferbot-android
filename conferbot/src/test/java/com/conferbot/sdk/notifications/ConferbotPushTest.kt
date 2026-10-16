package com.conferbot.sdk.notifications

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Push notifications (embed-server #189), as RN's __tests__/core/push.test.ts and Flutter's push_test.dart. */
class ConferbotPushTest {

    private val data = mapOf(
        "source" to "conferbot",
        "type" to "conferbot_message",
        "botId" to "bot-1",
        "chatSessionId" to "chat-1",
        "messageId" to "m-1",
        "senderType" to "agent",
        "senderName" to "Maya",
        "body" to "Your order ships today"
    )

    @Test
    fun `reads the server's payload`() {
        assertThat(ConferbotPush.parse(data)).isEqualTo(
            ConferbotPush("bot-1", "chat-1", "m-1", "agent", "Maya", "Your order ships today")
        )
        assertThat(ConferbotPush.isConferbotPush(data)).isTrue()
        // A tapped notification's extras carry Firebase's keys beside ours.
        assertThat(ConferbotPush.parse(data + mapOf("google.message_id" to "0:1", "from" to "123"))?.chatSessionId).isEqualTo("chat-1")
    }

    @Test
    fun `messageId may be empty, a sender that is not an agent is the bot`() {
        val p = ConferbotPush.parse(data + mapOf("messageId" to "", "senderType" to "bot"))!!
        assertThat(p.messageId).isEmpty()
        assertThat(p.senderType).isEqualTo("bot")
        assertThat(ConferbotPush.parse(data + ("senderType" to "system"))!!.senderType).isEqualTo("bot")
        assertThat(ConferbotPush.parse(data - "messageId" - "body")!!.body).isEmpty()
    }

    @Test
    fun `not ours - another source, another type, no bot or conversation`() {
        assertThat(ConferbotPush.parse(null)).isNull()
        assertThat(ConferbotPush.parse(emptyMap())).isNull()
        assertThat(ConferbotPush.parse(data + ("source" to "other"))).isNull()
        assertThat(ConferbotPush.parse(data - "source")).isNull()
        assertThat(ConferbotPush.parse(data + ("type" to "agent_joined"))).isNull()
        assertThat(ConferbotPush.parse(data + ("botId" to " "))).isNull()
        assertThat(ConferbotPush.parse(data - "chatSessionId")).isNull()
        assertThat(ConferbotPush.isConferbotPush(mapOf("title" to "Sale!"))).isFalse()
    }

    @Test
    fun `the token's type - the app's, else 64 hex is APNs`() {
        val hex = "a".repeat(64)
        assertThat(PushContract.tokenTypeOf(hex)).isEqualTo(PushTokenType.APNS)
        assertThat(PushContract.tokenTypeOf("A1".repeat(32))).isEqualTo(PushTokenType.APNS)
        assertThat(PushContract.tokenTypeOf("fcm:APA91b-abc")).isEqualTo(PushTokenType.FCM)
        assertThat(PushContract.tokenTypeOf(hex, PushTokenType.FCM)).isEqualTo(PushTokenType.FCM)
        assertThat(PushTokenType.FCM.wire).isEqualTo("fcm")
        assertThat(PushTokenType.APNS.wire).isEqualTo("apns")
    }

    @Test
    fun `the register body - token, chatSessionId, tokenType, deviceInfo, never visitorId`() {
        assertThat(PushContract.registerBody("t", "chat-1", PushTokenType.FCM))
            .isEqualTo(mapOf("token" to "t", "chatSessionId" to "chat-1", "tokenType" to "fcm"))
        val body = PushContract.registerBody("t", "chat-1", PushTokenType.FCM, PushDeviceInfo(model = "Pixel 8", osVersion = "14", appVersion = ""))
        assertThat(body["deviceInfo"]).isEqualTo(mapOf("model" to "Pixel 8", "osVersion" to "14"))
        assertThat(body).doesNotContainKey("visitorId")
        assertThat(PushContract.registerBody("t", "c", PushTokenType.APNS, PushDeviceInfo())).doesNotContainKey("deviceInfo")
        assertThat(PushContract.unregisterBody("t")).isEqualTo(mapOf("token" to "t"))
        assertThat(PushContract.REGISTER_PATH).isEqualTo("push/register")
        assertThat(PushContract.UNREGISTER_PATH).isEqualTo("push/unregister")
        assertThat(PushContract.CHANNEL_ID).isEqualTo("conferbot_messages")
    }

    @Test
    fun `reads the server's answers`() {
        assertThat(PushContract.registerResult(200, mapOf("success" to true))).isEqualTo(PushRegisterResult(true))
        assertThat(PushContract.registerResult(200, mapOf("success" to "true")).ok).isFalse()
        assertThat(PushContract.registerResult(200, null)).isEqualTo(PushRegisterResult(false, PushRegisterError.SERVER))
        for (code in listOf("MISSING_TOKEN", "INVALID_TOKEN", "MISSING_SESSION")) {
            assertThat(PushContract.registerResult(400, mapOf("success" to false, "error" to code)).error?.name).isEqualTo(code)
        }
        assertThat(PushContract.registerResult(403, mapOf("success" to false, "error" to "SESSION_FORBIDDEN")).error)
            .isEqualTo(PushRegisterError.SESSION_FORBIDDEN)
        assertThat(PushContract.registerResult(404, mapOf("success" to false, "error" to "SESSION_NOT_FOUND")).error)
            .isEqualTo(PushRegisterError.SESSION_NOT_FOUND)
        assertThat(PushContract.registerResult(404, null).error).isEqualTo(PushRegisterError.SESSION_NOT_FOUND)
        assertThat(PushContract.registerResult(500, mapOf("error" to "NETWORK")).error).isEqualTo(PushRegisterError.SERVER)
    }

    @Test
    fun `tried again only while the conversation is not there yet, or the network or server failed`() {
        assertThat(PushContract.retryable(PushRegisterError.SESSION_NOT_FOUND)).isTrue()
        assertThat(PushContract.retryable(PushRegisterError.NETWORK)).isTrue()
        assertThat(PushContract.retryable(PushRegisterError.SERVER)).isTrue()
        assertThat(PushContract.retryable(PushRegisterError.INVALID_TOKEN)).isFalse()
        assertThat(PushContract.retryable(PushRegisterError.SESSION_FORBIDDEN)).isFalse()
        assertThat(PushContract.retryable(PushRegisterError.MISSING_SESSION)).isFalse()
        assertThat(PushContract.retryable(null)).isFalse()
    }

    @Test
    fun `a tap opens the chat for the conversation on the device, else that one in Your requests`() {
        val p = ConferbotPush.parse(data)!!
        assertThat(PushContract.targetOf(p, "chat-1")).isEqualTo(PushTarget.CHAT)
        assertThat(PushContract.targetOf(p, null)).isEqualTo(PushTarget.CHAT)
        assertThat(PushContract.targetOf(p, "")).isEqualTo(PushTarget.CHAT)
        assertThat(PushContract.targetOf(p, "chat-0")).isEqualTo(PushTarget.REQUESTS)
    }

    // ---------- The registrar ----------

    private class Server {
        val registered = mutableListOf<Map<String, Any>>()
        val unregistered = mutableListOf<String>()
        var answer: (Map<String, Any>) -> PushRegisterResult = { PushRegisterResult(true) }
        fun registrar(info: PushDeviceInfo? = null) = PushRegistrar(
            register = { body -> registered += body; answer(body) },
            unregister = { t -> unregistered += t; true },
            deviceInfo = { info }
        )
    }

    @Test
    fun `nothing until there are a token and a conversation, then once per pair`() = runTest {
        val s = Server()
        val r = s.registrar(PushDeviceInfo(model = "Pixel"))
        r.setSession("chat-1")
        assertThat(s.registered).isEmpty()
        r.setToken("tok", PushTokenType.FCM)
        assertThat(s.registered).containsExactly(
            mapOf("token" to "tok", "chatSessionId" to "chat-1", "tokenType" to "fcm", "deviceInfo" to mapOf("model" to "Pixel"))
        )
        r.poke()
        r.setSession("chat-1")
        r.setToken("tok", PushTokenType.FCM)
        assertThat(s.registered).hasSize(1)
        assertThat(r.registered()).isEqualTo(PushRegistrar.Registered("tok", "chat-1"))
    }

    @Test
    fun `registered again for each new conversation`() = runTest {
        val s = Server()
        val r = s.registrar()
        r.setToken("tok")
        r.setSession("chat-1")
        r.setSession(null)
        r.setSession("chat-2")
        assertThat(s.registered.map { it["chatSessionId"] }).containsExactly("chat-1", "chat-2").inOrder()
        assertThat(r.registered()?.chatSessionId).isEqualTo("chat-2")
    }

    @Test
    fun `SESSION_NOT_FOUND is tried again as the conversation goes on`() = runTest {
        val s = Server()
        var stored = false
        s.answer = { if (stored) PushRegisterResult(true) else PushRegisterResult(false, PushRegisterError.SESSION_NOT_FOUND) }
        val r = s.registrar()
        r.setToken("tok")
        r.setSession("chat-1")
        assertThat(r.registered()).isNull()
        r.poke()
        assertThat(s.registered).hasSize(2)
        stored = true
        r.poke()
        assertThat(r.registered()).isNotNull()
        r.poke()
        assertThat(s.registered).hasSize(3)
    }

    @Test
    fun `a network failure is tried again, a thrown one too`() = runTest {
        val s = Server()
        var calls = 0
        val r = PushRegistrar(
            register = { calls++; if (calls == 1) throw java.io.IOException("offline") else PushRegisterResult(true) },
            unregister = { true }
        )
        r.setToken("tok")
        r.setSession("chat-1")
        assertThat(r.registered()).isNull()
        r.poke()
        assertThat(r.registered()).isNotNull()
        assertThat(s.unregistered).isEmpty()
    }

    @Test
    fun `a refused token or another visitor's conversation is never tried again for that pair`() = runTest {
        for (error in listOf(PushRegisterError.INVALID_TOKEN, PushRegisterError.SESSION_FORBIDDEN)) {
            val s = Server()
            s.answer = { PushRegisterResult(false, error) }
            val r = s.registrar()
            r.setToken("tok")
            r.setSession("chat-1")
            r.poke()
            r.poke()
            assertThat(s.registered).hasSize(1)
            // A new conversation is a new pair.
            r.setSession("chat-2")
            assertThat(s.registered).hasSize(2)
            // A token set again is tried again.
            r.setToken("tok")
            assertThat(s.registered).hasSize(3)
        }
    }

    @Test
    fun `logout unregisters, keeps the token, registers with the next conversation only`() = runTest {
        val s = Server()
        val r = s.registrar()
        r.setToken("tok")
        r.setSession("chat-1")
        r.unregister()
        assertThat(s.unregistered).containsExactly("tok")
        assertThat(r.registered()).isNull()
        assertThat(r.token()).isEqualTo("tok")
        // Not registered with the conversation left behind again.
        r.poke()
        r.setSession("chat-1")
        assertThat(s.registered).hasSize(1)
        r.setSession("chat-2")
        assertThat(s.registered.last()["chatSessionId"]).isEqualTo("chat-2")
    }

    @Test
    fun `a refreshed token replaces the old one, null unregisters`() = runTest {
        val s = Server()
        val r = s.registrar()
        r.setSession("chat-1")
        r.setToken("old")
        r.setToken("new")
        assertThat(s.unregistered).containsExactly("old")
        assertThat(s.registered.map { it["token"] }).containsExactly("old", "new").inOrder()
        r.setToken(null)
        assertThat(s.unregistered).containsExactly("old", "new").inOrder()
        assertThat(r.token()).isNull()
        r.poke()
        assertThat(s.registered).hasSize(2)
    }

    @Test
    fun `unregister with nothing registered sends nothing`() = runTest {
        val s = Server()
        val r = s.registrar()
        r.unregister()
        r.setSession("chat-1")
        r.unregister()
        assertThat(s.unregistered).isEmpty()
    }

    @Test
    fun `a conversation that changed during the request is registered next`() = runTest {
        val bodies = mutableListOf<Map<String, Any>>()
        lateinit var r: PushRegistrar
        var first = true
        r = PushRegistrar(
            register = { body ->
                bodies += body
                if (first) { first = false; r.setSession("chat-2") }
                PushRegisterResult(true)
            },
            unregister = { true }
        )
        r.setToken("tok")
        r.setSession("chat-1")
        assertThat(bodies.map { it["chatSessionId"] }).containsExactly("chat-1", "chat-2").inOrder()
        assertThat(r.registered()?.chatSessionId).isEqualTo("chat-2")
    }
}
