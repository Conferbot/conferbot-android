package com.conferbot.sdk.ui.compose.home

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.home.AskRow
import com.conferbot.sdk.core.home.Home
import com.conferbot.sdk.core.home.HomeModel
import com.conferbot.sdk.core.home.HomeSection
import com.conferbot.sdk.core.home.HelpSearch
import com.conferbot.sdk.core.home.RemoteHits
import com.conferbot.sdk.core.home.TextRun
import com.conferbot.sdk.core.requests.RequestState
import com.conferbot.sdk.ui.compose.lookColor
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient
import kotlinx.coroutines.delay

/** The home screen's colours: the theme's page, cards, quiet fills and ink, one accent. */
private data class HomeLook(
    val page: Color,
    val card: Color,
    val soft: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val font: FontFamily
) {
    fun text(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = ink, line: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = font, fontSize = size, fontWeight = weight, color = color, lineHeight = line)
}

private val GREEN = Color(0xFF22C55E)
private val SOLVED = Color(0xFF16A34A)
private val AMBER = Color(0xFFF5B544)
private val DARK_INK = Color(0xFF0F172A)
/** The web's cb-rise easing (polish-home.css cubic-bezier(.22,1,.36,1)). */
private val RISE_EASE = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private fun color(hex: String?, fallback: Color): Color = lookColor(hex) ?: fallback

/**
 * The home screen (web conferbot-widget src/ts/home/firstImpression.ts
 * enhanceHome, askBox.ts, trust.ts, requests/card.ts over
 * templates/shared.ts generateInitialPage; CSS src/css/polish.css "Home",
 * polish-home.css, polish-requests.css "Home card"; RN
 * components/Home/HomeScreen.tsx). Draws [Home.model] and sends each tap
 * where the web sends it:
 *
 *   "Talk to us" and a starter question     the conversation (asking it)
 *   "Ask anything" rows                     ask the agent, open an article, or the chat
 *   "Pick up where you left off"            the conversation, or ask again
 *   "Your requests" rows / See all (n)      the requests portal over the home
 *   a recent article / a topic / See all    the article / the help centre
 *   the cross                               closes the chat ([onClose])
 *
 * Calm, as the web: the brand band, the theme's cards, one accent; each part
 * rises 8dp into place once (the web's cb-rise), none when the phone asks for
 * less motion (animator duration scale 0), and it stays still after. Nothing
 * is drawn while the owner has no home screen. [mascot] is where the home's
 * mascot will go (a later item), above the greeting.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreenView(
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    mascot: (@Composable () -> Unit)? = null
) {
    val c by Conferbot.serverCustomizationMap.collectAsState()
    val look by Conferbot.customizations.collectAsState()
    val agentBot by Conferbot.agentBot.collectAsState()
    val kb by Conferbot.home.knowledgeBase.collectAsState()
    val memory by Conferbot.home.memory.collectAsState()
    val sessionId by Conferbot.chatSessionId.collectAsState()
    val requests by Conferbot.requests.requests.collectAsState()
    val seen by Conferbot.requests.seen.collectAsState()
    val theme = ConferbotThemeAmbient.current

    // The hour and "welcome back" are read again each time the home is drawn.
    val m = remember(c, look.title, agentBot, kb, memory, sessionId, requests, seen) {
        Home.model(
            c,
            botName = look.title,
            agent = Conferbot.homeAgent(agentBot),
            categories = kb.categories,
            recentArticles = kb.recentArticles,
            memory = memory,
            chatSessionId = sessionId,
            requests = requests,
            seen = seen
        )
    } ?: return

    val hl = remember(c, look, theme) {
        val p = Home.palette(if (c.isNotEmpty()) look.theme.tokens else null)
        HomeLook(
            page = color(p.page, Color(0xFFF6F7FB)),
            card = color(p.card, Color.White),
            soft = color(p.soft, Color(0xFFF1F5F9)),
            ink = color(p.ink, DARK_INK),
            muted = color(p.muted, Color(0xFF64748B)),
            line = color(p.line, Color(0xFFF1F5F9)),
            accent = color(m.accent, Color(0xFF4F46E5)),
            font = theme.typography.fontFamily
        )
    }
    val context = LocalContext.current
    val still = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }

    var query by remember { mutableStateOf("") }
    var helpQuery by remember { mutableStateOf("") }
    var remote by remember { mutableStateOf<RemoteHits?>(null) }
    val broken = remember { mutableStateMapOf<String, Boolean>() }
    val loadedLogos = remember { mutableStateMapOf<String, Boolean>() }
    val density = LocalDensity.current
    var contentTop by remember { mutableStateOf(0f) }
    var bandPx by remember { mutableStateOf(with(density) { 250.dp.toPx() }) }

    // The server's matches inside article text, 280ms after the last key (askBox.ts askRemote).
    LaunchedEffect(query, kb) {
        val q = query.trim()
        val key = if (q.isNotEmpty() && m.ask != null) Home.remoteQueryFor(q, kb.rawArticles) else null
        if (key != null) {
            delay(280)
            Conferbot.home.searchArticleText(key) { hits -> remote = RemoteHits(q.lowercase(), hits) }
        }
    }

    val bandA = color(m.band.first, Color(0xFF4F46E5))
    val bandB = color(m.band.second, bandA)
    val bandInk = if (m.light) DARK_INK else Color.White
    val asking = m.ask != null && query.trim().isNotEmpty()
    val rows = if (m.ask != null) Home.askRows(query, m.ask.agentName, m.articles, remote) else emptyList()
    fun ok(url: String?) = !url.isNullOrEmpty() && broken[url] != true

    val showChat: (String?) -> Unit = { Conferbot.showChat(it) }
    val openArticle: (String) -> Unit = { id -> Conferbot.openHelpArticle(id, m.articles.firstOrNull { it.id == id }?.title) }
    fun runAsk(r: AskRow?) {
        when (r) {
            is AskRow.Ask -> showChat(r.query)
            is AskRow.Article -> openArticle(r.id)
            is AskRow.Chat -> showChat(null)
            else -> {}
        }
    }

    val shown = m.order.filter { !(asking && it in Home.HIDDEN_WHILE_ASKING) }

    Box(
        modifier
            .fillMaxSize()
            .background(hl.page)
            .verticalScroll(rememberScrollState())
    ) {
        // The brand band, scrolling with the content, ending behind the middle of "Talk to us".
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { (if (asking) 250.dp.toPx() else bandPx).toDp() })
                .cssGradient(bandA, bandB, 150f)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { contentTop = it.positionInRoot().y }
                .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 24.dp)
        ) {
            Rise(100, still) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        m.brand?.let { b ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
                                if (ok(b.logo)) {
                                    AsyncImage(
                                        model = b.logo, contentDescription = null, contentScale = ContentScale.Crop,
                                        onError = { broken[b.logo] = true },
                                        modifier = Modifier.size(40.dp).clip(CircleShape)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                if (b.name.isNotEmpty()) Text(b.name, style = hl.text(15.sp, FontWeight.SemiBold, bandInk), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        mascot?.invoke()
                        Text(m.hello, style = hl.text(13.5.sp, FontWeight.SemiBold, bandInk.copy(alpha = 0.82f)))
                        if (m.title.isNotEmpty()) Text(
                            m.title,
                            style = hl.text(24.sp, FontWeight.Bold, bandInk, 29.sp).copy(letterSpacing = (-0.48).sp),
                            modifier = Modifier.semantics { heading() }
                        )
                    }
                    if (onClose != null) {
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button, onClickLabel = Home.CLOSE, onClick = onClose)
                                .semantics { contentDescription = Home.CLOSE },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Close, contentDescription = null, tint = bandInk, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
            Rise(160, still) {
                Text(m.description, style = hl.text(14.5.sp, color = bandInk.copy(alpha = 0.92f), line = 21.sp), modifier = Modifier.padding(bottom = 16.dp))
            }

            shown.forEach { part ->
                Rise(if (part == HomeSection.HELP || part == HomeSection.TRUST) 300 else 220, still) {
                    when (part) {
                        HomeSection.ASK -> AskCard(hl, m, query, { query = it }, rows) { runAsk(it) }
                        HomeSection.RESUME -> m.resume?.let { r ->
                            HelpCard(hl, Modifier.padding(bottom = 14.dp)) {
                                Label(hl, Home.RESUME, Modifier.offset(y = (-2).dp))
                                Row2(hl, lead = { IconBox(hl, Icons.Outlined.History) }, title = { Title(hl, r.topic, lines = 2) }, sub = r.line) {
                                    // The conversation is on the device: open it. Otherwise ask again.
                                    showChat(if (r.resumable) null else r.topic)
                                }
                            }
                        }
                        HomeSection.REQUESTS -> m.requests?.let { RequestsCard(hl, it) }
                        HomeSection.TALK -> TalkCard(hl, m, bandA, bandB, bandInk, ::ok, { broken[it] = true }, showChat) { top, h ->
                            // The band runs down to the middle of the card (at most 72 into it).
                            bandPx = (top - contentTop) + minOf(with(density) { 72.dp.toPx() }, h / 2f)
                        }
                        HomeSection.HELP -> m.help?.let { h ->
                            HelpPart(hl, m, h, helpQuery, { helpQuery = it }, agentBot != null, ::ok, { broken[it] = true }, openArticle, showChat)
                        }
                        HomeSection.TRUST -> m.trust?.let { t ->
                            val logos = t.logos.filter { ok(it) }
                            // The customer logos show once one has really loaded (trust.ts): a label over nothing is a claim with no proof.
                            if (logos.isNotEmpty()) Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 6.dp)
                                    .alpha(if (logos.any { loadedLogos[it] == true }) 1f else 0f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (t.label.isNotEmpty()) Text(t.label, style = hl.text(12.sp, FontWeight.SemiBold, hl.muted), textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 10.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    logos.forEach { src ->
                                        AsyncImage(
                                            model = src, contentDescription = Home.CUSTOMER_LOGO, contentScale = ContentScale.Fit,
                                            onError = { broken[src] = true }, onSuccess = { loadedLogos[src] = true },
                                            // No image filters here: the web's greyscale logos are drawn at 55%.
                                            modifier = Modifier.height(22.dp).width(88.dp).alpha(0.55f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ pieces

/** A CSS linear-gradient(<angle>deg, a, b) over the whole box. */
private fun Modifier.cssGradient(a: Color, b: Color, angleDeg: Float): Modifier = drawBehind {
    if (a == b) { drawRect(a); return@drawBehind }
    val r = Math.toRadians(angleDeg.toDouble())
    val dx = Math.sin(r).toFloat()
    val dy = (-Math.cos(r)).toFloat()
    // The CSS gradient line: long enough that the corners get the end colours.
    val half = (Math.abs(size.width * dx) + Math.abs(size.height * dy)) / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    drawRect(Brush.linearGradient(listOf(a, b), start = c - Offset(dx * half, dy * half), end = c + Offset(dx * half, dy * half)))
}

/** The web's cb-rise: up 8dp and in, once, after [delayMs]. */
@Composable
private fun Rise(delayMs: Int, still: Boolean, content: @Composable () -> Unit) {
    val v = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { if (!still) v.animateTo(1f, tween(450, delayMillis = delayMs, easing = RISE_EASE)) }
    val shift = with(LocalDensity.current) { 8.dp.toPx() }
    Box(Modifier.graphicsLayer { alpha = v.value; translationY = (1f - v.value) * shift }) { content() }
}

private fun cardModifier(hl: HomeLook, soft: Boolean = false): Modifier =
    Modifier
        .fillMaxWidth()
        .shadow(if (soft) 3.dp else 4.dp, RoundedCornerShape(18.dp), ambientColor = DARK_INK.copy(alpha = if (soft) 0.09f else 0.12f), spotColor = DARK_INK.copy(alpha = if (soft) 0.09f else 0.12f))
        .background(hl.card, RoundedCornerShape(18.dp))

@Composable
private fun HelpCard(hl: HomeLook, modifier: Modifier = Modifier, border: BorderStroke? = null, content: @Composable () -> Unit) {
    Column(
        modifier
            .then(cardModifier(hl, soft = true))
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(18.dp)) else Modifier)
            .padding(6.dp)
    ) { content() }
}

@Composable
private fun Label(hl: HomeLook, text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = hl.text(11.5.sp, FontWeight.SemiBold, hl.muted).copy(letterSpacing = 0.46.sp),
        modifier = modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SeeAll(hl: HomeLook, text: String, onClick: () -> Unit) {
    Text(
        text,
        style = hl.text(12.5.sp, FontWeight.SemiBold, hl.accent),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

@Composable
private fun IconBox(hl: HomeLook, icon: ImageVector, tint: Color = hl.accent, bg: Color = lerp(hl.card, hl.accent, 0.12f)) {
    Box(Modifier.size(40.dp).background(bg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Title(hl: HomeLook, text: String, lines: Int = 1, weight: FontWeight = FontWeight.SemiBold) {
    Text(text, style = hl.text(14.sp, weight, line = 19.sp), maxLines = lines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Runs(hl: HomeLook, runs: List<TextRun>, lines: Int = 1) {
    val mark = lerp(hl.card, hl.accent, 0.12f)
    Text(
        buildAnnotatedString {
            runs.forEach { r -> if (r.mark) withStyle(SpanStyle(background = mark)) { append(r.text) } else append(r.text) }
        },
        style = hl.text(14.sp, FontWeight.SemiBold, line = 19.sp), maxLines = lines, overflow = TextOverflow.Ellipsis
    )
}

/** A row of a card (polish.css .recent-articles .article-card / .cb-home-row). */
@Composable
private fun Row2(
    hl: HomeLook,
    lead: @Composable () -> Unit,
    title: @Composable () -> Unit,
    sub: String?,
    subStyle: TextStyle? = null,
    chevron: Boolean = true,
    trail: (@Composable BoxScope.() -> Unit)? = null,
    a11y: String? = null,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (a11y != null) Modifier.semantics { contentDescription = a11y } else Modifier)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 34.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            lead()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                title()
                if (!sub.isNullOrEmpty()) Text(
                    sub, style = subStyle ?: hl.text(12.5.sp, color = hl.muted, line = 17.5.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        when {
            trail != null -> trail()
            chevron -> Text("›", style = hl.text(18.sp, color = lerp(hl.card, hl.muted, 0.8f)), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
        }
    }
}

/** The search box (polish-home.css: 42 high, radius 12, the soft fill, a 2dp accent ring at 35% while focused). */
@Composable
private fun SearchBox(hl: HomeLook, value: String, onChange: (String) -> Unit, placeholder: String, label: String, ime: ImeAction, onSubmit: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .height(42.dp)
            .background(if (focused) hl.card else hl.soft, RoundedCornerShape(12.dp))
            .border(2.dp, if (focused) lerp(hl.card, hl.accent, 0.35f) else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = hl.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = hl.text(14.5.sp, color = hl.muted), maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= 300) onChange(it) },
                singleLine = true,
                textStyle = hl.text(14.5.sp),
                cursorBrush = SolidColor(hl.accent),
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(imeAction = ime, autoCorrect = false),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }, onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }
            )
        }
    }
}

// ------------------------------------------------------------ the parts

@Composable
private fun AskCard(hl: HomeLook, m: HomeModel, query: String, onQuery: (String) -> Unit, rows: List<AskRow>, run: (AskRow?) -> Unit) {
    val ask = m.ask ?: return
    val first = Home.askDefault(rows)
    HelpCard(hl, Modifier.padding(bottom = 14.dp)) {
        SearchBox(hl, query, onQuery, ask.placeholder, ask.label, if (ask.agentName != null) ImeAction.Send else ImeAction.Search) { run(first) }
        if (query.trim().isNotEmpty()) Column(Modifier.padding(top = 4.dp)) {
            rows.forEach { r ->
                when (r) {
                    is AskRow.Label -> Label(hl, r.text)
                    is AskRow.None -> Text(
                        r.text, style = hl.text(13.5.sp, color = hl.muted), textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 4.dp)
                    )
                    else -> Box(if (r == first) Modifier.background(lerp(hl.card, hl.accent, 0.08f), RoundedCornerShape(12.dp)) else Modifier) {
                        when (r) {
                            is AskRow.Ask -> Row2(hl, lead = { IconBox(hl, Icons.Filled.AutoAwesome) }, title = {
                                Text(
                                    buildAnnotatedString {
                                        append("${Home.ASK_QUERY} ")
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("“${r.query}”") }
                                    },
                                    style = hl.text(14.sp, FontWeight.Medium, line = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }, sub = r.sub, chevron = false) { run(r) }
                            is AskRow.Article -> Row2(hl, lead = { IconBox(hl, Icons.Outlined.Description) }, title = { Runs(hl, r.title) }, sub = r.sub, chevron = false) { run(r) }
                            is AskRow.Chat -> Row2(hl, lead = { IconBox(hl, Icons.Outlined.ChatBubbleOutline) }, title = { Title(hl, r.title) }, sub = r.sub, chevron = false) { run(r) }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestsCard(hl: HomeLook, card: HomeModel.RequestsCard) {
    HelpCard(
        hl,
        if (card.fresh) Modifier.padding(bottom = 14.dp) else Modifier.padding(top = 14.dp),
        border = if (card.fresh) BorderStroke(1.5.dp, lerp(hl.card, hl.accent, 0.45f)) else null
    ) {
        Row(Modifier.fillMaxWidth().padding(end = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Label(hl, Home.REQUESTS_TITLE)
            card.seeAll?.let { SeeAll(hl, it) { Conferbot.openRequests() } }
        }
        card.rows.forEach { r ->
            val tint = when (r.state) { RequestState.SOLVED -> SOLVED; RequestState.REPLIED -> hl.accent; RequestState.WAITING -> hl.ink }
            val bg = when (r.state) {
                RequestState.SOLVED -> SOLVED.copy(alpha = 0.14f)
                RequestState.REPLIED -> lerp(hl.card, hl.accent, 0.1f)
                RequestState.WAITING -> lerp(hl.card, hl.ink, 0.09f)
            }
            val icon = when (r.state) {
                RequestState.SOLVED -> Icons.Outlined.CheckCircle
                RequestState.REPLIED -> Icons.Outlined.ChatBubbleOutline
                RequestState.WAITING -> Icons.Outlined.Schedule
            }
            Row2(
                hl,
                lead = { IconBox(hl, icon, tint, bg) },
                title = { Title(hl, r.title, lines = 2, weight = if (r.fresh) FontWeight.Bold else FontWeight.SemiBold) },
                sub = r.line,
                subStyle = hl.text(13.sp, if (r.fresh) FontWeight.Bold else FontWeight.Normal, hl.ink.copy(alpha = if (r.fresh) 1f else 0.72f), 17.5.sp),
                chevron = false,
                trail = if (r.fresh) ({
                    Box(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(9.dp).background(hl.accent, CircleShape))
                }) else null,
                a11y = "${r.title}. ${r.line}"
            ) { Conferbot.openRequests(r.id) }
        }
    }
}

@Composable
private fun TalkCard(
    hl: HomeLook,
    m: HomeModel,
    bandA: Color,
    bandB: Color,
    bandInk: Color,
    ok: (String?) -> Boolean,
    lose: (String) -> Unit,
    showChat: (String?) -> Unit,
    onPlaced: (top: Float, height: Int) -> Unit
) {
    val t = m.talk
    Column {
        if (m.message.isNotEmpty()) Text(m.message, style = hl.text(14.sp, color = bandInk), modifier = Modifier.padding(bottom = 8.dp))
        Column(
            cardModifier(hl)
                .onGloballyPositioned { onPlaced(it.positionInRoot().y, it.size.height) }
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp)) {
                    val face = lerp(hl.card, hl.ink, 0.06f)
                    if (ok(t.avatar)) AsyncImage(
                        model = t.avatar, contentDescription = null, contentScale = ContentScale.Crop, onError = { lose(t.avatar) },
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(face)
                    ) else Box(Modifier.size(46.dp).background(face, CircleShape))
                    if (t.dot) Box(
                        Modifier
                            .offset(x = 34.dp, y = 34.dp)
                            .size(11.dp)
                            .background(hl.card, CircleShape)
                            .padding(2.5.dp)
                            .background(GREEN, CircleShape)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = hl.text(15.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 1.dp))
                    t.tagline?.let { Text(it, style = hl.text(13.sp, FontWeight.Medium, hl.muted, 17.5.sp)) }
                    t.status?.let { Text(it, style = hl.text(13.sp, FontWeight.Medium, hl.muted, 17.5.sp)) }
                }
                val faces = t.faces.filter(ok)
                if (faces.isNotEmpty()) Row(Modifier.padding(start = 10.dp).semantics { contentDescription = Home.OUR_TEAM }) {
                    faces.forEachIndexed { i, src ->
                        AsyncImage(
                            model = src, contentDescription = null, contentScale = ContentScale.Crop, onError = { lose(src) },
                            modifier = Modifier
                                .offset(x = (-7 * i).dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE2E8F0))
                                .border(2.dp, hl.card, CircleShape)
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .cssGradient(bandA, bandB, 135f)
                    .clickable(role = Role.Button, onClick = { showChat(null) }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t.button, style = hl.text(14.5.sp, FontWeight.SemiBold, bandInk).copy(letterSpacing = 0.15.sp))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Send, contentDescription = null, tint = bandInk, modifier = Modifier.size(16.dp))
            }

            if (t.starters.isNotEmpty()) Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .drawBehind { drawLine(hl.line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                t.starters.forEach { s ->
                    Text(
                        s,
                        style = hl.text(13.sp, FontWeight.SemiBold, hl.accent, 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(lerp(hl.card, hl.accent, 0.07f))
                            .border(1.dp, lerp(hl.card, hl.accent, 0.22f), RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button, onClick = { showChat(s) })
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            t.rating?.let { r ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp).semantics(mergeDescendants = true) { contentDescription = r.label },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Row(Modifier.alpha(0.3f)) { repeat(5) { Icon(Icons.Filled.Star, null, tint = AMBER, modifier = Modifier.size(12.dp)) } }
                        // The stars fill to the average shown.
                        Row(Modifier.width((60 * r.fill / 100).dp).clipToBounds().wrapContentWidth(Alignment.Start, unbounded = true)) {
                            repeat(5) { Icon(Icons.Filled.Star, null, tint = AMBER, modifier = Modifier.size(12.dp)) }
                        }
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(r.average, style = hl.text(12.5.sp, FontWeight.Medium, lerp(hl.muted, hl.ink, 0.5f)))
                    Spacer(Modifier.width(5.dp))
                    Text("·", style = hl.text(12.5.sp, color = hl.muted.copy(alpha = 0.7f)))
                    Spacer(Modifier.width(5.dp))
                    Text(r.count, style = hl.text(12.5.sp, color = hl.muted))
                }
            }
        }
    }
}

@Composable
private fun HelpPart(
    hl: HomeLook,
    m: HomeModel,
    h: HomeModel.Help,
    helpQuery: String,
    onHelpQuery: (String) -> Unit,
    hasAgent: Boolean,
    ok: (String?) -> Boolean,
    lose: (String) -> Unit,
    openArticle: (String) -> Unit,
    showChat: (String?) -> Unit
) {
    val found = if (h.search) Home.helpSearch(helpQuery, m.articles, hasAgent) else null
    HelpCard(hl, Modifier.padding(top = 14.dp)) {
        if (h.search) SearchBox(hl, helpQuery, onHelpQuery, Home.SEARCH_PLACEHOLDER, Home.SEARCH_LABEL, ImeAction.Search) {
            ((found as? HelpSearch.Results)?.rows?.firstOrNull())?.let { openArticle(it.id) }
        }
        when (found) {
            is HelpSearch.Results -> found.rows.forEach { r ->
                Row2(hl, lead = { IconBox(hl, Icons.Outlined.Description) }, title = { Runs(hl, r.title) }, sub = r.sub) { openArticle(r.id) }
            }
            is HelpSearch.Empty -> Column(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(found.text, style = hl.text(13.5.sp, color = hl.muted), textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 10.dp))
                Text(
                    found.action,
                    style = hl.text(13.5.sp, FontWeight.SemiBold, Color.White),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(hl.accent)
                        .clickable(role = Role.Button, onClick = { showChat(if (found.asks) helpQuery.trim() else null) })
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            null -> {
                Row(Modifier.fillMaxWidth().padding(end = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        h.title,
                        style = hl.text(13.sp, FontWeight.Bold).copy(letterSpacing = 0.26.sp),
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp).semantics { heading() }
                    )
                    if (h.seeAll) SeeAll(hl, Home.SEE_ALL) { Conferbot.showHelp() }
                }
                h.recent.forEach { a ->
                    Row2(
                        hl,
                        lead = {
                            if (a.image != null && ok(a.image)) AsyncImage(
                                model = a.image, contentDescription = null, contentScale = ContentScale.Crop, onError = { lose(a.image) },
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(hl.soft)
                            ) else IconBox(hl, Icons.Outlined.Description)
                        },
                        title = { Title(hl, a.title) },
                        sub = a.description
                    ) { openArticle(a.id) }
                }
                if (h.topics.isNotEmpty()) Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp)
                        .drawBehind { drawLine(hl.line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    h.topics.forEach { tp ->
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(hl.soft)
                                .clickable(role = Role.Button, onClick = { Conferbot.showHelp(tp.id) })
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tp.name, style = hl.text(12.5.sp, FontWeight.SemiBold, lerp(hl.muted, hl.ink, 0.6f)))
                            Spacer(Modifier.width(6.dp))
                            Text(tp.count.toString(), style = hl.text(11.sp, FontWeight.SemiBold, lerp(hl.card, hl.muted, 0.75f)))
                        }
                    }
                }
            }
        }
    }
}
