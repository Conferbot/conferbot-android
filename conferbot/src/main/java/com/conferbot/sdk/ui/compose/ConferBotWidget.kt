package com.conferbot.sdk.ui.compose

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

// ============================================================
// CONSTANTS
// ============================================================

/** Duration (ms) for the chat overlay enter/exit transitions. */
private const val OVERLAY_TRANSITION_MS = 300

/** Duration (ms) for the FAB icon crossfade. */
private const val ICON_CROSSFADE_MS = 200

/** Delay (ms) before the CTA tooltip auto-appears. */
private const val CTA_SHOW_DELAY_MS = 2_000L

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
fun WidgetCtaTooltip(text: String, onDismiss: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .widthIn(max = 212.dp)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss,
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        contentColor = Color(0xFF1F2937),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
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
 *  - CTA text   : chatIconCtaText — shown 2 s after mount, dismissed on tap or chat open
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
    var isCtaVisible by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }

    val unreadCount by Conferbot.unreadCount.collectAsState()
    val serverTheme by Conferbot.serverTheme.collectAsState()
    val serverCustomization by Conferbot.serverCustomization.collectAsState()

    // Synchronise visibility with the singleton so the SDK can suppress in-app notifications.
    LaunchedEffect(isChatOpen) {
        Conferbot.setChatVisible(isChatOpen)
        if (isChatOpen) {
            Conferbot.resetUnreadCount()
            isCtaVisible = false
        }
    }

    // Auto-show CTA tooltip after a short delay (only when chat is closed).
    val ctaText = serverCustomization?.chatIconCtaText
    LaunchedEffect(ctaText) {
        if (!ctaText.isNullOrBlank()) {
            delay(CTA_SHOW_DELAY_MS)
            if (!isChatOpen) isCtaVisible = true
        }
    }

    // ---- Resolve server-driven values with fallbacks ----

    val fabColor: Color = run {
        fun tryParse(hex: String?): Color? {
            if (hex.isNullOrBlank()) return null
            return try {
                Color(android.graphics.Color.parseColor(hex))
            } catch (_: Exception) {
                null
            }
        }
        tryParse(serverCustomization?.widgetIconBgColor)
            ?: tryParse(serverCustomization?.headerBgColor)
            ?: config.backgroundColor
            ?: DefaultFabColor
    }

    val position: WidgetPosition = when (serverCustomization?.widgetPosition?.lowercase()) {
        "left" -> WidgetPosition.BOTTOM_LEFT
        "right" -> WidgetPosition.BOTTOM_RIGHT
        else -> config.position
    }

    val size: Dp = serverCustomization?.widgetSize?.dp ?: config.size

    val offsetX: Dp = when (position) {
        WidgetPosition.BOTTOM_LEFT ->
            serverCustomization?.widgetOffsetLeft?.dp ?: config.offsetX
        WidgetPosition.BOTTOM_RIGHT ->
            serverCustomization?.widgetOffsetRight?.dp ?: config.offsetX
    }

    val offsetY: Dp = serverCustomization?.widgetOffsetBottom?.dp ?: config.offsetY

    // Border radius: server value > config value > fully circular (size / 2)
    val borderRadius: Dp = serverCustomization?.widgetBorderRadius?.dp
        ?: config.borderRadius
        ?: (size / 2)

    // CTA tooltip corner radius capped at 20 dp (mirrors web: Math.min(widgetBorderRadius ?? 50, 20))
    val ctaBorderRadius: Dp = minOf(
        serverCustomization?.widgetBorderRadius?.toFloat() ?: 50f,
        20f,
    ).dp

    // Icon name for the SVG renderer; null falls back to the default bubble shape.
    val iconName: String? = serverCustomization?.widgetIconSVG

    // CTA is placed at offsetX + size + 10 dp from the screen edge, same bottom as FAB.
    val ctaOffsetFromEdge: Dp = offsetX + size + 10.dp

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
    val fabShape = RoundedCornerShape(borderRadius)

    Box(modifier = modifier.fillMaxSize()) {

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

        // ---- CTA tooltip ----
        if (!ctaText.isNullOrBlank()) {
            AnimatedVisibility(
                visible = isCtaVisible && !isChatOpen,
                enter = fadeIn(tween(OVERLAY_TRANSITION_MS)),
                exit = fadeOut(tween(OVERLAY_TRANSITION_MS)),
                modifier = Modifier
                    .align(
                        if (position == WidgetPosition.BOTTOM_LEFT) Alignment.BottomStart
                        else Alignment.BottomEnd,
                    )
                    .padding(
                        start = if (position == WidgetPosition.BOTTOM_LEFT) ctaOffsetFromEdge else 0.dp,
                        end = if (position == WidgetPosition.BOTTOM_RIGHT) ctaOffsetFromEdge else 0.dp,
                        bottom = offsetY,
                    ),
            ) {
                Box(modifier = Modifier.widthIn(max = 212.dp)) {
                    WidgetCtaTooltip(
                        text = ctaText,
                        onDismiss = { isCtaVisible = false },
                    )
                }
            }
        }

        // ---- FAB ----
        Box(
            modifier = Modifier
                .align(fabAlignment)
                .padding(
                    start = if (position == WidgetPosition.BOTTOM_LEFT) offsetX else 0.dp,
                    end = if (position == WidgetPosition.BOTTOM_RIGHT) offsetX else 0.dp,
                    bottom = offsetY,
                ),
        ) {
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
                        isCtaVisible = false
                        isChatOpen = !isChatOpen
                    },
                    modifier = Modifier
                        .size(size)
                        .shadow(elevation = 12.dp, shape = fabShape)
                        .graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                        },
                    shape = fabShape,
                    color = fabColor,
                    contentColor = Color.White,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = isChatOpen,
                            transitionSpec = {
                                fadeIn(tween(ICON_CROSSFADE_MS)) togetherWith
                                        fadeOut(tween(ICON_CROSSFADE_MS))
                            },
                            label = "fab_icon",
                        ) { chatOpen ->
                            if (chatOpen) {
                                // Close icon — use the standard Material close vector
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close chat",
                                        modifier = Modifier.size(size * 0.5f),
                                        tint = Color.White,
                                    )
                                }
                            } else {
                                // Custom SVG bubble icon — drawn at 60 % of FAB size
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val iconPx = this.size.minDimension * 0.6f
                                    // Translate canvas origin so the icon is centered
                                    val offsetPx = (this.size.minDimension - iconPx) / 2f
                                    drawContext.canvas.save()
                                    drawContext.canvas.translate(offsetPx, offsetPx)
                                    drawBubbleIcon(iconName, Color.White, iconPx)
                                    drawContext.canvas.restore()
                                }
                            }
                        }
                    }
                }
            }
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
