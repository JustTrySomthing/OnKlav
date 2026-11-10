package app.onklav.home

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class FilesTransferTest {
    @Test fun copyFolderInAndMoveItBackWithoutOverwritingAndroidFiles() {
        val c = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(c).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        runBlocking { AppSdk.bootstrap(c) }
        val root = AndroidFilesAccess.root()
        val original = File(root, "Download/Cross storage fixture")
        File(original, "Nested").mkdirs()
        File(original, "Nested/item.txt").writeText("exact bytes")
        File(original, "Empty").mkdir()
        try {
            FileSelectionSession(c).use { session ->
                session.start()
                session.navigate(listOf("external", "android-downloads"))
                val picked = session.state.nodes("items").single { it.optString("name") == original.name }
                session.navigate(listOf("documents"))
                FilesTransfer(c, session) {}.paste(FilesClipboard(picked, true, false), "documents", false, null)
                assertTrue(original.isDirectory)

                val copied = session.state.nodes("items").single { it.optString("name") == original.name }
                session.event("entry:${copied.getString("id")}")
                val nested = session.state.nodes("items").single { it.optString("name") == "Nested" }
                assertTrue(session.state.nodes("items").any { it.optString("name") == "Empty" })
                session.event("entry:${nested.getString("id")}")
                val opened = session.choose(session.state.nodes("items").single())
                assertEquals("exact bytes", File(opened.getString("path")).readText())

                session.navigate(listOf("external", "android-downloads"))
                FilesTransfer(c, session) {}.paste(FilesClipboard(copied, false, true), "android-downloads", true, null)
                assertEquals("exact bytes", File(original, "Nested/item.txt").readText())
                val moved = File(original.parentFile, original.name + " (2)")
                assertEquals("exact bytes", File(moved, "Nested/item.txt").readText())
                assertTrue(File(moved, "Empty").isDirectory)
                session.navigate(listOf("documents"))
                assertTrue(session.state.nodes("items").none { it.optString("id") == copied.getString("id") })
                session.navigate(listOf("trash"))
                assertTrue(session.state.nodes("items").any { it.optString("id") == copied.getString("id") })
            }
        } finally {
            original.deleteRecursively()
            File(original.parentFile, original.name + " (2)").deleteRecursively()
        }
    }
}
