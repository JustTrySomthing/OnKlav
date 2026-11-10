package app.onklav.home

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkAccountsTest {
 @get:Rule val emptyKeys=EmptyKeyStoreRule()
 private class Keys:SecretKeys {
  val values=mutableMapOf<String,SecretKey>()
  override fun load(alias:String,create:Boolean):SecretKey=values[alias]?:run{check(create);KeyGenerator.getInstance("AES").apply{init(256)}.generateKey().also{values[alias]=it}}
  override fun deletePrefix(prefix:String){values.keys.filter{it.startsWith(prefix)}.forEach{values.remove(it)}}
 }
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private fun request(service:String="accounts.connect"):JSONObject {
  val provider=JSONObject("""{"id":"example","label":"Example","authorization_endpoint":"https://api.example.com/auth","token_endpoint":"https://api.example.com/token","client_id":"public-client","scopes":["profile"],"api_origins":["https://api.example.com"]}""")
  return JSONObject().put("service",service).put("owner",JSONObject().put("id","org.example.account").put("publisher","a".repeat(64))).put("providers",JSONArray().put(provider)).put("args",if(service=="accounts.list")JSONObject()else JSONObject().put("provider","example"))
 }
 private fun tokens()=JSONObject().put("token_type","Bearer").put("access_token","test-access-secret").put("refresh_token","test-refresh-secret").put("expires_in",3600).put("scope","profile")
 private fun callback(url:String){val uri=Uri.parse(url);assertFalse(url.contains("code_verifier"));assertEquals("S256",uri.getQueryParameter("code_challenge_method"));assertTrue(SdkOAuth.callback(uri.getQueryParameter("redirect_uri")+"?state="+uri.getQueryParameter("state")+"&code=test-code"))}
 @After fun stop(){SdkAccounts.cancelAll()}
 @Test fun pkceVectorAndExactCallbacksRejectReplayWrongRoutesAndExpiry(){
  assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",SdkOAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
  val f=SdkOAuth.begin("one","org.example.oauth://oauth/callback",1000);assertEquals(43,f.verifier.length);assertNotEquals(f.state,f.verifier)
  for(bad in listOf("other://oauth/callback?state=${f.state}&code=x","org.example.oauth://oauth/wrong?state=${f.state}&code=x","${f.redirect}?state=${f.state}&state=other&code=x","${f.redirect}?state=${f.state}&code=x&error=denied","${f.redirect}?state=${f.state}&code=x#fragment"))assertFalse(SdkOAuth.callback(bad,1100))
  assertTrue(SdkOAuth.callback("${f.redirect}?state=${f.state}&code=x",1100));assertFalse(SdkOAuth.callback("${f.redirect}?state=${f.state}&code=x",1101))
  val old=SdkOAuth.begin("two",f.redirect,1000);assertFalse(SdkOAuth.callback("${old.redirect}?state=${old.state}&code=x",121000));assertTrue(old.code.isCancelled)
 }
 @Test fun browserTimeoutCancellationAndDeniedFlowLeaveNoReusableState()=runBlocking<Unit>{
  val p=request().getJSONArray("providers").getJSONObject(0);var url=""
  try{SdkOAuth.signIn(c,"timeout",p,{},open={url=it},timeoutMillis=1);fail("Expected timeout")}catch(e:IllegalStateException){assertTrue(e.message!!.contains("timed out"))}
  val u=Uri.parse(url);assertFalse(SdkOAuth.callback(u.getQueryParameter("redirect_uri")+"?state="+u.getQueryParameter("state")+"&code=late"))
  val f=SdkOAuth.begin("cancel","org.example.oauth://oauth/callback");SdkOAuth.cancel("cancel");assertTrue(f.code.isCancelled)
  val denied=SdkOAuth.begin("denied",f.redirect);assertTrue(SdkOAuth.callback("${denied.redirect}?state=${denied.state}&error=access_denied"));assertTrue(denied.code.isCancelled)
 }
 @Test fun encryptedVaultIsPublisherIsolatedPolicyBoundAndDoesNotExportTokens(){
  val dir=kotlin.io.path.createTempDirectory("account-vault").toFile();try{
   val keys=Keys();val vault=SdkAccountVault(SdkSecretStore(dir,keys,"accounts"));val r=request();val p=r.getJSONArray("providers").getJSONObject(0)
   val row=vault.connect(r,p,SdkAccounts.tokens(tokens(),p)){};assertEquals(3,row.length());assertFalse(row.toString().contains("secret"));assertFalse(String(dir.walk().single{it.isFile}.readBytes(),Charsets.ISO_8859_1).contains("test-access-secret"))
   val other=request("accounts.list");other.getJSONObject("owner").put("publisher","b".repeat(64));assertEquals(0,vault.list(other){}.length())
   assertEquals(1,vault.list(request("accounts.list")){}.length());val changed=request("accounts.list");changed.getJSONArray("providers").getJSONObject(0).put("client_id","changed");assertEquals(0,vault.list(changed){}.length());assertEquals(0,vault.list(request("accounts.list")){}.length())
   for(i in 0 until 8)vault.connect(r,p,SdkAccounts.tokens(tokens(),p)){};assertEquals(8,vault.list(request("accounts.list")){}.length());assertThrows(IllegalStateException::class.java){vault.connect(r,p,SdkAccounts.tokens(tokens(),p)){}}
   assertTrue(keys.values.keys.all{it.startsWith("onklav.accounts.")});vault.deleteApp("org.example.account");assertTrue(keys.values.isEmpty())
  }finally{dir.deleteRecursively()}
 }
 @Test fun browserExchangeSavesOpaqueHandleAndRechecksAuthorization()=runBlocking<Unit>{
  val dir=kotlin.io.path.createTempDirectory("account-flow").toFile();try{
   val vault=SdkAccountVault(SdkSecretStore(dir,Keys(),"accounts"));val r=request();var checks=0;var calls=0
   val row=SdkAccounts.execute(c,"flow",{checks++;r},vault,browser=::callback,transport={_,_,fields,verify->verify();calls++;assertEquals("test-code",fields["code"]);assertEquals(43,fields.getValue("code_verifier").length);tokens()})as JSONObject
   assertEquals(1,calls);assertTrue(checks>=5);assertEquals(3,row.length());assertFalse(row.toString().contains("secret"));assertEquals(1,vault.list(request("accounts.list")){}.length())
   var live=true
   try{SdkAccounts.execute(c,"revoked",{check(live);r},vault,browser=::callback,transport={_,_,_,_->live=false;tokens()});fail("Expected denial")}catch(e:IllegalStateException){}
   assertEquals(1,vault.list(request("accounts.list")){}.length())
  }finally{dir.deleteRecursively()}
 }
 @Test fun removingAppClearsAccountsEvenWhenDocumentsAreKept(){
  var cleared:String?=null
  SdkNativeData.removed(c,"org.example.accountremove",false,clearAccounts={_,id->cleared=id},clearSecrets={_,_->fail("Document retention does not require guest-vault deletion")})
  assertEquals("org.example.accountremove",cleared)
 }
 @Test fun revokingNetworkAccessDeletesStoredAccountsAndRejectsThePendingToken()=runBlocking<Unit>{
  File(System.getProperty("onklav.project.root"),"docs/fixtures/accounts-wip.onklav-a").inputStream().use{AppSdk.stage(c,it)}
  val review=AppSdk.inspect(c);AppSdk.install(c,review,setOf("accounts.connect","network.http"))
  val opened=AppSdk.call(c,JSONObject().put("action","launch").put("id","org.example.accountfixture"))as JSONObject;val session=opened.getString("session")
  val event=AppSdk.call(c,JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action","connect")))as JSONObject
  val pending=event.getJSONObject("pending");val auth=JSONObject().put("action","authorize").put("session",session).put("token",pending.getString("token"))
  val request=AppSdk.sync(c,auth)as JSONObject;val provider=request.getJSONArray("providers").getJSONObject(0)
  val vault=SdkAccountVault(SdkSecretStore(File(c.noBackupFilesDir,"sdk-accounts"),Keys(),"accounts"))
  vault.connect(request,provider,SdkAccounts.tokens(tokens(),provider)){};assertEquals(1,vault.list(request){}.length())
  AppSdk.sync(c,JSONObject().put("action","permissions").put("id","org.example.accountfixture").put("grants",JSONArray().put("accounts.connect")))
  assertEquals(0,vault.list(request){}.length());assertThrows(IllegalStateException::class.java){AppSdk.sync(c,auth)}
 }
 @Test fun refreshRotationAndFailedRevocationKeepLocalSignOutFinal()=runBlocking<Unit>{
  val dir=kotlin.io.path.createTempDirectory("refresh").toFile();try{
   val vault=SdkAccountVault(SdkSecretStore(dir,Keys(),"accounts"));val r=request();r.getJSONObject("owner").put("id","org.example.account.refresh");r.getJSONArray("providers").getJSONObject(0).put("revocation_endpoint","https://api.example.com/revoke")
   val connected=SdkAccounts.execute(c,"refresh-connect",{r},vault,browser=::callback,transport={_,_,_,_->tokens()})as JSONObject;val h=connected.getString("account")
   val refresh=JSONObject(r.toString()).put("service","accounts.refresh").put("args",JSONObject().put("account",h));val old=vault.get(refresh){}
   val refreshed=SdkAccounts.execute(c,"refresh-token",{refresh},vault,transport={_,_,fields,verify->verify();assertEquals("test-refresh-secret",fields["refresh_token"]);tokens().put("access_token","rotated-secret").apply{remove("refresh_token")}})as JSONObject
   assertEquals(h,refreshed.getString("account"));assertFalse(refreshed.toString().contains("rotated-secret"));assertEquals("test-refresh-secret",vault.get(refresh){}.getJSONObject("tokens").getString("refresh_token"))
   val disconnect=JSONObject(refresh.toString()).put("service","accounts.disconnect")
   val result=SdkAccounts.execute(c,"disconnect",{disconnect},vault,transport={_,_,fields,_->assertTrue(fields.containsKey("token"));throw java.io.IOException("Provider offline")})as JSONObject
   assertTrue(result.getBoolean("disconnected"));assertFalse(result.getBoolean("revocation_submitted"));assertThrows(IllegalStateException::class.java){vault.get(refresh){}};assertThrows(IllegalStateException::class.java){vault.refresh(refresh,old,SdkAccounts.tokens(tokens(),r.getJSONArray("providers").getJSONObject(0))){}}
  }finally{dir.deleteRecursively()}
 }
 @Test fun authenticatedRequestsUseOnlyProviderOriginsAndStopAfterSignOut()=runBlocking<Unit>{
  val dir=kotlin.io.path.createTempDirectory("auth-http").toFile();try{
   val vault=SdkAccountVault(SdkSecretStore(dir,Keys(),"accounts"));val r=request();r.getJSONObject("owner").put("id","org.example.account.http");val p=r.getJSONArray("providers").getJSONObject(0);val account=vault.connect(r,p,SdkAccounts.tokens(tokens(),p)){}.getString("account")
   val packet=JSONObject(r.toString()).put("service","http.request").put("origins",JSONArray().put("https://api.example.com").put("https://public.example.com")).put("args",JSONObject().put("url","https://api.example.com/items").put("account",account))
   var sends=0
   val value=SdkHttpService.fetchWithAccount("auth-ok",{packet},context=c,accountAuthorize={packet},accountVault=vault,connect={req->assertEquals("Bearer test-access-secret",req.header("Authorization"));sends++;SdkServiceCall(req){sdkResponse(req,"ok".toByteArray())}})
   assertEquals("ok",value.getString("text"));assertEquals(1,sends);assertFalse(value.toString().contains("test-access-secret"))
   val bad=JSONObject(packet.toString());bad.getJSONObject("args").put("url","https://public.example.com/items")
   assertTrue(runCatching{SdkHttpService.fetchWithAccount("auth-wrong",{bad},context=c,accountAuthorize={bad},accountVault=vault,connect={error("Wrong account origin reached transport")})}.isFailure)
   val disconnect=JSONObject(r.toString()).put("args",JSONObject().put("account",account))
   assertTrue(runCatching{SdkHttpService.fetchWithAccount("auth-signout",{packet},context=c,accountAuthorize={packet},accountVault=vault,connect={req->SdkServiceCall(req){vault.disconnect(disconnect){};sdkResponse(req,"must not escape".toByteArray())}})}.isFailure)
  }finally{dir.deleteRecursively()}
 }
 @Test fun changedProviderNeverReceivesAnOldTokenDuringSignOut()=runBlocking<Unit>{
  val dir=kotlin.io.path.createTempDirectory("changed-revoke").toFile();try{
   val vault=SdkAccountVault(SdkSecretStore(dir,Keys(),"accounts"));val r=request();r.getJSONObject("owner").put("id","org.example.account.changed");val p=r.getJSONArray("providers").getJSONObject(0);val account=vault.connect(r,p,SdkAccounts.tokens(tokens(),p)){}.getString("account")
   val changed=JSONObject(r.toString()).put("service","accounts.disconnect").put("args",JSONObject().put("account",account));changed.getJSONArray("providers").getJSONObject(0).put("revocation_endpoint","https://api.example.com/new-owner-endpoint")
   var called=false;val result=SdkAccounts.execute(c,"changed-revoke",{changed},vault,transport={_,_,_,_->called=true;JSONObject()})as JSONObject
   assertTrue(result.getBoolean("disconnected"));assertFalse(result.getBoolean("revocation_submitted"));assertFalse(called);assertEquals(0,vault.list(r){}.length())
  }finally{dir.deleteRecursively()}
 }
 @Test fun signedAppRoutesAuthenticatedHttpAndDisconnectsThroughJni()=runBlocking<Unit>{
  val dir=kotlin.io.path.createTempDirectory("account-route").toFile();try{
   File(System.getProperty("onklav.project.root"),"docs/fixtures/accounts-wip.onklav-a").inputStream().use{AppSdk.stage(c,it)};val review=AppSdk.inspect(c);AppSdk.install(c,review,setOf("accounts.connect","network.http"))
   val opened=AppSdk.call(c,JSONObject().put("action","launch").put("id","org.example.accountfixture"))as JSONObject;val session=opened.getString("session");val vault=SdkAccountVault(SdkSecretStore(dir,Keys(),"accounts"))
   suspend fun event(action:String)=(AppSdk.call(c,JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action",action)))as JSONObject).getJSONObject("pending")
   fun authorize(p:JSONObject)=AppSdk.sync(c,JSONObject().put("action","authorize").put("session",session).put("token",p.getString("token")))as JSONObject
   suspend fun reply(p:JSONObject,value:Any)=AppSdk.call(c,JSONObject().put("action","reply").put("session",session).put("token",p.getString("token")).put("reply",JSONObject().put("ok",true).put("value",value)))as JSONObject
   val connect=event("connect");val account=SdkAccounts.execute(c,connect.getString("token"),{authorize(connect)},vault,browser=::callback,transport={_,_,_,verify->verify();tokens()})as JSONObject;reply(connect,account)
   val request=event("request");assertEquals("http.request",request.getString("service"));val response=SdkHttpService.fetchWithAccount(request.getString("token"),{authorize(request)},context=c,accountAuthorize={authorize(request)},accountVault=vault,connect={req->assertEquals("Bearer test-access-secret",req.header("Authorization"));SdkServiceCall(req){sdkResponse(req,"account data".toByteArray())}})
   val shown=reply(request,response);assertFalse(shown.toString().contains("test-access-secret"));assertEquals("account data",shown.getJSONObject("frame").getJSONObject("state").getJSONObject("last").getJSONObject("value").getString("text"))
   val disconnect=event("disconnect");val result=SdkAccounts.execute(c,disconnect.getString("token"),{authorize(disconnect)},vault)as JSONObject;assertTrue(result.getBoolean("disconnected"));val done=reply(disconnect,result);assertTrue(done.getJSONObject("frame").getJSONObject("state").isNull("account"))
  }finally{dir.deleteRecursively()}
 }
 @Test fun invalidTokenTypeScopesAndExpiryAreRejected(){val p=request().getJSONArray("providers").getJSONObject(0)
  for(raw in listOf(tokens().put("token_type","MAC"),tokens().put("scope","profile admin"),tokens().put("expires_in",0),tokens().put("access_token","line\nbreak")))assertThrows(IllegalArgumentException::class.java){SdkAccounts.tokens(raw,p)}
 }
 @Test fun signedGuestConnectsThroughJniAndReceivesOnlyPublicAccountFields()=runBlocking<Unit>{
  val dir=kotlin.io.path.createTempDirectory("account-jni").toFile();try{
   File(System.getProperty("onklav.project.root"),"docs/fixtures/accounts-wip.onklav-a").inputStream().use{AppSdk.stage(c,it)};val review=AppSdk.inspect(c);AppSdk.install(c,review,setOf("accounts.connect","network.http"))
   val opened=AppSdk.call(c,JSONObject().put("action","launch").put("id","org.example.accountfixture"))as JSONObject
   val session=opened.getString("session");val event=AppSdk.call(c,JSONObject().put("action","event").put("session",session).put("event",JSONObject().put("action","connect")))as JSONObject
   val pending=event.getJSONObject("pending");fun authorize()=AppSdk.sync(c,JSONObject().put("action","authorize").put("session",session).put("token",pending.getString("token")))as JSONObject
   val vault=SdkAccountVault(SdkSecretStore(dir,Keys(),"accounts"));val row=SdkAccounts.execute(c,pending.getString("token"),::authorize,vault,browser=::callback,transport={_,_,_,verify->verify();tokens()})as JSONObject
   val replied=AppSdk.call(c,JSONObject().put("action","reply").put("session",session).put("token",pending.getString("token")).put("reply",JSONObject().put("ok",true).put("value",row)))as JSONObject
   assertFalse(replied.toString().contains("test-access-secret"));assertEquals(row.getString("account"),replied.getJSONObject("frame").getJSONObject("state").getJSONObject("account").getString("account"))
  }finally{dir.deleteRecursively()}
 }
}
