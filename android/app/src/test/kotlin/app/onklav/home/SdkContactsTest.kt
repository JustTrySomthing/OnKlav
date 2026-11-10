package app.onklav.home

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkContactsTest {
    class Provider : ContentProvider() {
        var calls=0
        var lastUri: Uri?=null
        var lastProjection: Array<out String>?=null
        var rows: List<Array<String?>> = listOf(arrayOf("Charles", "+1 (555) 010-1234"), arrayOf("Other", "5559999"))
        var closed=false
        override fun onCreate()=true
        override fun query(uri:Uri, projection:Array<out String>?, selection:String?, args:Array<out String>?, sort:String?):Cursor {
            calls++;lastUri=uri;lastProjection=projection
            return object : MatrixCursor(arrayOf(Phone.DISPLAY_NAME,Phone.NUMBER)) {
                override fun close() {closed=true;super.close()}
            }.apply {rows.forEach {addRow(it)}}
        }
        override fun getType(uri:Uri)=Phone.CONTENT_ITEM_TYPE
        override fun insert(uri:Uri,values:ContentValues?):Uri?=null
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=0
        override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=0
    }
    @Test fun selectedRowOnlyAndPermissionChecksSurroundProviderRead() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val provider=Provider();ShadowContentResolver.registerProviderInternal("sdk-contacts-fixture",provider)
        val uri=Uri.parse("content://sdk-contacts-fixture/data/7")
        var checks=0
        val chosen=SdkContacts.selected(context,uri) {checks++}
        assertEquals(2,checks);assertEquals(uri,provider.lastUri)
        assertArrayEquals(arrayOf(Phone.DISPLAY_NAME,Phone.NUMBER),provider.lastProjection)
        assertEquals(2,chosen.length());assertEquals("Charles",chosen.getString("name"));assertEquals("+1 (555) 010-1234",chosen.getString("phone"));assertTrue(provider.closed)
        assertEquals(Intent.ACTION_PICK,SdkContacts.intent().action);assertEquals(Phone.CONTENT_URI,SdkContacts.intent().data)
        assertTrue(runCatching {SdkContacts.selected(context,uri) {error("Revoked")}}.isFailure)
        assertEquals(1,provider.calls)
        checks=0
        assertTrue(runCatching {SdkContacts.selected(context,uri) {if(++checks==2) error("Revoked during read")}}.isFailure)
        assertTrue(provider.closed)
    }
    @Test fun missingInvalidAndOversizedSelectionsFailWithoutLeakingContacts() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val provider=Provider();ShadowContentResolver.registerProviderInternal("sdk-contacts-fixture",provider)
        val uri=Uri.parse("content://sdk-contacts-fixture/data/7")
        assertTrue(runCatching {SdkContacts.selected(context,Uri.parse("file:///tmp/contacts")) {}}.isFailure)
        assertEquals(0,provider.calls)
        for(rows in listOf<List<Array<String?>>>(emptyList(),listOf(arrayOf("No number",null)),listOf(arrayOf("Oversized","1".repeat(65))))) {
            provider.rows=rows
            assertTrue(runCatching {SdkContacts.selected(context,uri) {}}.isFailure)
            assertTrue(provider.closed)
        }
        provider.rows=listOf(arrayOf("x".repeat(79)+"😀\n", "5551234"))
        val name=SdkContacts.selected(context,uri) {}.getString("name")
        assertEquals(79,name.length);assertFalse(name.any {it.isISOControl()})
    }
}
