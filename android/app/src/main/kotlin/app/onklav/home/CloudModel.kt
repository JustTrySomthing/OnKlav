package app.onklav.home

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import androidx.lifecycle.*
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import java.io.File

internal data class CloudUpload(val uri:Uri,val name:String,val size:Long)
internal class CloudModel(app:Application,private val store:CloudStore=CloudStore(app),private val factory:(CloudAccount)->CloudDav={CloudDav(it)}):AndroidViewModel(app){
 constructor(app:Application):this(app,CloudStore(app))
 var accounts by mutableStateOf<List<CloudAccount>>(emptyList());private set
 var account by mutableStateOf<CloudAccount?>(null);private set
 var path by mutableStateOf<HttpUrl?>(null);private set
 var listing by mutableStateOf(CloudListing(emptyList()));private set
 var busy by mutableStateOf(false);private set
 var phase by mutableStateOf("");private set
 var error by mutableStateOf("");private set
 var status by mutableStateOf("");private set
 var bytes by mutableLongStateOf(0);private set
 var total by mutableLongStateOf(-1);private set
 var upload by mutableStateOf<CloudUpload?>(null);private set
 var staged by mutableStateOf<File?>(null);private set
 var stagedEntry by mutableStateOf<CloudEntry?>(null);private set
 var loaded by mutableStateOf(false);private set
 var sharedLink by mutableStateOf<String?>(null);private set
 private var sharing:CloudSharing?=null
 fun clearShare(){sharedLink=null}
 fun share(e:CloudEntry){val a=account?:return;work("Creating sharing link"){check(withContext(Dispatchers.IO){store.load().any{it==a}}){"This cloud account changed. Reopen it before sharing."};currentCoroutineContext().ensureActive();val client=CloudSharing(a);sharing=client;val link=client.create(e);withContext(Dispatchers.Main){sharedLink=link}}}
 private var downloadAccount:CloudAccount?=null
 private var unlocked=false;private var task:Job?=null;private var dav:CloudDav?=null
 private val progressAt=java.util.concurrent.atomic.AtomicLong(0)
 private val handler=android.os.Handler(android.os.Looper.getMainLooper())
 init{start()}
 fun start(){if(unlocked||busy)return;work("Opening cloud accounts"){val saved=withContext(Dispatchers.IO){File(getApplication<Application>().cacheDir,"cloud-files").listFiles()?.filter{it.isFile&&it.name.endsWith(".part")&&System.currentTimeMillis()-it.lastModified()>24*60*60*1000L}?.forEach{it.delete()};store.load()};withContext(Dispatchers.Main){accounts=saved;unlocked=true;loaded=true};if(saved.isNotEmpty())openWithin(saved.first())}}
 private fun work(label:String,block:suspend()->Unit){if(busy)return;busy=true;phase=label;error="";status="";bytes=0;total=-1;task=viewModelScope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){error=when(e){is javax.crypto.AEADBadTagException->"Saved cloud accounts could not be unlocked. Existing data was kept.";is java.io.IOException->e.message?.takeIf{it.startsWith("Could not reach cloud storage")}?:"Connection interrupted. Check your network and retry.";else->e.message?.takeIf{it.length<=260&&!it.contains("https://")&&!it.contains("Authorization",true)}?:"Cloud Files could not complete this action."}}finally{sharing?.cancel();sharing=null;dav?.cancel();dav=null;busy=false;phase="";loaded=true}}}
 private suspend fun openWithin(a:CloudAccount,folder:HttpUrl=CloudPaths.base(a.endpoint)){if(account?.id!=a.id||path==null)withContext(Dispatchers.Main){account=a;path=folder;listing=CloudListing(emptyList())};val client=factory(a);dav=client;val result=client.list(folder);withContext(Dispatchers.Main){account=a;path=folder;listing=result}}
 fun open(a:CloudAccount){work("Loading files"){openWithin(a)}}
 fun refresh(){val a=account?:return;val p=path?:return;work("Refreshing"){openWithin(a,p)}}
 fun enter(e:CloudEntry){if(!e.folder)return;val a=account?:return;work("Opening folder"){openWithin(a,e.url)}}
 fun up(){val a=account?:return;val p=path?:return;val root=CloudPaths.base(a.endpoint);if(p==root)return;val segments=p.pathSegments.filter{it.isNotEmpty()}.dropLast(1);val u=p.newBuilder().encodedPath("/").apply{segments.forEach{addPathSegment(it)};addPathSegment("")}.build();if(CloudPaths.safe(root,u))work("Opening parent folder"){openWithin(a,u)}}
 fun connect(a:CloudAccount,done:()->Unit){if(!unlocked){error="Saved accounts are not available. Existing data has been kept.";return};work("Checking sign-in"){
  a.validate();val result=factory(a).also{dav=it}.list();currentCoroutineContext().ensureActive()
  // Once the short local commit begins, acknowledge it in memory even if Cancel
  // arrives during IO. Future changes always merge against the latest disk list.
  withContext(NonCancellable){val next=withContext(Dispatchers.IO){store.update{it.filterNot{old->old.id==a.id}+a}}
   withContext(Dispatchers.Main){accounts=next;account=a;path=CloudPaths.base(a.endpoint);listing=result;status="Connected to ${a.name}";done()}
  }
 }}
 fun disconnect(a:CloudAccount){if(!unlocked)return;work("Disconnecting"){
  currentCoroutineContext().ensureActive()
  withContext(NonCancellable){val next=withContext(Dispatchers.IO){store.update{it.filterNot{old->old.id==a.id}}}
   withContext(Dispatchers.Main){accounts=next;if(account?.id==a.id){account=null;path=null;listing=CloudListing(emptyList())};status="Disconnected. Your online files were kept."}
  }
  currentCoroutineContext().ensureActive();if(account==null&&accounts.isNotEmpty())openWithin(accounts.first())
 }}
 fun prepare(uri:Uri){work("Reading selected file"){require(uri.scheme=="content"){"Choose a file in OnKlav Files."};val selected=withContext(Dispatchers.IO){var name="File";var size=-1L;getApplication<Application>().contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use{q->if(q.moveToFirst()){val n=q.getColumnIndex(OpenableColumns.DISPLAY_NAME);val s=q.getColumnIndex(OpenableColumns.SIZE);if(n>=0)name=q.getString(n).orEmpty();if(s>=0&&!q.isNull(s))size=q.getLong(s)}};require(size<=CloudDav.MAX_TRANSFER){"Files up to 2 GB are supported."};CloudUpload(uri,FilesBroker.cleanName(name),size)};withContext(Dispatchers.Main){upload=selected}}}
 fun dismissUpload(){upload=null}
 private fun report(done:Long,length:Long,owner:Job?){val now=android.os.SystemClock.elapsedRealtime();val prior=progressAt.get();if(done!=length&&now-prior<120)return;progressAt.set(now);handler.post{if(task===owner&&owner?.isActive==true){bytes=done;total=length}}}
 fun send(name:String){val selected=upload?:return;val a=account?:return;val p=path?:return;work("Uploading"){val clean=CloudPaths.name(name);withContext(Dispatchers.Main){upload=selected.copy(name=clean)};val own=currentCoroutineContext()[Job];val client=factory(a);dav=client;client.upload(p,clean,selected.size,{getApplication<Application>().contentResolver.openInputStream(selected.uri)?:error("The selected file is unavailable. Choose it again.")}){n->report(n,selected.size,own)};withContext(Dispatchers.Main){upload=null;status="Uploaded $clean. Refresh to update the folder."};val result=client.list(p);withContext(Dispatchers.Main){listing=result;status="Uploaded $clean"}}}
 fun mkdir(name:String){val a=account?:return;val p=path?:return;work("Creating folder"){val client=factory(a);dav=client;client.mkdir(p,CloudPaths.name(name));withContext(Dispatchers.Main){status="Folder created. Refresh to update the folder."};val result=client.list(p);withContext(Dispatchers.Main){listing=result;status="Folder created"}}}
 /** Choose the destination before doing a network transfer, so its limits are known. */
 fun download(e:CloudEntry,ready:()->Unit){if(busy)return;val a=account?:return;clearStaged();stagedEntry=e;downloadAccount=a;ready()}
 fun export(uri:Uri){val e=stagedEntry?:return;val a=downloadAccount
  work("Saving to Files"){
   preflightFileExport(getApplication(),uri,cloudMime(e.name),staged?.length()?:e.size?:-1)
   val f=staged?:run{
    check(a!=null&&accounts.any{it==a}){"This cloud account changed. Select the file again."}
    val dir=File(getApplication<Application>().cacheDir,"cloud-files").apply{mkdirs()};val file=File(dir,java.util.UUID.randomUUID().toString()+".part")
    phase="Downloading";val client=factory(a);dav=client;val own=currentCoroutineContext()[Job]
    client.download(e,file,destinationExportLimit(getApplication(),uri)){n,length->report(n,length,own)}
    staged=file;file
   }
   phase="Saving to Files"
   writeFiledExport(getApplication(),uri){out->f.inputStream().use{input->val job=currentCoroutineContext();FilesBroker.copyBoundedLimit(input,out,CloudDav.MAX_TRANSFER){job.ensureActive()}}}
   status="Saved ${e.name} to Files";clearStaged()
  }
 }
 fun clearStaged(){staged?.delete();staged=null;stagedEntry=null;downloadAccount=null}
 fun stop(){sharing?.cancel();dav?.cancel();task?.cancel();if(busy){status=if(phase=="Uploading")"Upload stopped. Refresh before retrying; the provider may retain a partial file."else"Transfer stopped."};upload=null;clearStaged()}
 override fun onCleared(){sharing?.cancel();dav?.cancel();clearStaged();super.onCleared()}
}
