package app.onklav.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import java.io.File
import kotlinx.coroutines.*

internal object SettingsStorage {
    private const val AGE=2*24*60*60*1000L
    // Export copies only. Never touch MMS/drafts, WebView data or live voice files.
    fun eligible(c:Context,now:Long=System.currentTimeMillis()):List<File> {
        val cache=c.cacheDir.canonicalFile
        val dir=File(cache,"exports")
        if(java.nio.file.Files.isSymbolicLink(dir.toPath()) || dir.canonicalFile.parentFile!=cache)return emptyList()
        val canonical=dir.canonicalFile
        return dir.listFiles().orEmpty().filter {f->
            f.isFile && f.canonicalFile.parentFile==canonical && !java.nio.file.Files.isSymbolicLink(f.toPath()) &&
                f.lastModified()>0 && now-f.lastModified()>=AGE
        }
    }
    fun clear(c:Context):Long {
        var freed=0L
        eligible(c).forEach {f->
            val dir=File(c.cacheDir.canonicalFile,"exports")
            check(!java.nio.file.Files.isSymbolicLink(dir.toPath()) && f.canonicalFile.parentFile==dir.absoluteFile && !java.nio.file.Files.isSymbolicLink(f.toPath())) {"Export storage changed; cleanup stopped"}
            val size=f.length();check(f.delete()){ "Could not remove an old temporary copy" };freed+=size
        }
        return freed
    }
    data class Attachment(val id:Long,val message:Long,val name:String,val mime:String,val bytes:Long)
    data class Report(val items:List<Attachment>,val total:Long,val count:Int,val unknown:Int,val truncated:Boolean)
    suspend fun attachments(c:Context):Report=withContext(Dispatchers.IO) {
        check(MessageRepository(c).allowed()){ "Allow message access in Setup to review attachments." }
        val largest=mutableListOf<Attachment>();var total=0L;var count=0;var unknown=0;var truncated=false
        val uri=Uri.parse("content://mms/part")
        val cursor=c.contentResolver.query(uri,arrayOf("_id","mid","ct","name","cl"),"ct IS NOT NULL AND ct NOT LIKE 'text/%' AND ct != 'application/smil'",null,null)
            ?: error("Attachment storage is unavailable on this phone.")
        cursor.use {q->while(q.moveToNext()) {
            currentCoroutineContext().ensureActive()
            if(count>=10000){truncated=true;break};count++
            val id=q.getLong(0)
            val bytes=runCatching{c.contentResolver.openAssetFileDescriptor(Uri.withAppendedPath(uri,id.toString()),"r")?.use {fd->fd.length.takeIf{it>=0} ?: fd.parcelFileDescriptor.statSize}}.getOrNull() ?: -1
            if(bytes<0){unknown++;continue};total+=bytes
            largest+=Attachment(id,q.getLong(1),q.getString(3)?.takeIf{it.isNotBlank()} ?: q.getString(4)?.takeIf{it.isNotBlank()} ?: "Attachment",q.getString(2).orEmpty(),bytes)
            largest.sortByDescending{it.bytes};if(largest.size>20)largest.removeAt(20)
        }}
        Report(largest,total,count,unknown,truncated)
    }
}

@Composable
fun StorageSettingsDialog(close:()->Unit) {
    val c=LocalContext.current
    val scope=rememberCoroutineScope()
    var report by remember {mutableStateOf<SettingsStorage.Report?>(null)}
    var eligible by remember {mutableLongStateOf(0)}
    var status by remember {mutableStateOf("Reviewing Storage…")}
    var error by remember {mutableStateOf<String?>(null)}
    var loading by remember {mutableStateOf(true)}
    var clearing by remember {mutableStateOf(false)}
    var confirm by remember {mutableStateOf(false)}
    var refresh by remember {mutableIntStateOf(0)}
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME){refresh++}
    LaunchedEffect(refresh){
        loading=true;report=null;status="Reviewing Storage…"
        try {
            eligible=withContext(Dispatchers.IO){SettingsStorage.eligible(c).sumOf{it.length()}}
            report=SettingsStorage.attachments(c);status=""
        }catch(e:CancellationException){throw e}
        catch(e:Exception){status=e.message ?: "Could not review attachments"}
        finally{loading=false}
    }
    fun size(n:Long)=Formatter.formatFileSize(c,n)
    ReferenceDialog("Storage",{if(!clearing)close()},covered=confirm,footer={DialogAction("Close",enabled=!clearing,onClick=close)}) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Phone Storage Available · ${size(c.filesDir.usableSpace)}",fontSize=14.sp)
            Text("Old Export Copies · ${size(eligible)}",fontSize=14.sp)
            Text("Clearing removes temporary export copies older than two days. Messages, active drafts, app data, backgrounds and saved downloads are kept.",fontSize=14.sp,lineHeight=20.sp)
            ChoiceButton(if(clearing)"Clearing…" else "Clear Temporary Files",outlined=false,enabled=!clearing&&eligible>0){confirm=true}
            ChoiceButton("Phone Storage",outlined=false){runCatching{c.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))}.onFailure{error=it.message}}
            if(status.isNotEmpty())Text(status,fontSize=14.sp)
            if(!loading&&report==null)TextButton(onClick={refresh++}){Text("Try Again")}
            report?.let{r->
                Text("Message Attachments · ${if(r.unknown>0||r.truncated)"at least " else ""}${size(r.total)}\n${r.count} attachments${if(r.unknown>0)" · ${r.unknown} sizes unavailable" else ""}",fontSize=14.sp)
                Text("Largest Attachments",fontSize=17.sp)
                Text(if(r.truncated)"Showing the largest of the first 10,000 attachments." else "Tap an item to review its conversation. Delete unwanted messages there.",fontSize=13.sp)
                if(r.count==0)Text("No message attachments stored on this phone.",fontSize=14.sp)
                r.items.forEach{item->SettingsChoice(item.name,"${item.mime} · ${size(item.bytes)}"){
                    scope.launch{try{
                        val thread=withContext(Dispatchers.IO){
                            c.contentResolver.query(Uri.parse("content://mms/${item.message}"),arrayOf("thread_id"),null,null,null)?.use{q->if(q.moveToFirst())q.getLong(0) else 0} ?: 0
                        }
                        check(thread>0){"This message is no longer available"}
                        c.startActivity(Intent(c,MainActivity::class.java).putExtra("open_thread",thread).putExtra("open_message_key","mms:${item.message}")
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP));close()
                    }catch(e:Exception){error=e.message}}
                }}
            }
        }
    }
    if(confirm)ReferenceDialog("Clear Temporary Files?",{confirm=false},footer={
        DialogAction("Cancel"){confirm=false}
        DialogAction("Clear",destructive=true){confirm=false;clearing=true;scope.launch{try{
            val freed=withContext(Dispatchers.IO){SettingsStorage.clear(c)}
            eligible=withContext(Dispatchers.IO){SettingsStorage.eligible(c).sumOf{it.length()}}
            status="Freed ${size(freed)}"
        }catch(e:Exception){error=e.message}finally{clearing=false}}}
    }){Text("Remove old temporary export copies? Your saved files and messages stay.",Modifier.padding(20.dp),fontSize=15.sp)}
    ErrorDialog(error){error=null}
}
