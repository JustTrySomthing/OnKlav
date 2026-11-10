package app.onklav.home

import android.app.*
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import java.io.*
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EssentialsTest{
 private fun context():Application=ApplicationProvider.getApplicationContext<Application>().also{runBlocking{AppSdk.bootstrap(it)}}
 @Test fun localAlarmsHandleWeeklyDaysDstGapAndBothOverlapOccurrences(){
  val z=ZoneId.of("America/New_York")
  val gap=ClockAlarm(hour=2,minute=30);assertEquals(Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(),ClockDisk.next(gap,Instant.parse("2026-03-08T06:00:00Z"),z))
  val overlap=ClockAlarm(hour=1,minute=30);assertEquals(Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(),ClockDisk.next(overlap,Instant.parse("2026-11-01T05:00:00Z"),z));assertEquals(Instant.parse("2026-11-01T06:30:00Z").toEpochMilli(),ClockDisk.next(overlap,Instant.parse("2026-11-01T05:45:00Z"),z))
  assertEquals(Instant.parse("2026-11-02T06:30:00Z").toEpochMilli(),ClockDisk.afterRing(overlap.copy(days=127),Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(),Instant.parse("2026-11-01T05:30:01Z"),z))
  val monday=ClockAlarm(hour=9,days=1);assertEquals(Instant.parse("2026-11-02T14:00:00Z").toEpochMilli(),ClockDisk.next(monday,Instant.parse("2026-10-30T17:00:00Z"),z))
 }
 @Test fun timerUsesMonotonicTimeAndRebootFallsBackToSavedWallDeadline(){val c=context();val boot=ClockDisk.boot(c);val timer=ClockTimer(duration=60000,end=100000,wall=1000000,boot=boot);assertEquals(70000L,timer.left(c,30000,999000));assertEquals(1000L,timer.copy(boot=boot+1).left(c,30000,999000));assertEquals(0L,timer.left(c,110000,1));val watch=ClockWatch(accumulated=1500,start=SystemClock.elapsedRealtime()-1000,wall=System.currentTimeMillis()-5000,boot=boot,laps=listOf(300));assertTrue(watch.value(c) in 2500..2600);assertTrue(watch.copy(boot=boot+1).value(c) in 6500..6600);assertEquals(watch,ClockWatch.from(watch.json()))}
 @Test fun staleEventsCannotRingAndSnoozeAndTimerRecoveryPersist(){val c=context();val alarm=ClockAlarm(hour=8,minute=15,label="Morning");ClockDisk.saveAlarm(c,alarm);val saved=ClockDisk.alarms(c).single();assertTrue(saved.next>System.currentTimeMillis());assertFalse(ClockDisk.fire(c,"a-${saved.id}",saved.next-1));ClockDisk.update(c){it.put("alarms",JSONArray().put(saved.copy(next=System.currentTimeMillis()-100,ringing=false).json()))};val due=ClockDisk.alarms(c).single();assertTrue(ClockDisk.fire(c,"a-${due.id}",due.next));ClockDisk.dismiss(c,"a-${due.id}",true);val snoozed=ClockDisk.alarms(c).single();assertTrue(snoozed.enabled&&snoozed.snoozed&&!snoozed.ringing);assertTrue(snoozed.next-System.currentTimeMillis() in 590000..600000);ClockDisk.recover(c);assertEquals(snoozed.next,ClockDisk.alarms(c).single().next)
  val t=ClockTimer(duration=60000);ClockDisk.timer(c,t,true);val running=ClockDisk.timers(c).single();assertFalse(ClockDisk.fire(c,"t-${t.id}",running.wall));ClockDisk.timer(c,running,false);val paused=ClockDisk.timers(c).single();assertEquals(0L,paused.end);assertTrue(paused.remaining in 59000..60000);ClockDisk.timer(c,paused,true);assertTrue(ClockDisk.timers(c).single().end>SystemClock.elapsedRealtime());ClockDisk.stopAll(c)
 }
 @Test fun oldSnoozeActionCannotReenableAnAlarmOrReplaceItsSchedule(){
  val c=context()
  val alarm=ClockAlarm(enabled=false)
  ClockDisk.saveAlarm(c,alarm)
  ClockDisk.dismiss(c,"a-${alarm.id}",true)
  assertEquals(alarm,ClockDisk.alarms(c).single())
  ClockDisk.saveAlarm(c,alarm.copy(enabled=true))
  val scheduled=ClockDisk.alarms(c).single()
  ClockDisk.dismiss(c,"a-${alarm.id}",true)
  assertEquals(scheduled,ClockDisk.alarms(c).single())
 }
 @Test fun scheduleRecoveryKeepsAnAlreadyRingingRepeatingAlarm(){
  val c=context()
  val alarm=ClockAlarm(days=127)
  ClockDisk.saveAlarm(c,alarm)
  val due=System.currentTimeMillis()-100
  ClockDisk.update(c){it.put("alarms",JSONArray().put(alarm.copy(next=due).json()))}
  assertTrue(ClockDisk.fire(c,"a-${alarm.id}",due))
  ClockDisk.recover(c)
  assertEquals(listOf("a-${alarm.id}" to alarm.label),ClockDisk.rings(c))
  assertTrue(ClockDisk.alarms(c).single().next>due)
  ClockDisk.dismiss(c,"a-${alarm.id}")
  assertTrue(ClockDisk.rings(c).isEmpty())
 }
 @Test fun revokingClockAndFlashlightAccessDisablesHardwareAndScheduledWork(){val c=context();ClockDisk.saveAlarm(c,ClockAlarm());ClockDisk.timer(c,ClockTimer(),true);AppSdk.sync(c,JSONObject().put("action","permissions").put("id",CLOCK_APP).put("grants",JSONArray()));assertFalse(ClockDisk.allowed(c));assertTrue(ClockDisk.alarms(c).none{it.enabled});assertTrue(ClockDisk.timers(c).none{it.end>0});assertFalse(ClockDisk.fire(c,"bad",1));assertTrue(runCatching{ClockDisk.saveAlarm(c,ClockAlarm())}.isFailure);AppSdk.sync(c,JSONObject().put("action","permissions").put("id",TORCH_APP).put("grants",JSONArray()));assertFalse(TorchControl.allowed(c));assertTrue(runCatching{TorchControl.start(c,false,1,1)}.isFailure)}
 @Test fun captureValidationAndSessionCommitRetainFailedSavesAndProtectOwnership(){val c=context();val model=SdkAppModel(c);runBlocking{model.open(CAMERA_APP)};val file=CameraDisk.create(c,false);val b=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888);file.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,it)};b.recycle();CameraDisk.validate(file,false);val saved=runBlocking{CameraDisk.save(c,file,model)};assertFalse(file.exists());assertTrue(saved.has("id"));val rows=runBlocking{model.cameraAction("list")};assertEquals(1,rows.getInt("total"));runBlocking{model.cameraAction("trash",JSONObject().put("id",saved.getString("id")))};assertEquals(0,runBlocking{model.cameraAction("list")}.getInt("total"));val broken=CameraDisk.create(c,false);broken.writeText("broken JPEG");assertTrue("Invalid JPEG must be rejected by the native decoder",runCatching{CameraDisk.validate(broken,false)}.isFailure);assertTrue(broken.exists());broken.delete()}
 @Test fun largeVideoExportStreamsBeyondOld256MiBLimitAndChecksAccess(){
  var checks=0;var written=0L;val size=257L*1024*1024
  fun source():InputStream {
   return object:InputStream(){
    var left=size
    override fun read():Int{if(left<=0)return -1;left--;return 0}
    override fun read(b:ByteArray,off:Int,len:Int):Int{if(left<=0)return -1;val n=minOf(left,len.toLong()).toInt();left-=n;return n}
   }
  }
  val out=object:OutputStream(){override fun write(b:Int){written++};override fun write(b:ByteArray,off:Int,len:Int){written+=len}}
  source().use{FilesBroker.copyBoundedLimit(it,out,2*1024*1024*1024L){checks++}};assertEquals(size,written);assertTrue(checks>250)
  assertTrue(runCatching{source().use{FilesBroker.copyBounded(it,out){}}}.isFailure)
  assertTrue(runCatching{source().use{FilesBroker.copyBoundedLimit(it,out,2*1024*1024*1024L){error("permission revoked")}}}.isFailure)
 }
}
