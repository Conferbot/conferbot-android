package com.conferbot.sdk.core.nodes.handlers

import android.util.Log
import com.conferbot.sdk.core.nodes.*

/**
 * Handler for welcome-node
 * Displays the bot's welcome message with optional image/gif
 */
class WelcomeNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.WELCOME

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val rawText = stripHtml(getString(nodeData, "text"))
        // Resolve variable placeholders ({{var}}, ${var}, {var})
        val text = state.resolveVariables(rawText)
        val image = nodeData["image"]?.toString()
        val disableImage = nodeData["disableImage"] as? Boolean ?: false

        // Add welcome text to transcript
        state.addToTranscript("bot", text)

        // Record the response
        recordResponse(
            nodeId = nodeId,
            shape = "bot-message",
            text = text,
            type = nodeType
        )

        // If there's an image and it's not disabled, show as image node
        if (!disableImage && !image.isNullOrEmpty()) {
            return NodeResult.DisplayUI(
                NodeUIState.Image(
                    url = image,
                    caption = text,
                    nodeId = nodeId
                )
            )
        }

        // Otherwise show as text message
        return NodeResult.DisplayUI(
            NodeUIState.Message(
                text = text,
                nodeId = nodeId
            )
        )
    }
}

/**
 * Handler for message-node
 * Displays a text message from the bot
 */
class MessageNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.MESSAGE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val rawText = stripHtml(getString(nodeData, "text"))
        val rawMessage = stripHtml(getString(nodeData, "message", rawText))
        // Resolve variable placeholders ({{var}}, ${var}, {var})
        val message = state.resolveVariables(rawMessage)

        // Skip message-nodes that just echo the user's last choice selection
        val lastChoice = state.getVariable("_lastUserChoice")
        Log.d("ConferBot", "MessageHandler: text=$message, lastChoice=$lastChoice")
        if (lastChoice != null && message.trim() == lastChoice.toString().trim()) {
            state.setVariable("_lastUserChoice", null)
            return NodeResult.Proceed()
        }

        // Add to transcript
        state.addToTranscript("bot", message)

        // Record the message
        recordResponse(
            nodeId = nodeId,
            shape = "bot-message",
            text = message,
            type = nodeType
        )

        // Return UI state for display
        return NodeResult.DisplayUI(
            NodeUIState.Message(
                text = message,
                nodeId = nodeId
            )
        )
    }
}

/**
 * Handler for image-node
 * Displays an image
 */
class ImageNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.IMAGE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val imageUrl = getString(nodeData, "image")
        val caption = nodeData["caption"]?.toString()

        // Add to transcript
        state.addToTranscript("bot", caption ?: "[Image]")

        // Record
        recordResponse(
            nodeId = nodeId,
            shape = "bot-image",
            text = caption,
            type = nodeType,
            additionalData = mapOf("imageUrl" to imageUrl)
        )

        return NodeResult.DisplayUI(
            NodeUIState.Image(
                url = imageUrl,
                caption = caption,
                nodeId = nodeId
            )
        )
    }
}

/**
 * Handler for video-node
 * Displays a video player
 */
class VideoNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.VIDEO

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val videoUrl = getString(nodeData, "video")
        val caption = nodeData["caption"]?.toString()

        state.addToTranscript("bot", caption ?: "[Video]")

        recordResponse(
            nodeId = nodeId,
            shape = "bot-video",
            text = caption,
            type = nodeType,
            additionalData = mapOf("videoUrl" to videoUrl)
        )

        return NodeResult.DisplayUI(
            NodeUIState.Video(
                url = videoUrl,
                caption = caption,
                nodeId = nodeId
            )
        )
    }
}

/**
 * Handler for audio-node
 * Displays an audio player
 */
class AudioNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.AUDIO

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val audioUrl = getString(nodeData, "audio")
        val caption = nodeData["caption"]?.toString()

        state.addToTranscript("bot", caption ?: "[Audio]")

        recordResponse(
            nodeId = nodeId,
            shape = "bot-audio",
            text = caption,
            type = nodeType,
            additionalData = mapOf("audioUrl" to audioUrl)
        )

        return NodeResult.DisplayUI(
            NodeUIState.Audio(
                url = audioUrl,
                caption = caption,
                nodeId = nodeId
            )
        )
    }
}

/**
 * Handler for file-node
 * Displays a file download
 */
class FileNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.FILE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val fileUrl = getString(nodeData, "file")
        val fileName = getString(nodeData, "fileName", "Download File")

        state.addToTranscript("bot", "[File: $fileName]")

        recordResponse(
            nodeId = nodeId,
            shape = "bot-file",
            text = fileName,
            type = nodeType,
            additionalData = mapOf("fileUrl" to fileUrl, "fileName" to fileName)
        )

        return NodeResult.DisplayUI(
            NodeUIState.File(
                url = fileUrl,
                fileName = fileName,
                nodeId = nodeId
            )
        )
    }
}

/**
 * Handler for html-node
 * Displays HTML content
 */
class HtmlNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.HTML

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val htmlContent = getString(nodeData, "html")

        state.addToTranscript("bot", "[HTML Content]")

        recordResponse(
            nodeId = nodeId,
            shape = "bot-html",
            text = null,
            type = nodeType,
            additionalData = mapOf("html" to htmlContent)
        )

        return NodeResult.DisplayUI(
            NodeUIState.Html(
                htmlContent = htmlContent,
                nodeId = nodeId
            )
        )
    }
}

/**
 * Handler for user-redirect-node
 * Redirects user to external URL
 */
class UserRedirectNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.USER_REDIRECT

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val url = getString(nodeData, "url")
        val openInNewTab = getBoolean(nodeData, "openInNewTab", true)

        state.addToTranscript("bot", "[Redirect to: $url]")

        recordResponse(
            nodeId = nodeId,
            shape = "bot-redirect",
            text = url,
            type = nodeType
        )

        return NodeResult.DisplayUI(
            NodeUIState.Redirect(
                url = url,
                openInNewTab = openInNewTab,
                nodeId = nodeId
            )
        )
    }
}

/**
 * Handler for navigate-node
 *
 * In the web widget, navigate-node auto-proceeds to the next node after a
 * short delay (it does NOT open a browser). Mirror that behaviour here by
 * returning a DelayedProceed instead of a Redirect.
 */
class NavigateNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.NAVIGATE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val url = getString(nodeData, "url")
        val navigatePrompt = getString(nodeData, "navigatePrompt")

        if (navigatePrompt.isNotEmpty()) {
            state.addToTranscript("bot", navigatePrompt)
        }

        recordResponse(
            nodeId = nodeId,
            shape = "bot-navigate",
            text = url,
            type = nodeType
        )

        return NodeResult.DelayedProceed(delayMs = 600)
    }
}
