package app.onklav.home

import android.Manifest
import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
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
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HistoryBackupTest {
    class Provider(val c:Context):ContentProvider() {
        val target=File(c.cacheDir,"history-output.zip")
        val media=File(c.cacheDir,"history-media.bin").apply{writeBytes(byteArrayOf(0,1,2,127,-1))}
        var hideSms=false;var hideMms=false;var messageBody="Hello 😀";var empty=false;var fail=false;var mutate=false;var writes=0;var queries=0;var offset=0;var selectionSeen=false
        override fun onCreate()=true
        private fun rows(columns:Array<String>,vararg values:Array<out Any?>)=MatrixCursor(columns).apply{values.forEach{addRow(it)}}
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor? {
            queries++;if(fail)return null
            if((empty&&uri.authority in listOf("sms","mms"))||(hideSms&&uri.authority=="sms")||(hideMms&&uri.authority=="mms"))return MatrixCursor(projection ?: arrayOf("_id","type","body","date","msg_box"))
            return when {
                uri.authority=="sms"->{selectionSeen=selection=="type<>3";rows(arrayOf("_id","body","address","date","type","sub_id","thread_id"),arrayOf((1+offset).toString(),messageBody,"+15550101234","1700000000123","1","2",(81+offset).toString()))}
                uri.path?.endsWith("/addr")==true->rows(arrayOf("address","type","charset"),arrayOf("+15550101234","137","106"),arrayOf("+15550109999","151","106"))
                uri.path?.endsWith("/part")==true->rows(arrayOf("_id","ct","text","_data","chset","seq","name","cl"),arrayOf((8+offset).toString(),"text/plain","MMS text",null,"106","0",null,"text.txt"),arrayOf((9+offset).toString(),"image/png",null,"/provider/private/path",null,"1","photo.png","photo.png"))
                uri.authority=="mms"->rows(arrayOf("_id","date","msg_box","sub","sub_cs","m_type","thread_id"),arrayOf((2+offset).toString(),"1700000000","1","Subject","106","132",(81+offset).toString()))
                else->error("Unexpected query $uri")
            }
        }
        override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
            if(uri.authority=="mms") {
                if(mutate)c.contentResolver.notifyChange(Uri.parse("content://sms"),null)
                return ParcelFileDescriptor.open(media,ParcelFileDescriptor.MODE_READ_ONLY)
            }
            if(mode=="r")return ParcelFileDescriptor.open(target,ParcelFileDescriptor.MODE_READ_ONLY)
            writes++;return ParcelFileDescriptor.open(target,ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
        }
        override fun call(method:String,arg:String?,extras:Bundle?):Bundle? {
            if(method=="android:deleteDocument"){target.delete();return Bundle()};return super.call(method,arg,extras)
        }
        override fun getType(uri:Uri)="application/zip"
        override fun insert(uri:Uri,values:ContentValues?):Uri? {error("Provider insert is forbidden")}
        override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?):Int {error("Provider update is forbidden")}
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?):Int {error("Provider delete is forbidden")}
    }
    private val c:Context get()=ApplicationProvider.getApplicationContext()
    private val target=Uri.parse("content://history-fixture/document/output")
    private fun fixture():Provider {
        shadowOf(c as android.app.Application).grantPermissions(Manifest.permission.READ_SMS)
        return Provider(c).also{p->
            p.attachInfo(c,android.content.pm.ProviderInfo().apply{authority="sms;mms;mms-sms;history-fixture";exported=true;applicationInfo=c.applicationInfo})
            listOf("sms","mms","mms-sms","history-fixture").forEach{ShadowContentResolver.registerProviderInternal(it,p)}
        }
    }
    private fun assertClean(){assertTrue(File(c.filesDir,"history-export").listFiles().orEmpty().isEmpty())}
    @Test fun realProviderToJniToZipPreservesTextAddressesMediaAndUnits() {
        val p=fixture();val phases=mutableListOf<String>()
        val result=HistoryBackup.export(c,target,HistoryBackup.Stop()){phases+=it}
        assertEquals(1,result.sms);assertEquals(1,result.mms);assertEquals(2,result.parts);assertTrue(p.selectionSeen);assertEquals(1,p.writes)
        ZipFile(p.target).use{zip->
            val records=zip.getInputStream(zip.getEntry("records.jsonl")).bufferedReader().readLines().map{JSONObject(it)}
            assertEquals(6,records.size);assertEquals("Hello 😀",records[0].getJSONObject("fields").getString("body"))
            assertEquals("1700000000123",records[0].getJSONObject("fields").getString("date"));assertEquals("1700000000",records[1].getJSONObject("fields").getString("date"))
            assertEquals("137",records[2].getJSONObject("fields").getString("type"));assertEquals("MMS text",records[4].getJSONObject("fields").getString("text"))
            assertFalse(records.toString().contains("/provider/private/path"));assertArrayEquals(p.media.readBytes(),zip.getInputStream(zip.getEntry("media/9.bin")).readBytes())
            val index=JSONObject(zip.getInputStream(zip.getEntry("index.json")).bufferedReader().readText())
            assertEquals("onklav-message-history",index.getString("format"));assertFalse(index.getBoolean("restore_supported"))
            val entry=index.getJSONArray("entries").getJSONObject(2)
            val digest=java.security.MessageDigest.getInstance("SHA-256").digest(p.media.readBytes()).joinToString(""){"%02x".format(it)}
            assertEquals(digest,entry.getString("sha256"))
        }
        assertTrue(phases.contains("Building and verifying archive…"));assertClean()
    }
    @Test fun providerFailureAndChangesNeverPublishSuccessOrLeavePrivateHistory() {
        val p=fixture();p.fail=true
        assertTrue(assertThrows(Exception::class.java){HistoryBackup.export(c,target,HistoryBackup.Stop()) {}}.message.orEmpty().contains("provider"))
        assertEquals(0,p.writes);assertClean()
        p.fail=false;p.mutate=true
        assertTrue(assertThrows(Exception::class.java){HistoryBackup.export(c,target,HistoryBackup.Stop()) {}}.message.orEmpty().contains("Messages changed"))
        assertEquals(0,p.writes);assertClean()
    }
    @Test fun cancellationAndRevocationAreCheckedAgainBeforeDocumentWrite() {
        val p=fixture();val stop=HistoryBackup.Stop()
        assertThrows(Exception::class.java){HistoryBackup.export(c,target,stop){if(it=="Building and verifying archive…")stop.cancel()}}
        assertTrue(stop.signal.isCanceled);assertEquals(0,p.writes);assertClean()
        assertTrue(assertThrows(Exception::class.java){HistoryBackup.export(c,target,HistoryBackup.Stop()){if(it=="Building and verifying archive…")shadowOf(c as android.app.Application).denyPermissions(Manifest.permission.READ_SMS)}}.message.orEmpty().contains("SMS access"))
        assertEquals(0,p.writes);assertClean()
    }
    @Test fun historyReviewUsesExportedZipWithoutSmsAccessOrProviderMutationAndRemovesCopies() {
        val p=fixture();HistoryBackup.export(c,target,HistoryBackup.Stop()){}
        val original=p.target.readBytes();val queries=p.queries;val phases=mutableListOf<String>()
        val metadata=SettingsBackup.export(c).getJSONObject("metadata").toString()
        shadowOf(c as android.app.Application).denyPermissions(Manifest.permission.READ_SMS)
        val review=HistoryImport.inspect(c,target,HistoryBackup.Stop()){phases+=it}
        assertEquals(1,review.sms);assertEquals(1,review.mms);assertEquals(2,review.parts);assertEquals(5L,review.mediaBytes)
        assertEquals(2,review.inbox);assertEquals(0,review.other);assertEquals(64,review.sha256.length)
        assertEquals(queries,p.queries);assertEquals(1,p.writes);assertArrayEquals(original,p.target.readBytes())
        assertEquals(metadata,SettingsBackup.export(c).getJSONObject("metadata").toString())
        assertTrue(phases.contains("Checking archive, messages and media…"))
        assertTrue(File(c.filesDir,"history-import").listFiles().orEmpty().isEmpty())
    }
    @Test fun failedAndCancelledImportClearCopiesKeepOriginalAndCannotEnableRestore() {
        val p=fixture();p.target.writeText("not a ZIP")
        assertTrue(assertThrows(Exception::class.java){HistoryImport.inspect(c,target,HistoryBackup.Stop()) {}}.message.orEmpty().contains("ZIP"))
        assertEquals("not a ZIP",p.target.readText());assertTrue(File(c.filesDir,"history-import").listFiles().orEmpty().isEmpty())
        HistoryBackup.export(c,target,HistoryBackup.Stop()){};val original=p.target.readBytes();val stop=HistoryBackup.Stop()
        assertThrows(Exception::class.java){HistoryImport.inspect(c,target,stop){if(it=="Checking archive, messages and media…")stop.cancel()}}
        assertTrue(stop.signal.isCanceled);assertArrayEquals(original,p.target.readBytes());assertTrue(File(c.filesDir,"history-import").listFiles().orEmpty().isEmpty())
        assertFalse(MessagingTransport.MMS_READY)
        val bad=JSONObject().put("verified",true).put("provider_writes",true).put("restore_available",true)
        assertThrows(Exception::class.java){HistoryImport.Review.checked(bad)}
        assertThrows(Exception::class.java){HistoryImport.inspect(c,Uri.parse("file:///tmp/unsafe.zip"),HistoryBackup.Stop()) {}}
    }

    @Test fun comparisonMatchesSmsAndMmsAcrossChangedIdsWithoutMutatingProviderOrAppMetadata() {
        val p=fixture();val chat=ChatStore(c)
        chat.requestSync(JSONObject().put("action","message").put("key","sms:1").put("starred",true).put("date",1700000000123L).put("thread",81))
        chat.requestSync(JSONObject().put("action","thread").put("key","81").put("folder","Archive"))
        HistoryBackup.export(c,target,HistoryBackup.Stop()){}
        val review=HistoryImport.inspect(c,target,HistoryBackup.Stop()){}
        val original=p.target.readBytes();val before=SettingsBackup.export(c).getJSONObject("metadata").toString()
        p.offset=200
        val compared=HistoryImport.inspect(c,target,HistoryBackup.Stop(),true,review.sha256){}.comparison!!
        assertEquals(2,compared.matched);assertEquals(0,compared.fresh);assertEquals(0,compared.needsReview)
        assertEquals(1,compared.flagsMapped);assertEquals(1,compared.threadsMapped)
        assertEquals(1,p.writes);assertArrayEquals(original,p.target.readBytes())
        assertEquals(before,SettingsBackup.export(c).getJSONObject("metadata").toString())
        assertTrue(File(c.filesDir,"history-import").listFiles().orEmpty().isEmpty());assertFalse(MessagingTransport.MMS_READY)
    }
    @Test fun comparisonPermissionChangeAndCancellationInvalidateResultsAndRemoveSnapshots() {
        val p=fixture();HistoryBackup.export(c,target,HistoryBackup.Stop()){}
        val review=HistoryImport.inspect(c,target,HistoryBackup.Stop()){};val original=p.target.readBytes()
        shadowOf(c as android.app.Application).denyPermissions(Manifest.permission.READ_SMS);val before=p.queries
        assertTrue(assertThrows(Exception::class.java){HistoryImport.inspect(c,target,HistoryBackup.Stop(),true,review.sha256){}}.message.orEmpty().contains("SMS access"));assertEquals(before,p.queries)
        shadowOf(c as android.app.Application).grantPermissions(Manifest.permission.READ_SMS);p.mutate=true
        assertTrue(assertThrows(Exception::class.java){HistoryImport.inspect(c,target,HistoryBackup.Stop(),true,review.sha256){}}.message.orEmpty().contains("Messages changed"))
        p.mutate=false;val stop=HistoryBackup.Stop()
        assertThrows(Exception::class.java){HistoryImport.inspect(c,target,stop,true,review.sha256){if(it=="Comparing message content and media…")stop.cancel()}}
        assertTrue(stop.signal.isCanceled);assertTrue(File(c.filesDir,"history-import").listFiles().orEmpty().isEmpty());assertArrayEquals(original,p.target.readBytes());assertEquals(1,p.writes)
    }
    @Test fun comparisonIsBoundToThePreviouslyCheckedArchiveAndRejectsChangedFiles() {
        val p=fixture();HistoryBackup.export(c,target,HistoryBackup.Stop()){}
        assertTrue(assertThrows(Exception::class.java){HistoryImport.inspect(c,target,HistoryBackup.Stop(),true,"0".repeat(64)){}}.message.orEmpty().contains("Backup changed"))
        assertTrue(File(c.filesDir,"history-import").listFiles().orEmpty().isEmpty());assertEquals(1,p.writes)
    }

}
