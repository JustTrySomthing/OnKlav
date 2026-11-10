package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onklav.shared.*
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
internal fun HistoryRecoveryDialog(close:()->Unit) {
    val c=LocalContext.current;val scope=rememberCoroutineScope()
    var overview by remember{mutableStateOf<JSONObject?>(null)};var config by remember{mutableStateOf(emptyList<String>())}
    var compactingCompleted by remember{mutableStateOf(false)}
    var compacting by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf(false)};var text by remember{mutableStateOf("")}
    var detail by remember{mutableStateOf<JSONObject?>(null)};var cleanup by remember{mutableStateOf<String?>(null)}
    var checkingRemoved by remember{mutableStateOf<String?>(null)}
    var reopening by remember{mutableStateOf<HistoryResume.Binding?>(null)}
    var offset by remember{mutableIntStateOf(0)};var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)};var status by remember{mutableStateOf("")}
    suspend fun reload(){val data=withContext(Dispatchers.IO){HistoryRecovery.overview(c,offset) to HistoryRecovery.configuration(c)};overview=data.first;config=data.second}
    fun work(action:suspend()->Unit){if(busy)return;busy=true;scope.launch{try{action()}catch(e:Exception){error=e.message ?: "Restore recovery unavailable"}finally{busy=false}}}
    LaunchedEffect(Unit){work{reload()}}
    ReferenceDialog("Restore settings and recovery",{if(!busy)close()},covered=editing||detail!=null||cleanup!=null||compacting||compactingCompleted||reopening!=null||checkingRemoved!=null,footer={
        DialogAction("Refresh",enabled=!busy){work{reload()}}
        DialogAction("Close",enabled=!busy,onClick=close)
    }){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(MessagingTransport.carrierEnabled(c))"Review interrupted restores before continuing. No messages are resent."else"Enable carrier phone testing in Setup before restoring message history.",fontSize=14.sp,lineHeight=20.sp)
            ChoiceButton("Local phone identities",outlined=false,enabled=!busy&&overview!=null){text=config.joinToString("\n");editing=true}
            Text("${config.size} saved local phone ${if(config.size==1)"identity" else "identities"}",fontSize=13.sp)
            overview?.let {data->
                val state=data.getJSONObject("status")
                Text("${state.getInt("committed")} committed inserts · ${state.getInt("rolled_back")} cleaned up",fontSize=14.sp)
                ChoiceButton("Compact completed journal records…",outlined=false,enabled=!busy){compacting=true}
                Text("${state.optInt("archived")} journal records archived · ${state.getInt("remaining")} active slots available",fontSize=13.sp)
                Text("Interrupted inserts",fontSize=16.sp)
                val pending=data.getJSONArray("pending")
                if(pending.length()==0)Text("No interrupted inserts.",fontSize=14.sp)
                repeat(pending.length()){i->val row=pending.getJSONObject(i);val phase=row.getString("phase")
                    Text(when(phase){"uncertain"->"Insert result is unknown. Restore remains paused.";"needs_review"->"This insertion needs review. Its record is retained.";"cleanup_required"->"An incomplete insertion has a recorded message ID.";else->"An interrupted insertion needs review."},fontSize=14.sp,lineHeight=20.sp)
                    if(phase=="needs_review"&&!row.isNull("target")){
                        ChoiceButton("Check already removed message…",outlined=false,enabled=!busy){checkingRemoved=row.getString("id")}
                    }
                    if(phase=="cleanup_required"){
                        ChoiceButton("Remove incomplete insert…",outlined=false,enabled=!busy&&MessagingTransport.carrierEnabled(c)){cleanup=row.getString("id")}
                        if(!MessagingTransport.carrierEnabled(c))Text("Phone cleanup is unavailable until restore verification is complete.",fontSize=13.sp)
                    }
                }
                if(pending.length()>0)Text("No automatic retry runs, and unresolved records are retained.",fontSize=13.sp,lineHeight=18.sp)
                Text("Saved restore records",fontSize=16.sp)
                ChoiceButton("Compact completed restore records…",outlined=false,enabled=!busy){compactingCompleted=true}
                Text("${data.optInt("active_maps")} active · ${data.optInt("archived_maps")} archived",fontSize=13.sp)
                val maps=data.getJSONArray("maps")
                if(data.getInt("total")==0)Text("No saved restore records.",fontSize=14.sp)
                repeat(maps.length()){i->val row=maps.getJSONObject(i);val id=row.getString("id")
                    ChoiceButton("Restore ${id.take(8)} · ${if(row.getBoolean("metadata_applied"))"Saved" else "Review"}",outlined=false,enabled=!busy){work{detail=withContext(Dispatchers.IO){HistoryRecovery.detail(c,id)}}}
                }
                if(data.getInt("total")>20){
                    Text("Records ${offset+1}–${offset+maps.length()} of ${data.getInt("total")}",fontSize=13.sp)
                    if(offset>0)ChoiceButton("Previous records",outlined=false,enabled=!busy){offset=(offset-20).coerceAtLeast(0);work{reload()}}
                    if(data.getBoolean("has_more"))ChoiceButton("Next records",outlined=false,enabled=!busy){offset+=20;work{reload()}}
                }
            }
            if(busy)Text("Working…",fontSize=14.sp)
            if(status.isNotEmpty())Text(status,fontSize=14.sp)
        }
    }
    if(compactingCompleted)ReferenceDialog("Compact completed restores?",{if(!busy)compactingCompleted=false},footer={
        DialogAction("Cancel",enabled=!busy){compactingCompleted=false}
        DialogAction("Compact",enabled=!busy){work{val result=withContext(Dispatchers.IO){HistoryRecovery.compactCompleted(c)};compactingCompleted=false;reload();status="${result.getInt("compacted")} completed restore records archived."}}
    }){Text("Keep completed restore summaries and duplicate-restore protection in a compact archive. Incomplete restores keep their full records for review. Messages, folders, flags and reminders remain unchanged.",Modifier.padding(20.dp),fontSize=14.sp,lineHeight=20.sp)}
    if(compacting)ReferenceDialog("Compact completed records?",{if(!busy)compacting=false},footer={
        DialogAction("Cancel",enabled=!busy){compacting=false}
        DialogAction("Compact",enabled=!busy){work{val result=withContext(Dispatchers.IO){HistoryRecovery.compact(c)};compacting=false;reload();status="${result.getInt("compacted")} completed journal records archived."}}
    }){Text("Keep completed insertion and cleanup receipts in a compact archive to free active journal slots. Duplicate-restore protection is retained. Interrupted inserts, saved restore maps, message metadata and phone messages remain unchanged.",Modifier.padding(20.dp),fontSize=14.sp,lineHeight=20.sp)}
    if(editing)ReferenceDialog("Local phone identities",{if(!busy)editing=false},footer={
        DialogAction("Cancel",enabled=!busy){editing=false}
        DialogAction("Save",enabled=!busy){work{config=withContext(Dispatchers.IO){HistoryRecovery.save(c,text)};editing=false;status="Local phone identities saved."}}
    }){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Enter your own numbers used on this phone and the phone that made the backup. Use one number per line, with the same country-code format as the backup. Do not enter other people's numbers.",fontSize=14.sp,lineHeight=20.sp)
        OutlinedTextField(text,{text=it},Modifier.fillMaxWidth().testTag("restore-local-identities"),label={Text("Your phone numbers")},minLines=3,maxLines=8,enabled=!busy)
        Text("Up to 10 identities. These numbers stay inside OnKlav and are used only to resolve MMS conversations during restoration. Saving them does not enable restoration.",fontSize=13.sp,lineHeight=18.sp)
    }}
    detail?.let{data->ReferenceDialog("Restore record",{if(!busy)detail=null},covered=reopening!=null,footer={DialogAction("Close",enabled=!busy){detail=null}}){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("${data.getInt("inserted")} Inserted · ${data.getInt("matched")} Already Present",fontSize=15.sp)
            Text("${data.getInt("pending")} Pending · ${data.getInt("review")} Need Review · ${data.getInt("skipped")} Skipped",fontSize=14.sp)
            Text(if(data.getBoolean("metadata_applied"))"Metadata was saved. Existing entries were kept." else "Metadata has not been applied for this record. Keep the record for review.",fontSize=14.sp,lineHeight=20.sp)
            if(data.getBoolean("metadata_applied"))Text("${data.getInt("flags_imported")} Message Flags · ${data.getInt("threads_imported")} Folders imported",fontSize=14.sp)
            if(data.getBoolean("ack_pending")){
                Text("The metadata was saved, but its outcome record needs to be finished. This does not repeat the restore.",fontSize=14.sp,lineHeight=20.sp)
                ChoiceButton("Finish saved record",outlined=false,enabled=!busy){work{withContext(Dispatchers.IO){HistoryRecovery.repair(c,data.getString("id"))};detail=withContext(Dispatchers.IO){HistoryRecovery.detail(c,data.getString("id"))};reload();status="Saved restore record updated."}}
            }
            if(!data.getBoolean("metadata_applied"))ChoiceButton("Reopen original backup",outlined=false,enabled=!busy){work{reopening=withContext(Dispatchers.IO){HistoryResume.binding(c,data.getString("id"))}}}
            if(data.optBoolean("compacted"))Text("Archived completion summary. Duplicate-restore protection is retained.",fontSize=13.sp)
            Text("This screen does not retry message insertion or reapply metadata.",fontSize=13.sp)
        }
    }}
    checkingRemoved?.let{id->ReferenceDialog("Check removed message?",{if(!busy)checkingRemoved=null},footer={
        DialogAction("Cancel",enabled=!busy){checkingRemoved=null}
        DialogAction("Check",enabled=!busy&&MessagingTransport.carrierEnabled(c)){work{withContext(Dispatchers.IO){HistoryRecovery.confirmRemovedChecked(c,id)};checkingRemoved=null;reload();status="Recorded message and parts verified absent. Review evidence retained. No restore was retried."}}
    }){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("If you already removed this incomplete message, OnKlav can check its recorded message ID and parts. A message that is still present or an unknown insert result cannot be resolved this way. Nothing is deleted or restored by this check.",fontSize=14.sp,lineHeight=20.sp)
        if(!MessagingTransport.carrierEnabled(c))Text("Phone checks are unavailable until restore verification is complete.",fontSize=13.sp)
    }}}
    cleanup?.let{id->ReferenceDialog("Remove incomplete insert?",{if(!busy)cleanup=null},footer={
        DialogAction("Cancel",enabled=!busy){cleanup=null}
        DialogAction("Remove",destructive=true,enabled=!busy&&MessagingTransport.carrierEnabled(c)){work{withContext(Dispatchers.IO){HistoryRecovery.cleanupChecked(c,id)};cleanup=null;reload()}}
    }){Text("Remove only the incomplete message recorded by this operation? OnKlav will check ownership before removing its row and parts. Committed messages remain.",Modifier.padding(20.dp),fontSize=14.sp)}}
    reopening?.let{saved->HistoryResumeDialog(saved){reopening=null;work{detail=withContext(Dispatchers.IO){HistoryRecovery.detail(c,saved.id)};reload()}}}
    ErrorDialog(error){error=null}
}
