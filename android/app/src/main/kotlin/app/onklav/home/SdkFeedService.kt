package app.onklav.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.text.Html
import android.util.Xml
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/** Host service shared by SDK programs. It never gives a guest a socket or Context. */
internal object SdkFeedService {
    private val requests = ConcurrentHashMap<String,Call>()
    fun cancel(token:String) { requests.remove(token)?.cancel() }
    fun cancelAll() { requests.keys.toList().forEach(::cancel) }
    fun address(raw:String):String {
        require(raw.length<=4096 && raw.none { it.isWhitespace() || it.isISOControl() || it=='\\' }) { "Enter an HTTPS feed address" }
        val uri=URI(raw)
        val host=uri.host?.lowercase(Locale.ROOT) ?: error("Enter an HTTPS feed address")
        require(uri.scheme=="https" && uri.rawUserInfo==null && (uri.port == -1 || uri.port==443) && host!="localhost" && !host.endsWith(".localhost") && !host.endsWith(".local")) { "Use a public HTTPS source" }
        if(host.all { it.isDigit()||it=='.' } || host.contains(':')) require(isPublic(InetAddress.getByName(host.removeSurrounding("[","]")))) { "Use a public feed address" }
        return uri.toASCIIString()
    }
    internal fun publicAddresses(host:String):List<InetAddress> {
        val addresses=InetAddress.getAllByName(host)
        require(addresses.isNotEmpty() && addresses.all { isPublic(it) }) { "Use a public feed address" }
        return addresses.toList()
    }
    internal fun isPublic(ip:InetAddress):Boolean {
        if(ip.isAnyLocalAddress||ip.isLoopbackAddress||ip.isLinkLocalAddress||ip.isSiteLocalAddress||ip.isMulticastAddress) return false
        val b=ip.address.map { it.toInt() and 255 }
        // Android mobile networks can synthesize IPv6 DNS answers using RFC6052's
        // well-known NAT64 prefix. Validate the embedded IPv4 destination too.
        if(b.size==16) {
            if(b.take(12)==listOf(0,100,255,155,0,0,0,0,0,0,0,0))return isPublic(InetAddress.getByAddress(ip.address.copyOfRange(12,16)))
            return b[0] and 0xe0 == 0x20 && !(b[0]==0x20&&b[1]==0x01&&b[2]==0x0d&&b[3]==0xb8)
        }
        return !(b[0]==0||b[0]>=224||b[0]==100&&b[1] in 64..127||b[0]==192&&b[1]==0||b[0]==198&&b[1] in 18..19||b[0]==198&&b[1]==51&&b[2]==100||b[0]==203&&b[1]==0&&b[2]==113)
    }
    private val client=OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).dns(object:okhttp3.Dns { override fun lookup(hostname:String)=publicAddresses(hostname) }).build()
    suspend fun fetch(context:Context, token:String, args:JSONObject, authorize:suspend ()->Unit):JSONObject = withContext(Dispatchers.IO) { coroutineScope {
        if(args.optBoolean("wifi")) {
            val cm=context.getSystemService(ConnectivityManager::class.java)
            val n=cm.getNetworkCapabilities(cm.activeNetwork)
            check(n!=null && (n.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||n.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) { "Wi-Fi Only Is On. Connect to Wi-Fi to Refresh." }
        }
        var next=address(args.getString("url"))
        repeat(5) {
            currentCoroutineContext().ensureActive();authorize()
            val builder=Request.Builder().url(next).header("User-Agent","OnKlav-Feeds/0.11").header("Accept","application/rss+xml, application/atom+xml, application/xml, text/xml")
            for((key,header) in listOf("etag" to "If-None-Match","modified" to "If-Modified-Since")) {
                val value=args.optString(key).take(512)
                if(value.isNotBlank() && value.none { it=='\r'||it=='\n' }) builder.header(header,value)
            }
            val call=client.newCall(builder.build());requests[token]=call
            try {
                // Register before the final live check so revocation cannot miss setup.
                authorize();currentCoroutineContext().ensureActive()
                val watcher=launch(start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{call.cancel()}}
                try { call.execute().use { response ->
                when(val code=response.code) {
                    301,302,303,307,308 -> next=address(URI(next).resolve(response.header("Location") ?: error("Source redirect has no destination")).toString())
                    304 -> { authorize();return@coroutineScope JSONObject().put("unchanged",true).put("etag",response.header("ETag") ?: args.optString("etag")).put("modified",response.header("Last-Modified") ?: args.optString("modified")) }
                    200 -> {
                        val bytes=(response.body ?: error("Source returned no feed")).byteStream().use { input -> readBounded(input,authorize) }
                        currentCoroutineContext().ensureActive();authorize()
                        return@coroutineScope parse(bytes,next).put("etag",response.header("ETag").orEmpty().take(512)).put("modified",response.header("Last-Modified").orEmpty().take(512))
                    }
                    else -> error("Source returned HTTP $code")
                }
            } } finally { watcher.cancel() }
            } finally { requests.remove(token,call);call.cancel() }
        }
        error("Too many feed redirects")
    }}
    internal suspend fun readBounded(input:java.io.InputStream,authorize:suspend ()->Unit):ByteArray {
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var empty=0
        while(true){
            currentCoroutineContext().ensureActive();authorize();val n=input.read(buffer)
            if(n<0)break
            if(n==0){check(++empty<=3){"Feed source returned no data"};continue};empty=0
            require(out.size()+n<=2*1024*1024){"This feed is too large"};out.write(buffer,0,n)
        }
        currentCoroutineContext().ensureActive();authorize();return out.toByteArray()
    }
    private fun plain(raw:String,limit:Int):String = Html.fromHtml(raw,Html.FROM_HTML_MODE_LEGACY).toString().filter { !it.isISOControl() || it=='\n'||it=='\t' }.trim().take(limit).let { if(it.lastOrNull()?.isHighSurrogate()==true)it.dropLast(1) else it }
    private fun date(raw:String):Long {
        runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()?.let { return it.coerceAtLeast(0) }
        for(pattern in listOf("EEE, dd MMM yyyy HH:mm:ss Z","EEE, dd MMM yyyy HH:mm Z","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd")) {
            runCatching { SimpleDateFormat(pattern,Locale.US).apply { timeZone=TimeZone.getTimeZone("UTC");isLenient=false }.parse(raw)?.time }.getOrNull()?.let { return it.coerceAtLeast(0) }
        }
        return 0
    }
    fun parse(bytes:ByteArray,base:String):JSONObject {
        require(bytes.size<=2*1024*1024) { "This feed is too large" }
        address(base)
        val p=Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true)
        p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false)
        p.setInput(ByteArrayInputStream(bytes),null)
        val items=JSONArray();val seen=mutableSetOf<String>()
        var depth=0;var events=0;var itemDepth=-1;var field="";var fieldDepth=-1
        var title="";var link="";var guid="";var body="";var summary="";var published="";var feedTitle=""
        val text=StringBuilder()
        while(p.eventType!=XmlPullParser.END_DOCUMENT) {
            require(++events<=40000) { "This feed is too complex" }
            when(p.eventType) {
                XmlPullParser.DOCDECL -> error("Document types and external entities are not supported")
                XmlPullParser.START_TAG -> {
                    depth++;if(depth==1) require(p.name.lowercase(Locale.ROOT) in listOf("rss","feed","rdf")) { "This source did not return a supported RSS or Atom feed" };require(depth<=48) { "This feed is too deeply nested" }
                    val name=p.name.lowercase(Locale.ROOT)
                    if(name in listOf("item","entry")) { require(itemDepth<0) { "Nested feed items are unsupported" };itemDepth=depth;title="";link="";guid="";body="";summary="";published="" }
                    else if(itemDepth>0 && depth==itemDepth+1 && name=="link" && p.getAttributeValue(null,"href")!=null) {
                        if(p.getAttributeValue(null,"rel") in listOf(null,"alternate")) link=p.getAttributeValue(null,"href")
                    } else if(field.isEmpty() && (itemDepth>0 && depth==itemDepth+1 && name in listOf("title","link","guid","description","summary","content","encoded","pubdate","published","updated","date") || itemDepth<0 && name=="title" && feedTitle.isEmpty())) {
                        field=name;fieldDepth=depth;text.setLength(0)
                    } else if(field.isNotEmpty() && name in listOf("br","p","div")) text.append('\n')
                }
                XmlPullParser.TEXT,XmlPullParser.CDSECT,XmlPullParser.ENTITY_REF,XmlPullParser.IGNORABLE_WHITESPACE -> if(field.isNotEmpty() && text.length<24000) { text.append(p.text.orEmpty().take(24000-text.length)) }
                XmlPullParser.END_TAG -> {
                    if(depth==fieldDepth) {
                        val value=text.toString()
                        if(itemDepth<0) feedTitle=plain(value,80) else when(field) {
                            "title"->title=plain(value,240);"link"->link=value.trim();"guid"->guid=value.trim()
                            "content","encoded"->body=plain(value,6000);"summary","description"->summary=plain(value,6000)
                            "pubdate","published","updated","date"->if(published.isEmpty()) published=value.trim()
                        }
                        field="";fieldDepth=-1
                    }
                    if(depth==itemDepth) {
                        val url=runCatching { address(URI(base).resolve(link.ifEmpty { guid }).toString()) }.getOrNull()
                        if(url!=null && title.isNotBlank() && seen.add(url) && items.length()<40) items.put(JSONObject().put("url",url).put("title",title).put("body",body.ifBlank { summary }).put("published",date(published)))
                        itemDepth=-1
                    }
                    depth--
                }
            }
            p.nextToken()
        }
        require(items.length()>0 || feedTitle.isNotBlank()) { "This source did not return a supported RSS or Atom feed" }
        val result=JSONObject().put("title",feedTitle).put("items",items)
        require(result.toString().toByteArray(Charsets.UTF_8).size<950000) { "This feed has too much article text" }
        return result
    }
}
