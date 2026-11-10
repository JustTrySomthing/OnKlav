package app.onklav.home

import android.content.Context
import android.net.Uri
import app.onklav.shared.NativeCore
import org.json.JSONObject
import java.io.File

/** Explicitly reviewed continuation under the original restore record. */
internal object HistoryResume {
    data class Binding(val id:String,val mapSha:String,val archiveSha:String,val pendingSources:List<String> = emptyList())
    fun binding(c:Context,id:String):Binding {
        val value=HistoryRecovery.resumeInfo(c,id)
        return Binding(value.getString("id"),value.getString("map_sha"),value.getString("archive_sha"),value.getJSONArray("pending_sources").let{a->java.util.Collections.unmodifiableList((0 until a.length()).map{a.getString(it)})})
    }
    fun review(c:Context,uri:Uri,saved:Binding,stop:HistoryBackup.Stop,progress:(String)->Unit):HistoryConfirmation=HistoryRestore.exclusive {
        check(saved==binding(c,saved.id)){"Saved record changed. Reopen and review again."}
        val review=HistoryImport.inspectResume(c,uri,stop,saved,progress)
        HistoryConfirmation.prepare(c,uri,review,saved)
    }
    fun applyChecked(c:Context,ticket:HistoryConfirmation,stop:HistoryBackup.Stop,progress:(String)->Unit):JSONObject {
        val provider=HistoryRestoreProvider(c,stop);provider.checkAccess()
        return run(c,ticket,provider,stop,progress)
    }
    internal fun run(c:Context,ticket:HistoryConfirmation,provider:HistoryRestore.Provider,stop:HistoryBackup.Stop,progress:(String)->Unit):JSONObject=HistoryRestore.exclusive {
        fun ready(){stop.check();provider.checkAccess();ticket.checkIdentities(c)}
        ready();val saved=requireNotNull(ticket.resume){"Reopen the original backup for a saved record"}
        check(saved==binding(c,saved.id)){"Saved record changed. Reopen and review again."};ticket.consume(c)
        HistoryImport.withResume(c,ticket.uri,stop,saved,progress){stage,review->
            check(review.comparison?.planSha==ticket.review.comparison?.planSha){"Phone history changed since review. Reopen and compare again."}
            check(review.resumePending==saved.pendingSources){"Pending restore changed. Review again."}
            val guarded=object:HistoryRestore.Provider by provider {
                override fun checkAccess(){ready()}
            }
            val inputs=HistoryApply.preparedInputs(stage,review,saved.archiveSha,ticket.identities.toSet(),guarded,stop,saved.pendingSources.toSet())
            val state=HistoryRestore.Journal(c).call("status")
            check(state.getInt("pending")==0&&state.getInt("remaining")>=inputs.size){"Restore journal needs review or compaction"}
            var failure:Throwable?=null
            try{
                inputs.forEachIndexed{i,input->ready();progress("Restoring remaining messages: ${i+1}/${inputs.size}");HistoryRestore.execute(c,input,guarded,stop)}
            }catch(e:Exception){failure=e;throw e}
            finally{
                // Do not let Stop erase already committed receipts. A process death
                // before this acknowledgement is also reconciled on the next review.
                try{HistoryRecovery.checkpoint(c,saved)}catch(e:Exception){if(failure!=null)failure.addSuppressed(e)else throw e}
            }
            ready();val completed=binding(c,saved.id);check(completed.pendingSources.isEmpty()){"Pending messages remain"}
            progress("Verifying recorded messages and finishing folders and flags…")
            HistoryBackup.reading(c,stop,cancelOnChange=true){stable->
                val post=File(stage,"post");check(post.mkdir());check(File(post,"media").mkdir())
                HistoryBackup.capture(c,post,stop,progress,stable);stable();ready()
                val request=JSONObject().put("op","history_import").put("action","resume_merge").put("job",stage.name).put("record",saved.id).put("map_sha",completed.mapSha)
                val result=JSONObject(NativeCore.dispatch(c.filesDir.absolutePath,request.toString()));check(result.optBoolean("ok")){result.optString("error","Saved restore could not continue")}
                stable();ready();result.getJSONObject("value")
            }
        }
    }
}
