package app.onklav.home

import android.content.Intent
import android.os.Looper
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.LinearLayout
import androidx.core.graphics.Insets
import androidx.core.view.DisplayCutoutCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class GameWindowHostTest {
    private fun field(name:String)=WebGameActivity::class.java.getDeclaredField(name).apply{isAccessible=true}
    private fun ready():org.robolectric.android.controller.ActivityController<WebGameActivity> {
        val c=Robolectric.buildActivity(WebGameActivity::class.java,Intent().putExtra("game","four")).create()
        (field("worker").get(c.get()) as ExecutorService).submit{}.get(10,TimeUnit.SECONDS)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return c
    }
    private class TrackedView(a:WebGameActivity):WebView(a) {
        var resumes=0;var pauses=0;var timers=0;var delay=false
        var completion:ValueCallback<String>?=null
        override fun onResume(){resumes++;super.onResume()}
        override fun onPause(){pauses++;super.onPause()}
        override fun resumeTimers(){timers++;super.resumeTimers()}
        override fun evaluateJavascript(script:String,callback:ValueCallback<String>?){
            if(delay)completion=callback else callback?.onReceiveValue("true")
        }
    }
    @Test fun immersiveWindowHasNoExtraHeaderAndRespectsCutout(){
        val c=ready();val a=c.get();c.start().resume()
        val flags=a.window.decorView.systemUiVisibility
        assertTrue(flags and View.SYSTEM_UI_FLAG_FULLSCREEN!=0)
        assertTrue(flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION!=0)
        assertTrue(flags and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY!=0)
        val panel=field("panel").get(a) as LinearLayout
        val insets=WindowInsetsCompat.Builder().setDisplayCutout(DisplayCutoutCompat(android.graphics.Rect(3,24,4,0),emptyList()))
            .setInsets(WindowInsetsCompat.Type.navigationBars(),Insets.of(0,0,0,80)).build()
        ViewCompat.dispatchApplyWindowInsets(panel,insets)
        assertEquals(24,panel.paddingTop);assertEquals(3,panel.paddingLeft)
        assertEquals((8*a.resources.displayMetrics.density).toInt(),panel.paddingBottom)
        // No native title/Pause header is layered above the game.
        for(i in 0 until panel.childCount)assertFalse(panel.getChildAt(i) is LinearLayout)
        c.pause().stop().destroy()
    }
    @Test fun returnClosesRendererAndReordersOnKlavToTheList(){
        val c=ready();val a=c.get();c.start().resume()
        WebGameActivity::class.java.getDeclaredMethod("returnToOnKlav").apply{isAccessible=true}.invoke(a)
        val intent=Shadows.shadowOf(a).nextStartedActivity
        assertEquals(MainActivity::class.java.name,intent.component!!.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT!=0)
        assertTrue(a.isFinishing)
        c.pause().stop().destroy()
    }
    @Test fun resumedWebViewAlwaysRestartsTimersAndIgnoresLatePauseCallback(){
        val c=ready();val a=c.get();val view=TrackedView(a)
        field("web").set(a,view);field("active").set(a,true)
        c.start().resume();assertEquals(1,view.resumes);assertEquals(1,view.timers)
        c.pause();assertEquals(1,view.pauses)
        c.resume();assertEquals(2,view.resumes);assertEquals(2,view.timers)
        view.delay=true;c.pause();val old=view.completion;c.resume()
        old?.onReceiveValue("true");assertEquals(1,view.pauses)
        assertEquals(3,view.resumes);assertEquals(3,view.timers)
        view.delay=false;c.pause().stop().destroy()
    }
}
