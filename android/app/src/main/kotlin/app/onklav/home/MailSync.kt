package app.onklav.home

import android.app.*
import android.app.job.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

internal object MailSync{
 const val JOB=97001
 private const val CHANNEL="onklav-email"
 fun schedule(c:Context){val scheduler=c.getSystemService(JobScheduler::class.java);val minutes=c.getSharedPreferences("email-sync",0).getInt("minutes",30);if(minutes==0||!mailAccess(c,"background.tasks")){scheduler.cancel(JOB);return};val existing=scheduler.getPendingJob(JOB);if(existing!=null&&existing.intervalMillis==minutes.coerceAtLeast(15)*60000L)return;scheduler.schedule(JobInfo.Builder(JOB,ComponentName(c,MailSyncJobService::class.java)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(minutes.coerceAtLeast(15)*60000L).setPersisted(true).build())}
 suspend fun sync(c:Context){MailLocks.operations.withLock{
  val verify={check(mailAccess(c)&&mailAccess(c,"storage.secrets")&&mailAccess(c,"network.http")&&mailAccess(c,"background.tasks"));Unit};verify();val disk=MailDisk(c);val net=MailProtocol(c,disk,verify)
  for(a in disk.accounts().filter{it.enabled}){currentCoroutineContext().ensureActive();try{val old=disk.headers(a.id);val fetched=net.page(a,"INBOX");var page=fetched.headers;val rules=disk.rules();for(h in page){var row=h;for(r in rules.filter{it.matches(h)}){when(r.action){"star"->{net.flag(a,row,star=true);row=row.copy(starred=true)};"read"->{net.flag(a,row,seen=true);row=row.copy(seen=true)};"move"->{if(!r.folder.equals("INBOX",true)){net.move(a,row,r.folder);page=page.filter{it.key!=row.key};break}}};page=page.map{if(it.key==h.key)row else it}}};verify();val next=mailMergePage(old,page,"INBOX",fetched.validity);disk.saveHeaders(a.id,next);val fresh=page.filter{!it.seen&&old.none{o->o.key==it.key}};if(old.isNotEmpty()&&fresh.isNotEmpty()&&a.notify)notify(c,a,fresh.size);c.getSharedPreferences("email-sync",0).edit().putString(a.id,"Up to date").apply()}catch(e:CancellationException){throw e}catch(e:Exception){c.getSharedPreferences("email-sync",0).edit().putString(a.id,MailMime.error(e)).apply()}}
 }}
 private fun notify(c:Context,a:MailAccount,count:Int){if(!mailAccess(c,"notifications.post"))return;if(Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(c,android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;val manager=c.getSystemService(NotificationManager::class.java);manager.createNotificationChannel(NotificationChannel(CHANNEL,"Email",NotificationManager.IMPORTANCE_DEFAULT));if(!NotificationManagerCompat.from(c).areNotificationsEnabled())return;val intent=Intent(c,MainActivity::class.java).putExtra("open_email",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP);val open=PendingIntent.getActivity(c,97001,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val n=NotificationCompat.Builder(c,CHANNEL).setSmallIcon(app.onklav.shared.R.drawable.ref_app_email).setContentTitle("$count new message${if(count==1)"" else"s"}").setContentText(a.email).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build();try{manager.notify("email",a.id.hashCode(),n)}catch(_:SecurityException){}}
}
class MailSyncJobService:JobService(){private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO);private var job:Job?=null;override fun onStartJob(p:JobParameters):Boolean{job=scope.launch{try{withTimeout(110000){MailSync.sync(this@MailSyncJobService)}}catch(_:Exception){}finally{jobFinished(p,false)}};return true};override fun onStopJob(p:JobParameters):Boolean{job?.cancel();return true};override fun onDestroy(){scope.cancel();super.onDestroy()}}
