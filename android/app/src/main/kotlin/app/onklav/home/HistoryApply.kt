package app.onklav.home

import android.content.Context
import android.net.Uri
import app.onklav.shared.NativeCore
import org.json.JSONObject
import java.io.File

/** Host-only orchestration. Review counts are never apply authorization. A fresh
 * copy/validation/provider snapshot is required and every payload is preflighted
 * before the first insert. No guest API, role request or enabled Restore UI.
 */
internal object HistoryApply {
    private fun native(c:Context,stage:File,action:String):JSONObject {
        val response=JSONObject(NativeCore.dispatch(c.filesDir.absolutePath,JSONObject().put("op","history_import").put("action",action).put("job",stage.name).toString()))
        check(response.optBoolean("ok")){response.optString("error","Restore preparation failed")}
        return response.getJSONObject("value")
    }
    fun applyChecked(c:Context,confirmation:HistoryConfirmation,stop:HistoryBackup.Stop,progress:(String)->Unit):JSONObject {
        check(confirmation.resume==null){"Use the saved-record continuation path"}
        val provider=HistoryRestoreProvider(c,stop);provider.checkAccess()
        return runReviewed(c,confirmation,provider,stop,progress)
    }
    internal fun runReviewed(c:Context,confirmation:HistoryConfirmation,provider:HistoryRestore.Provider,stop:HistoryBackup.Stop,progress:(String)->Unit):JSONObject=HistoryRestore.exclusive {
        check(confirmation.resume==null){"A saved record cannot start another message batch"}
        stop.check();provider.checkAccess();confirmation.consume(c)
        val guarded=object:HistoryRestore.Provider by provider {
            override fun checkAccess(){stop.check();provider.checkAccess();confirmation.checkIdentities(c)}
        }
        run(c,confirmation.uri,confirmation.review.sha256,confirmation.identities.toSet(),guarded,stop,
            confirmation.review.comparison!!.planSha,progress)
    }
    private fun fields(row:JSONObject):Map<String,String?> =row.keys().asSequence().associateWith{key->if(row.isNull(key))null else row.getString(key)}
    private fun address(s:String)=if(s.all{it.isDigit()||it in "+ ()-."})s.filter{it !in " ()-."}else s
    private fun input(row:JSONObject,stage:File,archive:String,identities:Set<String>):HistoryRestore.Input {
        check(row.getString("archive_sha")==archive)
        val kind=row.getString("kind");val parent=fields(row.getJSONObject("fields"))
        val a=row.getJSONArray("addresses");val addresses=(0 until a.length()).map{fields(a.getJSONObject(it))}
        val p=row.getJSONArray("parts");val parts=(0 until p.length()).map{i->val part=p.getJSONObject(i)
            val file=if(part.isNull("file"))null else {
                val name=part.getString("file");require(name.matches(Regex("media/[0-9]{1,19}\\.bin")))
                File(File(stage,"checked"),name).also{check(it.isFile&&!java.nio.file.Files.isSymbolicLink(it.toPath()))}
            }
            HistoryRestore.Part(fields(part.getJSONObject("fields")),file)
        }
        val peers=if(kind=="sms")setOf(parent["address"] ?: error("SMS sender missing"))else {
            // Local identities come from host configuration, never from the archive.
            // Include the old device's identities in that reviewed configuration.
            require(identities.isNotEmpty()){"Configure local MMS identities before restoring"}
            val local=identities.map(::address).toSet();require(local.all{it.isNotEmpty()&&it!="insert-address-token"})
            val sent=parent["msg_box"]=="2"
            val sender=addresses.filter{it["type"]=="137"}.mapNotNull{it["address"]}
            if(!sent)require(sender.size==1&&sender.single()!="insert-address-token"&&address(sender.single()) !in local){"MMS sender needs review"}
            addresses.filter{it["type"] in (if(sent)listOf("129","130","151")else listOf("129","130","137","151"))}
                .map{address(it["address"] ?: error("Missing MMS address"))}.filter{it !in local}.toSet().also{peers->
                    require(peers.isNotEmpty()&&peers.none{it=="insert-address-token"}){"MMS recipients need review"}
                }
        }
        return HistoryRestore.Input(row.getString("source"),archive,kind,parent,peers,addresses,parts)
    }
    internal fun run(c:Context,uri:Uri,archiveSha:String,localIdentities:Set<String>,provider:HistoryRestore.Provider,stop:HistoryBackup.Stop,expectedPlanSha:String?=null,progress:(String)->Unit):JSONObject = HistoryRestore.exclusive {
        stop.check();provider.checkAccess()
        val identities=localIdentities.toSet()
        val retained=File(c.filesDir,"history-restore-maps")
        check(!java.nio.file.Files.isSymbolicLink(retained.toPath())&&(!retained.exists()||retained.isDirectory)){"Invalid restore map storage"}
        check(retained.listFiles().orEmpty().count{it.extension=="jsonl"}<100){"Restore outcome maps need durable compaction"}
        val journal=HistoryRestore.Journal(c);check(journal.call("status").getInt("pending")==0){"Review the interrupted restore first"}
        HistoryImport.withPrepared(c,uri,stop,archiveSha,progress){stage,review->
            if(expectedPlanSha!=null)check(expectedPlanSha.matches(Regex("[a-f0-9]{64}"))&&review.comparison?.planSha==expectedPlanSha){"Phone history changed since review; compare again"}
            val inputs=preparedInputs(stage,review,archiveSha,identities,provider,stop)
            val status=journal.call("status");check(status.getInt("pending")==0&&status.getInt("remaining")>=inputs.size){"Restore journal needs review or durable compaction"}
            val committed=journal.call("receipts",JSONObject().put("archive_sha",archiveSha)).getJSONArray("items")
            val sources=(0 until committed.length()).map{committed.getJSONObject(it).getString("source")}.toSet()
            check(inputs.none{it.source in sources}){"A previously committed message is absent from the phone; review its receipt"}
            var failure:Throwable?=null
            try {
                inputs.forEachIndexed{i,value->stop.check();provider.checkAccess();progress("Restoring messages: ${i+1}/${inputs.size}");HistoryRestore.execute(c,value,provider,stop)}
            }catch(e:Exception){failure=e;throw e}
            finally {
                // Save partial outcomes too. Never erase committed journal receipts.
                try{native(c,stage,"finish")}catch(e:Exception){if(failure!=null)failure.addSuppressed(e)else throw e}
            }
            stop.check();provider.checkAccess();progress("Verifying restored messages and restoring folders and flags…")
            val merged=HistoryBackup.reading(c,stop,cancelOnChange=true){stable->
                val post=File(stage,"post");check(post.mkdir());check(File(post,"media").mkdir())
                HistoryBackup.capture(c,post,stop,progress,stable);stable();provider.checkAccess()
                val result=native(c,stage,"merge");stable();provider.checkAccess();result
            }
            nativeSummary(c,stage).put("metadata_applied",true).put("phone_revalidated",true)
                .put("flags_imported",merged.getInt("flags_imported")).put("threads_imported",merged.getInt("threads_imported"))
        }
    }
    internal fun preparedInputs(stage:File,review:HistoryImport.Review,archiveSha:String,identities:Set<String>,provider:HistoryRestore.Provider,stop:HistoryBackup.Stop,selected:Set<String>?=null):List<HistoryRestore.Input> {
            val file=File(stage,"prepared.jsonl");check(file.isFile){"Restore batch exceeds the journal limit"}
            require(file.length()<=128L*1024*1024)
            val inputs=mutableListOf<HistoryRestore.Input>()
            // A bounded reader avoids an unbounded readLine allocation.
            file.bufferedReader().use{reader->val line=StringBuilder();val buffer=CharArray(8192)
                while(true){stop.check();val n=reader.read(buffer);if(n<0)break
                    repeat(n){i->val ch=buffer[i];if(ch=='\n'){
                        require(inputs.size<1000);val value=input(JSONObject(line.toString()),stage,archiveSha,identities)
                        inputs+=value;line.setLength(0)
                    }else{require(line.length<2_097_152);line.append(ch)}}
                };require(line.isEmpty())
            }
            check(inputs.size==review.comparison?.fresh){"Incomplete prepared restore"}
        require(inputs.map{it.source}.toSet().size==inputs.size){"Repeated prepared source"}
        val chosen=if(selected==null)inputs else inputs.filter{it.source in selected}
        if(selected!=null)check(chosen.map{it.source}.toSet()==selected){"Incomplete pending restore payload"}
        return chosen.map{HistoryRestore.preflight(it){stop.check();provider.checkAccess()}}
    }
    private fun nativeSummary(c:Context,stage:File):JSONObject {
        // finish already persisted a unique, fsynced outcome file; expose counts only.
        val file=File(File(c.filesDir,"history-restore-maps"),stage.name+".jsonl")
        var inserted=0;var pending=0
        file.bufferedReader().useLines{lines->lines.drop(1).forEach{line->val row=JSONObject(line);when(row.getString("decision")){"inserted"->inserted++;"pending"->pending++}}}
        return JSONObject().put("map",stage.name).put("inserted",inserted).put("pending",pending).put("metadata_applied",false)
    }
}
