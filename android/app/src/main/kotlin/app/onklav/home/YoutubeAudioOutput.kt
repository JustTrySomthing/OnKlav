package app.onklav.home

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodec
import java.io.File
import java.nio.ByteBuffer

internal data class YoutubeAudioFormat(val extension:String,val mime:String)
internal fun youtubeAudioFormat(stream:YoutubeStream,mp3:Boolean)=if(mp3)YoutubeAudioFormat("mp3","audio/mpeg")else if(stream.mime.endsWith("webm"))YoutubeAudioFormat("webm","audio/webm")else YoutubeAudioFormat("m4a","audio/mp4")

/** Native audio stays in its source container; muxed video needs only track extraction, never encoding. */
internal fun youtubeOriginalAudio(input:File,output:File,stream:YoutubeStream,verify:()->Unit){
    try{
        if(stream.mime.startsWith("audio/")){input.inputStream().use{source->output.outputStream().buffered().use{out->val bytes=ByteArray(131072);while(true){verify();val n=source.read(bytes);if(n<0)break;out.write(bytes,0,n)}}};return}
        val extractor=MediaExtractor();var muxer:MediaMuxer?=null
        try{
            extractor.setDataSource(input.path)
            val index=(0 until extractor.trackCount).firstOrNull{extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true}?:error("The downloaded file has no audio track")
            val format=extractor.getTrackFormat(index);extractor.selectTrack(index)
            val mux=MediaMuxer(output.path,if(stream.mime.endsWith("webm"))MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);muxer=mux
            val track=mux.addTrack(format);mux.start()
            var buffer=ByteBuffer.allocateDirect(256*1024);val info=MediaCodec.BufferInfo();var samples=0;val origin=extractor.sampleTime.coerceAtLeast(0)
            while(true){verify();val size=extractor.sampleSize;if(size<0)break;require(size<=16*1024*1024){"Audio sample is too large"};if(size>buffer.capacity())buffer=ByteBuffer.allocateDirect(size.toInt());buffer.clear();val n=extractor.readSampleData(buffer,0);if(n<0)break
                info.set(0,n,(extractor.sampleTime-origin).coerceAtLeast(0),if(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC!=0)MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                mux.writeSampleData(track,buffer,info);samples++;if(!extractor.advance())break
            };require(samples>0){"The downloaded audio is empty"};mux.stop()
        }finally{runCatching{muxer?.release()};extractor.release()}
    }catch(t:Throwable){output.delete();throw t}
}
