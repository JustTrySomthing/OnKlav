package app.onklav.home
import android.content.Context
import android.graphics.Bitmap
import android.util.TypedValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import app.onklav.shared.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearancePolishTest {
    private val c:Context get()=ApplicationProvider.getApplicationContext()
    private fun ratio(a:Color,b:Color)=(maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)
    @Test fun everyPaletteHasReadableControlsAndNoDefaultSecondaryAccent() {
        for(dark in listOf(false,true))for(palette in 0..14) {
            val s=appearanceColors(AppearanceSpec(dark=dark,palette=palette))
            assertEquals(s.primary,s.secondary);assertEquals(s.primary,s.tertiary)
            for((fill,ink) in listOf(s.primary to s.onPrimary,s.secondaryContainer to s.onSecondaryContainer,s.tertiaryContainer to s.onTertiaryContainer,s.surface to s.onSurface,s.surface to s.onSurfaceVariant,s.background to s.onBackground,s.primaryContainer to s.onPrimaryContainer,s.error to s.onError,s.errorContainer to s.onErrorContainer,s.inverseSurface to s.inverseOnSurface))assertTrue("dark=$dark palette=$palette contrast=${ratio(fill,ink)}",ratio(fill,ink)>=4.5f)
            assertEquals(s.surface,s.surfaceContainerHigh);assertEquals(s.surface,s.surfaceContainerHighest)
        }
    }
    @Test fun grayscaleNeutralSurfacesStayNeutral() {
        for(dark in listOf(true,false))for(color in appearanceColors(AppearanceSpec(dark=dark,palette=7)).let{listOf(it.surface,it.background,it.secondaryContainer,it.surfaceContainer,it.inverseSurface)}) {assertEquals(color.red,color.green,.001f);assertEquals(color.green,color.blue,.001f)}
    }
    @Test @Config(sdk=[35]) fun nativePickersUseCurrentPaletteAndFontOnAndroid15() {
        for(dark in listOf(false,true))for(palette in 0..14) {
            val spec=AppearanceSpec(dark=dark,palette=palette,font=4);val wrapped=appearanceDialogContext(c,spec);val color=TypedValue();val family=TypedValue()
            assertTrue(wrapped.theme.resolveAttribute(android.R.attr.colorAccent,color,true));assertEquals(appearanceColors(spec).primary.toArgb(),color.data)
            assertTrue(wrapped.theme.resolveAttribute(android.R.attr.fontFamily,family,true));assertEquals("monospace",family.string.toString())
        }
    }
    @Test fun memoryPressureDropsBackgroundCacheWithoutRecyclingDisplayedImages() {
        AppearanceStore.trimMemory();val store=AppearanceStore(c);val first=requireNotNull(store.bitmap("cosmos",64));assertSame(first,store.bitmap("cosmos",64))
        AppearanceStore.trimMemory();assertFalse(first.isRecycled);val second=requireNotNull(store.bitmap("cosmos",64));assertNotSame(first,second);assertFalse(second.isRecycled)
        assertThrows(IllegalArgumentException::class.java){store.bitmap("cosmos",0)}
    }
    @Test fun annotationChecksHeaderDimensionsBeforeFullDecode() {
        val image=Bitmap.createBitmap(100,50,Bitmap.Config.ARGB_8888);val bytes=ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.JPEG,80,bytes);image.recycle()
        val doc=JSONObject().put("image",android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP)).put("width",100).put("height",50)
        val decoded=SdkDrawingImages.image(doc);assertEquals(100,decoded.width);decoded.recycle();doc.put("width",50000);assertThrows(IllegalArgumentException::class.java){SdkDrawingImages.image(doc)}
    }
    @Test fun exportedFilesAndMessageFilesRejectLinkedTargets() {
        val secret=File(c.filesDir,"private-fixture").apply{writeText("keep private")};val uri=ExportProvider.stage(c,"Example.txt","public copy".toByteArray())
        val exports=ExportProvider().apply{attachInfo(c,android.content.pm.ProviderInfo().apply{authority=c.packageName+".exports"})}
        exports.openFile(uri,"r").close();assertThrows(FileNotFoundException::class.java){exports.openFile(uri,"rw")}
        val copy=File(c.cacheDir,"exports/${uri.lastPathSegment}");assertTrue(copy.delete());Files.createSymbolicLink(copy.toPath(),secret.toPath());assertThrows(FileNotFoundException::class.java){exports.openFile(uri,"r")};copy.delete()
        val messageUri=MessageTransferProvider.create(c,"image/jpeg");val id=messageUri.lastPathSegment!!.substringBefore('.');val message=MessageTransferProvider.file(c,id);assertTrue(message.delete());Files.createSymbolicLink(message.toPath(),secret.toPath())
        val provider=MessageTransferProvider().apply{attachInfo(c,android.content.pm.ProviderInfo().apply{authority=c.packageName+".message-files"})};assertThrows(FileNotFoundException::class.java){provider.openFile(messageUri,"wt")};message.delete();assertEquals("keep private",secret.readText())
    }
}
