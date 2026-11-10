package app.onklav.home

import android.Manifest
import android.content.*
import android.content.pm.ProviderInfo
import android.database.*
import android.net.Uri
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
class MessageLoadingTest {
 class Threads:ContentProvider(){
  var queries=0
  override fun onCreate()=true
  override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):Cursor {
   queries++
   return if(uri.path.orEmpty().contains("canonical-address"))MatrixCursor(arrayOf("address")).apply{addRow(arrayOf("15550009991"))}
   else MatrixCursor(arrayOf("_id","date","snippet","read","recipient_ids","message_count")).apply{addRow(arrayOf(991,1000,"Cached conversation",1,"991",1))}
  }
  override fun getType(uri:Uri):String?=null
  override fun insert(uri:Uri,values:ContentValues?):Uri?=null
  override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=0
  override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=0
 }
 @Test fun localMetadataChangesReuseProviderRowsAndApplyFreshUnreadState()=runBlocking {
  val c=ApplicationProvider.getApplicationContext<Context>();Shadows.shadowOf(c as android.app.Application).grantPermissions(Manifest.permission.READ_SMS)
  val provider=Threads();provider.attachInfo(c,ProviderInfo().apply{authority="mms-sms"});ShadowContentResolver.registerProviderInternal("mms-sms",provider)
  val repo=MessageRepository(c);val rows=repo.conversations(991,JSONObject());assertEquals(991L,rows.single().id);val queries=provider.queries;assertTrue(queries>=2)
  val state=JSONObject().put("threads",JSONObject().put("991",JSONObject().put("unread",true).put("read_at",1000)))
  assertTrue(repo.conversations(991,state).single().unread);assertEquals(queries,provider.queries)
 }
}
