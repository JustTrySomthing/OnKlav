package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
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
    Box(Modifier.fillMaxSize().testTag("settings-pane"), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight()
                .verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AlignedCardGrid(listOf("Appearance", "Profile"), gap = 8.dp) { name, modifier ->
                SettingsCard(name, modifier) { open(name) }
            }
            AlignedCardGrid(
                listOf("Setup", "Sounds", "Security", "Storage", "Read Aloud", "Backups", "Games",
                    "Apps", "Walkie Talkie", "Quiet Hours", "Reminders", "Help", "About", "Advanced"),
                maxColumns = 3,
                gap = 8.dp,
            ) { name, modifier ->
                SettingsCard(name, modifier) { open(name) }
            }
        }
    }
}

@Composable
private fun SettingsCard(name: String, modifier: Modifier, open: () -> Unit) {
    Surface(
        onClick = open,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(androidx.compose.ui.res.painterResource(settingsIcons.getValue(name)), null,
                Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        }
    }
}
