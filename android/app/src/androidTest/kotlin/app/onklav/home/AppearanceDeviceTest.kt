package app.onklav.home

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.onklav.shared.*
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun capture(tag: String, name: String) {
        compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.cacheDir, "qa-$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }

    private fun open() {
        compose.onNodeWithText("Appearance").performScrollTo().performClick()
        compose.onNodeWithText("How does this look?").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun stagedAppearanceThemesAndDataOnlyPackagesSurviveRecreation() {
        val initial = Appearance(context).spec
        val store = AppearanceStore(context)
        val themeName = "UI Regression " + java.util.UUID.randomUUID().toString().take(8)
        try {
            compose.onNodeWithText("Settings").performClick()
            capture("settings-pane", "settings-dark")
            open()
            capture("dialog-Appearance", "appearance-default")
            compose.onNodeWithText("Backgrounds & Overlay").performScrollTo().performClick()
            capture("dialog-Backgrounds", "background-default")
            compose
                .onNodeWithContentDescription("Cosmic Drift")
                .performScrollTo()
                .assertIsSelected()
            capture("dialog-Backgrounds", "background-gallery")
            val rootBounds =
                compose.onNodeWithTag("dialog-Backgrounds").fetchSemanticsNode().boundsInRoot
            val titleBounds =
                compose.onNodeWithText("Backgrounds").fetchSemanticsNode().boundsInRoot
            File(context.cacheDir, "qa-gallery-layout.txt")
                .writeText("surface=$rootBounds; title=$titleBounds")
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap
                ->
                File(context.cacheDir, "qa-background-gallery-system.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText(if (initial.dark) "Dark" else "Light").performClick()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals("Cancel must not apply staged changes", initial, Appearance(context).spec)
            open()
            compose.onNodeWithContentDescription("Appearance Help").performClick()
            capture("dialog-Appearance Help", "appearance-help")
            compose.onNodeWithText("Copy All").assertIsDisplayed().performClick()
            val clipboard =
                context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.adoptShellPermissionIdentity(
                "android.permission.READ_CLIPBOARD_IN_BACKGROUND"
            )
            try {
                assertEquals(appearanceHelp, clipboard.primaryClip!!.getItemAt(0).text.toString())
            } finally {
                automation.dropShellPermissionIdentity()
            }
            compose.onNodeWithText("Save Text File").assertIsDisplayed()
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Font Style · Clean Sans").performScrollTo().performClick()
            capture("dialog-Font Style", "font-choices")
            compose.onNodeWithText("Typewriter").performScrollTo().performClick()
            compose.onNodeWithText("Load & Save Themes").performScrollTo().performClick()
            compose.onNodeWithText("Neon Synthwave · Dark").assertIsDisplayed()
            capture("dialog-Load & Save Themes", "theme-chooser")
            compose
                .onNodeWithContentDescription("Load Theme: Cherry Blossom")
                .performScrollTo()
                .performClick()
            compose.onNodeWithText("Font Style · Clean Sans").performScrollTo().performClick()
            capture("dialog-Font Style", "font-choices-light")
            compose.onNodeWithText("Typewriter").performScrollTo().performClick()
            // A dialog recreation must retain the draft without changing the live settings.
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Font Style · Typewriter").performScrollTo().assertIsDisplayed()
            assertEquals(initial, Appearance(context).spec)
            compose
                .onNodeWithContentDescription("Blue")
                .performScrollTo()
                .performClick()
                .assertIsSelected()
            compose.onNodeWithText("Gradient Bubbles").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Font Size").performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.SetProgress
            ) {
                it(2f)
            }
            compose.onNodeWithTag("appearance-message-preview").performScrollTo()
            capture("dialog-Appearance", "appearance-preview")
            compose.onNodeWithText("Load & Save Themes").performScrollTo().performClick()
            compose.onNodeWithText("Save Current Theme").performScrollTo().performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement(themeName)
            compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
            compose.waitUntil(30000) { store.themes().containsKey(themeName) }
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Apply").performClick()
            compose.waitUntil(30000) {
                Appearance(context).spec.font == 4 && !Appearance(context).spec.dark
            }
            compose.activityRule.scenario.recreate()
            assertEquals(4, Appearance(context).spec.font)
            assertFalse(Appearance(context).spec.dark)
            assertEquals(1, Appearance(context).spec.palette)
            assertEquals(20, Appearance(context).spec.size)
            assertFalse(Appearance(context).spec.gradient)
            compose.onNodeWithText("Appearance").assertIsDisplayed()
            capture("settings-pane", "settings-light")
            open()
            compose.onNodeWithText("Backgrounds & Overlay").performScrollTo().performClick()
            compose.onNodeWithText("Background Opacity").performScrollTo().assertIsDisplayed()
            val initialOpacity = 100 - Appearance(context).spec.overlayLight
            compose.onNodeWithContentDescription("Background Opacity").performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.SetProgress
            ) {
                it(80f)
            }
            capture("dialog-Backgrounds", "background-picker")
            compose.onNodeWithText("No Background").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Background Opacity").assertIsNotEnabled()
            compose.onNodeWithText("Reset to Default Background").performScrollTo().performClick()
            compose.waitUntil(30000) {
                compose
                    .onAllNodesWithText("Current: Cosmic Drift")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText("Current: Cosmic Drift").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Backgrounds & Overlay").performScrollTo().performClick()
            compose.waitUntil(30000) {
                compose
                    .onAllNodesWithText("Current: Cherry Blossom")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText("Current: Cherry Blossom").performScrollTo().assertIsDisplayed()
            assertEquals(
                initialOpacity.toFloat(),
                compose
                    .onNodeWithContentDescription("Background Opacity")
                    .fetchSemanticsNode()
                    .config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo]
                    .current,
            )
            compose.onNodeWithContentDescription("Background Opacity").performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.SetProgress
            ) {
                it(75f)
            }
            compose.onNodeWithText("No Background").performScrollTo().performClick()
            compose.onNodeWithText("Save").performClick()
            compose.onNodeWithText("Apply").performClick()
            compose.waitUntil(30000) { Appearance(context).spec.background.isEmpty() }
            assertEquals(25, Appearance(context).spec.overlayLight)
            assertEquals(initial.overlayDark, Appearance(context).spec.overlayDark)

            // Run the same codec/store/provider used by Files import/export/share on Android.
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val image =
                ByteArrayOutputStream()
                    .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    .toByteArray()
            bitmap.recycle()
            val imported =
                store.importTheme(ThemePackage.pack("Image Theme", AppearanceSpec(), image))
            assertTrue(imported.second.background.startsWith("custom:"))
            val exported = store.export(imported.first, imported.second)
            val parsed = ThemePackage.read(exported)
            assertEquals("image", parsed.spec.background)
            assertNotNull(parsed.image)
            val uri = ExportProvider.stage(context, "Image Theme.onklav-t", exported, "onklav-t")
            assertEquals("application/octet-stream", context.contentResolver.getType(uri))
            assertArrayEquals(
                exported,
                context.contentResolver.openInputStream(uri)!!.use { it.readBytes() },
            )
            store.save("Imported QA Theme", imported.second)
            assertEquals(
                imported.second,
                AppearanceStore(context).themes().getValue("Imported QA Theme"),
            )
            try {
                store.save("imported qa theme", imported.second)
                fail("Duplicate theme overwritten without confirmation")
            } catch (expected: IllegalArgumentException) {}
            store.remove("Imported QA Theme")
            store.removeImage(imported.second.background, Appearance(context))
            // Built-in artwork must be embedded, so sharing does not depend on another app's
            // catalog.
            val bundled =
                ThemePackage.read(
                    store.export("Built-in QA", AppearanceSpec(background = "cosmos"))
                )
            assertEquals("image", bundled.spec.background)
            assertTrue(bundled.image!!.size <= 1024 * 1024)
            val provisional = store.importImage(image)
            assertFalse(store.gallery().contains(provisional))
            store.discardUnreferenced(listOf(provisional), Appearance(context).spec)
            assertFalse(runCatching { store.source(provisional).close() }.isSuccess)
        } finally {
            store.remove(themeName)
            // Persist cleanup; the activity is recreated by its rule for the next test.
            Appearance(context).apply(initial)
        }
    }
}
