package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.flow.FlowContext
import com.conferbot.sdk.core.flow.FlowEdge
import com.conferbot.sdk.core.flow.FlowLogic
import com.conferbot.sdk.core.flow.FlowRich
import com.conferbot.sdk.core.flow.FlowText
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.flow.flowContext
import com.conferbot.sdk.core.nodes.*
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.core.state.RecordEntry
import com.conferbot.sdk.models.AISettings
import kotlinx.coroutines.CompletableDeferred

/**
 * The integration steps, as the web widget runs them (ts/chat/handlers/
 * integrations.ts, gptRequest.ts, resultPaths.ts), ported from RN
 * IntegrationNodeHandlers.ts. Steps the server runs go over the socket
 * ("execute-integration"); the GPT step asks the server, which holds the
 * owner's key.
 */

/** The transcript as the server reads it: [{by, message}]. */
private fun transcriptOf(): List<Map<String, Any?>> =
    ChatState.transcript.value.filter { (it.by == "bot" || it.by == "user") && it.message.trim().isNotEmpty() }.map { mapOf("by" to it.by, "message" to it.message) }

/** The answer variables as the server reads them: [{nodeId, key, value}]. */
private fun answerVariablesOf(): List<Map<String, Any?>> = ChatState.answerVariables.value.map { mapOf("nodeId" to it.nodeId, "key" to it.key, "value" to it.value) }

/** A step's data as it goes to the server (no client-side objects). */
private fun serverData(data: Map<String, Any?>) = data.filterKeys { it != "socketClient" }

/**
 * Runs a step on the embed server: emits "execute-integration" with the step's
 * own data and resolves with the "integration-result" for this step, or a
 * failure after 30 s. On success, result.answerVariable = answerValue and
 * every columnMappedValues entry are saved by name (web _executeNativeIntegration).
 */
internal suspend fun executeNativeIntegration(ctx: FlowContext, nodeType: String, nodeId: String, nodeData: Map<String, Any?>, timeoutMs: Long = 30_000): Map<String, Any?> {
    val done = CompletableDeferred<Map<String, Any?>>()
    var off: () -> Unit = {}
    var cancelTimer: () -> Unit = {}
    fun finish(result: Map<String, Any?>) {
        if (!done.complete(result)) return
        cancelTimer()
        off()
    }
    off = ctx.on("integration-result") { result ->
        if (result == null || Js.str(result["nodeId"]) != nodeId) return@on
        if (result["success"] == true) {
            val key = result["answerVariable"] as? String
            if (!key.isNullOrEmpty() && result.containsKey("answerValue")) ChatState.saveVariableByName(key, result["answerValue"])
            Js.map(result["columnMappedValues"])?.forEach { (name, value) -> if (name.isNotEmpty()) ChatState.saveVariableByName(name, value) }
        }
        finish(result)
    }
    val sent = ctx.emit(
        "execute-integration",
        mapOf(
            "nodeType" to nodeType,
            "nodeId" to nodeId,
            "nodeData" to serverData(nodeData),
            "chatSessionId" to ChatState.chatSessionId,
            "chatbotId" to ChatState.botId,
            "workspaceId" to (ChatState.workspaceId ?: ""),
            "answerVariables" to answerVariablesOf(),
            "visitorData" to ChatState.userMetadata.value.let { mapOf("name" to (it.name ?: ""), "email" to (it.email ?: ""), "phone" to (it.phone ?: "")) }
        )
    )
    if (!sent) {
        finish(mapOf("success" to false, "error" to "Socket not connected"))
    } else {
        cancelTimer = ctx.setTimeout(timeoutMs) { finish(mapOf("success" to false, "error" to "Integration execution timed out", "continueFlow" to true)) }
    }
    return done.await()
}

/**
 * webhook-node: its answerVariable is registered at once and filled later;
 * the call runs in the background and the flow moves on at once (web
 * integrations.ts _handleWebhookNode). Authentication: bearer, basic, API key,
 * OAuth2 and custom token (the token is asked for first).
 */
class WebhookNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.WEBHOOK

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val answerVariable = Js.str(nodeData["answerVariable"])
        if (answerVariable.isNotEmpty()) ChatState.registerAnswerVariable(nodeId, answerVariable)
        val ctx = flowContext()
        ctx.background { run(ctx, nodeData, nodeId) }
        return NodeResult.Proceed()
    }

    private fun fill(text: String) = FlowLogic.fillPlaceholders(text) { ChatState.lookupVariable(it) }

    private fun authType(auth: Map<String, Any?>): String {
        Js.str(auth["type"]).lowercase().takeIf { it.isNotEmpty() }?.let {
            return when (it) {
                "bearer" -> "bearer"
                "basic" -> "basic"
                "apikey", "api_key", "api-key" -> "apiKey"
                "oauth2", "oauth" -> "oauth2"
                "custom", "token" -> "custom"
                else -> "none"
            }
        }
        return when {
            Js.truthy(auth["token"]) && !Js.truthy(auth["tokenUrl"]) -> "bearer"
            Js.truthy(auth["apiKey"]) || Js.truthy(auth["headerName"]) -> "apiKey"
            Js.truthy(auth["clientId"]) && Js.truthy(auth["clientSecret"]) -> "oauth2"
            Js.truthy(auth["tokenUrl"]) && Js.truthy(auth["username"]) -> "custom"
            Js.truthy(auth["username"]) && Js.truthy(auth["password"]) && !Js.truthy(auth["tokenUrl"]) -> "basic"
            else -> "none"
        }
    }

    private suspend fun run(ctx: FlowContext, data: Map<String, Any?>, nodeId: String) {
        val url = fill(Js.str(data["url"] ?: data["webhookUrl"]).trim())
        if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(url)) return
        val method = Js.str(data["method"]).ifEmpty { "POST" }.uppercase()
        val headers = linkedMapOf<String, String>()
        val rawBody = data["body"].takeIf { Js.truthy(it) } ?: data["payload"].takeIf { Js.truthy(it) }
        if (rawBody != null && method != "GET") headers["Content-Type"] = "application/json"
        Js.map(data["headers"])?.forEach { (k, v) -> headers[k] = fill(Js.str(v)) }

        val auth = Js.map(data["authentication"]) ?: emptyMap()
        when (authType(auth)) {
            "bearer" -> headers["Authorization"] = "Bearer ${fill(Js.str(auth["token"] ?: auth["bearerToken"]))}"
            "basic" -> headers["Authorization"] = "Basic " + android.util.Base64.encodeToString("${fill(Js.str(auth["username"]))}:${fill(Js.str(auth["password"]))}".toByteArray(), android.util.Base64.NO_WRAP)
            "apiKey" -> {
                val name = Js.str(auth["key"] ?: auth["headerName"]).ifEmpty { "X-API-Key" }
                if (auth["location"] != "query") headers[name] = fill(Js.str(auth["value"] ?: auth["apiKey"]))
            }
            "oauth2", "custom" -> {
                val tokenUrl = fill(Js.str(auth["tokenUrl"]))
                val reply = if (authType(auth) == "custom") {
                    ctx.fetch(tokenUrl, "POST", mapOf("Content-Type" to "application/json"), Js.stringify(mapOf("username" to fill(Js.str(auth["username"])), "password" to fill(Js.str(auth["password"])))))
                } else {
                    val form = listOf(
                        "grant_type" to Js.str(auth["grantType"]).ifEmpty { "client_credentials" },
                        "client_id" to fill(Js.str(auth["clientId"])),
                        "client_secret" to fill(Js.str(auth["clientSecret"])),
                        "scope" to Js.str(auth["scope"])
                    ).filter { it.second.isNotEmpty() }.joinToString("&") { "${it.first}=${Js.encodeURIComponent(it.second)}" }
                    ctx.fetch(tokenUrl, "POST", mapOf("Content-Type" to "application/x-www-form-urlencoded"), form)
                }
                val tokenPath = Js.str(auth["tokenPath"]).ifEmpty { "access_token" }
                val token = if (reply.ok) FlowRich.getPath(reply.body, tokenPath)?.let { Js.str(it) } else null
                if (token.isNullOrEmpty()) return
                headers["Authorization"] = "Bearer $token"
            }
        }

        var body: String? = null
        if (method != "GET" && rawBody != null) {
            val parsed: Any? = if (rawBody is String) Js.parse(rawBody) else rawBody
            body = if (parsed is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val m = LinkedHashMap(parsed as Map<String, Any?>)
                if (Js.truthy(data["includeAnswerVariables"])) m["answerVariables"] = ChatState.getAnswerVariablesMap()
                fill(Js.stringify(m))
            } else if (parsed != null) fill(Js.stringify(parsed)) else fill(Js.str(rawBody))
        }

        val maxRetries = Js.number(data["retryCount"] ?: data["maxRetries"] ?: 3).let { if (Js.isFinite(it)) it.toInt() else 3 }
        var attempt = 0
        while (true) {
            val reply = ctx.fetch(url, method, headers, body, 30_000)
            if (reply.ok) {
                reply.body?.let { ChatState.fillAnswerVariable(nodeId, it) }
                return
            }
            val retryable = reply.status == 0 || reply.status == 408 || reply.status == 429 || reply.status >= 500
            if (!retryable || attempt >= maxRetries) {
                ChatState.setVariable("_webhookError", reply.error ?: "HTTP ${reply.status}")
                return
            }
            ctx.delay(1000L shl attempt)
            attempt++
        }
    }
}

/**
 * gpt-node: the server makes the AI call with the owner's key (it strips
 * apiKey from every flow it sends): POST {api}/api/v1/bot/:botId/gpt with
 * {nodeId, transcript (last 30 {by, message})} -> {reply}. The reply is shown
 * after 1000 ms of typing; a failed call shows nothing. Either way the flow
 * moves on along the first edge (web gptRequest.ts).
 */
class GptNodeHandler(@Suppress("UNUSED_PARAMETER") aiSettings: AISettings = AISettings()) : BaseNodeHandler() {
    override val nodeType = NodeTypes.GPT

    companion object {
        const val MAX_TURNS = 30
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ctx = flowContext()
        val reply = ctx.api("gpt", mapOf("nodeId" to nodeId, "transcript" to transcriptOf().takeLast(MAX_TURNS)))
        val text = if (reply.ok) (Js.map(reply.body)?.get("reply") as? String).orEmpty() else ""
        if (text.isEmpty()) {
            ChatState.setVariable("_gptError", reply.error ?: "HTTP ${reply.status}")
            return NodeResult.Proceed()
        }
        ChatState.addRecord(RecordEntry(id = nodeId, shape = "gpt-node-response", time = FlowKit.nowIso(), fields = mapOf("value" to text)))
        ctx.typing(1000)
        FlowKit.botSay(text, side = true)
        return NodeResult.Proceed()
    }
}

/**
 * email-node: emits "email-node-trigger" with the node data, the transcript,
 * the visitor and every answer, and moves on at once. Reached by a
 * jump-to-node it sends nothing and only moves on (web index.ts).
 */
class EmailNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.EMAIL

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ctx = flowContext()
        if (ctx.via == "jump") return NodeResult.Proceed()
        val meta = ChatState.userMetadata.value
        val payload = linkedMapOf<String, Any?>(
            "nodeData" to serverData(nodeData),
            "botName" to (ChatState.getVariable("_botName")?.toString() ?: ""),
            "transcript" to transcriptOf(),
            "visitorName" to (meta.name ?: ""),
            "visitorEmail" to (meta.email ?: "")
        )
        ChatState.answerVariables.value.forEach { payload[it.key] = it.value }
        payload["answerVariables"] = answerVariablesOf()
        payload["chatDate"] = FlowKit.nowIso()
        payload["workspaceId"] = ChatState.workspaceId ?: ""
        ctx.emit("email-node-trigger", payload)
        return NodeResult.Proceed()
    }
}

/**
 * A server-run integration that does not hold the flow (gmail, google sheets /
 * docs / drive, slack, discord, airtable, hubspot, notion, zohocrm): the step
 * data goes to the server and the flow moves on at once; values that come
 * back later fill variables.
 */
open class NativeIntegrationNodeHandler(override val nodeType: String) : BaseNodeHandler() {
    /** The node type the server runs (google-sheets runs as its read / write node). */
    protected open fun serverType(data: Map<String, Any?>): String = nodeType

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ctx = flowContext()
        ctx.background {
            val result = executeNativeIntegration(ctx, serverType(nodeData), nodeId, nodeData)
            ChatState.addRecord(
                RecordEntry(
                    id = nodeId, shape = nodeType, time = com.conferbot.sdk.core.flow.FlowCalendar.iso(ctx.now()),
                    data = mutableMapOf("result" to mapOf("success" to (result["success"] == true), "error" to result["error"]))
                )
            )
        }
        return NodeResult.Proceed()
    }
}

class GmailNodeHandler : NativeIntegrationNodeHandler(NodeTypes.GMAIL)
class SlackNodeHandler : NativeIntegrationNodeHandler(NodeTypes.SLACK)
class DiscordNodeHandler : NativeIntegrationNodeHandler(NodeTypes.DISCORD)
class GoogleDocsNodeHandler : NativeIntegrationNodeHandler(NodeTypes.GOOGLE_DOCS)
class GoogleDriveNodeHandler : NativeIntegrationNodeHandler(NodeTypes.GOOGLE_DRIVE)
class AirtableNodeHandler : NativeIntegrationNodeHandler(NodeTypes.AIRTABLE)
class HubspotNodeHandler : NativeIntegrationNodeHandler(NodeTypes.HUBSPOT)
class NotionNodeHandler : NativeIntegrationNodeHandler(NodeTypes.NOTION)
class ZohoCrmNodeHandler : NativeIntegrationNodeHandler(NodeTypes.ZOHO_CRM)

/** google-sheets-node: runs on the server as google-sheets-read-node or -write-node (data.operation). */
class GoogleSheetsNodeHandler : NativeIntegrationNodeHandler(NodeTypes.GOOGLE_SHEETS) {
    override fun serverType(data: Map<String, Any?>): String {
        val operation = data["operation"] ?: Js.map(data["inputs"])?.get("operation") ?: "read"
        return if (operation == "write") "google-sheets-write-node" else "google-sheets-read-node"
    }
}

/**
 * google-calendar-node: an operation other than "book" runs in the background
 * and the flow moves on at once; "book" waits for the server (the web's slot
 * picker is web-only) and then moves on.
 */
class GoogleCalendarNodeHandler : NativeIntegrationNodeHandler(NodeTypes.GOOGLE_CALENDAR) {
    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        if ((nodeData["operation"] ?: "list") != "book") return super.process(nodeData, nodeId)
        executeNativeIntegration(flowContext(), nodeType, nodeId, nodeData)
        return NodeResult.Proceed()
    }
}

/** google-meet-node: waits for the server's result (30 s at most), then the first edge. */
class GoogleMeetNodeHandler : NativeIntegrationNodeHandler(NodeTypes.GOOGLE_MEET) {
    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val result = executeNativeIntegration(flowContext(), nodeType, nodeId, nodeData)
        if (result["success"] == true && result["waitForInput"] != true) {
            val d = Js.map(result["data"])
            ChatState.addRecord(
                RecordEntry(
                    id = nodeId, shape = nodeType, time = FlowKit.nowIso(),
                    data = mutableMapOf(
                        "summary" to nodeData["summary"], "meetingUri" to d?.get("meetingUri"), "eventLink" to d?.get("eventLink"),
                        "booking" to d?.get("booking"), "result" to mapOf("success" to true, "error" to null)
                    )
                )
            )
        }
        return NodeResult.Proceed()
    }
}

/**
 * stripe-node: the server makes the payment link; it is shown and the flow
 * moves on 500 ms later. It never waits for the payment (payment-node does).
 */
class StripeNodeHandler : NativeIntegrationNodeHandler(NodeTypes.STRIPE) {
    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = Js.str(nodeData["operation"]).ifEmpty { "createPaymentLink" }
        val result = executeNativeIntegration(flowContext(), nodeType, nodeId, nodeData)
        val d = Js.map(result["data"])
        val url = d?.get("url") as? String
        ChatState.addRecord(
            RecordEntry(
                id = nodeId, shape = nodeType, time = FlowKit.nowIso(),
                data = mutableMapOf(
                    "operation" to operation, "url" to url, "id" to d?.get("id"), "customerId" to d?.get("customerId"),
                    "message" to result["message"], "result" to mapOf("success" to (result["success"] == true), "error" to result["error"])
                )
            )
        )
        if (result["success"] == true && !url.isNullOrEmpty()) {
            if (operation == "createPaymentLink" || operation == "createCheckoutSession") {
                FlowKit.botSay(listOfNotNull(result["message"]?.let { Js.str(it) }?.takeIf { it.isNotEmpty() }, url).joinToString("\n"), side = true)
            }
            return NodeResult.DelayedProceed(500)
        }
        return NodeResult.Proceed()
    }
}

/**
 * mailchimp / segment / salesforce: the flow waits for the server, then
 * success leaves by "source" (or an edge with no handle), failure by
 * "source-failure" - or the success edge when no failure edge is drawn.
 */
open class ResultIntegrationNodeHandler(nodeType: String) : NativeIntegrationNodeHandler(nodeType) {
    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ctx = flowContext()
        val result = executeNativeIntegration(ctx, nodeType, nodeId, nodeData)
        val outgoing = ctx.edges()
        val isSuccess = { e: FlowEdge -> e.sourceHandle == "source" || e.sourceHandle == null }
        val successEdge = outgoing.firstOrNull(isSuccess)
        val edge = if (result["success"] == true) successEdge else outgoing.firstOrNull { it.sourceHandle == "source-failure" } ?: successEdge
        if (edge == null) return NodeResult.Proceed()
        if (edge.sourceHandle == null) return NodeResult.JumpTo(edge.target)
        return NodeResult.Proceed(edge.sourceHandle)
    }
}

class MailchimpNodeHandler : ResultIntegrationNodeHandler(NodeTypes.MAILCHIMP)
class SegmentNodeHandler : ResultIntegrationNodeHandler(NodeTypes.SEGMENT)
class SalesforceNodeHandler : ResultIntegrationNodeHandler(NodeTypes.SALESFORCE)

/**
 * zapier-node: when the bot has an active webhook for the node, relays the
 * answers through the server ("zapier-node-trigger"); moves on at once.
 */
class ZapierNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ZAPIER

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val stepId = Js.str(nodeData["nodeId"] ?: nodeId)
        val hook = ChatState.integrationWebhooks.firstOrNull { it["nodeId"] == stepId && it["botId"] == ChatState.botId && it["active"] == true }
        val url = hook?.get("webhookURL") as? String
        if (url.isNullOrEmpty()) return NodeResult.Proceed()
        flowContext().emit(
            "zapier-node-trigger",
            mapOf(
                "nodeData" to serverData(nodeData) + mapOf("nodeId" to stepId, "webhookURL" to url),
                "payload" to ChatState.getAnswerVariablesMap(),
                "chatSessionId" to ChatState.chatSessionId,
                "workspaceId" to (ChatState.workspaceId ?: "")
            )
        )
        return NodeResult.Proceed()
    }
}

/** The text a step's typed prompt shows, from owner HTML (used by the rich and AI steps). */
internal fun promptText(html: Any?): String = FlowText.bubbleText(ChatState.resolveVariables(Js.str(html), html = true))
