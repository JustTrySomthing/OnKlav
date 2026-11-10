package app.onklav.home

import android.app.job.*
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import android.util.AtomicFile
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Host-owned durable GET/download jobs. No Wasm execution in the background. */
internal object SdkJobs {
 internal const val BASE=0x53444a00
 private val calls=ConcurrentHashMap<String,Call>()
 private val client=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(90,TimeUnit.SECONDS)
  .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).dns(object:Dns{override fun lookup(hostname:String)=SdkFeedService.publicAddresses(hostname)}).build()
 private fun disk(c:Context)=AtomicFile(File(c.noBackupFilesDir,"sdk-jobs.json"))
 private fun load(c:Context):JSONObject {val f=disk(c);if(!f.baseFile.exists()&&!File(f.baseFile.path+".bak").exists())return JSONObject();return f.openRead().use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=input.read(buffer);if(n<0)break;check(n>0&&out.size()+n<=1_048_576);out.write(buffer,0,n)};JSONObject(out.toString("UTF-8")).also{check(it.length()<=32)}}}
 private fun save(c:Context,rows:JSONObject){val bytes=rows.toString().toByteArray();check(bytes.size<=1_048_576);val f=disk(c);f.baseFile.parentFile!!.mkdirs();val out=f.startWrite();try{out.write(bytes);f.finishWrite(out)}catch(e:Exception){f.failWrite(out);throw e}}
 private fun scheduler(c:Context)=c.getSystemService(JobScheduler::class.java)
 private fun own(a:JSONObject,b:JSONObject)=a.getString("id")==b.getString("id")&&a.getString("publisher")==b.getString("publisher")
 private fun publicRow(r:JSONObject)=JSONObject().apply{for(k in listOf("id","key","kind","state","bytes","total","file","error","attempts"))if(r.has(k))put(k,r.get(k))}
 private fun enqueue(c:Context,r:JSONObject){val spec=r.getJSONObject("args");val extras=PersistableBundle().apply{putString("id",r.getString("id"));putString("token",r.getString("token"))}
  val job=JobInfo.Builder(BASE+r.getInt("slot"),ComponentName(c,SdkTransferJobService::class.java)).setPersisted(true).setExtras(extras)
   .setRequiredNetworkType(if(spec.optBoolean("unmetered"))JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY).setRequiresCharging(spec.optBoolean("charging")).setRequiresBatteryNotLow(true)
   .setMinimumLatency((r.getLong("due")-System.currentTimeMillis()).coerceAtLeast(1000)).setBackoffCriteria(60_000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build()
  check(scheduler(c).schedule(job)==JobScheduler.RESULT_SUCCESS){"Android could not schedule this task"}
 }
 private fun row(c:Context,id:String,token:String):JSONObject=AppSdk.carrierEgress{load(c).getJSONObject(id).also{check(it.getString("token")==token&&it.getString("state")!in setOf("cancelled","failed","completed")){"Task expired"}}}
 private fun update(c:Context,r:JSONObject,change:(JSONObject)->Unit):JSONObject=AppSdk.carrierEgress{val rows=load(c);val live=rows.getJSONObject(r.getString("id"));check(live.getString("token")==r.getString("token"));change(live);rows.put(live.getString("id"),live);save(c,rows);JSONObject(live.toString())}
 internal fun authorize(c:Context,r:JSONObject,args:JSONObject):JSONObject=AppSdk.nativeTask(c,JSONObject().put("action","background_authorize").put("owner",r.getJSONObject("owner")).put("args",args))as JSONObject
 private fun blob(c:Context,r:JSONObject,service:String,args:JSONObject)=AppSdk.nativeTask(c,JSONObject().put("action","background_blob").put("owner",r.getJSONObject("owner")).put("service",service).put("args",args).put("job_token",r.getString("token")))as JSONObject
 private fun stagingId(r:JSONObject)=java.security.MessageDigest.getInstance("SHA-256").digest(("onklav.job.stage.v1\u0000"+r.getString("token")).toByteArray()).joinToString(""){"%02x".format(it)}.take(32)
 private fun cleanup(c:Context,r:JSONObject){val file=r.optString("staging").ifEmpty{stagingId(r)};runCatching{AppSdk.nativeTask(c,JSONObject().put("action","task_cleanup").put("owner",r.getJSONObject("owner")).put("file",file))}}
 private fun begin(c:Context,r:JSONObject,args:JSONObject):Pair<JSONObject,String> = AppSdk.carrierEgress{
  row(c,r.getString("id"),r.getString("token"));val file=blob(c,r,"blobs.begin",args).getString("id");update(c,r){it.put("staging",file)} to file
 }
 fun cancel(id:String){calls.remove(id)?.cancel();SdkHttpService.cancel("job-$id");SdkAccounts.cancel("download-$id")}
 fun cancelNetworkCalls(){calls.values.forEach{it.cancel()}}
 fun execute(c:Context,request:JSONObject,authorize:()->Unit):Any=AppSdk.carrierEgress{
  authorize();val rows=load(c);val owner=request.getJSONObject("owner");val args=request.getJSONObject("args");val service=request.getString("service")
  val mine=rows.keys().asSequence().map{rows.getJSONObject(it)}.filter{own(it.getJSONObject("owner"),owner)}.toList()
  if(service=="jobs.list")return@carrierEgress JSONArray(mine.map(::publicRow))
  if(service in setOf("jobs.status","jobs.cancel")){
   val r=mine.singleOrNull{it.getString("id")==args.getString("id")}?:error("Task belongs to another app or is unavailable")
   if(service=="jobs.status")return@carrierEgress publicRow(r)
   cancel(r.getString("id"));scheduler(c).cancel(BASE+r.getInt("slot"));cleanup(c,r);rows.remove(r.getString("id"));save(c,rows);return@carrierEgress JSONObject().put("cancelled",true)
  }
  if(service=="downloads.start"){val mime=args.getString("mime");require(mime.length<=100&&mime.matches(Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+"))){"Choose a supported MIME type without parameters"}}
  val previous=mine.firstOrNull{it.getString("key")==args.getString("key")};check(previous!=null||mine.size<8){"Keep up to eight tasks per app; replace an existing key"};check(previous!=null||rows.length()<32){"The task queue is full"}
  val old=rows.toString();val used=rows.keys().asSequence().map{rows.getJSONObject(it).getInt("slot")}.toSet();val slot=previous?.getInt("slot")?:(0 until 32).first{it !in used}
  val id=previous?.getString("id")?:UUID.randomUUID().toString();val r=JSONObject().put("id",id).put("slot",slot).put("token",UUID.randomUUID().toString()).put("owner",JSONObject(owner.toString())).put("args",JSONObject(args.toString())).put("key",args.getString("key")).put("kind",if(service=="downloads.start")"download"else"http").put("state","queued").put("bytes",0).put("attempts",0).put("due",System.currentTimeMillis()+args.optLong("delay_seconds")*1000)
  authorize();rows.put(id,r);save(c,rows)
  try{enqueue(c,r)}catch(e:Exception){save(c,JSONObject(old));throw e}
  if(previous!=null){cancel(id);cleanup(c,previous)};publicRow(r)
 }
 fun clearApp(c:Context,id:String)=AppSdk.carrierEgress{val rows=load(c);for(key in rows.keys().asSequence().toList()){val r=rows.getJSONObject(key);if(r.getJSONObject("owner").getString("id")==id){cancel(key);scheduler(c).cancel(BASE+r.getInt("slot"));cleanup(c,r);rows.remove(key)}};save(c,rows)}
 fun accountDisconnected(c:Context,id:String,account:String)=AppSdk.carrierEgress{
  val rows=load(c);for(key in rows.keys()){val r=rows.getJSONObject(key);val a=r.getJSONObject("args");if(r.getJSONObject("owner").getString("id")==id&&(a.optString("account")==account||a.optJSONObject("request")?.optString("account")==account)){cancel(key);scheduler(c).cancel(BASE+r.getInt("slot"));cleanup(c,r);r.put("state","cancelled").put("token",UUID.randomUUID().toString());rows.put(key,r)}};save(c,rows)
 }
 fun recover(c:Context)=AppSdk.carrierEgress{val rows=load(c);for(id in rows.keys()) {val r=rows.getJSONObject(id);if(r.getString("state")in setOf("queued","running","retry")){val pending=scheduler(c).getPendingJob(BASE+r.getInt("slot"));if(pending?.extras?.getString("token")!=r.getString("token"))enqueue(c,r)}}}
 private fun hex(bytes:ByteArray,n:Int)=buildString(n*2){val digits="0123456789abcdef";for(i in 0 until n){val b=bytes[i].toInt()and 255;append(digits[b ushr 4]);append(digits[b and 15])}}
 private fun append(c:Context,r:JSONObject,file:String,offset:Long,b:ByteArray,n:Int)=blob(c,r,"blobs.append",JSONObject().put("id",file).put("offset",offset).put("hex",hex(b,n)))
 internal fun contentRange(value:String?,start:Long,length:Long,max:Long):Long{
  val m=Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(value?:"")?:error("Invalid resumed response")
  val(a,b,total)=m.groupValues.drop(1).map{it.toLong()};check(a==start&&b>=a&&total>b&&total<=max&&(length<0||length==b-a+1));return total
 }
 internal fun run(c:Context,id:String,token:String,stopped:()->Boolean,connect:(Request)->Call={client.newCall(it)},fetch:(suspend(JSONObject,()->Unit)->JSONObject)?=null):Boolean{
  var r=runCatching{row(c,id,token)}.getOrNull()?:return false
  try{
   val spec=r.getJSONObject("args");val http=if(r.getString("kind")=="http")spec.getJSONObject("request")else JSONObject().put("url",spec.getString("url")).also{if(spec.has("account"))it.put("account",spec.getString("account"))}
   val policy=authorize(c,r,http)
   fun verify(){check(!stopped());row(c,id,token);check(authorize(c,r,http).toString()==policy.toString())}
   var file=r.optString("staging").ifEmpty{stagingId(r)};var info:JSONObject?=runCatching{blob(c,r,"blobs.info",JSONObject().put("id",file))}.getOrNull()
   if(info?.optBoolean("ready")==true){val completed=info;update(c,r){it.put("state","completed").put("file",completed).put("bytes",completed.getLong("size"))};return false}
   if(!r.has("staging")&&info!=null){cleanup(c,r);info=null;r=update(c,r){it.put("etag","").put("bytes",0)}};if(info==null)file=""
   check(r.getInt("attempts")<3){"Task retry limit reached"};r=update(c,r){it.put("state","running").put("attempts",it.getInt("attempts")+1)}
   if(r.getString("kind")=="http"){
    val response=runBlocking{if(fetch!=null)fetch(policy,::verify)else SdkHttpService.fetchWithAccount("job-$id",{verify();policy},context=c,accountAuthorize={verify();policy})}.toString().toByteArray()
    if(file.isNotEmpty())cleanup(c,r)
    val started=begin(c,r,JSONObject().put("name",r.getString("key")+".json").put("mime","application/json"));r=started.first;file=started.second
    for(offset in response.indices step 65536){verify();val chunk=response.copyOfRange(offset,minOf(offset+65536,response.size));append(c,r,file,offset.toLong(),chunk,chunk.size)}
   }else{
    var offset=info?.optLong("size")?:0L;val max=spec.getLong("max_bytes");var etag=r.optString("etag")
    if(offset>0&&(etag.isEmpty()||etag.startsWith("W/")||!etag.startsWith('"'))){cleanup(c,r);file="";offset=0;etag=""}
    if(file.isEmpty()){val started=begin(c,r,JSONObject().put("name",spec.getString("name")).put("mime",spec.getString("mime")).put("cache",spec.optBoolean("cache")));r=started.first;file=started.second}
    var request=SdkHttpPolicy.request(policy)
    if(http.has("account")){val header=runBlocking{SdkAccounts.authenticated(c,"download-$id",policy,http.getString("url"),::verify)}.build().header("Authorization")!!;request=request.newBuilder().header("Authorization",header).build()}
    val builder=request.newBuilder().header("Accept-Encoding","identity");if(offset>0)builder.header("Range","bytes=$offset-").header("If-Range",etag)
    val call=connect(builder.build());calls[id]=call
    try{verify();call.execute().use{res->
     check(res.code==200||res.code==206){"Download response was not successful"};check(res.header("Content-Encoding","identity")=="identity")
     val body=res.body?:error("Empty download response");val length=body.contentLength();check(length<=max)
     var expected=length
     if(res.code==206){check(offset>0&&res.header("ETag")==etag);expected=contentRange(res.header("Content-Range"),offset,length,max)}
     else if(offset>0){cleanup(c,r);val started=begin(c,r,JSONObject().put("name",spec.getString("name")).put("mime",spec.getString("mime")).put("cache",spec.optBoolean("cache")));r=started.first;file=started.second;offset=0}
     r=update(c,r){it.put("staging",file).put("etag",res.header("ETag")?.takeIf{it.length<=512}?:"").put("total",expected)}
     body.byteStream().use{input->val buffer=ByteArray(65536);var empty=0
      while(true){verify();if(http.has("account"))SdkAccounts.validateUse(c,policy,request.header("Authorization"),verify=::verify);val n=input.read(buffer);if(n<0)break;if(n==0){check(++empty<=3);continue};empty=0;check(offset+n<=max);append(c,r,file,offset,buffer,n);offset+=n;r=update(c,r){it.put("bytes",offset)}}
     };if(expected>=0)check(offset==expected){"Download ended before its declared size"}
    }}finally{calls.remove(id,call);call.cancel()}
   }
   verify();val args=JSONObject().put("id",file);if(spec.has("sha256"))args.put("sha256",spec.getString("sha256").lowercase(java.util.Locale.ROOT));val finished=blob(c,r,"blobs.finish",args);update(c,r){it.put("state","completed").put("file",finished).put("bytes",finished.getLong("size")).remove("error")};return false
  }catch(e:Exception){
   val live=runCatching{row(c,id,token)}.getOrNull()?:return false
   val retry=live.getInt("attempts")<3&&runCatching{authorize(c,live,JSONObject());true}.getOrDefault(false)
   runCatching{update(c,live){it.put("state",if(retry)"retry"else"failed").put("error",if(retry)"Transfer interrupted; Android will retry within the attempt limit"else"Transfer failed or permission changed. Check the source, integrity and storage limits.")}}
   if(!retry)cleanup(c,live);return retry
  }
 }
}

class SdkTransferJobService:JobService(){
 private val running=ConcurrentHashMap<Int,AtomicBoolean>()
 override fun onStartJob(params:JobParameters):Boolean{val stop=AtomicBoolean(false);running.put(params.jobId,stop)?.set(true)
  workers.execute{var retry=false;try{retry=SdkJobs.run(applicationContext,params.extras.getString("id")?:"",params.extras.getString("token")?:"",stop::get)}finally{android.os.Handler(mainLooper).post{if(running.remove(params.jobId,stop)&&!stop.get())jobFinished(params,retry)}}};return true
 }
 override fun onStopJob(params:JobParameters):Boolean{running.remove(params.jobId)?.set(true);SdkJobs.cancel(params.extras.getString("id")?:"");return true}
 override fun onDestroy(){running.values.forEach{it.set(true)};running.clear();super.onDestroy()}
 private companion object{val workers=Executors.newFixedThreadPool(2)}
}
