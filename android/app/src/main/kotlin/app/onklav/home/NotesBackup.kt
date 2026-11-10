package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.onklav.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/** Explicit host-owned transfer; packages, permissions and runtime state are excluded. */
internal object NotesBackup {
    private const val LIMIT=16*1024*1024
    data class Review(val bytes:ByteArray,val summary:JSONObject)
    private fun request(c:Context,operation:String,sha:String="")=AppSdk.sync(c,JSONObject()
        .put("action","notes_backup").put("operation",operation).put("sha256",sha)) as JSONObject
    suspend fun export(c:Context,uri:Uri):Int {
      val count=AppSdk.staging.withLock {
        withContext(Dispatchers.IO){
            val file=File(c.filesDir,"notes-backup-export.json")
            try {
                val result=request(c,"export")
                writeExportBytes(c,uri){out->file.inputStream().use{input->val buffer=ByteArray(65536);while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n)}}}
                result.getInt("notes")
            }finally{file.delete()}
        }
      }
      awaitFiledExport(c,uri);return count
    }
    suspend fun inspect(c:Context,uri:Uri):Review=AppSdk.staging.withLock {
        withContext(Dispatchers.IO){
            val bytes=c.contentResolver.openInputStream(uri)?.use{ThemePackage.bounded(it,LIMIT)}?:error("Could not open this backup")
            val file=File(c.filesDir,"notes-backup-import.json")
            try {file.writeBytes(bytes);Review(bytes,request(c,"inspect"))}finally{file.delete()}
        }
    }
    suspend fun restore(c:Context,review:Review):JSONObject=AppSdk.staging.withLock {
        withContext(Dispatchers.IO){
            val file=File(c.filesDir,"notes-backup-import.json")
            try {file.writeBytes(review.bytes);request(c,"restore",review.summary.getString("sha256"))}finally{file.delete()}
        }
    }
}

@Composable internal fun NotesBackupDialog(close:()->Unit){
    val c=LocalContext.current;val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)};var status by remember{mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)};var review by remember{mutableStateOf<NotesBackup.Review?>(null)}
    var confirm by remember{mutableStateOf(false)}
    fun job(action:suspend()->Unit){if(busy)return;busy=true;scope.launch{try{action()}catch(e:Exception){if(e is CancellationException)throw e;error=e.message}finally{busy=false}}}
    val save=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("application/json")){uri->if(uri!=null)job{
        val count=NotesBackup.export(c,uri);status="Saved $count notes."
    }}
    val choose=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){uri->if(uri!=null)job{
        review=null;status="";review=NotesBackup.inspect(c,uri)
    }}
    ReferenceDialog("Notes Backup",{if(!busy)close()},covered=confirm,footer={DialogAction("Close",enabled=!busy,onClick=close)}){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Save active Notes, including their titles and text. Notes in Trash, folders, other app documents and permissions are excluded. New notes restore into Notes.",fontSize=15.sp)
            Text("Backups are not encrypted. Keep them private. Maximum 2,000 notes and 16 MiB.",fontSize=13.sp)
            ChoiceButton("Save Notes Backup",outlined=false,enabled=!busy){runCatching{save.launch("OnKlav-Notes-Backup.json")}.onFailure{error=it.message}}
            ChoiceButton("Choose Notes Backup",outlined=false,enabled=!busy){runCatching{choose.launch(arrayOf("application/json","application/octet-stream"))}.onFailure{error=it.message}}
            if(busy)Text("Working…",fontSize=14.sp)
            if(status.isNotEmpty())Text(status,fontSize=14.sp)
            review?.let{r->Text("${r.summary.getInt("notes")} notes · ${r.summary.getInt("new")} new · ${r.summary.getInt("kept")} already present",fontSize=14.sp)
                ChoiceButton("Restore Notes",outlined=false,enabled=!busy){confirm=true}}
        }
    }
    if(confirm)ReferenceDialog("Restore Notes?",{confirm=false},footer={
        DialogAction("Cancel"){confirm=false}
        DialogAction("Restore"){confirm=false;review?.let{r->job{
            val result=NotesBackup.restore(c,r);review=null
            status="Restored ${result.getInt("new")} notes. Kept ${result.getInt("kept")} existing notes."
        }}}
    }){Text("Add missing notes? Existing notes, including those in Trash, are kept. If storage fails partway through, retrying keeps notes already restored.",Modifier.padding(20.dp),fontSize=15.sp)}
    ErrorDialog(error){error=null}
}
