package app.onklav.home

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h800dp") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppPolishUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun capture(name:String){compose.runOnIdle{val view=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(b));val f=File(System.getProperty("onklav.project.root"),"docs/evidence/v0.90.0/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Test(timeout=60000)fun arrowsPageBothWaysAndKeepSwipeNavigation(){
  val c=compose.activity;runBlocking{AppSdk.bootstrap(c)};c.getSharedPreferences("apps_desktop",0).edit().clear().commit()
  compose.setContent{OnKlavTheme{AppsDesktop{}}};compose.waitForIdle()
  compose.onNodeWithTag("apps-previous-page").assertIsNotEnabled();compose.onNodeWithTag("apps-next-page").assertIsEnabled();capture("apps-page-arrows")
  compose.onNodeWithTag("apps-next-page").performClick();compose.waitForIdle();compose.onNodeWithTag("apps-previous-page").assertIsEnabled();capture("apps-second-page")
  compose.onNodeWithTag("apps-previous-page").performClick();compose.waitForIdle();compose.onNodeWithTag("apps-previous-page").assertIsNotEnabled()
  compose.onNodeWithTag("apps-grid-viewport").performTouchInput{swipeLeft()};compose.waitForIdle();compose.onNodeWithTag("apps-previous-page").assertIsEnabled()
 }
 private class Voice:SpeechBackend{
  var range:((Int)->Unit)?=null;var done:((String?)->Unit)?=null
  override fun initialize(done:(Boolean,List<SpeechVoice>)->Unit){done(true,listOf(SpeechVoice("local","Local")))}
  override fun speak(text:String,voice:String,rate:Float,token:String,range:(Int)->Unit,done:(String?)->Unit):Boolean{this.range=range;this.done=done;return true}
  override fun stop(){};override fun close(){}
 }
 @Test(timeout=60000)fun speechRangeFollowsLongEditableTextWithoutChangingWords(){
  val c=compose.activity;shadowOf(c.getSystemService(Context.AUDIO_SERVICE)as AudioManager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
  val voice=Voice();val speech=SdkSpeechController(c,"test.polish",{},voice);val model=SdkAppModel(c.application as Application)
  val text=(1..100).joinToString("\n"){"Line $it has words to read."};val node=JSONObject().put("kind","editor").put("id","text").put("value",text).put("limit",50000).put("speech_highlight",true)
  compose.setContent{OnKlavTheme{Column(Modifier.fillMaxSize()){SdkWidget(node,"test",model,speech,model.webPool,Modifier.weight(1f))}}};compose.waitForIdle()
  compose.runOnIdle{speech.play(text,"play");voice.done!!(null);voice.range!!(800)};compose.waitForIdle()
  compose.onNodeWithTag("sdk-text").assertTextContains(text)
  val scroll=compose.onNodeWithTag("sdk-text").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
  capture("reader-word-highlight");println(compose.onNodeWithTag("sdk-text").printToString());assertTrue("Speech must reveal the active word: value=${scroll.value()} max=${scroll.maxValue()}",scroll.value()>0)
  compose.runOnIdle{speech.close()}
 }
}
