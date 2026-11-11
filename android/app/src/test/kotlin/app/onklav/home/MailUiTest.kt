package app.onklav.home
import androidx.activity.compose.setContent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class MailUiTest{
 @get:Rule val compose=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private var viewportHeight = 936
    private fun roots():List<Any>{
        val env=compose.javaClass.getDeclaredField("environment").apply{isAccessible=true}.get(compose)
        val registry=requireNotNull(env.javaClass.superclass).getDeclaredField("composeRootRegistry").apply{isAccessible=true}.get(env)
        return (registry.javaClass.getMethod("getRegisteredComposeRoots").invoke(registry) as Set<*>).filterNotNull()
    }
    // Do not combine a paused Android vsync clock with Compose's waitForNextChoreographerFrame.
    // Drive bounded frames and inspect the real native screen semantics directly instead.
    private fun pump(){repeat(4){
        Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeBy(32)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        compose.runOnUiThread{roots().forEach{root->val view=root.javaClass.getMethod("getView").invoke(root) as android.view.View
            val decor=view.rootView;decor.forceLayout();decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(432,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(viewportHeight,android.view.View.MeasureSpec.AT_MOST));decor.layout(0,0,decor.measuredWidth,decor.measuredHeight);root.javaClass.getMethod("measureAndLayoutForTest").invoke(root)}}
    }}
    private fun nodes():List<SemanticsNode>{val all=mutableListOf<SemanticsNode>();compose.runOnUiThread{
        fun walk(n:SemanticsNode){all.add(n);n.children.forEach(::walk)}
        roots().forEach{r->val owner=r.javaClass.getMethod("getSemanticsOwner").invoke(r) as SemanticsOwner;walk(owner.unmergedRootSemanticsNode)}
    };return all}
    private fun text(n:SemanticsNode,value:String)=n.config.getOrNull(SemanticsProperties.Text)?.any{it.text==value}==true
    private fun desc(n:SemanticsNode,value:String)=n.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(value)==true
    private fun waitFor(block:()->Boolean){val end=System.nanoTime()+15_000_000_000L;while(System.nanoTime()<end){pump();if(block())return;Thread.sleep(10)};error("Screen condition timed out: "+nodes().joinToString{"${it.config}"})}
    private fun click(value:String,description:Boolean=false){waitFor{nodes().any{if(description)desc(it,value)else text(it,value)}}
        compose.runOnUiThread{var node=nodes().last{if(description)desc(it,value)else text(it,value)};while(!node.config.contains(SemanticsActions.OnClick))node=requireNotNull(node.parent);assertFalse(node.config.contains(SemanticsProperties.Disabled));assertTrue(requireNotNull(node.config[SemanticsActions.OnClick].action).invoke())};pump()}
    private fun capture(name:String){compose.runOnUiThread{val v=compose.activity.window.decorView;val bitmap=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888);v.draw(android.graphics.Canvas(bitmap));val f=java.io.File(requireNotNull(System.getProperty("onklav.project.root")),"docs/evidence/v0.97.0/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}

 private fun input(label:String,value:String){compose.runOnUiThread{fun contains(n:SemanticsNode):Boolean { return text(n,label)||n.children.any{contains(it)} }
val n=nodes().last{it.config.contains(SemanticsActions.SetText)&&contains(it)};assertTrue(requireNotNull(n.config[SemanticsActions.SetText].action).invoke(AnnotatedString(value)))};pump()}
 @Test(timeout=90000) fun themedInboxFoldersFiltersReaderAndRichComposerAreReachable(){
  val app=compose.activity.application;runBlocking{AppSdk.bootstrap(app)};val disk=MailDisk(app,MailTestKeys());val a=MailAccount.preset("Yahoo","charles@yahoo.com").copy(name="Personal");val b=MailAccount(provider="Other",email="charles@example.org",name="Work",imap="mail.example.org",smtp="mail.example.org");disk.saveAccounts(listOf(a,b));val h=MailHeader(a.id,"INBOX",7,11,"Alice <alice@example.org>",a.email,"A weekend together",System.currentTimeMillis(),true,true);val second=MailHeader(b.id,"INBOX",9,14,"Workshop <shop@example.org>",b.email,"Your order is on its way",System.currentTimeMillis()-60000,false,false);disk.saveHeaders(a.id,listOf(h));disk.saveHeaders(b.id,listOf(second));disk.saveFolders(a.id,listOf(MailFolder("INBOX","Inbox","inbox"),MailFolder("Projects","Projects")));disk.write(disk.bodyName(h),MailBody("Let’s plan our weekend.","<p>Let’s plan our <b>weekend</b>.</p>").json())
  val m=MailModel(app,disk,allowNetwork=false)
  org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{EmailContent(m,autoSync=false)}}}
  waitFor{nodes().any{text(it,"A weekend together")}};capture("email-inbox")
  click("Starred");assertTrue(nodes().any{text(it,"A weekend together")});assertFalse(nodes().any{text(it,"Your order is on its way")});click("Inbox")
  click("Folders",true);assertTrue(nodes().any{text(it,"Projects")});click("Done")
  click("Email settings",true);click("Filters");click("Add filter");input("Name","Orders");input("Contains","shop@example.org");click("Save filter");waitFor{m.rules.size==1};assertEquals("shop@example.org",m.rules.single().contains);click("Done")
  click("A weekend together");waitFor{m.body!=null};click("Reply all");waitFor{m.draft!=null};assertEquals("Alice <alice@example.org>",m.draft!!.to);assertFalse(m.draft!!.cc.contains(a.email));assertTrue(nodes().any{desc(it,"Send email")});capture("email-compose")
  click("Save draft and close",true);waitFor{m.draft==null&&!m.busy};click("Drafts");assertTrue(nodes().any{text(it,"Re: A weekend together")});assertEquals(1,disk.drafts().size)
  val saved=disk.drafts().single()
  compose.runOnUiThread{m.editDraft(saved);m.changeDraft(saved.copy(subject="Uncommitted edit"))}
  AppSdk.sync(app,org.json.JSONObject().put("action","permissions").put("id",MAIL_APP).put("grants",org.json.JSONArray()))
  compose.runOnUiThread{m.flush()};waitFor{m.error};assertEquals(saved.subject,disk.drafts().single().subject)
  compose.runOnUiThread{m.cancel()}
 }
 @Test(timeout=90000) fun yahooAndBlankPrivateDomainFields(){
  val app=compose.activity.application;runBlocking{AppSdk.bootstrap(app)};val disk=MailDisk(app,MailTestKeys());val m=MailModel(app,disk,allowNetwork=false);val initial=MailAccount.preset("Yahoo")
  org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
  var closed = false
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{MailAccountDialog(initial,m,{closed = true},{})}}}
  waitFor{!m.busy&&nodes().any{text(it,"Setup directions")}}
  assertTrue(nodes().any{text(it,"GMX")});click("Setup directions");assertTrue(nodes().any{text(it,"Connect Yahoo")});click("Close setup directions",true)
  assertFalse(nodes().any{text(it,"Done")})
  assertTrue(nodes().any{desc(it,"Close email setup")})
  input("Email address","owner@yahoo.com")
  input("App password","fixture-password")
  click("Connect account")
  waitFor{m.error && nodes().any{text(it,"Offline")}}
  assertTrue(nodes().any{it.config.getOrNull(SemanticsProperties.EditableText)?.text=="owner@yahoo.com"})
  assertFalse(closed)
  click("Server settings");assertTrue(nodes().any{it.config.getOrNull(SemanticsProperties.EditableText)?.text=="imap.mail.yahoo.com"});assertTrue(nodes().any{it.config.getOrNull(SemanticsProperties.EditableText)?.text=="smtp.mail.yahoo.com"})
  click("Private domain")
  fun label(n:SemanticsNode):Boolean=text(n,"Incoming server")||text(n,"Outgoing server")||n.children.any{label(it)}
  val servers=nodes().filter{it.config.contains(SemanticsActions.SetText)&&label(it)}
  assertEquals(2,servers.size)
  assertTrue(servers.all{it.config[SemanticsProperties.EditableText].text.isEmpty()})
  viewportHeight = 390
  pump()
  val connect = nodes().first { text(it, "Connect account") }.boundsInRoot
  assertTrue(connect.height > 0 && connect.bottom <= viewportHeight)
  assertTrue(nodes().any{it.config.getOrNull(SemanticsProperties.EditableText)?.text=="owner@yahoo.com"})
  assertFalse(closed)
  click("Close email setup", true)
  assertTrue(closed)
  compose.runOnUiThread{m.cancel()}
 }

 @Test(timeout=60000) fun yahooAndGoogleFormsUseOnlyAppPasswords(){
  val app=compose.activity.application;runBlocking{AppSdk.bootstrap(app)};val m=MailModel(app,MailDisk(app,MailTestKeys()),allowNetwork=false)
  org.robolectric.shadows.ShadowChoreographer.setPaused(true);compose.mainClock.autoAdvance=false
  compose.runOnUiThread{compose.activity.setContent{OnKlavTheme{MailAccountDialog(MailAccount.preset("Gmail"),m,{},{})}}}
  waitFor{!m.busy&&nodes().any{text(it,"Setup directions")}}
  assertFalse(nodes().any{text(it,"Google sign-in")||text(it,"Connect with Google")});assertTrue(nodes().any{text(it,"App password")})
  click("Setup directions");assertTrue(nodes().any{text(it,"Connect Google")});click("Close setup directions",true);click("Yahoo")
  assertTrue(nodes().any{text(it,"App password")});click("Setup directions");assertTrue(nodes().any{text(it,"Connect Yahoo")});click("Close setup directions",true);compose.runOnUiThread{m.cancel()}
 }

}
