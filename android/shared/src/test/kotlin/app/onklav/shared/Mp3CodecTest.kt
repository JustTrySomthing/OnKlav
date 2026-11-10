package app.onklav.shared

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.*
import java.nio.file.Files
import kotlin.math.*

class Mp3CodecTest {
 private fun wav(file:File,rate:Int,channels:Int,seconds:Double=.3){val frames=(rate*seconds).toInt();val b=ByteBuffer.allocate(44+frames*channels*2).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".toByteArray());b.putInt(b.capacity()-8);b.put("WAVEfmt ".toByteArray());b.putInt(16);b.putShort(1);b.putShort(channels.toShort());b.putInt(rate);b.putInt(rate*channels*2);b.putShort((channels*2).toShort());b.putShort(16);b.put("data".toByteArray());b.putInt(frames*channels*2);repeat(frames){i->repeat(channels){c->b.putShort((12000*sin(2*PI*(if(c==0)440 else 880)*i/rate)).toInt().toShort())}};file.writeBytes(b.array())}
 @Test fun realEncoderDecoderRoundtripPreservesRateDurationAndDistinctStereoChannels(){
  val dir=Files.createTempDirectory("mp3-roundtrip").toFile()
  try{for((rate,channels)in listOf(44100 to 2,22050 to 1,11025 to 1,48000 to 2)){
   val input=File(dir,"source.wav");wav(input,rate,channels);val original=input.readBytes();val mp3=File(dir,"encoded.mp3");Mp3Codec.encode(input,mp3);assertTrue(Mp3Codec.isMp3(mp3));assertTrue(mp3.length()>500)
   val decoded=File(dir,"decoded.wav");val info=Mp3Codec.decode(mp3,decoded);assertEquals(rate,info.rate);assertEquals(channels,info.channels);assertEquals(.3,info.frames.toDouble()/rate,.035);assertArrayEquals(original,input.readBytes())
   val b=ByteBuffer.wrap(decoded.readBytes()).order(ByteOrder.LITTLE_ENDIAN);b.position(44);val values=Array(channels){ArrayList<Double>()};while(b.remaining()>=2*channels)for(c in 0 until channels)values[c].add(b.short/32768.0)
   fun amplitude(c:Int,hz:Int):Double{val a=values[c];var x=0.0;var y=0.0;for(i in a.indices){x+=a[i]*cos(2*PI*hz*i/rate);y+=a[i]*sin(2*PI*hz*i/rate)};return hypot(x,y)*2/a.size}
   assertTrue(amplitude(0,440)>.20);if(channels==2){assertTrue(amplitude(1,880)>.20);assertTrue(amplitude(0,440)>amplitude(0,880)*4)}
  }}finally{dir.deleteRecursively()}
 }
 @Test fun cancellationMalformedInputAndMimeAliasesAreHandled(){
  val dir=Files.createTempDirectory("mp3-cancel").toFile();try{
   val input=File(dir,"source.wav");wav(input,44100,1);val out=File(dir,"cancel.mp3")
   var checks=0;assertThrows(IllegalStateException::class.java){Mp3Codec.encode(input,out,check={if(++checks>3)error("cancel")})};assertFalse(out.exists());assertTrue(input.exists())
   val bad=File(dir,"bad.mp3").apply{writeBytes(byteArrayOf(-1,-5,-112,0))};val wave=File(dir,"bad.wav");assertThrows(Exception::class.java){Mp3Codec.decode(bad,wave)};assertFalse(wave.exists())
   assertEquals("audio/mpeg",Mp3Codec.mime("application/octet-stream","SONG.MP3"));assertEquals("audio/mpeg",Mp3Codec.mime("audio/x-mp3","Song"));assertEquals("video/mp4",Mp3Codec.mime("video/mp4","Clip.mp4"))
  }finally{dir.deleteRecursively()}
 }
}
