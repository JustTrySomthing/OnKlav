package app.onklav.home

import android.content.Context
import android.graphics.Bitmap
import android.media.*
import android.net.Uri
import android.os.SystemClock
import android.telephony.SmsManager
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.CoroutineContext
import kotlin.math.*

/** Android codec broker. Sources remain untouched; only checked, complete outputs enter drafts. */
internal object CarrierMedia {
 private const val MAX_MESSAGE_BYTES=1_000_000
 private const val DEFAULT_MESSAGE_BYTES=300_000
 private const val RESERVE_BYTES=16_384
 private const val MIN_VIDEO_RATE=64_000
 private const val VIDEO_AUDIO_RATE=32_000
 const val AUDIO_RATE=64_000
 private const val VIDEO_FPS=12

 fun messageLimit(configured:Int?):Int =
  (configured?.takeIf{it>0}?:DEFAULT_MESSAGE_BYTES).coerceAtMost(MAX_MESSAGE_BYTES)

 internal fun attachmentBudget(limit:Int,text:String,parts:org.json.JSONArray):Int {
  val used=(0 until parts.length()).sumOf{parts.getJSONObject(it).optLong("bytes").coerceIn(0,MAX_MESSAGE_BYTES.toLong())}
  return (limit-RESERVE_BYTES-text.toByteArray().size-used-parts.length()*1024L).coerceAtLeast(0).toInt()
 }

 internal fun batchBudget(available:Int,count:Int):Int =
  ((available-(count-1)*1024).coerceAtLeast(0))/count

 data class Info(val duration:Long,val width:Int,val height:Int,val rotation:Int,val audio:Boolean,val video:Boolean)
 data class Selection(val uri:Uri,val info:Info,val budget:Int,val name:String)
 fun inspect(c:Context,uri:Uri):Info=MediaMetadataRetriever().let { r->try {
  r.setDataSource(c,uri)
  fun n(key:Int)=r.extractMetadata(key)?.toLongOrNull()?:0
  Info(n(MediaMetadataRetriever.METADATA_KEY_DURATION),n(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt(),n(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt(),n(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt(),r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)=="yes",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)=="yes").also{require(it.duration>=500){"Choose media at least half a second long"}}
 }finally{r.release()} }
 fun available(c:Context,text:String,parts:org.json.JSONArray):Int {
  val configured=runCatching{MessagingTransport.manager(c).carrierConfigValues?.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE)}.getOrNull()
  return attachmentBudget(messageLimit(configured),text,parts)
 }
 fun recommended(budget:Int,video:Boolean,audio:Boolean):Long {
  val rate=if(video)MIN_VIDEO_RATE+(if(audio)VIDEO_AUDIO_RATE else 0)else AUDIO_RATE
  return ((budget-RESERVE_BYTES).coerceAtLeast(0)*6_800L/rate).coerceAtMost(if(video)60_000L else 180_000L)
 }
 fun validate(start:Long,end:Long,duration:Long,budget:Int,video:Boolean,audio:Boolean) {
  require(start>=0&&end<=duration&&end-start>=500){"Choose at least half a second within the original clip"}
  require(end-start<=recommended(budget,video,audio)){"Shorten the selection to ${recommended(budget,video,audio)/1000.0} seconds or less to fit this message"}
 }
 suspend fun prepare(c:Context,s:Selection,start:Long,end:Long,progress:(Float,String)->Unit):JSONObject {
  validate(start,end,s.info.duration,s.budget,s.info.video,s.info.audio)
  val job=kotlin.coroutines.coroutineContext
  require(c.cacheDir.usableSpace>s.budget*5L+8*1024*1024){"Free some phone storage before preparing this clip"}
  val video=File.createTempFile("onklav-video-",".mp4",c.cacheDir)
  val audio=File.createTempFile("onklav-audio-",".m4a",c.cacheDir)
  val id=MessageTransferProvider.id();val target=MessageTransferProvider.file(c,id)
  var success=false
  try {
   if(s.info.audio)encodeAudio(c,s.uri,start*1000,end*1000,if(s.info.video)VIDEO_AUDIO_RATE else AUDIO_RATE,audio,job){p->progress(p*.2f,"Compressing audio…")}
   if(s.info.video) {
    val sound=if(s.info.audio)audio.length()else 0
    val duration=end-start
    val planned=(s.budget-RESERVE_BYTES)*6_800L/duration-(if(s.info.audio)VIDEO_AUDIO_RATE else 0)
    var rate=minOf(planned,(s.budget-RESERVE_BYTES-sound)*6_800L/duration,700_000L).toInt()
    require(rate>=MIN_VIDEO_RATE){"Choose a shorter section to fit the carrier limit"}
    var useCompatibility=false
    for(attempt in 0..1) {
     val update:(Float)->Unit={p->progress(.2f+(attempt+p)*.35f,if(attempt==0)"Compressing video…" else "Adjusting video to fit…")}
     if(!useCompatibility) {
      try{encodeVideo(c,s.uri,s.info,start*1000,end*1000,rate,video,job,update)}
      catch(e:kotlinx.coroutines.CancellationException){throw e}
      catch(e:Exception){job.ensureActive();useCompatibility=true}
     }
     if(useCompatibility){
      video.delete()
      encodeVideoCompatibility(c,s.uri,start*1000,end*1000,rate,video,job,update)
     }
     combine(listOf(video)+if(s.info.audio)listOf(audio)else emptyList(),target,if(useCompatibility)0 else s.info.rotation,job)
     if(target.length() in 1..s.budget.toLong())break
     val measuredRate=(rate*minOf(.75,s.budget.toDouble()/target.length().coerceAtLeast(1)*.85)).toInt()
     require(attempt==0 && rate>MIN_VIDEO_RATE){"Choose a shorter section to fit the carrier limit"}
     rate=measuredRate.coerceAtLeast(MIN_VIDEO_RATE)
    }
   } else audio.copyTo(target,overwrite=true)
   job.ensureActive();require(target.length() in 1..s.budget.toLong()){"Choose a shorter section to fit this message"}
   val mime=if(s.info.video)"video/mp4"else"audio/mp4"
   val ready=inspect(c,MessageTransferProvider.uri(c,id,"blob",mime,s.name))
   require(abs(ready.duration-(end-start))<900 && (!s.info.audio||ready.audio) && (!s.info.video||ready.video)){"The phone produced an incomplete clip. Choose a different section"}
   progress(1f,"Ready")
   success=true
   return JSONObject().put("file",id).put("mime",mime).put("name",s.name.substringBeforeLast('.',s.name)+if(s.info.video)".mp4"else".m4a").put("bytes",target.length())
  } finally {video.delete();audio.delete();if(!success)target.delete()}
 }
 private class Sink(val codec:MediaCodec,file:File) : AutoCloseable {
  val mux=MediaMuxer(file.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);var track=-1;var ended=false;var samples=0;var last=SystemClock.elapsedRealtime();var pts=-1L
  fun drain() {
   val info=MediaCodec.BufferInfo()
   while(true){val i=codec.dequeueOutputBuffer(info,0);if(i==MediaCodec.INFO_TRY_AGAIN_LATER)return
    if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){check(track<0);track=mux.addTrack(codec.outputFormat);mux.start();last=SystemClock.elapsedRealtime()}
    else if(i>=0){try{if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0){check(track>=0);val data=requireNotNull(codec.getOutputBuffer(i));data.position(info.offset);data.limit(info.offset+info.size);info.presentationTimeUs=max(pts+1,info.presentationTimeUs);pts=info.presentationTimeUs;mux.writeSampleData(track,data,info);samples++};ended=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0;last=SystemClock.elapsedRealtime()}finally{codec.releaseOutputBuffer(i,false)}}
   }
  }
  fun input(job:CoroutineContext):Int {while(true){job.ensureActive();drain();val i=codec.dequeueInputBuffer(10000);if(i>=0)return i;check(SystemClock.elapsedRealtime()-last<15000){"The phone's media encoder stopped responding"}}}
  fun finish(job:CoroutineContext,pts:Long){val i=input(job);codec.queueInputBuffer(i,0,0,pts,MediaCodec.BUFFER_FLAG_END_OF_STREAM);while(!ended){job.ensureActive();drain();check(SystemClock.elapsedRealtime()-last<15000){"The phone's media encoder stopped responding"};if(!ended)Thread.sleep(2)};check(samples>0){"No media was produced"}}
  override fun close(){if(track>=0)runCatching{mux.stop()};mux.release()}
 }
 private fun source(c:Context,u:Uri,prefix:String):Pair<MediaExtractor,MediaFormat>{val e=MediaExtractor();try{e.setDataSource(c,u,null);for(i in 0 until e.trackCount){val f=e.getTrackFormat(i);if(f.getString(MediaFormat.KEY_MIME)?.startsWith(prefix)==true){e.selectTrack(i);return e to f}};error("This file has no $prefix track")}catch(t:Throwable){e.release();throw t}}
 private fun feed(e:MediaExtractor,d:MediaCodec,end:Long):Boolean {val i=d.dequeueInputBuffer(0);if(i<0)return false;val b=requireNotNull(d.getInputBuffer(i));b.clear();val time=e.sampleTime;val size=if(time<0||time>=end)-1 else e.readSampleData(b,0);if(size<0){d.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);return true};d.queueInputBuffer(i,0,size,time,0);e.advance();return false}
 private fun encodeAudio(c:Context,u:Uri,start:Long,end:Long,rate:Int,file:File,job:CoroutineContext,progress:(Float)->Unit){
  val(e,f)=source(c,u,"audio/");var decoder:MediaCodec?=null;var encoder:MediaCodec?=null
  try{
   val sampleRate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);var channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);require(channels in 1..8)
   val out=MediaFormat.createAudioFormat("audio/mp4a-latm",sampleRate,1).apply{setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);setInteger(MediaFormat.KEY_BIT_RATE,rate)}
   val enc=MediaCodec.createEncoderByType("audio/mp4a-latm");encoder=enc;enc.configure(out,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);enc.start()
   val dec=MediaCodec.createDecoderByType(requireNotNull(f.getString(MediaFormat.KEY_MIME)));decoder=dec;dec.configure(f,null,null,0);dec.start();e.seekTo(start,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
   Sink(enc,file).use{sink->val info=MediaCodec.BufferInfo();var inputEnd=false;var done=false;var samples=0L;var last=SystemClock.elapsedRealtime()
    while(!done){job.ensureActive();sink.drain();if(!inputEnd)inputEnd=feed(e,dec,end)
     val i=dec.dequeueOutputBuffer(info,10000)
     if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){channels=dec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);require(dec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)==sampleRate);require(!dec.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)||dec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)==android.media.AudioFormat.ENCODING_PCM_16BIT){"This phone decoded an unsupported audio format"}}
     if(i>=0){try{last=SystemClock.elapsedRealtime();val b=dec.getOutputBuffer(i)
      if(b!=null&&info.size>0){b.position(info.offset);b.limit(info.offset+info.size);val shorts=b.order(ByteOrder.LITTLE_ENDIAN).slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();val frames=shorts.remaining()/channels;val from=max(0L,((start-info.presentationTimeUs)*sampleRate+999999)/1000000).toInt().coerceAtMost(frames);val until=min(frames.toLong(),max(0,(end-info.presentationTimeUs)*sampleRate/1000000)).toInt();var at=from
       while(at<until){val slot=sink.input(job);val buf=requireNotNull(enc.getInputBuffer(slot)).order(ByteOrder.LITTLE_ENDIAN);buf.clear();val count=min(until-at,buf.remaining()/2);require(count>0);repeat(count){var sum=0;repeat(channels){ch->sum+=shorts.get((at+it)*channels+ch).toInt()};buf.putShort((sum/channels).toShort())};enc.queueInputBuffer(slot,0,count*2,samples*1000000/sampleRate,0);samples+=count;at+=count}
      };done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0 || info.presentationTimeUs>=end;progress(((info.presentationTimeUs-start).toFloat()/(end-start)).coerceIn(0f,1f))
     }finally{dec.releaseOutputBuffer(i,false)}}
     check(SystemClock.elapsedRealtime()-last<15000){"The phone's audio decoder stopped responding"}
    };sink.finish(job,samples*1000000/sampleRate)
   }
  }finally{runCatching{decoder?.stop()};decoder?.release();runCatching{encoder?.stop()};encoder?.release();e.release()}
 }
 private fun encodeVideo(c:Context,u:Uri,metadata:Info,start:Long,end:Long,rate:Int,file:File,job:CoroutineContext,progress:(Float)->Unit){
  val(e,f)=source(c,u,"video/");var decoder:MediaCodec?=null;var encoder:MediaCodec?=null
  try {
   val (w,h)=videoDimensions(metadata.width,metadata.height,rate)
   val format=videoFormat(w,h,rate)
   val enc=MediaCodec.createEncoderByType("video/avc");encoder=enc;enc.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);enc.start()
   f.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
   val dec=MediaCodec.createDecoderByType(requireNotNull(f.getString(MediaFormat.KEY_MIME)));decoder=dec;dec.configure(f,null,null,0);dec.start();e.seekTo(start,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
   Sink(enc,file).use{sink->val info=MediaCodec.BufferInfo();var inputEnd=false;var done=false;var next=start;var last=SystemClock.elapsedRealtime()
    while(!done){job.ensureActive();sink.drain();if(!inputEnd)inputEnd=feed(e,dec,end);val i=dec.dequeueOutputBuffer(info,10000)
     if(i>=0){try{last=SystemClock.elapsedRealtime();val time=info.presentationTimeUs
      if(info.size>0&&time>=next&&time<end){val slot=sink.input(job);val output=enc.getInputImage(slot)?:error("This phone cannot prepare this video format. Try a different video")
       val input=dec.getOutputImage(i)?:error("This phone cannot decode this video for compression")
       try{scaleYuv(input,output,w,h)}finally{input.close();output.close()}
       enc.queueInputBuffer(slot,0,w*h*3/2,time-start,0);next=start+((time-start)*VIDEO_FPS/1_000_000+1)*1_000_000/VIDEO_FPS
       progress(((time-start).toFloat()/(end-start)).coerceIn(0f,1f))
      };done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0||time>=end
     }finally{dec.releaseOutputBuffer(i,false)}}
     check(SystemClock.elapsedRealtime()-last<15000){"The phone's video decoder stopped responding"}
    };sink.finish(job,end-start)
   }
  }finally{runCatching{decoder?.stop()};decoder?.release();runCatching{encoder?.stop()};encoder?.release();e.release()}
 }
 private fun videoDimensions(width:Int,height:Int,rate:Int):Pair<Int,Int> {
  require(width>0 && height>0){"Invalid video dimensions"}
  val edge=if(rate>=300_000)480 else 320
  val scale=minOf(1.0,edge.toDouble()/maxOf(width,height))
  return maxOf(16,(width*scale/16).toInt()*16) to maxOf(16,(height*scale/16).toInt()*16)
 }
 private fun videoFormat(w:Int,h:Int,rate:Int)=MediaFormat.createVideoFormat("video/avc",w,h).apply{
  setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
  setInteger(MediaFormat.KEY_BIT_RATE,rate)
  setInteger(MediaFormat.KEY_FRAME_RATE,VIDEO_FPS)
  setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
 }
 /** Some decoders expose no readable YUV output. Retrieving bounded frames also applies source rotation. */
 private fun encodeVideoCompatibility(c:Context,u:Uri,start:Long,end:Long,rate:Int,file:File,job:CoroutineContext,progress:(Float)->Unit){
  val reader=MediaMetadataRetriever()
  var encoder:MediaCodec?=null
  try {
   reader.setDataSource(c,u)
   fun frame(time:Long,w:Int,h:Int)=reader.getScaledFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST,w,h)
    ?: error("A video frame could not be read")
   val first=frame(start,480,480)
   val (w,h)=try{videoDimensions(first.width,first.height,rate)}finally{first.recycle()}
   val enc=MediaCodec.createEncoderByType("video/avc")
   encoder=enc
   enc.configure(videoFormat(w,h,rate),null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);enc.start()
   val pixels=IntArray(w*h)
   val count=((end-start)*VIDEO_FPS+999_999)/1_000_000
   Sink(enc,file).use{sink->
    for(i in 0 until count){
     job.ensureActive()
     val pts=i*1_000_000/VIDEO_FPS
     val image=frame(start+pts,w,h)
     val scaled=if(image.width==w && image.height==h)image else Bitmap.createScaledBitmap(image,w,h,true)
     try{scaled.getPixels(pixels,0,w,0,0,w,h)}finally{if(scaled!==image)scaled.recycle();image.recycle()}
     val slot=sink.input(job)
     val output=enc.getInputImage(slot)?:error("This phone's encoder cannot prepare this clip")
     try{fillYuv(output,pixels,w,h)}finally{output.close()}
     enc.queueInputBuffer(slot,0,w*h*3/2,pts,0);sink.drain()
     progress((i+1).toFloat()/count)
    }
    sink.finish(job,end-start)
   }
  } finally {runCatching{encoder?.stop()};encoder?.release();reader.release()}
 }
 private fun fillYuv(image:Image,pixels:IntArray,w:Int,h:Int){
  val planes=image.planes
  val buffers=planes.map{it.buffer}
  val offsets=buffers.map{it.position()}
  fun put(plane:Int,x:Int,y:Int,value:Int){
   buffers[plane].put(offsets[plane]+y*planes[plane].rowStride+x*planes[plane].pixelStride,value.coerceIn(0,255).toByte())
  }
  for(y in 0 until h)for(x in 0 until w){
   val rgb=pixels[y*w+x]
   val r=rgb shr 16 and 255;val g=rgb shr 8 and 255;val b=rgb and 255
   put(0,x,y,((66*r+129*g+25*b+128) shr 8)+16)
   if(y%2==0 && x%2==0){
    var red=0;var green=0;var blue=0
    for(dy in 0..1)for(dx in 0..1){
     val sample=pixels[(y+dy)*w+x+dx]
     red+=sample shr 16 and 255;green+=sample shr 8 and 255;blue+=sample and 255
    }
    put(1,x/2,y/2,((-38*(red/4)-74*(green/4)+112*(blue/4)+128) shr 8)+128)
    put(2,x/2,y/2,((112*(red/4)-94*(green/4)-18*(blue/4)+128) shr 8)+128)
   }
  }
 }
 private fun scaleYuv(from:Image,to:Image,w:Int,h:Int){
  val crop=from.cropRect
  for(p in 0..2){val factor=if(p==0)1 else 2;val src=from.planes[p];val dst=to.planes[p];val sw=crop.width()/factor;val sh=crop.height()/factor;val dw=w/factor;val dh=h/factor;val sb=src.buffer;val db=dst.buffer;val base=sb.position();val destBase=db.position()
   for(y in 0 until dh){val sy=crop.top/factor+y*sh/dh;for(x in 0 until dw){val sx=crop.left/factor+x*sw/dw;db.put(destBase+y*dst.rowStride+x*dst.pixelStride,sb.get(base+sy*src.rowStride+sx*src.pixelStride))}}
  }
 }
 private fun combine(files:List<File>,output:File,rotation:Int,job:CoroutineContext){val mux=MediaMuxer(output.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);val extractors=mutableListOf<MediaExtractor>();var started=false
  try{mux.setOrientationHint(rotation);val tracks=files.map{file->val e=MediaExtractor();extractors+=e;e.setDataSource(file.path);val f=e.getTrackFormat(0);e.selectTrack(0);e to mux.addTrack(f)};mux.start();started=true;val buffer=java.nio.ByteBuffer.allocate(2*1024*1024)
   tracks.forEach{(e,track)->val info=MediaCodec.BufferInfo();while(true){job.ensureActive();buffer.clear();val size=e.readSampleData(buffer,0);if(size<0)break;info.set(0,size,e.sampleTime,if(e.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC!=0)MediaCodec.BUFFER_FLAG_KEY_FRAME else 0);mux.writeSampleData(track,buffer,info);e.advance()}}
  }finally{extractors.forEach{it.release()};if(started)runCatching{mux.stop()};mux.release()}
 }
}
