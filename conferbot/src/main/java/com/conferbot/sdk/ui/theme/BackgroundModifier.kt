package com.conferbot.sdk.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Extension function to apply Conferbot background to a Modifier
 *
 * Supports solid colors, gradients, and images based on the ConferbotBackground configuration
 */
fun Modifier.conferbotBackground(background: ConferbotBackground): Modifier {
    return when (background) {
        is ConferbotBackground.SolidColor -> {
            this.background(background.color)
        }
        is ConferbotBackground.Gradient -> {
            this.gradientBackground(
                startColor = background.startColor,
                endColor = background.endColor,
                angle = background.angle
            )
        }
        is ConferbotBackground.MultiGradient -> {
            this.multiGradientBackground(
                colors = background.colors,
                stops = background.stops.takeIf { it.isNotEmpty() },
                angle = background.angle
            )
        }
        is ConferbotBackground.Image -> {
            // For image backgrounds, we need to use a composable
            // This modifier just provides a fallback color
            this.background(background.overlayColor ?: Color.Transparent)
        }
        is ConferbotBackground.Pattern -> {
            // For pattern backgrounds, we need to use a composable
            // This modifier just provides a fallback color
            this.background(background.tint ?: Color.Transparent)
        }
    }
}

/**
 * Apply a two-color gradient background
 */
fun Modifier.gradientBackground(
    startColor: Color,
    endColor: Color,
    angle: Float = 0f
): Modifier {
    return this.drawBehind {
        val angleRad = (angle * PI / 180f).toFloat()
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // Calculate gradient direction based on angle
        val diagonal = kotlin.math.sqrt(size.width * size.width + size.height * size.height)
        val startX = centerX - (diagonal / 2f) * sin(angleRad)
        val startY = centerY + (diagonal / 2f) * cos(angleRad)
        val endX = centerX + (diagonal / 2f) * sin(angleRad)
        val endY = centerY - (diagonal / 2f) * cos(angleRad)

        val brush = Brush.linearGradient(
            colors = listOf(startColor, endColor),
            start = Offset(startX, startY),
            end = Offset(endX, endY)
        )
        drawRect(brush = brush)
    }
}

/**
 * Apply a multi-color gradient background
 */
fun Modifier.multiGradientBackground(
    colors: List<Color>,
    stops: List<Float>? = null,
    angle: Float = 0f
): Modifier {
    if (colors.isEmpty()) return this

    return this.drawBehind {
        val angleRad = (angle * PI / 180f).toFloat()
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        val diagonal = kotlin.math.sqrt(size.width * size.width + size.height * size.height)
        val startX = centerX - (diagonal / 2f) * sin(angleRad)
        val startY = centerY + (diagonal / 2f) * cos(angleRad)
        val endX = centerX + (diagonal / 2f) * sin(angleRad)
        val endY = centerY - (diagonal / 2f) * cos(angleRad)

        val brush = if (stops != null && stops.size == colors.size) {
            Brush.linearGradient(
                colorStops = colors.zip(stops).map { (color, stop) -> stop to color }.toTypedArray(),
                start = Offset(startX, startY),
                end = Offset(endX, endY)
            )
        } else {
            Brush.linearGradient(
                colors = colors,
                start = Offset(startX, startY),
                end = Offset(endX, endY)
            )
        }
        drawRect(brush = brush)
    }
}

/**
 * Apply a radial gradient background
 */
fun Modifier.radialGradientBackground(
    colors: List<Color>,
    centerX: Float = 0.5f,
    centerY: Float = 0.5f,
    radius: Float = 0.5f
): Modifier {
    if (colors.isEmpty()) return this

    return this.drawBehind {
        val center = Offset(
            x = size.width * centerX,
            y = size.height * centerY
        )
        val maxDimension = maxOf(size.width, size.height)
        val actualRadius = maxDimension * radius

        val brush = Brush.radialGradient(
            colors = colors,
            center = center,
            radius = actualRadius,
            tileMode = TileMode.Clamp
        )
        drawRect(brush = brush)
    }
}

/**
 * Composable that renders a Conferbot background
 *
 * Use this for image and pattern backgrounds that require async loading
 */
@Composable
fun ConferbotBackgroundContainer(
    background: ConferbotBackground,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    when (background) {
        is ConferbotBackground.SolidColor -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(background.color)
            ) {
                content()
            }
        }
        is ConferbotBackground.Gradient -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .gradientBackground(
                        startColor = background.startColor,
                        endColor = background.endColor,
                        angle = background.angle
                    )
            ) {
                content()
            }
        }
        is ConferbotBackground.MultiGradient -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .multiGradientBackground(
                        colors = background.colors,
                        stops = background.stops.takeIf { it.isNotEmpty() },
                        angle = background.angle
                    )
            ) {
                content()
            }
        }
        is ConferbotBackground.Image -> {
            ImageBackgroundContainer(
                background = background,
                modifier = modifier,
                content = content
            )
        }
        is ConferbotBackground.Pattern -> {
            PatternBackgroundContainer(
                background = background,
                modifier = modifier,
                content = content
            )
        }
    }
}

/**
 * Container with image background
 */
@Composable
private fun ImageBackgroundContainer(
    background: ConferbotBackground.Image,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        // Background image
        val imageModel = when {
            background.imageUrl != null -> background.imageUrl
            background.imageResId != null -> ImageRequest.Builder(context)
                .data(background.imageResId)
                .build()
            else -> null
        }

        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = when (background.scaleType) {
                    ImageScaleType.COVER -> ContentScale.Crop
                    ImageScaleType.CONTAIN -> ContentScale.Fit
                    ImageScaleType.FILL -> ContentScale.FillBounds
                    ImageScaleType.TILE -> ContentScale.None // Tiling would need custom implementation
                }
            )
        }

        // Overlay
        if (background.overlayColor != null && background.overlayOpacity > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background.overlayColor.copy(alpha = background.overlayOpacity))
            )
        }

        // Content
        content()
    }
}

/**
 * Container with pattern background
 */
@Composable
private fun PatternBackgroundContainer(
    background: ConferbotBackground.Pattern,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        // Pattern image (tiled)
        val patternModel = when {
            background.patternUrl != null -> background.patternUrl
            background.patternResId != null -> ImageRequest.Builder(context)
                .data(background.patternResId)
                .build()
            else -> null
        }

        if (patternModel != null) {
            // Note: True tiling would require a custom implementation
            // This provides a basic pattern display
            AsyncImage(
                model = patternModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (background.opacity < 1f) {
                            Modifier.drawBehind {
                                // Apply opacity by drawing with alpha
                            }
                        } else Modifier
                    ),
                contentScale = ContentScale.Crop,
                alpha = background.opacity
            )
        }

        // Tint overlay
        if (background.tint != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background.tint.copy(alpha = 0.3f))
            )
        }

        // Content
        content()
    }
}

/**
 * Create a common gradient presets
 */
object GradientPresets {
    val Sunset = ConferbotBackground.Gradient(
        startColor = Color(0xFFFF6B6B),
        endColor = Color(0xFFFFE66D),
        angle = 135f
    )

    val Ocean = ConferbotBackground.Gradient(
        startColor = Color(0xFF667EEA),
        endColor = Color(0xFF764BA2),
        angle = 135f
    )

    val Forest = ConferbotBackground.Gradient(
        startColor = Color(0xFF134E5E),
        endColor = Color(0xFF71B280),
        angle = 45f
    )

    val Midnight = ConferbotBackground.Gradient(
        startColor = Color(0xFF232526),
        endColor = Color(0xFF414345),
        angle = 180f
    )

    val Rose = ConferbotBackground.Gradient(
        startColor = Color(0xFFFFAFBD),
        endColor = Color(0xFFFFC3A0),
        angle = 45f
    )

    val Sky = ConferbotBackground.Gradient(
        startColor = Color(0xFF89F7FE),
        endColor = Color(0xFF66A6FF),
        angle = 135f
    )

    val Aurora = ConferbotBackground.MultiGradient(
        colors = listOf(
            Color(0xFF12C2E9),
            Color(0xFFC471ED),
            Color(0xFFF64F59)
        ),
        angle = 45f
    )

    val Warm = ConferbotBackground.MultiGradient(
        colors = listOf(
            Color(0xFFFF9A9E),
            Color(0xFFFECFEF),
            Color(0xFFFECFEF)
        ),
        stops = listOf(0f, 0.99f, 1f),
        angle = 180f
    )
}
