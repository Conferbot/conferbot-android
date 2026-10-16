package com.conferbot.sdk.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conferbot.sdk.core.Conferbot
import com.conferbot.sdk.ui.theme.ConferbotThemeProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.key
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.conferbot.sdk.core.customization.FxValues
import com.conferbot.sdk.core.customization.Keyframes
import com.conferbot.sdk.core.customization.LauncherAttention
import com.conferbot.sdk.core.customization.LauncherEntrance
import com.conferbot.sdk.core.customization.MascotArt
import kotlin.math.roundToInt

// ============================================================
// CONSTANTS
// ============================================================

/** Duration (ms) for the chat overlay enter/exit transitions. */
private const val OVERLAY_TRANSITION_MS = 300

/** Duration (ms) for the FAB icon crossfade. */
private const val ICON_CROSSFADE_MS = 200

/** Default brand blue — used when neither server nor config provides a color. */
private val DefaultFabColor = Color(0xFF1B55F3)

// ============================================================
// ENUMS / CONFIG
// ============================================================

/**
 * Position of the floating widget on screen.
 */
enum class WidgetPosition {
    BOTTOM_RIGHT,
    BOTTOM_LEFT
}

/**
 * Configuration for the floating chat widget appearance and behavior.
 *
 * All server-side customizations take priority over these values. Use this
 * config only for local defaults that apply when the server provides no override.
 *
 * @param position      Corner placement of the FAB (default: bottom-right).
 * @param size          Diameter of the FAB (default: 50 dp, matching the web widget).
 * @param offsetX       Horizontal distance from the screen edge (default: 10 dp).
 * @param offsetY       Vertical distance from the screen bottom (default: 10 dp).
 * @param backgroundColor Solid FAB color used when the server provides no color. When null
 *                      the SDK falls back to [DefaultFabColor].
 * @param showUnreadBadge Whether to display the unread message badge.
 * @param borderRadius  Corner radius of the FAB; null = fully circular (size / 2).
 */
data class ConferBotWidgetConfig(
    val position: WidgetPosition = WidgetPosition.BOTTOM_RIGHT,
    val size: Dp = 50.dp,
    val offsetX: Dp = 10.dp,
    val offsetY: Dp = 10.dp,
    val backgroundColor: Color? = null,
    val showUnreadBadge: Boolean = true,
    val borderRadius: Dp? = null,
)

// ============================================================
// CTA TOOLTIP
// ============================================================

/**
 * A small pill-shaped tooltip that shows the CTA text above / beside the FAB.
 *
 * @param text     The label to display.
 * @param onDismiss Called when the user taps the tooltip to dismiss it.
 */
@Composable
fun WidgetCtaTooltip(
    text: String,
    backgroundColor: Color,
    borderRadius: Dp,
    onDismiss: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(borderRadius)
    Surface(
        modifier = Modifier
            .widthIn(max = 212.dp)
            .shadow(
                elevation = 8.dp,
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss,
            ),
        shape = shape,
        color = backgroundColor,
        contentColor = Color.White,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            fontSize = 14.sp,
            maxLines = 3,
        )
    }
}

// ============================================================
// MAIN WIDGET
// ============================================================

/**
 * Floating chat widget launcher.
 *
 * Renders a server-customizable FAB in the configured screen corner. Tapping the FAB opens an
 * animated bottom-sheet chat overlay that hosts [ConferBotChatScreen]. The overlay includes a
 * semi-transparent scrim that dismisses the chat on tap.
 *
 * Server customizations (fetched from `chatbotData.customizations`) take priority over [config].
 * Priority for each attribute:
 *  - FAB color  : widgetIconBgColor > headerBgColor > config.backgroundColor > DefaultFabColor
 *  - FAB size   : widgetSize > config.size
 *  - Position   : widgetPosition ("left"/"right") > config.position
 *  - OffsetX    : widgetOffsetLeft/widgetOffsetRight (depending on position) > config.offsetX
 *  - OffsetY    : widgetOffsetBottom > config.offsetY
 *  - Radius     : widgetBorderRadius > config.borderRadius > size / 2 (circular)
 *  - Icon       : widgetIconSVG (one of WidgetBubbleIcon1..15) — drawn via Canvas
 *  - Greeting   : the web's greeting card (chatIconCta, greetingRules, greetingStyle, ...) - see GreetingCard
 *
 * Usage:
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     // Your app content
 *     ConferBotWidget()
 * }
 * ```
 *
 * @param config   Local fallback configuration for the widget.
 * @param modifier Modifier applied to the root container.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConferBotWidget(
    config: ConferBotWidgetConfig = ConferBotWidgetConfig(),
    modifier: Modifier = Modifier,
) {
    var isChatOpen by remember { mutableStateOf(false) }
    val fabInteractionSource = remember { MutableInteractionSource() }
    val isPressed by fabInteractionSource.collectIsPressedAsState()

    val unreadCount by Conferbot.unreadCount.collectAsState()
    val serverTheme by Conferbot.serverTheme.collectAsState()
    val serverCustomization by Conferbot.serverCustomization.collectAsState()

    // Synchronise visibility with the singleton so the SDK can suppress in-app notifications.
    LaunchedEffect(isChatOpen) {
        Conferbot.setChatVisible(isChatOpen)
        if (isChatOpen) {
            Conferbot.resetUnreadCount()
        }
    }

    // The launcher as the web draws it on a phone (core/customization/Launcher).
    val customizationMap by Conferbot.serverCustomizationMap.collectAsState()
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat()
    val look = remember(customizationMap, screenWidthDp) {
        com.conferbot.sdk.core.customization.Launcher.resolve(customizationMap, screenWidthDp)
    }
    val hasLook = customizationMap.isNotEmpty()

    // The bot's greeting is the web's greeting card (core/customization/Greeting):
    // its rules, delay, frequency and style.
    val agentBot by Conferbot.agentBot.collectAsState()
    val greeting = rememberGreeting(
        customizations = customizationMap,
        chatOpen = isChatOpen,
        botId = Conferbot.activeBotId ?: "default",
        agentBot = agentBot,
        enabled = hasLook,
    )
    // The card that just went, drawn while it leaves (RN ConferBotWidget leavingCard).
    val greetingLeave = remember { GreetingLeave() }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // A "send" greeting or a suggested question goes out once the chat has a
    // session, past the home screen to the conversation (web
    // firstImpression.ts openConversation).
    val sendWhenReady: (String) -> Unit = { text ->
        Conferbot.showChat()
        scope.launch {
            delay(400)
            kotlinx.coroutines.withTimeoutOrNull(8_000) { Conferbot.chatSessionId.first { it != null } }
            Conferbot.sendMessage(text)
        }
    }

    // ---- Resolve server-driven values with fallbacks ----

    val fabColor: Color = (if (hasLook) com.conferbot.sdk.ui.compose.lookColor(look.solid) else null)
        ?: config.backgroundColor
        ?: DefaultFabColor
    val fabBrush: androidx.compose.ui.graphics.Brush? = look.gradient?.takeIf { hasLook }?.let { (a, b) ->
        val start = com.conferbot.sdk.ui.compose.lookColor(a)
        val end = com.conferbot.sdk.ui.compose.lookColor(b)
        if (start != null && end != null) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(start, end)) else null
    }
    val inkColor: Color = (if (hasLook) com.conferbot.sdk.ui.compose.lookColor(look.ink) else null) ?: Color.White
    val glowColor: Color = (if (hasLook) com.conferbot.sdk.ui.compose.lookColor(look.glow) else null) ?: Color.Black

    val position: WidgetPosition = if (hasLook) {
        if (look.position == "left") WidgetPosition.BOTTOM_LEFT else WidgetPosition.BOTTOM_RIGHT
    } else config.position

    val size: Dp = if (hasLook) look.size.dp else config.size
    val fabWidth: Dp = if (hasLook) look.width.dp else config.size
    val offsetX: Dp = if (hasLook) look.side.dp else config.offsetX
    val offsetY: Dp = if (hasLook) look.bottom.dp else config.offsetY
    // Peek: the tab is tucked part-way off screen.
    val tucked: Dp = if (hasLook) look.tucked.dp else 0.dp

    val fabShape = if (hasLook) {
        RoundedCornerShape(
            topStart = look.corners.topLeft.dp,
            topEnd = look.corners.topRight.dp,
            bottomEnd = look.corners.bottomRight.dp,
            bottomStart = look.corners.bottomLeft.dp,
        )
    } else RoundedCornerShape(config.borderRadius ?: (size / 2))

    // Icon name for the SVG renderer; null falls back to the default bubble shape.
    val iconName: String? = look.iconId ?: serverCustomization?.widgetIconSVG
    val showLabel = hasLook && look.style == "label"
    val avatarUrl = if (hasLook && look.style == "avatar") look.avatar else null
    val imageUrl = if (hasLook) look.imageUrl else null
    // The owner's mascot on a soft light button (launcherStyle.ts "mascot"); an uploaded image wins (Launcher.resolve).
    val mascotStyle = hasLook && look.style == "mascot" && imageUrl == null

    // The entrance, the attention loop and the idle call (LauncherFxControllers):
    // all off unless the owner chose one; nothing with the phone's animations off.
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    val botId = Conferbot.activeBotId ?: "default"
    val fx = remember(botId) { LauncherFxHolder(context.applicationContext.getSharedPreferences("conferbot", android.content.Context.MODE_PRIVATE)) }
    DisposableEffect(fx) { onDispose { fx.dispose() } }
    // Decided as the settings arrive, before the launcher is drawn with them (so it never flashes in first).
    if (hasLook) fx.entrance.decide(look.entrance, botId, reduced, isChatOpen)
    @Suppress("UNUSED_VARIABLE") val fxVersion = fx.version

    // The icon's idle and new-message moments and the ripple (core LauncherMotion).
    val motion = com.conferbot.sdk.ui.compose.rememberLauncherMotion(
        iconId = iconName,
        mode = look.animation,
        chatOpen = isChatOpen,
        unreadCount = unreadCount,
        enabled = hasLook && imageUrl == null && avatarUrl == null && !mascotStyle,
        holdForAttention = { fx.attention.onLauncher },
    )
    val inMoment = motion.inMoment
    val accent = if (hasLook) look.solid else String.format("#%06x", fabColor.toArgb() and 0xFFFFFF)
    val cardShown = greeting.card != null && !isChatOpen
    val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    SideEffect {
        if (hasLook) {
            fx.attention.update(look.attention, true, reduced, isChatOpen, cardShown, inMoment, fx.entrance.busy, fx.launcher != null, showLabel)
        }
        val r = fx.launcher
        fx.idle.chatOpen = isChatOpen
        fx.idle.keyboard = imeUp
        fx.idle.entranceBusy = fx.entrance.busy
        fx.idle.cardShown = cardShown
        fx.idle.width = r?.width?.toDouble()
        fx.idle.height = r?.height?.toDouble()
        fx.idle.position = position.let { if (it == WidgetPosition.BOTTOM_LEFT) "left" else "right" }
        if (r != null && hasLook) {
            val screen = fx.screen
            fx.entrance.laidOut {
                LauncherEntrance.stage(
                    width = r.width.toDouble(),
                    height = r.height.toDouble(),
                    position = if (position == WidgetPosition.BOTTOM_LEFT) "left" else "right",
                    screenWidth = screen.width.toDouble(),
                    screenHeight = screen.height.toDouble(),
                    side = if (position == WidgetPosition.BOTTOM_LEFT) r.left.toDouble() else (screen.width - r.right).toDouble(),
                    bottom = (screen.height - r.bottom).toDouble(),
                    accent = accent,
                )
            }
        }
    }
    LaunchedEffect(fx, hasLook) {
        if (hasLook) fx.idle.start(look.idleCall, look.idleCallDelay, botId, reduced)
    }
    // Opening the chat by any route: the entrance ends at once, and no call comes for a few days.
    LaunchedEffect(fx, isChatOpen) {
        if (isChatOpen) {
            fx.entrance.chatOpened()
            fx.idle.chatOpened()
            fx.idle.stop()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, fx) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) fx.idle.appActive = true
            if (e == Lifecycle.Event.ON_PAUSE) fx.idle.appActive = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // One clock for the three effects: it redraws (not recomposes) while any of them plays.
    val fxLive = fx.entrance.active || fx.attention.running || fx.idle.perf != null
    LaunchedEffect(fx, fxLive) {
        while (fxLive) withFrameMillis { fx.tick = it }
    }
    val attentionOn = fx.attention.running && !isChatOpen
    val perf = fx.idle.perf?.takeIf { !isChatOpen }
    if (perf != null) {
        // The owner's mascot (shape, eyes, accessory, colour) in the launcher's colour.
        val look = MascotArt.lookOf(customizationMap)
        val key = "$look ${perf.mascot.arm} $accent"
        if (key != fx.mascotKey) {
            fx.mascotKey = key
            fx.mascot = MascotArt.layers(accent, perf.mascot.arm, look)
            fx.mascotOrigins = MascotArt.origins(look)
        }
    }

    // FAB press scale animation
    val fabScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(100),
        label = "fab_scale",
    )

    val fabAlignment = when (position) {
        WidgetPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
        WidgetPosition.BOTTOM_LEFT -> Alignment.BottomStart
    }

    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { fx.root = it }) {

        // ---- Scrim ----
        AnimatedVisibility(
            visible = isChatOpen,
            enter = fadeIn(tween(OVERLAY_TRANSITION_MS)),
            exit = fadeOut(tween(OVERLAY_TRANSITION_MS)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { isChatOpen = false },
            )
        }

        // ---- Chat overlay (bottom sheet) ----
        AnimatedVisibility(
            visible = isChatOpen,
            enter = slideInVertically(tween(OVERLAY_TRANSITION_MS)) { it } +
                    fadeIn(tween(OVERLAY_TRANSITION_MS)),
            exit = slideOutVertically(tween(OVERLAY_TRANSITION_MS)) { it } +
                    fadeOut(tween(OVERLAY_TRANSITION_MS)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                shadowElevation = 16.dp,
                color = Color.White,
            ) {
                val currentTheme = serverTheme
                if (currentTheme != null) {
                    ConferbotThemeProvider(theme = currentTheme) {
                        ConferBotChatScreen(onDismiss = { isChatOpen = false })
                    }
                } else {
                    ConferBotChatScreen(onDismiss = { isChatOpen = false })
                }
            }
        }

        // ---- The greeting card: 12 above the launcher, its edge in line with it ----
        // It stays drawn while it leaves (its collapse or fade, the figure's hand-off or bow), then goes.
        val shownCard = greeting.card?.takeIf { !isChatOpen }
        // Until the effect runs, the card that just went is still the last one shown (the same card, no remount).
        val justWent = greetingLeave.last?.takeIf { shownCard == null && greetingLeave.leaving == null }?.let { it to if (greetingLeave.closing) "close" else "open" }
        SideEffect {
            if (shownCard != null) {
                greetingLeave.last = shownCard
                greetingLeave.closing = false
                greetingLeave.leaving = null
            } else if (justWent != null) {
                greetingLeave.leaving = justWent
                greetingLeave.closing = false
            }
        }
        val card = shownCard ?: greetingLeave.leaving?.first ?: justWent?.first
        val leavingReason = if (shownCard != null) null else greetingLeave.leaving?.second ?: justWent?.second
        if (card != null) {
            val edge = maxOf(8.dp, offsetX - tucked)
            val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
            Box(
                Modifier
                    .align(if (position == WidgetPosition.BOTTOM_LEFT) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(
                        start = if (position == WidgetPosition.BOTTOM_LEFT) edge else 0.dp,
                        end = if (position == WidgetPosition.BOTTOM_RIGHT) edge else 0.dp,
                        bottom = offsetY + size + 12.dp,
                    )
                    .widthIn(max = screenWidth - 24.dp)
                    // Hidden until the launcher lands, then fading up; the wave / float loop.
                    .graphicsLayer {
                        fx.tick
                        val l = fx.entrance.lookAt(fx.entrance.elapsed)
                        val self = if (attentionOn && fx.attention.target == "greeting") fx.attention.part("self", fx.attention.elapsed) else null
                        alpha = (l?.greetingO ?: 1.0).toFloat().coerceIn(0f, 1f)
                        translationX = (self?.tx ?: 0.0).toFloat().dp.toPx()
                        translationY = ((l?.greetingTy ?: 0.0) + (self?.ty ?: 0.0)).toFloat().dp.toPx()
                    },
            ) {
                key(card) {
                    GreetingCard(
                        data = card,
                        leaving = leavingReason,
                        onGone = {
                            greetingLeave.leaving = null
                            greetingLeave.last = null
                        },
                        launcher = fx.launcher?.let { Triple(it.width, it.height, if (position == WidgetPosition.BOTTOM_LEFT) "left" else "right") },
                        onPress = {
                            if (greeting.card == null) return@GreetingCard
                            greeting.closeForNow()
                            if (card.click == "link" && card.linkUrl.isNotBlank()) {
                                try { uriHandler.openUri(card.linkUrl) } catch (_: Exception) {}
                            } else {
                                com.conferbot.sdk.ui.compose.LauncherOpened.at = System.currentTimeMillis()
                                isChatOpen = true
                                if (card.click == "send") sendWhenReady(card.text)
                            }
                        },
                        onClose = {
                            if (greeting.card == null) return@GreetingCard
                            greetingLeave.closing = true
                            greeting.dismiss()
                            fx.idle.greetingClosed()
                        },
                        onStarter = { text ->
                            if (greeting.card == null) return@GreetingCard
                            greeting.closeForNow()
                            com.conferbot.sdk.ui.compose.LauncherOpened.at = System.currentTimeMillis()
                            isChatOpen = true
                            sendWhenReady(text)
                        },
                    )
                }
                // The wave: a waving hand on the card's corner, unless its text has its own.
                if (shownCard != null && attentionOn && fx.attention.has("hand") && !card.text.contains(LauncherAttention.WAVING_HAND)) {
                    Text(
                        text = LauncherAttention.WAVING_HAND,
                        fontSize = 20.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = (-9).dp, y = (-13).dp)
                            .size(LauncherFxDraw.HAND_W.dp, LauncherFxDraw.HAND_H.dp)
                            .graphicsLayer {
                                fx.tick
                                rotationZ = (fx.attention.part("hand", fx.attention.elapsed)?.r ?: 0.0).toFloat()
                                transformOrigin = TransformOrigin(LauncherFxDraw.HAND_OX / LauncherFxDraw.HAND_W, LauncherFxDraw.HAND_OY / LauncherFxDraw.HAND_H)
                            },
                    )
                }
            }
        }

        // ---- Behind the launcher: the bounce's shadow, the idle call's mascot, the portal ----
        if (!isChatOpen) LauncherFxStage(fx, front = false, attentionOn = attentionOn, perf = perf)

        // ---- FAB ----
        // Hidden while the chat is open - the chat sheet has its own close
        // control and the toggle would overlap the send button
        if (!isChatOpen) {
        val density = LocalDensity.current.density
        Box(
            modifier = Modifier
                .align(fabAlignment)
                .padding(
                    start = if (position == WidgetPosition.BOTTOM_LEFT) offsetX else 0.dp,
                    end = if (position == WidgetPosition.BOTTOM_RIGHT) offsetX else 0.dp,
                    bottom = offsetY,
                )
                // Where the launcher sits (dp in the widget's box), before the effects move it.
                .onGloballyPositioned { c ->
                    val root = fx.root ?: return@onGloballyPositioned
                    if (!root.isAttached || !c.isAttached) return@onGloballyPositioned
                    val p = root.localPositionOf(c, Offset.Zero)
                    val r = Rect(p.x / density, p.y / density, (p.x + c.size.width) / density, (p.y + c.size.height) / density)
                    val old = fx.launcher
                    if (old == null || kotlin.math.abs(old.left - r.left) > 0.5f || kotlin.math.abs(old.top - r.top) > 0.5f ||
                        kotlin.math.abs(old.width - r.width) > 0.5f || kotlin.math.abs(old.height - r.height) > 0.5f) fx.launcher = r
                    val s = Size(root.size.width / density, root.size.height / density)
                    if (s != fx.screen) fx.screen = s
                }
                // The entrance: hidden until its reveal, then its arrival.
                .graphicsLayer {
                    fx.tick
                    val l = fx.entrance.lookAt(fx.entrance.elapsed) ?: return@graphicsLayer
                    alpha = l.o.toFloat().coerceIn(0f, 1f)
                    translationX = l.tx.toFloat().dp.toPx()
                    translationY = l.ty.toFloat().dp.toPx()
                    rotationZ = l.r.toFloat()
                    scaleX = l.sx.toFloat()
                    scaleY = l.sy.toFloat()
                }
                // The saucer's beam draws it top to bottom.
                .drawWithContent {
                    val shown = fx.entrance.lookAt(fx.entrance.elapsed)?.shown ?: 1.0
                    if (shown < 0.999) {
                        clipRect(-this.size.width, -this.size.height, this.size.width * 2, (this.size.height * shown).toFloat()) { this@drawWithContent.drawContent() }
                    } else drawContent()
                }
                // The attention loop moves the button.
                .graphicsLayer {
                    fx.tick
                    if (!attentionOn || !fx.attention.onLauncher) return@graphicsLayer
                    val v = fx.attention.part("self", fx.attention.elapsed) ?: return@graphicsLayer
                    translationX = (v.tx + v.txp / 100 * this.size.width / density).toFloat().dp.toPx()
                    translationY = v.ty.toFloat().dp.toPx()
                    rotationZ = v.r.toFloat()
                    scaleX = v.sx.toFloat()
                    scaleY = v.sy.toFloat()
                }
                // A knock or a tap jolts it.
                .graphicsLayer {
                    fx.tick
                    val p = perf ?: return@graphicsLayer
                    if (p.launcher.isEmpty()) return@graphicsLayer
                    val v = Keyframes.evaluate(p.launcher, fx.idle.elapsed)
                    translationX = v.tx.toFloat().dp.toPx()
                    translationY = v.ty.toFloat().dp.toPx()
                    scaleX = v.sx.toFloat()
                    scaleY = v.sy.toFloat()
                }
                // The orbit's spark round the button.
                .drawWithContent {
                    drawContent()
                    if (!attentionOn || !fx.attention.has("orbit")) return@drawWithContent
                    val v = fx.attention.part("orbit", fx.attention.elapsed) ?: return@drawWithContent
                    drawIntoCanvas { cv ->
                        val c = cv.nativeCanvas
                        val save = c.save()
                        c.scale(density, density)
                        LauncherFxDraw.orbit(c, this.size.width / density, this.size.height / density, v, fabColor.toArgb())
                        c.restoreToCount(save)
                    }
                },
        ) {
            // The pulse's two rings in the launcher's colour, under the button.
            if (attentionOn && fx.attention.has("ring")) {
                for (part in listOf("ring", "ring2")) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                fx.tick
                                val v = fx.attention.part(part, fx.attention.elapsed)
                                alpha = (v?.o ?: 0.0).toFloat().coerceIn(0f, 1f)
                                scaleX = (v?.sx ?: 1.0).toFloat()
                                scaleY = (v?.sy ?: 1.0).toFloat()
                            }
                            .border(2.5.dp, fabColor, fabShape),
                    )
                }
            }
            // The ripple: rings of the launcher's colour growing out from behind it.
            motion.rings.forEach { ring ->
                if (ring != null) {
                    Box(
                        Modifier
                            .align(if (position == WidgetPosition.BOTTOM_LEFT) Alignment.BottomStart else Alignment.BottomEnd)
                            .size(width = fabWidth, height = size)
                            .graphicsLayer {
                                scaleX = ring.scale.toFloat()
                                scaleY = ring.scale.toFloat()
                                alpha = ring.opacity.toFloat()
                            }
                            .background(fabColor, fabShape),
                    )
                }
            }
            BadgedBox(
                badge = {
                    if (config.showUnreadBadge && unreadCount > 0 && !isChatOpen) {
                        Badge(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White,
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                fontSize = 10.sp,
                            )
                        }
                    }
                },
            ) {
                Surface(
                    onClick = {
                        if (!isChatOpen) com.conferbot.sdk.ui.compose.LauncherOpened.at = System.currentTimeMillis()
                        isChatOpen = !isChatOpen
                    },
                    modifier = Modifier
                        .then(if (showLabel) Modifier.height(size).widthIn(min = size) else Modifier.size(width = fabWidth, height = size))
                        // A soft glow in the header colour (0 0 4px), as the web draws it,
                        // in the same layer as the press scale.
                        .graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                            shadowElevation = 6.dp.toPx()
                            shape = fabShape
                            clip = true
                            ambientShadowColor = glowColor
                            spotShadowColor = glowColor
                        },
                    shape = fabShape,
                    color = fabColor,
                    contentColor = inkColor,
                    interactionSource = fabInteractionSource,
                ) {
                    Box(
                        modifier = Modifier
                            .then(if (showLabel) Modifier.fillMaxHeight() else Modifier.fillMaxSize())
                            .then(if (fabBrush != null) Modifier.background(fabBrush, fabShape) else Modifier)
                            // The mascot's soft light button while closed; open, the brand colour.
                            .then(
                                if (mascotStyle && !isChatOpen) Modifier.drawBehind {
                                    drawRect(com.conferbot.sdk.ui.compose.mascot.mascotButtonBrush(look.mascot.light, this.size.width, this.size.height))
                                } else Modifier,
                            )
                            // The shine's gloss sweeping across the button (the button clips it).
                            .drawWithContent {
                                drawContent()
                                if (!attentionOn || !fx.attention.has("shine")) return@drawWithContent
                                val v = fx.attention.part("shine", fx.attention.elapsed) ?: return@drawWithContent
                                drawIntoCanvas { cv ->
                                    val c = cv.nativeCanvas
                                    val save = c.save()
                                    c.scale(density, density)
                                    LauncherFxDraw.gloss(c, this.size.width / density, this.size.height / density, v)
                                    c.restoreToCount(save)
                                }
                            }
                            .then(if (showLabel) Modifier.padding(horizontal = ((size - size * 0.52f) / 2)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            // The mascot is drawn over the button (below), its ears and hat over the rim.
                            mascotStyle && !isChatOpen -> Box(Modifier.fillMaxSize())
                            imageUrl != null -> coil.compose.AsyncImage(
                                model = imageUrl,
                                contentDescription = "Open chat",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(size * 0.6f).clip(androidx.compose.foundation.shape.CircleShape),
                            )
                            avatarUrl != null -> Box(Modifier.fillMaxSize().padding(3.dp)) {
                                // The photo fills the button inside a thin ring of its colour, with an online dot.
                                coil.compose.AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Open chat",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(fabShape).background(Color.White),
                                )
                            }
                            showLabel -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(size * 0.16f),
                            ) {
                                Canvas(Modifier.size(size * 0.52f)) { drawLauncherIcon(iconName, inkColor, fabColor, this.size.minDimension) }
                                Text(
                                    text = look.label,
                                    color = inkColor,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    fontSize = (size.value * 0.3f).coerceIn(13f, 16f).sp,
                                    maxLines = 1,
                                )
                            }
                            else -> Canvas(modifier = Modifier.fillMaxSize()) {
                                val iconPx = (if (hasLook) look.glyph.dp.toPx() else this.size.minDimension * 0.6f)
                                drawContext.canvas.save()
                                drawContext.canvas.translate((this.size.width - iconPx) / 2f, (this.size.height - iconPx) / 2f)
                                drawLauncherIcon(iconName, inkColor, fabColor, iconPx, motion.frame)
                                drawContext.canvas.restore()
                            }
                        }
                    }
                }
                if (mascotStyle && !isChatOpen) {
                    com.conferbot.sdk.ui.compose.mascot.LauncherMascot(
                        m = look.mascot,
                        boxW = fabWidth.value,
                        boxH = size.value,
                        unread = unreadCount,
                        chatOpen = isChatOpen,
                        reduced = reduced,
                        modifier = Modifier.graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                        },
                    )
                }
                if (avatarUrl != null) {
                    val dot = maxOf(10f, size.value * 0.24f).dp
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = if (size >= 56.dp) 2.dp else 1.dp, bottom = if (size >= 56.dp) 2.dp else 1.dp)
                            .size(dot)
                            .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                            .padding(2.dp)
                            .background(Color(0xFF22C55E), androidx.compose.foundation.shape.CircleShape),
                    )
                }
            }
        }
        }

        // ---- In front of the launcher: a knock / tap mascot and the other entrances ----
        if (!isChatOpen) LauncherFxStage(fx, front = true, attentionOn = attentionOn, perf = perf)

        // ---- The idle call's speech bubble, kept on the screen, its tail on the mascot (none while the greeting is up) ----
        val rect = fx.launcher
        if (perf != null && perf.talks && rect != null) {
            var bubbleSize by remember(perf) { mutableStateOf(IntSize.Zero) }
            val density = LocalDensity.current.density
            val bw = bubbleSize.width / density
            val bh = bubbleSize.height / density
            val screenW = fx.screen.width
            val left = (rect.left + perf.sayX.toFloat() - bw / 2).coerceIn(8f, maxOf(8f, screenW - bw - 8))
            val tail = (perf.sayX.toFloat() - (left - rect.left)).coerceIn(14f, maxOf(14f, bw - 14))
            fun bubbleNow(): FxValues = Keyframes.evaluate(listOf(perf.bubble), fx.idle.elapsed, mapOf("o" to 0.0))
            IdleCallBubble(
                line = look.idleCallText,
                tail = tail,
                onTap = {
                    if (bubbleNow().o > 0.05) {
                        fx.idle.stop()
                        LauncherOpened.at = System.currentTimeMillis()
                        isChatOpen = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .onSizeChanged { bubbleSize = it }
                    .offset { IntOffset((left * density).roundToInt(), ((rect.top + perf.sayY.toFloat() - 10 - bh) * density).roundToInt()) }
                    .graphicsLayer {
                        fx.tick
                        val v = bubbleNow()
                        alpha = if (bubbleSize == IntSize.Zero) 0f else v.o.toFloat().coerceIn(0f, 1f)
                        translationY = v.ty.toFloat().dp.toPx()
                        scaleX = v.sx.toFloat()
                        scaleY = v.sy.toFloat()
                    },
            )
        }
    }
}

/**
 * What the launcher's effects keep between frames: the three controllers,
 * a version bumped when what to draw changes (recomposes), a frame tick
 * (redraws only), and where the launcher sits.
 */
internal class LauncherFxHolder(prefs: android.content.SharedPreferences?) {
    val entrance = LauncherEntranceController()
    val attention = LauncherAttentionController()
    val idle = LauncherIdleCallController(store = PrefsIdleCallRestStore(prefs))
    var version by mutableStateOf(0)
    var tick by mutableStateOf(0L)
    /** The launcher's box and the widget's size, in dp. */
    var launcher by mutableStateOf<Rect?>(null)
    var screen by mutableStateOf(Size.Zero)
    var root: LayoutCoordinates? = null
    var mascot: MascotArt.Layers? = null
    var mascotOrigins: MascotArt.Origins = MascotArt.ORIGINS
    var mascotKey = ""

    init {
        val bump = { version += 1 }
        entrance.onChange = bump
        attention.onChange = bump
        idle.onChange = bump
    }

    fun dispose() {
        entrance.dispose()
        attention.onChange = null
        idle.dispose()
    }
}

/** The greeting card that just went, drawn while it leaves; `closing` when its x sent it. */
private class GreetingLeave {
    var last: GreetingCardData? = null
    var leaving by mutableStateOf<Pair<GreetingCardData, String>?>(null)
    var closing = false
}

/** The effects drawn round the launcher, behind it or in front of it, in its own box (dp); they reach past it. */
@Composable
private fun LauncherFxStage(fx: LauncherFxHolder, front: Boolean, attentionOn: Boolean, perf: com.conferbot.sdk.core.customization.LauncherIdleCall.Performance?) {
    val plan = if (fx.entrance.phase == "play") fx.entrance.plan else null
    val shadow = !front && attentionOn && fx.attention.has("shadow")
    val mascot = perf != null && perf.front == front && fx.mascot != null
    if (fx.launcher == null || !(shadow || mascot || (plan != null && plan.behind != front))) return
    Canvas(Modifier.fillMaxSize()) {
        fx.tick
        val r = fx.launcher ?: return@Canvas
        drawIntoCanvas { cv ->
            val c = cv.nativeCanvas
            val save = c.save()
            c.scale(density, density)
            c.translate(r.left, r.top)
            if (shadow) fx.attention.part("shadow", fx.attention.elapsed)?.let { LauncherFxDraw.bounceShadow(c, r.width, r.height, it) }
            if (mascot) LauncherFxDraw.idleCall(c, perf!!, fx.mascot!!, fx.mascotOrigins, fx.idle.elapsed)
            if (plan != null && plan.behind != front) LauncherFxDraw.entrance(c, plan, fx.entrance.elapsed)
            c.restoreToCount(save)
        }
    }
}

// ============================================================
// CONVENIENCE WRAPPER
// ============================================================

/**
 * Overlays [ConferBotWidget] on top of arbitrary host content.
 *
 * Usage:
 * ```
 * ConferBotWidgetScope {
 *     MyAppContent()
 * }
 * ```
 *
 * @param config  Widget configuration (falls back to server customizations).
 * @param content The host application content.
 */
@Composable
fun ConferBotWidgetScope(
    config: ConferBotWidgetConfig = ConferBotWidgetConfig(),
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        ConferBotWidget(config)
    }
}
