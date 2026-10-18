package com.conferbot.sdk.services

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

/**
 * The store-only REST route POST session/{id}/message (embed-server #194): it takes
 * { record, answerVariables, visitorId? }, never runs the bot and answers storeOnly.
 * The HTTP is answered by an interceptor: nothing leaves the test (#98).
 */
class StoreRecordApiTest {

    private class Fake(var status: Int = 200, var json: String = """{"success":true,"data":{"success":true,"responseId":"r1","storeOnly":true}}""") : Interceptor {
        val requests = mutableListOf<Request>()
        val bodies = mutableListOf<String>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            requests += req
            bodies += Buffer().also { req.body?.writeTo(it) }.readUtf8()
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
    fun `posts the record and answer variables, not a message, and reads storeOnly`() = runTest {
        val fake = Fake()
        val record = listOf(mapOf("_id" to "m1", "type" to "user-input-response", "text" to "hi"))
        val vars = listOf(mapOf("key" to "name", "value" to "Ada"))
        val r = client(fake).sendMessage("chat-1", record, vars, visitorId = "v1")

        assertThat(r.success).isTrue()
        assertThat(r.data?.get("storeOnly")).isEqualTo(true)
        assertThat(r.data?.get("responseId")).isEqualTo("r1")
        val req = fake.requests.single()
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.url.encodedPath).isEqualTo("/api/v1/mobile/session/chat-1/message")
        val sent = JsonParser.parseString(fake.bodies.single()).asJsonObject
        assertThat(sent["record"].asJsonArray[0].asJsonObject["text"].asString).isEqualTo("hi")
        assertThat(sent["answerVariables"].asJsonArray[0].asJsonObject["key"].asString).isEqualTo("name")
        assertThat(sent["visitorId"].asString).isEqualTo("v1")
        assertThat(sent.has("message")).isFalse()
        assertThat(sent.has("metadata")).isFalse()
    }

    @Test
    fun `leaves visitorId out when not given and reports a locked conversation`() = runTest {
        val fake = Fake(status = 423, json = """{"success":false,"error":"CONVERSATION_LOCKED","data":{"locked":true,"storeOnly":true}}""")
        val r = client(fake).sendMessage("chat-1", emptyList<Any?>())

        assertThat(r.success).isFalse()
        assertThat(r.error).isEqualTo("CONVERSATION_LOCKED")
        assertThat(r.data?.get("locked")).isEqualTo(true)
        val sent = JsonParser.parseString(fake.bodies.single()).asJsonObject
        assertThat(sent.has("visitorId")).isFalse()
        assertThat(sent["answerVariables"].asJsonArray.size()).isEqualTo(0)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `the old message form sends nothing the route would ignore`() = runTest {
        val fake = Fake()
        val r = client(fake).sendMessage("chat-1", "hello", mapOf("a" to 1))

        assertThat(r.success).isFalse()
        assertThat(r.error).startsWith("STORE_ONLY_ROUTE")
        assertThat(fake.requests).isEmpty()
    }
}
