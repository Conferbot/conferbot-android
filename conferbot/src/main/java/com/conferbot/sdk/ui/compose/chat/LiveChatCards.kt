@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.conferbot.sdk.ui.compose.chat

import com.conferbot.sdk.core.i18n.I18n
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.agent.AgentBotParser
import com.conferbot.sdk.core.livechat.CsatConfig
import com.conferbot.sdk.core.livechat.LiveChat
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient

/**
 * The small cards and lines of a live chat, as on the web widget: the
 * owner's quick actions and "Talk to a person" above the composer, the AI's
 * follow-up email card, the queue position, the "nobody could join" email
 * ask, leave a message, a locked conversation, an agent's dropped
 * connection, an agent asking for a file, the post-chat rating, "Email me
 * this chat" and the closed chat (blocked, or ended with a new chat).
 * State is on [Conferbot.liveChat] (core/livechat).
 */

@Composable
private fun LiveCard(indent: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val colors = ConferbotThemeAmbient.current.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 42.dp else 0.dp)
            .clip(shape)
            .border(1.dp, colors.divider, shape)
            .background(colors.surface, shape)
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun CardTitle(text: String) {
    val theme = ConferbotThemeAmbient.current
    Text(text, color = theme.colors.onSurface, style = TextStyle(fontFamily = theme.typography.fontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
}

@Composable
private fun CardSub(text: String) {
    val theme = ConferbotThemeAmbient.current
    Text(text, color = theme.colors.onSurfaceVariant, style = TextStyle(fontFamily = theme.typography.fontFamily, fontSize = 13.sp), modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean = true, busy: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = ConferbotThemeAmbient.current.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) colors.primary else colors.primary.copy(alpha = 0.45f))
            .clickable(enabled = enabled && !busy, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (busy) CircularProgressIndicator(color = colors.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        else Text(label, color = colors.onPrimary, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, minLines: Int = 1, email: Boolean = false, modifier: Modifier = Modifier) {
    val theme = ConferbotThemeAmbient.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .border(1.dp, theme.colors.divider, shape)
            .background(theme.colors.background, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .heightIn(min = (20 * minLines).dp)
    ) {
        if (value.isEmpty()) Text(placeholder, color = theme.colors.onSurfaceVariant, style = TextStyle(fontSize = 15.sp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(fontSize = 15.sp, color = theme.colors.onSurface),
            cursorBrush = SolidColor(theme.colors.primary),
            singleLine = minLines == 1,
            keyboardOptions = if (email) KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrect = false) else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder }
        )
    }
}

/** An email field (and an optional message) with one button. [onSubmit] reports ok. */
@Composable
private fun EmailForm(
    button: String,
    messagePlaceholder: String? = null,
    initialEmail: String = "",
    initialMessage: String = "",
    onSubmit: (email: String, message: String, done: (Boolean) -> Unit) -> Unit
) {
    var email by remember { mutableStateOf(initialEmail) }
    var message by remember { mutableStateOf(initialMessage) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = ConferbotThemeAmbient.current.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        if (messagePlaceholder != null) Field(message, { message = it }, messagePlaceholder, minLines = 3, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(email, { email = it }, "you@example.com", email = true, modifier = Modifier.weight(1f))
            PrimaryButton(button, busy = busy) {
                when {
                    !LiveChat.isEmail(email) -> error = I18n.t("sdk.form.invalidEmail")
                    messagePlaceholder != null && message.isBlank() -> error = I18n.t("leave.writeMessage")
                    else -> {
                        error = null
                        busy = true
                        onSubmit(email.trim(), message.trim()) { ok ->
                            busy = false
                            if (!ok) error = I18n.t("sdk.form.notThrough")
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = colors.error, style = TextStyle(fontSize = 12.sp)) }
    }
}

/**
 * The owner's quick actions (and "Talk to a person") above the composer, as
 * on the web (quickActions.ts): from the first AI answer on. "Talk to a
 * person" only for an AI agent bot, and only while no handover is under way
 * and nobody is here (core/livechat/HandoverState.kt talkToPersonAllowed); it
 * goes on tap and never comes back on the strip. The owner's links stay.
 */
@Composable
fun QuickActionsStrip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val customizations by Conferbot.serverCustomizationMap.collectAsState()
    val record by Conferbot.record.collectAsState()
    val agent by Conferbot.currentAgent.collectAsState()
    val agentBot by Conferbot.agentBot.collectAsState()
    val retired by Conferbot.talkToPersonRetired.collectAsState()
    val aiAnswered = record.any { it is com.conferbot.sdk.models.RecordItem.AgentMessage && it.agentDetails.isAIAgent }
    val personHere = agent != null && agent?.id?.startsWith("ai-agent-") != true
    val allowed = remember(record) {
        com.conferbot.sdk.core.livechat.HandoverState.talkToPersonAllowed(com.conferbot.sdk.core.livechat.HandoverState.entriesOfItems(record))
    }
    val offerHuman = agentBot != null && allowed && !personHere
    // Taken off by a handover: an AI reply arriving mid-handover cannot bring it back.
    LaunchedEffect(aiAnswered, offerHuman) { if (aiAnswered && !offerHuman) Conferbot.retireTalkToPerson() }
    if (!aiAnswered) return
    val actions = AgentBotParser.quickActionsOf(customizations)
    val human = offerHuman && !retired && customizations["chatTalkToHuman"] == true
    if (actions.isEmpty() && !human) return
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        actions.forEach { a ->
            Pill("${a.label} ↗") {
                Conferbot.trackGoal("quick-action:${a.label}", AgentBotParser.conversionEventOf(a.label, a.url))
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        if (human) Pill(I18n.t("chat.talkToPerson")) {
            Conferbot.retireTalkToPerson()
            Conferbot.sendMessage(LiveChat.TALK_TO_PERSON_ASK)
        }
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    val colors = ConferbotThemeAmbient.current.colors
    Text(
        label,
        color = colors.botBubbleText,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.botBubble)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/**
 * The handover step while it waits for a person: where they stand, about how
 * long. Cancel leaves the queue on the server (Conferbot.leaveQueue), then the
 * flow carries on.
 */
@Composable
fun HandoverWaitCard(message: String?, onCancel: (() -> Unit)? = { Conferbot.leaveQueue() }) {
    val theme = ConferbotThemeAmbient.current
    val queue by Conferbot.liveChat.queue.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Shown inline beside the bot's avatar already: no indent of its own.
        LiveCard(indent = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = theme.colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    CardTitle(LiveChat.queueLine(queue))
                    // The estimate, else the usual wait, else a calm promise (web queueCard.ts).
                    val map by Conferbot.serverCustomizationMap.collectAsState()
                    val lines = LiveChat.waitCardLines(queue, com.conferbot.sdk.core.livechat.ReplyTime.typicalWait(com.conferbot.sdk.core.livechat.ReplyTime.of(map["replyTime"]), queue?.typical))
                    CardSub(lines.estimate)
                    if (lines.typical.isNotEmpty()) CardSub(lines.typical)
                }
                if (onCancel != null) {
                    Text(I18n.t("common.cancel"), color = theme.colors.onSurfaceVariant, style = TextStyle(fontSize = 13.sp), modifier = Modifier.clickable(onClick = onCancel).padding(6.dp))
                }
            }
            if (!message.isNullOrBlank()) {
                Text(message, color = theme.colors.onSurfaceVariant, style = TextStyle(fontSize = 13.sp), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** Nobody could take the chat: the AI is back, and the team replies by email. */
@Composable
fun HandoverWaitingCard() {
    val w by Conferbot.liveChat.handoverWaiting.collectAsState()
    val waiting = w ?: return
    LiveCard {
        Text(LiveChat.handoverWaitingLine(waiting), color = ConferbotThemeAmbient.current.colors.onSurface, style = TextStyle(fontSize = 14.sp))
        if (waiting.email == null && waiting.askEmail) {
            EmailForm(I18n.t("common.send")) { email, _, done -> Conferbot.liveChat.shareEmailForReply(email, done) }
        }
    }
}

/** No one is available: leave a message, answered by email. */
@Composable
fun LeaveMessageCard() {
    val noAgents by Conferbot.liveChat.noAgentsAvailable.collectAsState()
    val sentTo by Conferbot.liveChat.leftMessageTo.collectAsState()
    val customizations by Conferbot.serverCustomizationMap.collectAsState()
    if ((!noAgents && sentTo == null) || customizations["chatLeaveMessage"] == false) return
    LiveCard {
        if (sentTo != null) {
            CardTitle(I18n.t("leave.sent", "email" to sentTo))
            CardSub(I18n.t("leave.sentSub"))
        } else {
            CardTitle(I18n.t("leave.title"))
            CardSub(I18n.t("leave.sub"))
            // Their email if known, and what they last asked in their own words.
            val (knownEmail, lastAsked) = remember { Conferbot.liveChat.leaveMessagePrefill() }
            EmailForm(I18n.t("common.send"), messagePlaceholder = I18n.t("leave.messagePlaceholder"), initialEmail = knownEmail, initialMessage = lastAsked) { email, message, done ->
                Conferbot.liveChat.leaveMessage(email, message, done)
            }
            Text(
                I18n.t("common.notNow"),
                color = ConferbotThemeAmbient.current.colors.onSurfaceVariant,
                style = TextStyle(fontSize = 13.sp),
                modifier = Modifier.padding(top = 8.dp).clickable { Conferbot.liveChat.skipLeaveMessage() }
            )
        }
    }
}

/** The AI saw buying intent and has no email yet: one field. */
@Composable
fun LeadCard() {
    val open by Conferbot.liveChat.leadCardOpen.collectAsState()
    val done by Conferbot.liveChat.leadEmail.collectAsState()
    if (!open && done == null) return
    LiveCard {
        if (done != null) {
            Text(I18n.t("lead.done", "email" to done), color = ConferbotThemeAmbient.current.colors.onSurface, style = TextStyle(fontSize = 14.sp))
        } else {
            CardTitle(I18n.t("lead.title"))
            CardSub(I18n.t("lead.sub"))
            EmailForm(I18n.t("common.send")) { email, _, finish ->
                Conferbot.liveChat.captureLeadEmail(email)
                finish(true)
            }
        }
    }
}

/** An agent asked for a file: the visitor's upload button. */
@Composable
fun FileRequestCard(onUpload: (() -> Unit)?) {
    val who by Conferbot.liveChat.fileRequestedBy.collectAsState()
    val name = who ?: return
    LiveCard {
        Text(I18n.t("file.requested", "name" to name), color = ConferbotThemeAmbient.current.colors.onSurface, style = TextStyle(fontSize = 14.sp))
        if (onUpload != null) {
            Spacer(Modifier.height(10.dp))
            PrimaryButton(I18n.t("sdk.file.choose"), onClick = onUpload)
        }
    }
}

private val FACES = listOf("😞", "😕", "😐", "🙂", "😄")

/** The post-chat rating, from the bot's csatConfig. */
@Composable
fun CsatCard() {
    val config: CsatConfig? by Conferbot.liveChat.csatConfig.collectAsState()
    val open by Conferbot.liveChat.csatOpen.collectAsState()
    val sent by Conferbot.liveChat.csatSent.collectAsState()
    val c = config ?: return
    if (!open) return
    val theme = ConferbotThemeAmbient.current
    var value by remember { mutableStateOf<Any?>(null) }
    var feedback by remember { mutableStateOf("") }
    LiveCard {
        if (sent) {
            CardTitle(c.thankYouText)
            return@LiveCard
        }
        CardTitle(c.titleText)
        CardSub(c.subtitleText)
        val options: List<Any> = when (c.ratingType) {
            "thumbs" -> listOf(true, false)
            "nps" -> (0..10).toList()
            else -> (1..5).toList()
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
            options.forEach { o ->
                val chosen = value != null && if (c.ratingType == "stars") (o as Int) <= (value as Int) else o == value
                if (c.ratingType == "stars") {
                    Text(
                        "★",
                        color = if (chosen) Color(0xFFF5A623) else theme.colors.divider,
                        style = TextStyle(fontSize = 30.sp),
                        modifier = Modifier.clickable { value = o }.semantics { contentDescription = I18n.t("rating.outOf", "n" to o, "max" to 5) }
                    )
                } else {
                    val label = when (c.ratingType) {
                        "thumbs" -> if (o == true) I18n.t("sdk.csat.yes") else I18n.t("sdk.csat.no")
                        "emoji" -> FACES[(o as Int) - 1]
                        else -> o.toString()
                    }
                    Text(
                        label,
                        color = if (chosen) theme.colors.onPrimary else theme.colors.onSurface,
                        textAlign = TextAlign.Center,
                        style = TextStyle(fontSize = 14.sp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, if (chosen) theme.colors.primary else theme.colors.divider, CircleShape)
                            .background(if (chosen) theme.colors.primary else Color.Transparent)
                            .clickable { value = o }
                            .widthIn(min = 36.dp)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }
        if (value != null && c.showFeedbackInput) {
            Spacer(Modifier.height(10.dp))
            Field(feedback, { feedback = it }, c.feedbackPlaceholder, minLines = 2, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            PrimaryButton(I18n.t("common.send"), enabled = value != null, modifier = Modifier.weight(1f)) {
                value?.let { Conferbot.liveChat.submitRating(it, feedback) }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, theme.colors.divider, RoundedCornerShape(12.dp))
                    .clickable { Conferbot.liveChat.dismissCsat() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) { Text(I18n.t("common.notNow"), color = theme.colors.onSurface, style = TextStyle(fontSize = 14.sp)) }
        }
    }
}

/**
 * "Email me this chat" (web emailTranscript.ts, core/livechat/EmailTranscript.kt):
 * one card, the visitor's email filled in when known; Send, Cancel; the
 * server's reason when it did not go; "Sent! Check your inbox at ..." once
 * it did.
 */
@Composable
fun EmailTranscriptCard() {
    val open by Conferbot.liveChat.mailOpen.collectAsState()
    val sentTo by Conferbot.liveChat.mailSentTo.collectAsState()
    if (!open && sentTo == null) return
    val theme = ConferbotThemeAmbient.current
    val t = com.conferbot.sdk.core.livechat.EmailTranscript
    LiveCard {
        val done = sentTo
        if (done != null) {
            CardTitle(t.sentText(done))
            return@LiveCard
        }
        var email by remember { mutableStateOf(Conferbot.liveChat.knownEmail()) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        fun submit() {
            if (busy) return
            if (!t.isEmail(email)) {
                error = t.CHECK_EMAIL
                return
            }
            error = null
            busy = true
            Conferbot.liveChat.emailTranscript(email) { r ->
                busy = false
                if (!r.ok) error = t.errorText(r)
            }
        }
        CardTitle(t.TITLE)
        CardSub(t.SUB)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Field(email, { email = it.take(120); error = null }, t.PLACEHOLDER, email = true, modifier = Modifier.weight(1f))
            PrimaryButton(if (busy) t.SENDING else t.SEND, busy = busy) { submit() }
        }
        error?.let { Text(it, color = theme.colors.error, style = TextStyle(fontSize = 12.sp), modifier = Modifier.padding(top = 6.dp)) }
        Text(
            t.CANCEL,
            color = theme.colors.onSurfaceVariant,
            style = TextStyle(fontSize = 13.sp),
            modifier = Modifier.padding(top = 8.dp).clickable { Conferbot.liveChat.closeEmailTranscript() }
        )
    }
}

/**
 * The closed chat (core/livechat/LiveEvents.kt closedStateOf): a blocked
 * visitor's neutral line, or, under an ended chat's own line, a button to
 * start a new chat.
 */
@Composable
fun ClosedChatCard(closed: com.conferbot.sdk.core.livechat.LiveEvents.ClosedState?, onNewChat: () -> Unit = { Conferbot.startNewChat() }) {
    closed ?: return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // An ended chat's "Chat has ended" line is in the record already.
        if (closed.kind == com.conferbot.sdk.core.livechat.LiveEvents.ClosedKind.BLOCKED) SystemLine(closed.text)
        if (closed.newChat) PrimaryButton(com.conferbot.sdk.core.livechat.LiveEvents.NEW_CHAT_TEXT, onClick = onNewChat)
    }
}

/** A centred system line (locked, connection dropped). */
@Composable
fun SystemLine(text: String) {
    val colors = ConferbotThemeAmbient.current.colors
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 12.sp),
            modifier = Modifier.clip(CircleShape).background(colors.surfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** The cards below the messages, in the web widget's order. */
@Composable
fun LiveChatFooterCards(onUpload: (() -> Unit)?, closed: com.conferbot.sdk.core.livechat.LiveEvents.ClosedState? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HandoverWaitingCard()
        LeaveMessageCard()
        FileRequestCard(onUpload)
        LeadCard()
        CsatCard()
        EmailTranscriptCard()
        ClosedChatCard(closed)
    }
}
