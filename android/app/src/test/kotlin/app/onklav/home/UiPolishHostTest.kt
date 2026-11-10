package app.onklav.home

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.*
import org.junit.Assert.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class UiPolishHostTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun layout(){
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        compose.runOnUiThread{
            val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose)
            val registry=requireNotNull(env.javaClass.superclass).getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
            val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>
            roots.filterNotNull().forEach{root->
                val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View;val decor=view.rootView;decor.forceLayout()
                decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(432,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(936,android.view.View.MeasureSpec.AT_MOST))
                decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)
            }
        }
    }
    private fun pump(){repeat(4){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);layout()}}
    private fun click(tag:String){compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()}
    @Test(timeout=90000) fun pageSlidesFromRightAndRetainsContentUntilExitCompletes(){
        kotlinx.coroutines.runBlocking{AppSdk.bootstrap(compose.activity)}
        compose.mainClock.autoAdvance=false;var route by mutableStateOf<String?>(null)
        val positions=mutableListOf<Float>()
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{Box(Modifier.fillMaxSize()){RightSlideRoute(route){Text(it,Modifier.testTag("moving-page").onGloballyPositioned{p->positions.add(p.positionInRoot().x)})}}}}};pump()
        compose.runOnUiThread{route="Retained content";androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()}
        compose.waitUntil(5000){pump();compose.onAllNodesWithTag("moving-page").fetchSemanticsNodes().isNotEmpty()};pump()
        assertTrue("Entry must contain intermediate right-side positions: $positions",positions.any{it>1f&&it<431f})
        assertEquals(0f,compose.onNodeWithTag("moving-page").getUnclippedBoundsInRoot().left.value,1f)
        positions.clear()
        compose.runOnUiThread{route=null;androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()};pump();pump()
        assertTrue("Exit must retain content at intermediate positions: $positions",positions.any{it>1f&&it<431f})
        compose.onNodeWithTag("moving-page").assertDoesNotExist()
    }
    @Test(timeout=90000) fun keypadAddsDigitsFormatsAndBackspacesWithoutChangingDialableTarget(){
        kotlinx.coroutines.runBlocking{AppSdk.bootstrap(compose.activity)}
        compose.mainClock.autoAdvance=false;var number by mutableStateOf("");var chosen="";var shown by mutableStateOf(true)
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{if(shown)NumberEntryDialog(number,{number=it},{shown=false},{chosen=number;shown=false})}}};pump()
        for(d in "6155550101")click("dialpad-$d")
        assertEquals("6155550101",number);assertEquals("(615) 555-0101",displayPhone(number))
        click("dialpad-⌫");assertEquals("615555010",number);click("dialpad-1")
        compose.onNodeWithText("Start Message").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}
        compose.mainClock.advanceTimeBy(96);layout();assertEquals("",chosen)
        pump();assertEquals("6155550101",chosen);compose.onNodeWithTag("dialpad-number").assertDoesNotExist()
    }
    @Test(timeout=90000) fun mailboxTabsSelectAndExposeBothTrashActions(){
        kotlinx.coroutines.runBlocking{AppSdk.bootstrap(compose.activity)}
        compose.mainClock.autoAdvance=false;var folder by mutableStateOf("Inbox");var restore=0;var empty=0
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{Column{MailboxControls(folder,{folder=it},"",{},false,{});if(folder=="Trash")TrashControls(true,{restore++},{empty++})}}}};pump()
        click("folder-Archive");compose.onNodeWithTag("folder-Archive").assertIsSelected();assertEquals("Archive",folder)
        click("folder-Trash");compose.onNodeWithTag("folder-Trash").assertIsSelected();click("trash-Restore all");click("trash-Empty trash");assertEquals(1,restore);assertEquals(1,empty)
        click("folder-Inbox");compose.onNodeWithTag("folder-Inbox").assertIsSelected();compose.onNodeWithTag("trash-Empty trash").assertDoesNotExist()
    }
    class MailFixture:android.content.ContentProvider(){
        override fun onCreate()=true
        override fun query(uri:android.net.Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):android.database.Cursor {
            if(uri.path.orEmpty().startsWith("/canonical-address"))return android.database.MatrixCursor(arrayOf("address")).apply{addRow(arrayOf("+15550000"+uri.lastPathSegment))}
            return android.database.MatrixCursor(arrayOf("_id","date","snippet","read","recipient_ids","message_count")).apply{
                for(id in 101..103)if(selection!="_id=?"||args?.firstOrNull()==id.toString())addRow(arrayOf(id,id.toLong(),listOf("inbox fixture","archive fixture","trash fixture")[id-101],1,id.toString(),1))
            }
        }
        override fun getType(uri:android.net.Uri):String?=null
        override fun insert(uri:android.net.Uri,values:android.content.ContentValues?):android.net.Uri?=null
        override fun update(uri:android.net.Uri,values:android.content.ContentValues?,selection:String?,args:Array<out String>?)=0
        override fun delete(uri:android.net.Uri,selection:String?,args:Array<out String>?)=throw AssertionError("Phone deletion is forbidden in this fixture")
    }
    @Test(timeout=90000) fun messageFoldersFilterProviderRowsAndRestoreAllUpdatesRustState(){
        val context=compose.activity
        kotlinx.coroutines.runBlocking{AppSdk.bootstrap(context)}
        org.robolectric.Shadows.shadowOf(context.applicationContext as android.app.Application).grantPermissions(android.Manifest.permission.READ_SMS)
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("mms-sms",MailFixture())
        val store=ChatStore(context)
        for((id,folder)in listOf(101 to "Inbox",102 to "Archive",103 to "Trash"))store.requestSync(org.json.JSONObject().put("action","thread").put("key",id.toString()).put("folder",folder))
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{Box(Modifier.fillMaxSize()){MessengerPane({})}}}};pump()
        compose.waitUntil(10000){pump();compose.onAllNodesWithText("inbox fixture").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("archive fixture").assertDoesNotExist()
        click("folder-Archive");compose.waitUntil(10000){pump();compose.onAllNodesWithText("archive fixture").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("archive fixture").assertExists();compose.onNodeWithText("inbox fixture").assertDoesNotExist()
        click("folder-Trash");compose.waitUntil(10000){pump();compose.onAllNodesWithText("trash fixture").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("trash fixture").assertExists();click("trash-Restore all")
        compose.waitUntil(10000){pump();store.requestSync(org.json.JSONObject().put("action","snapshot")).thread(103).optString("folder")=="Inbox"}
        compose.waitUntil(10000){pump();compose.onAllNodesWithText("trash fixture").fetchSemanticsNodes().isEmpty()};click("folder-Inbox");compose.waitUntil(10000){pump();compose.onAllNodesWithText("trash fixture").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("trash fixture").assertExists()
        assertFalse(MessagingTransport.role(context))
        assertThrows(Exception::class.java){kotlinx.coroutines.runBlocking{MessageRepository(context).emptyTrash(listOf(103))}}
    }
    @Test(timeout=60000) fun messageLongPressUsesReadUnreadDraftAndSaveCancel() {
        val context=compose.activity;kotlinx.coroutines.runBlocking{AppSdk.bootstrap(context)}
        org.robolectric.Shadows.shadowOf(context.applicationContext as android.app.Application).grantPermissions(android.Manifest.permission.READ_SMS)
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("mms-sms",MailFixture())
        val store=ChatStore(context)
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{MessengerPane({})}}};pump()
        compose.waitUntil(10000){pump();compose.onAllNodesWithText("inbox fixture").fetchSemanticsNodes().isNotEmpty()}
        fun open(){compose.onNodeWithText("inbox fixture").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick){it()};pump()}
        fun press(text:String){compose.onNodeWithText(text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()}
        fun unread()=store.requestSync(org.json.JSONObject().put("action","snapshot")).thread(101).optBoolean("unread")
        open();compose.onNodeWithTag("conversation-unread-switch").assertIsOff()
        compose.onNodeWithText("Move to Archive").assertDoesNotExist()
        click("conversation-unread-switch");press("Cancel");assertFalse(unread())
        open();compose.onNodeWithTag("conversation-unread-switch").assertIsOff()
        click("conversation-unread-switch");press("Save")
        compose.waitUntil(10000){pump();unread() && compose.onAllNodesWithTag("conversation-read-status").fetchSemanticsNodes().isEmpty()}
        open();compose.onNodeWithTag("conversation-unread-switch").assertIsOn()
        click("conversation-unread-switch");press("Save")
        compose.waitUntil(10000){pump();!unread() && compose.onAllNodesWithTag("conversation-read-status").fetchSemanticsNodes().isEmpty()}
    }
    class ContactFixture:android.content.ContentProvider(){
        var unavailable=false
        var firstName="Alex Example"
        override fun onCreate()=true
        override fun query(uri:android.net.Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):android.database.Cursor? {
            if(unavailable)return null
            return if(uri.path.orEmpty().contains("phones"))android.database.MatrixCursor(arrayOf("contact_id","data1")).apply{
                addRow(arrayOf(1,"6155550101"));addRow(arrayOf(1,"6155550102"));addRow(arrayOf(3,"6155550103"))
            }else android.database.MatrixCursor(arrayOf("_id","display_name","photo_thumb_uri")).apply{
                addRow(arrayOf(1,firstName,null));addRow(arrayOf(2,"Bob Example",null));addRow(arrayOf(3,"Cara Example",null))
            }
        }
        override fun getType(uri:android.net.Uri):String?=null
        override fun insert(uri:android.net.Uri,values:android.content.ContentValues?):android.net.Uri?=null
        override fun update(uri:android.net.Uri,values:android.content.ContentValues?,selection:String?,args:Array<out String>?)=0
        override fun delete(uri:android.net.Uri,selection:String?,args:Array<out String>?)=0
    }
    @Test(timeout=90000) fun contactsGroupLettersShowNoNumberAndChooseMessageOrWalkieRecipient(){
        val context=compose.activity;kotlinx.coroutines.runBlocking{AppSdk.bootstrap(context)}
        org.robolectric.Shadows.shadowOf(context.applicationContext as android.app.Application).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("com.android.contacts",ContactFixture())
        compose.mainClock.autoAdvance=false;var message="";var walkie=""
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{ContactsScreen({},walkie={walkie=it}){message=it}}}};pump()
        compose.waitUntil(10000){pump();compose.onAllNodesWithText("Alex Example").fetchSemanticsNodes().isNotEmpty()}
        listOf("A","B","C").forEach{compose.onNode(hasText(it) and SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Heading,Unit)).assertExists()}
        listOf("2 phone numbers","No phone number","(615) 555-0103").forEach{compose.onNodeWithText(it).assertExists()}
        compose.onNodeWithContentDescription("Message Bob Example").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Message Alex Example").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()
        compose.onNodeWithText("(615) 555-0102").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump();assertEquals("6155550102",message)
        compose.onNodeWithContentDescription("Add Contact").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()
        val insert=org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_INSERT,insert.action)
        assertEquals(android.provider.ContactsContract.Contacts.CONTENT_URI,insert.data)
        compose.onNodeWithContentDescription("Walkie Talkie Cara Example").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump();assertEquals("6155550103",walkie)
    }
    @Test(timeout=90000) fun contactsUnavailableCanRetryAndSearchHasAnExplicitEmptyResult(){
        val c=compose.activity;kotlinx.coroutines.runBlocking{AppSdk.bootstrap(c)}
        org.robolectric.Shadows.shadowOf(c.applicationContext as android.app.Application).grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val provider=ContactFixture().apply{unavailable=true}
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("com.android.contacts",provider)
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread{c.setContent{OnKlavTheme{ContactsScreen({}){}}}};pump()
        compose.waitUntil(10000){pump();compose.onAllNodesWithText("Try Again").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("No Contacts to Display").assertDoesNotExist()
        provider.unavailable=false
        compose.onNodeWithText("Try Again").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}
        compose.waitUntil(10000){pump();compose.onAllNodesWithText("Alex Example").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("contacts-search").performTextInput("Nobody Found");pump()
        compose.onNodeWithText("No Matching Contacts").assertExists()
        compose.onNodeWithText("Alex Example").assertDoesNotExist()
    }
    @Test(timeout=90000) fun contactsRefreshAfterEditorReturnAndHideRevokedContacts(){
        val c=compose.activity;kotlinx.coroutines.runBlocking{AppSdk.bootstrap(c)}
        val permissions=org.robolectric.Shadows.shadowOf(c.applicationContext as android.app.Application)
        permissions.grantPermissions(android.Manifest.permission.READ_CONTACTS)
        val provider=ContactFixture()
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("com.android.contacts",provider)
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread{c.setContent{OnKlavTheme{ContactsScreen({}){}}}};pump()
        compose.waitUntil(10000){pump();compose.onAllNodesWithText("Alex Example").fetchSemanticsNodes().isNotEmpty()}
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        provider.firstName="Updated Person"
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitUntil(10000){pump();compose.onAllNodesWithText("Updated Person").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Alex Example").assertDoesNotExist()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        permissions.denyPermissions(android.Manifest.permission.READ_CONTACTS)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);pump()
        compose.onNodeWithText("Allow Contacts").assertExists()
        compose.onNodeWithText("Updated Person").assertDoesNotExist()
    }

    @Test(timeout=60000) fun sameConversationIncomingRefreshKeepsComposerEditable(){
        val context=compose.activity;kotlinx.coroutines.runBlocking{AppSdk.bootstrap(context)}
        org.robolectric.Shadows.shadowOf(context.applicationContext as android.app.Application).grantPermissions(android.Manifest.permission.READ_SMS)
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("mms-sms",MailFixture())
        compose.mainClock.autoAdvance=false;var intent by mutableIntStateOf(0)
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{MessengerPane({},incomingAddress="15550100101",intentEpoch=intent)}}};pump()
        compose.waitUntil(15000){pump();runCatching{compose.onNodeWithTag("message-composer").assertIsEnabled()}.isSuccess}
        compose.onNodeWithTag("message-composer").performTextInput("Draft before incoming");pump()
        compose.runOnUiThread{intent++};pump()
        compose.onNodeWithTag("message-composer").assertIsEnabled();compose.onNodeWithTag("message-composer").performTextInput(" and after")
        pump();compose.onNodeWithTag("message-composer").assertTextContains("Draft before incoming and after")
    }

    class PagingFixture:android.content.ContentProvider(){
        var queries=0
        override fun onCreate()=true
        override fun query(uri:android.net.Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):android.database.Cursor{
            if(uri.path.orEmpty().startsWith("/canonical-address"))return android.database.MatrixCursor(arrayOf("address")).apply{addRow(arrayOf("+15550000"+uri.lastPathSegment))}
            queries++;val before=args?.firstOrNull()?.toLongOrNull()?:Long.MAX_VALUE
            return android.database.MatrixCursor(arrayOf("_id","date","snippet","read","recipient_ids","message_count")).apply{for(id in 242 downTo 200)if(id*10L<before)addRow(arrayOf(id,id*10L,"paging $id",1,id.toString(),1))}
        }
        override fun getType(uri:android.net.Uri):String?=null
        override fun insert(uri:android.net.Uri,values:android.content.ContentValues?):android.net.Uri?=null
        override fun update(uri:android.net.Uri,values:android.content.ContentValues?,selection:String?,args:Array<out String>?)=0
        override fun delete(uri:android.net.Uri,selection:String?,args:Array<out String>?)=0
    }
    @Test(timeout=60000) fun conversationHistoryLoadsOnScrollWithoutLoadOlderButton(){
        val context=compose.activity;kotlinx.coroutines.runBlocking{AppSdk.bootstrap(context)}
        org.robolectric.Shadows.shadowOf(context.applicationContext as android.app.Application).grantPermissions(android.Manifest.permission.READ_SMS)
        val provider=PagingFixture();org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("mms-sms",provider)
        compose.mainClock.autoAdvance=false;compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{MessengerPane({})}}};pump()
        compose.waitUntil(15000){pump();compose.onAllNodesWithText("paging 242").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Load older conversations").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(38);pump()
        val memory=androidx.lifecycle.ViewModelProvider(compose.activity)["messenger-inbox",MessengerMemory::class.java]
        compose.waitUntil(15000){pump();provider.queries>=2&&memory.rows.value.size>=43}
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(42);pump();compose.onNodeWithText("paging 200").assertIsDisplayed()
        val count=provider.queries;pump();assertEquals(count,provider.queries)
    }

}
