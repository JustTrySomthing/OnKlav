package app.onklav.home
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkDevicePreviewTest{
 @get:Rule val compose=createAndroidComposeRule<SdkDevPreviewActivity>()
 @Test fun invalidOrMissingTicketsFailBeforeAnyAppInstall(){
  compose.waitUntil(10000){File(compose.activity.filesDir,"sdk-dev/feedback.json").exists()}
  val result=JSONObject(File(compose.activity.filesDir,"sdk-dev/feedback.json").readText());assertEquals("error",result.getString("phase"));assertFalse(File(compose.activity.filesDir,"app-sdk/registry.json").exists())
 }
 private fun pump(){repeat(3){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(16))}}
 @Test(timeout=60000)fun signedPackageCanBeReviewedOpenedAndInteractedWithThroughTheRealHost(){
  val directory=File(compose.activity.filesDir,"sdk-dev").apply{mkdirs()};val ticket="a".repeat(32)
  File(directory,"package.onklav-a").writeBytes(File(System.getProperty("onklav.project.root"),"sdk/packages/Counter.onklav-a").readBytes())
  File(directory,"request.json").writeText(JSONObject().put("ticket",ticket).put("created",System.currentTimeMillis()).put("id","developer.example.counter").toString())
  compose.activity.intent.putExtra("ticket",ticket);compose.activityRule.scenario.recreate()
  compose.waitUntil(15000){pump();runCatching{compose.onNodeWithText("Install and open").assertIsDisplayed()}.isSuccess}
  compose.onNodeWithText("Install and open").performScrollTo().assertIsDisplayed().performClick()
  compose.waitUntil(15000){pump();runCatching{compose.onNodeWithText("Add One").assertIsDisplayed()}.isSuccess}
  compose.onNodeWithText("Add One").performClick()
  compose.waitUntil(10000){pump();runCatching{compose.onNodeWithTag("sdk-count").assertTextEquals("1")}.isSuccess}
  assertFalse(File(directory,"request.json").exists());assertFalse(File(directory,"package.onklav-a").exists())
 }

}
