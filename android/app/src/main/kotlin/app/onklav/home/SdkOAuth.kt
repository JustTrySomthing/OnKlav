package app.onklav.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import java.security.SecureRandom

/** Verifiers and authorization codes stay in host memory, never guest state or Intents. */
internal object SdkOAuth {
 internal class Flow(val owner:String,val state:String,val verifier:String,val redirect:String,val expires:Long){val code=CompletableDeferred<String>()}
 private val flows=linkedMapOf<String,Flow>()
 private fun random()=ByteArray(32).also{SecureRandom().nextBytes(it)}.let{Base64.encodeToString(it,Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)}
 internal fun challenge(verifier:String)=Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
 @Synchronized internal fun begin(owner:String,redirect:String,now:Long=SystemClock.elapsedRealtime()):Flow {
  flows.values.filter{it.expires<=now}.toList().forEach{finish(it)}
  check(flows.size<4){"Too many account connections are open"}
  return Flow(owner,random(),random(),redirect,now+120_000).also{flows[it.state]=it}
 }
 @Synchronized internal fun callback(raw:String,now:Long=SystemClock.elapsedRealtime()):Boolean {
  if(raw.length>8192)return false
  return try{
   val uri=Uri.parse(raw);val states=uri.getQueryParameters("state");if(states.size!=1)return false
   val flow=flows[states.single()]?:return false;val expected=Uri.parse(flow.redirect)
   if(now>=flow.expires){finish(flow);return false}
   if(uri.scheme!=expected.scheme||uri.encodedAuthority!=expected.encodedAuthority||uri.encodedPath!=expected.encodedPath||uri.fragment!=null)return false
   val codes=uri.getQueryParameters("code");val errors=uri.getQueryParameters("error")
   if(codes.size+errors.size!=1)return false
   if(codes.isNotEmpty()&&(codes.single().length !in 1..2048||codes.single().any{it.code !in 33..126}))return false
   flows.remove(flow.state)
   if(errors.isNotEmpty())flow.code.completeExceptionally(IllegalStateException("Account connection was declined"))else flow.code.complete(codes.single())
   true
  }catch(e:Exception){false}
 }
 @Synchronized internal fun finish(flow:Flow){flows.remove(flow.state,flow);flow.code.cancel()}
 @Synchronized fun cancel(owner:String){flows.values.filter{it.owner==owner}.toList().forEach{finish(it)}}
 @Synchronized fun cancelAll(){flows.values.toList().forEach{finish(it)}}
 internal suspend fun signIn(context:Context,owner:String,provider:org.json.JSONObject,authorize:()->Unit,
  open:(String)->Unit={url->context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))},timeoutMillis:Long=120_000):Pair<Flow,String> {
  val flow=begin(owner,"${context.packageName}.oauth://oauth/callback")
  try{
   val url=Uri.parse(provider.getString("authorization_endpoint")).buildUpon()
    .appendQueryParameter("response_type","code").appendQueryParameter("client_id",provider.getString("client_id"))
    .appendQueryParameter("redirect_uri",flow.redirect).appendQueryParameter("scope",provider.getJSONArray("scopes").let{a->List(a.length()){a.getString(it)}.joinToString(" ")})
    .appendQueryParameter("state",flow.state).appendQueryParameter("code_challenge",challenge(flow.verifier)).appendQueryParameter("code_challenge_method","S256").build().toString()
   authorize();open(url)
   val code=withTimeout(timeoutMillis.coerceIn(1,120_000)){flow.code.await()};authorize()
   return flow to code
  }catch(e:TimeoutCancellationException){throw IllegalStateException("Account connection timed out. Try again.")}
  finally{finish(flow)}
 }
}

/** Only an exact outstanding state can complete sign-in; no credentials are persisted here. */
class SdkOAuthCallbackActivity:Activity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);try{intent?.dataString?.let{SdkOAuth.callback(it)}}finally{finish()}}
}
