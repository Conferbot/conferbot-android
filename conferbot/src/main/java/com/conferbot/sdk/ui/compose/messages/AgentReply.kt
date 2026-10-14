@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.conferbot.sdk.ui.compose.messages

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.agent.AgentBotParser
import com.conferbot.sdk.core.agent.ActionRequest
import com.conferbot.sdk.core.agent.AnswerCard
import com.conferbot.sdk.core.agent.ResultCard
import com.conferbot.sdk.core.agent.RichEntry
import com.conferbot.sdk.models.RecordItem
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient
import com.conferbot.sdk.utils.ChatMarkdown

/**
 * What an AI agent's reply draws, as on the web widget: its text formatted
 * (lists, bold, links), the sources it was grounded in, the pages it points
 * to, a facts or result card, an action to confirm, and "Helpful?". Also
 * the agent's rich messages and the one-tap question chips.
 */

private fun openUrl(context: android.content.Context, url: String?) {
    if (url.isNullOrBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** An agent message's text: the markdown subset of utils/ChatMarkdown.kt. */
@Composable
fun ChatMarkdownText(text: String, color: Color, linkColor: Color, style: TextStyle) {
    val context = LocalContext.current
    val blocks = remember(text) { ChatMarkdown.parse(text) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            val annotated = buildAnnotatedString {
                block.inlines.forEach { inline ->
                    when (inline) {
                        is ChatMarkdown.Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inline.text) }
                        is ChatMarkdown.Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline.text) }
                        is ChatMarkdown.Inline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(inline.text) }
                        is ChatMarkdown.Inline.Link -> {
                            pushStringAnnotation("url", inline.url)
                            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(inline.text) }
                            pop()
                        }
                        is ChatMarkdown.Inline.Plain -> append(inline.text)
                    }
                }
            }
            val textStyle = style.copy(color = color)
            val onClick: (Int) -> Unit = { offset ->
                annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let { openUrl(context, it.item) }
            }
            when (block) {
                is ChatMarkdown.Block.Paragraph -> ClickableText(annotated, style = textStyle, onClick = onClick)
                else -> Row {
                    Text(
                        text = if (block is ChatMarkdown.Block.Numbered) "${block.n}." else "•",
                        style = textStyle,
                        modifier = Modifier.widthIn(min = 16.dp).padding(end = 4.dp)
                    )
                    ClickableText(annotated, style = textStyle, onClick = onClick, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CardSurface(content: @Composable ColumnScope.() -> Unit) {
    val colors = ConferbotThemeAmbient.current.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(12.dp))
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun Rows(rows: List<Pair<String, String>>) {
    val theme = ConferbotThemeAmbient.current
    rows.forEachIndexed { i, (k, v) ->
        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(theme.colors.divider))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(k, color = theme.colors.onSurfaceVariant, style = TextStyle(fontSize = theme.typography.captionSize), modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(12.dp))
            Text(v, color = theme.colors.onSurface, style = TextStyle(fontSize = theme.typography.captionSize, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun Pill(label: String, outlined: Color, textColor: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(BorderStroke(1.dp, outlined), RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, color = textColor, style = TextStyle(fontSize = ConferbotThemeAmbient.current.typography.captionSize, fontWeight = FontWeight.Medium))
    }
}

/** Under an AI reply: sources, buttons, cards, an action to confirm, "Helpful?". */
@Composable
fun AgentReplyExtras(message: RecordItem.AgentMessage, modifier: Modifier = Modifier) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val context = LocalContext.current
    val extras = message.extras
    val customizations by Conferbot.serverCustomizationMap.collectAsState()
    val showHelpful = message.agentDetails.isAIAgent && extras?.isFallback != true && !message.deleted &&
        AgentBotParser.canRate(message.id) && AgentBotParser.answerFeedbackOn(customizations)
    if ((extras == null || extras.isEmpty) && !showHelpful) return

    Column(
        modifier = modifier.fillMaxWidth().padding(start = theme.spacing.avatarSize + 10.dp).widthIn(max = theme.spacing.maxBubbleWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        extras?.sources?.forEach { source ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(8.dp))
                    .clickable(enabled = source.url != null) { openUrl(context, source.url) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    source.title,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = theme.typography.captionSize, fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (source.url != null) {
                    Spacer(Modifier.width(6.dp))
                    Text("${AgentBotParser.sourceHost(source.url)} ↗", color = colors.onSurfaceVariant, style = TextStyle(fontSize = theme.typography.timestampSize))
                }
            }
        }
        extras?.card?.let { FactsCard(it) }
        extras?.resultCard?.let { ResultCardView(it) }
        extras?.actionRequest?.let { ActionCard(it) }
        if (!extras?.actions.isNullOrEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                extras!!.actions.forEach { a ->
                    Pill("${a.label} ↗", colors.primary, colors.primary, onClick = {
                        Conferbot.trackGoal("answer-action:${a.label}", AgentBotParser.conversionEventOf(a.label, a.url))
                        openUrl(context, a.url)
                    })
                }
            }
        }
        if (showHelpful) Helpful(message)
    }
}

@Composable
private fun FactsCard(card: AnswerCard) {
    val theme = ConferbotThemeAmbient.current
    val context = LocalContext.current
    CardSurface {
        card.title?.let { Text(it, color = theme.colors.onSurface, style = TextStyle(fontSize = theme.typography.messageSize, fontWeight = FontWeight.SemiBold)) }
        Rows(card.rows)
        card.note?.let { Text(it, color = theme.colors.onSurfaceVariant, style = TextStyle(fontSize = theme.typography.timestampSize)) }
        card.url?.let { url ->
            Text("Learn more ↗", color = theme.colors.primary, style = TextStyle(fontSize = theme.typography.captionSize, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 8.dp).clickable { openUrl(context, url) })
        }
    }
}

@Composable
private fun ResultCardView(card: ResultCard) {
    val theme = ConferbotThemeAmbient.current
    val context = LocalContext.current
    val tone = when (card.tone) {
        "done" -> Color(0xFF15803D)
        "moving" -> Color(0xFF1D4ED8)
        "problem" -> theme.colors.error
        else -> theme.colors.onSurfaceVariant
    }
    CardSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.title, color = theme.colors.onSurface, style = TextStyle(fontSize = theme.typography.messageSize, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            card.status?.let {
                Text(it, color = tone, style = TextStyle(fontSize = theme.typography.timestampSize, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.border(BorderStroke(1.dp, tone), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        Rows(card.rows)
        if (card.linkUrl != null) {
            Text("${card.linkLabel} ↗", color = theme.colors.primary, style = TextStyle(fontSize = theme.typography.captionSize, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 8.dp).clickable { openUrl(context, card.linkUrl) })
        }
    }
}

@Composable
private fun ActionCard(request: ActionRequest) {
    val theme = ConferbotThemeAmbient.current
    var state by remember(request.token) { mutableStateOf("ask") }
    CardSurface {
        Text(request.name, color = theme.colors.onSurface, style = TextStyle(fontSize = theme.typography.messageSize, fontWeight = FontWeight.SemiBold))
        Rows(request.summary.map { it.label to it.value })
        if (state == "ask" || state == "busy") {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fun answer(accept: Boolean) {
                    if (state != "ask") return
                    state = "busy"
                    Conferbot.decideAction(request.token, accept) { ok, code ->
                        state = if (ok) (if (accept) "done" else "declined") else if (code == "declined") "declined" else "failed"
                    }
                }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(theme.colors.primary)
                        .clickable(enabled = state == "ask") { answer(true) }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state == "busy") CircularProgressIndicator(Modifier.size(16.dp), color = theme.colors.onPrimary, strokeWidth = 2.dp)
                    else Text("Confirm", color = theme.colors.onPrimary, style = TextStyle(fontSize = theme.typography.captionSize, fontWeight = FontWeight.SemiBold))
                }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(BorderStroke(1.dp, theme.colors.divider), RoundedCornerShape(10.dp))
                        .clickable(enabled = state == "ask") { answer(false) }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Not now", color = theme.colors.onSurface, style = TextStyle(fontSize = theme.typography.captionSize))
                }
            }
        } else {
            Text(
                when (state) { "done" -> "Confirmed"; "declined" -> "Not now"; else -> "That did not go through. Please ask again." },
                color = theme.colors.onSurfaceVariant,
                style = TextStyle(fontSize = theme.typography.captionSize),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun Helpful(message: RecordItem.AgentMessage) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    var askWhy by remember(message.id) { mutableStateOf(false) }
    val label = TextStyle(fontSize = theme.typography.timestampSize, color = colors.onSurfaceVariant)
    val given = message.feedback
    when {
        given != null -> Text(if (given.helpful) "Thanks, glad it helped" else "Thanks for telling us", style = label)
        askWhy -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What was wrong?", style = label, modifier = Modifier.align(Alignment.CenterVertically))
            AgentBotParser.FEEDBACK_REASONS.forEach { (key, text) ->
                Pill(text, colors.divider, colors.onSurface, onClick = { Conferbot.sendAnswerFeedback(message.id, false, key) })
            }
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Helpful?", style = label)
            Pill("Yes", colors.divider, colors.onSurface, onClick = { Conferbot.sendAnswerFeedback(message.id, true) })
            Pill("No", colors.divider, colors.onSurface, onClick = { askWhy = true })
        }
    }
}

/** An agent's article, quick replies or card. */
@Composable
fun RichAgentMessageBubble(message: RecordItem.AgentRichMessage, modifier: Modifier = Modifier) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val context = LocalContext.current
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        BotAvatar()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = theme.spacing.maxBubbleWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (val rich = message.rich) {
                is RichEntry.QuickReplies -> {
                    val prompt = rich.text.ifBlank { message.text }
                    if (prompt.isNotBlank()) {
                        Text(prompt, color = colors.agentBubbleText, style = TextStyle(fontSize = theme.typography.messageSize),
                            modifier = Modifier.clip(RoundedCornerShape(theme.shapes.bubbleRadius)).background(colors.agentBubble).padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rich.options.forEach { option ->
                            val chosen = message.answered == option
                            Box(
                                Modifier.clip(RoundedCornerShape(50))
                                    .background(if (chosen) colors.primary else colors.surface)
                                    .border(BorderStroke(1.dp, colors.primary), RoundedCornerShape(50))
                                    .clickable(enabled = message.answered == null) { Conferbot.answerRichMessage(message.id, option) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(option, color = if (chosen) colors.onPrimary else colors.primary,
                                    style = TextStyle(fontSize = theme.typography.captionSize, fontWeight = FontWeight.Medium))
                            }
                        }
                    }
                }
                is RichEntry.Article -> CardSurface {
                    Text("Help article", color = colors.onSurfaceVariant, style = TextStyle(fontSize = theme.typography.timestampSize))
                    Text(rich.title, color = colors.onSurface, style = TextStyle(fontSize = theme.typography.messageSize, fontWeight = FontWeight.SemiBold))
                    if (rich.snippet.isNotBlank()) Text(rich.snippet, color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = theme.typography.captionSize))
                    rich.url?.let { url ->
                        Text("Read article ↗", color = colors.primary, style = TextStyle(fontSize = theme.typography.captionSize, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(top = 8.dp).clickable { openUrl(context, url) })
                    }
                }
                is RichEntry.Card -> CardSurface {
                    rich.imageUrl?.let {
                        AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(rich.title, color = colors.onSurface, style = TextStyle(fontSize = theme.typography.messageSize, fontWeight = FontWeight.SemiBold))
                    if (rich.description.isNotBlank()) Text(rich.description, color = colors.onSurfaceVariant, style = TextStyle(fontSize = theme.typography.captionSize))
                    if (rich.buttonUrl != null) {
                        Box(
                            Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.primary)
                                .clickable { openUrl(context, rich.buttonUrl) }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(rich.buttonLabel ?: "", color = colors.onPrimary, style = TextStyle(fontSize = theme.typography.captionSize, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

/** One-tap questions: the agent's starters, then each answer's follow-ups. */
@Composable
fun SuggestionChips(items: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val theme = ConferbotThemeAmbient.current
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { q ->
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(theme.colors.surface)
                    .border(BorderStroke(1.dp, theme.colors.primary), RoundedCornerShape(50))
                    .clickable { onPick(q) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(q, color = theme.colors.primary, style = TextStyle(fontSize = theme.typography.captionSize, fontWeight = FontWeight.Medium))
            }
        }
    }
}
