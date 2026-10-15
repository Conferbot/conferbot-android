package com.conferbot.sdk.core.nodes.handlers

import com.conferbot.sdk.core.flow.FlowInputs
import com.conferbot.sdk.core.flow.FlowText
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.nodes.*
import com.conferbot.sdk.core.state.ChatState

/**
 * The steps that show something and move on: welcome, message, image,
 * video, audio, file, html, user-redirect - and navigate, which waits for
 * Done. Field names and behaviour follow the flow-node spec
 * (conferbot-react-native scripts/parity/flows/nodes.spec.json) and the web
 * widget's ts/chat/displayStep.ts; ported from RN DisplayNodeHandlers.ts.
 * The engine paces the typing dots and moves on (images and videos when
 * their media settles, at 4000 ms at most).
 */

/** Where a redirect / navigate link goes: answers filled in, a same-site path kept (web redirectHref). */
internal fun redirectHref(link: Any?): String {
    val filled = FlowText.fillLinkVariables(link) { ChatState.lookupVariable(it) }
    return FlowInputs.linkHref(filled).ifEmpty { if (Regex("^[/#?](?!/)").containsMatchIn(filled)) filled else "" }
}

/** A link label: tags stripped, the answers filled in as they are. */
internal fun linkLabel(html: Any?): String =
    FlowText.fillVariables(FlowText.stripHtmlAndDecode(html), { ChatState.lookupVariable(it) }, raw = true).trim()

/** welcome-node: data.text, then data.image unless disableImage. */
class WelcomeNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.WELCOME

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val text = FlowKit.shownText(nodeData["text"])
        FlowKit.botSay(text)
        val image = (nodeData["image"] as? String)?.trim().orEmpty()
        if (image.isNotEmpty() && !Js.truthy(nodeData["disableImage"])) ChatState.addToTranscript("bot", image)
        return NodeResult.DisplayUI(NodeUIState.Message(text = text, nodeId = nodeId))
    }
}

/** message-node: data.text, filled from the answers. Blank text is skipped by the engine. */
class MessageNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.MESSAGE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val text = FlowKit.shownText(nodeData["text"])
        FlowKit.botSay(text)
        return NodeResult.DisplayUI(NodeUIState.Message(text = text, nodeId = nodeId))
    }
}

/** image-node: data.image (not filled). Moves on when it loads, or at the cap. */
class ImageNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.IMAGE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val url = Js.str(nodeData["image"]).trim()
        ChatState.addToTranscript("bot", url)
        return NodeResult.DisplayUI(NodeUIState.Image(url = url, nodeId = nodeId))
    }
}

/** video-node: data.video (an mp4). */
class VideoNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.VIDEO

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val url = (nodeData["video"] as? String)?.trim().orEmpty()
        FlowKit.botSay(url)
        return NodeResult.DisplayUI(NodeUIState.Video(url = url, nodeId = nodeId))
    }
}

/**
 * audio-node: data.audio. Some live nodes saved a list there, which the web
 * does not read (a broken player) - the step still moves on.
 */
class AudioNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.AUDIO

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val url = (nodeData["audio"] as? String)?.trim().orEmpty()
        FlowKit.botSay(url)
        return NodeResult.DisplayUI(NodeUIState.Audio(url = url, nodeId = nodeId))
    }
}

/** file-node: a download card for data.file, named from the URL. */
class FileNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.FILE

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val url = (nodeData["file"] as? String)?.trim().orEmpty()
        val name = if (url.isEmpty()) "download" else
            Js.decodeURIComponent(url.split(Regex("[?#]"))[0].substringAfterLast('/')).ifEmpty { "download" }
        FlowKit.botSay(url)
        return NodeResult.DisplayUI(NodeUIState.File(url = url, fileName = name, nodeId = nodeId))
    }
}

/** html-node: data.html, answers filled (escaped), then sanitised. */
class HtmlNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.HTML

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val content = FlowText.sanitizeOwnerHtml(FlowText.fillVariables(nodeData["html"] ?: "", { ChatState.lookupVariable(it) }))
        FlowKit.botSay(FlowKit.shownText(content))
        return NodeResult.DisplayUI(NodeUIState.Html(htmlContent = content, nodeId = nodeId))
    }
}

/**
 * user-redirect-node: a link card - redirectPrompt as its label, data.link
 * as where it goes. It never navigates by itself; the flow moves on.
 */
class UserRedirectNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.USER_REDIRECT

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val href = redirectHref(nodeData["link"])
        val label = linkLabel(nodeData["redirectPrompt"] ?: "")
        val text = if (href.isNotEmpty()) "$label\n$href".trim() else label
        FlowKit.botSay(text)
        return NodeResult.DisplayUI(NodeUIState.Message(text = text, nodeId = nodeId))
    }
}

/**
 * navigate-node: the prompt, one button per navigateOptions link (blank ones
 * hidden) and Done. A link opens its page and does not move on; Done moves
 * on after 600 ms. Answers: {action: "link", index} or the "link-<i>"
 * button; {action: "done"} or the "done" button.
 */
class NavigateNodeHandler : BaseNodeHandler() {
    override val nodeType = NodeTypes.NAVIGATE

    private fun options(data: Map<String, Any?>) = Js.list(data["navigateOptions"]).mapNotNull { Js.map(it) }
        .filter { Js.str(it["navigateText"]).trim().isNotEmpty() }

    private fun doneLabel(data: Map<String, Any?>) = Js.str(data["doneNavigatingText"]).trim().ifEmpty { "Done" }

    private fun ui(data: Map<String, Any?>, nodeId: String): NodeUIState {
        val choices = options(data).mapIndexed { i, o ->
            NodeUIState.SingleChoice.Choice(id = "link-$i", text = linkLabel(o["navigateText"]), targetPort = redirectHref(o["navigateLink"]))
        } + NodeUIState.SingleChoice.Choice(id = "done", text = doneLabel(data))
        return NodeUIState.SingleChoice(FlowKit.shownText(data["navigatePrompt"]), choices, nodeId, "")
    }

    override suspend fun process(nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val prompt = FlowKit.shownText(nodeData["navigatePrompt"])
        FlowKit.botSay(prompt)
        return NodeResult.DisplayUI(ui(nodeData, nodeId))
    }

    override suspend fun handleResponse(response: Any, nodeData: Map<String, Any?>, nodeId: String): NodeResult {
        val r = FlowKit.asMap(response)
        val id = FlowKit.pickedId(response) ?: ""
        if (r["action"] == "link" || id.startsWith("link-")) {
            val index = if (r["action"] == "link") Js.number(r["index"]).toInt() else id.removePrefix("link-").toIntOrNull() ?: -1
            options(nodeData).getOrNull(index)?.let { o ->
                FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-navigate-link-click", "text" to linkLabel(o["navigateText"]), "url" to redirectHref(o["navigateLink"])))
            }
            // A link opens its page; the step keeps waiting for Done.
            return NodeResult.DisplayUI(ui(nodeData, nodeId))
        }
        val text = doneLabel(nodeData)
        FlowKit.pushAnswer(mapOf("id" to nodeId, "shape" to "user-navigate-done", "text" to text))
        FlowKit.userSay(text)
        return NodeResult.DelayedProceed(600)
    }
}
