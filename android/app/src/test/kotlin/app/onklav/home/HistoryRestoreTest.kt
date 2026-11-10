package app.onklav.home

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HistoryRestoreTest {
    private fun context():Context {
        val base=ApplicationProvider.getApplicationContext<Context>()
        val root=File(base.cacheDir,"restore-${java.util.UUID.randomUUID()}").apply{mkdirs()}
        return object:ContextWrapper(base){override fun getFilesDir()=root}
    }
    private fun sms()=HistoryRestore.Input("sms:1","a".repeat(64),"sms",mapOf("date" to "1700000000000","type" to "1","address" to "+15550101","body" to "Saved message"),setOf("+15550101"))
    private fun mms()=HistoryRestore.Input("mms:2","a".repeat(64),"mms",mapOf("date" to "1700000000","msg_box" to "1","m_type" to "132"),setOf("+15550101"),listOf(mapOf("address" to "+15550101","type" to "137")),listOf(HistoryRestore.Part(mapOf("seq" to "0","ct" to "text/plain","text" to "Media caption"))))
    private class Death:Error("Simulated process interruption")
    private class Fake:HistoryRestore.Provider {
        val rows=mutableSetOf("sms:7","mms:8");val children=mutableSetOf<String>();val deleted=mutableListOf<String>()
        var allowed=true;var failInsert=false;var crashInsert=false;var failComplete=false;var crashComplete=false
        var checks=0;var reads=0;var afterRemoved:(()->Unit)?=null
        var revoke=false;var changed=false;var goodReadback=true;var inserts=0;var failDelete=false;var afterParent:(()->Unit)?=null
        override fun checkAccess(){checks++;check(allowed){"Permission lost"}}
        override fun parentProof(input:HistoryRestore.Input)="c".repeat(64)
        override fun insert(input:HistoryRestore.Input):String {
            inserts++;val target="${input.kind}:${90+inserts}";rows.add(target)
            if(crashInsert)throw Death();if(failInsert)error("Lost insert acknowledgement");return target
        }
        override fun complete(input:HistoryRestore.Input,receipt:HistoryRestore.Receipt,check:()->Unit){
            check();if(input.kind=="mms")children.add(receipt.target)
            afterParent?.invoke()
            if(crashComplete)throw Death()
            if(revoke){allowed=false;error("Permission lost after insert")}
            if(failComplete)error("Child write failed")
        }
        override fun verify(input:HistoryRestore.Input,receipt:HistoryRestore.Receipt,check:()->Unit):Boolean {check();return goodReadback}
        override fun ownership(receipt:HistoryRestore.Receipt):HistoryRestore.Ownership {reads++;return when {receipt.target !in rows->HistoryRestore.Ownership.ABSENT;changed->HistoryRestore.Ownership.CHANGED;else->HistoryRestore.Ownership.OWNED}}
        override fun delete(receipt:HistoryRestore.Receipt){if(failDelete)error("Cleanup provider failed");deleted.add(receipt.target);rows.remove(receipt.target);children.remove(receipt.target)}
        override fun removed(receipt:HistoryRestore.Receipt):Boolean {reads++;val absent=receipt.target !in rows&&receipt.target !in children;afterRemoved?.invoke();return absent}
    }
    private fun attempt(c:Context,input:HistoryRestore.Input,p:Fake)=HistoryRestore.execute(c,input,p,HistoryBackup.Stop())
    private fun fails(action:()->Unit){try{action();fail("Expected failure")}catch(_:Exception){}}
    private fun pending(c:Context):JSONObject=HistoryRestore.Journal(c).call("pending").getJSONArray("items").getJSONObject(0)
    @Test fun successfulMessagesRetainActualIdsAcrossRecoveryAndBlockReplay(){
        val c=context();val p=Fake();val first=attempt(c,sms(),p);val second=attempt(c,mms(),p)
        assertEquals("sms:91",first.getString("target"));assertEquals("mms:92",second.getString("target"))
        assertEquals(2,HistoryRestore.recoverState(c).getInt("committed"));assertEquals(0,HistoryRestore.status(c).getInt("pending"))
        fails{attempt(c,sms(),p)};assertEquals(2,p.inserts)
        assertEquals("committed",HistoryRestore.cleanup(c,first.getString("id"),p).getString("phase"))
        assertTrue(p.deleted.isEmpty());assertTrue(p.rows.containsAll(listOf("sms:7","mms:8","sms:91","mms:92")))
    }
    @Test fun incompleteMmsDeletesOnlyNewParentAndChildren(){
        val c=context();val p=Fake().apply{failComplete=true};fails{attempt(c,mms(),p)}
        assertEquals(listOf("mms:91"),p.deleted);assertEquals(setOf("sms:7","mms:8"),p.rows);assertTrue(p.children.isEmpty())
        assertEquals(1,HistoryRestore.status(c).getInt("rolled_back"))
    }
    @Test fun lostInsertAcknowledgementNeverRetriesOrGuessesWhichRowToDelete(){
        val c=context();val p=Fake().apply{failInsert=true};fails{attempt(c,sms(),p)}
        HistoryRestore.recoverState(c);val row=pending(c);assertEquals("uncertain",row.getString("phase"))
        assertEquals("uncertain",HistoryRestore.cleanup(c,row.getString("id"),p).getString("phase"))
        fails{attempt(c,mms(),p)};assertEquals(1,p.inserts);assertTrue("sms:91" in p.rows);assertTrue(p.deleted.isEmpty())
    }
    @Test fun processInterruptionAfterParentReceiptCanBeCleanedAfterRestart(){
        val c=context();val p=Fake().apply{crashComplete=true}
        try{attempt(c,mms(),p);fail("Expected interruption")}catch(_:Death){}
        assertEquals("completing",pending(c).getString("phase"));HistoryRestore.recoverState(c)
        val row=pending(c);assertEquals("cleanup_required",row.getString("phase"))
        assertEquals("rolled_back",HistoryRestore.cleanup(c,row.getString("id"),p).getString("phase"));assertEquals(listOf("mms:91"),p.deleted)
    }
    @Test fun processInterruptionBeforeReceiptStaysUncertain(){
        val c=context();val p=Fake().apply{crashInsert=true}
        try{attempt(c,sms(),p);fail("Expected interruption")}catch(_:Death){}
        assertEquals("inserting",pending(c).getString("phase"));HistoryRestore.recoverState(c)
        assertEquals("uncertain",pending(c).getString("phase"));assertTrue(p.deleted.isEmpty());fails{attempt(c,sms(),p)}
    }
    @Test fun permissionLossRetainsReceiptUntilExplicitCleanup(){
        val c=context();val p=Fake().apply{revoke=true};fails{attempt(c,mms(),p)}
        val id=pending(c).getString("id");assertTrue(p.deleted.isEmpty());assertTrue("mms:91" in p.rows)
        fails{HistoryRestore.cleanup(c,id,p)};p.allowed=true
        assertEquals("rolled_back",HistoryRestore.cleanup(c,id,p).getString("phase"));assertEquals(listOf("mms:91"),p.deleted)
    }
    @Test fun changedOwnershipOrFailedReadbackCannotCommit(){
        val c=context();val p=Fake().apply{changed=true;goodReadback=false};fails{attempt(c,sms(),p)}
        assertEquals("needs_review",pending(c).getString("phase"));assertTrue(p.deleted.isEmpty());assertTrue("sms:91" in p.rows)
        assertEquals(0,HistoryRestore.status(c).getInt("committed"));fails{attempt(c,mms(),p)}
    }
    @Test fun unsafeInputsAndClosedProductionGateHaveNoInsertEffects(){
        val c=context();val p=Fake()
        fails{attempt(c,sms().copy(fields=sms().fields+mapOf("_id" to "77")),p)}
        fails{attempt(c,sms().copy(fields=sms().fields+mapOf("type" to "4")),p)}
        val stop=HistoryBackup.Stop();stop.cancel();fails{HistoryRestore.execute(c,sms(),p,stop)}
        assertFalse(MessagingTransport.MMS_READY);fails{HistoryRestore.insertChecked(c,sms(),HistoryBackup.Stop())}
        assertEquals(0,p.inserts);assertEquals(0,HistoryRestore.status(c).getInt("pending"))
    }
    @Test fun cancellationDuringChildrenRollsBackButCleanupFailureBlocksContinuation(){
        val c=context();val stop=HistoryBackup.Stop();val p=Fake().apply{afterParent={stop.cancel()};failDelete=true}
        fails{HistoryRestore.execute(c,mms(),p,stop)}
        assertEquals("cleaning",pending(c).getString("phase"));assertTrue("mms:91" in p.rows)
        fails{attempt(c,sms(),p)};assertEquals(1,p.inserts)
        HistoryRestore.recoverState(c);p.failDelete=false
        assertEquals("rolled_back",HistoryRestore.cleanup(c,pending(c).getString("id"),p).getString("phase"))
        assertEquals(setOf("sms:7","mms:8"),p.rows);assertTrue(p.children.isEmpty())
    }

    private fun needsReview(c:Context,p:Fake,input:HistoryRestore.Input=mms()):JSONObject {
        p.changed=true;p.goodReadback=false;fails{attempt(c,input,p)}
        val row=pending(c);assertEquals("needs_review",row.getString("phase"));return row
    }
    @Test fun explicitAbsentReviewRetainsEvidenceAndDoesNotRetryOrDelete(){
        val c=context();val p=Fake();val row=needsReview(c,p);val id=row.getString("id");p.rows.remove("mms:91");p.children.clear()
        val before=HistoryRestore.Journal(c).step(id,"get");val result=HistoryRestore.confirmRemoved(c,id,p,HistoryBackup.Stop())
        assertEquals("rolled_back",result.getString("phase"));assertEquals("verified_absent",result.getString("resolution"))
        for(key in listOf("id","source","archive_sha","content_sha","target","receipt"))assertEquals(before.getString(key),result.getString(key))
        HistoryRecovery.compact(c);assertEquals(result.toString(),HistoryRestore.Journal(c).step(id,"get").toString())
        assertEquals(0,HistoryRestore.status(c).getInt("pending"));assertEquals(1,p.inserts);assertTrue(p.deleted.isEmpty());assertEquals(setOf("sms:7","mms:8"),p.rows)
    }
    @Test fun unknownAndCommittedOutcomesNeverQueryOrResolveGuessedIds(){
        for(committed in listOf(false,true)){
            val c=context();val p=Fake();val id=if(committed)attempt(c,sms(),p).getString("id") else {p.failInsert=true;fails{attempt(c,sms(),p)};pending(c).getString("id")}
            val before=HistoryRestore.Journal(c).step(id,"get").toString();val reads=p.reads;val checks=p.checks
            fails{HistoryRestore.confirmRemoved(c,id,p,HistoryBackup.Stop())}
            assertEquals(before,HistoryRestore.Journal(c).step(id,"get").toString());assertEquals(reads,p.reads);assertEquals(checks,p.checks);assertTrue(p.deleted.isEmpty())
        }
    }
    @Test fun presentOwnedOrChangedMessageCannotBeAcknowledgedAbsent(){
        for(changed in listOf(false,true)){
            val c=context();val p=Fake();val id=needsReview(c,p).getString("id");p.changed=changed
            fails{HistoryRestore.confirmRemoved(c,id,p,HistoryBackup.Stop())};assertEquals("needs_review",pending(c).getString("phase"));assertTrue(p.deleted.isEmpty());assertEquals(1,p.inserts)
        }
    }
    @Test fun childrenReappearanceCancellationAndAccessLossRetainPendingEvidence(){
        for(fault in listOf("children","reappear","cancel","access","error")){
            val c=context();val p=Fake();val id=needsReview(c,p).getString("id");p.rows.remove("mms:91");val stop=HistoryBackup.Stop()
            if(fault!="children")p.children.clear()
            p.afterRemoved={when(fault){"reappear"->p.rows.add("mms:91");"cancel"->stop.cancel();"access"->p.allowed=false;"error"->error("Provider read failed")}}
            val before=HistoryRestore.Journal(c).step(id,"get").toString()
            fails{HistoryRestore.confirmRemoved(c,id,p,stop)}
            assertEquals(before,HistoryRestore.Journal(c).step(id,"get").toString());assertTrue(p.deleted.isEmpty());assertEquals(1,p.inserts)
            fails{attempt(c,sms(),p)};assertEquals(1,p.inserts)
        }
    }
    @Test fun explicitAbsentReviewProductionEntryStaysGateClosed(){
        val c=context();val p=Fake();val id=needsReview(c,p).getString("id");p.rows.remove("mms:91");p.children.clear()
        assertFalse(MessagingTransport.MMS_READY);fails{HistoryRecovery.confirmRemovedChecked(c,id)}
        assertEquals("needs_review",pending(c).getString("phase"));assertTrue(p.deleted.isEmpty())
    }
}
