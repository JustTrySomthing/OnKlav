package app.onklav.home

import android.content.Context
import android.media.MediaDataSource
import kotlinx.coroutines.runBlocking
import okhttp3.*
import org.json.JSONObject
import java.io.InputStream
import java.io.RandomAccessFile
import java.io.File
import java.util.concurrent.TimeUnit

/** Bounded decoder reads. Never hand remote URLs to a decoder that can follow redirects. */
internal object SdkMediaSources {
 internal fun networkChanged(){client.connectionPool.evictAll()}
 private val client=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
  .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(true).dns(object:Dns{override fun lookup(hostname:String)=SdkFeedService.publicAddresses(hostname)}).build()
 fun policy(c:Context,owner:JSONObject,item:JSONObject):JSONObject {
  if(item.has("audiobook_track"))return SdkAudioBooks.policy(c,owner,item)
  if(item.has("podcast_episode"))return SdkPodcasts.policy(c,owner,item)
  if(item.has("radio_station"))return SdkRadio.policy(c,owner,item)
  val args=JSONObject();if(item.has("url"))args.put("url",item.getString("url"));if(item.has("account"))args.put("account",item.getString("account"))
  return AppSdk.nativeTask(c,JSONObject().put("action","media_authorize").put("owner",owner).put("args",args))as JSONObject
 }
 internal fun mp3File(c:Context,owner:JSONObject,item:JSONObject):File? {
  policy(c,owner,item)
  val f=when{
   item.has("audiobook_track")->SdkAudioBooks.localPlaybackFile(c,owner,item)
   item.has("podcast_episode")->SdkPodcasts.localPlaybackFile(c,owner,item)
   item.has("file")->File(AppSdk.nativeTask(c,JSONObject().put("action","media_blob").put("owner",owner).put("service","resolve").put("args",JSONObject().put("id",item.getString("file")))).getString("path"))
   else->null
  };return f?.takeIf{app.onklav.shared.Mp3Codec.isMp3(it)}
 }
 internal fun pcmFile(file:File,verify:()->Unit):MediaDataSource=object:MediaDataSource(){
  val input=RandomAccessFile(file,"r");val length=input.length()
  @Synchronized override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int{verify();require(position>=0&&offset>=0&&size>=0&&offset.toLong()+size<=buffer.size);if(size==0)return 0;if(position>=length)return -1;input.seek(position);return input.read(buffer,offset,minOf(size,65536,(length-position).toInt()))}
  override fun getSize()=length
  override fun close(){input.close()}
 }
 fun open(c:Context,owner:JSONObject,item:JSONObject):MediaDataSource {
  if(item.has("audiobook_track"))return SdkAudioBooks.open(c,owner,item)
  if(item.has("podcast_episode"))SdkPodcasts.openLocal(c,owner,item)?.let{return it}
  val initial=policy(c,owner,item)
  fun verify(){check(policy(c,owner,item).toString()==initial.toString()){"Playback permission changed"}}
  if(item.has("file")){
   val file=AppSdk.nativeTask(c,JSONObject().put("action","media_blob").put("owner",owner).put("service","resolve").put("args",JSONObject().put("id",item.getString("file"))))as JSONObject
   check(file.getString("mime").startsWith("audio/")||file.getString("mime").startsWith("video/")){"Choose an audio or video file"}
   return object:MediaDataSource(){val input=RandomAccessFile(file.getString("path"),"r");val length=file.getLong("size")
    @Synchronized override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int{verify();require(position>=0);if(position>=length)return -1;input.seek(position);return input.read(buffer,offset,minOf(size,65536,(length-position).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))}
    override fun getSize()=length
    override fun close(){input.close()}
   }
  }
  return Remote(initial,{verify()},build={
   var request=if(item.has("podcast_episode"))SdkPodcasts.streamRequest(c,owner,item)else if(item.has("radio_station"))SdkRadio.streamRequest(c,owner,item)else SdkHttpPolicy.request(initial)
   if(item.has("account")){val auth=runBlocking{SdkAccounts.authenticated(c,"media-"+owner.getString("id"),initial,item.getString("url"),::verify)}.build().header("Authorization")!!;request=request.newBuilder().header("Authorization",auth).build()}
   request
  },checkAccount={header->if(item.has("account"))SdkAccounts.validateUse(c,initial,header,::verify)},probeFirst=item.has("podcast_episode"),allowUnvalidatedRange=item.has("podcast_episode"),sequentialFile=if(item.has("podcast_episode"))({File.createTempFile("podcast-stream-",".tmp",c.cacheDir)})else null)
 }
 /** Servers without byte ranges get one bounded disk-backed stream, rather than repeated downloads/discards. */
 internal class SequentialDisk(private val file:File,private val input:InputStream,private var total:Long,private val verify:()->Unit):MediaDataSource(){
  private val data=RandomAccessFile(file,"rw");private var written=0L;private var ended=false;@Volatile private var closed=false
  @Synchronized override fun readAt(at:Long,buffer:ByteArray,offset:Int,size:Int):Int{check(!closed);require(at>=0&&offset>=0&&size>=0&&offset.toLong()+size<=buffer.size);verify();if(size==0)return 0;if(total>=0&&at>=total)return -1;val wanted=minOf(at+minOf(size,65536),if(total>=0)total else Long.MAX_VALUE);check(wanted<=2L*1024*1024*1024){"Episode exceeds the2GB streaming cache limit"};val scratch=ByteArray(65536)
   while(!ended&&written<wanted){verify();check(!closed);val n=input.read(scratch,0,minOf(scratch.size.toLong(),wanted-written).toInt());if(n<0){ended=true;check(total<0||written==total){"Episode download was interrupted"};total=written;break};check(n>0){"Episode stream stalled"};data.seek(written);data.write(scratch,0,n);written+=n}
   if(at>=written)return -1;data.seek(at);return data.read(buffer,offset,minOf(size,65536,(written-at).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
  }
  override fun getSize()=total
  override fun close(){closed=true;runCatching{input.close()};runCatching{data.close()};file.delete()}
 }
 internal class Remote(private val policy:JSONObject,private val verify:()->Unit,private val build:()->Request,private val checkAccount:(String?)->Unit,
  private val connect:(Request)->Call={client.newCall(it)},internal val probeFirst:Boolean=false,private val allowUnvalidatedRange:Boolean=false,private val sequentialFile:(()->File)?=null):MediaDataSource(){
  @Volatile private var closed=false
  private var disk:SequentialDisk?=null
  @Volatile private var call:Call?=null
  private var response:Response?=null;private var input:InputStream?=null;private var position=0L;@Volatile private var total=-1L;private var etag="";private var modified="";private var authorization:String?=null;private var shortReads=0
  @Synchronized override fun readAt(at:Long,buffer:ByteArray,offset:Int,size:Int):Int{
   check(!closed);require(at>=0&&offset>=0&&size>=0&&offset.toLong()+size<=buffer.size);if(size==0)return 0;verify()
   disk?.let{return it.readAt(at,buffer,offset,size)}
   if(total>=0&&at>=total)return -1
   if(probeFirst&&total<0&&at>0&&input==null){readAt(0,ByteArray(1),0,1);if(total>=0&&at>=total)return -1}
   if(input==null||at!=position){
    response?.close();call?.cancel();val strong=etag.startsWith('"')&&!etag.startsWith("W/")
    val validator=if(strong)etag else if(probeFirst&&!allowUnvalidatedRange)modified else ""
    var sequential=false
    if(at>0&&!allowUnvalidatedRange)check(validator.isNotEmpty()){ "This stream does not support seeking. Download the book to listen offline." }
    val request=build().newBuilder().header("Accept-Encoding","identity").apply{if(at>0||probeFirst){header("Range","bytes=$at-");if(validator.isNotEmpty())header("If-Range",validator)}}.build();authorization=request.header("Authorization")
    val next=connect(request);call=next;if(closed){next.cancel();error("Playback closed")};verify();checkAccount(authorization)
    val res=next.execute();response=res;check(res.header("Content-Encoding","identity")=="identity")
    val body=res.body?:error("Empty media response")
    if(at>0){
     if(res.code==206){
      check(if(strong)res.header("ETag")==etag else if(modified.isNotEmpty()&&!allowUnvalidatedRange)res.header("Last-Modified")==modified else allowUnvalidatedRange)
      val length=SdkJobs.contentRange(res.header("Content-Range"),at,body.contentLength(),Long.MAX_VALUE);check(total<0||total==length){"Audio changed; restart this episode"};total=length
     }else{check(allowUnvalidatedRange&&res.code==200){"This episode does not support seeking. Download it to listen."};check(total<0||body.contentLength()<0||total==body.contentLength()){"Audio changed; download this episode to listen reliably"};total=body.contentLength()
      if(sequentialFile!=null){val source=SequentialDisk(sequentialFile.invoke(),body.byteStream(),total,verify);disk=source;return source.readAt(at,buffer,offset,size)};sequential=true}
    }else{check(res.code==200||probeFirst&&res.code==206);total=if(res.code==206)SdkJobs.contentRange(res.header("Content-Range"),0,body.contentLength(),Long.MAX_VALUE)else body.contentLength();etag=res.header("ETag")?:"";modified=res.header("Last-Modified").orEmpty().takeIf{it.length<=128&&runCatching{java.time.ZonedDateTime.parse(it,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)}.isSuccess}.orEmpty()}

    input=body.byteStream();position=if(sequential)0 else at
    if(sequential){
     check(at<=256L*1024*1024){"This episode cannot seek that far. Restart playback."}
     val scratch=ByteArray(65536)
     while(position<at){verify();check(!closed);val n=input!!.read(scratch,0,minOf(scratch.size.toLong(),at-position).toInt());if(n<0)return -1;check(n>0);position+=n}
    }
   }
   verify();checkAccount(authorization);var n:Int;var empty=0
   do{n=(input?:throw java.io.IOException("Playback closed")).read(buffer,offset,minOf(size,65536));if(n==0)check(++empty<=3)}while(n==0)
   if(n>0){position+=n;shortReads=0}else if(n<0&&probeFirst&&total>position){check(++shortReads<=2){"Episode stream ended before the file was complete"};response?.close();call?.cancel();input=null;return readAt(position,buffer,offset,size)};return n
  }
  /** Probe on an IO worker before passing this source to Android. A native size callback must never perform HTTP. */
  @Synchronized fun prime(){check(!closed);if(probeFirst&&total<0)readAt(0,ByteArray(1),0,1)}
  override fun getSize():Long = disk?.getSize()?:total

  override fun close(){closed=true;call?.cancel();runCatching{response?.close()};disk?.close();disk=null;input=null}
 }
}
