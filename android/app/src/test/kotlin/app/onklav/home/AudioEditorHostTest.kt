package app.onklav.home

import java.io.File
import java.nio.*
import kotlin.math.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.json.*
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AudioEditorHostTest{
 private fun fixture(dir:File,rate:Int=16000,seconds:Double=2.0,channels:Int=1,sample:(Int,Int)->Float):AudioSource{
  dir.mkdirs();val file=File(dir,"source.pcm");val frames=(rate*seconds).toInt();AudioPcmWriter(file).use{w->repeat(frames){i->repeat(channels){c->w.sample(sample(i,c))}}};return AudioSource(file,rate,channels,frames.toLong(),"Fixture.wav")
 }
 private fun readWave(file:File):FloatArray{val b=ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN);assertEquals("RIFF",String(b.array(),0,4));b.position(44);return FloatArray(b.remaining()/2){b.short/32768f}}
 private fun render(s:AudioSource,e:AudioEdit,dir:File,n:FloatArray?=null):FloatArray{val f=File(dir,"result.wav");AudioEditEngine.render(s,e,f,File(dir,"work"),n);return readWave(f)}
 private fun rms(a:FloatArray,from:Int=0,to:Int=a.size)=sqrt((from until to).sumOf{a[it].toDouble().pow(2)}/(to-from))
 private fun frequency(a:FloatArray,rate:Int):Double{val from=a.size/4;val to=a.size*3/4;var crossing=0;for(i in from+1 until to)if(a[i-1]<0&&a[i]>=0)crossing++;return crossing*rate.toDouble()/(to-from)}
 @Test fun editsKeepSourceAndRoundTripWithStereoSilence(){
  val d=createTempDir();try{val s=fixture(d,8000,.1,2){i,c->if(c==0)i/1000f else -i/1000f};val original=s.file.readBytes();val e=AudioEdit(listOf(AudioRegion(0,s.frames)))
   val cut=e.cut(100,200).silence(100,200).duplicate(0,50).trim(25,700);assertEquals(675,cut.frames.toInt());val restored=AudioEdit.parse(cut.json(),s.frames);assertEquals(cut,restored)
   val data=render(s,cut,d);assertEquals(1350,data.size);assertEquals(data[20],-data[21],.0001f);assertTrue(data.any{it==0f});assertArrayEquals(original,s.file.readBytes())
   assertThrows(IllegalArgumentException::class.java){AudioEdit.parse(JSONObject().put("regions",JSONArray("[[-1,99,false]]")),s.frames)}
   val imported=File(d,"reimport.pcm");val round=AudioEditMedia.wave(File(d,"result.wav"),imported,"Roundtrip",{},{});assertNotNull(round);assertEquals(675,round!!.frames.toInt());assertEquals(2,round.channels)
  }finally{d.deleteRecursively()}
 }
 @Test fun pitchAndTempoAreIndependentAndStereoRemainsLinked(){val d=createTempDir();try{
  val s=fixture(d,16000,2.0,2){i,c->(.2*sin(2*PI*440*i/16000)*(if(c==0)1.0 else .5)).toFloat()};val e=AudioEdit(listOf(AudioRegion(0,s.frames)))
  val shifted=render(s,e.copy(pitch=12f),d);val left=FloatArray(shifted.size/2){shifted[it*2]};assertEquals(s.frames.toInt(),left.size);assertEquals(880.0,frequency(left,16000),15.0)
  val faster=render(s,e.copy(tempo=1.5f),d);val mono=FloatArray(faster.size/2){faster[it*2]};assertEquals((s.frames/1.5).toInt(),mono.size);assertEquals(440.0,frequency(mono,16000),12.0)
  for(i in 3000 until min(faster.size/2,9000))assertEquals(faster[i*2]*.5f,faster[i*2+1],.0001f)
 }finally{d.deleteRecursively()}}
 @Test fun gainToneFadesNormalizationAndOverloadProtectionWork(){val d=createTempDir();try{
  val s=fixture(d){i,_->(.15*sin(2*PI*120*i/16000)).toFloat()};val e=AudioEdit(listOf(AudioRegion(0,s.frames)))
  val base=render(s,e,d);val gain=render(s,e.copy(gain=6f),d);assertEquals(2.0,rms(gain)/rms(base),.03)
  val bass=render(s,e.copy(bass=6f),d);assertTrue(rms(bass)>rms(base)*1.8)
  val faded=render(s,e.copy(fadeIn=.5f,fadeOut=.5f),d);assertTrue(rms(faded,0,1000)<rms(base,0,1000)*.2);assertTrue(rms(faded,faded.size-1000,faded.size)<rms(base,0,1000)*.2)
  val normal=render(s,e.copy(normalize=true),d);assertEquals(10.0.pow(-1.0/20),normal.maxOf{abs(it)}.toDouble(),.001)
  val loud=render(s,e.copy(gain=24f,bass=12f),d);assertTrue(loud.maxOf{abs(it)}<.99f)
  val dc=fixture(d,16000,1.0){_,_->.3f};val cleaned=render(dc,AudioEdit(listOf(AudioRegion(0,dc.frames)),dc=true),d);assertTrue(rms(cleaned,8000,15000)<.0001)
 }finally{d.deleteRecursively()}}
 @Test fun learnedSpectralNoiseReductionReducesNoiseAndKeepsTone(){val d=createTempDir();try{
  val random=java.util.Random(72);val s=fixture(d,16000,3.0){i,_->((random.nextDouble()-.5)*.12+if(i>=8000).25*sin(2*PI*500*i/16000)else 0.0).toFloat()};val e=AudioEdit(listOf(AudioRegion(0,s.frames)))
  val profile=AudioEditEngine.profile(s,e,0,8000);val before=render(s,e,d);val after=render(s,e.copy(noise=.7f),d,profile)
  assertTrue(rms(after,2048,7000)<rms(before,2048,7000)*.7);assertEquals(500.0,frequency(after.copyOfRange(16000,40000),16000),10.0);assertTrue(rms(after,16000,40000)>.12)
  assertThrows(IllegalArgumentException::class.java){AudioEditEngine.profile(s,e,0,10)}
  val file=File(d,"cancel.wav");assertThrows(IllegalStateException::class.java){AudioEditEngine.render(s,e,file,File(d,"cancel-work"),null,check={throw IllegalStateException("Cancelled")})};assertFalse(File(d,"cancel-work/timing.pcm").exists())
 }finally{d.deleteRecursively()}}
 @Test fun signedAppLaunchAndRevocableStorageAreEnforced(){val c=ApplicationProvider.getApplicationContext<Context>();runBlocking{AppSdk.bootstrap(c)}
  val launch=AppSdk.sync(c,JSONObject().put("action","launch").put("id","app.onklav.audioeditor")) as JSONObject
  assertEquals("Audio Editor",launch.getJSONObject("frame").getString("title"));assertTrue(audioEditorAccess(c));assertTrue(audioEditorAccess(c,"audio.playback"))
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.audioeditor").put("grants",JSONArray()));assertFalse(audioEditorAccess(c));assertFalse(audioEditorAccess(c,"audio.playback"))
 }
 @Test fun dcRemovalKeepsLowBassAtDifferentHighSampleRates(){
  val d=createTempDir();try{
   for(rate in listOf(48000,96000)){
    val source=fixture(d,rate,2.0){i,_->(.25+.1*sin(2*PI*20*i/rate)).toFloat()}
    val sound=render(source,AudioEdit(listOf(AudioRegion(0,source.frames)),dc=true),d)
    val ratio=rms(sound,rate,rate*2)/(.1/sqrt(2.0))
    assertTrue("DC removal should retain 20 Hz at $rate Hz; ratio=$ratio",ratio in .98..1.01)
    assertTrue(abs(sound.copyOfRange(rate,rate*2).average())<.001)
   }
  }finally{d.deleteRecursively()}
 }

}
