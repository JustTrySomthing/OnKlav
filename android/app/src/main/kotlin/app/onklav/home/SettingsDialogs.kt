package app.onklav.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import app.onklav.shared.R

@Composable
fun SettingsChoice(label: String, detail: String? = null, action: () -> Unit) {
    val art =
        when (label) {
            "Android Denied Default Access",
            "App Permissions" -> R.drawable.ref_help_permissions
            "Sending SIM" -> R.drawable.ref_help_sim
            "Mobile Data for Pictures" -> R.drawable.ref_help_mobile
            "Retry Pending Pictures" -> R.drawable.ref_help_retry
            "Open-source Licenses" -> R.drawable.ref_help_licenses
            else -> null
        }
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = action)
            .heightIn(min = if (detail == null) 48.dp else 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (art != null)
            Icon(
                androidx.compose.ui.res.painterResource(art),
                null,
                Modifier.padding(end = 14.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, lineHeight = 20.sp)
            if (detail != null)
                Text(
                    detail,
                    Modifier.padding(top = 4.dp),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

@Composable
fun PreferenceToggle(
    label: String,
    value: Boolean,
    enabled: Boolean = true,
    change: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value, enabled = enabled, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f).padding(end = 12.dp),
            fontSize = 17.sp,
            color =
                if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppearanceSwitch(value, enabled)
    }
}

@Composable
fun SettingsDialogs(route: String, close: () -> Unit) {
    val context = LocalContext.current
    var section by rememberSaveable(route) { mutableStateOf(route) }
    var error by remember { mutableStateOf<String?>(null) }
    fun back() { if (section == route) close() else section = route }
    fun androidPage(action: String) {
        runCatching { context.startActivity(Intent(action).apply {
            if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                data = Uri.parse("package:${context.packageName}")
        }) }.onFailure { error = "This phone could not open those settings." }
    }
    when (section) {
        "Notifications", "Quiet Hours", "Reminders", "Blocked Senders" -> MessengerSettings(section, ::back)
        "Read Aloud" -> ReadAloudSettingsDialog(::back)
        "Walkie Talkie" -> WalkieSettingsDialog(::back)
        "Privacy", "Privacy Policy" -> PrivacySettingsDialog(::back)
        "Spam" -> SpamSettingsDialog(::back)
        "Chat Sounds" -> ChatSoundsDialog(::back)
        "Blocked Messages" -> BlockedMessagesDialog(::back)
        "Manage Storage" -> StorageSettingsDialog(::back)
        "Backups" -> BackupSettingsDialog(::back)
        "Sending SIM" -> SendingSimDialog(::back)
        "Retry Pending Pictures" -> PendingPicturesDialog(::back)
        else -> {
            val menu = when (section) {
                "Sounds" -> listOf("Notifications" to "Vibration, app icon badges and Android notification access", "Chat Sounds" to "Choose a different sound for a person or Group")
                "Security" -> listOf("Spam" to "Control unwanted-message screening", "Blocked Senders" to "Review and manage blocked numbers", "Blocked Messages" to "View messages kept out of your inbox")
                "Storage" -> listOf("Backups" to "Save a backup or restore your messages", "Manage Storage" to "Review media, downloads and temporary files")
                "Help" -> listOf("Getting Started" to null, "Messages and Groups" to null, "Apps and Files" to null, "Reading and Listening" to null, "Android Help" to "Open Google's Android documentation")
                "About" -> listOf("About OnKlav" to null, "Privacy Policy" to null, "Open-source Licenses" to null)
                "Advanced" -> listOf("Android Denied Default Access" to null, "App Permissions" to null, "Sending SIM" to null, "Mobile Data for Pictures" to null, "Retry Pending Pictures" to null)
                else -> null
            }
            ReferenceDialog(section, ::back,
                footer = { DialogAction("Close", onClick = ::back) }) {
                if (menu != null) menu.forEach { (label, detail) -> SettingsChoice(label, detail) {
                    when(label) {
                        "Android Help" -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://support.google.com/android/"))) }.onFailure { error="Could not open Android Help. Please try again." }
                        "App Permissions" -> androidPage(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        "Mobile Data for Pictures" -> androidPage(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                        else -> section=label
                    }
                } } else Column(Modifier.padding(horizontal=20.dp,vertical=12.dp)) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(when(section) {
                            "About OnKlav" -> "OnKlav ${context.packageManager.getPackageInfo(context.packageName,0).versionName}\n\nMessages, Groups and your apps in one place. Manage apps and games in Settings."
                            "Getting Started" -> "Use Apps to open your apps. The bottom bar switches between Messages, Groups, Apps, Games and Settings. The top-left arrow closes an app and returns to where you came from.\n\nChoose your colors, background and text size in Settings > Appearance."
                            "Messages and Groups" -> "Use the new-message button to choose a contact or enter a number. Add photos, videos or files with the attachment button. Tap media to open it.\n\nSwipe a conversation to archive or delete it. Archive and Trash are at the top of the list. Long-press a message for more actions.\n\nGroups let you share posts and photos with invited members."
                            "Apps and Files" -> "Open Files to browse documents, images, audio and downloads. Use the folder-plus button to create a folder. The pencil beside a file or folder opens its actions.\n\nManage installed apps and their permissions in Settings > Apps."
                            "Reading and Listening" -> "Browse or search in eBooks and AudioBooks. Download a book to keep it in My Library. A book already saved is marked in eBook search.\n\nIn Podcasts, choose a show to see its episodes. You can play without subscribing. Use the down arrow to download an episode, and the X to remove the download. Radio offers Stations, Recent and Favorites."
                            "Android Denied Default Access" -> "Keep your current messaging app as the default while OnKlav picture messaging is being verified. You can allow reading messages and contacts separately in Setup. Changing those permissions does not change your default messenger."
                            "Open-source Licenses" -> "AndroidX / Compose / Media3 — Apache 2.0\nKotlin — Apache 2.0\nApache POI / Office document readers — Apache 2.0\nJakarta Mail for Android — EPL 2.0\nAndroid Activation — EDL 1.0\nNewPipe Extractor — GPL 3.0 or later\nRhino — MPL 2.0\njsoup — MIT\nProtobuf — BSD 3-Clause\nNanoJSON — Apache 2.0\nJump3r / LAME — LGPL 2.1 or later (bundled MP3 codec)\nSherpa-ONNX / Kitten Nano voices — Apache 2.0\nPiper phonemizer / eSpeak NG — GPL 3.0 or later\nONNX Runtime — MIT\nGodot — MIT\nRust libraries — individual dependency licenses\n\nFull licenses and third-party notices accompany the OnKlav source distribution."
                            else -> "Open this setting from the Settings page."
                        }, fontSize=15.sp, lineHeight=22.sp)
                    }
                }
            }
        }
    }
    ErrorDialog(error) { error=null }
}

@Composable
fun GameSettingsDialog(close: () -> Unit) {
    val c=LocalContext.current
    val prefs=remember { c.getSharedPreferences("game_preferences",0) }
    var sounds by rememberSaveable { mutableStateOf(prefs.getBoolean("sounds",true)) }
    var timeout by rememberSaveable { mutableFloatStateOf(prefs.getInt("timeout",30).coerceIn(30,60).toFloat()) }
    var error by remember { mutableStateOf<String?>(null) }
    ReferenceDialog("Game Settings",close,footer={
        DialogAction("Cancel",onClick=close)
        DialogAction("Save") { runCatching {
            check(prefs.edit().putBoolean("sounds",sounds).putInt("timeout",timeout.toInt()).commit()) { "Could not save game preferences" }
            close()
        }.onFailure { error=it.message } }
    }) {
        Column(Modifier.padding(20.dp)) {
            PreferenceToggle("Game Sounds",sounds) { sounds=it }
            Text("Compatibility Timeout · ${timeout.toInt()} seconds",Modifier.padding(top=20.dp),fontSize=16.sp)
            AppearanceSlider(timeout,{ timeout=it },range=30f..60f,steps=5)
            Text("A timeout means no response yet. Late confirmations are still accepted.",Modifier.padding(top=16.dp),fontSize=14.sp)
            Text("Saved for the game ports. Multiplayer and game audio integration are still being completed.",Modifier.padding(top=12.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    ErrorDialog(error) { error=null }
}
