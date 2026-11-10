package app.onklav.home

import android.content.Intent
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w411dp-h891dp") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileSelectionUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(2){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))}}
 private fun visible(text:String){compose.waitUntil(15000){pump();runCatching{compose.onNodeWithText(text,substring=false).assertIsDisplayed()}.isSuccess}}
 @Test(timeout=90000)fun chooseAndSaveStayInsideOurBrowserWithClearControls(){
  val c=compose.activity;runBlocking{AppSdk.bootstrap(c)}
  FileSelectionSession(c).use{s->s.start();s.importSaved(listOf("documents"),File(c.cacheDir,"ui-file").apply{writeText("Example")},"Example.txt","text/plain")}
  var result=emptyList<android.net.Uri>()
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{FileBrowserPane(Intent().putExtra("mode","open").putExtra("multiple",true).putExtra("mimes",arrayOf("text/*"))){result=it}}}}
  visible("Documents");compose.onNodeWithText("Documents",substring=false).performClick();visible("Example.txt");compose.onNodeWithText("Example.txt").performClick();visible("Open (1)")
  screenshot("open");compose.onNodeWithText("Open (1)").performClick();compose.waitUntil(10000){pump();result.size==1};assertEquals(c.packageName+".file-selection",result.first().authority)
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{FileBrowserPane(Intent().putExtra("mode","save").putExtra("name","Export.txt").putExtra("mimes",arrayOf("text/plain"))){result=it}}}}
  visible("Save here");compose.waitUntil(15000){pump();runCatching{compose.onNodeWithTag("file-picker-name").assertIsEnabled()}.isSuccess};compose.onNodeWithTag("file-picker-name").performTextReplacement("New file.txt");screenshot("save");compose.onNodeWithText("Save here").performClick();compose.waitUntil(10000){pump();result.firstOrNull()?.let{runCatching{FileSelectionStore.read(c,it).optString("kind")=="save"}.getOrDefault(false)}==true}
 }
 private fun screenshot(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));File("/tmp/onklav-files-default-$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}}
}
