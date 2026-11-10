package app.onklav.home

import android.content.Context
import android.net.Uri
import app.onklav.shared.NativeCore
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** SAF -> private bounded copy -> Rust ZIP/record verification -> read-only summary.
 * Inspect needs no SMS access; explicit compare uses a read-only provider snapshot.
 * No provider writes, role request, metadata restore or guest service.
 */
internal object HistoryImport {
    data class Comparison(val matched:Int,val fresh:Int,val needsReview:Int,val skipped:Int,val flagsMapped:Int,val flagsDeferred:Int,val threadsMapped:Int,val threadsDeferred:Int,val planSha:String="") {
        companion object {fun checked(data:JSONObject):Comparison {
            check(!data.getBoolean("provider_writes")&&!data.getBoolean("restore_available"))
            return Comparison(data.getInt("matched"),data.getInt("new"),data.getInt("review"),data.getInt("skipped"),data.getInt("flags_mapped"),data.getInt("flags_deferred"),data.getInt("threads_mapped"),data.getInt("threads_deferred"),data.getString("plan_sha256").also{require(it.matches(Regex("[a-f0-9]{64}")))})
        }}
    }
    const val MAX_ARCHIVE=2_400_000_000L
    private val running=AtomicBoolean(false)
    data class Review(val sms:Int,val mms:Int,val parts:Int,val mediaBytes:Long,
        val inbox:Int,val sent:Int,val other:Int,val undownloaded:Int,
        val groups:Int,val blocked:Int,val folders:Int,val flags:Int,val sha256:String,val comparison:Comparison?=null,val resumePending:List<String>?=null) {
        companion object {
            fun checked(data:JSONObject):Review {
                check(data.getBoolean("verified")&&!data.getBoolean("provider_writes")&&!data.getBoolean("restore_available"))
                val categories=data.getJSONObject("categories");val metadata=data.getJSONObject("metadata")
                return Review(data.getInt("sms"),data.getInt("mms"),data.getInt("parts"),data.getLong("media_bytes"),
                    categories.getInt("inbox"),categories.getInt("sent"),categories.getInt("other"),categories.getInt("undownloaded"),
                    metadata.getInt("groups"),metadata.getInt("blocked"),metadata.getInt("threads"),metadata.getInt("messages"),data.getString("sha256"),data.optJSONObject("comparison")?.let{Comparison.checked(it)},data.optJSONObject("resume")?.getJSONArray("pending_sources")?.let{a->java.util.Collections.unmodifiableList((0 until a.length()).map{a.getString(it)})})
            }
        }
    }
    const val CARRIER_SETUP_REQUIRED="Enable carrier phone testing in Setup before restoring. Nothing has been restored."
    fun inspect(c:Context,uri:Uri,stop:HistoryBackup.Stop,compare:Boolean=false,expectedSha:String?=null,progress:(String)->Unit):Review = runJob(c,uri,stop,compare,false,expectedSha,progress,{_,review->review})
    internal fun <T> withPrepared(c:Context,uri:Uri,stop:HistoryBackup.Stop,expectedSha:String,progress:(String)->Unit,consume:(File,Review)->T):T = runJob(c,uri,stop,true,true,expectedSha,progress,consume)
    internal fun inspectResume(c:Context,uri:Uri,stop:HistoryBackup.Stop,binding:HistoryResume.Binding,progress:(String)->Unit):Review = runJob(c,uri,stop,true,false,binding.archiveSha,progress,{_,review->review},binding)
    internal fun <T> withResume(c:Context,uri:Uri,stop:HistoryBackup.Stop,binding:HistoryResume.Binding,progress:(String)->Unit,consume:(File,Review)->T):T = runJob(c,uri,stop,true,true,binding.archiveSha,progress,consume,binding)
    private fun <T> runJob(c:Context,uri:Uri,stop:HistoryBackup.Stop,compare:Boolean,prepare:Boolean,expectedSha:String?,progress:(String)->Unit,consume:(File,Review)->T,resume:HistoryResume.Binding?=null):T {
        require(uri.scheme=="content"){"Choose a backup using Files"}
        check(running.compareAndSet(false,true)){"Another backup check is running"}
        var stage:File?=null
        try {
            stop.check()
            val base=File(c.filesDir,"history-import")
            check(!Files.isSymbolicLink(base.toPath())){"Invalid import staging directory"}
            check(base.isDirectory||base.mkdir()){"Cannot create import staging directory"}
            // Only our own UUID jobs, without following any nested link.
            base.listFiles()?.forEach{f->if(f.name.matches(Regex("[a-f0-9]{32}"))&&!Files.isSymbolicLink(f.toPath()))HistoryBackup.removeStage(f)}
            val job=UUID.randomUUID().toString().replace("-","")
            val created=File(base,job);check(created.mkdir());stage=created;stop.attach(created)
            val input=c.contentResolver.openAssetFileDescriptor(uri,"r",stop.signal)?.createInputStream() ?: error("Could not open the selected backup")
            progress("Copying backup for checking…")
            input.use{stream->
                stop.stream(stream)
                try {
                    File(created,"source.zip").outputStream().use{output->
                        val buffer=ByteArray(65536);var size=0L;var reported=0L
                        while(true){stop.check();val n=stream.read(buffer);if(n<0)break;size+=n
                            require(size<=MAX_ARCHIVE){"Backup exceeds the 2.4 GB archive limit"};output.write(buffer,0,n)
                            if(size-reported>=8*1024*1024){reported=size;progress("Copying backup: ${size/(1024*1024)} MiB")}}
                    }
                }finally{stop.stream(null)}
            }
            stop.check();progress("Checking archive, messages and media…")
            fun checked():Review {
                val request=JSONObject().put("op","history_import").put("action",if(prepare)"prepare" else if(compare)"compare" else "inspect").put("job",job)
                resume?.let{request.put("record",it.id).put("map_sha",it.mapSha)}
                if(compare)request.put("expected_sha",expectedSha ?: error("Check the backup before comparing"))
                val response=JSONObject(NativeCore.dispatch(c.filesDir.absolutePath,request.toString()))
                check(response.optBoolean("ok")){response.optString("error","Backup check failed")}
                stop.check();return Review.checked(response.getJSONObject("value"))
            }
            val reviewed=if(compare)HistoryBackup.reading(c,stop){stable->
                val current=File(created,"current");check(current.mkdir());check(File(current,"media").mkdir())
                progress("Reading phone history for comparison…")
                HistoryBackup.capture(c,current,stop,progress,stable)
                progress("Comparing message content and media…")
                val result=checked();stable();result
            }else checked()
            return consume(created,reviewed)
        }finally{
            stop.attach(null)
            try{stage?.let{HistoryBackup.removeStage(it)}}finally{running.set(false)}
        }
    }
}
