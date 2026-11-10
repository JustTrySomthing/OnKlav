package app.onklav.home

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractResponse
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import java.util.concurrent.TimeUnit

/** URL-based extraction, independent of the WebView's chosen playback quality. */
internal object YoutubeResolver {
 data class Result(val title:String,val streams:List<YoutubeStream>) {
  val heights:List<Int> get()=streams.filter{it.height>0&&(it.audio||streams.any{a->a.audio&&a.height==0})}.map{it.height}.distinct().sorted()
  fun select(audio:Boolean,height:Int):Pair<YoutubeStream,YoutubeStream?> {
   val sound=streams.filter{it.audio}.sortedWith(compareBy<YoutubeStream>{it.height!=0}.thenBy{it.mime!="audio/mp4"}).firstOrNull()
   if(audio)return requireNotNull(sound){"This video has no downloadable audio"} to null
   val video=streams.filter{it.height==height}.sortedBy{it.mime!="video/mp4"}.firstOrNull()?:error("This quality is no longer available. Reopen Download.")
   return video to if(video.audio)null else requireNotNull(sound){"This video has no downloadable audio track"}
  }
 }
 private val mutex=Mutex()
 private val gate=ThreadLocal<()->Unit>()
 private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(20,TimeUnit.SECONDS).dns(object:Dns{override fun lookup(hostname:String)=SdkFeedService.publicAddresses(hostname)}).build()
 private fun check(){if(Thread.currentThread().isInterrupted)throw java.io.InterruptedIOException();gate.get()?.invoke()?:error("Extraction session closed")}
 private fun address(raw:String):HttpUrl=raw.toHttpUrl().also{u->require(u.scheme=="https"&&u.port==443&&u.username.isEmpty()&&u.password.isEmpty()&&listOf("youtube.com","youtube-nocookie.com","googlevideo.com","google.com","googleapis.com","ytimg.com").any{u.host==it||u.host.endsWith(".$it")}){"Unsupported extraction address"}}
 init {NewPipe.init(object:Downloader(){override fun execute(request:ExtractRequest):ExtractResponse {
  var url=address(request.url())
  repeat(6){check();val body=request.dataToSend()?.toRequestBody();val b=Request.Builder().url(url).method(request.httpMethod(),body).header("User-Agent","Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36")
   request.headers().forEach{(k,values)->if(!k.equals("Authorization",true)){b.removeHeader(k);values.forEach{b.addHeader(k,it)}}}
   val call=client.newCall(b.build());try{call.execute().use{r->check();if(r.code in listOf(301,302,303,307,308)){url=address(url.resolve(r.header("Location")?:error("Missing redirect"))?.toString()?:error("Invalid redirect"));return@use}
    val out=java.io.ByteArrayOutputStream();r.body?.byteStream()?.use{input->val bytes=ByteArray(65536);while(true){check();val n=input.read(bytes);if(n<0)break;require(out.size()+n<=8*1024*1024){"Video information is too large"};out.write(bytes,0,n)}}
    return ExtractResponse(r.code,r.message,r.headers.toMultimap(),out.toString("UTF-8"),url.toString())
   }}finally{call.cancel()}
  };error("Too many extraction redirects")
 }})}
 suspend fun resolve(id:String,verify:()->Unit):Result=withTimeout(60000){mutex.withLock{runInterruptible(Dispatchers.IO){
  require(id.matches(Regex("[A-Za-z0-9_-]{11}"))){"Open a YouTube video first"};gate.set(verify)
  try {check();val extractor=ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$id");extractor.fetchPage();check()
   fun row(s:Stream,height:Int,audio:Boolean):YoutubeStream? {if(!s.isUrl||s.deliveryMethod!=DeliveryMethod.PROGRESSIVE_HTTP)return null;val url=runCatching{YoutubeStreams.complete(s.content)}.getOrNull()?:return null;val mime=s.format?.mimeType?:return null;if(mime !in listOf("video/mp4","video/webm","audio/mp4","audio/webm"))return null;return YoutubeStream(s.id.toIntOrNull()?:1,url,mime,height,audio)}
   val rows=extractor.audioStreams.mapNotNull{row(it,0,true)}+extractor.videoStreams.mapNotNull{row(it,it.resolution.takeWhile(Char::isDigit).toIntOrNull()?:it.height,true)}+extractor.videoOnlyStreams.mapNotNull{row(it,it.resolution.takeWhile(Char::isDigit).toIntOrNull()?:it.height,false)}
   require(rows.isNotEmpty()){"This video does not offer downloadable files"};Result(extractor.name,rows)
  }finally{gate.remove()}
 }}}
}
