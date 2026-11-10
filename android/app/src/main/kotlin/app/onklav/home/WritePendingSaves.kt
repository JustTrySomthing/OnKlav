package app.onklav.home

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeout

/** A protection transaction must not race a queued editor autosave. */
internal object WritePendingSaves{
 private val finalSaves=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 fun finalSnapshot(c:Context,doc:WriteDocument,disk:Mutex){track(finalSaves.launch{try{disk.withLock{AppSdk.carrierEgress{val stored=runCatching{WriteFiles.read(c,doc.id)}.getOrNull();if(stored==null||stored.modified<=doc.modified)WriteFiles.save(c,doc)}}}catch(e:Exception){android.util.Log.w("OnKlavWrite","Final draft could not be saved",e)}})}
 private val jobs=java.util.concurrent.ConcurrentHashMap.newKeySet<Job>()
 fun track(job:Job):Job{jobs.add(job);job.invokeOnCompletion{jobs.remove(job)};return job}
 suspend fun finish(){withTimeout(15000){while(true){val pending=jobs.toList();if(pending.isEmpty())return@withTimeout;pending.joinAll()}}}
}
