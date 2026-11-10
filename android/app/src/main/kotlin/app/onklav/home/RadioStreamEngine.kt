package app.onklav.home

import android.content.Context
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.datasource.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.*
import java.io.*
import java.net.URI
import java.util.concurrent.TimeUnit

/** Live transport owns a real streaming extractor (including ICY), never a seekable book source. */
internal interface RadioStreamEngine {
 val audioSession:Int
 val positionMs:Long? get()=null
 fun prepare();fun play();fun pause();fun release()
 /** Transfer a selected, already-open HTTP stream; test/alternative engines close it by default. */
 fun adopt(connection:RadioConnection?){connection?.close()}
 interface Events {fun ready();fun playing(value:Boolean);fun ended();fun failed(error:Exception)}
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class ExoRadioStream(c:Context,url:String,verify:()->Unit,private val events:RadioStreamEngine.Events):RadioStreamEngine {
 private val sources=RadioSources(verify)
 private val engine=ExoPlayer.Builder(c).setMediaSourceFactory(DefaultMediaSourceFactory(DataSource.Factory{sources.source()})).build()
 override fun adopt(connection:RadioConnection?){sources.adopt(connection)}
 private var closed=false
 override val audioSession get()=engine.audioSessionId
 override val positionMs get()=engine.currentPosition
 init{
  engine.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),false)
  engine.setWakeMode(C.WAKE_MODE_NETWORK)
  engine.addListener(object:Player.Listener{
   override fun onPlaybackStateChanged(state:Int){if(closed)return;when(state){Player.STATE_READY->events.ready();Player.STATE_BUFFERING->events.playing(false);Player.STATE_ENDED->events.ended()}}
   override fun onIsPlayingChanged(isPlaying:Boolean){if(!closed)events.playing(isPlaying)}
   override fun onPlayerError(error:PlaybackException){if(!closed)events.failed(error)}
  })
  engine.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(RadioMimeHints.mime(url)).build())
 }
 override fun prepare(){engine.prepare()}
 override fun play(){engine.play()}
 override fun pause(){engine.pause()}
 override fun release(){closed=true;sources.close();engine.release()}
}

/** Actively close HTTP sockets before releasing ExoPlayer; loader interruption alone
 * does not unblock every socket read, and release() may return after its timeout. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class RadioSources(private val verify:()->Unit):Closeable {
 private val gate=Any();private var closed=false;private var pending:RadioConnection?=null
 private val active=mutableSetOf<RadioHttpSource>()
 fun adopt(value:RadioConnection?){val old=synchronized(gate){if(closed){value?.close();return};val old=pending;pending=value;old};old?.close()}
 fun source():RadioHttpSource=synchronized(gate){check(!closed){"Playback closed"};val selected=pending;pending=null
  RadioHttpSource(verify,selected).also{s->
   s.onOpen={synchronized(gate){check(!closed){"Playback closed"};active.add(s)};Unit}
   s.onClose={synchronized(gate){active.remove(s)};Unit};active.add(s)
  }
 }
 override fun close(){val work=synchronized(gate){if(closed)return;closed=true;val work=pending to active.toList();pending=null;active.clear();work};work.first?.close();work.second.forEach{it.close()}}
}

/** One-shot ownership of a pre-opened live stream. Unclaimed/cancelled work expires. */
internal class RadioConnection(val url:String,private val call:Call,private val response:Response):Closeable {
 private val handler=android.os.Handler(android.os.Looper.getMainLooper())
 private var state=0 // pending / taken / closed
 private val expiry=Runnable{close()}
 init{handler.postDelayed(expiry,15000)}
 @Synchronized fun take():Pair<Call,Response>? {if(state!=0)return null;if(call.isCanceled()){close();return null};state=1;handler.removeCallbacks(expiry);return call to response}
 override fun close(){val dispose=synchronized(this){if(state!=0)false else{state=2;handler.removeCallbacks(expiry);true}};if(dispose){call.cancel();runCatching{response.close()}}}
}

/** Every redirect/playlist segment is public HTTP(S) and the root capability is checked on every read.
 * No credentials/cookies, private addresses, file schemes or decoder-owned network access. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class RadioHttpSource(private val verify:()->Unit,private var prepared:RadioConnection?=null,private val connect:(Request)->Call={client.newCall(it)}):BaseDataSource(true){
 companion object{internal fun networkChanged(){client.connectionPool.evictAll()};private val client=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(18,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).dns(object:Dns{override fun lookup(host:String)=SdkFeedService.publicAddresses(host)}).build()}
 internal var onOpen:()->Unit={}
 internal var onClose:()->Unit={}
 private val ioLock=Any()
 @Volatile private var call:Call?=null
 @Volatile private var closed=false
 private var response:Response?=null;private var input:InputStream?=null;private var uri:Uri?=null;private var opened=false;private var remaining=C.LENGTH_UNSET.toLong()
 override fun getUri()=uri
 override fun getResponseHeaders():Map<String,List<String>> = response?.headers?.toMultimap()?:emptyMap()
 override fun open(spec:DataSpec):Long{
  closed=false;transferInitializing(spec);var url=spec.uri.toString();val seen=mutableSetOf<String>()
  try{onOpen();verify();url=SdkRadio.publicUrl(url);repeat(6){verify();check(!closed&&seen.add(url)){"Station redirect loop"}
   val request=Request.Builder().url(url).header("User-Agent","OnKlav-Radio/0.2").header("Accept-Encoding","identity").header("Icy-MetaData","1").apply{if(spec.position>0||spec.length!=C.LENGTH_UNSET.toLong()){header("Range","bytes=${spec.position}-"+if(spec.length!=C.LENGTH_UNSET.toLong())(spec.position+spec.length-1).toString()else "")}}.build()
   val candidate=prepared;prepared=null
   val reusable=if(spec.position==0L&&url==candidate?.url)candidate?.take()else null
   if(reusable==null)candidate?.close()
   val next=reusable?.first?:connect(request);synchronized(ioLock){if(closed){next.cancel();reusable?.second?.close();throw IOException("Playback closed")};call=next};val r=reusable?.second?:next.execute();synchronized(ioLock){if(closed||call!==next){r.close();throw IOException("Playback closed")};response=r}
   if(r.code in setOf(301,302,303,307,308)){url=SdkRadio.publicUrl(URI(url).resolve(r.header("Location")?:throw IOException("Station redirect missing")).toString());r.close();return@repeat}
   if(r.code !in 200..299)throw SdkRadioHealth.http(r.code)
   check(r.header("Content-Encoding","identity").equals("identity",true)){"Unsupported station transfer encoding"}
   val body=r.body?:throw IOException("Empty station response");input=body.byteStream();uri=Uri.parse(url)
   if(spec.position>0&&r.code!=206){check(spec.position<=8*1024*1024){"Station does not support this range"};val scratch=ByteArray(8192);var skip=spec.position;while(skip>0){verify();val n=input!!.read(scratch,0,minOf(skip,scratch.size.toLong()).toInt());if(n<0)throw EOFException();skip-=n}}
   remaining=if(spec.length!=C.LENGTH_UNSET.toLong())spec.length else body.contentLength().let{if(it<0)it else (it-if(r.code==206)0 else spec.position).coerceAtLeast(0)}
   opened=true;transferStarted(spec);return remaining
  };throw IOException("Too many station redirects")
  }catch(e:Exception){close();throw if(e is IOException)e else IOException(e.message,e)}
 }
 override fun read(buffer:ByteArray,offset:Int,length:Int):Int{
  if(length==0)return 0;verify();if(closed)throw IOException("Playback closed");if(remaining==0L)return C.RESULT_END_OF_INPUT
  val n=input?.read(buffer,offset,if(remaining<0)length else minOf(length.toLong(),remaining).toInt())?:throw IOException("Station is not open")
  if(n<0)return C.RESULT_END_OF_INPUT;if(remaining>0)remaining-=n;bytesTransferred(n);return n
 }
 override fun close(){prepared?.close();prepared=null;val closing=synchronized(ioLock){closed=true;val pair=call to response;call=null;response=null;input=null;uri=null;pair};closing.first?.cancel();runCatching{closing.second?.close()};if(opened){opened=false;transferEnded()};onClose()}
}

/** Resolve a small PLS/M3U listing without handing arbitrary schemes to a decoder. */
internal fun radioPlaylistTarget(base:String,text:String):String {
 require(text.length<=65536){"Station playlist is too large"}
 val lines=text.lineSequence().take(256).map{it.trim()}.filter{it.isNotEmpty()&&!it.startsWith("#")&&!it.startsWith("[")&&!Regex("(?i)^(numberofentries|version|title[0-9]+|length[0-9]+)=").containsMatchIn(it)}.map{line->if(line.startsWith("File",true)&&line.contains('='))line.substringAfter('=').trim()else line}
 return lines.take(32).mapNotNull{line->runCatching{SdkRadio.publicUrl(URI(base).resolve(line).toString())}.getOrNull()}.firstOrNull()?:throw IOException("This playlist contains no public HTTP(S) station stream")
}

/** Content-type hints cover HLS URLs without a .m3u8 extension. No URLs enter guest state. */
internal object RadioMimeHints {
 private val hls=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
 fun mime(url:String):String?=if(url in hls)MimeTypes.APPLICATION_M3U8 else null
 fun record(url:String,isHls:Boolean){if(isHls){if(hls.size>=64)hls.clear();hls.add(url)}else hls.remove(url)}
}
