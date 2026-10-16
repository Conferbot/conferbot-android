package com.conferbot.sdk.ui.compose.steps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.core.flow.FlowRich
import com.conferbot.sdk.core.flow.FlowStepAnswer
import com.conferbot.sdk.core.flow.Js
import com.conferbot.sdk.core.nodes.NodeUIState

private val MD_LINK = Regex("\\[([^\\]\\n]{1,200})]\\(((?:[^()\\s]|\\([^()\\s]*\\)){1,500})\\)")
private val MD_BOLD = Regex("\\*\\*([^*\\n]+)\\*\\*")

/**
 * The consent text as the web draws it (markdown-lite): [label](url) links
 * that open in the browser (only safe ones), **bold**; the rest as typed.
 */
internal fun consentText(md: String, style: StepLook): AnnotatedString = buildAnnotatedString {
    var at = 0
    val tokens = (MD_LINK.findAll(md).map { it to true } + MD_BOLD.findAll(md).map { it to false }).sortedBy { it.first.range.first }
    for ((m, link) in tokens) {
        if (m.range.first < at) continue
        append(md.substring(at, m.range.first))
        val label = m.groupValues[1]
        val url = if (link) FlowRich.safeLink(m.groupValues[2]) else ""
        when {
            link && url.isNotEmpty() -> {
                pushStringAnnotation("url", url)
                withStyle(SpanStyle(color = style.accent, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium)) { append(label) }
                pop()
            }
            link -> append(label)
            else -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(label) }
        }
        at = m.range.last + 1
    }
    append(md.substring(at))
}

/**
 * consent-node's card (web richNodesView consentCardHtml): the text with
 * its links, a box to tick and Continue. Sends {agreed}; a required consent
 * not ticked is refused and the card says so.
 */
@Composable
fun ConsentStep(step: NodeUIState.StepInfo, style: StepLook, busy: Boolean, send: (Any) -> Unit) {
    val context = LocalContext.current
    var agreed by remember(step.visit) { mutableStateOf(false) }
    val md = Js.str(step.node["consentText"]).trim()
    val required = FlowRich.consentRequired(step.node)
    StepCard(style) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Checkbox(
                checked = agreed,
                onCheckedChange = { agreed = it },
                enabled = !busy,
                colors = CheckboxDefaults.colors(checkedColor = style.accent, checkmarkColor = style.ink, uncheckedColor = style.text.copy(alpha = 0.4f)),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(8.dp))
            val text = consentText(md.ifEmpty { "I agree" }, style)
            ClickableText(
                text = text,
                style = style.body,
                modifier = Modifier.weight(1f).padding(top = 4.dp),
                onClick = { offset ->
                    val link = text.getStringAnnotations("url", offset, offset).firstOrNull()
                    if (link != null) openStepLink(context, link.item) else if (!busy) agreed = !agreed
                }
            )
        }
        StepStatus(style, step.error, if (required) null else FlowRich.t("common.optional").trim('(', ')').replaceFirstChar { it.uppercase() })
        StepActions {
            StepButton(FlowRich.consentContinueLabel(step.node), style, primary = true, enabled = !busy) { send(FlowStepAnswer.consent(agreed)) }
        }
    }
}
