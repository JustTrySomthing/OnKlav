package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*

private val settingsIcons =
    mapOf(
        "Appearance" to app.onklav.shared.R.drawable.ref_setting_appearance,
        "Profile" to app.onklav.shared.R.drawable.ref_setting_profile,
        "Setup" to app.onklav.shared.R.drawable.ref_setting_setup,
        "Sounds" to app.onklav.shared.R.drawable.ref_setting_sounds,
        "Security" to app.onklav.shared.R.drawable.ref_setting_security,
        "Storage" to app.onklav.shared.R.drawable.ref_setting_storage,
        "Read Aloud" to app.onklav.shared.R.drawable.ref_setting_read_aloud,
        "Backups" to app.onklav.shared.R.drawable.ref_setting_backups,
        "Games" to app.onklav.shared.R.drawable.ref_setting_games,
        "Apps" to app.onklav.shared.R.drawable.ref_setting_apps,
        "Walkie Talkie" to app.onklav.shared.R.drawable.ref_setting_walkie_talkie,
        "Quiet Hours" to app.onklav.shared.R.drawable.ref_setting_quiet_hours,
        "Reminders" to app.onklav.shared.R.drawable.ref_setting_reminders,
        "About" to app.onklav.shared.R.drawable.ref_setting_about,
        "Advanced" to app.onklav.shared.R.drawable.ref_setting_advanced,
        "Help" to app.onklav.shared.R.drawable.ref_setting_help,
    )

@Composable
fun SettingsPane(open: (String) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize().testTag("settings-pane")) {
        val columns = if (fontScale > 1.3f || maxWidth - 16.dp < 300.dp) 1 else 2
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max=760.dp).fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            @Composable
            fun row(names: List<String>, slots: Int, shortcut: Boolean = false) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = if (shortcut) 0.dp else 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    names.forEach { name ->
                        Surface(
                            onClick = { open(name) },
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                Modifier.padding(
                                    start = 14.dp,
                                    end = 12.dp,
                                    top = 12.dp,
                                    bottom = 12.dp,
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    androidx.compose.ui.res.painterResource(
                                        settingsIcons.getValue(name)
                                    ),
                                    null,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    name,
                                    Modifier.weight(1f).padding(start = 10.dp),
                                    fontSize = 14.sp,
                                    maxLines =
                                        if (columns == 1) 3 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    repeat(slots - names.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            val shortcuts = columns
            listOf("Appearance", "Profile").chunked(shortcuts).forEach { row(it, shortcuts, true) }
            Spacer(Modifier.height(2.dp))
            listOf(
                    "Setup",
                    "Sounds",
                    "Security",
                    "Storage",
                    "Read Aloud",
                    "Backups",
                    "Games",
                    "Apps",
                    "Walkie Talkie",
                    "Quiet Hours",
                    "Reminders",
                    "Help",
                    "About",
                    "Advanced",
                )
                .chunked(columns)
                .forEach { row(it, columns) }
        }
    }
}
