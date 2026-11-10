package app.onklav.home

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SdkFeedServiceTest {
    @Test fun boundedReadsStopEmptyProvidersOversizedBodiesAndRevocation()=runBlocking {
        var reads=0
        val empty=object:java.io.InputStream(){override fun read()=0;override fun read(b:ByteArray,off:Int,len:Int):Int{reads++;return 0}}
        assertTrue(runCatching{SdkFeedService.readBounded(empty){}}.isFailure);assertEquals(4,reads)
        assertTrue(runCatching{SdkFeedService.readBounded(java.io.ByteArrayInputStream(ByteArray(2*1024*1024+1))) {}}.isFailure)
        var checks=0
        assertTrue(runCatching{SdkFeedService.readBounded(java.io.ByteArrayInputStream(ByteArray(20000))){if(++checks==2)throw CancellationException("Revoked")}}.exceptionOrNull()is CancellationException)
        assertEquals("feed",String(SdkFeedService.readBounded(java.io.ByteArrayInputStream("feed".toByteArray())){}))
    }
    @Test fun rssAndAtomKeepPlainTextAndResolveArticleLinks() {
        val rss="""<rss version="2.0"><channel><title>Example</title><item><title>One &amp; two</title><link>/story</link><description><![CDATA[<p>Body <b>text</b></p>]]></description><pubDate>Wed, 21 Oct 2026 12:00:00 +0000</pubDate></item></channel></rss>"""
        val parsed=SdkFeedService.parse(rss.toByteArray(),"https://example.org/feed")
        assertEquals("Example",parsed.getString("title"))
        val item=parsed.getJSONArray("items").getJSONObject(0)
        assertEquals("One & two",item.getString("title"));assertEquals("https://example.org/story",item.getString("url"));assertEquals("Body text",item.getString("body"));assertTrue(item.getLong("published")>0)
        val atom="""<feed xmlns="http://www.w3.org/2005/Atom"><title>Atom</title><entry><title>Atom story</title><link href="https://example.org/alternate" rel="alternate"/><content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>Nested body</p></div></content><updated>2026-10-21T10:00:00Z</updated></entry></feed>"""
        val entry=SdkFeedService.parse(atom.toByteArray(),"https://example.org/feed").getJSONArray("items").getJSONObject(0)
        assertEquals("Nested body",entry.getString("body"));assertEquals("https://example.org/alternate",entry.getString("url"))
    }
    @Test fun hostileXmlAndNonPublicSourcesAreRejected() {
        for(raw in listOf("file:///etc/passwd","content://private/x","http://example.org/feed","https://user:password@example.org/feed","https://localhost/feed","https://example.org:8443/feed")) assertTrue(raw,runCatching { SdkFeedService.address(raw) }.isFailure)
        for(host in listOf("127.0.0.1","10.1.1.1","169.254.169.254","100.64.0.1","198.18.0.1","::1","fc00::1")) assertTrue(host,runCatching { SdkFeedService.publicAddresses(host) }.isFailure)
        val entity="""<!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><title>&x;</title></channel></rss>"""
        assertTrue(runCatching { SdkFeedService.parse(entity.toByteArray(),"https://example.org/feed") }.isFailure)
        assertTrue(runCatching { SdkFeedService.parse(ByteArray(2*1024*1024+1),"https://example.org/feed") }.isFailure)
        val deep="<rss>"+"<a>".repeat(60)+"x"+"</a>".repeat(60)+"</rss>"
        assertTrue(runCatching { SdkFeedService.parse(deep.toByteArray(),"https://example.org/feed") }.isFailure)
    }
}
