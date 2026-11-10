package app.onklav.home
import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
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
class SdkRadioUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(2){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))}}
 @Test(timeout=60000)fun separateRadioScreensUseRealSignedAppAndKeepPlayerDocked(){
  runBlocking{AppSdk.bootstrap(compose.activity.application as Application)}
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.radio",exit={})}}}
  compose.waitUntil(15000){pump();runCatching{compose.onNodeWithText("Stations").assertIsDisplayed()}.isSuccess}
  compose.onNodeWithText("Choose a station").assertIsDisplayed()
  compose.onNodeWithContentDescription("Open music visualizer").assertIsDisplayed().performClick()
  compose.waitUntil(10000){pump();runCatching{compose.onNodeWithTag("radio-visualizer-portrait").assertExists()}.isSuccess}
  compose.onNodeWithText("Audio permission is required. The microphone is never recorded.").assertExists()
  compose.onNodeWithText("Enable audio visuals").assertExists()
  compose.onNodeWithContentDescription("Close music visualizer").performClick()
  compose.waitUntil(10000){pump();runCatching{compose.onNodeWithTag("radio-visualizer-portrait").assertDoesNotExist()}.isSuccess}

  compose.runOnUiThread {
   val view=compose.activity.window.decorView
   val image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
   view.draw(android.graphics.Canvas(image))
   File("/tmp/onklav-radio-listen.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  }
  compose.waitUntil(10000){pump();runCatching{compose.onNodeWithText("Recent",substring=false).assertIsEnabled()}.isSuccess};compose.onNodeWithText("Recent",substring=false).performSemanticsAction(SemanticsActions.OnClick){it()};pump()
  compose.waitUntil(10000){pump();runCatching{compose.onNodeWithText("No recent stations").assertExists()}.isSuccess}
  compose.onNodeWithText("Choose a station").assertIsDisplayed()
  compose.onNodeWithText("Favorites",substring=false).performSemanticsAction(SemanticsActions.OnClick){it()};pump()
  compose.waitUntil(10000){pump();runCatching{compose.onNodeWithText("Your Favorite stations").assertIsDisplayed()}.isSuccess}
  compose.onNodeWithText("Choose a station").assertIsDisplayed()
 }
}
