package app.onklav.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.onklav.shared.NativeCore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun editAutosaveAndReloadAcrossActualAndroidRustBridge() {
        compose.onNodeWithText("Apps").performClick()
        compose.onNodeWithText("RSS Reader").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Manage Feeds").assertIsDisplayed()
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Notes").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Notes").assertIsDisplayed()
        val hostContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.onklav.home.prototype.rewrite", hostContext.packageName)
        val engine =
            hostContext.packageManager.getActivityInfo(
                android.content.ComponentName(hostContext, "app.onklav.games.GameActivity"),
                0,
            )
        assertFalse(engine.exported)
        assertEquals(hostContext.applicationInfo.uid, engine.applicationInfo.uid)
        compose.onNodeWithContentDescription("Notes Settings").performClick()
        compose.onNodeWithText("About Saved Notes").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Saved Notes").performClick()
        compose.waitForIdle()
        val drawer =
            compose
                .onNodeWithTag("saved-notes-drawer")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue("Drawer must start at the left edge: $drawer", drawer.left < 2f)
        assertTrue("Drawer must have a visible width", drawer.width > 100f)
        compose.onNodeWithContentDescription("Close Saved Notes").performClick()
        compose.waitUntil(30000) {
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 2
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Native Rewrite Check")
        compose
            .onAllNodes(hasSetTextAction())[1]
            .performTextReplacement("Kotlin screen, Rust storage.\nSaved on Android.")
        compose.waitUntil(30000) {
            compose
                .onAllNodes(
                    hasText(
                        "${"Kotlin screen, Rust storage.\nSaved on Android.".length} / 100,000 · Saved"
                    )
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = context.getSharedPreferences("notes", 0).getString("last", null)!!
        val result =
            JSONObject(
                NativeCore.dispatch(
                    context.filesDir.absolutePath,
                    JSONObject().put("op", "load").put("id", id).toString(),
                )
            )
        assertTrue(result.toString(), result.getBoolean("ok"))
        assertEquals("Native Rewrite Check", result.getJSONObject("value").getString("title"))
        assertEquals(
            "Kotlin screen, Rust storage.\nSaved on Android.",
            result.getJSONObject("value").getString("text"),
        )
        val traversal =
            JSONObject(
                NativeCore.dispatch(
                    context.filesDir.absolutePath,
                    "{\"op\":\"load\",\"id\":\"../private\"}",
                )
            )
        assertFalse(traversal.getBoolean("ok"))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Native Rewrite Check").assertIsDisplayed()
        // Regression: clearing a persisted document must not resurrect the old text.
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("")
        compose.waitUntil(30000) {
            compose.onAllNodes(hasText("0 / 100,000 · Saved")).fetchSemanticsNodes().isNotEmpty()
        }
        val cleared =
            JSONObject(
                NativeCore.dispatch(
                    context.filesDir.absolutePath,
                    JSONObject().put("op", "load").put("id", id).toString(),
                )
            )
        assertTrue(cleared.toString(), cleared.getBoolean("ok"))
        assertEquals("", cleared.getJSONObject("value").getString("title"))
        assertEquals("", cleared.getJSONObject("value").getString("text"))
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30000) {
            compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 2
        }
        for (index in 0..1) {
            assertEquals(
                "",
                compose
                    .onAllNodes(hasSetTextAction())[index]
                    .fetchSemanticsNode()
                    .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText]
                    .text,
            )
        }
    }
}
