package com.conferbot.sdk.core.customization

import com.conferbot.sdk.core.i18n.I18n

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
    /** An outline round each bubble, from the theme (cartoon, neo-brutal...), or null. */
    val bubbleBorder: BubbleBorder? = null,
    /** The composer field's corner radius: 25 (a pill) unless the theme has its own. */
    val fieldRadius: Float = 25f,
    /** The chat surface the theme paints when the owner kept the default white, else null. */
    val surface: String? = null,
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
    /** dots, wave, shimmer, pencil or mascot (the bot's mascot, "<name> is typing"). */
    val typingStyle: String = "dots",
    /** Send button: the theme palette's accent when it supplied the colours, else the header colour. */
    val accent: String = "#0b4eb8",
    /** Ink on the send button: the theme palette's, else null (picked for contrast). */
    val accentInk: String? = null,
    /** The widget theme in force (core/customization/WidgetTheme). */
    val theme: ResolvedWidgetTheme = ResolvedWidgetTheme(),
    /** The owner's mascot (art3d mascotLookOf): the idle call, the mascot typing and launcher styles, the greeting card. */
    val mascot: MascotArt.Look = MascotArt.Look(),
    /** The colour it is drawn in when the owner chose none: the launcher's (widgetIconBgColor, else the header's). */
    val mascotAccent: String = "#4f46e5",
    /** The mascot greets on the greeting card, by its name, in place of the avatar. */
    val mascotInGreeting: Boolean = false,
    /** How the greeting card's figure moves: lively (absent), subtle or off. */
    val mascotMotion: String = MascotMotion.DEFAULT,
    /** The mascot's size in the launcher, % of the button (70-150, default 112). */
    val mascotLauncherScale: Int = MascotArt.SCALE_DEFAULT,
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

    /** The mascot's colour when the owner chose none: the launcher's (widgetIconBgColor), else the header's, else indigo. */
    fun mascotAccent(c: Map<String, Any?>?): String =
        str(c?.get("widgetIconBgColor")).ifEmpty { str(c?.get("headerBgColor")) }.ifEmpty { "#4f46e5" }

    /** "Live chat test (te..." - 18 characters, as the header cuts it. */
    fun headerTitle(name: String): String = if (name.length > 18) name.take(18) + "..." else name

    /** The avatar's corner radius for its shape: circle 50%, rounded 28%, square 4. */
    fun avatarRadius(shape: String?, size: Float): Float = when (shape) {
        "square" -> 4f
        "rounded" -> Math.round(size * 0.28f).toFloat()
        else -> size / 2f
    }

    fun resolve(raw: Map<String, Any?>?, botName: String = "", theme: ResolvedWidgetTheme? = null): ResolvedCustomizations {
        val c = raw ?: emptyMap()
        // A theme other than classic brings its own radii; the owner's still win.
        val themeShape = theme?.shape
        val r = themeShape?.rBubble ?: 18f
        val tail = themeShape?.rTail ?: 6f
        val optionSet = num(c["optionBubbleBorderRadius"])
        val name = str(c["logoText"]).ifEmpty { str(c["botName"]) }.ifEmpty { botName }.ifEmpty { "Chat" }
        // The stock "We're here to help" a new bot has is shown in the visitor's
        // language (web core/customizations.ts stockText "header.tagline").
        val tagline = if (c["enableTagline"] == true && str(c["tagline"]).isNotEmpty()) I18n.stockText(str(c["tagline"]), "header.tagline") else null
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
            botBubble = if (ownBubble) Corners.all(bubbleSet!!) else Corners(r, r, r, tail),
            userBubble = if (ownBubble) Corners.all(bubbleSet!!) else Corners(r, r, tail, r),
            optionRadius = if (optionSet != null && (optionSet != 10f || themeShape == null)) optionSet else themeShape?.rCtl ?: 10f,
            bubbleBorder = themeShape?.bubbleBorder,
            fieldRadius = themeShape?.rField ?: 25f,
            surface = theme?.tokens?.takeIf { !WidgetTheme.ownsChatBackground(c) }?.bg,
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
            accent = str(c["themeAccent"]).ifEmpty { str(c["headerBgColor"]) }.ifEmpty { "#0b4eb8" },
            accentInk = str(c["themeOnAccent"]).ifEmpty { null },
            theme = theme ?: ResolvedWidgetTheme(),
            mascot = MascotArt.lookOf(c),
            mascotAccent = mascotAccent(c),
            mascotInGreeting = c["mascotInGreeting"] == true,
            mascotMotion = MascotMotion.normalize(c["mascotMotion"]),
            mascotLauncherScale = MascotArt.scaleOf(c),
        )
    }
}
