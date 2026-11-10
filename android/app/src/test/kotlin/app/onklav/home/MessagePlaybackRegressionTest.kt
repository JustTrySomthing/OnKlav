package app.onklav.home

import android.media.MediaPlayer
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import app.onklav.shared.OnKlavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class MessagePlaybackRegressionTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(2){compose.mainClock.advanceTimeBy(50);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))}}
 @Test(timeout=60000)fun audioPreparesPlaysAndResumesWithoutInvalidPause(){
  checkMedia("audio/mpeg")
 }
 @Test(timeout=60000)fun videoPreparesPlaysAndResumesWithoutInvalidPause(){
  checkMedia("video/mp4")
 }
 @Test(timeout=60000)fun audioViewerDialogAutoplays(){checkViewer("audio/mpeg")}
 @Test(timeout=60000)fun videoViewerDialogAutoplays(){checkViewer("video/mp4")}
 private fun checkViewer(mime:String){
  ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(60000,1)}
  val part=MessagePart("content://attachment/dialog",mime,"Dialog attachment")
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{ConversationMediaViewer(listOf(ChatMedia(Message(0,"",0,false,parts=listOf(part)),part)),part.uri,org.json.JSONObject(),{_,_->},{})}}}
  pump();if(mime.startsWith("video/"))createVideoSurface()
  compose.waitUntil(10000){pump();runCatching{compose.onNodeWithText("Pause",substring=false).assertIsDisplayed()}.isSuccess}
  repeat(4){pump()};compose.onNodeWithText("Pause",substring=false).assertIsDisplayed()
 }
 @Test(timeout=60000)fun immediatePreparationCannotBePausedByInitialClipSetup(){
  ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(60000,0)}
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{InternalMessagePlayer(MessagePart("content://attachment/immediate","audio/mpeg","Immediate"),true)}}}
  compose.waitUntil(10000){pump();runCatching{compose.onNodeWithText("Pause",substring=false).assertIsDisplayed()}.isSuccess}
  repeat(4){pump()}
  compose.onNodeWithText("Pause",substring=false).assertIsDisplayed()
 }
 @Test(timeout=60000)fun rotatingVideoHidesChromeAndRestoresItInPortrait(){
  ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(60000,100)}
  val wide=mutableStateOf(false);val visible=mutableStateOf(true)
  val previousOrientation=compose.activity.requestedOrientation
  val part=MessagePart("content://attachment/rotation","video/mp4","Video")
  compose.runOnUiThread{compose.activity.setContent{
   val configuration=android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current)
   configuration.orientation=if(wide.value)android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT
   androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides configuration){OnKlavTheme{
    if(visible.value)ConversationMediaViewer(listOf(ChatMedia(Message(0,"",0,false,parts=listOf(part)),part)),part.uri,org.json.JSONObject(),{_,_->},{})
   }}
  }}
  pump();compose.onNodeWithText("1 / 1").assertExists()
  assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,compose.activity.requestedOrientation)
  compose.runOnUiThread{wide.value=true};pump()
  compose.onNodeWithText("1 / 1").assertDoesNotExist()
  compose.onNodeWithText("Full screen").assertDoesNotExist()
  compose.onNodeWithTag("attachment-video").assertExists()
  compose.onNodeWithText("−10s").assertDoesNotExist()
  compose.runOnUiThread{wide.value=false};pump()
  compose.onNodeWithText("1 / 1").assertExists()
  compose.onNodeWithText("−10s").assertExists()
  compose.runOnUiThread{visible.value=false};pump()
  assertEquals(previousOrientation,compose.activity.requestedOrientation)
 }
 private fun createVideoSurface(){
  // Robolectric does not deliver real Surface lifecycle callbacks.
  fun surface(v:android.view.View):android.view.SurfaceView?{
   if(v is android.view.SurfaceView)return v
   if(v is android.view.ViewGroup)for(i in 0 until v.childCount)surface(v.getChildAt(i))?.let{return it}
   return null
  }
  compose.runOnUiThread{
   val root=org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView?:compose.activity.window.decorView
   val view=requireNotNull(surface(root))
   val holder=shadowOf(view).fakeSurfaceHolder
   holder.callbacks.toList().forEach{it.surfaceCreated(holder)}
  }
 }
 private fun checkMedia(mime:String){
  ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(60000,500)}
  val visible=mutableStateOf(true)
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{if(visible.value)InternalMessagePlayer(MessagePart("content://attachment/fixture",mime,"Fixture"),true)}}}
  pump()
  if(mime.startsWith("video/"))createVideoSurface()
  // Leaving before preparation must not call pause() on a preparing MediaPlayer.
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
  pump();compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
  if(mime.startsWith("video/"))createVideoSurface()
  compose.waitUntil(10000){pump();runCatching{compose.onNodeWithText("Pause",substring=false).assertIsEnabled()}.isSuccess}
  compose.onAllNodesWithText("Playback failed",substring=true).assertCountEquals(0)
  // Prepared while backgrounded: first foreground playback begins without another tap.
  compose.onNodeWithText("Pause",substring=false).assertIsDisplayed()
  compose.onNodeWithText("Pause",substring=false).performClick();pump()
  compose.onNodeWithText("Play",substring=false).assertIsEnabled()
  compose.onNodeWithText("Play",substring=false).performClick();pump()
  compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED);pump()
  compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);pump()
  compose.onNodeWithText("Play",substring=false).assertIsEnabled()
  compose.onAllNodesWithText("Playback failed",substring=true).assertCountEquals(0)
  compose.runOnUiThread{visible.value=false};pump()
 }
}
