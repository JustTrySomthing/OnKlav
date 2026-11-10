package app.onklav.home

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class BackupFilesTest {
    private val c:Context get()=ApplicationProvider.getApplicationContext()
    class Storage:ContentProvider() {
        var opens=0;var deletes=0
        override fun onCreate()=true
        override fun query(u:Uri,p:Array<out String>?,s:String?,a:Array<out String>?,o:String?):Cursor?=null
        override fun getType(u:Uri)="application/octet-stream"
        override fun insert(u:Uri,v:ContentValues?):Uri?=null
        override fun update(u:Uri,v:ContentValues?,s:String?,a:Array<out String>?)=0
        override fun delete(u:Uri,s:String?,a:Array<out String>?)=0
        override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
            opens++;val f=File(context!!.cacheDir,"copy-${uri.lastPathSegment}")
            return ParcelFileDescriptor.open(f,if(mode=="r")ParcelFileDescriptor.MODE_READ_ONLY else ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
        }
        override fun call(method:String,arg:String?,extras:Bundle?):Bundle? {
            if(method=="android:deleteDocument") {deletes++;File(context!!.cacheDir,"copy-destination").delete();return Bundle()}
            return super.call(method,arg,extras)
        }
    }
    private fun provider()=Storage().also { p->p.attachInfo(c,ProviderInfo().apply{authority="copy-fixture"});ShadowContentResolver.registerProviderInternal("copy-fixture",p) }
    private val source=Uri.parse("content://copy-fixture/document/source")
    private val destination=Uri.parse("content://copy-fixture/document/destination")
    @Test fun sameDocumentAliasesRejectBeforeOpeningOrDeletingAnyFile()=runBlocking {
        val p=provider();val original=File(c.cacheDir,"copy-source").apply{writeText("valuable original")}
        for(alias in listOf(source,Uri.parse("content://copy-fixture/tree/root/document/source"),source.buildUpon().appendQueryParameter("display","different").build())) {
            try{BackupDocuments.copy(c,source,alias);fail("Same document accepted")}catch(_:IllegalArgumentException){}
            assertEquals("valuable original",original.readText());assertEquals(0,p.opens);assertEquals(0,p.deletes)
        }
    }
    @Test fun unreadableSourceDoesNotDeleteOrTruncateTheChosenDestination()=runBlocking {
        val p=provider();val target=File(c.cacheDir,"copy-destination").apply{writeText("keep target")}
        File(c.cacheDir,"copy-source").delete()
        try{BackupDocuments.copy(c,source,destination);fail("Missing source accepted")}catch(_:java.io.FileNotFoundException){}
        assertEquals("keep target",target.readText());assertEquals(0,p.deletes)
    }
    @Test fun partialCopyFailureRetainsTheSourceAndNeverDeletesProviderDocuments()=runBlocking {
        val p=provider();File(c.cacheDir,"copy-source").writeText("source remains")
        Shadows.shadowOf(c.contentResolver).registerInputStream(source,object:InputStream(){var sent=false
            override fun read():Int=throw IOException("fixture read failed")
            override fun read(bytes:ByteArray,offset:Int,length:Int):Int {if(sent)throw IOException("fixture read failed");sent=true;bytes[offset]=1;return 1}
        })
        try{BackupDocuments.copy(c,source,destination);fail("Read failure hidden")}catch(_:IOException){}
        assertEquals(0,p.deletes);assertTrue(File(c.cacheDir,"copy-destination").exists());assertEquals("source remains",File(c.cacheDir,"copy-source").readText())
    }
}
