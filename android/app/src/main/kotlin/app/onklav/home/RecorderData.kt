package app.onklav.home

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

internal enum class RecorderKind(val title:String,val tool:String,val folder:String,val root:String){VOICE("Voice Record","voicerecorder","Voice Recorder","audio"),SCREEN("Screen Record","screenrecorder","Screen Recorder","video")}
internal enum class RecorderSound(val label:String){NONE("Silent"),DEVICE("Device audio"),MIC("Microphone"),BOTH("Device + microphone")}
internal enum class RecorderFormat(val label:String,val extension:String,val mime:String){AAC("M4A · compact","m4a","audio/mp4"),WAV("WAV · lossless","wav","audio/wav")}
internal data class RecorderOptions(val kind:RecorderKind,val sound:RecorderSound=if(kind==RecorderKind.VOICE)RecorderSound.MIC else RecorderSound.DEVICE,val format:RecorderFormat=RecorderFormat.AAC,val high:Boolean=false,val countdown:Int=3){
 val microphone get()=kind==RecorderKind.VOICE||sound==RecorderSound.MIC||sound==RecorderSound.BOTH
 val audio get()=kind==RecorderKind.VOICE||sound!=RecorderSound.NONE
 val extension get()=if(kind==RecorderKind.SCREEN)"mp4" else format.extension
 val mime get()=if(kind==RecorderKind.SCREEN)"video/mp4"else format.mime
}
internal enum class RecorderPhase{IDLE,STARTING,COUNTDOWN,RECORDING,PAUSED,FINISHING}
internal data class RecorderState(val token:String="",val options:RecorderOptions?=null,val phase:RecorderPhase=RecorderPhase.IDLE,val elapsed:Long=0,val countdown:Int=0,val levels:List<Float> = emptyList(),val bytes:Long=0,val message:String="",val error:String?=null,val revision:Long=0,val controlling:Boolean=false){val active get()=phase!=RecorderPhase.IDLE}
internal fun recorderTime(ms:Long):String{val s=ms.coerceAtLeast(0)/1000;return if(s>=3600)String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60)else String.format(Locale.ROOT,"%02d:%02d",s/60,s%60)}
internal class RecorderClock(private val now:()->Long={android.os.SystemClock.elapsedRealtime()}){
 private var total=0L;private var start=0L;private var running=false
 @Synchronized fun resume(){if(!running){start=now();running=true}}
 @Synchronized fun pause(){if(running){total+=(now()-start).coerceAtLeast(0);running=false}}
 @Synchronized fun elapsed()=total+if(running)(now()-start).coerceAtLeast(0)else 0
}
internal fun recorderDimensions(width:Int,height:Int,high:Boolean):Pair<Int,Int>{require(width>0&&height>0);val scale=min(1.0,(if(high)1920.0 else 1280.0)/max(width,height));return (max(16,(width*scale/16).toInt()*16) to max(16,(height*scale/16).toInt()*16))}
internal fun recorderLevel(amplitude:Int):Float=if(amplitude<=0)0f else ((20*log10(amplitude.coerceAtMost(32767)/32767.0)+60)/60).toFloat().coerceIn(0f,1f)
/** Balanced stereo soundtrack + mono narration, without integer overflow or clipping. */
internal fun recorderMix(device:ShortArray,mic:ShortArray,count:Int):ShortArray{require(count<=device.size&&count%2==0&&mic.size>=count/2);return ShortArray(count){((device[it].toInt()+mic[it/2].toInt())/2).toShort()}}

internal data class RecorderItem(val id:String,val kind:RecorderKind,val name:String,val mime:String,val bytes:Long,val duration:Long=0,val created:Long=0,val pending:Boolean=false,val interrupted:Boolean=false,val warning:String=""){
 val title get()=name.substringBeforeLast('.')
}
/** Recorded media becomes ordinary Files content, so existing Files/App Documents
 * backups include it. Only unfiled/interrupted drafts live in this private directory. */
internal object RecorderLibrary{
 private val lock=Any()
 fun directory(c:Context)=File(c.filesDir,"recording-drafts").also{check(!java.nio.file.Files.isSymbolicLink(it.toPath())&&(it.isDirectory||it.mkdirs()))}
 private fun valid(id:String)=id.also{require(it.matches(Regex("[a-f0-9]{32}")))}
 fun file(c:Context,id:String,suffix:String)=File(directory(c),"${valid(id)}.$suffix")
 private fun meta(c:Context,id:String)=AtomicFile(file(c,id,"json"))
 private fun write(c:Context,id:String,j:JSONObject){val a=meta(c,id);val out=a.startWrite();try{out.write(j.toString().toByteArray());a.finishWrite(out)}catch(e:Exception){a.failWrite(out);throw e}}
 fun create(c:Context,id:String,o:RecorderOptions):JSONObject=synchronized(lock){val stamp=SimpleDateFormat("yyyy-MM-dd HH.mm.ss",Locale.ROOT).format(Date());val name="${if(o.kind==RecorderKind.VOICE)"Voice"else"Screen"} $stamp ${id.take(8)}.${o.extension}";JSONObject().put("kind",o.kind.name).put("sound",o.sound.name).put("name",name).put("mime",o.mime).put("created",System.currentTimeMillis()).put("duration",0L).put("state","recording").put("extension",o.extension).also{write(c,id,it)}}
 fun read(c:Context,id:String):JSONObject=synchronized(lock){val a=meta(c,id);require(a.baseFile.length()<=16384);a.openRead().use{JSONObject(it.readBytes().toString(Charsets.UTF_8))}}
 fun ready(c:Context,id:String,duration:Long,warning:String=""){synchronized(lock){val j=read(c,id).put("duration",duration).put("warning",warning).put("state","ready");write(c,id,j)}}
 fun draft(c:Context,id:String)=file(c,id,"ready."+read(c,id).getString("extension"))
 fun removeDraft(c:Context,id:String)=synchronized(lock){valid(id);directory(c).listFiles().orEmpty().filter{it.name.startsWith("$id.")}.forEach{check(it.delete()||!it.exists()){ "Could not remove recording" }}}
 private fun rows(session:FileSelectionSession):List<JSONObject>{val out=mutableListOf<JSONObject>();do{out+=session.state.nodes("items");if(session.state.optInt("offset")+40>=session.state.optInt("total"))break;session.event("next")}while(out.size<10000);return out}
 private fun folder(c:Context,s:FileSelectionSession,k:RecorderKind,create:Boolean):List<String>?{
  s.navigate(listOf(k.root));val prefs=c.getSharedPreferences("recorder-folders",0);val known=prefs.getString(k.name,null)
  val available=rows(s);var found=available.firstOrNull{it.optBoolean("folder")&&it.optString("id")==known}?:available.firstOrNull{it.optBoolean("folder")&&it.optString("name")==k.folder}
  if(found==null&&create){s.event("new.folder");s.event("name",k.folder);s.event("name.save");s.navigate(listOf(k.root));found=rows(s).firstOrNull{it.optBoolean("folder")&&it.optString("name")==k.folder}}
  return found?.getString("id")?.let{prefs.edit().putString(k.name,it).apply();listOf(k.root,it)}
 }
 private fun metadata(c:Context)=c.getSharedPreferences("recorder-metadata",0)
 private fun item(c:Context,k:RecorderKind,j:JSONObject):RecorderItem{val extra=runCatching{JSONObject(metadata(c).getString(j.getString("id"),"{}")!!)}.getOrDefault(JSONObject());return RecorderItem(j.getString("id"),k,j.getString("name"),j.optString("mime"),j.optLong("bytes"),extra.optLong("duration"),extra.optLong("created"),warning=extra.optString("warning"))}
 fun list(c:Context,k:RecorderKind):List<RecorderItem>{val pending=synchronized(lock){directory(c).listFiles().orEmpty().filter{it.extension=="json"}.mapNotNull{f->runCatching{val id=f.nameWithoutExtension;val j=read(c,id);if(j.getString("kind")!=k.name||RecordingRuntime.state.value.let{it.active&&it.token==id})null else RecorderItem(id,k,j.getString("name"),j.getString("mime"),draft(c,id).takeIf{it.isFile}?.length()?:directory(c).listFiles().orEmpty().filter{it.name.startsWith("$id.")&&it.extension!="json"}.sumOf{it.length()},j.optLong("duration"),j.optLong("created"),true,j.optString("state")!="ready",j.optString("warning"))}.getOrNull()}}
  val saved=FileSelectionSession(c).use{s->s.start();val path=folder(c,s,k,false);if(path==null)emptyList()else{s.navigate(path);rows(s).filter{!it.optBoolean("folder")&&it.optString("mime").startsWith(if(k==RecorderKind.VOICE)"audio/"else"video/")}.map{item(c,k,it)}}}
  return (pending+saved).sortedWith(compareByDescending<RecorderItem>{it.pending}.thenByDescending{it.created}.thenByDescending{it.name})
 }
 fun publish(c:Context,id:String):RecorderItem=synchronized(lock){val j=read(c,id);check(j.getString("state")=="ready");val k=RecorderKind.valueOf(j.getString("kind"));val source=draft(c,id);check(source.isFile&&source.length()>0)
  FileSelectionSession(c).use{s->s.start();val path=checkNotNull(folder(c,s,k,true));s.navigate(path)
   // A retry after the import receipt was committed must not create duplicates.
   val existing=rows(s).firstOrNull{it.optString("name")==j.getString("name")&&it.optLong("bytes")==source.length()}
   val saved=if(existing!=null){val descriptor=s.chooseAt(path,existing);val target=File(descriptor.getString("path"));check(sha(target)==sha(source)){"A different file has this recording name. Rename the draft before saving."};existing}else s.importSaved(path,source,j.getString("name"),j.getString("mime"))
   val recordId=saved.getString("id");check(metadata(c).edit().putString(recordId,j.toString()).commit()){ "Recording was filed. Retry to finish updating its details." };val result=RecorderItem(recordId,k,saved.optString("name",j.getString("name")),j.getString("mime"),source.length(),j.optLong("duration"),j.optLong("created"),warning=j.optString("warning"));removeDraft(c,id);result
  }
 }
 private fun sha(f:File):String{val d=java.security.MessageDigest.getInstance("SHA-256");f.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;d.update(b,0,n)}};return d.digest().joinToString(""){"%02x".format(it)}}
 fun resolve(c:Context,k:RecorderKind,id:String,pending:Boolean):Pair<File,RecorderItem>{valid(id)
  if(pending){val j=read(c,id);check(j.optString("state")=="ready"&&j.getString("kind")==k.name){"This recording needs recovery first"};val file=draft(c,id);check(file.isFile&&!java.nio.file.Files.isSymbolicLink(file.toPath()));return file to RecorderItem(id,k,j.getString("name"),j.getString("mime"),file.length(),j.optLong("duration"),j.optLong("created"),true)}
  return FileSelectionSession(c).use{s->s.start();val path=folder(c,s,k,false)?:error("Recording folder moved. Open it in Files.");s.navigate(path);val row=rows(s).firstOrNull{it.optString("id")==id&&!it.optBoolean("folder")}?:error("Recording moved or was deleted. Refresh the list.");val descriptor=s.chooseAt(path,row);val f=File(descriptor.getString("path"));check(f.canonicalFile.toPath().startsWith(File(c.filesDir,"onklav-content").canonicalFile.toPath())&&!java.nio.file.Files.isSymbolicLink(f.toPath())&&f.isFile);f to item(c,k,row)}
 }
 fun rename(c:Context,item:RecorderItem,title:String){val cleaned=FilesBroker.cleanName(title).take(80);require(title.isNotBlank()&&cleaned.isNotBlank());val name="$cleaned.${item.name.substringAfterLast('.')}"
  if(item.pending){synchronized(lock){write(c,item.id,read(c,item.id).put("name",name))};return}
  FileSelectionSession(c).use{s->s.start();s.navigate(checkNotNull(folder(c,s,item.kind,false)));while(s.state.nodes("items").none{it.optString("id")==item.id}){check(s.state.optInt("offset")+40<s.state.optInt("total")){"Recording moved or was deleted"};s.event("next")};s.event("options:${item.id}");s.event("rename");s.event("name",name);s.event("name.save")}
 }
 fun delete(c:Context,item:RecorderItem){if(item.pending){removeDraft(c,item.id);return};FileSelectionSession(c).use{s->s.start();s.navigate(checkNotNull(folder(c,s,item.kind,false)));while(s.state.nodes("items").none{it.optString("id")==item.id}){check(s.state.optInt("offset")+40<s.state.optInt("total")){"Recording moved or was deleted"};s.event("next")};s.event("options:${item.id}");s.event("trash");s.event("confirm.accept")}}
 fun uri(c:Context,item:RecorderItem)=Uri.parse("content://${c.packageName}.recordings/${item.kind.name}/${if(item.pending)"draft"else"file"}/${item.id}")
}
