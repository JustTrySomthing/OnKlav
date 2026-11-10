package app.onklav.home

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.runtime.mutableStateOf
import app.onklav.shared.AppearanceSpec
import org.json.JSONObject
import org.json.JSONArray
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

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h800dp") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class SdkRadioPolishTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @get:Rule val emptyKeyStore=EmptyKeyStoreRule()
 private fun pump(){repeat(2){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))}}
 private fun screenshot(name:String){compose.runOnUiThread{val view=compose.activity.window.decorView;val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(bitmap));java.io.File("/tmp/onklav-radio-$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}}
 private fun visible(text:String){val model=androidx.lifecycle.ViewModelProvider(compose.activity).get("sdk-app.onklav.radio",SdkAppModel::class.java);compose.waitUntil(15000){pump();runCatching{check(!model.busy&&model.pending==null);compose.onNodeWithText(text).assertIsDisplayed()}.isSuccess}}
 @Test(timeout=60000)fun signedRadioHasOneTabRowCompactFiltersCleanListsAndSettings(){
  val app=compose.activity.application as Application;runBlocking{AppSdk.bootstrap(app)}
  fun call(q:JSONObject)=AppSdk.sync(app,q) as JSONObject
  val opened=call(JSONObject().put("action","launch").put("id","app.onklav.radio"));val session=opened.getString("session")
  fun event(action:String)=call(JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action",action)))
  val pending=opened.getJSONObject("pending")
  val station="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
  call(JSONObject().put("action","reply").put("session",session).put("token",pending.getString("token")).put("reply",JSONObject().put("ok",true).put("value",JSONObject().put("items",JSONArray().put(JSONObject().put("id",station).put("name","Saved Jazz").put("countrycode","US").put("codec","MP3").put("bitrate",128))).put("next",JSONObject.NULL))))
  event("radio.star.$station");AppSdk.sync(app,JSONObject().put("action","close").put("session",session))
  val dark=mutableStateOf(true)
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme(preview=AppearanceSpec(dark=dark.value)){SdkAppScreen("app.onklav.radio",exit={})}}}
  visible("Stations")
  val tabs=listOf("radio.explore","radio.listen","radio.library").map{compose.onNodeWithTag("sdk-$it").fetchSemanticsNode().boundsInRoot}
  assertEquals(tabs[0].top,tabs[1].top,1f);assertEquals(tabs[0].top,tabs[2].top,1f);assertTrue(tabs[0].right<=tabs[1].left&&tabs[1].right<=tabs[2].left)
  val country=compose.onNodeWithTag("sdk-radio.country").fetchSemanticsNode().boundsInRoot
  val language=compose.onNodeWithTag("sdk-radio.language").fetchSemanticsNode().boundsInRoot
  assertEquals(country.top,language.top,1f);assertTrue(country.right<=language.left);assertTrue("Country selection needs readable width",country.width>100f);assertTrue("Language selection needs readable width",language.width>100f)
  compose.onNodeWithTag("sdk-radio.country").performClick();visible("United States");compose.onNodeWithText("United States").performClick();visible("United States ▾");screenshot("stations")
  compose.onNodeWithText("Recent").performClick();visible("No recent stations");compose.onNodeWithText("Find your sound").assertDoesNotExist();compose.onNodeWithTag("sdk-radio.genre").assertDoesNotExist()
  compose.onNodeWithText("Favorites").performClick();visible("Your Favorite stations");compose.onNodeWithText("First favorites / Reload").assertDoesNotExist()
  visible("Saved Jazz");compose.onNodeWithContentDescription("Remove favorite").performClick();compose.waitUntil(10000){pump();runCatching{compose.onNodeWithText("Saved Jazz").assertDoesNotExist()}.isSuccess}
  compose.runOnUiThread{dark.value=false};pump();compose.onNodeWithText("Your Favorite stations").assertIsDisplayed();screenshot("favorites-light")
  compose.onNodeWithContentDescription("Radio settings").performClick();visible("Audio waveform");compose.onNodeWithText("Reset station filters").assertIsDisplayed();compose.onNodeWithText("Auto-hide failed stations").assertIsDisplayed();compose.onNodeWithText("Show hidden stations").assertIsDisplayed();compose.onNodeWithText("Unhide all stations").assertIsDisplayed();screenshot("settings-controls")
 }
}
