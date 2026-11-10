package app.onklav.home

import android.app.Application
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.*
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.NativeCore
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.*
import java.time.*
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class AuditRepairTest {
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private fun keys():SecretKeys{val key=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey();return object:SecretKeys{override fun load(alias:String,create:Boolean):SecretKey=key;override fun deletePrefix(prefix:String){}}}
 @Before fun clear(){File(c.noBackupFilesDir,"cloud-files").deleteRecursively();c.getSharedPreferences("incoming-receipt-order",0).edit().clear().commit()}
 private fun calendar(cutoff:String,zone:String="UTC",start:String="20261101T100000"):CalendarEvent{
  val text="BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:cutoff\nSUMMARY:Cutoff\nDTSTART;TZID=$zone:$start\nDTEND;TZID=$zone:${start.substringBefore('T')}T110000\nRRULE:FREQ=DAILY;UNTIL=$cutoff\nEND:VEVENT\nEND:VCALENDAR\n"
  val imported=CalendarIcs.parse(text);assertEquals(0,imported.skipped);return imported.events.single()
 }
 @Test fun timestampCutoffNeverAdmitsAnOccurrenceAfterItsInstant(){
  val e=calendar("20261103T090000Z");val rows=CalendarRules.occurrences(e,Instant.parse("2026-11-01T00:00:00Z"),Instant.parse("2026-11-05T00:00:00Z"));assertEquals(2,rows.size);assertTrue(rows.all{it.start.toInstant()<=Instant.parse("2026-11-03T09:00:00Z")})
 }
 @Test fun exactTimestampAndDateCutoffsRemainInclusiveAcrossZones(){assertEquals(LocalDate.parse("2026-11-03"),calendar("20261103T100000Z").until);assertEquals(LocalDate.parse("2026-11-03"),calendar("20261103").until);assertEquals(LocalDate.parse("2026-11-02"),calendar("20261103T150000Z","America/Chicago").until);assertEquals(LocalDate.parse("2026-11-03"),calendar("20261103T160000Z","America/Chicago").until)}
 @Test fun twentyEscapedPasswordAccountsRoundTripAndCanBeRemoved(){val store=CloudStore(c,keys());val accounts=List(20){CloudAccount(name="Account $it",endpoint="https://example.test/dav/",user="fixture",password="\\".repeat(4096))};store.save(accounts);assertTrue(File(c.noBackupFilesDir,"cloud-files/accounts.bin").length()>150000);assertEquals(accounts,store.load());store.save(accounts.dropLast(1));assertEquals(19,store.load().size);store.save(emptyList());assertTrue(store.load().isEmpty())}
 @Test fun invalidEnvelopeIsKeptAndNeverOverwritten(){val store=CloudStore(c,keys());store.save(listOf(CloudAccount(name="Account",endpoint="https://example.test/dav/",user="fixture",password="fixture")));val file=File(c.noBackupFilesDir,"cloud-files/accounts.bin");file.appendBytes(ByteArray(CloudStore.MAX_ENVELOPE));val before=file.readBytes();assertThrows(Exception::class.java){store.load()};assertThrows(Exception::class.java){store.save(emptyList())};assertArrayEquals(before,file.readBytes())}
 private fun native(root:File,action:String,vararg args:Pair<String,Any>):JSONObject{val q=JSONObject().put("op","chat").put("action",action);args.forEach{q.put(it.first,it.second)};val reply=JSONObject(NativeCore.dispatch(root.path,q.toString()));assertTrue(reply.toString(),reply.getBoolean("ok"));return reply.getJSONObject("value")}
 @Test fun clockRollbackRestoresInboxAndUnreadWhileJournalReplayPreservesArchive(){
  val root=File(c.cacheDir,"receipt-native").apply{deleteRecursively();mkdirs()}
  native(root,"arrival","key" to "1","address" to "5551234567","at" to 2000L)
  native(root,"thread","key" to "1","folder" to "Archive","unread" to false,"at" to 2001L)
  val filing=IncomingReceiptOrder.next(c,native(root,"snapshot"),1000)
  assertTrue(filing>2001);val result=native(root,"arrival","key" to "1","address" to "5551234567","at" to filing).getJSONObject("threads").getJSONObject("1")
  assertEquals("Inbox",result.getString("folder"));assertTrue(result.getBoolean("unread"))
  native(root,"thread","key" to "1","folder" to "Trash")
  assertEquals("Trash",native(root,"arrival","key" to "1","address" to "5551234567","at" to filing).getJSONObject("threads").getJSONObject("1").getString("folder"));root.deleteRecursively()
 }
 @Test fun equalWallTimesGetDistinctDurableOrdersAndReadUsesTheSameClock(){val a=IncomingReceiptOrder.next(c,JSONObject(),3000);val b=IncomingReceiptOrder.next(c,JSONObject(),3000);assertEquals(a+1,b);assertEquals(b,IncomingReceiptOrder.readTime(c,1000));assertEquals(b+1,IncomingReceiptOrder.next(c,JSONObject(),500))}
 @Test fun oldNativeReadAndReceiveStateSeedsTheReceiptSequence(){val state=JSONObject("{\"threads\":{\"1\":{\"last_incoming_at\":9000,\"read_at\":10000}}}");assertEquals(10001L,IncomingReceiptOrder.next(c,state,1));assertEquals(10002L,IncomingReceiptOrder.next(c,JSONObject(),1))}
 @Test fun theSameMmsReceiptReusesItsPersistedOrder(){val prefs=c.getSharedPreferences("audit-mms-order",0);prefs.edit().clear().commit();val a=IncomingReceiptOrder.persisted(c,prefs,"receipt.filing_at",JSONObject());IncomingReceiptOrder.next(c,JSONObject(),a+100);assertEquals(a,IncomingReceiptOrder.persisted(c,prefs,"receipt.filing_at",JSONObject()))}
 @Test fun byteLimitsMatchFilesAndFailBeforeWritingTheOverflowChunk(){assertEquals(256L*1024*1024,fileImportLimit("application/zip"));assertEquals(2L*1024*1024*1024,fileImportLimit("video/mp4"));val target=ByteArrayOutputStream();val stream=LimitedExportStream(target,3);stream.write(byteArrayOf(1,2));assertThrows(IllegalArgumentException::class.java){stream.write(byteArrayOf(3,4))};assertArrayEquals(byteArrayOf(1,2),target.toByteArray());stream.write(3);assertThrows(IllegalArgumentException::class.java){stream.write(4)};assertEquals(3,target.size())}
 class Provider(val c:Context):ContentProvider(){val file=File(c.cacheDir,"audit-repair-export");var reject=false;var opens=0;override fun onCreate()=true;override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor{opens++;if(reject)throw IOException("Injected save failure");return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)};override fun getType(uri:Uri)="application/octet-stream";override fun query(uri:Uri,p:Array<out String>?,s:String?,a:Array<out String>?,o:String?):Cursor?=null;override fun insert(uri:Uri,v:ContentValues?):Uri?=null;override fun update(uri:Uri,v:ContentValues?,s:String?,a:Array<out String>?)=0;override fun delete(uri:Uri,s:String?,a:Array<out String>?)=0}
 private fun provider(authority:String)=Provider(c).also{it.attachInfo(c,android.content.pm.ProviderInfo().apply{this.authority=authority;applicationInfo=c.applicationInfo});ShadowContentResolver.registerProviderInternal(authority,it)}
 @Test fun exportHelperWritesTheWholeFileAndPropagatesCopyOrCancellationFailures()=runBlocking{
  val p=provider("audit-repair-export");val uri=Uri.parse("content://audit-repair-export/file")
  writeFiledExport(c,uri){it.write("complete".toByteArray())};assertEquals("complete",p.file.readText())
  assertTrue(runCatching{writeFiledExport(c,uri){it.write("partial".toByteArray());throw IOException("copy failed")}}.isFailure)
  assertTrue(runCatching{writeFiledExport(c,uri){throw CancellationException("cancelled")}}.exceptionOrNull() is CancellationException)
 }
}
