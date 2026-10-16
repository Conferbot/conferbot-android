package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.i18n.I18n
import com.conferbot.sdk.core.flow.FlowInputs
import com.conferbot.sdk.core.flow.FlowText
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.nodes.*
import com.conferbot.sdk.core.state.ChatState

/**
 * The choice steps: n-choices, image-choice, yes-or-no, rating-choice,
 * opinion-scale, n-select-option, n-check-options and carousel. Fields,
 * ports, saved values and records follow the flow-node spec and the web
 * widget (ts/chat/displayStep.ts, handlers/flowOperations.ts,
 * handlers/askQuestions.ts); ported from RN ChoiceNodeHandlers.ts:
 *
 * - n-choices / image-choice / yes-or-no / carousel leave by "source-" + the
 *   picked item's id; rating by source-<value-1>; opinion scale by
 *   source-<value-from>. No edge there ends the chat.
 * - n-select-option and n-check-options have no ports: the first edge,
 *   600 ms after the pick.
 * - Labels are shown with the answers filled in; what is saved is the
 *   option's own text, HTML stripped.
 */

/** The picked option by id, or by the label the SDK's cards send as {text}. */
private fun pick(response: Any, list: List<Map<String, Any?>>, idOf: (Map<String, Any?>) -> String, labelOf: (Map<String, Any?>) -> Any?): Map<String, Any?>? {
    val id = FlowKit.pickedId(response)
    list.firstOrNull { idOf(it) == id }?.let { return it }
    val text = (response as? Map<*, *>)?.get("text") ?: (response as? Map<*, *>)?.get("label") ?: (response as? String)
    if (text != null) return list.firstOrNull { FlowKit.savedText(labelOf(it)) == Js.str(text) || FlowKit.shownLabel(labelOf(it)) == Js.str(text) }
    return null
}

/** n-choices-node: choicePrompt, choices [{id, choiceText}] (blank ones hidden). */
class NChoicesNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.N_CHOICES

    private fun choices(data: Map<String, Any?>) = Js.list(data["choices"]).mapNotNull { Js.map(it) }.filter { Js.str(it["choiceText"]).trim().isNotEmpty() }

    private fun ui(data: Map<String, Any?>, nodeId: String) = NodeUIState.SingleChoice(
        questionText = FlowKit.shownText(data["choicePrompt"]),
        choices = choices(data).map { NodeUIState.SingleChoice.Choice(id = Js.str(it["id"]), text = FlowKit.shownLabel(it["choiceText"]), targetPort = "source-${Js.str(it["id"])}") },
        nodeId = nodeId,
        answerKey = Js.str(data["answerVariable"])
    )

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val choice = pick(response, choices(nodeData), { Js.str(it["id"]) }, { it["choiceText"] }) ?: return NodeResult.DisplayUI(ui(nodeData, nodeId))
        val text = FlowKit.savedText(choice["choiceText"])
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-selected-choice", "choices" to nodeData, "selectedChoice" to text))
        FlowKit.userSay(text)
        ChatState.fillAnswerVariable(nodeId, text)
        return NodeResult.Proceed("source-${Js.str(choice["id"])}")
    }
}

/** image-choice-node: questionText, images [{id, image, label}] (both non-blank to show). */
class ImageChoiceNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.IMAGE_CHOICE

    private fun images(data: Map<String, Any?>) = Js.list(data["images"]).mapNotNull { Js.map(it) }
        .filter { Js.str(it["image"]).trim().isNotEmpty() && Js.str(it["label"]).trim().isNotEmpty() }

    private fun ui(data: Map<String, Any?>, nodeId: String) = NodeUIState.ImageChoice(
        questionText = FlowKit.shownText(data["questionText"]),
        images = images(data).map { NodeUIState.ImageChoice.ImageOption(id = Js.str(it["id"]), imageUrl = Js.str(it["image"]), label = FlowKit.shownLabel(it["label"]), targetPort = "source-${Js.str(it["id"])}") },
        nodeId = nodeId,
        answerKey = Js.str(data["answerVariable"])
    )

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val image = pick(response, images(nodeData), { Js.str(it["id"]) }, { it["label"] }) ?: return NodeResult.DisplayUI(ui(nodeData, nodeId))
        val label = FlowKit.savedText(image["label"])
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-selected-image-choice", "images" to nodeData, "selectedImageLabel" to label, "selectedImage" to image["image"]))
        FlowKit.userSay(label)
        ChatState.fillAnswerVariable(nodeId, label)
        return NodeResult.Proceed("source-${Js.str(image["id"])}")
    }
}

/** yes-or-no-choice-node: questionText, options [{id, label}] (default Yes "1" / No "2"). */
class YesOrNoChoiceNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.YES_OR_NO_CHOICE

    private fun options(data: Map<String, Any?>): List<Map<String, Any?>> {
        val list = Js.list(data["options"]).mapNotNull { Js.map(it) }
        return list.ifEmpty { listOf(mapOf("id" to "1", "label" to I18n.t("sdk.common.yes")), mapOf("id" to "2", "label" to I18n.t("sdk.common.no"))) }
    }

    private fun ui(data: Map<String, Any?>, nodeId: String) = NodeUIState.SingleChoice(
        questionText = FlowKit.shownText(data["questionText"]),
        choices = options(data).map { NodeUIState.SingleChoice.Choice(id = Js.str(it["id"]), text = FlowKit.shownLabel(it["label"]), targetPort = "source-${Js.str(it["id"])}") },
        nodeId = nodeId,
        answerKey = Js.str(data["answerVariable"])
    )

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val option = pick(response, options(nodeData), { Js.str(it["id"]) }, { it["label"] }) ?: return NodeResult.DisplayUI(ui(nodeData, nodeId))
        val text = FlowKit.savedText(option["label"])
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-selected-yes-or-no-choice", "options" to nodeData, "selectedYesOrNoChoice" to text))
        FlowKit.userSay(text)
        ChatState.fillAnswerVariable(nodeId, text)
        return NodeResult.Proceed("source-${Js.str(option["id"])}")
    }
}

/** What the faces have always recorded (web nodeKit FACE_VALUES). */
internal val FACE_VALUES = listOf("😡", "😐", "😊", "😀", "😁")
/** The faces' words in the visitor's language (web nodeKit faceLabels). */
private val FACE_LABELS: List<String> get() = (1..5).map { I18n.t("rating.face.$it") }

internal fun ratingCount(ratingType: Any?): Int {
    if (ratingType == "smiley") return 5
    val n = Js.number(ratingType)
    return Math.max(1, Math.min(10, if (Js.isFinite(n) && n != 0.0) n.toInt() else 5))
}

/** What each score means, in the visitor's language (web nodeKit ratingWords). */
internal fun ratingWords(ratingType: Any?, n: Int): List<String> = when {
    ratingType == "smiley" -> FACE_LABELS
    n == 3 -> listOf("rating.bad", "rating.okay", "rating.great").map { I18n.t(it) }
    n == 5 -> listOf("rating.terrible", "rating.bad", "rating.okay", "rating.good", "rating.amazing").map { I18n.t(it) }
    else -> (1..n).map { I18n.t("rating.outOf", "n" to it, "max" to n) }
}

/** The value a rating button records: stars, or the face. */
internal fun ratingValue(ratingType: Any?, i: Int): String = if (ratingType == "smiley") FACE_VALUES.getOrElse(i - 1) { "😊" } else "⭐".repeat(i)

/** A number answer: the card's Int, or {value} / {rating}. */
internal fun numberAnswer(response: Any): Double {
    val v = if (response is Map<*, *>) response["value"] ?: response["rating"] else response
    return if (v == null) Double.NaN else Js.number(v)
}

/**
 * rating-choice-node: ratingType 1..10 stars or "smiley" (five faces).
 * Saves the NUMBER; leaves by source-<value-1>.
 */
class RatingChoiceNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.RATING_CHOICE

    private fun ui(data: Map<String, Any?>, nodeId: String): NodeUIState.Rating {
        val type = data["ratingType"]
        val n = ratingCount(type)
        return NodeUIState.Rating(
            questionText = FlowKit.shownText(data["questionText"]),
            ratingType = if (type == "smiley") NodeUIState.Rating.RatingType.SMILEY else if (n > 5) NodeUIState.Rating.RatingType.NUMBER else NodeUIState.Rating.RatingType.STAR,
            minValue = 1,
            maxValue = n,
            nodeId = nodeId,
            answerKey = Js.str(data["answerVariable"])
        )
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val type = nodeData["ratingType"]
        val n = ratingCount(type)
        val v = numberAnswer(response)
        if (!Js.isFinite(v) || v != Math.floor(v) || v < 1 || v > n) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        val value = v.toInt()
        val selected = ratingValue(type, value)
        FlowKit.pushAnswer(
            mapOf(
                "id" to nodeId, "shape" to "user-selected-rating-choice", "options" to nodeData, "selectedRatingChoice" to selected,
                "value" to value, "label" to ratingWords(type, n).getOrElse(value - 1) { "" }, "ratingType" to type
            )
        )
        FlowKit.userSay(selected)
        ChatState.fillAnswerVariable(nodeId, value)
        return NodeResult.Proceed("source-${value - 1}")
    }
}

/** A scale's range; blank is 0 to 10 (web nodeKit scaleRange). */
internal fun scaleRange(rawFrom: Any?, rawTo: Any?): Pair<Double, Double> {
    fun blank(v: Any?) = v == null || v == "" || !Js.isFinite(Js.number(v))
    val from = if (blank(rawFrom)) 0.0 else Js.number(rawFrom)
    val to = if (blank(rawTo) || Js.number(rawTo) < from) Math.max(from, 10.0) else Js.number(rawTo)
    return from to to
}

/** opinion-scale-choice-node: from..to buttons; saves the NUMBER; leaves by source-<value-from>. */
class OpinionScaleChoiceNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.OPINION_SCALE_CHOICE

    private fun ui(data: Map<String, Any?>, nodeId: String): NodeUIState.Rating {
        val (from, to) = scaleRange(data["from"], data["to"])
        return NodeUIState.Rating(
            questionText = FlowKit.shownText(data["questionText"]),
            ratingType = NodeUIState.Rating.RatingType.OPINION_SCALE,
            minValue = from.toInt(),
            maxValue = to.toInt(),
            nodeId = nodeId,
            answerKey = Js.str(data["answerVariable"])
        )
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val (from, to) = scaleRange(nodeData["from"], nodeData["to"])
        val v = numberAnswer(response)
        if (!Js.isFinite(v) || v < from || v > to) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        val value = Js.tidy(v)
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-selected-opinion-scale-choice", "options" to nodeData, "selectedOpinionScaleChoice" to Js.num(v), "value" to value))
        FlowKit.userSay(Js.num(v))
        ChatState.fillAnswerVariable(nodeId, value)
        return NodeResult.Proceed("source-${Js.num(v - from)}")
    }
}

/** n-select-option-node: selectPrompt, options [{id, optionText}]; no ports - first edge after 600 ms. */
class NSelectOptionNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.N_SELECT_OPTION

    private fun options(data: Map<String, Any?>) = Js.list(data["options"]).mapNotNull { Js.map(it) }.filter { Js.str(it["optionText"]).trim().isNotEmpty() }

    private fun ui(data: Map<String, Any?>, nodeId: String) = NodeUIState.Dropdown(
        questionText = FlowKit.shownText(data["selectPrompt"]),
        options = options(data).map { NodeUIState.Dropdown.Option(id = Js.str(it["id"]), text = FlowKit.shownLabel(it["optionText"])) },
        nodeId = nodeId,
        answerKey = Js.str(data["answerVariable"])
    )

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val option = pick(response, options(nodeData), { Js.str(it["id"]) }, { it["optionText"] }) ?: return NodeResult.DisplayUI(ui(nodeData, nodeId))
        val text = FlowKit.savedText(option["optionText"])
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-selected-option", "options" to nodeData, "selectedOption" to text))
        FlowKit.userSay(text)
        ChatState.fillAnswerVariable(nodeId, text)
        return NodeResult.DelayedProceed(600)
    }
}

/**
 * n-check-options-node: checkOptionsPrompt, options; the ticked texts in LIST
 * order joined ", "; first edge after 600 ms. Answers: {buttonIds: [...]} or
 * the card's list of ticked labels.
 */
class NCheckOptionsNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.N_CHECK_OPTIONS

    private fun options(data: Map<String, Any?>) = Js.list(data["options"]).mapNotNull { Js.map(it) }.filter { Js.str(it["optionText"]).trim().isNotEmpty() }

    private fun ui(data: Map<String, Any?>, nodeId: String) = NodeUIState.MultipleChoice(
        questionText = FlowKit.shownText(data["checkOptionsPrompt"]),
        options = options(data).map { NodeUIState.MultipleChoice.Option(id = Js.str(it["id"]), text = FlowKit.shownLabel(it["optionText"])) },
        nodeId = nodeId,
        answerKey = Js.str(data["answerVariable"])
    )

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val options = options(nodeData)
        val r = FlowKit.asMap(response)
        val ids: List<String> = when {
            r["buttonIds"] is List<*> -> Js.list(r["buttonIds"]).map { Js.str(it) }
            r["optionIds"] is List<*> -> Js.list(r["optionIds"]).map { Js.str(it) }
            response is List<*> -> response.mapNotNull { label -> options.firstOrNull { FlowKit.shownLabel(it["optionText"]) == Js.str(label) || FlowKit.savedText(it["optionText"]) == Js.str(label) }?.let { Js.str(it["id"]) } }
            else -> emptyList()
        }
        val ticked = FlowText.inListOrder(ids, options.map { Js.str(it["id"]) }).mapNotNull { id -> options.firstOrNull { Js.str(it["id"]) == id } }
        if (ticked.isEmpty()) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        val text = ticked.joinToString(", ") { FlowKit.savedText(it["optionText"]) }
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-selected-check-options", "options" to nodeData, "selectedCheckOptions" to text))
        FlowKit.userSay(text)
        ChatState.fillAnswerVariable(nodeId, text)
        return NodeResult.DelayedProceed(600)
    }
}

/** What a card step without path buttons records (English, as the web stores it); drawn as common.continue. */
internal const val CAROUSEL_CONTINUE = "Continue"

internal data class CarouselButton(val id: String, val label: String, val action: String, val href: String)
internal data class CarouselCard(val id: String, val image: String, val title: String, val description: String, val price: String, val buttons: List<CarouselButton>)

/** The cards as the web reads them (nodeKit carouselCards): at most 10, 3 buttons each. */
internal fun carouselCards(data: Map<String, Any?>?): List<CarouselCard> =
    Js.list(data?.get("cards")).mapIndexedNotNull { ci, c ->
        val card = Js.map(c) ?: emptyMap()
        CarouselCard(
            id = Js.str(card["id"] ?: "card-$ci"),
            image = Js.str(card["image"]).trim(),
            title = Js.str(card["title"]).trim(),
            description = Js.str(card["description"]).trim(),
            price = Js.str(card["price"]).trim(),
            buttons = Js.list(card["buttons"]).mapIndexed { bi, b ->
                val bb = Js.map(b) ?: emptyMap()
                val action = if (bb["action"] == "link") "link" else "path"
                CarouselButton(Js.str(bb["id"] ?: "$ci-$bi"), Js.str(bb["label"]).trim(), action, if (action == "link") FlowInputs.linkHref(bb["url"]) else "")
            }.filter { it.label.isNotEmpty() && (it.action == "path" || it.href.isNotEmpty()) }.take(3)
        )
    }.filter { it.title.isNotEmpty() || it.description.isNotEmpty() || it.image.isNotEmpty() }.take(10)

/** Cards as choices: one per path button ("Title - Label"), link buttons as links, Continue when no path. */
internal fun carouselChoices(cards: List<CarouselCard>): List<NodeUIState.SingleChoice.Choice> {
    val out = mutableListOf<NodeUIState.SingleChoice.Choice>()
    cards.forEach { c ->
        c.buttons.forEach { b ->
            val label = if (b.action == "path" && c.title.isNotEmpty()) "${FlowKit.shownLabel(c.title)} - ${b.label}" else b.label
            out.add(NodeUIState.SingleChoice.Choice(id = b.id, text = label, imageUrl = c.image.ifEmpty { null }, targetPort = if (b.action == "link") b.href else "source-${b.id}"))
        }
    }
    if (cards.none { c -> c.buttons.any { it.action == "path" } }) out.add(NodeUIState.SingleChoice.Choice(id = "continue", text = I18n.t("common.continue"), targetPort = "source"))
    return out
}

/**
 * carousel-node: a path button leaves by "source-" + its id and saves
 * "Card title - Button label"; link buttons only open their page. With no
 * path button anywhere, Continue leaves by "source" and saves nothing.
 * Answers: {buttonId} (cardId optional) or {action: "continue"}.
 */
class CarouselNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.CAROUSEL

    private fun ui(data: Map<String, Any?>, nodeId: String) =
        NodeUIState.SingleChoice(FlowKit.shownText(data["questionText"]), carouselChoices(carouselCards(data)), nodeId, Js.str(data["answerVariable"]))

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val r = FlowKit.asMap(response)
        val cards = carouselCards(nodeData)
        val buttonId = if (r["action"] == "continue") null else FlowKit.pickedId(response)?.takeIf { it != "continue" }
        var card: CarouselCard? = null
        var button: CarouselButton? = null
        for (c in cards) {
            val b = c.buttons.firstOrNull { it.id == buttonId && it.action == "path" }
            if (b != null && (r["cardId"] == null || r["cardId"] == c.id)) {
                card = c
                button = b
                break
            }
        }
        // A link button opens its page and the step keeps waiting.
        if (buttonId != null && button == null) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        if (button == null && cards.any { c -> c.buttons.any { it.action == "path" } }) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        val title = card?.title ?: ""
        val label = button?.label ?: ""
        val answer = if (button != null) (if (title.isNotEmpty() && label.isNotEmpty()) "$title - $label" else title.ifEmpty { label }) else CAROUSEL_CONTINUE
        val text = FlowKit.savedText(answer)
        FlowKit.pushAnswer(
            mapOf(
                "id" to nodeId, "shape" to "user-selected-carousel", "carousel" to nodeData, "selectedChoice" to text,
                "selectedCardId" to card?.id, "selectedButtonId" to button?.id
            )
        )
        FlowKit.userSay(if (button != null) text else I18n.t("common.continue"))
        if (button != null) ChatState.fillAnswerVariable(nodeId, text)
        return NodeResult.Proceed(if (button != null) "source-${button.id}" else "source")
    }
}
