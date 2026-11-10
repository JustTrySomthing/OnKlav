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
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h800dp") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherLocationsTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @get:Rule val emptyKeyStore=EmptyKeyStoreRule()
 private fun pump(){compose.mainClock.advanceTimeBy(100);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))}
 private fun visible(text:String){val model=androidx.lifecycle.ViewModelProvider(compose.activity).get("sdk-app.onklav.weather",SdkAppModel::class.java);try{compose.waitUntil(15000){pump();runCatching{check(!model.busy&&model.pending==null);compose.onNodeWithText(text).assertIsDisplayed()}.isSuccess}}catch(e:Throwable){println("Waiting for: $text; busy=${model.busy}; pending=${model.pending}; error=${model.error}; frame=${model.frame}");println(compose.onAllNodes(isRoot()).printToString());throw e}}
 @Test(timeout=60000)fun zipDefaultSurvivesForecastFailureAndIsPinnedAboveSearch(){
  val c=compose.activity.application as Application;runBlocking{AppSdk.bootstrap(c)}
  fun call(j:JSONObject)=AppSdk.sync(c,j)as JSONObject
  fun launch()=call(JSONObject().put("action","launch").put("id","app.onklav.weather"))
  var opened=launch();var session=opened.getString("session")
  fun event(action:String,value:String="")=call(JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action",action).put("value",value)))
  fun reply(f:JSONObject,value:JSONObject)=call(JSONObject().put("action","reply").put("session",session).put("token",f.getJSONObject("pending").getString("token")).put("reply",JSONObject().put("ok",true).put("value",value)))
  assertTrue(opened.getJSONObject("frame").toString().contains("Choose your default location"));reply(opened,SdkClock.snapshot(c))
  event("weather.query","60601");val found=event("weather.search");assertNull(found.optJSONObject("pending"));val place=found.getJSONObject("frame").getJSONObject("state").getJSONArray("results").getJSONObject(0);assertEquals("us-zip-60601",place.getString("id"))
  val clock=event("weather.pick.us-zip-60601");val request=reply(clock,SdkClock.snapshot(c));call(JSONObject().put("action","reply").put("session",session).put("token",request.getJSONObject("pending").getString("token")).put("reply",JSONObject().put("ok",false).put("error","Network unavailable")))
  AppSdk.sync(c,JSONObject().put("action","close").put("session",session));opened=launch();session=opened.getString("session");assertEquals("us-zip-60601",opened.getJSONObject("frame").getJSONObject("state").getJSONObject("default_place").getString("id"))
  val forecast=reply(opened,SdkClock.snapshot(c));val bytes=File(System.getProperty("onklav.project.root"),"docs/fixtures/met-norway-forecast.json").readBytes();reply(forecast,SdkHttpPayload.result(200,bytes,JSONObject(),false));AppSdk.sync(c,JSONObject().put("action","close").put("session",session))
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("app.onklav.weather",exit={})}}};visible(place.getString("name"))
  compose.onNodeWithText("°F").assertDoesNotExist();compose.onNodeWithText("Weather: MET Norway · Cities: GeoNames").assertDoesNotExist()
  compose.onNodeWithContentDescription("Places").performClick();visible("Default location");visible("City or US ZIP code")
  val pin=compose.onNodeWithTag("sdk-weather.default.open").fetchSemanticsNode().boundsInRoot;val search=compose.onNodeWithTag("sdk-weather.query").fetchSemanticsNode().boundsInRoot;assertTrue(pin.bottom<=search.top)
  compose.onNodeWithContentDescription("Change default location").performClick();visible("Choose a new default");compose.onNodeWithTag("sdk-weather.query").performTextReplacement("94105");compose.onNodeWithTag("sdk-weather.search").performClick();visible("Set as default")
  compose.onNodeWithContentDescription("Weather settings").assertIsEnabled().performClick();visible("Temperature: Fahrenheit");compose.onNodeWithText("MET Norway · CC BY 4.0").assertDoesNotExist();compose.onNodeWithText("GeoNames · CC BY 4.0").assertDoesNotExist()
 }
}
