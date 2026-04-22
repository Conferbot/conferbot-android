package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.nodes.*

/**
 * Handler for two-choices-node
 * Displays two choice buttons
 */
class TwoChoicesNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.TWO_CHOICES

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val choice1 = getString(nodeData, "choice1", "Option 1")
        val choice2 = getString(nodeData, "choice2", "Option 2")
        val disableSecond = getBoolean(nodeData, "disableSecondChoice", false)
        val answerKey = getString(nodeData, "answerVariable", nodeId)
        val choicePrompt = nodeData["choicePrompt"]?.toString()?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }

        state.addAnswerVariable(nodeId, answerKey)

        val choices = mutableListOf(
            NodeUIState.SingleChoice.Choice(
                id = "0",
                text = stripHtml(choice1),
                targetPort = "source-1"
            )
        )

        if (!disableSecond) {
            choices.add(
                NodeUIState.SingleChoice.Choice(
                    id = "1",
                    text = stripHtml(choice2),
                    targetPort = "source-2"
                )
            )
        }

        return NodeResult.DisplayUI(
            NodeUIState.SingleChoice(
                questionText = choicePrompt,
                choices = choices,
                nodeId = nodeId,
                answerKey = answerKey
            )
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val responseMap = when (response) {
            is Map<*, *> -> response as Map<String, Any?>
            else -> mapOf("id" to response.toString())
        }

        val choiceId = responseMap["id"]?.toString() ?: "0"
        val choiceText = responseMap["text"]?.toString()
            ?: when (choiceId) {
                "0" -> getString(nodeData, "choice1", "Option 1")
                "1" -> getString(nodeData, "choice2", "Option 2")
                else -> "Unknown"
            }

        val cleanText = stripHtml(choiceText)

        state.setAnswerVariable(nodeId, cleanText)
        state.addToTranscript("user", cleanText)

        recordResponse(
            nodeId = nodeId,
            shape = "user-selected-choice",
            text = cleanText,
            type = nodeType,
            additionalData = mapOf(
                "choiceId" to choiceId,
                "choices" to mapOf(
                    "choice1" to nodeData["choice1"],
                    "choice2" to nodeData["choice2"]
                )
            )
        )

        val targetPort = "source-${choiceId.toIntOrNull()?.plus(1) ?: 1}"
        return NodeResult.DelayedProceed(delayMs = 600, targetPort = targetPort)
    }
}

/**
 * Handler for three-choices-node
 * Displays three choice buttons
 */
class ThreeChoicesNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.THREE_CHOICES

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val choice1 = getString(nodeData, "choice1", "Option 1")
        val choice2 = getString(nodeData, "choice2", "Option 2")
        val choice3 = getString(nodeData, "choice3", "Option 3")
        val answerKey = getString(nodeData, "answerVariable", nodeId)
        val choicePrompt = nodeData["choicePrompt"]?.toString()?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }

        state.addAnswerVariable(nodeId, answerKey)

        val choices = listOf(
            NodeUIState.SingleChoice.Choice("0", stripHtml(choice1), targetPort = "source-1"),
            NodeUIState.SingleChoice.Choice("1", stripHtml(choice2), targetPort = "source-2"),
            NodeUIState.SingleChoice.Choice("2", stripHtml(choice3), targetPort = "source-3")
        )

        return NodeResult.DisplayUI(
            NodeUIState.SingleChoice(
                questionText = choicePrompt,
                choices = choices,
                nodeId = nodeId,
                answerKey = answerKey
            )
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val responseMap = when (response) {
            is Map<*, *> -> response as Map<String, Any?>
            else -> mapOf("id" to response.toString())
        }

        val choiceId = responseMap["id"]?.toString() ?: "0"
        val choiceText = responseMap["text"]?.toString()
            ?: when (choiceId) {
                "0" -> getString(nodeData, "choice1")
                "1" -> getString(nodeData, "choice2")
                "2" -> getString(nodeData, "choice3")
                else -> "Unknown"
            }

        val cleanText = stripHtml(choiceText)

        state.setAnswerVariable(nodeId, cleanText)
        state.addToTranscript("user", cleanText)

        recordResponse(
            nodeId = nodeId,
            shape = "user-selected-choice",
            text = cleanText,
            type = nodeType
        )

        val targetPort = "source-${choiceId.toIntOrNull()?.plus(1) ?: 1}"
        return NodeResult.DelayedProceed(delayMs = 600, targetPort = targetPort)
    }
}

/**
 * Handler for n-choices-node
 * Displays N choice buttons dynamically
 */
class NChoicesNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.N_CHOICES

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val choicesData = getList<Map<String, Any?>>(nodeData, "choices")
        val answerKey = getString(nodeData, "answerVariable", nodeId)
        val choicePrompt = nodeData["choicePrompt"]?.toString()?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }

        state.addAnswerVariable(nodeId, answerKey)

        val choices = choicesData.map { choice ->
            val id = choice["id"]?.toString() ?: ""
            val text = choice["choiceText"]?.toString() ?: choice["text"]?.toString() ?: ""
            NodeUIState.SingleChoice.Choice(
                id = id,
                text = stripHtml(text),
                targetPort = "source-$id"
            )
        }

        return NodeResult.DisplayUI(
            NodeUIState.SingleChoice(
                questionText = choicePrompt,
                choices = choices,
                nodeId = nodeId,
                answerKey = answerKey
            )
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val responseMap = when (response) {
            is Map<*, *> -> response as Map<String, Any?>
            else -> mapOf("id" to response.toString())
        }

        val choiceId = responseMap["id"]?.toString() ?: ""
        val choiceText = responseMap["text"]?.toString() ?: choiceId

        val cleanText = stripHtml(choiceText)

        state.setAnswerVariable(nodeId, cleanText)
        state.addToTranscript("user", cleanText)

        recordResponse(
            nodeId = nodeId,
            shape = "user-selected-choice",
            text = cleanText,
            type = nodeType,
            additionalData = mapOf("choiceId" to choiceId)
        )

        return NodeResult.DelayedProceed(delayMs = 600, targetPort = "source-$choiceId")
    }
}

/**
 * Handler for select-option-node (legacy)
 * Displays dropdown/option selection
 */
class SelectOptionNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.SELECT_OPTION

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val answerKey = getString(nodeData, "answerVariable", nodeId)
        val choicePrompt = nodeData["choicePrompt"]?.toString()?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }
        state.addAnswerVariable(nodeId, answerKey)

        // Build options from option1, option2, etc.
        val options = mutableListOf<NodeUIState.Dropdown.Option>()

        for (i in 1..5) {
            val optionKey = "option$i"
            val disableKey = "disableOption$i"

            if (getBoolean(nodeData, disableKey, false)) continue

            val optionText = nodeData[optionKey]?.toString()
            if (!optionText.isNullOrEmpty()) {
                options.add(NodeUIState.Dropdown.Option(
                    id = (i - 1).toString(),
                    text = stripHtml(optionText)
                ))
            }
        }

        return NodeResult.DisplayUI(
            NodeUIState.Dropdown(
                questionText = choicePrompt,
                options = options,
                nodeId = nodeId,
                answerKey = answerKey
            )
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val responseMap = when (response) {
            is Map<*, *> -> response as Map<String, Any?>
            else -> mapOf("text" to response.toString())
        }

        val optionText = responseMap["text"]?.toString() ?: ""
        val cleanText = stripHtml(optionText)

        state.setAnswerVariable(nodeId, cleanText)
        state.addToTranscript("user", cleanText)

        recordResponse(
            nodeId = nodeId,
            shape = "user-selected-option",
            text = cleanText,
            type = nodeType
        )

        return NodeResult.DelayedProceed(delayMs = 600)
    }
}

/**
 * Handler for n-select-option-node
 * Displays dropdown with dynamic options
 */
class NSelectOptionNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.N_SELECT_OPTION

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val optionsData = getList<Map<String, Any?>>(nodeData, "options")
        val answerKey = getString(nodeData, "answerVariable", nodeId)
        val choicePrompt = nodeData["choicePrompt"]?.toString()?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }

        state.addAnswerVariable(nodeId, answerKey)

        val options = optionsData.map { option ->
            NodeUIState.Dropdown.Option(
                id = option["id"]?.toString() ?: "",
                text = stripHtml(option["optionText"]?.toString() ?: "")
            )
        }

        return NodeResult.DisplayUI(
            NodeUIState.Dropdown(
                questionText = choicePrompt,
                options = options,
                nodeId = nodeId,
                answerKey = answerKey
            )
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val responseMap = when (response) {
            is Map<*, *> -> response as Map<String, Any?>
            else -> mapOf("text" to response.toString())
        }

        val optionText = responseMap["text"]?.toString() ?: ""
        val cleanText = stripHtml(optionText)

        state.setAnswerVariable(nodeId, cleanText)
        state.addToTranscript("user", cleanText)

        recordResponse(
            nodeId = nodeId,
            shape = "user-selected-option",
            text = cleanText,
            type = nodeType
        )

        return NodeResult.DelayedProceed(delayMs = 600)
    }
}

/**
 * Handler for n-check-options-node
 * Displays multiple checkboxes (multi-select)
 */
class NCheckOptionsNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.N_CHECK_OPTIONS

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val optionsData = getList<Map<String, Any?>>(nodeData, "options")
        val answerKey = getString(nodeData, "answerVariable", nodeId)
        val choicePrompt = nodeData["choicePrompt"]?.toString()?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }

        state.addAnswerVariable(nodeId, answerKey)

        val options = optionsData.map { option ->
            NodeUIState.MultipleChoice.Option(
                id = option["id"]?.toString() ?: "",
                text = stripHtml(option["optionText"]?.toString() ?: "")
            )
        }

        return NodeResult.DisplayUI(
            NodeUIState.MultipleChoice(
                questionText = choicePrompt,
                options = options,
                nodeId = nodeId,
                answerKey = answerKey
            )
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        // Response should be list of selected options
        val selectedOptions = when (response) {
            is List<*> -> response.filterIsInstance<String>()
            is String -> response.split(",").map { it.trim() }
            else -> listOf(response.toString())
        }

        val combinedText = selectedOptions.joinToString(", ")

        state.setAnswerVariable(nodeId, combinedText)
        state.addToTranscript("user", combinedText)

        recordResponse(
            nodeId = nodeId,
            shape = "user-check-options-response",
            text = combinedText,
            type = nodeType,
            additionalData = mapOf("selectedOptions" to selectedOptions)
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for image-choice-node
 * Displays image selection grid
 */
class ImageChoiceNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.IMAGE_CHOICE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val imagesData = getList<Map<String, Any?>>(nodeData, "images")
        val answerKey = getString(nodeData, "answerVariable", nodeId)
        val choicePrompt = nodeData["choicePrompt"]?.toString()?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }

        state.addAnswerVariable(nodeId, answerKey)

        val images = imagesData.map { image ->
            NodeUIState.ImageChoice.ImageOption(
                id = image["id"]?.toString() ?: "",
                imageUrl = image["image"]?.toString() ?: "",
                label = image["label"]?.toString() ?: "",
                targetPort = "source-${image["id"]}"
            )
        }

        return NodeResult.DisplayUI(
            NodeUIState.ImageChoice(
                questionText = choicePrompt,
                images = images,
                nodeId = nodeId,
                answerKey = answerKey
            )
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val responseMap = when (response) {
            is Map<*, *> -> response as Map<String, Any?>
            else -> mapOf("label" to response.toString())
        }

        val imageId = responseMap["id"]?.toString() ?: ""
        val label = responseMap["label"]?.toString() ?: ""

        state.setAnswerVariable(nodeId, label)
        state.addToTranscript("user", label)

        recordResponse(
            nodeId = nodeId,
            shape = "user-image-choice",
            text = label,
            type = nodeType,
            additionalData = mapOf(
                "imageId" to imageId,
                "imageUrl" to (responseMap["imageUrl"] ?: "")
            )
        )

        return NodeResult.DelayedProceed(delayMs = 600, targetPort = "source-$imageId")
    }
}

/**
 * Handler for yes-or-no-choice-node
 * Displays Yes/No buttons
 */
class YesOrNoChoiceNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.YES_OR_NO_CHOICE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val optionsData = getList<Map<String, Any?>>(nodeData, "options")
        val answerKey = getString(nodeData, "answerVariable", nodeId)
        val choicePrompt = nodeData["choicePrompt"]?.toString()?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }

        state.addAnswerVariable(nodeId, answerKey)

        val choices = if (optionsData.isNotEmpty()) {
            optionsData.map { option ->
                NodeUIState.SingleChoice.Choice(
                    id = option["id"]?.toString() ?: "",
                    text = option["label"]?.toString() ?: "",
                    targetPort = "source-${option["id"]}"
                )
            }
        } else {
            // Default Yes/No
            listOf(
                NodeUIState.SingleChoice.Choice("yes", "Yes", targetPort = "source-yes"),
                NodeUIState.SingleChoice.Choice("no", "No", targetPort = "source-no")
            )
        }

        return NodeResult.DisplayUI(
            NodeUIState.SingleChoice(
                questionText = choicePrompt,
                choices = choices,
                nodeId = nodeId,
                answerKey = answerKey
            )
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val responseMap = when (response) {
            is Map<*, *> -> response as Map<String, Any?>
            else -> mapOf("id" to response.toString(), "text" to response.toString())
        }

        val optionId = responseMap["id"]?.toString() ?: ""
        val label = responseMap["text"]?.toString() ?: optionId

        state.setAnswerVariable(nodeId, label)
        state.addToTranscript("user", label)

        recordResponse(
            nodeId = nodeId,
            shape = "user-yes-or-no-choice",
            text = label,
            type = nodeType
        )

        return NodeResult.DelayedProceed(delayMs = 600, targetPort = "source-$optionId")
    }
}

/**
 * Handler for rating-choice-node
 * Displays rating selector (smiley, 5-star, 10-point)
 */
class RatingChoiceNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.RATING_CHOICE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val ratingType = getString(nodeData, "ratingType", "5")
        val answerKey = getString(nodeData, "answerVariable", nodeId)
        val choicePrompt = nodeData["choicePrompt"]?.toString()?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }

        state.addAnswerVariable(nodeId, answerKey)

        val (type, min, max) = when (ratingType.lowercase()) {
            "smiley" -> Triple(NodeUIState.Rating.RatingType.SMILEY, 1, 5)
            "10" -> Triple(NodeUIState.Rating.RatingType.NUMBER, 1, 10)
            else -> Triple(NodeUIState.Rating.RatingType.STAR, 1, 5)
        }

        return NodeResult.DisplayUI(
            NodeUIState.Rating(
                questionText = choicePrompt,
                ratingType = type,
                minValue = min,
                maxValue = max,
                nodeId = nodeId,
                answerKey = answerKey
            )
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val rating = when (response) {
            is Number -> response.toInt()
            is String -> response.toIntOrNull() ?: 0
            else -> 0
        }

        state.setAnswerVariable(nodeId, rating)
        state.addToTranscript("user", rating.toString())

        recordResponse(
            nodeId = nodeId,
            shape = "user-rating-choice",
            text = rating.toString(),
            type = nodeType
        )

        // Port is 0-indexed (rating 1 -> source-0, etc.)
        val targetPort = "source-${rating - 1}"
        return NodeResult.DelayedProceed(delayMs = 600, targetPort = targetPort)
    }
}

/**
 * Handler for opinion-scale-choice-node
 * Displays NPS-style opinion scale
 */
class OpinionScaleChoiceNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.OPINION_SCALE_CHOICE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val from = getInt(nodeData, "from", 1)
        val to = getInt(nodeData, "to", 10)
        val answerKey = getString(nodeData, "answerVariable", nodeId)
        val choicePrompt = nodeData["choicePrompt"]?.toString()?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }

        state.addAnswerVariable(nodeId, answerKey)

        return NodeResult.DisplayUI(
            NodeUIState.Rating(
                questionText = choicePrompt,
                ratingType = NodeUIState.Rating.RatingType.OPINION_SCALE,
                minValue = from,
                maxValue = to,
                nodeId = nodeId,
                answerKey = answerKey
            )
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val value = when (response) {
            is Number -> response.toInt()
            is String -> response.toIntOrNull() ?: 0
            else -> 0
        }

        state.setAnswerVariable(nodeId, value)
        state.addToTranscript("user", value.toString())

        recordResponse(
            nodeId = nodeId,
            shape = "user-opinion-scale-choice",
            text = value.toString(),
            type = nodeType
        )

        // Calculate port index based on from value
        val from = getInt(nodeData, "from", 1)
        val portIndex = value - from
        return NodeResult.DelayedProceed(delayMs = 600, targetPort = "source-$portIndex")
    }
}

/**
 * Handler for user-rating-node (legacy)
 * Displays simple rating
 */
class UserRatingNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.USER_RATING

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val answerKey = getString(nodeData, "answerVariable", nodeId)
        val choicePrompt = nodeData["choicePrompt"]?.toString()?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }
        state.addAnswerVariable(nodeId, answerKey)

        return NodeResult.DisplayUI(
            NodeUIState.Rating(
                questionText = choicePrompt,
                ratingType = NodeUIState.Rating.RatingType.STAR,
                minValue = 1,
                maxValue = 5,
                nodeId = nodeId,
                answerKey = answerKey
            )
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val rating = when (response) {
            is Number -> response.toInt()
            is String -> response.toIntOrNull() ?: 0
            else -> 0
        }

        state.setAnswerVariable(nodeId, rating)
        state.addToTranscript("user", rating.toString())

        recordResponse(
            nodeId = nodeId,
            shape = "user-rating-response",
            text = rating.toString(),
            type = nodeType
        )

        return NodeResult.Proceed()
    }
}
