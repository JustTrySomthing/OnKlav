package app.onklav.home

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MessageVideoPreviewsTest {
    private fun png()=ByteArrayOutputStream().also {out->
        Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.GREEN);compress(Bitmap.CompressFormat.PNG,100,out);recycle()}
    }.toByteArray()
    @Test fun linksAreCanonicalDeduplicatedBoundedAndKeepStartTimes() {
        val id="AbC_def-123"
        assertEquals(MessageVideo(id,83),MessageVideoLinks.parse("https://youtu.be/$id?t=1m23s."))
        assertEquals(MessageVideo(id,42),MessageVideoLinks.parse("http://m.youtube.com/watch?v=$id#t=42"))
        assertEquals(MessageVideo(id,86400),MessageVideoLinks.parse("https://www.youtube-nocookie.com/embed/$id?start=999999"))
        assertEquals(0,MessageVideoLinks.seconds("garbage"))
        for(raw in listOf("https://youtube.com.evil.test/watch?v=$id","https://user@youtube.com/watch?v=$id","https://youtu.be:8443/$id","https://youtu.be/$id/extra","https://youtube.com/watch?v=bad","https://youtube.com/redirect?q=https://evil.test"))assertNull(raw,MessageVideoLinks.parse(raw))
        assertEquals(1,MessageVideoLinks.find("Video: youtube.com/watch?v=$id and https://youtu.be/$id").size)
        assertTrue(MessageVideoLinks.find("xhttps://youtu.be/$id someone@youtube.com/watch?v=$id").isEmpty())
        val many=(0..7).joinToString(" "){"https://youtu.be/AbC_def-12$it"}
        assertEquals(4,MessageVideoLinks.find(many).size)
        assertTrue(MessageVideoLinks.find("x".repeat(10001)+" https://youtu.be/$id").isEmpty())
    }
    @Test fun cardsReplaceOnlyPlayableLinksAndPreviewsDefaultOn(){
        val c=ApplicationProvider.getApplicationContext<Context>()
        c.getSharedPreferences("privacy",0).edit().remove("youtube").commit()
        assertTrue(MessageVideoPreviews.allowed(c))
        assertEquals("Watch this: . More: https://example.org",MessageVideoLinks.displayText("Watch this: https://youtu.be/AbC_def-123. More: https://example.org"))
        assertEquals("",MessageVideoLinks.displayText("https://youtu.be/AbC_def-123"))
        assertEquals("https://youtube.com/watch?v=bad",MessageVideoLinks.displayText("https://youtube.com/watch?v=bad"))
        val many=(0..4).joinToString(" "){"https://youtu.be/AbC_def-12$it"}
        assertTrue(MessageVideoLinks.displayText(many).contains("AbC_def-124"))
        MessageVideoPreviews.setAllowed(c,false)
    }
    @Test fun offMeansNoFetchOrCacheUseAndRevokeDropsStaleResults()=runBlocking {
        val c=ApplicationProvider.getApplicationContext<Context>()
        val id="AbC_def-123";val bytes=png();var fetches=0
        MessageVideoPreviews.setAllowed(c,false)
        assertNull(MessageVideoPreviews.load(c,id,download={_,_->fetches++;bytes}));assertEquals(0,fetches)
        MessageVideoPreviews.setAllowed(c,true)
        assertNotNull(MessageVideoPreviews.load(c,id,download={_,check->check();fetches++;bytes}));assertEquals(1,fetches)
        assertNotNull(MessageVideoPreviews.load(c,id,download={_,_->error("Must use memory cache")}))
        MessageVideoPreviews.setAllowed(c,false)
        assertNull(MessageVideoPreviews.load(c,id,download={_,_->error("Off must not fetch")}))
        MessageVideoPreviews.setAllowed(c,true)
        assertNull(MessageVideoPreviews.load(c,id,download={_,_->
            MessageVideoPreviews.setAllowed(c,false);MessageVideoPreviews.setAllowed(c,true);bytes
        }))
        assertNotNull(MessageVideoPreviews.load(c,id,download={_,_->fetches++;bytes}));assertEquals(2,fetches)
        assertNull(MessageVideoPreviews.load(c,id,visible={false},download={_,_->error("Background must not fetch")}))
        MessageVideoPreviews.setAllowed(c,false)
    }
    @Test fun invalidImagesAreNotCachedAndCannotStopThePlayControl()=runBlocking {
        val c=ApplicationProvider.getApplicationContext<Context>()
        MessageVideoPreviews.setAllowed(c,true)
        assertNull(MessageVideoPreviews.load(c,"AbC_def-124",download={_,_->ByteArray(98305)}))
        assertNotNull(MessageVideoPreviews.load(c,"AbC_def-124",download={_,_->png()}))
        MessageVideoPreviews.setAllowed(c,false)
    }
}
