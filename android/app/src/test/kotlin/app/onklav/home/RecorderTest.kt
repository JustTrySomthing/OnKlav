package app.onklav.home

import android.Manifest
import android.app.Application
import android.content.*
import android.content.pm.ProviderInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class RecorderTest{
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private fun id()=UUID.randomUUID().toString().replace("-","")
 private fun wav(file:File){file.outputStream().use{out->out.write(ByteArray(44));repeat(480){out.write(byteArrayOf(1,0))}};RecorderWav.header(file)}
 @Before fun setup(){RecordingRuntime.state.value=RecorderState();shadowOf(c).grantPermissions(Manifest.permission.RECORD_AUDIO)}
 @After fun cleanup(){RecordingRuntime.state.value=RecorderState();RecordingRuntime.factory=null}
 @Test fun clockExcludesPauseAndNeverGoesBackwards(){var now=1000L;val clock=RecorderClock{now};clock.resume();now+=1200;assertEquals(1200L,clock.elapsed());clock.pause();now+=5000;assertEquals(1200L,clock.elapsed());clock.pause();clock.resume();now+=700;clock.resume();assertEquals(1900L,clock.elapsed());assertEquals("1:02:03",recorderTime(3723000));assertEquals("00:00",recorderTime(-1))}
 @Test fun audioMixRetainsStereoAndDoesNotOverflow(){val mix=recorderMix(shortArrayOf(32767,-32768,2000,-2000),shortArrayOf(32767,-2000),4);assertArrayEquals(shortArrayOf(32767,0,0,-2000),mix);assertEquals(0f,recorderLevel(0));assertEquals(1f,recorderLevel(32767));assertTrue(recorderLevel(1000)>0);assertEquals(RecorderSound.MIC,RecorderOptions(RecorderKind.VOICE).sound)}
 @Test fun videoDimensionsKeepAspectWithinPresetAndNeverUpscale(){assertEquals(576 to 1280,recorderDimensions(1080,2400,false));assertEquals(864 to 1920,recorderDimensions(1080,2400,true));assertEquals(640 to 480,recorderDimensions(640,480,true));for((w,h)in listOf(1080 to 2400,2400 to 1080,720 to 1280)){val d=recorderDimensions(w,h,true);assertTrue(d.first%16==0&&d.second%16==0);assertTrue(maxOf(d.first,d.second)<=1920)}}
 @Test fun interruptedWavCanBeFinalizedWithoutChangingItsAudio(){val file=File(c.cacheDir,"interrupted.wav");wav(file);file.appendBytes(byteArrayOf(9));RecorderWav.repair(file);val bytes=file.readBytes();assertEquals(1004,bytes.size);assertEquals("RIFF",bytes.copyOfRange(0,4).toString(Charsets.US_ASCII));assertEquals(960,ByteBuffer.wrap(bytes,40,4).order(ByteOrder.LITTLE_ENDIAN).int);assertTrue(bytes.copyOfRange(44,bytes.size).asList().chunked(2).all{it==listOf(1.toByte(),0.toByte())});assertTrue(runCatching{RecorderWav.repair(File(c.cacheDir,"bad.wav").apply{writeText("not audio")})}.isFailure)}
 @Test fun completedRecordingsUseSignedFilesAndStayReadableAfterRename(){
  kotlinx.coroutines.runBlocking{AppSdk.bootstrap(c)};val id=id();RecorderLibrary.create(c,id,RecorderOptions(RecorderKind.VOICE,format=RecorderFormat.WAV));wav(RecorderLibrary.draft(c,id));val expected=RecorderLibrary.draft(c,id).readBytes();RecorderLibrary.ready(c,id,1200)
  val saved=RecorderLibrary.publish(c,id);assertFalse(RecorderLibrary.file(c,id,"json").exists());assertEquals(listOf(saved.id),RecorderLibrary.list(c,RecorderKind.VOICE).map{it.id})
  RecorderLibrary.rename(c,saved,"Meeting notes");val renamed=RecorderLibrary.list(c,RecorderKind.VOICE).single();assertEquals("Meeting notes.wav",renamed.name);assertEquals(1200L,renamed.duration)
  val provider=RecorderProvider().apply{attachInfo(c,ProviderInfo().apply{authority=c.packageName+".recordings";exported=false})};ShadowContentResolver.registerProviderInternal(c.packageName+".recordings",provider)
  val uri=RecorderLibrary.uri(c,renamed);c.contentResolver.openInputStream(uri)!!.use{assertArrayEquals(expected,it.readBytes())};assertTrue(runCatching{provider.openFile(uri,"w")}.isFailure)
  assertTrue(runCatching{provider.openFile(Uri.parse("content://${c.packageName}.recordings/VOICE/file/../../outside"),"r")}.isFailure)
  RecorderLibrary.delete(c,renamed);assertTrue(RecorderLibrary.list(c,RecorderKind.VOICE).isEmpty());assertTrue(runCatching{provider.openFile(uri,"r")}.isFailure)
 }
 @Test fun filingRetryKeepsOneCopyAndRevocationKeepsTheUnfiledOriginal(){
  kotlinx.coroutines.runBlocking{AppSdk.bootstrap(c)};val id=id();val json=RecorderLibrary.create(c,id,RecorderOptions(RecorderKind.VOICE,format=RecorderFormat.WAV));val file=RecorderLibrary.draft(c,id);wav(file);val bytes=file.readBytes();RecorderLibrary.ready(c,id,20);val metadata=RecorderLibrary.read(c,id).toString();val first=RecorderLibrary.publish(c,id)
  RecorderLibrary.file(c,id,"json").writeText(metadata);RecorderLibrary.draft(c,id).writeBytes(bytes)
  val again=RecorderLibrary.publish(c,id);assertEquals(first.id,again.id);assertEquals(1,RecorderLibrary.list(c,RecorderKind.VOICE).size)
  val pending=id();RecorderLibrary.create(c,pending,RecorderOptions(RecorderKind.VOICE,format=RecorderFormat.WAV));wav(RecorderLibrary.draft(c,pending));RecorderLibrary.ready(c,pending,20)
  AppSdk.sync(c,org.json.JSONObject().put("action","permissions").put("id","app.onklav.files").put("grants",org.json.JSONArray()))
  assertTrue(runCatching{RecorderLibrary.publish(c,pending)}.isFailure);assertTrue(RecorderLibrary.draft(c,pending).isFile);assertTrue(RecorderLibrary.file(c,pending,"json").exists())
 }
 @Test fun interruptedWavRecoversThroughTheAppAndKeepsEveryCompleteSample(){
  kotlinx.coroutines.runBlocking{AppSdk.bootstrap(c)};val token=id();RecorderLibrary.create(c,token,RecorderOptions(RecorderKind.VOICE,format=RecorderFormat.WAV));val original=RecorderLibrary.file(c,token,"audio.part.wav");wav(original);original.appendBytes(byteArrayOf(1,2,9));val expected=original.readBytes().copyOfRange(44,1006)
  val model=RecorderLibraryModel(c)
  fun waitFor(){val limit=System.nanoTime()+15_000_000_000;while(model.busy&&System.nanoTime()<limit){shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));Thread.sleep(10)};assertFalse(model.busy);assertNull(model.error)}
  model.refresh(RecorderKind.VOICE);waitFor();val draft=model.items.single();assertTrue(draft.interrupted);model.recover(draft);waitFor();val saved=model.items.single();assertFalse(saved.pending);val file=RecorderLibrary.resolve(c,saved.kind,saved.id,false).first;assertEquals(1006L,file.length());assertArrayEquals(expected,file.readBytes().copyOfRange(44,1006));assertFalse(RecorderLibrary.file(c,token,"json").exists())
 }
 @Test fun screenStartRequiresApprovalAndConcurrentRecordersCannotStart(){
  assertTrue(runCatching{RecordingRuntime.start(c,RecorderOptions(RecorderKind.SCREEN,sound=RecorderSound.NONE))}.isFailure)
  RecordingRuntime.state.value=RecorderState(token="a",options=RecorderOptions(RecorderKind.VOICE),phase=RecorderPhase.RECORDING)
  assertTrue(runCatching{RecordingRuntime.start(c,RecorderOptions(RecorderKind.VOICE))}.isFailure)
  assertEquals("a",RecordingRuntime.state.value.token)
 }
}
