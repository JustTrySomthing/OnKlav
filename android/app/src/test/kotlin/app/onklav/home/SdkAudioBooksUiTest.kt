package app.onklav.home
import android.app.Application
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h800dp") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class SdkAudioBooksUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @get:Rule val emptyKeyStore=EmptyKeyStoreRule()
 private fun pump(){repeat(2){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))}}
 private fun visible(t:String){compose.waitUntil(10000){pump();runCatching{compose.onNodeWithText(t,substring=false).assertIsDisplayed()}.isSuccess}}
 @Test(timeout=60000)fun savedCatalogBookOpensItsChapterList(){
  val c=compose.activity.application as Application;runBlocking{AppSdk.bootstrap(c)}
  val opened=AppSdk.sync(c,org.json.JSONObject().put("action","launch").put("id","app.onklav.audiobooks")) as org.json.JSONObject
  val owner=AppSdk.sync(c,org.json.JSONObject().put("action","media_component").put("session",opened.getString("session")).put("node","audiobooks.library")) as org.json.JSONObject
  val record=org.json.JSONObject("""{"id":"987655","title":"Chapter preview fixture","authors":[],"sections":[{"section_number":"1","title":"Chapter one fixture","listen_url":"https://archive.org/download/book/chapter.mp3","playtime":"60"}]}""")
  SdkAudioBooks.addCatalog(c,owner,"lv-987655","${owner.getString("id")}/fixture",connect={error("No network should be needed")},record=record)
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.audiobooks",exit={})}}}
  visible("LibriVox");compose.onNodeWithText("My Library",substring=false).performClick();visible("Chapter preview fixture")
  compose.onNodeWithText("Chapter preview fixture",substring=false).performClick();visible("Chapters");visible("Chapter one fixture")
 }
 @Test(timeout=60000)fun signedAudioBooksStartsAtLibriVoxWithReadyPermissionsAndCleanTabs(){
  runBlocking{AppSdk.bootstrap(compose.activity.application as Application)}
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.audiobooks",exit={})}}}
  visible("LibriVox")
  compose.waitUntil(10000){pump();runCatching{compose.onNodeWithText("Browse LibriVox",substring=false).assertIsEnabled()}.isSuccess}
  val tabs=listOf("discover","recent","library").map{compose.onNodeWithTag("reader-tab-$it").fetchSemanticsNode().boundsInRoot};assertEquals(tabs[0].top,tabs[1].top,1f);assertEquals(tabs[0].top,tabs[2].top,1f)
  compose.onNodeWithText("Your next chapter").assertDoesNotExist();compose.onNodeWithContentDescription("AudioBooks settings").assertExists()
  compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));File("/tmp/onklav-audiobooks-polished.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
  compose.onNodeWithText("Recent",substring=false).performClick();visible("No recent audiobooks")
  compose.onNodeWithText("My Library",substring=false).performClick();visible("Add files")
  compose.onNodeWithText("Add files",substring=false).assertIsEnabled();compose.onNodeWithText("Add folder",substring=false).assertIsEnabled()
  compose.onNodeWithText("Browse",substring=false).performClick();visible("LibriVox")
 }
}
