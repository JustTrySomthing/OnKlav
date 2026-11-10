package app.onklav.home

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Selected Files transactions only: former versions stay in Trash; interrupted swaps recover. */
internal object CodeEditFiles{
 fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
 fun atomic(file:File,value:JSONObject){file.parentFile!!.mkdirs();val a=AtomicFile(file);val stream=a.startWrite();try{stream.write(value.toString().toByteArray());a.finishWrite(stream)}catch(t:Throwable){a.failWrite(stream);throw t}}
 fun read(c:Context,uri:Uri):ByteArray=c.contentResolver.openInputStream(uri).use{input->requireNotNull(input);val out=java.io.ByteArrayOutputStream();FilesBroker.copyBoundedLimit(input,out,CodeText.MAX_BYTES.toLong()){};out.toByteArray()}
 private fun journal(c:Context)=File(c.filesDir,"code-edit/replace.json")
 private fun find(s:FileSelectionSession,path:List<String>,id:String):JSONObject{s.navigate(path);while(true){s.state.nodes("items").firstOrNull{it.optString("id")==id}?.let{return it};check(s.state.optInt("offset")+40<s.state.optInt("total")){"File moved or is unavailable. Save a copy instead."};s.event("next")}}
 private fun trash(s:FileSelectionSession,path:List<String>,id:String){find(s,path,id);s.event("options:$id");s.event("trash");s.event("confirm.accept")}
 fun writable(c:Context,uri:Uri)=runCatching{val row=FileSelectionStore.read(c,uri);row.optString("kind")=="read"&&row.optString("entry").matches(Regex("[a-f0-9]{32}"))&&FileSelectionStore.paths(row).isNotEmpty()}.getOrDefault(false)
 fun promote(c:Context,uri:Uri):Uri{if(uri.authority!=c.packageName+".file-selection")return uri;val row=FileSelectionStore.read(c,uri);if(row.optString("kind")=="read")return uri;check(row.optString("state")=="saved");val path=FileSelectionStore.paths(row);val id=FileSelectionStore.file(c,row).name;return FileSelectionSession(c).use{s->s.start();val item=find(s,path,id);FileSelectionStore.selected(c,s.chooseAt(path,item),path)}}
 fun recover(c:Context):Pair<String,Uri>?{
  val f=journal(c);if(!f.exists())return null;require(f.length()<=65536);val j=JSONObject(f.readText());val path=FileSelectionStore.paths(j)
  return FileSelectionSession(c).use{s->s.start();val new=runCatching{find(s,path,j.getString("new"))}.getOrNull()
   if(new?.optString("name")==j.getString("name")){val uri=FileSelectionStore.selected(c,s.chooseAt(path,new),path);check(f.delete());j.getString("doc") to uri}
   else{val old=runCatching{find(s,path,j.getString("old"))}.getOrNull();if(old==null){find(s,listOf("trash"),j.getString("old"));s.event("options:"+j.getString("old"));s.event("restore")};if(new!=null)trash(s,path,j.getString("new"));check(f.delete());null}
  }
 }
 fun replace(c:Context,doc:String,uri:Uri,expected:String,bytes:ByteArray):Uri{
  check(!journal(c).exists()){ "Finish the interrupted save by reopening Code Edit first." };check(writable(c,uri)){"This file is read-only. Use Save as."}
  val row=FileSelectionStore.read(c,uri);val path=FileSelectionStore.paths(row);val old=row.getString("entry");val name=row.getString("name")
  check(digest(read(c,uri))==expected){"File changed elsewhere. Use Save as to keep your edits, then reopen it."}
  val temp=File.createTempFile("code-", ".tmp",c.cacheDir)
  try{temp.writeBytes(bytes);val created=FileSelectionSession(c).use{s->s.start();s.importSaved(path,temp,"Code-draft-${UUID.randomUUID().toString().take(8)}.txt",row.getString("mime"))}
   val j=JSONObject().put("doc",doc).put("folders",JSONArray(path)).put("old",old).put("new",created.getString("id")).put("name",name);atomic(journal(c),j)
   try{FileSelectionSession(c).use{s->s.start();val current=find(s,path,old);check(current.getString("name")==name&&digest(read(c,uri))==expected){"File changed elsewhere. Use Save as."};trash(s,path,old);find(s,path,created.getString("id"));s.event("options:"+created.getString("id"));s.event("rename");s.event("name",name);s.event("name.save")}}
   catch(e:Exception){runCatching{recover(c)};throw e}
   return requireNotNull(recover(c)).second
  }finally{temp.delete()}
 }
}
