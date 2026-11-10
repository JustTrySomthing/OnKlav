package app.onklav.home

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.net.*
import android.os.*
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.*
import java.io.File
import java.time.Duration
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class OfflineHandoffTest{
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private val feed="https://publisher.example/rss"
 private val episode=SdkPodcasts.id("offline episode")
 @After fun cleanup(){SdkMedia.stop();SdkMedia.openSource=SdkMediaSources::open}
 private fun owner():JSONObject{runBlocking{AppSdk.bootstrap(c)};val row=runBlocking{AppSdk.list(c)}.first{it.child("manifest").optString("id")=="app.onklav.podcasts"};return JSONObject().put("id","app.onklav.podcasts").put("publisher",row.child("manifest").getString("publisher")).put("hash",row.getString("hash")).put("name","Podcasts")}
 private fun request(o:JSONObject,service:String="podcasts.play")=JSONObject().put("service",service).put("owner",o).put("args",JSONObject().put("feed",feed).put("episode",episode))
 private fun seed(o:JSONObject):File{val root=File(c.filesDir,"podcast-downloads/"+SdkPodcasts.id(o.getString("id")+"|"+o.getString("publisher"))).apply{mkdirs()};val data=File(root,"$episode.audio").apply{writeText("saved offline audio")};File(root,"$episode.json").writeText(JSONObject().put("id",episode).put("feed",feed).put("title","Saved episode").put("show_title","Saved show").put("bytes",data.length()).toString());return data}
 private fun player()=SdkMedia::class.java.getDeclaredField("player").apply{isAccessible=true}.get(SdkMedia) as? MediaPlayer
 private fun start(prepared:SdkPodcasts.Prepared,delay:Int=0):org.robolectric.android.controller.ServiceController<SdkPlaybackService>{ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(60000,delay)};SdkMedia.openSource={_,_,_->object:android.media.MediaDataSource(){override fun getSize()=60000L;override fun readAt(at:Long,b:ByteArray,off:Int,n:Int)=-1;override fun close(){}}};SdkPodcasts.start(c,prepared){};return Robolectric.buildService(SdkPlaybackService::class.java).create().also{it.get().onStartCommand(shadowOf(c).nextStartedService,0,1)}}
 private fun validated(){val cm=c.getSystemService(ConnectivityManager::class.java);val s=shadowOf(cm);s.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,ConnectivityManager.TYPE_MOBILE,0,true,NetworkInfo.State.CONNECTED));val caps=ShadowNetworkCapabilities.newInstance();shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);s.setNetworkCapabilities(requireNotNull(cm.activeNetwork),caps);assertTrue(validatedInternet(c))}
 private fun remote(o:JSONObject):SdkPodcasts.Prepared{val handle=UUID.randomUUID().toString();val url="https://audio.example/episode.mp3";val item=JSONObject().put("url",url).put("title","Stream").put("podcast_episode",handle).put("podcast_id",episode);return SdkPodcasts.Prepared(JSONObject().put("service","media.play").put("owner",o).put("args",JSONObject().put("queue",JSONArray().put(item))),JSONObject().put("id",episode),handle,url)}
 @Test fun offlineShowListsDownloadsAndFileDescriptorReadsWithoutFeedLookup()=runBlocking<Unit>{val o=owner();val file=seed(o);SdkPodcasts.clearApp(o.getString("id"));assertFalse(validatedInternet(c));val page=SdkPodcasts.feed(c,"offline-feed","Saved show"){request(o,"podcasts.feed")};assertEquals("Saved show",page.getString("title"));assertEquals(episode,page.getJSONArray("items").getJSONObject(0).getString("id"));val prepared=SdkPodcasts.prepare(c,"offline-play"){request(o)};val service=start(prepared);try{val item=prepared.request.getJSONObject("args").getJSONArray("queue").getJSONObject(0);val descriptor=SdkPodcasts.openLocalDescriptor(c,o,item)!!;val bytes=ParcelFileDescriptor.AutoCloseInputStream(descriptor).use{it.readBytes()};assertArrayEquals(file.readBytes(),bytes);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));assertEquals("playing",SdkMedia.status(o).getString("state"));assertNull(SdkMedia::class.java.getDeclaredField("source").apply{isAccessible=true}.get(SdkMedia));val before=player();SdkMedia.networkRecovered();assertSame(before,player());AppSdk.call(c,JSONObject().put("action","permissions").put("id",o.getString("id")).put("grants",JSONArray().put("storage.private")));shadowOf(Looper.getMainLooper()).idle();assertEquals("idle",SdkMedia.status(o).getString("state"))}finally{service.destroy()}}
 @Test fun remoteHandoffPreservesPositionAndDoesNotResumePausedPlayback(){val o=owner();val service=start(remote(o));try{shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));SdkMedia.controlFor(o,"seek",JSONObject().put("position_ms",15000));val before=player();SdkMedia.networkRecovered();assertNotSame(before,player());shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));assertTrue(SdkMedia.status(o).getLong("position_ms") in 15000..15500);SdkMedia.controlFor(o,"pause");val paused=player();SdkMedia.networkRecovered();assertSame(paused,player());assertEquals("paused",SdkMedia.status(o).getString("state"))}finally{service.destroy()}}
 @Test fun failedHandoffGetsOneRetryAndRepeatedFailureDoesNotLoop(){val o=owner();validated();val service=start(remote(o),5000);try{SdkMedia.networkRecovered();val first=player()!!;shadowOf(first).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN,-1004);assertEquals("error",SdkMedia.status(o).getString("state"));shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1010));val second=player()!!;assertNotSame(first,second);shadowOf(second).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN,-1004);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2010));assertNull(player());assertEquals("error",SdkMedia.status(o).getString("state"))}finally{service.destroy()}}
 @Test fun lostRouteCallbacksAreDiscardedAndCloseIsIdempotent(){val cm=c.getSystemService(ConnectivityManager::class.java);val shadow=shadowOf(cm);val before=shadow.networkCallbacks.toSet();var events=0;val recovery=DefaultNetworkRecovery(c){events++};val cb=(shadow.networkCallbacks.toSet()-before).single();val wifi=ShadowNetwork.newInstance(71);val cell=ShadowNetwork.newInstance(72);val caps=ShadowNetworkCapabilities.newInstance();shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);cb.onCapabilitiesChanged(wifi,caps);cb.onLost(wifi);shadowOf(Looper.getMainLooper()).idle();assertEquals(0,events);cb.onCapabilitiesChanged(wifi,caps);cb.onCapabilitiesChanged(cell,caps);shadowOf(Looper.getMainLooper()).idle();assertEquals(1,events);recovery.close();recovery.close();assertEquals(before,shadow.networkCallbacks.toSet())}
 @Test fun initialValidatedRouteDoesNotRestartThePlayer(){
  validated();val cm=c.getSystemService(ConnectivityManager::class.java);val shadow=shadowOf(cm);val before=shadow.networkCallbacks.toSet();var events=0
  val recovery=DefaultNetworkRecovery(c){events++};try{val cb=(shadow.networkCallbacks.toSet()-before).single();val active=requireNotNull(cm.activeNetwork);cb.onCapabilitiesChanged(active,requireNotNull(cm.getNetworkCapabilities(active)));shadowOf(Looper.getMainLooper()).idle();assertEquals(0,events)}finally{recovery.close()}
 }

 @Test(timeout=15000) fun streamedEpisodeProbeRunsOffMainAndFailureBecomesPlayerError(){
  val o=owner();val request=okhttp3.Request.Builder().url("https://audio.example/episode.mp3").build();val began=java.util.concurrent.CountDownLatch(1)
  SdkMedia.openSource={_,_,_->SdkMediaSources.Remote(JSONObject(),{},{request},{},probeFirst=true,connect={r->SdkServiceCall(r){assertNotSame(Looper.getMainLooper().thread,Thread.currentThread());began.countDown();throw java.io.IOException("No network")}})}
  ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(60000,0)}
  SdkPodcasts.start(c,remote(o)){};val service=Robolectric.buildService(SdkPlaybackService::class.java).create()
  try{service.get().onStartCommand(shadowOf(c).nextStartedService,0,1);assertTrue(began.await(2,java.util.concurrent.TimeUnit.SECONDS));repeat(30){shadowOf(Looper.getMainLooper()).idle();if(SdkMedia.status(o).optString("state")=="error")return@repeat;Thread.sleep(10)};assertEquals("error",SdkMedia.status(o).getString("state"));assertTrue(SdkMedia.status(o).getString("error").contains("episode"))}finally{service.destroy()}
 }
 @Test fun aFailingSourceCloseCannotCrashStationReplacementOrStop(){
  val o=owner();var closed=0
  SdkMedia.openSource={_,_,_->object:android.media.MediaDataSource(){override fun getSize()=60000L;override fun readAt(at:Long,b:ByteArray,off:Int,n:Int)=-1;override fun close(){closed++;throw java.io.IOException("Closed socket")}}}
  ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(60000,0)}
  SdkPodcasts.start(c,remote(o)){};val service=Robolectric.buildService(SdkPlaybackService::class.java).create()
  try{service.get().onStartCommand(shadowOf(c).nextStartedService,0,1);SdkMedia.stop();assertEquals(1,closed);assertEquals("idle",SdkMedia.status(o).getString("state"));shadowOf(Looper.getMainLooper()).idle()}finally{service.destroy()}
 }

}
