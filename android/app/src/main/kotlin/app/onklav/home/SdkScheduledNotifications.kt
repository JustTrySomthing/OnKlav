package app.onklav.home

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded host-owned reminder queue. No guest/background code or exact-alarm access. */
internal object SdkScheduledNotifications {
 internal const val JOB_BASE=0x53444e00
 private const val LIMIT=32
 private const val PREFS="sdk-app-reminders"
 private fun prefs(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
 private fun scheduler(c:Context)=c.getSystemService(JobScheduler::class.java)
 private fun row(c:Context,index:Int)=prefs(c).getString(index.toString(),null)?.let{runCatching{
  JSONObject(it).also{r->
   SdkNotifications.identity(r.getJSONObject("owner"));val a=r.getJSONObject("args")
   require(a.getString("key").matches(Regex("[A-Za-z0-9._-]{1,48}")))
   require(a.getString("title").length in 1..80&&a.getString("text").length in 1..500)
   require(r.getLong("due")>0&&r.getInt("attempts") in 0..2)
   require(r.getString("token").matches(Regex("[a-f0-9-]{36}")))
  }
 }.getOrNull()}
 private fun rows(c:Context)=(0 until LIMIT).mapNotNull{index->row(c,index)?.let{index to it}}
 private fun remove(c:Context,index:Int){check(prefs(c).edit().remove(index.toString()).commit());scheduler(c).cancel(JOB_BASE+index)}
 private fun enqueue(c:Context,index:Int,r:JSONObject,now:Long){
  val extras=PersistableBundle().apply{putString("token",r.getString("token"))}
  val job=JobInfo.Builder(JOB_BASE+index,ComponentName(c,SdkNotificationJobService::class.java))
   .setMinimumLatency((r.getLong("due")-now).coerceAtLeast(1000L)).setPersisted(true).setExtras(extras)
   .setBackoffCriteria(60_000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build()
  check(scheduler(c).schedule(job)==JobScheduler.RESULT_SUCCESS){"Android could not schedule this reminder. Try again later."}
 }
 fun schedule(c:Context,request:JSONObject,authorize:()->Unit,now:Long=System.currentTimeMillis()):JSONObject=AppSdk.carrierEgress{
  val owner=request.getJSONObject("owner");val id=SdkNotifications.identity(owner);val args=request.getJSONObject("args");val key=args.getString("key")
  require(key.matches(Regex("[A-Za-z0-9._-]{1,48}")))
  val absolute=request.getString("service")=="notifications.schedule_at"
  val due=if(absolute){val at=args.getLong("at_ms");require(args.get("at_ms").toString()==at.toString()&&at>0&&at<=253402300799999&&at>=Math.addExact(now,1000)&&at<=Math.addExact(now,31_622_400_000L)){"Choose a reminder at least1 second and at most366 days from now"};at}else{val delay=args.getLong("delay_seconds");require(delay in 60..2_592_000&&args.get("delay_seconds").toString()==delay.toString());Math.addExact(now,delay*1000)}
  for((name,limit)in listOf("title" to 80,"text" to 500)){val value=args.getString(name);require(value.isNotBlank()&&value.length<=limit)}
  check(SdkNotifications.allowedForOwner(c,id)){"Allow OnKlav notifications and this app notification channel in Android Settings before scheduling a reminder."};authorize()
  val all=rows(c);val own=all.filter{it.second.getJSONObject("owner").optString("id")==id};val previous=own.firstOrNull{it.second.getJSONObject("args").optString("key")==key}
  check(previous!=null||own.size<8){"Keep up to8 scheduled reminders per app; cancel an older one first."}
  val index=previous?.first?:(0 until LIMIT).firstOrNull{row(c,it)==null}?:error("The reminder queue is full. Cancel an older reminder first.")
  SdkReminderPolicy.validate(args)
  val saved=prefs(c).getString(index.toString(),null)
  val record=JSONObject().put("owner",JSONObject(owner.toString())).put("args",JSONObject(args.toString())).put("due",due).put("token",UUID.randomUUID().toString()).put("attempts",0)
  authorize();check(prefs(c).edit().putString(index.toString(),record.toString()).commit())
  try{enqueue(c,index,record,now)}catch(e:Exception){val edit=prefs(c).edit();if(saved==null)edit.remove(index.toString())else edit.putString(index.toString(),saved);check(edit.commit());throw e}
  JSONObject().put("scheduled",true).apply{if(absolute){put("due_ms",due);put("exact",false)}}
 }
 /** Repair a process interruption between durable commit and Android job registration. */
 fun recover(c:Context,now:Long=System.currentTimeMillis(),rescheduleAbsolute:Boolean=false)=AppSdk.carrierEgress{
  for(index in 0 until LIMIT){
   val r=row(c,index)
   if(r==null){if(prefs(c).contains(index.toString()))remove(c,index);continue}
   val job=scheduler(c).getPendingJob(JOB_BASE+index)
   if(job?.extras?.getString("token")!=r.getString("token")||(rescheduleAbsolute&&r.getJSONObject("args").has("at_ms")))enqueue(c,index,r,now)
  }
 }
 /** Queries expose only this signed owner's keys and timing, never titles or host identities. */
 fun query(c:Context,request:JSONObject,authorize:()->Unit,now:Long=System.currentTimeMillis()):JSONObject=AppSdk.carrierEgress{
  val owner=request.getJSONObject("owner");val id=SdkNotifications.identity(owner);authorize()
  val own=rows(c).filter{it.second.getJSONObject("owner").let{o->o.getString("id")==id&&o.getString("publisher")==owner.getString("publisher")}}.sortedWith(compareBy({it.second.getLong("due")},{it.second.getJSONObject("args").getString("key")}))
  fun publicRow(pair:Pair<Int,JSONObject>):JSONObject{val(index,r)=pair;return JSONObject().put("key",r.getJSONObject("args").getString("key")).put("due_ms",r.getLong("due")).put("overdue",r.getLong("due")<=now).put("registered",scheduler(c).getPendingJob(JOB_BASE+index)?.extras?.getString("token")==r.getString("token"))}
  val result=JSONObject().put("exact",false).put("notifications_enabled",SdkNotifications.allowedForOwner(c,id))
  if(request.getString("service")=="notifications.pending"){result.put("items",org.json.JSONArray(own.map{publicRow(it)}))}
  else{val key=request.getJSONObject("args").getString("key");require(key.matches(Regex("[A-Za-z0-9._-]{1,48}")));val found=own.firstOrNull{it.second.getJSONObject("args").getString("key")==key};val row=found?.let{publicRow(it)};result.put("pending",found!=null).put("due_ms",row?.getLong("due_ms")?:JSONObject.NULL).put("overdue",row?.getBoolean("overdue")?:false).put("registered",row?.getBoolean("registered")?:false)}
  authorize();result
 }
 fun cancel(c:Context,id:String,key:String)=AppSdk.carrierEgress{rows(c).filter{it.second.getJSONObject("owner").optString("id")==id&&it.second.getJSONObject("args").optString("key")==key}.forEach{remove(c,it.first)}}
 fun clearApp(c:Context,id:String)=AppSdk.carrierEgress{rows(c).filter{it.second.getJSONObject("owner").optString("id")==id}.forEach{remove(c,it.first)}}
 private fun advance(c:Context,index:Int,r:JSONObject,now:Long){
  val next=SdkReminderPolicy.next(r.getJSONObject("args"),r.getLong("due"),now)
  if(next==null){check(prefs(c).edit().remove(index.toString()).commit());return}
  r.put("due",next.first).put("token",UUID.randomUUID().toString()).put("attempts",0)
  r.getJSONObject("args").put("repeat_count",next.second)
  check(prefs(c).edit().putString(index.toString(),r.toString()).commit());enqueue(c,index,r,now)
 }
 /** At most one posting attempt: consume before native notify, including process death. */
 internal fun deliver(c:Context,jobId:Int,token:String,now:Long=System.currentTimeMillis(),
  owner:(String,String)->JSONObject?={id,publisher->SdkNotifications.currentOwner(c,id,publisher)},stopped:()->Boolean={false}):Boolean=AppSdk.carrierEgress{
  val index=jobId-JOB_BASE;if(index !in 0 until LIMIT)return@carrierEgress false
  val r=row(c,index)?:return@carrierEgress false
  if(r.optString("token")!=token)return@carrierEgress false
  if(stopped())return@carrierEgress true
  try{
   val stored=r.getJSONObject("owner");val id=SdkNotifications.identity(stored)
   val live=owner(id,stored.getString("publisher"))
   if(live==null||!SdkNotifications.allowed(c)){remove(c,index);return@carrierEgress false}
   check(live.getString("id")==id&&live.getString("publisher")==stored.getString("publisher"))
   if(now<r.getLong("due"))return@carrierEgress true
   if(SdkReminderPolicy.skip(r.getJSONObject("args"),r.getLong("due"),now)){advance(c,index,r,now);return@carrierEgress false}
   val request=JSONObject().put("service","notifications.show").put("owner",live).put("args",r.getJSONObject("args")).put("reminder",true)
   if(stopped())return@carrierEgress true
   // Remove only the durable row; cancelling the running job would stop this callback.
   advance(c,index,r,now)
   SdkNotifications.execute(c,request){check(!stopped());check(owner(id,stored.getString("publisher"))!=null){"App notification access changed"}}
   false
  }catch(e:Exception){
   // Retry transient pre-consumption failures at most twice; never replay a posting attempt.
   val remaining=row(c,index)?:return@carrierEgress false
   if(remaining.optString("token")!=token)return@carrierEgress false
   val attempts=remaining.optInt("attempts")+1
   if(attempts>=3){remove(c,index);false}else{check(prefs(c).edit().putString(index.toString(),remaining.put("attempts",attempts).toString()).commit());true}
  }
 }
}

/** Android persists each job across reboot; callbacks never launch Wasm or perform network work. */
class SdkNotificationJobService:JobService(){
 private val running=ConcurrentHashMap<Int,AtomicBoolean>()
 override fun onStartJob(params:JobParameters):Boolean{
  val stopped=AtomicBoolean(false);running.put(params.jobId,stopped)?.set(true)
  worker.execute{
   var retry=false
   try{retry=SdkScheduledNotifications.deliver(applicationContext,params.jobId,params.extras.getString("token")?:"",stopped={stopped.get()})}
   catch(e:Exception){ChatStore.lastError.value="A scheduled app reminder could not be completed"}
   finally{android.os.Handler(mainLooper).post{if(running.remove(params.jobId,stopped)&&!stopped.get())jobFinished(params,retry)}}
  };return true
 }
 override fun onStopJob(params:JobParameters):Boolean{running.remove(params.jobId)?.set(true);return true}
 override fun onDestroy(){running.values.forEach{it.set(true)};running.clear();super.onDestroy()}
 private companion object{val worker=Executors.newSingleThreadExecutor()}
}
