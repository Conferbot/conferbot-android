package com.conferbot.sdk.testutils

import com.conferbot.sdk.core.flow.ApiReply
import com.conferbot.sdk.core.flow.FlowContext
import com.conferbot.sdk.core.flow.FlowContextElement
import com.conferbot.sdk.core.flow.FlowEdge
import com.conferbot.sdk.core.flow.FlowNode
import com.conferbot.sdk.core.flow.UploadFile
import com.conferbot.sdk.core.flow.UploadResult
import com.conferbot.sdk.core.nodes.NodeHandler
import com.conferbot.sdk.core.nodes.NodeResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * A step context for calling one handler on its own: scripted bot API
 * replies, recorded emits and bot lines, the step's edges, no waiting.
 */
class TestFlowContext(
    override val nodeId: String = "n",
    override val via: String = "edge",
    private val edgeList: List<FlowEdge> = emptyList(),
    private val nodes: Map<String, FlowNode> = emptyMap(),
    override val customizations: Map<String, Any?> = emptyMap(),
    private val replies: Map<String, ApiReply> = emptyMap(),
    private val uploadResult: UploadResult = UploadResult(true, "https://api.test/media/f"),
    var clock: Long = 1_792_058_400_000L,
    private val rand: Double = 0.5,
    /** The bot's bookings (a calendar's taken slots). */
    var bookings: List<Map<String, Any?>> = emptyList()
) : FlowContext {
    override val calendarBookings: List<Map<String, Any?>> get() = bookings
    override val stepIndex = 1
    override val visitorTimeZone = "UTC"
    override val pageUrl: String? = "https://shop.example/pricing?utm_source=newsletter"
    override val pageReferrer: String? = "https://www.google.com/"

    val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
    val emits = mutableListOf<Pair<String, Map<String, Any?>>>()
    val said = mutableListOf<String>()
    val responded = mutableListOf<Any>()
    val listeners = mutableMapOf<String, suspend (Map<String, Any?>?) -> Unit>()

    override suspend fun api(path: String, body: Map<String, Any?>, timeoutMs: Long?): ApiReply {
        calls.add(path to body)
        return replies[path] ?: ApiReply(false, 0, null, "network")
    }

    override suspend fun upload(file: UploadFile): UploadResult = uploadResult
    override suspend fun fetch(url: String, method: String, headers: Map<String, String>, body: String?, timeoutMs: Long?): ApiReply {
        calls.add(url to emptyMap())
        return replies[url] ?: ApiReply(false, 0, null, "network")
    }

    override fun emit(event: String, payload: Map<String, Any?>): Boolean {
        emits.add(event to payload)
        return true
    }

    override fun on(event: String, listener: suspend (Map<String, Any?>?) -> Unit): () -> Unit {
        listeners[event] = listener
        return { listeners.remove(event) }
    }

    override suspend fun typing(ms: Long) {
        clock += ms
    }

    override suspend fun delay(ms: Long) {
        clock += ms
    }

    override fun setTimeout(ms: Long, fn: () -> Unit): () -> Unit = {}
    override fun setInterval(ms: Long, fn: () -> Unit): () -> Unit = {}
    override fun edges() = edgeList.filter { it.source == nodeId }
    override fun edgesOf(nodeId: String) = edgeList.filter { it.source == nodeId }
    override fun wired(handle: String) = edges().any { it.sourceHandle == handle }
    override fun random() = rand
    override fun now() = clock
    override fun respond(response: Any) {
        responded.add(response)
    }

    override fun shown() {}
    override fun isStale() = false
    override fun node(id: String) = nodes[id]
    override fun say(text: String) {
        said.add(text)
    }

    /** Background work, kept to run by hand (runBackground). */
    val backgroundWork = mutableListOf<suspend () -> Unit>()
    override fun background(block: suspend () -> Unit) {
        backgroundWork.add(block)
    }

    fun runBackground() = runBlocking { backgroundWork.toList().also { backgroundWork.clear() }.forEach { it() } }
    override fun sendRecord() {}
    override fun joinChatRoom() {}

    fun process(handler: NodeHandler, data: Map<String, Any?>): NodeResult = runBlocking {
        withContext(FlowContextElement(this@TestFlowContext)) { handler.process(data, nodeId) }
    }

    fun respond(handler: NodeHandler, data: Map<String, Any?>, response: Any): NodeResult = runBlocking {
        withContext(FlowContextElement(this@TestFlowContext)) { handler.handleResponse(response, data, nodeId) }
    }

    companion object {
        fun edge(source: String, handle: String?, target: String) = FlowEdge("$source-$handle-$target", source, target, handle, emptyMap())
    }
}
