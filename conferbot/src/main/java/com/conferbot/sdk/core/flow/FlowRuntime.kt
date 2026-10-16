package com.conferbot.sdk.core.flow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * What the flow engine and its step handlers run on besides the chat state:
 * a clock, the bot's server endpoints, the socket. The SDK runs them on
 * coroutines, OkHttp and SocketClient; the flow-node parity test gives them
 * a virtual clock, scripted replies and a fake socket.
 */

/** Time, as the flow sees it. */
interface FlowClock {
    /** Epoch milliseconds. */
    fun now(): Long
    suspend fun delay(ms: Long)
    /** Run [fn] once after [ms]; returns the cancel. */
    fun setTimeout(ms: Long, fn: () -> Unit): () -> Unit
    /** Run [fn] every [ms]; returns the cancel. */
    fun setInterval(ms: Long, fn: () -> Unit): () -> Unit
}

/** The real clock, on the engine's coroutine scope. */
class CoroutineFlowClock(private val scope: CoroutineScope) : FlowClock {
    override fun now(): Long = System.currentTimeMillis()
    override suspend fun delay(ms: Long) = kotlinx.coroutines.delay(Math.max(0, ms))
    override fun setTimeout(ms: Long, fn: () -> Unit): () -> Unit {
        val job: Job = scope.launch {
            kotlinx.coroutines.delay(Math.max(0, ms))
            fn()
        }
        return { job.cancel() }
    }

    override fun setInterval(ms: Long, fn: () -> Unit): () -> Unit {
        val job: Job = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(Math.max(1, ms))
                fn()
            }
        }
        return { job.cancel() }
    }
}

/** A bot API answer (POST {api}/api/v1/bot/:botId/<path>). */
data class ApiReply(val ok: Boolean, val status: Int, val body: Any?, val error: String? = null)

/** A file to upload: a picked file (uri), or generated content (dataUrl). */
data class UploadFile(val uri: String? = null, val name: String? = null, val type: String? = null, val size: Double? = null, val dataUrl: String? = null)

data class UploadResult(val ok: Boolean, val url: String? = null, val error: String? = null)

/** The bot's server endpoints the steps call (web richApi.ts, gptRequest.ts, nodeLogic upload). */
interface FlowApi {
    /** POST JSON; never throws ("network" / "timeout" in [ApiReply.error]). */
    suspend fun post(botId: String, path: String, body: Map<String, Any?>, timeoutMs: Long?): ApiReply
    /** Upload to /media; ok with the file's url. */
    suspend fun upload(botId: String, sessionId: String, visitorId: String, file: UploadFile): UploadResult
    /** Any other HTTP request (a webhook step's own call); never throws. */
    suspend fun fetch(url: String, method: String, headers: Map<String, String>, body: String?, timeoutMs: Long?): ApiReply
}

/** The socket as the flow uses it. */
interface FlowSocket {
    /** Emit an event; false when it could not go. */
    fun emit(event: String, payload: Map<String, Any?>): Boolean
    /** Listen; returns the unsubscribe. */
    fun on(event: String, listener: (Map<String, Any?>?) -> Unit): () -> Unit
    /** The conversation record (response-record). */
    fun sendResponseRecord(data: Map<String, Any?>)
    /** Be in the chat's room (join-chat-room-visitor), before a handover. */
    fun joinChatRoom(chatSessionId: String) {}
}

/** A node of the flow, as the builder saved it. */
data class FlowNode(val id: String, val type: String, val data: Map<String, Any?>, val raw: Map<String, Any?>)

/** An edge of the flow. */
data class FlowEdge(val id: String, val source: String, val target: String, val sourceHandle: String?, val raw: Map<String, Any?>)

/**
 * What a step handler may use besides the chat state: the bot API, the
 * socket, timers, the flow's edges (RN src/core/flow/context.ts). The engine
 * puts one in the coroutine context of every handler call; a handler called
 * on its own gets [FlowContext.Standalone].
 */
interface FlowContext {
    val nodeId: String
    /** How this step was reached: "start", "edge:<h>", "port:<h>" or "jump". */
    val via: String
    /** The step's place in the conversation (0 = the welcome). */
    val stepIndex: Int
    val customizations: Map<String, Any?>
    /** The visitor's IANA time zone. */
    val visitorTimeZone: String
    /** The page the chat runs on, when the host app supplies one (hidden-fields). */
    val pageUrl: String?
    val pageReferrer: String?
    /** The bot's bookings (the bot data's calendarBookings), for a calendar's taken slots. */
    val calendarBookings: List<Map<String, Any?>> get() = emptyList()

    suspend fun api(path: String, body: Map<String, Any?>, timeoutMs: Long? = null): ApiReply
    suspend fun upload(file: UploadFile): UploadResult
    /** Any other HTTP request (a webhook step's own call). */
    suspend fun fetch(url: String, method: String, headers: Map<String, String>, body: String?, timeoutMs: Long? = null): ApiReply
    fun emit(event: String, payload: Map<String, Any?>): Boolean
    fun on(event: String, listener: suspend (Map<String, Any?>?) -> Unit): () -> Unit
    /** Typing dots for [ms], then on. */
    suspend fun typing(ms: Long)
    suspend fun delay(ms: Long)
    fun setTimeout(ms: Long, fn: () -> Unit): () -> Unit
    fun setInterval(ms: Long, fn: () -> Unit): () -> Unit
    /** The edges that leave this step, in flow order. */
    fun edges(): List<FlowEdge>
    fun edgesOf(nodeId: String): List<FlowEdge>
    /** Whether an edge leaves this step by exactly this handle. */
    fun wired(handle: String): Boolean
    fun random(): Double
    fun now(): Long
    /** Give the waiting step an answer from outside (a socket event, a timer). */
    fun respond(response: Any)
    /** The step's content is on screen now (for a step that speaks before it returns). */
    fun shown()
    /** True once the flow has moved past this step. */
    fun isStale(): Boolean
    fun node(id: String): FlowNode?
    /** A bot line the chat shows besides the step's own card (a greeting, a reply). */
    fun say(text: String)
    /** Run in the background, off the flow (a webhook call). */
    fun background(block: suspend () -> Unit)
    /** The conversation record to the server now (response-record). */
    fun sendRecord()
    /** Be in the chat's room before a handover. */
    fun joinChatRoom()

    /** For a handler called outside the engine: no server, no socket, the real clock. */
    object Standalone : FlowContext {
        override val nodeId = ""
        override val via = "edge"
        override val stepIndex = 0
        override val customizations: Map<String, Any?> = emptyMap()
        override val visitorTimeZone: String get() = FlowCalendar.visitorTimeZone()
        override val pageUrl: String? = null
        override val pageReferrer: String? = null
        override suspend fun api(path: String, body: Map<String, Any?>, timeoutMs: Long?) = ApiReply(false, 0, null, "network")
        override suspend fun upload(file: UploadFile) = UploadResult(false, error = "network")
        override suspend fun fetch(url: String, method: String, headers: Map<String, String>, body: String?, timeoutMs: Long?) = ApiReply(false, 0, null, "network")
        override fun emit(event: String, payload: Map<String, Any?>) = false
        override fun on(event: String, listener: suspend (Map<String, Any?>?) -> Unit): () -> Unit = {}
        override suspend fun typing(ms: Long) {}
        override suspend fun delay(ms: Long) {}
        override fun setTimeout(ms: Long, fn: () -> Unit): () -> Unit = {}
        override fun setInterval(ms: Long, fn: () -> Unit): () -> Unit = {}
        override fun edges(): List<FlowEdge> = emptyList()
        override fun edgesOf(nodeId: String): List<FlowEdge> = emptyList()
        override fun wired(handle: String) = false
        override fun random() = Math.random()
        override fun now() = System.currentTimeMillis()
        override fun respond(response: Any) {}
        override fun shown() {}
        override fun isStale() = false
        override fun node(id: String): FlowNode? = null
        override fun say(text: String) {}
        override fun background(block: suspend () -> Unit) {}
        override fun sendRecord() {}
        override fun joinChatRoom() {}
    }
}

/** Carries the step's [FlowContext] into its handler. */
class FlowContextElement(val ctx: FlowContext) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<FlowContextElement>
}

/** The context the engine gave this handler call, or [FlowContext.Standalone]. */
suspend fun flowContext(): FlowContext = coroutineContext[FlowContextElement]?.ctx ?: FlowContext.Standalone

/**
 * The bot API over HTTP (OkHttp): {origin}/api/v1/bot/:botId/<path>, as the
 * web widget calls it. [readFile] reads a picked file's bytes (a content://
 * or file:// uri) for an upload.
 */
class HttpFlowApi(
    private val origin: () -> String,
    private val readFile: (String) -> ByteArray? = { null }
) : FlowApi {
    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun base(botId: String) = "${origin().trimEnd('/')}/api/v1/bot/${Js.encodeURIComponent(botId)}/"

    override suspend fun post(botId: String, path: String, body: Map<String, Any?>, timeoutMs: Long?): ApiReply = withContext(Dispatchers.IO) {
        val run: suspend () -> ApiReply = {
            try {
                val req = okhttp3.Request.Builder()
                    .url(base(botId) + path)
                    .post(Js.stringify(body).toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                client.newCall(req).execute().use { res ->
                    val text = res.body?.string().orEmpty()
                    ApiReply(res.isSuccessful, res.code, if (text.isBlank()) null else Js.parse(text))
                }
            } catch (e: Exception) {
                ApiReply(false, 0, null, "network")
            }
        }
        if (timeoutMs != null) withTimeoutOrNull(timeoutMs) { run() } ?: ApiReply(false, 0, null, "timeout") else run()
    }

    override suspend fun upload(botId: String, sessionId: String, visitorId: String, file: UploadFile): UploadResult = withContext(Dispatchers.IO) {
        try {
            val bytes: ByteArray = when {
                file.dataUrl != null -> dataUrlBytes(file.dataUrl)
                file.uri != null -> readFile(file.uri) ?: return@withContext UploadResult(false, error = "unreadable")
                else -> return@withContext UploadResult(false, error = "no file")
            }
            val type = file.type ?: "application/octet-stream"
            val form = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", file.name ?: "file", bytes.toRequestBody(type.toMediaTypeOrNull()))
                .build()
            val url = base(botId) + "media?chatSessionId=${Js.encodeURIComponent(sessionId)}&visitorId=${Js.encodeURIComponent(visitorId)}"
            client.newCall(okhttp3.Request.Builder().url(url).post(form).build()).execute().use { res ->
                val json = Js.map(Js.parse(res.body?.string().orEmpty()))
                val fileUrl = json?.get("url") as? String
                // Only a 2xx answer that carries the file's URL is an upload (web nodeLogic.uploadSucceeded)
                if (res.isSuccessful && !fileUrl.isNullOrBlank()) UploadResult(true, fileUrl) else UploadResult(false, error = "HTTP ${res.code}")
            }
        } catch (e: Exception) {
            UploadResult(false, error = "network")
        }
    }

    override suspend fun fetch(url: String, method: String, headers: Map<String, String>, body: String?, timeoutMs: Long?): ApiReply = withContext(Dispatchers.IO) {
        val run: suspend () -> ApiReply = {
            try {
                val builder = okhttp3.Request.Builder().url(url)
                headers.forEach { (k, v) -> builder.header(k, v) }
                val type = (headers["Content-Type"] ?: "application/json").toMediaTypeOrNull()
                builder.method(method.uppercase(), if (method.uppercase() == "GET" || method.uppercase() == "HEAD") null else (body ?: "").toRequestBody(type))
                client.newCall(builder.build()).execute().use { res ->
                    val text = res.body?.string().orEmpty()
                    ApiReply(res.isSuccessful, res.code, if (text.isBlank()) null else (Js.parse(text) ?: text))
                }
            } catch (e: Exception) {
                ApiReply(false, 0, null, "network")
            }
        }
        if (timeoutMs != null) withTimeoutOrNull(timeoutMs) { run() } ?: ApiReply(false, 0, null, "timeout") else run()
    }

    private fun dataUrlBytes(dataUrl: String): ByteArray {
        val comma = dataUrl.indexOf(',')
        val meta = dataUrl.substring(0, Math.max(0, comma))
        val payload = dataUrl.substring(comma + 1)
        return if (meta.endsWith(";base64")) android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
        else Js.decodeURIComponent(payload).toByteArray(Charsets.UTF_8)
    }

}
