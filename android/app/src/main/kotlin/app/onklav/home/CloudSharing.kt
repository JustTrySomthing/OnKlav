package app.onklav.home

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException

/** Provider sharing APIs are separate from the account's confined file transport. */
internal class CloudSharing(private val account:CloudAccount,private val exchange:(suspend(Request)->Response)?=null){
 private val active=AtomicReference<Call?>()
 companion object {
  private val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).build()
  fun provider(account:CloudAccount):String? {val u=CloudPaths.base(account.endpoint);if(u.port!=443)return null;return when(u.host){"webdav.pcloud.com","ewebdav.pcloud.com"->"pCloud";"app.koofr.net"->if(u.pathSegments.take(2)==listOf("dav","Koofr"))"Koofr"else null;else->null}}
  fun checkedLink(raw:String,provider:String):String {
   require(raw.length in 1..4096&&raw.none{it.isWhitespace()||it.isISOControl()}){"The provider returned an invalid sharing link."}
   val u=raw.toHttpUrl();val host=if(provider=="pCloud")u.host=="my.pcloud.com"||u.host=="e.pcloud.com"||u.host=="pcloud.link"||u.host.endsWith(".pcloud.link")else u.host=="app.koofr.net"&&u.encodedPath.startsWith("/links/")
   require(host&&u.scheme=="https"&&u.port==443&&u.username.isEmpty()&&u.password.isEmpty()&&u.queryParameterNames.none{it.lowercase() in setOf("auth","password","username","access_token")}){"The provider returned an invalid sharing link."}
   return u.toString()
  }
 }
 fun cancel(){active.getAndSet(null)?.cancel()}
 private suspend fun json(request:Request):JSONObject {
  val response:Response=exchange?.invoke(request)?:suspendCancellableCoroutine{cont->val call=http.newCall(request);active.set(call);cont.invokeOnCancellation{call.cancel()};call.enqueue(object:Callback{
   override fun onFailure(call:Call,e:IOException){if(cont.isActive)cont.resumeWithException(IOException("Could not reach cloud storage. Check your cloud account before retrying sharing."))}
   override fun onResponse(call:Call,response:Response){cont.resume(response){_,value,_->value.close()}}
  })}
  return response.use{r->check(r.isSuccessful){CloudDav.status(r.code)};val data=r.body?.byteStream()?.use{it.readBytesBounded(1024*1024)}?:error("The provider returned no sharing link.");JSONObject(String(data,Charsets.UTF_8))}
 }
 suspend fun create(entry:CloudEntry):String=withContext(Dispatchers.IO){
  val root=CloudPaths.base(account.endpoint);require(!entry.folder&&CloudPaths.safe(root,entry.url)){"Choose a file from this cloud account."}
  val provider=provider(account)?:error("Sharing links are available for pCloud and Koofr accounts.")
  val result=if(provider=="pCloud"){
   val host=if(root.host=="ewebdav.pcloud.com")"eapi.pcloud.com"else"api.pcloud.com"
   val path="/"+entry.url.pathSegments.joinToString("/")
   val body=FormBody.Builder().add("username",account.user).add("password",account.password).add("path",path).build()
   val j=json(Request.Builder().url("https://$host/getfilepublink").post(body).build())
   check(j.optInt("result",-1)==0){"pCloud could not create a sharing link. Check sign-in approval and sharing limits in your pCloud account before retrying."}
   checkedLink(j.getString("link"),provider)
  }else{
   val base="https://app.koofr.net";val auth=Credentials.basic(account.user,account.password,Charsets.UTF_8)
   fun request(url:HttpUrl)=Request.Builder().url(url).header("Authorization",auth).header("Accept","application/json")
   val mounts=json(request("$base/api/v2/mounts".toHttpUrl()).build()).getJSONArray("mounts")
   require(mounts.length()<=1000)
   val primary=(0 until mounts.length()).map{mounts.getJSONObject(it)}.singleOrNull{it.optBoolean("isPrimary")}?:error("Could not identify your Koofr storage. Check your account in Koofr.")
   val mount=primary.getString("id");require(mount.isNotBlank()&&mount.length<=100&&mount.all{it.isLetterOrDigit()||it=='-'})
   val path="/"+entry.url.pathSegments.drop(2).joinToString("/")
   val url="$base/api/v2/mounts/".toHttpUrl().newBuilder().addPathSegment(mount).addPathSegment("links").build()
   val j=json(request(url).post(JSONObject().put("path",path).toString().toRequestBody("application/json".toMediaType())).build())
   val link=checkedLink(j.getString("url"),provider)
   if(j.optBoolean("hasPassword")){
    val password=j.optString("password");check(password.isNotBlank()&&password.length<=256&&password.none{it.isISOControl()}){"This link is password protected. Open Koofr to view its sharing password before sending it."}
    "$link\nLink password: $password"
   }else link
  }
  currentCoroutineContext().ensureActive();result
 }
}
