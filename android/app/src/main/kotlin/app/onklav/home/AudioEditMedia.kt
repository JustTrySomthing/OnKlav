package app.onklav.home

import android.content.Context
import android.media.*
import android.net.Uri
import java.io.*
import java.nio.*
import kotlin.math.*

internal object AudioEditMedia{
    /** Fast, exact PCM WAV path also makes uncompressed imports independent of codecs. */
    fun wave(file:File,out:File,name:String,check:()->Unit,progress:(Float)->Unit):AudioSource?{
        RandomAccessFile(file,"r").use{r->
            if(r.length()<12)return null
            val head=ByteArray(12);r.readFully(head);if(String(head,0,4)!="RIFF"||String(head,8,4)!="WAVE")return null
            var format=0;var channels=0;var rate=0;var bits=0;var offset=0L;var length=0L
            fun u32():Long=java.lang.Integer.reverseBytes(r.readInt()).toLong() and 0xffffffffL
            while(r.filePointer+8<=r.length()){
                val tag=ByteArray(4);r.readFully(tag);val size=u32();val start=r.filePointer;require(start+size<=r.length()){"Incomplete WAV file"}
                when(String(tag)){"fmt "->{require(size>=16);format=java.lang.Short.reverseBytes(r.readShort()).toInt() and 65535;channels=java.lang.Short.reverseBytes(r.readShort()).toInt() and 65535;rate=u32().toInt();u32();r.readShort();bits=java.lang.Short.reverseBytes(r.readShort()).toInt() and 65535};"data"->{offset=start;length=size}}
                r.seek((start+size+(size and 1)).coerceAtMost(r.length()))
            }
            if(format !in listOf(1,3)||bits !in listOf(8,16,24,32)||format==3&&bits!=32)return null
            require(channels in 1..2&&rate in 8000..192000){"Use mono or stereo audio at 8–192 kHz"};val stride=channels*(bits/8);require(length%stride==0L){"WAV data ends inside an audio frame"};val frames=length/stride;require(frames>0&&frames<=rate*7200L){"Choose audio up to two hours long"}
            require(out.parentFile!!.usableSpace>frames*channels*4+32_000_000){"Not enough free space to open this audio"}
            r.seek(offset);AudioPcmWriter(out).use{w->
                val bytes=ByteArray((65536/stride)*stride);val block=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);var read=0L
                while(read<frames){check();val count=min((bytes.size/stride).toLong(),frames-read).toInt();r.readFully(bytes,0,count*stride);block.clear();block.limit(count*stride)
                    repeat(count*channels){val v=when(bits){
                        8->((block.get().toInt() and 255)-128)/128f
                        16->block.short/32768f
                        24->{val a=(block.get().toInt() and 255) or ((block.get().toInt() and 255) shl 8) or (block.get().toInt() shl 16);a/8388608f}
                        else->{val n=block.int;if(format==3)Float.fromBits(n)else n/2147483648f}
                    };w.sample(v)}
                    read+=count;progress(read.toFloat()/frames)
                }
            }
            return AudioSource(out,rate,channels,frames,name)
        }
    }
    fun decode(file:File,out:File,name:String,check:()->Unit,progress:(Float)->Unit):AudioSource{
        if(app.onklav.shared.Mp3Codec.isMp3(file)){
            val wav=File(out.parentFile,"mp3-${java.util.UUID.randomUUID()}.wav")
            try{app.onklav.shared.Mp3Codec.decode(file,wav,check,progress);return requireNotNull(wave(wav,out,name,check,progress))}finally{wav.delete()}
        }
        wave(file,out,name,check,progress)?.let{return it}
        val extractor=MediaExtractor();var codec:MediaCodec?=null
        try{
            extractor.setDataSource(file.path);val track=(0 until extractor.trackCount).firstOrNull{extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true}?:error("No audio track found")
            val format=extractor.getTrackFormat(track);require(!format.containsKey("crypto-mode")){"Protected audio cannot be edited"};extractor.selectTrack(track)
            var rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);var channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);require(channels in 1..2&&rate in 8000..192000){"Use mono or stereo audio at 8–192 kHz"}
            val duration=if(format.containsKey(MediaFormat.KEY_DURATION))format.getLong(MediaFormat.KEY_DURATION)else 0L
            require(duration<=7_200_000_000L){"Choose audio up to two hours long"}
            val decoder=MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)));codec=decoder
            decoder.configure(format,null,null,0);decoder.start();var encoding=AudioFormat.ENCODING_PCM_16BIT;var eos=false;var done=false;var frames=0L;var last=System.nanoTime()
            val info=MediaCodec.BufferInfo()
            AudioPcmWriter(out).use{w->while(!done){check();require(System.nanoTime()-last<30_000_000_000L){"The decoder stopped responding. Try WAV or another audio file."}
                if(!eos){val i=decoder.dequeueInputBuffer(10000);if(i>=0){val b=decoder.getInputBuffer(i)!!;val n=extractor.readSampleData(b,0);if(n<0){decoder.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);eos=true}else{decoder.queueInputBuffer(i,0,n,extractor.sampleTime,0);extractor.advance()}}}
                val i=decoder.dequeueOutputBuffer(info,10000)
                if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){val f=decoder.outputFormat;val rr=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);val cc=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);require(frames==0L||rr==rate&&cc==channels){"Changing audio formats are unsupported"};rate=rr;channels=cc;require(channels in 1..2&&rate in 8000..192000);encoding=if(f.containsKey(MediaFormat.KEY_PCM_ENCODING))f.getInteger(MediaFormat.KEY_PCM_ENCODING)else AudioFormat.ENCODING_PCM_16BIT;require(encoding in listOf(AudioFormat.ENCODING_PCM_16BIT,AudioFormat.ENCODING_PCM_FLOAT)){"This decoder's PCM format is unsupported"}}
                else if(i>=0){try{if(info.size>0){val b=decoder.getOutputBuffer(i)!!.duplicate().order(ByteOrder.LITTLE_ENDIAN);b.position(info.offset);b.limit(info.offset+info.size);val bytes=if(encoding==AudioFormat.ENCODING_PCM_FLOAT)4 else 2;require(info.size%(bytes*channels)==0)
                    while(b.remaining()>=bytes)w.sample(if(bytes==4)b.float else b.short/32768f);frames+=info.size/(bytes*channels);require(frames<=rate*7200L){"Choose audio up to two hours long"};require(out.parentFile!!.usableSpace>32_000_000){"Not enough free space"};last=System.nanoTime();if(duration>0)progress((info.presentationTimeUs.toFloat()/duration).coerceIn(0f,1f))};done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0}finally{decoder.releaseOutputBuffer(i,false)}}
            }};require(frames>0){"The audio file is empty"};return AudioSource(out,rate,channels,frames,name)
        }finally{runCatching{codec?.stop()};codec?.release();extractor.release()}
    }
    fun aac(wave:File,output:File,rate:Int,channels:Int,check:()->Unit,progress:(Float)->Unit){
        val encoder=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);var muxer:MediaMuxer?=null
        try{
            val fmt=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,rate,channels).apply{setInteger(MediaFormat.KEY_BIT_RATE,if(channels==1)128000 else 192000);setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,16384)}
            encoder.configure(fmt,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);encoder.start();val mux=MediaMuxer(output.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);muxer=mux
            var track=-1;var started=false;var sent=0L;var eos=false;var done=false;var last=System.nanoTime();val info=MediaCodec.BufferInfo();val total=wave.length()-44
            RandomAccessFile(wave,"r").use{r->r.seek(44);while(!done){check();require(System.nanoTime()-last<30_000_000_000L){"AAC encoding stopped responding. Save as WAV instead."}
                if(!eos){val i=encoder.dequeueInputBuffer(10000);if(i>=0){val b=encoder.getInputBuffer(i)!!;b.clear();val count=min((total-sent).coerceAtLeast(0),b.remaining().toLong()).toInt()/(channels*2)*(channels*2);val bytes=ByteArray(count);r.readFully(bytes);b.put(bytes);encoder.queueInputBuffer(i,0,count,sent/(channels*2)*1_000_000/rate,if(count==0)MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0);sent+=count;eos=count==0;progress(sent.toFloat()/max(1,total))}}
                val i=encoder.dequeueOutputBuffer(info,10000);if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){check(!started);track=mux.addTrack(encoder.outputFormat);mux.start();started=true}
                else if(i>=0){try{if(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0&&info.size>0){check(started);val b=encoder.getOutputBuffer(i)!!;b.position(info.offset);b.limit(info.offset+info.size);mux.writeSampleData(track,b,info);last=System.nanoTime()};done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0}finally{encoder.releaseOutputBuffer(i,false)}}
            }};check(started);mux.stop()
        }finally{runCatching{encoder.stop()};encoder.release();muxer?.release()}
    }
}
