package com.conferbot.sdk.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Comprehensive theme configuration for Conferbot SDK
 *
 * This data class holds all theming information including colors, typography,
 * shapes, spacing, and animation configurations. It matches the web widget
 * capabilities for consistent cross-platform theming.
 */
data class ConferbotTheme(
    /**
     * Color scheme for the chat interface
     */
    val colors: ConferbotColors,

    /**
     * Typography settings including font family and sizes
     */
    val typography: ConferbotTypography,

    /**
     * Shape configurations for UI elements
     */
    val shapes: ConferbotShapes,

    /**
     * Spacing values for consistent layouts
     */
    val spacing: ConferbotSpacing,

    /**
     * Animation durations and configurations
     */
    val animations: ConferbotAnimations,

    /**
     * Background configuration for the chat view
     */
    val background: ConferbotBackground = ConferbotBackground.SolidColor(Color.White),

    /**
     * Whether this is a dark theme
     */
    val isDarkTheme: Boolean = false,

    /**
     * Optional name for the theme
     */
    val name: String = "Custom"
) {
    companion object
}

/**
 * Color configuration for the Conferbot theme
 *
 * Includes all colors needed for the chat interface including:
 * - Primary/secondary brand colors
 * - Surface and background colors
 * - Message bubble colors for different sender types
 * - Status indicator colors
 */
data class ConferbotColors(
    // Brand colors
    val primary: Color,
    val secondary: Color,
    val tertiary: Color = primary.copy(alpha = 0.7f),

    // Surface colors
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val error: Color,

    // Content colors (text/icons on surfaces)
    val onPrimary: Color,
    val onSecondary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val onError: Color,

    // Message bubble colors
    val botBubble: Color,
    val botBubbleText: Color,
    val userBubble: Color,
    val userBubbleText: Color,
    val agentBubble: Color,
    val agentBubbleText: Color,
    val systemMessageText: Color,

    // Input area
    val inputBackground: Color,
    val inputText: Color,
    val inputPlaceholder: Color,
    val inputBorder: Color,
    val inputBorderFocused: Color,

    // Header
    val headerBackground: Color,
    val headerText: Color,
    val headerIcon: Color,

    // Status indicators
    val online: Color,
    val offline: Color,
    val typing: Color,
    val sending: Color,
    val sent: Color,
    val delivered: Color,
    val read: Color,

    // Interactive elements
    val buttonBackground: Color,
    val buttonText: Color,
    val buttonDisabled: Color,
    val buttonDisabledText: Color,
    val link: Color,

    // Dividers and outlines
    val divider: Color,
    val outline: Color,

    // Timestamps
    val timestamp: Color
)

/**
 * Typography configuration for the Conferbot theme
 *
 * Defines font family and text sizes for different UI elements
 */
data class ConferbotTypography(
    /**
     * Primary font family for all text
     */
    val fontFamily: FontFamily,

    /**
     * Secondary font family (optional, defaults to primary)
     */
    val secondaryFontFamily: FontFamily = fontFamily,

    /**
     * Header text size (chat title, dialogs)
     */
    val headerSize: TextUnit,

    /**
     * Subtitle text size
     */
    val subtitleSize: TextUnit,

    /**
     * Body text size (general content)
     */
    val bodySize: TextUnit,

    /**
     * Caption text size (small labels)
     */
    val captionSize: TextUnit,

    /**
     * Message text size in chat bubbles
     */
    val messageSize: TextUnit,

    /**
     * Input field text size
     */
    val inputSize: TextUnit,

    /**
     * Button text size
     */
    val buttonSize: TextUnit,

    /**
     * Timestamp text size
     */
    val timestampSize: TextUnit,

    /**
     * Default font weight for body text
     */
    val bodyWeight: FontWeight = FontWeight.Normal,

    /**
     * Font weight for headers
     */
    val headerWeight: FontWeight = FontWeight.SemiBold,

    /**
     * Font weight for buttons
     */
    val buttonWeight: FontWeight = FontWeight.Medium,

    /**
     * Line height multiplier for message text
     */
    val messageLineHeight: TextUnit = 1.4.sp
)

/**
 * Shape configuration for UI elements
 *
 * Defines corner radius values for various components
 */
data class ConferbotShapes(
    /**
     * Corner radius for message bubbles
     */
    val bubbleRadius: Dp,

    /**
     * Small bubble corner (for connected side)
     */
    val bubbleRadiusSmall: Dp = 4.dp,

    /**
     * Corner radius for buttons
     */
    val buttonRadius: Dp,

    /**
     * Corner radius for cards
     */
    val cardRadius: Dp,

    /**
     * Corner radius for input fields
     */
    val inputRadius: Dp,

    /**
     * Corner radius for the chat header
     */
    val headerRadius: Dp = 0.dp,

    /**
     * Corner radius for image previews
     */
    val imageRadius: Dp,

    /**
     * Corner radius for avatars (typically circular)
     */
    val avatarRadius: Dp = 50.dp,

    /**
     * Corner radius for chips/tags
     */
    val chipRadius: Dp = 16.dp,

    /**
     * Corner radius for dialogs/bottom sheets
     */
    val dialogRadius: Dp = 16.dp
)

/**
 * Spacing configuration for consistent layouts
 *
 * Provides standard spacing values following a design system
 */
data class ConferbotSpacing(
    /**
     * Extra small spacing (4dp)
     */
    val xs: Dp,

    /**
     * Small spacing (8dp)
     */
    val sm: Dp,

    /**
     * Medium spacing (12dp)
     */
    val md: Dp,

    /**
     * Large spacing (16dp)
     */
    val lg: Dp,

    /**
     * Extra large spacing (24dp)
     */
    val xl: Dp,

    /**
     * Extra extra large spacing (32dp)
     */
    val xxl: Dp = 32.dp,

    /**
     * Horizontal padding for message bubbles
     */
    val bubblePaddingHorizontal: Dp = 12.dp,

    /**
     * Vertical padding for message bubbles
     */
    val bubblePaddingVertical: Dp = 10.dp,

    /**
     * Spacing between messages
     */
    val messageSpacing: Dp = 10.dp,

    /**
     * Spacing between grouped messages from same sender
     */
    val groupedMessageSpacing: Dp = 2.dp,

    /**
     * Content padding for the chat list
     */
    val chatContentPadding: Dp = 14.dp,

    /**
     * Maximum width for message bubbles
     */
    val maxBubbleWidth: Dp = 260.dp,

    /**
     * Avatar size
     */
    val avatarSize: Dp = 32.dp,

    /**
     * Small avatar size
     */
    val avatarSizeSmall: Dp = 24.dp
)

/**
 * Animation configuration for the theme
 *
 * Defines duration and timing for various animations
 */
data class ConferbotAnimations(
    /**
     * Duration for typing indicator animation (ms)
     */
    val typingDuration: Int,

    /**
     * Duration for message fade in animation (ms)
     */
    val messageFadeDuration: Int,

    /**
     * Duration for message slide animation (ms)
     */
    val messageSlideDuration: Int = 200,

    /**
     * Duration for button press feedback (ms)
     */
    val buttonPressDuration: Int,

    /**
     * Duration for screen transitions (ms)
     */
    val transitionDuration: Int = 300,

    /**
     * Duration for scroll to bottom animation (ms)
     */
    val scrollDuration: Int = 200,

    /**
     * Duration for connection status banner animation (ms)
     */
    val statusBannerDuration: Int = 300,

    /**
     * Whether to enable animations globally
     */
    val enabled: Boolean = true,

    /**
     * Whether to reduce motion (accessibility)
     */
    val reduceMotion: Boolean = false
)

/**
 * Background configuration for the chat view
 *
 * Supports solid color, gradient, and image backgrounds
 */
sealed class ConferbotBackground {
    /**
     * Solid color background
     */
    data class SolidColor(val color: Color) : ConferbotBackground()

    /**
     * Gradient background with two colors
     */
    data class Gradient(
        val startColor: Color,
        val endColor: Color,
        val angle: Float = 0f // 0 = top to bottom, 90 = left to right
    ) : ConferbotBackground()

    /**
     * Gradient background with multiple color stops
     */
    data class MultiGradient(
        val colors: List<Color>,
        val stops: List<Float> = emptyList(), // If empty, colors are evenly distributed
        val angle: Float = 0f
    ) : ConferbotBackground()

    /**
     * Image background
     */
    data class Image(
        val imageUrl: String? = null,
        val imageResId: Int? = null,
        val scaleType: ImageScaleType = ImageScaleType.COVER,
        val overlayColor: Color? = null,
        val overlayOpacity: Float = 0f
    ) : ConferbotBackground()

    /**
     * Pattern background (repeating image/pattern)
     */
    data class Pattern(
        val patternUrl: String? = null,
        val patternResId: Int? = null,
        val tint: Color? = null,
        val opacity: Float = 1f
    ) : ConferbotBackground()
}

/**
 * Scale type for background images
 */
enum class ImageScaleType {
    /**
     * Scale to fill, may crop
     */
    COVER,

    /**
     * Scale to fit, may have letterboxing
     */
    CONTAIN,

    /**
     * Stretch to fill
     */
    FILL,

    /**
     * Repeat the image as tiles
     */
    TILE
}
