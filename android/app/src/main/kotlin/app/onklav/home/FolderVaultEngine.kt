package app.onklav.home

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Encrypted-at-rest folders with an authenticated temporary working tree.
 * Rust closes access before transactions; only a fully verified archive can
 * replace plaintext. Session recovery keys are destroyed after successful lock. */
internal class FolderVaultEngine(private val c:Context,private val recovery:FolderVaultRecovery=AndroidFolderVaultRecovery,private val changed:()->Unit={AppSdk.epoch.value++}){
 private data class Session(var header:FolderVaultCrypto.Header,val key:ByteArray)
 private val mutex=Mutex()
 private val sessions=ConcurrentHashMap<String,Session>()
 private fun core(action:String,id:String="",extra:JSONObject=JSONObject())=AppSdk.folderVault(c,action,id,extra)
 private fun id(value:String)=value.also{require(it.matches(Regex("[a-f0-9]{32}"))){"Invalid folder identity"}}
 private fun directory(relative:String):File{
  val file=File(c.filesDir,relative);check(file.mkdirs()||file.isDirectory);check(!Files.isSymbolicLink(file.toPath()));return file
 }
 private fun archive(folder:String)=AtomicFile(File(directory("onklav-content/vaults"),"${id(folder)}.bin"))
 private fun state(folder:String)=AtomicFile(File(directory("folder-vault-state"),"${id(folder)}.json"))
 private fun writeState(folder:String,value:JSONObject){val f=state(folder);val out=f.startWrite();try{out.write(value.toString().toByteArray());f.finishWrite(out)}catch(e:Exception){f.failWrite(out);throw e}}
 private fun readState(folder:String):JSONObject?{val f=state(folder);if(!f.baseFile.exists()&&!File(f.baseFile.path+".bak").exists())return null;return f.openRead().use{stream->val bytes=stream.readBytes();require(bytes.size<=65536);JSONObject(bytes.toString(Charsets.UTF_8))}}
 private fun header(value:JSONObject)=FolderVaultCrypto.readHeader(Base64.decode(value.getString("header"),Base64.NO_WRAP).inputStream())
 private fun putHeader(value:JSONObject,h:FolderVaultCrypto.Header)=value.put("header",Base64.encodeToString(FolderVaultCrypto.headerBytes(h),Base64.NO_WRAP))
 private fun currentHeader(folder:String)=archive(folder).openRead().use(FolderVaultCrypto::readHeader).also{require(it.folder==folder)}
 private fun needsCurrent(folder:String,value:JSONObject):Boolean=runCatching{currentHeader(folder).generation.contentEquals(header(value).generation)}.getOrDefault(false)
 private fun newState(folder:String,h:FolderVaultCrypto.Header,key:ByteArray,mode:String):JSONObject{
  val wrapped=recovery.wrap(folder,key);val value=putHeader(JSONObject().put("mode",mode).put("recovery",wrapped),h)
  try{writeState(folder,value)}catch(e:Exception){recovery.erase(folder,wrapped);throw e};return value
 }
 private fun clearState(folder:String,value:JSONObject){recovery.erase(folder,value.getJSONObject("recovery"));state(folder).delete()}
 private fun manageAllowed(){val rows=AppSdk.sync(c,JSONObject().put("action","list"))as JSONArray;check((0 until rows.length()).any{rows.getJSONObject(it).let{r->r.optBoolean("installed")&&r.child("manifest").optString("id")=="app.onklav.files"&&r.optJSONArray("grants")?.let{a->(0 until a.length()).any{a.optString(it)=="files.manage"}}==true}}){"Files access is off"}}
 private fun stage(folder:String):File{val dir=File(c.cacheDir,"folder-vault-stage/${id(folder)}-${UUID.randomUUID()}");check(dir.mkdirs());return dir}
 private fun deleteTree(file:File){if(!file.exists())return;Files.walkFileTree(file.toPath(),object:java.nio.file.SimpleFileVisitor<java.nio.file.Path>(){
  override fun visitFile(path:java.nio.file.Path,attrs:java.nio.file.attribute.BasicFileAttributes):java.nio.file.FileVisitResult{Files.delete(path);return java.nio.file.FileVisitResult.CONTINUE}
  override fun postVisitDirectory(path:java.nio.file.Path,error:IOException?):java.nio.file.FileVisitResult{if(error!=null)throw error;Files.delete(path);return java.nio.file.FileVisitResult.CONTINUE}
 })}
 private fun objectFile(value:String)=File(directory("onklav-content/objects"),id(value))
 private fun auxPaths(value:String):List<String>{val n=id(value);val uuid=UUID.fromString("${n.take(8)}-${n.substring(8,12)}-${n.substring(12,16)}-${n.substring(16,20)}-${n.substring(20)}")
  return listOf("write/documents/$uuid.json","write/documents/$uuid.json.bak","write/documents/$uuid.json.new","notes/$n.json","app-sdk/data/app.onklav.notes/notes/$n.json","onklav-content/notes/$n.json")
 }
 private fun auxFile(path:String,allowed:Set<String>):File{require(path in allowed){"Invalid protected recovery path"};val file=File(c.filesDir,path);check(file.canonicalFile.toPath().startsWith(c.filesDir.canonicalFile.toPath()));var current:File?=file;while(current!=null&&current!=c.filesDir){check(!Files.isSymbolicLink(current.toPath()));current=current.parentFile};return file}
 private fun safeSource(file:File){check(!Files.isSymbolicLink(file.toPath())&&file.isFile){"A folder file is unavailable"}}
 private fun capture(folder:String,action:String)=core(action,folder).also{require(it.getString("folder")==folder)}
 private fun prepareManifest(snapshot:JSONObject):JSONObject{
  val entries=snapshot.getJSONArray("entries");val aux=JSONArray();var total=0L
  for(i in 0 until entries.length()){
   val e=entries.getJSONObject(i);if(e.getBoolean("folder"))continue
   val source=objectFile(e.getString("id"));safeSource(source);check(source.length()==e.getLong("bytes")){"A file changed before protection"};total+=source.length()
   if(e.optString("mime")=="application/vnd.onklav.write+json")for(path in auxPaths(e.getString("id"))){val file=File(c.filesDir,path);if(file.exists()){safeSource(file);total+=file.length();aux.put(JSONObject().put("path",path).put("bytes",file.length()))}}
  }
  require(total<=240L*1024*1024){"Protect up to 240 MB of data in one folder"}
  return JSONObject(snapshot.toString()).put("aux",aux).also{require(it.toString().toByteArray().size<=900000){"This folder has too much recovery metadata; move some documents out before locking"}}
 }
 private fun sealArchive(folder:String,h:FolderVaultCrypto.Header,key:ByteArray,manifest:JSONObject){
  val file=archive(folder);val output=file.startWrite()
  try{
   FolderVaultCrypto.seal(output,h,key){encrypted->ZipOutputStream(encrypted).use{zip->
    fun entry(name:String,writer:(OutputStream)->Unit){zip.putNextEntry(ZipEntry(name));writer(zip);zip.closeEntry()}
    entry("manifest.json"){it.write(manifest.toString().toByteArray())}
    val entries=manifest.getJSONArray("entries");for(i in 0 until entries.length()){val e=entries.getJSONObject(i);if(!e.getBoolean("folder")){val f=objectFile(e.getString("id"));safeSource(f);entry("objects/${e.getString("id")}"){out->f.inputStream().use{FolderVaultCrypto.copy(it,out)}}}}
    val aux=manifest.getJSONArray("aux");for(i in 0 until aux.length()){val row=aux.getJSONObject(i);val f=File(c.filesDir,row.getString("path"));safeSource(f);entry("aux/$i"){out->f.inputStream().use{FolderVaultCrypto.copy(it,out)}}}
   }}
   file.finishWrite(output)
  }catch(e:Exception){file.failWrite(output);throw e}
 }
 /** Authentication completes before any object or document path is replaced. */
 private fun verified(folder:String,key:ByteArray,block:(File,JSONObject)->Unit){
  val dir=stage(folder)
  try{
   val plain=File(dir,"payload.zip");archive(folder).openRead().use{input->plain.outputStream().use{FolderVaultCrypto.decrypt(input,it,key,folder)}}
   ZipFile(plain).use{zip->
    val seen=mutableSetOf<String>();zip.entries().asSequence().forEach{entry->require(!entry.isDirectory&&seen.size<7001&&seen.add(entry.name)){"Invalid protected archive entry"}}
    val item=zip.getEntry("manifest.json")?:error("Protected folder metadata is missing");require(item.size in 1..900000)
    val manifest=zip.getInputStream(item).use{input->val bytes=ByteArrayOutputStream();FolderVaultCrypto.copy(input,bytes,900000);JSONObject(bytes.toString("UTF-8"))};require(manifest.getString("folder")==folder)
    val entries=manifest.getJSONArray("entries");require(entries.length() in 1..1000)
    var expanded=0L
    val expected=mutableSetOf("manifest.json");val allowedAux=mutableSetOf<String>()
    for(i in 0 until entries.length()){val e=entries.getJSONObject(i);val n=id(e.getString("id"));if(!e.getBoolean("folder")){val name="objects/$n";expected+=name;val source=zip.getEntry(name)?:error("Protected file is missing");require(source.size==e.getLong("bytes")&&source.size in 0..FolderVaultCrypto.MAX_BYTES);expanded+=source.size;require(expanded<=240L*1024*1024)};allowedAux.addAll(auxPaths(n))}
    val aux=manifest.getJSONArray("aux");require(aux.length()<=entries.length()*6)
    for(i in 0 until aux.length()){val a=aux.getJSONObject(i);auxFile(a.getString("path"),allowedAux);val name="aux/$i";expected+=name;val size=zip.getEntry(name)?.size?:-1;require(size==a.getLong("bytes")&&size in 0..FolderVaultCrypto.MAX_BYTES);expanded+=size;require(expanded<=240L*1024*1024)}
    require(expected==seen){"Unexpected protected archive contents"}
    core("validate_cleanup",folder,JSONObject().put("ids",manifest.getJSONArray("cleanup_ids")))
    block(plain,manifest)
   }
  }finally{deleteTree(dir)}
 }
 private fun cleanupIndexTemps(){File(c.filesDir,"onklav-content").listFiles().orEmpty().filter{it.name.matches(Regex("index\\.[a-f0-9]{32}\\.tmp"))}.forEach{check(!Files.isSymbolicLink(it.toPath()));check(it.delete())}}
 private fun cleanupObjectTemps(value:String){val n=id(value);val parents=listOf(File(c.filesDir,"onklav-content/objects"),File(c.filesDir,"onklav-content/notes"),File(c.filesDir,"notes"),File(c.filesDir,"app-sdk/data/app.onklav.notes/notes"));for(parent in parents)parent.listFiles().orEmpty().filter{it.name.matches(Regex("$n\\.[a-f0-9]{32}\\.tmp"))}.forEach{check(!Files.isSymbolicLink(it.toPath()));check(it.delete())}}
 private fun cleanupPlain(manifest:JSONObject){
  val entries=manifest.getJSONArray("entries");for(i in 0 until entries.length()){val e=entries.getJSONObject(i);if(!e.getBoolean("folder")){val file=objectFile(e.getString("id"));check(!file.exists()||file.delete()){ "Protected file cleanup is pending" }}}
  val cleanup=manifest.getJSONArray("cleanup_ids");for(i in 0 until cleanup.length())cleanupObjectTemps(cleanup.getString(i));for(i in 0 until cleanup.length())for(path in auxPaths(cleanup.getString(i))){val file=auxFile(path,auxPaths(cleanup.getString(i)).toSet());check(!file.exists()||file.delete()){ "Protected recovery cleanup is pending" }}
  // Existing one-file grants must not keep automatic plaintext export copies.
  val exports=File(c.cacheDir,"exports");if(exports.exists())deleteTree(exports)
 }
 private fun finishSeal(folder:String,key:ByteArray,value:JSONObject){
  verified(folder,key){_,manifest->cleanupPlain(manifest)}
  core("publish",folder);cleanupIndexTemps();clearState(folder,value);sessions.remove(folder)?.key?.fill(0);changed()
 }
 private suspend fun lockInside(folder:String,s:Session,value:JSONObject?=readState(folder)){
  core("close_reads",folder);changed();WritePendingSaves.finish()
  val snapshot=capture(folder,"freeze");val h=FolderVaultCrypto.nextPayload(s.header)
  val state=value?:newState(folder,h,s.key,"sealing");putHeader(state,h).put("mode","sealing");writeState(folder,state)
  val manifest=prepareManifest(snapshot);sealArchive(folder,h,s.key,manifest);finishSeal(folder,s.key,state)
 }
 suspend fun protect(folder:String,password:CharArray)=withContext(Dispatchers.IO){mutex.withLock{
  val n=id(folder);manageAllowed();check(!FileSelectionStore.pendingIn(c,n)){"Finish or recover pending saves in this folder before encrypting it"};check(core("status",n).getBoolean("protectable")){"Choose a user-created folder outside other encrypted folders"};WritePendingSaves.finish()
  val (h,key)=FolderVaultCrypto.create(n,password)
  var value:JSONObject?=null
  try{
   // An unreferenced encrypted archive is not the current folder contents.
   archive(n).delete();value=newState(n,h,key,"sealing");val manifest=prepareManifest(capture(n,"prepare"));sealArchive(n,h,key,manifest);finishSeal(n,key,value)
  }catch(e:Exception){if(value!=null&&!archive(n).baseFile.exists()){runCatching{if(core("status",n).optBoolean("encrypted"))core("rollback",n);clearState(n,value)}};changed();throw e}finally{key.fill(0)}
 }}
 private fun restoreObjects(folder:String,key:ByteArray,value:JSONObject){
  verified(folder,key){plain,manifest->
   core("restore_entries",folder,JSONObject().put("entries",manifest.getJSONArray("entries")))
   ZipFile(plain).use{zip->
    fun publish(name:String,target:File,bytes:Long){target.parentFile!!.mkdirs();check(!Files.isSymbolicLink(target.toPath()));val atomic=AtomicFile(target);val out=atomic.startWrite();try{zip.getInputStream(zip.getEntry(name)).use{check(FolderVaultCrypto.copy(it,out,bytes)==bytes){"Protected file size changed"}};atomic.finishWrite(out)}catch(e:Exception){atomic.failWrite(out);throw e}}
    val entries=manifest.getJSONArray("entries");for(i in 0 until entries.length()){val e=entries.getJSONObject(i);if(!e.getBoolean("folder"))publish("objects/${e.getString("id")}",objectFile(e.getString("id")),e.getLong("bytes"))}
    val aux=manifest.getJSONArray("aux");val allowed=(0 until entries.length()).flatMap{auxPaths(entries.getJSONObject(it).getString("id"))}.toSet()
    for(i in 0 until aux.length())publish("aux/$i",auxFile(aux.getJSONObject(i).getString("path"),allowed),aux.getJSONObject(i).getLong("bytes"))
   }
  }
  core("open",folder);value.put("mode","open");writeState(folder,value)
 }
 private suspend fun unlockInside(folder:String,password:CharArray){
  var old=readState(folder)
  val h=if(old!=null&&(old.optString("mode")=="open"||old.optString("mode")=="sealing"&&!needsCurrent(folder,old)))header(old)else currentHeader(folder)
  val key=FolderVaultCrypto.unlock(h,password)
  try{
   if(old!=null){
    // No replacement archive was committed: the working tree is still the
    // newest copy. Correct-password recovery reopens it so users can resolve
    // capacity/storage failures instead of trapping their edits behind a lock.
    if(old.optString("mode")=="open"||old.optString("mode")=="sealing"&&!needsCurrent(folder,old)){
     core("open",folder);old.put("mode","open");writeState(folder,old);sessions[folder]=Session(h,key.copyOf());changed();return
    }
    when(old.getString("mode")){
     "open"->lockInside(folder,Session(h,key),old)
     "sealing"->if(needsCurrent(folder,old))finishSeal(folder,key,old)else lockInside(folder,Session(header(old),key),old)
     "opening"->{verified(folder,key){_,m->cleanupPlain(m)};core("publish",folder);cleanupIndexTemps();clearState(folder,old)}
     else->error("Folder recovery needs attention")
    }
    old=null
   }
   val latest=currentHeader(folder);val value=newState(folder,latest,key,"opening")
   restoreObjects(folder,key,value);sessions[folder]=Session(latest,key.copyOf());changed()
  }catch(e:Exception){runCatching{core("freeze",folder)};changed();throw e}finally{key.fill(0)}
 }
 suspend fun unlock(folder:String,password:CharArray)=withContext(Dispatchers.IO){mutex.withLock{manageAllowed();unlockInside(id(folder),password)}}
 suspend fun lock(folder:String)=withContext(Dispatchers.IO){mutex.withLock{val n=id(folder);val s=sessions[n]?:error("Enter the folder password to finish locking");lockInside(n,s)}}
 suspend fun changePassword(folder:String,old:CharArray,password:CharArray)=withContext(Dispatchers.IO){mutex.withLock{
  manageAllowed();val n=id(folder);sessions[n]?.let{lockInside(n,it)};check(readState(n)==null){"Unlock this folder to finish recovery before changing its password"};val h=currentHeader(n);val key=FolderVaultCrypto.unlock(h,old)
  try{
   val replacement=FolderVaultCrypto.changePassword(h,key,password);val atomic=archive(n);val tmp=File(directory("folder-vault-state"),"$n.rekey")
   try{atomic.openRead().use{input->tmp.outputStream().use{FolderVaultCrypto.replaceHeader(input,it,replacement)}};val out=atomic.startWrite();try{tmp.inputStream().use{FolderVaultCrypto.copy(it,out)};atomic.finishWrite(out)}catch(e:Exception){atomic.failWrite(out);throw e}}finally{tmp.delete()}
   sessions[n]?.header=replacement;readState(n)?.let{putHeader(it,replacement);writeState(n,it)};changed()
  }finally{key.fill(0)}
 }}
 suspend fun removeProtection(folder:String,password:CharArray)=withContext(Dispatchers.IO){mutex.withLock{
  manageAllowed();val n=id(folder);if(sessions[n]==null)unlockInside(n,password)else{val check=FolderVaultCrypto.unlock(currentHeader(n),password);check.fill(0)}
  WritePendingSaves.finish();core("remove",n);readState(n)?.let{clearState(n,it)};archive(n).delete();sessions.remove(n)?.key?.fill(0);changed()
 }}
 suspend fun recoverAndLock():List<String> =withContext(Dispatchers.IO){mutex.withLock{
  val staging=File(c.cacheDir,"folder-vault-stage");if(staging.exists())deleteTree(staging)
  val failures=mutableListOf<String>();val rows=core("all").getJSONArray("folders")
  for(i in 0 until rows.length()){val n=rows.getJSONObject(i).getString("id");val value=readState(n)?:continue
   var key:ByteArray?=null
   try{
    key=recovery.unwrap(n,value.getJSONObject("recovery"))
    when(value.getString("mode")){
     "open"->lockInside(n,Session(runCatching{currentHeader(n)}.getOrElse{header(value)},key),value)
     "sealing"->if(needsCurrent(n,value))finishSeal(n,key,value)else lockInside(n,Session(header(value),key),value)
     "opening"->{verified(n,key){_,m->cleanupPlain(m)};core("publish",n);cleanupIndexTemps();clearState(n,value)}
    }
   }catch(e:Exception){failures+=n}finally{key?.fill(0)}
  }
  failures
 }}
 suspend fun lockOpenFolders(){mutex.withLock{for((folder,s)in sessions.toMap())try{lockInside(folder,s)}catch(e:Exception){core("close_reads",folder);changed()}}}
}

internal object FolderVaults{
 private val engines=ConcurrentHashMap<String,FolderVaultEngine>()
 private val worker=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 fun engine(c:Context)=engines.getOrPut(c.filesDir.absolutePath){FolderVaultEngine(c.applicationContext)}
 fun background(c:Context){
  runCatching{val rows=AppSdk.folderVault(c,"all").getJSONArray("folders");for(i in 0 until rows.length()){val row=rows.getJSONObject(i);if(row.optBoolean("open"))AppSdk.folderVault(c,"close_reads",row.getString("id"))};AppSdk.epoch.value++}
  worker.launch{engine(c).lockOpenFolders()}
 }
}
