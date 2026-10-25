@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.conferbot.sdk.ui.compose.messages

import com.conferbot.sdk.core.i18n.I18n
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

private fun annotatedOf(inlines: List<ChatMarkdown.Inline>, linkColor: Color) = buildAnnotatedString {
    inlines.forEach { inline ->
        when (inline) {
            is ChatMarkdown.Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inline.text) }
            is ChatMarkdown.Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline.text) }
            is ChatMarkdown.Inline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(inline.text) }
            is ChatMarkdown.Inline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(inline.text) }
            is ChatMarkdown.Inline.Link -> {
                pushStringAnnotation("url", inline.url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(inline.text) }
                pop()
            }
            is ChatMarkdown.Inline.Plain -> append(inline.text)
        }
    }
}

/**
 * An agent message's text: the markdown of utils/ChatMarkdown.kt, drawn as
 * the web's agentHtml.ts: headings bold (level 3 at 1.17x, 4-6 at 1.07x),
 * quotes with a bar on the left, preformatted text in monospace, and tables
 * in a sideways scroll (cells 5/9 padding, the head bold over a 2px rule,
 * rows over a hairline at 18% of the text colour).
 */
@Composable
fun ChatMarkdownText(text: String, color: Color, linkColor: Color, style: TextStyle) {
    val context = LocalContext.current
    val blocks = remember(text) { ChatMarkdown.parse(text) }
    val textStyle = style.copy(color = color)
    @Composable
    fun Runs(inlines: List<ChatMarkdown.Inline>, st: TextStyle = textStyle, modifier: Modifier = Modifier, softWrap: Boolean = true) {
        val annotated = remember(inlines, linkColor) { annotatedOf(inlines, linkColor) }
        ClickableText(annotated, style = st, modifier = modifier, softWrap = softWrap, onClick = { offset ->
            annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let { openUrl(context, it.item) }
        })
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ChatMarkdown.Block.Paragraph -> Runs(block.inlines)
                is ChatMarkdown.Block.Bullet, is ChatMarkdown.Block.Numbered -> Row {
                    Text(
                        text = if (block is ChatMarkdown.Block.Numbered) "${block.n}." else "•",
                        style = textStyle,
                        modifier = Modifier.widthIn(min = 16.dp).padding(end = 4.dp)
                    )
                    Runs(block.inlines, modifier = Modifier.weight(1f))
                }
                is ChatMarkdown.Block.Heading -> Runs(
                    block.inlines,
                    textStyle.copy(fontWeight = FontWeight.Bold, fontSize = textStyle.fontSize * (if (block.level == 3) 1.17f else 1.07f))
                )
                is ChatMarkdown.Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(color.copy(alpha = 0.3f)))
                    Spacer(Modifier.width(8.dp))
                    Runs(block.inlines, textStyle.copy(color = color.copy(alpha = 0.85f)), Modifier.weight(1f))
                }
                is ChatMarkdown.Block.Pre -> Text(
                    block.text,
                    style = textStyle.copy(fontFamily = FontFamily.Monospace, fontSize = textStyle.fontSize * 0.92f),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.08f))
                        .horizontalScroll(rememberScrollState()).padding(8.dp)
                )
                // Column by column, each cell on one line, so the rows line up
                // and the rules under them join across the columns.
                is ChatMarkdown.Block.Table -> Row(Modifier.horizontalScroll(rememberScrollState())) {
                    val rule = color.copy(alpha = 0.18f)
                    block.head.forEachIndexed { k, headCell ->
                        Column(Modifier.width(IntrinsicSize.Max)) {
                            Runs(headCell, textStyle.copy(fontWeight = FontWeight.Bold), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), softWrap = false)
                            Box(Modifier.fillMaxWidth().height(2.dp).background(rule))
                            block.rows.forEachIndexed { r, row ->
                                Runs(row.getOrElse(k) { emptyList() }, textStyle, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), softWrap = false)
                                if (r < block.rows.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(rule))
                            }
                        }
                    }
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

/** An AI answer stored under its server id, not a fallback: it can be rated. */
private fun rateable(item: RecordItem): Boolean =
    item is RecordItem.AgentMessage && item.agentDetails.isAIAgent && item.extras?.isFallback != true && !item.deleted &&
        AgentBotParser.canRate(item.id)

/** Under an AI reply: sources, buttons, cards, an action to confirm, "Helpful?". */
@Composable
fun AgentReplyExtras(message: RecordItem.AgentMessage, modifier: Modifier = Modifier) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val context = LocalContext.current
    val extras = message.extras
    val customizations by Conferbot.serverCustomizationMap.collectAsState()
    val record by Conferbot.record.collectAsState()
    // Rated answers keep their line; only the newest unrated one asks.
    val newestRateable = remember(record) { record.lastOrNull { rateable(it) }?.id }
    val showHelpful = rateable(message) && AgentBotParser.answerFeedbackOn(customizations) &&
        com.conferbot.sdk.core.agent.Helpful.showsRow(message.id, message.feedback != null, newestRateable)
    // No source cards when the owner turned them off (live and restored answers alike).
    val sources = if (AgentBotParser.answerSourcesOn(customizations)) extras?.sources.orEmpty() else emptyList()
    val nothingElse = extras == null ||
        (extras.actions.isEmpty() && extras.card == null && extras.resultCard == null && extras.actionRequest == null)
    if (sources.isEmpty() && nothingElse && !showHelpful) return

    Column(
        modifier = modifier.fillMaxWidth().padding(start = theme.spacing.avatarSize + 10.dp).widthIn(max = theme.spacing.maxBubbleWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // A help article opens in the app while the help centre is on (its
        // list, else fetched by id), else its page in the browser, else it is
        // not tappable (agentSources.ts; AgentBotParser.sourceTarget).
        val articleHosts by Conferbot.articleHosts.collectAsState()
        val canOpenArticles = Conferbot.canOpenChatArticles(customizations, articleHosts)
        sources.forEach { source ->
            val target = AgentBotParser.sourceTarget(source, canOpenArticles)
            val inApp = target is com.conferbot.sdk.core.agent.OpenTarget.Article
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(8.dp))
                    .clickable(enabled = target != null) {
                        when (target) {
                            is com.conferbot.sdk.core.agent.OpenTarget.Article -> if (!Conferbot.openChatArticle(target.id, target.title)) openUrl(context, source.url)
                            is com.conferbot.sdk.core.agent.OpenTarget.Url -> openUrl(context, target.url)
                            null -> {}
                        }
                    }
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
                if (inApp) {
                    Spacer(Modifier.width(6.dp))
                    Text(I18n.t("kb.helpArticle") + " ›", color = colors.onSurfaceVariant, style = TextStyle(fontSize = theme.typography.timestampSize))
                } else if (target is com.conferbot.sdk.core.agent.OpenTarget.Url) {
                    Spacer(Modifier.width(6.dp))
                    Text("${AgentBotParser.sourceHost(target.url)} ↗", color = colors.onSurfaceVariant, style = TextStyle(fontSize = theme.typography.timestampSize))
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
        if (showHelpful) {
            // "Talk to a person" only while nobody is coming or here (web offerHuman).
            val canAskPerson = remember(record) {
                com.conferbot.sdk.core.livechat.HandoverState.talkToPersonAllowed(com.conferbot.sdk.core.livechat.HandoverState.entriesOfItems(record))
            }
            Helpful(message, canAskPerson)
        }
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
            Text(I18n.t("result.seeDetails") + " ↗", color = theme.colors.primary, style = TextStyle(fontSize = theme.typography.captionSize, fontWeight = FontWeight.SemiBold),
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
                    else Text(I18n.t("action.confirm"), color = theme.colors.onPrimary, style = TextStyle(fontSize = theme.typography.captionSize, fontWeight = FontWeight.SemiBold))
                }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(BorderStroke(1.dp, theme.colors.divider), RoundedCornerShape(10.dp))
                        .clickable(enabled = state == "ask") { answer(false) }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(I18n.t("common.notNow"), color = theme.colors.onSurface, style = TextStyle(fontSize = theme.typography.captionSize))
                }
            }
        } else {
            Text(
                when (state) { "done" -> I18n.t("action.done"); "declined" -> I18n.t("action.cancelled"); else -> I18n.t("action.failedRetry") },
                color = theme.colors.onSurfaceVariant,
                style = TextStyle(fontSize = theme.typography.captionSize),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * "Helpful?" (core/agent/AnswerFeedback.kt, web answerFeedback.ts): a vote
 * goes the moment it is given - a No too, before any reason - then the
 * reasons, an optional comment, "Talk to a person" and "Ask another way".
 * The rated line's thumbs change the vote.
 */
@Composable
private fun Helpful(message: RecordItem.AgentMessage, canAskPerson: Boolean) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val h = com.conferbot.sdk.core.agent.Helpful
    var state by remember(message.id) { mutableStateOf(h.start(message.feedback)) }
    var comment by remember(message.id) { mutableStateOf("") }
    val label = TextStyle(fontSize = theme.typography.timestampSize, color = colors.onSurfaceVariant)
    fun tap(t: com.conferbot.sdk.core.agent.Helpful.Tap) {
        val r = h.step(state, t)
        r.send?.let { Conferbot.sendAnswerFeedback(message.id, it) }
        r.ask?.let { Conferbot.sendMessage(it) }
        if (r.focus) Conferbot.focusComposer()
        state = r.state
    }
    // "Thanks for letting us know!", then the quiet rated line.
    LaunchedEffect(state.view) {
        if (state.view == com.conferbot.sdk.core.agent.Helpful.View.THANKS) {
            kotlinx.coroutines.delay(h.THANKS_MS)
            state = h.step(state, com.conferbot.sdk.core.agent.Helpful.Tap.Settle).state
        }
    }

    @Composable
    fun Thumb(helpful: Boolean, pressed: Boolean, idle: Boolean) {
        val rated = state.view == com.conferbot.sdk.core.agent.Helpful.View.RATED
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (pressed) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                .border(BorderStroke(1.dp, if (pressed) colors.primary else colors.divider), RoundedCornerShape(50))
                .clickable(onClickLabel = if (rated) (if (helpful) h.RATED_UP else h.RATED_DOWN) else if (helpful) h.YES else h.NO) {
                    tap(com.conferbot.sdk.core.agent.Helpful.Tap.Vote(helpful))
                }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                if (helpful) "\uD83D\uDC4D" else "\uD83D\uDC4E",
                style = TextStyle(fontSize = theme.typography.captionSize),
                modifier = Modifier.then(if (idle) Modifier.alpha(0.45f) else Modifier)
            )
        }
    }

    when (state.view) {
        com.conferbot.sdk.core.agent.Helpful.View.THANKS -> Text(h.THANKS, style = label)
        com.conferbot.sdk.core.agent.Helpful.View.RATED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Thumb(true, pressed = state.current?.helpful == true, idle = state.current?.helpful != true)
            Thumb(false, pressed = state.current?.helpful == false, idle = state.current?.helpful != false)
            Text(h.ratedLabel(state.current), style = label)
        }
        com.conferbot.sdk.core.agent.Helpful.View.DOWN -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(h.SORRY, style = label)
            val reason = state.current?.reason
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                h.REASONS.forEach { (key, text) ->
                    val on = reason == key
                    Pill(text, if (on) colors.primary else colors.divider, if (on) colors.primary else colors.onSurface,
                        onClick = { tap(com.conferbot.sdk.core.agent.Helpful.Tap.Reason(key)) })
                }
            }
            if (state.commented) Text(h.THANKS_COMMENT, style = label)
            else if (reason != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).border(BorderStroke(1.dp, colors.divider), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (comment.isEmpty()) Text(h.COMMENT, style = label)
                    androidx.compose.foundation.text.BasicTextField(
                        value = comment,
                        onValueChange = { comment = it.take(500) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = theme.typography.captionSize, color = colors.onSurface),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { tap(com.conferbot.sdk.core.agent.Helpful.Tap.Comment(comment)) }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Pill("\u2192", colors.primary, colors.primary, onClick = { tap(com.conferbot.sdk.core.agent.Helpful.Tap.Comment(comment)) })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canAskPerson) Pill(h.TALK_TO_PERSON, colors.divider, colors.onSurface, onClick = { tap(com.conferbot.sdk.core.agent.Helpful.Tap.Human) })
                Pill(h.RETRY, colors.divider, colors.onSurface, onClick = { tap(com.conferbot.sdk.core.agent.Helpful.Tap.Retry) })
            }
        }
        com.conferbot.sdk.core.agent.Helpful.View.ASK -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(h.ASK, style = label)
            Thumb(true, pressed = false, idle = false)
            Thumb(false, pressed = false, idle = false)
        }
    }
}

/** An agent's article, quick replies or card. */
@Composable
fun RichAgentMessageBubble(message: RecordItem.AgentRichMessage, modifier: Modifier = Modifier) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val context = LocalContext.current
    val customizationMap by Conferbot.serverCustomizationMap.collectAsState()
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
                    Text(I18n.t("kb.helpArticle"), color = colors.onSurfaceVariant, style = TextStyle(fontSize = theme.typography.timestampSize))
                    Text(rich.title, color = colors.onSurface, style = TextStyle(fontSize = theme.typography.messageSize, fontWeight = FontWeight.SemiBold))
                    if (rich.snippet.isNotBlank()) Text(rich.snippet, color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = theme.typography.captionSize))
                    // agentRich.ts: its public page first, else the article in
                    // the app (AgentBotParser.richArticleTarget).
                    val articleHosts by Conferbot.articleHosts.collectAsState()
                    val target = AgentBotParser.richArticleTarget(rich, Conferbot.canOpenChatArticles(customizationMap, articleHosts))
                    if (target != null) {
                        Text(I18n.t("sdk.kb.readArticle") + if (target is com.conferbot.sdk.core.agent.OpenTarget.Url) " ↗" else " ›", color = colors.primary,
                            style = TextStyle(fontSize = theme.typography.captionSize, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(top = 8.dp).clickable {
                                when (target) {
                                    is com.conferbot.sdk.core.agent.OpenTarget.Url -> openUrl(context, target.url)
                                    is com.conferbot.sdk.core.agent.OpenTarget.Article -> Conferbot.openChatArticle(target.id, target.title)
                                }
                            })
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
