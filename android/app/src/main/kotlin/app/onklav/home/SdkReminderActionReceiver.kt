package app.onklav.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class SdkReminderActionReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  val token=intent.getStringExtra("token")?:return
  val pending=goAsync()
  worker.execute{try{SdkNotifications.snooze(context.applicationContext,token)}catch(e:Exception){ChatStore.lastError.value="Reminder could not be snoozed. Open the app to try again."}finally{pending.finish()}}
 }
 private companion object{val worker=Executors.newSingleThreadExecutor()}
}
