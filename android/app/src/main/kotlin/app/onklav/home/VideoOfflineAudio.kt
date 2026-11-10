@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.*

internal val videoAudioTones=listOf("Original","Speech clarity","Warm","Telephone")
/** Smooth timeline-based ducking: narration role, not neural speech detection. */
internal fun videoDuckGain(at:Long,ranges:List<Pair<Long,Long>>):Float {
    var amount=0f
    for((start,end) in ranges){val attack=((at-(start-200))/200f).coerceIn(0f,1f);val release=((end+400-at)/400f).coerceIn(0f,1f);amount=max(amount,min(attack,release))}
    return 1f-.75f*amount
}
internal class VideoSound(private val pan:Float=0f,private val tone:String="Original",private val offset:Long=0,private val duckRanges:List<Pair<Long,Long>> = emptyList()):BaseAudioProcessor(){
    private var frames=0L;private var low=DoubleArray(0);private var high=DoubleArray(0)
    override fun onConfigure(inputAudioFormat:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat {
        if(inputAudioFormat.encoding !in listOf(C.ENCODING_PCM_16BIT,C.ENCODING_PCM_FLOAT))throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }
    override fun onFlush(){frames=0;low=DoubleArray(inputAudioFormat.channelCount);high=DoubleArray(inputAudioFormat.channelCount)}
    override fun queueInput(inputBuffer:ByteBuffer){
        val floating=inputAudioFormat.encoding==C.ENCODING_PCM_FLOAT;val channels=inputAudioFormat.channelCount;val bytes=if(floating)4 else 2
        val out=replaceOutputBuffer(inputBuffer.remaining());val rate=inputAudioFormat.sampleRate
        val lo=1-exp(-2*PI*(if(tone=="Telephone")300.0 else 240.0)/rate);val hi=1-exp(-2*PI*(if(tone=="Telephone")3400.0 else 3200.0)/rate)
        while(inputBuffer.remaining()>=channels*bytes){val duck=videoDuckGain(offset+frames*1000/rate,duckRanges)
            repeat(channels){channel->val x=if(floating)inputBuffer.float.toDouble()else inputBuffer.short/32768.0
                low[channel]+=lo*(x-low[channel]);high[channel]+=hi*(x-high[channel])
                val shaped=when(tone){"Speech clarity"->x*.7+(high[channel]-low[channel])*.65;"Warm"->x*.8+low[channel]*.35;"Telephone"->(high[channel]-low[channel])*1.5;else->x}
                // Stereo balance attenuates the opposite side; mono/multichannel remain unchanged.
                val balance=if(channels==2)if(channel==0)1-max(0f,pan)else 1+min(0f,pan)else 1f
                val y=(shaped*balance*duck).coerceIn(-1.0,1.0)
                if(floating)out.putFloat(y.toFloat())else out.putShort((y*32768).roundToInt().coerceIn(-32768,32767).toShort())
            };frames++
        };out.flip()
    }
}
