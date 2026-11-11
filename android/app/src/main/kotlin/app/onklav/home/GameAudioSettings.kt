package app.onklav.home

import android.content.Context
import androidx.webkit.WebViewFeature

internal object GameAudioSettings {
    const val EXTRA_MUTED = "game_audio_muted"
    private const val KEY = "audio_enabled"

    private fun preferences(context: Context) =
        context.getSharedPreferences("game_preferences", Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = preferences(context).getBoolean(KEY, true)

    fun save(context: Context, enabled: Boolean): Boolean =
        preferences(context).edit().putBoolean(KEY, enabled).commit()

    fun supportsGlobalMute(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)
}
