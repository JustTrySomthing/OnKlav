package app.onklav.home

import android.app.Application
import android.app.NotificationManager
import android.app.job.JobScheduler
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkScheduledNotificationsTest{
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private val scheduler get()=c.getSystemService(JobScheduler::class.java)
 private fun request(id:String="org.example.one",key:String="one",delay:Long=60)=JSONObject().put("service","notifications.schedule").put("owner",JSONObject().put("id",id).put("publisher","a".repeat(64)).put("name","Example")).put("args",JSONObject().put("key",key).put("title","Reminder").put("text","Body").put("delay_seconds",delay))
 private fun enable(){shadowOf(c.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)}
 private fun save(r:JSONObject=request(),now:Long=1000){SdkScheduledNotifications.schedule(c,r,{},now)}
 @Test fun schedulesPersistedBoundedJobsAndReplacementRejectsOldTokens(){
  enable();save();val original=scheduler.allPendingJobs.single();assertTrue(original.isPersisted);assertEquals(60_000L,original.minLatencyMillis)
  save(request(delay=120));val updated=scheduler.allPendingJobs.single();assertEquals(original.id,updated.id);assertNotEquals(original.extras.getString("token"),updated.extras.getString("token"))
  assertFalse(SdkScheduledNotifications.deliver(c,original.id,original.extras.getString("token")!!,200_000,owner={_,_->error("Stale job must not read owner")}))
  assertTrue(c.getSharedPreferences("sdk-app-reminders",0).all.isNotEmpty())
 }
 @Test fun queuesHavePerAppAndHostLimitsAndCancelOnlyTheirOwner(){
  enable();for(app in 0..3)for(key in 0..7)save(request("org.example.app$app","key$key"))
  assertEquals(32,scheduler.allPendingJobs.size)
  assertThrows(Exception::class.java){save(request("org.example.other"))};assertThrows(Exception::class.java){save(request("org.example.app0","overflow"))}
  save(request("org.example.app0","key0",120));assertEquals(32,scheduler.allPendingJobs.size)
  SdkNotifications.execute(c,request("org.example.app0","key0").put("service","notifications.cancel")){};assertEquals(31,scheduler.allPendingJobs.size)
  SdkNotifications.clearApp(c,"org.example.app0");assertEquals(24,scheduler.allPendingJobs.size)
 }
 @Test fun deliveryRechecksOwnerAndConsumesOnlyOnce(){
  enable();val r=request();save(r);val job=scheduler.allPendingJobs.single();val token=job.extras.getString("token")!!;var checks=0
  assertTrue(SdkScheduledNotifications.deliver(c,job.id,token,60_999,owner={_,_->checks++;r.getJSONObject("owner")}));assertEquals(0,c.getSystemService(NotificationManager::class.java).activeNotifications.size)
  assertFalse(SdkScheduledNotifications.deliver(c,job.id,token,61_000,owner={_,_->checks++;r.getJSONObject("owner")}));assertEquals(1,c.getSystemService(NotificationManager::class.java).activeNotifications.size);assertEquals(4,checks)
  assertFalse(SdkScheduledNotifications.deliver(c,job.id,token,61_000,owner={_,_->error("Consumed reminder") }));assertEquals(1,c.getSystemService(NotificationManager::class.java).activeNotifications.size)
 }
 @Test fun revokedOrChangedPublisherNeverPostsAndStoppedWorkKeepsQueue(){
  enable();save();val job=scheduler.allPendingJobs.single();val token=job.extras.getString("token")!!
  assertTrue(SdkScheduledNotifications.deliver(c,job.id,token,61_000,owner={_,_->error("Stopped")},stopped={true}))
  assertFalse(SdkScheduledNotifications.deliver(c,job.id,token,61_000,owner={_,_->null}));assertTrue(c.getSharedPreferences("sdk-app-reminders",0).all.isEmpty());assertTrue(c.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
 }
 @Test fun transientOwnerFailuresHaveBoundedRetriesAndLateRevocationNeverPosts(){
  enable();save();var job=scheduler.allPendingJobs.single();var token=job.extras.getString("token")!!
  repeat(2){assertTrue(SdkScheduledNotifications.deliver(c,job.id,token,61_000,owner={_,_->error("Unavailable registry")}))}
  assertFalse(SdkScheduledNotifications.deliver(c,job.id,token,61_000,owner={_,_->error("Unavailable registry")}));assertTrue(c.getSharedPreferences("sdk-app-reminders",0).all.isEmpty())
  save();job=scheduler.allPendingJobs.single();token=job.extras.getString("token")!!;var reads=0
  assertFalse(SdkScheduledNotifications.deliver(c,job.id,token,61_000,owner={_,_->if(++reads==1)request().getJSONObject("owner")else null}));assertTrue(c.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty());assertTrue(c.getSharedPreferences("sdk-app-reminders",0).all.isEmpty())
 }
 @Test fun recoveryRestoresMissingJobsAndRejectsMalformedRows(){
  enable();save();val first=scheduler.allPendingJobs.single();scheduler.cancel(first.id)
  assertTrue(scheduler.allPendingJobs.isEmpty());SdkScheduledNotifications.recover(c,1000);val restored=scheduler.allPendingJobs.single();assertEquals(first.extras.getString("token"),restored.extras.getString("token"))
  val p=c.getSharedPreferences("sdk-app-reminders",0);p.edit().putString("1","{\"token\":\"bad\"}").commit();SdkScheduledNotifications.recover(c,1000);assertFalse(p.contains("1"));assertEquals(1,scheduler.allPendingJobs.size)
 }
 @Test fun failedAuthorizationAndInvalidDelaysDoNotCreateJobs(){
  enable();for(delay in listOf(-1L,0L,59L,2_592_001L))assertThrows(Exception::class.java){save(request(delay=delay))}
  assertThrows(Exception::class.java){SdkScheduledNotifications.schedule(c,request(),{error("Revoked")},1000)}
  assertTrue(scheduler.allPendingJobs.isEmpty());assertTrue(c.getSharedPreferences("sdk-app-reminders",0).all.isEmpty())
 }
 private fun at(key:String="one",due:Long=121_000)=request(key=key).put("service","notifications.schedule_at").apply{getJSONObject("args").remove("delay_seconds");getJSONObject("args").put("at_ms",due)}
 private fun query(service:String="notifications.status",key:String="one",id:String="org.example.one")=request(id,key).put("service",service).put("args",if(service=="notifications.pending")JSONObject()else JSONObject().put("key",key))
 @Test fun absoluteSchedulingStatusPendingAndCancelExposeOnlyOwnedTiming(){
  enable();val result=SdkScheduledNotifications.schedule(c,at(),{},1000);assertEquals(121_000,result.getLong("due_ms"));assertFalse(result.getBoolean("exact"));assertEquals(120_000,scheduler.allPendingJobs.single().minLatencyMillis)
  val status=SdkScheduledNotifications.query(c,query(),{},121_000);assertTrue(status.getBoolean("pending"));assertTrue(status.getBoolean("overdue"));assertTrue(status.getBoolean("registered"));assertFalse(status.has("title"));assertFalse(status.has("owner"))
  save(request("org.example.other","private"));val pending=SdkNotifications.execute(c,query("notifications.pending")){};assertEquals(1,pending.getJSONArray("items").length());assertEquals("one",pending.getJSONArray("items").getJSONObject(0).getString("key"))
  SdkNotifications.execute(c,query("notifications.cancel")){};val missing=SdkScheduledNotifications.query(c,query(),{},200_000);assertFalse(missing.getBoolean("pending"));assertTrue(missing.isNull("due_ms"));assertFalse(missing.getBoolean("registered"));assertEquals(1,scheduler.allPendingJobs.size)
 }
 @Test fun absoluteRecoveryRecomputesLatencyAfterClockChangeAndReportsMissingRegistration(){
  enable();SdkScheduledNotifications.schedule(c,at(),{},1000);val first=scheduler.allPendingJobs.single();scheduler.cancel(first.id)
  assertFalse(SdkScheduledNotifications.query(c,query(),{},1000).getBoolean("registered"));SdkScheduledNotifications.recover(c,61_000,true);assertEquals(60_000,scheduler.allPendingJobs.single().minLatencyMillis)
  SdkScheduledNotifications.recover(c,200_000,true);assertEquals(1000,scheduler.allPendingJobs.single().minLatencyMillis);assertTrue(SdkScheduledNotifications.query(c,query(),{},200_000).getBoolean("overdue"))
 }
 @Test fun absoluteBoundsDenialAndStatusCannotLeakOtherPublishersOrClaimDelivery(){
  enable();for(due in listOf(-1L,1000L,1999L,31_622_401_001L)){assertThrows(Exception::class.java){SdkScheduledNotifications.schedule(c,at(due=due),{},1000)}}
  assertTrue(scheduler.allPendingJobs.isEmpty());assertThrows(Exception::class.java){SdkScheduledNotifications.query(c,query(),{error("Revoked")},1000)}
  SdkScheduledNotifications.schedule(c,at(),{},1000);val changed=query();changed.getJSONObject("owner").put("publisher","b".repeat(64));assertFalse(SdkScheduledNotifications.query(c,changed,{},1000).getBoolean("pending"))
  val job=scheduler.allPendingJobs.single();assertFalse(SdkScheduledNotifications.deliver(c,job.id,job.extras.getString("token")!!,121_000,owner={_,_->request().getJSONObject("owner")}));val status=SdkScheduledNotifications.query(c,query(),{},121_000);assertFalse(status.getBoolean("pending"));assertFalse(status.has("delivered"))
 }

 @Test fun disabledOwnerChannelIsVisibleInStatusAndBlocksScheduling(){
  enable();val channel="sdk-app-"+AppSdk.desktopId("org.example.one");c.getSystemService(NotificationManager::class.java).createNotificationChannel(android.app.NotificationChannel(channel,"Example",NotificationManager.IMPORTANCE_NONE))
  assertFalse(SdkScheduledNotifications.query(c,query(),{},1000).getBoolean("notifications_enabled"));assertThrows(Exception::class.java){SdkScheduledNotifications.schedule(c,at(),{},1000)};assertTrue(scheduler.allPendingJobs.isEmpty())
 }

 @Test fun repeatSkipsBacklogAndMissedPolicyHasGrace(){
  val a=JSONObject().put("repeat_seconds",60).put("repeat_count",5).put("missed","skip");SdkReminderPolicy.validate(a)
  assertEquals(241000L to 2,SdkReminderPolicy.next(a,61000,200000))
  assertNull(SdkReminderPolicy.next(a,61000,400000));assertFalse(SdkReminderPolicy.skip(a,61000,361000));assertTrue(SdkReminderPolicy.skip(a,61000,361001))
  a.put("repeat_count",101);assertThrows(Exception::class.java){SdkReminderPolicy.validate(a)}
 }
 @Test fun repeatDeliveryRotatesTokenAndRevocationClearsFutureOccurrences(){
  enable();val r=request();r.getJSONObject("args").put("repeat_seconds",60).put("repeat_count",3)
  save(r);val job=scheduler.allPendingJobs.single();val token=job.extras.getString("token")!!
  assertFalse(SdkScheduledNotifications.deliver(c,job.id,token,61000,owner={_,_->r.getJSONObject("owner")}))
  assertEquals(121000L,SdkScheduledNotifications.query(c,query(),{},61000).getLong("due_ms"))
  assertFalse(SdkScheduledNotifications.deliver(c,job.id,token,121000,owner={_,_->error("stale")}))
  val next=scheduler.allPendingJobs.single();SdkScheduledNotifications.deliver(c,next.id,next.extras.getString("token")!!,121000,owner={_,_->null})
  assertFalse(SdkScheduledNotifications.query(c,query(),{},121000).getBoolean("pending"))
 }
 @Test fun snoozeActionsAreOneUseAndRecheckTheSignedOwner(){
  enable();val r=request();r.getJSONObject("args").put("snooze_seconds",120);save(r)
  val job=scheduler.allPendingJobs.single();SdkScheduledNotifications.deliver(c,job.id,job.extras.getString("token")!!,61000,owner={_,_->r.getJSONObject("owner")})
  val record=c.getSharedPreferences("sdk-app-notifications",0).all.values.filterIsInstance<String>().single();val token=JSONObject(record).getString("token")
  SdkNotifications.snooze(c,token,62000,current={_,_->null});assertFalse(SdkScheduledNotifications.query(c,query(),{},62000).getBoolean("pending"))
  SdkNotifications.snooze(c,token,62000,current={_,_->r.getJSONObject("owner")});assertEquals(182000L,SdkScheduledNotifications.query(c,query(),{},62000).getLong("due_ms"))
  SdkNotifications.snooze(c,token,100000,current={_,_->error("Consumed action cannot authorize again")});assertEquals(182000L,SdkScheduledNotifications.query(c,query(),{},100000).getLong("due_ms"))
 }
}
