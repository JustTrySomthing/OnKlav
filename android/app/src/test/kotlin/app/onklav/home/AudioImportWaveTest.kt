package app.onklav.home

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AudioImportWaveTest {
 @Test fun downloadedAudioBecomesRealMp3AndReopensWithExpectedDuration(){
  val dir=kotlin.io.path.createTempDirectory("download-mp3").toFile()
  try{val rate=44100;val frames=rate/2;val input=File(dir,"source.wav")
   input.outputStream().buffered().use{out->AudioEditEngine.writeWaveHeader(out,rate,2,frames.toLong());repeat(frames){i->repeat(2){channel->val n=(12000*sin(2*PI*(if(channel==0)440 else 880)*i/rate)).roundToInt();out.write(n and 255);out.write((n shr 8)and 255)}}}
   val wave=File(dir,"converted.wav");AudioImportWave.convert(input,wave,"Download",{},{})
   val mp3=File(dir,"download.mp3");app.onklav.shared.Mp3Codec.encode(wave,mp3,192)
   assertTrue(app.onklav.shared.Mp3Codec.isMp3(mp3));val decoded=File(dir,"decoded.wav");val info=app.onklav.shared.Mp3Codec.decode(mp3,decoded)
   assertEquals(2,info.channels);assertEquals(rate,info.rate);assertTrue(decoded.length()>frames*3)
  }finally{dir.deleteRecursively()}
 }
 @Test fun cancelledDecodeRemovesPartialOutputAndKeepsSource(){
  val dir=kotlin.io.path.createTempDirectory("download-cancel").toFile();try{val source=File(dir,"source.wav");source.outputStream().use{AudioEditEngine.writeWaveHeader(it,44100,1,5000);it.write(ByteArray(10000))};val output=File(dir,"result.wav")
   try{AudioImportWave.convert(source,output,"Download",{throw kotlinx.coroutines.CancellationException()},{ });fail()}catch(_:kotlinx.coroutines.CancellationException){};assertFalse(output.exists());assertTrue(source.isFile);assertEquals(1,dir.listFiles()!!.size)
  }finally{dir.deleteRecursively()}
 }
 private fun wav(file:File,format:Int,bits:Int,channels:Int,data:ByteArray){
  val header=java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
  header.put("RIFF".toByteArray());header.putInt(data.size+36);header.put("WAVEfmt ".toByteArray());header.putInt(16);header.putShort(format.toShort());header.putShort(channels.toShort());header.putInt(8000);header.putInt(8000*channels*bits/8);header.putShort((channels*bits/8).toShort());header.putShort(bits.toShort());header.put("data".toByteArray());header.putInt(data.size)
  file.outputStream().use{it.write(header.array());it.write(data)}
 }
 @Test fun bufferedWaveImportPreservesAllPcmDepthsAndFloatingHeadroom(){
  val dir=kotlin.io.path.createTempDirectory("wave-depths").toFile()
  try{
   for(bits in listOf(8,16,24,32)){
    val bytes=java.nio.ByteBuffer.allocate(3*bits/8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    when(bits){8->bytes.put(byteArrayOf(0,128.toByte(),255.toByte()));16->{bytes.putShort(Short.MIN_VALUE);bytes.putShort(0);bytes.putShort(Short.MAX_VALUE)};24->bytes.put(byteArrayOf(0,0,128.toByte(),0,0,0,255.toByte(),255.toByte(),127));32->{bytes.putInt(Int.MIN_VALUE);bytes.putInt(0);bytes.putInt(Int.MAX_VALUE)}}
    val input=File(dir,"$bits.wav");wav(input,1,bits,1,bytes.array());val source=AudioEditMedia.wave(input,File(dir,"$bits.pcm"),"Depth",{},{})!!
    AudioPcmReader(source.file,1).use{assertEquals(-1f,it.get(0,0),.000001f);assertEquals(0f,it.get(1,0),0f);assertTrue(it.get(2,0)>.99f)}
   }
   val data=java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply{putFloat(-1.5f);putFloat(.5f);putFloat(Float.NaN);putFloat(1.25f)}
   val input=File(dir,"float.wav");wav(input,3,32,1,data.array());val source=AudioEditMedia.wave(input,File(dir,"float.pcm"),"Float",{},{})!!
   AudioPcmReader(source.file,1).use{assertEquals(-1.5f,it.get(0,0),0f);assertEquals(0f,it.get(2,0),0f);assertEquals(1.25f,it.get(3,0),0f)}
   val out=File(dir,"quiet.wav");AudioEditEngine.render(source,AudioEdit(listOf(AudioRegion(0,4)),gain=-6.0206f,limiter=false),out,File(dir,"work"),null)
   val b=java.nio.ByteBuffer.wrap(out.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN);assertEquals(-.75f,b.getShort(44)/32768f,.0001f)
  }finally{dir.deleteRecursively()}
 }
 @Test fun incompletePcmFramesAreRejectedRatherThanSilentlyTruncated(){
  val dir=kotlin.io.path.createTempDirectory("wave-frame").toFile();try{val input=File(dir,"bad.wav");wav(input,1,16,2,ByteArray(6));assertThrows(IllegalArgumentException::class.java){AudioEditMedia.wave(input,File(dir,"out.pcm"),"Bad",{},{})}}finally{dir.deleteRecursively()}
 }
 @Test fun abortedExportClosesItsDescriptorWithoutDeletingTheDestination(){
  val c=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
  val destination=File(c.cacheDir,"interrupted.wav").apply{writeBytes(ByteArray(100))}
  var descriptor:android.os.ParcelFileDescriptor?=null;var deletes=0
  val provider=object:android.content.ContentProvider(){
   override fun onCreate()=true;override fun getType(uri:android.net.Uri)="audio/wav"
   override fun openFile(uri:android.net.Uri,mode:String)=android.os.ParcelFileDescriptor.open(destination,android.os.ParcelFileDescriptor.MODE_READ_WRITE).also{descriptor=it}
   override fun query(uri:android.net.Uri,p:Array<out String>?,s:String?,a:Array<out String>?,sort:String?):android.database.Cursor?=null
   override fun insert(uri:android.net.Uri,v:android.content.ContentValues?):android.net.Uri?=null
   override fun update(uri:android.net.Uri,v:android.content.ContentValues?,s:String?,a:Array<out String>?)=0
   override fun delete(uri:android.net.Uri,s:String?,a:Array<out String>?):Int{deletes++;return 1}
  }
  provider.attachInfo(c,android.content.pm.ProviderInfo().apply{authority="audio.copy"});org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("audio.copy",provider)
  val input=File(c.cacheDir,"complete.wav").apply{writeBytes(ByteArray(1000){23})}
  assertThrows(kotlinx.coroutines.CancellationException::class.java){writeAudioExport(c.contentResolver,android.net.Uri.parse("content://audio.copy/output"),input,{}, {throw kotlinx.coroutines.CancellationException("Cancel before publication")})}
  assertEquals(0,deletes);assertTrue(destination.isFile);assertFalse(descriptor!!.fileDescriptor.valid())
 }

 @Test fun riffBoundaryIncludesTheHeaderAndOversizedExportsFailBeforeProcessing(){
  val limit=(0xffffffffL-36)/4
  val out=java.io.ByteArrayOutputStream();AudioEditEngine.writeWaveHeader(out,192000,2,limit)
  assertEquals(44,out.size());val header=java.nio.ByteBuffer.wrap(out.toByteArray()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
  assertEquals(limit*4+36,header.getInt(4).toLong() and 0xffffffffL)
  assertThrows(IllegalArgumentException::class.java){AudioEditEngine.writeWaveHeader(java.io.ByteArrayOutputStream(),192000,2,limit+1)}
  val d=kotlin.io.path.createTempDirectory("wav-limit").toFile();try{
   val missing=File(d,"not-created.pcm");val source=AudioSource(missing,192000,2,192000L*7200,"Long")
   assertThrows(IllegalArgumentException::class.java){AudioEditEngine.render(source,AudioEdit(listOf(AudioRegion(0,source.frames)),tempo=.5f),File(d,"out.wav"),File(d,"work"),null)}
   assertFalse(File(d,"work").exists());assertFalse(File(d,"out.wav").exists())
  }finally{d.deleteRecursively()}
 }

}
