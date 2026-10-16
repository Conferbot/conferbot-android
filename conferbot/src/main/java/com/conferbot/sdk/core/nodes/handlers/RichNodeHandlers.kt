package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.flow.FlowCalendar
import com.conferbot.sdk.core.flow.FlowContext
import com.conferbot.sdk.core.flow.FlowRich
import com.conferbot.sdk.core.flow.FlowRich.t
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.flow.UploadFile
import com.conferbot.sdk.core.flow.flowContext
import com.conferbot.sdk.core.nodes.*
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.core.state.RecordEntry

/**
 * The rich steps (conferbot-widget/src/ts/chat/richNodes.ts and
 * richNodesView.ts), run headless: dynamic-choices, hidden-fields, consent,
 * product-card, slider, datetime, ranking, otp, address, matrix, ai-extract,
 * ai-route, kb-answer, signature and payment. The rules are the web's, ported
 * in core/flow/FlowRich (by way of RN RichNodeHandlers.ts); each step draws
 * through the SDK's existing UI states (choices, slider, calendar, text
 * input) and takes the answer those send, or the explicit actions listed on
 * each handler.
 *
 * Gates stay gates: payment leaves by "source" only when the server says
 * paid, OTP only when otp/verify says verified (the web's documented
 * fallback aside), required consent only once agreed.
 */

/** A server call as the rich steps read it (web richApi.ts richPost). */
private data class RichReply(val ok: Boolean, val status: Int, val data: Map<String, Any?>?, val code: String?)

private suspend fun richPost(ctx: FlowContext, path: String, body: Map<String, Any?>, timeoutMs: Long = 15_000): RichReply {
    val r = ctx.api(path, body, timeoutMs)
    if (r.error != null) return RichReply(false, 0, null, r.error)
    val data = Js.map(r.body)
    return RichReply(r.ok, r.status, data, if (r.ok) null else (data?.get("code")?.let { Js.str(it) } ?: if (r.status == 429) "rate_limited" else "error"))
}

/** Who a code / payment belongs to: the chat session (web richSessionId). */
private fun sessionOf(): String = (ChatState.chatSessionId ?: ChatState.visitorId ?: "anonymous").take(100)

/** A variable read the way the rich steps read it: the answers, by name. */
private fun lookup(key: String): Any? = if (ChatState.hasAnswerVariable(key)) ChatState.getAnswer(key) else null

/** Fills the step's own variable (registered when shown) and the rest by name. */
private fun saveVars(nodeId: String, data: Map<String, Any?>, vars: Map<String, Any?>) {
    val key = Js.str(data["answerVariable"]).trim()
    vars.forEach { (k, v) -> if (k == key) ChatState.fillAnswerVariable(nodeId, v) else ChatState.saveVariableByName(k, v) }
}

/** The visitor's answer (web richNodesView answer()): record, transcript, the step's variable. */
private suspend fun answer(nodeId: String, data: Map<String, Any?>, entry: Map<String, Any?>, text: String, value: Any?) {
    FlowKit.pushAnswer(mapOf("id" to nodeId) + entry)
    FlowKit.userSay(text)
    if (Js.truthy(data["answerVariable"])) ChatState.fillAnswerVariable(nodeId, value)
}

abstract class RichNodeHandler : BaseNodeHandler() {
    protected fun prompt(data: Map<String, Any?>, fallback: String = ""): String {
        val q = data["questionText"]
        return FlowKit.shownText(if (FlowRich.plainText(q).isNotEmpty()) q else fallback)
    }

    protected fun textInput(
        nodeId: String, question: String, data: Map<String, Any?>, type: NodeUIState.TextInput.InputType = NodeUIState.TextInput.InputType.TEXT,
        placeholder: String? = null, extra: Map<String, Any?> = emptyMap()
    ) = NodeUIState.TextInput(
        questionText = question, inputType = type, placeholder = placeholder, nodeId = nodeId, answerKey = Js.str(data["answerVariable"]),
        step = if (extra.isEmpty()) null else NodeUIState.StepInfo(extra = extra)
    )
}

/**
 * dynamic-choices-node: options from the list in data.sourceVariable
 * (labelField / valueField); saves the item's value and <var>_label when it
 * differs. Nothing to choose: emptyMessage and source-empty (or "source"
 * when only that is wired). "webhook" waits up to 8 s for the list.
 */
class DynamicChoicesNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.DYNAMIC_CHOICES

    private val shown = mutableMapOf<String, List<FlowRich.DynamicOption>>()

    private suspend fun sourceValue(data: Map<String, Any?>, ctx: FlowContext): Any? {
        val key = FlowRich.dynamicSourceVariable(data)
        fun read() = if (key.isNotEmpty()) lookup(key) else null
        var v = read()
        if (v != null || data["source"] != "webhook") return v
        val started = ctx.now()
        while (ctx.now() - started <= 8000) {
            ctx.delay(250)
            v = read()
            if (v != null) return v
        }
        return v
    }

    private fun ui(data: Map<String, Any?>, nodeId: String, options: List<FlowRich.DynamicOption>) =
        NodeUIState.SingleChoice(prompt(data), options.map { NodeUIState.SingleChoice.Choice(id = it.id, text = it.label) }, nodeId, Js.str(data["answerVariable"]))

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ctx = flowContext()
        val options = FlowRich.dynamicOptions(nodeData, sourceValue(nodeData, ctx))
        ChatState.addRecord(
            RecordEntry(id = nodeId, shape = "", type = nodeType, time = FlowKit.nowIso(), fields = mapOf("options" to options.map { mapOf("label" to it.label, "value" to it.value) }))
        )
        if (options.isEmpty()) {
            val empty = nodeData["emptyMessage"].takeIf { Js.truthy(it) } ?: "<p>${t("rich.nothingToChoose")}</p>"
            FlowKit.botSay(FlowKit.shownText(empty), side = true)
            return NodeResult.Proceed(FlowRich.DYNAMIC_EMPTY_HANDLE, "source")
        }
        val ui = ui(nodeData, nodeId, options)
        FlowKit.botSay(ui.questionText ?: "")
        shown[nodeId] = options
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val options = shown[nodeId] ?: emptyList()
        val id = FlowKit.pickedId(response)
        val r = FlowKit.asMap(response)
        val o = options.firstOrNull { it.id == id }
            ?: (r["index"] as? Number)?.let { options.getOrNull(it.toInt()) }
            ?: options.firstOrNull { it.label == Js.str(r["text"] ?: r["label"]) }
            ?: return NodeResult.DisplayUI(ui(nodeData, nodeId, options))
        answer(nodeId, nodeData, mapOf("shape" to "user-selected-dynamic-choice", "selectedChoice" to o.label, "value" to o.value), o.label, o.value)
        val key = Js.str(nodeData["answerVariable"])
        if (key.isNotEmpty() && Js.str(o.value) != o.label) ChatState.saveVariableByName("${key}_label", o.label)
        shown.remove(nodeId)
        return NodeResult.Proceed("source")
    }
}

/**
 * hidden-fields-node: shows nothing; saves the page's query parameters, its
 * URL and the referrer by name. An app has no page: the host app supplies one
 * (NodeFlowEngine.Config pageUrl, e.g. a deep link) or nothing is captured.
 */
class HiddenFieldsNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.HIDDEN_FIELDS

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ctx = flowContext()
        val captured = try {
            FlowRich.captureHiddenFields(nodeData, ctx.pageUrl, ctx.pageReferrer)
        } catch (e: Exception) {
            emptyMap()
        }
        captured.forEach { (k, v) -> ChatState.saveVariableByName(k, v) }
        ChatState.addRecord(RecordEntry(id = nodeId, shape = "", type = nodeType, time = FlowKit.nowIso(), fields = mapOf("captured" to captured)))
        return NodeResult.Proceed("source")
    }
}

/**
 * consent-node: the consent text and Continue. Answers: {agreed: boolean}
 * (the box ticked or not, then Continue), or the buttons "agree" /
 * "continue". Required and not agreed: "Please tick the box to continue."
 * and it waits. Saves true / false and <var>_at; always leaves by "source".
 */
class ConsentNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.CONSENT

    private fun ui(data: Map<String, Any?>, nodeId: String): NodeUIState.SingleChoice {
        val text = FlowRich.markdownLitePlain(data["consentText"])
        return NodeUIState.SingleChoice(
            prompt(data),
            listOf(
                NodeUIState.SingleChoice.Choice(id = "agree", text = if (text.isNotEmpty()) "✓ $text" else "I agree"),
                NodeUIState.SingleChoice.Choice(id = "continue", text = FlowRich.consentContinueLabel(data))
            ),
            nodeId,
            Js.str(data["answerVariable"])
        )
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        if (!ui.questionText.isNullOrEmpty()) FlowKit.botSay(ui.questionText)
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val r = FlowKit.asMap(response)
        val agreed = (r["agreed"] as? Boolean) ?: (FlowKit.pickedId(response) == "agree")
        val required = FlowRich.consentRequired(nodeData)
        if (required && !agreed) return NodeResult.Invalid(t("consent.tickBox"), ui(nodeData, nodeId), inCard = true)
        val at = FlowKit.nowIso()
        val reply = FlowRich.consentReply(nodeData["consentText"], agreed)
        answer(
            nodeId, nodeData,
            mapOf(
                "shape" to "user-consent", "agreed" to agreed, "agreedAt" to (if (agreed) at else null),
                "consentText" to FlowRich.markdownLitePlain(nodeData["consentText"]), "documents" to FlowRich.consentDocuments(nodeData["consentText"]),
                "required" to required, "selectedChoice" to reply
            ),
            reply, agreed
        )
        val k = Js.str(nodeData["answerVariable"])
        if (k.isNotEmpty() && agreed) ChatState.saveVariableByName("${k}_at", at)
        return NodeResult.Proceed("source")
    }
}

/**
 * product-card-node: one card (image, title, price, description) with up to
 * three buttons. A path button leaves by "source-" + its id and saves its
 * label; with only link buttons, Continue leaves by "source".
 */
class ProductCardNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.PRODUCT_CARD

    private fun card(data: Map<String, Any?>) = carouselCards(FlowRich.productCarouselData(data)).firstOrNull()

    private fun ui(data: Map<String, Any?>, nodeId: String): NodeUIState.SingleChoice {
        val c = card(data)
        val question = listOf(prompt(data), c?.let { listOf(FlowKit.shownLabel(it.title), it.price, it.description).filter { s -> s.isNotEmpty() }.joinToString(" · ") } ?: "")
            .filter { it.isNotEmpty() }.joinToString("\n")
        return NodeUIState.SingleChoice(question, carouselChoices(listOfNotNull(c)), nodeId, Js.str(data["answerVariable"]))
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val q = prompt(nodeData)
        if (q.isNotEmpty()) FlowKit.botSay(q)
        return NodeResult.DisplayUI(ui(nodeData, nodeId))
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val c = card(nodeData)
        val r = FlowKit.asMap(response)
        val id = if (r["action"] == "continue") null else FlowKit.pickedId(response)?.takeIf { it != "continue" }
        val button = c?.buttons?.firstOrNull { it.id == id && it.action == "path" }
        if (id != null && button == null) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        if (button != null) {
            answer(
                nodeId, nodeData,
                mapOf("shape" to "user-selected-product-card", "selectedChoice" to button.label, "selectedButtonId" to button.id, "product" to mapOf("title" to c.title, "price" to c.price)),
                button.label, button.label
            )
            return NodeResult.Proceed("source-${button.id}")
        }
        if (c?.buttons?.any { it.action == "path" } == true) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-selected-product-card", "selectedChoice" to CAROUSEL_CONTINUE, "selectedButtonId" to null))
        FlowKit.userSay(CAROUSEL_CONTINUE)
        return NodeResult.Proceed("source")
    }
}

/** slider-node: min / max / step / unit; the value snapped to the step, saved as a number. Answer {value} or the slider's number. */
class SliderNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.SLIDER

    private fun ui(data: Map<String, Any?>, nodeId: String): NodeUIState.Range {
        val cfg = FlowRich.sliderConfig(data)
        return NodeUIState.Range(prompt(data), cfg.min.toInt(), cfg.max.toInt(), cfg.start.toInt(), nodeId, Js.str(data["answerVariable"]))
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val cfg = FlowRich.sliderConfig(nodeData)
        val raw = numberAnswer(response)
        if (!Js.isFinite(raw)) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        val value = Js.tidy(FlowRich.snapToStep(raw, cfg.min, cfg.max, cfg.step))
        val text = FlowRich.sliderLabel(value, cfg)
        answer(nodeId, nodeData, mapOf("shape" to "user-slider", "value" to value, "display" to text, "min" to Js.tidy(cfg.min), "max" to Js.tidy(cfg.max), "unit" to cfg.unit), text, value)
        return NodeResult.Proceed("source")
    }
}

/**
 * datetime-node: mode date / time / datetime with minDate / maxDate,
 * disabledWeekdays, minTime / maxTime. A refused pick says why on the card.
 * Saves the ISO value and <var>_display. Answer {value} ("2026-10-19",
 * "14:30", "2026-10-19T14:30") or the calendar card's {date, time}.
 */
class DateTimeNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.DATETIME

    private fun ui(data: Map<String, Any?>, nodeId: String) =
        NodeUIState.Calendar(prompt(data), FlowRich.dateMode(data) != "date", null, null, nodeId, Js.str(data["answerVariable"]))

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ctx = flowContext()
        val mode = FlowRich.dateMode(nodeData)
        val r = FlowKit.asMap(response)
        var raw: Any? = if (response is String) response else r["value"] ?: r["datetime"]
        if (raw == null && r["date"] != null) raw = if (mode == "datetime") "${Js.str(r["date"])}T${Js.str(r["time"])}" else r["date"]
        if (raw == null && r["time"] != null) raw = r["time"]
        val iso = FlowRich.isoFromInput(mode, raw)
        val today = FlowCalendar.dayKeyInZone(ctx.now(), ctx.visitorTimeZone)
        FlowRich.dateTimeError(nodeData, iso, today)?.let { return NodeResult.Invalid(it, ui(nodeData, nodeId), inCard = true) }
        val display = FlowRich.dateTimeDisplay(nodeData, iso)
        answer(nodeId, nodeData, mapOf("shape" to "user-datetime", "value" to iso, "display" to display, "mode" to mode), display, iso)
        val k = Js.str(nodeData["answerVariable"])
        if (k.isNotEmpty()) ChatState.saveVariableByName("${k}_display", display)
        return NodeResult.Proceed("source")
    }
}

/**
 * ranking-node: the items in the visitor's order. Answer {order: [ids]}, or
 * the multi-select card's picks in order (unpicked items follow).
 */
class RankingNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.RANKING

    private fun ui(data: Map<String, Any?>, nodeId: String) = NodeUIState.MultipleChoice(
        prompt(data), FlowRich.rankingItems(data).map { NodeUIState.MultipleChoice.Option(it.id, it.text) }, nodeId, Js.str(data["answerVariable"])
    )

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val items = FlowRich.rankingItems(nodeData)
        val r = FlowKit.asMap(response)
        val ids: List<String> = when {
            r["order"] is List<*> -> Js.list(r["order"]).map { Js.str(it) }
            r["buttonIds"] is List<*> -> Js.list(r["buttonIds"]).map { Js.str(it) }
            response is List<*> -> response.mapNotNull { label -> items.firstOrNull { it.text == Js.str(label) || it.id == Js.str(label) }?.id }
            else -> emptyList()
        }
        val picked = ids.mapNotNull { id -> items.firstOrNull { it.id == id } }.distinct()
        val order = picked + items.filter { it !in picked }
        val text = FlowRich.rankingText(order)
        val values = order.map { it.text }
        answer(nodeId, nodeData, mapOf("shape" to "user-ranking", "order" to order.map { mapOf("id" to it.id, "text" to it.text) }, "value" to values, "display" to text), text, values)
        return NodeResult.Proceed("source")
    }
}

/**
 * otp-node: an e-mail, then a six-digit code checked on the server (POST
 * otp/send, otp/verify). Answers: {action: "send", email} (or the typed
 * e-mail), {action: "verify", code} (or six typed digits), {action: "skip"}
 * (allowSkip). Verified leaves by "source"; locked, skipped, sms or a failed
 * send by "source-failed" - or "source" when only that edge is drawn (the
 * web's goPort fallback). A network error says so and keeps waiting.
 */
class OtpNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.OTP

    private data class Stage(val stage: String, val email: String = "")

    private val stages = mutableMapOf<String, Stage>()

    /** The card: the stage, where the code went and when it may be sent again, Skip. */
    private fun ui(data: Map<String, Any?>, nodeId: String, stage: String, resendIn: Int = FlowRich.OTP_RESEND_SECONDS): NodeUIState.TextInput {
        val extra = linkedMapOf<String, Any?>(
            "stage" to stage,
            "allowSkip" to Js.truthy(data["allowSkip"]),
            "skipLabel" to Js.str(data["skipLabel"]).trim().ifEmpty { t("common.skip") }
        )
        if (stage == "code") {
            extra["sentTo"] = com.conferbot.sdk.core.flow.FlowStepAnswer.maskEmail(stages[nodeId]?.email)
            extra["resendIn"] = resendIn
        }
        return if (stage == "email") textInput(nodeId, prompt(data).ifEmpty { "Email address" }, data, NodeUIState.TextInput.InputType.EMAIL, "name@example.com", extra)
        else textInput(nodeId, "Enter the ${FlowRich.OTP_LENGTH}-digit code we sent you", data, NodeUIState.TextInput.InputType.NUMBER, "123456", extra)
    }

    private fun seconds(v: Any?): Int = Js.number(v).let { if (Js.isFinite(it) && it > 0) Math.ceil(it).toInt() else FlowRich.OTP_RESEND_SECONDS }

    private suspend fun finish(data: Map<String, Any?>, nodeId: String, verified: Boolean, email: String, reason: String? = null): NodeResult {
        val text = FlowRich.otpReplyText(verified, email)
        val entry = linkedMapOf<String, Any?>("id" to nodeId, "shape" to "user-otp", "verified" to verified)
        if (verified) entry["email"] = email
        entry["channel"] = "email"
        if (reason != null) entry["reason"] = reason
        entry["at"] = FlowKit.nowIso()
        FlowKit.pushAnswer(entry)
        FlowKit.userSay(text)
        val k = Js.str(data["answerVariable"]).trim()
        if (k.isNotEmpty()) {
            if (verified) ChatState.fillAnswerVariable(nodeId, email)
            ChatState.saveVariableByName("${k}_verified", verified)
        }
        stages.remove(nodeId)
        return if (verified) NodeResult.Proceed("source") else NodeResult.Proceed("source-failed", "source")
    }

    private suspend fun send(data: Map<String, Any?>, nodeId: String, to: String): NodeResult {
        val ctx = flowContext()
        if (!FlowRich.isEmailAddress(to)) return NodeResult.Invalid(FlowRich.otpErrorText("invalid_email"), ui(data, nodeId, "email"), inCard = true)
        val target = to.trim().lowercase()
        val r = richPost(ctx, "otp/send", mapOf("nodeId" to nodeId, "sessionId" to sessionOf(), "email" to target))
        if (r.ok || r.code == "resend_wait") {
            stages[nodeId] = Stage("code", target)
            FlowKit.botSay("We sent a ${FlowRich.OTP_LENGTH}-digit code to ${Js.str(r.data?.get("sentTo") ?: target)}", side = true)
            if (r.code == "resend_wait") return NodeResult.Invalid(FlowRich.otpErrorText("resend_wait", retryAfter = r.data?.get("retryAfter")), ui(data, nodeId, "code", seconds(r.data?.get("retryAfter"))), inCard = true)
            return NodeResult.DisplayUI(ui(data, nodeId, "code", seconds(r.data?.get("resendIn"))))
        }
        if (FlowRich.otpEndsStep(r.code)) {
            return finish(data, nodeId, false, "", when (r.code) {
                "locked" -> "locked"
                "sms_unavailable" -> "sms_unavailable"
                else -> "send_failed"
            })
        }
        return NodeResult.Invalid(FlowRich.otpErrorText(r.code), ui(data, nodeId, "email"), inCard = true)
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        stages.remove(nodeId)
        if (FlowRich.otpChannel(nodeData) == "sms") return finish(nodeData, nodeId, false, "", "sms_unavailable")
        val ui = ui(nodeData, nodeId, "email")
        FlowKit.botSay(ui.questionText)
        val preset = FlowRich.otpPresetEmail(nodeData, ::lookup)
        if (preset.isNotEmpty()) return send(nodeData, nodeId, preset)
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val st = stages[nodeId] ?: Stage("email")
        val r = FlowKit.asMap(response)
        val action = r["action"]
        if (action == "skip") {
            if (!Js.truthy(nodeData["allowSkip"])) return NodeResult.DisplayUI(ui(nodeData, nodeId, st.stage))
            return finish(nodeData, nodeId, false, "", "skipped")
        }
        if (action == "send" || action == "resend" || (action == null && st.stage == "email")) {
            return send(nodeData, nodeId, if (action == "resend") st.email else Js.str(r["email"] ?: FlowKit.typedText(response)))
        }
        // verify
        val code = Js.str(r["code"] ?: FlowKit.typedText(response)).replace(Regex("\\D"), "")
        if (code.length != FlowRich.OTP_LENGTH) return NodeResult.Invalid(FlowRich.otpErrorText("invalid_code"), ui(nodeData, nodeId, "code"), inCard = true)
        val res = richPost(flowContext(), "otp/verify", mapOf("nodeId" to nodeId, "sessionId" to sessionOf(), "code" to code))
        val body: Map<String, Any?> = if (res.ok) res.data ?: emptyMap() else mapOf("verified" to false, "code" to res.code)
        if (body["verified"] == true) return finish(nodeData, nodeId, true, Js.str(body["email"].takeIf { Js.truthy(it) } ?: st.email).lowercase())
        if (body["code"] == "locked") return finish(nodeData, nodeId, false, "", "locked")
        return NodeResult.Invalid(FlowRich.otpErrorText(body["code"], attemptsLeft = body["attemptsLeft"]), ui(nodeData, nodeId, "code"), inCard = true)
    }
}

/**
 * address-node: search (POST geo/search, results to pick and confirm) or
 * typed by hand (street required). Answers: {action: "search", q},
 * {action: "pick", index} (confirms), {action: "manual", line1, city, region,
 * postal, country}, or typed text (the street line). Saves the formatted
 * address and <var>_line1 / _city / _region / _postal / _country /
 * _country_code / _lat / _lng.
 */
class AddressNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.ADDRESS

    private val results = mutableMapOf<String, List<FlowRich.AddressValue>>()

    /** The card: the search results to pick from, typing by hand, the field's placeholder. */
    private fun ui(data: Map<String, Any?>, nodeId: String, found: List<FlowRich.AddressValue> = emptyList()): NodeUIState.TextInput {
        val list = if (found.isEmpty()) "" else "\n" + found.mapIndexed { i, a -> "${i + 1}. ${a.formatted}" }.joinToString("\n")
        val extra = mapOf(
            "results" to found.map { it.formatted },
            "allowManual" to (data["allowManual"] != false),
            "placeholder" to Js.str(data["placeholder"]).trim()
        )
        return textInput(nodeId, prompt(data) + list, data, NodeUIState.TextInput.InputType.LOCATION, "Street address", extra)
    }

    private suspend fun finish(data: Map<String, Any?>, nodeId: String, value: FlowRich.AddressValue, source: String): NodeResult {
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-address", "value" to value.toMap(), "display" to value.formatted, "source" to source, "at" to FlowKit.nowIso()))
        FlowKit.userSay(value.formatted)
        val k = Js.str(data["answerVariable"]).trim()
        if (k.isNotEmpty()) saveVars(nodeId, data, FlowRich.addressVariables(k, value))
        results.remove(nodeId)
        return NodeResult.Proceed("source")
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText)
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val r = FlowKit.asMap(response)
        when (r["action"]) {
            "search" -> {
                val q = Js.str(r["q"]).trim()
                if (q.length < 3) return NodeResult.DisplayUI(ui(nodeData, nodeId))
                val res = richPost(flowContext(), "geo/search", mapOf("nodeId" to nodeId, "q" to q))
                val found = if (res.ok) Js.list(res.data?.get("results")).map { FlowRich.normalizeAddress(Js.map(it)) }.filter { it.formatted.isNotEmpty() }.take(5) else emptyList()
                results[nodeId] = found
                if (!res.ok) return NodeResult.Invalid(FlowRich.addressErrorText(res.code), ui(nodeData, nodeId), inCard = true)
                if (found.isEmpty()) return NodeResult.Invalid(FlowRich.addressErrorText("no_results"), ui(nodeData, nodeId), inCard = true)
                return NodeResult.DisplayUI(ui(nodeData, nodeId, found))
            }
            "pick" -> {
                val list = results[nodeId] ?: emptyList()
                val picked = list.getOrNull(Js.number(r["index"]).toInt()) ?: return NodeResult.DisplayUI(ui(nodeData, nodeId, list))
                return finish(nodeData, nodeId, picked, "search")
            }
        }
        // A number typed against a list of results picks it.
        val typed = FlowKit.typedText(response)
        results[nodeId]?.let { list -> typed.toIntOrNull()?.let { n -> list.getOrNull(n - 1)?.let { return finish(nodeData, nodeId, it, "search") } } }
        val fields = if (r["action"] == "manual") r else mapOf("line1" to (if (response is String) response else r["address"] ?: typed))
        val value = (if (nodeData["allowManual"] == false) null else FlowRich.manualAddress(fields))
            ?: return NodeResult.Invalid("Enter the street address.", ui(nodeData, nodeId), inCard = true)
        return finish(nodeData, nodeId, value, "manual")
    }
}

/**
 * matrix-node: rows x columns, one column per row (single) or several
 * (multiple). requireAll: every row; else at least one. Saves {row: column}
 * (or {row: [columns]}). No rows, or fewer than two columns: straight on by
 * "source". Answers: {cells: {rowId: colId | [colIds]}}, the form's
 * {formData: {...}}, or {rowId or row text: column id or text}.
 */
class MatrixNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.MATRIX

    private fun ui(data: Map<String, Any?>, nodeId: String): NodeUIState.MultipleQuestions {
        val cols = FlowRich.matrixColumns(data).joinToString(" / ") { it.text }
        return NodeUIState.MultipleQuestions(
            questions = FlowRich.matrixRows(data).map { NodeUIState.MultipleQuestions.Question("${it.text} ($cols)", "select", it.id) },
            currentIndex = 0,
            nodeId = nodeId
        )
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        if (FlowRich.matrixRows(nodeData).isEmpty() || FlowRich.matrixColumns(nodeData).size < 2) return NodeResult.Proceed("source")
        FlowKit.botSay(prompt(nodeData), side = true)
        return NodeResult.DisplayUI(ui(nodeData, nodeId))
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val rows = FlowRich.matrixRows(nodeData)
        val cols = FlowRich.matrixColumns(nodeData)
        val mode = FlowRich.matrixMode(nodeData)
        val r = FlowKit.asMap(response)
        val cells: Map<String, Any?> = Js.map(r["cells"]) ?: Js.map(r["formData"]) ?: r
        var sel: Map<String, List<String>> = emptyMap()
        for ((rowKey, picked) in cells) {
            val row = rows.firstOrNull { it.id == rowKey } ?: rows.firstOrNull { it.text == rowKey } ?: continue
            val list = if (picked is List<*>) picked else listOf(picked)
            for (c in list) {
                val col = cols.firstOrNull { it.id == Js.str(c) } ?: cols.firstOrNull { it.text.equals(Js.str(c).trim(), ignoreCase = true) } ?: continue
                sel = FlowRich.matrixToggle(sel, row.id, col.id, mode)
            }
        }
        FlowRich.matrixError(sel, rows, FlowRich.matrixRequireAll(nodeData)).takeIf { it.isNotEmpty() }?.let {
            return NodeResult.Invalid(it, ui(nodeData, nodeId), inCard = true)
        }
        val value = FlowRich.matrixValue(sel, rows, cols, mode)
        val display = FlowRich.matrixDisplay(value)
        answer(
            nodeId, nodeData,
            mapOf("shape" to "user-matrix", "value" to value, "rows" to FlowRich.matrixRowAnswers(sel, rows, cols, mode), "mode" to mode, "display" to display, "at" to FlowKit.nowIso()),
            display, value
        )
        return NodeResult.Proceed("source")
    }
}

/** The text an AI or KB step reads without asking (source last_message / variable), else null. */
private fun presetText(data: Map<String, Any?>): String? {
    val transcript = ChatState.transcript.value.map { it.by to it.message }
    val variable = if (FlowRich.aiSourceOf(data) == "variable") lookup(Js.str(data["sourceVariable"]).trim()) else null
    return FlowRich.aiPresetText(data, FlowRich.lastUserMessage(transcript), variable)
}

/** Thinking dots for at least AI_MIN_THINK_MS around the server call. */
private suspend fun <T> think(ctx: FlowContext, work: suspend () -> T): T {
    val started = ctx.now()
    val result = work()
    val wait = FlowRich.AI_MIN_THINK_MS - (ctx.now() - started)
    if (wait > 0) ctx.typing(wait)
    return result
}

/**
 * ai-extract-node: the visitor's reply (or the last message / a variable)
 * goes to POST ai/extract {nodeId, text}; every field is saved by name,
 * <var> = all values, <var>_missing = what is missing. ok -> "source";
 * missing or failed -> "source-missing" (or "source" when only that is
 * wired). Answer: the typed reply.
 */
class AiExtractNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.AI_EXTRACT

    private fun ui(data: Map<String, Any?>, nodeId: String) = textInput(nodeId, prompt(data, "<p>How can we help?</p>"), data)

    private suspend fun run(data: Map<String, Any?>, nodeId: String, text: String, asked: Boolean): NodeResult {
        val ctx = flowContext()
        val input = text.trim().take(FlowRich.AI_MAX_INPUT)
        val r = think(ctx) { richPost(ctx, "ai/extract", mapOf("nodeId" to nodeId, "text" to input), FlowRich.AI_CLIENT_TIMEOUT_MS) }
        val ex = FlowRich.aiExtractResultOf(data, if (r.ok) r.data else null, if (r.ok) null else r.code)
        val entry = linkedMapOf<String, Any?>("id" to nodeId, "shape" to "user-ai-extract", "asked" to asked, "input" to input, "values" to ex.values, "missing" to ex.missing, "ok" to ex.ok)
        if (ex.error != null) entry["error"] = ex.error
        entry["at"] = FlowKit.nowIso()
        FlowKit.pushAnswer(entry)
        saveVars(nodeId, data, FlowRich.aiExtractVariables(data, ex))
        if (asked) FlowKit.userSay(input)
        return if (ex.ok) NodeResult.Proceed("source") else NodeResult.Proceed("source-missing", "source")
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        presetText(nodeData)?.takeIf { it.isNotEmpty() }?.let { return run(nodeData, nodeId, it, false) }
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText)
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val text = FlowKit.typedText(response)
        if (text.isEmpty()) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        return run(nodeData, nodeId, text, true)
    }
}

/**
 * ai-route-node: POST ai/route {nodeId, text} -> an intent. Leaves by
 * "source-" + the intent id when that edge is drawn; no match, a failure or
 * an intent with no edge -> "source-fallback" (no further fallback: no edge
 * there ends the chat). Saves <var> (the intent name, "" on no match) and
 * <var>_confidence.
 */
class AiRouteNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.AI_ROUTE

    private fun ui(data: Map<String, Any?>, nodeId: String) = textInput(nodeId, prompt(data, "<p>How can we help?</p>"), data)

    private suspend fun run(data: Map<String, Any?>, nodeId: String, text: String, asked: Boolean): NodeResult {
        val ctx = flowContext()
        val input = text.trim().take(FlowRich.AI_MAX_INPUT)
        val r = think(ctx) { richPost(ctx, "ai/route", mapOf("nodeId" to nodeId, "text" to input), FlowRich.AI_CLIENT_TIMEOUT_MS) }
        val rt = FlowRich.aiRouteResultOf(if (r.ok) r.data else null, if (r.ok) null else r.code)
        val entry = linkedMapOf<String, Any?>(
            "id" to nodeId, "shape" to "user-ai-route", "asked" to asked, "input" to input, "intentId" to rt.intentId,
            "intent" to rt.intent, "confidence" to Js.tidy(rt.confidence), "fallback" to rt.fallback
        )
        if (rt.error != null) entry["error"] = rt.error
        entry["at"] = FlowKit.nowIso()
        FlowKit.pushAnswer(entry)
        saveVars(nodeId, data, FlowRich.aiRouteVariables(data, rt))
        if (asked) FlowKit.userSay(input)
        if (!rt.fallback && rt.intentId != null && ctx.wired("source-${rt.intentId}")) return NodeResult.Proceed("source-${rt.intentId}")
        return NodeResult.Proceed("source-fallback")
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        presetText(nodeData)?.takeIf { it.isNotEmpty() }?.let { return run(nodeData, nodeId, it, false) }
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText)
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val text = FlowKit.typedText(response)
        if (text.isEmpty()) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        return run(nodeData, nodeId, text, true)
    }
}

/**
 * kb-answer-node: POST kb/answer {nodeId, question}; the answer is shown with
 * its sources and saved (<var>, <var>_found, <var>_sources). Found ->
 * "source"; not found or failed -> notFoundMessage and "source-notfound" (or
 * "source" when only that is wired).
 */
class KbAnswerNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.KB_ANSWER

    private fun ui(data: Map<String, Any?>, nodeId: String) = textInput(nodeId, prompt(data, "<p>${t("kb.askPrompt")}</p>"), data)

    private suspend fun run(data: Map<String, Any?>, nodeId: String, text: String, asked: Boolean): NodeResult {
        val ctx = flowContext()
        val input = text.trim().take(1000)
        val res = think(ctx) { richPost(ctx, "kb/answer", mapOf("nodeId" to nodeId, "question" to input), FlowRich.KB_CLIENT_TIMEOUT_MS) }
        val r = FlowRich.kbResultOf(data, if (res.ok) res.data else null, if (res.ok) null else res.code)
        val shown = if (r.found) r.answer else FlowRich.plainText(FlowRich.kbNotFoundHtml(data))
        val sources = if (r.found && r.sources.isNotEmpty()) "\n\n" + r.sources.joinToString("\n") { "• ${it.title}${if (it.url.isNotEmpty()) " (${it.url})" else ""}" } else ""
        FlowKit.botSay("$shown$sources", side = true)
        val entry = linkedMapOf<String, Any?>("id" to nodeId, "shape" to "kb-answer", "asked" to asked, "input" to input, "found" to r.found)
        if (r.found) entry["answer"] = r.answer
        entry["sources"] = r.sources.map { it.toMap() }
        if (r.error != null) entry["error"] = r.error
        entry["at"] = FlowKit.nowIso()
        FlowKit.pushAnswer(entry)
        if (asked) FlowKit.userSay(input)
        saveVars(nodeId, data, FlowRich.kbVariables(data, r))
        return if (r.found) NodeResult.Proceed("source") else NodeResult.Proceed("source-notfound", "source")
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        presetText(nodeData)?.takeIf { it.isNotEmpty() }?.let { return run(nodeData, nodeId, it, false) }
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText)
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val text = FlowKit.typedText(response)
        if (text.isEmpty()) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        return run(nodeData, nodeId, text, true)
    }
}

/** A typed name drawn as a signature image (SVG), sized to the name. */
internal fun typedSignatureSvg(name: String): Triple<String, Int, Int> {
    val clean = FlowRich.cleanSignatureName(name)
    val width = Math.max(96, Math.round(clean.length * 16 + 64.0).toInt())
    val height = 114
    val esc = clean.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\"><text x=\"50%\" y=\"62%\" text-anchor=\"middle\" " +
        "font-family=\"Segoe Script, Bradley Hand, Snell Roundhand, cursive\" font-style=\"italic\" font-size=\"40\" fill=\"#111827\">$esc</text></svg>"
    return Triple("data:image/svg+xml;charset=utf-8,${Js.encodeURIComponent(svg)}", width, height)
}

/**
 * signature-node: a typed name (allowTyped) or a drawing the UI exports as an
 * image, uploaded to POST media; saves the URL and <var>_at / _method / _name.
 * Answers: {action: "sign", method: "typed", name, fullName?}, {action:
 * "sign", method: "drawn", dataUrl, width, height} (no dataUrl: nothing
 * drawn), or a typed name.
 */
class SignatureNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.SIGNATURE

    private fun ui(data: Map<String, Any?>, nodeId: String) = textInput(nodeId, prompt(data).ifEmpty { t("signature.typeToSign") }, data, placeholder = "Your full name")

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText)
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val cfg = FlowRich.signatureConfig(nodeData)
        val r = FlowKit.asMap(response)
        val typed = response is String || r["method"] == "typed"
        val method = if (typed) "typed" else "drawn"
        val name = if (typed) FlowRich.cleanSignatureName(if (response is String) response else r["name"]) else ""
        val fullName = FlowRich.cleanSignatureName(r["fullName"] ?: if (cfg.requireName) name else "")
        if (cfg.requireName && !FlowRich.isSignatureName(fullName)) return NodeResult.Invalid("Enter your full name.", ui(nodeData, nodeId), inCard = true)
        if (method == "drawn" && !Js.truthy(r["dataUrl"])) {
            return NodeResult.Invalid(
                if (cfg.allowTyped) "Draw your signature in the box - or type your name instead." else "Draw your signature in the box.",
                ui(nodeData, nodeId), inCard = true
            )
        }
        if (method == "typed" && (!cfg.allowTyped || !FlowRich.isSignatureName(name))) return NodeResult.Invalid("Type your full name to sign.", ui(nodeData, nodeId), inCard = true)
        val (dataUrl, width, height) = if (method == "typed") typedSignatureSvg(name)
        else Triple(Js.str(r["dataUrl"]), Js.number(r["width"]).let { if (Js.isFinite(it)) it.toInt() else 0 }, Js.number(r["height"]).let { if (Js.isFinite(it)) it.toInt() else 0 })
        val ctx = flowContext()
        val up = ctx.upload(UploadFile(dataUrl = dataUrl, name = "signature-${ctx.now()}.${if (method == "typed") "svg" else "png"}", type = if (method == "typed") "image/svg+xml" else "image/png"))
        if (!up.ok || up.url.isNullOrEmpty()) return NodeResult.Invalid("We couldn't save your signature. Please try again.", ui(nodeData, nodeId), inCard = true)
        val at = FlowKit.nowIso()
        val shownName = fullName.ifEmpty { name }
        val entry = linkedMapOf<String, Any?>("id" to nodeId, "shape" to "user-signature", "url" to up.url, "at" to at, "method" to method)
        if (shownName.isNotEmpty()) entry["name"] = shownName
        entry["width"] = width
        entry["height"] = height
        FlowKit.pushAnswer(entry)
        FlowKit.userSay("Signed: ${up.url}")
        val k = Js.str(nodeData["answerVariable"]).trim()
        if (k.isNotEmpty()) {
            ChatState.fillAnswerVariable(nodeId, up.url)
            FlowRich.signatureVariables(k, up.url, at, shownName, method).forEach { (vk, vv) -> if (vk != k) ChatState.saveVariableByName(vk, vv) }
        }
        return NodeResult.Proceed("source")
    }
}

/**
 * payment-node: POST pay/start makes a Stripe Checkout on the server. The
 * visitor opens it ("Pay now"); while they are away the status is peeked
 * every 5 s (POST pay/status) and the socket's "payment-status" counts too.
 * Paid -> "source"; failed or cancelled -> "source-failed" (never the paid
 * path); timed out -> "source-timeout", else "source-failed". Answers:
 * {action: "open"} (the button carries the Checkout URL to open), {action:
 * "check"}, {action: "cancel"} - or the card's "open" / "check" / "cancel".
 */
class PaymentNodeHandler : RichNodeHandler() {
    override val nodeType = NodeTypes.PAYMENT

    private class Session(
        var paymentId: String = "",
        var amount: Double? = null,
        var currency: String = "usd",
        var amountText: String = "",
        var deadline: Long = 0,
        var waiting: Boolean = false,
        var done: Boolean = false,
        var url: String = "",
        val timers: MutableList<() -> Unit> = mutableListOf(),
        var off: () -> Unit = {}
    )

    private val sessions = mutableMapOf<String, Session>()

    private fun ui(data: Map<String, Any?>, nodeId: String, s: Session?): NodeUIState.SingleChoice {
        val label = FlowRich.plainText(data["buttonLabel"]).ifEmpty { t("pay.payNow") }
        val choices = mutableListOf(NodeUIState.SingleChoice.Choice(id = "open", text = if (s?.amountText?.isNotEmpty() == true) "$label · ${s.amountText}" else label, targetPort = s?.url))
        if (s?.waiting == true) choices.add(NodeUIState.SingleChoice.Choice(id = "check", text = t("pay.ivePaid")))
        choices.add(NodeUIState.SingleChoice.Choice(id = "cancel", text = t("common.cancel")))
        // The card: what is paid for, how much, the Checkout and whether it is open.
        val extra = mapOf(
            "checkoutUrl" to (s?.url ?: ""),
            "waiting" to (s?.waiting == true),
            "amountText" to (s?.amountText ?: ""),
            "payLabel" to label,
            "productName" to FlowRich.plainText(data["productName"]),
            "description" to FlowRich.plainText(data["description"])
        )
        return NodeUIState.SingleChoice(prompt(data), choices, nodeId, Js.str(data["answerVariable"]), step = NodeUIState.StepInfo(extra = extra))
    }

    private fun stop(s: Session) {
        s.timers.forEach { it() }
        s.timers.clear()
        s.off()
    }

    private suspend fun finish(data: Map<String, Any?>, nodeId: String, s: Session, outcome: String, r: Map<String, Any?> = emptyMap()): NodeResult {
        s.done = true
        stop(s)
        sessions.remove(nodeId)
        val status = Js.str(r["status"]).ifEmpty { if (outcome == "paid") "paid" else if (outcome == "timeout") "timeout" else "cancelled" }
        val receiptUrl = if (FlowRich.isReceiptUrl(r["receiptUrl"])) Js.str(r["receiptUrl"]) else ""
        val amt = r["amount"]
        if (amt != null && Js.isFinite(Js.number(amt))) s.amount = Js.number(amt)
        if (Js.truthy(r["currency"])) s.currency = Js.str(r["currency"]).lowercase()
        val shownAmount = s.amount?.let { FlowRich.paymentAmountText(it, s.currency) } ?: s.amountText
        val text = FlowRich.paymentReplyText(outcome, shownAmount)
        val entry = linkedMapOf<String, Any?>(
            "id" to nodeId, "shape" to "user-payment", "outcome" to outcome, "status" to status,
            "amount" to s.amount?.let { Js.tidy(it) }, "currency" to s.currency, "amountText" to shownAmount
        )
        if (receiptUrl.isNotEmpty()) entry["receiptUrl"] = receiptUrl
        if (s.paymentId.isNotEmpty()) entry["paymentId"] = s.paymentId
        entry["at"] = FlowKit.nowIso()
        FlowKit.pushAnswer(entry)
        FlowKit.userSay(text)
        saveVars(nodeId, data, FlowRich.paymentVariables(Js.str(data["answerVariable"]).trim(), status, s.amount, s.currency, receiptUrl, s.paymentId))
        if (outcome == "timeout") return NodeResult.Proceed(FlowRich.PAYMENT_TIMEOUT_HANDLE, FlowRich.PAYMENT_FAILED_HANDLE)
        return NodeResult.Proceed(FlowRich.paymentHandleOf(outcome))
    }

    private suspend fun ask(ctx: FlowContext, nodeId: String, s: Session, action: String) =
        richPost(ctx, "pay/status", mapOf("nodeId" to nodeId, "sessionId" to sessionOf(), "paymentId" to s.paymentId, "action" to action))

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ctx = flowContext()
        val prompt = prompt(nodeData)
        if (prompt.isNotEmpty()) FlowKit.botSay(prompt)
        val s = Session(currency = Js.str(nodeData["currency"]).ifEmpty { "usd" }.lowercase(), deadline = ctx.now() + FlowRich.paymentTimeoutMs(nodeData))
        sessions[nodeId]?.let { stop(it) }
        sessions[nodeId] = s
        val v = Js.str(nodeData["amountVariable"]).trim()
        val body = linkedMapOf<String, Any?>("nodeId" to nodeId, "sessionId" to sessionOf(), "step" to ctx.stepIndex.toString())
        if (nodeData["amountMode"] == "variable" && v.isNotEmpty()) body["variables"] = mapOf(v to lookup(v))
        val res = richPost(ctx, "pay/start", body)
        val d = res.data
        if (res.ok && d != null && Js.truthy(d["paymentId"])) {
            FlowRich.paymentOutcomeOf(d["status"])?.let { return finish(nodeData, nodeId, s, it, d) }
            s.paymentId = Js.str(d["paymentId"])
            if (d["amount"] != null && Js.isFinite(Js.number(d["amount"]))) s.amount = Js.number(d["amount"])
            if (Js.truthy(d["currency"])) s.currency = Js.str(d["currency"]).lowercase()
            s.amountText = s.amount?.let { FlowRich.paymentAmountText(it, s.currency) } ?: ""
            com.conferbot.sdk.core.flow.FlowCalendar.parseIso(d["expiresAt"])?.let { s.deadline = it }
            s.url = if (FlowRich.isCheckoutUrl(d["url"])) Js.str(d["url"]) else ""
            // The server's word, from the socket.
            s.off = ctx.on("payment-status") { st ->
                if (!s.done && st != null && Js.str(st["paymentId"]) == s.paymentId && Js.str(st["nodeId"] ?: nodeId) == nodeId && FlowRich.paymentOutcomeOf(st["status"]) != null) {
                    ctx.respond(mapOf("action" to "__status", "status" to st))
                }
            }
            // The countdown: at the deadline, ask the server once more, then time out.
            var asked = false
            s.timers.add(ctx.setInterval(1000) {
                if (s.done || asked || ctx.now() < s.deadline) return@setInterval
                asked = true
                ctx.background {
                    val r = ask(ctx, nodeId, s, "timeout")
                    if (s.done) return@background
                    if (r.ok && FlowRich.paymentOutcomeOf(r.data?.get("status")) != null) ctx.respond(mapOf("action" to "__status", "status" to r.data))
                    else if (r.ok) s.timers.add(ctx.setTimeout(6000) {
                        if (s.done) return@setTimeout
                        ctx.background {
                            val r2 = ask(ctx, nodeId, s, "timeout")
                            if (s.done) return@background
                            ctx.respond(mapOf("action" to "__status", "status" to (if (r2.ok && FlowRich.paymentOutcomeOf(r2.data?.get("status")) != null) r2.data else mapOf("status" to "timeout"))))
                        }
                    })
                    else ctx.respond(mapOf("action" to "__status", "status" to mapOf("status" to "timeout")))
                }
            })
            return NodeResult.DisplayUI(ui(nodeData, nodeId, s))
        }
        val code = res.code
        if (code == "not_configured" || code == "invalid_amount" || code == "node_not_found") {
            FlowKit.botSay(FlowRich.paymentErrorText(code), side = true)
            ctx.delay(1600)
            return finish(nodeData, nodeId, s, "failed", mapOf("status" to "failed"))
        }
        return NodeResult.Invalid(FlowRich.paymentErrorText(code), ui(nodeData, nodeId, s), inCard = true)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ctx = flowContext()
        val s = sessions[nodeId]
        if (s == null || s.done) return process(nodeData, nodeId)
        val r = FlowKit.asMap(response)
        when (r["action"] ?: FlowKit.pickedId(response)) {
            "__status" -> {
                val st = Js.map(r["status"]) ?: emptyMap()
                return finish(nodeData, nodeId, s, FlowRich.paymentOutcomeOf(st["status"]) ?: "timeout", st)
            }
            "open" -> {
                if (!s.waiting) {
                    s.waiting = true
                    s.timers.add(ctx.setInterval(FlowRich.PAYMENT_PEEK_MS) {
                        if (s.done) return@setInterval
                        ctx.background {
                            val p = ask(ctx, nodeId, s, "peek")
                            if (!s.done && p.ok && FlowRich.paymentOutcomeOf(p.data?.get("status")) != null) ctx.respond(mapOf("action" to "__status", "status" to p.data))
                        }
                    })
                }
                return NodeResult.DisplayUI(ui(nodeData, nodeId, s))
            }
            "check" -> {
                val p = ask(ctx, nodeId, s, "check")
                FlowRich.paymentOutcomeOf(p.data?.get("status"))?.let { if (p.ok) return finish(nodeData, nodeId, s, it, p.data ?: emptyMap()) }
                return NodeResult.Invalid(if (p.ok) t("pay.notYet") else FlowRich.paymentErrorText(p.code), ui(nodeData, nodeId, s), inCard = true)
            }
            "cancel" -> {
                val p = ask(ctx, nodeId, s, "cancel")
                return finish(nodeData, nodeId, s, "failed", mapOf("status" to "cancelled") + (if (p.ok) p.data ?: emptyMap() else emptyMap()))
            }
        }
        return NodeResult.DisplayUI(ui(nodeData, nodeId, s))
    }
}
