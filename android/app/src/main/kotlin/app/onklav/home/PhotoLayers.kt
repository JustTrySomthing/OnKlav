package app.onklav.home

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Portable local image project. Entry names, uncompressed sizes and edits are validated before opening. */
internal object PhotoLayers {
 const val MIME="application/x-onklav-layers"
 data class State(val edit:PhotoEdit,val undo:List<PhotoEdit>,val redo:List<PhotoEdit>){
  fun json()=JSONObject().put("format","OnKlav Layers").put("version",1).put("edit",JSONObject(edit.json())).put("undo",JSONArray(undo.map{JSONObject(it.json())})).put("redo",JSONArray(redo.map{JSONObject(it.json())}))
 }
 fun parse(text:String):State{
  require(text.length<=8*1024*1024){"Layers history is too large"};val j=JSONObject(text)
  require(j.getString("format")=="OnKlav Layers"&&j.getInt("version")==1){"Unsupported Layers project version"}
  fun edits(key:String):List<PhotoEdit>{val a=j.getJSONArray(key);require(a.length()<=60);return (0 until a.length()).map{PhotoEdit.parse(a.getJSONObject(it).toString())}}
  val state=State(PhotoEdit.parse(j.getJSONObject("edit").toString()),edits("undo"),edits("redo"))
  require((state.undo+state.redo).sumOf{it.json().length}<=4*1024*1024){"Layers history is too large"};return state
 }
 fun isProject(file:File)=file.inputStream().use{it.read()==80&&it.read()==75}
 fun write(file:File,source:File,state:State,check:()->Unit){
  val manifest=state.json().toString();parse(manifest);require(source.length() in 1..150_000_000)
  ZipOutputStream(file.outputStream().buffered()).use{z->z.putNextEntry(ZipEntry("project.json"));z.write(manifest.toByteArray());z.closeEntry();z.putNextEntry(ZipEntry("source"));source.inputStream().use{FilesBroker.copyBoundedLimit(it,z,150_000_000,check)};z.closeEntry()};check()
 }
 fun read(file:File,source:File,check:()->Unit):State=ZipFile(file).use{z->
  val entries=z.entries().asSequence().take(3).toList();require(entries.size==2&&entries.map{it.name}.toSet()==setOf("source","project.json")){"This is not a Layers project"}
  fun copy(name:String,target:File,limit:Long){val e=z.getEntry(name);require(!e.isDirectory&&e.size in 1..limit){"Invalid Layers entry"};z.getInputStream(e).use{input->target.outputStream().use{FilesBroker.copyBoundedLimit(input,it,limit,check)}}}
  val manifest=File(source.parentFile,"layers-manifest-${java.util.UUID.randomUUID()}")
  try{copy("project.json",manifest,8L*1024*1024);val state=parse(manifest.readText());copy("source",source,150_000_000);check();state}finally{manifest.delete()}
 }
}
