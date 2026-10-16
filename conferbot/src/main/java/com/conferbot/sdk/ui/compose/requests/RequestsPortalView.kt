package com.conferbot.sdk.ui.compose.requests

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.requests.PendingReply
import com.conferbot.sdk.core.requests.RequestMessage
import com.conferbot.sdk.core.requests.RequestState
import com.conferbot.sdk.core.requests.Requests
import com.conferbot.sdk.core.requests.RequestsExtras
import com.conferbot.sdk.core.requests.RequestsPortalState
import com.conferbot.sdk.ui.compose.lookColor
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient

/** Calm colours for the portal: the theme's page, card and ink, one accent. */
private data class PortalLook(
    val page: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val font: FontFamily
) {
    fun text(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = ink, line: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = font, fontSize = size, fontWeight = weight, color = color, lineHeight = line)
}

private val SOLVED = Color(0xFF16A34A)
private val ERROR = Color(0xFFDC2626)
/** The web's ease-out (polish-requests.css cubic-bezier(.22,1,.36,1)). */
private val EASE_OUT = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
private fun rememberPortalLook(): PortalLook {
    val theme = ConferbotThemeAmbient.current
    val look by Conferbot.customizations.collectAsState()
    val map by Conferbot.serverCustomizationMap.collectAsState()
    return remember(theme, look, map) {
        val tokens = if (map.isNotEmpty()) look.theme.tokens else null
        val page = lookColor(tokens?.bg) ?: theme.colors.background
        val card = lookColor(tokens?.card) ?: theme.colors.surface
        val ink = lookColor(tokens?.ink) ?: theme.colors.onBackground
        PortalLook(
            page = page,
            card = card,
            ink = ink,
            muted = lerp(page, ink, 0.64f),
            line = lerp(card, ink, 0.14f),
            accent = lookColor(Requests.accent(map)) ?: Color(0xFF4F46E5),
            font = theme.typography.fontFamily
        )
    }
}

/**
 * "Your requests": the screens (web conferbot-widget
 * src/ts/home/requests/view.ts, its CSS src/css/polish-requests.css; RN
 * components/Requests/RequestsPortal.tsx). Draws the portal's state
 * ([RequestsExtras.state]) over whatever it is placed in - the Compose chat
 * puts it over the conversation, an app can put it over its own screen and
 * call [Conferbot.openRequests]: the list, one request with its banner, the
 * conversation and the reply box. Back (and the system back) goes one step
 * back. Nothing is drawn while it is closed.
 *
 * Calm: the theme's page, card and ink, the bot's font, one accent for the
 * Back link, the visitor's bubbles and Send ([Requests.accent]). It slides
 * in 14dp over 220ms unless the phone asks for less motion, and stays still
 * after.
 */
@Composable
fun RequestsPortalView(modifier: Modifier = Modifier, extras: RequestsExtras = Conferbot.requests) {
    val s by extras.state.collectAsState()
    val open = s.screen != RequestsPortalState.Screen.CLOSED
    // The system back: one step back, as the web's Escape.
    BackHandler(enabled = open) { extras.back() }
    // The screen it sits on went (the chat closed): the portal goes with it.
    DisposableEffect(extras) { onDispose { if (extras.portal.isOpen) extras.close() } }
    if (!open) return

    val look = rememberPortalLook()
    val context = LocalContext.current
    val reduced = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    val slide = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduced) slide.animateTo(1f, tween(220, easing = EASE_OUT)) }
    val shift = with(LocalDensity.current) { 14.dp.toPx() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = slide.value
                translationX = (1f - slide.value) * shift
            }
            .background(look.page)
            // Taps stay in the portal, not on the chat under it.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .semantics { contentDescription = s.title }
    ) {
        Bar(look, s.title) { extras.back() }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Body(look, s, extras)
        }
        if (s.screen == RequestsPortalState.Screen.REQUEST && s.request != null) {
            Composer(look, s, extras)
        }
    }
}

@Composable
private fun Bar(look: PortalLook, title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(look.card)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .widthIn(min = 72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = Requests.BACK, onClick = onBack)
                    .padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", style = look.text(22.sp, color = look.accent, line = 22.sp), modifier = Modifier.padding(end = 2.dp))
                Text(Requests.BACK, style = look.text(15.sp, FontWeight.SemiBold, look.accent))
            }
            Text(
                title,
                style = look.text(16.sp, FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            Spacer(Modifier.width(72.dp))
        }
        Divider(color = look.line, thickness = 1.dp)
    }
}

@Composable
private fun Note(look: PortalLook, text: String, action: Pair<String, () -> Unit>? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text, style = look.text(15.sp, color = look.muted, line = 22.sp), textAlign = TextAlign.Center)
        action?.let { (label, onClick) -> TextLink(look, label, look.accent, onClick) }
    }
}

@Composable
private fun TextLink(look: PortalLook, label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        style = look.text(15.sp, FontWeight.Bold, color).copy(textDecoration = TextDecoration.Underline),
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button, onClick = onClick).padding(2.dp)
    )
}

@Composable
private fun StateIcon(look: PortalLook, state: RequestState, size: Dp, round: Boolean) {
    val tint = when (state) {
        RequestState.SOLVED -> SOLVED
        RequestState.REPLIED -> look.accent
        RequestState.WAITING -> look.ink
    }
    val bg = when (state) {
        RequestState.SOLVED -> SOLVED.copy(alpha = 0.14f)
        RequestState.REPLIED -> lerp(look.card, look.accent, 0.1f)
        RequestState.WAITING -> lerp(look.card, look.ink, 0.09f)
    }
    val icon = when (state) {
        RequestState.SOLVED -> Icons.Outlined.CheckCircle
        RequestState.REPLIED -> Icons.Outlined.ChatBubbleOutline
        RequestState.WAITING -> Icons.Outlined.Schedule
    }
    Box(
        modifier = Modifier.size(size).clip(if (round) CircleShape else RoundedCornerShape(10.dp)).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Body(look: PortalLook, s: RequestsPortalState, extras: RequestsExtras) {
    when {
        s.loading -> Note(look, Requests.LOADING)
        s.failed -> Note(look, Requests.LOAD_FAILED, Requests.TRY_AGAIN to { extras.portal.reload() })
        s.screen == RequestsPortalState.Screen.LIST -> RequestList(look, s, extras)
        s.request != null -> RequestThread(look, s, extras)
    }
}

@Composable
private fun RequestList(look: PortalLook, s: RequestsPortalState, extras: RequestsExtras) {
    val list = s.list.orEmpty()
    if (list.isEmpty()) return Note(look, Requests.EMPTY)
    LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = look.card,
                border = BorderStroke(1.dp, look.line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    list.forEachIndexed { i, q ->
                        val fresh = Requests.isNewReply(q, s.seen)
                        val words = Requests.rowLine(q, s.seen)
                        if (i > 0) Divider(color = look.line, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { extras.portal.pick(q.id) }
                                .semantics(mergeDescendants = true) { contentDescription = "${q.title}. $words" }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StateIcon(look, Requests.stateOf(q), 40.dp, round = false)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    q.title,
                                    style = look.text(15.sp, if (fresh) FontWeight.Bold else FontWeight.SemiBold, line = 20.sp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    words,
                                    style = look.text(13.sp, if (fresh) FontWeight.Bold else FontWeight.Normal, look.ink.copy(alpha = if (fresh) 1f else 0.72f)),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            if (fresh) Box(Modifier.size(9.dp).clip(CircleShape).background(look.accent))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestThread(look: PortalLook, s: RequestsPortalState, extras: RequestsExtras) {
    val r = s.request ?: return
    val listState = rememberLazyListState()
    val count = r.messages.size + s.pending.size + 1
    // The newest at the bottom, in view.
    LaunchedEffect(count) { listState.scrollToItem(count - 1) }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "banner") {
            val banner = s.banner
            val state = s.requestState
            if (banner != null && state != null) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(look.card)
                    .border(1.dp, look.line, RoundedCornerShape(14.dp))
                    .semantics(mergeDescendants = true) {}
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StateIcon(look, state, 38.dp, round = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(banner.label, style = look.text(16.sp, FontWeight.Bold, line = 23.sp))
                    Text(banner.detail, style = look.text(14.sp, color = look.muted, line = 20.sp))
                }
            }
        }
        r.messages.forEach { m -> item(key = "m-${m.id}") { Bubble(look, m, null, extras) } }
        s.pending.forEach { p ->
            item(key = "p-${p.entryId}") { Bubble(look, RequestMessage(p.entryId, "you", "", p.text, p.at), p, extras) }
        }
    }
}

@Composable
private fun Bubble(look: PortalLook, m: RequestMessage, pending: PendingReply?, extras: RequestsExtras) {
    val you = m.from == "you"
    val who = if (you) Requests.YOU else m.name.ifEmpty { Requests.SUPPORT_TEAM }
    val whenText = Requests.whenWords(m.at)
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (you) 16.dp else 4.dp, bottomEnd = if (you) 4.dp else 16.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (you) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            modifier = Modifier.fillMaxWidth(0.86f),
            horizontalAlignment = if (you) Alignment.End else Alignment.Start
        ) {
            Text(
                if (whenText.isNotEmpty()) "$who · $whenText" else who,
                style = look.text(12.5.sp, color = look.muted),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            )
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = if (pending != null) 0.75f else 1f }
                    .clip(shape)
                    .background(if (you) look.accent else look.card)
                    .then(if (you) Modifier else Modifier.border(1.dp, look.line, shape))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Linked(look, m.text, look.text(15.sp, color = if (you) Color.White else look.ink, line = 22.sp))
            }
            when (pending?.status) {
                null -> {}
                PendingReply.Status.SENDING ->
                    Text(Requests.SENDING, style = look.text(12.5.sp, color = look.muted), modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
                PendingReply.Status.LOCKED ->
                    Text(Requests.LOCKED, style = look.text(12.5.sp, FontWeight.SemiBold, ERROR), modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp))
                PendingReply.Status.FAILED -> Row(Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${Requests.NOT_SENT} ", style = look.text(12.5.sp, FontWeight.SemiBold, ERROR))
                    Text(
                        Requests.TRY_AGAIN,
                        style = look.text(12.5.sp, FontWeight.Bold, ERROR).copy(textDecoration = TextDecoration.Underline),
                        modifier = Modifier.clickable(role = Role.Button) { extras.portal.retry(pending.entryId) }
                    )
                }
            }
        }
    }
}

/** Text with its links tappable (web view.ts appendText). */
@Composable
private fun Linked(look: PortalLook, text: String, style: TextStyle) {
    val runs = remember(text) { Requests.linkRuns(text) }
    if (runs.none { it.url != null }) return Text(text, style = style)
    val uri = LocalUriHandler.current
    val annotated: AnnotatedString = remember(runs) {
        buildAnnotatedString {
            runs.forEach { run ->
                val url = run.url
                if (url == null) append(run.text)
                else {
                    pushStringAnnotation("URL", url)
                    pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                    append(run.text)
                    pop(); pop()
                }
            }
        }
    }
    ClickableText(annotated, style = style) { offset ->
        annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { runCatching { uri.openUri(it.item) } }
    }
}

@Composable
private fun Composer(look: PortalLook, s: RequestsPortalState, extras: RequestsExtras) {
    val focus = remember { MutableInteractionSource() }
    val focused by focus.collectIsFocusedAsState()
    Column(Modifier.fillMaxWidth().background(look.card).imePadding()) {
        Divider(color = look.line, thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = s.draft,
                onValueChange = { extras.portal.setDraft(it.take(Requests.MAX_REPLY)) },
                textStyle = look.text(15.sp),
                cursorBrush = SolidColor(look.accent),
                interactionSource = focus,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp, max = 120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(look.card)
                    .border(1.5.dp, if (focused) look.accent else lerp(look.card, look.ink, 0.24f), RoundedCornerShape(12.dp))
                    .semantics { contentDescription = Requests.YOUR_REPLY },
                decorationBox = { inner ->
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        if (s.draft.isEmpty()) Text(s.placeholder, style = look.text(15.sp, color = look.muted))
                        inner()
                    }
                }
            )
            Box(
                modifier = Modifier
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(look.accent.copy(alpha = if (s.canSend) 1f else 0.45f))
                    .clickable(enabled = s.canSend, role = Role.Button) { extras.portal.send() }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(Requests.SEND, style = look.text(15.sp, FontWeight.Bold, Color.White))
            }
        }
    }
}
