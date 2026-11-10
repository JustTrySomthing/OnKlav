package app.onklav.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.time.Duration
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class RadioSceneRenderTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(3){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));compose.mainClock.advanceTimeByFrame()}}
 private var captureName:String?=null
 private fun pixels():IntArray{var out=IntArray(0);compose.runOnUiThread{
  val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));out=IntArray((b.width/4)*(b.height/4)){i->b.getPixel((i%(b.width/4))*4,(i/(b.width/4))*4)};captureName?.let{name->val f=java.io.File(System.getProperty("onklav.project.root"),"docs/evidence/radio-geometry-polish/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};captureName=null};b.recycle()
 };return out}
 @Test(timeout=90000)fun featuredScenesMoveWithNewAudioAndIntensityChangesTheirOutput(){
  val mode=mutableIntStateOf(3);val strength=mutableFloatStateOf(1.25f)
  val base=ByteArray(512){(128+76*kotlin.math.sin(it*.06)).toInt().toByte()};val sound=mutableStateOf(base.copyOf());val bands=FloatArray(32){.2f+.65f*kotlin.math.abs(kotlin.math.sin(it*.3f))}
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{Surface(color=Color(0xFF070C18)){RadioVisualCanvas(sound.value,bands,mode.intValue,2,Modifier.fillMaxSize(),intensity=strength.floatValue)}}}}
  compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
  for(scene in 3..5){compose.runOnUiThread{mode.intValue=scene;strength.floatValue=1.25f};pump();val before=pixels()
   repeat(12){compose.runOnUiThread{sound.value=base.copyOf()};pump()};val after=pixels();assertTrue("Effect $scene must move visibly",before.indices.count{before[it]!=after[it]}>100)
   compose.runOnUiThread{strength.floatValue=.25f};pump();val gentle=pixels();assertTrue("Effect $scene intensity must control output",after.indices.count{after[it]!=gentle[it]}>100)
  }
 }
 @Test(timeout=90000)fun radialScenesExpandPastFormerBoundsAndAllLayersReact(){
  val mode=mutableIntStateOf(2);val strength=mutableFloatStateOf(.25f)
  val samples=ByteArray(512){(128+76*kotlin.math.sin(it*.06)).toInt().toByte()};val bands=FloatArray(32){.3f+.6f*kotlin.math.abs(kotlin.math.sin(it*.3f))}
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{Surface(color=Color(0xFF070C18)){RadioVisualCanvas(samples,bands,mode.intValue,2,Modifier.fillMaxSize(),intensity=strength.floatValue)}}}}
  compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
  for(scene in listOf(2,3)){
   compose.runOnUiThread{mode.intValue=scene;strength.floatValue=.25f};pump();val low=pixels()
   compose.runOnUiThread{strength.floatValue=2.5f};pump();captureName="expanded-scene-$scene";val high=pixels()
   var outside=0;compose.runOnUiThread{val v=compose.activity.window.decorView;val width=v.width/4;val height=v.height/4;for(i in high.indices){val x=(i%width-width/2).toFloat();val y=(i/width-height/2).toFloat();if(kotlin.math.hypot(x,y)>minOf(width,height)*.49f&&high[i]!=low[i])outside++}}
   assertTrue("Scene $scene must reach outside its former circle, changed=$outside",outside>100)
  }
 }
 @Test fun removedPrismFallsBackAndMandalaLayersUseSeparateFrequencyEnvelopes(){
  assertEquals(6,radioVisualNames.size);assertFalse(radioVisualNames.any{it.contains("Prism")});assertEquals(1,radioVisualMode(6))
  for(layer in 0..3){val bands=FloatArray(32);val starts=intArrayOf(0,6,14,23);val ends=intArrayOf(5,13,22,31);for(i in starts[layer]..ends[layer])bands[i]=.9f
   for(other in 0..3)if(other==layer)assertTrue(radioMandalaPulse(bands,other)>.7f)else assertEquals(0f,radioMandalaPulse(bands,other),0f)
  }
 }
 @Test fun starburstIsSymmetricAndTapersOutwardAndColorsStaySelected(){
  val steady=FloatArray(32){.8f}
  for(i in 0..100){val t=i/100f;assertEquals(radioStarburstHeight(steady,t,1f),radioStarburstHeight(steady,1-t,1f),.0001f)}
  assertTrue(radioStarburstHeight(steady,.5f,1f)>radioStarburstHeight(steady,0f,1f)*2.8f)
  for(palette in 0..5)for(time in listOf(0.0,12.0,10000.0))for(offset in 0..360 step 30){val difference=abs(radioSceneHue(palette,offset.toFloat(),time)-radioVisualBaseHue(palette));assertTrue(minOf(difference,360-difference)<=23.01f)}
  for(layer in 0..3){val turn=radioMandalaTurn(2.0,layer);assertTrue(abs(turn)>.2f&&abs(turn)<1.3f);assertEquals(if(layer%2==0)1f else -1f,kotlin.math.sign(turn),0f)
   val low=(0..256).map{radioMandalaRadius(400f,layer,it/256f*2*kotlin.math.PI.toFloat(),.1f)}
   val high=(0..256).map{radioMandalaRadius(400f,layer,it/256f*2*kotlin.math.PI.toFloat(),.9f)}
   assertTrue(high.max()-high.min()>(low.max()-low.min())*4)
  }
 }
 @Test fun rainbowCoversAllHuesAndOneBandDoesNotDriveAllBars(){
  for(offset in -720..720 step 30)assertTrue(radioSceneHue(6,offset.toFloat(),10.0) in 0f..360f)
  assertTrue(abs(radioSceneHue(6,0f)-radioSceneHue(6,180f))>150f)
  val a=FloatArray(32);a[10]=1f
  assertTrue(radioStarburstHeight(a,(1+10f/31)/2,1f)>radioStarburstHeight(a,.5f,1f)*5)
  assertTrue(radioVisualNames.indices.map(::radioDefaultIntensity).distinct().size>=5)
 }
 @Test fun frequencyLayersAreIndependentAndWaveComponentsKeepActualSound(){
  assertTrue(radioVisualExpansion(2.5f)>radioVisualExpansion(.25f)*3)
  val bass=FloatArray(32){if(it<10)1f else 0f}
  assertTrue(radioLayerBand(bass,0,.4f)>.9f);assertEquals(0f,radioLayerBand(bass,2,.4f),0f)
  val trace=FloatArray(144){kotlin.math.sin(it*.04f)+.4f*kotlin.math.sin(it*.6f)};val layers=radioWaveLayers(trace)
  assertEquals(3,layers.size);assertFalse(layers[0].contentEquals(layers[1]));assertFalse(layers[1].contentEquals(layers[2]));assertTrue(radioWaveLayers(FloatArray(144)).all{it.all{v->v==0f}})
 }
 @Test(timeout=90000)fun allSixScenesReactToAudioAndPausedFramesRemainStill(){
  val mode=mutableIntStateOf(0);val active=mutableStateOf(true)
  val samples=ByteArray(512){(128+76*kotlin.math.sin(it*.06)).toInt().toByte()}
  val bands=FloatArray(32){.2f+.65f*kotlin.math.abs(kotlin.math.sin(it*.3f))}
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{Surface(color=Color(0xFF070C18)){RadioVisualCanvas(samples,bands,mode.intValue,2,Modifier.fillMaxSize(),active=active.value)}}}}
  compose.waitForIdle();compose.mainClock.autoAdvance=false;pump()
  val signatures=mutableSetOf<Int>()
  for(i in radioVisualNames.indices){
   compose.runOnUiThread{mode.intValue=i;active.value=true};pump();captureName="radio-scene-$i";val sound=pixels();assertTrue(sound.toSet().size>80);signatures.add(sound.contentHashCode())
   compose.runOnUiThread{active.value=false};pump();val quiet=pixels();assertFalse("Scene $i must respond to audio",sound.contentEquals(quiet));pump();assertArrayEquals("Paused scene $i must not animate",quiet,pixels())
  }
  assertEquals("Distinct effect artwork",6,signatures.size)
 }
}
