package app.onklav.home

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Save-back never writes content objects or their index directly. Existing signed Files actions
 * retain the former version in Trash, with a small recovery journal for interrupted replacements. */
internal object PhotoImageSave {
 fun digest(file:File,check:()->Unit={}):String {val hash=MessageDigest.getInstance("SHA-256");file.inputStream().use{input->val b=ByteArray(65536);while(true){check();val n=input.read(b);if(n<0)break;hash.update(b,0,n)}};return hash.digest().joinToString(""){"%02x".format(it)}}
 fun atomic(file:File,value:JSONObject){file.parentFile!!.mkdirs();val tmp=File(file.parentFile,file.name+".tmp");FileOutputStream(tmp).use{it.write(value.toString().toByteArray());it.fd.sync()};check(tmp.renameTo(file)){"Unable to save image recovery information"}}
 fun origin(c:Context,uri:Uri,source:File):JSONObject {
  var name="Image";var mime=c.contentResolver.getType(uri).orEmpty();var writable=false
  runCatching{c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,DocumentsContract.Document.COLUMN_FLAGS),null,null,null)?.use{q->if(q.moveToFirst()){val n=q.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0)name=q.getString(n).orEmpty();val f=q.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS);if(f>=0)writable=q.getInt(f)and DocumentsContract.Document.FLAG_SUPPORTS_WRITE!=0}}}
  val internal=uri.authority==c.packageName+".file-selection"
  if(internal){val row=FileSelectionStore.read(c,uri);name=row.optString("name",name);mime=row.optString("mime",mime);writable=row.optString("kind")=="read"&&row.optString("entry").matches(Regex("[a-f0-9]{32}"))&&row.optJSONArray("folders")?.length()?.let{it>0}==true}
  if(mime !in setOf("image/jpeg","image/png"))writable=false
  return JSONObject().put("uri",uri.toString()).put("name",name).put("mime",mime).put("writable",writable).put("digest",digest(source))
 }
 /** Complete a validated open only when the copied source matches its staged metadata.
  * Otherwise retain the preceding source/target pair. This prevents save-back to the wrong photo. */
 fun recoverOpening(c:Context,authorize:()->Unit){
  val dir=File(c.filesDir,"photo-editor");val staged=File(dir,"opening.json");if(!staged.exists())return
  authorize();require(staged.length()<=65536);val info=JSONObject(staged.readText());val source=File(dir,"source")
  if(source.isFile&&digest(source,authorize)==info.getString("digest")){authorize();atomic(File(dir,"origin.json"),info);val project=if(info.optBoolean("layers")){val f=File(dir,"opening-project.json");require(f.length()<=8*1024*1024);PhotoLayers.parse(f.readText())}else null;atomic(File(dir,"edits.json"),project?.let{JSONObject(it.edit.json())}?:JSONObject());if(project!=null)atomic(File(dir,"history.json"),project.json())else File(dir,"history.json").delete()}
  File(dir,"opening-project.json").delete();check(staged.delete()){"Unable to finish image opening recovery"}
 }
 private fun find(s:FileSelectionSession,path:List<String>,id:String):JSONObject {
  s.navigate(path);while(true){s.state.nodes("items").firstOrNull{it.optString("id")==id}?.let{return it};check(s.state.optInt("offset")+40<s.state.optInt("total")){"This image moved or is unavailable. Reopen it or save a copy."};s.event("next")}
 }
 private fun trash(s:FileSelectionSession,path:List<String>,id:String){find(s,path,id);s.event("options:$id");s.event("trash");s.event("confirm.accept")}
 private fun rename(s:FileSelectionSession,path:List<String>,id:String,name:String){find(s,path,id);s.event("options:$id");s.event("rename");s.event("name",name);s.event("name.save")}
 private fun journal(c:Context)=File(c.filesDir,"photo-editor/replace.json")
 /** Roll back an incomplete name swap, or finish retargeting a completed one. Original bytes
  * always exist in the active folder or Trash. Failures retain the journal for the next attempt. */
 fun recover(c:Context,authorize:()->Unit):JSONObject? {
  val f=journal(c);if(!f.exists())return null;require(f.length()<=65536);val j=JSONObject(f.readText());val path=FileSelectionStore.paths(j)
  return AppSdk.carrierEgress{authorize();FileSelectionSession(c).use{s->s.start()
   val newRow=runCatching{find(s,path,j.getString("new"))}.getOrNull()
   if(newRow?.optString("name")==j.getString("name")){
    val descriptor=s.choose(newRow);val uri=Uri.parse(j.getString("uri"));FileSelectionStore.retarget(c,uri,descriptor)
    val origin=j.getJSONObject("origin");origin.put("digest",j.getString("new_digest"));atomic(File(c.filesDir,"photo-editor/origin.json"),origin);check(f.delete());origin
   }else{
    val old=runCatching{find(s,path,j.getString("old"))}.getOrNull()
    if(old==null){find(s,listOf("trash"),j.getString("old"));s.event("options:"+j.getString("old"));s.event("restore")}
    if(newRow!=null)trash(s,path,j.getString("new"));check(f.delete());null
   }
  }}
 }
 fun replace(c:Context,origin:JSONObject,encoded:File,authorize:()->Unit,afterArchive:()->Unit={}):JSONObject {
  authorize();recover(c,authorize);val uri=Uri.parse(origin.getString("uri"));check(origin.optBoolean("writable")){"This image is read-only. Save a copy instead."}
  if(uri.authority!=c.packageName+".file-selection"){
   // Provider save-back: keep a verified local recovery copy before touching the destination.
   val backup=File(c.filesDir,"photo-editor/previous-image");check(backup.parentFile!!.mkdirs()||backup.parentFile!!.isDirectory){"Unable to keep the previous image safely"};c.contentResolver.openInputStream(uri).use{input->requireNotNull(input);backup.outputStream().use{FilesBroker.copyBoundedLimit(input,it,150_000_000,authorize)}}
   val details=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeFile(backup.path,details)
   atomic(File(backup.parentFile,"previous-info.json"),JSONObject().put("name",origin.getString("name")).put("mime",details.outMimeType?:"application/octet-stream"))
   check(digest(backup,authorize)==origin.getString("digest")){"This image changed elsewhere. Reopen it or save a copy."}
   try{authorize();c.contentResolver.openOutputStream(uri,"wt").use{out->requireNotNull(out);encoded.inputStream().use{FilesBroker.copyBoundedLimit(it,out,FilesBroker.MAX_FILE,authorize)}}}
   catch(e:Exception){runCatching{authorize();c.contentResolver.openOutputStream(uri,"wt").use{out->requireNotNull(out);backup.inputStream().use{FilesBroker.copyBoundedLimit(it,out,150_000_000,authorize)}}};throw IllegalStateException("The image could not be replaced. Its previous version is kept in the editor; save a copy.",e)}
   return JSONObject(origin.toString()).put("digest",digest(encoded,authorize))
  }
  val selected=FileSelectionStore.read(c,uri);val old=selected.getString("entry");val path=FileSelectionStore.paths(selected);val original=FileSelectionStore.file(c,selected)
  check(digest(original,authorize)==origin.getString("digest")){"This image changed elsewhere. Reopen it or save a copy."}
  val tempName="Edited-"+UUID.randomUUID().toString().take(8)+if(origin.getString("mime")=="image/png")".png"else".jpg"
  val created=FileSelectionSession(c).use{s->s.start();s.importSaved(path,encoded,tempName,origin.getString("mime"))}
  val j=JSONObject().put("folders",org.json.JSONArray(path)).put("old",old).put("new",created.getString("id")).put("name",origin.getString("name")).put("uri",uri.toString()).put("new_digest",digest(encoded,authorize)).put("origin",origin)
  atomic(journal(c),j)
  try{AppSdk.carrierEgress{authorize();FileSelectionSession(c).use{s->s.start();val current=find(s,path,old);check(current.getString("name")==origin.getString("name")){"This image was renamed. Reopen it before saving changes."};trash(s,path,old);afterArchive();authorize();rename(s,path,created.getString("id"),origin.getString("name"))}}}
  catch(e:Exception){runCatching{recover(c,authorize)};throw e}
  return requireNotNull(recover(c,authorize)){"The replacement was not completed. Save a copy instead."}
 }
}
