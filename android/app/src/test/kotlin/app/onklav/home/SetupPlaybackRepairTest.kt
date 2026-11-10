package app.onklav.home

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[33])
class SetupPlaybackRepairTest {
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 @Test fun setupRequestsFeaturePermissionsAndRechecksAndroidApprovals(){
  shadowOf(c.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY,true)
  shadowOf(c.packageManager).setSystemFeature(PackageManager.FEATURE_MICROPHONE,true)
  shadowOf(c.packageManager).setSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE,true)
  shadowOf(c.packageManager).setSystemFeature(PackageManager.FEATURE_WIFI,true)
  val expected=setOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_CONTACTS,Manifest.permission.POST_NOTIFICATIONS,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.NEARBY_WIFI_DEVICES)
  assertTrue(OnKlavPermissions.runtime(c).containsAll(expected))
  shadowOf(c).grantPermissions(*expected.toTypedArray())
  assertTrue(OnKlavPermissions.runtime(c).isEmpty())
  shadowOf(c).denyPermissions(Manifest.permission.CAMERA)
  assertEquals(listOf(Manifest.permission.CAMERA),OnKlavPermissions.runtime(c))
  assertFalse(OnKlavPermissions.seen(c));OnKlavPermissions.finish(c);assertTrue(OnKlavPermissions.seen(c))
 }
 @Test fun upgradeRepairsShippedDefaultsOnceAndLaterExplicitRevocationsStayEffective()=runBlocking<Unit>{
  AppSdk.bootstrap(c)
  AppSdk.call(c,JSONObject().put("action","permissions").put("id",CLOCK_APP).put("grants",JSONArray()))
  c.getSharedPreferences("bundled-app-default-grants-v1",0).edit().remove("included-ready-v2:$CLOCK_APP").commit()
  AppSdk.bootstrap(c);assertTrue(ClockDisk.allowed(c))
  AppSdk.call(c,JSONObject().put("action","permissions").put("id",CLOCK_APP).put("grants",JSONArray()))
  AppSdk.bootstrap(c);assertFalse(ClockDisk.allowed(c))
 }
 @Test fun nativeSizeCallbackNeverOpensTheNetworkEvenWhenLengthIsUnknown(){
  val request=Request.Builder().url("https://publisher.example/stream.mp3").build();var calls=0
  val source=SdkMediaSources.Remote(JSONObject(),{},{request},{},probeFirst=true,connect={r->calls++;SdkServiceCall(r){sdkResponse(r,"abc".toByteArray()).newBuilder().body(object:okhttp3.ResponseBody(){override fun contentType():okhttp3.MediaType?=null;override fun contentLength()=-1L;override fun source()=okio.Buffer().writeUtf8("abc")}).build()}})
  try{repeat(3){assertEquals(-1L,source.getSize())};assertEquals(0,calls);source.prime();assertEquals(1,calls);repeat(3){assertEquals(-1L,source.getSize())};assertEquals(1,calls)}finally{source.close()}
 }
}
