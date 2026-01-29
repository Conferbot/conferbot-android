package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.nodes.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handler for webhook-node
 * Makes HTTP requests to external APIs
 */
class WebhookNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.WEBHOOK

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val url = getString(nodeData, "url", "")
        val method = getString(nodeData, "method", "POST").uppercase()
        val headers = getMap(nodeData, "headers")
        val body = nodeData["body"]
        val includeAnswerVariables = getBoolean(nodeData, "includeAnswerVariables", false)
        val answerVariable = nodeData["answerVariable"]?.toString()

        if (url.isEmpty()) {
            return NodeResult.Proceed()  // Skip if no URL
        }

        // Handle authentication if provided
        val auth = getMap(nodeData, "authentication")
        var authToken: String? = null
        if (auth.isNotEmpty()) {
            val tokenUrl = auth["tokenUrl"]?.toString() ?: ""
            val username = auth["username"]?.toString() ?: ""
            val password = auth["password"]?.toString() ?: ""

            if (tokenUrl.isNotEmpty() && username.isNotEmpty()) {
                authToken = authenticateAndGetToken(tokenUrl, username, password)
            }
        }

        try {
            // Build request body
            val requestBody = buildRequestBody(body, includeAnswerVariables)

            // Make the request
            val response = withContext(Dispatchers.IO) {
                makeHttpRequest(url, method, headers, authToken, requestBody)
            }

            // Store response if answer variable specified
            if (!answerVariable.isNullOrEmpty() && response != null) {
                state.setAnswerVariableByKey(answerVariable, response)
            }

        } catch (e: Exception) {
            // Log error but continue flow
            recordResponse(
                nodeId = nodeId,
                shape = "webhook-error",
                text = e.message,
                type = nodeType
            )
        }

        return NodeResult.Proceed()
    }

    private suspend fun authenticateAndGetToken(
        tokenUrl: String,
        username: String,
        password: String
    ): String? {
        return try {
            withContext(Dispatchers.IO) {
                val connection = URL(tokenUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val authBody = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(authBody.toString())
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    json.optString("access", json.optString("token"))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildRequestBody(body: Any?, includeAnswerVariables: Boolean): String? {
        val json = when (body) {
            is String -> {
                try {
                    JSONObject(body)
                } catch (e: Exception) {
                    JSONObject().put("data", body)
                }
            }
            is Map<*, *> -> JSONObject(body as Map<String, Any?>)
            else -> JSONObject()
        }

        if (includeAnswerVariables) {
            val vars = state.getAnswerVariablesMap()
            json.put("answerVariables", JSONObject(vars))
        }

        return json.toString()
    }

    private fun makeHttpRequest(
        url: String,
        method: String,
        headers: Map<String, Any?>,
        authToken: String?,
        body: String?
    ): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Content-Type", "application/json")

        // Add custom headers
        headers.forEach { (key, value) ->
            connection.setRequestProperty(key, value?.toString() ?: "")
        }

        // Add auth token if present
        if (authToken != null) {
            connection.setRequestProperty("Authorization", "Bearer $authToken")
        }

        // Write body for POST/PUT
        if (body != null && method in listOf("POST", "PUT", "PATCH")) {
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body)
            }
        }

        return if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().readText()
        } else {
            null
        }
    }
}

/**
 * Handler for gpt-node
 * Sends conversation context to OpenAI
 */
class GptNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.GPT

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val apiKey = getString(nodeData, "apiKey", "")
        val model = getString(nodeData, "selectedModel", "gpt-3.5-turbo")
        val context = nodeData["context"]?.toString()

        if (apiKey.isEmpty()) {
            return NodeResult.Proceed()  // Skip if no API key
        }

        try {
            // Build messages from transcript
            val messages = mutableListOf<Map<String, String>>()

            // Add system context if provided
            if (!context.isNullOrEmpty()) {
                messages.add(mapOf("role" to "system", "content" to context))
            }

            // Add conversation transcript
            messages.addAll(state.getTranscriptForGPT())

            // Call OpenAI API
            val response = withContext(Dispatchers.IO) {
                callOpenAI(apiKey, model, messages)
            }

            if (response != null) {
                // Add response to transcript
                state.addToTranscript("bot", response)

                // Record the response
                recordResponse(
                    nodeId = nodeId,
                    shape = "gpt-response",
                    text = response,
                    type = nodeType
                )

                // Display the message
                return NodeResult.DisplayUI(
                    NodeUIState.Message(
                        text = response,
                        nodeId = nodeId
                    )
                )
            }

        } catch (e: Exception) {
            recordResponse(
                nodeId = nodeId,
                shape = "gpt-error",
                text = e.message,
                type = nodeType
            )
        }

        return NodeResult.Proceed()
    }

    private fun callOpenAI(apiKey: String, model: String, messages: List<Map<String, String>>): String? {
        val url = URL("https://api.openai.com/v1/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.doOutput = true

        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray(messages.map { JSONObject(it) }))
        }

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(body.toString())
        }

        return if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } else {
            null
        }
    }
}

/**
 * Handler for email-node
 * Sends email (fire-and-forget via server)
 */
class EmailNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.EMAIL

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        // Email is handled server-side via socket event
        // Just record and proceed
        recordResponse(
            nodeId = nodeId,
            shape = "email-triggered",
            text = null,
            type = nodeType,
            additionalData = mapOf(
                "to" to nodeData["to"],
                "subject" to nodeData["subject"]
            )
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for zapier-node
 * Triggers Zapier webhook (fire-and-forget)
 */
class ZapierNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ZAPIER

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        // Zapier trigger is handled server-side
        recordResponse(
            nodeId = nodeId,
            shape = "zapier-triggered",
            text = null,
            type = nodeType,
            additionalData = mapOf("answerVariables" to state.getAnswerVariablesMap())
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for google-sheets-node
 * Reads/writes to Google Sheets
 */
class GoogleSheetsNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.GOOGLE_SHEETS

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "write")

        recordResponse(
            nodeId = nodeId,
            shape = "google-sheets-$operation",
            text = null,
            type = nodeType,
            additionalData = mapOf(
                "operation" to operation,
                "spreadsheetId" to nodeData["spreadsheetId"],
                "sheetName" to nodeData["sheetName"]
            )
        )

        // For read operations, column mappings should be handled via socket response
        return NodeResult.Proceed()
    }
}

/**
 * Handler for gmail-node
 * Sends email via Gmail
 */
class GmailNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.GMAIL

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        recordResponse(
            nodeId = nodeId,
            shape = "gmail-triggered",
            text = null,
            type = nodeType,
            additionalData = mapOf(
                "to" to nodeData["to"],
                "subject" to nodeData["subject"]
            )
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for google-calendar-node
 * Books calendar appointments
 */
class GoogleCalendarNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.GOOGLE_CALENDAR

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "book")

        if (operation == "book") {
            // Display calendar booking UI
            val timezone = getString(nodeData, "timeZone", java.util.TimeZone.getDefault().id)
            val collectEmail = getBoolean(nodeData, "collectAttendeeEmail", false)
            val answerKey = getString(nodeData, "answerVariable", "calendar_booking")

            state.addAnswerVariable(nodeId, answerKey)

            return NodeResult.DisplayUI(
                NodeUIState.Calendar(
                    questionText = "Select a date and time",
                    showTimeSelection = true,
                    timezone = timezone,
                    nodeId = nodeId,
                    answerKey = answerKey
                )
            )
        }

        return NodeResult.Proceed()
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val responseMap = when (response) {
            is Map<*, *> -> response as Map<String, Any?>
            else -> mapOf("date" to response.toString())
        }

        val date = responseMap["date"]?.toString() ?: ""
        val time = responseMap["time"]?.toString() ?: ""
        val email = responseMap["email"]?.toString()

        state.setAnswerVariable(nodeId, "$date $time")
        state.addToTranscript("user", "Booked: $date at $time")

        recordResponse(
            nodeId = nodeId,
            shape = "google-calendar-booking",
            text = "$date $time",
            type = nodeType,
            additionalData = mapOf(
                "date" to date,
                "time" to time,
                "attendeeEmail" to email,
                "timezone" to nodeData["timeZone"]
            )
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for google-meet-node
 * Creates Google Meet meetings
 */
class GoogleMeetNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.GOOGLE_MEET

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "book")

        if (operation == "book") {
            val timezone = getString(nodeData, "timeZone", java.util.TimeZone.getDefault().id)
            val answerKey = getString(nodeData, "answerVariable", "meet_booking")

            state.addAnswerVariable(nodeId, answerKey)

            return NodeResult.DisplayUI(
                NodeUIState.Calendar(
                    questionText = "Select a time for your meeting",
                    showTimeSelection = true,
                    timezone = timezone,
                    nodeId = nodeId,
                    answerKey = answerKey
                )
            )
        }

        return NodeResult.Proceed()
    }

    override suspend fun handleResponse(
        response: Any,
        nodeData: Map<String, Any?>,
        nodeId: String
    ): NodeResult {
        val responseMap = when (response) {
            is Map<*, *> -> response as Map<String, Any?>
            else -> mapOf("date" to response.toString())
        }

        recordResponse(
            nodeId = nodeId,
            shape = "google-meet-booking",
            text = null,
            type = nodeType,
            additionalData = responseMap.toMutableMap()
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for google-drive-node
 * Uploads/downloads from Google Drive
 */
class GoogleDriveNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.GOOGLE_DRIVE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "upload")

        recordResponse(
            nodeId = nodeId,
            shape = "google-drive-$operation",
            text = null,
            type = nodeType,
            additionalData = mapOf("operation" to operation)
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for google-docs-node
 * Creates/updates Google Docs
 */
class GoogleDocsNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.GOOGLE_DOCS

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "create")

        recordResponse(
            nodeId = nodeId,
            shape = "google-docs-$operation",
            text = null,
            type = nodeType,
            additionalData = mapOf(
                "operation" to operation,
                "title" to nodeData["title"]
            )
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for slack-node
 * Sends messages to Slack
 */
class SlackNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.SLACK

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        recordResponse(
            nodeId = nodeId,
            shape = "slack-message",
            text = nodeData["message"]?.toString(),
            type = nodeType,
            additionalData = mapOf("channel" to nodeData["channel"])
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for discord-node
 * Sends messages to Discord
 */
class DiscordNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.DISCORD

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        recordResponse(
            nodeId = nodeId,
            shape = "discord-message",
            text = nodeData["message"]?.toString(),
            type = nodeType,
            additionalData = mapOf("channel" to nodeData["channel"])
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for airtable-node
 * CRUD operations on Airtable
 */
class AirtableNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.AIRTABLE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "create")

        recordResponse(
            nodeId = nodeId,
            shape = "airtable-$operation",
            text = null,
            type = nodeType,
            additionalData = mapOf(
                "baseId" to nodeData["baseId"],
                "tableName" to nodeData["tableName"],
                "operation" to operation
            )
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for hubspot-node
 * Creates/updates HubSpot contacts
 */
class HubspotNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.HUBSPOT

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "createContact")

        recordResponse(
            nodeId = nodeId,
            shape = "hubspot-$operation",
            text = null,
            type = nodeType,
            additionalData = mapOf("operation" to operation)
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for notion-node
 * Creates/updates Notion pages
 */
class NotionNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.NOTION

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "createPage")

        recordResponse(
            nodeId = nodeId,
            shape = "notion-$operation",
            text = null,
            type = nodeType,
            additionalData = mapOf(
                "databaseId" to nodeData["databaseId"],
                "operation" to operation
            )
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for zohocrm-node
 * Creates/updates Zoho CRM records
 */
class ZohoCrmNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.ZOHO_CRM

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "create")
        val module = getString(nodeData, "module", "Contacts")

        recordResponse(
            nodeId = nodeId,
            shape = "zohocrm-$operation",
            text = null,
            type = nodeType,
            additionalData = mapOf(
                "module" to module,
                "operation" to operation
            )
        )

        return NodeResult.Proceed()
    }
}

/**
 * Handler for stripe-node
 * Creates payment links/sessions
 */
class StripeNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.STRIPE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val operation = getString(nodeData, "operation", "createPaymentLink")

        // For payment operations, display payment UI
        if (operation in listOf("createPaymentLink", "createCheckoutSession")) {
            val amount = nodeData["customAmount"]
            val currency = nodeData["currency"]?.toString() ?: "USD"
            val description = nodeData["description"]?.toString()

            recordResponse(
                nodeId = nodeId,
                shape = "stripe-payment",
                text = null,
                type = nodeType,
                additionalData = mapOf(
                    "operation" to operation,
                    "amount" to amount,
                    "currency" to currency
                )
            )

            // Payment URL will be provided by server via socket
            // For now, return a placeholder UI
            return NodeResult.DisplayUI(
                NodeUIState.Payment(
                    paymentUrl = "",  // Will be filled by server response
                    amount = when (amount) {
                        is Number -> amount.toDouble()
                        is String -> amount.toDoubleOrNull()
                        else -> null
                    },
                    currency = currency,
                    description = description,
                    nodeId = nodeId
                )
            )
        }

        return NodeResult.Proceed()
    }
}
