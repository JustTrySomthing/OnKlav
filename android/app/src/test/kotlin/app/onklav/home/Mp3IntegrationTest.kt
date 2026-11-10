package app.onklav.home

import android.app.Application
import android.content.*
import android.content.pm.ProviderInfo
import android.database.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.Mp3Codec
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.*
import java.io.File
import java.nio.*
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class Mp3IntegrationTest {
 private val root get()=File(System.getProperty("onklav.project.root"))
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 @Test fun independentVbrMpeg2AndMpeg25FilesDecodeThroughTheEditorsSoftwareImport(){
  val folder=createTempDir();try{
   for((name,rate,ch)in listOf(Triple("vbr-stereo",44100,2),Triple("mpeg2-mono",22050,1),Triple("mpeg25-mono",11025,1))){
    val input=File(root,"docs/fixtures/mp3/$name.mp3");val original=input.readBytes();val source=try{AudioEditMedia.decode(input,File(folder,"$name.pcm"),"$name.mp3",{},{})}catch(e:Exception){throw IllegalStateException("Fixture $name: ${e.message}",e)}
    assertEquals(rate,source.rate);assertEquals(ch,source.channels);assertEquals(.4,source.frames.toDouble()/rate,.04);assertTrue(source.file.length()>0);assertArrayEquals(original,input.readBytes())
   }
   assertTrue(filePickerAccepts(JSONObject().put("name","Track.MP3").put("mime","application/octet-stream"),listOf("audio/*")))
  }finally{folder.deleteRecursively()}
 }
 @Test fun pitchedAudioCanBeExportedAsMp3AndReopened(){
  val folder=createTempDir();try{
   val file=File(root,"docs/fixtures/mp3/vbr-stereo.mp3");val source=AudioEditMedia.decode(file,File(folder,"source.pcm"),"Tone.mp3",{},{})
   val wave=File(folder,"edited.wav");AudioEditEngine.render(source,AudioEdit(listOf(AudioRegion(0,source.frames)),pitch=12f),wave,File(folder,"work"),null)
   val encoded=File(folder,"edited.mp3");Mp3Codec.encode(wave,encoded);val decoded=File(folder,"decoded.wav");val info=Mp3Codec.decode(encoded,decoded);assertEquals(2,info.channels)
   val b=ByteBuffer.wrap(decoded.readBytes()).order(ByteOrder.LITTLE_ENDIAN);b.position(44);val left=ArrayList<Float>();while(b.remaining()>=4){left+=b.short/32768f;b.short}
   var crossings=0;val from=left.size/4;val to=left.size*3/4;for(i in from+1 until to)if(left[i-1]<0&&left[i]>=0)crossings++
   assertEquals(880.0,crossings*info.rate.toDouble()/(to-from),30.0)
  }finally{folder.deleteRecursively()}
 }
 class FixtureProvider:ContentProvider(){lateinit var file:File;override fun onCreate()=true;override fun getType(uri:Uri)="audio/mpeg";override fun query(uri:Uri,p:Array<out String>?,s:String?,a:Array<out String>?,o:String?)=MatrixCursor(p?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)).apply{addRow(columnNames.map{if(it==OpenableColumns.DISPLAY_NAME)"Tone.mp3"else file.length()})};override fun openFile(uri:Uri,mode:String)=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);override fun insert(uri:Uri,v:ContentValues?):Uri?=null;override fun delete(uri:Uri,s:String?,a:Array<out String>?)=0;override fun update(uri:Uri,v:ContentValues?,s:String?,a:Array<out String>?)=0}
 @Test(timeout=60000) fun signedSdkAudiobookPlaybackUsesBundledFallbackAndRevocationStillStopsIt()=runBlocking<Unit>{
  AppSdk.bootstrap(c);val row=AppSdk.list(c).first{it.child("manifest").optString("id")=="app.onklav.audiobooks"};val owner=JSONObject().put("id","app.onklav.audiobooks").put("publisher",row.child("manifest").getString("publisher")).put("hash",row.getString("hash")).put("name","AudioBooks")
  val fixture=File(root,"docs/fixtures/mp3/vbr-stereo.mp3");val original=fixture.readBytes();val provider=FixtureProvider().apply{file=fixture;attachInfo(c,ProviderInfo().apply{authority="test.mp3";exported=false;grantUriPermissions=true})};ShadowContentResolver.registerProviderInternal("test.mp3",provider)
  val book=SdkAudioBooks.import(c,owner,listOf(Uri.parse("content://test.mp3/tone")),"mp3-fixture")
  ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(400,0)};SdkAudioBooks.start(c,owner,book.getString("id"));val controller=Robolectric.buildService(SdkPlaybackService::class.java).create()
  try{controller.get().onStartCommand(shadowOf(c).nextStartedService,0,1);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
   val player=SdkMedia::class.java.getDeclaredField("player").apply{isAccessible=true}.get(SdkMedia)as MediaPlayer
   shadowOf(player).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN,MediaPlayer.MEDIA_ERROR_UNSUPPORTED)
   var wave:File?=null
   repeat(200){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));wave=SdkMedia::class.java.getDeclaredField("compatibleMp3").apply{isAccessible=true}.get(SdkMedia)as? File;if(wave==null)Thread.sleep(5)}
   assertNotNull(wave);assertTrue(wave!!.isFile);assertEquals("RIFF",String(wave!!.readBytes(),0,4));assertArrayEquals(original,fixture.readBytes())
   val grants=row.getJSONArray("grants");val kept=JSONArray((0 until grants.length()).map{grants.getString(it)}.filter{it!="audio.playback"});AppSdk.sync(c,JSONObject().put("action","permissions").put("id",owner.getString("id")).put("grants",kept));shadowOf(Looper.getMainLooper()).idle();assertEquals("idle",SdkMedia.status(owner).getString("state"));assertFalse(wave!!.exists())
  }finally{SdkMedia.stop();controller.destroy()}
 }
}
