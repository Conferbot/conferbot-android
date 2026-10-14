package com.conferbot.sdk.core.customization

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatBackgroundTest {

    @Test
    fun `is a white solid by default and reads the type in any case`() {
        assertThat(ChatBackgrounds.resolve(emptyMap())).isEqualTo(ChatBackground.Solid("#ffffff"))
        assertThat(ChatBackgrounds.resolve(mapOf("chatBgType" to "Gradient"))).isInstanceOf(ChatBackground.Gradient::class.java)
        assertThat(ChatBackgrounds.resolve(mapOf("chatBgType" to "Image", "chatBgImg" to "https://x/bg.jpg"))).isInstanceOf(ChatBackground.Image::class.java)
    }

    @Test
    fun `reads gradient stops, angle and style`() {
        assertThat(ChatBackgrounds.gradientStops(emptyMap())).containsExactly("#fffcf1", "#1b55f3").inOrder()
        assertThat(ChatBackgrounds.gradientStops(mapOf("chatBgGradientStops" to listOf("#111", "bogus(", "#222", "#333", "#444", "#555"))))
            .containsExactly("#111", "#222", "#333", "#444").inOrder()
        val g = ChatBackgrounds.resolve(mapOf("chatBgType" to "gradient", "chatBgGradientAngle" to 400, "chatBgGradientStyle" to "mesh")) as ChatBackground.Gradient
        assertThat(g.angle).isEqualTo(360)
        assertThat(g.style).isEqualTo("mesh")
        assertThat((ChatBackgrounds.resolve(mapOf("chatBgType" to "gradient", "chatBgGradientStyle" to "nope")) as ChatBackground.Gradient).style).isEqualTo("linear")
    }

    @Test
    fun `masks images only when an opacity is set and refuses script urls`() {
        assertThat((ChatBackgrounds.resolve(mapOf("chatBgType" to "image", "chatBgImg" to "https://x/bg.jpg")) as ChatBackground.Image).maskColor).isNull()
        val masked = ChatBackgrounds.resolve(mapOf("chatBgType" to "image", "chatBgImg" to "https://x/bg.jpg", "chatBgMaskOpacity" to 95, "chatBgMaskColor" to "#123456")) as ChatBackground.Image
        assertThat(masked.maskColor).isEqualTo("#123456")
        assertThat(masked.maskAlpha).isWithin(0.001f).of(0.9f)
        assertThat(ChatBackgrounds.resolve(mapOf("chatBgType" to "image", "chatBgImg" to "javascript:alert(1)"))).isEqualTo(ChatBackground.Solid("#ffffff"))
    }

    @Test
    fun `reads patterns with the web defaults and has every tile`() {
        assertThat(ChatBackgrounds.resolve(mapOf("chatBgType" to "pattern"))).isEqualTo(ChatBackground.Pattern("#ffffff", "dots", "#0f172a", 0.12f))
        assertThat(ChatBackgrounds.PATTERNS.keys).containsAtLeast("dots", "grid", "diagonal", "waves", "topography", "confetti", "doodles", "paper", "hexagons", "rings", "plus", "zigzag", "sparkles")
        assertThat(ChatBackgrounds.rimPoint(0f)).isEqualTo(Pair(0.5f, 0f))
        assertThat(ChatBackgrounds.rimPoint(90f)).isEqualTo(Pair(1f, 0.5f))
    }
}
