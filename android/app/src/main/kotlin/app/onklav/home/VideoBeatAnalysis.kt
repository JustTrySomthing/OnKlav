package app.onklav.home

import android.media.*
import java.io.File
import java.nio.ByteOrder
import kotlin.math.*

/** Energy-onset markers, not a tempo grid or an AI beat estimate. Bounded to ten minutes. */
internal class VideoBeatDetector {
    private var bucket=-1L;private var sum=0.0;private var samples=0
    private val history=ArrayDeque<Double>();private var before=0.0;private var peak=0.0;private var peakAt=0L;private var last=-1000L
    val beats=mutableListOf<Long>()
    fun sample(value:Float,ms:Long){val next=ms/10;if(bucket>=0&&next!=bucket){window(bucket*10,if(samples==0)0.0 else sum/samples);sum=0.0;samples=0};bucket=next;sum+=value*value;samples++}
    private fun window(at:Long,energy:Double){val baseline=if(history.isEmpty())0.0 else history.average();if(peak>before&&peak>=energy&&peak>max(.00015,baseline*1.8)&&peakAt-last>=200&&beats.size<5000){beats+=peakAt;last=peakAt};before=peak;peak=energy;peakAt=at;history.addLast(energy);if(history.size>70)history.removeFirst()}
    fun finish(){if(samples>0)window(bucket*10,sum/samples);window((bucket+1)*10,0.0)}
}
internal fun videoDetectBeats(file:File,start:Long,end:Long,checkpoint:()->Unit={}):List<Long>{
    val extractor=MediaExtractor();var codec:MediaCodec?=null
    try{extractor.setDataSource(file.path);val track=(0 until extractor.trackCount).firstOrNull{extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true}?:error("This source has no audio")
        val format=extractor.getTrackFormat(track);extractor.selectTrack(track);extractor.seekTo(start*1000,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val decoder=MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)));codec=decoder;decoder.configure(format,null,null,0);decoder.start()
        var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);var rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);var encoding=androidx.media3.common.C.ENCODING_PCM_16BIT
        val info=MediaCodec.BufferInfo();var inputEnd=false;var done=false;val detector=VideoBeatDetector();val until=min(end,start+600_000);val deadline=android.os.SystemClock.elapsedRealtime()+120_000
        while(!done){checkpoint();check(android.os.SystemClock.elapsedRealtime()<deadline){"Beat analysis took too long; trim the audio and try again"}
            if(!inputEnd){val id=decoder.dequeueInputBuffer(10_000);if(id>=0){val buffer=requireNotNull(decoder.getInputBuffer(id));val time=extractor.sampleTime;val n=if(time<0||time>=until*1000)-1 else extractor.readSampleData(buffer,0);if(n<0){decoder.queueInputBuffer(id,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true}else{decoder.queueInputBuffer(id,0,n,time,0);extractor.advance()}}}
            val id=decoder.dequeueOutputBuffer(info,10_000)
            if(id==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){val output=decoder.outputFormat;channels=output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);rate=output.getInteger(MediaFormat.KEY_SAMPLE_RATE);encoding=if(output.containsKey(MediaFormat.KEY_PCM_ENCODING))output.getInteger(MediaFormat.KEY_PCM_ENCODING)else androidx.media3.common.C.ENCODING_PCM_16BIT;require(encoding in listOf(androidx.media3.common.C.ENCODING_PCM_16BIT,androidx.media3.common.C.ENCODING_PCM_FLOAT)){"Unsupported decoded audio format"}}
            else if(id>=0){try{val buffer=requireNotNull(decoder.getOutputBuffer(id)).order(ByteOrder.nativeOrder());buffer.position(info.offset);buffer.limit(info.offset+info.size);var frame=0L;val bytes=if(encoding==androidx.media3.common.C.ENCODING_PCM_FLOAT)4 else 2
                while(buffer.remaining()>=channels*bytes){var value=0f;repeat(channels){val sample=if(bytes==4)buffer.float else buffer.short/32768f;if(sample.isFinite())value+=sample*sample};val ms=info.presentationTimeUs/1000+frame*1000/rate;if(ms in start until until)detector.sample(sqrt(value/channels),ms);frame++};done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0
            }finally{decoder.releaseOutputBuffer(id,false)}}
        };detector.finish();return detector.beats
    }finally{runCatching{codec?.stop()};codec?.release();extractor.release()}
}
