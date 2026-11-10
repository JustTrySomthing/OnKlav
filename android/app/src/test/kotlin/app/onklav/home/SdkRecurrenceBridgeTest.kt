package app.onklav.home

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkRecurrenceBridgeTest{
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private fun call(j:JSONObject)=AppSdk.sync(c,j) as JSONObject
 private fun start(granted:Boolean):JSONObject{
  File(c.filesDir,"app-install.zip").writeBytes(File(System.getProperty("onklav.project.root"),"docs/fixtures/recurrence-wip.onklav-a").readBytes());val review=call(JSONObject().put("action","inspect"));call(JSONObject().put("action","install").put("hash",review.getString("hash")).put("grants",JSONArray(if(granted)listOf("notifications.post")else emptyList<String>())));return call(JSONObject().put("action","launch").put("id","org.example.recurrencefixture"))
 }
 private fun authorize(s:String,p:JSONObject)=call(JSONObject().put("action","authorize").put("session",s).put("token",p.getString("token")))
 private fun reply(s:String,p:JSONObject,v:JSONObject)=call(JSONObject().put("action","reply").put("session",s).put("token",p.getString("token")).put("reply",JSONObject().put("ok",true).put("value",v)))
 private fun event(s:String,a:String)=call(JSONObject().put("action","event").put("session",s).put("event",JSONObject().put("action",a)))
 private fun expand(start:JSONObject):JSONObject{val s=start.getString("session");val p=start.getJSONObject("pending");assertEquals("calendar.expand",p.getString("service"));val expanded=reply(s,p,SdkRecurrence.expand(authorize(s,p).getJSONObject("args")));val clock=expanded.getJSONObject("pending");assertEquals("clock.read",clock.getString("service"));authorize(s,clock);return reply(s,clock,SdkClock.snapshot(c))}
 @Test fun signedGuestUsesPermissionFreeTimezoneExpansionAndAbsoluteReminderStatus(){
  shadowOf(c.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
  val start=start(true);val s=start.getString("session");val done=expand(start);assertEquals(3,done.getJSONObject("frame").getJSONObject("state").getJSONObject("result").getJSONObject("value").getJSONArray("occurrences").length())
  var p=event(s,"at").getJSONObject("pending");assertEquals("notifications.schedule_at",p.getString("service"));val scheduled=SdkNotifications.execute(c,authorize(s,p)){authorize(s,p)};reply(s,p,scheduled);assertFalse(scheduled.getBoolean("exact"))
  p=event(s,"status").getJSONObject("pending");val status=SdkNotifications.execute(c,authorize(s,p)){authorize(s,p)};assertTrue(status.getBoolean("pending"));reply(s,p,status)
  p=event(s,"pending").getJSONObject("pending");val pending=SdkNotifications.execute(c,authorize(s,p)){authorize(s,p)};assertEquals(1,pending.getJSONArray("items").length());reply(s,p,pending)
  p=event(s,"cancel").getJSONObject("pending");reply(s,p,SdkNotifications.execute(c,authorize(s,p)){authorize(s,p)})
  p=event(s,"status").getJSONObject("pending");val gone=SdkNotifications.execute(c,authorize(s,p)){authorize(s,p)};assertFalse(gone.getBoolean("pending"));reply(s,p,gone)
 }
 @Test fun recurrenceNeedsNoGrantButRevokedReminderQueryCannotBeAuthorized(){
  val start=start(false);val s=start.getString("session");assertTrue(expand(start).getJSONObject("frame").getJSONObject("state").getJSONObject("result").getBoolean("ok"))
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id","org.example.recurrencefixture").put("grants",JSONArray(listOf("notifications.post"))))
  val relaunched=call(JSONObject().put("action","launch").put("id","org.example.recurrencefixture"));val active=relaunched.getString("session");expand(relaunched)
  val p=event(active,"status").getJSONObject("pending")
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id","org.example.recurrencefixture").put("grants",JSONArray()))
  assertThrows(IllegalStateException::class.java){authorize(active,p)}
 }
}
