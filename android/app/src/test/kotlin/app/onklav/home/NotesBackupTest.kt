package app.onklav.home

import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotesBackupTest {
    class Provider(val c:Context):ContentProvider(){
        val target=File(c.cacheDir,"notes-backup-provider.json")
        var failWrite=false;var deletions=0
        override fun onCreate()=true
        override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor{
            if(mode!="r"&&failWrite){target.writeText("partial");throw java.io.IOException("Provider write failed")}
            return ParcelFileDescriptor.open(target,if(mode=="r")ParcelFileDescriptor.MODE_READ_ONLY else ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
        }
        override fun call(method:String,arg:String?,extras:Bundle?):Bundle?{
            if(method=="android:deleteDocument"){deletions++;target.delete();return Bundle()};return super.call(method,arg,extras)
        }
        override fun getType(uri:Uri)="application/json"
        override fun query(uri:Uri,p:Array<out String>?,s:String?,a:Array<out String>?,o:String?):Cursor?=null
        override fun insert(uri:Uri,v:ContentValues?):Uri?=error("No provider insertion")
        override fun update(uri:Uri,v:ContentValues?,s:String?,a:Array<out String>?)=error("No provider update")
        override fun delete(uri:Uri,s:String?,a:Array<out String>?)=error("Use document deletion")
    }
    private val c:Context get()=ApplicationProvider.getApplicationContext()
    private val uri=Uri.parse("content://notes-backup-fixture/document/notes")
    private fun provider()=Provider(c).also{it.attachInfo(c,android.content.pm.ProviderInfo().apply{authority="notes-backup-fixture";exported=true;applicationInfo=c.applicationInfo});ShadowContentResolver.registerProviderInternal("notes-backup-fixture",it)}
    private fun backup(id:String,text:String)=JSONObject().put("format","onklav-notes-backup").put("version",1).put("notes",JSONArray().put(JSONObject().put("id",id.repeat(32)).put("title","Portable note").put("text",text).put("updated",12)))
    private fun clean(){listOf("notes-backup-import.json","notes-backup-export.json").forEach{assertFalse(File(c.filesDir,it).exists())}}
    @Test fun realProviderJniRoundTripKeepsCurrentNotesAndNeverExportsGrants()=runBlocking{
        val p=provider();p.target.writeText(backup("d","Saved 🐈").toString())
        val review=NotesBackup.inspect(c,uri);assertEquals(1,review.summary.getInt("new"));clean()
        assertEquals(1,NotesBackup.restore(c,review).getInt("new"));clean()
        assertTrue(NotesBackup.export(c,uri)>0);val saved=JSONObject(p.target.readText());assertFalse(saved.has("grants"));assertFalse(saved.has("packages"))
        assertTrue(saved.getJSONArray("notes").toString().contains("Saved 🐈"));clean()
        p.target.writeText(backup("d","Overwrite attempted").toString());val again=NotesBackup.inspect(c,uri)
        assertEquals(1,NotesBackup.restore(c,again).getInt("kept"));NotesBackup.export(c,uri)
        assertFalse(p.target.readText().contains("Overwrite attempted"));clean()
    }
    @Test fun invalidOrChangedReviewCannotWriteAndActiveAppMustCloseBeforeRestore()=runBlocking{
        val p=provider();p.target.writeText(backup("e","Good").toString());val review=NotesBackup.inspect(c,uri)
        val changed=NotesBackup.Review(backup("f","Changed").toString().toByteArray(),review.summary)
        assertTrue(runCatching{NotesBackup.restore(c,changed)}.isFailure);clean()
        p.target.writeText(backup("e","bad\u0000").toString());assertTrue(runCatching{NotesBackup.inspect(c,uri)}.isFailure);clean()
        AppSdk.bootstrap(c);val launched=AppSdk.sync(c,JSONObject().put("action","launch").put("id","app.onklav.files")) as JSONObject
        try{assertTrue(runCatching{NotesBackup.restore(c,review)}.exceptionOrNull()!!.message!!.contains("Close open apps"))}
        finally{AppSdk.sync(c,JSONObject().put("action","close").put("session",launched.getString("session")))}
        assertEquals(1,NotesBackup.restore(c,review).getInt("new"));clean()
    }
    @Test fun providerWriteFailureKeepsProviderDataAndRemovesPrivateStaging()=runBlocking{
        val p=provider();p.failWrite=true
        assertTrue(runCatching{NotesBackup.export(c,uri)}.isFailure)
        assertEquals(0,p.deletions);assertTrue(p.target.exists());clean()
    }
}
