package app.onklav.home
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
/** Explicit opt-in: actual public directory, one selected free stream, bounded sample only. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class SdkRadioLiveTest {
 @Test(timeout=60000) fun signedRadioUsesNoAccountStreamsRealBytesAndRevocationStopsReads()=runBlocking<Unit>{
  assumeTrue("Live check is opt-in",System.getenv("ONKLAV_LIVE_RADIO")=="1")
  val c=ApplicationProvider.getApplicationContext<Application>();AppSdk.bootstrap(c)
  fun raw(j:JSONObject)=AppSdk.sync(c,j)
  fun call(j:JSONObject)=raw(j) as JSONObject
  raw(JSONObject().put("action","permissions").put("id","app.onklav.radio").put("grants",JSONArray(listOf("storage.private","radio.stations","audio.playback"))))
  val opened=call(JSONObject().put("action","launch").put("id","app.onklav.radio"));val session=opened.getString("session")
  fun event(a:String,v:String="")=call(JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action",a).put("value",v)))
  fun authorize(p:JSONObject)=call(JSONObject().put("action","authorize").put("session",session).put("token",p.getString("token")))
  try {
   val initial=opened.getJSONObject("pending");val first=SdkRadio.search(c,initial.getString("token")){authorize(initial)};call(JSONObject().put("action","reply").put("session",session).put("token",initial.getString("token")).put("reply",JSONObject().put("ok",true).put("value",first)))
   event("radio.explore");event("radio.query","SomaFM");val pending=event("radio.search").getJSONObject("pending");assertEquals("radio.search",pending.getString("service"))
   val page=SdkRadio.search(c,pending.getString("token")){authorize(pending)};assertTrue(page.getJSONArray("items").length()>0)
   val result=call(JSONObject().put("action","reply").put("session",session).put("token",pending.getString("token")).put("reply",JSONObject().put("ok",true).put("value",page)));assertTrue(result.isNull("pending"))
   val rows=page.getJSONArray("items");val station=(0 until rows.length()).map{rows.getJSONObject(it)}.firstOrNull{it.getString("name").contains("Groove Salad",true)&&it.getString("codec")=="MP3"}?:rows.getJSONObject(0)
   val play=event("radio.play."+station.getString("id")).getJSONObject("pending");val prepared=SdkRadio.prepare(c,play.getString("token")){authorize(play)}
   SdkRadio.start(c,prepared){authorize(play);Unit}
   val owner=prepared.request.getJSONObject("owner");val item=prepared.request.getJSONObject("args").getJSONArray("queue").getJSONObject(0)
   val source=SdkMediaSources.open(c,owner,item)
   try{val b=ByteArray(8192);val count=source.readAt(0,b,0,b.size);assertTrue("No live audio bytes received",count>0);assertTrue(SdkMedia.status(owner).getBoolean("live"));raw(JSONObject().put("action","permissions").put("id","app.onklav.radio").put("grants",JSONArray().put("storage.private")));assertThrows(Exception::class.java){source.readAt(count.toLong(),b,0,b.size)}}finally{source.close()}
  } finally {SdkRadio.cancelAll();SdkMedia.stop();raw(JSONObject().put("action","close").put("session",session))}
 }
}
