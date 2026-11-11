package app.onklav.home

import android.app.job.*
import android.content.*
import java.util.concurrent.atomic.AtomicBoolean

/** One bounded recovery pass. No automatic SMS/MMS resend, no UI or emulator loop. */
internal object TransportRecovery {
    private const val JOB=4181
    private val running=AtomicBoolean(false)
    fun schedule(context:Context,delay:Long=1000L) {
        val scheduler=context.getSystemService(JobScheduler::class.java)?:return
        val due=System.currentTimeMillis()+delay.coerceIn(1000L,15*60*1000L)
        val previous=scheduler.getPendingJob(JOB)?.extras?.getLong("due",0)?:0
        if(previous>System.currentTimeMillis()&&previous<=due)return
        val extras=android.os.PersistableBundle().apply{putLong("due",due)}
        val requested=JobInfo.Builder(JOB,ComponentName(context,TransportRecoveryService::class.java))
            .setMinimumLatency(delay.coerceIn(1000L,15*60*1000L)).setExtras(extras).setPersisted(true).build()
        scheduler.schedule(requested)
    }
    fun run(context:Context):Long? {
        if(!running.compareAndSet(false,true))return null
        try {
            MessagingTransport.recoverResults(context)
            MmsDownloadRecovery.recover(context)
            if(MessagingTransport.role(context))MessagingTransport.retrySms(context)
            val social=runCatching { RealSocial.recover(context) }.getOrElse { ChatStore.lastError.value=it.message; null }
            val walkie=runCatching { SdkWalkieTransport.recover(context) }.getOrElse { ChatStore.lastError.value=it.message; null }
            val games=runCatching { FriendGames.recoverInvites(context) }.getOrElse { ChatStore.lastError.value=it.message; null }
            val nextInvite=listOfNotNull(walkie,social,games).minOrNull()
            val receipts=CarrierResults(context)
            val nextReceipt=receipts.ids().mapNotNull{runCatching{receipts.read(it)?.let{row->MessagingTransport.receiptWait(row).takeIf{n->n>0}?:900_000L}}.getOrNull()}.minOrNull()
            return if(nextReceipt!=null)minOf(nextInvite?:900_000L,nextReceipt)else nextInvite
        } finally {running.set(false)}
    }
}
class TransportRecoveryService:JobService() {
    override fun onStartJob(params:JobParameters):Boolean {
        if(params.jobId==RealSocial.CLEANUP_JOB){
            ChatStore.executor.execute { try { RealSocial.cleanup(applicationContext) } finally { jobFinished(params,false) } }
            return true
        }
        ChatStore.executor.execute {var next:Long?=null;try{next=TransportRecovery.run(applicationContext)}catch(e:Exception){ChatStore.lastError.value=e.message}finally{jobFinished(params,false);next?.let{delay->android.os.Handler(mainLooper).post{TransportRecovery.schedule(applicationContext,delay)}}}}
        return true
    }
    override fun onStopJob(params:JobParameters)=true
}
class TransportRecoveryReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED))TransportRecovery.schedule(context)
    }
}
