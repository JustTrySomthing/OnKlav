package app.onklav.home

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Public Radio Browser and Icecast. No accounts, credentials, cookies, paid APIs or stream relay. */
internal object SdkRadio {
 internal fun networkChanged(){client.connectionPool.evictAll()}
 val unavailable get()=SdkRadioHealth.hidden
 internal fun loadHealth(c:Context)=SdkRadioHealth.load(c)
 internal fun hidden(id:String)=SdkRadioHealth.isHidden(id)
 internal fun hide(c:Context,id:String)=SdkRadioHealth.record(c,id,SdkRadioHealth.Failure("manual","Hidden by you","Hidden for 24 hours. You can retry it at any time.",true),manual=true)
 internal fun retryUnavailable(c:Context)=SdkRadioHealth.restore(c)
 fun playbackFailed(c:Context,item:JSONObject,extra:Int=android.media.MediaPlayer.MEDIA_ERROR_UNSUPPORTED){val handle=item.optString("radio_station");val lease=leases.values.firstOrNull{it.handle==handle}?:return;val id=item.optString("radio_id");if(id.isEmpty()||lease.url!=item.optString("url"))return;val failure=when(extra){android.media.MediaPlayer.MEDIA_ERROR_UNSUPPORTED->SdkRadioHealth.unsupported();android.media.MediaPlayer.MEDIA_ERROR_MALFORMED->SdkRadioHealth.Failure("unavailable","Audio unavailable","The station sent audio the player could not read. Retry later.",true);android.media.MediaPlayer.MEDIA_ERROR_TIMED_OUT->SdkRadioHealth.classify(java.net.SocketTimeoutException());else->SdkRadioHealth.classify(java.io.IOException())};SdkRadioHealth.record(c,id,failure)}
 fun playbackRecovered(c:Context,item:JSONObject){item.optString("radio_id").takeIf{it.isNotEmpty()}?.let{SdkRadioHealth.recovered(c,it)}}
 internal val servers=listOf("https://de1.api.radio-browser.info","https://de2.api.radio-browser.info")
 private val client=OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS)
  .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(true).dns(object:okhttp3.Dns{override fun lookup(hostname:String)=SdkFeedService.publicAddresses(hostname)}).build()
 // Live responses are handed to the player: keep bounded connection/read waits, never a whole-call deadline.
 private val liveClient=client.newBuilder().callTimeout(0,TimeUnit.SECONDS).readTimeout(18,TimeUnit.SECONDS).build()
 internal fun liveCall(request:Request):Call=liveClient.newCall(request)
 private val slots=Semaphore(2);private val calls=ConcurrentHashMap<String,Call>()
 private data class Cached(val at:Long,val rows:String)
 private val cache=linkedMapOf<String,Cached>();private val rates=linkedMapOf<String,java.util.ArrayDeque<Long>>()
 private data class Lease(val handle:String,val owner:String,val url:String,val connection:java.util.concurrent.atomic.AtomicReference<RadioConnection?>)
 private val leases=ConcurrentHashMap<String,Lease>()
 private data class Fresh(val station:JSONObject,val url:String,val at:Long)
 private val freshStations=ConcurrentHashMap<String,Fresh>()
 private val pages=linkedMapOf<String,SdkRadioCatalog.Snapshot>()
 private val catalogClient=client.newBuilder().callTimeout(45,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
 private fun owner(o:JSONObject)=listOf("id","publisher","hash").joinToString("|"){o.getString(it)}
 fun cancel(token:String){calls.remove(token)?.cancel()}
 fun cancelAll(){calls.values.forEach{it.cancel()};calls.clear()}
 fun clearApp(id:String){leases.remove(id)?.connection?.getAndSet(null)?.close();synchronized(pages){pages.keys.filter{it.startsWith("$id|")}.forEach{pages.remove(it)}};synchronized(cache){cache.keys.filter{it.startsWith("$id|")}.forEach{cache.remove(it)}};synchronized(rates){rates.keys.filter{it.startsWith("$id|")}.forEach{rates.remove(it)}}}
 private fun reserve(o:JSONObject,kind:String,limit:Int){synchronized(rates){val key=owner(o)+"|"+kind;val now=SystemClock.elapsedRealtime();if(key !in rates&&rates.size>=200)rates.remove(rates.keys.first());val q=rates.getOrPut(key){java.util.ArrayDeque()};while(q.isNotEmpty()&&now-q.first>=60000)q.removeFirst();check(q.size<limit){"Please wait a moment before trying Radio again"};q.addLast(now)}}
 internal fun stationId(value:String):String=UUID.fromString(value).toString().also{require(it==value){"Choose a valid station"}}
 internal fun publicUrl(value:String):String{require(value.length<=4096&&value.none{it.isWhitespace()||it.isISOControl()||it=='\\'});val u=value.toHttpUrl();require(u.scheme in setOf("https","http")&&u.username.isEmpty()&&u.password.isEmpty()&&u.fragment==null&&(u.port==(if(u.scheme=="https")443 else 80)||u.port in 1024..65535));SdkHttpPolicy.origin("https://"+u.host);return value}
 private fun plain(row:JSONObject,key:String,limit:Int):String{val points=row.optString(key,"").codePoints().filter{!Character.isISOControl(it)}.limit(limit.toLong()).toArray();return String(points,0,points.size)}
 /** Projection deliberately omits URLs, artwork/tracking URLs and arbitrary directory fields. */
 internal fun station(row:JSONObject):JSONObject?=runCatching {
  val id=stationId(row.getString("stationuuid"));val codec=row.optString("codec").uppercase();require((codec in setOf("MP3","AAC","AAC+","AACP","OGG","VORBIS","OPUS","FLAC")||row.optInt("hls")==1)&&row.optInt("lastcheckok")==1)
  publicUrl(row.getString("url_resolved"))
  val name=plain(row,"name",100).trim();require(name.isNotEmpty())
  JSONObject().put("id",id).put("name",name).put("country",plain(row,"country",80)).put("countrycode",plain(row,"countrycode",2)).put("language",plain(row,"language",64)).put("tags",plain(row,"tags",120)).put("codec",codec).put("bitrate",row.optInt("bitrate").coerceIn(0,1024))
 }.getOrNull()
 private fun request(url:String)=Request.Builder().url(publicUrl(url)).header("User-Agent","OnKlav-Radio/0.1 (public station player)").header("Accept-Encoding","identity").build()
 private fun bytes(token:String,request:Request,verify:()->Unit):ByteArray{verify();SdkFeedService.publicAddresses(request.url.host);val call=client.newCall(request);calls[token]=call;try{verify();return call.execute().use{r->check(r.code==200){"Radio directory is unavailable"};val body=r.body?:error("Radio directory returned no data");require(body.contentLength()<=524288);body.byteStream().use{input->val out=java.io.ByteArrayOutputStream();val b=ByteArray(8192);while(true){verify();val n=input.read(b);if(n<0)break;if(n==0)error("Radio directory stalled");require(out.size()+n<=524288);out.write(b,0,n)};verify();out.toByteArray()}}}finally{calls.remove(token,call);call.cancel()}}
 private fun directory(token:String,path:String,query:Map<String,String>,verify:()->Unit):JSONArray{var failure:Exception?=null;for(server in servers){verify();try{val url=server.toHttpUrl().newBuilder().addPathSegments(path).apply{query.forEach{(k,v)->addQueryParameter(k,v)}}.build();return JSONArray(bytes(token,request(url.toString()),verify).toString(Charsets.UTF_8))}catch(e:Exception){verify();failure=e}};throw IllegalStateException("Radio directory unavailable. Your favorites are kept.",failure)}
 private fun catalogRows(token:String,source:String,verify:()->Unit):List<SdkRadioCatalog.Station>{
  val targets=if(source=="Icecast")listOf("https://dir.xiph.org/yp.xml")else servers.map{it+"/json/stations/search?hidebroken=true&limit=100000&order=clickcount&reverse=true"};var failure:Exception?=null
  for(url in targets){verify();try{SdkFeedService.publicAddresses(url.toHttpUrl().host);val call=catalogClient.newCall(request(url).newBuilder().removeHeader("Accept-Encoding").build());calls[token]=call
   try{return call.execute().use{response->verify();check(response.code==200){"Station directory unavailable"};val body=response.body?:error("Empty station directory");check(body.contentLength()<=96L*1024*1024);SdkRadioCatalog.Limited(body.byteStream(),96L*1024*1024).use{input->if(source=="Icecast")SdkRadioCatalog.readIcecast(input,verify)else SdkRadioCatalog.readBrowser(input,verify)}}}finally{calls.remove(token,call);call.cancel()}
  }catch(e:Exception){verify();failure=e}}
  throw IOException("Station directory unavailable",failure)
 }
 suspend fun search(c:Context,token:String,authorize:()->JSONObject):JSONObject=withContext(Dispatchers.IO){coroutineScope{val job=currentCoroutineContext()[Job]!!;val watcher=launch(start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{cancel(token)}};try{slots.withPermit{
  val initial=authorize();val args=initial.getJSONObject("args");val o=initial.getJSONObject("owner");val fingerprint=initial.toString();var checkedAt=SystemClock.elapsedRealtime();fun verify(){job.ensureActive();val now=SystemClock.elapsedRealtime();if(now-checkedAt>=200){check(authorize().toString()==fingerprint);checkedAt=now}}
  loadHealth(c);SdkRadioHealth.configure(c,if(args.has("auto_hide"))args.getBoolean("auto_hide")else null,if(args.has("show_unavailable"))args.getBoolean("show_unavailable")else null);if(args.optBoolean("retry_unavailable"))retryUnavailable(c);args.optString("hide_station").takeIf{it.isNotEmpty()}?.let{hide(c,stationId(it))}
  reserve(o,"search",30)
  val filterKey=owner(o)+"|"+JSONArray(listOf("query","tag","country","language").map{args.optString(it)}).toString()
  val snapshot=if(args.optInt("offset")>0)synchronized(pages){pages[filterKey]}else null
  val catalog=snapshot?:SdkRadioCatalog.get(c,args.optBoolean("refresh"),::verify){source->catalogRows(token,source,::verify)}.let{it.copy(rows=it.rows.filterNot{r->hidden(r.id)||r.aliases.any(::hidden)})}.also{value->synchronized(pages){if(pages.size>=16)pages.remove(pages.keys.first());pages[filterKey]=value}}
  val result=SdkRadioCatalog.page(catalog.rows,args);val ids=result.getJSONArray("items").let{a->(0 until a.length()).map{a.getJSONObject(it).getString("id")}.toSet()}
  try{SdkRadioCatalog.remember(c,catalog.rows.filter{it.id in ids},::verify)}catch(e:Exception){verify()};job.ensureActive();check(authorize().toString()==fingerprint);result.put("partial",catalog.partial).put("cached",true)

 }}finally{watcher.cancel()}}}
 /** Resolve only the selected catalog row, then at most three public HTTPS redirects. */
 internal fun resolve(token:String,url:String,verify:()->Unit,connect:(Request)->Call={liveCall(it)},lookup:(String)->Unit={SdkFeedService.publicAddresses(it);Unit},handoff:((RadioConnection)->Unit)?=null):String{
  var current=publicUrl(url);val visited=mutableSetOf<String>()
  repeat(6){verify();check(visited.add(current)){"Station redirect loop"};val req=request(current).newBuilder().header("Icy-MetaData","1").build();lookup(req.url.host);val call=connect(req);calls[token]=call;var response:okhttp3.Response?=null;var transferred=false
   try{verify();val r=call.execute();response=r;verify()
    if(r.code==200){val type=r.header("Content-Type","")!!.substringBefore(';').lowercase();val path=req.url.encodedPath.lowercase()
     val playlist=type in setOf("audio/x-scpls","application/pls+xml","audio/x-mpegurl","audio/mpegurl","application/x-mpegurl","application/vnd.apple.mpegurl")||path.endsWith(".m3u")||path.endsWith(".pls")||path.endsWith(".m3u8")
     if(playlist){
      if(type=="application/vnd.apple.mpegurl"||path.endsWith(".m3u8")){RadioMimeHints.record(current,true);return current}
      val text=app.onklav.shared.ThemePackage.bounded(r.body?.byteStream()?:error("Empty station playlist"),65536).toString(Charsets.UTF_8)
      if(text.contains("#EXT-X-")){RadioMimeHints.record(current,true);return current}
      current=radioPlaylistTarget(current,text);return@repeat
     }
     if(!(type.startsWith("audio/")||type in setOf("application/octet-stream","application/ogg")))throw SdkRadioHealth.unsupported()
     check(r.header("Content-Encoding","identity").equals("identity",true)){"Unsupported station transfer encoding"}
     check(r.body!=null){"Empty station response"};RadioMimeHints.record(current,false)
     if(handoff!=null){verify();val selected=RadioConnection(current,call,r);try{handoff(selected);transferred=true}catch(e:Exception){selected.close();throw e}}
     return current
    }
    if(r.code !in setOf(301,302,303,307,308))throw SdkRadioHealth.http(r.code)
    current=publicUrl(URI(current).resolve(r.header("Location")?:error("Station redirect missing")).toString())
   }finally{calls.remove(token,call);if(!transferred){call.cancel();runCatching{response?.close()}}}
  };error("Too many station redirects")
 }
 internal data class Prepared(val request:JSONObject,val station:JSONObject,val handle:String,val url:String,val connection:RadioConnection?=null)
 suspend fun prepare(c:Context,token:String,resolveStream:((String,()->Unit)->String)?=null,refreshStation:((String,()->Unit)->JSONObject?)?=null,authorize:()->JSONObject):Prepared=withContext(Dispatchers.IO){coroutineScope{val job=currentCoroutineContext()[Job]!!;val watcher=launch(start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{cancel(token)}};try{slots.withPermit{
  networkChanged();val initial=authorize();val o=initial.getJSONObject("owner");val id=stationId(initial.getJSONObject("args").getString("station"));fun verify(){job.ensureActive();check(authorize().toString()==initial.toString())};reserve(o,"play",30)
  verify();SdkMedia.retireRadioForSelection(o);verify()
  var connection:RadioConnection?=null;var completed=false
  try{val known=SdkRadioCatalog.find(c,id,::verify)
  var selected:JSONObject;var stream:String
  val fresh=freshStations[id]?.takeIf{SystemClock.elapsedRealtime()-it.at in 0..15*60*1000L}
  if(fresh!=null){selected=JSONObject(fresh.station.toString());stream=fresh.url}
  else if(known!=null){selected=known.publicRow().put("id",id);stream=known.url}
  else{val rows=directory(token,"json/stations/byuuid/$id",emptyMap(),::verify);if(rows.length()!=1)throw SdkRadioHealth.http(404);val raw=rows.getJSONObject(0);check(raw.getString("stationuuid")==id);selected=station(raw)?:throw SdkRadioHealth.unsupported();stream=raw.getString("url_resolved")}
  fun open(url:String)=publicUrl(resolveStream?.invoke(url,::verify)?:resolve(token,url,::verify,handoff={connection?.close();connection=it}))
  val url=try{open(stream)}catch(e:Exception){
   verify();freshStations.remove(id)
   if(known==null||known.source!="Radio Browser")throw e
   // Repair only this selected station. Never force a full catalog refresh to listen.
   val updated=try{refreshStation?.invoke(id,::verify)?:directory(token,"json/stations/byuuid/$id",emptyMap(),::verify).let{if(it.length()==1)it.getJSONObject(0)else null}}catch(repair:Exception){verify();null}
   if(updated==null||updated.optString("stationuuid")!=id)throw e
   val row=station(updated)?:throw e;val target=publicUrl(updated.getString("url_resolved"));if(target==stream)throw e
   selected=row;open(target)
  }
  verify();if(freshStations.size>=128)freshStations.clear();freshStations[id]=Fresh(JSONObject(selected.toString()),url,SystemClock.elapsedRealtime())
  val handle=UUID.randomUUID().toString()
  val item=JSONObject().put("url",url).put("title",selected.getString("name")).put("radio_station",handle).put("radio_id",id)
  completed=true;Prepared(JSONObject().put("service","media.play").put("owner",o).put("args",JSONObject().put("queue",JSONArray().put(item)).put("position_ms",0)),selected,handle,url,connection)
  }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){verify();val f=SdkRadioHealth.classify(e);SdkRadioHealth.record(c,id,f);throw f}finally{if(!completed)connection?.close()}
 }}finally{watcher.cancel()}}}
 fun start(c:Context,p:Prepared,verify:()->Unit):JSONObject=AppSdk.carrierEgress{verify();val o=p.request.getJSONObject("owner");val lease=Lease(p.handle,owner(o),p.url,java.util.concurrent.atomic.AtomicReference(p.connection));leases.put(o.getString("id"),lease)?.connection?.getAndSet(null)?.close();try{JSONObject().put("station",p.station).put("player",SdkMedia.execute(c,p.request,verify))}catch(e:Exception){leases.remove(o.getString("id"),lease);lease.connection.getAndSet(null)?.close();throw e}}
 internal fun releasePending(o:JSONObject,item:JSONObject){
  val lease=leases[o.optString("id")]?:return
  if(lease.owner==owner(o)&&lease.handle==item.optString("radio_station"))lease.connection.getAndSet(null)?.close()
 }
 internal fun takeConnection(c:Context,o:JSONObject,item:JSONObject):RadioConnection? {
  policy(c,o,item);return leases[o.getString("id")]?.connection?.getAndSet(null)
 }
 internal fun streamRequest(c:Context,o:JSONObject,item:JSONObject):Request=request(policy(c,o,item).getJSONObject("args").getString("url"))
 fun policy(c:Context,o:JSONObject,item:JSONObject):JSONObject{
  val handle=stationId(item.getString("radio_station"));val gate=AppSdk.nativeTask(c,JSONObject().put("action","media_authorize").put("owner",o).put("args",JSONObject().put("radio_station",handle)))
  val lease=leases[o.getString("id")]?:error("Choose the station again");check(lease.handle==handle&&lease.owner==owner(o)&&item.getString("url")==lease.url)
  return JSONObject().put("service","http.request").put("owner",o).put("origins",JSONArray().put(lease.url.toHttpUrl().let{"${it.scheme}://${it.host}:${it.port}"})).put("args",JSONObject().put("url",lease.url)).put("radio_grant",gate)
 }
}
