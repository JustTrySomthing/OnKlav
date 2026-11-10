package app.onklav.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesktopFoldersUiTest{
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private val prefs get()=compose.activity.getSharedPreferences("apps_desktop",0)
 private fun pump(){repeat(4){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));compose.runOnUiThread{val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env);val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>;roots.filterNotNull().forEach{root->val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View;val d=view.rootView;d.forceLayout();d.measure(android.view.View.MeasureSpec.makeMeasureSpec(compose.activity.resources.displayMetrics.widthPixels,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(compose.activity.resources.displayMetrics.heightPixels,android.view.View.MeasureSpec.AT_MOST));d.layout(0,0,d.measuredWidth,d.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)}}}}
 private fun click(tag:String){
  if(tag.startsWith("app-icon-")){
   while(runCatching{compose.onNodeWithTag("apps-previous-page").assertIsEnabled()}.isSuccess){compose.onNodeWithTag("apps-previous-page").performClick();pump()}
   repeat(64){if(runCatching{compose.onNodeWithTag(tag).assertIsDisplayed()}.isSuccess){compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick){it()};pump();return};if(runCatching{compose.onNodeWithTag("apps-next-page").assertIsEnabled()}.isFailure)error("App not found: $tag");compose.onNodeWithTag("apps-next-page").performClick();pump()}
  }
  compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick){it()};pump()
 }
 private fun textClick(text:String){compose.onAllNodesWithText(text,substring=false).onLast().performSemanticsAction(SemanticsActions.OnClick){it()};pump()}
 private fun capture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(b));File(System.getProperty("onklav.project.root"),"docs/evidence/desktop-folders/$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 @Before fun prepare(){runBlocking{AppSdk.bootstrap(compose.activity)};prefs.edit().clear().commit();compose.mainClock.autoAdvance=false;androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(10,java.util.concurrent.TimeUnit.SECONDS)}
 @After fun clean(){prefs.edit().clear().commit()}
 @Test fun navigationOrderIsPhoneCameraAppsMessagesGroups(){var picked=-1;compose.activity.setContent{OnKlavTheme{HomeNavigation(2){picked=it}}};pump();assertEquals(listOf("Phone","Camera","Apps","Messages","Groups"),homeTabs);val x=homeTabs.map{compose.onNodeWithTag("tab-$it").fetchSemanticsNode().boundsInRoot.left};assertEquals(x.sorted(),x);click("tab-Phone");assertEquals(5,picked);click("tab-Camera");assertEquals(6,picked);compose.onNodeWithTag("tab-Settings").assertDoesNotExist()}
 @Test fun appsAlwaysResetToFirstPageAndDefaultsOpen(){var reset by mutableIntStateOf(0);prefs.edit().putInt("page",3).commit();compose.activity.setContent{OnKlavTheme{androidx.compose.material3.Surface{AppsDesktop(reset){}}}};pump();compose.onNodeWithTag("app-icon-phone").assertDoesNotExist();compose.onNodeWithTag("app-icon-camera").assertDoesNotExist();compose.onNodeWithTag("apps-previous-page").assertIsNotEnabled();click("apps-next-page");compose.onNodeWithTag("apps-previous-page").assertIsEnabled();compose.runOnUiThread{reset++};pump();compose.onNodeWithTag("apps-previous-page").assertIsNotEnabled();click("app-icon-folder-utilities");compose.onNodeWithTag("app-icon-compress").assertIsDisplayed();capture("utilities");compose.onNodeWithContentDescription("Close folder").performClick();pump();compose.onNodeWithTag("app-icon-settings").assertIsDisplayed();capture("desktop")}
 @Test fun folderCreateRenameDeleteAndSecondPageAreUsable(){
  val f=DesktopFolders.defaults();val utilities=f.folders[0].id;var many=f;platformApps.filter{it.id !in setOf("phone","camera")}.take(20).forEach{many=many.put(utilities,it.id)};prefs.edit().putString("folders_v1",many.encode()).putInt("arrangement_version",2).commit()
  compose.activity.setContent{OnKlavTheme{androidx.compose.material3.Surface{AppsDesktop{}}}};pump();click("app-icon-folder-utilities");compose.onNodeWithContentDescription("Folder page 1 of 3").assertExists();compose.onNodeWithContentDescription("Next folder page").performClick();pump();compose.onNodeWithContentDescription("Folder page 2 of 3").assertExists();capture("folder-page-two");compose.onNodeWithContentDescription("Close folder").performClick();pump()
  click("action-New folder");compose.onNodeWithTag("folder-name").performTextInput("Studio");pump();textClick("Create");compose.onAllNodesWithText("Studio",substring=false).onLast().assertExists();compose.onNodeWithContentDescription("Folder options").performClick();pump();textClick("Rename");compose.onNodeWithTag("folder-name").performTextReplacement("Studio Two");pump();textClick("Save");assertTrue(DesktopFolders.read(prefs.getString("folders_v1",null)).folders.any{it.name=="Studio Two"});compose.onNodeWithContentDescription("Folder options").performClick();pump();textClick("Delete folder");textClick("Delete folder");assertFalse(DesktopFolders.read(prefs.getString("folders_v1",null)).folders.any{it.name=="Studio Two"})
 }
 @Test fun draggingAppIntoFolderThenOutsideMovesItWithoutLosingApp(){
  prefs.edit().putInt("arrangement_version",2).commit()
  compose.activity.setContent{OnKlavTheme{androidx.compose.material3.Surface{AppsDesktop{}}}};pump()
  fun drag(from:Offset,to:Offset,finish:Boolean=true){val node=compose.onNodeWithTag("apps-grid-viewport");node.performTouchInput{down(from)};compose.mainClock.advanceTimeBy(700);pump();node.performTouchInput{moveTo(to,delayMillis=700)};pump();if(finish){node.performTouchInput{up()};pump()}}
  val viewport=compose.onNodeWithTag("apps-grid-viewport").fetchSemanticsNode().boundsInRoot
  val phone=compose.onNodeWithTag("app-icon-calendar").fetchSemanticsNode().boundsInRoot
  val target=compose.onNodeWithTag("app-icon-folder-utilities").fetchSemanticsNode().boundsInRoot
  val node=compose.onNodeWithTag("apps-grid-viewport")
  node.performTouchInput{down(phone.center-viewport.topLeft)};compose.mainClock.advanceTimeBy(700);pump()
  // A real drag enters a folder at the side before reaching its center.
  node.performTouchInput{moveTo(Offset(target.left+2f,target.center.y)-viewport.topLeft,delayMillis=80)};pump()
  node.performTouchInput{moveTo(target.center-viewport.topLeft,delayMillis=100)};pump();compose.mainClock.advanceTimeBy(600);pump();capture("hover-open");compose.onNodeWithTag("desktop-folder-dialog").assertIsDisplayed();compose.onNodeWithTag("apps-grid-viewport").performTouchInput{up()};pump();assertTrue("calendar" in DesktopFolders.read(prefs.getString("folders_v1",null)).folders[0].apps)
  compose.onNodeWithContentDescription("Next folder page").performClick();pump()
  val inside=compose.onNodeWithTag("app-icon-calendar").fetchSemanticsNode().boundsInRoot;drag(inside.center-viewport.topLeft,Offset(viewport.width-25f,viewport.height-25f));assertFalse("calendar" in DesktopFolders.read(prefs.getString("folders_v1",null)).folders[0].apps);compose.onNodeWithTag("desktop-folder-dialog").assertDoesNotExist();capture("drag-out")
 }
 @Test @Config(qualifiers="w320dp-h568dp-mdpi",fontScale=1.4f) fun smallLargeTextFolderKeepsItsLastRowReachable(){
  val f=DesktopFolders(listOf(DesktopFolder("folder-small","Small",platformApps.filter{it.id !in setOf("phone","camera")}.take(9).map{it.id})));prefs.edit().putString("folders_v1",f.encode()).putInt("arrangement_version",2).commit()
  compose.activity.setContent{OnKlavTheme{androidx.compose.material3.Surface{AppsDesktop{}}}};pump();click("app-icon-folder-small")
  compose.onNodeWithTag("app-icon-docuview").performScrollTo().assertIsDisplayed();compose.onNodeWithContentDescription("Close folder").assertIsDisplayed();capture("small-large-text")
 }
 @Test fun shellOpensPhoneSettingsAndGamesAndReturnsToApps(){
  OnKlavPermissions.finish(compose.activity);compose.activity.setContent{OnKlavTheme{OnKlavScreen()}};pump();pump()
  val header=compose.onNodeWithTag("home-header").getUnclippedBoundsInRoot();assertEquals(0f,header.left.value,1f);assertEquals(432f,header.right.value,1f)
  click("tab-Phone");pump();compose.onNodeWithContentDescription("Phone settings").assertIsDisplayed()
  compose.runOnUiThread{compose.activity.onBackPressedDispatcher.onBackPressed()};pump();pump();click("app-icon-settings");pump();compose.onNodeWithTag("settings-pane").assertIsDisplayed()
  compose.onNode(hasText("Apps") and hasAnyAncestor(hasTestTag("settings-pane"))).performSemanticsAction(SemanticsActions.OnClick){it()};pump();pump();compose.onNodeWithTag("package-center-apps").assertExists()
  compose.runOnUiThread{compose.activity.onBackPressedDispatcher.onBackPressed()};pump();pump();compose.onNodeWithTag("settings-pane").assertIsDisplayed()
  compose.runOnUiThread{compose.activity.onBackPressedDispatcher.onBackPressed()};pump();pump();click("app-icon-games");pump();compose.onNode(hasText("Games") and hasAnyAncestor(hasTestTag("home-header"))).assertIsDisplayed()
  compose.runOnUiThread{compose.activity.onBackPressedDispatcher.onBackPressed()};pump();pump();compose.onNodeWithTag("app-icon-games").assertIsDisplayed();capture("main-navigation")
 }

 @Test(timeout=45000) fun requestedLayoutAppliesOnceAndCustomMovesSurviveReopen(){
  var generation by mutableIntStateOf(0)
  compose.activity.setContent{OnKlavTheme{androidx.compose.material3.Surface{key(generation){AppsDesktop{}}}}};pump();pump()
  assertEquals(2,prefs.getInt("arrangement_version",0))
  val ids=DesktopArrangement.order
  assertEquals(ids,app.onklav.shared.LauncherLayout.read(prefs.getString("positions","").orEmpty(),ids).slots.filter{it.isNotBlank()})
  ids.take(15).forEach{compose.onNodeWithTag("app-icon-$it").assertIsDisplayed()};capture("requested-page-one")
  click("apps-next-page");ids.drop(15).forEach{compose.onNodeWithTag("app-icon-$it").assertIsDisplayed()};capture("requested-page-two")
  click("apps-previous-page")
  val action=compose.onNodeWithTag("app-icon-calendar").fetchSemanticsNode().config[SemanticsActions.CustomActions].first{it.label=="Move Earlier"};compose.runOnUiThread{assertTrue(action.action())};pump()
  val customized=prefs.getString("positions","");assertNotEquals("calendar",customized.orEmpty().removePrefix("g1|").split(',')[9])
  compose.runOnUiThread{generation++};pump();pump();assertEquals(customized,prefs.getString("positions",""))
 }

 @Test(timeout=45000) fun quickDropNearEdgeDoesNotSendTheAppToAnotherPage(){
  compose.activity.setContent{OnKlavTheme{androidx.compose.material3.Surface{AppsDesktop{}}}};pump();pump()
  val viewport=compose.onNodeWithTag("apps-grid-viewport").fetchSemanticsNode().boundsInRoot
  val from=compose.onNodeWithTag("app-icon-calendar").fetchSemanticsNode().boundsInRoot.center-viewport.topLeft
  val node=compose.onNodeWithTag("apps-grid-viewport")
  node.performTouchInput{down(from)};compose.mainClock.advanceTimeBy(700);pump()
  node.performTouchInput{moveTo(Offset(viewport.width-8f,from.y),delayMillis=20);up()};pump()
  val ids=DesktopArrangement.order;val saved=app.onklav.shared.LauncherLayout.read(prefs.getString("positions","").orEmpty(),ids)
  assertTrue(saved.slots.indexOf("calendar")<15);assertEquals(ids.drop(15),saved.slots.drop(15).filter{it.isNotEmpty()})
 }

}
