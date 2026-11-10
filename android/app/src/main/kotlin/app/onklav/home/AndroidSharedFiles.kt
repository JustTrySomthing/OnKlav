package app.onklav.home

import android.content.*
import android.database.*
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import android.provider.Settings
import org.json.*
import java.io.File
import java.security.MessageDigest

internal object AndroidFilesAccess {
 fun allowed(c:Context)=if(Build.VERSION.SDK_INT>=30)Environment.isExternalStorageManager()else OnKlavPermissions.granted(c,android.Manifest.permission.READ_EXTERNAL_STORAGE)&&OnKlavPermissions.granted(c,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
 fun request(c:Context)=if(Build.VERSION.SDK_INT>=30)Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:${c.packageName}"))else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}"))
 fun root():File=Environment.getExternalStorageDirectory().canonicalFile
 fun checked(c:Context,relative:String,root:File=root()):File {
  check(allowed(c)){"Allow Android file access in OnKlav setup to browse your phone files."}
  require(!relative.startsWith('/')&&relative.split('/').none{it in setOf(".","..")||it.startsWith('.')&&it.isNotEmpty()}){"This Android location is unavailable."}
  val f=File(root,relative);var cursor=f
  while(cursor!=root){require(!java.nio.file.Files.isSymbolicLink(cursor.toPath())){"Linked folders are not supported."};cursor=cursor.parentFile?:error("Invalid Android location")}
  require(f.canonicalFile.toPath().startsWith(root.toPath())&&(relative!="Android"&&!relative.startsWith("Android/")||relative=="Android/media"||relative.startsWith("Android/media/"))){"Private Android app files are unavailable."}
  return f
 }
 fun uri(c:Context,relative:String)=Uri.Builder().scheme("content").authority(c.packageName+".android-files").apply{relative.split('/').forEach{appendPath(it)}}.build()
 fun mime(f:File)=app.onklav.shared.Mp3Codec.mime(android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())?:"application/octet-stream",f.name)
 /** Protect platform containers and app-owned directory trees, while keeping personal media editable. */
 fun protected(relative:String,folder:Boolean):Boolean{
  val parts=relative.split('/').filter{it.isNotEmpty()};if(parts.isEmpty())return true
  val standard=setOf("Android","DCIM","Pictures","Music","Movies","Download","Downloads","Documents","Alarms","Notifications","Ringtones","Podcasts","Audiobooks","Recordings","Samsung","Amazon","OnKlav Trash","LOST.DIR")
  if(parts.size==1&&folder&&(parts[0] in standard||parts[0].matches(Regex("(?:com|org|net)\\.[A-Za-z0-9_.]+"))))return true
  if(appManaged(relative))return true
  if(folder&&relative in setOf("DCIM/Camera","DCIM/Screenshots","Pictures/Screenshots"))return true
  if(parts[0]=="Android")return folder||parts.drop(1).any{it in setOf("data","obb")}||relative.substringAfterLast('.').lowercase() in setOf("db","sqlite","sqlite3","xml","json","ini","cfg","properties")
  return false
 }
 fun appManaged(relative:String):Boolean{
  val top=relative.substringBefore('/')
  return top in setOf("Samsung","Amazon","LOST.DIR")||top.matches(Regex("(?:com|org|net)\\.[A-Za-z0-9_.]+"))
 }
 val categories=listOf("audio" to "Audio","video" to "Video","images" to "Images","documents" to "Documents","downloads" to "Downloads","trash" to "Trash","other" to "Internal files")
 private val legacyCategories=listOf("photos" to "Photos","apk" to "APK")
 fun categoryLabel(key:String)=(categories+legacyCategories).firstOrNull{it.first==key}?.second
 fun roots()=(categories+legacyCategories).map{(key,label)->JSONObject().put("id","android-$key").put("parent","external").put("name",label).put("folder",true).put("editable",false).put("protected",true).put("detail",if(key=="photos")"Camera photos"else"Android $label")}
}

/** Shared-storage categories; opaque entry IDs stay inside each app's Files broker. */
internal class AndroidSharedFiles(private val c:Context){
 private fun inTrash(f:File,root:File)=f.toPath().startsWith(File(root,"OnKlav Trash").toPath())
 private fun originalName(f:File)=f.name.substringAfter("--",f.name)
 private fun destination(id:String,entries:JSONObject):JSONObject?=when(id){"android-downloads"->JSONObject().put("android_path","Download");"android-other"->JSONObject().put("android_path","");else->entries.optJSONObject(id)}
 fun create(folder:String,entries:JSONObject,name:String,authorize:()->Unit):Uri? {
  val row=destination(folder,entries)?:return null;if(!row.has("android_path"))return null
  require(name==FilesBroker.cleanName(name));authorize();val root=AndroidFilesAccess.root();val dir=AndroidFilesAccess.checked(c,row.getString("android_path"),root);check(dir.isDirectory&&!inTrash(dir,root)){"Choose an active Android folder."}
  val rel=dir.relativeTo(root).resolve(name).invariantSeparatorsPath;val target=AndroidFilesAccess.checked(c,rel,root);check(target.createNewFile()){"A file with this name already exists."};return AndroidFilesAccess.uri(c,rel)
 }
 private fun id(relative:String)=MessageDigest.getInstance("SHA-256").digest(relative.toByteArray()).take(16).joinToString(""){"%02x".format(it)}
 private fun zipped(source:File,target:File,authorize:()->Unit){
  var count=0;var bytes=0L;val root=AndroidFilesAccess.root()
  try{java.util.zip.ZipOutputStream(target.outputStream().buffered()).use{zip->
   fun add(f:File,name:String,depth:Int){authorize();check(depth<=24&&++count<=10000){"Choose a smaller folder to compress."};AndroidFilesAccess.checked(c,f.relativeTo(root).invariantSeparatorsPath,root);zip.putNextEntry(java.util.zip.ZipEntry(name+if(f.isDirectory)"/"else""))
    if(f.isDirectory){zip.closeEntry();for(child in f.listFiles()?:error("Folder is unreadable."))add(child,name+"/"+FilesBroker.cleanName(child.name),depth+1)}else{f.inputStream().use{input->val buffer=ByteArray(65536);while(true){authorize();val n=input.read(buffer);if(n<0)break;if(n==0)continue;bytes+=n;check(bytes<=FilesBroker.MAX_FILE){"Compress up to 256 MB at a time."};zip.write(buffer,0,n)}};zip.closeEntry()}
   };add(source,FilesBroker.cleanName(source.name),0)
  }}catch(e:Exception){target.delete();throw e}
 }
 private fun copy(source:File,target:File,authorize:()->Unit){
  val created=mutableListOf<File>();var count=0;var bytes=0L;val root=AndroidFilesAccess.root();check(!target.exists()&&!target.toPath().startsWith(source.toPath())){"Choose a different destination; existing files are kept."}
  fun transfer(f:File,out:File,depth:Int){authorize();check(depth<=24&&++count<=10000){"Choose a smaller folder to copy."};AndroidFilesAccess.checked(c,f.relativeTo(root).invariantSeparatorsPath,root);AndroidFilesAccess.checked(c,out.relativeTo(root).invariantSeparatorsPath,root)
   if(f.isDirectory){check(out.mkdir());created+=out;for(child in f.listFiles()?:error("Folder is unreadable."))transfer(child,File(out,child.name),depth+1)}else{check(out.createNewFile());created+=out;f.inputStream().use{input->out.outputStream().use{dest->val buffer=ByteArray(65536);while(true){authorize();val n=input.read(buffer);if(n<0)break;if(n==0)continue;bytes+=n;check(bytes<=FilesBroker.MAX_FILE){"Copy up to 256 MB at a time."};dest.write(buffer,0,n)}}}}
  }
  try{transfer(source,target,0)}catch(e:Exception){created.asReversed().forEach{owned->runCatching{AndroidFilesAccess.checked(c,owned.relativeTo(root).invariantSeparatorsPath,root).delete()}};throw e}
 }
 private fun deleteTree(source:File,authorize:()->Unit){
  val root=AndroidFilesAccess.root().toPath();val nodes=mutableListOf<File>()
  fun validate(f:File){authorize();require(!java.nio.file.Files.isSymbolicLink(f.toPath())&&f.canonicalFile.toPath().startsWith(root)){"Linked folders cannot be deleted here. Original files were kept."}}
  fun collect(f:File,depth:Int){validate(f);check(depth<=32&&nodes.size<10000){"Choose a smaller folder to delete."};nodes+=f
   if(f.isDirectory)for(child in f.listFiles()?:error("Folder is unreadable."))collect(child,depth+1)
  }
  collect(source,0)
  for(f in nodes.asReversed()){validate(f);check(f.delete()){"Some files could not be deleted. Remaining files were kept."}}
 }
 fun run(args:JSONObject,entries:JSONObject,authorize:()->Unit):JSONObject? {
  val operation=args.getString("operation");val folder=args.optString("folder","external").ifBlank{"external"};val key=args.optString("id")
  if(operation=="list"&&folder=="external")return JSONObject().put("folder","external").put("parent","").put("title","Android Files").put("external",true).put("items",JSONArray(AndroidFilesAccess.roots())).put("total",AndroidFilesAccess.roots().size).put("offset",0)
  val category=folder.removePrefix("android-").takeIf{folder.startsWith("android-")&&AndroidFilesAccess.categoryLabel(it)!=null}
  val row=if(operation=="folder")destination(args.optString("parent"),entries)else entries.optJSONObject(if(operation=="list")folder else key)
  if(category==null&&row?.has("android_path")!=true)return null
  authorize();check(AndroidFilesAccess.allowed(c)){"Allow Android file access in OnKlav setup to browse your phone files."}
  val root=AndroidFilesAccess.root()
  fun file(r:JSONObject)=AndroidFilesAccess.checked(c,r.getString("android_path"),root)
  fun entry(f:File,parent:String):JSONObject {val rel=f.relativeTo(root).invariantSeparatorsPath;return JSONObject().put("id",id(rel)).put("parent",parent).put("android_path",rel).put("uri",AndroidFilesAccess.uri(c,rel).toString()).put("name",f.name).put("folder",f.isDirectory).put("mime",if(f.isDirectory)"inode/directory"else AndroidFilesAccess.mime(f)).put("bytes",f.length()).put("editable",f.canWrite()&&!AndroidFilesAccess.protected(rel,f.isDirectory)).put("protected",AndroidFilesAccess.protected(rel,f.isDirectory)).put("detail",if(f.isDirectory)"Folder"else android.text.format.Formatter.formatShortFileSize(c,f.length()))}
  if(operation=="list"){
   if(category==null)check(!inTrash(file(requireNotNull(row)),root)){"Restore this folder from Trash before opening it."}
   val found=mutableListOf<File>();var scanned=0
   fun scan(dir:File,depth:Int){authorize();AndroidFilesAccess.checked(c,dir.relativeTo(root).invariantSeparatorsPath,root);check(depth<=32){"Open a smaller Android folder."};for(f in dir.listFiles().orEmpty()){if(++scanned>50000)error("Too many files in this category. Open Internal files to browse a specific folder.");if(f.name.startsWith('.')||f.name=="OnKlav Trash"||java.nio.file.Files.isSymbolicLink(f.toPath()))continue
    if(f.isDirectory){if(f==File(root,"Android")){File(f,"media").takeIf{it.isDirectory}?.let{scan(it,depth+1)}}else if(f.canRead())scan(f,depth+1)}else{
     val mime=AndroidFilesAccess.mime(f);val camera=f.toPath().startsWith(File(root,"DCIM").toPath())
     val match=when(category){"photos"->camera&&mime.startsWith("image/");"images"->mime.startsWith("image/");"audio"->mime.startsWith("audio/");"video"->mime.startsWith("video/");"apk"->f.extension.equals("apk",true);"documents"->mime.startsWith("text/")||mime in setOf("application/pdf","application/msword","application/rtf")||f.extension.lowercase() in setOf("docx","odt","xls","xlsx","ods","csv","ppt","pptx","epub");else->false};if(match)found+=f
    }
   }}
   if(category in setOf("photos","images","audio","video","documents","apk"))scan(root,0)
   else{val dir=when(category){"downloads"->File(root,"Download");"other"->root;"trash"->File(root,"OnKlav Trash");else->file(requireNotNull(row))};found+=dir.listFiles().orEmpty().filter{!it.name.startsWith('.')&&!java.nio.file.Files.isSymbolicLink(it.toPath())&&it!=File(root,"Android")&&(category=="trash"||it!=File(root,"OnKlav Trash"))}}
   val trashPrefs=c.getSharedPreferences("android-trash",0)
   fun displayName(f:File)=if(category=="trash")trashPrefs.getString(f.name,null)?.let{File(it).name}?:originalName(f) else f.name
   val query=args.optString("query");val matching=found.filter{displayName(it).contains(query,true)}.sortedWith(compareByDescending<File>{it.isDirectory}.thenByDescending{it.lastModified()}.thenBy{displayName(it).lowercase()});val offset=args.optInt("offset").coerceAtLeast(0)
   val page=matching.drop(offset).take(40).map{f->entry(f,folder).apply{if(category=="trash"){
    put("trash",true);put("name",displayName(f));if(!f.isDirectory)put("mime",AndroidFilesAccess.mime(File(displayName(f))))
   }}};page.forEach{entries.put(it.getString("id"),it)}
   if(entries.length()>1200){val keep=page.map{it.getString("id")}.toSet();entries.keys().asSequence().toList().filter{it !in keep&&entries.optJSONObject(it)?.has("android_path")==true&&entries.getJSONObject(it).optBoolean("folder")==false}.take((entries.length()-800).coerceAtLeast(0)).forEach{entries.remove(it)}}
   fun public(r:JSONObject)=JSONObject().apply{for(k in listOf("id","parent","name","folder","mime","bytes","editable","protected","detail","trash"))if(r.has(k))put(k,r.get(k))}
   val title=if(category!=null)AndroidFilesAccess.categoryLabel(category)!! else row!!.getString("name")
   return JSONObject().put("folder",folder).put("parent",if(category!=null)"external"else row!!.getString("parent")).put("title",title).put("external",true).put("trash",category=="trash").put("items",JSONArray(page.map(::public))).put("total",matching.size).put("offset",offset)
  }
  if(operation in setOf("restore_all","empty")){
   check(folder=="android-trash"){"Open Android Trash first."}
   val bin=AndroidFilesAccess.checked(c,"OnKlav Trash",root);val children=bin.listFiles().orEmpty().filter{!it.name.startsWith('.')}
   check(children.size<=10000){"Choose fewer items to process."};var done=0
   for(child in children){authorize();val item=entry(child,folder).put("trash",true);entries.put(item.getString("id"),item)
    try{run(JSONObject().put("operation",if(operation=="empty")"purge"else"restore").put("id",item.getString("id")),entries,authorize);done++}
    catch(e:Exception){throw IllegalStateException("$done items completed. Remaining items were kept: ${e.message}",e)}
   }
   return JSONObject().put("done",true).put("completed",done)
  }
  val selected=requireNotNull(row){"Refresh Android Files and select the item again."};val f=file(selected);check(f.exists()){"This Android file is no longer available."}
  if(operation in setOf("rename","trash","move","compress"))check(!AndroidFilesAccess.protected(f.relativeTo(root).invariantSeparatorsPath,f.isDirectory)){"This system or app-managed item cannot be changed."}
  if(inTrash(f,root))check(selected.optBoolean("trash")&&operation in setOf("restore","purge","info")){"Restore this item from Trash before using it."}
  return when(operation){
   "open","export"->{check(f.isFile){"Choose a file to open."};JSONObject(selected.toString())}
   "share"->{if(f.isFile)JSONObject(selected.toString())else{val dir=File(c.filesDir,"onklav-content").apply{mkdirs()};val target=File(dir,"share-${java.util.UUID.randomUUID()}.zip");zipped(f,target,authorize);JSONObject().put("path",target.path).put("name",f.name+".zip").put("mime","application/zip").put("temporary",true)}}
   "compress"->{val target=AndroidFilesAccess.checked(c,f.parentFile!!.relativeTo(root).resolve(f.name+".zip").invariantSeparatorsPath,root);check(target.createNewFile()){"A ZIP file with this name already exists."};zipped(f,target,authorize);JSONObject().put("done",true)}
   "info"->JSONObject(selected.toString()).put("properties","Name: ${selected.optString("name",f.name)}\nLocation: Android Files / ${selected.getString("android_path").substringBeforeLast('/',"")}\nSize: ${f.length()} bytes")
   "rename"->{val name=args.getString("name");require(name==FilesBroker.cleanName(name));val dest=AndroidFilesAccess.checked(c,f.parentFile!!.relativeTo(root).resolve(name).invariantSeparatorsPath,root);check(!dest.exists()){"A file with this name already exists."};authorize();check(f.renameTo(dest)){"Android could not rename this item."};entries.put(key,entry(dest,selected.getString("parent")).put("id",key));JSONObject().put("done",true)}
   "trash"->{
    val bin=AndroidFilesAccess.checked(c,"OnKlav Trash",root);check(bin.mkdirs()||bin.isDirectory)
    require(f!=bin&&!bin.toPath().startsWith(f.toPath())){"This folder cannot be moved to Trash."}
    File(bin,".nomedia").let{check(it.exists()||it.createNewFile()){"Could not prepare Android Trash."}}
    var storedName=f.name
    while(storedName.toByteArray(Charsets.UTF_8).size>180)storedName=storedName.dropLast(if(storedName.last().isLowSurrogate())2 else 1)
    val token=java.util.UUID.randomUUID().toString()+"--"+storedName;val target=File(bin,token);val prefs=c.getSharedPreferences("android-trash",0)
    authorize();check(prefs.edit().putString(token,f.relativeTo(root).invariantSeparatorsPath).commit()){"Could not save Trash information."}
    if(!f.renameTo(target)){prefs.edit().remove(token).commit();error("Android could not move this item to Trash.")}
    entries.remove(key);JSONObject().put("done",true)
   }
   "restore"->{
    require(selected.optBoolean("trash"));val prefs=c.getSharedPreferences("android-trash",0)
    val original=prefs.getString(f.name,null)
    val requested=original?.let{AndroidFilesAccess.checked(c,it,root)}
    val target=if(requested!=null&&requested.parentFile?.isDirectory==true&&!inTrash(requested,root))requested else{
     val downloads=AndroidFilesAccess.checked(c,"Download",root);check(downloads.mkdirs()||downloads.isDirectory){"Downloads is unavailable. Your trashed item was kept."}
     var candidate=File(downloads,original?.let{File(it).name}?:originalName(f));var number=2
     while(candidate.exists()){check(number<=10000){"Too many files with this name. Your trashed item was kept."};val name=original?.let{File(it).name}?:originalName(f);val dot=name.lastIndexOf('.');val stem=if(dot>0&&!f.isDirectory)name.substring(0,dot)else name;val extension=if(dot>0&&!f.isDirectory)name.substring(dot)else "";candidate=File(downloads,"$stem (${number++})$extension")};candidate
    };check(!target.exists()){"An item with this name already exists. Your trashed item was kept."}
    check(target.parentFile?.isDirectory==true){"The original folder is unavailable. Your trashed item was kept."}
    authorize();check(f.renameTo(target)){"Android could not restore this item."};prefs.edit().remove(f.name).commit();entries.remove(key);JSONObject().put("done",true)
   }
   "purge"->{require(selected.optBoolean("trash"));authorize();deleteTree(f,authorize);c.getSharedPreferences("android-trash",0).edit().remove(f.name).commit();entries.remove(key);JSONObject().put("done",true)}
   "folder"->{val name=args.getString("name");require(name==FilesBroker.cleanName(name));val dest=AndroidFilesAccess.checked(c,f.relativeTo(root).resolve(name).invariantSeparatorsPath,root);check(!dest.exists()&&dest.mkdir()){"This folder could not be created."};JSONObject().put("done",true)}
   "copy","move"->{val parent=destination(args.getString("parent"),entries)?:error("Open the destination folder first.");val dir=file(parent);check(dir.isDirectory&&!inTrash(dir,root)){"Choose an Android destination folder."};val target=AndroidFilesAccess.checked(c,dir.relativeTo(root).resolve(f.name).invariantSeparatorsPath,root);copy(f,target,authorize);if(operation=="move"){authorize();deleteTree(f,authorize)};JSONObject().put("done",true)}
   else->error("This action is unavailable for Android files.")
  }
 }
}

class AndroidFilesProvider:ContentProvider(){
 override fun onCreate()=true
 private fun file(uri:Uri):File {val c=requireNotNull(context);require(uri.authority==c.packageName+".android-files");return AndroidFilesAccess.checked(c,uri.pathSegments.joinToString("/")).also{require(it.isFile)}}
 override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {require(mode in setOf("r","w","wt"));if(mode!="r")check(!AndroidFilesAccess.protected(uri.pathSegments.joinToString("/"),false)){"This system file is read-only."};return ParcelFileDescriptor.open(file(uri),if(mode=="r")ParcelFileDescriptor.MODE_READ_ONLY else ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)}
 override fun getType(uri:Uri)=AndroidFilesAccess.mime(file(uri))
 override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor {val f=file(uri);val columns=projection?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE);return MatrixCursor(columns).apply{addRow(columns.map{if(it==OpenableColumns.DISPLAY_NAME)f.name else if(it==OpenableColumns.SIZE)f.length()else null})}}
 override fun insert(uri:Uri,values:ContentValues?):Uri=throw UnsupportedOperationException()
 override fun delete(uri:Uri,selection:String?,args:Array<out String>?):Int{check(!AndroidFilesAccess.protected(uri.pathSegments.joinToString("/"),false)){"This system file is read-only."};return if(file(uri).delete())1 else 0}
 override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=throw UnsupportedOperationException()
}
