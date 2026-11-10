package app.onklav.home

import android.app.Application
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import java.io.File
import java.time.Duration
import java.util.UUID

/** Signed owners and real service/lease/focus handoffs; only audio decoding is simulated. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AudioHandoffTest{
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private var service:org.robolectric.android.controller.ServiceController<SdkPlaybackService>?=null
 private var opens=0;private var closes=0
 private val engines=mutableListOf<Engine>()
 private fun field(name:String)=SdkMedia::class.java.getDeclaredField(name).apply{isAccessible=true}.get(SdkMedia)
 private fun player()=field("player") as? MediaPlayer
 private fun idle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30))}
 private fun owner(id:String):JSONObject{val row=runBlocking{AppSdk.list(c)}.first{it.child("manifest").optString("id")==id};return JSONObject().put("id",id).put("publisher",row.child("manifest").getString("publisher")).put("hash",row.getString("hash")).put("name",id)}
 private inner class Engine(val events:RadioStreamEngine.Events):RadioStreamEngine{
  var released=false;var paused=false;var lockedRelease=false
  override val audioSession=42
  override fun prepare(){events.ready()}
  override fun play(){events.playing(true)}
  override fun pause(){paused=true;events.playing(false)}
  override fun release(){val lock=AppSdk::class.java.getDeclaredField("carrierLock").apply{isAccessible=true}.get(AppSdk);lockedRelease=Thread.holdsLock(lock);released=true}
 }
 @Before fun setup(){runBlocking{AppSdk.bootstrap(c)};ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(600000,0)};SdkMedia.radioFactory={_,_,_,e->Engine(e).also{engines+=it}};SdkMedia.openSource={_,_,_->opens++;object:MediaDataSource(){override fun getSize()=600000L;override fun readAt(p:Long,b:ByteArray,o:Int,n:Int)=-1;override fun close(){closes++}}}}
 @After fun clean(){SdkMedia.stop();idle();service?.destroy();SdkMedia.openSource=SdkMediaSources::open;SdkMedia.radioFactory={c,u,v,e->ExoRadioStream(c,u,v,e)}}
 private fun radio():JSONObject{val o=owner("app.onklav.radio");val handle=UUID.randomUUID().toString();val url="https://radio.example/live";val item=JSONObject().put("radio_station",handle).put("radio_id",UUID.randomUUID().toString()).put("url",url).put("title","Radio");SdkRadio.start(c,SdkRadio.Prepared(JSONObject().put("service","media.play").put("owner",o).put("args",JSONObject().put("queue",JSONArray().put(item))),JSONObject(),handle,url)){};return o}
 private fun podcast():JSONObject{val o=owner("app.onklav.podcasts");val handle=UUID.randomUUID().toString();val f=File(c.filesDir,"handoff-episode.mp3").apply{writeBytes(ByteArray(512))};val url=f.toURI().toString();val item=JSONObject().put("podcast_episode",handle).put("podcast_id","a".repeat(64)).put("podcast_local",true).put("url",url).put("title","Downloaded episode");SdkPodcasts.start(c,SdkPodcasts.Prepared(JSONObject().put("service","media.play").put("owner",o).put("args",JSONObject().put("queue",JSONArray().put(item))),JSONObject(),handle,url,f)){};return o}
 private fun audio():JSONObject{val o=owner("app.onklav.audioeditor");SdkMedia.execute(c,JSONObject().put("service","media.play").put("owner",o).put("args",JSONObject().put("queue",JSONArray().put(JSONObject().put("title","Audio clip").put("url","https://audio.example/clip"))))){};return o}
 private fun attach(intent:android.content.Intent){if(service==null)service=Robolectric.buildService(SdkPlaybackService::class.java).create();service!!.get().onStartCommand(intent,0,1);idle()}
 @Test fun radioPodcastAndOtherAudioReleaseOldPlayersAndKeepTheNewOwnerPlaying(){
  val r=radio();attach(Shadows.shadowOf(c).nextStartedService);val old=engines.single();assertEquals("playing",SdkMedia.status(r).getString("state"))
  val p=podcast();idle();assertTrue(old.paused);assertTrue(old.released);assertFalse(old.lockedRelease);assertEquals("idle",SdkMedia.status(r).getString("state"));assertEquals("playing",SdkMedia.status(p).getString("state"))
  old.events.ended();old.events.failed(java.io.IOException("Late old station callback"));idle();assertEquals("playing",SdkMedia.status(p).getString("state"))
  val oldPodcast=player();val a=audio();idle();assertNotSame(oldPodcast,player());assertEquals("idle",SdkMedia.status(p).getString("state"));assertEquals("playing",SdkMedia.status(a).getString("state"))
  val r2=radio();idle();assertEquals(opens,closes);assertEquals("idle",SdkMedia.status(a).getString("state"));assertEquals("playing",SdkMedia.status(r2).getString("state"));assertEquals(2,engines.size)
 }
 @Test fun staleServiceStartsAndDuplicateDeliveryCannotStopOrReprepareTheNewSession(){
  radio();val obsolete=Shadows.shadowOf(c).nextStartedService;val p=podcast();val current=Shadows.shadowOf(c).nextStartedService
  service=Robolectric.buildService(SdkPlaybackService::class.java).create();service!!.get().onStartCommand(obsolete,0,1);assertFalse(Shadows.shadowOf(service!!.get()).isStoppedBySelf)
  service!!.get().onStartCommand(current,0,2);idle();val active=player();val session=field("session");assertEquals("playing",SdkMedia.status(p).getString("state"))
  service!!.get().onStartCommand(obsolete,0,1);service!!.get().onStartCommand(current,0,2);idle();assertSame(active,player());assertSame(session,field("session"));assertFalse(Shadows.shadowOf(service!!.get()).isStoppedBySelf);assertEquals("playing",SdkMedia.status(p).getString("state"))
  SdkMedia.stop();SdkMedia.system("play",current.getStringExtra("generation")!!);assertEquals("idle",SdkMedia.status(p).getString("state"))
 }
 @Test fun lateInterruptionCannotPauseReplacementAndHandoffInterruptsBeforeFocusIsRequested(){
  val r=radio();attach(Shadows.shadowOf(c).nextStartedService);val interruption=HostSpeechOwner::class.java.getDeclaredField("interrupt").apply{isAccessible=true}.get(HostSpeechOwner) as ()->Unit
  val external=Any();HostSpeechOwner.claim(external){};assertEquals("paused",SdkMedia.status(r).getString("state"));HostSpeechOwner.release(external)
  val p=podcast();idle();interruption();idle();assertEquals("playing",SdkMedia.status(p).getString("state"))
 }
 @Test fun previousOwnerCleanupDoesNotHoldTheOwnershipMonitorOrBlockNewAudioOnFailure(){
  val old=Any();val next=Any();val third=Any();var held=true;var interrupted=false;HostSpeechOwner.claim(old){held=Thread.holdsLock(HostSpeechOwner);error("Old decoder failed cleanup")};HostSpeechOwner.claim(next){interrupted=true};assertFalse(held);HostSpeechOwner.release(old);HostSpeechOwner.claim(third){};assertTrue(interrupted);HostSpeechOwner.release(next);HostSpeechOwner.release(third)
 }
 @Test fun earlyOrOutOfRangeSystemSeekDoesNotTurnAPodcastIntoAPlaybackFailure(){
  val o=podcast();val epoch=field("generation") as String
  SdkMedia.system("seek",epoch,JSONObject().put("position_ms",100))
  assertEquals("preparing",SdkMedia.status(o).getString("state"))
  attach(Shadows.shadowOf(c).nextStartedService);val active=player()
  assertEquals("playing",SdkMedia.status(o).getString("state"))
  SdkMedia.system("seek",epoch,JSONObject().put("position_ms",SdkMedia.status(o).getLong("duration_ms")+1))
  assertEquals("playing",SdkMedia.status(o).getString("state"));assertSame(active,player())
  assertThrows(IllegalArgumentException::class.java){SdkMedia.controlFor(o,"seek",JSONObject().put("position_ms",-1))}
 }

}
