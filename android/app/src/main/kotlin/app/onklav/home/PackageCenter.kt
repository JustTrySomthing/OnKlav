package app.onklav.home

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.onklav.shared.DialogAction
import app.onklav.shared.ReferenceDialog
import app.onklav.shared.R

@Composable
fun PackageCenter(games: Boolean, exit: () -> Unit) {
    if (games) GamesPackageCenter(exit) else SdkAppsCenter(exit)
}

@Composable
internal fun ManageGamesAction(open: () -> Unit) {
    OriginalAction(R.drawable.ref_nav_settings, "Manage games", action = open)
}

@Composable
internal fun PackageManagementHeader(title: String, busy: Boolean, exit: () -> Unit) {
    HomeHeader(title, leading = {
        OriginalAction(R.drawable.ref_action_back, "Back", enabled = !busy, action = exit)
    })
}

@Composable
internal fun RemoveIncludedDialog(title: String, games: Boolean, dismiss: () -> Unit, remove: () -> Unit) {
    val catalog = if (games) "Games" else "Apps"
    ReferenceDialog("Remove $title?", dismiss, footer = {
        DialogAction("Cancel") { dismiss() }
        DialogAction("Remove", destructive = true) { remove() }
    }) {
        Text(
            "$title will be removed from $catalog. You can restore it here. Saved data is kept.",
            Modifier.padding(20.dp),
        )
    }
}
