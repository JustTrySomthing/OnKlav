package app.onklav.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

/** Recompute absolute job latency after wall-clock changes or reboot; never execute guests. */
class SdkReminderRecoveryReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,Intent.ACTION_TIME_CHANGED))return
  val pending=goAsync()
  worker.execute{try{SdkScheduledNotifications.recover(context.applicationContext,rescheduleAbsolute=true)}catch(e:Exception){ChatStore.lastError.value="App reminders need recovery. Open OnKlav to retry."}finally{pending.finish()}}
 }
 private companion object{val worker=Executors.newSingleThreadExecutor()}
}
