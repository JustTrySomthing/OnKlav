package app.onklav.home

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoCanvasMetadataTest {
    private fun file()=File(ApplicationProvider.getApplicationContext<Context>().cacheDir,"${videoId()}.media")
    @Test fun portraitAndRotatedPhotoFilesChooseTheirDisplayShape(){
        val photo=file()
        try{
            val bitmap=Bitmap.createBitmap(90,160,Bitmap.Config.ARGB_8888)
            photo.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)};bitmap.recycle()
            assertEquals("9:16",videoInitialCanvasRatio(photo,true,"16:9"))
            android.media.ExifInterface(photo.path).apply{
                setAttribute(android.media.ExifInterface.TAG_ORIENTATION,"6");saveAttributes()
            }
            assertEquals("16:9",videoInitialCanvasRatio(photo,true,"1:1"))
            val thumbnail=requireNotNull(videoPhotoThumbnail(photo,80))
            try{assertEquals(80,thumbnail.width);assertEquals(45,thumbnail.height)}finally{thumbnail.recycle()}
        }finally{photo.delete()}
    }
    @Test fun videoMetadataRotationAndUnreadableFilesUseSafeShapes(){
        val video=file().apply{writeText("metadata fixture")}
        try{
            ShadowMediaMetadataRetriever.addMetadata(video.path,MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH,"1920")
            ShadowMediaMetadataRetriever.addMetadata(video.path,MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,"1080")
            ShadowMediaMetadataRetriever.addMetadata(video.path,MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION,"90")
            assertEquals("9:16",videoInitialCanvasRatio(video,false,"16:9"))
            assertEquals("4:5",videoInitialCanvasRatio(file(),false,"4:5"))
            assertEquals("1:1",videoInitialCanvasRatio(video,true,"1:1"))
        }finally{video.delete()}
    }
}
