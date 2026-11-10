package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onklav.shared.*
import kotlinx.coroutines.*

@Composable
internal fun HistoryResumeDialog(saved:HistoryResume.Binding,close:()->Unit){
    val c=LocalContext.current;val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)};var token by remember{mutableStateOf<HistoryBackup.Stop?>(null)}
    var ticket by remember{mutableStateOf<HistoryConfirmation?>(null)};var error by remember{mutableStateOf<String?>(null)}
    var status by remember{mutableStateOf("")};var waitingUri by remember{mutableStateOf<Uri?>(null)}
    var stopping by remember{mutableStateOf(false)};var ended by remember{mutableStateOf(false)}
    DisposableEffect(Unit){onDispose{token?.cancel();ticket?.cancel()}}
    fun recheck(uri:Uri){if(busy||ended)return;val stop=HistoryBackup.Stop();token=stop;busy=true;stopping=false;status="Checking the original backup and recorded messages…"
        scope.launch{try{ticket=withContext(Dispatchers.IO){HistoryResume.review(c,uri,saved,stop){text->scope.launch{status=text}}};status="Original backup and recorded message IDs checked. Review before continuing."}
        catch(e:Exception){status=if(stop.signal.isCanceled)"Check stopped. Nothing was resumed." else "Nothing was resumed.";error=e.message}
        finally{busy=false;token=null;stopping=false}}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->val uri=waitingUri;waitingUri=null;if(granted&&uri!=null)recheck(uri)else error="SMS access is required to check the recorded messages."}
    val picker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){uri->if(uri!=null&&!busy&&!ended){if(HistoryBackup.allowed(c))recheck(uri)else{waitingUri=uri;permission.launch(Manifest.permission.READ_SMS)}}}
    fun continueRestore(checked:HistoryConfirmation){if(busy)return;val stop=HistoryBackup.Stop();token=stop;busy=true;stopping=false
        scope.launch{try{withContext(Dispatchers.IO){HistoryResume.applyChecked(c,checked,stop){text->scope.launch{status=text}}};status=if(saved.pendingSources.isEmpty())"Saved folders and flags finished. No messages were inserted." else "Remaining messages restored; saved folders and flags finished.";ended=true}
        catch(e:Exception){status="Continuation stopped. Close this screen and reopen the saved record for a fresh review.";ended=true;error=e.message}
        finally{checked.cancel();ticket=null;busy=false;token=null;stopping=false}}
    }
    ReferenceDialog("Reopen saved restore",{if(!busy)close()},covered=ticket!=null,footer={
        if(busy)DialogAction(if(stopping)"Stopping…" else "Stop",enabled=!stopping){stopping=true;token?.cancel()}
        DialogAction("Close",enabled=!busy,onClick=close)
    }){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Choose the original message backup used for this saved restore. OnKlav will check the file and compare every recorded message with its saved ID.",fontSize=14.sp,lineHeight=20.sp)
        Text("${saved.pendingSources.size} remaining messages need review before insertion. Already completed messages will not be inserted again. Unknown insert results must be resolved first; no automatic retry runs.",fontSize=14.sp,lineHeight=20.sp)
        ChoiceButton("Choose original backup",outlined=false,enabled=!busy&&!ended){runCatching{picker.launch(arrayOf("application/zip","application/octet-stream"))}.onFailure{error=it.message}}
        if(status.isNotEmpty())Text(status,fontSize=14.sp)
    }}
    ticket?.let{checked->HistoryRestoreConfirmationDialog(checked,busy,status,stopping,onStop={stopping=true;token?.cancel()},onApply={continueRestore(checked)}){checked.cancel();ticket=null}}
    ErrorDialog(error){error=null}
}
