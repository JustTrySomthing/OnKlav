package app.onklav.home

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.RandomAccessFile

/** Track presence comes from the extractor, not optional HAS_AUDIO retriever tags.
 * Native MP3 imports become WAV: read their exact PCM duration without vendor metadata.
 */
internal fun videoAudioDuration(file:File):Long{
    RandomAccessFile(file,"r").use{r->
        if(r.length()>=12){val header=ByteArray(12);r.readFully(header)
            if(String(header,0,4)=="RIFF"&&String(header,8,4)=="WAVE"){
                var bytesPerSecond=0L;var dataBytes=-1L
                while(r.filePointer+8<=r.length()){
                    val tag=ByteArray(4);r.readFully(tag);val size=Integer.reverseBytes(r.readInt()).toLong()and 0xffffffffL;val start=r.filePointer
                    require(size<=r.length()-start){"The WAV audio is incomplete"}
                    if(String(tag)=="fmt "){require(size>=16);val format=java.lang.Short.reverseBytes(r.readShort()).toInt()and 65535;require(format in listOf(1,3,0xfffe)){"Unsupported WAV encoding"};r.skipBytes(6);bytesPerSecond=Integer.reverseBytes(r.readInt()).toLong()and 0xffffffffL}
                    if(String(tag)=="data")dataBytes=size
                    r.seek((start+size+(size and 1)).coerceAtMost(r.length()))
                }
                require(bytesPerSecond>0&&dataBytes>0){"The WAV audio has no samples"};return dataBytes*1000/bytesPerSecond
            }
        }
    }
    val extractor=MediaExtractor()
    try{
        extractor.setDataSource(file.path)
        val format=(0 until extractor.trackCount).map{extractor.getTrackFormat(it)}.firstOrNull{it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true}?:error("This file has no readable audio track")
        if(format.containsKey(MediaFormat.KEY_DURATION)){val duration=format.getLong(MediaFormat.KEY_DURATION)/1000;if(duration>0)return duration}
        val meta=MediaMetadataRetriever();return try{meta.setDataSource(file.path);meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:error("Unable to read the audio duration")}finally{meta.release()}
    }finally{extractor.release()}
}
