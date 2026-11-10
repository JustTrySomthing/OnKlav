package app.onklav.home

import android.Manifest
import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class RecorderServiceTest{
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private fun await(condition:()->Boolean){val until=System.nanoTime()+15_000_000_000;while(!condition()&&System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));Thread.sleep(10)};assertTrue("Timed out waiting for recorder transition: ${RecordingRuntime.state.value}",condition())}
 @Before fun setup(){RecordingRuntime.state.value=RecorderState();Shadows.shadowOf(c).grantPermissions(Manifest.permission.RECORD_AUDIO);kotlinx.coroutines.runBlocking{AppSdk.bootstrap(c)}}
 @After fun clear(){RecordingRuntime.factory=null;RecordingRuntime.state.value=RecorderState()}
 @Test fun pauseResumeStaleControlsAndDuplicateStopKeepOneDurableRecording(){
  var pauses=0;var resumes=0;var releases=0
  RecordingRuntime.factory={context,id,_,_,_->object:RecorderBackend{
   override fun start(){}
   override fun pause(){pauses++}
   override fun resume(){resumes++}
   override fun level()=.5f
   override fun bytes()=1004L
   override fun finish():RecorderResult{val file=RecorderLibrary.draft(context,id);file.writeBytes(ByteArray(1004));RecorderWav.header(file);return RecorderResult(file)}
   override fun release(){releases++}
  }}
  RecordingRuntime.start(c,RecorderOptions(RecorderKind.VOICE,format=RecorderFormat.WAV))
  val start=Shadows.shadowOf(c).nextStartedService;assertNotNull(start)
  val controller=Robolectric.buildService(RecorderService::class.java,start).create();val service=controller.get();service.onStartCommand(start,0,1)
  await{RecordingRuntime.state.value.phase==RecorderPhase.RECORDING};val token=RecordingRuntime.state.value.token
  service.onStartCommand(Intent(c,RecorderService::class.java).setAction("pause").putExtra("token","stale"),0,2);assertEquals(0,pauses)
  service.onStartCommand(Intent(c,RecorderService::class.java).setAction("pause").putExtra("token",token),0,3);await{RecordingRuntime.state.value.phase==RecorderPhase.PAUSED};val elapsed=RecordingRuntime.state.value.elapsed
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));assertEquals(elapsed,RecordingRuntime.state.value.elapsed)
  service.onStartCommand(Intent(c,RecorderService::class.java).setAction("resume").putExtra("token",token),0,4);await{RecordingRuntime.state.value.phase==RecorderPhase.RECORDING}
  val stop=Intent(c,RecorderService::class.java).setAction("stop").putExtra("token",token);service.onStartCommand(stop,0,5);service.onStartCommand(stop,0,6);await{Shadows.shadowOf(service).isStoppedBySelf};assertTrue(RecordingRuntime.state.value.active);assertTrue(runCatching{RecordingRuntime.start(c,RecorderOptions(RecorderKind.VOICE))}.isFailure);controller.destroy();await{!RecordingRuntime.state.value.active}
  assertEquals(1,pauses);assertEquals(1,resumes);assertTrue(releases>=1);assertNull(RecordingRuntime.state.value.error);assertEquals(1,RecorderLibrary.list(c,RecorderKind.VOICE).size)
  RecordingRuntime.start(c,RecorderOptions(RecorderKind.VOICE,format=RecorderFormat.WAV));val next=Shadows.shadowOf(c).nextStartedService;val second=Robolectric.buildService(RecorderService::class.java,next).create();second.get().onStartCommand(next,0,7);await{RecordingRuntime.state.value.phase==RecorderPhase.RECORDING};second.get().onStartCommand(Intent(c,RecorderService::class.java).setAction("stop").putExtra("token",RecordingRuntime.state.value.token),0,8);await{Shadows.shadowOf(second.get()).isStoppedBySelf};second.destroy();await{!RecordingRuntime.state.value.active};assertEquals(2,RecorderLibrary.list(c,RecorderKind.VOICE).size)
 }
 @Test fun cancellingDuringPreparationDoesNotPublishAPhantomRecording(){
  val entered=java.util.concurrent.atomic.AtomicBoolean(false);val gate=java.util.concurrent.CountDownLatch(1);var released=false
  RecordingRuntime.factory={context,id,_,_,_->object:RecorderBackend{
   override fun start(){RecorderLibrary.file(context,id,"audio.part.m4a").writeText("not a finished recording");entered.set(true);check(gate.await(5,java.util.concurrent.TimeUnit.SECONDS))}
   override fun pause(){};override fun resume(){};override fun level()=0f;override fun bytes()=0L
   override fun finish():RecorderResult=error("Cancelled recording must not be published")
   override fun release(){released=true}
  }}
  RecordingRuntime.start(c,RecorderOptions(RecorderKind.VOICE));val start=Shadows.shadowOf(c).nextStartedService;val controller=Robolectric.buildService(RecorderService::class.java,start).create();controller.get().onStartCommand(start,0,1);await{entered.get()};val token=RecordingRuntime.state.value.token
  controller.get().onStartCommand(Intent(c,RecorderService::class.java).setAction("stop").putExtra("token",token),0,2);gate.countDown();await{Shadows.shadowOf(controller.get()).isStoppedBySelf};controller.destroy();await{!RecordingRuntime.state.value.active}
  assertTrue(released);assertNull(RecordingRuntime.state.value.error);assertEquals("Recording cancelled",RecordingRuntime.state.value.message);assertTrue(RecorderLibrary.list(c,RecorderKind.VOICE).isEmpty());assertFalse(RecorderLibrary.file(c,token,"json").exists())
 }
 @Test fun failedBackendKeepsItsInterruptedDraftAndReleasesResources(){
  var released=false
  RecordingRuntime.factory={context,id,_,_,_->object:RecorderBackend{
   override fun start(){RecorderLibrary.file(context,id,"audio.part.m4a").writeText("unfinished");error("Microphone unavailable")}
   override fun pause(){};override fun resume(){};override fun level()=0f;override fun bytes()=0L
   override fun finish():RecorderResult=error("No playable recording was captured")
   override fun release(){released=true}
  }}
  RecordingRuntime.start(c,RecorderOptions(RecorderKind.VOICE));val start=Shadows.shadowOf(c).nextStartedService
  val controller=Robolectric.buildService(RecorderService::class.java,start).create();controller.get().onStartCommand(start,0,1);await{Shadows.shadowOf(controller.get()).isStoppedBySelf};controller.destroy();await{!RecordingRuntime.state.value.active}
  assertTrue(released);assertNotNull(RecordingRuntime.state.value.error);val draft=RecorderLibrary.list(c,RecorderKind.VOICE).single();assertTrue(draft.pending&&draft.interrupted);assertEquals(10L,draft.bytes)
 }
}
