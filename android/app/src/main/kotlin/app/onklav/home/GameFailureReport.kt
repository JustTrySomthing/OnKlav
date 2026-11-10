package app.onklav.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

internal data class GameFailureReport(val operation: String, val message: String, val gameId: String? = null) {
    fun text(): String = buildString {
        appendLine("OnKlav game diagnostics")
        appendLine("Host build: ${BuildConfig.VERSION_CODE}")
        appendLine("Operation: $operation")
        gameId?.takeIf { it.matches(Regex("[a-z][a-z0-9.]{0,159}")) }?.let { appendLine("Game: $it") }
        append("Reason: ${message.take(400).replace(Regex("[\\p{Cntrl}]"), " ")}")
    }

    fun copyToClipboard(context: Context) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("OnKlav game diagnostics", text()))
    }
}
