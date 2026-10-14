package com.conferbot.sdk.core.customization

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CustomizationsTest {

    @Test
    fun `uses the web defaults when nothing is set`() {
        val c = Customizations.resolve(emptyMap())
        assertThat(c.hideBrand).isFalse()
        assertThat(c.customBrand).isNull()
        assertThat(c.showOnline).isTrue()
        assertThat(c.logo).isEqualTo(Customizations.DEFAULT_LOGO)
        assertThat(c.avatar).isEqualTo(Customizations.DEFAULT_LOGO)
        assertThat(c.avatarSize).isEqualTo(32f)
        assertThat(c.botBubble).isEqualTo(Corners(18f, 18f, 18f, 6f))
        assertThat(c.userBubble).isEqualTo(Corners(18f, 18f, 6f, 18f))
        assertThat(c.optionRadius).isEqualTo(10f)
        assertThat(c.fontSize).isEqualTo(14.5f)
        assertThat(c.userBubbleColor).isEqualTo("#f0f0f0")
        assertThat(c.userTextColor).isEqualTo("#000000")
        assertThat(c.optionBubbleColor).isEqualTo("#e7e4e4")
        assertThat(c.optionTextColor).isEqualTo("#000000")
        assertThat(c.typingStyle).isEqualTo("dots")
    }

    @Test
    fun `treats the server defaults as unset`() {
        val c = Customizations.resolve(mapOf("bubbleBorderRadius" to 10, "fontSize" to 14, "avatarSize" to 40))
        assertThat(c.botBubble.bottomLeft).isEqualTo(6f)
        assertThat(c.fontSize).isEqualTo(14.5f)
        assertThat(c.avatarSize).isEqualTo(32f)
    }

    @Test
    fun `applies the owner's own values and colour fallbacks`() {
        val c = Customizations.resolve(mapOf(
            "bubbleBorderRadius" to 4, "optionBubbleBorderRadius" to "20", "fontSize" to 16, "avatarSize" to 44, "avatarShape" to "square",
            "hideBrand" to true, "enableCustomBrand" to true, "customBrand" to " Acme ", "enableTagline" to true, "tagline" to "Here 24/7",
            "hideAvatar" to true, "hideLogo" to true, "typingStyle" to "wave", "botMsgColor" to "#0F766E", "botTextColor" to "#ffffff",
            "userMsgColor" to "#123456",
        ))
        assertThat(c.botBubble).isEqualTo(Corners.all(4f))
        assertThat(c.optionRadius).isEqualTo(20f)
        assertThat(c.fontSize).isEqualTo(16f)
        assertThat(c.avatarRadius).isEqualTo(4f)
        assertThat(c.customBrand).isEqualTo("Acme")
        assertThat(c.tagline).isEqualTo("Here 24/7")
        assertThat(c.showOnline).isFalse()
        assertThat(c.hideAvatar).isTrue()
        assertThat(c.hideLogo).isTrue()
        assertThat(c.typingStyle).isEqualTo("wave")
        // Choices follow the bot's colours; a visitor colour alone gets white text.
        assertThat(c.optionBubbleColor).isEqualTo("#0F766E")
        assertThat(c.userTextColor).isEqualTo("#ffffff")
    }

    @Test
    fun `cuts the title and shapes avatars`() {
        assertThat(Customizations.headerTitle("Live chat test (temporary) quick")).isEqualTo("Live chat test (te...")
        assertThat(Customizations.resolve(mapOf("logoText" to "Acme", "botName" to "Bot")).title).isEqualTo("Acme")
        assertThat(Customizations.avatarRadius("rounded", 32f)).isEqualTo(9f)
        assertThat(Customizations.avatarRadius("circle", 32f)).isEqualTo(16f)
        assertThat(Customizations.resolve(mapOf("hideOnlineStatus" to true)).showOnline).isFalse()
        assertThat(Customizations.resolve(mapOf("customBrand" to "Acme")).customBrand).isNull()
    }
}
