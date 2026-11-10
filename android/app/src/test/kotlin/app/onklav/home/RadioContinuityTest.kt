package app.onklav.home

import android.app.Application
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import java.time.Duration
import java.util.UUID

/** Real signed broker policy/service/listeners; only decoder/network bytes are simulated. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class RadioContinuityTest {
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private var controller:org.robolectric.android.controller.ServiceController<SdkPlaybackService>?=null
 private var opens=0
 @Before fun useFrameworkFixture(){SdkMedia.radioFactory=null}
 @After fun cleanup(){SdkMedia.internetAvailable=::validatedInternet;SdkMedia.stop();controller?.destroy();SdkMedia.openSource=SdkMediaSources::open;SdkMedia.radioFactory={c,u,v,e->ExoRadioStream(c,u,v,e)}}
 private fun player()=SdkMedia::class.java.getDeclaredField("player").apply{isAccessible=true}.get(SdkMedia) as? MediaPlayer
 private fun idle(ms:Long=20){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))}
 private fun start():JSONObject{
  SdkMedia.internetAvailable={true}
  runBlocking{AppSdk.bootstrap(c)}
  val row=runBlocking{AppSdk.list(c)}.first{it.child("manifest").optString("id")=="app.onklav.radio"}
  val o=JSONObject().put("id","app.onklav.radio").put("publisher",row.child("manifest").getString("publisher")).put("hash",row.getString("hash")).put("name","Radio")
  ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(600000,0)}
  SdkMedia.openSource={_,_,_->opens++;object:MediaDataSource(){override fun getSize()=600000L;override fun readAt(p:Long,b:ByteArray,off:Int,n:Int)=-1;override fun close(){}}}
  val handle=UUID.randomUUID().toString();val url="https://radio.example/live"
  val item=JSONObject().put("radio_station",handle).put("radio_id",UUID.randomUUID().toString()).put("url",url).put("title","Continuous Radio")
  val prepared=SdkRadio.Prepared(JSONObject().put("service","media.play").put("owner",o).put("args",JSONObject().put("queue",JSONArray().put(item))),JSONObject(),handle,url)
  SdkRadio.start(c,prepared){}
  if(controller==null)controller=Robolectric.buildService(SdkPlaybackService::class.java).create()
  shadowOf(c).nextStartedService?.let{controller!!.get().onStartCommand(it,0,1)}
  idle();assertEquals("playing",SdkMedia.status(o).getString("state"));return o
 }
 @Test fun trackBoundaryReconnectsLiveEdgeAndKeepsChangingAudioSession(){
  val o=start();val first=player()!!;shadowOf(first).invokeCompletionListener();assertEquals("preparing",SdkMedia.status(o).getString("state"));idle(280)
  val second=player()!!;assertNotSame(first,second);assertEquals("playing",SdkMedia.status(o).getString("state"));assertEquals(2,opens);assertTrue(SdkMedia.status(o).getLong("position_ms")<1000)
  // A normal song duration resets rapid-disconnect budget; repeat beyond the retry cap.
  repeat(5){idle(16000);shadowOf(player()!!).invokeCompletionListener();idle(280);assertEquals("playing",SdkMedia.status(o).getString("state"))}
 }
 @Test fun pauseStopAndReplacementCancelQueuedReconnection(){
  val o=start();shadowOf(player()!!).invokeCompletionListener();SdkMedia.controlFor(o,"pause");val count=opens;idle(4000);assertEquals(count,opens);assertEquals("paused",SdkMedia.status(o).getString("state"))
  SdkMedia.controlFor(o,"play");idle();assertEquals("playing",SdkMedia.status(o).getString("state"));shadowOf(player()!!).invokeCompletionListener();SdkMedia.stop();idle(4000);assertEquals("idle",SdkMedia.status(o).getString("state"));assertNull(player())
  val newer=start();val before=opens;shadowOf(player()!!).invokeCompletionListener();start();idle(4000);assertEquals(before+1,opens);assertEquals("playing",SdkMedia.status(newer).getString("state"))
 }
 @Test fun rapidEofAndIoErrorsAreBoundedAndManualPlayCanRetry(){
  val o=start()
  shadowOf(player()!!).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN,MediaPlayer.MEDIA_ERROR_IO);idle(280)
  shadowOf(player()!!).invokeCompletionListener();idle(1030)
  shadowOf(player()!!).invokeCompletionListener();idle(2530)
  shadowOf(player()!!).invokeCompletionListener();idle(5000)
  assertEquals("error",SdkMedia.status(o).getString("state"));assertEquals(4,opens);assertNull(player())
  assertTrue(SdkMedia.status(o).getString("error").contains("keeps disconnecting"))
  SdkMedia.controlFor(o,"play");idle();assertEquals("playing",SdkMedia.status(o).getString("state"))
 }
 @Test fun sleepDeadlineAndRevocationCannotBeBypassedByReconnect(){
  val o=start();SdkMedia.controlFor(o,"sleep",JSONObject().put("minutes",1));idle(59500);shadowOf(player()!!).invokeCompletionListener();idle(700);assertEquals("idle",SdkMedia.status(o).getString("state"));val count=opens;idle(4000);assertEquals(count,opens)
  val next=start();shadowOf(player()!!).invokeCompletionListener()
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.radio").put("grants",JSONArray().put("storage.private")));idle(4000);assertNull(player());assertEquals("idle",SdkMedia.status(next).getString("state"))
 }
 @Test fun unsupportedHeadsetCommandsDoNotStopLiveRadio(){
  val o=start();val active=player()!!
  val epoch=SdkMedia::class.java.getDeclaredField("generation").apply{isAccessible=true}.get(SdkMedia) as String
  for(op in listOf("seek","next","previous","speed")){
   SdkMedia.system(op,epoch,JSONObject().put("position_ms",400).put("speed",2))
   assertEquals("playing",SdkMedia.status(o).getString("state"));assertSame(active,player())
  }
  assertEquals(1,opens)
  SdkMedia.system("pause",epoch);assertEquals("paused",SdkMedia.status(o).getString("state"))
 }

}
