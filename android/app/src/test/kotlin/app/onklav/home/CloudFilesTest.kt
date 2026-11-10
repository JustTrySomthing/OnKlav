package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

internal fun cloudResponse(r:Request,code:Int,body:String="")=Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("Fixture").body(body.toResponseBody()).build()
internal fun cloudXml(vararg responses:String)="<d:multistatus xmlns:d=\"DAV:\">"+responses.joinToString("")+"</d:multistatus>"
internal fun cloudRow(href:String,name:String,folder:Boolean=false,size:Long=3,status:Int=200)="<d:response><d:href>$href</d:href><d:propstat><d:prop><d:displayname>$name</d:displayname><d:resourcetype>${if(folder)"<d:collection/>"else""}</d:resourcetype><d:getcontentlength>$size</d:getcontentlength><d:getetag>&quot;revision&quot;</d:getetag></d:prop><d:status>HTTP/1.1 $status Fixture</d:status></d:propstat></d:response>"
internal fun cloudAccount()=CloudAccount(name="Example cloud",endpoint="https://cloud.example.test/dav/",user="example@example.test",password="not-real-secret")
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class CloudFilesTest{
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 @Before fun clear(){File(c.noBackupFilesDir,"cloud-files").deleteRecursively()}
 @After fun cleanup(){clear()}
 @Test fun providerPresetsHaveRegionsAndAppPasswordHelp(){assertEquals("https://webdav.pcloud.com/",cloudProviders[0].endpoint);assertEquals("https://ewebdav.pcloud.com/",cloudProviders[1].endpoint);assertTrue(cloudProviders[2].instructions.contains("app password"));assertTrue(cloudProviders[2].instructions.contains("Free accounts"));assertEquals(3,cloudProviders.size);assertTrue(cloudProviders.all{it.signup.startsWith("https://")})}
 @Test fun credentialsAndPathsStayOnHttpsAccountRoot(){for(u in listOf("http://cloud.test/","https://user:secret@cloud.test/","https://cloud.test/?token=secret","https://cloud.test/#secret"))assertThrows(Exception::class.java){CloudPaths.base(u)};val base=CloudPaths.base("https://cloud.test/dav");assertEquals("/dav/",base.encodedPath);assertFalse(CloudPaths.safe(base,"https://other.test/dav/file".toHttpUrl()));assertFalse(CloudPaths.safe(base,"https://cloud.test/dav-other/file".toHttpUrl()));for(href in listOf("../outside","https://evil.test/dav/file","/dav/%2e%2e/file","/dav/one%2ftwo"))assertNull(CloudPaths.href(base,base,href));assertEquals("/dav/a%20%23%3F.txt",CloudPaths.child(base,"a #?.txt").encodedPath);assertFalse(cloudAccount().toString().contains("not-real-secret"))}
 @Test fun listingsHonorPropstatStatusDirectChildrenNamespacesAndFolders(){val client=CloudDav(cloudAccount());val xml=cloudXml(cloudRow("/dav/","Root",true),cloudRow("/dav/folder/","Folder",true),cloudRow("/dav/a%20b.txt","A &amp; B"),cloudRow("https://evil.test/dav/x","Evil"),cloudRow("/dav/missing.txt","Missing",status=404),cloudRow("/dav/deep/file.txt","Grandchild"));val listing=client.parse(client.root,xml.toByteArray());assertEquals(listOf("Folder","A & B"),listing.entries.map{it.name});assertTrue(listing.entries[0].folder);assertEquals(3L,listing.entries[1].size);assertEquals("\"revision\"",listing.entries[1].etag)}
 @Test fun unsafeOrOversizedXmlIsRejected(){val client=CloudDav(cloudAccount());assertThrows(Exception::class.java){client.parse(client.root,"<!DOCTYPE x [<!ENTITY a SYSTEM 'file:///secret'>]><x/>".toByteArray())};assertThrows(Exception::class.java){client.parse(client.root,ByteArray(4*1024*1024+1))}}
 @Test fun redirectDoesNotTriggerAnotherRequestOrExposePassword()=runBlocking{var count=0;val a=cloudAccount();val client=CloudDav(a){r->count++;assertEquals(Credentials.basic(a.user,a.password,Charsets.UTF_8),r.header("Authorization"));cloudResponse(r,302).newBuilder().header("Location","https://evil.test/steal").build()};val e=runCatching{client.list()}.exceptionOrNull()!!;assertEquals(1,count);assertFalse(e.message.orEmpty().contains(a.password));assertTrue(e.message.orEmpty().contains("not forwarded"))}
 @Test fun uploadsStreamWithCreateOnlyPreconditionAndNeverRetryConflict()=runBlocking{val a=cloudAccount();var bytes=byteArrayOf();var request:Request?=null;val payload=ByteArray(1024*1024){(it%251).toByte()};val client=CloudDav(a){r->request=r;val b=Buffer();r.body!!.writeTo(b);bytes=b.readByteArray();cloudResponse(r,201)};client.upload(client.root,"Sample.bin",payload.size.toLong(),{payload.inputStream()}){};assertArrayEquals(payload,bytes);assertEquals("*",request!!.header("If-None-Match"));assertEquals("PUT",request!!.method);assertTrue(request!!.body!!.isOneShot());var calls=0;val conflict=CloudDav(a){r->calls++;cloudResponse(r,412)};assertTrue(runCatching{conflict.upload(conflict.root,"Sample.bin",0,{byteArrayOf().inputStream()}){}}.isFailure);assertEquals(1,calls)}
 @Test fun downloadChecksRevisionAndDeletesFailedTemporaryCopies()=runBlocking{val dir=File(c.cacheDir,"cloud-test").apply{mkdirs()};val f=File(dir,"part");val a=cloudAccount();var tag:String?=null;val client=CloudDav(a){r->tag=r.header("If-Match");cloudResponse(r,200,"abc")};client.download(CloudEntry("https://cloud.example.test/dav/file".toHttpUrl(),"file",false,etag="\"revision\""),f){_,_->};assertEquals("abc",f.readText());assertEquals("\"revision\"",tag);val fail=CloudDav(a){r->cloudResponse(r,401)};assertTrue(runCatching{fail.download(CloudEntry("https://cloud.example.test/dav/file".toHttpUrl(),"file",false),f){_,_->}}.isFailure);assertFalse(f.exists());dir.deleteRecursively();Unit}
 @Test fun credentialsAreEncryptedAndCorruptionCannotOverwriteExistingAccounts(){val keys=MailTestKeys();val store=CloudStore(c,keys);val a=cloudAccount();store.save(listOf(a));assertEquals(listOf(a),store.load());val file=File(c.noBackupFilesDir,"cloud-files/accounts.bin");val before=file.readBytes();assertFalse(String(before,Charsets.UTF_8).contains(a.password));val wrong=CloudStore(c,MailTestKeys());assertThrows(Exception::class.java){wrong.load()};assertThrows(Exception::class.java){wrong.save(emptyList())};assertArrayEquals(before,file.readBytes());store.save(emptyList());assertTrue(store.load().isEmpty())}
 @Test fun cloudAndBackupsAreRegisteredWithoutGoogleDriveUi(){assertEquals("Cloud Files",platformApps.single{it.id=="cloudfiles"}.title);val source=File(System.getProperty("onklav.project.root"),"android/app/src/main/kotlin/app/onklav/home");for(name in listOf("SettingsPane.kt","MessageAttachments.kt"))assertFalse(File(source,name).readText().contains("Google Drive"));assertNotNull(CloudModel::class.java.getConstructor(Application::class.java))}
}
