package com.conferbot.sdk.services

import com.conferbot.sdk.notifications.PushContract
import com.conferbot.sdk.notifications.PushDeviceInfo
import com.conferbot.sdk.notifications.PushRegisterError
import com.conferbot.sdk.notifications.PushRegisterResult
import com.conferbot.sdk.notifications.PushTokenType
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test

/** The push routes (embed-server #189), the HTTP answered by an interceptor: nothing leaves the test. */
class PushApiTest {

    private class Fake(var status: Int = 200, var json: String = """{"success":true}""", var fail: Boolean = false) : Interceptor {
        val requests = mutableListOf<Request>()
        val bodies = mutableListOf<String>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            requests += req
            bodies += Buffer().also { req.body?.writeTo(it) }.readUtf8()
            if (fail) throw java.io.IOException("offline")
            return Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("x")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun client(fake: Fake) = ApiClient(
        apiKey = "key-1",
        botId = "bot-1",
        baseUrl = "https://embed.example.test/api/v1/mobile/",
        testInterceptor = fake
    )

    @Test
    fun `register posts token, chatSessionId, tokenType and deviceInfo with the mobile headers, no visitorId`() = runTest {
        val fake = Fake()
        val body = PushContract.registerBody("tok", "chat-1", PushTokenType.FCM, PushDeviceInfo(model = "Pixel 8", osVersion = "14"))
        assertThat(client(fake).registerPush(body)).isEqualTo(PushRegisterResult(true))

        val req = fake.requests.single()
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.url.encodedPath).isEqualTo("/api/v1/mobile/push/register")
        assertThat(req.header("X-Bot-ID")).isEqualTo("bot-1")
        assertThat(req.header("X-Platform")).isNotEmpty()
        val sent = JsonParser.parseString(fake.bodies.single()).asJsonObject
        assertThat(sent["token"].asString).isEqualTo("tok")
        assertThat(sent["chatSessionId"].asString).isEqualTo("chat-1")
        assertThat(sent["tokenType"].asString).isEqualTo("fcm")
        assertThat(sent["deviceInfo"].asJsonObject["model"].asString).isEqualTo("Pixel 8")
        assertThat(sent.has("visitorId")).isFalse()
        assertThat(sent.has("platform")).isFalse()
    }

    @Test
    fun `register reads the server's refusals and never throws`() = runTest {
        val fake = Fake()
        val api = client(fake)
        val body = PushContract.registerBody("tok", "chat-1", PushTokenType.FCM)

        fake.status = 404; fake.json = """{"success":false,"error":"SESSION_NOT_FOUND"}"""
        assertThat(api.registerPush(body).error).isEqualTo(PushRegisterError.SESSION_NOT_FOUND)
        fake.status = 403; fake.json = """{"success":false,"error":"SESSION_FORBIDDEN"}"""
        assertThat(api.registerPush(body).error).isEqualTo(PushRegisterError.SESSION_FORBIDDEN)
        fake.status = 400; fake.json = """{"success":false,"error":"INVALID_TOKEN"}"""
        assertThat(api.registerPush(body).error).isEqualTo(PushRegisterError.INVALID_TOKEN)
        fake.status = 404; fake.json = "Not Found"
        assertThat(api.registerPush(body).error).isEqualTo(PushRegisterError.SESSION_NOT_FOUND)
        fake.status = 502; fake.json = "<html>"
        assertThat(api.registerPush(body).error).isEqualTo(PushRegisterError.SERVER)
        fake.fail = true
        assertThat(api.registerPush(body)).isEqualTo(PushRegisterResult(false, PushRegisterError.NETWORK))
    }

    @Test
    fun `unregister posts the token only, false on a failure`() = runTest {
        val fake = Fake()
        val api = client(fake)
        assertThat(api.unregisterPush("tok")).isTrue()
        assertThat(fake.requests.single().url.encodedPath).isEqualTo("/api/v1/mobile/push/unregister")
        assertThat(JsonParser.parseString(fake.bodies.single()).asJsonObject.keySet()).containsExactly("token")

        fake.status = 500; fake.json = """{"success":false}"""
        assertThat(api.unregisterPush("tok")).isFalse()
        fake.fail = true
        assertThat(api.unregisterPush("tok")).isFalse()
    }
}
