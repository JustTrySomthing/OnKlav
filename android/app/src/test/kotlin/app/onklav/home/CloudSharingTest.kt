package app.onklav.home

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class CloudSharingTest {
 private fun account(endpoint:String)=cloudAccount().copy(endpoint=endpoint)
 private fun entry(a:CloudAccount,path:String)=CloudEntry(CloudPaths.base(a.endpoint).resolve(path)!!,"File",false)
 @Test fun pcloudUsesRegionalApiAndPostBodyCredentialsWithDecodedFilePath()=runBlocking{
  for((endpoint,api) in listOf("https://webdav.pcloud.com/" to "api.pcloud.com","https://ewebdav.pcloud.com/" to "eapi.pcloud.com")){
   val a=account(endpoint)
   val link=CloudSharing(a){r->assertEquals(api,r.url.host);assertEquals("POST",r.method);assertNull(r.url.query);assertNull(r.header("Authorization"));val b=r.body as FormBody;val values=(0 until b.size).associate{b.name(it) to b.value(it)};assertEquals(a.password,values["password"]);assertEquals(a.user,values["username"]);assertEquals("/Folder/a b.txt",values["path"]);cloudResponse(r,200,"{\"result\":0,\"link\":\"https://u.pcloud.link/publink/show?code=fixture\"}")}.create(entry(a,"Folder/a%20b.txt"))
   assertTrue(link.startsWith("https://u.pcloud.link/"))
  }
 }
 @Test fun koofrUsesPrimaryStorageAndPreservesLinkPassword()=runBlocking{
  val a=account("https://app.koofr.net/dav/Koofr/");val calls=mutableListOf<Request>()
  val link=CloudSharing(a){r->calls+=r;assertEquals(Credentials.basic(a.user,a.password,Charsets.UTF_8),r.header("Authorization"));assertEquals("app.koofr.net",r.url.host)
   if(r.method=="GET")cloudResponse(r,200,"{\"mounts\":[{\"id\":\"wrong\"},{\"id\":\"primary\",\"isPrimary\":true}]}")
   else {assertEquals("/api/v2/mounts/primary/links",r.url.encodedPath);val b=okio.Buffer();r.body!!.writeTo(b);assertEquals("/Folder/a b.txt",JSONObject(b.readUtf8()).getString("path"));cloudResponse(r,201,"{\"url\":\"https://app.koofr.net/links/fixture\",\"hasPassword\":true,\"password\":\"link-only\"}")}
  }.create(entry(a,"Folder/a%20b.txt"))
  assertEquals(2,calls.size);assertEquals("https://app.koofr.net/links/fixture\nLink password: link-only",link)
 }
 @Test fun foreignAccountFolderAndEscapedEntryNeverSendCredentials()=runBlocking{
  val a=account("https://webdav.pcloud.com/private/");var calls=0;val client=CloudSharing(a){calls++;error("Must not call")}
  for(e in listOf(CloudEntry("https://evil.example/file".toHttpUrl(),"Bad",false),CloudEntry("https://webdav.pcloud.com/elsewhere/file".toHttpUrl(),"Bad",false),CloudEntry("https://webdav.pcloud.com/private/dir/".toHttpUrl(),"Folder",true))){assertTrue(runCatching{client.create(e)}.isFailure)}
  val other=account("https://other.example/");assertTrue(runCatching{CloudSharing(other){calls++;error("Must not call")}.create(entry(other,"file"))}.isFailure);assertEquals(0,calls)
 }
 @Test fun rejectsCredentialBearingInsecureAndForeignSharingLinks(){
  for(url in listOf("http://u.pcloud.link/file","https://evil.example/file","https://pcloud.link.evil.example/file","https://name:secret@u.pcloud.link/file","https://u.pcloud.link/file?auth=secret","https://u.pcloud.link:444/file","https://u.pcloud.link/file\n"))assertTrue(url,runCatching{CloudSharing.checkedLink(url,"pCloud")}.isFailure)
  assertTrue(runCatching{CloudSharing.checkedLink("https://app.koofr.net/api/v2/mounts","Koofr")}.isFailure)
 }
 @Test fun failedAndRedirectedCreationDoesNotRetryOrRevealResponse()=runBlocking{
  val a=account("https://webdav.pcloud.com/")
  for(code in listOf(302,403,500)){var calls=0;val failure=runCatching{CloudSharing(a){r->calls++;cloudResponse(r,code,"secret-private-error")}.create(entry(a,"file"))}.exceptionOrNull();assertNotNull(failure);assertEquals(1,calls);assertFalse(failure!!.message.orEmpty().contains("secret-private-error"))}
 }
 @Test fun koofrRefusesAmbiguousMountAndMissingSharingPassword()=runBlocking{
  val a=account("https://app.koofr.net/dav/Koofr/");var posts=0
  assertTrue(runCatching{CloudSharing(a){r->if(r.method=="POST")posts++;cloudResponse(r,200,"{\"mounts\":[{\"id\":\"one\",\"isPrimary\":true},{\"id\":\"two\",\"isPrimary\":true}]}")}.create(entry(a,"file"))}.isFailure);assertEquals(0,posts)
  assertTrue(runCatching{CloudSharing(a){r->if(r.method=="GET")cloudResponse(r,200,"{\"mounts\":[{\"id\":\"one\",\"isPrimary\":true}]}")else cloudResponse(r,201,"{\"url\":\"https://app.koofr.net/links/fixture\",\"hasPassword\":true}")}.create(entry(a,"file"))}.isFailure)
 }
 @Test fun cancellationDoesNotReturnALink()=runBlocking{
  val a=account("https://webdav.pcloud.com/");val entered=CompletableDeferred<Unit>();var returned=false
  val job=launch{CloudSharing(a){entered.complete(Unit);awaitCancellation()}.create(entry(a,"file"));returned=true}
  entered.await();job.cancelAndJoin();assertFalse(returned)
 }
 @Test fun enrollmentOffersOnlyPcloudAndKoofr(){
  assertEquals(listOf("pCloud · US","pCloud · EU","Koofr"),cloudProviders.map{it.name});assertTrue(cloudProviders.all{it.signup.startsWith("https://")&&!it.instructions.contains("webdav",true)})

 }
}
