package app.onklav.home

import android.app.Application
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoSaveTest {
 private val c get()=ApplicationProvider.getApplicationContext<Application>()
 private fun image(color:Int):File {val f=File.createTempFile("photo-fixture-",".png",c.cacheDir);val b=Bitmap.createBitmap(24,16,Bitmap.Config.ARGB_8888);b.eraseColor(color);f.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle();return f}
 private fun fixture():Pair<JSONObject,File>{runBlocking{AppSdk.bootstrap(c)};val f=image(Color.RED);val descriptor=FileSelectionSession(c).use{s->s.start();val saved=s.importSaved(listOf("images"),f,"Canvas.png","image/png");FileSelectionSession(c).use{q->q.start();q.navigate(listOf("images"));q.choose(q.state.nodes("items").first{it.optString("id")==saved.getString("id")})}}
  val uri=FileSelectionStore.selected(c,descriptor,listOf("images"));return JSONObject().put("uri",uri.toString()).put("name",descriptor.getString("name")).put("mime","image/png").put("digest",PhotoImageSave.digest(f)).put("writable",true)to f
 }
 private fun active()=FileSelectionSession(c).use{s->s.start();s.navigate(listOf("images"));s.state.nodes("items")}
 @Test fun interruptedOpenNeverBindsNewSourceToThePreviousTarget(){
  runBlocking{AppSdk.bootstrap(c)};val dir=File(c.filesDir,"photo-editor").apply{mkdirs()};val red=image(Color.RED);val blue=image(Color.BLUE)
  val old=JSONObject().put("uri","content://previous/photo").put("digest",PhotoImageSave.digest(red)).put("writable",true)
  val next=JSONObject().put("uri","content://next/photo").put("digest",PhotoImageSave.digest(blue)).put("writable",true)
  red.copyTo(File(dir,"source"),true);PhotoImageSave.atomic(File(dir,"origin.json"),old);PhotoImageSave.atomic(File(dir,"edits.json"),JSONObject().put("angle",23));PhotoImageSave.atomic(File(dir,"opening.json"),next)
  PhotoImageSave.recoverOpening(c){check(photoAccess(c))};assertEquals(old.toString(),File(dir,"origin.json").readText());assertEquals(23,JSONObject(File(dir,"edits.json").readText()).getInt("angle"))
  blue.copyTo(File(dir,"source"),true);PhotoImageSave.atomic(File(dir,"opening.json"),next);PhotoImageSave.recoverOpening(c){check(photoAccess(c))};assertEquals(next.toString(),File(dir,"origin.json").readText());assertEquals(0,JSONObject(File(dir,"edits.json").readText()).length());assertFalse(File(dir,"opening.json").exists())
 }
 @Test fun saveChangesReplacesNamedImageAndRetainsPreviousVersionInTrash(){val(origin,original)=fixture();val encoded=image(Color.BLUE);val uri=android.net.Uri.parse(origin.getString("uri"));val old=FileSelectionStore.read(c,uri).getString("entry")
  val updated=PhotoImageSave.replace(c,origin,encoded,{check(photoAccess(c))});val selected=FileSelectionStore.read(c,uri);assertNotEquals(old,selected.getString("entry"));assertEquals(origin.getString("name"),selected.getString("name"));assertEquals(PhotoImageSave.digest(encoded),updated.getString("digest"));assertArrayEquals(encoded.readBytes(),FileSelectionStore.file(c,selected).readBytes());assertEquals(1,active().count{it.getString("name")==origin.getString("name")})
  FileSelectionSession(c).use{s->s.start();s.navigate(listOf("trash"));assertTrue(s.state.nodes("items").any{it.getString("id")==old})};assertArrayEquals(original.readBytes(),File(c.filesDir,"onklav-content/objects/$old").readBytes())
 }
 @Test fun failureAfterArchivingRestoresOriginalAndNeverLosesEitherImage(){val(origin,original)=fixture();val encoded=image(Color.BLUE);assertThrows(IllegalStateException::class.java){PhotoImageSave.replace(c,origin,encoded,{check(photoAccess(c))}){error("simulated failed rename")}}
  val uri=android.net.Uri.parse(origin.getString("uri"));assertArrayEquals(original.readBytes(),FileSelectionStore.file(c,FileSelectionStore.read(c,uri)).readBytes());assertEquals(1,active().count{it.getString("name")==origin.getString("name")});assertFalse(File(c.filesDir,"photo-editor/replace.json").exists())
 }
 @Test fun processInterruptionJournalRecoversOriginalOnNextLaunch(){val(origin,original)=fixture();val encoded=image(Color.BLUE);assertThrows(AssertionError::class.java){PhotoImageSave.replace(c,origin,encoded,{check(photoAccess(c))}){throw AssertionError("simulated process interruption")}}
  assertTrue(File(c.filesDir,"photo-editor/replace.json").exists());assertNull(PhotoImageSave.recover(c){check(photoAccess(c))});assertEquals(1,active().count{it.getString("name")==origin.getString("name")});assertArrayEquals(original.readBytes(),FileSelectionStore.file(c,FileSelectionStore.read(c,android.net.Uri.parse(origin.getString("uri")))).readBytes());assertFalse(File(c.filesDir,"photo-editor/replace.json").exists())
 }
 @Test fun changedSourcesAndRevokedAccessNeverOverwriteTheCurrentImage(){val(origin,original)=fixture();val encoded=image(Color.BLUE);origin.put("digest","0".repeat(64));assertThrows(IllegalStateException::class.java){PhotoImageSave.replace(c,origin,encoded,{check(photoAccess(c))})};assertEquals(1,active().size);origin.put("digest",PhotoImageSave.digest(original));assertThrows(IllegalStateException::class.java){PhotoImageSave.replace(c,origin,encoded,{error("revoked")})};assertEquals(1,active().size);assertArrayEquals(original.readBytes(),FileSelectionStore.file(c,FileSelectionStore.read(c,android.net.Uri.parse(origin.getString("uri")))).readBytes())}
}
