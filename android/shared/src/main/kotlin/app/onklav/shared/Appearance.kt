package app.onklav.shared

import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONObject

val paletteNames =
    listOf(
        "Green",
        "Blue",
        "Orange",
        "Lavender",
        "Pink",
        "Red",
        "Yellow",
        "Grayscale",
        "Teal",
        "Aqua",
        "Indigo",
        "Violet",
        "Coral",
        "Copper",
        "Slate",
    )
val paletteOrder = listOf(0, 8, 9, 1, 10, 3, 11, 4, 12, 5, 2, 13, 6, 14, 7)
val fontNames =
    listOf(
        "Clean Sans",
        "Handwritten",
        "Classic Serif",
        "Narrow Sans",
        "Typewriter",
        "Serif Italic",
    )
val fontSizes = listOf(16, 17, 20, 23, 26)
val backgroundNames =
    linkedMapOf(
        "" to "No Background",
        "midnight" to "Midnight Waves",
        "aurora" to "Northern Lights",
        "forest" to "Quiet Forest",
        "dunes" to "Desert Glow",
        "botanical" to "Botanical Paper",
        "pearl" to "Pearl Silk",
        "garnet" to "Garnet Velvet",
        "moonlit" to "Moonlit Lake",
        "graphite" to "Graphite Forms",
        "lagoon" to "Lagoon Flow",
        "wildflowers" to "Wildflower Meadow",
        "watercolor" to "Watercolor Sky",
        "terracotta" to "Terracotta Arches",
        "sage" to "Sage Linen",
        "coast" to "Tropical Coast",
        "blossom" to "Cherry Blossom",
        "alpine" to "Alpine Sunrise",
        "cosmos" to "Cosmic Drift",
        "onyx" to "Onyx & Gold",
        "neon" to "Neon Rain",
        "starry" to "Starry Nights",
        "greatwave" to "The Great Wave",
        "sugarbloom" to "Sugar Bloom",
        "lofi" to "Lo-Fi Evenings",
        "angkor" to "Angkor Wat",
        "aquabeach" to "Aqua Beach",
        "waterlilies" to "Water Lilies",
        "synthwave" to "Neon Synthwave",
    )

/** An immutable staged snapshot; applying is the only operation that changes the live theme. */
data class AppearanceSpec(
    val dark: Boolean = true,
    val palette: Int = 3,
    val font: Int = 0,
    val size: Int = 17,
    val gradient: Boolean = true,
    val background: String = "cosmos",
    val overlayDark: Int = 65,
    val overlayLight: Int = 72,
) {
    fun json(): String =
        JSONObject()
            .put("dark", dark)
            .put("palette", palette)
            .put("font", font)
            .put("size", size)
            .put("gradient", gradient)
            .put("background", background)
            .put("overlayDark", overlayDark)
            .put("overlayLight", overlayLight)
            .toString()

    companion object {
        fun decode(text: String): AppearanceSpec {
            val j = JSONObject(text)
            val key = j.optString("background", "cosmos")
            return AppearanceSpec(
                j.optBoolean("dark", true),
                j.optInt("palette", 3).coerceIn(0, 14),
                j.optInt("font", 0).coerceIn(0, 5),
                j.optInt("size", 17).takeIf { it in fontSizes } ?: 17,
                j.optBoolean("gradient", true),
                key.takeIf { it in backgroundNames || it.matches(Regex("custom:[a-f0-9]{32}")) }
                    ?: "",
                j.optInt("overlayDark", 65).coerceIn(0, 100),
                j.optInt("overlayLight", 72).coerceIn(0, 100),
            )
        }
    }
}

class Appearance(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("appearance", 0)
    var spec by
        mutableStateOf(
            runCatching { AppearanceSpec.decode(prefs.getString("snapshot", null) ?: "") }
                .getOrElse {
                    AppearanceSpec(
                        dark = prefs.getBoolean("dark", true),
                        palette = prefs.getInt("palette", 3).coerceIn(0, 14),
                        font =
                            when (prefs.getInt("font", 0)) {
                                2 -> 2
                                3 -> 4
                                else -> 0
                            },
                        size = prefs.getInt("text_size", 17).takeIf { it in fontSizes } ?: 17,
                    )
                }
        )
        private set

    val dark
        get() = spec.dark

    val palette
        get() = spec.palette

    val font
        get() = spec.font

    val size
        get() = spec.size

    fun apply(value: AppearanceSpec) {
        check(prefs.edit().putString("snapshot", value.json()).commit()) {
            "Could not save appearance. Try again."
        }
        spec = value
    }
}

val presetThemes: List<Pair<String, AppearanceSpec>> =
    listOf(
        "Midnight Blue" to AppearanceSpec(palette = 1, background = "midnight"),
        "Northern Lights" to AppearanceSpec(palette = 0, background = "aurora"),
        "Forest Retreat" to AppearanceSpec(palette = 0, gradient = false, background = "forest"),
        "Velvet Plum" to AppearanceSpec(),
        "Garnet Night" to AppearanceSpec(palette = 5, background = "garnet"),
        "Onyx & Gold" to AppearanceSpec(palette = 6, background = "onyx"),
        "Pearl Blue" to AppearanceSpec(dark = false, palette = 1, background = "pearl"),
        "Botanical Green" to AppearanceSpec(dark = false, palette = 0, background = "botanical"),
        "Desert Glow" to AppearanceSpec(dark = false, palette = 2, background = "dunes"),
        "Cherry Blossom" to AppearanceSpec(dark = false, palette = 4, background = "blossom"),
        "Watercolor Sky" to AppearanceSpec(dark = false, palette = 3, background = "watercolor"),
        "Simply Light" to
            AppearanceSpec(dark = false, palette = 7, gradient = false, background = ""),
        "Starry Nights" to
            AppearanceSpec(palette = 6, background = "starry", overlayDark = 64, overlayLight = 58),
        "The Great Wave" to
            AppearanceSpec(
                dark = false,
                palette = 1,
                background = "greatwave",
                overlayDark = 64,
                overlayLight = 58,
            ),
        "Sugar Bloom" to
            AppearanceSpec(
                palette = 4,
                background = "sugarbloom",
                overlayDark = 64,
                overlayLight = 58,
            ),
        "Lo-Fi Evenings" to
            AppearanceSpec(palette = 3, background = "lofi", overlayDark = 64, overlayLight = 58),
        "Angkor Wat" to AppearanceSpec(palette = 2, background = "angkor", overlayDark = 64),
        "Aqua Beach" to
            AppearanceSpec(dark = false, palette = 9, background = "aquabeach", overlayDark = 66),
        "Water Lilies" to
            AppearanceSpec(dark = false, palette = 8, background = "waterlilies", overlayDark = 66),
        "Neon Synthwave" to AppearanceSpec(palette = 11, background = "synthwave", overlayDark = 66),
    )
