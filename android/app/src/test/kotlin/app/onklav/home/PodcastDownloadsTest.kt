package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class PodcastDownloadsTest {
 private val c get()=ApplicationProvider.getApplicationContext<Context>()
 private val owner=JSONObject().put("id","test.downloads").put("publisher","a".repeat(64)).put("hash","b".repeat(64)).put("name","Test")
 private val feed="https://publisher.example/rss"
 private val episode=SdkPodcasts.id("https://audio.example/episode.mp3")
 private val root get()=File(c.filesDir,"podcast-downloads/"+SdkPodcasts.id("test.downloads|"+"a".repeat(64)))
 private fun request(service:String="podcasts.download")=JSONObject().put("service",service).put("owner",owner).put("args",JSONObject().put("feed",feed).put("episode",episode))
 private fun seed(){
  root.deleteRecursively()
  val row=JSONObject().put("id",episode).put("title","Large episode").put("url","https://audio.example/episode.mp3")
  val data=JSONObject().put("title","Show").put("items",JSONArray().put(row))
  val type=SdkPodcasts::class.java.declaredClasses.single{it.simpleName=="Feed"}
  val cached=type.declaredConstructors.first().apply{isAccessible=true}.newInstance(android.os.SystemClock.elapsedRealtime(),data)
  val field=SdkPodcasts::class.java.getDeclaredField("feeds").apply{isAccessible=true}
  @Suppress("UNCHECKED_CAST") val feeds=field.get(SdkPodcasts) as MutableMap<String,Any>
  synchronized(feeds){feeds["test.downloads|"+"a".repeat(64)+"|"+"b".repeat(64)+"|"+feed]=cached}
 }
 @Test fun largeEpisodeCommitsAndOfflinePreparationUsesDiskAfterFeedCacheCleared()=runBlocking{
  seed();val bytes=ByteArray(21*1024*1024){(it%251).toByte()};val p=request();var sawProgress=false
  val result=SdkPodcasts.download(c,"large-test",lookup={},connect={r->SdkServiceCall(r){sdkResponse(r,bytes).newBuilder().header("Content-Type","audio/mpeg").build()}}){if(SdkPodcasts.transfers.value[SdkPodcasts.transferKey("test.downloads",episode)]?.let{it.state=="downloading"&&it.bytes>0}==true)sawProgress=true;p}
  assertTrue("No in-flight byte progress",sawProgress)
  val status=SdkPodcasts.transfers.value[SdkPodcasts.transferKey("test.downloads",episode)]!!;assertEquals("complete",status.state);assertEquals(bytes.size.toLong(),status.bytes);assertEquals(status.bytes,status.total);assertEquals(episode,result.getJSONArray("downloads").getString(0));assertEquals(bytes.size.toLong(),File(root,"$episode.audio").length());assertFalse(File(root,"$episode.part").exists())
  SdkPodcasts.clearApp("test.downloads")
  val offline=SdkPodcasts.prepare(c,"offline-test"){request("podcasts.play")}
  assertNotNull(offline.local);assertTrue(offline.request.getJSONObject("args").getJSONArray("queue").getJSONObject(0).getBoolean("podcast_local"));assertEquals("Large episode",offline.episode.getString("title"))
  assertTrue(SdkPodcasts.downloads(c,owner).toString().contains(episode));val other=JSONObject(owner.toString()).put("publisher","c".repeat(64));assertEquals(0,SdkPodcasts.downloads(c,other).getJSONArray("downloads").length())
  SdkPodcasts.delete(c,"delete-test"){request("podcasts.delete")};assertFalse(File(root,"$episode.audio").exists());assertEquals(0,SdkPodcasts.downloads(c,owner).getJSONArray("downloads").length())
 }
 @Test fun incompleteOrCancelledDownloadsNeverBecomePlayable()=runBlocking{
  seed();val p=request();var checks=0
  val failure=runCatching{SdkPodcasts.download(c,"cancel-test",lookup={},connect={r->SdkServiceCall(r){sdkResponse(r,ByteArray(1024*1024)).newBuilder().header("Content-Type","audio/mpeg").build()}}){checks++;check(checks<10){"Permission revoked"};p}}
  assertTrue(failure.isFailure);assertEquals("error",SdkPodcasts.transfers.value[SdkPodcasts.transferKey("test.downloads",episode)]!!.state);assertFalse(File(root,"$episode.audio").exists());assertFalse(File(root,"$episode.part").exists());assertEquals(0,SdkPodcasts.downloads(c,owner).getJSONArray("downloads").length())
  File(root,"$episode.audio").writeBytes(ByteArray(1));File(root,"$episode.json").writeText(JSONObject().put("bytes",200).toString());assertEquals(0,SdkPodcasts.downloads(c,owner).getJSONArray("downloads").length())
 }
}
