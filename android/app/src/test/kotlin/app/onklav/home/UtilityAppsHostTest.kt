package app.onklav.home

import android.app.Application
import android.content.Context
import android.content.ClipboardManager
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w411dp-h891dp") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class UtilityAppsHostTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @get:Rule val emptyKeyStore=EmptyKeyStoreRule()
 private fun pump(){repeat(2){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));compose.mainClock.advanceTimeBy(100)}}
 private fun await(test:()->Boolean){compose.waitUntil(15000){pump();test()}}
 private fun open(name:String){compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.$name",exit={})}}};await{runCatching{compose.onNodeWithTag(if(name=="calc")"sdk-calc.key.2"else if(name=="convert")"sdk-convert.left"else"sdk-weather-display").assertIsDisplayed()}.isSuccess};await{runCatching{compose.onNodeWithTag("sdk-app-loading").assertDoesNotExist()}.isSuccess}}
 private fun ready(){await{runCatching{compose.onNodeWithTag("sdk-app-loading").assertDoesNotExist()}.isSuccess}}
 private fun desc(label:String){await{runCatching{compose.onNodeWithContentDescription(label).assertIsEnabled()}.isSuccess};compose.onNodeWithContentDescription(label).performClick();pump()}
 private fun text(label:String){await{runCatching{compose.onNodeWithText(label).assertIsEnabled()}.isSuccess};compose.onNodeWithText(label).performClick();pump()}
 private fun tap(id:String){compose.onNodeWithTag("sdk-$id").performClick();pump()}
 private fun picture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));File("/tmp/onklav-$name-83.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}}
 @Test(timeout=60000)fun calculatorKeypadCopyAndHistory(){
  open("calc");for(id in listOf("calc.key.2","calc.key.+","calc.key.3","calc.key.×","calc.key.4","calc.equals"))tap(id)
  try{await{runCatching{compose.onNodeWithTag("sdk-calc.output").assertTextEquals("14")}.isSuccess}}catch(e:Throwable){picture("calc-failed");println(compose.onRoot().printToString());throw e}
  ready();picture("calc");compose.onNodeWithTag("sdk-calc.output").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick){it()};val c=compose.activity.application
  try{await{(c.getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString()=="14"}}catch(e:Throwable){println(compose.onRoot().printToString());throw e}
  ready();picture("calc")
  desc("Calculation history");await{runCatching{compose.onNodeWithText("History").assertIsDisplayed()}.isSuccess};compose.onNodeWithTag("sdk-calc.pick.0").assertIsDisplayed()
  compose.mainClock.advanceTimeBy(500);pump();compose.onNodeWithTag("sdk-calc.pick.0").performClick();await{runCatching{compose.onNodeWithTag("sdk-app-drawer").assertDoesNotExist()}.isSuccess};ready();tap("calc.clear");await{runCatching{compose.onNodeWithTag("sdk-calc.output").assertTextEquals("0")}.isSuccess}
  for(id in listOf("calc.key.8","calc.key.+","calc.key.1","calc.equals"))tap(id)
  try{await{runCatching{compose.onNodeWithTag("sdk-calc.output").assertTextEquals("9")}.isSuccess}}catch(e:Throwable){picture("calc-second-failed");println(compose.onRoot().printToString());throw e};tap("calc.previous")
  await{runCatching{compose.onNodeWithTag("sdk-calc.output").assertTextEquals("14")}.isSuccess};tap("calc.next")
  await{runCatching{compose.onNodeWithTag("sdk-calc.output").assertTextEquals("9")}.isSuccess}
 }
 @Test(timeout=60000)fun converterEditsEitherSideAndUsesTheme(){
  open("convert");compose.onNodeWithTag("sdk-convert.left").performTextReplacement("12")
  await{runCatching{compose.onNodeWithTag("sdk-convert.right").assertTextContains("3.6576")}.isSuccess};picture("convert")
  compose.onNodeWithTag("sdk-convert.right").performTextReplacement("1")
  await{runCatching{compose.onNodeWithTag("sdk-convert.left").assertTextContains("3.280839895")}.isSuccess}
 }
 @Test(timeout=60000)fun cachedRealWeatherRendersAndCitySearchNeedsNoNetwork(){
  val c=compose.activity.application as Application;runBlocking{AppSdk.bootstrap(c)}
  fun call(j:JSONObject)=AppSdk.sync(c,j)as JSONObject
  val start=call(JSONObject().put("action","launch").put("id","app.onklav.weather"));val s=start.getString("session")
  fun event(action:String,value:String="")=call(JSONObject().put("action","event").put("session",s).put("event",JSONObject().put("action",action).put("value",value)))
  fun reply(frame:JSONObject,value:JSONObject)=call(JSONObject().put("action","reply").put("session",s).put("token",frame.getJSONObject("pending").getString("token")).put("reply",JSONObject().put("ok",true).put("value",value)))
  if(start.has("pending"))reply(start,SdkClock.snapshot(c))
  event("weather.query","Chicago");val results=event("weather.search");assertTrue(results.optJSONObject("pending")==null);val id=results.getJSONObject("frame").getJSONObject("state").getJSONArray("results").getJSONObject(0).getString("id")
  val pending=reply(event("weather.pick.$id"),SdkClock.snapshot(c));assertEquals("http.request",pending.getJSONObject("pending").getString("service"))
  val bytes=File(System.getProperty("onklav.project.root"),"docs/fixtures/met-norway-forecast.json").readBytes();reply(pending,SdkHttpPayload.result(200,bytes,JSONObject(),false))
  AppSdk.sync(c,JSONObject().put("action","close").put("session",s));open("weather");compose.onNodeWithText("7-day outlook").assertExists();compose.onNodeWithText("Next hours").assertIsDisplayed();picture("weather")
  desc("Weather settings");await{runCatching{compose.onNodeWithText("Temperature: Fahrenheit").assertIsDisplayed()}.isSuccess};text("Temperature: Fahrenheit");await{runCatching{compose.onNodeWithText("Temperature: Celsius").assertIsDisplayed()}.isSuccess};text("Done");pump()
  desc("Places");await{runCatching{compose.onNodeWithTag("sdk-weather.query").assertIsDisplayed()}.isSuccess};compose.onNodeWithTag("sdk-weather.query").performTextReplacement("London GB");tap("weather.search");await{runCatching{compose.onAllNodesWithText("London",substring=true).fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
 }
}
