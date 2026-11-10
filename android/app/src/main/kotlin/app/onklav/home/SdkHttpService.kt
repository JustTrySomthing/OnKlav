package app.onklav.home

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal object SdkHttpPolicy {
    fun origin(raw:String):String = try {
        SdkFeedService.address(raw)
        val uri=URI(raw);val host=uri.host?.lowercase(Locale.ROOT)?:error("Use a public HTTPS origin")
        require(uri.rawFragment==null&&host.contains('.')&&host.split('.').all{it.isNotEmpty()&&it.length<=63&&!it.startsWith('-')&&!it.endsWith('-')&&it.all{c->c in 'a'..'z'||c.isDigit()||c=='-'}}&&host.any{it in 'a'..'z'}){"Use a public DNS HTTPS origin"}
        "https://$host"
    }catch(e:Exception){throw IllegalArgumentException("Use a public DNS HTTPS origin")}
    fun request(operation:JSONObject,uploadAuthorize:()->Unit={},filesDir:java.io.File?=null):Request {
        val args=operation.getJSONObject("args");require(args.keys().asSequence().all{it in setOf("url","method","headers","body","account","response","upload")}){"Unsupported HTTP arguments"}
        val url=args.getString("url");val allowed=operation.getJSONArray("origins");require(allowed.length() in 1..8){"Missing signed HTTPS origins"}
        val origins=List(allowed.length()){allowed.getString(it)}
        require(origins.all{origin(it)==it}&&origin(url)in origins){"HTTPS origin is not declared by this signed app"}
        val owner=operation.getJSONObject("owner").getString("id");require(owner.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+"))&&owner.length<=128){"Invalid HTTP app identity"}
        require(!args.has("response")||args.getString("response") in setOf("text","hex")){"Unsupported HTTP response mode"}
        val method=args.optString("method","GET");require(method in setOf("GET","HEAD","POST","PUT","PATCH","DELETE")){"Unsupported HTTP method"}
        require(!args.has("body")||args.get("body")is String&&method!in setOf("GET","HEAD")){"Invalid HTTP body"}
        val body=args.optString("body","").toByteArray(Charsets.UTF_8);require(body.size<=65536){"HTTP body exceeds64KiB"}
        val builder=Request.Builder().url(url).header("User-Agent","OnKlav/0.83 ($owner; https://onklav.app)")
        val headers=args.optJSONObject("headers");require(!args.has("headers")||headers!=null){"Invalid HTTP headers"}
        if(headers!=null){require(headers.length()<=8);val names=mutableSetOf<String>();for(key in headers.keys()){
            val name=key.lowercase(Locale.ROOT);require(name in setOf("accept","accept-language","content-type","if-none-match","if-modified-since","x-request-id")&&names.add(name)){"Only noncredential HTTP headers are supported"}
            val value=headers.get(key);require(value is String&&value.length<=512&&value.all{it.code in 32..126}){"Invalid HTTP header value"};builder.header(key,value)
        }}
        val type=headers?.keys()?.asSequence()?.firstOrNull{it.equals("content-type",true)}?.let{headers.getString(it)}?:"text/plain; charset=utf-8"
        val payload=if(args.has("upload")){
            require(method=="POST"&&!args.has("body"));val file=SdkHttpUpload.source(filesDir?:error("Upload requires host context"),operation);val size=operation.getJSONObject("upload_file").getLong("size")
            object:okhttp3.RequestBody(){override fun contentType()=type.toMediaTypeOrNull();override fun contentLength()=size;override fun isOneShot()=true;override fun writeTo(sink:okio.BufferedSink){SdkHttpUpload.copy(file,size,sink.outputStream(),uploadAuthorize)}}
        }else if(method in setOf("POST","PUT","PATCH","DELETE")){
            val delegate=body.toRequestBody(type.toMediaTypeOrNull())
            object:okhttp3.RequestBody(){
                override fun contentType()=delegate.contentType()
                override fun contentLength()=delegate.contentLength()
                override fun writeTo(sink:okio.BufferedSink)=delegate.writeTo(sink)
                override fun isOneShot()=true
            }
        }else null
        return builder.method(method,payload).build()
    }
}

/** No cookies, disk cache, credentials, redirects or application-level retries. */
internal object SdkHttpService {
    private val client=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
        .dns(object:okhttp3.Dns{override fun lookup(hostname:String)=SdkFeedService.publicAddresses(hostname)}).build()
    private val requests=ConcurrentHashMap<String,Call>()
    private val slots=Semaphore(4)
    private class Budget{var active=0;val times=java.util.ArrayDeque<Long>()}
    private val budgets=linkedMapOf<String,Budget>()
    private fun reserve(owner:String):Budget=synchronized(budgets){
        val now=System.nanoTime()
        budgets.values.forEach{b->while(!b.times.isEmpty()&&now-b.times.first>=60_000_000_000L)b.times.removeFirst()}
        if(owner !in budgets&&budgets.size>=128)budgets.entries.removeAll{it.value.active==0&&it.value.times.isEmpty()}
        check(owner in budgets||budgets.size<128){"HTTP owner limit reached"}
        val b=budgets.getOrPut(owner){Budget()};check(b.active<2&&b.times.size<60){"Request limit reached. Try again later."};b.active++;b.times.addLast(now);b
    }
    fun cancel(token:String){requests.remove(token)?.cancel()}
    fun cancelAll(){requests.keys.toList().forEach(::cancel)}
    suspend fun fetch(token:String,authorize:suspend ()->JSONObject,connect:(Request)->Call={client.newCall(it)}):JSONObject=fetchWithAccount(token,authorize,connect=connect)
    suspend fun fetchWithAccount(token:String,authorize:suspend ()->JSONObject,context:android.content.Context?=null,accountAuthorize:(()->JSONObject)?=null,accountVault:SdkAccountVault?=null,connect:(Request)->Call={client.newCall(it)}):JSONObject=withContext(Dispatchers.IO){
        val initial=authorize();val binary=initial.getJSONObject("args").optString("response","text")=="hex";val limit=if(binary)262144 else 131072;var request=SdkHttpPolicy.request(initial,uploadAuthorize={check((accountAuthorize?:error("Live upload authorization required"))().toString()==initial.toString())},filesDir=context?.filesDir)
        if(initial.getJSONObject("args").has("account")){
         val live=accountAuthorize?:error("Live account authorization is required");val authorized=SdkAccounts.authenticated(context?:error("Account context unavailable"),token,initial,request.url.toString(),{check(live().toString()==initial.toString())},accountVault?:SdkAccountVault.forContext(context))
         request=request.newBuilder().header("Authorization",authorized.build().header("Authorization")!!).build()
        }
        val owner=initial.getJSONObject("owner")
        suspend fun live(){authorize();if(initial.getJSONObject("args").has("account")){val verify=accountAuthorize!!;SdkAccounts.validateUse(context!!,initial,request.header("Authorization"),{check(verify().toString()==initial.toString())},accountVault?:SdkAccountVault.forContext(context))}}
        val budget=reserve(owner.getString("id")+":"+owner.getString("publisher"))
        try{slots.withPermit{
            live();currentCoroutineContext().ensureActive()
            val call=connect(request);requests[token]=call
            try{
                // Register first: a revocation during setup cancels this call too.
                live();currentCoroutineContext().ensureActive()
                coroutineScope{
                val watcher=launch(start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{call.cancel()}}
                try{call.execute().use{response->
                    require(response.code in 100..599){"Invalid HTTP status"}
                    val body=response.body;require(body==null||body.contentLength()<=limit){"HTTP response exceeds its byte limit"}
                    val out=java.io.ByteArrayOutputStream()
                    body?.byteStream()?.use{input->val buffer=ByteArray(8192);var empty=0
                        while(true){currentCoroutineContext().ensureActive();live();val n=input.read(buffer);if(n<0)break;if(n==0){check(++empty<=3){"HTTP source returned no data"};continue};empty=0
                            require(out.size()+n<=limit){"HTTP response exceeds its byte limit"};out.write(buffer,0,n)
                        }
                    }
                    live();currentCoroutineContext().ensureActive()
                    val headers=JSONObject()
                    for(name in listOf("content-type","etag","last-modified","retry-after","cache-control","location","date","expires","age"))response.header(name)?.let{value->
                        if(value.length<=512&&value.all{it.code in 32..126}){
                            if(name!="location")headers.put(name,value)
                            else runCatching{val target=URI(request.url.toString()).resolve(value).toString();if(target.length<=512&&SdkHttpPolicy.origin(target)in List(initial.getJSONArray("origins").length()){initial.getJSONArray("origins").getString(it)})headers.put(name,target)}
                        }
                    }
                    SdkHttpPayload.result(response.code,out.toByteArray(),headers,binary)
                }}finally{watcher.cancel()}
            }}catch(e:CancellationException){throw e}catch(e:Exception){
                // No server URL, query, response body or credential-like exception text in diagnostics.
                throw IllegalStateException("HTTPS request failed, was cancelled or exceeded its limits")
            }finally{requests.remove(token,call);call.cancel()}
        }}finally{synchronized(budgets){budget.active--}}
    }
}
