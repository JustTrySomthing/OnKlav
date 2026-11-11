package app.onklav.home

import android.Manifest
import android.app.Application
import android.content.*
import android.content.pm.ProviderInfo
import android.database.*
import android.net.Uri
import androidx.lifecycle.*
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class PagingAndReactionsTest {
 @Test fun newestRefreshPreservesOlderRowsAndUpdatesChangedStatusWithoutDuplicates(){
  val old=(1L..40L).map{Message(it,"Message $it",it,false)}
  val fresh=(25L..44L).map{Message(it,"Updated $it",it,false,status="Delivered")}
  val merged=mergeLatestMessages(old,fresh);assertEquals(44,merged.size);assertEquals("Message 1",merged.first().text);assertEquals("Updated 25",merged[24].text);assertEquals(44,merged.map{it.key}.distinct().size)
 }
 @Test fun tabViewModelRetainsLoadedRowsAndWindows(){
  val store=ViewModelStore();val provider=ViewModelProvider(store,ViewModelProvider.NewInstanceFactory())
  val first=provider["messages",MessengerMemory::class.java];first.rows.value=listOf(Conversation(1,"15550100101","Friend","Hi",1,false));first.keep(1,listOf(Message(1,"Cached",1,false)))
  val next=ViewModelProvider(store,ViewModelProvider.NewInstanceFactory())["messages",MessengerMemory::class.java]
  assertSame(first,next);assertEquals("Friend",next.rows.value.single().name);assertEquals("Cached",next.windows[1]!!.single().text);store.clear()
 }
 @Test fun viewerKeepsItsSnapshotWhenGalleryRefreshesOrShrinks(){
  val source=mutableListOf(ChatMedia(Message(1,"",1,false),MessagePart("content://media/a","image/jpeg","A")),ChatMedia(Message(2,"",2,false),MessagePart("content://media/b","image/gif","B")))
  val viewer=mediaViewerSnapshot(source);source.clear();assertEquals(2,viewer.size);assertEquals("content://media/b",viewer[1].part.uri)
 }
 @Test fun knownAndArbitraryEmojiReactionsAndRemovalParseWithoutEatingOrdinaryMessages(){
  assertEquals("🥰",textReaction("Reacted 🥰 to \"Hello\"")!!.emoji);assertTrue(textReaction("Removed 🥰 from “Hello”")!!.remove)
  assertEquals("❤️",textReaction("Loved \"Hello\"")!!.emoji);assertNull(textReaction("An ordinary \"message\""))
 }
 @Test fun reactionsOnBothSidesBecomeCornerBadgesAndTransportRowsAreHidden(){
  val peer=Message(1,"Hello",1,false);val own=Message(2,"My reply",2,true)
  val mine=Message(3,"Loved \"Hello\"",3,true);val theirs=Message(4,"Reacted 🥰 to \"My reply\"",4,false)
  val projected=projectReactions(listOf(peer,own,mine,theirs),JSONObject());assertEquals(listOf(peer,own),projected.messages);assertEquals(listOf("❤️"),projected.emojis[peer.key]);assertEquals(listOf("🥰"),projected.emojis[own.key])
  val removed=projectReactions(listOf(peer,own,mine,theirs,Message(5,"Removed 🥰 from \"My reply\"",5,false)),JSONObject());assertTrue(removed.emojis[own.key].isNullOrEmpty())
 }
 @Test fun carrierTapbacksMatchMultilineMessagesAndCanBeRemoved(){
  val original=Message(1,"Here is a \"quote\"\r\nSee you soon",1,true)
  val liked=Message(2,"Liked “Here is a \"quote\"\nSee you soon”",2,false)
  val changed=Message(3,"Reacted with 🥰 to “Here is a \"quote\"\nSee you soon”",3,false)
  val remove=Message(4,"Removed 🥰 from “Here is a \"quote\"\nSee you soon”",4,false)
  val reactions=projectReactions(listOf(original,liked,changed),JSONObject())
  assertEquals(listOf(original),reactions.messages)
  assertEquals(ReactionMark("🥰",changed.key),reactions.marks[original.key]?.get("peer"))
  assertEquals(listOf(original),projectReactions(listOf(original,liked,changed,remove),JSONObject()).messages)
  assertTrue(projectReactions(listOf(original,liked,changed,remove),JSONObject()).emojis.isEmpty())
  assertEquals(TextReaction("👍","text",true),textReaction(reactionSms("👍","text",true)))
  assertEquals(TextReaction("‼️","text"),textReaction("Emphasized “text”"))
  assertEquals(TextReaction("🥰","text"),textReaction("🥰 to \"text\""))
  assertNull(textReaction("Reacted strongly to “text”"))
 }
 @Test fun groupQuotesAndStructuredReactionsShareTheBubbleWithoutDuplicatingTheSender(){
  val post=Message(1,"Group post",1,true)
  val carrierReaction=Message(2,"Liked “Group post”",2,false,sender="+1 (555) 555-0101")
  val group=JSONObject().put("keys",JSONObject().put(post.key,"post"))
   .put("reactions",JSONObject().put("post",JSONObject().put("You","❤️")))
  val state=JSONObject().put("messages",JSONObject().put(post.key,JSONObject().put("reaction","❤️")))
  val view=projectReactions(listOf(post,carrierReaction),state,group)
  assertEquals(listOf(post),view.messages)
  assertEquals(setOf("you","5555550101"),view.marks.getValue(post.key).keys)
  assertEquals(setOf("❤️","👍"),view.emojis.getValue(post.key).toSet())
  val failed=carrierReaction.copy(id=3,key="sms:3",sent=true,status="Failed")
  assertTrue(projectReactions(listOf(post,failed),state,group).messages.contains(failed))
 }
 @Test fun conversationHeaderResolvesTheStructuredGivenNameFromAnAddress()=runBlocking {
  val app=ApplicationProvider.getApplicationContext<Application>()
  Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
  val contacts=object:ContentProvider(){
   override fun onCreate()=true
   override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor? = when(uri.path){
    "/phone_lookup/+15555550101" -> MatrixCursor(arrayOf("contact_id")).apply{addRow(arrayOf(42))}
    "/data" -> MatrixCursor(arrayOf("data2")).apply{assertEquals("42",args?.first());addRow(arrayOf("Mary Jane"))}
    else -> null
   }
   override fun getType(uri:Uri):String?=null
   override fun insert(uri:Uri,values:ContentValues?):Uri?=null
   override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=0
   override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=0
  }
  contacts.attachInfo(app,ProviderInfo().apply{authority="com.android.contacts"})
  ShadowContentResolver.registerProviderInternal("com.android.contacts",contacts)
  val repo=MessageRepository(app)
  assertEquals("Mary Jane",repo.contactGivenName("+15555550101"))
  assertNull(repo.contactGivenName("+15555550102"))
  assertEquals("Jane",firstNameFromDisplayName("Smith, Jane","+15555550101"))
 }
 class Media:ContentProvider(){
  var parts=0;var lastArgs=listOf<String>()
  override fun onCreate()=true
  override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor {
   if(uri.path.orEmpty().endsWith("/part")){parts++;return MatrixCursor(arrayOf("_id","ct","text","name","cl")).apply{addRow(arrayOf(parts,"image/jpeg",null,"Photo.jpg","Photo.jpg"))}}
   lastArgs=args?.toList().orEmpty();val start=if(args!=null&&args.size>1)args.last().toInt()-1 else 1000
   return MatrixCursor(arrayOf("_id","date","msg_box")).apply{for(id in start downTo 1)addRow(arrayOf(id,1000,1))}
  }
  override fun getType(uri:Uri):String?=null
  override fun insert(uri:Uri,values:ContentValues?):Uri?=null
  override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=0
  override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=0
 }
 @Test fun galleryReadsOneBoundedPageAndUsesIdToAdvanceThroughEqualTimestamps()=runBlocking {
  val c=ApplicationProvider.getApplicationContext<Application>();Shadows.shadowOf(c).grantPermissions(Manifest.permission.READ_SMS)
  val media=Media();media.attachInfo(c,ProviderInfo().apply{authority="mms"});ShadowContentResolver.registerProviderInternal("mms",media)
  val repo=MessageRepository(c);val first=repo.mediaPage(17,JSONObject());assertEquals(24,first.messages.size);assertEquals(24,media.parts);assertTrue(first.more);assertEquals(977L,first.next!!.id)
  val second=repo.mediaPage(17,JSONObject(),first.next);assertEquals(976L,second.messages.first().id);assertEquals(48,media.parts);assertEquals(listOf("17","1000","1000","977"),media.lastArgs)
 }
}
