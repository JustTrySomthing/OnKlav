package app.onklav.home

import android.content.*
import android.database.*
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.io.*
import java.util.UUID

/** Adapts the SAME signed Files controller and Rust content broker used by Files.
 * The picker never parses/rewrites the shared content index or grants SDK paths. */
internal class FileSelectionSession(private val c:Context):AutoCloseable {
 private var session=""
 var result=JSONObject();private set
 val state get()=result.child("frame").child("state")
 private val external=ExternalFiles(c,"app.onklav.files")
 private fun call(q:JSONObject)=AppSdk.sync(c,q)as JSONObject
 fun start(){check(session.isEmpty());result=call(JSONObject().put("action","launch").put("id","app.onklav.files"));session=result.getString("session");settle()}
 fun authorize(p:JSONObject)=call(JSONObject().put("action","authorize").put("session",session).put("token",p.getString("token")))
 private fun settle(){repeat(4){val p=result.optJSONObject("pending")?:return;val a=authorize(p);check(a.optString("service")=="library.external"&&a.child("args").optString("operation")=="list"){"Complete or cancel the current file action"};val rows=external.run(a.child("args")){authorize(p)};result=call(JSONObject().put("action","reply").put("session",session).put("token",p.getString("token")).put("reply",JSONObject().put("ok",true).put("value",rows)))};error("File listing did not settle")}
 fun event(action:String,value:String="",complete:Boolean=true):JSONObject{result=call(JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action",action).put("value",value)));if(complete)settle();val status=state.optString("status");check(status.isBlank()||(action=="copy"&&status.startsWith("Copied.")&&state.child("clipboard").optString("id").isNotBlank())){status};return result}
 fun cancelPending(message:String){result.optJSONObject("pending")?.let{p->runCatching{result=call(JSONObject().put("action","reply").put("session",session).put("token",p.getString("token")).put("reply",JSONObject().put("ok",false).put("error",message)))}};runCatching{event("menu.close")}}
 fun navigate(path:List<String>){event("home");for(id in path){event("query");var found=false;do{if(state.nodes("items").any{it.optString("id")==id}){found=true;break};if(state.optInt("offset")+40>=state.optInt("total"))break;event("next")}while(true);check(found){"This folder moved or is unavailable. Choose it again."};event("entry:$id")}}
 fun breadcrumbs():List<String>{val ids=mutableListOf<String>();repeat(32){val folder=state.optString("folder");if(folder.isEmpty()){val path=ids.asReversed();navigate(path);return path};ids+=folder;event("up")};error("Folder nesting is too deep")}
 fun chooseAt(path:List<String>,row:JSONObject):JSONObject{navigate(path);while(state.nodes("items").none{it.optString("id")==row.optString("id")}){check(state.optInt("offset")+40<state.optInt("total")){"File moved or is unavailable"};event("next")};return choose(row)}
 fun choose(row:JSONObject):JSONObject{
  check(!state.optBoolean("in_trash")&&state.optString("folder") !in setOf("trash","android-trash")&&!row.optBoolean("trash")){"Restore this file from Trash before selecting it."}
  val p=event("entry:"+row.getString("id"),complete=false).getJSONObject("pending");val a=authorize(p)
  val descriptor=if(a.getString("service")=="library.external")external.run(a.child("args")){authorize(p)}else call(JSONObject().put("action","library_host").put("session",session).put("token",p.getString("token")).put("operation","resolve"))
  result=call(JSONObject().put("action","reply").put("session",session).put("token",p.getString("token")).put("reply",JSONObject().put("ok",true).put("value",JSONObject())));settle();return descriptor
 }
 fun importSaved(path:List<String>,file:File,name:String,mime:String):JSONObject{
  navigate(path);check(!state.optBoolean("external_mode")&&state.optString("folder") !in setOf("","system","trash","external"))
  val p=event("import",complete=false).getJSONObject("pending");authorize(p)
  return runBlocking{AppSdk.staging.withLock{
   val staging=File(c.filesDir,"content-import.tmp")
   try{file.inputStream().use{input->staging.outputStream().use{FilesBroker.copyBoundedLimit(input,it,if(mime.startsWith("audio/")||mime.startsWith("video/"))2L*1024*1024*1024 else FilesBroker.MAX_FILE){authorize(p)}}};call(JSONObject().put("action","library_host").put("session",session).put("token",p.getString("token")).put("operation","import").put("name",name).put("mime",mime))}finally{staging.delete()}
  }}
 }
 fun connect(uri:Uri){val p=event("external.connect",complete=false).getJSONObject("pending");val saved=external.connect(uri){authorize(p)};result=call(JSONObject().put("action","reply").put("session",session).put("token",p.getString("token")).put("reply",JSONObject().put("ok",true).put("value",saved)));settle()}
 fun createExternal(name:String,mime:String):Uri {
  check(state.optBoolean("external_mode"));val folder=state.getString("folder");val p=event("refresh",complete=false).getJSONObject("pending");authorize(p)
  return external.createDirect(folder,name){authorize(p)}?:android.provider.DocumentsContract.createDocument(c.contentResolver,external.folderDestination(folder){authorize(p)},mime,name)?:error("This Android folder cannot create files")
 }
 fun externalDestination():Uri{check(state.optBoolean("external_mode")&&state.optString("folder")!="external");val folder=state.getString("folder");val p=event("refresh",complete=false).getJSONObject("pending");authorize(p);return external.folderDestination(folder){authorize(p)}}
 override fun close(){if(session.isNotEmpty()){val token=session;session="";AppSdk.releaseSession(c,token)}}
}

internal object FileSelectionStore {
 private val lock=Any()
 private val writing=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
 private val worker=HandlerThread("OnKlav file commits").apply{start()}
 val handler=Handler(worker.looper)
 private fun directory(c:Context):File{val f=File(c.filesDir,"file-selections");check(!java.nio.file.Files.isSymbolicLink(f.toPath()));check(f.mkdirs()||f.isDirectory);return f}
 private fun metadata(c:Context,id:String):File{require(id.matches(Regex("[a-f0-9]{32}")));val dir=directory(c);val f=File(dir,"$id.json");check(!java.nio.file.Files.isSymbolicLink(f.toPath())&&f.canonicalFile.parentFile==dir.canonicalFile);return f}
 fun read(c:Context,uri:Uri):JSONObject=AppSdk.carrierEgress{synchronized(lock){require(uri.authority==c.packageName+".file-selection"&&uri.pathSegments.size==1);metadata(c,uri.lastPathSegment!!).let{check(it.length()<=65536);JSONObject(it.readText()).also{row->
 val entry=row.optString("entry");if(entry.isNotEmpty())AppSdk.folderVault(c,"access",entry)
 row.optJSONArray("folders")?.let{folders->for(i in 0 until folders.length())AppSdk.folderVault(c,"access",folders.getString(i))}
 }}}}
 private fun write(c:Context,id:String,row:JSONObject){val target=metadata(c,id);val tmp=File(target.parentFile,"$id.tmp");tmp.writeText(row.toString());FileOutputStream(tmp,true).use{it.fd.sync()};check(tmp.renameTo(target))}
 private fun create(c:Context,row:JSONObject):Uri=synchronized(lock){val id=UUID.randomUUID().toString().replace("-","");row.put("token",id);write(c,id,row);Uri.parse("content://${c.packageName}.file-selection/$id")}
 fun selected(c:Context,descriptor:JSONObject,path:List<String>?=null):Uri{if(descriptor.has("uri"))return Uri.parse(descriptor.getString("uri"));val f=File(descriptor.getString("path"));checkedContent(c,f);return create(c,JSONObject().put("kind","read").put("path",f.path).put("entry",descriptor.optString("id")).put("folders",path?.let{JSONArray(it)}?:JSONObject.NULL).put("name",descriptor.getString("name")).put("mime",app.onklav.shared.Mp3Codec.mime(descriptor.getString("mime"),descriptor.getString("name"))))}
 internal fun retarget(c:Context,uri:Uri,descriptor:JSONObject)=AppSdk.carrierEgress{synchronized(lock){
  val row=read(c,uri);check(row.optString("kind")=="read");val file=checkedContent(c,File(descriptor.getString("path")))
  row.put("path",file.path).put("entry",descriptor.getString("id")).put("name",descriptor.getString("name")).put("mime",descriptor.getString("mime"));write(c,row.getString("token"),row)
 }}
 fun folder(c:Context,path:List<String>,name:String)=create(c,JSONObject().put("kind","folder").put("folders",JSONArray(path)).put("name",name).put("mime","inode/directory"))
 fun save(c:Context,path:List<String>,name:String,mime:String):Uri{
  check(path.none{AppSdk.folderVault(c,"status",it).optBoolean("encrypted")}){"Save the file outside the encrypted folder, then move it into the unlocked folder in Files."}
  require(name.isNotBlank()&&name==FilesBroker.cleanName(name)&&name.length<=120){"Use a file name without slashes or leading dots"}
  val uri=create(c,JSONObject().put("kind","save").put("folders",JSONArray(path)).put("name",name).put("mime",mime).put("state","new"))
  val f=File(directory(c),"${uri.lastPathSegment}.data");check(f.createNewFile());return uri
 }
 internal fun pendingIn(c:Context,folder:String):Boolean=synchronized(lock){directory(c).listFiles().orEmpty().filter{it.extension=="json"}.any{file->runCatching{val row=JSONObject(file.readText());row.optString("kind")=="save"&&row.optString("state") in setOf("writing","recovery")&&row.optJSONArray("folders")?.let{a->(0 until a.length()).any{a.optString(it)==folder}}==true}.getOrDefault(true)}}
 fun paths(row:JSONObject)=row.getJSONArray("folders").let{a->List(a.length()){a.getString(it)}}
 private fun checkedContent(c:Context,f:File):File{
  val base=File(c.filesDir,"onklav-content").canonicalFile;val packages=File(c.filesDir,"app-sdk/packages").canonicalFile
  check(!java.nio.file.Files.isSymbolicLink(f.toPath())&&f.isFile)
  val real=f.canonicalFile;check(real.toPath().startsWith(base.toPath())||real.parentFile==packages&&real.name.matches(Regex("[a-f0-9]{64}\\.onklav-a"))){"This selected file is unavailable"};return real
 }
 fun file(c:Context,row:JSONObject):File=if(row.has("path"))checkedContent(c,File(row.getString("path")))else{
  val dir=directory(c);val f=File(dir,"${row.getString("token")}.data");check(!java.nio.file.Files.isSymbolicLink(f.toPath())&&f.canonicalFile.parentFile==dir.canonicalFile&&f.isFile);f
 }
 fun writable(c:Context,uri:Uri):File=AppSdk.carrierEgress{synchronized(lock){val row=read(c,uri);check(row.optString("kind")=="save"){"This file selection is read-only"};check(paths(row).none{AppSdk.folderVault(c,"status",it).optBoolean("encrypted")}){"Save outside the encrypted folder, then move the file into it in Files."};check(row.optString("kind")=="save"&&row.optString("state")=="new"){"This save destination has already been used"};row.put("state","writing");write(c,row.getString("token"),row);file(c,row).also{writing.add(row.getString("token"))}}}
 /** Persist failure before closing: do not rely only on the descriptor's error channel. */
 fun interrupted(c:Context,uri:Uri){
  if(uri.authority!=c.packageName+".file-selection")return
  AppSdk.carrierEgress{synchronized(lock){val row=read(c,uri)
   if(row.optString("kind")=="save"&&row.optString("state")=="writing"){
    row.put("write_failed",true);write(c,row.getString("token"),row)
   }
  }}
 }
 fun completed(c:Context,uri:Uri,error:IOException?,allowInterrupted:Boolean=false){
  val row=synchronized(lock){JSONObject(metadata(c,uri.lastPathSegment!!).readText())};if(row.optString("state")=="saved")return;val data=file(c,row)
  try{
   check(error==null&&(allowInterrupted||!row.optBoolean("write_failed"))){"Save was interrupted; the partial file is kept in Recovered saves"}
   val saved=FileSelectionSession(c).use{s->s.start();s.importSaved(paths(row),data,row.getString("name"),row.getString("mime"))}
   val target=File(c.filesDir,"onklav-content/objects/"+saved.getString("id"));checkedContent(c,target)
   synchronized(lock){row.put("path",target.path).put("name",saved.getString("name")).put("state","saved");write(c,row.getString("token"),row)}
   data.delete()
  }catch(e:Exception){synchronized(lock){row.put("state","recovery").put("error",e.message?:"Save could not be filed");write(c,row.getString("token"),row)};Handler(Looper.getMainLooper()).post{android.widget.Toast.makeText(c,"Save kept in Files · Recovered saves. "+row.optString("error"),android.widget.Toast.LENGTH_LONG).show()}}finally{writing.remove(row.getString("token"))}
 }
 fun recovered(c:Context):List<JSONObject> = AppSdk.carrierEgress{synchronized(lock){directory(c).listFiles().orEmpty().filter{it.extension=="json"}.mapNotNull{runCatching{JSONObject(it.readText())}.getOrNull()}.filter{it.optString("token") !in writing&&it.optString("kind")=="save"&&it.optString("state") in setOf("writing","recovery")&&runCatching{read(c,uri(c,it));file(c,it).isFile}.getOrDefault(false)}}
 }
 fun uri(c:Context,row:JSONObject)=Uri.parse("content://${c.packageName}.file-selection/${row.getString("token")}")
 fun folderEntries(c:Context,uri:Uri):Pair<String,List<Uri>>{
  val row=read(c,uri);require(row.getString("kind")=="folder");val files=mutableListOf<Uri>();var count=0
  FileSelectionSession(c).use{s->s.start();fun scan(path:List<String>,depth:Int){check(depth<=24&&++count<=5000){"Choose a smaller folder"};s.navigate(path);val rows=mutableListOf<JSONObject>();do{rows+=s.state.nodes("items");if(s.state.optInt("offset")+40>=s.state.optInt("total"))break;s.event("next")}while(true)
   for(item in rows){check(++count<=5000){"Choose a smaller folder"};if(item.optBoolean("folder"))scan(path+item.getString("id"),depth+1)else{files+=selected(c,s.chooseAt(path,item));}}
  };scan(paths(row),0)};return row.getString("name") to files
 }
}

/** Non-exported host provider. Only unguessable explicit selections are addressable. */
class FileSelectionProvider:ContentProvider(){
 override fun onCreate()=true
 override fun getType(uri:Uri)=FileSelectionStore.read(requireNotNull(context),uri).getString("mime")
 override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor{
  val c=requireNotNull(context);val row=FileSelectionStore.read(c,uri);val cols=projection?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
  return MatrixCursor(cols).apply{addRow(cols.map{when(it){OpenableColumns.DISPLAY_NAME->row.getString("name");OpenableColumns.SIZE->if(row.optString("kind")=="folder")0L else FileSelectionStore.file(c,row).length();else->null}})}
 }
 override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor{
  val c=requireNotNull(context);return if(mode=="r")ParcelFileDescriptor.open(FileSelectionStore.file(c,FileSelectionStore.read(c,uri)),ParcelFileDescriptor.MODE_READ_ONLY)
  else{require(mode in setOf("w","wt","rw","rwt"));val f=FileSelectionStore.writable(c,uri);ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE,FileSelectionStore.handler){error->FileSelectionStore.completed(c,uri,error)}}
 }
 override fun insert(uri:Uri,values:ContentValues?):Uri=throw UnsupportedOperationException()
 override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=throw UnsupportedOperationException()
 override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=throw UnsupportedOperationException()
}

/** Close with an error on failed/cancelled writes, then await Files' durable filing receipt. */
internal suspend fun writeExportBytes(c:Context,uri:Uri,write:suspend (OutputStream)->Unit){
 withContext(Dispatchers.IO){
  currentCoroutineContext().ensureActive()
  val limit=destinationExportLimit(c,uri)
  val descriptor=requireNotNull(c.contentResolver.openFileDescriptor(uri,"wt")){"Destination could not be opened"}
  val output=LimitedExportStream(ParcelFileDescriptor.AutoCloseOutputStream(descriptor),limit)
  try{write(output);currentCoroutineContext().ensureActive();output.flush();output.close()}
  catch(error:Throwable){runCatching{FileSelectionStore.interrupted(c,uri)};runCatching{descriptor.closeWithError("Export interrupted")};throw error}
  finally{runCatching{output.close()}}
 }
}
internal suspend fun writeFiledExport(c:Context,uri:Uri,write:suspend (OutputStream)->Unit){
 writeExportBytes(c,uri,write);awaitFiledExport(c,uri)
}
internal suspend fun awaitFiledExport(c:Context,uri:Uri){
 if(uri.authority!=c.packageName+".file-selection")return
 withContext(Dispatchers.IO){
  val end=SystemClock.elapsedRealtime()+20000
  while(true){val row=FileSelectionStore.read(c,uri)
   when(row.optString("state")){"saved"->return@withContext;"recovery"->error("Save kept in Files · Recovered saves. "+row.optString("error"))}
   check(SystemClock.elapsedRealtime()<end){"Save is still being filed. Check Files · Recovered saves before retrying."}
   delay(50)
  }
 }
}
