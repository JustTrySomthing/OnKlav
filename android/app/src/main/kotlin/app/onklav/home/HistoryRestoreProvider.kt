package app.onklav.home

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import org.json.JSONArray
import java.io.File
import java.nio.file.Files

/** Android adapter is never exposed to guests or archive-supplied URIs. All write
 * methods are blocked by the immutable MMS readiness gate plus live role/access.
 */
internal class HistoryRestoreProvider(private val c:Context,private val stop:HistoryBackup.Stop):HistoryRestore.Provider {
    companion object {
        val SMS=listOf("address","body","date","date_sent","type","subject","read","seen","status","locked","service_center","protocol","reply_path_present","error_code")
        val MMS=listOf("date","date_sent","msg_box","m_type","sub","sub_cs","m_id","v","ct_t","m_cls","pri","read","seen","locked","text_only")
        val ADDRESS=listOf("address","type","charset")
        val PART=listOf("seq","ct","name","chset","cd","fn","cid","cl","text")
        private val SMS_PROOF=listOf("address","body","date","type","subject","creator")
        private val MMS_PROOF=listOf("date","msg_box","m_type","m_id","sub","sub_cs","creator")
    }
    override fun checkAccess() {
        check(MessagingTransport.carrierEnabled(c)){"Enable carrier phone testing in Setup before restoring message history"}
        check(MessagingTransport.role(c)){"OnKlav must already be the default messenger"}
        check(ContextCompat.checkSelfPermission(c,Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED){"SMS access was removed"}
    }
    private fun uri(target:String):Uri {
        val parts=target.split(':');require(parts.size==2&&parts[0] in listOf("sms","mms")&&parts[1].toLongOrNull()?.let{it>0&&it.toString()==parts[1]}==true)
        return Uri.parse("content://${parts[0]}/${parts[1]}")
    }
    private fun values(fields:Map<String,String?>,columns:List<String>)=ContentValues().also {v->columns.forEach{key->fields[key]?.let{v.put(key,it)} ?: v.putNull(key)}}
    private fun proof(kind:String,fields:Map<String,String?>):String {
        val keys=if(kind=="sms")SMS_PROOF else MMS_PROOF
        return HistoryRestore.hash(JSONArray().put(kind).apply{keys.forEach{put(fields[it] ?: org.json.JSONObject.NULL)}}.toString().toByteArray(Charsets.UTF_8))
    }
    override fun parentProof(input:HistoryRestore.Input)=proof(input.kind,input.fields+mapOf("creator" to c.packageName))
    override fun insert(input:HistoryRestore.Input):String {
        stop.check();checkAccess()
        // New thread resolution is host-owned; archive thread/subscription IDs are never reused.
        val thread=Telephony.Threads.getOrCreateThreadId(c,input.peers)
        checkAccess()
        stop.check()
        val fields=values(input.fields,if(input.kind=="sms")SMS else MMS)
        fields.put("thread_id",thread);fields.put("sub_id",-1);fields.put("creator",c.packageName)
        val collection=if(input.kind=="sms")Telephony.Sms.CONTENT_URI else Telephony.Mms.CONTENT_URI
        val inserted=c.contentResolver.insert(collection,fields) ?: error("Provider did not return an inserted row")
        require(inserted.scheme=="content"&&inserted.authority==input.kind&&inserted.pathSegments.size==1)
        val id=inserted.lastPathSegment ?: error("Missing inserted message ID");require(id.toLongOrNull()?.let{it>0&&it.toString()==id}==true)
        return "${input.kind}:$id"
    }
    private fun binary(file:File):File {
        val base=File(c.filesDir,"history-import");val path=file.canonicalFile
        require(path.path.startsWith(base.canonicalPath+File.separator)&&file.parentFile?.name=="media"&&file.name.matches(Regex("[0-9]{1,19}\\.bin"))){"Restore media must be in checked private staging"}
        var current:File?=file
        while(current!=null&&current!=c.filesDir){require(!Files.isSymbolicLink(current.toPath()));current=current.parentFile}
        require(file.isFile&&file.length()<=64L*1024*1024);return file
    }
    override fun complete(input:HistoryRestore.Input,receipt:HistoryRestore.Receipt,check:()->Unit) {
        if(input.kind=="sms")return
        val parent=uri(receipt.target)
        input.addresses.forEach {fields->check();checkAccess();checkNotNull(c.contentResolver.insert(Uri.withAppendedPath(parent,"addr"),values(fields,ADDRESS)))}
        input.parts.forEach {part->
            check();checkAccess();val inserted=checkNotNull(c.contentResolver.insert(Uri.withAppendedPath(parent,"part"),values(part.fields,PART)))
            val segments=inserted.pathSegments
            require(inserted.scheme=="content"&&inserted.authority=="mms"&&((segments.size==2&&segments[0]=="part")||(segments.size==3&&segments[0]==parent.lastPathSegment&&segments[1]=="part")))
            val id=segments.last();require(id.toLongOrNull()?.let{it>0&&it.toString()==id}==true)
            part.binary?.let {source->
                val sourceFile=binary(source);check();checkAccess()
                c.contentResolver.openAssetFileDescriptor(Uri.parse("content://mms/part/$id"),"w",stop.signal)?.createOutputStream()?.use{output->
                    stop.stream(output)
                    try{sourceFile.inputStream().use{stream->val buffer=ByteArray(65536);var size=0L;while(true){check();checkAccess();val n=stream.read(buffer);if(n<0)break;size+=n;require(size<=64L*1024*1024);output.write(buffer,0,n)}}}
                    finally{stop.stream(null)}
                } ?: error("Could not write restored media")
            }
        }
    }
    private fun row(cursor:Cursor,columns:List<String>)=columns.associateWith {key->val index=cursor.getColumnIndexOrThrow(key);if(cursor.isNull(index))null else cursor.getString(index)}
    override fun ownership(receipt:HistoryRestore.Receipt):HistoryRestore.Ownership {
        checkAccess();val target=uri(receipt.target);val kind=receipt.target.substringBefore(':');val columns=if(kind=="sms")SMS_PROOF else MMS_PROOF
        val cursor=c.contentResolver.query(target,columns.toTypedArray(),null,null,null) ?: error("Could not check restored row ownership")
        return cursor.use {if(!it.moveToFirst())HistoryRestore.Ownership.ABSENT else {
            val fields=row(it,columns);if(it.moveToNext())HistoryRestore.Ownership.CHANGED
            else if(proof(kind,fields)==receipt.proof)HistoryRestore.Ownership.OWNED else HistoryRestore.Ownership.CHANGED
        }}
    }
    private fun tuples(uri:Uri,columns:List<String>,check:()->Unit):List<String> {
        check();checkAccess();val cursor=c.contentResolver.query(uri,columns.toTypedArray(),null,null,null) ?: error("Could not verify restored children")
        return cursor.use{val list=mutableListOf<String>();while(it.moveToNext()){check();checkAccess();require(list.size<256);list+=JSONArray().apply{val values=row(it,columns);columns.forEach{put(values[it] ?: org.json.JSONObject.NULL)}}.toString()};list.sorted()}
    }
    override fun verify(input:HistoryRestore.Input,receipt:HistoryRestore.Receipt,check:()->Unit):Boolean {
        check();if(ownership(receipt)!=HistoryRestore.Ownership.OWNED)return false
        if(input.kind=="sms")return true
        val parent=uri(receipt.target)
        val expected=input.addresses.map{f->JSONArray().apply{ADDRESS.forEach{put(f[it] ?: org.json.JSONObject.NULL)}}.toString()}.sorted()
        if(tuples(Uri.withAppendedPath(parent,"addr"),ADDRESS,check)!=expected)return false
        val expectedParts=input.parts.map {part->JSONArray().apply{PART.forEach{put(part.fields[it] ?: org.json.JSONObject.NULL)};put(part.binary?.let{HistoryRestore.hashFile(binary(it),check)} ?: org.json.JSONObject.NULL)}.toString()}.sorted()
        check();checkAccess();val cursor=c.contentResolver.query(Uri.withAppendedPath(parent,"part"),(listOf("_id")+PART).toTypedArray(),null,null,null) ?: error("Could not verify restored parts")
        val actual=cursor.use {
            val list=mutableListOf<String>()
            while(it.moveToNext()) {
                check();checkAccess();require(list.size<256);val fields=row(it,PART);val id=it.getString(it.getColumnIndexOrThrow("_id"));require(id.toLongOrNull()?.let{it>0&&it.toString()==id}==true)
                // The part's binary/text disposition is derived from the validated source
                // tuple, not from an arbitrary URI in the archive.
                val candidates=input.parts.filter{p->PART.all{key->p.fields[key]==fields[key]}}
                if(candidates.isEmpty())return false
                val needsBinary=candidates.any{p->p.binary!=null}
                val hash=if(needsBinary){
                    val digest=java.security.MessageDigest.getInstance("SHA-256");var size=0L
                    c.contentResolver.openAssetFileDescriptor(Uri.parse("content://mms/part/$id"),"r",stop.signal)?.createInputStream()?.use{stream->
                        stop.stream(stream)
                        try{val buffer=ByteArray(65536);while(true){check();checkAccess();val n=stream.read(buffer);if(n<0)break;size+=n;require(size<=64L*1024*1024);digest.update(buffer,0,n)}}finally{stop.stream(null)}
                    } ?: error("Could not read back restored media")
                    digest.digest().joinToString(""){b->"%02x".format(b)}
                }else null
                list+=JSONArray().apply{PART.forEach{put(fields[it] ?: org.json.JSONObject.NULL)};put(hash ?: org.json.JSONObject.NULL)}.toString()
            };list.sorted()
        }
        return actual==expectedParts
    }
    override fun delete(receipt:HistoryRestore.Receipt){checkAccess();c.contentResolver.delete(uri(receipt.target),null,null)}
    override fun removed(receipt:HistoryRestore.Receipt):Boolean {
        checkAccess();val target=uri(receipt.target)
        fun empty(uri:Uri):Boolean {checkAccess();return (c.contentResolver.query(uri,arrayOf("_id"),null,null,null) ?: error("Could not verify restore cleanup")).use{!it.moveToFirst()}}
        return empty(target)&&(receipt.target.startsWith("sms:")||(empty(Uri.withAppendedPath(target,"addr"))&&empty(Uri.withAppendedPath(target,"part"))))
    }
}
