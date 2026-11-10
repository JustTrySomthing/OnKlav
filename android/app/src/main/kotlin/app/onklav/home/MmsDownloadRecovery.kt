package app.onklav.home

import android.app.job.*
import android.content.ComponentName
import android.content.Context
import android.telephony.SmsManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withContext

/** Retrying an incoming download never retries an outgoing send or chooses a different SIM. */
internal fun incomingMmsTransient(code:Int)=code in setOf(android.app.Activity.RESULT_CANCELED,SmsManager.MMS_ERROR_UNSPECIFIED,SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS,SmsManager.MMS_ERROR_HTTP_FAILURE,SmsManager.MMS_ERROR_IO_ERROR,SmsManager.MMS_ERROR_RETRY,SmsManager.MMS_ERROR_NO_DATA_NETWORK)
internal fun incomingMmsFailure(code:Int)=when(code){
 SmsManager.MMS_ERROR_DATA_DISABLED,SmsManager.MMS_ERROR_NO_DATA_NETWORK->"The picture could not download. Turn on mobile data for the receiving SIM, then retry. The request is kept."
 SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID,SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION->"The receiving SIM is unavailable. Check that SIM is enabled, then retry. The request is kept."
 SmsManager.MMS_ERROR_INVALID_APN,SmsManager.MMS_ERROR_CONFIGURATION_ERROR->"The carrier’s picture-message settings could not be used. Check the receiving SIM’s MMS/APN settings, then retry. The request is kept."
 SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER->"The carrier has disabled picture messaging for this SIM. The request is kept."
 else->"The carrier could not download this picture. Check mobile data and retry. The request is kept."
}
internal const val INCOMING_MMS_TIMEOUT_MS=180_000L
internal fun incomingMmsInProgress(attempt:Long,now:Long)=attempt>0&&now>=attempt&&now-attempt<INCOMING_MMS_TIMEOUT_MS
internal fun incomingMmsStatus(error:String?,attempt:Long,now:Long):String=when{
 incomingMmsInProgress(attempt,now)->"Downloading picture…"
 attempt>0->"The picture download timed out. Retry to reconnect. The request is kept."
 error.isNullOrBlank()||error=="Downloading…"||error=="Downloading..."->"Picture waiting to download."
 error.startsWith("Carrier download failed (0)")->incomingMmsFailure(android.app.Activity.RESULT_CANCELED)
 else->error
}
class MmsDownloadRecovery:JobService(){
 companion object {
  private const val ID=0x4D4D53
  internal fun schedule(c:Context,minimumDelay:Long=1000L){
   val p=c.getSharedPreferences("mms_transfers",0);val now=System.currentTimeMillis()
   val due=p.all.keys.filter{it.endsWith(".retry_due")}.map{p.getLong(it,0)}.filter{it>0}.minOrNull()?:return
   c.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(ID,ComponentName(c,MmsDownloadRecovery::class.java))
    .setPersisted(true).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency((due-now).coerceAtLeast(minimumDelay.coerceAtLeast(1000))).build())
  }
  internal fun recover(c:Context){
   if(!MessagingTransport.role(c)||!MessagingTransport.carrierEnabled(c))return
   val p=c.getSharedPreferences("mms_transfers",0);val now=System.currentTimeMillis()
   val ids=(p.all.keys.filter{it.endsWith(".retry_due")&&p.getLong(it,0) in 1..now}.map{it.removeSuffix(".retry_due")}+p.all.keys.filter{it.endsWith(".file_pending")&&p.getBoolean(it,false)}.map{it.removeSuffix(".file_pending")}).distinct()
   var failed=false
   for(id in ids){
    if(!MessagingTransport.role(c))return
    try{
    // Keep the previous durable deadline until the action commits its successor.
    // One refused journal write must not discard this job or starve other jobs.
    if(p.getBoolean("$id.file_pending",false)){runCatching{MessagingTransport.finishReceivedMms(c,id)}.onFailure{check(p.edit().putLong("$id.retry_due",now+30000).commit());ChatStore.lastError.value="Received picture filing needs review: ${it.message}"};continue}
    if(!p.getBoolean("$id.complete",false)){
     val attempt=p.getLong("$id.attempt",0)
     if(attempt>0){
      if(incomingMmsInProgress(attempt,now)){check(p.edit().putLong("$id.retry_due",attempt+INCOMING_MMS_TIMEOUT_MS).commit());continue}
      MessagingTransport.downloaded(c,id,false,SmsManager.MMS_ERROR_RETRY)
     }else {
      val result=runCatching{MessagingTransport.download(c,id,automatic=true)}
      if(result.isFailure&&p.contains("$id.retry_due")&&p.getLong("$id.retry_due",0)<=now)throw result.exceptionOrNull()!!
     }
    }else check(p.edit().remove("$id.retry_due").commit())
    }catch(e:Exception){
     failed=true
     runCatching{check(p.edit().putLong("$id.retry_due",now+30000).commit())}
     ChatStore.lastError.value="Picture recovery retained for retry: ${e.message}"
    }
   }
   schedule(c,if(failed)30000 else 1000)
  }
 }
 override fun onStartJob(params:JobParameters):Boolean{ChatStore.executor.execute{try{recover(applicationContext)}finally{jobFinished(params,false)}};return true}
 override fun onStopJob(params:JobParameters)=false
}

@Composable internal fun PendingPictureBanner(){
 val c=androidx.compose.ui.platform.LocalContext.current
 val epoch by ChatStore.epoch.collectAsState();var count by remember{mutableIntStateOf(0)};var open by remember{mutableStateOf(false)}
 LaunchedEffect(epoch){count=withContext(ChatStore.dispatcher){MessagingTransport.pendingDownloads(c).size}}
 if(count>0)Surface(onClick={open=true},modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.secondaryContainer){
  Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){
   Column(Modifier.weight(1f)){Text(if(count==1)"1 picture message pending"else"$count picture messages pending",style=MaterialTheme.typography.titleSmall);Text("View download status and retry",style=MaterialTheme.typography.bodySmall)}
   Text("View",color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(start=12.dp))
  }
 }
 if(open)PendingPicturesDialog{open=false}
}
