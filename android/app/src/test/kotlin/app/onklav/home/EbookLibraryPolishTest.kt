package app.onklav.home
import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import okhttp3.HttpUrl.Companion.toHttpUrl
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h800dp") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class EbookLibraryPolishTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @get:Rule val keys=EmptyKeyStoreRule()
 private fun pump(){compose.mainClock.advanceTimeBy(100);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))}
 private fun visible(t:String){val model=androidx.lifecycle.ViewModelProvider(compose.activity).get("sdk-app.onklav.ebooks",SdkAppModel::class.java);compose.waitUntil(15000){pump();runCatching{check(!model.busy&&model.pending==null);compose.onNodeWithText(t).assertIsDisplayed()}.isSuccess}}
 @Test(timeout=60000)fun savedBookIsInertAndNewBookShowsDescriptionAboveDownload(){
  val c=compose.activity.application as Application;runBlocking{AppSdk.bootstrap(c)}
  fun call(j:JSONObject)=AppSdk.sync(c,j)as JSONObject
  val session=call(JSONObject().put("action","launch").put("id","app.onklav.ebooks")).getString("session")
  fun event(a:String)=call(JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action",a)))
  fun reply(p:JSONObject,v:JSONObject)=call(JSONObject().put("action","reply").put("session",session).put("token",p.getString("token")).put("reply",JSONObject().put("ok",true).put("value",v)))
  val rows=JSONArray().put(JSONObject().put("id",11).put("title","Saved book").put("author","Author One"))
  reply(event("book.search").getJSONObject("pending"),JSONObject().put("items",rows).put("next",JSONObject.NULL));reply(event("book.preview.11").getJSONObject("pending"),JSONObject().put("items",rows).put("next",JSONObject.NULL));val p=event("book.acquire.11").getJSONObject("pending")
  fun blob(op:String,a:JSONObject=JSONObject())=call(JSONObject().put("action","blob_host").put("session",session).put("token",p.getString("token")).put("operation",op).put("args",a))
  blob("begin",JSONObject().put("name","Gutenberg-11.epub").put("mime","application/epub+zip"));val data=File(System.getProperty("onklav.project.root"),"docs/fixtures/text-profile.epub").readBytes();blob("append",JSONObject().put("offset",0).put("hex",data.joinToString(""){"%02x".format(it)}));reply(p,blob("finish").put("gutenberg_id",11));AppSdk.sync(c,JSONObject().put("action","close").put("session",session))
  val url="https://www.gutenberg.org/ebooks/search.opds/".toHttpUrl().newBuilder().addQueryParameter("query","").addQueryParameter("start_index","1").build().toString();val name=java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString(""){"%02x".format(it)}
  File(c.cacheDir,"ebook-opds").mkdirs();File(c.cacheDir,"ebook-opds/$name").writeText("<feed><entry><id>https://www.gutenberg.org/ebooks/11.opds</id><title>Saved book</title><author><name>Author One</name></author></entry><entry><id>https://www.gutenberg.org/ebooks/12.opds</id><title>New book</title><author><name>Author Two</name></author></entry></feed>")
  File(c.cacheDir,"ebook-summary-v2-12").writeText("Two characters explore a mystery.")
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.ebooks",exit={})}}};visible("Browse books");compose.onNodeWithText("Browse books").performClick();visible("Saved book")
  compose.onNodeWithTag("sdk-book.preview.11").assertIsNotEnabled();compose.onNodeWithText("Already in your library",substring=true).assertIsDisplayed();compose.onNodeWithTag("sdk-book.preview.11").performClick();pump();compose.onNodeWithText("Download book").assertDoesNotExist()
  compose.onNodeWithTag("sdk-book.preview.12").performClick();visible("Two characters explore a mystery.")
  val author=compose.onNodeWithTag("sdk-book.preview.author").fetchSemanticsNode().boundsInRoot;val description=compose.onNodeWithTag("sdk-book.preview.description").fetchSemanticsNode().boundsInRoot;val download=compose.onNodeWithTag("sdk-book.acquire.12").fetchSemanticsNode().boundsInRoot;assertTrue(author.bottom<=description.top);assertTrue(description.bottom<=download.top)
 }
}
