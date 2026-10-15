package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.flow.FlowInputs
import com.conferbot.sdk.core.flow.FlowInputs.EN
import com.conferbot.sdk.core.flow.FlowText
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.flow.flowContext
import com.conferbot.sdk.core.nodes.*
import com.conferbot.sdk.core.state.ChatState

/**
 * The v1 steps still found in live bots: two-choices, three-choices,
 * select-option, user-input, user-range, user-rating and quiz. Fields and
 * behaviour follow the flow-node spec and the web widget
 * (ts/chat/handlers/_legacy.ts, flowOperations.ts, displayStep.ts); ported
 * from RN LegacyNodeHandlers.ts. Legacy steps never register an answer variable.
 */

/** Numbered options (choiceText1.., option1..) as choices, ids "1".."n". */
private fun numberedChoices(data: Map<String, Any?>, key: String, count: Int, hidden: (Int) -> Boolean): List<NodeUIState.SingleChoice.Choice> =
    (1..count).filter { !hidden(it) }.map { n -> NodeUIState.SingleChoice.Choice(id = n.toString(), text = FlowKit.shownLabel(data["$key$n"])) }

/** The picked number: the card's {id}, an {index} (0-based), or the label. */
private fun pickedNumber(response: Any, data: Map<String, Any?>, key: String, count: Int): Int? {
    val r = FlowKit.asMap(response)
    FlowKit.pickedId(response)?.toIntOrNull()?.let { return it }
    (r["index"] as? Number)?.let { return it.toInt() + 1 }
    val text = r["text"] ?: r["label"] ?: (response as? String) ?: return null
    return (1..count).firstOrNull { FlowKit.savedText(data["$key$it"]) == Js.str(text) || FlowKit.shownLabel(data["$key$it"]) == Js.str(text) }
}

/** two-choices-node: choiceText1 -> source-1, choiceText2 -> source-2 (disableSecondChoice hides it). */
open class TwoChoicesNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.TWO_CHOICES
    protected open val count = 2

    protected open fun hidden(data: Map<String, Any?>, n: Int) = n == 2 && Js.truthy(data["disableSecondChoice"])

    private fun ui(data: Map<String, Any?>, nodeId: String) =
        NodeUIState.SingleChoice(FlowKit.shownText(data["choicePrompt"]), numberedChoices(data, "choiceText", count) { hidden(data, it) }, nodeId, "")

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val n = pickedNumber(response, nodeData, "choiceText", count)
        if (n == null || n < 1 || n > count || hidden(nodeData, n)) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        val text = FlowKit.savedText(nodeData["choiceText$n"])
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-selected-choice", "choices" to nodeData, "selectedChoice" to text))
        FlowKit.userSay(text)
        return NodeResult.Proceed("source-$n")
    }
}

/** three-choices-node: choiceText1..3 -> source-1..3. */
class ThreeChoicesNodeHandler : TwoChoicesNodeHandler() {
    override val nodeType = NodeTypes.THREE_CHOICES
    override val count = 3
    override fun hidden(data: Map<String, Any?>, n: Int) = false
}

/** select-option-node: option1..option5 (disableOption3..5); the first edge 600 ms after the pick. */
class SelectOptionNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.SELECT_OPTION

    private fun hidden(data: Map<String, Any?>, n: Int) = n >= 3 && Js.truthy(data["disableOption$n"])

    private fun ui(data: Map<String, Any?>, nodeId: String) =
        NodeUIState.SingleChoice(FlowKit.shownText(data["selectPrompt"]), numberedChoices(data, "option", 5) { hidden(data, it) }, nodeId, "")

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val n = pickedNumber(response, nodeData, "option", 5)
        if (n == null || n < 1 || n > 5 || hidden(nodeData, n)) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        val text = FlowKit.savedText(nodeData["option$n"])
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-selected-option", "options" to nodeData, "selectedOption" to text))
        FlowKit.userSay(text)
        return NodeResult.DelayedProceed(600)
    }
}

private val LEGACY_URL_RE = Regex("^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})([/\\w .-]*)*/?$", RegexOption.IGNORE_CASE)

/**
 * user-input-node: data.type name | email | number | url | date | file |
 * phone | mobile. email / number / url are checked (incorrect*Response);
 * name greets with nameGreet after 1000 ms of typing and moves on at 2000
 * ms; others at 500 ms; a date or a file at once.
 */
class UserInputNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.USER_INPUT

    private fun ui(data: Map<String, Any?>, nodeId: String): NodeUIState {
        val type = Js.str(data["type"])
        val question = FlowKit.shownText(data["inputPrompt"])
        if (type == "file") return NodeUIState.FileUpload(questionText = question, maxSizeMb = 5, useSignedUrl = false, nodeId = nodeId, answerKey = "", chatSessionId = ChatState.chatSessionId)
        val inputType = when (type) {
            "email" -> NodeUIState.TextInput.InputType.EMAIL
            "number" -> NodeUIState.TextInput.InputType.NUMBER
            "url" -> NodeUIState.TextInput.InputType.URL
            "phone", "mobile" -> NodeUIState.TextInput.InputType.PHONE
            "name" -> NodeUIState.TextInput.InputType.NAME
            else -> NodeUIState.TextInput.InputType.TEXT
        }
        return NodeUIState.TextInput(questionText = question, inputType = inputType, nodeId = nodeId, answerKey = "")
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        FlowKit.botSay(FlowKit.shownText(nodeData["inputPrompt"]))
        return NodeResult.DisplayUI(ui(nodeData, nodeId))
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val type = nodeData["type"] as? String
        val ctx = flowContext()
        if (type == "file") {
            val r = FlowKit.asMap(response)
            var url = r["url"] as? String
            if (url == null) {
                val file = pickedFileOf(r)
                if (file != null) {
                    if ((file.size ?: 0.0) > 5_000_000) return NodeResult.Invalid("Please upload a file smaller than 5MB", ui(nodeData, nodeId))
                    val up = ctx.upload(file)
                    if (!up.ok) return NodeResult.Invalid("Couldn't upload this file. Please try again.", ui(nodeData, nodeId))
                    url = up.url
                }
            }
            if (url == null) return NodeResult.DisplayUI(ui(nodeData, nodeId))
            FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-input-response", "type" to type, "url" to url))
            FlowKit.userSay(url)
            return NodeResult.Proceed()
        }

        val value = FlowKit.typedText(response)
        if (value.isEmpty()) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        if (type == "email" && !FlowInputs.isValidEmail(value)) {
            return NodeResult.Invalid(FlowInputs.ownerTextOr(nodeData["incorrectEmailResponse"], EN.emailInvalid, listOf("Please enter a valid email")), ui(nodeData, nodeId))
        }
        if (type == "number" && (value.trim().isEmpty() || Js.number(value.trim()).isNaN())) {
            return NodeResult.Invalid(FlowInputs.ownerTextOr(nodeData["incorrectNumberResponse"], "Please enter a valid number"), ui(nodeData, nodeId))
        }
        if (type == "url" && !LEGACY_URL_RE.matches(value.lowercase())) {
            return NodeResult.Invalid(FlowInputs.ownerTextOr(nodeData["incorrectUrlResponse"], "Please enter a valid url"), ui(nodeData, nodeId))
        }
        if (type == "name") ChatState.setUserMetadata("name", value)
        if (type == "email") ChatState.setUserMetadata("email", value)
        if (type == "phone" || type == "mobile") ChatState.setUserMetadata("phone", value)

        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-input-response", "type" to type, "text" to value))
        FlowKit.userSay(value)

        if (type == "date") return NodeResult.Proceed()
        if (type == "name") {
            val greet = nodeData["nameGreet"].let { if (Js.truthy(it)) it else null }
            val greeting = FlowText.nameGreetingHtml(greet, value)
            ctx.typing(1000)
            if (greeting.isNotEmpty()) FlowKit.botSay(FlowKit.shownText(greeting), side = true)
            return NodeResult.DelayedProceed(1000)
        }
        return NodeResult.DelayedProceed(500)
    }
}

/** user-range-node: a number; below minVal -> source-1, within -> source-2, above maxVal -> source-3. */
class UserRangeNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.USER_RANGE

    private fun ui(data: Map<String, Any?>, nodeId: String) =
        NodeUIState.TextInput(questionText = FlowKit.shownText(data["rangePrompt"]), inputType = NodeUIState.TextInput.InputType.NUMBER, nodeId = nodeId, answerKey = "")

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText)
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val value = if (response is Number) Js.num(response.toDouble()) else FlowKit.typedText(response)
        val n = FlowInputs.parseNumberInput(value) ?: return NodeResult.Invalid(EN.numberLike, ui(nodeData, nodeId))
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-input-response-range", "type" to nodeData["type"], "text" to value))
        FlowKit.userSay(value)
        val handle = when {
            n < Js.number(nodeData["minVal"]) -> "source-1"
            n > Js.number(nodeData["maxVal"]) -> "source-3"
            else -> "source-2"
        }
        return NodeResult.Proceed(handle)
    }
}

/** user-rating-node: type number (data.range buttons), star or smiley (five); the first edge. */
class UserRatingNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.USER_RATING

    private fun count(data: Map<String, Any?>): Int {
        if (data["type"] != "number") return 5
        val n = Js.number(data["range"])
        return Math.max(1, if (Js.isFinite(n) && n != 0.0) n.toInt() else 5)
    }

    private fun ui(data: Map<String, Any?>, nodeId: String) = NodeUIState.Rating(
        questionText = FlowKit.shownText(data["ratingPrompt"]),
        ratingType = when (data["type"]) {
            "number" -> NodeUIState.Rating.RatingType.NUMBER
            "smiley" -> NodeUIState.Rating.RatingType.SMILEY
            else -> NodeUIState.Rating.RatingType.STAR
        },
        minValue = 1,
        maxValue = count(data),
        nodeId = nodeId,
        answerKey = ""
    )

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val v = numberAnswer(response)
        if (!Js.isFinite(v) || v != Math.floor(v) || v < 1 || v > count(nodeData)) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        val rating = v.toInt()
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-selected-rating", "type" to nodeData["type"], "rating" to rating))
        FlowKit.userSay(rating.toString())
        return NodeResult.Proceed()
    }
}

/**
 * quiz-node: option1..5 (disableOption3..5), correctAnswer "optionN".
 * Wrong -> source-1, right -> source-2. When source-2 leads to a
 * variable-node, that step is not run: its value is ADDED to the flow
 * variable (the score) and the flow jumps to the step after it.
 */
class QuizNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.QUIZ

    private fun hidden(data: Map<String, Any?>, n: Int) = n >= 3 && Js.truthy(data["disableOption$n"])

    private fun ui(data: Map<String, Any?>, nodeId: String) =
        NodeUIState.SingleChoice(FlowKit.shownText(data["quizPrompt"]), numberedChoices(data, "option", 5) { hidden(data, it) }, nodeId, "")

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ui = ui(nodeData, nodeId)
        FlowKit.botSay(ui.questionText ?: "")
        return NodeResult.DisplayUI(ui)
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val n = pickedNumber(response, nodeData, "option", 5)
        if (n == null || n < 1 || n > 5 || hidden(nodeData, n)) return NodeResult.DisplayUI(ui(nodeData, nodeId))
        val option = "option$n"
        val correctAnswer = nodeData["correctAnswer"]
        FlowKit.pushAnswer(
            mapOf(
                "id" to nodeId, "shape" to "user-selected-quiz-option", "options" to nodeData, "selectedOption" to nodeData[option],
                "correctAnswer" to correctAnswer, "correct" to (option == correctAnswer)
            )
        )
        FlowKit.userSay(FlowKit.savedText(nodeData[option]))
        if (option != correctAnswer) return NodeResult.Proceed("source-1")

        val ctx = flowContext()
        val rightEdge = ctx.edges().firstOrNull { it.sourceHandle == "source-2" }
        val nextNode = rightEdge?.let { ctx.node(it.target) }
        if (nextNode?.type == "variable-node") {
            val name = Js.str(nextNode.data["name"])
            val current = ChatState.getVariable(name)
            val add = Js.number(nextNode.data["value"])
            val value = if (Js.truthy(current)) Js.number(current) + add else add
            ChatState.setVariable(name, Js.tidy(value))
            val after = ctx.edgesOf(nextNode.id).firstOrNull()
            if (after != null && after.target.isNotEmpty()) return NodeResult.JumpTo(after.target)
            return NodeResult.Proceed("source-1")
        }
        return NodeResult.Proceed("source-2")
    }
}
