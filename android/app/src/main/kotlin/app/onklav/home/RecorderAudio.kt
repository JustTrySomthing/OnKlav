package app.onklav.home

import android.media.*
import android.media.projection.MediaProjection
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

internal object RecorderWav {
 const val RATE=48000
 fun header(file:File){val bytes=(file.length()-44).coerceAtLeast(0);require(bytes<=0xfffffff0L&&bytes%2==0L);RandomAccessFile(file,"rw").use{out->out.seek(0);val h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);h.put("RIFF".toByteArray()).putInt((bytes+36).toInt()).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE*2).putShort(2).putShort(16).put("data".toByteArray()).putInt(bytes.toInt());out.write(h.array());out.fd.sync()}}
 fun repair(file:File){require(file.isFile&&file.length()>44){"No audio samples were retained"};RandomAccessFile(file,"rw").use{f->val head=ByteArray(12);f.readFully(head);require(head.copyOfRange(0,4).contentEquals("RIFF".toByteArray())&&head.copyOfRange(8,12).contentEquals("WAVE".toByteArray())){"This is not a recoverable WAV recording"};if((f.length()-44)%2!=0L)f.setLength(f.length()-1)};header(file)}
}
internal fun recorderAudioRecord(source:Int,stereo:Boolean=false,projection:MediaProjection?=null):AudioRecord{
 val channels=if(stereo)AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
 val format=AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(48000).setChannelMask(channels).build()
 val minimum=AudioRecord.getMinBufferSize(48000,channels,AudioFormat.ENCODING_PCM_16BIT);require(minimum>0){"This audio format is unavailable"}
 val builder=AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(maxOf(minimum*4,16384))
 if(projection==null)builder.setAudioSource(source)else builder.setAudioPlaybackCaptureConfig(AudioPlaybackCaptureConfiguration.Builder(projection).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build())
 val recorder=builder.build();if(recorder.state!=AudioRecord.STATE_INITIALIZED){recorder.release();error("Audio input could not be initialized")};return recorder
}
/** Dedicated PCM reader. Paused samples are drained and discarded, never buffered
 * into the resumed recording. Stop unblocks reads before joining the encoder. */
internal class RecorderPcm(private val output:File,private val wav:Boolean,private val projection:MediaProjection?,private val sound:RecorderSound,private val budget:Long,private val failure:(String)->Unit){
 private val inputsLock=Any()
 @Volatile private var device:AudioRecord?=null;@Volatile private var microphone:AudioRecord?=null
 private var thread:Thread?=null;private val running=AtomicBoolean(false)
 private val gate=Any();private var paused=false
 @Volatile var level=0f;private set
 @Volatile var error:String?=null;private set
 var offsetUs=0L;private set
 fun start(videoStartedNs:Long=System.nanoTime()){
  try{
   if(sound==RecorderSound.DEVICE||sound==RecorderSound.BOTH)device=recorderAudioRecord(MediaRecorder.AudioSource.DEFAULT,true,requireNotNull(projection))
   if(sound==RecorderSound.MIC||sound==RecorderSound.BOTH)microphone=recorderAudioRecord(MediaRecorder.AudioSource.MIC)
   device?.startRecording();microphone?.startRecording()
   check(listOfNotNull(device,microphone).all{it.recordingState==AudioRecord.RECORDSTATE_RECORDING}){"Audio capture could not start"}
   offsetUs=((System.nanoTime()-videoStartedNs)/1000).coerceAtLeast(0)
   running.set(true);thread=Thread({capture()},"OnKlav recording audio").apply{start()}
  }catch(e:Exception){releaseInputs();throw e}
 }
 fun pause(){synchronized(gate){paused=true;level=0f}}
 fun resume(){synchronized(gate){paused=false}}
 private fun read(record:AudioRecord,data:ShortArray):Boolean{var at=0;while(at<data.size&&running.get()){val n=record.read(data,at,data.size-at,AudioRecord.READ_BLOCKING);if(n<=0){if(!running.get())return false;error("Audio input stopped ($n)")};at+=n};return at==data.size}
 private fun capture(){var codec:MediaCodec?=null;var muxer:MediaMuxer?=null;var stream:FileOutputStream?=null;var track=-1;var started=false;var frames=0L;var lastPts=-1L
  val stereo=device!=null;val channels=if(stereo)2 else 1;val dev=ShortArray(512*2);val mic=ShortArray(512);val info=MediaCodec.BufferInfo()
  try{
   if(wav){stream=FileOutputStream(output);stream.write(ByteArray(44));RecorderWav.header(output)}else{
    codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply{configure(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,48000,channels).apply{setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);setInteger(MediaFormat.KEY_BIT_RATE,if(stereo)192000 else 128000);setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,16384)},null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);start()};muxer=MediaMuxer(output.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
   }
   fun drain(end:Boolean=false){val encoder=codec?:return;val deadline=System.nanoTime()+3_000_000_000L
    do{val index=encoder.dequeueOutputBuffer(info,if(end)10000 else 0)
     if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){check(!started);track=muxer!!.addTrack(encoder.outputFormat);muxer!!.start();started=true}
     else if(index>=0){val buffer=requireNotNull(encoder.getOutputBuffer(index));if(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0&&info.size>0){check(started);info.presentationTimeUs=maxOf(lastPts+1,info.presentationTimeUs.coerceAtLeast(0));lastPts=info.presentationTimeUs;buffer.position(info.offset);buffer.limit(info.offset+info.size);muxer!!.writeSampleData(track,buffer,info)};val eos=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0;encoder.releaseOutputBuffer(index,false);if(eos)return}
     else if(!end)return
    }while(System.nanoTime()<deadline);if(end)error("Audio encoder did not finish")
   }
   while(running.get()){
    if(device!=null&&!read(device!!,dev))break
    if(microphone!=null&&!read(microphone!!,mic))break
    val samples=if(device!=null&&microphone!=null)recorderMix(dev,mic,dev.size)else if(device!=null)dev else mic
    synchronized(gate){if(!paused&&running.get()){
     level=recorderLevel(samples.maxOf{abs(it.toInt())})
     val bytes=ByteBuffer.allocate(samples.size*2).order(ByteOrder.LITTLE_ENDIAN);samples.forEach{bytes.putShort(it)}
     if(wav)stream!!.write(bytes.array())else{val encoder=codec!!;var index=encoder.dequeueInputBuffer(10000);if(index<0){drain();index=encoder.dequeueInputBuffer(10000)};check(index>=0){"Audio encoder is busy"};encoder.getInputBuffer(index)!!.apply{clear();put(bytes.array())};encoder.queueInputBuffer(index,0,bytes.position(),frames*1_000_000/48000,0);drain()}
     frames+=samples.size/channels
    }}
    if(output.length()>=budget){failure("Recording reached its file-size limit");break}
   }
   if(wav){stream!!.flush();stream.fd.sync();stream.close();stream=null;RecorderWav.header(output)}else{val encoder=codec!!;val i=encoder.dequeueInputBuffer(100000);check(i>=0){"Audio encoder could not finish"};encoder.queueInputBuffer(i,0,0,frames*1_000_000/48000,MediaCodec.BUFFER_FLAG_END_OF_STREAM);drain(true);check(started&&lastPts>=0){"No audio was captured"}}
  }catch(e:Exception){error=e.message?:"Audio recording failed";if(running.get())failure(error!!)}finally{
   running.set(false);level=0f;runCatching{stream?.close()};if(started)runCatching{muxer?.stop()};runCatching{muxer?.release()};runCatching{codec?.stop()};runCatching{codec?.release()};releaseInputs()
  }
 }
 fun silenced():Boolean=runCatching{microphone?.activeRecordingConfiguration?.isClientSilenced==true}.getOrDefault(false)
 private fun releaseInputs(){synchronized(inputsLock){runCatching{device?.release()};runCatching{microphone?.release()};device=null;microphone=null}}
 fun finish(){running.set(false);synchronized(inputsLock){runCatching{device?.stop()};runCatching{microphone?.stop()}};thread?.join(6000);check(thread?.isAlive!=true){"Audio is still finishing. The recording draft was kept."};thread=null;error?.let{error(it)}}
}
