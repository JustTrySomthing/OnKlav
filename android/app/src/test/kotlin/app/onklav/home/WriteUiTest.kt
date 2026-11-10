package app.onklav.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.unit.dp
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
class WriteUiTest {
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
        val env = compose.javaClass.getDeclaredField("environment").apply { isAccessible = true }.get(compose)
        val registry = env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply { isAccessible = true }.get(env)
        val roots = registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>
        val views=roots.filterNotNull().map{it.javaClass.getMethod("getView").invoke(it) as android.view.View}.map{it.rootView}.filter{it.width>0&&it.height>0}
        val view=views.lastOrNull{it!==compose.activity.window.decorView}?:views.lastOrNull()?:compose.activity.window.decorView
        val b=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(b))
        val f=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/write-refinement/$name.png")
        f.parentFile!!.mkdirs();f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    } }
    private fun nativeEditor():WriteNativeEditor? {
        fun visit(v:android.view.View):WriteNativeEditor? {if(v is WriteNativeEditor)return v;if(v is android.view.ViewGroup)for(i in 0 until v.childCount){visit(v.getChildAt(i))?.let{return it}};return null}
        return visit(compose.activity.window.decorView)
    }
    @Test(timeout=60000) fun signedWriterFormatsManagesAndExportsWithoutLosingSelection(){
        java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/write-refinement/write-ui-progress.txt").apply{parentFile!!.mkdirs()}.appendText("WRITE_STAGE bootstrap\n")
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking{AppSdk.bootstrap(c)}
        val launch=AppSdk.sync(c,JSONObject().put("action","launch").put("id","app.onklav.write"))as JSONObject
        assertEquals("Write",launch.child("frame").getString("title"))
        java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/write-refinement/write-ui-progress.txt").appendText("WRITE_STAGE content\n")
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{WriteWorkspace()}}};compose.mainClock.autoAdvance=false
        java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/write-refinement/write-ui-progress.txt").appendText("WRITE_STAGE waiting-editor\n")
        waitFor{nativeEditor()!=null && nativeEditor()!!.isEnabled && compose.onAllNodesWithText("Untitled").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnUiThread{nativeEditor()!!.setText("A speling issue")}
        waitFor{val e=nativeEditor()!!;e.editableText.getSpans(0,e.length(),android.text.style.SuggestionSpan::class.java).any{e.editableText.getSpanStart(it)==2}}
        compose.runOnIdle{assertEquals("A speling issue",nativeEditor()!!.text.toString())}
        java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/write-refinement/write-ui-progress.txt").appendText("WRITE_STAGE typing\n")
        compose.runOnUiThread{nativeEditor()!!.setText("A polished draft\nFirst item\nSecond item");nativeEditor()!!.setSelection(2,10)};pump()
        java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/write-refinement/write-ui-progress.txt").appendText("WRITE_STAGE formatting\n")
        compose.onNodeWithContentDescription("Bold").readyClick();compose.onNodeWithContentDescription("Italic").readyClick();compose.onNodeWithContentDescription("Underline").readyClick();compose.onNodeWithContentDescription("Strikethrough").readyClick()
        compose.runOnIdle{val e=nativeEditor()!!;assertTrue(WriteFormatting.active(e.editableText,2,10,"bold"));assertTrue(WriteFormatting.active(e.editableText,2,10,"italic"));assertTrue(WriteFormatting.active(e.editableText,2,10,"underline"));assertTrue(WriteFormatting.active(e.editableText,2,10,"strike"));assertEquals(2,e.selectionStart);assertEquals(10,e.selectionEnd)}
        java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/write-refinement/write-ui-progress.txt").appendText("WRITE_STAGE highlight\n")
        compose.onNodeWithContentDescription("Highlight").readyClick();compose.onNodeWithContentDescription("Gold").readyClick();compose.onNodeWithContentDescription("Done formatting").readyClick()
        compose.runOnIdle{assertTrue(nativeEditor()!!.editableText.getSpans(2,10,android.text.style.BackgroundColorSpan::class.java).isNotEmpty())}
        val pageBefore=android.graphics.Rect();compose.runOnIdle{nativeEditor()!!.getGlobalVisibleRect(pageBefore)}
        val imm=c.getSystemService(Context.INPUT_METHOD_SERVICE)as android.view.inputmethod.InputMethodManager
        compose.runOnUiThread{nativeEditor()!!.requestFocus();imm.showSoftInput(nativeEditor()!!,0)}
        assertTrue(org.robolectric.Shadows.shadowOf(imm).isSoftInputVisible)
        compose.onNodeWithContentDescription("Text").readyClick();capture("write-font-popover")
        assertTrue("Formatting keeps the keyboard",org.robolectric.Shadows.shadowOf(imm).isSoftInputVisible)
        val pageAfter=android.graphics.Rect();compose.runOnIdle{nativeEditor()!!.getGlobalVisibleRect(pageAfter)};assertEquals(pageBefore,pageAfter)
        compose.onNodeWithContentDescription("Increase font size").readyClick()
        compose.onNodeWithText("Serif").readyClick()
        compose.runOnIdle{val e=nativeEditor()!!;assertEquals(2,e.selectionStart);assertEquals(10,e.selectionEnd);assertTrue(e.editableText.getSpans(2,10,android.text.style.TypefaceSpan::class.java).any{it.family=="serif"})}
        compose.onNodeWithContentDescription("Color").readyClick();capture("write-color-popover")
        compose.onNodeWithContentDescription("Blue").readyClick();compose.onNodeWithContentDescription("Done formatting").readyClick()
        compose.onNodeWithContentDescription("Color").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"#0D47A1"))
        compose.runOnIdle{val e=nativeEditor()!!;assertEquals(2,e.selectionStart);assertEquals(10,e.selectionEnd);assertEquals(0xff0d47a1.toInt(),e.editableText.getSpans(2,10,android.text.style.ForegroundColorSpan::class.java).single().foregroundColor)}
        java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/write-refinement/write-ui-progress.txt").appendText("WRITE_STAGE list\n")
        compose.runOnUiThread{nativeEditor()!!.setSelection(17,nativeEditor()!!.length())};pump();compose.onNodeWithContentDescription("Lists").readyClick();compose.onNodeWithText("Numbered").readyClick()
        compose.runOnIdle{assertEquals(2,nativeEditor()!!.editableText.getSpans(17,nativeEditor()!!.length(),WriteListSpan::class.java).size)}
        compose.onNodeWithContentDescription("Align").readyClick();compose.onNodeWithText("Center").readyClick()
        compose.runOnIdle{assertTrue(nativeEditor()!!.editableText.getSpans(17,nativeEditor()!!.length(),android.text.style.AlignmentSpan::class.java).any{it.alignment==android.text.Layout.Alignment.ALIGN_CENTER})}
        compose.onNodeWithContentDescription("Indent").readyClick();compose.onNodeWithText("Indent").readyClick()
        compose.runOnIdle{assertTrue(nativeEditor()!!.editableText.getSpans(17,nativeEditor()!!.length(),android.text.style.LeadingMarginSpan::class.java).any{it.getLeadingMargin(true)>0})}
        compose.onNodeWithContentDescription("Undo").readyClick();compose.onNodeWithContentDescription("Redo").readyClick();capture("write-editor")
        java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/write-refinement/write-ui-progress.txt").appendText("WRITE_STAGE export\n")
        compose.onNodeWithContentDescription("Save options").readyClick();waitFor{compose.onAllNodesWithText("Export a copy").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("PDF").readyClick();capture("write-save-options");compose.onNodeWithText("Cancel").readyClick()
        compose.onNodeWithText("Untitled").readyClick();waitFor{compose.onAllNodesWithText("Document name").fetchSemanticsNodes().isNotEmpty()};compose.onNodeWithText("Cancel").readyClick()
        compose.onNodeWithContentDescription("Documents").readyClick()
        waitFor{compose.onAllNodesWithText("Search saved documents").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Search saved documents").performTextInput("no-such-document-8675309");pump()
        compose.onNodeWithText("No matching documents").assertExists()
        compose.onNodeWithContentDescription("Close documents").readyClick()
        compose.onNodeWithContentDescription("Save options").assertExists()
        compose.onNodeWithContentDescription("Write settings").readyClick()
        waitFor{compose.onAllNodesWithText("Spelling & grammar").fetchSemanticsNodes().isNotEmpty()}
        capture("write-settings")
        compose.onNodeWithText("Spelling & grammar").readyClick()
        waitFor{compose.onAllNodesWithText("No spelling or supported grammar issues found.").fetchSemanticsNodes().isNotEmpty()}
        capture("write-review");compose.onNodeWithText("Done").readyClick()
        AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.write").put("grants",org.json.JSONArray()));pump();assertFalse(WriteFiles.allowed(c));waitFor{nativeEditor()==null}
    }
    @Test(timeout=45000) fun colorAndHighlightKeepSelectionKeyboardAndDocumentViewportStable(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext;runBlocking{AppSdk.bootstrap(c)}
        // The preceding revocation regression deliberately leaves this test profile denied.
        val installed=(AppSdk.sync(c,JSONObject().put("action","list")) as org.json.JSONArray).let{rows->(0 until rows.length()).map{rows.getJSONObject(it)}.first{it.child("manifest").optString("id")=="app.onklav.write"}}
        AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.write").put("grants",installed.child("manifest").getJSONArray("capabilities")))
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{WriteWorkspace()}}};compose.mainClock.autoAdvance=false
        waitFor{nativeEditor()!=null&&nativeEditor()!!.isEnabled}
        val text=(1..50).joinToString("\n"){"Line $it keeps the editor steady"};val a=text.indexOf("Line 12")
        compose.runOnUiThread{nativeEditor()!!.setText(text);nativeEditor()!!.setSelection(a,a+7)};pump()
        val imm=c.getSystemService(Context.INPUT_METHOD_SERVICE)as android.view.inputmethod.InputMethodManager
        compose.runOnUiThread{nativeEditor()!!.requestFocus();imm.showSoftInput(nativeEditor()!!,0)};pump()
        val rect=android.graphics.Rect();var scroll=0;var padding=0
        compose.runOnIdle{nativeEditor()!!.let{it.getGlobalVisibleRect(rect);scroll=it.scrollY;padding=it.paddingBottom}}
        compose.onNodeWithContentDescription("Highlight").readyClick();capture("write-steady-highlight")
        repeat(2){compose.onNodeWithContentDescription(if(it==0)"Gold"else"Blue").readyClick();compose.runOnIdle{val e=nativeEditor()!!;assertEquals(a,e.selectionStart);assertEquals(a+7,e.selectionEnd);assertEquals(scroll,e.scrollY);assertEquals(padding,e.paddingBottom);val now=android.graphics.Rect();e.getGlobalVisibleRect(now);assertEquals(rect,now)}}
        assertTrue(org.robolectric.Shadows.shadowOf(imm).isSoftInputVisible)
        compose.onNodeWithContentDescription("Done formatting").readyClick();compose.onNodeWithContentDescription("Color").readyClick();compose.onNodeWithContentDescription("Blue").readyClick()
        compose.runOnIdle{assertEquals(0xff0d47a1.toInt(),nativeEditor()!!.editableText.getSpans(a,a+7,android.text.style.ForegroundColorSpan::class.java).single().foregroundColor)}
        compose.onNodeWithContentDescription("Done formatting").readyClick()
        compose.onNodeWithContentDescription("Save status:",substring=true).assertExists()
    }
    @Test(timeout=45000) fun saveChoicesWrapAtLargeTypeAndExportUsesTheChosenFormat(){
        var selected="docx";var saved=0;var discarded=0;var exported=""
        compose.runOnUiThread{
            val config=android.content.res.Configuration(compose.activity.resources.configuration);config.fontScale=1.6f
            @Suppress("DEPRECATION")
            compose.activity.resources.updateConfiguration(config,compose.activity.resources.displayMetrics)
            compose.activity.setContent{OnKlavTheme{
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1.6f)){
                val format=androidx.compose.runtime.remember{androidx.compose.runtime.mutableStateOf("docx")}
                WriteSaveDialog(format.value,false,{format.value=it;selected=it},{saved++},{discarded++},{exported=selected},{})
            }
        }}};compose.mainClock.autoAdvance=false;pump()
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Save (Overwrite)",useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertTrue("Large-text fixture uses real scaled text",layouts.single().layoutInput.density.fontScale>=1.5f)
        compose.onNodeWithText("Save (Overwrite)").assertIsDisplayed()
        compose.onNodeWithText("Close (Discard Changes)").assertIsDisplayed()
        for(name in listOf("DOCX","PDF","HTML","TXT","Write"))compose.onNodeWithText(name).assertExists()
        compose.onNodeWithText("PDF").readyClick();compose.onNodeWithText("Export PDF").readyClick();assertEquals("pdf",exported)
        compose.onNodeWithText("Save (Overwrite)").readyClick();assertEquals(1,saved)
        compose.onNodeWithText("Close (Discard Changes)").readyClick();assertEquals(1,discarded)
        capture("write-save-large-type")
    }

    @Test(timeout=45000) fun reviewAppliesOnlyTheChosenSpellingSuggestion(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext;runBlocking{AppSdk.bootstrap(c)}
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{WriteWorkspace()}}};compose.mainClock.autoAdvance=false
        waitFor{nativeEditor()?.isEnabled==true}
        compose.runOnUiThread{nativeEditor()!!.setText("A speling issue")};pump()
        compose.onNodeWithContentDescription("Spelling & grammar").readyClick()
        waitFor{compose.onAllNodesWithText("spelling").fetchSemanticsNodes().isNotEmpty()}
        capture("write-review-suggestion")
        compose.onNodeWithText("spelling").readyClick()
        waitFor{nativeEditor()!!.text.toString()=="A spelling issue"&&compose.onAllNodesWithText("No spelling or supported grammar issues found.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Done").readyClick()
        compose.runOnIdle{assertEquals("A spelling issue",nativeEditor()!!.text.toString())}
    }

    @Test(timeout=45000) fun twoRowsFitNarrowScreensWithLargeText(){
        compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{
            val config=android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply{screenWidthDp=320}
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides config,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1.6f)){
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.requiredWidth(320.dp)){
                    WriteTools(true,true,true,{false},"",0xff0d47a1.toInt(),0xffffd54f.toInt(),16,{}, {}, {}, {_,_->}, {_,_->}, {}, {}, {}, {}, {})
                }
            }
        }}};compose.mainClock.autoAdvance=false;pump()
        compose.mainClock.autoAdvance=true
        val top=listOf("Text","Bold","Italic","Underline","Strikethrough","Color","Highlight").map{compose.onNodeWithContentDescription(it).performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot} + compose.onNodeWithContentDescription("Redo").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val bottom=listOf("Lists","Align","Indent","Insert image","Insert link","Spelling & grammar").map{compose.onNodeWithContentDescription(it).performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot} + compose.onNodeWithContentDescription("Undo").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(1,top.map{it.top}.distinct().size);assertEquals(1,bottom.map{it.top}.distinct().size)
        assertTrue(bottom.first().top>=top.first().bottom)
        assertTrue(bottom.last().right<=320f)
        assertTrue(bottom.zipWithNext().all{(a,b)->a.right<=b.left+0.1f})
        capture("write-toolbar-narrow-large-type")
    }

}
