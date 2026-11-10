package app.onklav.home

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal object SdkNotifications {
 private const val PREFS="sdk-app-notifications"
 private fun manager(c:Context)=c.getSystemService(NotificationManager::class.java)
 private fun prefix(id:String):String{require(id.length<=128&&id.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+")));return "onklav.app."+AppSdk.desktopId(id)+"."}
 private fun slot(id:String,key:String)=prefix(id)+key
 fun allowed(c:Context)=NotificationManagerCompat.from(c).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
 internal fun allowedForOwner(c:Context,id:String):Boolean{prefix(id);return allowed(c)&&manager(c).getNotificationChannel("sdk-app-"+AppSdk.desktopId(id))?.importance!=NotificationManager.IMPORTANCE_NONE}
 internal fun identity(owner:JSONObject):String{val id=owner.getString("id");prefix(id);require(owner.getString("publisher").matches(Regex("[a-f0-9]{64}")));require(owner.getString("name").length in 1..60);return id}
 internal fun execute(c:Context,request:JSONObject,authorize:()->Unit):JSONObject=AppSdk.carrierEgress{
  val owner=request.getJSONObject("owner");val id=identity(owner);val service=request.getString("service");val args=request.getJSONObject("args")
  if(service in setOf("notifications.status","notifications.pending"))return@carrierEgress SdkScheduledNotifications.query(c,request,authorize)
  val key=args.getString("key")
  require(key.length in 1..48&&key.matches(Regex("[A-Za-z0-9._-]+")));require(service in setOf("notifications.show","notifications.schedule","notifications.schedule_at","notifications.cancel"));authorize()
  val p=c.getSharedPreferences(PREFS,0);val tag=slot(id,key)
  if(service in setOf("notifications.schedule","notifications.schedule_at"))return@carrierEgress SdkScheduledNotifications.schedule(c,request,authorize)
  if(service=="notifications.cancel"){SdkScheduledNotifications.cancel(c,id,key);manager(c).cancel(tag,0);check(p.edit().remove(tag).commit());return@carrierEgress JSONObject().put("cancelled",true)}
  check(allowed(c)){"Notifications are off for OnKlav. Allow them in Android Settings."}
  val title=args.getString("title");val text=args.getString("text");require(title.isNotBlank()&&title.length<=80&&text.isNotBlank()&&text.length<=500)
  val channel="sdk-app-"+AppSdk.desktopId(id);manager(c).createNotificationChannel(NotificationChannel(channel,includedAppDisplayName(owner),NotificationManager.IMPORTANCE_DEFAULT))
  check(manager(c).getNotificationChannel(channel).importance!=NotificationManager.IMPORTANCE_NONE){"This app's notification channel is disabled in Android Settings."}
  val active=manager(c).activeNotifications.mapNotNull{it.tag}.toSet();val stale=p.all.keys.filter{it.startsWith(prefix(id))&&it !in active};if(stale.isNotEmpty()){val edit=p.edit();stale.forEach{edit.remove(it)};check(edit.commit())}
  val own=p.all.keys.filter{it.startsWith(prefix(id))};check(tag in own||own.size<8){"Keep up to8 active notifications per app; cancel an older one first"}
  val token=UUID.randomUUID().toString().replace("-","");val row=JSONObject().put("id",id).put("publisher",owner.getString("publisher")).put("token",token)
  val intent=Intent(c,MainActivity::class.java).setData(Uri.parse("onklav://sdk-notification/$token")).putExtra("sdk_notice",token).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  val open=PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val builder=NotificationCompat.Builder(c,channel).setSmallIcon(app.onklav.shared.R.drawable.ref_nav_apps).setSubText(includedAppDisplayName(owner)).setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
  if(request.optBoolean("reminder")&&args.has("snooze_seconds")){
   row.put("args",JSONObject(args.toString()))
   val snooze=Intent(c,SdkReminderActionReceiver::class.java).setData(Uri.parse("onklav://sdk-snooze/$token")).putExtra("token",token)
   builder.addAction(0,"Snooze",PendingIntent.getBroadcast(c,0,snooze,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
  }
  val notice=builder.build()
  val previous=p.getString(tag,null);check(p.edit().putString(tag,row.toString()).commit())
  try{authorize();manager(c).notify(tag,0,notice)}catch(error:Exception){val edit=p.edit();if(previous==null)edit.remove(tag)else edit.putString(tag,previous);check(edit.commit());open.cancel();throw error}
  JSONObject().put("posted",true)
 }
 fun clearApp(c:Context,id:String)=AppSdk.carrierEgress{
  SdkScheduledNotifications.clearApp(c,id)
  val start=prefix(id);manager(c).activeNotifications.filter{it.tag?.startsWith(start)==true}.forEach{manager(c).cancel(it.tag,it.id)}
  val p=c.getSharedPreferences(PREFS,0);val edit=p.edit();p.all.keys.filter{it.startsWith(start)}.forEach{edit.remove(it)};check(edit.commit())
 }
 internal fun snooze(c:Context,token:String,now:Long=System.currentTimeMillis(),current:(String,String)->JSONObject?={id,publisher->currentOwner(c,id,publisher)})=AppSdk.carrierEgress{
  require(token.matches(Regex("[a-f0-9]{32}")))
  val p=c.getSharedPreferences(PREFS,0)
  val match=p.all.entries.firstOrNull{(_,v)->v is String&&runCatching{JSONObject(v).optString("token")==token}.getOrDefault(false)}?:return@carrierEgress
  val r=JSONObject(match.value as String);val id=r.getString("id");val publisher=r.getString("publisher")
  val owner=current(id,publisher)?:return@carrierEgress
  val args=r.getJSONObject("args");SdkReminderPolicy.validate(args)
  val due=Math.addExact(now,Math.multiplyExact(args.getLong("snooze_seconds"),1000L))
  args.remove("delay_seconds");args.put("at_ms",due)
  SdkScheduledNotifications.schedule(c,JSONObject().put("service","notifications.schedule_at").put("owner",owner).put("args",args),{check(current(id,publisher)!=null)},now)
  check(p.edit().remove(match.key).commit());manager(c).cancel(match.key,0)
 }
 internal fun currentOwner(c:Context,id:String,publisher:String):JSONObject?{
  val rows=AppSdk.sync(c,JSONObject().put("action","list")) as JSONArray
  return (0 until rows.length()).map{rows.getJSONObject(it)}.firstOrNull{row->row.optBoolean("installed")&&row.child("manifest").optString("id")==id&&row.child("manifest").optString("publisher")==publisher&&row.optJSONArray("grants").let{grants->grants!=null&&(0 until grants.length()).any{grants.optString(it)=="notifications.post"}}}?.child("manifest")?.let{JSONObject().put("id",id).put("publisher",publisher).put("name",it.getString("name"))}
 }
 internal fun resolveTap(c:Context,token:String,eligible:(String,String)->Boolean={id,publisher->currentOwner(c,id,publisher)!=null
 }):String?=AppSdk.carrierEgress{
  if(!token.matches(Regex("[a-f0-9]{32}")))return@carrierEgress null
  val row=c.getSharedPreferences(PREFS,0).all.values.asSequence().filterIsInstance<String>().mapNotNull{runCatching{JSONObject(it)}.getOrNull()}.firstOrNull{it.optString("token")==token}?:return@carrierEgress null
  val id=row.optString("id");val publisher=row.optString("publisher")
  if(!id.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+"))||id.length>128||!publisher.matches(Regex("[a-f0-9]{64}")))return@carrierEgress null
  id.takeIf{runCatching{eligible(it,publisher)}.getOrDefault(false)}
 }
}
