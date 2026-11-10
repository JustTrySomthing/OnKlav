package app.onklav.home

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AudioPreviewModelTest{
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private var model:AudioEditorModel?=null
 private fun waitFor(condition:()->Boolean){val end=System.nanoTime()+15_000_000_000;while(!condition()&&System.nanoTime()<end){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20));Thread.sleep(10)};assertTrue("Editor operation did not finish within its bound",condition())}
 private fun load():AudioEditorModel{
  runBlocking{AppSdk.bootstrap(c)};val dir=File(c.filesDir,"audio-editor").apply{mkdirs()};val f=File(dir,"00000000-0000-0000-0000-000000000001.pcm");AudioPcmWriter(f).use{w->repeat(8000*30){w.sample(.2f)}};val e=AudioEdit(listOf(AudioRegion(0,240000)))
  File(dir,"project.json").writeText(JSONObject().put("file",f.name).put("rate",8000).put("channels",1).put("frames",240000).put("name","Clip.wav").put("edit",e.json()).toString());val m=AudioEditorModel(c);model=m;waitFor{m.source!=null&&!m.busy};return m
 }
 @After fun cleanup(){model?.cancel();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30))}
 @Test(timeout=30000) fun seekingIsRelativeToTheAuditionWindowAndOutsideSeekingStopsIt(){val m=load();ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(12000,0)};m.seek(.5f);m.preview();waitFor{m.playing&&!m.busy};assertEquals(.5f,m.cursor,.002f)
  m.seek(.7f);val p=AudioEditorModel::class.java.getDeclaredField("player").apply{isAccessible=true}.get(m) as android.media.MediaPlayer;waitFor{p.currentPosition in 5900..6400}
  m.seek(.1f);assertFalse(m.playing);assertEquals(.1f,m.cursor,.0001f)
 }
 @Test(timeout=30000) fun ownershipInterruptionDuringDecoderPreparationCancelsWithoutWaitingForTimeout(){val m=load();ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(12000,100000)};m.preview();waitFor{AudioEditorModel::class.java.getDeclaredField("player").apply{isAccessible=true}.get(m)!=null}
  val next=Any();HostSpeechOwner.claim(next){};waitFor{!m.busy};assertFalse(m.playing);assertNull(AudioEditorModel::class.java.getDeclaredField("player").apply{isAccessible=true}.get(m));HostSpeechOwner.release(next)
 }
 @Test(timeout=30000) fun reverseTrimTargetsTheVisibleSectionAndTempoUpdatesTimelineTimes(){val m=load();m.change(m.edit.copy(reverse=true,tempo=2f));m.commit();assertEquals(15f,m.duration,.001f)
  m.select(0f..(1f/3));m.operation("Trim");assertEquals(listOf(AudioRegion(160000,240000)),m.edit.regions);assertTrue(m.edit.reverse);assertEquals(5f,m.duration,.001f)
  m.history(true);assertEquals(240000L,m.edit.frames);assertTrue(m.edit.reverse);assertEquals(15f,m.duration,.001f)
 }

 @Test(timeout=30000) fun reverseRefreshesWaveformAndShortEditsKeepFadeValuesHonest(){
  val m=load();AudioPcmWriter(m.source!!.file).use{w->repeat(240000){w.sample(if(it<120000).1f else .8f)}}
  m.change(m.edit.copy(reverse=true,fadeIn=20f,fadeOut=20f));m.commit();waitFor{m.peaks.isNotEmpty()&&m.peaks.first()>.7f&&m.peaks.last()<.2f}
  m.select(0f..(1f/30));m.operation("Trim");assertEquals(1f,m.duration,.001f);assertEquals(1f,m.edit.fadeIn,.001f);assertEquals(1f,m.edit.fadeOut,.001f)
  m.change(m.edit.copy(tempo=2f));m.commit();assertEquals(.5f,m.edit.fadeIn,.001f)
  val json=JSONObject(File(c.filesDir,"audio-editor/project.json").readText()).getJSONObject("edit");assertEquals(.5,json.getDouble("fadeOut"),.001)
  m.history(true);assertEquals(1f,m.edit.fadeIn,.001f);assertEquals(1f,m.duration,.001f)
 }
 @Test(timeout=30000) fun failedHistorySavePreservesTheCurrentEditAndBothStacks(){
  val m=load();m.change(m.edit.copy(gain=6f));m.commit();m.change(m.edit.copy(gain=9f));m.commit()
  val rows=AppSdk.sync(c,JSONObject().put("action","list")) as org.json.JSONArray
  val row=(0 until rows.length()).map{rows.getJSONObject(it)}.first{it.child("manifest").getString("id")=="app.onklav.audioeditor"}
  val grants=org.json.JSONArray(row.getJSONArray("grants").toString())
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.audioeditor").put("grants",org.json.JSONArray()))
  m.history(true);assertEquals(9f,m.edit.gain);assertTrue(m.canUndo);assertFalse(m.canRedo);assertTrue(m.error)
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.audioeditor").put("grants",grants))
  m.history(true);assertEquals(6f,m.edit.gain);assertTrue(m.canRedo);assertFalse(m.error);m.history(false);assertEquals(9f,m.edit.gain)
 }
 class Target:android.content.ContentProvider(){
  lateinit var file:File;var opened=0;var deleted=0;var beforeOpen:(()->Unit)?=null
  override fun onCreate()=true
  override fun getType(uri:android.net.Uri)="audio/wav"
  override fun query(uri:android.net.Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?)=android.database.MatrixCursor(arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)).apply{addRow(arrayOf("Input.wav"))}
  override fun openFile(uri:android.net.Uri,mode:String):android.os.ParcelFileDescriptor{opened++;beforeOpen?.invoke();return android.os.ParcelFileDescriptor.open(file,if(mode=="r")android.os.ParcelFileDescriptor.MODE_READ_ONLY else android.os.ParcelFileDescriptor.MODE_READ_WRITE)}
  override fun delete(uri:android.net.Uri,selection:String?,args:Array<out String>?):Int{deleted++;file.delete();return 1}
  override fun insert(uri:android.net.Uri,values:android.content.ContentValues?):android.net.Uri?=null
  override fun update(uri:android.net.Uri,values:android.content.ContentValues?,selection:String?,args:Array<out String>?)=0
 }
 private fun target(file:File):Target=Target().apply{this.file=file;attachInfo(c,android.content.pm.ProviderInfo().apply{authority="audio.audit"});org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("audio.audit",this)}
 @Test(timeout=30000) fun failedRenderNeverDeletesOrOpensTheChosenDestination(){
  val m=load();val file=File(c.cacheDir,"keep-existing.wav").apply{writeText("Existing data")};val provider=target(file)
  m.change(m.edit.copy(noise=.5f));m.commit();m.export(android.net.Uri.parse("content://audio.audit/output"),false);waitFor{!m.busy}
  assertTrue(m.error);assertEquals(0,provider.opened);assertEquals(0,provider.deleted);assertEquals("Existing data",file.readText())
 }
 @Test(timeout=30000) fun cancellingAnImportKeepsThePreviousDraftAndRemovesTemporaryFiles(){
  val m=load();val before=File(c.filesDir,"audio-editor/project.json").readText();val pcm=m.source!!.file
  val input=File(c.cacheDir,"new.wav").apply{outputStream().use{AudioEditEngine.writeWaveHeader(it,8000,1,8000);it.write(ByteArray(16000))}}
  val provider=target(input);provider.beforeOpen={val gate=java.util.concurrent.CountDownLatch(1);android.os.Handler(Looper.getMainLooper()).post{m.cancel();gate.countDown()};check(gate.await(2,java.util.concurrent.TimeUnit.SECONDS))}
  m.open(android.net.Uri.parse("content://audio.audit/input"));waitFor{!m.busy}
  assertEquals(pcm,m.source!!.file);assertTrue(pcm.isFile);assertEquals(before,File(c.filesDir,"audio-editor/project.json").readText())
  assertEquals(1,File(c.filesDir,"audio-editor").listFiles()!!.count{it.extension=="pcm"});assertFalse(File(c.filesDir,"audio-editor").listFiles()!!.any{it.extension=="input"})
 }
 @Test(timeout=30000) fun mp3LengthLimitIsReportedBeforeAFullRender(){
  val m=load();m.apply(m.edit.copy(regions=List(240){AudioRegion(0,240000)},tempo=.5f))
  val provider=target(File(c.cacheDir,"unused.mp3"));m.export(android.net.Uri.parse("content://audio.audit/output"),false,true)
  assertFalse(m.busy);assertTrue(m.error);assertTrue(m.status.contains("two hours"));assertEquals(0,provider.opened)
 }
 @Test(timeout=30000) fun unpluggingHeadphonesReleasesPreviewAndLateOldRouteEventsAreIgnored(){
  val m=load();ShadowMediaPlayer.setMediaInfoProvider{ShadowMediaPlayer.MediaInfo(12000,0)};m.preview();waitFor{m.playing&&!m.busy}
  val field=AudioEditorModel::class.java.getDeclaredField("noisy").apply{isAccessible=true};val old=field.get(m) as android.content.BroadcastReceiver
  c.sendBroadcast(android.content.Intent(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY));waitFor{!m.playing};assertNull(field.get(m));assertTrue(m.status.contains("headphones"))
  m.preview();waitFor{m.playing&&!m.busy};old.onReceive(c,android.content.Intent(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY));assertTrue(m.playing)
  m.stop();assertNull(field.get(m))
 }

}
