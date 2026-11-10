package app.onklav.home

import app.onklav.shared.OnKlavFileContracts

import android.content.*
import android.database.*
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract as Docs
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContract
import kotlinx.coroutines.*
import org.json.*
import java.io.*
import java.util.UUID

internal data class FileExportRequest(val name:String,val mime:String)
internal class FileExportContract:ActivityResultContract<FileExportRequest,Uri?>(){
    override fun createIntent(context:Context,input:FileExportRequest)=OnKlavFileContracts.intent(context,"save",arrayOf(input.mime),input.name)
    override fun parseResult(resultCode:Int,intent:Intent?)=if(resultCode==android.app.Activity.RESULT_OK)intent?.data else null
}

/** Streams only explicitly selected documents. No broad Android storage permission. */
internal object FilesBroker {
    const val MAX_FILE=256L*1024*1024
    fun cleanName(value:String):String=value.replace(Regex("[\\p{Cntrl}/\\\\:]"),"_").trim().trimStart('.').take(120).ifBlank{"File"}
    suspend fun stage(c:Context,uri:Uri,authorize:()->Unit):JSONObject=withContext(Dispatchers.IO){
        require(uri.scheme=="content"){"Choose a file in OnKlav Files"};val job=currentCoroutineContext();val checked={job.ensureActive();authorize()};checked()
        var name="File";var reported=-1L
        c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),android.os.Bundle.EMPTY,null)?.use{q->if(q.moveToFirst()){val n=q.getColumnIndex(OpenableColumns.DISPLAY_NAME);val b=q.getColumnIndex(OpenableColumns.SIZE);if(n>=0)name=q.getString(n).orEmpty();if(b>=0&&!q.isNull(b))reported=q.getLong(b)}}
        require(reported<=MAX_FILE){"Choose a file smaller than 256 MB"}
        val target=File(c.filesDir,"content-import.tmp")
        try{c.contentResolver.openInputStream(uri)?.use{input->target.outputStream().use{copyBounded(input,it,checked)}}?:error("This file could not be opened");authorize();JSONObject().put("name",cleanName(name)).put("mime",app.onklav.shared.Mp3Codec.mime(c.contentResolver.getType(uri)?:"application/octet-stream",name))}
        catch(e:Exception){target.delete();throw e}
    }
    fun copyBounded(input:InputStream,out:OutputStream,authorize:()->Unit)=copyBoundedLimit(input,out,MAX_FILE,authorize)
    fun copyBoundedLimit(input:InputStream,out:OutputStream,limit:Long,authorize:()->Unit)=copyBoundedLimit(input,out,limit,{},authorize)
    fun copyBoundedLimit(input:InputStream,out:OutputStream,limit:Long,onProgress:(Long)->Unit,authorize:()->Unit){
        val buffer=ByteArray(64*1024);var size=0L;var nextCheck=0L
        while(true){val n=input.read(buffer);if(n<0)break;if(n==0){authorize();val one=input.read();if(one<0)break;size++;require(size<=limit){"File exceeds the supported export size"};out.write(one);onProgress(size);continue};size+=n;require(size<=limit){"File exceeds the supported export size"};if(size>=nextCheck){authorize();nextCheck=size+1024*1024};out.write(buffer,0,n);onProgress(size)};authorize()
    }
    fun open(c:Context,uri:Uri,mime:String){
        try{val name=uri.getQueryParameter("name")?:runCatching{c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0) else ""}}.getOrNull().orEmpty();val internal=DocumentReading.supports(name,mime);c.startActivity(Intent(Intent.ACTION_VIEW).apply{if(internal)setClass(c,DocuViewActivity::class.java)}.setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=ClipData.newRawUri("File",uri)})}
        catch(e:ActivityNotFoundException){error("No app is available to open this file type. You can save a copy to Android instead.")}
    }
}

/** Explicit one-file, read-only grants for opening media/documents outside the host. */
class FilesOpenProvider:ContentProvider(){
    override fun onCreate()=true
    private fun file(uri:Uri):File {
        val c=requireNotNull(context);val id=uri.pathSegments.singleOrNull()?:throw FileNotFoundException()
        if(uri.authority!=c.packageName+".files-open"||!id.matches(Regex("[a-f0-9]{32}")))throw FileNotFoundException()
        val rawDir=File(c.cacheDir,"exports");if(java.nio.file.Files.isSymbolicLink(rawDir.toPath()))throw FileNotFoundException();val dir=rawDir.canonicalFile;val f=File(dir,"$id.data")
        if(java.nio.file.Files.isSymbolicLink(f.toPath())||f.canonicalFile.parentFile!=dir||!f.isFile)throw FileNotFoundException()
        val metadata=File(dir,"$id.meta")
        if(metadata.exists()){check(metadata.length()<=65536);val entry=JSONObject(metadata.readText()).optString("entry");if(entry.isNotEmpty())AppSdk.folderVault(c,"access",entry)}
        return f
    }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor{if(mode!="r")throw FileNotFoundException("Read-only file");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY)}
    override fun getType(uri:Uri):String{file(uri);return uri.getQueryParameter("mime")?.takeIf{it.length<=200&&!it.any(Char::isISOControl)}?:"application/octet-stream"}
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor{val f=file(uri);val cols=projection?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE);return MatrixCursor(cols).apply{addRow(cols.map{when(it){OpenableColumns.DISPLAY_NAME->uri.getQueryParameter("name")?.take(180)?:"File";OpenableColumns.SIZE->f.length();else->null}})}}
    override fun insert(uri:Uri,values:ContentValues?):Uri=throw UnsupportedOperationException()
    override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=throw UnsupportedOperationException()
    override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=throw UnsupportedOperationException()
    companion object {
        fun stage(c:Context,descriptor:JSONObject,authorize:()->Unit):Uri{
            authorize();val source=descriptor.optString("path").takeIf{it.isNotBlank()}?.let(::File);val base=File(c.filesDir,"onklav-content").canonicalFile
            if(source!=null)require(source.canonicalFile.toPath().startsWith(base.toPath())&&!java.nio.file.Files.isSymbolicLink(source.toPath())&&source.isFile){"This OnKlav file is unavailable"}else require(Uri.parse(descriptor.getString("uri")).scheme=="content")
            val dir=File(c.cacheDir,"exports");check(dir.mkdirs()||dir.isDirectory);require(!java.nio.file.Files.isSymbolicLink(dir.toPath()))
            val id=UUID.randomUUID().toString().replace("-","");val f=File(dir,"$id.data")
            try{(source?.inputStream()?:c.contentResolver.openInputStream(Uri.parse(descriptor.getString("uri")))?:error("File unavailable")).use{input->f.outputStream().use{FilesBroker.copyBoundedLimit(input,it,if(descriptor.optString("mime").startsWith("video/")||descriptor.optString("mime").startsWith("audio/"))2*1024*1024*1024L else FilesBroker.MAX_FILE,authorize)}};File(dir,"$id.meta").writeText(JSONObject().put("entry",descriptor.optString("id")).toString());return Uri.Builder().scheme("content").authority(c.packageName+".files-open").appendPath(id).appendQueryParameter("name",descriptor.getString("name")).appendQueryParameter("mime",descriptor.getString("mime")).build()}
            catch(e:Exception){f.delete();File(dir,"$id.meta").delete();throw e}finally{if(descriptor.optBoolean("temporary"))source?.delete()}
        }
    }
}

/** Android document trees are isolated per SDK app and exposed as opaque tokens. */
internal class ExternalFiles(private val c:Context,app:String){
    companion object{private val locks=java.util.concurrent.ConcurrentHashMap<String,Any>()}
    private val lock=locks.computeIfAbsent(c.filesDir.absolutePath+":"+app){Any()}
    private val prefs=c.getSharedPreferences("external-files-"+AppSdk.desktopId(app),0)
    private fun load()=JSONObject(prefs.getString("entries","{}")!!)
    private fun save(entries:JSONObject){check(prefs.edit().putString("entries",entries.toString()).commit()){"Folder access could not be saved"}}
    private fun active(row:JSONObject):Uri{val tree=Uri.parse(row.getString("tree"));check(c.contentResolver.persistedUriPermissions.any{it.uri==tree&&it.isReadPermission}){"Android folder access was removed. Connect the folder again."};val uri=Uri.parse(row.getString("uri"));val root=Docs.buildDocumentUriUsingTree(tree,Docs.getTreeDocumentId(tree));check(uri==root||Docs.isChildDocument(c.contentResolver,root,uri)){"This file is outside the connected Android folder"};return uri}
    private fun writable(row:JSONObject){val tree=Uri.parse(row.getString("tree"));check(c.contentResolver.persistedUriPermissions.any{it.uri==tree&&it.isWritePermission}){"This Android folder is read-only. Choose a writable folder to change files."}}
    private fun entry(entries:JSONObject,id:String)=entries.optJSONObject(id)?:error("This Android file is no longer available. Refresh the folder.")
    fun connect(uri:Uri,authorize:()->Unit):JSONObject=synchronized(lock){connectLocked(uri,authorize)}
    private fun connectLocked(uri:Uri,authorize:()->Unit):JSONObject{
        require(uri.scheme=="content"&&Docs.isTreeUri(uri)){"Choose an Android folder"};authorize()
        try{c.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}catch(e:SecurityException){c.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        val doc=Docs.buildDocumentUriUsingTree(uri,Docs.getTreeDocumentId(uri));var name="Android Folder"
        c.contentResolver.query(doc,arrayOf(Docs.Document.COLUMN_DISPLAY_NAME),android.os.Bundle.EMPTY,null)?.use{q->if(q.moveToFirst()){val column=q.getColumnIndex(Docs.Document.COLUMN_DISPLAY_NAME);if(column>=0)name=q.getString(column).orEmpty().ifBlank{"Android Folder"}}}
        val entries=load();val existing=entries.keys().asSequence().firstOrNull{entries.getJSONObject(it).optBoolean("connected_root")&&entries.getJSONObject(it).optString("tree")==uri.toString()};val id=existing?:UUID.randomUUID().toString().replace("-","")
        authorize();entries.put(id,JSONObject().put("id",id).put("name",name).put("uri",doc.toString()).put("tree",uri.toString()).put("folder",true).put("parent","external").put("connected_root",true));save(entries);return JSONObject().put("id",id)
    }
    internal fun sharedPath(id:String,destination:Boolean=false):String=synchronized(lock){
        if(destination)androidPasteFolders[id]?.let{return@synchronized it}
        val row=entry(load(),id)
        check(row.has("android_path")){"Open a folder in Android shared storage."}
        row.getString("android_path")
    }
    internal fun createDirect(folder:String,name:String,authorize:()->Unit):Uri?=AndroidSharedFiles(c).create(folder,load(),name,authorize)
    internal fun folderDestination(id:String,authorize:()->Unit):Uri {authorize();val row=entry(load(),id);check(row.getBoolean("folder"));val uri=active(row);writable(row);return uri}
    private fun editable(row:JSONObject):Boolean {
        if(row.optBoolean("connected_root"))return false
        val tree=Uri.parse(row.getString("tree"))
        if(c.contentResolver.persistedUriPermissions.none{it.uri==tree&&it.isWritePermission})return false
        val flags=row.optInt("flags")
        return (flags and (Docs.Document.FLAG_SUPPORTS_WRITE or Docs.Document.FLAG_SUPPORTS_DELETE or Docs.Document.FLAG_SUPPORTS_RENAME or Docs.Document.FLAG_SUPPORTS_MOVE or Docs.Document.FLAG_DIR_SUPPORTS_CREATE))!=0
    }
    private fun public(row:JSONObject)=JSONObject().put("editable",editable(row)).put("id",row.getString("id")).put("name",row.getString("name")).put("folder",row.getBoolean("folder")).put("connected_root",row.optBoolean("connected_root")).put("protected",false).put("mime",row.optString("mime")).put("detail",if(row.getBoolean("folder"))"Android Folder"else row.optString("mime").ifBlank{"Android File"})
    /** Providers such as Downloads may omit copyDocument; use bounded streaming in that case. */
    private fun copyTree(row:JSONObject,dest:Uri,authorize:()->Unit):Uri{
        var count=0;var bytes=0L;var created:Uri?=null;val seen=mutableSetOf<String>()
        fun copy(source:Uri,parent:Uri,name:String,folder:Boolean,mime:String,depth:Int):Uri{authorize();check(depth<=32&&++count<=10000&&seen.add(source.toString())){"Folder is too large or contains a cycle"}
            val out=Docs.createDocument(c.contentResolver,parent,if(folder)Docs.Document.MIME_TYPE_DIR else mime.ifBlank{"application/octet-stream"},FilesBroker.cleanName(name))?:error("This provider cannot copy files");if(created==null)created=out
            if(folder){val tree=Uri.parse(row.getString("tree"));val children=Docs.buildChildDocumentsUriUsingTree(tree,Docs.getDocumentId(source));val cursor=c.contentResolver.query(children,arrayOf(Docs.Document.COLUMN_DOCUMENT_ID,Docs.Document.COLUMN_DISPLAY_NAME,Docs.Document.COLUMN_MIME_TYPE),android.os.Bundle.EMPTY,null)?:error("Folder unavailable");cursor.use{q->while(q.moveToNext()){val child=Docs.buildDocumentUriUsingTree(tree,q.getString(0));check(Docs.isChildDocument(c.contentResolver,source,child));copy(child,out,q.getString(1).orEmpty(),q.getString(2)==Docs.Document.MIME_TYPE_DIR,q.getString(2).orEmpty(),depth+1)}}}
            else{c.contentResolver.openInputStream(source)?.use{input->c.contentResolver.openOutputStream(out,"wt")?.use{output->val b=ByteArray(65536);while(true){authorize();val n=input.read(b);if(n<0)break;if(n==0)continue;bytes+=n;check(bytes<=FilesBroker.MAX_FILE){"Copy up to256MB at a time"};output.write(b,0,n)}}?:error("Copy could not be written")}?:error("File unavailable")};return out
        }
        try{return copy(active(row),dest,row.getString("name"),row.getBoolean("folder"),row.optString("mime"),0)}catch(e:Exception){created?.let{runCatching{Docs.deleteDocument(c.contentResolver,it)}};throw e}
    }
    private fun zipped(row:JSONObject,authorize:()->Unit):File{
        val dir=File(c.filesDir,"onklav-content");check(dir.mkdirs()||dir.isDirectory);val file=File(dir,"share-${UUID.randomUUID()}.zip");var count=0;var bytes=0L
        try{java.util.zip.ZipOutputStream(file.outputStream().buffered()).use{zip->
            fun add(uri:Uri,name:String,folder:Boolean,depth:Int){authorize();check(depth<=32&&++count<=10000){"Folder is too large to compress"};val safe=name.split('/').joinToString("/"){FilesBroker.cleanName(it)}
                zip.putNextEntry(java.util.zip.ZipEntry(safe+if(folder)"/"else""))
                if(!folder){c.contentResolver.openInputStream(uri)?.use{input->val b=ByteArray(65536);while(true){authorize();val n=input.read(b);if(n<0)break;if(n==0)continue;bytes+=n;check(bytes<=FilesBroker.MAX_FILE){"Compress up to256MB at a time"};zip.write(b,0,n)}}?:error("File unavailable")};zip.closeEntry()
                if(folder){val tree=Uri.parse(row.getString("tree"));val children=Docs.buildChildDocumentsUriUsingTree(tree,Docs.getDocumentId(uri));val cursor=c.contentResolver.query(children,arrayOf(Docs.Document.COLUMN_DOCUMENT_ID,Docs.Document.COLUMN_DISPLAY_NAME,Docs.Document.COLUMN_MIME_TYPE),android.os.Bundle.EMPTY,null)?:error("Folder unavailable");cursor.use{q->while(q.moveToNext()){val child=Docs.buildDocumentUriUsingTree(tree,q.getString(0));check(Docs.isChildDocument(c.contentResolver,uri,child)){"File is outside this folder"};add(child,safe+"/"+FilesBroker.cleanName(q.getString(1).orEmpty()),q.getString(2)==Docs.Document.MIME_TYPE_DIR,depth+1)}}}
            };add(active(row),row.getString("name"),row.getBoolean("folder"),0)
        };check(file.length()<=FilesBroker.MAX_FILE){"Compressed file exceeds256MB"};authorize();return file}catch(e:Exception){file.delete();throw e}
    }
    fun run(args:JSONObject,authorize:()->Unit):JSONObject=synchronized(lock){runLocked(args,authorize)}
    private fun runLocked(args:JSONObject,authorize:()->Unit):JSONObject{
        authorize();val entries=load();val operation=args.getString("operation")
        AndroidSharedFiles(c).run(args,entries,authorize)?.let{authorize();if(operation in setOf("list","rename","trash","restore","purge","restore_all","empty"))save(entries);return it}
        if(operation=="list"){
            val folder=args.optString("folder","external").ifBlank{"external"};val query=args.optString("query");val offset=args.optInt("offset").coerceIn(0,10000)
            val byUri=entries.keys().asSequence().associate{entries.getJSONObject(it).optString("uri") to it}
            val rows=mutableListOf<JSONObject>();var title="External Files";var parent=""
            if(folder=="external"){entries.keys().asSequence().map{entries.getJSONObject(it)}.filter{it.optBoolean("connected_root")}.forEach{rows+=it}}
            else{
                val row=entry(entries,folder);val uri=active(row);check(row.getBoolean("folder")){"Choose an Android folder"};title=row.getString("name");parent=row.getString("parent")
                val tree=Uri.parse(row.getString("tree"));val child=Docs.buildChildDocumentsUriUsingTree(tree,Docs.getDocumentId(uri))
                val cursor=c.contentResolver.query(child,arrayOf(Docs.Document.COLUMN_DOCUMENT_ID,Docs.Document.COLUMN_DISPLAY_NAME,Docs.Document.COLUMN_MIME_TYPE,Docs.Document.COLUMN_FLAGS),android.os.Bundle.EMPTY,null)?:error("This Android folder is unavailable. Try Again.")
                cursor.use{q->var count=0;while(q.moveToNext()&&count++<10000){if(count%100==0)authorize();val childUri=Docs.buildDocumentUriUsingTree(tree,q.getString(0));val token=byUri[childUri.toString()]?:UUID.randomUUID().toString().replace("-","");val reported=q.getString(2).orEmpty();val mime=if(reported==Docs.Document.MIME_TYPE_DIR)reported else app.onklav.shared.Mp3Codec.mime(reported,q.getString(1).orEmpty());val item=JSONObject().put("id",token).put("parent",folder).put("uri",childUri.toString()).put("tree",tree.toString()).put("name",q.getString(1).orEmpty().ifBlank{"Unnamed File"}).put("mime",mime).put("folder",mime==Docs.Document.MIME_TYPE_DIR).put("flags",q.getInt(3));entries.put(token,item);rows+=item;}}
                // Drop stale snapshots for this folder; keep connected roots and other folders.
                val current=rows.map{it.getString("id")}.toSet();entries.keys().asSequence().toList().filter{entries.getJSONObject(it).optString("parent")==folder&&it !in current}.forEach{entries.remove(it)}
                check(entries.length()<=10000){"Too many Android file entries. Disconnect unused folders."};authorize();save(entries)
            }
            val matching=rows.filter{it.getString("name").contains(query,true)}.sortedWith(compareByDescending<JSONObject>{it.getBoolean("folder")}.thenBy{it.getString("name").lowercase(java.util.Locale.ROOT)})
            return JSONObject().put("folder",folder).put("title",title).put("parent",parent).put("external",true).put("items",JSONArray(matching.drop(offset).take(40).map(::public))).put("total",matching.size).put("offset",offset)
        }
        if(operation=="folder"){
            val target=entry(entries,args.getString("parent"));val dest=active(target);writable(target);check(target.getBoolean("folder"));val name=FilesBroker.cleanName(args.getString("name"));require(name==args.getString("name")){"Choose a name without slashes or leading dots"};authorize();Docs.createDocument(c.contentResolver,dest,Docs.Document.MIME_TYPE_DIR,name)?:error("This provider does not support creating folders");return JSONObject().put("done",true)
        }
        val id=args.getString("id");val row=entry(entries,id)
        if(operation=="disconnect"){check(row.optBoolean("connected_root"));val tree=row.getString("tree");entries.keys().asSequence().toList().filter{entries.getJSONObject(it).getString("tree")==tree}.forEach{entries.remove(it)};authorize();save(entries);return JSONObject().put("done",true)}
        val uri=active(row)
        if(operation in setOf("rename","move","trash","compress"))writable(row)
        return when(operation){
            "info"->{val result=public(row);var bytes=0L;var modified=0L;c.contentResolver.query(uri,arrayOf(Docs.Document.COLUMN_SIZE,Docs.Document.COLUMN_LAST_MODIFIED),android.os.Bundle.EMPTY,null)?.use{q->if(q.moveToFirst()){val b=q.getColumnIndex(Docs.Document.COLUMN_SIZE);val m=q.getColumnIndex(Docs.Document.COLUMN_LAST_MODIFIED);if(b>=0&&!q.isNull(b))bytes=q.getLong(b);if(m>=0&&!q.isNull(m))modified=q.getLong(m)}};result.put("properties","Name: ${row.getString("name")}\nType: ${if(row.getBoolean("folder"))"Folder"else row.optString("mime")}\nLocation: Connected Android folder\nSize: $bytes bytes"+(if(modified>0)"\nModified: "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(modified))else ""));if(row.optString("mime").startsWith("text/")){c.contentResolver.openInputStream(uri)?.use{stream->val b=ByteArray(24000);val out=ByteArrayOutputStream();while(out.size()<b.size){val n=stream.read(b,0,b.size-out.size());if(n<0)break;out.write(b,0,n)};authorize();result.put("preview",out.toString("UTF-8").take(12000))}};result}
            "share"->{if(!row.getBoolean("folder"))JSONObject(row.toString())else{val file=zipped(row,authorize);JSONObject().put("path",file.path).put("name",row.getString("name")+".zip").put("mime","application/zip").put("temporary",true)}}
            "compress"->{val file=zipped(row,authorize);try{val parent=if(row.optBoolean("connected_root"))row else entry(entries,row.getString("parent"));writable(parent);val dest=active(parent);authorize();val target=Docs.createDocument(c.contentResolver,dest,"application/zip",FilesBroker.cleanName(row.getString("name")+".zip"))?:error("This provider cannot create ZIP files");try{file.inputStream().use{input->c.contentResolver.openOutputStream(target,"wt")?.use{out->FilesBroker.copyBounded(input,out,authorize)}?:error("ZIP file cannot be written")};JSONObject().put("done",true)}catch(e:Exception){runCatching{Docs.deleteDocument(c.contentResolver,target)};throw e}}finally{file.delete()}}
            "open","export"->JSONObject(row.toString())
            "rename"->{check(!row.optBoolean("connected_root")){"Rename folders inside the connected folder"};val name=FilesBroker.cleanName(args.getString("name"));require(name==args.getString("name")){"Use a name without slashes or leading dots"};authorize();val changed=Docs.renameDocument(c.contentResolver,uri,name)?:error("This provider does not support renaming this item");row.put("name",name).put("uri",changed.toString());save(entries);JSONObject().put("done",true)}
            "folder"->{val target=entry(entries,args.getString("parent"));val dest=active(target);writable(target);check(target.getBoolean("folder"));val name=FilesBroker.cleanName(args.getString("name"));require(name==args.getString("name"));authorize();Docs.createDocument(c.contentResolver,dest,Docs.Document.MIME_TYPE_DIR,name)?:error("This provider does not support creating folders");JSONObject().put("done",true)}
            "move","copy"->{check(!row.optBoolean("connected_root"));val dest=entry(entries,args.getString("parent"));check(dest.getBoolean("folder")&&dest.getString("tree")==row.getString("tree")){"Choose a folder in the same connected Android location"};writable(dest);val destUri=active(dest);val sourceParent=active(entry(entries,row.getString("parent")));check(uri!=destUri&&(!row.getBoolean("folder")||!Docs.isChildDocument(c.contentResolver,uri,destUri))){"A folder cannot be moved inside itself"};authorize();val result=if(operation=="move")Docs.moveDocument(c.contentResolver,uri,sourceParent,destUri)else try{Docs.copyDocument(c.contentResolver,uri,destUri)?:copyTree(row,destUri,authorize)}catch(e:UnsupportedOperationException){copyTree(row,destUri,authorize)};check(result!=null){"This Android provider does not support $operation"};if(operation=="move"){row.put("uri",result.toString()).put("parent",dest.getString("id"));save(entries)};JSONObject().put("done",true)}
            "trash"->{check(!row.optBoolean("connected_root")){"Disconnect this folder to keep its Android files"};authorize();check(Docs.deleteDocument(c.contentResolver,uri)){"Android could not delete this item"};entries.remove(id);save(entries);JSONObject().put("done",true)}
            else->error("Unsupported Android file action")
        }
    }
}
