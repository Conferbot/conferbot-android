package com.conferbot.sdk.core.customization

/**
 * The bot's customizations as the web widget applies them
 * (conferbot-widget core/customizations.ts buildCustomizationsRef and the
 * polish / owner sheets): defaults, the values that count as unset, and what
 * each one turns into on screen. Pure: the Compose and XML screens read it.
 */
data class Corners(val topLeft: Float, val topRight: Float, val bottomRight: Float, val bottomLeft: Float) {
    companion object {
        fun all(r: Float) = Corners(r, r, r, r)
    }
}

data class ResolvedCustomizations(
    /** "Powered by Conferbot" hidden (the server forces false on Free/Starter). */
    val hideBrand: Boolean = false,
    /** The owner's footer text instead of "Powered by", when turned on. */
    val customBrand: String? = null,
    /** Header title: logoText (or the bot name), cut to 18 characters. */
    val title: String = "Chat",
    /** Header logo: the owner's, else the Conferbot logo (as the web). */
    val logo: String = Customizations.DEFAULT_LOGO,
    val hideLogo: Boolean = false,
    /** Under the title: the tagline when turned on, else "Online" (unless hidden). */
    val tagline: String? = null,
    val showOnline: Boolean = true,
    /** Bot avatar beside messages (the logo when no avatar of its own). */
    val avatar: String = Customizations.DEFAULT_LOGO,
    val hideAvatar: Boolean = false,
    val avatarSize: Float = 32f,
    val avatarShape: String = "circle",
    val avatarRadius: Float = 16f,
    /** Bubble corners: the web's 18/6 shapes unless the owner set a radius. */
    val botBubble: Corners = Corners(18f, 18f, 18f, 6f),
    val userBubble: Corners = Corners(18f, 18f, 6f, 18f),
    val optionRadius: Float = 10f,
    /** Message text size: 14.5 unless the owner set something other than 14. */
    val fontSize: Float = 14.5f,
    /** Colours, as hex strings, with the web's defaults and fallbacks. */
    val headerBg: String = "#0b4eb8",
    val headerText: String = "#ffffff",
    val botBubbleColor: String = "#0b4eb8",
    val botTextColor: String = "#ffffff",
    val userBubbleColor: String = "#f0f0f0",
    val userTextColor: String = "#000000",
    val optionBubbleColor: String = "#0b4eb8",
    val optionTextColor: String = "#ffffff",
    /** dots, wave, shimmer, pencil or mascot (pencil and mascot draw dots until mascots land). */
    val typingStyle: String = "dots",
)

object Customizations {
    /** The web widget's default logo (utils/constants BOT_LOGO): header and bot avatar. */
    const val DEFAULT_LOGO = "https://www.conferbot.com/img/logo/conferbot-logo.png"

    private val TYPING = setOf("dots", "wave", "shimmer", "pencil", "mascot")

    private fun str(v: Any?): String = (v as? String)?.trim().orEmpty()

    private fun num(v: Any?): Float? = when (v) {
        is Number -> v.toFloat().takeIf { it.isFinite() }
        is String -> v.trim().toFloatOrNull()
        else -> null
    }

    private fun url(v: Any?): String? {
        val s = str(v)
        return if (Regex("^(https?:)?//", RegexOption.IGNORE_CASE).containsMatchIn(s) || s.startsWith("data:image/", true)) s else null
    }

    /** "Live chat test (te..." - 18 characters, as the header cuts it. */
    fun headerTitle(name: String): String = if (name.length > 18) name.take(18) + "..." else name

    /** The avatar's corner radius for its shape: circle 50%, rounded 28%, square 4. */
    fun avatarRadius(shape: String?, size: Float): Float = when (shape) {
        "square" -> 4f
        "rounded" -> Math.round(size * 0.28f).toFloat()
        else -> size / 2f
    }

    fun resolve(raw: Map<String, Any?>?, botName: String = ""): ResolvedCustomizations {
        val c = raw ?: emptyMap()
        val name = str(c["logoText"]).ifEmpty { str(c["botName"]) }.ifEmpty { botName }.ifEmpty { "Chat" }
        val tagline = if (c["enableTagline"] == true && str(c["tagline"]).isNotEmpty()) str(c["tagline"]) else null
        val avatarSet = num(c["avatarSize"])
        val avatarSize = if (avatarSet != null && avatarSet > 0f && avatarSet != 40f) avatarSet else 32f
        val shape = (c["avatarShape"] as? String).takeIf { it == "square" || it == "rounded" } ?: "circle"
        val bubbleSet = num(c["bubbleBorderRadius"])
        val ownBubble = bubbleSet != null && bubbleSet != 10f
        val fontSet = num(c["fontSize"])
        val logo = url(c["logo"])

        val bot = str(c["botMsgColor"]).ifEmpty { "#0b4eb8" }
        val botText = str(c["botTextColor"]).ifEmpty { "#ffffff" }
        val userSet = str(c["userMsgColor"])
        return ResolvedCustomizations(
            hideBrand = c["hideBrand"] == true,
            customBrand = if (c["enableCustomBrand"] == true && str(c["customBrand"]).isNotEmpty()) str(c["customBrand"]) else null,
            title = headerTitle(name),
            logo = logo ?: DEFAULT_LOGO,
            hideLogo = c["hideLogo"] == true,
            tagline = tagline,
            showOnline = tagline == null && c["hideOnlineStatus"] != true,
            avatar = url(c["avatar"]) ?: logo ?: DEFAULT_LOGO,
            hideAvatar = c["hideAvatar"] == true,
            avatarSize = avatarSize,
            avatarShape = shape,
            avatarRadius = avatarRadius(shape, avatarSize),
            botBubble = if (ownBubble) Corners.all(bubbleSet!!) else Corners(18f, 18f, 18f, 6f),
            userBubble = if (ownBubble) Corners.all(bubbleSet!!) else Corners(18f, 18f, 6f, 18f),
            optionRadius = num(c["optionBubbleBorderRadius"]) ?: 10f,
            fontSize = if (fontSet != null && fontSet > 0f && fontSet != 14f) fontSet else 14.5f,
            headerBg = str(c["headerBgColor"]).ifEmpty { "#0b4eb8" },
            headerText = str(c["headerTextColor"]).ifEmpty { "#ffffff" },
            botBubbleColor = bot,
            botTextColor = botText,
            // The visitor's bubble: light grey with dark text, never the bot's colour.
            userBubbleColor = userSet.ifEmpty { "#f0f0f0" },
            userTextColor = str(c["userTextColor"]).ifEmpty { if (userSet.isNotEmpty()) "#ffffff" else "#000000" },
            // Choices: the option colours, else the bot's, else grey with black text.
            optionBubbleColor = str(c["optionBubbleMsgColor"]).ifEmpty { str(c["botMsgColor"]) }.ifEmpty { "#e7e4e4" },
            optionTextColor = str(c["optionBubbleTextColor"]).ifEmpty { str(c["botTextColor"]) }.ifEmpty { "#000000" },
            typingStyle = (c["typingStyle"] as? String)?.takeIf { it in TYPING } ?: "dots",
        )
    }
}
