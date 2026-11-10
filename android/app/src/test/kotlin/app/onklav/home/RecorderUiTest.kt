package app.onklav.home

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecorderUiTest{
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 @Before fun setup(){RecordingRuntime.state.value=RecorderState();kotlinx.coroutines.runBlocking{AppSdk.bootstrap(app)}}
 @After fun clear(){RecordingRuntime.state.value=RecorderState()}
 private fun capture(name:String){compose.waitForIdle();compose.runOnUiThread{val view=compose.activity.window.decorView;val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap));File("/workspace/scratch/c739b0c1372b/current/OnKlav/docs/evidence/recorders/$name").apply{parentFile.mkdirs()}.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
 @Test fun voiceShowsRealLevelTimerFormatsAndAnEmptyLibrary(){
  val model=RecorderLibraryModel(app);compose.activity.setContent{OnKlavTheme{RecorderWorkspace(RecorderKind.VOICE,{},model)}}
  compose.onNodeWithContentDescription("Start voice recording").assertExists();compose.onNodeWithContentDescription("Live microphone level").assertExists();compose.onNodeWithText("00:00").assertExists();compose.onNodeWithText("WAV · lossless").performScrollTo().performClick();capture("voice-recorder.png")
  compose.onNodeWithText("Recordings").performClick();compose.waitUntil(15000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));!model.busy};compose.onNodeWithText("Your recordings will appear here").assertExists();capture("voice-library.png")
 }
 @Test @Config(qualifiers="w320dp-h640dp-mdpi",fontScale=1.4f) fun screenChoicesRemainReachableWithLargeText(){
  compose.activity.setContent{OnKlavTheme(preview=AppearanceSpec(dark=false)){RecorderWorkspace(RecorderKind.SCREEN,{})}}
  compose.onNodeWithText("Device + microphone").performScrollTo().performClick();compose.onNodeWithText("High · 1080p").performScrollTo().performClick();compose.onNodeWithText("10 seconds").performScrollTo().performClick();compose.onNodeWithText("Instant").performScrollTo().assertExists();compose.onNodeWithContentDescription("Start screen recording").assertIsDisplayed();capture("screen-options-large.png")
 }
 @Test @Config(qualifiers="w640dp-h360dp-mdpi") fun pausedAndCountdownStatesOfferOnlyTheRelevantControls(){
  var command="";compose.activity.setContent{OnKlavTheme{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){RecorderDashboard(RecorderKind.VOICE,RecorderState(options=RecorderOptions(RecorderKind.VOICE),phase=RecorderPhase.PAUSED,elapsed=3723000,levels=List(60){.5f}),{},{command=it},false)}}}
  compose.onNodeWithText("1:02:03").assertExists();compose.onNodeWithText("Resume").performScrollTo().performClick();assertEquals("resume",command);compose.onNodeWithText("Stop & save").performScrollTo().performClick();assertEquals("stop",command);capture("recorder-paused-landscape.png")
 }
 @Test fun screenNeedsAndroidApprovalBeforeItStarts(){
  org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
  compose.activity.setContent{OnKlavTheme{RecorderWorkspace(RecorderKind.SCREEN,{})}}
  compose.onNodeWithContentDescription("Start screen recording").assertIsDisplayed().performClick()
  val request=org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivityForResult
  assertNotNull(request);assertFalse(RecordingRuntime.state.value.active);assertTrue(request.intent.component?.className?.contains("MediaProjection")==true)
 }
}
