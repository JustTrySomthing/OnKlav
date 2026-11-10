package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.UUID

/** Metadata backup only, intentionally excluding pending sends, permissions and media. */
internal object SettingsBackup {
    private fun origin(c:Context):String {
        val p=c.getSharedPreferences("backup_identity",0)
        return p.getString("origin",null) ?: UUID.randomUUID().toString().also{check(p.edit().putString("origin",it).commit())}
    }
    fun export(c:Context):JSONObject {
        val metadata=ChatStore(c).requestSync(JSONObject().put("action","backup_export"))
        val flags=metadata.getJSONObject("messages")
        flags.keysList().forEach {key->flags.getJSONObject(key).apply {
            put("reminder_title","");put("reminder_text","");put("remind_at",0)
        }}
        return JSONObject().put("format","onklav-app-backup").put("version",1)
            .put("origin",origin(c)).put("created",System.currentTimeMillis()).put("metadata",metadata)
    }
    fun inspect(c:Context,backup:JSONObject):JSONObject {
        require(backup.getString("format")=="onklav-app-backup"&&backup.getInt("version")==1){"Choose an OnKlav app backup"}
        require(backup.getString("origin").length<=64){"Invalid backup identity"}
        return ChatStore(c).requestSync(JSONObject().put("action","backup_check").put("backup",backup.getJSONObject("metadata")))
            .put("same_device",backup.getString("origin")==origin(c))
    }
    fun restore(c:Context,backup:JSONObject) {
        val checked=inspect(c,backup)
        ChatStore(c).requestSync(JSONObject().put("action","backup_restore").put("backup",backup.getJSONObject("metadata"))
            .put("same_device",checked.getBoolean("same_device")))
    }
}

@Composable
fun BackupSettingsDialog(close:()->Unit) {
    val c=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var status by remember {mutableStateOf("")}
    var error by remember {mutableStateOf<String?>(null)}
    var selected by remember {mutableStateOf<JSONObject?>(null)}
    var summary by remember {mutableStateOf<JSONObject?>(null)}
    var confirm by remember {mutableStateOf(false)}
    var history by remember {mutableStateOf(false)}
    var notes by remember {mutableStateOf(false)}
    var library by remember {mutableStateOf<String?>(null)}
    var recovery by remember{mutableStateOf(false)}
    var interrupted by remember {mutableIntStateOf(0)}
    LaunchedEffect(Unit){try{interrupted=withContext(Dispatchers.IO){HistoryRestore.recoverState(c).getInt("pending")}}catch(e:Exception){error=e.message}}
    fun job(action:suspend()->Unit) {
        if(busy)return;busy=true
        scope.launch{try{action()}catch(e:Exception){error=e.message}finally{busy=false}}
    }
    val export=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("application/json")){uri->
        if(uri!=null)job{
            withContext(ChatStore.dispatcher){
                val data=SettingsBackup.export(c).toString().toByteArray(Charsets.UTF_8)
                writeFiledExport(c,uri){it.write(data)}
            };status="App metadata backup saved."
        }
    }
    val picker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){uri->
        if(uri!=null)job{
            selected=null;summary=null
            val checked=withContext(ChatStore.dispatcher){
                val bytes=c.contentResolver.openInputStream(uri)?.use{ThemePackage.bounded(it,8_000_000)} ?: error("Could not open this backup")
                val data=JSONObject(bytes.toString(Charsets.UTF_8))
                data to SettingsBackup.inspect(c,data)
            };selected=checked.first;summary=checked.second;status="Backup checked. Review it before restoring."
        }
    }
    ReferenceDialog("Backup and Restore",{if(!busy)close()},covered=confirm||history||recovery||notes||library!=null,footer={DialogAction("Close",enabled=!busy,onClick=close)}){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Save Groups, blocked senders, folders, stars and message flags. Share appearance settings separately using Appearance › Load & Save Themes.",fontSize=16.sp,lineHeight=22.sp)
            Text("This backup contains phone numbers and group names. It does not contain message bodies, media, app documents, permissions or pending sends. Keep it private.",fontSize=14.sp,lineHeight=20.sp)
            if(interrupted>0)Text("An interrupted message restore needs review. No automatic retry will run.",fontSize=14.sp)
            if(interrupted>0)ChoiceButton("Review interrupted restore",outlined=false,enabled=!busy){recovery=true}
            ChoiceButton("App Documents Backup",outlined=false,enabled=!busy){library="documents"}
            ChoiceButton("Browser Backup",outlined=false,enabled=!busy){library="browser"}
            ChoiceButton("YouTube Backup",outlined=false,enabled=!busy){library="youtube"}
            ChoiceButton("RSS Backup",outlined=false,enabled=!busy){library="rss"}
            ChoiceButton("Read Aloud Backup",outlined=false,enabled=!busy){library="reader"}
            ChoiceButton("Files Backup",outlined=false,enabled=!busy){library="files"}
            ChoiceButton("Annotated Images Backup",outlined=false,enabled=!busy){library="annotation"}
            ChoiceButton("Notes Backup",outlined=false,enabled=!busy){notes=true}
            ChoiceButton("Messages and Media Backup",outlined=false,enabled=!busy){history=true}
            Text("Export SMS/MMS history and downloaded media separately. Restoration uses a checked archive and explicit carrier test mode.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceButton("Save App Backup",outlined=false,enabled=!busy){runCatching{export.launch("OnKlav-App-Backup.json")}.onFailure{error=it.message}}
            ChoiceButton("Choose Backup to Restore",outlined=false,enabled=!busy){runCatching{picker.launch(arrayOf("application/json","application/octet-stream"))}.onFailure{error=it.message}}
            if(busy)Text("Working…",fontSize=14.sp)
            if(status.isNotEmpty())Text(status,fontSize=14.sp)
            summary?.let{data->
                Text("${data.getInt("groups")} Groups · ${data.getInt("blocked")} Blocked Senders\n${data.getInt("threads")} Folders · ${data.getInt("messages")} Message Flags",fontSize=14.sp)
                Text(if(data.getBoolean("same_device"))"Matching existing entries are kept. Reminders will not be rescheduled." else "From another installation: only Groups and blocked senders can be restored. Phone-specific folders and message flags are skipped.",fontSize=13.sp)
                ChoiceButton("Restore App Metadata",outlined=false,enabled=!busy){confirm=true}
            }
        }
    }
    if(recovery)HistoryRecoveryDialog{recovery=false;scope.launch{runCatching{withContext(Dispatchers.IO){HistoryRestore.status(c).getInt("pending")}}.onSuccess{interrupted=it}}}
    library?.let{LibraryBackupDialog(it){library=null}}
    if(notes)NotesBackupDialog{notes=false}
    if(history)HistoryBackupDialog{history=false}
    if(confirm)ReferenceDialog("Restore App Metadata?",{confirm=false},footer={
        DialogAction("Cancel"){confirm=false}
        DialogAction("Restore"){confirm=false;selected?.let{data->job{
            withContext(ChatStore.dispatcher){SettingsBackup.restore(c,data)}
            selected=null;summary=null;status="App metadata restored. Existing entries were kept."
        }}}
    }){Text("Merge the checked metadata into OnKlav? Messages are not replaced or sent, and app permissions stay unchanged.",Modifier.padding(20.dp),fontSize=15.sp)}
    ErrorDialog(error){error=null}
}

@Composable
internal fun HistoryBackupDialog(close:()->Unit) {
    val c=LocalContext.current;val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)};var status by remember{mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)};var stopping by remember{mutableStateOf(false)}
    var token by remember{mutableStateOf<HistoryBackup.Stop?>(null)}
    var review by remember{mutableStateOf<HistoryImport.Review?>(null)}
    var recovery by remember{mutableStateOf(false)}
    var confirmation by remember{mutableStateOf<HistoryConfirmation?>(null)}
    var reviewUri by remember{mutableStateOf<android.net.Uri?>(null)}
    DisposableEffect(Unit){onDispose{token?.cancel();confirmation?.cancel()}}
    val export=rememberLauncherForActivityResult(OnKlavFileContracts.CreateDocument("application/zip")){uri->
        if(uri!=null&&!busy){
            val stop=HistoryBackup.Stop();token=stop;busy=true;stopping=false;review=null;status="Reading messages…"
            scope.launch {
                try {
                    val result=withContext(Dispatchers.IO){HistoryBackup.export(c,uri,stop){text->scope.launch{status=text}}}
                    status="Saved and verified: ${result.sms} SMS · ${result.mms} MMS · ${result.parts} parts."
                } catch(e:Exception){
                    if(stop.signal.isCanceled)status="Backup stopped. No complete backup was saved."
                    else error="${e.message ?: "Backup failed"}. The selected document may need to be removed if its provider could not delete the incomplete copy."
                }finally{busy=false;token=null;stopping=false}
            }
        }
    }
    val importPicker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){uri->
        if(uri!=null&&!busy) {
            val stop=HistoryBackup.Stop();token=stop;busy=true;stopping=false;review=null;reviewUri=null;status="Checking backup…"
            scope.launch {
                try {
                    review=withContext(Dispatchers.IO){HistoryImport.inspect(c,uri,stop){text->scope.launch{status=text}}}
                    reviewUri=uri;status="Backup checked. Nothing has been restored."
                }catch(e:Exception){
                    review=null
                    if(stop.signal.isCanceled)status="Backup check stopped. Nothing has been restored." else {status="";error=e.message ?: "Backup check failed"}
                }finally{busy=false;token=null;stopping=false}
            }
        }
    }
    fun compareNow() {
        val previous=review ?: return;val uri=reviewUri ?: return;if(busy)return
        confirmation?.cancel();confirmation=null
        val stop=HistoryBackup.Stop();token=stop;busy=true;stopping=false;review=previous.copy(comparison=null);status="Preparing comparison…"
        scope.launch {
            try {
                review=withContext(Dispatchers.IO){HistoryImport.inspect(c,uri,stop,true,previous.sha256){text->scope.launch{status=text}}}
                status="Comparison complete. Nothing has been restored."
            }catch(e:Exception){
                if(stop.signal.isCanceled)status="Comparison stopped. Nothing has been restored." else {status="";error=e.message ?: "Comparison failed"}
            }finally{busy=false;token=null;stopping=false}
        }
    }
    fun reviewRestoration(){
        val checked=review ?: return;val uri=reviewUri ?: return;if(busy)return
        busy=true
        scope.launch{try{confirmation=withContext(Dispatchers.IO){HistoryConfirmation.prepare(c,uri,checked)}}catch(e:Exception){error=e.message}finally{busy=false}}
    }
    fun applyRestoration(checked:HistoryConfirmation){
        if(busy)return
        val stop=HistoryBackup.Stop();token=stop;busy=true;stopping=false;status="Preparing restoration…"
        scope.launch{try{
            val result=withContext(Dispatchers.IO){HistoryApply.applyChecked(c,checked,stop){text->scope.launch{status=text}}}
            status="Restoration completed: ${result.getInt("inserted")} inserted · ${result.getInt("pending")} pending."
        }catch(e:Exception){status=if(stop.signal.isCanceled)"Restoration stopped. Review saved outcomes before continuing." else "Restoration did not complete. Review saved outcomes before continuing.";error=e.message}
        finally{checked.cancel();confirmation=null;review=review?.copy(comparison=null);busy=false;token=null;stopping=false}}
    }
    val comparePermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted)compareNow() else error="SMS access is required to compare with phone history."
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        status=if(granted)"SMS access granted. Choose Save messages and media to continue." else "SMS access is required to read history."
    }
    ReferenceDialog("Messages and media backup",{if(!busy)close()},covered=review!=null||recovery,footer={
        if(busy)DialogAction(if(stopping)"Stopping…" else "Stop",enabled=!stopping){stopping=true;token?.cancel()}
        DialogAction("Close",enabled=!busy,onClick=close)
    }) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Save SMS, MMS, downloaded photos, video and audio in one ZIP file. Messages are read without changing or sending anything.",fontSize=16.sp,lineHeight=22.sp)
            Text("This backup is not encrypted. It includes private messages, phone numbers, media and OnKlav message metadata. Keep it somewhere private.",fontSize=14.sp,lineHeight=20.sp)
            Text("RCS, iMessage, contacts, drafts and undownloaded media are excluded. Limits: 200,000 messages, 100,000 MMS parts, 128 MiB of record text, 64 MiB per attachment and 2 GiB of media. Exceeding a limit stops the export; nothing is silently truncated.",fontSize=13.sp,lineHeight=18.sp)
            ChoiceButton("Save messages and media",outlined=false,enabled=!busy){
                runCatching{if(HistoryBackup.allowed(c))export.launch("OnKlav-Messages.zip") else permission.launch(android.Manifest.permission.READ_SMS)}.onFailure{error=it.message}
            }
            ChoiceButton("Choose backup to restore",outlined=false,enabled=!busy){runCatching{importPicker.launch(arrayOf("application/zip","application/octet-stream"))}.onFailure{error=it.message}}
            ChoiceButton("Restore settings and recovery",outlined=false,enabled=!busy){recovery=true}
            Text("Choose a backup to check its contents. Checking will not change your phone’s messages.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(status.isNotEmpty())Text(status,fontSize=14.sp)
        }
    }
    if(recovery)HistoryRecoveryDialog{recovery=false}
    review?.let{HistoryRestoreReviewDialog(it,busy,status,stopping,
        onCompare={runCatching{if(HistoryBackup.allowed(c))compareNow() else comparePermission.launch(android.Manifest.permission.READ_SMS)}.onFailure{error=it.message}},
        onStop={stopping=true;token?.cancel()},onReview={reviewRestoration()},covered=confirmation!=null){if(!busy){confirmation?.cancel();confirmation=null;review=null;reviewUri=null;status=""}}}
    confirmation?.let{checked->HistoryRestoreConfirmationDialog(checked,busy,status,stopping,onStop={stopping=true;token?.cancel()},onApply={applyRestoration(checked)}){checked.cancel();confirmation=null}}
    ErrorDialog(error){error=null}
}


@Composable
internal fun HistoryRestoreReviewDialog(review:HistoryImport.Review,busy:Boolean=false,status:String="",stopping:Boolean=false,onCompare:(()->Unit)?=null,onStop:(()->Unit)?=null,onReview:(()->Unit)?=null,covered:Boolean=false,close:()->Unit) {
    ReferenceDialog("Review message backup",{if(!busy)close()},covered=covered,footer={
        if(busy&&onStop!=null)DialogAction(if(stopping)"Stopping…" else "Stop",enabled=!stopping,onClick=onStop)
        DialogAction("Close",enabled=!busy,onClick=close)
    }) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("${review.sms} SMS · ${review.mms} MMS · ${review.parts} parts",fontSize=16.sp)
            Text("${review.inbox} Inbox · ${review.sent} Sent · ${review.other} Other message states\nDownloaded media: ${android.text.format.Formatter.formatShortFileSize(LocalContext.current,review.mediaBytes)}",fontSize=14.sp)
            if(review.undownloaded>0)Text("${review.undownloaded} MMS notifications have undownloaded media. Checking a backup does not download it.",fontSize=14.sp)
            Text("App metadata: ${review.groups} Groups · ${review.blocked} Blocked Senders · ${review.folders} Folders · ${review.flags} Message Flags",fontSize=14.sp)
            Text("The backup passed its integrity checks. This check does not identify who created it.",fontSize=14.sp,lineHeight=20.sp)
            if(onCompare!=null)ChoiceButton("Compare with phone",outlined=false,enabled=!busy,onClick=onCompare)
            review.comparison?.let{comparison->
                Text("${comparison.matched} Already Present · ${comparison.fresh} New Candidates",fontSize=14.sp)
                Text("${comparison.needsReview} Need Review · ${comparison.skipped} Skipped",fontSize=14.sp)
                Text("Metadata mapping candidates: ${comparison.flagsMapped} Message Flags · ${comparison.threadsMapped} Folders",fontSize=14.sp)
                Text("Ambiguous matches are held for review. Pending sends and undownloaded MMS are skipped. Phone messages were only read.",fontSize=13.sp,lineHeight=18.sp)
            }
            if(status.isNotEmpty())Text(status,fontSize=14.sp)
            if(onReview!=null&&review.comparison!=null)ChoiceButton("Review restoration",outlined=false,enabled=!busy,onClick=onReview)
            if(!MessagingTransport.carrierEnabled(LocalContext.current))Text("Enable carrier phone testing in Setup and choose OnKlav as the default messenger before restoring. Compare the archive first, then choose Review restoration.",fontSize=14.sp,lineHeight=20.sp)
        }
    }
}

@Composable
internal fun HistoryRestoreConfirmationDialog(confirmation:HistoryConfirmation,busy:Boolean=false,status:String="",stopping:Boolean=false,onStop:()->Unit={},onApply:()->Unit={},close:()->Unit) {
    val c=LocalContext.current
    val comparison=requireNotNull(confirmation.review.comparison)
    ReferenceDialog(if(confirmation.resume==null)"Confirm message restoration" else "Finish saved restore",{if(!busy)close()},footer={
        if(busy)DialogAction(if(stopping)"Stopping…" else "Stop",enabled=!stopping,onClick=onStop)
        DialogAction("Cancel",enabled=!busy,onClick=close)
        DialogAction("Restore",enabled=!busy&&MessagingTransport.carrierEnabled(c),onClick=onApply)
    }){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(if(confirmation.resume==null)"${comparison.fresh} New Candidates · ${comparison.matched} Already Present" else "${confirmation.resume.pendingSources.size} Remaining Candidates · ${comparison.matched} Already Present",fontSize=16.sp)
        Text("${comparison.needsReview} Need Review · ${comparison.skipped} Skipped",fontSize=14.sp)
        Text(if(confirmation.resume==null)"Only new, eligible Inbox and Sent messages will be inserted. Existing messages are kept. Ambiguous messages and unsupported states are not inserted." else if(confirmation.resume.pendingSources.isNotEmpty())"${confirmation.resume.pendingSources.size} remaining messages from the saved record will be inserted after rechecking. Completed messages are kept. Held messages remain deferred." else "No messages will be inserted by this continuation. Only folders and flags from this saved record can be finished, after checking its recorded messages again. Held messages remain deferred.",fontSize=14.sp,lineHeight=20.sp)
        Text("Your local phone identities",fontSize=16.sp)
        Text(if(confirmation.identities.isEmpty())"None saved. This review contains no MMS." else confirmation.identities.joinToString("\n"),fontSize=14.sp,lineHeight=20.sp)
        Text("These must include your own numbers on this phone and the phone that made the backup. They determine MMS participants. If they are wrong, cancel and correct Restore settings and recovery.",fontSize=13.sp,lineHeight=18.sp)
        Text("Folders and flags are applied only after checking restored messages again. Current entries are kept; old reminders are not scheduled. Stop may leave already completed messages in place. Saved outcomes must be reviewed before another attempt.",fontSize=14.sp,lineHeight=20.sp)
        if(!MessagingTransport.carrierEnabled(c))Text(HistoryImport.CARRIER_SETUP_REQUIRED,fontSize=14.sp,lineHeight=20.sp)
        if(status.isNotEmpty())Text(status,fontSize=14.sp)
    }}
}
