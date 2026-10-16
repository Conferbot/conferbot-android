package com.conferbot.sdk.core.customization

/**
 * The owner font catalog and the widget themes' Google Fonts queries, as
 * the React Native SDK's src/core/fonts.data.ts has them (copied from
 * conferbot-widget src/ts/fonts/catalog.ts, between its FONTS:BEGIN /
 * FONTS:END markers, and src/ts/theme/registry.ts, each theme's
 * font("Family", "query")). OwnerFontTest fails when they drift from RN's.
 */
object FontsData {
    data class CatalogFont(val id: String, val label: String, val family: String, val category: String, val stack: String, val google: String?)

    val FONT_CATALOG: List<CatalogFont> = listOf(
        CatalogFont("inter", "Inter", "Inter", "sans", "'Inter', sans-serif", "Inter:wght@400;500;600;700"),
        CatalogFont("manrope", "Manrope", "Manrope", "sans", "'Manrope', sans-serif", "Manrope:wght@400;500;600;700"),
        CatalogFont("plus-jakarta-sans", "Plus Jakarta Sans", "Plus Jakarta Sans", "sans", "'Plus Jakarta Sans', sans-serif", "Plus+Jakarta+Sans:wght@400;500;600;700"),
        CatalogFont("dm-sans", "DM Sans", "DM Sans", "sans", "'DM Sans', sans-serif", "DM+Sans:wght@400;500;600;700"),
        CatalogFont("outfit", "Outfit", "Outfit", "sans", "'Outfit', sans-serif", "Outfit:wght@400;500;600;700"),
        CatalogFont("poppins", "Poppins", "Poppins", "sans", "'Poppins', sans-serif", "Poppins:wght@400;500;600;700"),
        CatalogFont("figtree", "Figtree", "Figtree", "sans", "'Figtree', sans-serif", "Figtree:wght@400;500;600;700"),
        CatalogFont("onest", "Onest", "Onest", "sans", "'Onest', sans-serif", "Onest:wght@400;500;600;700"),
        CatalogFont("geist", "Geist", "Geist", "sans", "'Geist', sans-serif", "Geist:wght@400;500;600;700"),
        CatalogFont("nunito", "Nunito", "Nunito", "rounded", "'Nunito', sans-serif", "Nunito:wght@400;500;600;700"),
        CatalogFont("quicksand", "Quicksand", "Quicksand", "rounded", "'Quicksand', sans-serif", "Quicksand:wght@400;500;600;700"),
        CatalogFont("fredoka", "Fredoka", "Fredoka", "rounded", "'Fredoka', sans-serif", "Fredoka:wght@400;500;600;700"),
        CatalogFont("baloo-2", "Baloo 2", "Baloo 2", "rounded", "'Baloo 2', sans-serif", "Baloo+2:wght@400;500;600;700"),
        CatalogFont("varela-round", "Varela Round", "Varela Round", "rounded", "'Varela Round', sans-serif", "Varela+Round"),
        CatalogFont("m-plus-rounded-1c", "M PLUS Rounded 1c", "M PLUS Rounded 1c", "rounded", "'M PLUS Rounded 1c', sans-serif", "M+PLUS+Rounded+1c:wght@400;500;700"),
        CatalogFont("playfair-display", "Playfair Display", "Playfair Display", "serif", "'Playfair Display', serif", "Playfair+Display:wght@400;500;600;700"),
        CatalogFont("fraunces", "Fraunces", "Fraunces", "serif", "'Fraunces', serif", "Fraunces:wght@400;500;600;700"),
        CatalogFont("cormorant-garamond", "Cormorant Garamond", "Cormorant Garamond", "serif", "'Cormorant Garamond', serif", "Cormorant+Garamond:wght@400;500;600;700"),
        CatalogFont("lora", "Lora", "Lora", "serif", "'Lora', serif", "Lora:wght@400;500;600;700"),
        CatalogFont("libre-baskerville", "Libre Baskerville", "Libre Baskerville", "serif", "'Libre Baskerville', serif", "Libre+Baskerville:wght@400;700"),
        CatalogFont("dm-serif-display", "DM Serif Display", "DM Serif Display", "serif", "'DM Serif Display', serif", "DM+Serif+Display"),
        CatalogFont("jetbrains-mono", "JetBrains Mono", "JetBrains Mono", "mono", "'JetBrains Mono', monospace", "JetBrains+Mono:wght@400;500;600;700"),
        CatalogFont("space-grotesk", "Space Grotesk", "Space Grotesk", "mono", "'Space Grotesk', sans-serif", "Space+Grotesk:wght@400;500;600;700"),
        CatalogFont("ibm-plex-sans", "IBM Plex Sans", "IBM Plex Sans", "mono", "'IBM Plex Sans', sans-serif", "IBM+Plex+Sans:wght@400;500;600;700"),
        CatalogFont("ibm-plex-mono", "IBM Plex Mono", "IBM Plex Mono", "mono", "'IBM Plex Mono', monospace", "IBM+Plex+Mono:wght@400;500;600;700"),
        CatalogFont("space-mono", "Space Mono", "Space Mono", "mono", "'Space Mono', monospace", "Space+Mono:wght@400;700"),
        CatalogFont("bricolage-grotesque", "Bricolage Grotesque", "Bricolage Grotesque", "display", "'Bricolage Grotesque', sans-serif", "Bricolage+Grotesque:wght@400;500;600;700"),
        CatalogFont("syne", "Syne", "Syne", "display", "'Syne', sans-serif", "Syne:wght@400;500;600;700"),
        CatalogFont("righteous", "Righteous", "Righteous", "display", "'Righteous', sans-serif", "Righteous"),
        CatalogFont("pacifico", "Pacifico", "Pacifico", "display", "'Pacifico', cursive", "Pacifico"),
        CatalogFont("caveat", "Caveat", "Caveat", "display", "'Caveat', cursive", "Caveat:wght@400;500;600;700"),
        CatalogFont("patrick-hand", "Patrick Hand", "Patrick Hand", "display", "'Patrick Hand', cursive", "Patrick+Hand"),
        CatalogFont("press-start-2p", "Press Start 2P", "Press Start 2P", "display", "'Press Start 2P', monospace", "Press+Start+2P"),
        CatalogFont("bungee", "Bungee", "Bungee", "display", "'Bungee', sans-serif", "Bungee"),
        CatalogFont("system-ui", "System UI", "system-ui", "system", "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif", null),
        CatalogFont("arial", "Arial", "Arial", "system", "Arial, Helvetica, sans-serif", null),
        CatalogFont("helvetica", "Helvetica", "Helvetica", "system", "Helvetica, Arial, sans-serif", null),
        CatalogFont("georgia", "Georgia", "Georgia", "system", "Georgia, 'Times New Roman', serif", null),
        CatalogFont("verdana", "Verdana", "Verdana", "system", "Verdana, Geneva, sans-serif", null),
        CatalogFont("trebuchet", "Trebuchet MS", "Trebuchet MS", "system", "'Trebuchet MS', Helvetica, sans-serif", null)
    )

    /** Theme font family -> its Google Fonts css2 "family=" query. */
    val THEME_FONT_QUERIES: Map<String, String> = linkedMapOf(
        "Inter" to "Inter:wght@400;500;600;700",
        "Manrope" to "Manrope:wght@400;500;600;700",
        "Fredoka" to "Fredoka:wght@400;500;600;700",
        "VT323" to "VT323",
        "Nunito" to "Nunito:wght@400;600;700;800",
        "Fraunces" to "Fraunces:opsz,wght@9..144,400;9..144,500;9..144,600;9..144,700",
        "JetBrains Mono" to "JetBrains+Mono:wght@400;500;700",
        "Plus Jakarta Sans" to "Plus+Jakarta+Sans:wght@400;500;600;700;800",
        "Space Grotesk" to "Space+Grotesk:wght@400;500;600;700",
        "Quicksand" to "Quicksand:wght@400;500;600;700",
        "Orbitron" to "Orbitron:wght@500;600;700",
        "Patrick Hand" to "Patrick+Hand",
        "Cormorant Garamond" to "Cormorant+Garamond:wght@500;600;700",
        "Baloo 2" to "Baloo+2:wght@400;500;600;700;800",
        "Lora" to "Lora:wght@500;600;700",
        "Bangers" to "Bangers",
        "Press Start 2P" to "Press+Start+2P",
        "Righteous" to "Righteous",
        "Unbounded" to "Unbounded:wght@500;600;700",
        "Bungee" to "Bungee",
        "Permanent Marker" to "Permanent+Marker",
        "Cabin Sketch" to "Cabin+Sketch:wght@400;700",
        "Chewy" to "Chewy",
        "Chakra Petch" to "Chakra+Petch:wght@400;500;600;700",
        "Exo 2" to "Exo+2:wght@400;500;600;700"
    )
}
