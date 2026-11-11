package app.onklav.home

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class MessageRefreshVCardTest {
    private val card="BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Alex\r\n  Example\r\nitem1.TEL;TYPE=CELL:+15550100001\r\nEND:VCARD\r\n"

    @Test fun anEmptyFirstReadDoesNotHideNewSmsOrLateMmsParts() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.READ_SMS)
        val provider=object:ContentProvider() {
            var arrived=false
            var partsReady=false
            override fun onCreate()=true
            override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sort:String?):MatrixCursor {
                val cols=requireNotNull(projection).map {it}.toTypedArray()
                return MatrixCursor(cols).apply {
                    if(arrived)when {
                        uri.authority=="sms" -> addRow(arrayOf<Any>(1L,"New SMS",1000L,1,-1,"5550100"))
                        uri.pathSegments.lastOrNull()=="addr" -> addRow(arrayOf<Any>("5550100",137))
                        uri.pathSegments.lastOrNull()=="part" && partsReady ->
                            addRow(arrayOf<Any>(12L,"text/plain",card,"text.txt","Alex.VCF"))
                        uri.pathSegments.isEmpty() -> addRow(arrayOf<Any>(2L,2L,1,"",132))
                    }
                }
            }
            override fun getType(uri:Uri):String?=null
            override fun insert(uri:Uri,values:ContentValues?):Uri?=null
            override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?)=0
            override fun delete(uri:Uri,selection:String?,args:Array<out String>?)=0
        }
        provider.attachInfo(app,ProviderInfo().apply{authority="sms;mms";applicationInfo=app.applicationInfo})
        ShadowContentResolver.registerProviderInternal("sms",provider)
        ShadowContentResolver.registerProviderInternal("mms",provider)
        val repo=MessageRepository(app)
        val state=JSONObject()
        assertTrue(repo.messages(9,20,state).isEmpty())
        provider.arrived=true
        val first=repo.messages(9,20,state)
        assertEquals(listOf("sms:1","mms:2"),first.map{it.key})
        assertTrue(first.last().parts.isEmpty())
        provider.partsReady=true
        val message=repo.messages(9,20,state).last()
        assertEquals("",message.text)
        val part=message.parts.single()
        assertEquals("Alex.VCF",part.name)
        assertEquals(MessageMedia.VCARD_MIME,part.mime)
        assertEquals(card,MessageMedia.input(app,part)!!.bufferedReader().use{it.readText()})
        val prepared=MessageMedia.import(app,part,1024)
        val staged=MessageTransferProvider.file(app,prepared.getString("file"))
        try {
            assertEquals(MessageMedia.VCARD_MIME,prepared.getString("mime"))
            assertArrayEquals(card.toByteArray(),staged.readBytes())
        } finally { staged.delete() }
    }

    @Test fun contactCardsStayOutOfPhotoViewerAndAppearInContactCardPicker() {
        val part=MessagePart("content://mms/part/12","application/octet-stream","Alex.VCF")
        val photo=MessagePart("content://mms/part/11","image/jpeg","photo.jpg")
        assertEquals(listOf(photo),conversationMedia(listOf(Message(1,"",0,true,parts=listOf(photo,part)))).map{it.part})
        val pickerRow=JSONObject().put("name",part.name).put("mime",part.mime)
        assertTrue(filePickerAccepts(pickerRow,MessageMedia.vCardTypes.toList()))
        assertFalse(filePickerAccepts(pickerRow.put("name",photo.name).put("mime",photo.mime),MessageMedia.vCardTypes.toList()))
        val preview=vCardPreview(card).single()
        assertEquals("Alex Example",preview.name)
        assertEquals(listOf("Phone" to "+15550100001"),preview.details)
    }
}
