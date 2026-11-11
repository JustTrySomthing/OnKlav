package app.onklav.home

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Real Compose measurement/input against simulated Android frameworks, not OEM firmware. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
abstract class DeviceCompatibilityChecks {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val evidence get()=File(System.getenv("ONKLAV_COMPAT_EVIDENCE")?:File(System.getProperty("onklav.project.root"),"docs/evidence/compatibility-${BuildConfig.VERSION_NAME.substringBefore('-')}").path).apply{mkdirs()}
    private fun pump() {repeat(3) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(100)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        compose.runOnUiThread {
            roots().forEach { root ->
                val view=root.javaClass.getMethod("getView").invoke(root) as View
                val decor=view.rootView;val display=compose.activity.resources.displayMetrics
                decor.forceLayout()
                decor.measure(View.MeasureSpec.makeMeasureSpec(display.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(display.heightPixels,View.MeasureSpec.AT_MOST))
                decor.layout(0,0,decor.measuredWidth,decor.measuredHeight)
                root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)
            }
        }
    }}
    private fun roots():List<Any> {
        val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose)
        val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
        return (registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>).filterNotNull()
    }
    private fun show(content:@Composable ()->Unit) {
        compose.activity.setContent {OnKlavTheme{content()}}
        pump()
    }
    private fun text(label:String):SemanticsNodeInteraction=compose.onNodeWithText(label,substring=false).reveal(compose,::pump)
    private fun noTextOverflow(label:String) {
        val results=mutableListOf<TextLayoutResult>()
        text(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(results)}
        assertTrue("Missing text layout for $label",results.isNotEmpty())
        // Android text metrics retain fractional pixels while layout bounds are integers.
        // Allow one pixel of rounding, but reject truncation or actual overflow.
        val metrics=results.map{"${it.size}, right=${(0 until it.lineCount).maxOf(it::getLineRight)}, bottom=${it.getLineBottom(it.lineCount-1)}"}
        assertTrue("Clipped text: $label — $metrics",results.none{
            it.multiParagraph.didExceedMaxLines || (0 until it.lineCount).any{line->it.getLineRight(line)>it.size.width+1f} ||
                it.getLineBottom(it.lineCount-1)>it.size.height+1f
        })
    }
    private fun screenshot(profile:String) {
        compose.runOnUiThread {
            val view=roots().map{it.javaClass.getMethod("getView").invoke(it) as View}.map{it.rootView}.last{it.width>0&&it.height>0}
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(evidence,"updates-api${Build.VERSION.SDK_INT}-$profile.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
    private fun profile(name:String,width:Int,height:Int,font:Float=1f) {
        val config=compose.activity.resources.configuration
        assertEquals(width,config.screenWidthDp);assertEquals(height,config.screenHeightDp)
        assertEquals(font,config.fontScale,.01f)
        var selected=""
        show {SettingsPane{selected=it}}
        for(label in listOf("Appearance","Profile","Setup","Sounds","Security","Storage","Read Aloud","Backups","Games","Apps","Walkie Talkie","Quiet Hours","Reminders","Check for updates","Help","About","Advanced"))text(label).assertIsDisplayed()
        text("Check for updates").performClick();pump();assertEquals("Check for updates",selected)
        show {UpdateSettingsDialog{}}
        screenshot(name)
        noTextOverflow("Installed: OnKlav ${BuildConfig.VERSION_NAME}")
        for(label in listOf("Check automatically","Include release candidates","Check for updates","Close"))text(label).assertIsDisplayed()
        val dialog=compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        val check=text("Check for updates").fetchSemanticsNode().boundsInRoot
        val inset=20*compose.activity.resources.displayMetrics.density
        assertTrue("Update action touches left edge",check.left-dialog.left>=inset)
        assertTrue("Update action touches right edge",dialog.right-check.right>=inset)
        var digits by mutableStateOf("5550101234");var chosen=false
        show {NumberEntryDialog(digits,{digits=it},{},{chosen=true})}
        for(key in listOf("1","5","9","0","⌫"))compose.onNodeWithTag("dialpad-$key").reveal(compose,::pump).assertIsDisplayed()
        text("Start Message").performClick();pump();assertTrue("Number action cannot be reached",chosen)
        var destination=-1
        show {Column(Modifier.fillMaxSize()){Spacer(Modifier.weight(1f));HomeNavigation(2){destination=it}}}
        for((label,id) in listOf("Phone" to 5,"Camera" to 6,"Apps" to 2,"Messages" to 0,"Groups" to 1)) {
            compose.onNodeWithTag("tab-$label").assertIsDisplayed().performClick();pump();assertEquals(id,destination)
        }
        show {GamesCatalog{selected=it}}
        for(id in listOf("hockey","four","blackjack","dominos","panda","naval")) {
            compose.onNodeWithTag("games-catalog").assertIsDisplayed()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("game-card-$id"));pump()
            compose.onNodeWithTag("game-card-$id").assertIsDisplayed().performClick();pump()
            compose.waitUntil(5000){pump();selected==id}
            assertEquals(id,selected)
        }
        File(evidence,"api${Build.VERSION.SDK_INT}-$name.json").writeText(JSONObject().put("sdk",Build.VERSION.SDK_INT)
            .put("width_dp",width).put("height_dp",height).put("font_scale",font)
            .put("density_dpi",compose.activity.resources.displayMetrics.densityDpi)
            .put("checks","Settings reachability; update text/margins/actions; dial pad; navigation taps; game selection").put("passed",true).toString(2))
    }
    @Test(timeout=90000) @Config(qualifiers="w320dp-h568dp-port-mdpi") fun compactPhone()=profile("compact",320,568)
    @Test(timeout=90000) @Config(qualifiers="w360dp-h800dp-port-xxhdpi") fun standardPhone()=profile("standard",360,800)
    @Test(timeout=90000) @Config(qualifiers="w432dp-h936dp-port-xhdpi") fun largePhone()=profile("large",432,936)
    @Test(timeout=90000) @Config(qualifiers="w640dp-h360dp-land-mdpi") fun landscapePhone()=profile("landscape",640,360)
    @Test(timeout=90000) @Config(qualifiers="w800dp-h1280dp-port-mdpi") fun tablet()=profile("tablet",800,1280)
    @Test(timeout=90000) @Config(qualifiers="w320dp-h568dp-port-mdpi",fontScale=1.5f) fun compactLargeText()=profile("compact-large-text",320,568,1.5f)
    @Test(timeout=90000) @Config(qualifiers="w360dp-h800dp-port-xhdpi",fontScale=2f) fun extraLargeText()=profile("extra-large-text",360,800,2f)
    @Test(timeout=90000) fun desktopSettingsRemainReachable() {
        runBlocking {AppSdk.bootstrap(compose.activity)}
        show {AppsDesktop{}}
        compose.waitUntil(20000){pump();compose.onAllNodesWithTag("apps-page-0").fetchSemanticsNodes().isNotEmpty()}
        var settingsVisible=false
        for(page in 0..8) {
            settingsVisible=runCatching{compose.onNodeWithTag("app-icon-settings").assertIsDisplayed()}.isSuccess
            if(settingsVisible)break
            compose.onNodeWithTag("apps-next-page").assertIsEnabled().performClick();pump()
        }
        assertTrue("Settings must remain reachable on the desktop",settingsVisible)
        show {Box(Modifier.fillMaxSize())}
        File(evidence,"api${Build.VERSION.SDK_INT}-desktop.json").writeText(JSONObject().put("sdk",Build.VERSION.SDK_INT)
            .put("settings_reachable",true).put("passed",true).toString(2))

    }
}
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h800dp-port-mdpi")
class Android10DeviceCompatibilityTest:DeviceCompatibilityChecks()
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-port-mdpi")
class Android15DeviceCompatibilityTest:DeviceCompatibilityChecks()

/** Keep controller startup checks outside the Compose rule's root/idleness registry. */
abstract class LaunchCompatibilityChecks {
    @Test(timeout=90000) fun appAndEightGameEntriesOpen() {
        val evidence=File(System.getProperty("onklav.project.root"),"docs/evidence/compatibility-${BuildConfig.VERSION_NAME.substringBefore('-')}").apply{mkdirs()}
        fun field(name:String)=WebGameActivity::class.java.getDeclaredField(name).apply{isAccessible=true}
        val main=Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        try {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            assertFalse("Main app closed during startup",main.get().isFinishing)
            assertTrue(main.get().findViewById<android.view.ViewGroup>(android.R.id.content).childCount>0)
        } finally {main.pause().stop().destroy()}
        val games=listOf("hockey","four","blackjack","dominos","panda","naval",GamePackages.SHROOM_ID,GamePackages.LAST_LIGHT_ID)
        for(id in games) {
            val controller=Robolectric.buildActivity(WebGameActivity::class.java,Intent().putExtra("game",id)).create().start().resume()
            val activity=controller.get()
            try {
                (field("worker").get(activity) as ExecutorService).submit{}.get(15,TimeUnit.SECONDS)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                assertFalse("$id failed preflight",activity.isFinishing)
                assertNotNull("$id has no native start UI",field("entryView").get(activity))
                assertNull("$id loaded before explicit Play",field("web").get(activity))
            } finally {controller.pause().stop().destroy()}
        }
        File(evidence,"api${Build.VERSION.SDK_INT}-entry.json").writeText(JSONObject().put("sdk",Build.VERSION.SDK_INT)
            .put("main_startup",true).put("native_game_preflights",org.json.JSONArray(games)).put("passed",true).toString(2))
    }
}
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h800dp-port-mdpi")
class Android10LaunchCompatibilityTest:LaunchCompatibilityChecks()
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w360dp-h800dp-port-mdpi")
class Android15LaunchCompatibilityTest:LaunchCompatibilityChecks()
