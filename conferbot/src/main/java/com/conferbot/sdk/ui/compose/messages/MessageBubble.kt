@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.conferbot.sdk.ui.compose.messages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conferbot.sdk.models.RecordItem
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient
import java.text.SimpleDateFormat
import java.util.*

/**
 * Top-level dispatcher — renders the correct bubble for any RecordItem variant.
 */
@Composable
fun MessageBubble(
    message: RecordItem,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val theme = ConferbotThemeAmbient.current
    val animationDuration = if (animate && theme.animations.enabled) {
        theme.animations.messageFadeDuration
    } else 0

    when (message) {
        is RecordItem.UserMessage -> UserMessageBubble(message, modifier, animationDuration)
        is RecordItem.UserInputResponse -> UserMessageBubble(
            RecordItem.UserMessage(id = message.id, time = message.time, text = message.text),
            modifier, animationDuration
        )
        is RecordItem.UserLiveMessage -> UserMessageBubble(
            RecordItem.UserMessage(id = message.id, time = message.time, text = message.text),
            modifier, animationDuration
        )
        is RecordItem.BotMessage -> BotMessageBubble(message, modifier, animationDuration)
        is RecordItem.AgentMessage -> Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AgentMessageBubble(message, Modifier, animationDuration)
            // An AI reply's sources, buttons, cards and "Helpful?" (AgentReply.kt).
            AgentReplyExtras(message)
        }
        is RecordItem.AgentRichMessage -> RichAgentMessageBubble(message, modifier)
        // "Connecting you with the team…" while an AI hands the visitor on,
        // then how the wait ended (core/livechat/HandoverState.kt); its dots
        // only while pending.
        is RecordItem.AgentLeftMessage -> if (message.handover) {
            val outcome = com.conferbot.sdk.core.livechat.HandoverOutcome.of(message.settled)
            SystemMessageBubble(
                RecordItem.SystemMessage(id = message.id, time = message.time, text = com.conferbot.sdk.core.livechat.HandoverState.lineText(outcome)),
                modifier, animationDuration, pending = outcome == null
            )
        } else SystemMessageBubble(
            RecordItem.SystemMessage(id = message.id, time = message.time, text = message.text),
            modifier, animationDuration
        )
        is RecordItem.SystemMessage -> SystemMessageBubble(message, modifier, animationDuration)
        // Files: the agent's, and the visitor's in a live chat.
        is RecordItem.AgentMessageFile -> FileBubble(message.file, null, null, fromVisitor = false, sender = message.agentDetails?.name, modifier = modifier)
        is RecordItem.UserLiveMessageFile -> FileBubble(message.url, message.fileName, message.mimeType, fromVisitor = true, sender = null, modifier = modifier)
        // A person's join after an AI handover: "They can see your conversation so far".
        is RecordItem.AgentJoinedMessage -> SystemMessageBubble(
            RecordItem.SystemMessage(
                id = message.id,
                time = message.time,
                text = com.conferbot.sdk.core.livechat.HandoverState.joinedText(message.agentDetails.name)
            ),
            modifier, animationDuration,
            note = if (message.afterAI) com.conferbot.sdk.core.livechat.HandoverState.HANDED_OVER_NOTE else null
        )
        else -> {}
    }
}

// ─── User bubble ────────────────────────────────────────────────────
// Web widget: border-radius 1.15rem 1.15rem 0 1.15rem  (rounded except bottom-right)

@Composable
fun UserMessageBubble(
    message: RecordItem.UserMessage,
    modifier: Modifier = Modifier,
    animationDuration: Int = 0
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val shapes = theme.shapes
    val spacing = theme.spacing
    val typography = theme.typography

    // Matches web widget: all corners rounded except bottom-right (user's side)
    val bubbleShape = userBubbleShape()
    val dur = if (animationDuration > 0) 300 else 0

    AnimatedVisibility(
        visible = true,
        enter = if (dur > 0) {
            fadeIn(tween(dur, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { it / 4 }
        } else fadeIn(tween(0))
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = spacing.maxBubbleWidth)
                    .shadow(1.dp, bubbleShape, clip = false)
                    .background(colors.userBubble, bubbleShape)
                    .then(bubbleOutline(bubbleShape))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = message.text,
                    color = colors.userBubbleText,
                    style = TextStyle(
                        fontFamily = typography.fontFamily,
                        fontSize = typography.messageSize,
                        lineHeight = typography.messageSize * 1.4f
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatTime(message.time),
                    color = colors.userBubbleText.copy(alpha = 0.6f),
                    style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.timestampSize)
                )
            }
        }
    }
}

// ─── Bot bubble ─────────────────────────────────────────────────────
// Web widget: border-radius 1.15rem 0.8rem 0.8rem 0  (rounded except bottom-left)

@Composable
fun BotMessageBubble(
    message: RecordItem.BotMessage,
    modifier: Modifier = Modifier,
    animationDuration: Int = 0
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val shapes = theme.shapes
    val spacing = theme.spacing
    val typography = theme.typography

    // Matches web widget: rounded except bottom-left (bot's side)
    val bubbleShape = botBubbleShape()
    val dur = if (animationDuration > 0) 300 else 0

    AnimatedVisibility(
        visible = true,
        enter = if (dur > 0) {
            fadeIn(tween(dur, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 4 }
        } else fadeIn(tween(0))
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            if (!message.text.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Bottom
                ) {
                    BotAvatar()
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .widthIn(max = spacing.maxBubbleWidth)
                            .shadow(1.dp, bubbleShape, clip = false)
                            .background(colors.botBubble, bubbleShape)
                            .then(bubbleOutline(bubbleShape))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = message.text,
                            color = colors.botBubbleText,
                            style = TextStyle(
                                fontFamily = typography.fontFamily,
                                fontSize = typography.messageSize,
                                lineHeight = typography.messageSize * 1.4f
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatTime(message.time),
                            color = colors.botBubbleText.copy(alpha = 0.6f),
                            style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.timestampSize)
                        )
                    }
                }
            }
            // Answered choice nodes keep their chips visible but disabled,
            // with the selection highlighted (web widget behavior).
            @Suppress("UNCHECKED_CAST")
            val persistedChoices = message.nodeData?.get("choices") as? List<String>
            if (!persistedChoices.isNullOrEmpty()) {
                val selected = message.nodeData?.get("selected")?.toString()
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(50.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        persistedChoices.forEach { choiceText ->
                            val isSelected = choiceText == selected
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) colors.primary
                                        else colors.optionBubble.copy(alpha = 0.55f),
                                contentColor = if (isSelected) Color.White
                                        else colors.optionBubbleText.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = choiceText,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = TextStyle(
                                        fontFamily = typography.fontFamily,
                                        fontSize = typography.messageSize * 0.9f
                                    )
                                )
                            }
                        }
                    }
                }
            }
            // Web widget parity: media renders as its own block below the
            // text bubble, indented past the avatar, full bubble width.
            message.imageUrl?.let { url ->
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (message.text.isNullOrBlank()) {
                        BotAvatar()
                        Spacer(Modifier.width(10.dp))
                    } else {
                        Spacer(Modifier.width(50.dp))
                    }
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(
                            androidx.compose.ui.platform.LocalContext.current
                        ).data(url).crossfade(true).build(),
                        contentDescription = message.text,
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                        modifier = Modifier
                            .width(spacing.maxBubbleWidth * 0.75f)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        }
    }
}

// ─── Agent bubble ───────────────────────────────────────────────────

@Composable
fun AgentMessageBubble(
    message: RecordItem.AgentMessage,
    modifier: Modifier = Modifier,
    animationDuration: Int = 0
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val shapes = theme.shapes
    val spacing = theme.spacing
    val typography = theme.typography

    // Same shape as bot bubble — rounded except bottom-left
    val bubbleShape = botBubbleShape()
    val dur = if (animationDuration > 0) 300 else 0
    var showOriginal by remember(message.id) { androidx.compose.runtime.mutableStateOf(false) }

    AnimatedVisibility(
        visible = true,
        enter = if (dur > 0) {
            fadeIn(tween(dur, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(dur, easing = FastOutSlowInEasing)) { -it / 4 }
        } else fadeIn(tween(0))
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            BotAvatar()
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .widthIn(max = spacing.maxBubbleWidth)
                    .shadow(1.dp, bubbleShape, clip = false)
                    .background(colors.agentBubble, bubbleShape)
                    .then(bubbleOutline(bubbleShape))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.agentDetails.name,
                    color = colors.agentBubbleText.copy(alpha = 0.85f),
                    style = TextStyle(
                        fontFamily = typography.fontFamily,
                        fontSize = typography.captionSize,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(Modifier.height(spacing.xs))
                if (message.deleted) {
                    Text(
                        text = "This message was deleted",
                        color = colors.agentBubbleText.copy(alpha = 0.7f),
                        style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.messageSize, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    )
                } else {
                    // Lists, bold and tappable links (utils/ChatMarkdown.kt).
                    ChatMarkdownText(
                        text = if (showOriginal && message.translation != null) message.translation.original else message.text,
                        color = colors.agentBubbleText,
                        linkColor = colors.agentBubbleText,
                        style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.messageSize, lineHeight = typography.messageSize * 1.4f)
                    )
                }
                // Sent in the visitor's language: the agent's own words one tap away.
                message.translation?.takeIf { !message.deleted }?.let { tr ->
                    val (seeOriginal, seeTranslation) = com.conferbot.sdk.core.livechat.LiveChat.translationLabels(tr.to)
                    Text(
                        text = if (showOriginal) seeTranslation else seeOriginal,
                        color = colors.agentBubbleText.copy(alpha = 0.75f),
                        style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.timestampSize, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                        modifier = Modifier.padding(top = 4.dp).clickable { showOriginal = !showOriginal }
                    )
                }
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = if (message.edited) "Edited · ${formatTime(message.time)}" else formatTime(message.time),
                    color = colors.agentBubbleText.copy(alpha = 0.7f),
                    style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.timestampSize)
                )
            }
        }
    }
}

// ─── System bubble ──────────────────────────────────────────────────

@Composable
fun SystemMessageBubble(
    message: RecordItem.SystemMessage,
    modifier: Modifier = Modifier,
    animationDuration: Int = 0,
    /** A wait still pending: the typing dots above the words. */
    pending: Boolean = false,
    /** A smaller line under the words. */
    note: String? = null
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val spacing = theme.spacing
    val typography = theme.typography

    AnimatedVisibility(
        visible = true,
        enter = if (animationDuration > 0) fadeIn(tween(animationDuration)) else fadeIn(tween(0))
    ) {
        Column(
            modifier = modifier.fillMaxWidth().padding(spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (pending) PendingDots(colors.systemMessageText)
            Text(
                text = message.text,
                color = colors.systemMessageText,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.captionSize)
            )
            if (note != null) {
                Text(
                    text = note,
                    color = colors.systemMessageText.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.captionSize * 0.92f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * The typing dots of a pending "Connecting you with the team…" line (the
 * web's .cb-sys__typing): three dots fading in turn, one 1.2s loop.
 */
@Composable
private fun PendingDots(color: Color) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "handover-dots")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(1200, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "handover-dots-t"
    )
    Row(
        modifier = Modifier.padding(bottom = 4.dp).hiddenFromAccessibility(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(3) { i ->
            // Bright at (i + 1) / 4 of the loop, dim either side, as the web.
            val peak = (i + 1) / 4f
            val d = kotlin.math.abs(t - peak)
            val alpha = if (d < 0.25f) 1f - (d / 0.25f) * 0.7f else 0.3f
            Box(
                Modifier
                    .padding(horizontal = 1.5.dp)
                    .size(5.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

/** Decorative: hidden from TalkBack. */
private fun Modifier.hiddenFromAccessibility(): Modifier =
    this.clearAndSetSemantics { }

// ─── Helpers ────────────────────────────────────────────────────────

private fun formatTime(date: Date): String {
    val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return format.format(date)
}

/**
 * A file in the chat: an image shows as a picture, anything else as a named
 * link; a tap opens it. Relative links (the /media proxy) open on the server.
 */
@Composable
fun FileBubble(
    rawUrl: String,
    fileName: String?,
    mimeType: String?,
    fromVisitor: Boolean,
    sender: String?,
    modifier: Modifier = Modifier
) {
    val theme = ConferbotThemeAmbient.current
    val colors = theme.colors
    val spacing = theme.spacing
    val typography = theme.typography
    val context = LocalContext.current
    val url = com.conferbot.sdk.core.livechat.LiveChat.resolveMediaUrl(rawUrl, com.conferbot.sdk.core.Conferbot.serverOrigin) ?: return
    val name = com.conferbot.sdk.core.livechat.LiveChat.fileNameOf(fileName, url)
    var imageFailed by remember(url) { androidx.compose.runtime.mutableStateOf(false) }
    val isImage = !imageFailed && com.conferbot.sdk.core.livechat.LiveChat.isImageUrl(url, mimeType)
    val bg = if (fromVisitor) colors.userBubble else colors.agentBubble
    val fg = if (fromVisitor) colors.userBubbleText else colors.agentBubbleText
    val shape = RoundedCornerShape(theme.shapes.bubbleRadius)
    val open = {
        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Unit
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (fromVisitor) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!fromVisitor) {
            BotAvatar()
            Spacer(Modifier.width(10.dp))
        }
        Column(
            modifier = Modifier
                .widthIn(max = spacing.maxBubbleWidth)
                .shadow(1.dp, shape, clip = false)
                .background(bg, shape)
                .clip(shape)
                .clickable { open() }
                .padding(if (isImage) 4.dp else 12.dp)
        ) {
            if (!sender.isNullOrBlank() && !isImage) {
                Text(sender, color = fg.copy(alpha = 0.85f), style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.captionSize, fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(spacing.xs))
            }
            if (isImage) {
                coil.compose.AsyncImage(
                    model = url,
                    contentDescription = name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier.size(width = 220.dp, height = 160.dp).clip(RoundedCornerShape(theme.shapes.bubbleRadius - 2.dp))
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("\uD83D\uDCCE", style = TextStyle(fontSize = typography.messageSize))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = name,
                        color = fg,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = typography.fontFamily, fontSize = typography.messageSize, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                    )
                }
            }
        }
    }
}
