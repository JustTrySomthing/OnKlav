package app.onklav.home
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import app.onklav.shared.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ResponsiveAppearanceHostTest {
    // Layout tests own an empty host: launcher startup, recovery and splash callbacks
    // must not race replacement settings content or retain a previous window size.
    val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    // Register the empty host only in Robolectric, including diagnostic variants
    // that intentionally do not package Compose's debug test-activity manifest.
    @get:Rule val rules:org.junit.rules.TestRule=org.junit.rules.RuleChain.outerRule(
        org.junit.rules.TestRule { base, _ -> object:org.junit.runners.model.Statement() {
            override fun evaluate() {
                val app=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
                org.robolectric.Shadows.shadowOf(app.packageManager).addOrUpdateActivity(
                    android.content.pm.ActivityInfo().apply {
                        packageName=app.packageName
                        name=androidx.activity.ComponentActivity::class.java.name
                        exported=false
                    })
                base.evaluate()
            }
        } }
    ).around(compose)
    private fun layout(width:Int,height:Int) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        compose.runOnUiThread {
            val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose)
            val registry=requireNotNull(env.javaClass.superclass).getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
            val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>
            roots.filterNotNull().forEach { root ->
                val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View;val decor=view.rootView
                decor.forceLayout();decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(height,if(decor===compose.activity.window.decorView)android.view.View.MeasureSpec.EXACTLY else android.view.View.MeasureSpec.AT_MOST))
                decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)
            }
        }
    }
    private fun pump(width:Int,height:Int){repeat(4){compose.mainClock.advanceTimeBy(100);layout(width,height)}}
    private fun settings(width:Int,height:Int,scale:Float,dark:Boolean,palette:Int,name:String) {
        compose.mainClock.autoAdvance=false
        compose.runOnUiThread { compose.activity.setContent { OnKlavTheme(preview=AppearanceSpec(dark=dark,palette=palette)) {CompositionLocalProvider(LocalDensity provides Density(1f,scale)){Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){SettingsPane{}}}} } }
        pump(width,height)
        for(label in listOf("Appearance","Profile","Setup","Sounds","Security","Storage","Read Aloud","Backups","Games","Apps","Walkie Talkie","Quiet Hours","Reminders","Privacy","Help")) {
            compose.onNodeWithText(label).reveal(compose){pump(width,height)}.assertIsDisplayed();val bounds=compose.onNodeWithText(label).getUnclippedBoundsInRoot();assertTrue("$label left=$bounds",bounds.left.value>=0);assertTrue("$label right=$bounds",bounds.right.value<=width+1)
        }
        compose.onNodeWithText("Appearance").reveal(compose){pump(width,height)};pump(width,height)
        val folder=File(System.getProperty("onklav.project.root"),"docs/evidence/v0.67.0/layout").apply{mkdirs()}
        compose.runOnUiThread {val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);compose.activity.window.decorView.draw(Canvas(bitmap));File(folder,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    }
    @Test(timeout=60000) fun normalPhoneDark(){settings(432,936,1f,true,3,"phone-dark")}
    @Test(timeout=60000) @Config(qualifiers="w320dp-h640dp-mdpi") fun narrowPhoneLargeTextLight(){settings(320,640,1.8f,false,8,"narrow-light-large-text")}
    @Test(timeout=60000) @Config(qualifiers="w800dp-h960dp-mdpi") fun tabletContentStaysBounded(){settings(800,960,1f,false,1,"tablet-light")}
    @Test(timeout=60000) @Config(qualifiers="w800dp-h360dp-mdpi") fun landscapeSettingsAndAlertActionsStayReachable() {
        settings(800,360,1.3f,true,2,"landscape-dark");var accepted=false
        compose.runOnUiThread {compose.activity.setContent {OnKlavTheme {CompositionLocalProvider(LocalDensity provides Density(1f,1.8f)){OnKlavAlertDialog({},confirmButton={DialogAction("Restore selected messages"){accepted=true}},dismissButton={DialogAction("Cancel"){}},title={Text("Restore messages")},text={Text("Review your messages before restoring. ".repeat(30))})}}}}
        pump(800,360);compose.onNodeWithText("Restore selected messages").reveal(compose){pump(800,360)}.assertIsDisplayed().performClick();pump(800,360);assertTrue(accepted)
    }
}
