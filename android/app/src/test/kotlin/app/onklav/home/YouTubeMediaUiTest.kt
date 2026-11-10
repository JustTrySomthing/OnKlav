package app.onklav.home

import android.content.res.Configuration
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class YouTubeMediaUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(4){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));compose.mainClock.advanceTimeByFrame()}}
 @Test(timeout=90000)fun explicitHandoffArmsAutoplayAndRotationRestoresToolbar(){
  runBlocking{AppSdk.bootstrap(ApplicationProvider.getApplicationContext())}
  val wide=mutableStateOf(false);val exited=mutableStateOf(false)
  val previousOrientation=compose.activity.requestedOrientation
  compose.runOnUiThread{compose.activity.setContent{
   val config=Configuration(LocalConfiguration.current)
   config.orientation=if(wide.value)Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
   CompositionLocalProvider(LocalConfiguration provides config){OnKlavTheme{
    if(!exited.value)SdkAppScreen("app.onklav.youtube",initialVideoUrl="https://youtu.be/AbC_def-123?t=83",launchKey=1,exit={exited.value=true})
   }}
  }}
  compose.waitForIdle();compose.mainClock.autoAdvance=false
  val model=ViewModelProvider(compose.activity)["sdk-app.onklav.youtube",SdkAppModel::class.java]
  compose.waitUntil(15000){pump();model.frame.optJSONObject("state")?.optString("video")=="AbC_def-123"&&!model.busy}
  // This host lacks WebView profiles, so the native playback request remains pending.
  assertEquals("AbC_def-123",model.webPool.autoplayVideo)
  assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,compose.activity.requestedOrientation)
  compose.onNodeWithContentDescription("YouTube Home").assertExists()
  compose.runOnUiThread{wide.value=true};pump()
  compose.onNodeWithContentDescription("YouTube Home").assertDoesNotExist()
  compose.onNodeWithContentDescription("Bookmarks").assertDoesNotExist()
  compose.onNodeWithTag("sdk-app-body").assertExists()
  compose.runOnUiThread{wide.value=false};pump()
  compose.onNodeWithContentDescription("YouTube Home").assertExists()
  compose.onNodeWithContentDescription("Close").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}
  compose.waitUntil(15000){pump();exited.value}
  assertNull(model.webPool.autoplayVideo)
  pump();assertEquals(previousOrientation,compose.activity.requestedOrientation)
 }
}
