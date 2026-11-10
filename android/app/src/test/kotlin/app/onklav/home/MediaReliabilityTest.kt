package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class MediaReliabilityTest {
 @Test fun largePodcastWithoutRangesUsesOneDiskCacheForRepeatedSeeks(){
  val c=ApplicationProvider.getApplicationContext<Context>();val file=File.createTempFile("seek-test-",".tmp",c.cacheDir);val body=ByteArray(40*1024*1024){(it%251).toByte()};var calls=0
  val request=Request.Builder().url("https://podcast.example/episode.mp3").build()
  val source=SdkMediaSources.Remote(JSONObject(),{},{request},{},probeFirst=true,allowUnvalidatedRange=true,sequentialFile={file},connect={r->calls++;SdkServiceCall(r){sdkResponse(r,body).newBuilder().header("ETag","\"same\"").build()}})
  source.prime();assertEquals(body.size.toLong(),source.getSize());val b=ByteArray(12);val at=35L*1024*1024;assertEquals(12,source.readAt(at,b,0,12));assertArrayEquals(body.copyOfRange(at.toInt(),at.toInt()+12),b);assertEquals(12,source.readAt(0,b,0,12));assertArrayEquals(body.copyOfRange(0,12),b);assertEquals(2,calls);source.close();assertFalse(file.exists())
 }
 @Test fun boundedHttpRangeSegmentsContinueInsteadOfEndingThePodcastEarly(){
  var calls=0;val request=Request.Builder().url("https://podcast.example/episode.mp3").build()
  val source=SdkMediaSources.Remote(JSONObject(),{},{request},{},probeFirst=true,allowUnvalidatedRange=true,connect={r->calls++;SdkServiceCall(r){val start=if(calls==1)0 else 4;assertEquals("bytes=$start-",r.header("Range"));sdkResponse(r,if(start==0)"0123".toByteArray()else"4567".toByteArray(),206).newBuilder().header("Content-Range","bytes $start-${start+3}/8").build()}})
  val b=ByteArray(4);assertEquals(4,source.readAt(0,b,0,4));assertEquals("0123",String(b));assertEquals(4,source.readAt(4,b,0,4));assertEquals("4567",String(b));assertEquals(2,calls);source.close()
 }
 @Test fun validOggAndM4aEnclosuresAreNotDiscardedByMimeFiltering(){
  for(mime in listOf("application/ogg","audio/m4a","audio/x-mp3","video/mp4")){
   val xml="<rss version='2.0'><channel><title>Show</title><item><title>Episode</title><enclosure url='https://podcast.example/audio' type='$mime'/></item></channel></rss>"
   assertEquals(mime,1,SdkPodcasts.parse(xml.toByteArray()).getJSONArray("items").length())
  }
 }
 @Test fun podcastTransportAcceptsPublicTlsPortsAndSafelyUpgradesExplicitHttpPort(){
  assertEquals("https://publisher.example/audio",SdkPodcasts.secureUrl("http://publisher.example:80/audio"));assertEquals("https://publisher.example:8443/audio",SdkPodcasts.publicUrl("https://publisher.example:8443/audio"));assertThrows(Exception::class.java){SdkPodcasts.publicUrl("https://127.0.0.1:8443/audio")}
 }
 @Test fun confirmedRadioFailureCanBeHiddenAndRetriedWithoutDeletingFavorites(){
  val c=ApplicationProvider.getApplicationContext<Context>();val id="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";SdkRadio.retryUnavailable(c);assertFalse(SdkRadio.hidden(id));SdkRadio.hide(c,id);assertTrue(SdkRadio.hidden(id));assertTrue(SdkRadio.unavailable.value[id]!!>System.currentTimeMillis());SdkRadio.retryUnavailable(c);assertFalse(SdkRadio.hidden(id))
 }
}
