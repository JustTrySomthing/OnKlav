package app.onklav.home

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class CodeDocument(val id:String=UUID.randomUUID().toString(),val name:String="Untitled.txt",val text:String="",val saved:String="",val uri:String="",val encoding:String="UTF-8",val bom:Boolean=false,val ending:String="LF",val language:String=CodeText.language(name),val start:Int=0,val end:Int=0){val dirty get()=runCatching{CodeEditFiles.digest(CodeText.encode(text,encoding,bom,ending))!=saved}.getOrDefault(true)}
internal class CodeEditModel(app:Application):AndroidViewModel(app){
 var documents by mutableStateOf(listOf<CodeDocument>());private set
 var selected by mutableStateOf("");private set
 var busy by mutableStateOf(false);private set
 var status by mutableStateOf("Drafts recover automatically; Save writes to Files.");private set
 var wrap by mutableStateOf(false)
 var font by mutableFloatStateOf(14f)
 var indent by mutableStateOf("    ")
 var highlight by mutableStateOf(true)
 val current get()=documents.firstOrNull{it.id==selected}
 private val undo=mutableMapOf<String,MutableList<CodeText.Edit>>();private val redo=mutableMapOf<String,MutableList<CodeText.Edit>>();private var lastEdit=0L
 var historyEpoch by mutableIntStateOf(0);private set
 val canUndo get()=undo[selected]?.isNotEmpty()==true
 val canRedo get()=redo[selected]?.isNotEmpty()==true
 private val draft=File(app.filesDir,"code-edit/workspace.json")
 private var draftJob:Job?=null
 private val disk=Dispatchers.IO.limitedParallelism(1)
 init{busy=true;viewModelScope.launch{try{val recovered=withContext(disk){CodeEditFiles.recover(app)};withContext(disk){if(draft.exists()){require(draft.length()<=16_000_000);JSONObject(android.util.AtomicFile(draft).openRead().bufferedReader().use{it.readText()})}else null}?.let{j->val a=j.getJSONArray("files");require(a.length()<=8);documents=List(a.length()){i->val d=a.getJSONObject(i);val text=d.getString("text");require(text.length<=CodeText.MAX_CHARS);CodeDocument(d.getString("id"),d.getString("name"),text,d.getString("saved"),d.optString("uri"),d.getString("encoding"),d.getBoolean("bom"),d.getString("ending"),d.getString("language"),d.optInt("start").coerceIn(0,text.length),d.optInt("end").coerceIn(0,text.length))};selected=j.optString("selected");wrap=j.optBoolean("wrap");font=j.optDouble("font",14.0).toFloat().coerceIn(10f,24f);indent=j.optString("indent","    ").takeIf{it in listOf("  ","    ","\t")}?:"    "}
    if(recovered!=null){documents=documents.map{if(it.id==recovered.first)CodeEditFiles.read(app,recovered.second).let{bytes->val decoded=CodeText.decode(bytes);it.copy(text=decoded.text,uri=recovered.second.toString(),saved=CodeEditFiles.digest(bytes),encoding=decoded.encoding,bom=decoded.bom,ending=decoded.ending,start=0,end=0)}else it};status="Interrupted save recovered"}
   }catch(e:Exception){status="Draft recovery needs attention: ${e.message}. Existing recovery data was kept.";recoveryFailed=true}finally{if(documents.isEmpty())documents=listOf(CodeDocument());if(current==null)selected=documents.first().id;busy=false}}}
 private var recoveryFailed=false
 private fun state()=JSONObject().put("files",JSONArray().apply{documents.forEach{d->put(JSONObject().put("id",d.id).put("name",d.name).put("text",d.text).put("saved",d.saved).put("uri",d.uri).put("encoding",d.encoding).put("bom",d.bom).put("ending",d.ending).put("language",d.language).put("start",d.start).put("end",d.end))}}).put("selected",selected).put("wrap",wrap).put("font",font.toDouble()).put("indent",indent)
 fun persist(){if(recoveryFailed)return;draftJob?.cancel();val snapshot=state();draftJob=viewModelScope.launch{delay(450);try{withContext(disk){CodeEditFiles.atomic(draft,snapshot)}}catch(e:Exception){if(e is CancellationException)throw e;status="Draft not saved: ${e.message}"}}}
 fun flush(){if(recoveryFailed)return;draftJob?.cancel();val snapshot=state();finalScope.launch(disk){runCatching{CodeEditFiles.atomic(draft,snapshot)}}}
 fun pick(id:String){if(busy)return;selected=id;lastEdit=0;historyEpoch++;persist()}
 fun new(name:String="Untitled.txt"){if(busy)return;if(documents.size>=8){status="Eight files are open. Close a tab first.";return};val d=CodeDocument(name=name);documents=documents+d;pick(d.id)}
 fun close(id:String){if(busy)return;documents=documents.filter{it.id!=id};undo.remove(id);redo.remove(id);if(documents.isEmpty())documents=listOf(CodeDocument());if(current==null)selected=documents.first().id;historyEpoch++;persist()}
 fun selection(a:Int,b:Int){val d=current?:return;if(d.start==a&&d.end==b)return;documents=documents.map{if(it.id==d.id)it.copy(start=a.coerceIn(0,d.text.length),end=b.coerceIn(0,d.text.length))else it}}
 fun change(text:String,a:Int,b:Int,group:Boolean=true){if(busy||text.length>CodeText.MAX_CHARS)return;val d=current?:return;if(text==d.text){selection(a,b);return};val now=android.os.SystemClock.uptimeMillis();val h=undo.getOrPut(d.id){mutableListOf()};if(!group||lastEdit==0L||now-lastEdit>900){h.add(CodeText.Edit(d.text,d.start,d.end));while(h.size>40||h.sumOf{it.text.length}>1_000_000)h.removeAt(0)};lastEdit=if(group)now else 0;redo[d.id]?.clear();documents=documents.map{if(it.id==d.id)it.copy(text=text,start=a.coerceIn(0,text.length),end=b.coerceIn(0,text.length))else it};historyEpoch++;persist()}
 fun apply(e:CodeText.Edit)=change(e.text,e.start,e.end,false)
 fun history(back:Boolean){if(busy)return;val d=current?:return;val a=(if(back)undo else redo).getOrPut(d.id){mutableListOf()};val b=(if(back)redo else undo).getOrPut(d.id){mutableListOf()};if(a.isEmpty())return;b.add(CodeText.Edit(d.text,d.start,d.end));val e=a.removeAt(a.lastIndex);documents=documents.map{if(it.id==d.id)it.copy(text=e.text,start=e.start,end=e.end)else it};lastEdit=0;historyEpoch++;persist()}
 fun language(name:String){documents=documents.map{if(it.id==selected)it.copy(language=name)else it};persist()}
 private fun task(block:suspend()->Unit){if(busy)return;busy=true;viewModelScope.launch{try{block()}catch(e:Exception){if(e is CancellationException)throw e;status=e.message?:"Unable to complete"}finally{busy=false}}}
 fun open(uri:Uri){if(documents.size>=8){status="Close a tab before opening another file.";return};documents.firstOrNull{it.uri==uri.toString()}?.let{pick(it.id);return};task{
  val c=getApplication<Application>();val result=withContext(disk){require(uri.scheme=="content"){"Choose a source file in Files."};if(uri.authority==c.packageName+".file-selection"){val row=FileSelectionStore.read(c,uri);require(FileSelectionStore.paths(row).none{AppSdk.folderVault(c,"status",it).optBoolean("encrypted")}){"Copy this file outside the protected folder before editing it."}};val bytes=CodeEditFiles.read(c,uri);val decoded=CodeText.decode(bytes);val name=c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}?:"Source.txt";CodeDocument(name=name.take(120),text=decoded.text,saved=CodeEditFiles.digest(bytes),uri=uri.toString(),encoding=decoded.encoding,bom=decoded.bom,ending=decoded.ending)}
  documents=documents+result;selected=result.id;status=if(result.dirty)"Opened ${result.name} · mixed line endings normalize to LF on save"else"Opened ${result.name}";persist()
 }}
 fun canSaveBack()=current?.uri?.takeIf{it.isNotBlank()}?.let{CodeEditFiles.writable(getApplication(),Uri.parse(it))}==true
 fun save(target:Uri?=null){val d=current?:return;task{val bytes=CodeText.encode(d.text,d.encoding,d.bom,d.ending);val c=getApplication<Application>();val uri=if(target!=null){writeFiledExport(c,target){it.write(bytes)};withContext(disk){CodeEditFiles.promote(c,target)}}else withContext(disk){CodeEditFiles.replace(c,d.id,Uri.parse(d.uri),d.saved,bytes)}
  val name=c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else null}?:d.name
  documents=documents.map{if(it.id==d.id)it.copy(uri=uri.toString(),name=name,saved=CodeEditFiles.digest(bytes),language=if(d.language=="Plain text")CodeText.language(name)else d.language)else it};status="Saved · $name";persist()
 }}
 fun validateJson(){val text=current?.text?:return;task{status="Checking JSON…";status=withContext(Dispatchers.Default){runCatching{CodeText.validateJson(text);"Valid JSON"}.getOrElse{"JSON: ${it.message}"}}}}
 fun message(text:String){status=text}
 override fun onCleared(){flush();super.onCleared()}
 companion object{private val finalScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)}
}
