package app.onklav.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
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
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MapsAppUiTest {
    @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
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
                    decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(432, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(936, android.view.View.MeasureSpec.AT_MOST))
                    decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
                    root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)
                }
            }
        }
    } }

    private fun waitFor(test:()->Boolean) {
        try { compose.waitUntil(15000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));pump();test() } }
        catch(e:Throwable) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.forEach { println(compose.onAllNodes(isRoot())[it].printToString()) }
            capture("sdk-failure")
            throw e
        }
    }

    private fun capture(name:String) { compose.runOnIdle {
        val view=compose.activity.window.decorView
        val b=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(b))
        val f=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/v0.95.0/$name.png")
        f.parentFile!!.mkdirs();f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    } }
    @Test(timeout=90000) fun zipBookmarksRenamingAndSavedDirectionsAreReachable(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking{AppSdk.bootstrap(c)}
        val home=MapsPlace("home","Home",36.16,-86.78,"Nashville, Tennessee")
        val fixture=org.json.JSONObject(java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/fixtures/maps-app/route.json").readText())
        val route=MapsParser.route(fixture,home,MapsPlace("to","Town",36.17,-86.79),"Drive").copy(name="Weekend trip")
        MapsStore.save(c,MapsSaved(listOf(home),listOf(route)))
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{var saved by androidx.compose.runtime.remember{androidx.compose.runtime.mutableStateOf(false)};androidx.compose.material3.Surface{androidx.compose.foundation.layout.Column{MapsFolderButton{saved=!saved};MapsWorkspace(savedOpen=saved,online=false,onSavedChange={saved=it})}}}}};compose.mainClock.autoAdvance=false
        compose.onNodeWithContentDescription("Saved places and directions").readyClick();waitFor{compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Rename Home").readyClick();compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Workshop");compose.onNodeWithText("Save").readyClick()
        waitFor{compose.onAllNodesWithText("Workshop").fetchSemanticsNodes().isNotEmpty()};assertEquals("Workshop",MapsStore.read(c).places.single().name)
        compose.onNodeWithText("Directions").readyClick();compose.onNodeWithText("Weekend trip").readyClick();waitFor{compose.onAllNodesWithText("Show map").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("Save directions").assertExists();capture("maps-directions")
        compose.onNodeWithText("Save directions").readyClick();compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Morning drive");compose.onNodeWithText("Save").readyClick();waitFor{MapsStore.read(c).routes.single().name=="Morning drive"}
        compose.onNodeWithText("Show map").readyClick();compose.onNodeWithContentDescription("Search maps").assertExists()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("37201");compose.onNodeWithContentDescription("Search maps").readyClick();waitFor{compose.onAllNodes(hasText("Nashville",substring=true)).fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodes(hasText("Nashville",substring=true))[0].readyClick()
        compose.runOnIdle{
            val tile=org.json.JSONObject(java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/fixtures/maps-app/tile.json").readText())
            val bytes=java.util.Base64.getDecoder().decode(tile.getString("png"));val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size);assertEquals(256,bitmap.width)
            val model=androidx.lifecycle.ViewModelProvider(compose.activity).get("maps-${AppSdk.epoch.value}",MapsModel::class.java)
            assertEquals(tile.getDouble("lat"),model.camera.lat,.000001);assertEquals(tile.getDouble("lon"),model.camera.lon,.000001);assertNull(model.route)
            @Suppress("UNCHECKED_CAST")
            val holder=MapsModel::class.java.getDeclaredField("tiles\$delegate").apply{isAccessible=true}.get(model) as androidx.compose.runtime.MutableState<Map<MapsTile,android.graphics.Bitmap>>
            holder.value=mapOf(MapsTile(tile.getInt("z"),tile.getInt("x"),tile.getInt("y")) to bitmap)
        };pump();capture("maps-map")
        AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.maps").put("grants",org.json.JSONArray()));pump();waitFor{compose.onAllNodesWithText("Maps storage access is off. Restore it in Settings > Apps > Maps.").fetchSemanticsNodes().isNotEmpty()}
    }
}
