package app.onklav.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.onklav.shared.LauncherLayout
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopChromeDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun capture(tag: String, name: String) {
        compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.cacheDir, "qa-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private fun selectTab(name: String) {
        compose.onNodeWithTag("tab-$name").performClick()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    }

    private fun setting(name: String) {
        compose
            .onNode(hasText(name) and hasAnyAncestor(hasTestTag("settings-pane")))
            .performScrollTo()
            .performClick()
    }

    private fun layout() =
        LauncherLayout.read(
            context.getSharedPreferences("apps_desktop", 0).getString("positions", "")!!,
            platformApps.map { it.id },
        )

    @Test
    fun catalogManagementReturnsToItsOrigin() {
        selectTab("Apps")
        compose.onNodeWithContentDescription("Manage apps").performClick()
        compose.onNodeWithTag("package-center-apps").assertIsDisplayed()
        back()
        compose.onNodeWithTag("package-center-apps").assertDoesNotExist()
        compose.onNodeWithTag("apps-desktop").assertIsDisplayed()

        compose.onNodeWithTag("app-icon-games").performClick()
        compose.onNodeWithContentDescription("Manage games").performClick()
        compose.onNodeWithText("Install a game").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Install a game").assertDoesNotExist()
        compose.onNodeWithTag("games-catalog").assertIsDisplayed()
        compose.onNodeWithTag("settings-pane").assertDoesNotExist()
    }

    @Test
    fun sparseLauncherChromeAndPackageManagement() {
        context.getSharedPreferences("apps_desktop", 0).edit().clear().commit()
        context.getSharedPreferences("package_visibility", 0).edit().clear().commit()
        compose.onNodeWithContentDescription("Add Contact").assertIsDisplayed()
        compose.onNodeWithContentDescription("Enter Phone Number").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open Contacts").assertIsDisplayed()
        listOf("Inbox", "Archive", "Trash").forEach {
            compose.onNodeWithTag("folder-$it").assertIsDisplayed()
        }
        capture("home-header", "messages-header")
        selectTab("Groups")
        compose.onNodeWithContentDescription("Create Group").assertIsDisplayed().performClick()
        compose.onNodeWithText("0 People · Choose 2–10").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        selectTab("Apps")
        capture("apps-desktop", "apps-original-grid")
        // Move an icon into the last row with a real long-press gesture.
        val icon = compose.onNodeWithTag("app-icon-notes").fetchSemanticsNode().boundsInRoot
        val cell = compose.onNodeWithTag("app-cell-14").fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithTag("apps-page-0").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("apps-page-0").performTouchInput {
            down(Offset(icon.center.x - grid.left, icon.top + 28 - grid.top))
            advanceEventTime(650)
            moveTo(
                Offset(icon.center.x - grid.left + 2, icon.top + 30 - grid.top),
                delayMillis = 30,
            )
            moveTo(Offset(cell.center.x - grid.left, cell.top + 28 - grid.top), delayMillis = 300)
            up()
        }
        compose.waitForIdle()
        assertEquals(
            "Drop into an empty bottom cell must preserve gaps",
            "notes",
            layout().slots[14],
        )
        val viewport = compose.onNodeWithTag("apps-grid-viewport").fetchSemanticsNode().boundsInRoot
        val bottomIcon = compose.onNodeWithTag("app-icon-notes").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("apps-grid-viewport").performTouchInput {
            down(Offset(bottomIcon.center.x - viewport.left, bottomIcon.top + 28 - viewport.top))
            advanceEventTime(650)
            moveTo(
                Offset(bottomIcon.center.x - viewport.left - 2, bottomIcon.top + 30 - viewport.top),
                delayMillis = 30,
            )
            moveTo(
                Offset(viewport.width - 2, bottomIcon.top + 28 - viewport.top),
                delayMillis = 200,
            )
        }
        try {
            compose.waitUntil(15000) {
                compose
                    .onAllNodesWithContentDescription("Apps Page 2 of 2")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        } finally {
            compose.onNodeWithTag("apps-grid-viewport").performTouchInput { up() }
        }
        compose.onNodeWithTag("apps-page-dots").assertContentDescriptionEquals("Apps Page 2 of 2")
        assertEquals("notes", layout().slots[29])
        capture("apps-desktop", "apps-second-page")
        // Recreate on the Apps tab, then inspect saved positions and page selection.
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("apps-page-dots").assertContentDescriptionEquals("Apps Page 2 of 2")
        compose.onNodeWithTag("app-icon-notes").assertIsDisplayed()
        assertEquals("", layout().slots[14])
        selectTab("Games")
        compose.onNodeWithText("Play Friends or Alone").assertIsDisplayed()
        compose.onNodeWithTag("game-card-hockey").assertIsDisplayed()
        capture("games-catalog", "games-artwork")
        selectTab("Settings")
        capture("settings-pane", "settings-original-icons")
        setting("Apps")
        compose.onNodeWithTag("package-center-apps").assertIsDisplayed()
        compose.onNodeWithText("Install an App").assertIsDisplayed()
        compose.onNodeWithText("Notes").performClick()
        compose.onNodeWithText("System apps cannot be deleted").assertIsDisplayed()
        compose.onNodeWithText("Hide App").performClick()
        back()
        selectTab("Apps")
        compose.onNodeWithTag("app-icon-notes").assertDoesNotExist()
        selectTab("Settings")
        setting("Games")
        compose.onNodeWithTag("package-center-games").assertIsDisplayed()
        compose.onNodeWithText("Install a Game").assertIsDisplayed()
        capture("package-center-games", "settings-games-manager")
        back()
        setting("Sounds")
        compose.onNodeWithText("Chat Sounds").assertIsDisplayed()
        capture("dialog-Sounds", "settings-sounds-menu")
        compose.onNodeWithText("Close").performClick()
        setting("Security")
        compose.onNodeWithText("Blocked Senders").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        setting("Storage")
        compose.onNodeWithText("Backups").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        // Restore visibility and verify this manager is separate from the launcher.
        setting("Apps")
        compose.onNodeWithText("Notes").performClick()
        compose.onNodeWithText("Show App").performClick()
        back()
        selectTab("Apps")
        compose.onNodeWithTag("app-icon-notes").assertIsDisplayed()
    }
}
