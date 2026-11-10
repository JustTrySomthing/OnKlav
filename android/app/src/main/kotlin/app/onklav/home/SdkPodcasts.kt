package app.onklav.home

import java.io.File

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Public Apple podcast search, fast bundled browse snapshot, and direct publisher RSS/audio. */
internal object SdkPodcasts {
 internal fun networkChanged(){client.connectionPool.evictAll()}
 private val client=OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).dns(object:Dns{override fun lookup(hostname:String)=SdkFeedService.publicAddresses(hostname)}).build()
 private val calls=ConcurrentHashMap<String,Call>();private val slots=Semaphore(2)
 private data class Lease(val handle:String,val owner:String,val url:String,val local:java.io.File?=null)
 private val leases=ConcurrentHashMap<String,Lease>()
 private data class Feed(val at:Long,val value:JSONObject)
 private val feeds=linkedMapOf<String,Feed>()
 private var catalog:String?=null
 private val searches=linkedMapOf<String,Feed>()
 internal fun id(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
 private fun owner(o:JSONObject)=listOf("id","publisher","hash").joinToString("|"){o.getString(it)}
 fun cancel(token:String){calls.remove(token)?.cancel();SdkRadio.cancel("podcast-"+token)}
 fun cancelAll(){calls.values.forEach{it.cancel()};calls.clear()}
 fun clearApp(app:String){leases.remove(app);synchronized(feeds){feeds.keys.filter{it.startsWith("$app|")}.forEach{feeds.remove(it)}}}
 internal fun publicUrl(value:String):String {require(value.length in 1..4096&&value.none{it.isWhitespace()||it.isISOControl()||it=='\\'});val u=value.toHttpUrl();require(u.port==443||u.port in 1024..65535);return SdkRadio.publicUrl(value)}
 internal fun secureUrl(value:String):String{val u=value.toHttpUrl();return publicUrl(if(u.scheme=="http")u.newBuilder().scheme("https").apply{if(u.port==80)port(443)}.build().toString()else value)}
 internal data class Transfer(val state:String,val bytes:Long=0,val total:Long=-1,val message:String="")
 val transfers=MutableStateFlow<Map<String,Transfer>>(emptyMap())
 internal fun transferKey(app:String,episode:String)="$app|$episode"
 private fun progress(o:JSONObject,episode:String,value:Transfer){val key=transferKey(o.getString("id"),episode);transfers.update{old->val keep=if(old.size>500)old.filter{it.value.state in setOf("connecting","downloading")}else old;if(old[key]?.state=="cancelled"&&value.state=="downloading")old else keep+(key to value)}}
 internal fun cancelDownload(app:String,episode:String,token:String){transfers.update{it+(transferKey(app,episode) to Transfer("cancelled",message="Download cancelled"))};cancel(token)}
 internal fun failureMessage(e:Exception):String=when(e){is CancellationException->"Download cancelled";is java.net.SocketTimeoutException->"Unable to download: connection stalled. Tap the arrow to retry.";is java.net.UnknownHostException->"Unable to download: check your connection and retry.";else->"Unable to download: "+(e.message?:"connection interrupted").replace(Regex("[\\r\\n]+")," ").take(160)}
 private fun request(url:String)=Request.Builder().url(publicUrl(url)).header("User-Agent","OnKlav-Podcasts/0.1").header("Accept-Encoding","identity").build()
 private fun plain(s:String,max:Int)=s.replace(Regex("<[^>]*>")," ").filter{!it.isISOControl()}.trim().take(max)
 internal fun directory(rows:JSONArray,args:JSONObject):JSONObject{
  val q=args.optString("query").trim();val language=args.optString("language").lowercase();val matches=(0 until rows.length()).map{rows.getJSONObject(it)}.filter{r->(q.isEmpty()||(r.optString("title")+" "+r.optString("author")).contains(q,true))&&(language.isEmpty()||r.optString("language").isEmpty()||r.optString("language").lowercase().startsWith(language))}
  val offset=args.optInt("offset");val items=JSONArray();matches.drop(offset).take(20).forEach{items.put(JSONObject(it.toString()))}
  return JSONObject().put("items",items).put("total",matches.size).put("catalog_count",rows.length()).put("next",if(offset+20<matches.size)offset+20 else JSONObject.NULL).put("source","Apple Podcasts public directory")
 }
 internal fun appleRows(value:JSONObject):JSONArray{
  val rows=JSONArray();val seen=mutableSetOf<String>();val all=value.optJSONArray("results")?:JSONArray()
  for(i in 0 until all.length()){
   val r=all.getJSONObject(i);val feed=runCatching{secureUrl(r.optString("feedUrl"))}.getOrNull()?:continue
   val title=plain(r.optString("collectionName",r.optString("trackName")),200)
   if(title.isNotEmpty()&&seen.add(feed))rows.put(JSONObject().put("title",title).put("author",plain(r.optString("artistName"),200)).put("feed",feed).put("language",""))
  };return rows
 }
 suspend fun search(c:Context,token:String,authorize:()->JSONObject):JSONObject=network(token,authorize){p,verify->
  val args=p.getJSONObject("args");val q=args.optString("query").trim();val lang=args.optString("language")
  val raw=synchronized(this@SdkPodcasts){catalog?:c.assets.open("podcasts/catalog.json").bufferedReader().use{it.readText()}.also{catalog=it}}
  if(q.isEmpty())return@network directory(JSONArray(raw),args)
  val key=q.lowercase()+"|"+lang
  val cached=synchronized(searches){searches[key]?.takeIf{SystemClock.elapsedRealtime()-it.at in 0..900000}?.value}
  try{
   val data=cached?:run{
    val country=mapOf("es" to "ES","de" to "DE","fr" to "FR","pt" to "BR","it" to "IT","nl" to "NL")[lang]?:"US"
    val url="https://itunes.apple.com/search".toHttpUrl().newBuilder().addQueryParameter("media","podcast").addQueryParameter("entity","podcast").addQueryParameter("term",q).addQueryParameter("limit","200").addQueryParameter("country",country).build()
    val call=client.newCall(request(url.toString()));calls[token]=call
    try{call.execute().use{response->verify();check(response.isSuccessful){"Podcast directory is unavailable. Try again."};val body=response.body?:error("Empty podcast directory");val bytes=body.byteStream().use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){verify();val n=input.read(buffer);if(n<0)break;require(out.size()+n<=2*1024*1024);out.write(buffer,0,n)};out.toByteArray()};JSONObject().put("rows",appleRows(JSONObject(bytes.toString(Charsets.UTF_8))))}}finally{calls.remove(token,call);call.cancel()}
   }.also{data->synchronized(searches){if(searches.size>=20)searches.remove(searches.keys.first());searches[key]=Feed(SystemClock.elapsedRealtime(),data)}}
   verify();val filters=JSONObject(args.toString()).put("query","").put("language","");directory(data.getJSONArray("rows"),filters)
  }catch(e:CancellationException){throw e}catch(e:Exception){verify();directory(JSONArray(raw),args).put("offline",true)}
 }
 /** Follow only public TLS redirects, without credentials/cookies. Read a bounded RSS document. */
 private fun xml(token:String,url:String,verify:()->Unit):ByteArray{
  var current=publicUrl(url);val seen=mutableSetOf<String>()
  repeat(6){verify();check(seen.add(current)){"Podcast redirect loop"};SdkFeedService.publicAddresses(current.toHttpUrl().host);val call=client.newCall(request(current).newBuilder().removeHeader("Accept-Encoding").build());calls[token]=call
   try{call.execute().use{r->verify();if(r.code in setOf(301,302,303,307,308)){current=secureUrl(URI(current).resolve(r.header("Location")?:error("Podcast redirect missing")).toString())}else{
    check(r.code==200){"Podcast feed unavailable or requires sign-in"};val body=r.body?:error("Empty podcast feed");require(body.contentLength()<=16*1024*1024)
    return body.byteStream().use{input->val out=java.io.ByteArrayOutputStream();val b=ByteArray(65536);while(true){verify();val n=input.read(b);if(n<0)break;check(n>0);require(out.size()+n<=16*1024*1024){"Podcast feed too large"};out.write(b,0,n)};out.toByteArray()}
   }}}finally{calls.remove(token,call);call.cancel()}
  };error("Too many podcast redirects")
 }
 /** Podcast tracking chains are longer than radio redirects; every hop remains public TLS. */
 internal fun resolveAudio(token:String,url:String,verify:()->Unit,connect:(Request)->Call={client.newCall(it)},lookup:(String)->Unit={SdkFeedService.publicAddresses(it);Unit}):String{
  var current=publicUrl(url);val seen=mutableSetOf<String>();val deadline=SystemClock.elapsedRealtime()+30000
  repeat(12){verify();check(seen.add(current)){"Podcast audio redirect loop"};lookup(current.toHttpUrl().host)
   val remaining=deadline-SystemClock.elapsedRealtime();check(remaining>0){"Podcast audio is taking too long. Try again."}
   val call=connect(request(current).newBuilder().header("Range","bytes=0-0").build());call.timeout().timeout(minOf(10000,remaining),TimeUnit.MILLISECONDS);calls[token]=call
   try{call.execute().use{r->verify();if(r.code in setOf(301,302,303,307,308)){current=secureUrl(URI(current).resolve(r.header("Location")?:error("Audio redirect missing")).toString())}else{
    check(r.code in setOf(200,206)){"Podcast audio unavailable (${r.code})"};val type=r.header("Content-Type","")!!.substringBefore(';').lowercase();check(type.startsWith("audio/")||type in setOf("application/octet-stream","binary/octet-stream","video/mp4","application/ogg","application/mp3","application/mpeg")){"This episode did not return audio"};return current
   }}}finally{calls.remove(token,call);call.cancel()}
  };error("Too many podcast audio redirects")
 }
 /** RSS2 pages skip unsupported enclosures; a long feed is paged rather than rejected at20 items. */
 internal fun parse(data:ByteArray):JSONObject{
  require(data.size<=16*1024*1024);val parser=XmlPullParserFactory.newInstance().newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);parser.setInput(ByteArrayInputStream(data),null)
  var event=parser.eventType;val path=mutableListOf<String>();val rows=JSONArray();var title="";var date="";var duration="";var audio="";var show="";val seen=mutableSetOf<String>();var root=false
  while(event!=XmlPullParser.END_DOCUMENT){
   when(event){
    XmlPullParser.DOCDECL->error("Podcast DTDs are unsupported")
    XmlPullParser.START_TAG->{val name=parser.name;path.add(name);require(path.size<=64);if(path.size==1){require(name=="rss"&&parser.getAttributeValue(null,"version")=="2.0"){"Choose an RSS2 podcast feed"};root=true};if(path==listOf("rss","channel","item")){title="";date="";duration="";audio=""};if(path==listOf("rss","channel","item","enclosure")&&audio.isEmpty()){
     val type=parser.getAttributeValue(null,"type")?.substringBefore(';')?.lowercase().orEmpty();val url=parser.getAttributeValue(null,"url").orEmpty();if(type in setOf("audio/mpeg","audio/mp3","audio/mp4","audio/aac","audio/x-m4a","audio/ogg","audio/flac","audio/wav","audio/x-wav","audio/m4a","application/octet-stream","audio/x-mp3","audio/mpeg3","application/ogg","application/mp3","video/mp4"))audio=runCatching{secureUrl(url)}.getOrDefault("")
    }}
    XmlPullParser.TEXT,XmlPullParser.CDSECT,XmlPullParser.ENTITY_REF->{val t=parser.text.orEmpty();when(path.joinToString("/")){"rss/channel/title"->show=(show+t).take(200);"rss/channel/item/title"->title=(title+t).take(200);"rss/channel/item/pubDate"->date=(date+t).take(100);"rss/channel/item/duration"->duration=(duration+t).take(32)}}
    XmlPullParser.END_TAG->{if(path==listOf("rss","channel","item")&&audio.isNotEmpty()&&title.isNotBlank()&&seen.add(audio)){rows.put(JSONObject().put("id",id(audio)).put("title",plain(title,200)).put("date",plain(date,100)).put("duration",plain(duration,32)).put("url",audio))};if(path.isNotEmpty())path.removeAt(path.lastIndex)}
   };event=parser.nextToken()
  };require(root&&show.isNotBlank()){ "Podcast title missing" };return JSONObject().put("title",plain(show,200)).put("items",rows)
 }
 private fun load(token:String,feed:String,key:String,verify:()->Unit,refresh:Boolean=false,maxAge:Long=300000):JSONObject{
  publicUrl(feed);if(!refresh)synchronized(feeds){feeds[key]?.takeIf{SystemClock.elapsedRealtime()-it.at in 0..maxAge}?.let{verify();return JSONObject(it.value.toString())}}
  val result=parse(xml(token,feed,verify));verify();synchronized(feeds){if(feeds.size>=12)feeds.remove(feeds.keys.first());feeds[key]=Feed(SystemClock.elapsedRealtime(),result)};return result
 }
 internal fun page(value:JSONObject,offset:Int):JSONObject{val all=value.getJSONArray("items");val rows=JSONArray();for(i in offset until minOf(offset+20,all.length()))rows.put(JSONObject(all.getJSONObject(i).toString()).apply{remove("url")});return JSONObject().put("title",value.getString("title")).put("items",rows).put("next",if(offset+20<all.length())offset+20 else JSONObject.NULL).put("total",all.length())}
 private suspend fun <T> network(token:String,authorize:()->JSONObject,block:(JSONObject,()->Unit)->T):T=withContext(Dispatchers.IO){coroutineScope{
  val job=currentCoroutineContext()[Job]!!;val watcher=launch(start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{cancel(token)}}
  try{slots.withPermit{val initial=authorize();fun verify(){job.ensureActive();check(authorize().toString()==initial.toString())};block(initial,::verify)}}finally{watcher.cancel()}
 }}
 internal fun downloadedFeed(c:Context,o:JSONObject,feed:String,title:String=""):JSONObject{
  publicUrl(feed);val items=JSONArray();var show=plain(title,200)
  downloadRoot(c,o).listFiles().orEmpty().filter{it.name.matches(Regex("[a-f0-9]{64}\\.json"))}.sortedByDescending{it.lastModified()}.take(500).forEach{meta->
   val episode=meta.name.removeSuffix(".json");val info=local(c,o,episode)?.second
   if(info!=null&&info.optString("feed")==feed){if(show.isBlank())show=plain(info.optString("show_title"),200);items.put(JSONObject().put("id",episode).put("title",plain(info.optString("title","Episode"),200).ifBlank{"Episode"}).put("date",plain(info.optString("date"),100)).put("duration",plain(info.optString("duration"),100)))}
  };return JSONObject().put("title",show.ifBlank{"Downloaded episodes"}).put("items",items)
 }
 suspend fun feed(c:Context,token:String,fallbackTitle:String="",authorize:()->JSONObject):JSONObject=network(token,authorize){p,verify->val a=p.getJSONObject("args");val feed=a.getString("feed");val saved=downloadedFeed(c,p.getJSONObject("owner"),feed,fallbackTitle);val hasSaved=saved.getJSONArray("items").length()>0
  if(hasSaved&&!validatedInternet(c)){verify();return@network page(saved,a.optInt("offset"))}
  try{page(load(token,feed,owner(p.getJSONObject("owner"))+"|"+feed,verify,refresh=a.optBoolean("refresh")),a.optInt("offset"))}catch(e:CancellationException){throw e}catch(e:Exception){verify();if(hasSaved)page(saved,a.optInt("offset"))else throw e}
 }
 private fun downloadRoot(c:Context,o:JSONObject):java.io.File=java.io.File(c.filesDir,"podcast-downloads/"+id(o.getString("id")+"|"+o.getString("publisher"))).also{check(it.mkdirs()||it.isDirectory)}
 private fun local(c:Context,o:JSONObject,episode:String):Pair<java.io.File,JSONObject>?{require(episode.matches(Regex("[a-f0-9]{64}")));val root=downloadRoot(c,o);val file=java.io.File(root,"$episode.audio");val meta=java.io.File(root,"$episode.json");return runCatching{val info=JSONObject(meta.readText());check(file.isFile&&file.length()>0&&file.length()==info.getLong("bytes"));file to info}.getOrNull()}
 internal fun downloads(c:Context,o:JSONObject):JSONObject{val rows=JSONArray();downloadRoot(c,o).listFiles().orEmpty().filter{it.name.matches(Regex("[a-f0-9]{64}\\.json"))}.take(500).forEach{val episode=it.name.removeSuffix(".json");if(local(c,o,episode)!=null)rows.put(episode)};return JSONObject().put("downloads",rows)}
 suspend fun saved(c:Context,token:String,authorize:()->JSONObject):JSONObject=network(token,authorize){p,verify->verify();downloads(c,p.getJSONObject("owner"))}
 suspend fun delete(c:Context,token:String,authorize:()->JSONObject):JSONObject=network(token,authorize){p,verify->val o=p.getJSONObject("owner");val episode=p.getJSONObject("args").getString("episode");require(episode.matches(Regex("[a-f0-9]{64}")));val root=downloadRoot(c,o);verify();for(ext in listOf("audio","json")){val f=java.io.File(root,"$episode.$ext");check(!f.exists()||f.delete()){ "Download could not be removed" }};transfers.update{it-transferKey(o.getString("id"),episode)};downloads(c,o)}
 /** Disk streamed download: commit only a complete audio response; never buffer episodes in RAM. */
 internal suspend fun download(c:Context,token:String,connect:(Request)->Call={client.newBuilder().callTimeout(0,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build().newCall(it)},lookup:(String)->Unit={SdkFeedService.publicAddresses(it);Unit},authorize:()->JSONObject):JSONObject=network(token,authorize){p,verify->
  val a=p.getJSONObject("args");val o=p.getJSONObject("owner");val episode=a.getString("episode");progress(o,episode,Transfer("connecting",message="Connecting…"));try{if(local(c,o,episode)!=null){progress(o,episode,Transfer("complete",message="Download complete"));return@network downloads(c,o)}
  check(downloads(c,o).getJSONArray("downloads").length()<500){"Remove an older download first"}
  val feed=a.getString("feed");val feedData=load(token,feed,owner(o)+"|"+feed,verify,maxAge=86400000);val all=feedData.getJSONArray("items");val selected=(0 until all.length()).map{all.getJSONObject(it)}.firstOrNull{it.getString("id")==episode}?:error("Episode no longer in its public feed")
  var url=selected.getString("url");val root=downloadRoot(c,o);val temp=java.io.File(root,"$episode.part");val metaTemp=java.io.File(root,"$episode.meta-part");val final=java.io.File(root,"$episode.audio");val meta=java.io.File(root,"$episode.json");val seen=mutableSetOf<String>()
  try{repeat(12){verify();check(seen.add(url)){"Podcast redirect loop"};lookup(url.toHttpUrl().host)
   val call=connect(request(url));calls[token]=call
   try{call.execute().use{response->verify();if(response.code in setOf(301,302,303,307,308)){url=secureUrl(URI(url).resolve(response.header("Location")?:error("Missing redirect")).toString())}else{
    check(response.code==200){"Episode download unavailable (${response.code})"};val type=response.header("Content-Type","")!!.substringBefore(';').lowercase();check(type.startsWith("audio/")||type in setOf("application/octet-stream","binary/octet-stream","video/mp4","application/ogg","application/mp3","application/mpeg")){"This episode did not return audio"}
    val body=response.body?:error("Empty episode");val expected=body.contentLength();val limit=2L*1024*1024*1024;require(expected<=limit){"Episode is larger than2GB"};check(root.usableSpace>maxOf(32L*1024*1024,expected+16L*1024*1024)){"Not enough space. Remove some downloads first."}
    var bytes=0L;var updated=0L;var published=0L;progress(o,episode,Transfer("downloading",0,expected));body.byteStream().use{input->java.io.FileOutputStream(temp).use{out->val buffer=ByteArray(65536);while(true){verify();val n=input.read(buffer);if(n<0)break;if(n==0)continue;bytes+=n;check(bytes<=limit){"Episode exceeds2GB"};out.write(buffer,0,n);val now=SystemClock.elapsedRealtime();if(now-updated>=200||bytes-published>=1024*1024){progress(o,episode,Transfer("downloading",bytes,expected));updated=now;published=bytes}};out.fd.sync()}}
    check(bytes>0&&(expected<0||bytes==expected)){"Episode download was incomplete"};verify();check(temp.renameTo(final)){"Could not save episode"};val info=JSONObject(selected.toString()).apply{remove("url");put("feed",feed);put("show_title",feedData.optString("title"));put("bytes",bytes)};java.io.FileOutputStream(metaTemp).use{out->out.write(info.toString().toByteArray());out.fd.sync()};verify();check(metaTemp.renameTo(meta)){"Could not save download details"};progress(o,episode,Transfer("complete",bytes,bytes,"Download complete"));return@network downloads(c,o)
   }}}finally{calls.remove(token,call);call.cancel()}
  };error("Too many podcast redirects")
  }finally{temp.delete();metaTemp.delete();if(!meta.exists())final.delete()}
  }catch(e:Exception){progress(o,episode,if(transfers.value[transferKey(o.getString("id"),episode)]?.state=="cancelled")Transfer("cancelled",message="Download cancelled")else Transfer(if(e is CancellationException)"cancelled"else"error",message=failureMessage(e)));throw e}
 }
 internal data class Prepared(val request:JSONObject,val episode:JSONObject,val handle:String,val url:String,val local:java.io.File?=null)
 suspend fun prepare(c:Context,token:String,authorize:()->JSONObject):Prepared=network(token,authorize){p,verify->
  val a=p.getJSONObject("args");val o=p.getJSONObject("owner");val feed=a.getString("feed");local(c,o,a.getString("episode"))?.let{(file,info)->verify();val handle=UUID.randomUUID().toString();val item=JSONObject().put("url",file.toURI().toString()).put("title",info.getString("title")).put("podcast_episode",handle).put("podcast_id",a.getString("episode")).put("podcast_local",true);return@network Prepared(JSONObject().put("service","media.play").put("owner",o).put("args",JSONObject().put("queue",JSONArray().put(item))),info,handle,file.toURI().toString(),file)};val all=load(token,feed,owner(o)+"|"+feed,verify,maxAge=86400000).getJSONArray("items");val selected=(0 until all.length()).map{all.getJSONObject(it)}.firstOrNull{it.getString("id")==a.getString("episode")}?:error("Episode no longer in its public feed")
  val url=resolveAudio(token,selected.getString("url"),verify);verify();val handle=UUID.randomUUID().toString();val item=JSONObject().put("url",url).put("title",selected.getString("title")).put("podcast_episode",handle).put("podcast_id",selected.getString("id"))
  Prepared(JSONObject().put("service","media.play").put("owner",o).put("args",JSONObject().put("queue",JSONArray().put(item))),JSONObject(selected.toString()).apply{remove("url")},handle,url)
 }
 fun start(c:Context,p:Prepared,verify:()->Unit):JSONObject=AppSdk.carrierEgress{verify();val o=p.request.getJSONObject("owner");val lease=Lease(p.handle,owner(o),p.url,p.local);leases[o.getString("id")]=lease;try{JSONObject().put("episode",p.episode).put("player",SdkMedia.execute(c,p.request,verify))}catch(e:Exception){leases.remove(o.getString("id"),lease);throw e}}
 fun policy(c:Context,o:JSONObject,item:JSONObject):JSONObject{
  val handle=SdkRadio.stationId(item.getString("podcast_episode"));val gate=AppSdk.nativeTask(c,JSONObject().put("action","media_authorize").put("owner",o).put("args",JSONObject().put("podcast_episode",handle).apply{if(item.optBoolean("podcast_local"))put("podcast_local",true)}));val lease=leases[o.getString("id")]?:error("Choose the episode again");check(lease.owner==owner(o)&&lease.handle==handle&&lease.url==item.getString("url"))
  if(lease.local!=null)return JSONObject().put("local",true).put("podcast_grant",gate)
  return JSONObject().put("service","http.request").put("owner",o).put("origins",JSONArray().put(lease.url.toHttpUrl().let{"https://${it.host}:${it.port}"})).put("args",JSONObject().put("url",lease.url)).put("podcast_grant",gate)
 }
 internal fun localPlaybackFile(c:Context,o:JSONObject,item:JSONObject):File? {if(!item.optBoolean("podcast_local"))return null;policy(c,o,item);return leases[o.getString("id")]?.local?.also{check(it.isFile)}}
 internal fun openLocalDescriptor(c:Context,o:JSONObject,item:JSONObject):android.os.ParcelFileDescriptor?{
  if(!item.optBoolean("podcast_local"))return null
  policy(c,o,item);val lease=leases[o.getString("id")]?:error("Choose the episode again");val file=lease.local?:error("Download unavailable")
  return android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY)
 }

 fun openLocal(c:Context,o:JSONObject,item:JSONObject):android.media.MediaDataSource?{if(!item.optBoolean("podcast_local"))return null;policy(c,o,item);val lease=leases[o.getString("id")]?:error("Choose the episode again");val input=java.io.RandomAccessFile(lease.local?:error("Download unavailable"),"r");val size=input.length();return object:android.media.MediaDataSource(){@Synchronized override fun readAt(position:Long,buffer:ByteArray,offset:Int,length:Int):Int{policy(c,o,item);require(position>=0&&offset>=0&&length>=0&&offset.toLong()+length<=buffer.size);if(length==0)return 0;if(position>=size)return -1;input.seek(position);return input.read(buffer,offset,minOf(length,65536,(size-position).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))};override fun getSize()=size;override fun close(){input.close()}}}
 fun streamRequest(c:Context,o:JSONObject,item:JSONObject)=request(policy(c,o,item).getJSONObject("args").getString("url"))
}
