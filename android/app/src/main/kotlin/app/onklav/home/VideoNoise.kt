@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package app.onklav.home

import androidx.media3.common.C
import androidx.media3.common.audio.*
import java.nio.ByteBuffer
import kotlin.math.*

/** Conventional adaptive three-band expansion plus optional 80 Hz rumble removal. No learned model. */
internal class VideoNoise(private val amount:Float,private val rumble:Boolean):BaseAudioProcessor(){
    private var low=DoubleArray(0);private var high=DoubleArray(0);private var dc=DoubleArray(0)
    private var envelope=DoubleArray(0);private var floor=DoubleArray(0);private var gains=DoubleArray(0);private var frames=0L
    override fun onConfigure(format:AudioProcessor.AudioFormat):AudioProcessor.AudioFormat{if(format.encoding !in listOf(C.ENCODING_PCM_16BIT,C.ENCODING_PCM_FLOAT))throw AudioProcessor.UnhandledAudioFormatException(format);return format}
    override fun onFlush(){frames=0;val n=inputAudioFormat.channelCount;low=DoubleArray(n);high=DoubleArray(n);dc=DoubleArray(n);envelope=DoubleArray(n*3);floor=DoubleArray(n*3){if(it%3==1).004 else .002};gains=DoubleArray(n*3){1.0}}
    override fun queueInput(inputBuffer:ByteBuffer){val channels=inputAudioFormat.channelCount;val sr=inputAudioFormat.sampleRate;val floating=inputAudioFormat.encoding==C.ENCODING_PCM_FLOAT;val bytes=if(floating)4 else 2;val output=replaceOutputBuffer(inputBuffer.remaining())
        val a=1-exp(-2*PI*250/sr);val b=1-exp(-2*PI*4000/sr);val d=1-exp(-2*PI*80/sr);val env=1-exp(-1.0/(sr*.01));val attack=1-exp(-1.0/(sr*.003));val release=1-exp(-1.0/(sr*.08));val rise=exp(1.0/(sr*8))
        while(inputBuffer.remaining()>=bytes*channels){repeat(channels){channel->val raw=if(floating)inputBuffer.float.toDouble()else inputBuffer.short/32768.0;val x=if(raw.isFinite())raw else 0.0;dc[channel]+=d*(x-dc[channel]);val source=if(rumble)x-dc[channel]else x;low[channel]+=a*(source-low[channel]);high[channel]+=b*(source-high[channel]);var out=0.0
            for(band in 0..2){val value=when(band){0->low[channel];1->high[channel]-low[channel];else->source-high[channel]};val k=channel*3+band;envelope[k]+=env*(abs(value)-envelope[k]);if(frames>sr/5)floor[k]=min(floor[k]*rise,max(.0005,envelope[k]));val open=((envelope[k]-floor[k]*1.5)/(floor[k]*2)).coerceIn(0.0,1.0);val target=1-amount*.88*(1-open);gains[k]+=(if(target>gains[k])attack else release)*(target-gains[k]);out+=value*gains[k]}
            out=out.coerceIn(-1.0,1.0);if(floating)output.putFloat(out.toFloat())else output.putShort((out*32768).roundToInt().coerceIn(-32768,32767).toShort())
        };frames++};output.flip()
    }
}
