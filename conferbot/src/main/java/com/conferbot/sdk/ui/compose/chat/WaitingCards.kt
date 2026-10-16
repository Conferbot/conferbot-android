package com.conferbot.sdk.ui.compose.chat

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.core.waiting.Board
import com.conferbot.sdk.core.waiting.GameInput
import com.conferbot.sdk.core.waiting.GamesCard
import com.conferbot.sdk.core.waiting.GamesCardState
import com.conferbot.sdk.core.waiting.GamesText
import com.conferbot.sdk.core.waiting.HelpCardState
import com.conferbot.sdk.core.waiting.WaitArticles
import com.conferbot.sdk.core.waiting.WaitingGames
import com.conferbot.sdk.ui.compose.lookColor
import com.conferbot.sdk.ui.compose.steps.openStepLink
import com.conferbot.sdk.ui.compose.steps.rememberStepLook
import com.conferbot.sdk.ui.theme.ConferbotThemeAmbient
import kotlinx.coroutines.launch

/**
 * While you wait for a person, in the conversation: the help card and the
 * waiting games card, drawn from [Conferbot.waiting] (core/waiting). Ported
 * from the React Native SDK's components/LiveChat/WaitHelpCard.tsx and
 * GamesCard.tsx (web waitArticles.ts mountHelpCard with css/polish.css
 * .cb-wait-articles, games/ui.ts with css/polish-games.css). Both sit in the
 * bot's column; the help card in the bot bubble's colours, the games in the
 * theme's card colours with the bot's accent and font. Motion is the web's
 * and no more, all of it off when the phone asks for reduced motion.
 */

/** The phone asks for less motion (the animator duration scale is 0). */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

/** The bot's column: the avatar's width (none when hidden) and the gap after it. */
@Composable
private fun botIndent(): Dp {
    val theme = ConferbotThemeAmbient.current
    val look by Conferbot.customizations.collectAsState()
    val map by Conferbot.serverCustomizationMap.collectAsState()
    val loaded = map.isNotEmpty()
    val avatar = if (loaded && look.hideAvatar) 0.dp else if (loaded) look.avatarSize.dp else theme.spacing.avatarSize
    return avatar + theme.spacing.sm
}

// ------------------------------------------------------------ the help card

/**
 * "While you wait, this might help": a row per item (an article opens in
 * the chat's help centre, a page in the browser) and, for the server's pick,
 * "That solved it". Once the wait is over it stays as history under
 * "Articles that might help".
 */
@Composable
fun WaitHelpCard(card: HelpCardState, modifier: Modifier = Modifier) {
    if (card.items.isEmpty()) return
    val theme = ConferbotThemeAmbient.current
    val bg = theme.colors.botBubble
    val ink = theme.colors.botBubbleText
    val font = theme.typography.fontFamily
    val context = LocalContext.current
    val title = if (card.settled) WaitArticles.SETTLED_TITLE else WaitArticles.TITLE
    Box(modifier.fillMaxWidth().padding(start = botIndent(), end = 12.dp, top = 4.dp, bottom = 4.dp)) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(10.dp)
                .semantics { contentDescription = if (card.settled) WaitArticles.SETTLED_TITLE else WaitArticles.LABEL },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                color = ink.copy(alpha = 0.8f),
                style = TextStyle(fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 2.dp)
            )
            card.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(lerp(bg, ink, 0.1f))
                        .clickable(onClickLabel = "${WaitArticles.READ}: ${item.title}", role = Role.Button) {
                            Conferbot.openWaitHelpItem(item)
                            if (item.kind == "page" && item.url != null) openStepLink(context, item.url)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.title, color = ink, style = TextStyle(fontFamily = font, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.5.sp))
                        item.description?.let {
                            Text(it, color = ink.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = font, fontSize = 12.sp, lineHeight = 16.sp))
                        }
                    }
                    Text(WaitArticles.READ, color = ink.copy(alpha = 0.85f), style = TextStyle(fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                }
            }
            if (card.done) {
                Text(
                    WaitArticles.DONE,
                    color = ink.copy(alpha = 0.85f),
                    style = TextStyle(fontFamily = font, fontSize = 12.sp),
                    modifier = Modifier.padding(horizontal = 2.dp).padding(top = 2.dp).semantics { liveRegion = LiveRegionMode.Polite }
                )
            } else if (card.solvable && !card.settled) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.End) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, lerp(bg, ink, 0.45f), CircleShape)
                            .clickable(enabled = !card.busy, role = Role.Button) { Conferbot.waiting.solveWait() }
                            .alpha(if (card.busy) 0.6f else 1f)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (card.busy) CircularProgressIndicator(color = ink, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
                        Text(WaitArticles.SOLVED, color = ink, style = TextStyle(fontFamily = font, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ the games card

private data class GamesLook(
    val card: Color,
    val ink: Color,
    val muted: Color,
    val fill: Color,
    val line: Color,
    val accent: Color,
    val soft: Color,
    val accentInk: Color,
    val font: androidx.compose.ui.text.font.FontFamily
) {
    fun text(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = ink, line: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = font, fontSize = size, fontWeight = weight, color = color, lineHeight = line)
}

@Composable
private fun rememberGamesLook(): GamesLook {
    val step = rememberStepLook()
    val theme = ConferbotThemeAmbient.current
    val map by Conferbot.serverCustomizationMap.collectAsState()
    return remember(step, map, theme) {
        val accent = lookColor(WaitingGames.pickAccent(map)) ?: Color(0xFF0808EE)
        val dark = step.card.luminance() < 0.5f
        GamesLook(
            card = step.card,
            ink = step.text,
            muted = step.muted,
            fill = lerp(step.card, step.text, if (dark) 0.1f else 0.05f),
            line = step.line,
            accent = accent,
            soft = lerp(step.card, accent, 0.09f),
            // A cross in the accent on a dark card is lifted so it reads.
            accentInk = if (dark) lerp(accent, Color.White, 0.5f) else accent,
            font = theme.typography.fontFamily
        )
    }
}

/** A quiet tile (a pick, an option, a cell). */
@Composable
private fun Tile(
    look: GamesLook,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = look.fill,
    label: String? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun FootButtonView(look: GamesLook, label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (primary) look.accent else Color.Transparent)
            .then(if (primary) Modifier else Modifier.border(1.dp, look.line, shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, style = look.text(12.5.sp, FontWeight.SemiBold, if (primary) Color.White else look.ink))
    }
}

@Composable
private fun RpsView(b: Board.Rps, look: GamesLook, reduced: Boolean, act: (GameInput) -> Unit) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(b.shaking, reduced) {
        if (!b.shaking || reduced) {
            shake.snapTo(0f)
            return@LaunchedEffect
        }
        // Three shakes in 900ms (web polish-games.css).
        repeat(3) {
            shake.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
            shake.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
        }
    }
    @Composable
    fun Big(text: String, who: String) {
        val won = (who == "you" && b.outcome == "win") || (who == "bot" && b.outcome == "lose")
        val lost = (who == "you" && b.outcome == "lose") || (who == "bot" && b.outcome == "win")
        Text(
            text,
            style = look.text(40.sp, line = 46.sp),
            modifier = Modifier.graphicsLayer {
                val v = if (b.shaking) shake.value else 0f
                translationY = -12.dp.toPx() * v
                rotationZ = -10f * v
                alpha = if (lost) 0.45f else 1f
                val s = if (won) 1.18f else 1f
                scaleX = if (who == "bot") -s else s
                scaleY = s
            }
        )
    }
    Column {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Big(b.you, "you")
            Text("VS", style = look.text(11.sp, FontWeight.Bold, look.muted).copy(letterSpacing = 0.9.sp))
            Big(b.bot, "bot")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { contentDescription = "Pick your hand" }) {
            b.hands.forEach { h ->
                Tile(look, Modifier.weight(1f).alpha(if (b.busy) 0.6f else 1f), enabled = !b.busy, label = h.name, onClick = { act(GameInput.Rps(h.hand)) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
                        Text(h.emoji, style = look.text(22.sp, line = 26.sp))
                        Spacer(Modifier.height(3.dp))
                        Text(h.name, style = look.text(11.5.sp, FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

@Composable
private fun TttView(b: Board.Ttt, look: GamesLook, act: (GameInput) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 210.dp).semantics { contentDescription = "Tic-tac-toe board" }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0 until 3) {
                    val i = r * 3 + col
                    val c = b.cells[i]
                    val inLine = i in b.line
                    val label = "Row ${r + 1}, column ${col + 1}, " + when (c) { "X" -> "yours"; "O" -> "the bot's"; else -> "empty" }
                    Tile(
                        look,
                        Modifier.weight(1f).aspectRatio(1f).alpha(if (b.over && !inLine) 0.5f else 1f),
                        enabled = c == null && !b.over && !b.thinking,
                        color = if (inLine) (if (c == "O") look.muted else look.accent) else look.fill,
                        label = label,
                        onClick = { act(GameInput.Ttt(i)) }
                    ) {
                        Text(
                            when (c) { "X" -> "✕"; "O" -> "◯"; else -> "" },
                            style = look.text(26.sp, FontWeight.Bold, if (inLine) Color.White else if (c == "X") look.accentInk else look.muted)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryView(b: Board.Memory, look: GamesLook, act: (GameInput) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { contentDescription = "Memory cards" }) {
        b.cards.chunked(4).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { k, c ->
                    val i = r * 4 + k
                    Tile(
                        look,
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .then(if (c.matched) Modifier.border(1.5.dp, lerp(look.card, look.accent, 0.45f), RoundedCornerShape(12.dp)) else Modifier),
                        enabled = !c.matched,
                        color = if (c.up) (if (c.matched) look.soft else look.fill) else look.accent,
                        label = "Card ${i + 1}, ${if (c.up) c.face else "face down"}${if (c.matched) ", matched" else ""}",
                        onClick = { act(GameInput.Memory(i)) }
                    ) {
                        if (c.up) Text(c.face, style = look.text(24.sp))
                        else Text("?", style = look.text(18.sp, FontWeight.Bold, Color.White.copy(alpha = 0.75f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceView(b: Board.Choice, look: GamesLook, act: (GameInput) -> Unit) {
    val emoji = b.game == "emoji"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        b.tag?.let {
            Box(Modifier.clip(CircleShape).background(look.soft).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(it, style = look.text(10.5.sp, FontWeight.Bold))
            }
        }
        b.big?.let {
            Text(
                it,
                style = look.text(40.sp, line = 46.sp).copy(textAlign = TextAlign.Center, letterSpacing = 3.sp),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Emoji: $it" }
            )
        }
        Text(
            b.q,
            style = if (emoji) look.text(12.sp, color = look.muted).copy(textAlign = TextAlign.Center) else look.text(13.5.sp, FontWeight.SemiBold, line = 19.sp),
            modifier = Modifier.fillMaxWidth()
        )
        val done = b.picked != null
        @Composable
        fun Option(i: Int, o: String, modifier: Modifier) {
            val right = done && i == b.answer
            val wrong = done && i == b.picked && i != b.answer
            Tile(
                look,
                modifier.alpha(if (done && !right && !wrong) 0.5f else if (wrong) 0.6f else 1f),
                enabled = !done,
                color = if (right) look.accent else look.fill,
                label = o,
                onClick = { act(GameInput.Choice(i)) }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                    horizontalArrangement = if (emoji) Arrangement.Center else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        o,
                        style = look.text(13.sp, FontWeight.SemiBold, if (right) Color.White else look.ink)
                            .copy(textDecoration = if (wrong) TextDecoration.LineThrough else TextDecoration.None),
                        modifier = if (emoji) Modifier else Modifier.weight(1f, fill = false)
                    )
                    if (right) {
                        if (!emoji) Spacer(Modifier.weight(1f))
                        Text("✓", style = look.text(13.sp, FontWeight.SemiBold, Color.White), modifier = Modifier.padding(start = if (emoji) 4.dp else 6.dp))
                    }
                }
            }
        }
        if (emoji) {
            b.options.withIndex().chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { (i, o) -> Option(i, o, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                b.options.forEachIndexed { i, o -> Option(i, o, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun ReactionView(b: Board.Reaction, look: GamesLook, act: (GameInput) -> Unit) {
    val go = b.state == "go"
    val wait = b.state == "wait"
    val shape = RoundedCornerShape(16.dp)
    val current by rememberUpdatedState(b.state)
    val tap by rememberUpdatedState(act)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(shape)
            .background(if (go) Color(0xFF16A34A) else if (wait) look.fill else look.soft)
            .then(if (go) Modifier else Modifier.border(1.dp, if (wait) look.line else lerp(look.card, look.accent, 0.18f), shape))
            // On press, not on release, so the time is the visitor's.
            .pointerInput(Unit) { detectTapGestures(onPress = { if (current != "done") tap(GameInput.Reaction) }) }
            .semantics { role = Role.Button; contentDescription = b.line },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        Text(b.big, style = look.text(30.sp, line = 34.sp))
        Text(b.line, style = look.text(13.5.sp, FontWeight.SemiBold, if (go) Color.White else look.ink))
    }
}

@Composable
private fun ScrambleView(b: Board.Scramble, look: GamesLook, reduced: Boolean, act: (GameInput) -> Unit) {
    val nudge = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(b.nope) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (reduced) return@LaunchedEffect
        nudge.snapTo(0f)
        nudge.animateTo(1f, tween(400))
    }
    // The web's shake: 0, -5, 5, -3, 3, 0 over the step.
    fun shakeAt(t: Float): Float {
        val pts = floatArrayOf(0f, -5f, 5f, -3f, 3f, 0f)
        val x = (t.coerceIn(0f, 1f) * 5f)
        val i = x.toInt().coerceAtMost(4)
        return pts[i] + (pts[i + 1] - pts[i]) * (x - i)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(b.hint, style = look.text(30.sp, line = 34.sp).copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
        Row(
            Modifier.fillMaxWidth().graphicsLayer { translationX = shakeAt(nudge.value).dp.toPx() }
                .semantics { contentDescription = "Your word, tap a letter to take it back" },
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
        ) {
            b.letters.indices.forEach { i ->
                val t = b.placed.getOrNull(i)?.takeIf { it >= 0 }
                val filled = t != null
                val solvedLook = b.solved && !b.revealed
                val shape = RoundedCornerShape(9.dp)
                Box(
                    Modifier
                        .size(width = 34.dp, height = 40.dp)
                        .clip(shape)
                        .background(if (solvedLook) look.accent else if (b.revealed) look.fill else if (filled) look.soft else Color.Transparent)
                        .then(if (solvedLook || b.revealed) Modifier else Modifier.border(1.5.dp, if (filled) lerp(look.card, look.accent, 0.55f) else look.line, shape))
                        .clickable(enabled = filled && !b.solved, role = Role.Button) { act(GameInput.Slot(i)) }
                        .semantics { contentDescription = if (filled) "Space ${i + 1}, ${b.letters[t!!]}" else "Space ${i + 1}, empty" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (t != null) b.letters[t] else "",
                        style = look.text(16.sp, FontWeight.Bold, if (solvedLook) Color.White else if (b.revealed) look.muted else look.ink)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().semantics { contentDescription = "Letters" }, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
            b.letters.forEachIndexed { i, letter ->
                val used = i in b.placed
                Tile(
                    look,
                    Modifier.size(width = 36.dp, height = 40.dp).alpha(if (used) 0.2f else 1f).graphicsLayer { val s = if (used) 0.9f else 1f; scaleX = s; scaleY = s },
                    enabled = !used && !b.solved,
                    label = "Letter $letter",
                    onClick = { act(GameInput.Tile(i)) }
                ) { Text(letter, style = look.text(16.sp, FontWeight.Bold)) }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FootButtonView(look, b.skipLabel, enabled = !b.solved) { act(GameInput.Skip) }
        }
    }
}

// The web's confetti (games/ui.ts confetti): 34 pieces falling 300 in about 1.75s.
private class Piece(val x: Float, val dx: Float, val r: Float, val d: Float, val round: Boolean)

@Composable
private fun Confetti(look: GamesLook, burst: Int) {
    if (burst == 0) return
    val pieces = remember { List(34) { i -> Piece(Math.random().toFloat(), ((Math.random() - 0.5) * 120).toFloat(), (Math.random() * 720 - 360).toFloat(), (Math.random() * 0.15).toFloat(), i % 3 == 0) } }
    val progress = remember { Animatable(0f) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(burst) {
        running = true
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1750, easing = CubicBezierEasing(0.25f, 0.6f, 0.4f, 1f)))
        running = false
    }
    if (!running) return
    val tints = listOf(look.accent, Color(0xFFF5A524), lerp(look.accent, Color.White, 0.55f), Color(0xFFFF6B6B), Color(0xFF22C55E))
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        pieces.forEachIndexed { i, piece ->
            val t = if (p <= piece.d) 0f else ((p - piece.d) / (1f - piece.d)).coerceIn(0f, 1f)
            val alpha = if (t < 0.85f) 1f else (1f - (t - 0.85f) / 0.15f).coerceIn(0f, 1f)
            val w = (if (piece.round) 8 else 7).dp.toPx()
            val h = (if (piece.round) 8 else 11).dp.toPx()
            val left = piece.x * size.width + piece.dx.dp.toPx() * t
            val top = -12.dp.toPx() + 300.dp.toPx() * t
            rotate(piece.r * t, pivot = Offset(left + w / 2, top + h / 2)) {
                drawRoundRect(
                    color = tints[i % tints.size].copy(alpha = alpha),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    cornerRadius = CornerRadius((if (piece.round) 4 else 2).dp.toPx())
                )
            }
        }
    }
}

/** The games card: the menu, a game, or the saved game when a teammate joined. */
@Composable
fun GamesCardView(card: GamesCard, modifier: Modifier = Modifier) {
    val look = rememberGamesLook()
    val reduced = rememberReducedMotion()
    var state by remember(card) { mutableStateOf(card.state) }
    DisposableEffect(card) {
        state = card.state
        val off = card.subscribe { state = it }
        onDispose { off() }
    }
    val s: GamesCardState = state
    if (s.view == "gone") return

    // The card rises 8 in 450ms; a small cheer on a right answer.
    val rise = remember(card) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(card) { if (!reduced) rise.animateTo(1f, tween(450, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))) }
    val cheer = remember(card) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(s.cheer) {
        if (s.cheer == 0 || reduced) return@LaunchedEffect
        scope.launch {
            cheer.animateTo(1f, tween(200))
            cheer.animateTo(0f, tween(300))
        }
    }
    val act: (GameInput) -> Unit = { card.input(it) }
    val win = s.result == "win"
    val shape = RoundedCornerShape(18.dp)

    Box(modifier.fillMaxWidth().padding(start = botIndent(), end = 12.dp, top = 6.dp, bottom = 6.dp)) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = rise.value
                    translationY = 8.dp.toPx() * (1f - rise.value)
                    val sc = 1f + 0.015f * cheer.value
                    scaleX = sc
                    scaleY = sc
                }
                .clip(shape)
                .background(look.card)
                .border(if (win) 1.5.dp else 1.dp, if (win) lerp(look.card, look.accent, 0.55f) else look.line, shape)
                .semantics { contentDescription = GamesText.CARD }
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (s.titleEmoji.isNotEmpty()) {
                            Text(s.titleEmoji, style = look.text(17.sp))
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(s.title, style = look.text(14.sp, FontWeight.Bold, line = 18.sp), modifier = Modifier.semantics { heading() })
                    }
                    if (s.meta.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(s.meta, style = look.text(11.5.sp, FontWeight.SemiBold, look.muted).copy(fontFeatureSettings = "tnum"))
                    }
                    if (s.closable) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClickLabel = GamesText.CLOSE) { card.dismiss() }
                                .semantics { contentDescription = GamesText.CLOSE },
                            contentAlignment = Alignment.Center
                        ) { Text("✕", style = look.text(14.sp, color = look.muted)) }
                    }
                }

                val b = s.board
                if (s.view == "menu") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { contentDescription = GamesText.PICK }) {
                        s.chips.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { g ->
                                    Row(
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(look.soft)
                                            .border(1.dp, lerp(look.card, look.accent, 0.16f), RoundedCornerShape(12.dp))
                                            .clickable(role = Role.Button, onClickLabel = "${g.name}. ${g.blurb}") { card.open(g.id) }
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(g.emoji, style = look.text(18.sp))
                                        Text(g.name, maxLines = 2, style = look.text(12.5.sp, FontWeight.SemiBold, line = 16.sp))
                                    }
                                }
                            }
                        }
                    }
                } else if (s.view == "game" && b != null) {
                    // A result left when a teammate joined takes no more taps.
                    val play: (GameInput) -> Unit = if (s.settled) ({}) else act
                    when (b) {
                        is Board.Rps -> RpsView(b, look, reduced, play)
                        is Board.Ttt -> TttView(b, look, play)
                        is Board.Memory -> MemoryView(b, look, play)
                        is Board.Choice -> ChoiceView(b, look, play)
                        is Board.Reaction -> ReactionView(b, look, play)
                        is Board.Scramble -> ScrambleView(b, look, reduced, play)
                    }
                }

                if (s.status.isNotEmpty()) {
                    Text(
                        s.status,
                        style = look.text(12.5.sp, if (s.result != null) FontWeight.SemiBold else FontWeight.Normal, line = 18.sp),
                        modifier = Modifier.padding(top = 10.dp).semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                if (s.foot.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
                    ) {
                        s.foot.forEach { f -> FootButtonView(look, f.label, f.primary) { card.press(f.key) } }
                    }
                }
            }
            if (!reduced) Box(Modifier.matchParentSize()) { Confetti(look, s.confetti) }
        }
    }
}

/**
 * The while-you-wait cards placed after [afterId] (null: the ones whose item
 * is not on screen, drawn at the end). [onScreen] are the ids on screen.
 */
@Composable
internal fun WaitingCardsAfter(afterId: String?, onScreen: Set<String>) {
    val help by Conferbot.waiting.help.collectAsState()
    val games by Conferbot.waiting.games.collectAsState()
    fun here(after: String?) = if (afterId == null) after == null || after !in onScreen else after == afterId
    help?.takeIf { here(it.after) }?.let { WaitHelpCard(it.card) }
    games?.takeIf { here(it.after) }?.let { GamesCardView(it.card) }
}

/** The Compose chat draws the cards: games may be offered while it is up. */
@Composable
internal fun WaitingCardsHost() {
    DisposableEffect(Unit) {
        Conferbot.setWaitingCardsShown(true)
        onDispose { Conferbot.setWaitingCardsShown(false) }
    }
}
