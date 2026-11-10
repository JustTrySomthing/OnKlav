package app.onklav.home

import android.Manifest
import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class IncomingGroupsTest {
 private val c get()=ApplicationProvider.getApplicationContext<android.app.Application>()
 class Phone:ContentProvider(){
  data class Row(val id:Long,val recipients:String,val date:Long=1000,var read:Int=0)
  val rows=mutableListOf(Row(7,"1"),Row(42,"1 2"),Row(43,"1 3"))
  var lastSelection=""
  override fun onCreate()=true
  override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor?{
   if(uri.path!!.startsWith("/canonical-address")){
    val all=mapOf("1" to "5550101","2" to "5550102","3" to "5550199")
    val columns=projection?:arrayOf("_id","address");return MatrixCursor(columns).apply{val single=uri.lastPathSegment?.takeIf{it.all(Char::isDigit)};for((id,address)in all)if(single==null||single==id)addRow(columns.map{if(it=="_id")id else address})}
   }
   if(uri.path!="/conversations")return null
   lastSelection=selection.orEmpty();val cols=projection?:emptyArray();var chosen=rows.toList()
   if(selection=="_id=?")chosen=chosen.filter{it.id==args!![0].toLong()}
   if(selection?.contains("recipient_ids LIKE")==true)chosen=chosen.filter{it.recipients.contains(' ')}
   if(selection?.contains("NOT IN")==true)chosen=chosen.filter{it.id !in args.orEmpty().mapNotNull(String::toLongOrNull)}
   return MatrixCursor(cols).apply{for(r in chosen.sortedByDescending{it.date})addRow(cols.map{when(it){"_id"->r.id;"date"->r.date;"snippet"->"New group text";"read"->r.read;"recipient_ids"->r.recipients;"message_count"->1;else->null}})}
  }
  override fun getType(uri:Uri)="text/plain"
  override fun insert(uri:Uri,v:ContentValues?):Uri?=null
  override fun delete(uri:Uri,s:String?,a:Array<out String>?)=0
  override fun update(uri:Uri,v:ContentValues?,s:String?,a:Array<out String>?)=0
 }
 private lateinit var phone:Phone
 @Before fun setup(){shadowOf(c).grantPermissions(Manifest.permission.READ_SMS);c.getSharedPreferences("profile",0).edit().putString("phone","5550199").commit();phone=Phone().apply{attachInfo(c,ProviderInfo().apply{authority="mms-sms";exported=false})};ShadowContentResolver.registerProviderInternal("mms-sms",phone)}
 @Test fun otherPeoplesGroupTextsAreDiscoveredButDirectTextsAndOwnNumberAreExcluded()=runBlocking {
  val state=MessageRepository(c).reconcileGroups();val groups=state.savedGroups();assertEquals(listOf(42L),groups.map{it.thread});assertEquals(listOf("5550101","5550102"),groups.single().members)
  val id=groups.single().id;val again=MessageRepository(c).reconcileGroups();assertEquals(id,again.savedGroups().single().id)
  val direct=MessageRepository(c).conversations(40,again,directOnly=true);assertEquals(setOf(7L,43L),direct.map{it.id}.toSet());assertTrue(phone.lastSelection.contains("NOT IN"))
 }
 @Test fun discoveryPreservesArchiveNamesPinAndMuteButNewCreationReturnsToInbox()=runBlocking {
  val store=ChatStore(c);store.requestSync(JSONObject().put("action","thread").put("key","42").put("folder","Trash").put("pinned",true).put("muted",true))
  val discovered=MessageRepository(c).reconcileGroups();assertEquals("Trash",discovered.thread(42).getString("folder"))
  val group=discovered.savedGroups().single();store.requestSync(JSONObject().put("action","group_save").put("id",group.id).put("name","Chosen name").put("members",JSONArray(group.members)).put("thread",42))
  assertEquals("Chosen name",MessageRepository(c).reconcileGroups().savedGroups().single().name)
  val created=store.requestSync(JSONObject().put("action","group_save").put("id","a".repeat(32)).put("name","Created now").put("members",JSONArray(group.members)).put("thread",42))
  assertEquals("Inbox",created.thread(42).getString("folder"));assertTrue(created.thread(42).getBoolean("pinned"));assertTrue(created.thread(42).getBoolean("muted"))
 }
 @Test fun newUnreadArrivalRestoresArchiveAndTrashAndReplayCannotUndoLaterReadOrArchive()=runBlocking {
  val store=ChatStore(c)
  for((i,folder)in listOf("Archive","Trash").withIndex()){
   store.requestSync(JSONObject().put("action","thread").put("key","42").put("folder",folder).put("unread",false).put("at",1000))
   val arrival=JSONObject().put("action","arrival").put("key","42").put("address","5550101").put("at",2000+i*100)
   val state=store.requestSync(arrival);assertEquals("Inbox",state.thread(42).getString("folder"));assertEquals(42L,state.savedGroups().single().thread)
   assertTrue(MessageRepository(c).conversation(42)!!.unread)
   store.requestSync(JSONObject().put("action","thread").put("key","42").put("folder","Archive").put("unread",false).put("at",2050+i*100))
   val replay=store.requestSync(arrival);assertEquals("Archive",replay.thread(42).getString("folder"));assertFalse(MessageRepository(c).conversation(42)!!.unread)
  }
 }
 @Test fun bindingAnUnsentGroupKeepsItsNameMuteAndPinDuringTheFirstArrival(){
  val store=ChatStore(c);val id="b".repeat(32)
  store.requestSync(JSONObject().put("action","group_save").put("id",id).put("name","My people").put("members",JSONArray(listOf("5550101","5550102"))).put("thread",0))
  store.requestSync(JSONObject().put("action","thread").put("key","group:$id").put("folder","Archive").put("muted",true).put("pinned",true))
  val state=store.requestSync(JSONObject().put("action","arrival").put("key","42").put("address","5550101").put("at",2000))
  val group=state.savedGroups().single();assertEquals(id,group.id);assertEquals("My people",group.name);assertEquals(42L,group.thread)
  assertTrue(state.thread(42).getBoolean("pinned"));assertTrue(state.thread(42).getBoolean("muted"));assertTrue(state.thread(42).getBoolean("unread"));assertEquals("Inbox",state.thread(42).getString("folder"))
 }
 @Test fun manualUnreadUsesTheSameStateForGroupAndMessageRows(){
  val row=Conversation(42,"5550101;5550102","Friends","text",1000,false)
  val state=JSONObject().put("threads",JSONObject().put("42",JSONObject().put("unread",true).put("read_at",1100)))
  assertTrue(conversationReadState(row,state).unread)
  state.thread(42).put("unread",false).put("last_incoming_at",1200)
  assertTrue(conversationReadState(row.copy(unread=true),state).unread)
 }
 @Test fun blockedGroupArrivalsStayOutOfInboxAndKeepOtherControls()=runBlocking{
  val store=ChatStore(c);MessageRepository(c).reconcileGroups();store.requestSync(JSONObject().put("action","blocked").put("address","5550101").put("blocked",true))
  store.requestSync(JSONObject().put("action","thread").put("key","42").put("folder","Trash").put("muted",true).put("pinned",true))
  val state=store.requestSync(JSONObject().put("action","arrival").put("key","42").put("address","5550101").put("at",3000))
  assertEquals("Trash",state.thread(42).getString("folder"));assertTrue(state.thread(42).getBoolean("muted"));assertTrue(state.thread(42).getBoolean("pinned"))
 }
}
