package app.onklav.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import app.onklav.shared.NativeCore
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Read-only, host-owned SMS/MMS snapshot. No APK, SDK guest or provider write path. */
internal object HistoryBackup {
    private const val MAX_MESSAGES=200_000
    private const val MAX_ROWS=500_000
    private const val MAX_TEXT=128L*1024*1024
    private const val MAX_PART=64L*1024*1024
    private const val MAX_MEDIA=2L*1024*1024*1024
    private val running=AtomicBoolean(false)
    class Stop {
        val signal=CancellationSignal()
        @Volatile private var stage:File?=null
        private var active:Closeable?=null
        @Synchronized fun attach(file:File?){stage=file;if(signal.isCanceled)file?.resolve("cancel")?.createNewFile()}
        @Synchronized fun stream(value:Closeable?) {if(signal.isCanceled){value?.close();throw OperationCanceledException()};active=value}
        @Synchronized fun cancel(){signal.cancel();runCatching{active?.close()};runCatching{stage?.resolve("cancel")?.createNewFile()}}
        fun check(){signal.throwIfCanceled()}
    }
    data class Result(val sms:Int,val mms:Int,val parts:Int,val bytes:Long)
    fun allowed(c:Context)=ContextCompat.checkSelfPermission(c,Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED
    private val smsColumns=listOf("_id","thread_id","address","person","date","date_sent","protocol","read","status","type","reply_path_present","subject","body","service_center","locked","sub_id","error_code","creator","seen")
    private val mmsColumns=listOf("_id","thread_id","date","date_sent","msg_box","read","m_id","sub","sub_cs","ct_t","ct_l","exp","m_cls","m_type","v","m_size","pri","rr","rpt_a","resp_st","st","tr_id","retr_st","retr_txt","retr_txt_cs","read_status","ct_cls","resp_txt","d_tm","d_rpt","locked","sub_id","creator","seen","text_only")
    private val partColumns=listOf("_id","seq","ct","name","chset","cd","fn","cid","cl","text")
    private fun fields(row:Cursor,columns:List<String>):JSONObject=JSONObject().also { result->
        columns.forEach {name->val index=row.getColumnIndex(name);if(index>=0)result.put(name,if(row.isNull(index))JSONObject.NULL else row.getString(index))}
    }
    private fun id(row:Cursor):String=row.getString(row.getColumnIndexOrThrow("_id")).also{
        require(it.isNotEmpty()&&it.length<=20&&it.all{ch->ch in '0'..'9'}){"Invalid provider identity"}
    }
    internal fun removeStage(file:File) {
        if(!file.exists())return
        // Files.walkFileTree does not follow symbolic links, including nested links.
        Files.walkFileTree(file.toPath(),object:SimpleFileVisitor<Path>() {
            override fun visitFile(path:Path,attrs:BasicFileAttributes):FileVisitResult {Files.delete(path);return FileVisitResult.CONTINUE}
            override fun postVisitDirectory(path:Path,error:java.io.IOException?):FileVisitResult {if(error!=null)throw error;Files.delete(path);return FileVisitResult.CONTINUE}
        })
    }
    /** Deletes only our fixed private staging jobs; crash remnants are removed before a new export. */
    private fun base(c:Context):File=File(c.filesDir,"history-export").also { base->
        check(!Files.isSymbolicLink(base.toPath())){"Invalid backup staging directory"}
        check(base.isDirectory||base.mkdir()){"Cannot create backup staging directory"}
        base.listFiles()?.forEach { f->if(f.name.matches(Regex("[a-f0-9]{32}"))&&!Files.isSymbolicLink(f.toPath()))removeStage(f)}
    }
    internal fun <T> reading(c:Context,stop:Stop,cancelOnChange:Boolean=false,action:(()->Unit)->T):T {
        check(running.compareAndSet(false,true)){"Another history read is running"}
        val changed=AtomicBoolean(false)
        val observer=object:ContentObserver(null){override fun onChange(selfChange:Boolean){changed.set(true);if(cancelOnChange)runCatching{stop.cancel()}}}
        var observing=false
        try {
            require(allowed(c)){"Grant SMS access in Settings › Setup before reading phone history"};stop.check()
            observing=true
            c.contentResolver.registerContentObserver(Uri.parse("content://mms-sms"),true,observer)
            c.contentResolver.registerContentObserver(Uri.parse("content://sms"),true,observer)
            c.contentResolver.registerContentObserver(Uri.parse("content://mms"),true,observer)
            fun stable(){stop.check();check(!changed.get()){"Messages changed during backup. Please try again when messaging is idle."};require(allowed(c)){"SMS access was removed"}}
            return action(::stable)
        }finally{if(observing)runCatching{c.contentResolver.unregisterContentObserver(observer)};running.set(false)}
    }
    internal fun capture(c:Context,stage:File,stop:Stop,progress:(String)->Unit,stable:()->Unit) {
            var count=0;var parts=0;var rows=0;var textBytes=0L;var mediaBytes=0L
            fun query(uri:String,selection:String?=null,body:(Cursor)->Unit) {
                stable();c.contentResolver.query(Uri.parse(uri),null,selection,null,null,stop.signal)?.use{body(it)} ?: error("Message provider could not be read")
            }
            File(stage,"records.jsonl").bufferedWriter(Charsets.UTF_8).use { out->
                fun record(kind:String,id:String,fields:JSONObject,parent:String?=null,file:String?=null) {
                    stable();rows++;check(rows<=MAX_ROWS){"Backup exceeds 500,000 records"}
                    val line=JSONObject().put("kind",kind).put("id",id).put("fields",fields).apply{parent?.let{put("parent",it)};file?.let{put("file",it)}}.toString()+"\n"
                    val size=line.toByteArray(Charsets.UTF_8).size;check(size<=1_048_576){"A message exceeds the 1 MiB record limit"}
                    textBytes+=size;check(textBytes<=MAX_TEXT){"Message text exceeds the 128 MiB backup limit"};out.write(line)
                }
                query("content://sms","type<>3"){ cursor->
                    cursor.getColumnIndexOrThrow("type");cursor.getColumnIndexOrThrow("body");cursor.getColumnIndexOrThrow("date")
                    while(cursor.moveToNext()) {stable();check(++count<=MAX_MESSAGES){"Backup exceeds 200,000 messages"};record("sms",id(cursor),fields(cursor,smsColumns));if(count%100==0)progress("Reading messages: $count")}
                }
                query("content://mms","msg_box<>3"){ cursor->
                    cursor.getColumnIndexOrThrow("msg_box");cursor.getColumnIndexOrThrow("date")
                    while(cursor.moveToNext()) {
                        stable();check(++count<=MAX_MESSAGES){"Backup exceeds 200,000 messages"};val mid=id(cursor);record("mms",mid,fields(cursor,mmsColumns))
                        query("content://mms/$mid/addr"){ addr->
                            addr.getColumnIndexOrThrow("address");addr.getColumnIndexOrThrow("type");var index=0
                            while(addr.moveToNext()){record("address",(index++).toString(),fields(addr,listOf("address","type","charset")),mid)}
                        }
                        query("content://mms/$mid/part"){ part->
                            part.getColumnIndexOrThrow("ct")
                            while(part.moveToNext()) {
                                stable();check(++parts<=100_000){"Backup exceeds 100,000 MMS parts"};val pid=id(part);val values=fields(part,partColumns)
                                val data=part.getColumnIndex("_data")
                                val hasBinary=(data>=0&&!part.isNull(data)) || values.isNull("text")
                                var name:String?=null
                                if(hasBinary) {
                                    name="media/$pid.bin";val dest=File(stage,name);check(dest.createNewFile()){"Duplicate media identity"}
                                    val input=c.contentResolver.openAssetFileDescriptor(Uri.parse("content://mms/part/$pid"),"r",stop.signal)?.createInputStream() ?: error("An attachment could not be opened")
                                    input.use{stream->
                                        stop.stream(stream)
                                        try {dest.outputStream().use{output->
                                            val buffer=ByteArray(65536);var bytes=0L
                                            while(true){stable();val n=stream.read(buffer);if(n<0)break;bytes+=n;mediaBytes+=n
                                                check(bytes<=MAX_PART&&mediaBytes<=MAX_MEDIA){"Media exceeds the 64 MiB per-part or 2 GiB total backup limit"};output.write(buffer,0,n)}
                                        }}finally{stop.stream(null)}
                                    }
                                }
                                record("part",pid,values,mid,name)
                            }
                        }
                        progress("Reading messages: $count")
                    }
                }
            }
            stable();File(stage,"metadata.json").writeText(SettingsBackup.export(c).toString())
    }
    fun export(c:Context,target:Uri,stop:Stop,progress:(String)->Unit):Result {
        var stage:File?=null;var complete=false
        try { return reading(c,stop){stable->
            val job=UUID.randomUUID().toString().replace("-","")
            val created=File(base(c),job);check(created.mkdir());stage=created;check(File(created,"media").mkdir());stop.attach(created)
            capture(c,created,stop,progress,stable)
            progress("Building and verifying archive…")
            val response=JSONObject(NativeCore.dispatch(c.filesDir.absolutePath,JSONObject().put("op","history_backup").put("action","pack").put("job",job).toString()))
            check(response.optBoolean("ok")){response.optString("error","Archive verification failed")}
            val summary=response.getJSONObject("value");check(summary.getBoolean("verified"));stable()
            progress("Saving verified archive…")
            c.contentResolver.openAssetFileDescriptor(target,"wt",stop.signal)?.createOutputStream()?.use{output->
                stop.stream(output)
                try{File(stage,"history.zip").inputStream().use{input->val buffer=ByteArray(65536);while(true){stable();val n=input.read(buffer);if(n<0)break;output.write(buffer,0,n)}};output.flush()}
                finally{stop.stream(null)}
            } ?: error("Could not write the selected document")
            stable();complete=true
            Result(summary.getInt("sms"),summary.getInt("mms"),summary.getInt("parts"),summary.getLong("bytes"))
        }} finally {
            stop.attach(null)
            try{stage?.let{removeStage(it)}}finally{if(!complete)runCatching{DocumentsContract.deleteDocument(c.contentResolver,target)}}
        }
    }
}
