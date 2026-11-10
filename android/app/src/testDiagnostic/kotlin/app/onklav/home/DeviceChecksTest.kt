package app.onklav.home

import android.app.NotificationManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class DeviceChecksTest{
 @get:Rule val compose=createAndroidComposeRule<DeviceChecksActivity>()
 private val checks get()=ViewModelProvider(compose.activity)[DeviceChecks::class.java]
 @Test fun dashboardWaitsForUserAndReportHasNoPrivateContent(){
  compose.onNodeWithText("Start checks").assertIsDisplayed()
  assertFalse(checks.ready)
  assertFalse(java.io.File(compose.activity.filesDir,"app-sdk/registry.json").exists())
  val report=JSONObject(checks.report())
  assertTrue(report.getJSONArray("not_verified").length()>=4)
  assertFalse(report.toString().contains("private fixture"))
  assertEquals("Not tested",report.getJSONObject("results").getString("Camera"))
 }
 @Test fun signedSampleStartsAndNotificationUsesProductionNativeBridge(){
  val c=compose.activity
  val old=java.io.File(System.getProperty("onklav.project.root"),"developer/tests/fixtures/NativeServices-checks1.onklav-a")
  java.io.File(c.filesDir,"app-install.zip").writeBytes(old.readBytes())
  val previous=AppSdk.sync(c,JSONObject().put("action","inspect")) as JSONObject
  AppSdk.sync(c,JSONObject().put("action","install").put("hash",previous.getString("hash")).put("grants",org.json.JSONArray()))
  compose.onNodeWithText("Start checks").performClick()
  try{compose.waitUntil(15000){checks.ready&&!checks.busy}}catch(e:Exception){throw AssertionError(checks.report(),e)}
  assertTrue(checks.results.getValue("Native bridge").startsWith("Passed"))
  compose.onNodeWithText("Restart checks").performClick()
  compose.waitUntil(15000){checks.ready&&!checks.busy}
  assertTrue(checks.results.getValue("Native bridge").startsWith("Passed"))
  fun call(j:JSONObject)=AppSdk.sync(c,j)as JSONObject
  val session=call(JSONObject().put("action","launch").put("id","org.example.nativebridge.checks2")).getString("session")
  fun result(action:String,value:JSONObject,expected:String,ok:Boolean=true){
   val pending=call(JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action",action))).getJSONObject("pending")
   call(JSONObject().put("action","authorize").put("session",session).put("token",pending.getString("token")))
   val reply=JSONObject().put("ok",ok);if(ok)reply.put("value",value)else reply.put("error","private fixture")
   val frame=call(JSONObject().put("action","reply").put("session",session).put("token",pending.getString("token")).put("reply",reply)).getJSONObject("frame").toString()
   assertTrue(frame,frame.contains(expected));assertFalse(frame.contains("private fixture"))
  }
  result("secret",JSONObject().put("value",JSONObject.NULL),"No test secret saved yet.")
  result("store",JSONObject().put("stored",true),"Test secret saved. Tap Read secret next.")
  result("secret",JSONObject().put("value","private fixture"),"Secure storage preview passed.")
  result("store",JSONObject(),"Test failed.",false)
  AppSdk.sync(c,JSONObject().put("action","close").put("session",session))
  checks.feedback(JSONObject().put("phase","opened").put("service",JSONObject.NULL))
  assertEquals("opened",checks.results["Preview service feedback"])
  assertFalse(checks.report().contains("private fixture"))
  shadowOf(compose.activity.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
  compose.runOnIdle{checks.notice()}
  compose.waitUntil(10000){!checks.busy}
  assertTrue(checks.noticeSent)
  assertEquals("Not confirmed",checks.results["Notification visibility"])
  assertEquals(1,compose.activity.getSystemService(NotificationManager::class.java).activeNotifications.size)
 }
}
