package app.onklav.home
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import okhttp3.*
import java.util.concurrent.TimeUnit
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class SdkEbooksLiveTest {
 @Test(timeout=90000)fun realGutenbergSearchDownloadAndRustReaderRequireNoAccount()=runBlocking<Unit>{
  assumeTrue("Explicit live acceptance only",System.getenv("ONKLAV_LIVE_EBOOKS")=="1")
  val c=ApplicationProvider.getApplicationContext<Application>();AppSdk.bootstrap(c)
  fun call(j:JSONObject)=AppSdk.sync(c,j)as JSONObject
  val s=call(JSONObject().put("action","launch").put("id","app.onklav.ebooks")).getString("session")
  fun event(a:String,v:String="")=call(JSONObject().put("action","event").put("session",s).put("event",JSONObject().put("action",a).put("value",v)))
  fun auth(p:JSONObject)=call(JSONObject().put("action","authorize").put("session",s).put("token",p.getString("token")))
  fun reply(p:JSONObject,v:JSONObject)=call(JSONObject().put("action","reply").put("session",s).put("token",p.getString("token")).put("reply",JSONObject().put("ok",true).put("value",v)))
  // This build environment requires a localhost HTTPS proxy. Its transport uses
  // system DNS; the Android production client deliberately rejects loopback DNS.
  // Fetch one real OPDS page into the production cache, then use the same proxy
  // transport seam for the selected EPUB. Do not claim device DNS qualification.
  val proxyHost=System.getProperty("https.proxyHost");val proxyPort=System.getProperty("https.proxyPort")?.toIntOrNull()
  val proxy=if(proxyHost!=null&&proxyPort!=null)java.net.Proxy(java.net.Proxy.Type.HTTP,java.net.InetSocketAddress(proxyHost,proxyPort))else java.net.Proxy.NO_PROXY
  val transport=OkHttpClient.Builder().proxy(proxy).followRedirects(false).followSslRedirects(false).connectTimeout(12,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
  val target="https://www.gutenberg.org/ebooks/search.opds/?query=alice&start_index=1"
  val request=Request.Builder().url(target).header("User-Agent","OnKlav-eBooks/1.0 (+https://onklav.app)").build()
  val bytes=transport.newCall(request).execute().use{r->assertTrue(r.isSuccessful);SdkEbooks.bounded(r,1048576){}}
  val cache=File(c.cacheDir,"ebook-opds");cache.mkdirs();val name=java.security.MessageDigest.getInstance("SHA-256").digest(target.toByteArray()).joinToString(""){"%02x".format(it)};File(cache,name).writeBytes(bytes)
  try{event("book.discover");event("book.query","alice");val p=event("book.search").getJSONObject("pending");val page=SdkEbooks.search(c,p.getString("token")){auth(p)};assertTrue(page.getJSONArray("items").length()>0);reply(p,page)
   val detail=event("book.preview.11").getJSONObject("pending");reply(detail,SdkEbooks.search(c,detail.getString("token")){auth(detail)});val pick=event("book.acquire.11").getJSONObject("pending");val saved=SdkEbooks.acquire(c,pick.getString("token"),{auth(pick)},{op,a->call(JSONObject().put("action","blob_host").put("session",s).put("token",pick.getString("token")).put("operation",op).put("args",a))},connect=transport::newCall);reply(pick,saved);val f=event("book.read");val state=f.getJSONObject("frame").getJSONObject("state");assertEquals("read",state.getString("view"));assertTrue(state.getJSONObject("active").getString("title").contains("Alice",true));val reader=state.getJSONObject("reader");assertTrue(reader.getJSONObject("book").getJSONArray("chapters").length()>1)
   val report=JSONObject().put("status","passed").put("source","Project Gutenberg HTTPS OPDS and live EPUB acquisition").put("catalog_items",page.getJSONArray("items").length()).put("title",state.getJSONObject("active").getString("title")).put("chapters",reader.getJSONObject("book").getJSONArray("chapters").length()).put("bytes",saved.getLong("size")).put("sha256",saved.getString("sha256")).put("account_or_api_key",false).put("physical_phone",false).put("transport","environment HTTPS proxy via injected transport; live OPDS cache and EPUB; Android default DNS client unverified")
   File(System.getProperty("onklav.project.root"),"docs/evidence/v0.74.0/gutenberg-live.json").writeText(report.toString(2))
  }finally{SdkEbooks.cancelAll();AppSdk.sync(c,JSONObject().put("action","close").put("session",s))}
 }
}
