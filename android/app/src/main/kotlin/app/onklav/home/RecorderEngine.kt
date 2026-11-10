package app.onklav.home

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.nio.ByteBuffer

internal data class RecorderResult(val file:File,val warning:String="")
internal interface RecorderBackend{
 fun start()
 fun pause()
 fun resume()
 fun level():Float
 fun silenced():Boolean=false
 fun bytes():Long
 fun finish():RecorderResult
 fun release()
}
@Suppress("DEPRECATION")
internal class AndroidRecorderBackend(private val c:Context,private val id:String,private val options:RecorderOptions,private val projection:MediaProjection?,private val limit:(String)->Unit):RecorderBackend{
 private var recorder:MediaRecorder?=null;private var display:VirtualDisplay?=null;private var pcm:RecorderPcm?=null
 private var started=false;private var paused=false
 private val video=RecorderLibrary.file(c,id,"video.part.mp4")
 private val raw=RecorderLibrary.file(c,id,if(options.kind==RecorderKind.SCREEN)"video.part.mp4"else"audio.part.${options.extension}")
 private val audio=RecorderLibrary.file(c,id,"capture.part.m4a")
 private val budget=minOf(1536L*1024*1024,(RecorderLibrary.directory(c).usableSpace-128L*1024*1024)/6).coerceAtLeast(0)
 @Synchronized override fun start(){
  require(budget>=16L*1024*1024){"Free at least 224 MB before recording"}
  if(options.microphone)check(c.getSystemService(AudioManager::class.java).mode==AudioManager.MODE_NORMAL){"Finish your phone call before recording the microphone"}
  try{
   if(options.kind==RecorderKind.VOICE&&options.format==RecorderFormat.WAV){pcm=RecorderPcm(raw,true,null,RecorderSound.MIC,budget,limit).also{it.start()};started=true;return}
   val r=if(Build.VERSION.SDK_INT>=31)MediaRecorder(c)else MediaRecorder();recorder=r
   val builtInMic=options.microphone&&(options.kind==RecorderKind.VOICE||options.sound==RecorderSound.MIC)
   if(builtInMic)r.setAudioSource(MediaRecorder.AudioSource.MIC)
   if(options.kind==RecorderKind.SCREEN)r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
   r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
   if(builtInMic){r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);r.setAudioChannels(1);r.setAudioSamplingRate(48000);r.setAudioEncodingBitRate(128000)}
   var dimensions:Pair<Int,Int>?=null;var density=1
   if(options.kind==RecorderKind.SCREEN){
    val manager=c.getSystemService(WindowManager::class.java)
    val size=if(Build.VERSION.SDK_INT>=30)manager.maximumWindowMetrics.bounds.let{it.width() to it.height()}else{val metrics=DisplayMetrics();manager.defaultDisplay.getRealMetrics(metrics);metrics.widthPixels to metrics.heightPixels}
    density=c.resources.displayMetrics.densityDpi;dimensions=recorderDimensions(size.first,size.second,options.high)
    val supported=MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any{info->info.isEncoder&&info.supportedTypes.any{it.equals(MediaFormat.MIMETYPE_VIDEO_AVC,true)}&&runCatching{info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities.areSizeAndRateSupported(dimensions.first,dimensions.second,30.0)}.getOrDefault(false)}
    check(supported){"This video size is unavailable. Choose Standard quality."}
    r.setVideoEncoder(MediaRecorder.VideoEncoder.H264);r.setVideoSize(dimensions.first,dimensions.second);r.setVideoFrameRate(30);r.setVideoEncodingBitRate(if(options.high)8_000_000 else 4_000_000)
   }
   r.setMaxDuration(6*60*60*1000);r.setMaxFileSize(budget);r.setOutputFile(raw.path)
   r.setOnInfoListener{_,what,_->if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED||what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED)limit(if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)"Recording reached its file-size limit"else"Recording reached six hours")}
   r.setOnErrorListener{_,_,_->limit("Android interrupted the recorder")}
   r.prepare()
   if(dimensions!=null)display=requireNotNull(projection).createVirtualDisplay("OnKlav Screen Record",dimensions.first,dimensions.second,density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,r.surface,null,null)
   r.start();started=true
   val startNs=System.nanoTime()
   if(options.kind==RecorderKind.SCREEN&&(options.sound==RecorderSound.DEVICE||options.sound==RecorderSound.BOTH))pcm=RecorderPcm(audio,false,requireNotNull(projection),options.sound,budget/3,limit).also{it.start(startNs)}
  }catch(e:Exception){release();throw e}
 }
 @Synchronized override fun pause(){check(started&&!paused);pcm?.pause();try{recorder?.pause();paused=true}catch(e:Exception){pcm?.resume();throw e}}
 @Synchronized override fun resume(){check(started&&paused);if(options.microphone)check(c.getSystemService(AudioManager::class.java).mode==AudioManager.MODE_NORMAL){"Finish your phone call before resuming"};recorder?.resume();pcm?.resume();paused=false}
 @Synchronized override fun level():Float=if(paused||!options.audio)0f else pcm?.level?:runCatching{recorderLevel(recorder?.maxAmplitude?:0)}.getOrDefault(0f)
 @Synchronized override fun silenced():Boolean=pcm?.silenced()?:runCatching{recorder?.activeRecordingConfiguration?.isClientSilenced==true}.getOrDefault(false)
 override fun bytes()=raw.length()+audio.length()
 @Synchronized override fun finish():RecorderResult{
  check(started){"Recording did not start"};started=false;var warning=""
  pcm?.pause()
  var stopError:Exception?=null
  try{recorder?.setOnErrorListener(null);recorder?.setOnInfoListener(null);recorder?.stop()}catch(e:Exception){stopError=e}
  runCatching{recorder?.release()};recorder=null;display?.release();display=null
  try{pcm?.finish()}catch(e:Exception){if(options.kind==RecorderKind.VOICE)throw e;warning="Audio could not be finalized. The saved video is silent."}
  val ready=RecorderLibrary.draft(c,id)
  if(options.kind==RecorderKind.SCREEN&&audio.isFile&&warning.isEmpty()){
   recorderMux(video,audio,ready,pcm?.offsetUs?:0);video.delete();audio.delete()
  }else{
   if(options.format==RecorderFormat.WAV&&options.kind==RecorderKind.VOICE)RecorderWav.repair(raw)
   recorderValidate(raw,options.kind,options.format)
   check(raw.renameTo(ready)){"Could not retain the recording. Its draft was kept."}
   if(stopError!=null)warning="Android interrupted recording; the playable portion was saved."
  }
  pcm=null;return RecorderResult(ready,warning)
 }
 @Synchronized override fun release(){runCatching{pcm?.finish()};pcm=null;runCatching{display?.release()};display=null;runCatching{recorder?.reset()};runCatching{recorder?.release()};recorder=null;started=false}
}
internal fun recorderValidate(file:File,kind:RecorderKind,format:RecorderFormat=RecorderFormat.AAC){
 require(file.isFile&&file.length()>0){"No recording data was captured"}
 if(kind==RecorderKind.VOICE&&format==RecorderFormat.WAV){RecorderWav.repair(file);return}
 val extractor=MediaExtractor();try{extractor.setDataSource(file.path);check((0 until extractor.trackCount).any{extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(if(kind==RecorderKind.SCREEN)"video/"else"audio/")==true}){"The recording is not playable. Its interrupted draft was kept."}}finally{extractor.release()}
}
/** Copy encoded samples, preserving sync; each track is rebased once, audio gets
 * its measured start offset. No transcode, whole-file byte arrays, or quality loss. */
internal fun recorderMux(video:File,audio:File,output:File,audioOffsetUs:Long){
 val v=MediaExtractor();val a=MediaExtractor();var muxer:MediaMuxer?=null;var started=false
 try{v.setDataSource(video.path);a.setDataSource(audio.path)
  val vi=(0 until v.trackCount).firstOrNull{v.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true}?:error("Screen video is missing")
  val ai=(0 until a.trackCount).firstOrNull{a.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true}?:error("Captured audio is missing")
  val mux=MediaMuxer(output.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);muxer=mux
  val vt=mux.addTrack(v.getTrackFormat(vi));val at=mux.addTrack(a.getTrackFormat(ai));mux.start();started=true
  val buffer=ByteBuffer.allocateDirect(8*1024*1024);val info=MediaCodec.BufferInfo()
  fun copy(source:MediaExtractor,index:Int,target:Int,offset:Long){source.selectTrack(index);var first=-1L;var previous=-1L
   while(source.sampleTime>=0){require(source.sampleSize<=buffer.capacity()){ "An encoded frame is too large" };buffer.clear();val size=source.readSampleData(buffer,0);if(size<0)break;if(first<0)first=source.sampleTime;info.set(0,size,maxOf(previous+1,source.sampleTime-first+offset),if(source.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC!=0)MediaCodec.BUFFER_FLAG_KEY_FRAME else 0);previous=info.presentationTimeUs;mux.writeSampleData(target,buffer,info);source.advance()}
   check(first>=0){"Recording contains no samples"}
  }
  copy(v,vi,vt,0);copy(a,ai,at,audioOffsetUs.coerceAtLeast(0));mux.stop();started=false
 }catch(e:Exception){output.delete();throw e}finally{v.release();a.release();if(started)runCatching{muxer?.stop()};runCatching{muxer?.release()}}
}
