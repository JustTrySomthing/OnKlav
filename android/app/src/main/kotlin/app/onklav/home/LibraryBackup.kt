package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.onklav.shared.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

internal object LibraryBackup {
    private const val MAX=512L*1024*1024
    class Stop(private val c:Context){
        val cancelled=AtomicBoolean(false);private val active=AtomicBoolean(false)
        private val marker get()=File(c.filesDir,"library-backup.cancel")
        fun cancel(){cancelled.set(true);if(active.get())runCatching{marker.writeText("")}}
        fun check(){kotlin.check(!cancelled.get()){"Backup operation stopped"}}
        fun start(){marker.delete();active.set(true);if(cancelled.get()){runCatching{marker.writeText("")};check()}}
        fun finish(){active.set(false);marker.delete()}
    }
    data class Review(val kind:String,val file:File,val summary:JSONObject){fun close(){file.delete()}}
    private fun request(c:Context,kind:String,operation:String,sha:String="")=AppSdk.sync(c,JSONObject().put("action","library_backup").put("kind",kind).put("operation",operation).put("sha256",sha)) as JSONObject
    private suspend fun copy(input:InputStream,out:OutputStream,stop:Stop){val buffer=ByteArray(65536);var size=0L;while(true){currentCoroutineContext().ensureActive();stop.check();val n=input.read(buffer);if(n<0)break;size+=n;require(size<=MAX){"Backup exceeds 512 MiB"};out.write(buffer,0,n)}}
    private fun directory(c:Context):File=File(c.cacheDir,"library-backups").also{dir->
        check(!java.nio.file.Files.isSymbolicLink(dir.toPath())&&(dir.isDirectory||dir.mkdir())){"Backup staging is unavailable"}
        dir.listFiles().orEmpty().filter{it.name.startsWith("review-")&&it.isFile&&!java.nio.file.Files.isSymbolicLink(it.toPath())&&it.lastModified()<System.currentTimeMillis()-86400000L}.forEach{it.delete()}
    }
    suspend fun export(c:Context,kind:String,uri:Uri,stop:Stop):JSONObject {
      val result=AppSdk.staging.withLock{
        withContext(Dispatchers.IO){val file=File(c.filesDir,"library-backup-export.zip")
            try{stop.start();require(uri.scheme=="content"){"Choose a save location in Files"};val result=request(c,kind,"export");stop.check()
                preflightFileExport(c,uri,"application/zip",file.length())
                writeExportBytes(c,uri){out->file.inputStream().use{copy(it,out,stop)}}
                stop.check();result
            }finally{file.delete();File(c.filesDir,"library-backup-export.tmp").delete();stop.finish()}
        }
      }
      awaitFiledExport(c,uri);return result
    }
    suspend fun inspect(c:Context,kind:String,uri:Uri,stop:Stop):Review=AppSdk.staging.withLock{
        withContext(Dispatchers.IO){val input=File(c.filesDir,"library-backup-import.zip");var retained:File?=null
            try{stop.start();require(uri.scheme=="content"){"Choose a backup from Files"}
                c.contentResolver.openInputStream(uri)?.use{source->FileOutputStream(input).use{copy(source,it,stop);it.fd.sync()}}?:error("Could not open this backup")
                val summary=request(c,kind,"inspect");stop.check()
                val file=File.createTempFile("review-",".zip",directory(c));retained=file
                check(input.renameTo(file)){"Could not retain the checked backup"};Review(kind,file,summary)
            }catch(e:Exception){retained?.delete();throw e}finally{input.delete();stop.finish()}
        }
    }
    suspend fun restore(c:Context,review:Review,stop:Stop):JSONObject=AppSdk.staging.withLock{
        withContext(Dispatchers.IO){val input=File(c.filesDir,"library-backup-import.zip")
            try{stop.start();check(review.file.isFile&&!java.nio.file.Files.isSymbolicLink(review.file.toPath())){"Choose the backup again"}
                review.file.inputStream().use{source->FileOutputStream(input).use{copy(source,it,stop);it.fd.sync()}}
                val result=request(c,review.kind,"restore",review.summary.getString("sha256"));stop.check();result
            }finally{input.delete();stop.finish()}
        }
    }
}

@Composable internal fun LibraryBackupDialog(kind:String,close:()->Unit){
    val c=LocalContext.current;val scope=rememberCoroutineScope();val title=when(kind){"files"->"Files Backup";"annotation"->"Annotated Images Backup";"rss"->"RSS Backup";"documents"->"App Documents Backup";"browser"->"Browser Backup";"youtube"->"YouTube Backup";else->"Read Aloud Backup"}
    var busy by remember{mutableStateOf(false)};var status by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    var review by remember{mutableStateOf<LibraryBackup.Review?>(null)};var confirm by remember{mutableStateOf(false)};var stop by remember{mutableStateOf<LibraryBackup.Stop?>(null)}
    DisposableEffect(Unit){onDispose{stop?.cancel();review?.close()}}
    fun job(action:suspend(LibraryBackup.Stop)->Unit){if(busy)return;val token=LibraryBackup.Stop(c);stop=token;busy=true;status=""
        scope.launch{try{action(token)}catch(e:Exception){if(e is CancellationException)throw e
            if(token.cancelled.get())status="Stopped. Restored items are kept; retry to finish. A failed save may leave an incomplete document." else error=e.message
        }finally{stop=null;busy=false}}
    }
    val save=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("application/zip")){uri->if(uri!=null)job{token->val result=LibraryBackup.export(c,kind,uri,token);status="Saved and checked ${result.getInt("items")} items."}}
    val choose=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){uri->if(uri!=null)job{token->review?.close();review=null;review=LibraryBackup.inspect(c,kind,uri,token)}}
    ReferenceDialog(title,{if(!busy)close()},covered=confirm,footer={
        if(busy)DialogAction(if(stop?.cancelled?.get()==true)"Stopping…" else "Stop",enabled=stop?.cancelled?.get()!=true){stop?.cancel();status="Stopping…"}
        DialogAction("Close",enabled=!busy,onClick=close)
    }){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(when(kind){"files"->"Save active Files and folders. Notes use Notes Backup. Trash, System Files and Android folder links are excluded.";"annotation"->"Save editable annotated images, including their marks and undo history. App permissions and the last-open picture are excluded.";"rss"->"Save sources, cached articles, bookmarks, read status and RSS settings. Existing data and settings are kept when merging. Restore does not fetch articles.";"documents"->"Save all built-in app documents together: Notes, Files, encrypted Vault, annotated images, RSS, Read Aloud, Browser, YouTube and saved Walkie audio/recipients. Restored audio is playback only. Messages and appearance use their separate backups below and in Appearance.";"browser"->"Save tabs, bookmarks and Browser settings. Existing tabs, bookmarks and settings are kept when merging. Website cookies and sign-in data are excluded.";"youtube"->"Save bookmarks and viewing history. Existing entries are kept. Website cookies, sign-in data and playback state are excluded.";else->"Save the current saved draft. An existing saved draft, including cleared text, is kept. Voice settings and playback position stay on this device."},fontSize=15.sp)
            Text((if(kind=="documents")"The archive is not encrypted; Vault stays encrypted with its own password. Existing Vault data is kept on restore. "else"Backups are not encrypted. ")+"Maximum 512 MiB. For backups over 256 MiB, choose a connected phone folder in Files. Allow about twice the backup size in temporary space, plus space for restored items.",fontSize=13.sp)
            ChoiceButton("Save Backup",outlined=false,enabled=!busy){runCatching{save.launch("OnKlav-${when(kind){"files"->"Files";"annotation"->"Annotation";"rss"->"RSS";"documents"->"App-Documents";"browser"->"Browser";"youtube"->"YouTube";else->"Read-Aloud"}}-Backup.zip")}.onFailure{error=it.message}}
            ChoiceButton("Choose Backup",outlined=false,enabled=!busy){runCatching{choose.launch(arrayOf("application/zip","application/octet-stream"))}.onFailure{error=it.message}}
            if(busy)Text("Working…",fontSize=14.sp)
            if(status.isNotEmpty())Text(status,fontSize=14.sp)
            review?.let{r->Text("${r.summary.getInt("items")} items · ${r.summary.getInt("new")} new · ${r.summary.getInt("kept")} already present",fontSize=14.sp)
                ChoiceButton("Restore Checked Backup",outlined=false,enabled=!busy){confirm=true}}
        }
    }
    if(confirm)ReferenceDialog("Restore Checked Backup?",{confirm=false},footer={
        DialogAction("Cancel"){confirm=false}
        DialogAction("Restore"){confirm=false;review?.let{r->job{token->val result=LibraryBackup.restore(c,r,token);r.close();review=null;status="Restored ${result.getInt("new")} items. Kept ${result.getInt("kept")} existing items."}}}
    }){Text(when(kind){"rss"->"Add missing sources, bookmarks and read marks? Existing data and settings are kept. Close open apps before restoring.";"browser","youtube"->"Add missing entries? Existing data is kept. Website cookies and sign-in data stay on this device. Close open apps before restoring.";"reader"->"Restore this draft if no saved draft exists? An existing draft, including cleared text, is kept. Close open apps before restoring.";else->"Add missing items? Existing content, including Trash, is kept. If interrupted, retrying keeps completed items. Close open apps before restoring."},Modifier.padding(20.dp),fontSize=15.sp)}
    ErrorDialog(error){error=null}
}
