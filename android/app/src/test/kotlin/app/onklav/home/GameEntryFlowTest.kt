package app.onklav.home

import android.app.AlertDialog
import android.content.Intent
import android.os.Looper
import android.webkit.*
import android.net.Uri
import android.widget.Button
import android.widget.LinearLayout
import java.io.ByteArrayInputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class GameEntryFlowTest {
    private fun field(name:String)=WebGameActivity::class.java.getDeclaredField(name).apply{isAccessible=true}
    private fun store(id:String)=GameSaveStore(ApplicationProvider.getApplicationContext<android.content.Context>().noBackupFilesDir,"onklav.builtin.$id")
    private fun ready(id:String):org.robolectric.android.controller.ActivityController<WebGameActivity> {
        val c=Robolectric.buildActivity(WebGameActivity::class.java,Intent().putExtra("game",id)).create().start().resume()
        (field("worker").get(c.get()) as ExecutorService).submit{}.get(10,TimeUnit.SECONDS)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return c
    }
    private fun client(a:WebGameActivity)=WebGameActivity::class.java.getDeclaredMethod("gameClient")
        .apply{isAccessible=true}.invoke(a) as WebViewClient
    private fun request(main:Boolean)=object:WebResourceRequest {
        override fun getUrl()=Uri.parse("https://builtin.onklav.invalid/web/"+if(main)"index.html"else"missing.png")
        override fun isForMainFrame()=main
        override fun isRedirect()=false
        override fun hasGesture()=false
        override fun getMethod()="GET"
        override fun getRequestHeaders():Map<String,String> = emptyMap()
    }
    private fun panel(a:WebGameActivity)=field("panel").get(a) as LinearLayout
    private fun renderer(a:WebGameActivity):WebView {
        (field("entryDialog").get(a) as? AlertDialog)?.dismiss()
        val view=WebView(a);panel(a).addView(view);field("web").set(a,view);return view
    }
    @Test @Config(sdk=[29,35]) fun mainFrameHttpFailureOffersRetryWithoutChangingSave() {
        val saved=store("hockey");saved.commit(saved.begin(),"resume",JSONObject().put("score",3),1,true)
        val c=ready("hockey").visible();val a=c.get();val view=renderer(a);val callback=client(a)
        val blocked=WebResourceResponse("text/plain","UTF-8",403,"Blocked",emptyMap(),ByteArrayInputStream(byteArrayOf()))
        callback.onReceivedHttpError(view,request(false),blocked)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertSame("An optional resource error must not close the game",view,field("web").get(a))
        callback.onReceivedHttpError(view,request(true),blocked)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNull(field("web").get(a));assertFalse(field("active").getBoolean(a))
        assertEquals(3,saved.load("resume").getJSONObject("record").getJSONObject("state").getInt("score"))
        val retry=(0 until panel(a).childCount).map{panel(a).getChildAt(it)}.filterIsInstance<Button>().single{it.text=="Try again"}
        retry.performClick()
        assertEquals("hockey",Shadows.shadowOf(a).nextStartedActivity.getStringExtra("game"))
        c.pause().stop().destroy()
    }
    @Test fun lateNavigationFailureCannotCloseTheReplacementRenderer() {
        val c=ready("panda").visible();val a=c.get();val retired=renderer(a);val callback=client(a)
        val error=WebResourceResponse("text/plain","UTF-8",404,"Not found",emptyMap(),ByteArrayInputStream(byteArrayOf()))
        callback.onReceivedHttpError(retired,request(true),error)
        panel(a).removeView(retired);retired.destroy()
        val current=renderer(a)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertSame(current,field("web").get(a))
        callback.onPageFinished(current,"about:blank")
        assertFalse("A blank page must not activate the game",field("active").getBoolean(a))
        callback.onReceivedHttpError(current,request(true),error)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNull(field("web").get(a));assertFalse(field("active").getBoolean(a))
        c.pause().stop().destroy()
    }
    @Test fun stalledIncludedLoaderCanSwitchGamesWithoutDiscardingSavedProgress() {
        val saved=store("panda");saved.commit(saved.begin(),"resume",JSONObject().put("level",4),1,true)
        val c=ready("panda");val a=c.get()
        field("web").set(a,WebView(a));field("active").setBoolean(a,true)
        field("gameReady").setBoolean(a,false)
        c.newIntent(Intent(a,WebGameActivity::class.java).putExtra("game","hockey"))
        assertTrue(a.isFinishing)
        assertEquals("hockey",Shadows.shadowOf(a).nextStartedActivity.getStringExtra("game"))
        assertEquals(4,saved.load("resume").getJSONObject("record").getJSONObject("state").getInt("level"))
        c.pause().stop().destroy()
    }
    @Test fun backCanLeaveIncludedLoaderBeforeSessionReady() {
        val c=ready("hockey");val a=c.get()
        field("web").set(a,WebView(a));field("active").setBoolean(a,true)
        field("gameReady").setBoolean(a,false)
        a.onBackPressed();assertTrue(a.isFinishing)
        c.pause().stop().destroy()
    }
    @Test fun gameWindowSupportsThePortraitAndLandscapeOrientationRequests() {
        val context=android.view.ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
            R.style.OnKlavGameEntryTheme,
        )
        val attributes=context.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent))
        try { assertFalse(attributes.getBoolean(0,false)) } finally { attributes.recycle() }
    }
    @Test fun noSaveShowsStartBeforeAnyWebViewAndCancelLoadsNothing(){
        val store=store("dominos");store.clear(store.begin(),"resume")
        val c=ready("dominos");val a=c.get()
        assertNull(field("web").get(a));assertNotNull(field("entryView").get(a));assertNull(field("entryDialog").get(a))
        assertEquals("",field("launchAction").get(a));assertFalse(field("active").getBoolean(a))
        c.pause().stop().destroy()
    }
    @Test fun resumeChoiceIsResolvedBeforeLoadingAndOnlyForSelectedGame(){
        val p=store("panda");p.commit(p.begin(),"resume",JSONObject().put("level",3),1,true)
        val h=store("hockey");h.clear(h.begin(),"resume")
        val other=ready("hockey");assertNull(field("entryDialog").get(other.get()));assertNull(field("web").get(other.get()));other.pause().stop().destroy()
        val c=ready("panda");val a=c.get();val dialog=field("entryDialog").get(a) as AlertDialog
        assertNull(field("web").get(a));assertEquals("Resume",dialog.getButton(AlertDialog.BUTTON_POSITIVE).text)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(a.isFinishing)
        assertTrue(p.load("resume").getBoolean("exists"));c.pause().stop().destroy()
    }
    @Test fun newChoiceKeepsPreviousSaveUntilStartAndQuitTombstoneOffersOnlyStart(){
        val s=store("four");s.commit(s.begin(),"resume",JSONObject().put("board",5),1,true)
        val c=ready("four");val a=c.get();val dialog=field("entryDialog").get(a) as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(field("entryView").get(a));assertNull(field("web").get(a));assertEquals("",field("launchAction").get(a))
        assertEquals(5,s.load("resume").getJSONObject("record").getJSONObject("state").getInt("board"));c.pause().stop().destroy()
        s.clear(s.begin(),"resume")
        val next=ready("four");assertNull(field("entryDialog").get(next.get()));assertNotNull(field("entryView").get(next.get()));next.pause().stop().destroy()
    }
    @Test fun completedGameHasNoResumePrompt(){
        val s=store("blackjack");s.commit(s.begin(),"resume",JSONObject().put("finished",true),1,false)
        val c=ready("blackjack");assertNull(field("entryDialog").get(c.get()));assertNotNull(field("entryView").get(c.get()));c.pause().stop().destroy()
    }
    @Test fun explicitResumeBypassesSecondEntryDialog(){
        val s=store("panda");s.commit(s.begin(),"resume",JSONObject().put("level",1),1,true)
        val c=ready("panda");val a=c.get();val d=field("entryDialog").get(a) as AlertDialog
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals("resume",field("launchAction").get(a));assertNull(field("entryDialog").get(a));assertNull(field("entryView").get(a))
        c.pause().stop().destroy()
    }
    @Test fun hostRejectsLateSessionSaveAfterAcknowledgedDiscard(){
        val c=ready("panda");val a=c.get();val view=WebView(a);field("web").set(a,view)
        val command=WebGameActivity::class.java.getDeclaredMethod("saveCommand",WebView::class.java,String::class.java).apply{isAccessible=true}
        command.invoke(a,view,JSONObject().put("request",1).put("op","clear").put("slot","session").toString())
        command.invoke(a,view,JSONObject().put("request",2).put("op","save").put("slot","session").put("state",JSONObject().put("late",true)).toString())
        (field("worker").get(a) as ExecutorService).submit{}.get(10,TimeUnit.SECONDS)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val saved=store("panda").load("resume");assertTrue(saved.getBoolean("known"));assertFalse(saved.getBoolean("exists"))
        c.pause().stop().destroy()
    }
    @Test fun friendNewRetainsTheHostStorageProtocolWhenSwitchingToSolo(){
        val s=store("four");s.commit(s.begin(),"resume",JSONObject().put("board",7),1,true)
        val c=ready("four");val a=c.get()
        field("friend").set(a,JSONObject().put("id","ended-test-match"));field("saveSlot").set(a,"friend.ended-test-match")
        val d=field("entryDialog").get(a) as AlertDialog
        d.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNull(field("friend").get(a));assertEquals("resume",field("saveSlot").get(a))
        assertEquals(2,(field("saveBoot").get(a) as JSONObject).getInt("protocol"))
        assertNotNull(field("entryView").get(a));assertNull(field("web").get(a))
        c.pause().stop().destroy()
    }
    @Test fun retiredFriendIntentCannotSelectPandasFriendSaveSlot() {
        val s=store("panda");s.clear(s.begin(),"resume")
        val intent=Intent().putExtra("game","panda").putExtra("friend_match","retired-pt3-match")
        val c=Robolectric.buildActivity(WebGameActivity::class.java,intent).create().start().resume()
        (field("worker").get(c.get()) as ExecutorService).submit{}.get(10,TimeUnit.SECONDS)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNull(field("friend").get(c.get()));assertEquals("resume",field("saveSlot").get(c.get()))
        assertNull(field("web").get(c.get()));assertNotNull(field("entryView").get(c.get()))
        c.pause().stop().destroy()
    }
    @Test fun backFromEntryClosesToListWithoutAnySaveWarning(){
        val s=store("dominos");s.clear(s.begin(),"resume")
        val c=ready("dominos");val a=c.get()
        WebGameActivity::class.java.getDeclaredMethod("requestReturn").apply{isAccessible=true}.invoke(a)
        assertTrue(a.isFinishing);assertNull(field("web").get(a));assertFalse(field("returning").getBoolean(a))
        c.pause().stop().destroy()
    }
}
