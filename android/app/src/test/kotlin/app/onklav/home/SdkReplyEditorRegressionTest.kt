package app.onklav.home
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
/** Exercise changed shared transactions directly, independently of launcher setup/paging. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class SdkReplyEditorRegressionTest {
 @get:Rule val emptyKeyStore=EmptyKeyStoreRule()
 private fun idle(){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))}
 private fun await(ok:()->Boolean){val end=System.nanoTime()+10_000_000_000;while(!ok()&&System.nanoTime()<end){idle();Thread.sleep(5)};assertTrue(ok())}
 private fun action(m:SdkAppModel,id:String){m.action(id);idle();await{!m.busy}}
 @Test fun notesDirtyFlushClipboardRepliesAndReopenRemainUsable(){
  val c=ApplicationProvider.getApplicationContext<Application>();val m=SdkAppModel(c)
  runBlocking{m.open("app.onklav.notes")};assertNull(m.error)
  m.edit("note.title","Shared reply regression");m.edit("note.text","Hello 🐈");action(m,"settings")
  assertEquals("Hello 🐈",m.frame.child("state").child("note").getString("text"));assertNull(m.error)
  action(m,"copy");val copy=m.pending!!;val write=m.authorizeNow(copy);assertEquals("clipboard.write",write.getString("service"));assertEquals("Hello 🐈",write.child("args").getString("text"))
  runBlocking{m.reply(copy)};assertNull(m.pending);assertNull(m.error)
  m.selection="Hello 🐈".length;action(m,"settings");action(m,"paste");val paste=m.pending!!;assertEquals("clipboard.read",m.authorizeNow(paste).getString("service"))
  runBlocking{m.reply(paste," again")};assertNull(m.pending);assertNull(m.error);assertEquals("Hello 🐈 again",m.frame.child("state").child("note").getString("text"))
  var closed=false;m.close{closed=true};await{closed};val next=SdkAppModel(c);runBlocking{next.open("app.onklav.notes")};assertNull(next.error);assertEquals("Hello 🐈 again",next.frame.child("state").child("note").getString("text"))
 }
}
