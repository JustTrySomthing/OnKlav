package app.onklav.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import app.onklav.shared.R

data class PlatformApp(val id: String, val title: String, val artwork: Int, val iconPng:String?=null)

val platformApps =
    listOf(
        PlatformApp("cloudfiles", "Cloud Files", R.drawable.ref_app_cloud_files),
        PlatformApp("settings", "Settings", R.drawable.ref_app_settings),
        PlatformApp("games", "Games", R.drawable.ref_nav_games),
        PlatformApp("phone", "Phone", R.drawable.ref_app_phone),
        PlatformApp("calendar", "Calendar", R.drawable.ref_app_calendar),
        PlatformApp("authentic", "Authentic", R.drawable.ref_app_authentic),
        PlatformApp("codeedit", "Code Edit", R.drawable.ref_app_code_edit),
        PlatformApp("voicerecorder", "Voice Record", R.drawable.ref_app_voice_recorder),
        PlatformApp("screenrecorder", "Screen Record", R.drawable.ref_app_screen_recorder),
        PlatformApp("docuview", "Docu View", R.drawable.ref_app_docu_view),
        PlatformApp("vault", "Vault", R.drawable.ref_app_vault),
        PlatformApp("compress", "Compress", R.drawable.ref_app_compress),
        PlatformApp("formatswap", "Format Swap", R.drawable.ref_app_format_swap),
        PlatformApp("power", "Power", R.drawable.ref_app_power),
        PlatformApp("clock", "Clock", R.drawable.ref_app_clock),
        PlatformApp("flashlight", "Flashlight", R.drawable.ref_app_flashlight),
        PlatformApp("camera", "Camera", R.drawable.ref_app_camera),
        PlatformApp("email", "Email", R.drawable.ref_app_email),
        PlatformApp("videoeditor", "Video Edit", R.drawable.ref_app_video_editor),
        PlatformApp("maps", "Maps", R.drawable.ref_app_maps),
        PlatformApp("audioeditor", "Audio Edit", R.drawable.ref_app_audio_editor),
        PlatformApp("write", "Write", R.drawable.ref_app_write),
        PlatformApp("photo", "Image Edit", R.drawable.ref_app_photo),
        PlatformApp("calc", "Calc", R.drawable.ref_app_calc),
        PlatformApp("weather", "Weather", R.drawable.ref_app_weather),
        PlatformApp("convert", "Convert", R.drawable.ref_app_convert),
        PlatformApp("files", "Files", R.drawable.ref_app_files),
        PlatformApp("walkie", "Walkie-Talkie", R.drawable.ref_app_walkie),
        PlatformApp("social", "Real Social", R.drawable.ref_app_social),
        PlatformApp("browser", "Browser", R.drawable.ref_app_browser),
        PlatformApp("youtube", "YouTube", R.drawable.ref_app_youtube),
        PlatformApp("reader", "Read Aloud", R.drawable.ref_app_reader),
        PlatformApp("rss", "RSS Reader", R.drawable.ref_app_rss),
        PlatformApp("ebooks", "eBooks", R.drawable.ref_app_ebooks),
        PlatformApp("audiobooks", "AudioBooks", R.drawable.ref_app_audiobooks),
        PlatformApp("podcasts", "Podcasts", R.drawable.ref_app_podcasts),
        PlatformApp("radio", "Radio", R.drawable.ref_app_radio),
    )

data class PlatformGame(
    val id: String,
    val title: String,
    val description: String,
    val artwork: String,
)

val singlePlayerOnlyGames = setOf("hockey", "dominos", "panda", GamePackages.SHROOM_ID, GamePackages.LAST_LIGHT_ID)

val platformGames =
    listOf(
        PlatformGame(GamePackages.LAST_LIGHT_ID, "Last Light Express", "Ten levels · fight across a moving train and face the Iron Warden.", "last-light"),
        PlatformGame("panda", "Crash Test Panda", "Solo demolition: twelve courses, explosive chains and a shot-for-shot panda rivalry.", "panda"),
        PlatformGame(GamePackages.SHROOM_ID, "Shroom", "Fight through five spore-infested sectors and take down the Cap King.", "shroom"),
        PlatformGame(
            "hockey",
            "Air Hockey",
            "Solo Air Hockey · ten goals · redesigned arcade table.",
            "hockey",
        ),
        PlatformGame(
            "naval",
            "Naval Battle",
            "Outsmart your opponent and destroy their ships.",
            "naval",
        ),
        PlatformGame("dominos", "Neon Dominos", "Earn pips by playing tiles, then subtract the pips you still hold. Highest total after three rounds wins.", "dominos"),
        PlatformGame("four", "Cats vs Dogs", "Classic four in a row with a playful twist.", "pets"),
        PlatformGame(
            "blackjack",
            "Blackjack",
            "Test your luck and skill in a blackjack tournament.",
            "blackjack",
        ),
    )

fun visibilityPrefs(context: Context) =
    context.getSharedPreferences("package_visibility", Context.MODE_PRIVATE)

internal val corePlatformApps = setOf("settings", "games", "phone", "camera", "files")

internal fun canHideCatalogEntry(games: Boolean, id: String): Boolean =
    if (games) id != GamePackages.SHROOM_ID else id !in corePlatformApps

@Composable
internal fun removedCatalogIds(games: Boolean): Set<String> {
    val prefs = visibilityPrefs(LocalContext.current)
    val prefix = if (games) "game:" else "app:"
    fun readRemoved() = prefs.all.entries
        .filter { (key, value) -> key.startsWith(prefix) && value == true }
        .map { it.key.removePrefix(prefix) }
        .filter { canHideCatalogEntry(games, it) }.toSet()
    var removed by remember(prefs, games) { mutableStateOf(readRemoved()) }
    DisposableEffect(prefs, games) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith(prefix)) removed = readRemoved()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        removed = readRemoved()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return removed
}

fun hidden(context: Context, games: Boolean, id: String) =
    canHideCatalogEntry(games, id) && visibilityPrefs(context).getBoolean("${if (games) "game" else "app"}:$id", false)

fun setHidden(context: Context, games: Boolean, id: String, value: Boolean) {
    check(
        visibilityPrefs(context)
            .edit()
            .putBoolean("${if (games) "game" else "app"}:$id", value && canHideCatalogEntry(games, id))
            .commit()
    ) {
        "Could not save visibility"
    }
}

/** Host display labels for included apps; signed manifests and routing identities stay intact. */
internal fun includedAppDisplayName(id:String,fallback:String):String=when(id){
 "app.onklav.videoeditor"->"Video Edit"
 "app.onklav.audioeditor"->"Audio Edit"
 "app.onklav.photo"->"Image Edit"
 RealSocial.APP->"Real Social"
 else->fallback
}
internal fun includedAppDisplayName(manifest:org.json.JSONObject)=includedAppDisplayName(manifest.optString("id"),manifest.optString("name"))

internal fun includedAppFrameTitle(id:String,title:String):String=when {
 id=="app.onklav.videoeditor"&&title=="Video Editor"->"Video Edit"
 id=="app.onklav.audioeditor"&&title=="Audio Editor"->"Audio Edit"
 id=="app.onklav.photo"&&title=="Image Editor"->"Image Edit"
 else->title
}
