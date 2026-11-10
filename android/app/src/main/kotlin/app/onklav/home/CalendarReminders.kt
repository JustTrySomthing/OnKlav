package app.onklav.home

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import org.json.*
import java.time.*

internal data class CalendarAlert(val key:String,val eventId:String,val updated:Long,val at:Long,val sound:Boolean,val title:String,val whenText:String)
internal object CalendarReminders{
 private fun prefs(c:Context)=c.getSharedPreferences("calendar-reminders",0)
 private fun intent(c:Context)=PendingIntent.getBroadcast(c,902,Intent(c,CalendarReceiver::class.java).setAction("calendar.fire").setData(Uri.parse("onklav-calendar://schedule")),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 fun enabled(c:Context)=NotificationManagerCompat.from(c).areNotificationsEnabled()
 fun exact(c:Context)=Build.VERSION.SDK_INT<31||c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
 fun plan(state:CalendarState,now:Long,delivered:Set<String>,zone:ZoneId=ZoneId.systemDefault()):List<CalendarAlert>{
  val out=mutableListOf<CalendarAlert>()
  state.events.forEach{e->e.reminders.forEachIndexed{i,r->
   val begin=Instant.ofEpochMilli(now).plusSeconds(r.minutes*60L).minusSeconds(600)
   val stop=begin.atZone(zone).plusYears(400).toInstant()
   val candidates=CalendarRules.occurrences(e,begin,stop,3,false,zone)
   for(o in candidates){val at=o.start.toInstant().toEpochMilli()-r.minutes*60000L;val key="${e.id}:${e.updated}:${o.date}:$i:${r.minutes}:${r.sound}"
    if(at>=now-600000&&key !in delivered){out+=CalendarAlert(key,e.id,e.updated,at,r.sound,e.title,if(e.allDay)"${o.date} · All day"else"${o.start.withZoneSameInstant(zone).toLocalDate()} · ${o.start.withZoneSameInstant(zone).toLocalTime()}");break}
   }
  }};return out.sortedBy{it.at}
 }
 private fun delivered(c:Context):Set<String> =prefs(c).getStringSet("delivered",emptySet()).orEmpty().toSet()
 private fun snoozes(c:Context)=runCatching{val a=JSONArray(prefs(c).getString("snoozes","[]"));(0 until a.length()).map{a.getJSONObject(it)}}.getOrDefault(emptyList())
 private fun all(c:Context,s:CalendarState,now:Long):List<CalendarAlert>{val base=plan(s,now,delivered(c));val snoozed=snoozes(c).mapNotNull{j->s.events.firstOrNull{it.id==j.optString("id")&&it.updated==j.optLong("updated")}?.let{e->val at=j.optLong("at");if(at<now-600000)null else CalendarAlert(j.getString("key"),e.id,e.updated,at,j.optBoolean("sound"),e.title,"Snoozed reminder")}};return(base+snoozed).sortedBy{it.at}}
 fun recover(c:Context)=synchronized(CalendarDisk.lock){
  val am=c.getSystemService(AlarmManager::class.java);if(!enabled(c)){am.cancel(intent(c));return@synchronized}
  val now=System.currentTimeMillis();val next=all(c,CalendarDisk(c).read(),now).firstOrNull()?:run{am.cancel(intent(c));return@synchronized};val at=maxOf(now+1000,next.at)
  if(Build.VERSION.SDK_INT<31||am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent(c))else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent(c))
 }
 private fun action(c:Context,a:CalendarAlert,name:String)=PendingIntent.getBroadcast(c,0,Intent(c,CalendarReceiver::class.java).setAction("calendar.$name").setData(Uri.parse("onklav-calendar://$name/${Uri.encode(a.key)}")).putExtra("id",a.eventId).putExtra("updated",a.updated).putExtra("key",a.key).putExtra("sound",a.sound),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 private fun show(c:Context,a:CalendarAlert){
  val nm=c.getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel("calendar-silent","Calendar · silent",NotificationManager.IMPORTANCE_DEFAULT).apply{setSound(null,null);enableVibration(false)})
  nm.createNotificationChannel(NotificationChannel("calendar-sound","Calendar · alarm sound",NotificationManager.IMPORTANCE_HIGH).apply{setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());enableVibration(false)})
  val open=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java).setAction("calendar.open").setData(Uri.parse("onklav-calendar://open/${Uri.encode(a.key)}")).putExtra("open_native_tool","calendar").putExtra("calendar_event",a.eventId).putExtra("calendar_date",a.key.split(':').getOrNull(2)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val n=NotificationCompat.Builder(c,if(a.sound)"calendar-sound"else"calendar-silent").setSmallIcon(android.R.drawable.ic_menu_my_calendar).setContentTitle(a.title).setContentText(a.whenText).setStyle(NotificationCompat.BigTextStyle().bigText(a.whenText)).setCategory(NotificationCompat.CATEGORY_REMINDER).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).addAction(0,"Snooze 10 min",action(c,a,"snooze")).addAction(0,"Dismiss",action(c,a,"dismiss")).build()
  if(Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(c,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
  nm.notify(a.key,902,n)
 }
 fun fire(c:Context)=synchronized(CalendarDisk.lock){if(!enabled(c)){c.getSystemService(AlarmManager::class.java).cancel(intent(c));return@synchronized}
  val now=System.currentTimeMillis();val due=all(c,CalendarDisk(c).read(),now).filter{it.at<=now+1500}.take(20);val done=delivered(c).toMutableSet();val snoozes=snoozes(c).toMutableList()
  due.forEach{a->show(c,a);done+=a.key;snoozes.removeAll{it.optString("key")==a.key}}
  check(prefs(c).edit().putStringSet("delivered",done.toList().takeLast(50000).toSet()).putString("snoozes",JSONArray(snoozes).toString()).commit());recover(c)
 }
 fun snooze(c:Context,i:Intent)=synchronized(CalendarDisk.lock){val key=i.getStringExtra("key")?:return@synchronized;c.getSystemService(NotificationManager::class.java).cancel(key,902)
  if(i.action=="calendar.snooze"){val e=CalendarDisk(c).read().events.firstOrNull{it.id==i.getStringExtra("id")&&it.updated==i.getLongExtra("updated",-1)}?:return@synchronized
   val nextKey="$key:snooze";val pending=snoozes(c)
   // Notification actions can be delivered twice or replayed after the child
   // reminder fired. Stable successor identity also recognizes older timestamp keys.
   fun child(value:String)=value==nextKey||value.startsWith("$nextKey:")
   if(pending.any{child(it.optString("key"))}||delivered(c).any(::child))return@synchronized
   val list=pending.filter{it.optString("key")!=key&&it.optLong("at")>System.currentTimeMillis()-600000}.takeLast(499)+JSONObject().put("key",nextKey).put("id",e.id).put("updated",e.updated).put("at",System.currentTimeMillis()+600000).put("sound",i.getBooleanExtra("sound",false));check(prefs(c).edit().putString("snoozes",JSONArray(list).toString()).commit());recover(c)
  }
 }
 fun cleanup(c:Context,old:List<CalendarEvent>,next:List<CalendarEvent>){val changed=old.filter{a->next.none{it.id==a.id&&it.updated==a.updated}}.map{it.id}.toSet();val nm=c.getSystemService(NotificationManager::class.java);nm.activeNotifications.filter{n->n.tag?.substringBefore(':') in changed}.forEach{nm.cancel(it.tag,it.id)};recover(c)}
}
class CalendarReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){val pending=goAsync();CoroutineScope(Dispatchers.IO).launch{try{withTimeout(20000){when(i.action){"calendar.fire"->CalendarReminders.fire(c);"calendar.snooze","calendar.dismiss"->CalendarReminders.snooze(c,i);else->CalendarReminders.recover(c)}}}catch(e:Exception){c.getSharedPreferences("calendar-reminders",0).edit().putString("error","Reminders need attention. Open Calendar to retry.").apply()}finally{pending.finish()}}}}
