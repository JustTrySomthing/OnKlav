package app.onklav.home

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.Normalizer
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** One public, paged catalog. Provider URLs and durable playback lookups stay in the host. */
internal object SdkRadioCatalog {
 internal data class Station(val id:String,val name:String,val url:String,val country:String="",val countrycode:String="",val language:String="",val tags:String="",val codec:String="MP3",val bitrate:Int=0,val homepage:String="",val source:String="Radio Browser",val aliases:List<String> = emptyList()) {
  fun publicRow()=JSONObject().put("id",id).put("name",name).put("country",country).put("countrycode",countrycode).put("language",language).put("tags",tags).put("codec",codec).put("bitrate",bitrate).put("aliases",JSONArray(aliases.take(32)))
 }
 internal data class Snapshot(val at:Long,val raw:List<Station>,val rows:List<Station>,val partial:Boolean=false)
 private val lock=Mutex()
 @Volatile private var snapshot:Snapshot?=null
 private var history:LinkedHashMap<String,Station>?=null
 private var lastAttempt=0L
 private val accents=Regex("\\p{M}+");private val punctuation=Regex("[^\\p{L}\\p{N}]+")
 private val quality=Regex("(?i)[\\[(][^\\])]*(?:kbps|kbit|mp3|aac|ogg|opus|quality)[^\\])]*[\\])]")
 private val encoding=Regex("(?i)\\b\\d{2,4}\\s*(?:kbps|kbit/s|kbits|kbit)\\b|\\b(?:mp3|aacp?|stereo|mono)\\b")
 private const val AGE=6*60*60*1000L
 internal fun clean(value:String,max:Int):String{val points=value.codePoints().filter{!Character.isISOControl(it)}.limit(max.toLong()).toArray();return String(points,0,points.size).trim()}
 internal fun fold(value:String)=Normalizer.normalize(value,Normalizer.Form.NFKD).replace(accents,"").lowercase(java.util.Locale.ROOT).replace(punctuation," ").trim()
 internal fun stationName(value:String):String=fold(value.replace(quality," ").replace(encoding," "))

 internal fun streamKey(value:String):String {val u=SdkRadio.publicUrl(value).toHttpUrl();val b=u.newBuilder();u.queryParameterNames.filter{it.startsWith("utm_",true)||it.equals("fbclid",true)}.forEach{b.removeAllQueryParameters(it)};return b.build().toString()}
 private fun host(value:String)=runCatching{value.toHttpUrl().host.removePrefix("www.")}.getOrDefault("")
 private fun distinctive(name:String)=name.length>=8&&name !in setOf("online radio","internet radio","unspecified name","no name","live radio","radio music","radio stream")
 /** Exact streams always merge. Names merge only with matching country/origin, or an unambiguous cross-source identity. */
 internal fun merge(raw:List<Station>):List<Station>{
  data class Group(var row:Station,val aliases:LinkedHashSet<String>,var parent:Int)
  val labels=HashMap<String,String>();fun label(name:String)=labels.getOrPut(name){stationName(name)}
  val groups=mutableListOf<Group>();val streams=HashMap<String,Int>();val names=HashMap<String,MutableSet<Int>>()
  fun root(index:Int):Int{var n=index;while(groups[n].parent!=n)n=groups[n].parent;var p=index;while(groups[p].parent!=p){val next=groups[p].parent;groups[p].parent=n;p=next};return n}
  fun metadata(old:Station,r:Station)=old.copy(country=old.country.ifEmpty{r.country},countrycode=old.countrycode.ifEmpty{r.countrycode},language=old.language.ifEmpty{r.language},tags=(old.tags.split(',')+r.tags.split(',')).filter{it.isNotBlank()}.distinct().joinToString(",").take(120))
  fun join(a:Int,b:Int):Int{val first=root(a);val second=root(b);if(first==second)return first;val keep=minOf(first,second);val drop=maxOf(first,second);groups[keep].row=metadata(groups[keep].row,groups[drop].row);groups[keep].aliases.addAll(groups[drop].aliases);groups[drop].parent=keep;return keep}
  for(r in raw){val stream=runCatching{streamKey(r.url)}.getOrNull()?:continue;val name=label(r.name);val candidates=names[name].orEmpty().map(::root).distinct()
   val named=if(!distinctive(name))null else candidates.firstOrNull{n->val old=groups[n].row
    (r.countrycode.isNotEmpty()&&old.countrycode.isNotEmpty()&&r.countrycode.equals(old.countrycode,true))||
    ((r.countrycode.isEmpty()||old.countrycode.isEmpty()||r.countrycode.equals(old.countrycode,true))&&(host(r.url)==host(old.url)||(host(r.homepage).isNotEmpty()&&host(r.homepage)==host(old.homepage))))
   }?:candidates.singleOrNull()?.takeIf{n->val old=groups[n].row;old.source!=r.source&&(r.countrycode.isEmpty()||old.countrycode.isEmpty())}
   val exact=streams[stream]?.let(::root);val existing=if(exact!=null&&named!=null)join(exact,named)else exact?:named
   val n=if(existing==null){val index=groups.size;groups+=Group(r,linkedSetOf(r.id).apply{addAll(r.aliases)},index);index}else{val g=groups[existing];g.aliases.add(r.id);g.aliases.addAll(r.aliases);g.row=metadata(g.row,r);existing}
   streams[stream]=n;names.getOrPut(name){linkedSetOf()}.add(n)
  }
  return groups.filterIndexed{i,g->root(i)==i}.map{it.row.copy(aliases=it.aliases.toList())}.sortedWith(compareBy<Station>{label(it.name)}.thenBy{it.countrycode}.thenBy{it.id})
 }

 internal fun aliases(id:String):List<String> = snapshot?.rows?.firstOrNull{it.id==id||id in it.aliases}?.let{it.aliases+it.id}?:emptyList()
 private fun direct(value:String)=runCatching{SdkRadio.publicUrl(value).also{require(!it.toHttpUrl().encodedPath.lowercase().endsWith(".asx"))}}.getOrNull()
 internal fun fromBrowser(v:JSONObject):Station?{val p=SdkRadio.station(v)?:return null;return Station(p.getString("id"),p.getString("name"),v.getString("url_resolved"),p.optString("country"),p.optString("countrycode"),p.optString("language"),p.optString("tags"),p.getString("codec"),p.optInt("bitrate"),clean(v.optString("homepage"),2048))}
 internal fun readBrowser(input:InputStream,verify:()->Unit):List<Station>{val rows=mutableListOf<Station>();JsonReader(InputStreamReader(input,Charsets.UTF_8)).use{reader->reader.beginArray();var count=0;while(reader.hasNext()){verify();check(++count<=100000){"Radio catalog is too large"};val v=JSONObject();reader.beginObject();while(reader.hasNext()){val key=reader.nextName();if(key in setOf("stationuuid","name","url_resolved","country","countrycode","language","tags","codec","homepage")){if(reader.peek()==JsonToken.NULL)reader.nextNull()else v.put(key,reader.nextString())}else if(key in setOf("hls","lastcheckok","bitrate")){v.put(key,runCatching{reader.nextInt()}.getOrElse{throw IOException("Invalid radio catalog number")})}else reader.skipValue()};reader.endObject();fromBrowser(v)?.let(rows::add)};reader.endArray()};return rows}
 internal fun readIcecast(input:InputStream,verify:()->Unit):List<Station>{val rows=mutableListOf<Station>();val parser=XmlPullParserFactory.newInstance().newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);parser.setInput(input,null);var event=parser.eventType;val path=mutableListOf<String>();var fields=linkedMapOf<String,String>();var count=0;var root=false
  while(event!=XmlPullParser.END_DOCUMENT){verify();when(event){XmlPullParser.DOCDECL->error("Radio directory DTD is unsupported");XmlPullParser.START_TAG->{path.add(parser.name);check(path.size<=16);if(path.size==1){check(parser.name=="directory");root=true};if(path==listOf("directory","entry")){check(++count<=100000);fields=linkedMapOf()}};XmlPullParser.TEXT,XmlPullParser.CDSECT,XmlPullParser.ENTITY_REF->{if(path.size==3&&path[1]=="entry"&&path[2] in setOf("server_name","server_type","listen_url","genre","bitrate")){val key=path[2];fields[key]=(fields[key].orEmpty()+parser.text.orEmpty()).take(if(key=="listen_url")4097 else 2048)}};XmlPullParser.END_TAG->{if(path==listOf("directory","entry")){val url=direct(fields["listen_url"].orEmpty());val name=clean(fields["server_name"].orEmpty(),100);val type=fields["server_type"].orEmpty().substringBefore(';').lowercase();val codec=when(type){"audio/mpeg","audio/mp3","application/mp3","mp3"->"MP3";"audio/aac","audio/aacp","application/aacp"->"AAC";"audio/ogg","application/ogg","audio/vorbis"->"OGG";"audio/opus"->"OPUS";"audio/flac"->"FLAC";else->""};if(url!=null&&name.isNotEmpty()&&codec.isNotEmpty()){val id=UUID.nameUUIDFromBytes(("onklav.icecast|"+streamKey(url)).toByteArray(Charsets.UTF_8)).toString();rows+=Station(id,name,url,tags=clean(fields["genre"].orEmpty(),120),codec=codec,bitrate=fields["bitrate"]?.toIntOrNull()?.coerceIn(0,1024)?:0,source="Icecast")}};if(path.isNotEmpty())path.removeAt(path.lastIndex)}};event=parser.nextToken()};check(root);return rows
 }
 internal class Limited(input:InputStream,private val max:Long):FilterInputStream(input){private var count=0L;override fun read():Int{val n=super.read();if(n>=0)check(++count<=max){"Radio catalog exceeds its size limit"};return n};override fun read(b:ByteArray,off:Int,len:Int):Int{val n=`in`.read(b,off,len);if(n>0){count+=n;check(count<=max){"Radio catalog exceeds its size limit"}};return n};override fun skip(n:Long):Long{val b=ByteArray(minOf(n,65536).toInt());var skipped=0L;while(skipped<n){val k=read(b,0,minOf(b.size.toLong(),n-skipped).toInt());if(k<0)break;skipped+=k};return skipped}}
 private fun file(c:Context,name:String)=File(c.filesDir,"radio-directory/$name").also{check(it.parentFile!!.mkdirs()||it.parentFile!!.isDirectory)}
 private fun read(input:InputStream,verify:()->Unit,compressed:Boolean=true):Pair<Long,List<Station>>{val rows=mutableListOf<Station>();var at=0L;JsonReader(InputStreamReader(Limited(if(compressed)GZIPInputStream(input)else input,64L*1024*1024),Charsets.UTF_8)).use{r->r.beginObject();while(r.hasNext()){when(r.nextName()){"at"->at=r.nextLong();"rows"->{r.beginArray();while(r.hasNext()){verify();check(rows.size<100000);val v=JSONObject();r.beginObject();while(r.hasNext()){val key=r.nextName();when{key=="bitrate"->v.put(key,r.nextInt());key=="aliases"->{val a=JSONArray();r.beginArray();while(r.hasNext()){check(a.length()<10000);a.put(r.nextString())};r.endArray();v.put(key,a)};r.peek()==JsonToken.NULL->r.nextNull();else->v.put(key,r.nextString())}};r.endObject();val id=runCatching{SdkRadio.stationId(v.getString("id"))}.getOrNull();val url=direct(v.optString("url"));val name=clean(v.optString("name"),100);if(id!=null&&url!=null&&name.isNotEmpty())rows+=Station(id,name,url,clean(v.optString("country"),80),clean(v.optString("countrycode"),2),clean(v.optString("language"),64),clean(v.optString("tags"),120),v.optString("codec"),v.optInt("bitrate").coerceIn(0,1024),v.optString("homepage"),v.optString("source"),v.optJSONArray("aliases")?.let{a->(0 until a.length()).map{a.getString(it)}}?:emptyList())};r.endArray()};else->r.skipValue()}};r.endObject()};return at to rows}
 private fun write(target:File,at:Long,rows:List<Station>,verify:()->Unit){val temp=File(target.parentFile,target.name+".tmp");try{FileOutputStream(temp).use{raw->val gzip=GZIPOutputStream(raw);val w=JsonWriter(OutputStreamWriter(gzip,Charsets.UTF_8));w.beginObject().name("at").value(at).name("rows").beginArray();rows.forEach{r->verify();w.beginObject();for((k,v)in listOf("id" to r.id,"name" to r.name,"url" to r.url,"country" to r.country,"countrycode" to r.countrycode,"language" to r.language,"tags" to r.tags,"codec" to r.codec,"homepage" to r.homepage,"source" to r.source))w.name(k).value(v);w.name("bitrate").value(r.bitrate.toLong());w.name("aliases").beginArray();r.aliases.forEach{w.value(it)};w.endArray().endObject()};w.endArray().endObject();w.flush();gzip.finish();raw.fd.sync()};verify();Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)}finally{temp.delete()}}
 /** Uses a compact build-time snapshot immediately; refreshes public provider data at most every30minutes. */
 suspend fun get(c:Context,force:Boolean,verify:()->Unit,fetch:(String)->List<Station>):Snapshot=lock.withLock{
  verify();var current=snapshot?:run{val disk=runCatching{file(c,"catalog.json.gz").inputStream().use{read(it,verify)}}.getOrNull();val data=disk?:c.assets.open("radio/catalog.json").use{read(it,verify,compressed=false)};Snapshot(data.first,data.second,merge(data.second)).also{snapshot=it}}
  val now=System.currentTimeMillis();if(force&&now-lastAttempt !in 0..30*60*1000L){lastAttempt=now;val all=mutableListOf<Station>();var partial=false
   for(source in listOf("Radio Browser","Icecast")){verify();try{val fresh=fetch(source);check(fresh.isNotEmpty());all+=fresh}catch(e:Exception){verify();partial=true;all+=current.raw.filter{it.source==source}}}
   verify();current=Snapshot(now,all,merge(all),partial);runCatching{write(file(c,"catalog.json.gz"),now,all,verify)}.onFailure{verify()};snapshot=current
  };current
 }
 internal fun page(rows:List<Station>,args:JSONObject):JSONObject{val q=fold(args.optString("query"));val tag=fold(args.optString("tag"));val country=args.optString("country");val language=fold(args.optString("language"));val matching=rows.filter{r->(q.isEmpty()||fold(r.name+" "+r.tags+" "+r.country+" "+r.language).contains(q))&&(tag.isEmpty()||fold(r.tags).contains(tag))&&(country.isEmpty()||r.countrycode.equals(country,true))&&(language.isEmpty()||fold(r.language).contains(language))};val offset=args.optInt("offset");val ordered=if(q.isEmpty())matching else matching.sortedBy{if(fold(it.name)==q)0 else if(fold(it.name).startsWith(q))1 else 2};return JSONObject().put("items",JSONArray(ordered.drop(offset).take(20).map{it.publicRow()})).put("total",matching.size).put("next",if(offset+20<matching.size)offset+20 else JSONObject.NULL).put("source","Radio Browser + Icecast")}
 suspend fun remember(c:Context,rows:List<Station>,verify:()->Unit)=lock.withLock{val map=history?:runCatching{file(c,"history.json.gz").inputStream().use{read(it,verify)}.second.associateByTo(linkedMapOf()){it.id}}.getOrDefault(linkedMapOf()).also{history=it};var dirty=false;for(r in rows){if(map[r.id]!=r){check(map.size<100000||map.containsKey(r.id));map[r.id]=r;dirty=true}};if(dirty)write(file(c,"history.json.gz"),System.currentTimeMillis(),map.values.toList(),verify)}
 suspend fun find(c:Context,id:String,verify:()->Unit):Station?=lock.withLock{verify();snapshot?.rows?.firstOrNull{it.id==id||id in it.aliases}?.let{return@withLock it};val map=history?:runCatching{file(c,"history.json.gz").inputStream().use{read(it,verify)}.second.associateByTo(linkedMapOf()){it.id}}.getOrDefault(linkedMapOf()).also{history=it};map[id]?:map.values.firstOrNull{id in it.aliases}}
}
