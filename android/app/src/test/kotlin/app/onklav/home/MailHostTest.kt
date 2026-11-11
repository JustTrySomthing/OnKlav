package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.util.Properties
import javax.crypto.*
import javax.mail.*
import javax.mail.internet.*

internal class MailTestKeys:SecretKeys{val values=mutableMapOf<String,SecretKey>();override fun load(alias:String,create:Boolean)=values[alias]?:if(create)KeyGenerator.getInstance("AES").apply{init(256)}.generateKey().also{values[alias]=it}else error("Key unavailable");override fun deletePrefix(prefix:String){values.keys.removeAll{it.startsWith(prefix)}}}
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class MailHostTest{
 @Test fun nativeWorkspaceUsesTheAndroidViewModelFactory(){assertNotNull(MailModel::class.java.getConstructor(android.app.Application::class.java))}
 private val context get()=ApplicationProvider.getApplicationContext<Context>()
 @Test fun encryptedAccountsDraftsAndLargeAttachmentsRoundTripAndFailClosed(){val keys=MailTestKeys();val disk=MailDisk(context,keys);val a=MailAccount.preset("Yahoo","me@yahoo.com");disk.saveAccounts(listOf(a));disk.write("secret-${a.id}",JSONObject().put("password","sensitive-fixture"));val bytes=ByteArray(13_000_000){(it%251).toByte()};var progress=0L;assertEquals(bytes.size.toLong(),disk.writeBlob("attach-fixture",ByteArrayInputStream(bytes),25_000_000){progress=it});assertEquals(bytes.size.toLong(),progress);assertArrayEquals(bytes,disk.source("attach-fixture","audio/mpeg","Example.mp3").inputStream.use{it.readBytes()});val d=MailDraft(account=a.id,to="to@example.org",subject="A draft",doc=WriteDocument(text="Hello"),phase="uncertain");disk.saveDrafts(listOf(d));assertEquals(d,disk.drafts().single());assertEquals(a,disk.accounts().single());val folder=File(context.noBackupFilesDir,"email");assertFalse(folder.listFiles()!!.any{String(it.readBytes(),Charsets.ISO_8859_1).contains("sensitive-fixture")});val file=File(folder,"accounts");val encrypted=file.readBytes();keys.values.clear();assertThrows(Exception::class.java){disk.saveAccounts(emptyList())};assertArrayEquals(encrypted,file.readBytes());assertThrows(Exception::class.java){disk.read("../outside")}}
 @Test fun gmxCredentialsCanBeStoredThroughAnAndroidPrivateDirectoryAlias() {
  val parent = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "mail-path")
  val real = java.nio.file.Files.createDirectory(parent.resolve("private"))
  val alias = java.nio.file.Files.createSymbolicLink(parent.resolve("android-alias"), real)
  val aliasedContext = object : android.content.ContextWrapper(context) {
   override fun getNoBackupFilesDir(): File = alias.toFile()
  }
  val account = MailAccount.preset("GMX", "owner@gmx.com")
  val disk = MailDisk(aliasedContext, MailTestKeys())
  val secretName = "secret-${account.id}"
  assertNull(disk.read(secretName))
  disk.write(secretName, JSONObject().put("password", "fixture"))
  assertEquals("fixture", disk.read(secretName)?.getString("password"))
  disk.remove(secretName)
  assertNull(disk.read(secretName))

  val outside = java.nio.file.Files.createDirectory(parent.resolve("outside"))
  java.nio.file.Files.delete(real.resolve("email"))
  java.nio.file.Files.createSymbolicLink(real.resolve("email"), outside)
  assertThrows(IllegalStateException::class.java) { disk.write(secretName, JSONObject()) }
  assertFalse(java.nio.file.Files.exists(outside.resolve(secretName)))
 }
 @Test fun richMimeContainsAlternativesListsAttachmentsAndSafeRecipientHandling(){val a=MailAccount.preset("Gmail","me@gmail.com").copy(name="A sender");val d=MailDraft(account=a.id,to="Alice <alice@example.org>",bcc="hidden@example.org",subject="Résumé",doc=WriteDocument(text="Hello\nFirst",marks=listOf(WriteMark(0,5,"style","1"),WriteMark(6,11,"list","number:1:0"))),files=listOf(MailAttachment("x","clip.txt","text/plain",5)));val source=object:javax.activation.DataSource{override fun getInputStream()=ByteArrayInputStream("hello".toByteArray());override fun getOutputStream():OutputStream=error("Read only");override fun getContentType()="text/plain";override fun getName()="clip.txt"};val m=MailMime.outgoing(Session.getInstance(Properties()),a,d){source};assertEquals(2,m.allRecipients.size);val out=ByteArrayOutputStream();m.writeTo(out);val parsed=MimeMessage(Session.getInstance(Properties()),ByteArrayInputStream(out.toByteArray()));assertEquals("Résumé",parsed.subject);val body=MailMime.body(parsed);assertEquals("Hello\nFirst",body.text);assertTrue(body.html.contains("font-weight:bold"));assertTrue(body.html.contains("<ol>"));assertEquals("clip.txt",body.attachments.single().name);assertEquals("hello",MailMime.part(parsed,body.attachments.single().part).inputStream.reader().readText());assertThrows(IllegalArgumentException::class.java){MailMime.addresses("safe@example.org\r\nBcc: other@example.org")};assertThrows(Exception::class.java){MailMime.addresses("not-an-email")}}
 @Test fun htmlReaderNeverLoadsRemoteContentAndDropsUnsafeLinks(){val text=MailMime.display("<script>danger()</script><style>hidden</style><p>Hello <b>world</b> <a href='javascript:bad()'>bad</a><a href='https://example.org'>safe</a><img src='https://tracker.test/pixel'></p>");assertFalse(text.toString().contains("danger"));assertTrue(text.toString().contains("Hello world"));assertEquals(0,text.getSpans(0,text.length,android.text.style.ImageSpan::class.java).size);assertEquals(listOf("https://example.org"),text.getSpans(0,text.length,android.text.style.URLSpan::class.java).map{it.url})}
 @Test fun filtersAndProviderCredentialsStayBoundToTheirAccount(){val a=MailAccount.preset("Gmail","me@gmail.com");a.validate();assertThrows(IllegalArgumentException::class.java){a.copy(provider="Unsupported").validate()};assertThrows(IllegalArgumentException::class.java){MailAccount.preset("Other","me@example.org").copy(provider="Unsupported").validate()};val h=MailHeader(a.id,"INBOX",7,20,"A Shop <shop@example.org>",a.email,"Your order",1,false,false);val r=MailRule(name="Orders",account=a.id,contains="SHOP@EXAMPLE.ORG",action="move",folder="Orders");r.validate();assertTrue(r.matches(h));assertFalse(r.matches(h.copy(account=mailId())));assertEquals(r,MailRule.parse(r.json()));assertThrows(IllegalArgumentException::class.java){r.copy(account="").validate()};assertEquals(h,MailHeader.parse(h.json()))}
 @Test fun signedEmailAppLaunchesAndGrantsAreRevocable(){runBlocking{AppSdk.bootstrap(context)};val frame=(AppSdk.sync(context,JSONObject().put("action","launch").put("id",MAIL_APP))as JSONObject).getJSONObject("frame");assertEquals("Email",frame.getString("title"));assertTrue(mailAccess(context));assertTrue(mailAccess(context,"storage.secrets"));assertTrue(mailAccess(context,"network.http"));AppSdk.sync(context,JSONObject().put("action","permissions").put("id",MAIL_APP).put("grants",JSONArray()));assertFalse(mailAccess(context));assertFalse(mailAccess(context,"network.http"))}
}
