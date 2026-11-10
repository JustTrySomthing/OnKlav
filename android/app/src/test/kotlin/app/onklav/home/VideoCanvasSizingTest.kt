package app.onklav.home

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCanvasSizingTest {
    @Test fun firstSourceUsesItsDisplayOrientation(){
        assertEquals("9:16",videoSourceCanvasRatio(1080,1920))
        assertEquals("9:16",videoSourceCanvasRatio(1920,1080,90))
        assertEquals("9:16",videoSourceCanvasRatio(1920,1080,270))
        assertEquals("16:9",videoSourceCanvasRatio(1920,1080,180))
        assertEquals("1:1",videoSourceCanvasRatio(1080,1080))
        assertEquals("4:5",videoSourceCanvasRatio(1080,1350))
        assertEquals("4:3",videoSourceCanvasRatio(1440,1080))
    }
    @Test fun unreadableDimensionsPreserveTheChosenFallback(){
        assertEquals("4:5",videoSourceCanvasRatio(0,1080,fallback="4:5"))
        assertEquals("16:9",videoSourceCanvasRatio(1920,-1))
    }
}
