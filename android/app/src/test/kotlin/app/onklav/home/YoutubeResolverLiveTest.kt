package app.onklav.home

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Explicit opt-in live provider check. A provider/network rejection is not a pass. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class YoutubeResolverLiveTest {
 @Test(timeout=75000) fun resolvesPublicVideoWithoutStartingTheWebPlayer()=runBlocking {
  assumeTrue(System.getenv("ONKLAV_LIVE_YOUTUBE")=="1")
  val result=YoutubeResolver.resolve("aqz-KE-bpKQ"){}
  println("Extracted ${result.streams.size} tracks, heights ${result.heights}")
  System.getProperty("onklav.liveStreamsPath")?.let{path->java.io.File(path).writeText(org.json.JSONArray(result.streams.map{org.json.JSONObject().put("itag",it.itag).put("url",it.url).put("mime",it.mime).put("height",it.height)}).toString())}
  assertTrue(result.title.isNotBlank());assertTrue(result.heights.isNotEmpty())
  assertTrue(result.select(true,0).first.audio)
  val client=okhttp3.OkHttpClient.Builder().connectTimeout(10,java.util.concurrent.TimeUnit.SECONDS).readTimeout(10,java.util.concurrent.TimeUnit.SECONDS).followRedirects(false).build()
  for(stream in listOf(result.select(true,0).first,result.select(false,result.heights.first()).first)){
   val req=okhttp3.Request.Builder().url(stream.url).header("Range","bytes=0-1023").build()
   client.newCall(req).execute().use{r->assertTrue("Media response ${r.code}",r.code==200||r.code==206);val body=requireNotNull(r.body);println("Media ${stream.itag}: HTTP ${r.code}, ${body.contentType()}");assertTrue("Unexpected media type: ${body.contentType()}",body.contentType().toString().let{it.startsWith("audio/")||it.startsWith("video/")||it.startsWith("application/octet-stream")});assertTrue("Empty media response",body.byteStream().read(ByteArray(1024))>0)}
  }
  println("Live URL extraction: ${result.streams.size} tracks; heights ${result.heights}")
 }
}
