package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.nodes.*

/**
 * Handler for ask-name-node
 * Asks user for their name
 */
class AskNameNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ASK_NAME

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val questionText = getString(nodeData, "questionText", "What is your name?")
        val answerKey = getString(nodeData, "answerVariable", "name")

        // Add question to transcript
        state.addToTranscript("bot", questionText)

        // Initialize answer variable
        state.addAnswerVariable(nodeId, answerKey)

        return NodeResult.DisplayUI(
            NodeUIState.TextInput(
                questionText = questionText,
                inputType = NodeUIState.TextInput.InputType.NAME,
                placeholder = "Enter your name",
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
        val name = response.toString().trim()

        if (name.isEmpty()) {
            return NodeResult.Error("Please enter your name", shouldProceed = false)
        }

        // Update answer variable
        state.setAnswerVariable(nodeId, name)

        // Set user metadata
        state.setUserMetadata("name", name)

        // Add to transcript
        state.addToTranscript("user", name)

        // Record the response
        recordResponse(
            nodeId = nodeId,
            shape = "user-ask-name-response",
            text = name,
            type = nodeType
        )

        // Display greeting if configured
        val greetResponse = nodeData["nameGreetResponse"]?.toString()
            ?: nodeData["nameGreet"]?.toString()

        if (!greetResponse.isNullOrEmpty()) {
            // Replace placeholders with name
            val greeting = greetResponse
                .replace("{name}", name)
                .replace("\${name}", name)
                .replace("{{name}}", name)

            state.addToTranscript("bot", greeting)
        }

        return NodeResult.DelayedProceed(delayMs = 500)
    }
}

/**
 * Handler for ask-email-node
 * Asks user for their email
 */
class AskEmailNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ASK_EMAIL

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val questionText = getString(nodeData, "questionText", "What is your email?")
        val answerKey = getString(nodeData, "answerVariable", "email")
        val errorMessage = getString(nodeData, "incorrectEmailResponse", "Please enter a valid email address")

        state.addToTranscript("bot", questionText)
        state.addAnswerVariable(nodeId, answerKey)

        return NodeResult.DisplayUI(
            NodeUIState.TextInput(
                questionText = questionText,
                inputType = NodeUIState.TextInput.InputType.EMAIL,
                placeholder = "Enter your email",
                errorMessage = errorMessage,
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
        val email = response.toString().trim()
        val errorMessage = getString(nodeData, "incorrectEmailResponse", "Please enter a valid email address")

        if (!isValidEmail(email)) {
            return NodeResult.Error(errorMessage, shouldProceed = false)
        }

        state.setAnswerVariable(nodeId, email)
        state.setUserMetadata("email", email)
        state.addToTranscript("user", email)

        recordResponse(
            nodeId = nodeId,
            shape = "user-ask-email-response",
            text = email,
            type = nodeType
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for ask-phone-number-node
 * Asks user for their phone number
 */
class AskPhoneNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ASK_PHONE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val questionText = getString(nodeData, "questionText", "What is your phone number?")
        val answerKey = getString(nodeData, "answerVariable", "phone")
        val errorMessage = getString(nodeData, "incorrectPhoneNumberResponse", "Please enter a valid phone number")

        state.addToTranscript("bot", questionText)
        state.addAnswerVariable(nodeId, answerKey)

        return NodeResult.DisplayUI(
            NodeUIState.TextInput(
                questionText = questionText,
                inputType = NodeUIState.TextInput.InputType.PHONE,
                placeholder = "Enter your phone number",
                errorMessage = errorMessage,
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
        val phone = response.toString().trim()
        val errorMessage = getString(nodeData, "incorrectPhoneNumberResponse", "Please enter a valid phone number")

        if (!isValidPhone(phone)) {
            return NodeResult.Error(errorMessage, shouldProceed = false)
        }

        state.setAnswerVariable(nodeId, phone)
        state.setUserMetadata("phone", phone)
        state.addToTranscript("user", phone)

        recordResponse(
            nodeId = nodeId,
            shape = "user-ask-phone-response",
            text = phone,
            type = nodeType
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for ask-number-node
 * Asks user for a number
 */
class AskNumberNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ASK_NUMBER

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val questionText = getString(nodeData, "questionText", "Please enter a number")
        val answerKey = getString(nodeData, "answerVariable", "number")

        state.addToTranscript("bot", questionText)
        state.addAnswerVariable(nodeId, answerKey)

        return NodeResult.DisplayUI(
            NodeUIState.TextInput(
                questionText = questionText,
                inputType = NodeUIState.TextInput.InputType.NUMBER,
                placeholder = "Enter a number",
                errorMessage = "Please enter a valid number",
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
        val value = response.toString().trim()

        if (!isValidNumber(value)) {
            return NodeResult.Error("Please enter a valid number", shouldProceed = false)
        }

        val number = value.toDoubleOrNull() ?: 0.0

        state.setAnswerVariable(nodeId, number)
        state.addToTranscript("user", value)

        recordResponse(
            nodeId = nodeId,
            shape = "user-ask-number-response",
            text = value,
            type = nodeType
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for ask-url-node
 * Asks user for a URL
 */
class AskUrlNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ASK_URL

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val questionText = getString(nodeData, "questionText", "Please enter a URL")
        val answerKey = getString(nodeData, "answerVariable", "url")

        state.addToTranscript("bot", questionText)
        state.addAnswerVariable(nodeId, answerKey)

        return NodeResult.DisplayUI(
            NodeUIState.TextInput(
                questionText = questionText,
                inputType = NodeUIState.TextInput.InputType.URL,
                placeholder = "Enter a URL",
                errorMessage = "Please enter a valid URL",
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
        val url = response.toString().trim()

        if (!isValidUrl(url)) {
            return NodeResult.Error("Please enter a valid URL", shouldProceed = false)
        }

        state.setAnswerVariable(nodeId, url)
        state.addToTranscript("user", url)

        recordResponse(
            nodeId = nodeId,
            shape = "user-ask-url-response",
            text = url,
            type = nodeType
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for ask-location-node
 * Asks user for a location
 */
class AskLocationNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ASK_LOCATION

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val questionText = getString(nodeData, "questionText", "What is your location?")
        val answerKey = getString(nodeData, "answerVariable", "location")

        state.addToTranscript("bot", questionText)
        state.addAnswerVariable(nodeId, answerKey)

        return NodeResult.DisplayUI(
            NodeUIState.TextInput(
                questionText = questionText,
                inputType = NodeUIState.TextInput.InputType.LOCATION,
                placeholder = "Enter your location",
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
        val location = response.toString().trim()

        if (location.isEmpty()) {
            return NodeResult.Error("Please enter a location", shouldProceed = false)
        }

        state.setAnswerVariable(nodeId, location)
        state.addToTranscript("user", location)

        recordResponse(
            nodeId = nodeId,
            shape = "user-ask-location-response",
            text = location,
            type = nodeType
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for ask-custom-question-node
 * Asks a custom question
 */
class AskCustomNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ASK_CUSTOM

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val questionText = getString(nodeData, "questionText", "Please answer the question")
        val answerKey = getString(nodeData, "answerVariable", nodeId)

        state.addToTranscript("bot", questionText)
        state.addAnswerVariable(nodeId, answerKey)

        return NodeResult.DisplayUI(
            NodeUIState.TextInput(
                questionText = questionText,
                inputType = NodeUIState.TextInput.InputType.TEXT,
                placeholder = "Type your answer",
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
        val answer = response.toString().trim()

        if (answer.isEmpty()) {
            return NodeResult.Error("Please enter an answer", shouldProceed = false)
        }

        state.setAnswerVariable(nodeId, answer)
        state.addToTranscript("user", answer)

        recordResponse(
            nodeId = nodeId,
            shape = "user-ask-custom-response",
            text = answer,
            type = nodeType
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for ask-file-node
 * Asks user to upload a file
 */
class AskFileNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ASK_FILE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val questionText = getString(nodeData, "questionText", "Please upload a file")
        val answerKey = getString(nodeData, "answerVariable", "file")
        val maxSizeMb = getInt(nodeData, "maxSize", 5)

        state.addToTranscript("bot", questionText)
        state.addAnswerVariable(nodeId, answerKey)

        return NodeResult.DisplayUI(
            NodeUIState.FileUpload(
                questionText = questionText,
                maxSizeMb = maxSizeMb,
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
        // Response should be a map with url and fileName
        val responseMap = when (response) {
            is Map<*, *> -> response as Map<String, Any?>
            else -> mapOf("url" to response.toString())
        }

        val fileUrl = responseMap["url"]?.toString() ?: return NodeResult.Error("Invalid file upload", shouldProceed = false)
        val fileName = responseMap["fileName"]?.toString() ?: "uploaded_file"

        state.setAnswerVariable(nodeId, fileUrl)
        state.addToTranscript("user", "[File: $fileName]")

        recordResponse(
            nodeId = nodeId,
            shape = "user-ask-file-response",
            text = fileName,
            type = nodeType,
            additionalData = mapOf("url" to fileUrl, "fileName" to fileName)
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for ask-multiple-questions-node
 * Asks multiple questions in sequence
 */
class AskMultipleQuestionsNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ASK_MULTIPLE

    // Track current question index per node
    private val questionIndices = mutableMapOf<String, Int>()

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val questions = getList<Map<String, Any?>>(nodeData, "questions")

        if (questions.isEmpty()) {
            return NodeResult.Proceed()
        }

        // Initialize question index for this node
        if (!questionIndices.containsKey(nodeId)) {
            questionIndices[nodeId] = 0
        }

        return displayCurrentQuestion(nodeData, nodeId, questions)
    }

    private fun displayCurrentQuestion(
        nodeData: Map<String, Any?>,
        nodeId: String,
        questions: List<Map<String, Any?>>
    ): NodeResult {
        val currentIndex = questionIndices[nodeId] ?: 0

        if (currentIndex >= questions.size) {
            questionIndices.remove(nodeId)
            return NodeResult.Proceed()
        }

        val question = questions[currentIndex]
        val questionText = question["questionText"]?.toString() ?: "Please answer"
        val answerType = question["answerVariable"]?.toString() ?: "text"

        state.addToTranscript("bot", questionText)

        val answerKey = "${nodeId}_q${currentIndex}"
        state.addAnswerVariable(nodeId, answerKey)

        val inputType = when (answerType.lowercase()) {
            "name" -> NodeUIState.TextInput.InputType.NAME
            "email" -> NodeUIState.TextInput.InputType.EMAIL
            "phone", "mobile" -> NodeUIState.TextInput.InputType.PHONE
            else -> NodeUIState.TextInput.InputType.TEXT
        }

        return NodeResult.DisplayUI(
            NodeUIState.MultipleQuestions(
                questions = questions.mapIndexed { i, q ->
                    NodeUIState.MultipleQuestions.Question(
                        questionText = q["questionText"]?.toString() ?: "",
                        answerType = q["answerVariable"]?.toString() ?: "text",
                        answerKey = "${nodeId}_q${i}"
                    )
                },
                currentIndex = currentIndex,
                nodeId = nodeId
            )
        )
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val questions = getList<Map<String, Any?>>(nodeData, "questions")
        val currentIndex = questionIndices[nodeId] ?: 0

        if (currentIndex >= questions.size) {
            questionIndices.remove(nodeId)
            return NodeResult.Proceed()
        }

        val question = questions[currentIndex]
        val answerType = question["answerVariable"]?.toString() ?: "text"
        val value = response.toString().trim()

        // Validate based on answer type
        when (answerType.lowercase()) {
            "email" -> {
                if (!isValidEmail(value)) {
                    val errorMsg = question["incorrectEmailResponse"]?.toString()
                        ?: nodeData["incorrectEmailResponse"]?.toString()
                        ?: "Please enter a valid email"
                    return NodeResult.Error(errorMsg, shouldProceed = false)
                }
                state.setUserMetadata("email", value)
            }
            "phone", "mobile" -> {
                if (!isValidPhone(value)) {
                    val errorMsg = question["incorrectPhoneNumberResponse"]?.toString()
                        ?: nodeData["incorrectPhoneNumberResponse"]?.toString()
                        ?: "Please enter a valid phone number"
                    return NodeResult.Error(errorMsg, shouldProceed = false)
                }
                state.setUserMetadata("phone", value)
            }
            "name" -> {
                if (value.isEmpty()) {
                    return NodeResult.Error("Please enter your name", shouldProceed = false)
                }
                state.setUserMetadata("name", value)
            }
        }

        // Store answer
        val answerKey = "${nodeId}_q${currentIndex}"
        state.setAnswerVariable(nodeId, value)
        state.setAnswerVariableByKey(answerKey, value)
        state.addToTranscript("user", value)

        recordResponse(
            nodeId = nodeId,
            shape = "user-multiple-questions-response",
            text = value,
            type = nodeType,
            additionalData = mapOf(
                "questionIndex" to currentIndex,
                "answerType" to answerType
            )
        )

        // Move to next question
        questionIndices[nodeId] = currentIndex + 1

        // Check if more questions
        if (currentIndex + 1 < questions.size) {
            return displayCurrentQuestion(nodeData, nodeId, questions)
        }

        // All questions answered
        questionIndices.remove(nodeId)
        return NodeResult.Proceed()
    }
}

/**
 * Handler for calendar-node
 * Displays date/time picker
 */
class CalendarNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.CALENDAR

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val questionText = nodeData["questionText"]?.toString()
        val showTimeSelection = getBoolean(nodeData, "showTimeSelection", false)
        val timezone = nodeData["botTimeZone"]?.toString()
            ?: nodeData["timezone"]?.toString()
        val answerKey = getString(nodeData, "answerVariable", "calendar_selection")

        if (questionText != null) {
            state.addToTranscript("bot", questionText)
        }
        state.addAnswerVariable(nodeId, answerKey)

        return NodeResult.DisplayUI(
            NodeUIState.Calendar(
                questionText = questionText,
                showTimeSelection = showTimeSelection,
                timezone = timezone,
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
        // Response should be a map with date/time info
        val responseMap = when (response) {
            is Map<*, *> -> response as Map<String, Any?>
            else -> mapOf("date" to response.toString())
        }

        val date = responseMap["date"]?.toString() ?: ""
        val time = responseMap["time"]?.toString()
        val showTimeSelection = getBoolean(nodeData, "showTimeSelection", false)

        val displayText = if (showTimeSelection && time != null) {
            "$date at $time"
        } else {
            date
        }

        state.setAnswerVariable(nodeId, displayText)
        state.addToTranscript("user", displayText)

        recordResponse(
            nodeId = nodeId,
            shape = "user-calendar-selection",
            text = displayText,
            type = nodeType,
            additionalData = mapOf(
                "date" to date,
                "time" to time,
                "botTimeZone" to (nodeData["botTimeZone"] ?: nodeData["timezone"]),
                "visitorTimeZone" to java.util.TimeZone.getDefault().id
            )
        )

        return NodeResult.DelayedProceed(delayMs = 400)
    }
}
