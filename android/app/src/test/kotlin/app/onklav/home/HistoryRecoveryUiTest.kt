package app.onklav.home

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HistoryRecoveryUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun context():Context {val base=compose.activity;val root=File(base.cacheDir,"recovery-${java.util.UUID.randomUUID()}").apply{mkdirs()};return object:ContextWrapper(base){override fun getFilesDir()=root;override fun getApplicationContext():Context=this}}
    // Drive offscreen WindowManager layout explicitly, as in the existing Groups harness.
    private fun pump(){repeat(4){
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        compose.runOnUiThread{
            val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose)
            val registry=requireNotNull(env.javaClass.superclass).getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
            val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>
            roots.filterNotNull().forEach{root->
                run { // A new offscreen dialog may need layout even when Compose reports no pending pass.
                    val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View
                    val decor=view.rootView;decor.forceLayout()
                    decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(432,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(936,android.view.View.MeasureSpec.AT_MOST))
                    decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)
                }
            }
        }
    }}
    private fun click(text:String){
        println("Recovery UI button: $text")
        try{compose.waitUntil(15000){pump();runCatching{compose.onNodeWithText(text).assertExists().assertIsEnabled()}.isSuccess}}
        catch(e:Throwable){
            val tree=runCatching{val roots=compose.onAllNodes(isRoot(),useUnmergedTree=true);roots.fetchSemanticsNodes().indices.joinToString("\n"){roots[it].printToString()}}.getOrElse{it.toString()}
            throw AssertionError("Waiting for button '$text'\n$tree",e)
        }
        val n=compose.onNodeWithText(text);if(runCatching{n.assertIsDisplayed()}.isFailure)n.reveal(compose) { pump() }
        n.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()
    }
    private fun show(c:Context,backup:Boolean=false){
        // Finish the real activity's asynchronous SDK startup before replacing
        // its content, as in GroupsFlowHostTest. No production data/API is mocked.
        kotlinx.coroutines.runBlocking{AppSdk.bootstrap(compose.activity)}
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{CompositionLocalProvider(LocalContext provides c){if(backup)BackupSettingsDialog{}else HistoryRecoveryDialog{}}}}};pump()
    }
    @Test(timeout=90000) fun recoveryRouteAndStagedIdentitySaveCancelAndValidation(){
        val c=context();show(c,backup=true);click("Messages and Media Backup");click("Restore settings and recovery");click("Local phone identities")
        compose.onNodeWithTag("restore-local-identities").performTextInput("+1 (555) 010-1234");click("Cancel");assertTrue(HistoryRecovery.configuration(c).isEmpty())
        click("Local phone identities");compose.onNodeWithTag("restore-local-identities").performTextInput("123");click("Save");click("OK");assertTrue(HistoryRecovery.configuration(c).isEmpty())
        compose.onNodeWithTag("restore-local-identities").performTextReplacement("+1 (555) 010-1234\n5550100999");click("Save")
        compose.waitUntil(15000){pump();HistoryRecovery.configuration(c).size==2};assertEquals(listOf("+15550101234","5550100999"),HistoryRecovery.configuration(c));click("Close");click("Restore settings and recovery");click("Local phone identities")
        compose.onNodeWithTag("restore-local-identities").assertTextContains("+15550101234\n5550100999");click("Cancel")
    }
    private fun journal(c:Context,known:Boolean):String {val j=HistoryRestore.Journal(c);val row=j.call("begin",JSONObject().put("kind","sms").put("source","sms:1").put("archive_sha","a".repeat(64)).put("content_sha","b".repeat(64)));val id=row.getString("id");j.step(id,"insert_intent");if(known)j.step(id,"row",JSONObject().put("target","sms:91").put("receipt","c".repeat(64)));return id}
    @Test(timeout=90000) fun unknownInsertReviewStaysPausedWithoutClearOrRetry(){val c=context();val id=journal(c,false);show(c,backup=true);click("Review interrupted restore");compose.waitUntil(15000){pump();runCatching{compose.onNodeWithText("Insert result is unknown. Restore remains paused.").assertExists()}.isSuccess};compose.onNodeWithText("Retry").assertDoesNotExist();compose.onNodeWithText("Clear").assertDoesNotExist();compose.onNodeWithText("Check already removed message…").assertDoesNotExist();assertEquals("uncertain",HistoryRestore.Journal(c).step(id,"get").getString("phase"))}
    @Test(timeout=90000) fun knownIncompleteCleanupRemainsGateClosed(){val c=context();val id=journal(c,true);assertEquals("cleanup_required",HistoryRecovery.overview(c).getJSONArray("pending").getJSONObject(0).getString("phase"));show(c);run { try { compose.waitUntil(15000){pump();runCatching{compose.onNodeWithText("Remove incomplete insert…").assertExists()}.isSuccess} } catch(e:Throwable){val roots=compose.onAllNodes(isRoot(),useUnmergedTree=true);throw AssertionError(roots.fetchSemanticsNodes().indices.joinToString("\n"){roots[it].printToString()},e)} };compose.onNodeWithText("Remove incomplete insert…").reveal(compose) { pump() }.assertIsNotEnabled();assertThrows(Exception::class.java){HistoryRecovery.cleanupChecked(c,id)};assertEquals("cleanup_required",HistoryRestore.Journal(c).step(id,"get").getString("phase"))}
    @Test(timeout=90000) fun compactConfirmationRetainsReplayProtectionAndPendingRecords(){
        val c=context();val id=journal(c,true);val j=HistoryRestore.Journal(c);j.step(id,"commit")
        show(c);click("Compact completed journal records…");click("Cancel")
        assertEquals(1,File(c.filesDir,"history-restore-journal").listFiles()!!.count{it.extension=="json"})
        click("Compact completed journal records…");click("Compact")
        compose.waitUntil(15000){pump();File(c.filesDir,"history-restore-journal/terminal.archive").exists()&&File(c.filesDir,"history-restore-journal").listFiles()!!.none{it.extension=="json"}}
        assertEquals("committed",j.step(id,"get").getString("phase"))
        assertEquals(1,j.call("receipts",JSONObject().put("archive_sha","a".repeat(64))).getJSONArray("items").length())
        assertThrows(Exception::class.java){j.call("begin",JSONObject().put("kind","sms").put("source","sms:1").put("archive_sha","a".repeat(64)).put("content_sha","b".repeat(64)))}
        val pending=j.call("begin",JSONObject().put("kind","sms").put("source","sms:2").put("archive_sha","a".repeat(64)).put("content_sha","b".repeat(64))).getString("id")
        j.step(pending,"insert_intent");HistoryRecovery.overview(c);HistoryRecovery.compact(c)
        assertEquals("uncertain",j.step(pending,"get").getString("phase"))
        assertFalse(MessagingTransport.MMS_READY)
    }
    @Test(timeout=90000) fun savedRecordReopenRouteDoesNotWriteOrRequestAnAutomaticRetry(){
        val c=context();val id="8".repeat(32);val dir=File(c.filesDir,"history-restore-maps").apply{mkdirs()};val map=File(dir,"$id.jsonl")
        map.writeText(JSONObject().put("format","onklav-restore-outcomes").put("version",1).put("archive_sha","a".repeat(64)).put("plan_sha","b".repeat(64)).put("metadata_applied",false).put("phone_revalidated",false).toString()+"\n"+JSONObject().put("kind","message").put("source","sms:1").put("target","sms:91").put("decision","match").toString()+"\n")
        val before=map.readBytes();show(c);click("Restore 88888888 · Review");click("Reopen original backup");compose.onNodeWithText("Choose original backup").assertIsEnabled();compose.onNodeWithText("Retry").assertDoesNotExist();click("Close")
        assertArrayEquals(before,map.readBytes());assertFalse(File(c.filesDir,"messenger.json").exists());assertFalse(MessagingTransport.MMS_READY)
    }
    @Test(timeout=90000) fun finalConfirmationShowsReviewedIdentitiesAndRemainsGateClosed(){
        val c=context();HistoryRecovery.save(c,"+15550101234")
        val comparison=HistoryImport.Comparison(4,2,1,1,2,1,1,1,"b".repeat(64))
        val review=HistoryImport.Review(7,1,3,1024,3,4,1,0,1,2,3,4,"a".repeat(64),comparison)
        val ticket=HistoryConfirmation.prepare(c,android.net.Uri.parse("content://review-fixture/backup"),review);var cancelled=false
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{CompositionLocalProvider(LocalContext provides c){HistoryRestoreConfirmationDialog(ticket,onApply={fail("Production gate must stay closed")}){ticket.cancel();cancelled=true}}}}};pump()
        compose.onNodeWithText("2 New Candidates · 4 Already Present").assertExists();compose.onNodeWithText("+15550101234").assertExists();compose.onNodeWithText("Restore").assertIsNotEnabled()
        click("Cancel");assertTrue(cancelled);assertThrows(Exception::class.java){ticket.consume(c)};assertEquals(0,HistoryRestore.status(c).getInt("pending"))
    }
    @Test(timeout=90000) fun completedRestoreCompactionCancelsThenRetainsReviewAndMetadata(){
        val c=context();val id="6".repeat(32);val archive="a".repeat(64);val plan="b".repeat(64);val post="c".repeat(64)
        val dir=File(c.filesDir,"history-restore-maps").apply{mkdirs()};val map=File(dir,"$id.jsonl")
        val header=JSONObject().put("format","onklav-restore-outcomes").put("version",1).put("archive_sha",archive).put("plan_sha",plan).put("metadata_applied",true).put("phone_revalidated",true).put("post_plan_sha",post).put("flags_imported",1).put("threads_imported",0)
        map.writeText(header.toString()+"\n"+JSONObject().put("kind","message").put("source","sms:1").put("target","sms:91").put("decision","match").toString()+"\n")
        val receipt=JSONObject().put("archive_sha",archive).put("plan_sha",plan).put("post_plan_sha",post).put("flags_imported",1).put("threads_imported",0)
        val state=File(c.filesDir,"messenger.json").apply{writeText(JSONObject().put("history_merges",JSONObject().put(id,receipt)).put("messages",JSONObject().put("sms:91",JSONObject().put("starred",true))).put("drafts",JSONObject().put("70","keep draft")).toString())}
        show(c);click("Compact completed restore records…");click("Cancel");assertTrue(map.exists());assertTrue(JSONObject(state.readText()).getJSONObject("history_merges").has(id))
        click("Compact completed restore records…");click("Compact")
        compose.waitUntil(15000){pump();!map.exists()}
        val saved=JSONObject(state.readText());assertEquals(0,saved.getJSONObject("history_merges").length());assertTrue(saved.getJSONObject("messages").getJSONObject("sms:91").getBoolean("starred"));assertEquals("keep draft",saved.getJSONObject("drafts").getString("70"))
        click("Restore 66666666 · Saved");compose.onNodeWithText("Archived completion summary. Duplicate-restore protection is retained.").assertExists();compose.onNodeWithText("Finish saved record").assertDoesNotExist()
        assertTrue(HistoryRecovery.detail(c,id).getBoolean("compacted"));assertFalse(MessagingTransport.MMS_READY)
    }
    @Test(timeout=90000) fun finishSavedRecordOnlyAcknowledgesExistingMetadata(){
        val c=context();val id="1".repeat(32);val archive="a".repeat(64);val plan="b".repeat(64)
        val dir=File(c.filesDir,"history-restore-maps").apply{mkdirs()};val map=File(dir,"$id.jsonl")
        map.writeText(JSONObject().put("format","onklav-restore-outcomes").put("version",1).put("archive_sha",archive).put("plan_sha",plan).put("metadata_applied",false).put("phone_revalidated",false).toString()+"\n"+JSONObject().put("kind","message").put("source","sms:1").put("target","sms:91").put("decision","inserted").toString()+"\n")
        val receipt=JSONObject().put("archive_sha",archive).put("plan_sha",plan).put("post_plan_sha","c".repeat(64)).put("flags_imported",1).put("threads_imported",0)
        val state=File(c.filesDir,"messenger.json").apply{writeText(JSONObject().put("history_merges",JSONObject().put(id,receipt)).put("messages",JSONObject().put("sms:91",JSONObject().put("starred",false))).toString())};val before=state.readBytes()
        show(c);click("Restore 11111111 · Saved");click("Finish saved record")
        compose.waitUntil(15000){pump();!HistoryRecovery.detail(c,id).getBoolean("ack_pending")};assertArrayEquals(before,state.readBytes());assertTrue(JSONObject(map.readLines().first()).getBoolean("metadata_applied"));compose.onNodeWithText("Finish saved record").assertDoesNotExist()
    }
    @Test(timeout=90000) fun changedInsertionReviewExplainsAbsenceCheckWithoutEnablingPhoneAccess(){
        val c=context();val id=journal(c,true);HistoryRestore.Journal(c).step(id,"review");val before=HistoryRestore.Journal(c).step(id,"get").toString()
        show(c);click("Check already removed message…");compose.onNodeWithText("Check").assertIsNotEnabled()
        compose.onNodeWithText("Phone checks are unavailable until restore verification is complete.").assertExists();click("Cancel")
        assertEquals(before,HistoryRestore.Journal(c).step(id,"get").toString());assertFalse(MessagingTransport.MMS_READY)
    }
    @Test(timeout=90000) fun partialSavedRecordOffersExplicitReopenAndShowsRemainingCount(){
        val c=context();val id="7".repeat(32);val dir=File(c.filesDir,"history-restore-maps").apply{mkdirs()};val map=File(dir,"$id.jsonl")
        map.writeText(JSONObject().put("format","onklav-restore-outcomes").put("version",1).put("archive_sha","a".repeat(64)).put("plan_sha","b".repeat(64)).put("metadata_applied",false).put("phone_revalidated",false).toString()+"\n"+JSONObject().put("kind","message").put("source","sms:1").put("target",JSONObject.NULL).put("decision","pending").toString()+"\n")
        val before=map.readBytes();show(c);click("Restore 77777777 · Review");click("Reopen original backup")
        compose.onNodeWithText("1 remaining messages need review before insertion.",substring=true).assertExists();compose.onNodeWithText("Choose original backup").assertIsEnabled();click("Close");assertArrayEquals(before,map.readBytes());assertFalse(MessagingTransport.MMS_READY)
    }
}
