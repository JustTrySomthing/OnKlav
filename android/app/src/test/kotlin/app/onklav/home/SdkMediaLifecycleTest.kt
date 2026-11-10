package app.onklav.home

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkMediaLifecycleTest {
 @get:Rule val emptyKeys=EmptyKeyStoreRule()
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 @After fun cleanup(){SdkMedia.stop()}
 private suspend fun operation():JSONObject{
  File(System.getProperty("onklav.project.root"),"docs/fixtures/tasks-wip.onklav-a").inputStream().use{AppSdk.stage(c,it)};val review=AppSdk.inspect(c);AppSdk.install(c,review,setOf("background.tasks","network.http","storage.private","audio.playback"))
  val opened=AppSdk.call(c,JSONObject().put("action","launch").put("id","org.example.tasksfixture"))as JSONObject;val token=opened.getString("session")
  val event=AppSdk.call(c,JSONObject().put("action","event").put("session",token).put("event",JSONObject().put("action","media")))as JSONObject
  return AppSdk.call(c,JSONObject().put("action","authorize").put("session",token).put("token",event.getJSONObject("pending").getString("token")))as JSONObject
 }
 @Test fun signedPlayerOwnsSessionControlsAndStopsOnRevocation()=runBlocking<Unit>{
  val op=operation();ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(60000,0)}
  val row=SdkMedia.execute(c,op){};assertEquals("preparing",row.getString("state"));val intent=shadowOf(c).nextStartedService
  val controller=Robolectric.buildService(SdkPlaybackService::class.java).create();try{
   controller.get().onStartCommand(intent,0,1);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
   val owner=op.getJSONObject("owner");assertEquals("playing",SdkMedia.status(owner).getString("state"));SdkMedia.controlFor(owner,"pause");assertEquals("paused",SdkMedia.status(owner).getString("state"));SdkMedia.controlFor(owner,"seek",JSONObject().put("position_ms",10000));SdkMedia.controlFor(owner,"speed",JSONObject().put("speed",1.5));assertEquals(1.5,SdkMedia.status(owner).getDouble("speed"),0.001)
   val other=JSONObject(owner.toString()).put("publisher","b".repeat(64));assertEquals("idle",SdkMedia.status(other).getString("state"));assertThrows(IllegalStateException::class.java){SdkMedia.controlFor(other,"play")}
   AppSdk.call(c,JSONObject().put("action","permissions").put("id","org.example.tasksfixture").put("grants",org.json.JSONArray().put("storage.private")));shadowOf(Looper.getMainLooper()).idle();assertEquals("idle",SdkMedia.status(owner).getString("state"))
  }finally{controller.destroy()}
 }
 @Test fun pauseDuringPreparationAndReplacementNeverRestartOldPlayback()=runBlocking<Unit>{
  val op=operation();val owner=op.getJSONObject("owner");ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(60000,30)}
  SdkMedia.execute(c,op){};val controller=Robolectric.buildService(SdkPlaybackService::class.java).create();try{
   controller.get().onStartCommand(shadowOf(c).nextStartedService,0,1);SdkMedia.controlFor(owner,"pause");shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));assertEquals("paused",SdkMedia.status(owner).getString("state"))
   SdkMedia.controlFor(owner,"speed",JSONObject().put("speed",2.0));assertEquals("paused",SdkMedia.status(owner).getString("state"));SdkMedia.controlFor(owner,"play");assertEquals("playing",SdkMedia.status(owner).getString("state"))
   SdkMedia.execute(c,op){};shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));assertEquals("playing",SdkMedia.status(owner).getString("state"));SdkMedia.controlFor(owner,"next");assertEquals("ended",SdkMedia.status(owner).getString("state"))
  }finally{controller.destroy()}
 }
 @Test fun deletingAppDataClearsPositionsWithoutALateStopRecreatingThem()=runBlocking<Unit>{
  val op=operation();val owner=op.getJSONObject("owner");val id=owner.getString("id");ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(60000,0)}
  SdkMedia.execute(c,op){};val controller=Robolectric.buildService(SdkPlaybackService::class.java).create();try{
   controller.get().onStartCommand(shadowOf(c).nextStartedService,0,1);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10));SdkMedia.controlFor(owner,"pause");SdkMedia.controlFor(owner,"seek",JSONObject().put("position_ms",10000))
   val prefs=c.getSharedPreferences("sdk-media-positions-$id",0);assertFalse(prefs.all.isEmpty())
   SdkNativeData.removed(c,id,true,clearAccounts={_,_->},clearSecrets={_,_->});shadowOf(Looper.getMainLooper()).idle();assertTrue(prefs.all.isEmpty())
   val fresh=SdkMedia.execute(c,op){};assertEquals(0L,fresh.getLong("position_ms"));assertTrue(prefs.all.isEmpty())
  }finally{controller.destroy()}
 }

 @Test fun sleepStopsOnlyItsCurrentPlayerAndReplacementCancelsDeadline()=runBlocking<Unit>{
  val op=operation();val owner=op.getJSONObject("owner");ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(600000,0)};SdkMedia.execute(c,op){};val controller=Robolectric.buildService(SdkPlaybackService::class.java).create();try{controller.get().onStartCommand(shadowOf(c).nextStartedService,0,1);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10));SdkMedia.controlFor(owner,"sleep",JSONObject().put("minutes",1));assertTrue(SdkMedia.status(owner).getLong("sleep_remaining_ms")>0);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));assertEquals("idle",SdkMedia.status(owner).getString("state"));SdkMedia.execute(c,op){};controller.get().onStartCommand(shadowOf(c).nextStartedService,0,2);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10));SdkMedia.controlFor(owner,"sleep",JSONObject().put("minutes",1));SdkMedia.execute(c,op){};shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));assertEquals("playing",SdkMedia.status(owner).getString("state"));assertEquals(0,SdkMedia.status(owner).getLong("sleep_remaining_ms"));}finally{controller.destroy()}
 }
}
