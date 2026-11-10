package app.onklav.home

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Host-owned OAuth lifecycle and origin-bound credential use. */
internal object SdkAccounts {
 private val client=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS)
  .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
  .dns(object:okhttp3.Dns{override fun lookup(hostname:String)=SdkFeedService.publicAddresses(hostname)}).build()
 private val calls=ConcurrentHashMap<String,okhttp3.Call>()
 private val slots=Semaphore(4)
 private val refreshLock=Mutex()
 private val attempts=linkedMapOf<String,java.util.ArrayDeque<Long>>()
 private fun reserve(owner:String)=synchronized(attempts){
  val now=System.nanoTime();attempts.values.forEach{q->while(q.isNotEmpty()&&now-q.first>=60_000_000_000L)q.removeFirst()};attempts.entries.removeAll{it.value.isEmpty()}
  check(owner in attempts||attempts.size<128){"Account connection limit reached"};val q=attempts.getOrPut(owner){java.util.ArrayDeque()};check(q.size<10){"Too many account connections. Try again later."};q.addLast(now)
 }
 fun cancel(token:String){SdkOAuth.cancel(token);calls.remove(token)?.cancel()}
 fun cancelAll(){SdkOAuth.cancelAll();calls.keys.toList().forEach(::cancel)}
 internal fun tokens(raw:JSONObject,provider:JSONObject,now:Long=System.currentTimeMillis()):JSONObject {
  require(raw.optString("token_type").equals("Bearer",true)){"Unsupported account token type"}
  val out=JSONObject().put("token_type","Bearer")
  for(k in listOf("access_token","refresh_token")){
   if(k=="access_token"||raw.has(k)){val v=raw.get(k);require(v is String&&v.length in 1..8192&&v.all{it.code in 33..126}){"Invalid account token"};out.put(k,v)}
  }
  if(raw.has("expires_in")){val value=raw.get("expires_in");val n=when(value){is Number->value.toLong();is String->value.toLongOrNull();else->null};require(n!=null&&n in 1..2_592_000&&value.toString()==n.toString()){"Invalid account token expiry"};out.put("expires_at",Math.addExact(now,n*1000))}
  val allowed=provider.getJSONArray("scopes").let{a->List(a.length()){a.getString(it)}.toSet()}
  if(raw.has("scope")){val scope=raw.get("scope");require(scope is String&&scope.length<=8192&&scope.split(' ').filter{it.isNotEmpty()}.all{it in allowed}){"Account granted unexpected scopes"};out.put("scope",scope)}
  return out
 }
 private suspend fun exchange(token:String,provider:JSONObject,fields:Map<String,String>,authorize:()->Unit):JSONObject=withContext(Dispatchers.IO){slots.withPermit{
  val revoking=fields.containsKey("token");val endpoint=if(revoking)provider.getString("revocation_endpoint")else provider.getString("token_endpoint");SdkHttpPolicy.origin(endpoint)
  val form=FormBody.Builder().apply{fields.forEach{(k,v)->add(k,v)}}.build()
  val body=object:okhttp3.RequestBody(){override fun contentType()=form.contentType();override fun contentLength()=form.contentLength();override fun writeTo(sink:okio.BufferedSink)=form.writeTo(sink);override fun isOneShot()=true}
  val call=client.newCall(Request.Builder().url(endpoint).post(body).header("Accept","application/json").build());check(calls.putIfAbsent(token,call)==null)
  try{coroutineScope{
   val watcher=launch(start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{call.cancel()}}
   try{authorize();currentCoroutineContext().ensureActive();call.execute().use{response->
    check(response.code in 200..299){"Account token exchange was rejected"};if(revoking)return@use JSONObject();val b=response.body?:error("Missing account response");check(b.contentLength()<=32768)
    val out=java.io.ByteArrayOutputStream();b.byteStream().use{input->val buf=ByteArray(4096);var empty=0
     while(true){authorize();currentCoroutineContext().ensureActive();val n=input.read(buf);if(n<0)break;if(n==0){check(++empty<=3);continue};empty=0;check(out.size()+n<=32768);out.write(buf,0,n)}
    };authorize();JSONObject(out.toString("UTF-8"))
   }}finally{watcher.cancel()}
  }}catch(e:CancellationException){throw e}catch(e:Exception){throw IllegalStateException("Account token exchange failed. No new account was saved.")}
  finally{calls.remove(token,call);call.cancel()}
 }}
 suspend fun execute(context:Context,token:String,authorize:()->JSONObject,vault:SdkAccountVault=SdkAccountVault.forContext(context),
  browser:((String)->Unit)?=null,transport:suspend(String,JSONObject,Map<String,String>,()->Unit)->JSONObject={t,p,f,a->exchange(t,p,f,a)}):Any = try {
  val request=authorize();val verify={val live=authorize();check(live.toString()==request.toString()){"Account permission or provider changed"};Unit}
  if(request.getString("service")=="accounts.list")vault.list(request,verify) else {
  val service=request.getString("service")
  if(service=="accounts.disconnect"){
   // Commit local sign-out first. A failed revocation must never restore credentials.
   val old=AppSdk.carrierEgress{val value=vault.disconnect(request,verify);SdkHttpService.cancelAll();SdkJobs.accountDisconnected(context,request.getJSONObject("owner").getString("id"),request.getJSONObject("args").getString("account"));SdkJobs.cancelNetworkCalls();SdkMedia.changed(context,request.getJSONObject("owner").getString("id"));value};var submitted=false
   if(old!=null){val p=vault.revocationProvider(request,old)
    if(p?.has("revocation_endpoint")==true)try{reserve(request.getJSONObject("owner").toString());val t=old.getJSONObject("tokens");transport(token,p,mapOf("client_id" to p.getString("client_id"),"token" to t.optString("refresh_token",t.getString("access_token"))),verify);submitted=true}catch(e:CancellationException){throw e}catch(e:Exception){}
   }
   JSONObject().put("disconnected",old!=null).put("revocation_submitted",submitted)
  }else if(service=="accounts.refresh")refreshLock.withLock{refresh(token,request,vault,verify,transport)}else{
  check(service=="accounts.connect");val id=request.getJSONObject("args").getString("provider")
  val providers=request.getJSONArray("providers");val provider=(0 until providers.length()).map{providers.getJSONObject(it)}.single{it.getString("id")==id}
  val owner=request.getJSONObject("owner");reserve(owner.getString("id")+":"+owner.getString("publisher"))
  val(flow,code)=if(browser==null)SdkOAuth.signIn(context,token,provider,verify)else SdkOAuth.signIn(context,token,provider,verify,browser)
  val fields=mapOf("grant_type" to "authorization_code","client_id" to provider.getString("client_id"),"redirect_uri" to flow.redirect,"code" to code,"code_verifier" to flow.verifier)
  verify();val protectedTokens=tokens(transport(token,provider,fields,verify),provider);verify()
  vault.connect(request,provider,protectedTokens,verify)
  }
  }
 }catch(e:CancellationException){throw e}catch(e:Exception){throw IllegalStateException("Account connection could not complete. Existing accounts were kept. Try again or check this app’s permissions.")}
 private suspend fun refresh(token:String,request:JSONObject,vault:SdkAccountVault,verify:()->Unit,transport:suspend(String,JSONObject,Map<String,String>,()->Unit)->JSONObject):JSONObject{
  val old=vault.get(request,verify);val providers=request.getJSONArray("providers");val provider=(0 until providers.length()).map{providers.getJSONObject(it)}.single{it.getString("id")==old.getString("provider")}
  val previous=old.getJSONObject("tokens");val refresh=previous.optString("refresh_token");check(refresh.isNotEmpty()){"Connect this account again"};reserve(request.getJSONObject("owner").toString())
  val value=tokens(transport(token,provider,mapOf("grant_type" to "refresh_token","client_id" to provider.getString("client_id"),"refresh_token" to refresh),verify),provider)
  if(!value.has("refresh_token"))value.put("refresh_token",refresh)
  return vault.refresh(request,old,value,verify)
 }
 internal suspend fun authenticated(context:Context,token:String,request:JSONObject,url:String,verify:()->Unit,vault:SdkAccountVault=SdkAccountVault.forContext(context)):okhttp3.Request.Builder{
  val scoped=JSONObject(request.toString()).put("args",JSONObject().put("account",request.getJSONObject("args").getString("account")))
  return refreshLock.withLock{
   var row=vault.get(scoped,verify);val provider=request.getJSONArray("providers").let{a->(0 until a.length()).map{a.getJSONObject(it)}.single{it.getString("id")==row.getString("provider")}}
   val origins=provider.getJSONArray("api_origins");check((0 until origins.length()).any{origins.getString(it)==SdkHttpPolicy.origin(url)}){"Account cannot be used at this origin"}
   if(row.getJSONObject("tokens").has("expires_at")&&row.getJSONObject("tokens").getLong("expires_at")<=System.currentTimeMillis()+30_000){refresh(token,scoped,vault,verify){t,p,f,a->exchange(t,p,f,a)};row=vault.get(scoped,verify)}
   verify();okhttp3.Request.Builder().url(url).header("Authorization","Bearer "+row.getJSONObject("tokens").getString("access_token"))
  }
 }

 internal fun validateUse(context:Context,request:JSONObject,header:String?,verify:()->Unit,vault:SdkAccountVault=SdkAccountVault.forContext(context))=AppSdk.carrierEgress{
  val scoped=JSONObject(request.toString()).put("args",JSONObject().put("account",request.getJSONObject("args").getString("account")))
  val row=vault.get(scoped,verify);val provider=request.getJSONArray("providers").let{a->(0 until a.length()).map{a.getJSONObject(it)}.single{it.getString("id")==row.getString("provider")}}
  val origin=SdkHttpPolicy.origin(request.getJSONObject("args").getString("url"));val allowed=provider.getJSONArray("api_origins");check((0 until allowed.length()).any{allowed.getString(it)==origin})
  val tokens=row.getJSONObject("tokens");check(header=="Bearer "+tokens.getString("access_token")){"Account credentials changed"};check(!tokens.has("expires_at")||tokens.getLong("expires_at")>System.currentTimeMillis()){"Account credentials expired"};verify()
 }

}
