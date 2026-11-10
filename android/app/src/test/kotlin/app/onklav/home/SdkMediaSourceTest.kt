package app.onklav.home

import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[29])
class SdkMediaSourceTest {
 @Test fun decoderLearnsLargeAudioLengthBeforeReadingWithoutDownloadingWholeFile(){
  val request=Request.Builder().url("https://archive.org/book.mp3").build();var calls=0
  val total=80L*1024*1024
  val source=SdkMediaSources.Remote(JSONObject(),{},{request},{},probeFirst=true,allowUnvalidatedRange=true,connect={r->calls++;SdkServiceCall(r){assertEquals("bytes=0-",r.header("Range"));sdkResponse(r,ByteArray(1),206).newBuilder().body(object:okhttp3.ResponseBody(){override fun contentType():okhttp3.MediaType?=null;override fun contentLength()=total;override fun source()=okio.Buffer().write(byteArrayOf(1))}).header("Content-Range","bytes 0-${total-1}/$total").header("Content-Length",total.toString()).build()}})
  // A real range body reports the whole range length even though only a byte is consumed.
  source.prime();assertEquals(total,source.getSize());assertEquals(1,calls);source.close()
 }
 @Test fun dynamicPodcastDatesDoNotBreakValidRanges(){
  val request=Request.Builder().url("https://publisher.example/audio.mp3").build();var calls=0
  val source=SdkMediaSources.Remote(JSONObject(),{},{request},{},probeFirst=true,allowUnvalidatedRange=true,connect={r->calls++;SdkServiceCall(r){if(calls==1)sdkResponse(r,"0123456789".toByteArray()).newBuilder().header("Last-Modified","Wed, 21 Oct 2015 07:28:00 GMT").build()else {assertNull(r.header("If-Range"));sdkResponse(r,"789".toByteArray(),206).newBuilder().header("Last-Modified","Thu, 22 Oct 2015 07:28:00 GMT").header("Content-Range","bytes 7-9/10").build()}}})
  source.readAt(0,ByteArray(1),0,1);val bytes=ByteArray(3);assertEquals(3,source.readAt(7,bytes,0,3));assertEquals("789",String(bytes));source.close()
 }
 @Test fun podcastTailProbeCanUseRangeWithoutEtag(){
  val request=Request.Builder().url("https://publisher.example/audio.mp3").build();var calls=0
  val source=SdkMediaSources.Remote(JSONObject(),{},{request},{},probeFirst=true,allowUnvalidatedRange=true,connect={r->calls++;SdkServiceCall(r){if(calls==1)sdkResponse(r,"0123456789".toByteArray())else{assertEquals("bytes=7-",r.header("Range"));assertNull(r.header("If-Range"));sdkResponse(r,"789".toByteArray(),206).newBuilder().header("Content-Range","bytes 7-9/10").build()}}})
  val bytes=ByteArray(3);assertEquals(3,source.readAt(7,bytes,0,3));assertEquals("789",String(bytes));assertEquals(2,calls);source.close()
 }
 @Test fun podcastWithoutValidatorsStillRejectsChangedLength(){
  val request=Request.Builder().url("https://publisher.example/audio.mp3").build();var calls=0
  val source=SdkMediaSources.Remote(JSONObject(),{},{request},{},probeFirst=true,allowUnvalidatedRange=true,connect={r->calls++;SdkServiceCall(r){if(calls==1)sdkResponse(r,"0123456789".toByteArray())else sdkResponse(r,"789".toByteArray(),206).newBuilder().header("Content-Range","bytes 7-9/20").build()}})
  source.readAt(0,ByteArray(1),0,1);assertThrows(IllegalStateException::class.java){source.readAt(7,ByteArray(3),0,3)};source.close()
 }
 @Test fun sourceReusesSequentialReadsAndRequiresMatchingRangeForSeeking(){
  var calls=0;var checks=0;val request=Request.Builder().url("https://api.example.com/sample").build()
  val source=SdkMediaSources.Remote(JSONObject(),{checks++},{request},{},connect={r->calls++;SdkServiceCall(r){if(calls==1)sdkResponse(r,"0123456789".toByteArray()).newBuilder().header("ETag","\"one\"").build()else{assertEquals("bytes=7-",r.header("Range"));sdkResponse(r,"789".toByteArray(),206).newBuilder().header("ETag","\"one\"").header("Content-Range","bytes 7-9/10").build()}}})
  val b=ByteArray(3);assertEquals(3,source.readAt(0,b,0,3));assertEquals("012",String(b));assertEquals(3,source.readAt(3,b,0,3));assertEquals(1,calls);assertEquals(3,source.readAt(7,b,0,3));assertEquals("789",String(b));assertEquals(10L,source.getSize());assertEquals(-1,source.readAt(10,b,0,3));assertTrue(checks>=4);source.close()
 }
 @Test fun decoderCannotFollowRedirectsOrContinueAfterPermissionLoss(){
  val request=Request.Builder().url("https://api.example.com/sample").build();var allowed=true;var last:SdkServiceCall?=null
  val source=SdkMediaSources.Remote(JSONObject(),{check(allowed)},{request},{},connect={r->SdkServiceCall(r){sdkResponse(r,"bytes".toByteArray())}.also{last=it}})
  source.readAt(0,ByteArray(1),0,1);allowed=false;assertThrows(IllegalStateException::class.java){source.readAt(1,ByteArray(1),0,1)};source.close();assertTrue(last!!.cancelled)
  val redirected=SdkMediaSources.Remote(JSONObject(),{},{request},{},connect={r->SdkServiceCall(r){sdkResponse(r,byteArrayOf(),302).newBuilder().header("Location","https://private.example/").build()}})
  assertThrows(IllegalStateException::class.java){redirected.readAt(0,ByteArray(1),0,1)};redirected.close()
 }
 @Test fun readChunksAreBoundedAndClosedSourceCannotReopen(){
  val request=Request.Builder().url("https://api.example.com/sample").build();var calls=0
  val source=SdkMediaSources.Remote(JSONObject(),{},{request},{},connect={r->calls++;SdkServiceCall(r){sdkResponse(r,ByteArray(100000){42})}})
  assertTrue(source.readAt(0,ByteArray(100000),0,100000)<=65536);source.close();assertThrows(IllegalStateException::class.java){source.readAt(0,ByteArray(1),0,1)};assertEquals(1,calls)
 }
 @Test fun audiobookCanProbeTailFirstAndSeekUsingLastModified(){
  val request=Request.Builder().url("https://archive.org/book.mp3").build();val modified="Wed, 21 Oct 2015 07:28:00 GMT";var calls=0
  val source=SdkMediaSources.Remote(JSONObject(),{},{request},{},probeFirst=true,connect={r->calls++;SdkServiceCall(r){if(calls==1){assertEquals("bytes=0-",r.header("Range"));sdkResponse(r,"0123456789".toByteArray()).newBuilder().header("Last-Modified",modified).build()}else{assertEquals("bytes=7-",r.header("Range"));assertEquals(modified,r.header("If-Range"));sdkResponse(r,"789".toByteArray(),206).newBuilder().header("Last-Modified",modified).header("Content-Range","bytes 7-9/10").build()}}})
  val bytes=ByteArray(3);assertEquals(3,source.readAt(7,bytes,0,3));assertEquals("789",String(bytes));assertEquals(2,calls);source.close()
 }
 @Test fun audiobookRejectsChangedDateOrLengthAcrossSeek(){
  val request=Request.Builder().url("https://archive.org/book.mp3").build();var calls=0
  val source=SdkMediaSources.Remote(JSONObject(),{},{request},{},probeFirst=true,connect={r->calls++;SdkServiceCall(r){if(calls==1)sdkResponse(r,"0123456789".toByteArray()).newBuilder().header("Last-Modified","Wed, 21 Oct 2015 07:28:00 GMT").build()else sdkResponse(r,"789".toByteArray(),206).newBuilder().header("Last-Modified","Thu, 22 Oct 2015 07:28:00 GMT").header("Content-Range","bytes 7-9/10").build()}})
  source.readAt(0,ByteArray(1),0,1);assertThrows(IllegalStateException::class.java){source.readAt(7,ByteArray(3),0,3)};source.close()
 }

}
