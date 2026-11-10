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
class SdkPodcastsUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @get:Rule val emptyKeyStore=EmptyKeyStoreRule()
 private fun pump(){repeat(2){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))}}
 private val model get()=androidx.lifecycle.ViewModelProvider(compose.activity).get("sdk-app.onklav.podcasts",SdkAppModel::class.java)
 private fun visible(text:String){compose.waitUntil(10000){pump();runCatching{check(!model.busy&&model.pending==null);compose.onNodeWithText(text,substring=false).assertIsDisplayed()}.isSuccess}}
 @Test(timeout=60000)fun signedPodcastsHasPinnedTabsTopicsSettingsAndRecoverableNavigation(){
  runBlocking{AppSdk.bootstrap(compose.activity.application as Application)}
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.podcasts",exit={})}}}
  visible("Find podcasts")
  compose.waitUntil(10000){pump();runCatching{compose.onNodeWithTag("sdk-pod.next").assertExists()}.isSuccess}
  val tabs=listOf("pod.discover","pod.recent","pod.library").map{compose.onNodeWithTag("sdk-$it").fetchSemanticsNode().boundsInRoot}
  assertEquals(tabs[0].top,tabs[1].top,1f);assertEquals(tabs[0].top,tabs[2].top,1f)
  compose.onNodeWithText("Find your next listen").assertDoesNotExist()
  compose.onNodeWithTag("sdk-pod.topic").assertExists()
  compose.onNodeWithText("Choose an episode").assertIsDisplayed()
  compose.runOnUiThread{val view=compose.activity.window.decorView;val image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(image));File("/tmp/onklav-podcasts-polished.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
  compose.onNodeWithText("Recent",substring=false).performClick();visible("No recent episodes")
  compose.onNodeWithTag("sdk-pod.topic").assertDoesNotExist()
  compose.onNodeWithTag("sdk-pod.library").performClick();visible("No subscriptions yet")
  compose.onNodeWithText("Browse podcasts",substring=false).performClick();visible("Find podcasts")
 }
 @Test(timeout=60000)fun podcastSettingsExposesSourcesAndCloses(){
  runBlocking{AppSdk.bootstrap(compose.activity.application as Application)}
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.podcasts",exit={})}}}
  visible("Find podcasts")
  compose.onNodeWithContentDescription("Podcast settings").performClick()
  compose.waitUntil(5000){pump();model.frame.optBoolean("menu_open")&&!model.busy}
  repeat(4){pump()}
  val roots=compose.onAllNodes(isRoot(),useUnmergedTree=true).fetchSemanticsNodes().size
  File("/tmp/podcast-settings-roots.txt").writeText((0 until roots).joinToString("\n"){compose.onAllNodes(isRoot(),useUnmergedTree=true)[it].printToString()})
  compose.onNodeWithTag("sdk-pod.feed.url").assertExists()
  compose.onNodeWithText("Add podcast",substring=false).assertExists()
  compose.onNodeWithText("Done",substring=false).performClick()
  compose.waitUntil(5000){pump();!model.frame.optBoolean("menu_open")&&!model.busy}
 }
}
