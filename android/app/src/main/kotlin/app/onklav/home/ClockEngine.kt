package app.onklav.home

import android.app.*
import android.content.*
import android.media.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.*
import java.time.*
import java.util.UUID

internal data class ClockAlarm(val id:String=UUID.randomUUID().toString(),val hour:Int=7,val minute:Int=0,val days:Int=0,val label:String="Alarm",val enabled:Boolean=true,val vibrate:Boolean=true,val tone:String="",val snooze:Int=10,val next:Long=0,val ringing:Boolean=false,val snoozed:Boolean=false,val skippedDay:Long=Long.MIN_VALUE){
 fun json()=JSONObject().put("id",id).put("hour",hour).put("minute",minute).put("days",days).put("label",label.take(80)).put("enabled",enabled).put("vibrate",vibrate).put("tone",tone).put("snooze",snooze).put("next",next).put("ringing",ringing).put("snoozed",snoozed).put("skippedDay",skippedDay)
 companion object{fun from(j:JSONObject)=ClockAlarm(j.getString("id"),j.optInt("hour",7).coerceIn(0,23),j.optInt("minute").coerceIn(0,59),j.optInt("days")and 127,j.optString("label","Alarm"),j.optBoolean("enabled"),j.optBoolean("vibrate",true),j.optString("tone"),j.optInt("snooze",10).coerceIn(1,30),j.optLong("next"),j.optBoolean("ringing"),j.optBoolean("snoozed"),j.optLong("skippedDay",Long.MIN_VALUE))}
}
internal data class ClockTimer(val id:String=UUID.randomUUID().toString(),val label:String="Timer",val duration:Long=300000,val remaining:Long=duration,val end:Long=0,val wall:Long=0,val boot:Int=0,val ringing:Boolean=false){
 fun left(c:Context,now:Long=SystemClock.elapsedRealtime(),wallNow:Long=System.currentTimeMillis())=if(end==0L)remaining else if(boot==ClockDisk.boot(c))(end-now).coerceAtLeast(0)else(wall-wallNow).coerceAtLeast(0)
 fun json()=JSONObject().put("id",id).put("label",label.take(80)).put("duration",duration).put("remaining",remaining).put("end",end).put("wall",wall).put("boot",boot).put("ringing",ringing)
 companion object{fun from(j:JSONObject)=ClockTimer(j.getString("id"),j.optString("label","Timer"),j.optLong("duration",300000).coerceIn(1000,7*86400000L),j.optLong("remaining"),j.optLong("end"),j.optLong("wall"),j.optInt("boot"),j.optBoolean("ringing"))}
}
internal data class ClockWatch(val accumulated:Long=0,val start:Long=0,val wall:Long=0,val boot:Int=0,val laps:List<Long> = emptyList()){
 fun value(c:Context)=accumulated+if(start==0L)0 else if(boot==ClockDisk.boot(c))(SystemClock.elapsedRealtime()-start).coerceAtLeast(0)else(System.currentTimeMillis()-wall).coerceAtLeast(0)
 fun json()=JSONObject().put("accumulated",accumulated).put("start",start).put("wall",wall).put("boot",boot).put("laps",JSONArray(laps))
 companion object{fun from(j:JSONObject)=ClockWatch(j.optLong("accumulated"),j.optLong("start"),j.optLong("wall"),j.optInt("boot"),j.optJSONArray("laps")?.let{a->(0 until a.length()).take(1000).map{a.optLong(it)}}?:emptyList())}
}
internal object ClockDisk{
 val epoch=MutableStateFlow(0L)
 private fun prefs(c:Context)=c.getSharedPreferences("native-clock",0)
 fun boot(c:Context)=Settings.Global.getInt(c.contentResolver,Settings.Global.BOOT_COUNT,0)
 @Synchronized fun read(c:Context)=JSONObject(prefs(c).getString("state","{}")?:"{}")
 @Synchronized fun update(c:Context,action:(JSONObject)->Unit){val j=read(c);action(j);check(j.toString().length<400000){"Clock storage is full"};check(prefs(c).edit().putString("state",j.toString()).commit()){ "Could not save Clock" };epoch.value++}
 fun alarms(c:Context)=read(c).optJSONArray("alarms")?.let{a->(0 until a.length()).map{ClockAlarm.from(a.getJSONObject(it))}}?:emptyList()
 fun timers(c:Context)=read(c).optJSONArray("timers")?.let{a->(0 until a.length()).map{ClockTimer.from(a.getJSONObject(it))}}?:emptyList()
 fun watch(c:Context)=ClockWatch.from(read(c).optJSONObject("watch")?:JSONObject())
 fun setWatch(c:Context,w:ClockWatch){check(essentialAccess(c,CLOCK_APP,"storage.private")){"Clock storage access is off"};update(c){it.put("watch",w.json())}}
 /** A gap advances to the first valid local instant; an overlap may ring at either still-future occurrence. */
 fun next(a:ClockAlarm,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault()):Long{
  val today=now.atZone(zone).toLocalDate()
  for(d in 0L..8L){val date=today.plusDays(d);if(date.toEpochDay()==a.skippedDay)continue;if(a.days!=0&&a.days and (1 shl (date.dayOfWeek.value-1))==0)continue
   val local=date.atTime(a.hour,a.minute);val offsets=zone.rules.getValidOffsets(local)
   val candidates=if(offsets.isEmpty())listOf(local.atZone(zone).toInstant())else offsets.map{local.toInstant(it)}
   candidates.sorted().firstOrNull{it>now}?.let{return it.toEpochMilli()}
  };error("No next alarm")
 }
 fun afterRing(a:ClockAlarm,firedAt:Long,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault()):Long{
  if(a.days==0)return 0
  val boundary=Instant.ofEpochMilli(firedAt).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)
  return next(a,if(a.snoozed)now else maxOf(now,boundary),zone)
 }
 fun exact(c:Context)=Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
 fun intent(c:Context,key:String,at:Long)=PendingIntent.getBroadcast(c,0,Intent(c,ClockReceiver::class.java).setAction("ring").setData(Uri.parse("onklav-clock:///$key")).putExtra("key",key).putExtra("at",at),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 fun open(c:Context)=PendingIntent.getActivity(c,831,Intent(c,ClockAlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 fun cancel(c:Context,key:String){c.getSystemService(AlarmManager::class.java).cancel(intent(c,key,0))}
 private fun schedule(c:Context,key:String,at:Long){check(exact(c)){"Allow alarms & reminders to use Clock"};c.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(at,open(c)),intent(c,key,at))}
 fun allowed(c:Context)=essentialAccess(c,CLOCK_APP,"storage.private","background.tasks","notifications.post","audio.playback")
 // Schedule mutations acquire carrierEgress before the disk monitor used by read/update.
 fun saveAlarm(c:Context,a:ClockAlarm,preserveRing:Boolean=false)=AppSdk.carrierEgress {check(allowed(c)){"Clock permissions are off"};val next=if(a.enabled)if(a.next>System.currentTimeMillis())a.next else next(a)else 0;val value=a.copy(next=next,ringing=preserveRing&&a.ringing);val previous=alarms(c).firstOrNull{it.id==a.id}
  if(a.enabled){check(exact(c)){"Allow alarms & reminders first"};schedule(c,"a-${a.id}",next)}else cancel(c,"a-${a.id}")
  try{update(c){j->val rows=alarms(c).filter{it.id!=a.id};check(rows.size<32){"Keep up to32 alarms"};j.put("alarms",JSONArray((rows+value).map{it.json()}))}}catch(e:Exception){try{if(previous?.enabled==true)schedule(c,"a-${a.id}",previous.next)else cancel(c,"a-${a.id}")}catch(rollback:Exception){e.addSuppressed(rollback)};throw e};ClockUpcoming.refresh(c);notifyRing(c)
 }
 fun deleteAlarm(c:Context,id:String)=AppSdk.carrierEgress {update(c){j->j.put("alarms",JSONArray(alarms(c).filter{it.id!=id}.map{it.json()}))};cancel(c,"a-$id");ClockUpcoming.refresh(c);notifyRing(c)}
 fun timer(c:Context,t:ClockTimer,run:Boolean)=AppSdk.carrierEgress {check(allowed(c)){"Clock permissions are off"};val previous=timers(c).firstOrNull{it.id==t.id};val existing=timers(c).filter{it.id!=t.id};check(existing.size<16){"Keep up to16 timers"};val left=if(t.end>0)t.left(c)else t.remaining
  val v=if(run)t.copy(end=SystemClock.elapsedRealtime()+left.coerceAtLeast(1000),wall=System.currentTimeMillis()+left.coerceAtLeast(1000),boot=boot(c),ringing=false)else t.copy(remaining=left,end=0,wall=0,ringing=false)
  if(run)schedule(c,"t-${t.id}",v.wall)else cancel(c,"t-${t.id}")
  try{update(c){j->val rows=timers(c).filter{it.id!=t.id};check(rows.size<16){"Keep up to16 timers"};j.put("timers",JSONArray((rows+v).map{it.json()}))}}catch(e:Exception){try{if(previous!=null&&previous.end>0)schedule(c,"t-${t.id}",previous.wall)else cancel(c,"t-${t.id}")}catch(rollback:Exception){e.addSuppressed(rollback)};throw e};notifyRing(c)
 }
 fun deleteTimer(c:Context,id:String)=AppSdk.carrierEgress {update(c){j->j.put("timers",JSONArray(timers(c).filter{it.id!=id}.map{it.json()}))};cancel(c,"t-$id");notifyRing(c)}
 fun fire(c:Context,key:String,at:Long):Boolean=AppSdk.carrierEgress {
  if(!allowed(c)){stopAll(c);return@carrierEgress false};val now=System.currentTimeMillis()
  if(key.startsWith("a-")){val a=alarms(c).firstOrNull{"a-${it.id}"==key&&it.enabled&&it.next==at}?:return@carrierEgress false
   if(now+1000<at){schedule(c,key,at);return@carrierEgress false};if(now-at>20*60000L){update(c){it.put("error","An alarm was missed while OnKlav was unavailable")};saveAlarm(c,a.copy(enabled=a.days!=0,next=0));return@carrierEgress false}
   val next=afterRing(a,at);update(c){j->j.put("alarms",JSONArray(alarms(c).map{if(it.id==a.id)a.copy(enabled=a.days!=0,next=next,ringing=true,snoozed=false).json()else it.json()}))};if(next>0)runCatching{schedule(c,key,next)}.onFailure{update(c){j->j.put("error","Could not schedule the next alarm. Check Alarms & reminders access.")}}
   ClockUpcoming.refresh(c)
  }else{val t=timers(c).firstOrNull{"t-${it.id}"==key&&it.end!=0L&&it.wall==at}?:return@carrierEgress false;val left=t.left(c);if(left>1000){val adjusted=t.copy(wall=now+left);update(c){j->j.put("timers",JSONArray(timers(c).map{if(it.id==t.id)adjusted.json()else it.json()}))};schedule(c,key,adjusted.wall);return@carrierEgress false};update(c){j->j.put("timers",JSONArray(timers(c).map{if(it.id==t.id)t.copy(end=0,wall=0,remaining=0,ringing=now-at<20*60000L).json()else it.json()}))}}
  if(rings(c).isNotEmpty())update(c){if(it.optLong("ringAt")==0L)it.put("ringAt",now)};return@carrierEgress rings(c).isNotEmpty()
 }
 fun rings(c:Context):List<Pair<String,String>> =alarms(c).filter{it.ringing}.map{"a-${it.id}" to it.label}+timers(c).filter{it.ringing}.map{"t-${it.id}" to it.label}
 fun dismiss(c:Context,key:String,snooze:Boolean=false)=AppSdk.carrierEgress {
  val alarm=alarms(c).firstOrNull{"a-${it.id}"==key&&it.ringing}
  if(alarm!=null&&snooze){
   saveAlarm(c,alarm.copy(enabled=true,next=System.currentTimeMillis()+alarm.snooze*60000L,ringing=false,snoozed=true))
   return@carrierEgress
  }
  val timer=timers(c).firstOrNull{"t-${it.id}"==key&&it.ringing}
  if(alarm==null&&timer==null)return@carrierEgress
  update(c){j->
   if(alarm!=null)j.put("alarms",JSONArray(alarms(c).map{if(it.id==alarm.id)it.copy(ringing=false).json()else it.json()}))
   if(timer!=null)j.put("timers",JSONArray(timers(c).map{if(it.id==timer.id)it.copy(ringing=false).json()else it.json()}))
  }
  notifyRing(c)
 }
 fun dismissUpcoming(c:Context,id:String,at:Long)=AppSdk.carrierEgress {
  val now=Instant.now()
  val alarm=alarms(c).firstOrNull{it.id==id&&it.enabled&&it.next==at&&at>now.toEpochMilli()}?:return@carrierEgress
  val zone=ZoneId.systemDefault()
  val skippedDay=if(alarm.snoozed)alarm.skippedDay else Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toEpochDay()
  val next=afterRing(alarm,at,now,zone)
  saveAlarm(c,alarm.copy(enabled=next>0,next=next,snoozed=false,skippedDay=skippedDay),preserveRing=true)
 }
 fun notifyRing(c:Context){if(rings(c).isEmpty()&&read(c).has("ringAt"))update(c){it.remove("ringAt")};if(ClockRingService.alive)c.startService(Intent(c,ClockRingService::class.java).setAction("refresh"))}
 fun stopAll(c:Context)=AppSdk.carrierEgress {update(c){it.remove("ringAt")};alarms(c).forEach{cancel(c,"a-${it.id}")};timers(c).forEach{cancel(c,"t-${it.id}")};update(c){j->j.put("alarms",JSONArray(alarms(c).map{it.copy(enabled=false,next=0,ringing=false).json()}));j.put("timers",JSONArray(timers(c).map{it.copy(remaining=it.left(c),end=0,wall=0,ringing=false).json()}))};ClockUpcoming.refresh(c);c.stopService(Intent(c,ClockRingService::class.java))}
 fun recover(c:Context)=AppSdk.carrierEgress {if(!allowed(c)){stopAll(c);return@carrierEgress};if(!exact(c)){ClockUpcoming.refresh(c);return@carrierEgress}
  alarms(c).filter{it.enabled}.forEach{a->val at=if(a.snoozed&&a.next>System.currentTimeMillis())a.next else next(a);saveAlarm(c,a.copy(next=at,enabled=a.days!=0||a.next>System.currentTimeMillis()),preserveRing=true)}
  timers(c).filter{it.end>0}.forEach{t->timer(c,t.copy(remaining=t.left(c),end=0),t.left(c)>0)}
  ClockUpcoming.refresh(c)
  // Boot/time broadcasts never start media playback; a future exact event does.
 }
}
class ClockReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){runCatching{
 when(i.action){ClockUpcoming.SHOW->AppSdk.carrierEgress { ClockUpcoming.refresh(c) };ClockUpcoming.DISMISS->ClockDisk.dismissUpcoming(c,i.getStringExtra("id").orEmpty(),i.getLongExtra("at",0));"ring"->if(ClockDisk.fire(c,i.getStringExtra("key").orEmpty(),i.getLongExtra("at",0)))ContextCompat.startForegroundService(c,Intent(c,ClockRingService::class.java));"dismiss","snooze"->ClockDisk.dismiss(c,i.getStringExtra("key").orEmpty(),i.action=="snooze");else->ClockDisk.recover(c)}
 }.onFailure{ClockDisk.update(c){j->j.put("error",it.message?:"Alarm could not start")}}}}
class ClockRingService:Service(){
 companion object{@Volatile var alive=false}
 override fun onCreate(){super.onCreate();alive=true}
 private var player:MediaPlayer?=null;private var vibrator:Vibrator?=null;private var wake:PowerManager.WakeLock?=null;private var focus:AudioFocusRequest?=null;private val handler=Handler(Looper.getMainLooper());private var current="";private var began=0L
 override fun onBind(i:Intent?)=null
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{if(!ClockDisk.allowed(this)){stopSelf();return START_NOT_STICKY};val first=ClockDisk.rings(this).firstOrNull();if(first==null){stopSelf();return START_NOT_STICKY}
  val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel("clock-alarms","Alarms & timers",NotificationManager.IMPORTANCE_HIGH).apply{setSound(null,null);enableVibration(false)})
  fun action(name:String)=PendingIntent.getBroadcast(this,0,Intent(this,ClockReceiver::class.java).setAction(name).setData(Uri.parse("onklav-clock:///$name/${first.first}")).putExtra("key",first.first),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val n=NotificationCompat.Builder(this,"clock-alarms").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(first.second.ifBlank{"Alarm"}).setContentText("Tap to open Clock").setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).setOngoing(true).setContentIntent(ClockDisk.open(this)).addAction(0,"Dismiss",action("dismiss")).apply{if(first.first.startsWith("a-"))addAction(0,"Snooze",action("snooze"));if(current!=first.first&&OnKlavPermissions.lockScreen(this@ClockRingService))setFullScreenIntent(ClockDisk.open(this@ClockRingService),true)}.build()
  startForeground(830,n)
  if(current!=first.first){release();current=first.first;began=ClockDisk.read(this).optLong("ringAt",System.currentTimeMillis());val a=ClockDisk.alarms(this).firstOrNull{"a-${it.id}"==current};val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();val am=getSystemService(AudioManager::class.java)
   focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).setOnAudioFocusChangeListener{if(it==AudioManager.AUDIOFOCUS_LOSS)ClockDisk.dismiss(this,current)}.build().also{am.requestAudioFocus(it)}
   fun play(uri:Uri){player=MediaPlayer().apply{setAudioAttributes(attributes);setDataSource(this@ClockRingService,uri);isLooping=true;setOnErrorListener{_,_,_->ClockDisk.update(this@ClockRingService){it.put("error","Alarm sound unavailable. Check alarm volume and sound.")};true};prepare();start()}}
   runCatching{play(if(a?.tone.isNullOrBlank())RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)else Uri.parse(a!!.tone))}.onFailure{player?.release();player=null;runCatching{play(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))}}
   if(a?.vibrate!=false){vibrator=getSystemService(Vibrator::class.java);vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0,500,500),0),attributes)}
   wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"onklav:clock").apply{acquire(21*60000L)}
  }
  handler.removeCallbacksAndMessages(null);handler.postDelayed(object:Runnable{override fun run(){val limit=ClockDisk.read(this@ClockRingService).optInt("silence",10).coerceIn(1,20)*60000L;if(!ClockDisk.allowed(this@ClockRingService)||System.currentTimeMillis()-began>=limit)ClockDisk.dismiss(this@ClockRingService,current)else handler.postDelayed(this,1000)}},1000)
  return START_NOT_STICKY
 }
 private fun release(){player?.release();player=null;vibrator?.cancel();vibrator=null;wake?.let{if(it.isHeld)it.release()};wake=null;focus?.let{getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it)};focus=null}
 override fun onDestroy(){alive=false;handler.removeCallbacksAndMessages(null);release();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()}
}
