package app.onklav.home
import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkWebPolicyTest {
    @Test fun addressesAndProfilesStayWithinTheirBoundary() {
        assertEquals("https://example.org/a%2Fb?q=x%26y",SdkWebPolicy.address("http://example.org:80/a%2Fb?q=x%26y"))
        assertEquals("https://example.org:8443/",SdkWebPolicy.address("https://example.org:8443"))
        for(s in listOf("file:///secret","content://contacts","javascript:alert(1)","intent://apps","https://a:b@example.org/","https://accounts.google.com/","https://example.org/\n","https://example.org/\\evil")) assertTrue(s,runCatching { SdkWebPolicy.address(s) }.isFailure)
        assertEquals(SdkWebPolicy.profile("app.example.first"),SdkWebPolicy.profile("app.example.first"))
        assertNotEquals(SdkWebPolicy.profile("app.example.first"),SdkWebPolicy.profile("app.example.second"))
    }
    @Test fun secureBrowserHandsOffOnlyValidatedWebAddresses() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        var launched: android.content.Intent? = null
        val context = object : android.content.ContextWrapper(app) {
            override fun startActivity(intent: android.content.Intent) { launched = intent }
        }
        assertFalse(openSecureBrowser(context, "intent://accounts.google.com/"))
        assertFalse(openSecureBrowser(context, "https://user:password@accounts.google.com/"))
        assertNull(launched)
        val address = "https://accounts.google.com/signin"
        assertTrue(openSecureBrowser(context, address))
        assertEquals(android.content.Intent.ACTION_VIEW, launched?.action)
        assertEquals(address, launched?.dataString)
        assertNull(launched?.`package`)
        assertTrue(launched?.hasExtra("android.support.customtabs.extra.SESSION") == true)
    }
    @Test fun nativeClipboardEntryPointsCheckLiveGrants() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        var allow=false;val checks=mutableListOf<String>()
        val view=SdkGatedWebView(context) { cap -> checks.add(cap);allow }
        assertFalse(view.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE,null))
        assertTrue(checks.contains("clipboard.read"));checks.clear()
        assertFalse(view.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_COPY,null))
        assertTrue(checks.contains("clipboard.write"));checks.clear()
        val paste=android.view.KeyEvent(0,0,android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_V,0,android.view.KeyEvent.META_CTRL_ON)
        assertTrue(view.onKeyDown(android.view.KeyEvent.KEYCODE_V,paste));assertTrue(checks.contains("clipboard.read"))
        allow=true;allow=false;checks.clear()
        assertTrue(view.onKeyDown(android.view.KeyEvent.KEYCODE_V,paste));assertTrue(checks.contains("clipboard.read"))
        view.destroy()
    }
    @Test fun historyMemoryIsBounded() {
        val pool=SdkWebPool()
        for(i in 0..12) pool.save("tab$i",Bundle().apply { putString("url","https://example.org/$i") },i.toLong())
        assertEquals(8,pool.entries.size);assertFalse(pool.entries.containsKey("tab0"))
        val huge=Bundle().apply { putByteArray("history",ByteArray(300000)) }
        assertFalse(SdkWebPolicy.bounded(huge));pool.save("oversized",huge,1);assertFalse(pool.entries.containsKey("oversized"))
    }
    private class Pages(context:android.content.Context):android.webkit.WebView(context) {
        var depth=2;var backs=0;var clears=0
        override fun canGoBack()=depth>0
        override fun goBack() { backs++;depth-- }
        override fun clearHistory() { clears++;depth=0 }
    }
    @Test fun systemBackUsesCurrentWebHistoryAndLiveGrant() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val view=Pages(context);val page=SdkWebPage();val pool=SdkWebPool();var allowed=true
        page.attach(view);pool.onBack={page.back{check(allowed){"Revoked"}}}
        assertTrue(pool.back());assertEquals(1,view.backs)
        allowed=false;assertTrue(pool.back());assertEquals(1,view.backs);assertEquals("Revoked",page.failure)
        allowed=true;assertTrue(pool.back());assertFalse(pool.back())
        page.detach(view);assertFalse(pool.back());pool.onBack=null;assertFalse(pool.back());view.destroy()
    }
    @Test fun rendererReplacementRejectsLateErrorsProgressAndClearCallbacks() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val old=Pages(context);val next=Pages(context);val page=SdkWebPage();var loads=0
        page.attach(old);page.failed(old,"Failed");page.progressed(old,40)
        assertTrue(page.detach(old,true));assertTrue(page.rendererLost);assertEquals(100,page.progress)
        page.attach(next);assertFalse(page.rendererLost)
        page.failed(old,"Late error");page.progressed(old,2);page.started(old)
        page.cleared(old,4,4,page.navigation,{fail("Old surface must not authorize")}){loads++}
        assertNull(page.failure);assertEquals(100,page.progress);assertEquals(0,loads)
        assertFalse(page.detach(old,true));assertTrue(page.current(next))
        page.failed(next,"Network error");page.started(next);assertNull(page.failure);assertEquals(0,page.progress)
        page.detach(next);old.destroy();next.destroy()
    }
    @Test fun clearCompletionIsBoundToCurrentCommandGrantAndNewPageFinish() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val view=Pages(context);val page=SdkWebPage();page.attach(view);var loads=0
        page.cleared(view,3,4,page.navigation,{fail("Old command must not authorize")}){loads++}
        assertEquals(0,loads)
        page.cleared(view,4,4,page.navigation,{error("Revoked")}){loads++};assertEquals(0,loads);assertEquals("Revoked",page.failure)
        val oldNavigation=page.navigation;page.started(view)
        page.cleared(view,5,5,oldNavigation,{fail("New navigation must cancel old clear reload")}){loads++}
        assertEquals(0,loads)
        page.cleared(view,5,5,page.navigation,{}){loads++};assertEquals(1,loads);assertEquals(0,view.clears)
        page.finished(view,4);assertEquals(0,view.clears)
        page.finished(view,5);assertEquals(1,view.clears)
        page.finished(view,5);assertEquals(1,view.clears)
        page.detach(view);view.destroy()
    }

}
