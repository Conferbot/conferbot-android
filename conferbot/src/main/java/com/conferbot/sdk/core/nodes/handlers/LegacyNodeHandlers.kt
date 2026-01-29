package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.nodes.*

/**
 * Handler for user-input-node (legacy)
 * Handles various input types: name, email, number, url, phone, file, date
 */
class UserInputNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.USER_INPUT

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val inputType = getString(nodeData, "type", "text")
        val answerKey = getString(nodeData, "answerVariable", nodeId)

        state.addAnswerVariable(nodeId, answerKey)

        return when (inputType.lowercase()) {
            "name" -> NodeResult.DisplayUI(
                NodeUIState.TextInput(
                    questionText = "",
                    inputType = NodeUIState.TextInput.InputType.NAME,
                    placeholder = "Enter your name",
                    nodeId = nodeId,
                    answerKey = answerKey
                )
            )
            "email" -> NodeResult.DisplayUI(
                NodeUIState.TextInput(
                    questionText = "",
                    inputType = NodeUIState.TextInput.InputType.EMAIL,
                    placeholder = "Enter your email",
                    errorMessage = getString(nodeData, "incorrectEmailResponse", "Please enter a valid email"),
                    nodeId = nodeId,
                    answerKey = answerKey
                )
            )
            "number" -> NodeResult.DisplayUI(
                NodeUIState.TextInput(
                    questionText = "",
                    inputType = NodeUIState.TextInput.InputType.NUMBER,
                    placeholder = "Enter a number",
                    errorMessage = getString(nodeData, "incorrectNumberResponse", "Please enter a valid number"),
                    nodeId = nodeId,
                    answerKey = answerKey
                )
            )
            "url" -> NodeResult.DisplayUI(
                NodeUIState.TextInput(
                    questionText = "",
                    inputType = NodeUIState.TextInput.InputType.URL,
                    placeholder = "Enter a URL",
                    errorMessage = getString(nodeData, "incorrectUrlResponse", "Please enter a valid URL"),
                    nodeId = nodeId,
                    answerKey = answerKey
                )
            )
            "mobile", "phone" -> NodeResult.DisplayUI(
                NodeUIState.TextInput(
                    questionText = "",
                    inputType = NodeUIState.TextInput.InputType.PHONE,
                    placeholder = "Enter phone number",
                    nodeId = nodeId,
                    answerKey = answerKey
                )
            )
            "file" -> NodeResult.DisplayUI(
                NodeUIState.FileUpload(
                    questionText = "",
                    maxSizeMb = 5,
                    nodeId = nodeId,
                    answerKey = answerKey
                )
            )
            "date" -> NodeResult.DisplayUI(
                NodeUIState.Calendar(
                    questionText = null,
                    showTimeSelection = false,
                    timezone = null,
                    nodeId = nodeId,
                    answerKey = answerKey
                )
            )
            else -> NodeResult.DisplayUI(
                NodeUIState.TextInput(
                    questionText = "",
                    inputType = NodeUIState.TextInput.InputType.TEXT,
                    placeholder = "Type here...",
                    nodeId = nodeId,
                    answerKey = answerKey
                )
            )
        }
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val inputType = getString(nodeData, "type", "text")
        val value = response.toString().trim()

        // Validate based on type
        when (inputType.lowercase()) {
            "email" -> {
                if (!isValidEmail(value)) {
                    return NodeResult.Error(
                        getString(nodeData, "incorrectEmailResponse", "Please enter a valid email"),
                        shouldProceed = false
                    )
                }
                state.setUserMetadata("email", value)
            }
            "number" -> {
                if (!isValidNumber(value)) {
                    return NodeResult.Error(
                        getString(nodeData, "incorrectNumberResponse", "Please enter a valid number"),
                        shouldProceed = false
                    )
                }
            }
            "url" -> {
                if (!isValidUrl(value)) {
                    return NodeResult.Error(
                        getString(nodeData, "incorrectUrlResponse", "Please enter a valid URL"),
                        shouldProceed = false
                    )
                }
            }
            "name" -> {
                if (value.isEmpty()) {
                    return NodeResult.Error("Please enter your name", shouldProceed = false)
                }
                state.setUserMetadata("name", value)
            }
            "mobile", "phone" -> {
                if (!isValidPhone(value)) {
                    return NodeResult.Error("Please enter a valid phone number", shouldProceed = false)
                }
                state.setUserMetadata("phone", value)
            }
        }

        state.setAnswerVariable(nodeId, value)
        state.addToTranscript("user", value)

        recordResponse(
            nodeId = nodeId,
            shape = "user-input-response",
            text = value,
            type = nodeType,
            additionalData = mapOf("inputType" to inputType)
        )

        // Handle name greeting
        if (inputType.lowercase() == "name") {
            val greetResponse = nodeData["nameGreet"]?.toString()
            if (!greetResponse.isNullOrEmpty()) {
                val greeting = greetResponse
                    .replace("{name}", value)
                    .replace("\${name}", value)
                state.addToTranscript("bot", greeting)
            }
            return NodeResult.DelayedProceed(delayMs = 2000)
        }

        return NodeResult.DelayedProceed(delayMs = 500)
    }
}

/**
 * Handler for user-range-node (legacy)
 * Displays a range slider
 */
class UserRangeNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.USER_RANGE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val minVal = getInt(nodeData, "minVal", 0)
        val maxVal = getInt(nodeData, "maxVal", 100)
        val answerKey = getString(nodeData, "answerVariable", nodeId)

        state.addAnswerVariable(nodeId, answerKey)

        return NodeResult.DisplayUI(
            NodeUIState.Range(
                questionText = null,
                minValue = minVal,
                maxValue = maxVal,
                defaultValue = (minVal + maxVal) / 2,
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

        val minVal = getInt(nodeData, "minVal", 0)
        val maxVal = getInt(nodeData, "maxVal", 100)

        state.setAnswerVariable(nodeId, value)
        state.addToTranscript("user", value.toString())

        recordResponse(
            nodeId = nodeId,
            shape = "user-range-response",
            text = value.toString(),
            type = nodeType,
            additionalData = mapOf("minVal" to minVal, "maxVal" to maxVal)
        )

        // Route based on value relative to min/max
        val targetPort = when {
            value < minVal -> "source-1"
            value in minVal..maxVal -> "source-2"
            else -> "source-3"
        }

        return NodeResult.Proceed(targetPort = targetPort)
    }
}

/**
 * Handler for quiz-node (legacy)
 * Displays a quiz question with correct/incorrect branching
 */
class QuizNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.QUIZ

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val answerKey = getString(nodeData, "answerVariable", nodeId)
        state.addAnswerVariable(nodeId, answerKey)

        // Build options from option1, option2, etc.
        val options = mutableListOf<String>()
        for (i in 1..5) {
            val optionKey = "option$i"
            val disableKey = "disableOption$i"

            if (getBoolean(nodeData, disableKey, false)) continue

            val optionText = nodeData[optionKey]?.toString()
            if (!optionText.isNullOrEmpty()) {
                options.add(stripHtml(optionText))
            }
        }

        val correctAnswer = getInt(nodeData, "correctAnswer", 0)

        return NodeResult.DisplayUI(
            NodeUIState.Quiz(
                questionText = "",
                options = options,
                correctAnswerIndex = correctAnswer,
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
        val selectedIndex = when (response) {
            is Number -> response.toInt()
            is String -> response.toIntOrNull() ?: -1
            is Map<*, *> -> (response as Map<String, Any?>)["index"]?.toString()?.toIntOrNull() ?: -1
            else -> -1
        }

        val correctAnswer = getInt(nodeData, "correctAnswer", 0)
        val isCorrect = selectedIndex == correctAnswer

        // Get selected option text
        val optionKey = "option${selectedIndex + 1}"
        val selectedText = nodeData[optionKey]?.toString() ?: "Unknown"

        state.setAnswerVariable(nodeId, selectedText)
        state.addToTranscript("user", selectedText)

        recordResponse(
            nodeId = nodeId,
            shape = "user-quiz-response",
            text = selectedText,
            type = nodeType,
            additionalData = mapOf(
                "selectedIndex" to selectedIndex,
                "correctAnswer" to correctAnswer,
                "isCorrect" to isCorrect
            )
        )

        // Route based on correct/incorrect
        return if (isCorrect) {
            // Correct: go to source-2 (may increment a variable node)
            NodeResult.Proceed(targetPort = "source-2")
        } else {
            // Incorrect: go to source-1
            NodeResult.Proceed(targetPort = "source-1")
        }
    }
}
