package com.conferbot.sdk.core.customization

import com.google.gson.Gson

/**
 * The web widget's launcher icons as flat draw operations on a 24x24 grid
 * (generated from conferbot-widget src/svg/launcher): a path, its 2x3
 * transform, fill and stroke ("ink" = the launcher's icon colour, "bg" = the
 * launcher's own colour, or a hex; or a gradient), opacity, clip and the
 * motion part it belongs to. Pure data; the Compose launcher draws it.
 */
data class IconPaint(val c: String? = null, val g: Int? = null, val a: Double = 1.0)

data class IconStop(val o: Double = 0.0, val c: String = "#000000", val a: Double = 1.0)

data class IconGradient(
    val t: String = "l",
    val x1: Double = 0.0, val y1: Double = 0.0, val x2: Double = 1.0, val y2: Double = 0.0,
    val cx: Double = 0.5, val cy: Double = 0.5, val r: Double = 0.5,
    val stops: List<IconStop> = emptyList(),
)

/** An animated group a shape sits in, outer first (see LauncherMotion). */
data class IconPart(
    /** Its motion classes ("cbl-eye", "cbl-conf>*"...). */
    val c: List<String> = emptyList(),
    /** Group id: its box in LauncherIcon.boxes is the transform origin's box. */
    val g: Int = 0,
    val dx: Double? = null,
    val dy: Double? = null,
    /** Its own transform attribute (an animation on it replaces this). */
    val t: List<Double>? = null,
    /** No class of its own: it turns about the icon's origin. */
    val u: Int? = null,
    /** Its own opacity attribute (an animated opacity replaces this). */
    val o: Double? = null,
    /** The static transform between the previous part and this one. */
    val pre: List<Double>? = null,
)

data class IconOp(
    val d: String = "",
    val m: List<Double>? = null,
    val fill: IconPaint? = null,
    val stroke: IconPaint? = null,
    val sw: Double? = null,
    val cap: String? = null,
    val join: String? = null,
    val op: Double? = null,
    val rule: String? = null,
    val strokeFirst: Boolean? = null,
    val clip: String? = null,
    /** How many of the parts the clip moves with. */
    val clipDepth: Int? = null,
    /** The shape itself is the innermost animated element. */
    val self: Int? = null,
    /** The opacity from elements that are not animated parts (when it differs from op). */
    val opRest: Double? = null,
    /** The static transform after the last part, down to the shape. */
    val post: List<Double>? = null,
    val parts: List<IconPart>? = null,
)

data class LauncherIcon(
    val id: String = "",
    val anim: String = "classic",
    val grads: List<IconGradient> = emptyList(),
    val ops: List<IconOp> = emptyList(),
    /** Each animated group's box (and "0", the whole icon): x, y, width, height. */
    val boxes: Map<String, List<Double>>? = null,
)

object LauncherIcons {
    /** The icon a bot without one (or with an unknown id) draws, as on the web. */
    const val DEFAULT_ICON = "WidgetBubbleIcon40"

    val all: List<LauncherIcon> by lazy {
        Gson().fromJson(LauncherIconsData.JSON, Array<LauncherIcon>::class.java).toList()
    }

    private val byId by lazy { all.associateBy { it.id } }

    fun byId(id: String?): LauncherIcon = id?.let { byId[it] } ?: byId.getValue(DEFAULT_ICON)
}
