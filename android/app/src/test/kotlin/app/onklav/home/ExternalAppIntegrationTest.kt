package app.onklav.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ExternalAppIntegrationTest {
    @get:org.junit.Rule val emptyKeyStore=EmptyKeyStoreRule()
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.readyClick(): androidx.compose.ui.test.SemanticsNodeInteraction {
        compose.waitUntil(15000) { pump();runCatching { assertIsEnabled();assertIsDisplayed() }.isSuccess }
        performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        pump()
        return this
    }
    private fun pump() { repeat(8) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(100)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        compose.runOnUiThread {
            val env = compose.javaClass.getDeclaredField("environment").apply { isAccessible = true }.get(compose)
            val base = requireNotNull(env.javaClass.superclass)
            val registry = base.getDeclaredField("composeRootRegistry").apply { isAccessible = true }.get(env)
            val roots = registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>
            roots.filterNotNull().forEach { root ->
                val view = root.javaClass.getMethod("getView").invoke(root) as android.view.View
                run { // Android traverses newly created windows even without a pending Compose measure flag.
                    val decor = view.rootView
                    decor.forceLayout()
                    decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(320, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(640, android.view.View.MeasureSpec.AT_MOST))
                    decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
                    root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)
                }
            }
        }
    } }

    private fun waitFor(test:()->Boolean) {
        try { compose.waitUntil(15000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));pump();test() } }
        catch(e:Throwable) {compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.forEach{println(compose.onAllNodes(isRoot())[it].printToString())};throw e }
    }

    private val context:Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(name:String)=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"developer/tests/fixtures/$name.onklav-a")
    @Test(timeout=120000)fun hostChromeOverflowModalDrawerAndCloseRemainUsable(){
        runBlocking{AppSdk.staging.withLock{fixture("HostContainment").inputStream().use{AppSdk.stage(context,it)};AppSdk.install(context,AppSdk.inspect(context),emptySet())}}
        val installed=runBlocking{AppSdk.list(context)}.single{it.child("manifest").optString("id")=="org.example.hostfixture"}
        val desktop=SdkDesktopIcons.app(installed.child("manifest"));assertNotNull(desktop.iconPng)
        context.getSharedPreferences("apps_desktop",0).edit().putString("positions",app.onklav.shared.LauncherLayout.read("",listOf(desktop.id)).encode()).putInt("page",0).commit()
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{AppsDesktop{}}}}
        waitFor{compose.onAllNodesWithTag("desktop-custom-icon-${AppSdk.desktopId("org.example.hostfixture")}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        var exited=false
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{SdkAppScreen("org.example.hostfixture",exit={exited=true})}}}
        waitFor{runCatching{compose.onNodeWithText("External body").assertIsDisplayed()}.isSuccess}
        compose.onNodeWithContentDescription("App controls").assertIsDisplayed()
        compose.onNodeWithContentDescription("Action 79").assertDoesNotExist()
        for(id in listOf("disabled-toggle","disabled-button","disabled-field","disabled-record"))compose.onNodeWithTag("sdk-$id").assertIsNotEnabled()
        compose.onNodeWithContentDescription("App controls").readyClick()
        compose.onNodeWithText("Secondary action").readyClick()
        waitFor{runCatching{compose.onNodeWithText("Secondary action ran").assertIsDisplayed()}.isSuccess}
        compose.onNodeWithContentDescription("Action 0").readyClick()
        waitFor{runCatching{compose.onNodeWithText("Guest modal").assertIsDisplayed()}.isSuccess}
        compose.onNodeWithText("Cancel").readyClick()
        waitFor{compose.onAllNodesWithText("Guest modal").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithContentDescription("Action 1").readyClick()
        waitFor{runCatching{compose.onNodeWithText("Guest modal").assertIsDisplayed()}.isSuccess}
        compose.onNodeWithText("Cancel").readyClick()
        compose.onNodeWithContentDescription("Close Guest drawer").readyClick()
        waitFor{compose.onAllNodesWithTag("sdk-app-drawer").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithContentDescription("App controls").readyClick()
        compose.onNodeWithText("Close App").readyClick()
        waitFor{runCatching{compose.onNodeWithText("Close App?").assertIsDisplayed()}.isSuccess}
        compose.onNodeWithText("Close App").readyClick()
        waitFor{exited}
        runBlocking{AppSdk.call(context,JSONObject().put("action","remove").put("id","org.example.hostfixture").put("delete_data",true))}
        assertFalse(runBlocking{AppSdk.list(context)}.any{it.child("manifest").optString("id")=="org.example.hostfixture"&&it.optBoolean("installed")})
    }
    @Test(timeout=120000)fun longFormsScrollAtLargeFontAndSaveThroughSharedController(){
        runBlocking{AppSdk.staging.withLock{fixture("FormLayout").inputStream().use{AppSdk.stage(context,it)};AppSdk.install(context,AppSdk.inspect(context),setOf("storage.private"))}}
        compose.runOnUiThread{compose.activity.setContent{androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1.6f)){OnKlavTheme{SdkAppScreen("org.example.formfixture",exit={})}}}}
        waitFor{compose.onAllNodesWithTag("sdk-form.field.field17").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("sdk-form.field.field17").performScrollTo().performTextReplacement("Last field saved")
        compose.onNodeWithText("Save").performScrollTo().readyClick()
        waitFor{runCatching{compose.onNodeWithText("Saved").assertIsDisplayed()}.isSuccess}
        compose.onNodeWithContentDescription("App controls").assertIsDisplayed()
        runBlocking{AppSdk.call(context,JSONObject().put("action","remove").put("id","org.example.formfixture").put("delete_data",true))}
    }
    @Test(timeout=30000)fun newerHostRequirementIsRejected(){
        runBlocking{AppSdk.staging.withLock{fixture("FutureHost").inputStream().use{AppSdk.stage(context,it)}
            val failure=runCatching{AppSdk.inspect(context)}.exceptionOrNull();assertNotNull(failure);assertTrue(failure!!.message.orEmpty().contains("requires"))
        }}
    }
    @Test(timeout=30000)fun customIconDecodeIsBoundedAndTrimKeepsDisplayedBitmapValid(){
        val manifest=java.util.zip.ZipFile(fixture("HostContainment")).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).bufferedReader().readText())}
        val hex=manifest.getJSONObject("icon").getString("png_hex")
        runBlocking{
            val bitmap=SdkDesktopIcons.bitmap(hex);assertNotNull(bitmap);assertEquals(1,bitmap!!.width)
            assertSame(bitmap,SdkDesktopIcons.bitmap(hex));SdkDesktopIcons.clear();assertFalse(bitmap.isRecycled)
            assertNull(SdkDesktopIcons.bitmap("00".repeat(6145)));assertNull(SdkDesktopIcons.bitmap(hex.replaceRange(32,40,"ffffffff")))
        }
    }
    @Test(timeout=30000)fun unknownReservedIdentityIsRejected(){
        runBlocking{AppSdk.staging.withLock{fixture("ReservedNamespace").inputStream().use{AppSdk.stage(context,it)}
            val failure=runCatching{AppSdk.inspect(context)}.exceptionOrNull();assertNotNull(failure);assertTrue(failure!!.message.orEmpty().contains("reserved"))
        }}
    }
}
