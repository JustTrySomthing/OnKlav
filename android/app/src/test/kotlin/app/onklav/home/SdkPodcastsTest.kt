package app.onklav.home
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class SdkPodcastsTest {
 private fun rss(items:String)="<rss version='2.0' xmlns:itunes='http://www.itunes.com/dtds/podcast-1.0.dtd'><channel><title>Public &amp; free</title>$items</channel></rss>".toByteArray()
 @Test fun podcastAudioAllowsLongTrackingChainsButRejectsPrivateRedirects(){
  var calls=0
  val url=SdkPodcasts.resolveAudio("redirect-fixture","https://publisher.example/0",{},lookup={},connect={r->SdkServiceCall(r){calls++;if(calls<8)sdkResponse(r,byteArrayOf(),302).newBuilder().header("Location","http://publisher.example/$calls").build()else sdkResponse(r,byteArrayOf()).newBuilder().header("Content-Type","audio/mpeg").build()}})
  assertEquals(8,calls);assertEquals("https://publisher.example/7",url)
  assertThrows(Exception::class.java){SdkPodcasts.resolveAudio("bad-redirect","https://publisher.example/audio",{},lookup={},connect={r->SdkServiceCall(r){sdkResponse(r,byteArrayOf(),302).newBuilder().header("Location","https://127.0.0.1/audio").build()}})}
 }
 @Test fun publicSearchUsesPublisherFeedsAndFiltersUnsafeResults(){
  val rows=SdkPodcasts.appleRows(JSONObject("""{"results":[{"collectionName":"Popular show","artistName":"Creator","feedUrl":"https://publisher.example/rss"},{"collectionName":"Duplicate","feedUrl":"https://publisher.example/rss"},{"collectionName":"Private","feedUrl":"https://127.0.0.1/rss"},{"collectionName":"No feed"}]}"""))
  assertEquals(1,rows.length());assertEquals("Popular show",rows.getJSONObject(0).getString("title"));assertEquals("https://publisher.example/rss",rows.getJSONObject(0).getString("feed"))
 }
 @Test fun longFeedsArePagedAndMissingEnclosureLengthsDoNotHideEpisodes(){
  val value=SdkPodcasts.parse(rss((0..44).joinToString(""){i->"<item><title>Episode $i</title><itunes:duration>12:30</itunes:duration><enclosure url='https://audio.example.org/$i.mp3' type='audio/mpeg'/></item>"}))
  assertEquals("Public & free",value.getString("title"));assertEquals(45,value.getJSONArray("items").length());val first=SdkPodcasts.page(value,0);assertEquals(20,first.getJSONArray("items").length());assertEquals(20,first.getInt("next"));assertFalse(first.toString().contains("audio.example.org"));val last=SdkPodcasts.page(value,40);assertEquals(5,last.getJSONArray("items").length());assertTrue(last.isNull("next"));assertEquals("12:30",last.getJSONArray("items").getJSONObject(0).getString("duration"))
 }
 @Test fun unsafeFeedsAndEnclosuresAreRejectedWithoutMarkupOrDuplicates(){
  assertThrows(Exception::class.java){SdkPodcasts.parse("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///secret'>]><rss version='2.0'><channel><title>&x;</title></channel></rss>".toByteArray())}
  for(u in listOf("http://example.org/rss","https://user:pass@example.org/rss","https://127.0.0.1/rss","https://localhost/rss"))assertThrows(Exception::class.java){SdkPodcasts.publicUrl(u)}
  val value=SdkPodcasts.parse(rss("<item><title>Bad</title><enclosure url='http://127.0.0.1/a.mp3' type='audio/mpeg'/></item>"+"<item><title><![CDATA[Good <b>audio</b>]]></title><enclosure url='https://example.org/a.mp3' type='audio/mpeg'/></item>".repeat(2)))
  assertEquals(1,value.getJSONArray("items").length());assertFalse(value.getJSONArray("items").getJSONObject(0).getString("title").contains("<b>"))
 }
 @Test fun localDirectoryHasNoNetworkCallOrSearchUsageCounter(){val rows=JSONArray((0..44).map{i->mapOf("title" to "Science $i","author" to "Creator","feed" to "https://example.org/$i/rss","language" to "en")});repeat(100){val result=SdkPodcasts.directory(rows,JSONObject().put("query","science").put("language","en").put("offset",40));assertEquals(5,result.getJSONArray("items").length());assertEquals(45,result.getInt("total"));assertTrue(result.isNull("next"))};assertEquals(0,SdkPodcasts.directory(rows,JSONObject().put("query","missing")).getInt("total"))}
}
