package app.onklav.home

import android.content.Context
import app.onklav.shared.NativeCore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Trusted per-message executor, not an archive approval or SDK service. The
 * future archive coordinator must revalidate source/plan and resolve recipients.
 * Real writes remain gate-closed; private journal status is used by Settings.
 */
internal object HistoryRestore {
    private val lock=Any()
    internal fun <T> exclusive(action:()->T):T=synchronized(lock){action()}
    internal fun preflight(raw:Input,check:()->Unit):Input=validated(raw,check).first
    data class Part(val fields:Map<String,String?>,val binary:File?=null)
    data class Input(val source:String,val archiveSha:String,val kind:String,val fields:Map<String,String?>,
        val peers:Set<String>,val addresses:List<Map<String,String?>> = emptyList(),val parts:List<Part> = emptyList())
    data class Receipt(val target:String,val proof:String)
    enum class Ownership { ABSENT, OWNED, CHANGED }
    interface Provider {
        fun checkAccess()
        fun parentProof(input:Input):String
        fun insert(input:Input):String
        fun complete(input:Input,receipt:Receipt,check:()->Unit)
        fun verify(input:Input,receipt:Receipt,check:()->Unit):Boolean
        fun ownership(receipt:Receipt):Ownership
        fun delete(receipt:Receipt)
        fun removed(receipt:Receipt):Boolean
    }
    class Journal(private val c:Context) {
        fun call(action:String,fields:JSONObject=JSONObject()):JSONObject {
            val request=JSONObject(fields.toString()).put("op","history_journal").put("action",action)
            val response=JSONObject(NativeCore.dispatch(c.filesDir.absolutePath,request.toString()))
            check(response.optBoolean("ok")){response.optString("error","Restore journal unavailable")}
            return response.getJSONObject("value")
        }
        fun step(id:String,action:String,fields:JSONObject=JSONObject())=call(action,fields.put("id",id))
    }
    fun status(c:Context):JSONObject=synchronized(lock){Journal(c).call("status")}
    fun recoverState(c:Context):JSONObject=synchronized(lock){
        val journal=Journal(c);val pending=journal.call("pending").getJSONArray("items")
        repeat(pending.length()){journal.step(pending.getJSONObject(it).getString("id"),"recover")}
        journal.call("status")
    }
    private val hashPattern=Regex("[a-f0-9]{64}")
    private val keyPattern=Regex("(sms|mms):[1-9][0-9]{0,18}")
    internal fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    internal fun hashFile(file:File,check:()->Unit):String {
        val hash=MessageDigest.getInstance("SHA-256");var bytes=0L
        file.inputStream().use{input->val buffer=ByteArray(65536);while(true){check();val n=input.read(buffer);if(n<0)break;bytes+=n;require(bytes<=64L*1024*1024){"Restore part exceeds limits"};hash.update(buffer,0,n)}}
        return hash.digest().joinToString(""){"%02x".format(it)}
    }
    private fun validated(raw:Input,check:()->Unit):Pair<Input,String> {
        require(raw.kind in listOf("sms","mms")&&keyPattern.matches(raw.source)&&raw.source.startsWith(raw.kind+":"))
        require(hashPattern.matches(raw.archiveSha));require(raw.source.substringAfter(':').toLong()>0)
        val input=raw.copy(fields=raw.fields.toMap(),peers=raw.peers.toSet(),addresses=raw.addresses.map{it.toMap()},parts=raw.parts.map{it.copy(fields=it.fields.toMap())})
        val allowed=if(input.kind=="sms")HistoryRestoreProvider.SMS else HistoryRestoreProvider.MMS
        require(input.fields.keys.all{it in allowed}){"Unsupported restore parent field"}
        require(input.fields.values.sumOf{it?.toByteArray(Charsets.UTF_8)?.size ?: 0}<=1_048_576){"Restore message text exceeds limits"}
        val date=input.fields["date"]?.toLongOrNull();require(date!=null&&date>0&&date.toString()==input.fields["date"]&&(input.kind=="sms"||date<=Long.MAX_VALUE/1000)){"Restore message needs a valid date"}
        val box=input.fields[if(input.kind=="sms")"type" else "msg_box"]
        require(box=="1"||box=="2"){"Only Inbox and Sent history can be restored"}
        require(input.peers.isNotEmpty()&&input.peers.size<=256&&input.peers.all{it.isNotEmpty()&&it.length<=256&&!it.any(Char::isISOControl)}){"Resolve message recipients before restoring"}
        if(input.kind=="sms") {
            require(input.fields["body"]!=null&&!input.fields["address"].isNullOrEmpty()&&input.addresses.isEmpty()&&input.parts.isEmpty()){"Invalid SMS restore input"}
        }else{
            require(input.fields["m_type"] in listOf("128","132"))
            require(input.addresses.size in 1..256&&input.parts.size in 1..256){"MMS restore exceeds child-row limits"}
            input.addresses.forEach{row->require(row.keys.all{it in HistoryRestoreProvider.ADDRESS}&&!row["address"].isNullOrEmpty()&&row["type"] in listOf("129","130","137","151"))}
        }
        var media=0L;var textBytes=input.fields.values.sumOf{it?.toByteArray(Charsets.UTF_8)?.size ?: 0};val parts=JSONArray()
        input.addresses.forEach{a->a.values.forEach{v->textBytes+=v?.toByteArray(Charsets.UTF_8)?.size ?: 0};require(textBytes<=1_048_576)}
        input.parts.forEach {part->
            require(part.fields.keys.all{it in HistoryRestoreProvider.PART})
            part.fields.values.forEach{v->textBytes+=v?.toByteArray(Charsets.UTF_8)?.size ?: 0};require(textBytes<=1_048_576)
            val mime=part.fields["ct"];require(!mime.isNullOrEmpty()&&mime.length<=256&&!mime.any(Char::isISOControl))
            require(part.fields["seq"]?.toIntOrNull()?.let{it>=-1}==true)
            require(part.binary!=null||part.fields["text"]!=null)
            val hash=part.binary?.let{require(it.isFile&&!java.nio.file.Files.isSymbolicLink(it.toPath()));media+=it.length();require(media<=128L*1024*1024);hashFile(it,check)}
            parts.put(JSONArray().put(JSONObject(part.fields.toSortedMap())).put(hash ?: JSONObject.NULL))
        }
        val proof=JSONArray().put(input.kind).put(JSONObject(input.fields.toSortedMap())).put(JSONArray(input.peers.sorted()))
            .put(JSONArray(input.addresses.map{JSONObject(it.toSortedMap())})).put(parts)
        return input to hash(proof.toString().toByteArray(Charsets.UTF_8))
    }
    /** Production entry has an immutable MMS/role/permission guard in its provider. */
    fun insertChecked(c:Context,input:Input,stop:HistoryBackup.Stop)=execute(c,input,HistoryRestoreProvider(c,stop),stop)
    internal fun execute(c:Context,raw:Input,provider:Provider,stop:HistoryBackup.Stop):JSONObject=synchronized(lock) {
        fun ready(){stop.check();provider.checkAccess()}
        ready();val(input,content)=validated(raw,::ready);val journal=Journal(c)
        val begun=journal.call("begin",JSONObject().put("source",input.source).put("kind",input.kind).put("archive_sha",input.archiveSha).put("content_sha",content))
        val id=begun.getString("id");var handedOff=false;var recorded=false;var committed=false
        try {
            val proof=provider.parentProof(input);require(hashPattern.matches(proof))
            journal.step(id,"insert_intent");ready();handedOff=true
            val target=provider.insert(input)
            require(keyPattern.matches(target)&&target.startsWith(input.kind+":")){"Provider returned an invalid message ID"}
            val receipt=Receipt(target,proof)
            journal.step(id,"row",JSONObject().put("target",target).put("receipt",proof));recorded=true
            ready();journal.step(id,"complete_intent");provider.complete(input,receipt,::ready)
            ready();check(provider.verify(input,receipt,::ready)){"Restored row verification failed"};ready()
            val result=journal.step(id,"commit");committed=true;result
        }catch(e:Exception){
            // Never undo a commit if its acknowledgement failed after the fsync.
            val current=runCatching{journal.step(id,"get")}.getOrNull()
            if(current?.optString("phase")=="committed")throw IllegalStateException("Message committed; review the saved receipt before continuing",e)
            if(!committed) {
                if(!handedOff)runCatching{journal.step(id,"no_effect")}
                else if(!recorded)runCatching{journal.step(id,"uncertain")}
                else runCatching{cleanup(c,id,provider)}
            }
            throw e
        }
    }
    /** Explicitly resolve a recorded, changed insertion only after its parent
     * and children have already been removed elsewhere. This never deletes or
     * retries anything; unknown-ID and committed outcomes cannot use this path.
     */
    internal fun confirmRemoved(c:Context,id:String,provider:Provider,stop:HistoryBackup.Stop):JSONObject=exclusive {
        val journal=Journal(c);val row=journal.step(id,"get")
        require(row.getString("phase")=="needs_review"&&!row.isNull("target")&&!row.isNull("receipt")){"Only a recorded insertion awaiting review can be checked"}
        val receipt=Receipt(row.getString("target"),row.getString("receipt"))
        fun ready(){stop.check();provider.checkAccess()}
        // Repeat before acknowledging: absence of just the parent is not enough
        // for MMS, and role/access/cancellation loss leaves the journal untouched.
        repeat(2){
            ready();check(provider.ownership(receipt)==Ownership.ABSENT){"The recorded message is still present. Its review record is retained."}
            ready();check(provider.removed(receipt)){"Recorded message parts remain. Its review record is retained."}
        }
        ready()
        journal.step(id,"reviewed_absent",JSONObject().put("target",receipt.target).put("receipt",receipt.proof))
    }
    /** Explicit cleanup/recovery, never an automatic retry. Guard applies to every
     * provider boundary. Committed, unknown-ID and changed rows cannot be deleted.
     */
    internal fun cleanup(c:Context,id:String,provider:Provider):JSONObject=synchronized(lock) {
        val journal=Journal(c);var row=journal.step(id,"get")
        if(row.getString("phase") in listOf("prepared","inserting","row_known","completing","cleaning"))row=journal.step(id,"recover")
        if(row.getString("phase")!="cleanup_required")return@synchronized row
        provider.checkAccess();val receipt=Receipt(row.getString("target"),row.getString("receipt"))
        when(provider.ownership(receipt)) {
            Ownership.CHANGED->journal.step(id,"review")
            Ownership.ABSENT->{journal.step(id,"cleanup_intent");provider.checkAccess();check(provider.removed(receipt)){"Orphaned restore children need review"};journal.step(id,"cleaned")}
            Ownership.OWNED->{
                journal.step(id,"cleanup_intent");provider.checkAccess()
                // Check again immediately before delete; never remove a mismatched row.
                if(provider.ownership(receipt)!=Ownership.OWNED)return@synchronized journal.step(id,"review")
                provider.checkAccess();provider.delete(receipt);provider.checkAccess();check(provider.removed(receipt)){"Restore cleanup is incomplete"};journal.step(id,"cleaned")
            }
        }
    }
}
