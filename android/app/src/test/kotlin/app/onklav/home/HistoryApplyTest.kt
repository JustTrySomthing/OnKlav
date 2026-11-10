package app.onklav.home

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HistoryApplyTest {
    private class Sink:HistoryRestore.Provider {
        val inputs=mutableListOf<HistoryRestore.Input>();val rows=mutableSetOf<String>();val deleted=mutableListOf<String>();var failSecond=false;var afterAll:(()->Unit)?=null;var afterInsert:(()->Unit)?=null
        override fun checkAccess(){}
        override fun parentProof(input:HistoryRestore.Input)="b".repeat(64)
        override fun insert(input:HistoryRestore.Input):String {inputs+=input;return (if(input.kind=="sms")"sms:101" else "mms:102").also{rows+=it;afterInsert?.invoke()}}
        override fun complete(input:HistoryRestore.Input,receipt:HistoryRestore.Receipt,check:()->Unit){check();if(failSecond&&inputs.size==2)error("Second message failed")}
        override fun verify(input:HistoryRestore.Input,receipt:HistoryRestore.Receipt,check:()->Unit):Boolean {check();input.parts.mapNotNull{it.binary}.forEach{assertArrayEquals(byteArrayOf(0,1,2,127,-1),it.readBytes())};if(inputs.size==2)afterAll?.invoke();return true}
        override fun ownership(receipt:HistoryRestore.Receipt)=if(receipt.target in rows)HistoryRestore.Ownership.OWNED else HistoryRestore.Ownership.ABSENT
        override fun delete(receipt:HistoryRestore.Receipt){deleted+=receipt.target;rows-=receipt.target}
        override fun removed(receipt:HistoryRestore.Receipt)=receipt.target !in rows
    }
    private data class Fixture(val c:Context,val p:HistoryBackupTest.Provider,val sha:String)
    private val uri=Uri.parse("content://history-fixture/document/output")
    private val own=setOf("+15550109999")
    private fun fixture(withFlags:Boolean=false):Fixture {
        val base=ApplicationProvider.getApplicationContext<Context>();shadowOf(base as android.app.Application).grantPermissions(Manifest.permission.READ_SMS)
        val root=File(base.cacheDir,"apply-${java.util.UUID.randomUUID()}").apply{mkdirs()};val c=object:ContextWrapper(base){override fun getFilesDir()=root;override fun getApplicationContext():Context=this}
        val p=HistoryBackupTest.Provider(c);p.attachInfo(c,android.content.pm.ProviderInfo().apply{authority="sms;mms;mms-sms;history-fixture";exported=true;applicationInfo=c.applicationInfo})
        listOf("sms","mms","mms-sms","history-fixture").forEach{ShadowContentResolver.registerProviderInternal(it,p)}
        if(withFlags){
            native(c,JSONObject().put("action","message").put("key","sms:1").put("date",1700000000123L).put("thread",81).put("starred",true).put("reaction","👍").put("remind_at",9999999999999L))
            native(c,JSONObject().put("action","message").put("key","mms:2").put("date",1700000000000L).put("thread",81).put("locked",true))
            native(c,JSONObject().put("action","thread").put("key","81").put("folder","Archive").put("pinned",true).put("muted",true).put("unread",true).put("at",123))
        }
        HistoryBackup.export(c,uri,HistoryBackup.Stop()){}
        // Fresh fixture destination represents a different phone, not user state.
        if(withFlags)File(root,"messenger.json").delete()
        val review=HistoryImport.inspect(c,uri,HistoryBackup.Stop()){};p.empty=true
        return Fixture(c,p,review.sha256)
    }
    private fun native(c:Context,request:JSONObject):JSONObject {
        val result=JSONObject(app.onklav.shared.NativeCore.dispatch(c.filesDir.absolutePath,request.put("op","chat").toString()))
        check(result.getBoolean("ok")){result.optString("error")};return result.getJSONObject("value")
    }
    private fun sink(p:HistoryBackupTest.Provider)=Sink().apply{afterAll={p.empty=false;p.offset=100}}
    private fun maps(c:Context)=File(c.filesDir,"history-restore-maps").listFiles().orEmpty().filter{it.extension=="jsonl"}
    private fun clean(c:Context)=assertTrue(File(c.filesDir,"history-import").listFiles().orEmpty().isEmpty())
    @Test fun checkedArchiveFeedsTypedExecutorAndPersistsActualIdsWithoutOldIds(){
        val(c,p,sha)=fixture();val sink=sink(p);val metadata=SettingsBackup.export(c).getJSONObject("metadata").toString()
        val result=HistoryApply.run(c,uri,sha,own,sink,HistoryBackup.Stop()){}
        assertEquals(2,result.getInt("inserted"));assertEquals(0,result.getInt("pending"));assertEquals(2,sink.inputs.size)
        sink.inputs.forEach{input->assertTrue(input.fields.keys.none{it in listOf("_id","thread_id","creator","sub_id")});assertEquals(setOf("+15550101234"),input.peers)}
        val rows=maps(c).single().readLines().drop(1).map{JSONObject(it)};assertEquals(setOf("sms:101","mms:102"),rows.map{it.getString("target")}.toSet())
        assertEquals(metadata,SettingsBackup.export(c).getJSONObject("metadata").toString());assertEquals(1,p.writes);assertTrue(result.getBoolean("metadata_applied"));clean(c)
    }
    @Test fun changedArchiveAndMissingMmsIdentitiesFailBeforeFirstInsert(){
        val(c,_,sha)=fixture();val sink=Sink()
        assertTrue(assertThrows(Exception::class.java){HistoryApply.run(c,uri,"0".repeat(64),own,sink,HistoryBackup.Stop()){}}.message.orEmpty().contains("Backup changed"))
        assertTrue(assertThrows(Exception::class.java){HistoryApply.run(c,uri,sha,emptySet(),sink,HistoryBackup.Stop()){}}.message.orEmpty().contains("Configure local MMS"))
        assertTrue(assertThrows(Exception::class.java){HistoryApply.run(c,uri,sha,own,sink,HistoryBackup.Stop(),expectedPlanSha="0".repeat(64)){}}.message.orEmpty().contains("Phone history changed"))
        assertTrue(sink.inputs.isEmpty());assertTrue(maps(c).isEmpty());clean(c)
    }
    @Test fun stopAfterCommitPreservesPartialOutcomeMapAndReceipt(){
        val(c,_,sha)=fixture();val sink=Sink();val stop=HistoryBackup.Stop()
        assertThrows(android.os.OperationCanceledException::class.java){HistoryApply.run(c,uri,sha,own,sink,stop){if(it=="Restoring messages: 2/2")stop.cancel()}}
        assertEquals(1,sink.inputs.size);assertEquals(1,HistoryRestore.recoverState(c).getInt("committed"))
        val rows=maps(c).single().readLines().drop(1).map{JSONObject(it)};assertEquals(setOf("inserted","pending"),rows.map{it.getString("decision")}.toSet());clean(c)
    }
    @Test fun secondMessageFailureKeepsFirstAndCleansOnlyIncompleteInsert(){
        val(c,_,sha)=fixture();val sink=Sink().apply{failSecond=true}
        assertThrows(Exception::class.java){HistoryApply.run(c,uri,sha,own,sink,HistoryBackup.Stop()){}}
        assertEquals(listOf("sms:101"),sink.deleted);assertEquals(setOf("mms:102"),sink.rows)
        assertEquals(1,HistoryRestore.status(c).getInt("committed"));assertEquals(1,HistoryRestore.status(c).getInt("rolled_back"));assertEquals(1,maps(c).size);clean(c)
    }
    @Test fun freshPhoneComparisonSkipsExistingRowsAndAbsentCommittedRowsBlockReplay(){
        val(c,p,sha)=fixture();val sink=sink(p);HistoryApply.run(c,uri,sha,own,sink,HistoryBackup.Stop()){}
        p.empty=true
        assertThrows(Exception::class.java){HistoryApply.run(c,uri,sha,own,sink,HistoryBackup.Stop()){}}
        assertEquals(2,sink.inputs.size)
        p.empty=false;p.offset=100
        val result=HistoryApply.run(c,uri,sha,own,sink,HistoryBackup.Stop()){}
        assertEquals(0,result.getInt("inserted"));assertEquals(2,sink.inputs.size)
        val matched=maps(c).flatMap{it.readLines().drop(1)}.map{JSONObject(it)}.filter{it.getString("decision")=="match"}
        assertEquals(setOf("sms:101","mms:102"),matched.map{it.getString("target")}.toSet());clean(c)
    }
    @Test fun productionGateBlocksBeforeSafAndProviderSnapshot(){
        val(c,p,sha)=fixture();val before=p.queries
        HistoryRecovery.save(c,own.single())
        val inspected=HistoryImport.inspect(c,uri,HistoryBackup.Stop()){}
        val confirmation=HistoryConfirmation.prepare(c,uri,inspected.copy(comparison=HistoryImport.Comparison(0,2,0,0,0,0,0,0,"0".repeat(64))))
        assertThrows(Exception::class.java){HistoryApply.applyChecked(c,confirmation,HistoryBackup.Stop()){}}
        assertEquals(before,p.queries);assertTrue(maps(c).isEmpty());assertEquals(0,HistoryRestore.status(c).getInt("pending"));clean(c)
    }
    @Test fun postSnapshotRemapsFlagsAndFoldersAndDisarmsOldReminders(){
        val(c,p,sha)=fixture(withFlags=true);val sink=sink(p)
        val result=HistoryApply.run(c,uri,sha,own,sink,HistoryBackup.Stop()){}
        assertEquals(2,result.getInt("flags_imported"));assertEquals(1,result.getInt("threads_imported"))
        val state=native(c,JSONObject().put("action","snapshot"));val flags=state.getJSONObject("messages")
        assertFalse(flags.has("sms:1"));assertFalse(flags.has("mms:2"))
        val sms=flags.getJSONObject("sms:101");assertTrue(sms.getBoolean("starred"));assertEquals("👍",sms.getString("reaction"));assertEquals(181,sms.getInt("thread"));assertEquals(0,sms.getLong("remind_at"))
        assertTrue(flags.getJSONObject("mms:102").getBoolean("locked"))
        val thread=state.getJSONObject("threads").getJSONObject("181");assertEquals("Archive",thread.getString("folder"));assertTrue(thread.getBoolean("pinned"));assertTrue(thread.getBoolean("muted"));assertTrue(thread.isNull("unread"));assertEquals(0,thread.getLong("read_at"))
        val header=JSONObject(maps(c).single().readLines().first());assertTrue(header.getBoolean("phone_revalidated"));assertTrue(header.getBoolean("metadata_applied"));clean(c)
    }
    @Test fun metadataEditedDuringInsertsWinsOverArchiveEntries(){
        val(c,p,sha)=fixture(withFlags=true);val sink=sink(p);val original=sink.afterAll
        sink.afterAll={original?.invoke();native(c,JSONObject().put("action","message").put("key","sms:101").put("date",1700000000123L).put("starred",false).put("reaction","❤️").put("remind_at",4444));native(c,JSONObject().put("action","thread").put("key","181").put("folder","Inbox").put("pinned",false))}
        val result=HistoryApply.run(c,uri,sha,own,sink,HistoryBackup.Stop()){}
        assertEquals(1,result.getInt("flags_imported"));assertEquals(0,result.getInt("threads_imported"))
        val state=native(c,JSONObject().put("action","snapshot"));val current=state.getJSONObject("messages").getJSONObject("sms:101")
        assertFalse(current.getBoolean("starred"));assertEquals("❤️",current.getString("reaction"));assertEquals(4444,current.getLong("remind_at"));assertEquals("Inbox",state.getJSONObject("threads").getJSONObject("181").getString("folder"));clean(c)
    }
    @Test fun changedContentOrWrongPostIdsLeaveMetadataUntouchedAndReceiptsIntact(){
        for(change in listOf<(HistoryBackupTest.Provider)->Unit>({it.messageBody="Changed after insertion"},{it.offset=200})){
            val(c,p,sha)=fixture(withFlags=true);val sink=sink(p);val original=sink.afterAll;sink.afterAll={original?.invoke();change(p)}
            val before=native(c,JSONObject().put("action","snapshot")).toString()
            assertTrue(assertThrows(Exception::class.java){HistoryApply.run(c,uri,sha,own,sink,HistoryBackup.Stop()){}}.message.orEmpty().contains("metadata was not applied"))
            assertEquals(before,native(c,JSONObject().put("action","snapshot")).toString());assertEquals(2,HistoryRestore.status(c).getInt("committed"));assertFalse(JSONObject(maps(c).single().readLines().first()).getBoolean("metadata_applied"));clean(c)
        }
    }
    @Test fun providerChangeDuringPostCaptureStopsMetadataMerge(){
        val(c,p,sha)=fixture(withFlags=true);val sink=sink(p);val original=sink.afterAll;sink.afterAll={original?.invoke();p.mutate=true}
        val before=native(c,JSONObject().put("action","snapshot")).toString()
        assertThrows(Exception::class.java){HistoryApply.run(c,uri,sha,own,sink,HistoryBackup.Stop()){}}
        assertEquals(before,native(c,JSONObject().put("action","snapshot")).toString());assertEquals(2,HistoryRestore.status(c).getInt("committed"));clean(c)
    }

    private fun reviewed(f:Fixture):HistoryConfirmation {
        HistoryRecovery.save(f.c,own.single())
        val review=HistoryImport.inspect(f.c,uri,HistoryBackup.Stop(),true,f.sha){}
        return HistoryConfirmation.prepare(f.c,uri,review)
    }
    @Test fun reviewedApplyUsesFreshPlanAndCannotReuseConfirmation(){
        val f=fixture();val confirmation=reviewed(f);val sink=sink(f.p)
        val result=HistoryApply.runReviewed(f.c,confirmation,sink,HistoryBackup.Stop()){}
        assertEquals(2,result.getInt("inserted"));assertEquals(2,sink.inputs.size);val queries=f.p.queries
        assertThrows(Exception::class.java){HistoryApply.runReviewed(f.c,confirmation,sink,HistoryBackup.Stop()){}}
        assertEquals(queries,f.p.queries);assertEquals(2,sink.inputs.size);clean(f.c)
    }
    @Test fun confirmationNeedsComparisonMmsIdentitiesAndExplicitUncancelledReview(){
        val f=fixture();val review=HistoryImport.inspect(f.c,uri,HistoryBackup.Stop(),true,f.sha){}
        assertThrows(Exception::class.java){HistoryConfirmation.prepare(f.c,uri,review.copy(comparison=null))}
        assertThrows(Exception::class.java){HistoryConfirmation.prepare(f.c,uri,review)}
        HistoryRecovery.save(f.c,own.single());assertThrows(Exception::class.java){HistoryConfirmation.prepare(f.c,uri,review.copy(comparison=review.comparison!!.copy(fresh=1001)))}
        val ticket=HistoryConfirmation.prepare(f.c,uri,review);ticket.cancel();val queries=f.p.queries;val sink=sink(f.p)
        assertThrows(Exception::class.java){HistoryApply.runReviewed(f.c,ticket,sink,HistoryBackup.Stop()){}}
        assertEquals(queries,f.p.queries);assertTrue(sink.inputs.isEmpty());assertTrue(maps(f.c).isEmpty());clean(f.c)
    }
    @Test fun changedIdentityConfigurationRejectsReviewBeforeSafOrProviderReads(){
        val f=fixture();val ticket=reviewed(f);HistoryRecovery.save(f.c,"+15550108888");val queries=f.p.queries;val sink=sink(f.p)
        assertThrows(Exception::class.java){HistoryApply.runReviewed(f.c,ticket,sink,HistoryBackup.Stop()){}}
        assertEquals(queries,f.p.queries);assertTrue(sink.inputs.isEmpty());assertTrue(maps(f.c).isEmpty());clean(f.c)
    }
    @Test fun changedPhonePlanRequiresAnotherReviewBeforeAnyInsertion(){
        val f=fixture();val ticket=reviewed(f);f.p.empty=false;f.p.offset=100;val sink=sink(f.p)
        assertThrows(Exception::class.java){HistoryApply.runReviewed(f.c,ticket,sink,HistoryBackup.Stop()){}}
        assertTrue(sink.inputs.isEmpty());assertTrue(maps(f.c).isEmpty());clean(f.c)
    }
    @Test fun identityChangeAtProviderBoundaryPausesWithKnownReceiptAndNoAutomaticRetry(){
        val f=fixture();val ticket=reviewed(f);val sink=sink(f.p).apply{afterInsert={HistoryRecovery.save(f.c,"+15550108888")}}
        assertThrows(Exception::class.java){HistoryApply.runReviewed(f.c,ticket,sink,HistoryBackup.Stop()){}}
        assertEquals(1,sink.inputs.size);assertTrue(sink.deleted.isEmpty());assertEquals(1,HistoryRestore.status(f.c).getInt("pending"));assertEquals(0,HistoryRestore.status(f.c).getInt("committed"));assertEquals(1,maps(f.c).size);clean(f.c)
    }

    private fun failedMetadata():Fixture {
        val f=fixture(withFlags=true);val sink=sink(f.p);val original=sink.afterAll
        sink.afterAll={original?.invoke();f.p.messageBody="Changed after insertion"}
        assertThrows(Exception::class.java){HistoryApply.run(f.c,uri,f.sha,own,sink,HistoryBackup.Stop()){}}
        f.p.messageBody="Hello 😀";HistoryRecovery.save(f.c,own.single());return f
    }
    private fun resumeTicket(f:Fixture):HistoryConfirmation=HistoryResume.review(f.c,uri,HistoryResume.binding(f.c,maps(f.c).single().nameWithoutExtension),HistoryBackup.Stop()){}
    @Test fun savedRecordMetadataResumesOriginalJobWithoutInsertingOrReplayingMessages(){
        val f=failedMetadata();val map=maps(f.c).single();val id=map.nameWithoutExtension;val ticket=resumeTicket(f);val provider=Sink()
        val result=HistoryResume.run(f.c,ticket,provider,HistoryBackup.Stop()){}
        assertTrue(result.getBoolean("metadata_applied"));assertTrue(provider.inputs.isEmpty());assertTrue(provider.deleted.isEmpty());assertEquals(1,maps(f.c).size)
        val state=native(f.c,JSONObject().put("action","snapshot"));assertTrue(state.getJSONObject("history_merges").has(id));assertTrue(state.getJSONObject("messages").getJSONObject("sms:101").getBoolean("starred"));assertEquals(0,state.getJSONObject("messages").getJSONObject("sms:101").getLong("remind_at"));assertTrue(JSONObject(map.readLines().first()).getBoolean("metadata_applied"))
        native(f.c,JSONObject().put("action","remove_message").put("key","sms:101"));val before=File(f.c.filesDir,"messenger.json").readBytes()
        assertThrows(Exception::class.java){HistoryResume.run(f.c,ticket,provider,HistoryBackup.Stop()){}};assertArrayEquals(before,File(f.c.filesDir,"messenger.json").readBytes());clean(f.c)
    }
    @Test fun changedSavedRecordOrWrongOriginalArchiveCannotContinue(){
        val f=failedMetadata();val ticket=resumeTicket(f);val map=maps(f.c).single();val before=File(f.c.filesDir,"messenger.json").takeIf{it.exists()}?.readBytes();map.appendText("\n");val queries=f.p.queries
        assertThrows(Exception::class.java){HistoryResume.run(f.c,ticket,Sink(),HistoryBackup.Stop()){}};assertEquals(queries,f.p.queries);assertEquals(before?.toList(),File(f.c.filesDir,"messenger.json").takeIf{it.exists()}?.readBytes()?.toList());clean(f.c)
        val other=failedMetadata();val saved=HistoryResume.binding(other.c,maps(other.c).single().nameWithoutExtension);other.p.target.appendBytes(byteArrayOf(1))
        assertThrows(Exception::class.java){HistoryResume.review(other.c,uri,saved,HistoryBackup.Stop()){}};assertFalse(JSONObject(maps(other.c).single().readLines().first()).getBoolean("metadata_applied"));clean(other.c)
    }
    @Test fun wrongRecordedPhoneIdsAndProviderChangesLeaveResumeMetadataUntouched(){
        val f=failedMetadata();val saved=HistoryResume.binding(f.c,maps(f.c).single().nameWithoutExtension);val before=native(f.c,JSONObject().put("action","snapshot")).toString();f.p.offset=200
        assertThrows(Exception::class.java){HistoryResume.review(f.c,uri,saved,HistoryBackup.Stop()){}};assertEquals(before,native(f.c,JSONObject().put("action","snapshot")).toString());f.p.offset=100
        val ticket=resumeTicket(f);f.p.mutate=true;assertThrows(Exception::class.java){HistoryResume.run(f.c,ticket,Sink(),HistoryBackup.Stop()){}};assertEquals(before,native(f.c,JSONObject().put("action","snapshot")).toString());assertFalse(JSONObject(maps(f.c).single().readLines().first()).getBoolean("metadata_applied"));clean(f.c)
    }
    @Test fun pendingBatchesHaveExplicitSourcesAndProductionGatePrecedesSnapshot(){
        val f=fixture();val provider=sink(f.p).apply{failSecond=true}
        assertThrows(Exception::class.java){HistoryApply.run(f.c,uri,f.sha,own,provider,HistoryBackup.Stop()){}}
        assertEquals(listOf("sms:1"),HistoryResume.binding(f.c,maps(f.c).single().nameWithoutExtension).pendingSources);clean(f.c)
        val completed=failedMetadata();val ticket=resumeTicket(completed);val queries=completed.p.queries
        assertThrows(Exception::class.java){HistoryResume.applyChecked(completed.c,ticket,HistoryBackup.Stop()){}};assertEquals(queries,completed.p.queries);assertFalse(JSONObject(maps(completed.c).single().readLines().first()).getBoolean("metadata_applied"));clean(completed.c)
    }
    @Test fun resumeTicketCannotStartAnotherInsertBatchAndChangedIdentitiesNeedReview(){
        val f=failedMetadata();val ticket=resumeTicket(f);val queries=f.p.queries;val provider=Sink()
        assertThrows(Exception::class.java){HistoryApply.runReviewed(f.c,ticket,provider,HistoryBackup.Stop()){}};assertEquals(queries,f.p.queries);assertTrue(provider.inputs.isEmpty())
        HistoryRecovery.save(f.c,"+15550108888");assertThrows(Exception::class.java){HistoryResume.run(f.c,ticket,provider,HistoryBackup.Stop()){}};assertEquals(queries,f.p.queries);assertTrue(provider.inputs.isEmpty());clean(f.c)
    }

    private fun partial():Fixture {
        val f=fixture(withFlags=true);val sink=Sink().apply{failSecond=true}
        assertThrows(Exception::class.java){HistoryApply.run(f.c,uri,f.sha,own,sink,HistoryBackup.Stop()){}}
        f.p.empty=false;f.p.offset=100;f.p.hideSms=true;HistoryRecovery.save(f.c,own.single());return f
    }
    @Test fun partialBatchFinishesOnlyMissingMessageUnderOriginalRecord(){
        val f=partial();val map=maps(f.c).single();val id=map.nameWithoutExtension;val plan=JSONObject(map.readLines().first()).getString("plan_sha")
        val ticket=resumeTicket(f);assertEquals(listOf("sms:1"),ticket.resume!!.pendingSources)
        val sink=Sink().apply{afterInsert={f.p.hideSms=false}}
        val result=HistoryResume.run(f.c,ticket,sink,HistoryBackup.Stop()){}
        assertTrue(result.getBoolean("metadata_applied"));assertEquals(listOf("sms:1"),sink.inputs.map{it.source});assertTrue(sink.deleted.isEmpty());assertEquals(1,maps(f.c).size)
        val rows=map.readLines().map{JSONObject(it)};assertEquals(plan,rows.first().getString("plan_sha"));assertTrue(rows.first().getBoolean("metadata_applied"));assertTrue(rows.drop(1).all{it.getString("decision")=="inserted"})
        assertTrue(native(f.c,JSONObject().put("action","snapshot")).getJSONObject("history_merges").has(id));assertEquals(2,HistoryRestore.status(f.c).getInt("committed"));clean(f.c)
    }
    @Test fun stoppingContinuationRetainsNewCommitAndNextReviewDoesNotInsertAgain(){
        val f=partial();val ticket=resumeTicket(f);val stop=HistoryBackup.Stop();val sink=Sink().apply{afterInsert={f.p.hideSms=false}}
        assertThrows(Exception::class.java){HistoryResume.run(f.c,ticket,sink,stop){if(it.startsWith("Verifying recorded messages"))stop.cancel()}}
        assertEquals(1,sink.inputs.size);assertEquals(2,HistoryRestore.status(f.c).getInt("committed"));assertFalse(JSONObject(maps(f.c).single().readLines().first()).getBoolean("metadata_applied"))
        val fresh=resumeTicket(f);assertTrue(fresh.resume!!.pendingSources.isEmpty());val second=Sink();assertTrue(HistoryResume.run(f.c,fresh,second,HistoryBackup.Stop()){}.getBoolean("metadata_applied"));assertTrue(second.inputs.isEmpty());assertEquals(1,maps(f.c).size);clean(f.c)
    }
    @Test fun lostInsertAcknowledgementBlocksContinuationWithoutDiscardingPartialMap(){
        val f=partial();val ticket=resumeTicket(f);val sink=Sink().apply{afterInsert={error("Lost provider acknowledgement")}}
        assertThrows(Exception::class.java){HistoryResume.run(f.c,ticket,sink,HistoryBackup.Stop()){}}
        assertEquals(1,sink.inputs.size);assertTrue(sink.deleted.isEmpty());HistoryRestore.recoverState(f.c);assertEquals(1,HistoryRestore.status(f.c).getInt("pending"));assertEquals(1,HistoryRestore.status(f.c).getInt("committed"))
        assertThrows(Exception::class.java){resumeTicket(f)};assertEquals(1,maps(f.c).size);assertEquals(1,HistoryRecovery.detail(f.c,maps(f.c).single().nameWithoutExtension).getInt("pending"));clean(f.c)
    }
    @Test fun pendingMessageAppearingOrChangedCompletedTargetRequiresReviewBeforeInsertion(){
        for(change in listOf<(HistoryBackupTest.Provider)->Unit>({it.hideSms=false},{it.offset=200})){
            val f=partial();val saved=HistoryResume.binding(f.c,maps(f.c).single().nameWithoutExtension);val before=maps(f.c).single().readBytes();change(f.p)
            assertThrows(Exception::class.java){HistoryResume.review(f.c,uri,saved,HistoryBackup.Stop()){}};assertArrayEquals(before,maps(f.c).single().readBytes());assertEquals(1,HistoryRestore.status(f.c).getInt("committed"));clean(f.c)
        }
    }
    @Test fun recoveredCommitBeforeMapAcknowledgementRequiresNoNewInsert(){
        val f=partial();val old=maps(f.c).single().readBytes();val sink=Sink().apply{afterInsert={f.p.hideSms=false}};val ticket=resumeTicket(f)
        HistoryResume.run(f.c,ticket,sink,HistoryBackup.Stop()){}
        // Simulate commit durable but map acknowledgement missing; metadata state
        // belongs to a later step, so remove only this fixture's metadata receipt.
        val state=File(f.c.filesDir,"messenger.json");val obj=JSONObject(state.readText());obj.put("history_merges",JSONObject());state.writeText(obj.toString());maps(f.c).single().writeBytes(old)
        val next=resumeTicket(f);assertTrue(next.resume!!.pendingSources.isEmpty());val second=Sink();HistoryResume.run(f.c,next,second,HistoryBackup.Stop()){}
        assertTrue(second.inputs.isEmpty());assertEquals(2,HistoryRestore.status(f.c).getInt("committed"));clean(f.c)
    }
}
