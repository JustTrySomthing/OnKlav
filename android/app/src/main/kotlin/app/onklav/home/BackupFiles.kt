package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import kotlinx.coroutines.*

/** Provider-neutral file-copy helpers for Backups. */
internal object BackupDocuments {
 internal fun sameDocument(source:Uri,destination:Uri):Boolean {
  if(source.buildUpon().clearQuery().fragment(null).build()==destination.buildUpon().clearQuery().fragment(null).build())return true
  if(source.authority!=destination.authority)return false
  val first=runCatching{DocumentsContract.getDocumentId(source)}.getOrNull()
  return first!=null&&first==runCatching{DocumentsContract.getDocumentId(destination)}.getOrNull()
 }
 suspend fun copy(c:Context,source:Uri,destination:Uri)=withContext(Dispatchers.IO){
  // Validate before opening or deleting anything, including tree/document aliases.
  require(source.scheme=="content"&&destination.scheme=="content"&&!sameDocument(source,destination)){"Choose a new destination file"}
  val job=currentCoroutineContext()
  c.contentResolver.openInputStream(source)?.use { input ->
   writeFiledExport(c,destination){output->FilesBroker.copyBounded(input,output){job.ensureActive()}}
  }?:error("The selected file could not be opened")
 }
}

@Composable internal fun BackupFilesScreen(exit:()->Unit){
 val c=LocalContext.current;val scope=rememberCoroutineScope();val prefs=remember{c.getSharedPreferences("backup-files",0)}
 var selected by rememberSaveable{mutableStateOf<String?>(null)};var name by rememberSaveable{mutableStateOf("File")};var mime by rememberSaveable{mutableStateOf("application/octet-stream")}
 var busy by remember{mutableStateOf(false)};var job by remember{mutableStateOf<Job?>(null)};var status by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
 var documents by remember{mutableStateOf(false)};var backup by remember{mutableStateOf(false)};var history by remember{mutableStateOf(false)};var latest by remember{mutableStateOf(prefs.getString("last_uri",null))}
 val destination=rememberLauncherForActivityResult(FileExportContract()){uri->
  val source=selected;selected=null
  if(uri!=null&&source!=null){busy=true;status="Saving $name…";job=scope.launch{
   try{BackupDocuments.copy(c,Uri.parse(source),uri);runCatching{c.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)};val remembered=withContext(Dispatchers.IO){prefs.edit().putString("last_uri",uri.toString()).putString("last_mime",mime).putString("last_name",name).commit()};latest=uri.toString();status=if(remembered)"Saved through OnKlav Files."else"File saved, but the last-saved shortcut could not be remembered."}
   catch(e:CancellationException){status="Copy stopped. Check the destination for a partial file.";throw e}
   catch(e:Exception){status="Copy did not complete. Check the destination before retrying.";error=e.message}
   finally{busy=false;job=null}
  }}
 }
 val picker=rememberLauncherForActivityResult(OnKlavFileContracts.OpenDocument()){uri->if(uri!=null){
  selected=uri.toString();runCatching{c.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
  busy=true;job=scope.launch{try{val info=withContext(Dispatchers.IO){MessageMedia.name(c,uri) to (c.contentResolver.getType(uri)?:"application/octet-stream")};name=info.first;mime=info.second;destination.launch(FileExportRequest(name,mime))}catch(e:CancellationException){selected=null;throw e}catch(e:Exception){selected=null;error=e.message}finally{busy=false;job=null}}
 }}
 BackHandler{if(busy)job?.cancel()else exit()}
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){
  HomeHeader("Backups",back={if(busy)job?.cancel()else exit()})
  Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max=760.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text("Save a file copy",fontSize=23.sp)
   Text("Choose a destination in OnKlav Files. To upload files online, open the Cloud Files app.",fontSize=15.sp,lineHeight=21.sp)
   ChoiceButton("Copy a file…",outlined=false,enabled=!busy){picker.launch(arrayOf("*/*"))}
   Text("Files up to 256 MB are streamed without loading the whole file into memory. This makes a local copy; Cloud Files handles online uploads separately.",fontSize=13.sp,lineHeight=18.sp)
   HorizontalDivider()
   Text("Backup and Restore",fontSize=22.sp)
   ChoiceButton("App documents backup & restore",outlined=false,enabled=!busy){documents=true}
   ChoiceButton("App metadata backup & restore",outlined=false,enabled=!busy){backup=true}
   ChoiceButton("SMS, MMS and media backup & restore",outlined=false,enabled=!busy){history=true}
   Text("Save or open backups through OnKlav Files. Message restoration requires a checked archive, comparison with phone history, explicit confirmation and OnKlav as the default messenger in carrier test mode.",fontSize=14.sp,lineHeight=20.sp)
   Text("Backups contain private data and are not encrypted. App metadata backups do not include SDK documents, credentials, pending sends or permissions.",fontSize=13.sp,lineHeight=18.sp)
   latest?.let{uri->ChoiceButton("Open last saved file",outlined=false,enabled=!busy){try{c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri),prefs.getString("last_mime","application/octet-stream")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}catch(e:Exception){error=e.message}}}
   if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());ChoiceButton("Stop",outlined=false){job?.cancel()}}
   if(status.isNotBlank())Text(status,fontSize=14.sp)
  }
 }}
 if(documents)LibraryBackupDialog("documents"){documents=false}
 if(backup)BackupSettingsDialog{backup=false}
 if(history)HistoryBackupDialog{history=false}
 error?.let{ErrorDialog(it){error=null}}
}
