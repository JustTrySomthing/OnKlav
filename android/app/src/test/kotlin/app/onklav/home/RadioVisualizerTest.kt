package app.onklav.home

import android.content.res.Configuration
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class RadioVisualizerTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private var visualDialog:android.app.Dialog?=null
 private fun pump(){repeat(3){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));compose.mainClock.advanceTimeByFrame()}}
 @Test fun packedFftIgnoresDcAndNyquistAndMapsComplexToneToLogFrequency(){
  val fft=ByteArray(1024);fft[0]=127;fft[1]=127
  assertTrue(radioSpectrum(fft,48_000_000).all{it==0f})
  fft[2*32]=70;fft[2*32+1]=70 // 1500 Hz complex tone.
  val bands=radioSpectrum(fft,48_000_000)
  assertTrue(bands.all{it.isFinite()&&it in 0f..1f})
  val peak=bands.indices.maxBy{bands[it]}
  assertTrue("1500 Hz belongs near band19, got $peak",peak in 18..21)
  assertTrue(bands[peak]>.85f);assertTrue(bands.take(10).all{it==0f})
  assertTrue(radioSpectrum(byteArrayOf(),0).all{it==0f})
  assertTrue(radioSpectrum(fft,20_000).all{it==0f})
 }
 @android.annotation.SuppressLint("MissingPermission") // Invalid session is rejected before Android capture is constructed.
 @Test fun captureRejectsGlobalSessionAndCloseClearsWaveform(){
  var cleared=false
  val capture=RadioWaveform(emit={cleared=it.isEmpty()},emitFft={_,_->})
  try{capture.start(0);fail("Global device mix must never be opened")}catch(_:IllegalArgumentException){}
  capture.close();assertTrue(cleared)
 }
 @Test(timeout=90000)fun visualizerModesLandscapeBackAndOrientationRestore(){
  compose.activity.getSharedPreferences("radio-player",0).edit().clear().commit()
  compose.activity.getSharedPreferences("radio-player",0).edit().putInt("visual_color",3).commit()
  val wide=mutableStateOf(false);val open=mutableStateOf(true)
  val previous=compose.activity.requestedOrientation
  val fft=ByteArray(1024);for(i in 1 until 512){fft[2*i]=(30+20*kotlin.math.sin(i*.15)).toInt().toByte()}
  val wave=ByteArray(512){i->(128+76*kotlin.math.sin(i*.06)).toInt().toByte()}
  compose.runOnUiThread{compose.activity.setContent{
   val config=Configuration(LocalConfiguration.current).apply{orientation=if(wide.value)Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT}
   CompositionLocalProvider(LocalConfiguration provides config){OnKlavTheme{
    if(open.value)RadioVisualizerDialog("Aurora Radio",true,true,wave,radioSpectrum(fft,48_000_000),null,false,{},{},{open.value=false},true,{})
   }}
  }}
  compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
  visualDialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
  compose.onNodeWithTag("radio-visualizer-portrait").assertExists()
  assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,compose.activity.requestedOrientation)
  compose.onNodeWithContentDescription("Visualizer intensity").assertIsDisplayed().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(2f)};pump()
  compose.onNodeWithText("200%").assertExists();assertEquals(2f,compose.activity.getSharedPreferences("radio-player",0).getFloat("visual_intensity",0f),.001f)
  compose.onNodeWithContentDescription("Choose visualizer color").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Rainbow"))
  chooseEffect(0);screenshot("wave-default");compose.onNodeWithText("105%").assertExists();compose.onNodeWithContentDescription("Audio waveform").assertExists()
  chooseEffect(2);compose.onNodeWithContentDescription("Circular audio spectrum").assertExists()
  compose.onNodeWithContentDescription("Choose visualizer color").performClick();pump()
  for(name in radioColorNames)compose.onNodeWithContentDescription("$name visualizer color").assertIsDisplayed()
  compose.onNodeWithContentDescription("Orange visualizer color").performClick();pump()
  assertEquals(3,compose.activity.getSharedPreferences("radio-player",0).getInt("visual_color_v2",-1))
  for(i in radioVisualNames.indices){chooseEffect(i);compose.onNodeWithContentDescription(radioSceneDescriptions[i]).assertIsDisplayed();screenshot("portrait-$i")}
  chooseEffect(2)
  screenshot("portrait")
  compose.runOnUiThread{wide.value=true};pump()
  compose.onNodeWithTag("radio-visualizer-landscape").assertExists()
  compose.onNodeWithText("Rotate for full screen").assertDoesNotExist()
  compose.onNodeWithContentDescription("Close music visualizer").performClick();pump()
  compose.onNodeWithTag("radio-visualizer-landscape").assertDoesNotExist()
  assertEquals(previous,compose.activity.requestedOrientation)
 }
 @Test(timeout=90000) @Config(qualifiers="w936dp-h432dp-land-mdpi")
 fun actualLandscapeFillsWindowControlsHideRevealAndBackRestores(){
  val open=mutableStateOf(true);val previous=compose.activity.requestedOrientation
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{
   if(open.value)RadioVisualizerDialog("Night Drive • Live",true,true,ByteArray(512){(128+76*kotlin.math.sin(it*.06)).toInt().toByte()},FloatArray(32){i->.15f+.75f*kotlin.math.abs(kotlin.math.sin(i*.3f))},null,false,{},{},{open.value=false},true,{})
  }}}
  compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
  visualDialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
  val bounds=compose.onNodeWithTag("radio-visualizer-landscape").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
  assertTrue("Real wide layout required: $bounds",bounds.width>bounds.height)
  chooseEffect(1)
  val scene=compose.onNodeWithTag("radio-visual-scene").fetchSemanticsNode().boundsInRoot
  val footer=compose.onNodeWithContentDescription("Choose visualizer effect").fetchSemanticsNode().boundsInRoot
  assertTrue("Art must fit above controls, $scene $footer",scene.bottom<=footer.top)
  for(i in radioVisualNames.indices){chooseEffect(i);compose.onNodeWithContentDescription(radioSceneDescriptions[i]).assertIsDisplayed();screenshot("landscape-$i")}
  chooseEffect(1)
  compose.onNodeWithContentDescription("Pause station").assertIsDisplayed()
  screenshot("landscape")
  compose.mainClock.advanceTimeBy(5200);pump()
  compose.onNodeWithContentDescription("Close music visualizer").assertDoesNotExist()
  compose.onNodeWithContentDescription("Audio frequency spectrum").performClick();pump()
  compose.onNodeWithContentDescription("Close music visualizer").assertIsDisplayed()
  compose.runOnUiThread{visualDialog!!.onBackPressed()};pump()
  assertFalse(open.value);assertEquals(previous,compose.activity.requestedOrientation)
 }
 @Test(timeout=60000) @Config(qualifiers="w320dp-h640dp-mdpi")
 fun narrowPortraitReservesFullTransportTouchTarget(){
  var clicks=0
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{
   RadioVisualizerDialog("A station with a long title",true,true,byteArrayOf(),FloatArray(32),null,false,{},{},{},true,{clicks++})
  }}}
  compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
  val pause=compose.onNodeWithContentDescription("Pause station").assertIsDisplayed().assertIsEnabled()
  val bounds=pause.fetchSemanticsNode().boundsInRoot
  assertTrue("Pause needs a 48dp touch target: $bounds",bounds.width>=48&&bounds.height>=48)
  val picker=compose.onNodeWithContentDescription("Choose visualizer effect").fetchSemanticsNode().boundsInRoot
  assertTrue(picker.right<=bounds.left);pause.performClick();pump();assertEquals(1,clicks)
 }
 private fun chooseEffect(index:Int){
  compose.onNodeWithContentDescription("Choose visualizer effect").performClick();pump()
  compose.onNodeWithTag("radio-effect-wheel").performScrollToIndex(index);pump()
  compose.onNode(hasText(radioVisualNames[index],substring=false) and hasAnyAncestor(hasTestTag("radio-effect-wheel"))).performClick();pump()
  assertEquals(index,compose.activity.getSharedPreferences("radio-player",0).getInt("visual_mode",-1))
 }
 @Test fun portraitWindowAndUnitBasedOrbitRemainReadableAtEitherAspect(){
  val wave=ByteArray(1024){(128+70*kotlin.math.sin(it*.09)).toInt().toByte()}
  val portrait=radioWaveTrace(wave,false);val wide=radioWaveTrace(wave,true)
  assertEquals(144,portrait.size);assertEquals(512,wide.size)
  assertTrue(portrait.maxOf{kotlin.math.abs(it)}>.8f)
  assertTrue(radioWaveTrace(ByteArray(1024){128.toByte()},false).all{it==0f})
  assertTrue(radioWaveTrace(byteArrayOf(),false).all{it==0f})
  for((w,h)in listOf(400f to 630f,936f to 290f)){
   val stage=RadioStage(w,h);assertEquals(.5f,stage.spectrumBottom/h,.001f)
   assertEquals(.18f,stage.orbitReach/minOf(w,h),.001f)
   assertTrue(stage.orbitRadius+stage.orbitReach<minOf(w,h)/2)
  }
  assertEquals(0f,radioVisualLevel(Float.NaN),0f);assertEquals(0f,radioVisualLevel(0f),0f)
  assertTrue(radioVisualLevel(.16f)>.35f)
 }
 private fun screenshot(name:String){compose.runOnUiThread{
  val view=visualDialog!!.window!!.decorView
  val image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
  view.draw(android.graphics.Canvas(image));val inks=mutableSetOf<Int>();for(y in 0 until image.height step 4)for(x in 0 until image.width step 4)inks.add(image.getPixel(x,y));assertTrue("Screenshot must contain native artwork",inks.size>80);File("/tmp/radio-visualizer-$name.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
 }}
}
