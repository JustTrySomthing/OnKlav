package app.onklav.home

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.media3.datasource.DataSpec
import android.net.Uri
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import okio.buffer
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
// Host-only Media3 fixtures explicitly opt in; no private API is shipped.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@android.annotation.SuppressLint("BlockedPrivateApi")
class RadioStreamRepairTest {
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private var service:org.robolectric.android.controller.ServiceController<SdkPlaybackService>?=null
 private val engines=mutableListOf<Fake>()
 private val checks=mutableListOf<()->Unit>()
 private var audio=true
 private var moving=true
 private class Fake(val events:RadioStreamEngine.Events,val audible:()->Boolean,val moving:()->Boolean):RadioStreamEngine {var released=false;var starts=0;override val audioSession=42;override val positionMs get()=if(moving())android.os.SystemClock.elapsedRealtime()else 0L;override fun prepare(){events.ready()};override fun play(){starts++;if(audible())events.playing(true)};override fun pause(){events.playing(false)};override fun release(){released=true}}
 @After fun clean(){SdkMedia.internetAvailable=::validatedInternet;SdkMedia.stop();service?.destroy();SdkMedia.radioFactory={c,u,v,e->ExoRadioStream(c,u,v,e)}}
 private fun start():JSONObject{
  SdkMedia.internetAvailable={true}
  runBlocking{AppSdk.bootstrap(c)};val row=runBlocking{AppSdk.list(c)}.first{it.child("manifest").optString("id")=="app.onklav.radio"}
  val o=JSONObject().put("id","app.onklav.radio").put("publisher",row.child("manifest").getString("publisher")).put("hash",row.getString("hash")).put("name","Radio")
  SdkMedia.radioFactory={_,_,verify,e->verify();checks+=verify;Fake(e,{audio},{moving}).also{engines+=it}}
  val handle=UUID.randomUUID().toString();val url="http://radio.example:8000/live";val item=JSONObject().put("radio_station",handle).put("radio_id",UUID.randomUUID().toString()).put("url",url).put("title","Live")
  SdkRadio.start(c,SdkRadio.Prepared(JSONObject().put("service","media.play").put("owner",o).put("args",JSONObject().put("queue",JSONArray().put(item))),JSONObject(),handle,url)){}
  if(service==null)service=Robolectric.buildService(SdkPlaybackService::class.java).create();shadowOf(c).nextStartedService?.let{service!!.get().onStartCommand(it,0,1)};idle(20);return o
 }
 private fun idle(ms:Long)=shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
 @Test fun resumeCreatesFreshLiveConnectionAndNeverStaysReady(){val o=start();assertEquals("playing",SdkMedia.status(o).getString("state"));val first=engines.last();SdkMedia.controlFor(o,"pause");idle(20);assertTrue(first.released);idle(120000);SdkMedia.controlFor(o,"play");idle(20);assertEquals(2,engines.size);assertEquals("playing",SdkMedia.status(o).getString("state"));assertEquals(42,SdkMedia.radioAudioSession(o))}
 @Test fun screenOffDoesNotStopServiceAndExplicitStopAndRevocationCancelRecovery(){val o=start();c.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_SCREEN_OFF));idle(20000);assertEquals("playing",SdkMedia.status(o).getString("state"));assertFalse(engines.last().released);engines.last().events.ended();SdkMedia.controlFor(o,"pause");val size=engines.size;idle(4000);assertEquals(size,engines.size);SdkMedia.controlFor(o,"play");idle(20);engines.last().events.failed(java.io.IOException("Disconnected"));AppSdk.sync(c,JSONObject().put("action","permissions").put("id",o.getString("id")).put("grants",JSONArray().put("storage.private")));idle(4000);assertEquals("idle",SdkMedia.status(o).getString("state"));assertTrue(engines.last().released)}
 @Test fun replacedStationCannotAuthorizeOldReadsOrReceiveOldCallbacks(){val o=start();val old=engines.last();val oldCheck=checks.last();start();val size=engines.size;assertThrows(Exception::class.java){oldCheck()};old.events.ended();idle(4000);assertEquals(size,engines.size);assertEquals("playing",SdkMedia.status(o).getString("state"))}
 @Test fun newRadioSessionRestoresQueryAndLoadsCachedResultsWithoutAUserSearch()=runBlocking<Unit>{
  AppSdk.bootstrap(c);val row=AppSdk.list(c).first{it.child("manifest").optString("id")=="app.onklav.radio"};val publisher=row.child("manifest").getString("publisher")
  c.getSharedPreferences("radio-view-"+publisher,0).edit().putString("query","Jazz").putString("tab","explore").commit()
  val model=SdkAppModel(c);model.open("app.onklav.radio");assertTrue(model.restoreRadioView());assertEquals("radio.search",model.pending!!.getString("service"));assertEquals("Jazz",model.pending!!.child("args").getString("query"))
  var fetches=0;val snapshot=SdkRadioCatalog.get(c,false,{}){fetches++;error("No network needed")};assertEquals(0,fetches);assertTrue(snapshot.rows.isNotEmpty())
 }
 @Test fun readyWithoutAudioAndLongBufferingCannotRemainStuck(){
  audio=false;val o=start();assertEquals("preparing",SdkMedia.status(o).getString("state"));val first=engines.last();idle(20300);assertTrue(first.released);assertEquals(2,engines.size)
  audio=true;SdkMedia.networkRecovered();idle(20);assertEquals("playing",SdkMedia.status(o).getString("state"))
  val second=engines.last();second.events.playing(false);idle(20300);assertTrue(second.released);assertEquals("playing",SdkMedia.status(o).getString("state"))
 }
 @Test fun frozenPlaybackClockIsRecoveredEvenWhenEngineClaimsPlaying(){
  moving=false;val o=start();val first=engines.last();idle(21300);assertTrue(first.released);assertTrue(engines.size>=2)
  moving=true;SdkMedia.networkRecovered();idle(20);assertEquals("playing",SdkMedia.status(o).getString("state"));val count=engines.size;idle(30000);assertEquals(count,engines.size)
 }
 @Test fun offlineWaitDoesNotExhaustRetriesAndFallbackRecoversWithoutCallback(){
  val o=start();var online=false;SdkMedia.internetAvailable={online};engines.last().events.failed(java.io.IOException());idle(120000)
  assertEquals(1,engines.size);assertEquals("offline",SdkMedia.status(o).getString("connection"));assertEquals("Waiting for internet…",radioConnectionLabel(SdkMedia.status(o)))
  online=true;idle(5100);assertEquals("playing",SdkMedia.status(o).getString("state"));assertEquals(2,engines.size)
 }
 @Test fun longPauseAndRepeatedRouteChangesNeverRequireAppRestart(){
  val o=start();SdkMedia.controlFor(o,"pause");val count=engines.size;idle(1200000);SdkMedia.networkRecovered();assertEquals(count,engines.size);assertEquals("paused",SdkMedia.status(o).getString("state"))
  SdkMedia.controlFor(o,"play");idle(20);assertEquals("playing",SdkMedia.status(o).getString("state"))
  repeat(4){val old=engines.last();SdkMedia.networkRecovered();idle(20);assertTrue(old.released);old.events.failed(java.io.IOException());idle(300);assertEquals("playing",SdkMedia.status(o).getString("state"))}
 }
 @Test fun abandonedAudioFocusCallbackCannotPauseNewStation(){
  val o=start();val field=SdkMedia::class.java.getDeclaredField("focus").apply{isAccessible=true};val old=field.get(SdkMedia) as android.media.AudioFocusRequest
  start();(android.media.AudioFocusRequest::class.java.getDeclaredMethod("getOnAudioFocusChangeListener").apply{isAccessible=true}.invoke(old) as android.media.AudioManager.OnAudioFocusChangeListener).onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_LOSS);idle(20);assertEquals("playing",SdkMedia.status(o).getString("state"))
 }
 private class Stub(val req:Request,val response:()->Response):Call{var canceled=false;override fun request()=req;override fun execute()=response();override fun enqueue(responseCallback:Callback)=error("unused");override fun cancel(){canceled=true};override fun isExecuted()=true;override fun isCanceled()=canceled;override fun clone():Call=Stub(req,response);override fun timeout()=Timeout()}
 @Test fun streamingSourcePreservesIcyHeadersAndChecksPermissionOnEachRead(){var allowed=true;val calls=mutableListOf<Stub>();val source=RadioHttpSource({check(allowed)}){req->assertEquals("1",req.header("Icy-MetaData"));assertNull(req.header("Authorization"));Stub(req){Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").header("icy-metaint","16000").body("audio".toResponseBody()).build()}.also{calls+=it}}
  assertEquals(5L,source.open(DataSpec(Uri.parse("http://stream.example/live"))))
  assertEquals(listOf("16000"),source.responseHeaders["icy-metaint"]);assertEquals(2,source.read(ByteArray(2),0,2));allowed=false;assertThrows(Exception::class.java){source.read(ByteArray(2),0,2)};source.close();assertTrue(calls.single().canceled)
 }
 @Test fun closingDuringResponseArrivalClosesLateBodyAndCannotResurrectAConnection(){
  val entered=java.util.concurrent.CountDownLatch(1);val finish=java.util.concurrent.CountDownLatch(1);val closed=java.util.concurrent.atomic.AtomicBoolean(false)
  val error=java.util.concurrent.atomic.AtomicReference<Throwable?>()
  val body=object:ResponseBody(){val stream=object:okio.ForwardingSource(okio.Buffer().writeUtf8("audio")){override fun close(){closed.set(true);super.close()}}.let{it.buffer()};override fun contentType():MediaType?=null;override fun contentLength()=5L;override fun source()=stream}
  val source=RadioHttpSource({}){req->Stub(req){entered.countDown();check(finish.await(3,java.util.concurrent.TimeUnit.SECONDS));Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()}}
  val thread=kotlin.concurrent.thread(isDaemon=true){try{source.open(DataSpec(Uri.parse("https://stream.example/live")))}catch(e:Throwable){error.set(e)}}
  assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));source.close();finish.countDown();thread.join(3000)
  assertFalse(thread.isAlive);assertTrue(closed.get());assertTrue(error.get() is java.io.IOException)
 }
 @Test fun stationProbeCancelsTheLiveSocketBeforeClosingTheResponseBody(){
  var connection:Stub?=null;var closed=false
  val url=SdkRadio.resolve("probe-order","https://stream.example/live",{},connect={req->
   Stub(req){val body=object:ResponseBody(){val input=object:okio.ForwardingSource(okio.Buffer().writeUtf8("audio")){override fun close(){assertTrue("Cancel the live probe before closing its body",connection!!.canceled);closed=true;super.close()}}.buffer();override fun contentType():MediaType?=null;override fun contentLength()=5L;override fun source()=input}
    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").header("Content-Type","audio/mpeg").body(body).build()
   }.also{connection=it}
  },lookup={})
  assertTrue(closed);assertTrue(connection!!.canceled);assertEquals("https://stream.example/live",url)
 }
 @Test fun extensionlessHlsKeepsItsContentType(){val url=SdkRadio.resolve("hls-fixture","https://stream.example/channel",{},connect={req->Stub(req){Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK").header("Content-Type","application/vnd.apple.mpegurl").body("#EXTM3U\n#EXT-X-VERSION:3".toResponseBody()).build()}},lookup={});assertEquals("https://stream.example/channel",url);assertEquals(androidx.media3.common.MimeTypes.APPLICATION_M3U8,RadioMimeHints.mime(url))}
 @Test fun playlistsResolveRelativeAudioAndPublicHttpButNeverPrivateSchemes(){assertEquals("https://stream.example/live.aac",radioPlaylistTarget("https://stream.example/radio/list.m3u","#EXTM3U\n../live.aac"));assertEquals("http://stream.example:8000/live",radioPlaylistTarget("https://stream.example/list.pls","[playlist]\nNumberOfEntries=1\nFile1=http://stream.example:8000/live\nTitle1=Radio\nVersion=2"));assertThrows(Exception::class.java){radioPlaylistTarget("https://stream.example/list","file:///private\nhttp://127.0.0.1/secret")}}
}
