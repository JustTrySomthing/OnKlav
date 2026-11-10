package app.onklav.home

import android.app.Application
import android.content.Intent
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkDeviceServicesTest {
 @Test fun timezoneGapAndOverlapAreExplicit(){
  fun args(start:String,end:String)=JSONObject().put("start",start).put("end",end).put("timezone","America/Chicago")
  val gap=args("2026-03-08T02:30:00","2026-03-08T04:30:00")
  assertThrows(Exception::class.java){SdkCalendarHandoff.resolve(gap)};assertTrue(SdkCalendarHandoff.resolve(gap.put("gap","forward")).getLong("end_ms")>0)
  val fold=args("2026-11-01T01:30:00","2026-11-01T03:30:00")
  val early=SdkCalendarHandoff.resolve(fold);val late=SdkCalendarHandoff.resolve(fold.put("overlap","later"));assertEquals(3600000L,late.getLong("start_ms")-early.getLong("start_ms"))
  val local=SdkCalendarHandoff.localize(JSONObject().put("start",early.getString("start")).put("end",early.getString("end")).put("timezone","America/Chicago"));assertEquals("20261101T013000Z",local.getString("start"))
 }
 @Test fun calendarHandoffLeavesFinalCalendarAndSaveToUser(){
  val a=JSONObject().put("title","Meeting").put("description","Review me").put("start_ms",1000L).put("end_ms",2000L).put("timezone","America/Chicago")
  val i=SdkCalendarHandoff.intent(a);assertEquals(Intent.ACTION_INSERT,i.action);assertEquals(CalendarContract.Events.CONTENT_URI,i.data);assertFalse(i.hasExtra(CalendarContract.Events.CALENDAR_ID));assertEquals(1000L,i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,0));assertThrows(Exception::class.java){SdkCalendarHandoff.intent(a.put("end_ms",500L))}
 }
 @Test fun manualLocationAndLanEndpointsRejectInvalidAndPublicTargets(){
  assertThrows(Exception::class.java){SdkLocation.manual(JSONObject().put("latitude",91).put("longitude",0))}
  val v=SdkLocation.manual(JSONObject().put("latitude",41).put("longitude",-87));assertEquals("manual",v.getString("source"));assertTrue(v.isNull("accuracy_m"))
  for(ip in listOf("127.0.0.1","8.8.8.8","0.0.0.0","224.0.0.1"))assertFalse(SdkNearby.local(InetAddress.getByName(ip)))
  assertTrue(SdkNearby.local(InetAddress.getByName("192.168.1.10")));assertTrue(SdkNearby.local(InetAddress.getByName("fd00::1")))
 }
 @Test fun connectivityHasNoNetworkOrHardwareIdentifiersAndSettingsAreWhitelisted(){
  val c=ApplicationProvider.getApplicationContext<Application>();val v=SdkConnectivity.snapshot(c)
  assertEquals(setOf("connected","internet","validated","captive_portal","metered","roaming","transport"),v.keys().asSequence().toSet())
  assertThrows(Exception::class.java){SdkConnectivity.settings(c,JSONObject().put("target","arbitrary.intent")){error("Must reject first")}}
 }
 @Test fun flashlightLeaseRejectsOtherOwnersAndExpiresWithoutReleasingAnotherSession(){
  val c=ApplicationProvider.getApplicationContext<Application>();val manager=c.getSystemService(android.hardware.camera2.CameraManager::class.java)
  val info=org.robolectric.shadows.ShadowCameraCharacteristics.newCameraCharacteristics();org.robolectric.Shadows.shadowOf(info).set(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE,true);org.robolectric.Shadows.shadowOf(info).set(android.hardware.camera2.CameraCharacteristics.LENS_FACING,android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)
  val camera=org.robolectric.Shadows.shadowOf(manager);camera.addCamera("0",info)
  fun request(owner:String)=JSONObject().put("owner",JSONObject().put("session",owner)).put("args",JSONObject().put("enabled",true).put("seconds",1))
  try{SdkTorch.execute(c,request("one")){};assertTrue(camera.getTorchMode("0"));assertThrows(Exception::class.java){SdkTorch.execute(c,request("two")){} };SdkTorch.release("two");assertTrue(camera.getTorchMode("0"));org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));assertFalse(camera.getTorchMode("0"))}finally{SdkTorch.clear()}
 }
 @Test fun deviceCoordinatesRemainApproximateWhenHostHasFinePermission(){
  assertEquals(41.88,SdkLocation.approximate(41.881234),0.000001);assertEquals(-87.62,SdkLocation.approximate(-87.627123),0.000001)
 }
 @Test fun foregroundLocationDenialOffersManualFallbackAndUnselectedTransportsFailClosed(){
  val c=ApplicationProvider.getApplicationContext<Application>()
  kotlinx.coroutines.runBlocking{
   val result=SdkLocation.current(c){};assertFalse(result.getBoolean("available"));assertTrue(result.getBoolean("manual_fallback"))
   var bleFailed=false;try{SdkBle.execute(c,JSONObject().put("owner",JSONObject().put("session","missing")).put("service","ble.read").put("args",JSONObject())){}}catch(e:Exception){bleFailed=true};assertTrue(bleFailed)
   var lanFailed=false;try{SdkNearby.exchange("missing",JSONObject().put("hex","00")){}}catch(e:Exception){lanFailed=true};assertTrue(lanFailed)
  }
 }
}
