package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/** Generic SDK file services; app UI and file operations remain guest controlled. */
@Composable internal fun SdkFilesEffects(model:SdkAppModel,app:String,openNote:((String)->Unit)?){
    val c=LocalContext.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    var media by remember{mutableStateOf<MessagePart?>(null)}
    media?.let{part->ConversationMediaViewer(listOf(ChatMedia(Message(0,"",0,false,parts=listOf(part)),part)),part.uri,JSONObject(),{_,_->},{media=null})}
    val scope=rememberCoroutineScope();val pending=model.pending
    var importing by rememberSaveable{mutableStateOf<String?>(null)}
    var connecting by rememberSaveable{mutableStateOf<String?>(null)}
    var exporting by rememberSaveable{mutableStateOf<String?>(null)}
    val external=remember(c,app){ExternalFiles(c,app)}
    val epoch by AppSdk.epoch.collectAsState()
    LaunchedEffect(epoch,app){media=null;model.refreshFiles()}
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME){scope.launch{model.refreshFiles()}}
    fun current(token:String?)=model.pending?.takeIf{it.optString("token")==token}
    suspend fun importFiles(p:JSONObject,uris:List<Uri>){
        if(uris.isEmpty()){model.reply(p,failure="No files were selected");return}
        require(uris.size<=20){"Import up to 20 files at a time"}
        var count=0
        try{
            for(uri in uris)AppSdk.staging.withLock{
                model.fileStatus="Importing file ${count+1} of ${uris.size}…"
                val metadata=FilesBroker.stage(c,uri){model.authorizeNow(p)}
                try{model.files(p,if(p.getString("service")=="library.external")"import_external"else"import",metadata);count++}
                finally{File(c.filesDir,"content-import.tmp").delete()}
            }
            model.reply(p,JSONObject().put("imported",count))
        }catch(e:CancellationException){throw e}
        catch(e:Exception){model.reply(p,failure=if(count>0)"$count file(s) imported. Remaining files were not copied: ${e.message}"else e.message?:"File import failed")}finally{model.fileStatus=null}
    }
    val importer=rememberLauncherForActivityResult(OnKlavFileContracts.OpenMultipleDocuments()){uris->val p=current(importing);importing=null;if(p!=null)scope.launch{try{importFiles(p,uris)}catch(e:CancellationException){throw e}catch(e:Exception){model.reply(p,failure=e.message?:"File import failed")}}}
    val connector=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->val p=current(connecting);connecting=null;if(p!=null)scope.launch{try{if(uri==null)model.reply(p,failure="No Android folder was selected")else{val result=withContext(Dispatchers.IO){external.connect(uri){model.authorizeNow(p)}};model.reply(p,result)}}catch(e:CancellationException){throw e}catch(e:Exception){model.reply(p,failure=e.message?:"Folder could not be connected")}}}
    suspend fun descriptor(p:JSONObject):JSONObject=if(p.getString("service")=="library.external")withContext(Dispatchers.IO){val op=model.authorizeNow(p).child("args");external.run(op){model.authorizeNow(p)}}else model.files(p,"resolve")
    suspend fun share(p:JSONObject,file:JSONObject){
        val uri=withContext(Dispatchers.IO){val job=currentCoroutineContext();FilesOpenProvider.stage(c,file){job.ensureActive();model.authorizeNow(p)}}
        model.authorize(p);val send=Intent(Intent.ACTION_SEND).setType(file.getString("mime")).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=android.content.ClipData.newRawUri(file.optString("name","File"),uri)}
        c.startActivity(Intent.createChooser(send,"Share "+file.optString("name","File")));model.reply(p)
    }
    val exporter=rememberLauncherForActivityResult(FileExportContract()){uri->val p=current(exporting);exporting=null;if(p!=null)scope.launch{
        try{model.fileStatus="Saving a copy…";if(uri==null)model.reply(p,failure="No destination was selected")else{
            require(uri.scheme=="content");val file=descriptor(p)
            withContext(Dispatchers.IO){val job=currentCoroutineContext();val check={job.ensureActive();model.authorizeNow(p);Unit};check()
                val input=if(file.has("path"))File(file.getString("path")).inputStream()else c.contentResolver.openInputStream(Uri.parse(file.getString("uri")))?:error("Source file is unavailable")
                input.use{source->writeFiledExport(c,uri){FilesBroker.copyBounded(source,it,check)}}
            };model.reply(p)
        }}catch(e:CancellationException){throw e}catch(e:Exception){model.reply(p,failure="Copy did not complete; check the Android destination before retrying. ${e.message}")}finally{model.fileStatus=null}
    }}
    LaunchedEffect(pending?.optString("token")){
        val p=pending?:return@LaunchedEffect
        if(!p.optString("service").startsWith("library.")||!model.launchPending(p.getString("token")))return@LaunchedEffect
        try{
            model.fileStatus="Reading files…"
            val operation=model.authorize(p);val args=operation.child("args")
            when(operation.getString("service")){
                "library.import"->{importing=p.getString("token");importer.launch(arrayOf("*/*"))}
                "library.open"->{val file=model.files(p,"resolve");if(file.optString("mime")=="application/vnd.onklav.write+json"){check(openNote!=null){"Write navigation is unavailable"};val id=file.getString("id");model.reply(p);focus.clearFocus(force=true);openNote("write:$id")}else if(file.optBoolean("note")){
                    val id=file.getString("id");check(openNote!=null){"Notes navigation is unavailable"};model.reply(p);focus.clearFocus(force=true);openNote(id)
                }else{val uri=withContext(Dispatchers.IO){val job=currentCoroutineContext();FilesOpenProvider.stage(c,file){job.ensureActive();model.authorizeNow(p)}};model.authorize(p);val mime=file.getString("mime");model.reply(p);focus.clearFocus(force=true);if(mime.startsWith("image/")||mime.startsWith("video/")||mime.startsWith("audio/"))media=MessagePart(uri.toString(),mime,file.optString("name","File"))else FilesBroker.open(c,uri,mime)}}
                "library.share"->{share(p,model.files(p,"resolve"))}
                "library.export"->{val file=model.files(p,"resolve");exporting=p.getString("token");exporter.launch(FileExportRequest(file.getString("name"),file.getString("mime")))}
                "library.external"->when(args.getString("operation")){
                    "connect"->{connecting=p.getString("token");val hint=model.fileConnectLocation;model.fileConnectLocation=null;connector.launch(hint)}
                    "browse"->{importing=p.getString("token");importer.launch(arrayOf("*/*"))}
                    "open"->{val file=withContext(Dispatchers.IO){val currentJob=currentCoroutineContext();external.run(args){currentJob.ensureActive();model.authorizeNow(p)}};model.authorize(p);val uri=Uri.parse(file.getString("uri"));val mime=file.optString("mime","application/octet-stream");model.reply(p);focus.clearFocus(force=true);if(mime.startsWith("image/")||mime.startsWith("video/")||mime.startsWith("audio/"))media=MessagePart(uri.toString(),mime,file.optString("name","File"))else FilesBroker.open(c,uri,mime)}
                    "share"->{val file=withContext(Dispatchers.IO){val job=currentCoroutineContext();external.run(args){job.ensureActive();model.authorizeNow(p)}};share(p,file)}
                    "export"->{val file=withContext(Dispatchers.IO){val currentJob=currentCoroutineContext();external.run(args){currentJob.ensureActive();model.authorizeNow(p)}};exporting=p.getString("token");exporter.launch(FileExportRequest(file.getString("name"),file.optString("mime","application/octet-stream")))}
                    else->{val result=withContext(Dispatchers.IO){val currentJob=currentCoroutineContext();external.run(args){currentJob.ensureActive();model.authorizeNow(p)}};model.reply(p,result)}
                }
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){model.reply(p,failure=e.message?:"File action could not be completed")}finally{model.fileStatus=null}
    }
}
