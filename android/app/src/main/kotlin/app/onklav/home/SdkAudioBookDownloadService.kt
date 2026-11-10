package app.onklav.home

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One explicit foreground transfer. Completed chapters survive cancellation or process death. */
class SdkAudioBookDownloadService:Service(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 private var job:Job?=null
 private var token=""
 companion object {
  internal val status=MutableStateFlow<Map<String,String>>(emptyMap())
  private val running=ConcurrentHashMap<String,Job>()
  fun start(c:Context,o:JSONObject,key:String){SdkAudioBooks.authorize(c,o);check(running.isEmpty()){"Another audiobook is downloading. Cancel it or wait until it finishes."};ContextCompat.startForegroundService(c,Intent(c,SdkAudioBookDownloadService::class.java).putExtra("owner",o.toString()).putExtra("book",key))}
  fun cancel(token:String){SdkAudioBooks.cancel(token);running[token]?.cancel();status.value=status.value+(token to "Stopped · Completed chapters kept; tap Download to resume")}
  fun cancelApp(id:String){running.keys.filter{id.isEmpty()||it.startsWith("$id/")}.forEach{cancel(it)}}
 }
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent==null){stopSelf();return START_NOT_STICKY}
  if(intent.hasExtra("cancel")){cancel(intent.getStringExtra("cancel").orEmpty());stopSelf();return START_NOT_STICKY}
  val o=runCatching{JSONObject(intent.getStringExtra("owner")?:"")}.getOrNull()?:run{stopSelf();return START_NOT_STICKY};val key=intent.getStringExtra("book")?:run{stopSelf();return START_NOT_STICKY}
  if(job?.isActive==true)return START_NOT_STICKY
  token=SdkAudioBooks.downloadToken(o,key)
  try{SdkAudioBooks.authorize(this,o);notification("Starting download…")}catch(e:Exception){stopSelf();return START_NOT_STICKY}
  job=scope.launch{try{SdkAudioBooks.download(this@SdkAudioBookDownloadService,o,key){line->status.value=status.value+(token to line);notification(line)};status.value=status.value+(token to "Available offline")}
   catch(e:Exception){status.value=status.value+(token to (e.message?:"Download interrupted · Tap Download to resume"))}
   finally{running.remove(token);withContext(NonCancellable+Dispatchers.Main){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId)}}};running[token]=job!!
  return START_NOT_STICKY
 }
 private fun notification(line:String){val manager=getSystemService(NotificationManager::class.java);manager.createNotificationChannel(NotificationChannel("audiobooks-download","Audiobook downloads",NotificationManager.IMPORTANCE_LOW));val cancel=PendingIntent.getService(this,token.hashCode(),Intent(this,SdkAudioBookDownloadService::class.java).putExtra("cancel",token),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  startForeground(0x41424b,Notification.Builder(this,"audiobooks-download").setSmallIcon(app.onklav.shared.R.drawable.ref_app_audiobooks).setContentTitle("AudioBooks · Downloading").setContentText(line).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).addAction(Notification.Action.Builder(null,"Cancel",cancel).build()).build())
 }
 override fun onTimeout(startId:Int,fgsType:Int){SdkAudioBooks.cancel(token);job?.cancel();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
 override fun onDestroy(){if(token.isNotEmpty()){SdkAudioBooks.cancel(token);running.remove(token)};scope.cancel();super.onDestroy()}
}
