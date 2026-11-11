package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.*
import java.security.KeyStore
import java.util.concurrent.*
import javax.net.ssl.*

/** Loopback servers only. A temporary test CA is trusted explicitly; production hostname checks remain enabled. */
private class MailTlsFixture(private val rejectData:Boolean=false, private val rejectImap:Boolean=false,
 private val rejectSmtp:Boolean=false):AutoCloseable{
 val commands=CopyOnWriteArrayList<String>();val recipients=CopyOnWriteArrayList<String>();var data="";private val dir=kotlin.io.path.createTempDirectory("email-tls").toFile();private val prior=SSLContext.getDefault();private val sockets=CopyOnWriteArrayList<java.net.Socket>();private val servers=mutableListOf<java.net.ServerSocket>()
 val imapPort:Int;val smtpPort:Int
 init{val key=File(dir,"fixture.p12");val process=ProcessBuilder(File(System.getProperty("java.home"),"bin/keytool").path,"-genkeypair","-alias","fixture","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=localhost","-ext","SAN=dns:localhost","-storetype","PKCS12","-keystore",key.path,"-storepass","fixture-password","-keypass","fixture-password","-noprompt").redirectErrorStream(true).start();check(process.waitFor(20,TimeUnit.SECONDS)&&process.exitValue()==0){"TLS fixture generation failed"};val ks=KeyStore.getInstance("PKCS12").apply{key.inputStream().use{load(it,"fixture-password".toCharArray())}};val km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply{init(ks,"fixture-password".toCharArray())};val tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply{init(ks)};val ssl=SSLContext.getInstance("TLS").apply{init(km.keyManagers,tm.trustManagers,null)};SSLContext.setDefault(ssl);imapPort=serve(ssl,false);smtpPort=serve(ssl,true)}
 private fun serve(ssl:SSLContext,smtp:Boolean):Int{val server=ssl.serverSocketFactory.createServerSocket(0,10,java.net.InetAddress.getLoopbackAddress()) as SSLServerSocket;servers.add(server);Thread{while(!server.isClosed){val socket=try{server.accept()}catch(_:Exception){break};sockets.add(socket);Thread{try{socket.soTimeout=15000;val input=socket.getInputStream().bufferedReader(Charsets.US_ASCII);val out=socket.getOutputStream().bufferedWriter(Charsets.US_ASCII);fun line(s:String){out.write(s+"\r\n");out.flush()};if(smtp){line("220 localhost ESMTP");var auth=0;while(true){val raw=input.readLine()?:break;val verb=raw.substringBefore(' ').uppercase();when{auth==1->{line("334 UGFzc3dvcmQ6");auth=2};auth==2->{line("235 2.7.0 Accepted");auth=0};verb=="EHLO"->{line("250-localhost");line("250-AUTH LOGIN PLAIN");line("250 SIZE 40000000")};verb=="AUTH"&&rejectSmtp->line("535 5.7.8 rejected");verb=="AUTH"->{if(raw.contains("PLAIN"))line("235 2.7.0 Accepted")else{line("334 VXNlcm5hbWU6");auth=1}};verb=="MAIL"->line("250 OK");verb=="RCPT"->{recipients.add(raw);line("250 OK")};verb=="DATA"->{line("354 Send data");val body=StringBuilder();while(true){val v=input.readLine()?:break;if(v==".")break;body.append(v).append('\n')};data=body.toString();if(rejectData){socket.close();break}else line("250 2.0.0 Accepted")};verb=="QUIT"->{line("221 Bye");break};else->line("250 OK")}}}else{line("* OK [CAPABILITY IMAP4rev1 UIDPLUS MOVE] localhost");while(true){val raw=input.readLine()?:break;val tag=raw.substringBefore(' ');val command=raw.substringAfter(' ');val verb=command.substringBefore(' ').uppercase();if(verb in listOf("LOGIN","AUTHENTICATE") && rejectImap){line("$tag NO [AUTHENTICATIONFAILED] rejected");continue};if(verb !in listOf("LOGIN","AUTHENTICATE"))commands.add(command);when(verb){"CAPABILITY"->line("* CAPABILITY IMAP4rev1 UIDPLUS MOVE");"LIST"->line("* LIST (\\HasNoChildren) \"/\" \"INBOX\"");"SELECT","EXAMINE"->{line("* FLAGS (\\Seen \\Flagged \\Deleted)");line("* 2 EXISTS");line("* 0 RECENT");line("* OK [UIDVALIDITY 7] valid");line("* OK [UIDNEXT 13] next");line("* OK [PERMANENTFLAGS (\\Seen \\Flagged \\Deleted)] flags")};"FETCH","UID"->{val ids=if(command.contains("1:2"))listOf(1,2)else if(command.contains("12"))listOf(2)else listOf(1);ids.forEach{n->line("* $n FETCH (UID ${10+n} FLAGS () INTERNALDATE \"27-Oct-2026 10:00:00 +0000\" RFC822.SIZE 100 ENVELOPE (\"Tue, 27 Oct 2026 10:00:00 +0000\" \"Example $n\" ((\"Alice\" NIL \"alice\" \"example.test\")) NIL NIL ((NIL NIL \"user\" \"example.test\")) NIL NIL NIL \"<$n@example.test>\") BODYSTRUCTURE (\"TEXT\" \"PLAIN\" (\"CHARSET\" \"UTF-8\") NIL NIL \"7BIT\" 5 1))")}};"LOGOUT"->{line("* BYE");line("$tag OK logout");break}};line("$tag OK completed")}}}catch(e:Exception){System.err.println("TLS fixture: ${e.javaClass.name}: ${e.message}")}finally{runCatching{socket.close()}}}.apply{isDaemon=true;start()}}}.apply{isDaemon=true;start()};return server.localPort}
 override fun close(){servers.forEach{runCatching{it.close()}};sockets.forEach{runCatching{it.close()}};SSLContext.setDefault(prior);dir.deleteRecursively()}
}
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class MailProtocolTest{
 private val context get()=ApplicationProvider.getApplicationContext<Context>()
 @Test(timeout=60000) fun secureImapAndSmtpUseRealProtocolAndKeepBccOutOfMessage(){MailTlsFixture().use{server->val disk=MailDisk(context,MailTestKeys());val a=MailAccount(provider="Other",email="user@example.test",imap="localhost",imapPort=server.imapPort,smtp="localhost",smtpPort=server.smtpPort);disk.write("secret-${a.id}",JSONObject().put("password","fixture-password"));val protocol=MailProtocol(context,disk){};runBlocking{protocol.test(a);val page=protocol.page(a,"INBOX");assertEquals(7L,page.validity);assertEquals(listOf(11L,12L),page.headers.map{it.uid}.sorted());assertEquals("Example 1",page.headers.first{it.uid==11L}.subject);protocol.flag(a,page.headers.first(),star=true);val phases=mutableListOf<String>();val d=MailDraft(account=a.id,to="to@example.test",bcc="private@example.test",subject="Test",doc=WriteDocument(text="Hello"));assertEquals("Sent",protocol.send(a,d,{error("No attachment")}){phases.add(it)});assertEquals(listOf("sending","sent"),phases);assertTrue(server.recipients.any{it.contains("private@example.test")});assertFalse(server.data.lineSequence().any{it.startsWith("Bcc:",true)});val submitted=javax.mail.internet.MimeMessage(javax.mail.Session.getInstance(java.util.Properties()),server.data.byteInputStream());assertEquals(a.email,(submitted.from.single() as javax.mail.internet.InternetAddress).address);assertFalse(submitted.messageID.isNullOrBlank());assertTrue(server.data.contains("Hello"));assertTrue(server.commands.any{it.startsWith("STORE")||it.contains("STORE")})}}}
 @Test(timeout=60000) fun lostSmtpAcknowledgementIsUncertainAndNeverAutomaticallyRetried(){MailTlsFixture(true).use{server->val disk=MailDisk(context,MailTestKeys());val a=MailAccount(provider="Other",email="user@example.test",imap="localhost",imapPort=server.imapPort,smtp="localhost",smtpPort=server.smtpPort);disk.write("secret-${a.id}",JSONObject().put("password","fixture-password"));val phases=mutableListOf<String>();val d=MailDraft(account=a.id,to="to@example.test",doc=WriteDocument(text="Only one transmission"));try{runBlocking{MailProtocol(context,disk){}.send(a,d,{error("No attachment")}){phases.add(it)}};fail("Should report uncertain delivery")}catch(e:IllegalStateException){assertTrue(e.message!!.contains("unconfirmed"))};assertEquals(listOf("sending","uncertain"),phases);assertEquals(1,server.recipients.size)}}
 @Test(timeout=60000) fun gmxSetupReportsTheServerThatRejectsAuthentication() {
  val disk = MailDisk(context, MailTestKeys())
  for (outgoing in listOf(false, true)) {
   MailTlsFixture(rejectImap = !outgoing, rejectSmtp = outgoing).use { server ->
    val account = MailAccount.preset("GMX", "owner@gmx.com").copy(
     imap = "localhost", imapPort = server.imapPort, smtp = "localhost", smtpPort = server.smtpPort)
    disk.write("secret-${account.id}", JSONObject().put("password", "fixture-password"))
    val failure = assertThrows(MailSetupSignInRejected::class.java) {
     runBlocking { MailProtocol(context, disk) {}.test(account) }
    }
    val stage = if (outgoing) "Outgoing mail (SMTP)" else "Incoming mail (IMAP)"
    assertTrue(MailMime.error(failure).startsWith("$stage rejected sign-in."))
   }
  }
 }
 @Test(timeout=60000) fun certificateHostnameMismatchIsRejected(){MailTlsFixture().use{server->val disk=MailDisk(context,MailTestKeys());val a=MailAccount(provider="Other",email="user@example.test",imap="127.0.0.1",imapPort=server.imapPort,smtp="localhost",smtpPort=server.smtpPort);disk.write("secret-${a.id}",JSONObject().put("password","fixture-password"));val e=assertThrows(javax.mail.MessagingException::class.java){runBlocking{MailProtocol(context,disk){}.test(a)}};assertTrue(e.toString().contains("identity",true)||e.toString().contains("certificate",true))}}
}
