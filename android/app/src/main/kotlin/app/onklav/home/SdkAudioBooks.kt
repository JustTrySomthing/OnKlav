package app.onklav.home

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.media.MediaDataSource
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.*
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Private book storage and scoped source broker. Guest frames never receive local paths or source URLs. */
internal object SdkAudioBooks {
 val changes=MutableStateFlow(0L)
 val saveErrors=MutableStateFlow<Map<String,String>>(emptyMap())
 private val lock=Any()
 private val activeImports=ConcurrentHashMap.newKeySet<String>()
 private val leases=ConcurrentHashMap<String,JSONObject>()
 private val cancelled=ConcurrentHashMap.newKeySet<String>()
 private val calls=ConcurrentHashMap<String,Call>()
 private val deletedApps=ConcurrentHashMap.newKeySet<String>()
 private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
  .connectTimeout(12,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).callTimeout(35,TimeUnit.SECONDS).dns(object:Dns{override fun lookup(hostname:String)=SdkFeedService.publicAddresses(hostname)}).build()
 private val catalogClient by lazy{client.newBuilder().connectTimeout(6,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS).build()}
 fun authorize(c:Context,o:JSONObject){AppSdk.nativeTask(c,JSONObject().put("action","audiobook_authorize").put("owner",o).put("args",JSONObject()))}
 private fun root(c:Context,id:String):File {require(id.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+"))&&id.length<=128);return File(c.filesDir,"sdk-audiobooks/$id")}
 private fun root(c:Context,o:JSONObject):File {val d=root(c,o.getString("id"));d.mkdirs();val marker=File(d,"publisher");if(marker.exists())check(marker.readText()==o.getString("publisher")){"Saved books belong to another publisher"}else marker.writeText(o.getString("publisher"));return d}
 private fun index(d:File):JSONArray=if(File(d,"library.json").exists())JSONArray(AtomicFile(File(d,"library.json")).openRead().bufferedReader().use{it.readText()})else JSONArray()
 private fun write(d:File,a:JSONArray){val f=AtomicFile(File(d,"library.json"));val out=f.startWrite();try{out.write(a.toString().toByteArray());f.finishWrite(out)}catch(e:Exception){f.failWrite(out);throw e};changes.value++}
 private fun id(s:String):String {require(s.matches(Regex("[a-zA-Z0-9-]{1,80}")));return s}
 private fun book(d:File,key:String)=index(d).objects().firstOrNull{it.getString("id")==id(key)}?:error("Book no longer in library")
 private fun prefs(c:Context,o:JSONObject)=c.getSharedPreferences("audiobooks-progress-"+o.getString("id"),0)
 fun progress(c:Context,o:JSONObject,key:String)=JSONObject(prefs(c,o).getString(id(key),"{}")?:"{}")
 fun library(c:Context,o:JSONObject):List<JSONObject>{authorize(c,o);return synchronized(lock){deletedApps.remove(o.getString("id"));root(c,o).also{d->val ids=index(d).objects().map{it.getString("id")}.toSet();d.listFiles()?.filter{(it.name.startsWith(".import-")&&it.name !in activeImports)||(it.isDirectory&&it.name.startsWith("local-")&&it.name !in ids)}?.forEach{it.deleteRecursively()}}.let{index(it).objects().map{row->JSONObject(row.toString())}}}}
 fun get(c:Context,o:JSONObject,key:String):JSONObject {authorize(c,o);return synchronized(lock){JSONObject(book(root(c,o),key).toString())}}
 private fun update(c:Context,o:JSONObject,key:String,fn:(JSONObject)->Unit)=AppSdk.carrierEgress{authorize(c,o);synchronized(lock){val d=root(c,o);val a=index(d);val b=a.objects().firstOrNull{it.getString("id")==id(key)}?:error("Book removed");fn(b);write(d,a)}}
 fun rename(c:Context,o:JSONObject,key:String,title:String,author:String){require(title.trim().isNotEmpty());update(c,o,key){it.put("title",clean(title,200)).put("author",clean(author,200))}}
 fun finished(c:Context,o:JSONObject,key:String,done:Boolean)=AppSdk.carrierEgress{authorize(c,o);get(c,o,key);val p=progress(c,o,key).put("finished",done).put("updated",System.currentTimeMillis());check(prefs(c,o).edit().putString(key,p.toString()).commit());changes.value++}
 fun remove(c:Context,o:JSONObject,key:String)=AppSdk.carrierEgress{authorize(c,o);synchronized(lock){val d=root(c,o);val kept=JSONArray(index(d).objects().filter{it.getString("id")!=id(key)});write(d,kept);check(prefs(c,o).edit().remove(key).commit());File(d,id(key)).deleteRecursively();leases.entries.removeAll{it.value.optString("book")==key&&it.value.getJSONObject("owner").optString("id")==o.optString("id")}};cancelDownload(o.getString("id"),key);SdkMedia.changed(c,o.getString("id"))}
 fun clearApp(c:Context,key:String){deletedApps.add(key);invalidate(key);synchronized(lock){root(c,key).deleteRecursively();check(c.getSharedPreferences("audiobooks-progress-$key",0).edit().clear().commit())}}
 fun invalidate(key:String){leases.entries.removeAll{key.isEmpty()||it.value.getJSONObject("owner").optString("id")==key};calls.filterKeys{key.isEmpty()||it.startsWith("$key/")}.values.forEach{it.cancel()};SdkAudioBookDownloadService.cancelApp(key)}
 fun cancel(token:String){cancelled.add(token);calls.remove(token)?.cancel()}
 private fun check(c:Context,o:JSONObject,token:String){kotlin.check(token !in cancelled){"Cancelled"};authorize(c,o)}
 fun import(c:Context,o:JSONObject,uris:List<Uri>,token:String,folderTitle:String=""):JSONObject {
  require(uris.isNotEmpty()&&uris.size<=2000){"Choose up to 2,000 chapter files per book"};check(c,o,token)
  val key="local-"+UUID.randomUUID();val d=root(c,o);val stage=File(d,".import-$key");activeImports.add(stage.name);stage.mkdirs()
  try{
   val selected=uris.map{uri->var n="Audio";c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())n=it.getString(0)?:n};uri to n}.sortedWith{a,b->naturalCompare(a.second,b.second)}
   val tracks=JSONArray();var title=folderTitle;var author=""
   for((uri,name) in selected){check(c,o,token);val ext=name.substringAfterLast('.',"").lowercase();require(ext in setOf("mp3","m4b","m4a","aac","ogg","opus","flac","wav","mp4")){"Unsupported file: ${clean(name,80)}. Choose DRM-free audio files."}
    val tid=UUID.randomUUID().toString();val file=File(stage,"$tid.$ext");var bytes=0L
    c.contentResolver.openInputStream(uri)?.use{input->file.outputStream().use{out->val buffer=ByteArray(131072);while(true){val n=input.read(buffer);if(n<0)break;check(c,o,token);kotlin.check(d.usableSpace>n+8L*1024*1024){"Not enough free storage"};out.write(buffer,0,n);bytes+=n}}}?:error("File could not be opened")
    require(bytes>0){"Audio file is empty"};val meta=MediaMetadataRetriever()
    val track=JSONObject().put("id",tid).put("file",file.name).put("title",clean(name.substringBeforeLast('.'),200)).put("duration_ms",0)
    try{meta.setDataSource(file.path);track.put("duration_ms",meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0);meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf{it.isNotBlank()}?.let{track.put("title",clean(it,200))};if(title.isEmpty())title=meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty();if(author.isEmpty())author=meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)?:meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()}catch(e:Exception){throw IllegalArgumentException("Cannot read ${clean(name,80)}. Use a supported DRM-free audio file.")}finally{meta.release()}
    track.put("markers",SdkAudioBookChapters.read(file));tracks.put(track)
   }
   if(title.isBlank())title=selected.first().second.substringBeforeLast('.');val b=JSONObject().put("id",key).put("title",clean(title,200)).put("author",clean(author,200)).put("source","Imported").put("tracks",tracks).put("bookmarks",JSONArray()).put("added",System.currentTimeMillis())
   AppSdk.carrierEgress{check(c,o,token);synchronized(lock){val a=index(d);kotlin.check(stage.renameTo(File(d,key))){"Import could not be saved"};try{a.put(b);write(d,a);deletedApps.remove(o.getString("id"))}catch(e:Exception){File(d,key).deleteRecursively();throw e}}};return b
  }finally{stage.deleteRecursively();activeImports.remove(stage.name);cancelled.remove(token)}
 }
 fun folder(c:Context,uri:Uri):Pair<String,List<Uri>> {
  if(uri.authority==c.packageName+".file-selection")return FileSelectionStore.folderEntries(c,uri)

  val tree=DocumentsContract.getTreeDocumentId(uri);val children=DocumentsContract.buildChildDocumentsUriUsingTree(uri,tree);val out=mutableListOf<Uri>();var title="Audiobook"
  c.contentResolver.query(DocumentsContract.buildDocumentUriUsingTree(uri,tree),arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())title=it.getString(0)}
  c.contentResolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use{cursor->while(cursor.moveToNext()){if(cursor.getString(1).substringAfterLast('.',"").lowercase() in setOf("mp3","m4b","m4a","aac","ogg","opus","flac","wav","mp4")){require(out.size<2000){"Choose a folder with at most 2,000 chapter files"};out+=DocumentsContract.buildDocumentUriUsingTree(uri,cursor.getString(0))}}}
  require(out.isNotEmpty()){"No supported audio files in this folder. Choose the folder containing the chapters."};return title to out
 }
 fun recorded(c:Context,o:JSONObject,item:JSONObject,position:Long,duration:Long,speed:Float,done:Boolean=false){if(!item.has("audiobook_track")||o.getString("id") in deletedApps||(!done&&position<=0&&duration<=0))return;runCatching{AppSdk.carrierEgress{authorize(c,o);synchronized(lock){val key=item.getString("audiobook_book");if(index(root(c,o)).objects().none{it.getString("id")==key})return@carrierEgress;val p=progress(c,o,key).put("index",item.getInt("audiobook_index")).put("position_ms",position).put("duration_ms",duration).put("speed",speed).put("finished",done).put("listened",true).put("updated",System.currentTimeMillis());kotlin.check(prefs(c,o).edit().putString(key,p.toString()).commit()){ "Listening position could not be saved" }}}}.onSuccess{saveErrors.value=saveErrors.value-o.getString("id")}.onFailure{saveErrors.value=saveErrors.value+(o.getString("id") to "Listening position could not be saved. Reopen AudioBooks and retry.")}}
 fun bookmark(c:Context,o:JSONObject,key:String,index:Int,position:Long,label:String){update(c,o,key){val a=it.getJSONArray("bookmarks");require(index in 0 until it.getJSONArray("tracks").length()&&position>=0);a.put(JSONObject().put("id",UUID.randomUUID().toString()).put("index",index).put("position_ms",position).put("label",clean(label.ifBlank{"Bookmark"},120)))}}
 fun deleteBookmark(c:Context,o:JSONObject,key:String,mark:String)=update(c,o,key){it.put("bookmarks",JSONArray(it.getJSONArray("bookmarks").objects().filter{b->b.getString("id")!=mark}))}
 fun reorder(c:Context,o:JSONObject,key:String,from:Int,to:Int)=update(c,o,key){val tracks=it.getJSONArray("tracks").objects().toMutableList();require(from in tracks.indices&&to in tracks.indices);val row=tracks.removeAt(from);tracks.add(to,row);it.put("tracks",JSONArray(tracks));val marks=it.getJSONArray("bookmarks");marks.objects().forEach{m->val old=m.getInt("index");m.put("index",when{old==from->to;from<to&&old in from+1..to->old-1;from>to&&old in to until from->old+1;else->old})};val p=progress(c,o,key);val old=p.optInt("index");p.put("index",when{old==from->to;from<to&&old in from+1..to->old-1;from>to&&old in to until from->old+1;else->old});prefs(c,o).edit().putString(key,p.toString()).apply();SdkMedia.changed(c,o.getString("id"))}
 fun start(c:Context,o:JSONObject,key:String,track:Int?=null,at:Long?=null):JSONObject {
  authorize(c,o);val b=get(c,o,key);val tracks=b.getJSONArray("tracks");val p=progress(c,o,key);val queue=sourceQueue(c,o,key,b)
  val ix=(track?:if(p.optBoolean("finished"))0 else p.optInt("index")).coerceIn(0,tracks.length()-1);val pos=at?:if(p.optBoolean("finished"))0 else (p.optLong("position_ms")-5000).coerceAtLeast(0)
  return SdkMedia.execute(c,JSONObject().put("service","media.play").put("owner",o).put("args",JSONObject().put("queue",queue).put("index",ix).put("position_ms",pos).put("speed",p.optDouble("speed",1.0)))){authorize(c,o)}
 }
 internal fun sourceQueue(c:Context,o:JSONObject,key:String,b:JSONObject=get(c,o,key)):JSONArray {
  authorize(c,o);leases.entries.removeAll{it.value.getJSONObject("owner").optString("id")==o.optString("id")};val queue=JSONArray();val tracks=b.getJSONArray("tracks")
  for(i in 0 until tracks.length()){val t=tracks.getJSONObject(i);val handle=UUID.randomUUID().toString();leases[handle]=JSONObject().put("owner",JSONObject(o.toString())).put("book",key).put("track",JSONObject(t.toString()));queue.put(JSONObject().put("audiobook_track",handle).put("audiobook_book",key).put("audiobook_index",i).put("title",b.getString("title")+" · "+t.getString("title")))};return queue
 }
 fun policy(c:Context,o:JSONObject,item:JSONObject):JSONObject {
  val h=item.getString("audiobook_track");val lease=leases[h]?:error("Book playback expired. Open the book and tap Resume.");kotlin.check(listOf("id","publisher","hash").all{o.optString(it)==lease.getJSONObject("owner").optString(it)}){"Book owner changed"}
  val auth=AppSdk.nativeTask(c,JSONObject().put("action","media_authorize").put("owner",o).put("args",JSONObject().put("audiobook_track",h)));get(c,o,lease.getString("book"));return auth
 }
 internal fun localPlaybackFile(c:Context,o:JSONObject,item:JSONObject):File? {
  policy(c,o,item);val l=leases[item.getString("audiobook_track")]?:error("Book playback expired");val key=l.getString("book");val tid=l.getJSONObject("track").getString("id");val track=get(c,o,key).getJSONArray("tracks").objects().first{it.getString("id")==tid};val name=track.optString("file");if(name.isEmpty())return null
  val parent=File(root(c,o),id(key)).canonicalFile;return File(parent,name).canonicalFile.also{kotlin.check(it.parentFile==parent&&it.isFile)}
 }
 fun open(c:Context,o:JSONObject,item:JSONObject):MediaDataSource {
  policy(c,o,item);val l=leases[item.getString("audiobook_track")]!!;val track=l.getJSONObject("track");val key=l.getString("book");val current=get(c,o,key).getJSONArray("tracks").objects().first{it.getString("id")==track.getString("id")};val name=current.optString("file");val file=if(name.isEmpty())null else File(File(root(c,o),id(key)),name).canonicalFile
  if(file!=null){kotlin.check(file.parentFile==File(root(c,o),id(key)).canonicalFile&&file.isFile);return object:MediaDataSource(){val input=RandomAccessFile(file,"r");val length=input.length();@Synchronized override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int{policy(c,o,item);require(position>=0&&offset>=0&&size>=0&&offset.toLong()+size<=buffer.size);if(size==0)return 0;if(position>=length)return -1;input.seek(position);return input.read(buffer,offset,minOf(size,65536,(length-position).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))};override fun getSize()=length;override fun close(){input.close()}}}
  val request by lazy { resolve(track.getString("url"),"${o.getString("id")}/stream"){policy(c,o,item)} };return SdkMediaSources.Remote(JSONObject(),{policy(c,o,item)},build={request},checkAccount={},probeFirst=true,allowUnvalidatedRange=true)
 }
 fun cancelDownload(app:String,key:String){val token="$app/download/$key";cancel(token);SdkAudioBookDownloadService.cancel(token)}
 fun downloadToken(o:JSONObject,key:String)="${o.getString("id")}/download/$key"
 fun download(c:Context,o:JSONObject,key:String,connect:(Request)->Call={client.newCall(it)},notify:(String)->Unit){
  val token=downloadToken(o,key);cancelled.remove(token);val b=get(c,o,key);val tracks=b.getJSONArray("tracks");val folder=File(root(c,o),id(key));folder.mkdirs()
  try{for(i in 0 until tracks.length()){
   check(c,o,token);val t=tracks.getJSONObject(i);if(t.optString("file").isNotEmpty()&&File(folder,t.getString("file")).isFile)continue
   val target=File(folder,id(t.getString("id"))+".mp3");val part=File(folder,target.name+".part");part.delete()
   try{val req=resolve(t.getString("url"),token,connect){check(c,o,token)};val call=connect(req);calls[token]=call;call.execute().use{res->require(res.code==200){"Download unavailable (${res.code}); try again later"};val body=res.body?:error("Empty download");require(body.contentType()?.type !in setOf("text","image")){"Source did not return audio"};val total=body.contentLength();var bytes=0L;require(total!=0L);if(total>0)kotlin.check(folder.usableSpace>total+8L*1024*1024){"Not enough free storage"};body.byteStream().use{input->part.outputStream().use{out->val buffer=ByteArray(131072);var last=0L;while(true){val n=input.read(buffer);if(n<0)break;check(c,o,token);kotlin.check(folder.usableSpace>n+8L*1024*1024){"Not enough free storage"};out.write(buffer,0,n);bytes+=n;val now=android.os.SystemClock.elapsedRealtime();if(now-last>1000){last=now;notify("Chapter ${i+1}/${tracks.length()} · ${bytes/1048576} MB")}}}};kotlin.check(bytes>0&&(total<0||bytes==total)){"Incomplete download; completed chapters are kept"}}
    update(c,o,key){live->kotlin.check(part.renameTo(target));live.getJSONArray("tracks").getJSONObject(i).put("file",target.name)};notify("Chapter ${i+1}/${tracks.length()} saved")
   }finally{part.delete();calls.remove(token)?.cancel()}
  }}finally{cancelled.remove(token);changes.value++}
 }
 internal fun publicUrl(raw:String):String {
  val u=raw.toHttpUrl();val host=u.host.lowercase();require(u.username.isEmpty()&&u.password.isEmpty()&&u.port==443&&u.scheme=="https"&&u.fragment==null){"Use a public HTTPS source"}
  require(host=="librivox.org"||host.endsWith(".librivox.org")||host=="archive.org"||host.endsWith(".archive.org")){"Only LibriVox and Internet Archive audio sources are supported"};return u.toString()
 }
 private fun upgrade(raw:String):String=publicUrl(if(raw.startsWith("http://"))"https://"+raw.removePrefix("http://")else raw)
 internal fun resolve(raw:String,token:String,connect:(Request)->Call={client.newCall(it)},verify:()->Unit):Request {var url=upgrade(raw);repeat(6){verify();val req=Request.Builder().url(url).header("Accept-Encoding","identity").build();val call=connect(req.newBuilder().head().build());calls[token]=call;try{call.execute().use{res->if(res.code in setOf(301,302,303,307,308)){url=publicUrl(res.request.url.resolve(res.header("Location")?:error("Redirect missing"))?.toString()?:error("Invalid redirect"))}else{require(res.isSuccessful||res.code==405){"Audio unavailable (${res.code})"};return req}}}finally{calls.remove(token,call);call.cancel()}};error("Too many audio redirects")}
 private var lastCatalog=0L
 private val catalogLock=Any()
 private fun fetch(url:String,token:String,connect:(Request)->Call={catalogClient.newCall(it)},verify:()->Unit):JSONObject=synchronized(catalogLock){
  val delay=2000-(android.os.SystemClock.elapsedRealtime()-lastCatalog);if(delay>0)Thread.sleep(delay);verify();lastCatalog=android.os.SystemClock.elapsedRealtime();var target=publicUrl(url)
  repeat(5){verify();val call=connect(Request.Builder().url(target).header("Accept","application/json").header("User-Agent","OnKlav-AudioBooks/1.0").build());calls[token]=call;try{call.execute().use{r->if(r.code in setOf(301,302,303,307,308)){target=publicUrl(r.request.url.resolve(r.header("Location")?:error("Redirect missing"))?.toString()?:error("Invalid redirect"))}else{if(r.code==404)return@synchronized JSONObject().put("books",JSONArray());require(r.isSuccessful){if(r.code==429)"LibriVox is busy. Wait a little and retry."else"LibriVox unavailable (${r.code}). Your books are kept."};val body=r.body?:error("Empty catalog");val bytes=body.byteStream().use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384);while(out.size()<=4*1024*1024){val n=input.read(buffer,0,minOf(buffer.size,4*1024*1024+1-out.size()));if(n<0)break;out.write(buffer,0,n)};out.toByteArray()};require(bytes.size<=4*1024*1024){"Catalog response too large"};verify();return@synchronized JSONObject(String(bytes,Charsets.UTF_8))}}}finally{calls.remove(token,call);call.cancel()}};error("Too many catalog redirects")
 }
 private fun archiveKey(identifier:String)="ia-"+java.security.MessageDigest.getInstance("SHA-256").digest(identifier.toByteArray()).joinToString(""){"%02x".format(it)}.take(32)
 internal fun archiveResults(value:JSONObject):List<JSONObject>{
  val docs=value.optJSONObject("response")?.optJSONArray("docs")?:JSONArray()
  return docs.objects().mapNotNull{r->val identifier=r.optString("identifier");if(!identifier.matches(Regex("[A-Za-z0-9_.-]{1,180}")))null else JSONObject().put("id",archiveKey(identifier)).put("title",clean(r.optString("title","Audiobook"),200)).put("author",clean(r.optJSONArray("creator")?.let{a->(0 until a.length()).joinToString(", "){a.optString(it)}}?:r.optString("creator"),200)).put("language",r.optString("language")).put("duration","").put("source","LibriVox").put("catalog_record",JSONObject().put("archive_id",identifier))}
 }
 private fun archiveSearch(c:Context,o:JSONObject,query:String,author:Boolean,offset:Int,genre:String,token:String):List<JSONObject>{
  val terms=mutableListOf("collection:librivoxaudio","mediatype:audio")
  fun quoted(v:String)="\""+v.replace("\\"," ").replace("\""," ")+"\""
  if(query.isNotBlank())terms.add((if(author)"creator:"else"title:")+quoted(query.trim()))
  if(genre.isNotBlank())terms.add("subject:"+quoted(if(genre=="Children")"children"else genre))
  val url="https://archive.org/advancedsearch.php".toHttpUrl().newBuilder().addQueryParameter("q",terms.joinToString(" AND ")).addQueryParameter("fl[]","identifier,title,creator,language").addQueryParameter("sort[]","downloads desc").addQueryParameter("rows","20").addQueryParameter("page",(offset/20+1).toString()).addQueryParameter("output","json").build()
  return archiveResults(fetch(url.toString(),token){check(c,o,token)})
 }
 private fun archiveBook(key:String,record:JSONObject,token:String,connect:(Request)->Call,verify:()->Unit):JSONObject{
  val identifier=record.getString("archive_id");require(key==archiveKey(identifier)&&identifier.matches(Regex("[A-Za-z0-9_.-]{1,180}")))
  val data=fetch("https://archive.org/metadata/"+identifier,token,connect,verify);val meta=data.optJSONObject("metadata")?:error("Book details unavailable. Try another recording.")
  val all=(data.optJSONArray("files")?:JSONArray()).objects().filter{it.optString("name").endsWith(".mp3",true)}
  val preferred=all.filter{it.optString("format")=="VBR MP3"}.ifEmpty{all.filter{it.optString("source")=="original"}}.ifEmpty{all.filter{it.optString("format").contains("64Kbps")}}.ifEmpty{all}
  require(preferred.size in 1..2000){"No playable chapters were returned for this recording."}
  val tracks=JSONArray()
  for(f in preferred.sortedWith{a,b->naturalCompare(a.optString("name"),b.optString("name"))}){
   val name=f.getString("name");require(name.split('/').none{it.isEmpty()||it==".."||it=="."})
   val url="https://archive.org/download".toHttpUrl().newBuilder().addPathSegment(identifier).apply{name.split('/').forEach{addPathSegment(it)}}.build().toString()
   tracks.put(JSONObject().put("id",UUID.nameUUIDFromBytes(url.toByteArray()).toString()).put("title",clean(f.optString("title").ifBlank{name.substringBeforeLast('.')},200)).put("url",url).put("duration_ms",playtime(f.optString("length"))).put("markers",JSONArray()))
  }
  return JSONObject().put("id",key).put("title",clean(meta.optString("title","Audiobook"),200)).put("author",clean(meta.optJSONArray("creator")?.let{a->(0 until a.length()).joinToString(", "){a.optString(it)}}?:meta.optString("creator"),200)).put("source","LibriVox").put("tracks",tracks).put("bookmarks",JSONArray())
 }
 fun discover(c:Context,o:JSONObject,query:String,author:Boolean,offset:Int,token:String,genre:String=""):List<JSONObject>{
  require(query.length<=100&&offset in 0..1000000&&offset%20==0);authorize(c,o)
  val dir=File(c.cacheDir,"audiobooks-catalog").apply{mkdirs()};val cache=File(dir,"browse-v2-"+archiveKey("$query|$author|$offset|$genre"))
  if(cache.isFile&&System.currentTimeMillis()-cache.lastModified()<1800000){check(c,o,token);return JSONArray(cache.readText()).objects()}
  val rows=try{archiveSearch(c,o,query,author,offset,genre,token)}catch(e:Exception){
   check(c,o,token)
   if(cache.isFile)return JSONArray(cache.readText()).objects()
   val url="https://librivox.org/api/feed/audiobooks/".toHttpUrl().newBuilder().addQueryParameter("format","json").addQueryParameter("limit","20").addQueryParameter("offset",offset.toString()).addQueryParameter("sort_order","desc").addQueryParameter("extended","1").addQueryParameter("fields","{id,title,authors,language,totaltime,sections}")
   if(query.isNotBlank())url.addQueryParameter(if(author)"author"else"title",query.trim())
   if(genre.isNotBlank())url.addQueryParameter("genre","^"+genre)
   (fetch(url.toString(),token){check(c,o,token)}.optJSONArray("books")?:JSONArray()).objects().take(20).map{catalogBook(it)}
  }
  check(c,o,token);cache.writeText(JSONArray(rows).toString());return rows
 }
 internal fun catalogBook(b:JSONObject):JSONObject {val key=b.getString("id");require(key.matches(Regex("[0-9]{1,12}")));val author=(b.optJSONArray("authors")?:JSONArray()).objects().joinToString(", "){(it.optString("first_name")+" "+it.optString("last_name")).trim()};return JSONObject().put("id","lv-$key").put("title",clean(b.optString("title","Audiobook"),200)).put("author",clean(author,200)).put("language",clean(b.optString("language"),80)).put("duration",b.optString("totaltime")).put("source","LibriVox").put("catalog_record",JSONObject(b.toString()))}
 fun addCatalog(c:Context,o:JSONObject,key:String,token:String,connect:(Request)->Call={client.newCall(it)},inLibrary:Boolean=true,record:JSONObject?=null):JSONObject {require(key.matches(Regex("lv-[0-9]{1,12}|ia-[a-f0-9]{32}")));library(c,o).firstOrNull{it.getString("id")==key}?.let{if(inLibrary&&it.optBoolean("in_library",true)==false)keep(c,o,key);return get(c,o,key)};if(key.startsWith("ia-")){
   val b=archiveBook(key,requireNotNull(record),token,connect){check(c,o,token)}.put("in_library",inLibrary).put("added",System.currentTimeMillis())
   AppSdk.carrierEgress{check(c,o,token);synchronized(lock){val d=root(c,o);val a=index(d);if(a.objects().none{it.getString("id")==key}){a.put(b);write(d,a)};deletedApps.remove(o.getString("id"))}}
   return get(c,o,key)
  };val row=record?.takeIf{"lv-"+it.optString("id")==key&&(it.optJSONArray("sections")?.length()?:0)>0}?:fetch("https://librivox.org/api/feed/audiobooks/?format=json&extended=1&id="+key.removePrefix("lv-"),token,connect){check(c,o,token)}.getJSONArray("books").getJSONObject(0);kotlin.check("lv-"+row.getString("id")==key);val b=catalogBook(row).apply{remove("catalog_record")};val sections=row.optJSONArray("sections")?:error("No chapter list available for this recording");require(sections.length() in 1..2000);val tracks=JSONArray();for(t in sections.objects().sortedBy{it.optInt("section_number",Int.MAX_VALUE)}){val u=upgrade(t.getString("listen_url"));tracks.put(JSONObject().put("id",UUID.nameUUIDFromBytes(u.toByteArray()).toString()).put("title",clean(t.optString("title","Chapter ${tracks.length()+1}"),200)).put("url",u).put("duration_ms",playtime(t.optString("playtime"))).put("markers",JSONArray()))};b.put("tracks",tracks).put("bookmarks",JSONArray()).put("in_library",inLibrary).put("added",System.currentTimeMillis());AppSdk.carrierEgress{check(c,o,token);synchronized(lock){val d=root(c,o);val a=index(d);if(a.objects().none{it.getString("id")==key}){a.put(b);write(d,a)};deletedApps.remove(o.getString("id"))}};return get(c,o,key)}
 fun keep(c:Context,o:JSONObject,key:String)=update(c,o,key){it.put("in_library",true)}
 fun offline(c:Context,o:JSONObject,b:JSONObject)=b.getJSONArray("tracks").objects().count{it.optString("file").isNotEmpty()&&File(File(root(c,o),id(b.getString("id"))),it.optString("file")).isFile}
 internal fun playtime(raw:String):Long=if(raw.contains(':'))raw.split(':').fold(0L){a,v->a*60+(v.toLongOrNull()?:0)}*1000 else ((raw.toDoubleOrNull()?:0.0)*1000).toLong()
 internal fun clean(s:String,n:Int)=s.replace(Regex("<[^>]*>"),"").replace(Regex("[\\p{Cntrl}]")," ").take(n).trim()
 internal fun naturalCompare(a:String,b:String):Int {val parts=Regex("[0-9]+|[^0-9]+");val x=parts.findAll(a.lowercase()).map{it.value}.toList();val y=parts.findAll(b.lowercase()).map{it.value}.toList();for(i in 0 until minOf(x.size,y.size)){val u=x[i];val v=y[i];val result=if(u[0].isDigit()&&v[0].isDigit()){val m=u.trimStart('0').ifEmpty{"0"};val n=v.trimStart('0').ifEmpty{"0"};if(m.length!=n.length)m.length.compareTo(n.length)else m.compareTo(n)}else u.compareTo(v);if(result!=0)return result};return x.size.compareTo(y.size)}
 internal fun JSONArray.objects()=(0 until length()).map{getJSONObject(it)}
}
