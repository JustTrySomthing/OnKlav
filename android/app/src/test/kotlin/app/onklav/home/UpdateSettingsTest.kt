package app.onklav.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpdateSettingsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun settingsExposeManualCheckInstalledVersionAndAutomaticPreference() {
        val prefs=compose.activity.getSharedPreferences("onklav-updates",0)
        prefs.edit().putLong("checked",System.currentTimeMillis()).commit()
        compose.setContent {OnKlavTheme{UpdateSettingsDialog{}}}
        compose.onNodeWithText("OnKlav updates").assertExists()
        compose.onNodeWithText("Installed: OnKlav ${BuildConfig.VERSION_NAME}").assertExists()
        compose.onNodeWithText("Check for updates").assertExists()
        compose.onNodeWithText("Check automatically").performClick()
        compose.runOnIdle {
            check(!prefs.getBoolean("automatic",true))
        }
        compose.onNodeWithText("Include release candidates").assertExists()
    }
}
