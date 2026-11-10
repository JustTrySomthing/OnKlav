package app.onklav.home
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class MailWorkflowTest{
 private fun h(uid:Long,id:String="",refs:List<String> = emptyList(),account:String="a",validity:Long=7)=MailHeader(account,"INBOX",validity,uid,"Alice <alice@example.org>","me@example.org","Same subject",uid,false,false,messageId=id,references=refs)
 @Test fun explicitLinksThreadRepliesWithoutSubjectOrAccountCollisions(){
  val rows=listOf(h(1,"<one@example.org>"),h(2,"<two@example.org>",listOf("<one@example.org>")),h(3,"<other@example.org>"),h(4,"<one@example.org>",account="b"))
  val threads=mailConversations(rows);assertEquals(3,threads.size);assertEquals(listOf(2L,1L),threads.single{it.size==2}.map{it.uid})
 }
 @Test fun missingRootsAndCyclesAreBoundedAndLegacyHeadersRemainSeparate(){
  val linked=listOf(h(1,"<one@example.org>",listOf("<missing@example.org>","<two@example.org>")),h(2,"<two@example.org>",listOf("<missing@example.org>","<one@example.org>")))
  assertEquals(1,mailConversations(linked).size);assertEquals(2,mailConversations(listOf(h(3),h(4))).size)
  val j=linked.first().json();assertEquals(linked.first(),MailHeader.parse(j));j.remove("references");assertTrue(MailHeader.parse(j).references.isEmpty())
 }
 @Test fun suggestionsAreScopedDeduplicatedAndQuotedNamesInsertCorrectly(){
  val rows=listOf(h(1).copy(from="\"Doe, Jane\" <jane@example.org>"),h(2,account="other").copy(from="Jane Other <private@example.org>"))
  val raw="Bob <bob@example.org>, ja";val result=mailRecipientSuggestions(raw,"a",rows,emptyList());assertEquals(1,result.size);assertEquals("jane@example.org",result.single().email)
  val full=mailInsertRecipient(raw,result.single());assertEquals(listOf("bob@example.org","jane@example.org"),MailMime.addresses(full.trim().trimEnd(',')).map{it.address})
  assertTrue(mailRecipientSuggestions("jane@example.org, ja","a",rows,emptyList()).isEmpty());assertEquals(0,mailRecipientStart("\"Doe, Ja"))
 }
 @Test fun suggestionsIncludeSavedRecipientsAndRejectBlankAndInvalidInput(){
  val draft=MailDraft(account="a",to="Carol <carol@example.org>")
  assertEquals("carol@example.org",mailRecipientSuggestions("ca","a",emptyList(),listOf(draft)).single().email)
  assertTrue(mailRecipientSuggestions("c","a",emptyList(),listOf(draft)).isEmpty());assertTrue(mailRecipientSuggestions("ca","b",emptyList(),listOf(draft)).isEmpty())
 }
 @Test fun headRefreshKeepsOlderPagesAndOtherFoldersButDropsStaleValidity(){
  val old=listOf(h(1),h(2),h(3),h(9,validity=6),h(10).copy(folder="Projects"))
  val next=mailMergePage(old,listOf(h(3).copy(seen=true),h(4)),"INBOX",7)
  assertEquals(setOf(1L,2L,3L,4L,10L),next.map{it.uid}.toSet());assertTrue(next.single{it.uid==3L}.seen)
  assertEquals(listOf(10L),mailMergePage(old,emptyList(),"INBOX",8).map{it.uid})
 }
 @Test fun queuedDraftFlushCannotReplaceAcceptedOrNewerSavedData(){
  val app=ApplicationProvider.getApplicationContext<Application>();runBlocking{AppSdk.bootstrap(app)};val disk=MailDisk(app,MailTestKeys());val a=MailAccount.preset("Yahoo","me@yahoo.com");disk.saveAccounts(listOf(a))
  val d=MailDraft(account=a.id,to="to@example.org",subject="Initial",modified=100);disk.saveDrafts(listOf(d));val model=MailModel(app,disk,false)
  val end=System.nanoTime()+10_000_000_000L;while(model.busy&&System.nanoTime()<end){shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5)};assertFalse(model.busy)
  model.editDraft(d);disk.saveDrafts(listOf(d.copy(phase="sent")));runBlocking{model.flush()?.join()};assertEquals("sent",disk.drafts().single().phase)
  disk.saveDrafts(listOf(d.copy(subject="Newer",modified=200)));runBlocking{model.flush()?.join()};assertEquals("Newer",disk.drafts().single().subject)
 }
}
