package app.onklav.home
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import org.json.JSONObject
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class GameSaveStoreTest {
 private fun withStore(block:(java.io.File,GameSaveStore,GameSaveStore.Lease)->Unit){val root=Files.createTempDirectory("cartridges").toFile();try{val store=GameSaveStore(root,"builtin.panda");block(root,store,store.begin())}finally{root.deleteRecursively()}}
 private fun data(n:Int)=JSONObject().put("level",n).put("inventory",org.json.JSONArray("[\"key\",\"sword\"]"))
 @Test fun everyGameAndPublisherHasItsOwnCartridge()=withStore{root,panda,lease->
  panda.commit(lease,"resume",data(4),1,true)
  val hockey=GameSaveStore(root,"builtin.hockey");val h=hockey.begin();assertFalse(hockey.load("resume").getBoolean("exists"));hockey.commit(h,"resume",data(8),1,true)
  assertEquals(4,panda.load("resume").getJSONObject("record").getJSONObject("state").getInt("level"))
  assertFalse(GameSaveStore(root,"other.publisher.panda").load("resume").getBoolean("exists"))
 }
 @Test fun obsoleteRendererCannotOverwriteNewSession()=withStore{_,store,old->
  store.commit(old,"resume",data(1),1,true);val next=store.begin();store.commit(next,"resume",data(9),1,true)
  assertThrows(IllegalStateException::class.java){store.commit(old,"resume",data(2),1,true)}
  assertThrows(IllegalStateException::class.java){store.clear(old,"resume")}
  assertEquals(9,store.load("resume").getJSONObject("record").getJSONObject("state").getInt("level"))
 }
 @Test fun invalidOrOversizedNewGamePreservesPreviousSave()=withStore{_,store,lease->
  store.commit(lease,"resume",data(5),1,true)
  assertThrows(IllegalStateException::class.java){store.commit(lease,"resume",JSONObject().put("huge","x".repeat(GameSaveStore.LIMIT+1)),1,true,true)}
  assertEquals(5,store.load("resume").getJSONObject("record").getJSONObject("state").getInt("level"))
 }
 @Test fun diskFailureKeepsOldCheckpoint()=withStore{root,store,lease->
  store.commit(lease,"resume",data(5),1,true)
  val failing=GameSaveStore(root,"builtin.panda"){file->if(file.name=="resume.json")throw java.io.IOException("Injected disk-full write failure")}
  assertThrows(java.io.IOException::class.java){failing.commit(lease,"resume",data(6),1,true)}
  assertEquals(5,store.load("resume").getJSONObject("record").getJSONObject("state").getInt("level"))
 }
 @Test fun corruptPrimaryIsReportedAndPreviousCopyRetained()=withStore{root,store,lease->
  store.commit(lease,"resume",data(1),1,true);store.commit(lease,"resume",data(2),1,true)
  val file=root.walkTopDown().first{it.name=="resume.json"};file.writeText("broken")
  val damaged=store.load("resume");assertFalse(damaged.getBoolean("ok"));assertEquals("broken",file.readText());assertEquals(1,damaged.getJSONObject("backup").getJSONObject("state").getInt("level"))
  assertThrows(Exception::class.java){store.commit(lease,"resume",data(3),1,true)}
  store.commit(lease,"resume",data(3),2,true,true);assertEquals(2,store.load("resume").getJSONObject("record").getInt("payload_version"))
 }
 @Test fun profileAndFriendSessionSurviveClearingSolo()=withStore{_,store,lease->
  store.commit(lease,"data.profile",data(10),1,false);store.commit(lease,"friend.abc",data(4),1,true);store.commit(lease,"resume",data(2),1,true)
  store.clear(lease,"resume");val empty=store.load("resume");assertTrue(empty.getBoolean("known"));assertFalse(empty.getBoolean("exists"))
  assertTrue(store.load("data.profile").getBoolean("exists"));assertTrue(store.load("friend.abc").getBoolean("exists"))
 }
 @Test fun rebootPreservesDataAndTerminalProgressIsDistinctFromResumability()=withStore{root,store,lease->
  store.commit(lease,"resume",data(12),3,false)
  val row=GameSaveStore(root,"builtin.panda").load("resume").getJSONObject("record");assertFalse(row.getBoolean("resumable"));assertEquals(12,row.getJSONObject("state").getInt("level"));assertTrue(row.getLong("saved_at")>0)
 }
 @Test fun slotNamesCannotEscapePrivateStorage()=withStore{_,store,lease->
  for(name in listOf("../other","a/b","/resume","", "x".repeat(97)))assertThrows(IllegalArgumentException::class.java){store.commit(lease,name,data(1),1,true)}
 }
}
