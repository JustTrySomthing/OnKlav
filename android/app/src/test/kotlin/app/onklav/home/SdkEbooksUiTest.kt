package app.onklav.home
import android.app.Application
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w411dp-h891dp") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class SdkEbooksUiTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @get:Rule val emptyKeyStore=EmptyKeyStoreRule()
 private fun pump(){repeat(2){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))}}
 @Test(timeout=60000)fun signedEbooksLibraryAndReaderShowOurSpeakerAndVoiceSettings(){
  val c=compose.activity.application as Application;runBlocking{AppSdk.bootstrap(c)}
  fun call(j:JSONObject)=AppSdk.sync(c,j)as JSONObject
  val s=call(JSONObject().put("action","launch").put("id","app.onklav.ebooks")).getString("session")
  fun event(a:String)=call(JSONObject().put("action","event").put("session",s).put("event",JSONObject().put("action",a)))
  fun reply(p:JSONObject,v:JSONObject)=call(JSONObject().put("action","reply").put("session",s).put("token",p.getString("token")).put("reply",JSONObject().put("ok",true).put("value",v)))
  event("book.discover");val search=event("book.search").getJSONObject("pending");reply(search,JSONObject().put("items",JSONArray().put(JSONObject().put("id",11).put("title","Fixture").put("author","Author"))).put("next",JSONObject.NULL));val detail=event("book.preview.11").getJSONObject("pending");reply(detail,JSONObject().put("items",JSONArray().put(JSONObject().put("id",11).put("title","Fixture").put("author","Author").put("description","A short story."))).put("next",JSONObject.NULL));val p=event("book.acquire.11").getJSONObject("pending")
  fun blob(op:String,a:JSONObject=JSONObject())=call(JSONObject().put("action","blob_host").put("session",s).put("token",p.getString("token")).put("operation",op).put("args",a))
  blob("begin",JSONObject().put("name","Book.epub").put("mime","application/epub+zip"));val bytes=File(System.getProperty("onklav.project.root"),"docs/fixtures/text-profile.epub").readBytes();blob("append",JSONObject().put("offset",0).put("hex",bytes.joinToString(""){"%02x".format(it)}));reply(p,blob("finish"));AppSdk.sync(c,JSONObject().put("action","close").put("session",s))
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.ebooks",exit={})}}}
  compose.waitUntil(15000){pump();runCatching{compose.onNodeWithText("Project Gutenberg",substring=false).assertIsDisplayed()}.isSuccess};compose.onNodeWithText("My Library",substring=false).performClick();compose.waitUntil(10000){pump();runCatching{compose.onNodeWithText("Add EPUB",substring=false).assertIsDisplayed()}.isSuccess};compose.onNodeWithText("Add EPUB",substring=false).assertIsEnabled();compose.onNodeWithText("OnKlav sample book").performClick()
  compose.waitUntil(15000){pump();runCatching{compose.onNodeWithContentDescription("Play read aloud").assertIsDisplayed()}.isSuccess};compose.onNodeWithContentDescription("Play read aloud").assertIsEnabled()
  val label=compose.onNodeWithText("Read aloud").fetchSemanticsNode().boundsInRoot
  val play=compose.onNodeWithContentDescription("Play read aloud").fetchSemanticsNode().boundsInRoot
  org.junit.Assert.assertEquals(label.center.y,play.center.y,2f)
  compose.onNodeWithText("End of chapter").assertExists()
  compose.onNodeWithTag("ebook-next").performScrollTo().performClick()
  compose.waitUntil(10000){pump();runCatching{compose.onNodeWithText("End of book").assertExists()}.isSuccess}
  compose.onNodeWithText("This UI action is no longer available").assertDoesNotExist()
  compose.onNodeWithTag("ebook-next").assertIsNotEnabled();compose.onNodeWithText("Next page continues where this page ends.").assertDoesNotExist();compose.onNodeWithText("Previous chapter").assertDoesNotExist();compose.onNodeWithText("Previous").assertExists()

  compose.runOnUiThread{val view=compose.activity.window.decorView;val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap));File("/tmp/onklav-ebooks-reader.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
  compose.onNodeWithContentDescription("Reader settings").assertExists()
  compose.onNodeWithTag("ebook-previous").performScrollTo().performClick()
  compose.waitUntil(10000){pump();runCatching{compose.onNodeWithText("End of chapter").assertExists()}.isSuccess}
  compose.onNodeWithText("This UI action is no longer available").assertDoesNotExist()
 }
}
