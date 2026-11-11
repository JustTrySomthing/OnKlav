package app.onklav.home
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w360dp-h780dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class FriendGameUiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private fun pump(){repeat(4){androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(100);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));compose.runOnUiThread{val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env);val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>;roots.filterNotNull().forEach{root->val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View;val d=view.rootView;d.forceLayout();d.measure(android.view.View.MeasureSpec.makeMeasureSpec(compose.activity.resources.displayMetrics.widthPixels,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(compose.activity.resources.displayMetrics.heightPixels,android.view.View.MeasureSpec.AT_MOST));d.layout(0,0,d.measuredWidth,d.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)}}}}
 private fun capture(name:String){compose.runOnUiThread{val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose);val registry=env.javaClass.superclass!!.getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env);val roots=registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>;val view=roots.filterNotNull().map{it.javaClass.getMethod("getView").invoke(it) as android.view.View}.map{it.rootView}.last{it.width>0&&it.height>0};val b=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888);view.draw(android.graphics.Canvas(b));File(System.getProperty("onklav.project.root"),"docs/evidence/friend-dialog-repair/$name.png").apply{parentFile!!.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private fun click(text:String){compose.onNodeWithText(text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()};pump()}
 private fun await(text:String){compose.waitUntil(10000){pump();runCatching{compose.onNodeWithText(text).assertExists()}.isSuccess}}
 @Test(timeout=45000) fun openingAnIncomingInviteShowsWhoWantsToPlayAndBothChoices(){
  compose.mainClock.autoAdvance=false
  val match="a1".repeat(16)
  val number="+15550100001"
  val game=platformGames.first{it.id=="naval"}
  val journal=File(compose.activity.noBackupFilesDir,"friend-games/state.json")
  journal.parentFile!!.mkdirs()
  journal.writeText(org.json.JSONObject().put("answers",org.json.JSONObject()).put("matches",org.json.JSONObject()
   .put(match,org.json.JSONObject().put("id",match).put("game",game.id).put("peer",number)
    .put("status","invite").put("at",System.currentTimeMillis()).put("jobs",org.json.JSONObject()))).toString())
  compose.activity.setContent{OnKlavTheme{GamesCatalog(inviteMatch=match){fail("Unexpected solo launch")}}};pump()
  await("$number wants to play ${game.title} with you.")
  val inPrompt=hasAnyAncestor(isDialog())
  compose.onNode(hasText("Decline") and inPrompt).assertIsEnabled()
  compose.onNode(hasText("Play") and inPrompt).assertIsEnabled()
 }
 @Test(timeout=45000) fun originalOpponentChoicesAndSeparateNumberInvitation(){
  compose.mainClock.autoAdvance=false
  compose.activity.setContent{OnKlavTheme{FriendInviteDialog(platformGames.first{it.id=="naval"},null){}}};pump()
  compose.onNodeWithText("Choose your opponent").assertExists();compose.onNodeWithText("Choose contact").assertExists();compose.onNodeWithText("Enter number").assertExists();capture("opponent")
  compose.onNodeWithText("Send app check").assertDoesNotExist()
  click("Enter number");compose.onNodeWithText("Invite by number").assertExists();compose.onNodeWithText("Invite").assertIsNotEnabled();capture("number")
 }
 @Test(timeout=45000) fun messageShortcutSkipsOpponentAndOffersRetryAfterSetupFailure(){
  compose.mainClock.autoAdvance=false
  compose.activity.setContent{OnKlavTheme{FriendInviteDialog(platformGames.first{it.id=="naval"},"+15550100001"){}}};pump()
  await("Unable to Connect");compose.onNodeWithText("Choose your opponent").assertDoesNotExist();compose.onNodeWithText("Try Again").assertExists();compose.onNodeWithText("Cancel").assertExists();capture("connection-failed")
 }
 @Test(timeout=45000) fun messageTileResumesOnlyTheSelectedContactsMatch(){
  compose.mainClock.autoAdvance=false
  val activity=compose.activity
  val matchId="e1".repeat(16)
  val journal=File(activity.noBackupFilesDir,"friend-games/state.json")
  journal.parentFile!!.mkdirs()
  journal.writeText("""{"matches":{"$matchId":{"id":"$matchId","game":"naval","peer":"+15550100001","status":"active"}}}""")
  val peer=androidx.compose.runtime.mutableStateOf("+15550100002")
  val shadow=org.robolectric.Shadows.shadowOf(activity)
  compose.runOnUiThread{activity.setContent{OnKlavTheme{
   androidx.compose.runtime.key(peer.value){GamesCatalog(friendPeer=peer.value){fail("Unexpected solo launch")}}
  }}}
  pump()
  val tile=compose.onNodeWithTag("game-card-naval")
  tile.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}
  await("This game already has a match or invitation with another contact. Open Games to manage it.")
  assertNull(shadow.nextStartedActivity)
  compose.runOnUiThread{peer.value="+1 (555) 010-0001"}
  pump()
  tile.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}
  var started:android.content.Intent?=null
  compose.waitUntil(10000){pump();started=shadow.nextStartedActivity;started!=null}
  assertEquals(WebGameActivity::class.java.name,started!!.component?.className)
  assertEquals("naval",started!!.getStringExtra("game"))
  assertEquals(matchId,started!!.getStringExtra("friend_match"))
 }
 @Test(timeout=45000) fun navalTapOpensNavalAndLeavesOtherSavedGameAlone(){
  compose.mainClock.autoAdvance=false
  val context=compose.activity
  GameSessionStatus.open(context).save("panda")
  var routed:String?=null
  compose.activity.setContent{OnKlavTheme{GamesCatalog{routed=it}}};pump()
  compose.onNodeWithContentDescription("Games scrollbar").assertExists()
  val hockeyHeight=compose.onNodeWithTag("game-card-hockey").fetchSemanticsNode().boundsInRoot.height
  val navalHeight=compose.onNodeWithTag("game-card-naval").fetchSemanticsNode().boundsInRoot.height
  assertEquals(hockeyHeight,navalHeight,.1f)
  compose.onNodeWithTag("game-card-naval").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}
  compose.waitUntil(10000){pump();routed!=null}
  assertEquals("naval",routed)
  compose.onNodeWithText("Game in progress").assertDoesNotExist()
  assertEquals("panda",GameSessionStatus.open(context).current())
 }
}
