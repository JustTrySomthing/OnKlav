package app.onklav.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpdateSettingsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun settingsExposeManualCheckInstalledVersionAndAutomaticPreference() {
        val prefs=UpdateChecks.prefs(compose.activity)
        prefs.edit().clear().putBoolean("automatic",true).putLong("checked",System.currentTimeMillis())
            .putInt("foregroundBoot",UpdateChecks.bootCount(compose.activity)).commit()
        compose.setContent {OnKlavTheme{UpdateSettingsDialog{}}}
        compose.onNodeWithText("OnKlav updates").assertExists()
        compose.onNodeWithText("Installed ${BuildConfig.VERSION_NAME}").assertExists()
        compose.onNodeWithText("Last checked",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Check now").assertExists()
        compose.onNodeWithText("Check daily").performClick()
        compose.runOnIdle {
            check(!prefs.getBoolean("automatic",true))
        }
        compose.onNodeWithText("Include release candidates").assertExists()
    }

    @Test @Config(qualifiers="w320dp-h280dp-mdpi",fontScale=1.3f)
    fun availableVersionAndUpdateActionAreVisibleWithoutScrolling() {
        val current=UpdateChecks.current()
        val version=current.copy(patch=current.patch+1,rc=1)
        val release=OnKlavRelease(version,
            "https://github.com/JustTrySomthing/OnKlav/releases/download/v${version.label}/OnKlav-${version.base}-RC.apk",
            133_000_000,"a".repeat(64))
        UpdateChecks.prefs(compose.activity).edit().clear().putBoolean("automatic",false).putBoolean("previews",true)
            .putLong("checked",System.currentTimeMillis())
            .putString("available",release.json().toString()).commit()
        compose.setContent {OnKlavTheme{UpdateSettingsDialog{}}}
        compose.onNodeWithText("${version.label} available").assertIsDisplayed()
        compose.onNodeWithText("Last checked",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Update now").assertIsDisplayed()
        assertEquals(1,compose.onAllNodesWithText(version.label,substring=true).fetchSemanticsNodes().size)
        compose.onNodeWithText(" MB",substring=true).assertDoesNotExist()
    }
}
