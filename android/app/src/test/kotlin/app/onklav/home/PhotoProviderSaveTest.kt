package app.onklav.home

import android.app.Application
import android.content.*
import android.content.pm.ProviderInfo
import android.database.*
import android.graphics.*
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.*
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoProviderSaveTest {
 class Provider:ContentProvider(){lateinit var file:File;var writes=0;override fun onCreate()=true;override fun getType(u:Uri)="image/png";override fun query(u:Uri,p:Array<out String>?,s:String?,a:Array<out String>?,o:String?)=MatrixCursor(p?:arrayOf(OpenableColumns.DISPLAY_NAME)).apply{addRow(columnNames.map{when(it){OpenableColumns.DISPLAY_NAME->"Phone.png";DocumentsContract.Document.COLUMN_FLAGS->DocumentsContract.Document.FLAG_SUPPORTS_WRITE;else->file.length()}})};override fun openFile(u:Uri,mode:String):ParcelFileDescriptor{if(mode.contains('w'))writes++;return ParcelFileDescriptor.open(file,ParcelFileDescriptor.parseMode(mode))};override fun insert(u:Uri,v:ContentValues?):Uri?=null;override fun update(u:Uri,v:ContentValues?,s:String?,a:Array<out String>?)=0;override fun delete(u:Uri,s:String?,a:Array<out String>?)=0}
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private fun image(color:Int):File=File.createTempFile("provider-photo-",".png",c.cacheDir).also{f->val b=Bitmap.createBitmap(24,16,Bitmap.Config.ARGB_8888);b.eraseColor(color);f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 private fun provider():Provider=Provider().apply{file=image(Color.RED);attachInfo(c,ProviderInfo().apply{authority="photo.provider";exported=false;grantUriPermissions=true});ShadowContentResolver.registerProviderInternal("photo.provider",this)}
 private val uri=Uri.parse("content://photo.provider/photo")
 @Test fun writableProviderRetainsPreviousBytesAndSaveBackUsesOriginalFormat(){val p=provider();val original=p.file.readBytes();val origin=PhotoImageSave.origin(c,uri,p.file);assertTrue(origin.getBoolean("writable"));val encoded=image(Color.BLUE);val updated=PhotoImageSave.replace(c,origin,encoded,{})
  assertArrayEquals(encoded.readBytes(),p.file.readBytes());assertArrayEquals(original,File(c.filesDir,"photo-editor/previous-image").readBytes());assertEquals(PhotoImageSave.digest(encoded),updated.getString("digest"));assertEquals("image/png",org.json.JSONObject(File(c.filesDir,"photo-editor/previous-info.json").readText()).getString("mime"))
 }
 @Test fun revocationAfterOpeningDestinationDoesNotAttemptAnotherWrite(){val p=provider();val original=p.file.readBytes();val origin=PhotoImageSave.origin(c,uri,p.file);val encoded=image(Color.BLUE);assertThrows(IllegalStateException::class.java){PhotoImageSave.replace(c,origin,encoded,{check(p.writes==0){"revoked"}})};assertEquals(1,p.writes);assertArrayEquals(original,File(c.filesDir,"photo-editor/previous-image").readBytes())}
 @Test fun providerFailureRestoresOriginalWhenPermissionStillAllowsIt(){val p=provider();val original=p.file.readBytes();val origin=PhotoImageSave.origin(c,uri,p.file);val encoded=image(Color.BLUE);var failed=false;assertThrows(IllegalStateException::class.java){PhotoImageSave.replace(c,origin,encoded,{if(p.writes==1&&!failed){failed=true;throw java.io.IOException("interrupted")}})};assertEquals(2,p.writes);assertArrayEquals(original,p.file.readBytes())}
}
