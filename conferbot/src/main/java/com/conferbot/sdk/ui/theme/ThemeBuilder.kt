package com.conferbot.sdk.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Builder class for creating custom Conferbot themes
 *
 * Provides a fluent API for customizing theme properties with sensible defaults.
 *
 * Usage:
 * ```
 * val customTheme = ConferbotTheme.Builder()
 *     .primaryColor(Color.Blue)
 *     .fontFamily(FontFamily.SansSerif)
 *     .bubbleRadius(12.dp)
 *     .build()
 * ```
 */
class ConferbotThemeBuilder {
    private var baseTheme: ConferbotTheme = LightTheme
    private var isDarkTheme: Boolean = false
    private var name: String = "Custom"

    // Colors
    private var primaryColor: Color? = null
    private var secondaryColor: Color? = null
    private var backgroundColor: Color? = null
    private var surfaceColor: Color? = null
    private var errorColor: Color? = null

    // Message bubble colors
    private var botBubbleColor: Color? = null
    private var botBubbleTextColor: Color? = null
    private var userBubbleColor: Color? = null
    private var userBubbleTextColor: Color? = null
    private var agentBubbleColor: Color? = null
    private var agentBubbleTextColor: Color? = null

    // Header colors
    private var headerBackgroundColor: Color? = null
    private var headerTextColor: Color? = null

    // Input colors
    private var inputBackgroundColor: Color? = null
    private var inputTextColor: Color? = null
    private var inputBorderColor: Color? = null

    // Typography
    private var fontFamily: FontFamily? = null
    private var headerSize: TextUnit? = null
    private var bodySize: TextUnit? = null
    private var messageSize: TextUnit? = null
    private var captionSize: TextUnit? = null
    private var inputSize: TextUnit? = null
    private var buttonSize: TextUnit? = null

    // Shapes
    private var bubbleRadius: Dp? = null
    private var buttonRadius: Dp? = null
    private var cardRadius: Dp? = null
    private var inputRadius: Dp? = null
    private var imageRadius: Dp? = null

    // Spacing
    private var spacingXs: Dp? = null
    private var spacingSm: Dp? = null
    private var spacingMd: Dp? = null
    private var spacingLg: Dp? = null
    private var spacingXl: Dp? = null
    private var messageSpacing: Dp? = null
    private var maxBubbleWidth: Dp? = null

    // Animations
    private var typingDuration: Int? = null
    private var messageFadeDuration: Int? = null
    private var buttonPressDuration: Int? = null
    private var animationsEnabled: Boolean = true

    // Background
    private var background: ConferbotBackground? = null

    /**
     * Set the base theme to modify (default is LightTheme)
     */
    fun baseTheme(theme: ConferbotTheme) = apply {
        this.baseTheme = theme
        this.isDarkTheme = theme.isDarkTheme
    }

    /**
     * Set whether this is a dark theme
     */
    fun isDarkTheme(isDark: Boolean) = apply {
        this.isDarkTheme = isDark
    }

    /**
     * Set the theme name
     */
    fun name(name: String) = apply {
        this.name = name
    }

    // ==================== Color Setters ====================

    /**
     * Set the primary brand color (also updates user bubble, header, buttons)
     */
    fun primaryColor(color: Color) = apply {
        this.primaryColor = color
    }

    /**
     * Set the secondary color
     */
    fun secondaryColor(color: Color) = apply {
        this.secondaryColor = color
    }

    /**
     * Set the background color
     */
    fun backgroundColor(color: Color) = apply {
        this.backgroundColor = color
    }

    /**
     * Set the surface color
     */
    fun surfaceColor(color: Color) = apply {
        this.surfaceColor = color
    }

    /**
     * Set the error color
     */
    fun errorColor(color: Color) = apply {
        this.errorColor = color
    }

    /**
     * Set bot message bubble colors
     */
    fun botBubbleColors(background: Color, text: Color) = apply {
        this.botBubbleColor = background
        this.botBubbleTextColor = text
    }

    /**
     * Set user message bubble colors
     */
    fun userBubbleColors(background: Color, text: Color) = apply {
        this.userBubbleColor = background
        this.userBubbleTextColor = text
    }

    /**
     * Set agent message bubble colors
     */
    fun agentBubbleColors(background: Color, text: Color) = apply {
        this.agentBubbleColor = background
        this.agentBubbleTextColor = text
    }

    /**
     * Set header colors
     */
    fun headerColors(background: Color, text: Color) = apply {
        this.headerBackgroundColor = background
        this.headerTextColor = text
    }

    /**
     * Set input field colors
     */
    fun inputColors(background: Color, text: Color, border: Color) = apply {
        this.inputBackgroundColor = background
        this.inputTextColor = text
        this.inputBorderColor = border
    }

    // ==================== Typography Setters ====================

    /**
     * Set the font family for all text
     */
    fun fontFamily(fontFamily: FontFamily) = apply {
        this.fontFamily = fontFamily
    }

    /**
     * Set the header text size
     */
    fun headerSize(size: TextUnit) = apply {
        this.headerSize = size
    }

    /**
     * Set the body text size
     */
    fun bodySize(size: TextUnit) = apply {
        this.bodySize = size
    }

    /**
     * Set the message text size
     */
    fun messageSize(size: TextUnit) = apply {
        this.messageSize = size
    }

    /**
     * Set the caption text size
     */
    fun captionSize(size: TextUnit) = apply {
        this.captionSize = size
    }

    /**
     * Set the input field text size
     */
    fun inputSize(size: TextUnit) = apply {
        this.inputSize = size
    }

    /**
     * Set the button text size
     */
    fun buttonSize(size: TextUnit) = apply {
        this.buttonSize = size
    }

    /**
     * Set all text sizes at once
     */
    fun textSizes(
        header: TextUnit = 20.sp,
        body: TextUnit = 16.sp,
        message: TextUnit = 15.sp,
        caption: TextUnit = 12.sp,
        input: TextUnit = 16.sp,
        button: TextUnit = 14.sp
    ) = apply {
        this.headerSize = header
        this.bodySize = body
        this.messageSize = message
        this.captionSize = caption
        this.inputSize = input
        this.buttonSize = button
    }

    // ==================== Shape Setters ====================

    /**
     * Set the message bubble corner radius
     */
    fun bubbleRadius(radius: Dp) = apply {
        this.bubbleRadius = radius
    }

    /**
     * Set the button corner radius
     */
    fun buttonRadius(radius: Dp) = apply {
        this.buttonRadius = radius
    }

    /**
     * Set the card corner radius
     */
    fun cardRadius(radius: Dp) = apply {
        this.cardRadius = radius
    }

    /**
     * Set the input field corner radius
     */
    fun inputRadius(radius: Dp) = apply {
        this.inputRadius = radius
    }

    /**
     * Set the image corner radius
     */
    fun imageRadius(radius: Dp) = apply {
        this.imageRadius = radius
    }

    /**
     * Set all corner radii at once
     */
    fun cornerRadii(
        bubble: Dp = 16.dp,
        button: Dp = 12.dp,
        card: Dp = 12.dp,
        input: Dp = 24.dp,
        image: Dp = 12.dp
    ) = apply {
        this.bubbleRadius = bubble
        this.buttonRadius = button
        this.cardRadius = card
        this.inputRadius = input
        this.imageRadius = image
    }

    // ==================== Spacing Setters ====================

    /**
     * Set the extra small spacing
     */
    fun spacingXs(spacing: Dp) = apply {
        this.spacingXs = spacing
    }

    /**
     * Set the small spacing
     */
    fun spacingSm(spacing: Dp) = apply {
        this.spacingSm = spacing
    }

    /**
     * Set the medium spacing
     */
    fun spacingMd(spacing: Dp) = apply {
        this.spacingMd = spacing
    }

    /**
     * Set the large spacing
     */
    fun spacingLg(spacing: Dp) = apply {
        this.spacingLg = spacing
    }

    /**
     * Set the extra large spacing
     */
    fun spacingXl(spacing: Dp) = apply {
        this.spacingXl = spacing
    }

    /**
     * Set spacing between messages
     */
    fun messageSpacing(spacing: Dp) = apply {
        this.messageSpacing = spacing
    }

    /**
     * Set maximum width for message bubbles
     */
    fun maxBubbleWidth(width: Dp) = apply {
        this.maxBubbleWidth = width
    }

    /**
     * Set all spacing values at once
     */
    fun spacing(
        xs: Dp = 4.dp,
        sm: Dp = 8.dp,
        md: Dp = 12.dp,
        lg: Dp = 16.dp,
        xl: Dp = 24.dp
    ) = apply {
        this.spacingXs = xs
        this.spacingSm = sm
        this.spacingMd = md
        this.spacingLg = lg
        this.spacingXl = xl
    }

    // ==================== Animation Setters ====================

    /**
     * Set the typing indicator animation duration (ms)
     */
    fun typingDuration(duration: Int) = apply {
        this.typingDuration = duration
    }

    /**
     * Set the message fade-in duration (ms)
     */
    fun messageFadeDuration(duration: Int) = apply {
        this.messageFadeDuration = duration
    }

    /**
     * Set the button press feedback duration (ms)
     */
    fun buttonPressDuration(duration: Int) = apply {
        this.buttonPressDuration = duration
    }

    /**
     * Enable or disable animations
     */
    fun animationsEnabled(enabled: Boolean) = apply {
        this.animationsEnabled = enabled
    }

    /**
     * Set all animation durations at once
     */
    fun animations(
        typing: Int = 1000,
        messageFade: Int = 200,
        buttonPress: Int = 100
    ) = apply {
        this.typingDuration = typing
        this.messageFadeDuration = messageFade
        this.buttonPressDuration = buttonPress
    }

    // ==================== Background Setters ====================

    /**
     * Set a solid color background
     */
    fun background(color: Color) = apply {
        this.background = ConferbotBackground.SolidColor(color)
    }

    /**
     * Set a gradient background
     */
    fun gradientBackground(startColor: Color, endColor: Color, angle: Float = 0f) = apply {
        this.background = ConferbotBackground.Gradient(startColor, endColor, angle)
    }

    /**
     * Set a multi-color gradient background
     */
    fun gradientBackground(colors: List<Color>, angle: Float = 0f) = apply {
        this.background = ConferbotBackground.MultiGradient(colors, emptyList(), angle)
    }

    /**
     * Set an image background from URL
     */
    fun imageBackground(
        url: String,
        scaleType: ImageScaleType = ImageScaleType.COVER,
        overlayColor: Color? = null,
        overlayOpacity: Float = 0f
    ) = apply {
        this.background = ConferbotBackground.Image(
            imageUrl = url,
            scaleType = scaleType,
            overlayColor = overlayColor,
            overlayOpacity = overlayOpacity
        )
    }

    /**
     * Set an image background from resource ID
     */
    fun imageBackground(
        resId: Int,
        scaleType: ImageScaleType = ImageScaleType.COVER,
        overlayColor: Color? = null,
        overlayOpacity: Float = 0f
    ) = apply {
        this.background = ConferbotBackground.Image(
            imageResId = resId,
            scaleType = scaleType,
            overlayColor = overlayColor,
            overlayOpacity = overlayOpacity
        )
    }

    /**
     * Set a custom background
     */
    fun background(background: ConferbotBackground) = apply {
        this.background = background
    }

    // ==================== Build ====================

    /**
     * Build the theme with all configured values
     */
    fun build(): ConferbotTheme {
        val baseColors = baseTheme.colors
        val baseTypography = baseTheme.typography
        val baseShapes = baseTheme.shapes
        val baseSpacing = baseTheme.spacing
        val baseAnimations = baseTheme.animations

        // Build colors - if primaryColor is set, also update related colors
        val finalPrimaryColor = primaryColor ?: baseColors.primary
        val colors = baseColors.copy(
            primary = finalPrimaryColor,
            secondary = secondaryColor ?: baseColors.secondary,
            background = backgroundColor ?: baseColors.background,
            surface = surfaceColor ?: baseColors.surface,
            error = errorColor ?: baseColors.error,
            botBubble = botBubbleColor ?: baseColors.botBubble,
            botBubbleText = botBubbleTextColor ?: baseColors.botBubbleText,
            userBubble = userBubbleColor ?: primaryColor ?: baseColors.userBubble,
            userBubbleText = userBubbleTextColor ?: baseColors.userBubbleText,
            agentBubble = agentBubbleColor ?: baseColors.agentBubble,
            agentBubbleText = agentBubbleTextColor ?: baseColors.agentBubbleText,
            headerBackground = headerBackgroundColor ?: primaryColor ?: baseColors.headerBackground,
            headerText = headerTextColor ?: baseColors.headerText,
            headerIcon = headerTextColor ?: baseColors.headerIcon,
            inputBackground = inputBackgroundColor ?: baseColors.inputBackground,
            inputText = inputTextColor ?: baseColors.inputText,
            inputBorder = inputBorderColor ?: baseColors.inputBorder,
            inputBorderFocused = primaryColor ?: baseColors.inputBorderFocused,
            buttonBackground = primaryColor ?: baseColors.buttonBackground,
            link = primaryColor ?: baseColors.link,
            delivered = primaryColor ?: baseColors.delivered,
            read = primaryColor ?: baseColors.read
        )

        // Build typography
        val typography = baseTypography.copy(
            fontFamily = fontFamily ?: baseTypography.fontFamily,
            headerSize = headerSize ?: baseTypography.headerSize,
            bodySize = bodySize ?: baseTypography.bodySize,
            messageSize = messageSize ?: baseTypography.messageSize,
            captionSize = captionSize ?: baseTypography.captionSize,
            inputSize = inputSize ?: baseTypography.inputSize,
            buttonSize = buttonSize ?: baseTypography.buttonSize
        )

        // Build shapes
        val shapes = baseShapes.copy(
            bubbleRadius = bubbleRadius ?: baseShapes.bubbleRadius,
            buttonRadius = buttonRadius ?: baseShapes.buttonRadius,
            cardRadius = cardRadius ?: baseShapes.cardRadius,
            inputRadius = inputRadius ?: baseShapes.inputRadius,
            imageRadius = imageRadius ?: baseShapes.imageRadius
        )

        // Build spacing
        val spacing = baseSpacing.copy(
            xs = spacingXs ?: baseSpacing.xs,
            sm = spacingSm ?: baseSpacing.sm,
            md = spacingMd ?: baseSpacing.md,
            lg = spacingLg ?: baseSpacing.lg,
            xl = spacingXl ?: baseSpacing.xl,
            messageSpacing = messageSpacing ?: baseSpacing.messageSpacing,
            maxBubbleWidth = maxBubbleWidth ?: baseSpacing.maxBubbleWidth
        )

        // Build animations
        val animations = baseAnimations.copy(
            typingDuration = typingDuration ?: baseAnimations.typingDuration,
            messageFadeDuration = messageFadeDuration ?: baseAnimations.messageFadeDuration,
            buttonPressDuration = buttonPressDuration ?: baseAnimations.buttonPressDuration,
            enabled = animationsEnabled
        )

        // Determine background
        val finalBackground = background
            ?: backgroundColor?.let { ConferbotBackground.SolidColor(it) }
            ?: baseTheme.background

        return ConferbotTheme(
            colors = colors,
            typography = typography,
            shapes = shapes,
            spacing = spacing,
            animations = animations,
            background = finalBackground,
            isDarkTheme = isDarkTheme,
            name = name
        )
    }

    companion object {
        /**
         * Create a new builder instance
         */
        fun create() = ConferbotThemeBuilder()

        /**
         * Create a theme with just a primary color
         */
        fun withPrimaryColor(color: Color, isDark: Boolean = false): ConferbotTheme {
            return ConferbotThemeBuilder()
                .baseTheme(if (isDark) DarkTheme else LightTheme)
                .primaryColor(color)
                .build()
        }

        /**
         * Create a theme from a hex color string
         */
        fun withPrimaryColor(hexColor: String, isDark: Boolean = false): ConferbotTheme {
            val color = try {
                Color(android.graphics.Color.parseColor(hexColor))
            } catch (e: IllegalArgumentException) {
                if (isDark) DarkTheme.colors.primary else LightTheme.colors.primary
            }
            return withPrimaryColor(color, isDark)
        }
    }
}

/**
 * Extension function to create a builder from an existing theme
 */
fun ConferbotTheme.toBuilder(): ConferbotThemeBuilder {
    return ConferbotThemeBuilder()
        .baseTheme(this)
        .isDarkTheme(this.isDarkTheme)
        .name(this.name)
}

/**
 * Extension property to access the builder
 */
object ConferbotThemeFactory {
    /**
     * Create a new theme builder
     */
    fun Builder() = ConferbotThemeBuilder.create()
}

/**
 * Add Builder() to ConferbotTheme companion object style access
 */
fun ConferbotTheme.Companion.Builder() = ConferbotThemeBuilder.create()

/**
 * Companion object for ConferbotTheme to enable Builder pattern
 */
val ConferbotTheme.Companion: ConferbotThemeCompanion get() = ConferbotThemeCompanion

object ConferbotThemeCompanion {
    fun Builder() = ConferbotThemeBuilder.create()
}
