package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.i18n.I18n
import com.conferbot.sdk.core.flow.FlowCalendar
import com.conferbot.sdk.core.flow.FlowInputs
import com.conferbot.sdk.core.flow.FlowInputs.EN
import com.conferbot.sdk.core.flow.FlowText
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.flow.UploadFile
import com.conferbot.sdk.core.flow.flowContext
import com.conferbot.sdk.core.nodes.*
import com.conferbot.sdk.core.state.ChatState
import com.conferbot.sdk.core.state.RecordEntry

/**
 * The question steps answered in the composer (ask-name, ask-email,
 * ask-phone-number, ask-number, ask-url, ask-location, ask-custom-question),
 * the file step (ask-file), calendar-node and ask-multiple-questions-node.
 * Field names, checks, records and saved values follow the flow-node spec
 * and the web widget's ts/chat/handlers/askQuestions.ts; ported from RN
 * AskNodeHandlers.ts and AdvancedInputHandlers.ts:
 *
 * - the prompt is data.questionText;
 * - the answer fills the variable the step registered when it was shown
 *   (data.answerVariable) - a step without one stores nothing;
 * - phone numbers are saved normalised, numbers as numbers, bare links as https;
 * - a refused answer says why and the step keeps waiting.
 */
abstract class BaseAskHandler : BaseNodeHandler() {
    protected abstract val inputType: NodeUIState.TextInput.InputType
    protected abstract val shape: String
    // The composer's hints in the active language (web i18n composer.*).
    protected open val placeholder: String get() = I18n.t("composer.answer")

    protected fun ui(nodeData: Map<String, Any?>, nodeId: String) = NodeUIState.TextInput(
        questionText = FlowKit.shownText(nodeData["questionText"]),
        inputType = inputType,
        placeholder = placeholder,
        nodeId = nodeId,
        answerKey = Js.str(nodeData["answerVariable"])
    )

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText)
        return NodeResult.DisplayUI(ui)
    }

    /** The refusal message, or null when the answer is fine. */
    protected open fun problem(value: String, nodeData: Map<String, Any?>): String? = null

    /** What is saved (variable) and recorded (text) for the answer. */
    protected open fun saved(value: String): Pair<Any?, String> = value to value

    /** After the answer is saved: by default, on along the first edge. */
    protected open suspend fun after(value: String, nodeData: Map<String, Any?>, nodeId: String): NodeResult = NodeResult.Proceed()

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val value = FlowKit.typedText(response)
        if (value.isEmpty()) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        problem(value, nodeData)?.let { return NodeResult.Invalid(it, ui(nodeData, nodeId)) }
        val (variable, text) = saved(value)
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to shape, "text" to text))
        FlowKit.userSay(text)
        ChatState.fillAnswerVariable(nodeId, variable)
        return after(value, nodeData, nodeId)
    }
}

/**
 * ask-name-node: any text. The full answer is saved; nameGreetResponse
 * greets by the first name after 2000 ms of typing (none set: the default
 * greeting; cleared in the builder: no greeting).
 */
class AskNameNodeHandler : BaseAskHandler() {
    override val nodeType = NodeTypes.ASK_NAME
    override val inputType = NodeUIState.TextInput.InputType.NAME
    override val shape = "user-ask-name-response"
    override val placeholder: String get() = I18n.t("composer.name")

    override suspend fun after(value: String, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        ChatState.setUserMetadata("name", value)
        val greeting = FlowText.nameGreetingHtml(if (nodeData.containsKey("nameGreetResponse")) nodeData["nameGreetResponse"] else null, value)
        if (greeting.isEmpty()) return NodeResult.Proceed()
        flowContext().typing(2000)
        ChatState.addRecord(
            RecordEntry(
                id = nodeId, shape = "ask-name-response-greet", type = "ask-name-response-greet", text = greeting, time = FlowKit.nowIso(),
                fields = mapOf("renderedText" to FlowText.fillVariables(greeting, { ChatState.lookupVariable(it) }))
            )
        )
        FlowKit.botSay(FlowKit.shownText(greeting), side = true)
        return NodeResult.Proceed()
    }
}

/**
 * ask-email-node: the widget's e-mail check (incorrectEmailResponse, else
 * the default). A likely domain typo is refused once with the fix offered;
 * the same address sent again is kept as typed.
 */
class AskEmailNodeHandler : BaseAskHandler() {
    override val nodeType = NodeTypes.ASK_EMAIL
    override val inputType = NodeUIState.TextInput.InputType.EMAIL
    override val shape = "user-ask-email-response"
    override val placeholder: String get() = I18n.t("composer.email")

    override fun problem(value: String, nodeData: Map<String, Any?>): String? {
        if (!FlowInputs.isValidEmail(value)) {
            return FlowInputs.ownerTextOr(nodeData["incorrectEmailResponse"], EN.emailInvalid, listOf("Please enter a valid email"))
        }
        val fix = FlowInputs.suggestEmailFix(value)
        val typed = value.trim().lowercase()
        if (fix != null && ChatState.getVariable("_lastEmailTypo") != typed) {
            ChatState.setVariable("_lastEmailTypo", typed)
            return EN.emailTypo(fix)
        }
        ChatState.setVariable("_lastEmailTypo", "")
        return null
    }

    override suspend fun after(value: String, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        ChatState.setUserMetadata("email", value)
        return NodeResult.Proceed()
    }
}

/** ask-phone-number-node: 6-15 digits once normalised; saved normalised. */
class AskPhoneNodeHandler : BaseAskHandler() {
    override val nodeType = NodeTypes.ASK_PHONE
    override val inputType = NodeUIState.TextInput.InputType.PHONE
    override val shape = "user-ask-phone-number-response"
    override val placeholder: String get() = I18n.t("composer.phone")

    override fun problem(value: String, nodeData: Map<String, Any?>): String? =
        if (FlowInputs.isValidPhone(value)) null else FlowInputs.phoneErrorText(nodeData)

    override fun saved(value: String): Pair<Any?, String> = FlowInputs.normalizePhone(value).let { it to it }

    override suspend fun after(value: String, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        ChatState.setUserMetadata("phone", FlowInputs.normalizePhone(value))
        return NodeResult.Proceed()
    }
}

/**
 * ask-number-node: "1,200" is 1200, "3,5" is 3.5; enableConditions bounds
 * (minVal / maxVal); invalidNumberResponse ({min}/{max} filled). Saved as a NUMBER.
 */
class AskNumberNodeHandler : BaseAskHandler() {
    override val nodeType = NodeTypes.ASK_NUMBER
    override val inputType = NodeUIState.TextInput.InputType.NUMBER
    override val shape = "user-ask-number-response"
    override val placeholder: String get() = I18n.t("composer.number")

    override fun problem(value: String, nodeData: Map<String, Any?>): String? = FlowInputs.numberProblem(value, nodeData)

    override fun saved(value: String): Pair<Any?, String> {
        val n = FlowInputs.parseNumberInput(value) ?: return value to value
        return Js.tidy(n) to Js.num(n)
    }
}

/** ask-url-node: a link people would type; a bare host is saved with https://. */
class AskUrlNodeHandler : BaseAskHandler() {
    override val nodeType = NodeTypes.ASK_URL
    override val inputType = NodeUIState.TextInput.InputType.URL
    override val shape = "user-ask-url-response"

    override fun problem(value: String, nodeData: Map<String, Any?>): String? = if (FlowInputs.isValidLink(value)) null else EN.link

    override fun saved(value: String): Pair<Any?, String> = FlowInputs.linkHref(value).ifEmpty { value.trim() }.let { it to it }
}

/** ask-location-node: any text. */
class AskLocationNodeHandler : BaseAskHandler() {
    override val nodeType = NodeTypes.ASK_LOCATION
    override val inputType = NodeUIState.TextInput.InputType.LOCATION
    override val shape = "user-ask-location-response"
    override val placeholder: String get() = I18n.t("composer.location")
}

/** ask-custom-question-node: any text. */
class AskCustomNodeHandler : BaseAskHandler() {
    override val nodeType = NodeTypes.ASK_CUSTOM
    override val inputType = NodeUIState.TextInput.InputType.TEXT
    override val shape = "user-ask-custom-question-response"
}

/** A picked file from an answer: the SDK card's {action: "upload", files: [...]} or {file: {...}}. */
internal fun pickedFileOf(response: Map<String, Any?>): UploadFile? {
    val f = Js.map(response["file"]) ?: Js.map(Js.list(response["files"]).firstOrNull()) ?: return null
    return UploadFile(
        uri = f["uri"]?.let { Js.str(it) },
        name = f["name"]?.let { Js.str(it) },
        type = (f["type"] ?: f["mimeType"])?.let { Js.str(it) },
        size = (f["size"] as? Number)?.toDouble()
    )
}

/**
 * ask-file-node: an upload card. A file over maxFileSizeMB (default 5) or not
 * in allowedFileTypes is refused on the card; the picked file is uploaded to
 * POST /api/v1/bot/:id/media and its URL saved. A failed upload keeps the step
 * waiting. Optional ("(optional)" in the question, or data.optional) offers
 * Skip: recorded as skipped, no variable.
 *
 * Answers: {file: {uri, name, type, size}} or the card's {action: "upload",
 * files: [...]} (uploaded here), {url, fileName} (uploaded already), or
 * {skip: true} / {action: "skip"}.
 */
class AskFileNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ASK_FILE

    private fun ui(nodeData: Map<String, Any?>, nodeId: String): NodeUIState.FileUpload {
        val types = Js.list(nodeData["allowedFileTypes"]).map { Js.str(it) }.filter { it.isNotEmpty() }
        val mb = Js.number(nodeData["maxFileSizeMB"])
        return NodeUIState.FileUpload(
            questionText = FlowKit.shownText(nodeData["questionText"]),
            maxSizeMb = if (mb > 0) Math.ceil(mb).toInt() else FlowInputs.DEFAULT_MAX_FILE_MB.toInt(),
            allowedTypes = types.ifEmpty { null },
            useSignedUrl = false,
            nodeId = nodeId,
            answerKey = Js.str(nodeData["answerVariable"]),
            chatSessionId = ChatState.chatSessionId
        )
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText)
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val r = FlowKit.asMap(response)
        if (r["skip"] == true || r["action"] == "skip") {
            if (!FlowInputs.isOptionalUpload(nodeData)) return NodeResult.DisplayUI(ui(nodeData, nodeId))
            FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-ask-file-response", "skipped" to true))
            FlowKit.userSay(EN.skipped)
            return NodeResult.Proceed()
        }
        if (r["action"] == "cancel") return NodeResult.DisplayUI(ui(nodeData, nodeId))

        var url: String? = (r["url"] as? String)?.takeIf { it.isNotBlank() }
            ?: Js.map(Js.list(r["files"]).firstOrNull())?.get("url")?.let { Js.str(it) }?.takeIf { it.isNotBlank() }
        var fileName = Js.str(r["fileName"] ?: Js.map(r["file"])?.get("name") ?: Js.map(Js.list(r["files"]).firstOrNull())?.get("name") ?: "file")

        if (url == null) {
            val file = pickedFileOf(r) ?: return NodeResult.DisplayUI(ui(nodeData, nodeId))
            FlowInputs.fileUploadProblem(FlowInputs.PickedFile(file.name, file.type, file.size), nodeData)?.let {
                return NodeResult.Invalid(it, ui(nodeData, nodeId), inCard = true)
            }
            val uploaded = flowContext().upload(file)
            if (!uploaded.ok || uploaded.url.isNullOrEmpty()) return NodeResult.Invalid(EN.uploadRetry, ui(nodeData, nodeId), inCard = true)
            url = uploaded.url
            fileName = file.name ?: fileName
        }

        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-ask-file-response", "fileName" to fileName, "url" to url))
        FlowKit.userSay(url)
        ChatState.fillAnswerVariable(nodeId, url)
        return NodeResult.Proceed()
    }
}

/**
 * calendar-node: a month calendar on the bot's clock (data.timeZone, default
 * UTC). Without time selection the picked day is saved as the visitor's long
 * date ("Tuesday, October 20, 2026"); with it, the day's weeklyHours cut into
 * slotDuration slots, and the saved value names both clocks. Moves on 400 ms
 * after the pick. Answer: {date: "YYYY-MM-DD", slot?: "HH:MM"} (slot start on
 * the bot's clock; `time` is read the same).
 */
class CalendarNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.CALENDAR

    private val calendarDays = 90

    private fun zone(data: Map<String, Any?>) = Js.str(data["timeZone"]).ifEmpty { "UTC" }

    private fun slotsFor(data: Map<String, Any?>, dayKey: String, nowMs: Long): List<FlowCalendar.Slot> {
        val zone = zone(data)
        val dayName = FlowCalendar.dayNameInZone(FlowCalendar.noonInZone(dayKey, zone), zone)
        val schedule = Js.list(data["weeklyHours"]).mapNotNull { Js.map(it) }.firstOrNull { it["dayName"] == dayName }
        if (schedule == null || !Js.truthy(schedule["available"])) return emptyList()
        return FlowCalendar.futureSlots(FlowCalendar.expandSlots(schedule["slots"], data["slotDuration"], data["bufferMinutes"]), dayKey, zone, nowMs)
    }

    private fun dayOpen(data: Map<String, Any?>, dayKey: String): Boolean {
        val zone = zone(data)
        val dayName = FlowCalendar.dayNameInZone(FlowCalendar.noonInZone(dayKey, zone), zone)
        val excludedDays = Js.list(data["excludeDays"]).map { Js.str(it).lowercase() }
        val excludedDates = Js.list(data["excludeDates"]).map { Js.str(it) }
        return dayName.lowercase() !in excludedDays && dayKey !in excludedDates
    }

    /** The day's taken slots ("HH:MM - HH:MM", bot's clock): the bot's bookings and the node's own (web displayStep.ts daySlotsFor). */
    private suspend fun booked(data: Map<String, Any?>, nodeId: String, dayKey: String): List<String> =
        FlowCalendar.bookedLabels(flowContext().calendarBookings, nodeId, data, dayKey, zone(data))

    private suspend fun ui(data: Map<String, Any?>, nodeId: String): NodeUIState.Calendar {
        val zone = zone(data)
        val now = flowContext().now()
        val today = FlowCalendar.dayKeyInZone(now, zone)
        val showTime = Js.truthy(data["showTimeSelection"])
        val slots = mutableListOf<NodeUIState.Calendar.TimeSlot>()
        // The days the card offers: the next 90 open ones (with times: those with a free slot).
        val days = mutableListOf<String>()
        for (i in 0 until calendarDays) {
            val key = FlowCalendar.dayKeyInZone(FlowCalendar.noonInZone(today, zone) + i * 86_400_000L, zone)
            if (!dayOpen(data, key)) continue
            if (!showTime) {
                days.add(key)
                continue
            }
            val daySlots = slotsFor(data, key, now)
            val booked = booked(data, nodeId, key)
            // Every slot taken: the day is not offered (web: greyed out as "fully booked").
            if (daySlots.isEmpty() || FlowCalendar.isDayFullyBooked(daySlots, booked)) continue
            // A taken slot is shown, not pickable.
            daySlots.forEach { slots.add(NodeUIState.Calendar.TimeSlot(date = key, time = it.start, available = FlowCalendar.slotLabel(it) !in booked)) }
        }
        return NodeUIState.Calendar(
            questionText = FlowKit.shownText(data["calendarPrompt"]),
            showTimeSelection = showTime,
            timezone = zone,
            availableSlots = if (showTime) slots else null,
            nodeId = nodeId,
            answerKey = Js.str(data["answerVariable"]),
            step = if (showTime) null else NodeUIState.StepInfo(extra = mapOf("days" to days))
        )
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ctx = flowContext()
        val r = FlowKit.asMap(response)
        val botZone = zone(nodeData)
        val visitorZone = ctx.visitorTimeZone
        val dateString = Js.str(r["date"] ?: r["selectedDate"]).take(10)
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dateString)) return NodeResult.DisplayUI(ui(nodeData, nodeId))

        val showTime = Js.truthy(nodeData["showTimeSelection"])
        val slotStart = Js.str(r["slot"] ?: r["time"]).trim()

        val value: String
        val record: Map<String, Any?>
        if (!showTime) {
            val date = FlowCalendar.longDate(FlowCalendar.noonInZone(dateString, botZone), botZone)
            val localDate = FlowCalendar.longDate(FlowCalendar.noonInZone(dateString, visitorZone), visitorZone, FlowCalendar.visitorLocale())
            value = localDate.ifEmpty { date }
            record = linkedMapOf("date" to value, "selectedDate" to dateString, "botTimeZone" to botZone, "visitorTimeZone" to visitorZone, "showTimeSelection" to false)
            FlowKit.userSay(value)
        } else {
            val slot = slotsFor(nodeData, dateString, ctx.now()).firstOrNull { it.start == slotStart }
                // A taken slot cannot be booked again (web: its button is disabled).
                ?.takeIf { FlowCalendar.slotLabel(it) !in booked(nodeData, nodeId, dateString) }
                ?: return NodeResult.DisplayUI(ui(nodeData, nodeId))
            val botDate = FlowCalendar.noonInZone(dateString, botZone)
            val visitorDate = FlowCalendar.noonInZone(FlowCalendar.dayKeyInZone(botDate, botZone), visitorZone)
            val date = FlowCalendar.longDate(botDate, botZone)
            val localDate = FlowCalendar.longDate(visitorDate, visitorZone, FlowCalendar.visitorLocale())
            val visitorStart = FlowCalendar.hhmmInZone(FlowCalendar.wallTimeInZone(dateString, slot.start, botZone), visitorZone)
            val visitorEnd = FlowCalendar.hhmmInZone(FlowCalendar.wallTimeInZone(dateString, slot.end, botZone), visitorZone)
            val timeSlotSelected = FlowCalendar.slotLabel(slot)
            val visitorTime = "$visitorStart - $visitorEnd"
            record = linkedMapOf(
                "date" to localDate.ifEmpty { date },
                "selectedDate" to FlowCalendar.iso(botDate),
                "botTimeZone" to botZone,
                "visitorTimeZone" to visitorZone,
                "showTimeSelection" to true,
                "timeSlotSelected" to timeSlotSelected,
                "visitorTime" to visitorTime
            )
            value = "${localDate.ifEmpty { date }} $visitorTime (Visitor's local time) / $timeSlotSelected (Bot's time)"
            FlowKit.userSay("$localDate, $visitorTime (Your local time)")
            ctx.emit(
                "calendar-slot-selection-record",
                mapOf(
                    "visitorId" to (ChatState.visitorId ?: ChatState.chatSessionId),
                    "chatbotId" to ChatState.botId,
                    "nodeId" to nodeId,
                    "selectedDate" to record["selectedDate"],
                    "botTimeZone" to botZone,
                    "visitorTimeZone" to visitorZone,
                    "timeSlotSelected" to timeSlotSelected,
                    "visitorTime" to visitorTime
                )
            )
        }
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-calendar-selection", "data" to record))
        ChatState.fillAnswerVariable(nodeId, value)
        return NodeResult.DelayedProceed(400)
    }
}

/**
 * ask-multiple-questions-node: data.message, then each of data.questions in
 * turn after 2000 ms of typing. An "email" answer is checked as an e-mail,
 * "phone" / "mobile" as a phone (saved normalised). Each answer fills the
 * variable registered as nodeId + index and is recorded as
 * "<nodeId>:<questionId>". After the last one, the first edge.
 */
class AskMultipleQuestionsNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ASK_MULTIPLE

    private fun questions(data: Map<String, Any?>) = Js.list(data["questions"]).map { Js.map(it) ?: emptyMap() }
    private fun indexKey(nodeId: String) = "_mq_$nodeId"

    private fun ui(data: Map<String, Any?>, nodeId: String, index: Int): NodeUIState.TextInput {
        val q = questions(data).getOrNull(index) ?: emptyMap()
        val variable = Js.str(q["answerVariable"]).lowercase()
        val type = when (variable) {
            "email" -> NodeUIState.TextInput.InputType.EMAIL
            "phone", "mobile" -> NodeUIState.TextInput.InputType.PHONE
            "name" -> NodeUIState.TextInput.InputType.NAME
            else -> NodeUIState.TextInput.InputType.TEXT
        }
        return NodeUIState.TextInput(questionText = FlowKit.shownText(q["questionText"]), inputType = type, nodeId = nodeId, answerKey = Js.str(q["answerVariable"]))
    }

    /** Ask question `index` (2000 ms of typing first). */
    private suspend fun ask(data: Map<String, Any?>, nodeId: String, index: Int): NodeResult {
        val qs = questions(data)
        val q = qs.getOrNull(index) ?: emptyMap()
        flowContext().typing(2000)
        ChatState.setVariable(indexKey(nodeId), index)
        val label = if (qs.size > 1) I18n.t("node.questionOf", "n" to index + 1, "total" to qs.size) + "\n" else ""
        FlowKit.botSay("$label${FlowKit.shownText(q["questionText"])}")
        val qid = Js.str(q["id"] ?: index)
        ChatState.addRecord(
            RecordEntry(
                id = "$nodeId:$qid", shape = "ask-multiple-questions-question", type = "ask-multiple-questions-question",
                time = FlowKit.nowIso(),
                fields = mapOf(
                    "nodeId" to nodeId, "questionId" to (q["id"] ?: index.toString()), "answerVariable" to q["answerVariable"],
                    "questionText" to q["questionText"], "renderedText" to FlowText.fillVariables(q["questionText"], { ChatState.lookupVariable(it) })
                )
            )
        )
        return NodeResult.DisplayUI(ui(data, nodeId, index))
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val message = FlowKit.shownText(nodeData["message"])
        if (message.isNotEmpty()) FlowKit.botSay(message, side = true)
        flowContext().shown()
        if (questions(nodeData).isEmpty()) return NodeResult.Proceed()
        return ask(nodeData, nodeId, 0)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val qs = questions(nodeData)
        val index = (ChatState.getVariable(indexKey(nodeId)) as? Number)?.toInt() ?: 0
        val q = qs.getOrNull(index) ?: emptyMap()
        var value = FlowKit.typedText(response)
        if (value.isEmpty()) return NodeResult.DisplayUI(ui(nodeData, nodeId, index))
        val variable = Js.str(q["answerVariable"])
        if (variable == "email" && !FlowInputs.isValidEmail(value)) {
            return NodeResult.Invalid(FlowInputs.ownerTextOr(nodeData["incorrectEmailResponse"], EN.emailInvalid, listOf("Please enter a valid email")), ui(nodeData, nodeId, index))
        }
        if (variable == "mobile" || variable == "phone") {
            if (!FlowInputs.isValidPhone(value)) return NodeResult.Invalid(FlowInputs.phoneErrorText(nodeData), ui(nodeData, nodeId, index))
            value = FlowInputs.normalizePhone(value)
        }
        if (variable == "name") ChatState.setUserMetadata("name", value)
        if (variable == "email") ChatState.setUserMetadata("email", value)
        if (variable == "phone" || variable == "mobile") ChatState.setUserMetadata("phone", value)

        val qid = Js.str(q["id"] ?: index)
        FlowKit.pushAnswer(
            mapOf(
                "id" to "$nodeId:$qid", "nodeId" to nodeId, "questionId" to (q["id"] ?: index.toString()), "answerVariable" to q["answerVariable"],
                "shape" to "user-ask-multiple-questions-question-response", "type" to "user-ask-multiple-questions-question-response", "text" to value
            )
        )
        ChatState.fillAnswerVariable("$nodeId$index", value)
        FlowKit.userSay(value)

        if (index + 1 < qs.size) return ask(nodeData, nodeId, index + 1)
        ChatState.setVariable(indexKey(nodeId), null)
        return NodeResult.Proceed()
    }
}
