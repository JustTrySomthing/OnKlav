package app.onklav.home

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** User-paced OPDS requests and a scoped, cancellable EPUB transfer. No credentials or keys. */
internal object SdkEbooks {
 private const val AGENT="OnKlav-eBooks/1.0 (+https://onklav.app)"
 private val calls=ConcurrentHashMap<String,Call>();private val cancelled=ConcurrentHashMap.newKeySet<String>()
 private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).connectTimeout(12,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).dns(object:Dns{override fun lookup(hostname:String)=SdkFeedService.publicAddresses(hostname)}).build()
 fun cancel(token:String){cancelled.add(token);calls[token]?.cancel()}
 fun cancelAll(){calls.keys.forEach{cancel(it)}}
 internal fun url(raw:String):String {val u=raw.toHttpUrl();require(u.scheme=="https"&&u.port==443&&u.username.isEmpty()&&u.password.isEmpty()&&u.fragment==null&&(u.host=="gutenberg.org"||u.host.endsWith(".gutenberg.org"))){"Choose a public Project Gutenberg HTTPS source"};return u.toString()}
 private fun request(raw:String,token:String,verify:()->Unit,connect:(Request)->Call):Response {var target=url(raw);repeat(6){verify();check(token !in cancelled){"Request cancelled"};val request=Request.Builder().url(target).header("User-Agent",AGENT).header("Accept-Encoding","identity").header("Referer","https://www.gutenberg.org/").build();val call=connect(request);calls[token]=call
   val res=call.execute();if(res.code in setOf(301,302,303,307,308)){try{target=url(res.request.url.resolve(res.header("Location")?:error("Missing redirect"))?.toString()?:error("Invalid redirect"))}finally{res.close();calls.remove(token,call)}}else{if(!res.isSuccessful){val code=res.code;res.close();error(if(code==429)"Project Gutenberg is busy. Wait a little and retry."else"Project Gutenberg unavailable ($code). Your library is kept.")};return res}}
  error("Too many source redirects")}
 internal fun bounded(res:Response,max:Int,verify:()->Unit):ByteArray {val input=res.body?.byteStream()?:error("Empty response");val out=java.io.ByteArrayOutputStream();val buf=ByteArray(16384);input.use{while(true){verify();val n=it.read(buf);if(n<0)break;require(out.size().toLong()+n<=max){"Book or catalog exceeds the supported size"};out.write(buf,0,n)}};return out.toByteArray()}
 internal fun summary(bytes:ByteArray):String {
  require(bytes.size<=1048576);val xml=bytes.toString(Charsets.UTF_8);require(!xml.contains("<!DOCTYPE",true)&&!xml.contains("<!ENTITY",true))
  val parser=XmlPullParserFactory.newInstance().also{it.isNamespaceAware=true}.newPullParser();parser.setInput(StringReader(xml))
  var depth=0;val content=StringBuilder()
  while(parser.eventType!=XmlPullParser.END_DOCUMENT){when(parser.eventType){
   XmlPullParser.START_TAG->{if(depth>0){depth++;if(parser.name in setOf("p","div","br","li"))content.append('\n')}else if(parser.name=="content"){depth=1;content.clear()}}
   XmlPullParser.TEXT->if(depth>0)content.append(parser.text)
   XmlPullParser.END_TAG->if(depth>0){if(parser.name in setOf("p","div","li"))content.append('\n');depth--;if(depth==0){
    val found=Regex("(?is)(?:^|\\n)\\s*Summary:\\s*(.*?)(?=\\n\\s*(?:Reading Level|Author|Title|Credits|Language|Subject|Category|Release Date|Most Recently Updated|Copyright Status|EBook-No\\.|Downloads):|$)").find(content)?.groupValues?.get(1)?.replace(Regex("\\s+")," ")?.trim().orEmpty()
    if(found.isNotBlank()){val short=Regex("(?<=[.!?])\\s+(?=[A-Z\"“])").split(found).take(2).joinToString(" ");return if(short.length<=360)short else short.take(357).substringBeforeLast(' ')+"…"}
   }}
  };parser.next()};return ""
 }
 /** Host-owned read of this service caller's committed library, including legacy Gutenberg file names. */
 internal fun owned(c:Context,owner:JSONObject):Set<Long>{
  val id=owner.getString("id");require(Regex("[a-zA-Z0-9._-]{1,96}").matches(id));val base=File(c.filesDir,"app-sdk/data").canonicalFile;val root=File(base,id);require(id!="."&&id!=".."&&root.canonicalFile==root.absoluteFile)
  fun read(name:String,max:Int):JSONObject{val file=File(root,name);if(!file.exists())return JSONObject();require(file.canonicalFile==file.absoluteFile&&file.length()<=max){"Could not check your library. Reopen eBooks and retry."};return JSONObject(file.readText())}
  val books=read("collection-ebooks_library_v1.json",2*1024*1024).optJSONObject("rows")?:return emptySet();val blobs=read("private-blobs-v1.json",1024*1024).optJSONObject("entries")?:JSONObject();val result=mutableSetOf<Long>()
  for(key in books.keys()){val row=books.optJSONObject(key)?:continue;val explicit=row.optLong("gutenberg_id",0);val blob=blobs.optJSONObject(row.optString("id",key));if(blob?.optBoolean("ready")!=true)continue
   val legacy=Regex("^Gutenberg-([0-9]{1,8})\\.epub$",RegexOption.IGNORE_CASE).matchEntire(blob?.optString("name").orEmpty())?.groupValues?.get(1)?.toLongOrNull()?:0L
   (explicit.takeIf{it in 1..10000000}?:legacy.takeIf{it in 1..10000000})?.let(result::add)
  };return result
 }
 private fun markOwned(c:Context,owner:JSONObject,items:JSONArray){val owned=owned(c,owner);for(i in 0 until items.length()){val row=items.getJSONObject(i);row.put("in_library",row.getLong("id") in owned)}}
 internal data class Catalog(val items:JSONArray,val next:Int?,val epub:String?)
 internal fun parse(bytes:ByteArray):Catalog {
  require(bytes.size<=1048576);val xml=String(bytes,Charsets.UTF_8);require(!xml.contains("<!DOCTYPE",true)&&!xml.contains("<!ENTITY",true)){"Catalog DTDs are unsupported"}
  val parser=XmlPullParserFactory.newInstance().also{it.isNamespaceAware=true}.newPullParser();parser.setInput(StringReader(xml));val rows=JSONArray();var next:Int?=null;var epub:String?=null;var score=-1;var entry:JSONObject?=null;val stack=mutableListOf<String>();var count=0
  while(parser.eventType!=XmlPullParser.END_DOCUMENT){require(++count<100000){"Catalog too complex"};when(parser.eventType){
   XmlPullParser.START_TAG->{val name=parser.name;stack.add(name);require(stack.size<32);if(name=="entry")entry=JSONObject().put("title","").put("author","")
    if(name=="link"){val href=parser.getAttributeValue(null,"href").orEmpty();val rel=parser.getAttributeValue(null,"rel").orEmpty();val type=parser.getAttributeValue(null,"type").orEmpty()
     if(rel=="next"&&entry==null){val n=runCatching{("https://www.gutenberg.org".toHttpUrl().resolve(href)?:error("Invalid page")).queryParameter("start_index")?.toIntOrNull()}.getOrNull();if(n!=null&&n in 1..1000000)next=n}
     if(type=="application/epub+zip"&&rel.startsWith("http://opds-spec.org/acquisition")){val target=runCatching{url("https://www.gutenberg.org".toHttpUrl().resolve(href)?.toString()?:"")}.getOrNull();val rank=if(href.contains("epub3"))3 else if(href.contains("noimages"))2 else 1;if(target!=null&&rank>score){score=rank;epub=target}}
    }}
   XmlPullParser.TEXT->{val e=entry;if(e!=null){val value=parser.text;when{stack.lastOrNull()=="id"->{Regex("https?://(?:www\\.)?gutenberg\\.org/ebooks/([0-9]{1,8})(?:\\.opds)?/?$").find(value.trim())?.groupValues?.get(1)?.toLongOrNull()?.takeIf{it in 1..10000000}?.let{e.put("id",it)}};stack.lastOrNull()=="title"->e.put("title",(e.optString("title")+value).take(200));stack.lastOrNull()=="content"||stack.takeLast(2)==listOf("author","name")->e.put("author",(e.optString("author")+value).take(200))}}}
   XmlPullParser.END_TAG->{if(parser.name=="entry"){entry?.takeIf{it.has("id")&&it.optString("title").isNotBlank()}?.let{if(rows.length()<25)rows.put(it)};entry=null};if(stack.isNotEmpty())stack.removeAt(stack.lastIndex)}
  };parser.next()};return Catalog(rows,next,epub)
 }
 suspend fun search(c:Context,token:String,authorize:()->JSONObject):JSONObject=withContext(Dispatchers.IO){
  try{val a=authorize().getJSONObject("args");val detail=Regex("^id:([0-9]{1,8})$").matchEntire(a.optString("query"))
   if(detail!=null){val id=detail.groupValues[1].toLong();require(id in 1..10000000);val target="https://www.gutenberg.org/ebooks/$id.opds";val file=File(c.cacheDir,"ebook-summary-v2-$id");val description=if(file.isFile&&file.length()>0&&file.length()<=4096)file.readText()else request(target,token,{authorize()},client::newCall).use{summary(bounded(it,1048576){authorize()})}.also{file.writeText(it)};return@withContext JSONObject().put("items",JSONArray().put(JSONObject().put("id",id).put("title","Book").put("author","").put("description",description).put("in_library",id in owned(c,authorize().getJSONObject("owner"))))).put("next",JSONObject.NULL)}
   val target="https://www.gutenberg.org/ebooks/search.opds/".toHttpUrl().newBuilder().addQueryParameter("query",a.optString("query")).addQueryParameter("start_index",a.optInt("start",1).toString()).build().toString();val name=java.security.MessageDigest.getInstance("SHA-256").digest(target.toByteArray()).joinToString(""){"%02x".format(it)};val dir=File(c.cacheDir,"ebook-opds");dir.mkdirs();val cache=File(dir,name)
   val bytes=if(cache.isFile&&System.currentTimeMillis()-cache.lastModified()<900000)cache.readBytes()else request(target,token,{authorize()},client::newCall).use{bounded(it,1048576){authorize();check(token !in cancelled)}}.also{cache.writeBytes(it)};authorize();val page=parse(bytes);markOwned(c,authorize().getJSONObject("owner"),page.items);authorize();JSONObject().put("items",page.items).put("next",page.next?:JSONObject.NULL)
  }finally{calls.remove(token)?.cancel();cancelled.remove(token)}
 }
 suspend fun acquire(c:Context,token:String,authorize:()->JSONObject,transfer:(String,JSONObject)->JSONObject,connect:(Request)->Call=client::newCall):JSONObject=withContext(Dispatchers.IO){
  var begun=false
  fun verify(){authorize();check(token !in cancelled){"Download cancelled"}}
  try{val id=authorize().getJSONObject("args").getLong("id");require(id in 1..10000000);check(id !in owned(c,authorize().getJSONObject("owner"))){"Already in your library"};val catalog=request("https://www.gutenberg.org/ebooks/$id.opds",token,::verify,connect).use{parse(bounded(it,1048576,::verify))};val target=catalog.epub?:error("No supported EPUB edition is available for this book")
   transfer("begin",JSONObject().put("name","Gutenberg-$id.epub").put("mime","application/epub+zip"));begun=true
   request(target,token,::verify,connect).use{res->val body=res.body?:error("Empty book");require(body.contentType()?.type !in setOf("text","image")){"Source did not return an EPUB"};val expected=body.contentLength();require(expected<=1024L*1024*1024){"This book exceeds the 1 GB library file limit"};var copied=0L;body.byteStream().use{input->val buf=ByteArray(65536);var empty=0;while(true){verify();val n=input.read(buf);if(n<0)break;if(n==0){require(++empty<=3);continue};empty=0;require(copied+n<=1024L*1024*1024){"This book exceeds the 1 GB library file limit"};val hex=CharArray(n*2);val digits="0123456789abcdef";for(i in 0 until n){val b=buf[i].toInt()and 255;hex[i*2]=digits[b ushr 4];hex[i*2+1]=digits[b and 15]};transfer("append",JSONObject().put("offset",copied).put("hex",String(hex)));copied+=n}};require(copied>0&&(expected<0||copied==expected)){"Download interrupted. Retry; your existing books are kept."}}
   verify();check(id !in owned(c,authorize().getJSONObject("owner"))){"Already in your library"};transfer("finish",JSONObject()).put("gutenberg_id",id)
  }catch(e:Exception){if(begun)runCatching{transfer("abort",JSONObject())};throw e}finally{calls.remove(token)?.cancel();cancelled.remove(token)}
 }
}
