package app.onklav.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.webkit.*
import android.widget.*
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.ZipFile
import java.util.concurrent.Executors

/** One shared Godot web engine for bundled and user-installed games. No JavaScript/Android
 * bridge, file/content access, device permissions, network, popups or external navigation.
 * A separate app process and Chromium's sandboxed renderer contain game crashes/hangs.
 */
class WebGameActivity : androidx.activity.ComponentActivity() {
    private var web: WebView? = null
    private var archive: ZipFile? = null
    private var origin = ""
    private lateinit var gameEngine: GameEngineBundle
    private var builtin = false
    private var builtinId = "four"
    private var active = false
    private var gameReady = false
    private var rendererWarning: AlertDialog? = null
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var panel: LinearLayout
    private var resumed = false
    private var returning = false
    private var friend:JSONObject?=null
    private var turnPolling=false
    private var closing = false
    private var sessionId = ""
    private lateinit var saveStore:GameSaveStore
    private lateinit var saveLease:GameSaveStore.Lease
    private var saveBoot=JSONObject()
    private var saveSlot="resume"
    private var saveSequence=0L
    private var pendingLaunch:Intent?=null
    private var sessionDiscarded=false
    private var launchAction=""
    private var entryView:androidx.compose.ui.platform.ComposeView?=null
    private var entryDialog:AlertDialog?=null

    override fun onCreate(state: Bundle?) {
        // This process never opens the OnKlav Browser's profile or authentication cookies.
        if(!profileReady) { WebView.setDataDirectorySuffix("onklav-games");profileReady=true }
        super.onCreate(state)
        volumeControlStream = AudioManager.STREAM_MUSIC
        WindowCompat.setDecorFitsSystemWindows(window,false)
        window.statusBarColor=Color.TRANSPARENT
        window.navigationBarColor=Color.TRANSPARENT
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id=intent.getStringExtra("game") ?: "four"
        sessionId=id
        panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.TRANSPARENT) }
        setContentView(panel)
        ViewCompat.setOnApplyWindowInsetsListener(panel) { _,insets ->
            val cutout=insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val ime=insets.getInsets(WindowInsetsCompat.Type.ime())
            // Before API30, IME insets can include the ordinary navigation bar.
            val keyboard=if(android.os.Build.VERSION.SDK_INT>=30) {
                if(insets.isVisible(WindowInsetsCompat.Type.ime()))ime.bottom else 0
            } else {
                val bars=insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom
                if(ime.bottom>bars+dp(80))ime.bottom else 0
            }
            val bars=if(showsPortraitBars()) insets.getInsets(WindowInsetsCompat.Type.systemBars()) else androidx.core.graphics.Insets.NONE
            val bottom=maxOf(cutout.bottom,bars.bottom,keyboard)
            panel.setPadding(maxOf(cutout.left,bars.left),maxOf(cutout.top,bars.top),maxOf(cutout.right,bars.right),
                bottom+if(id==GamePackages.LAST_LIGHT_ID) 0 else dp(8))
            WindowInsetsCompat.CONSUMED
        }
        immersive()
        if(android.os.Build.VERSION.SDK_INT>=33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT){ requestReturn() }
        }
        worker.execute {
            try {
                intent.getStringExtra("friend_match")?.takeIf{id !in singlePlayerOnlyGames}?.let{match->
                    val row=FriendGames.find(this,match)?:error("Friend match not found")
                    check(row.getString("game")==id && row.optString("status")in setOf("active","ended")){"Accept the game invitation first"}
                    friend=JSONObject().put("id",match).put("host",row.getBoolean("host")).put("host_dog",row.optString("token")=="fd1").put("panda_rules",when(row.optString("token")){"pt3"->3;"pt2"->2;else->1})
                }
                check(!hidden(this, true, id)) { "Restore this game in Manage games first." }
                builtin=id in BUILTINS
                val orientation: String
                if(builtin) {
                    gameEngine = GameEngineRegistry.load(assets).primary
                    builtinId=id;origin="https://builtin.onklav.invalid";orientation="portrait"
                }
                else {
                    val packages = GamePackages.open(this)
                    val pair = when (id) {
                        GamePackages.SHROOM_ID, GamePackages.LAST_LIGHT_ID -> packages.openIncluded(this, id)
                        else -> packages.open(id)
                    }
                    archive = pair.second
                    gameEngine = packages.engineFor(pair.first)
                    origin=pair.first.origin;orientation=pair.first.manifest.getString("orientation")
                }
                val publisher=if(builtin)"onklav.builtin.$id" else origin
                saveStore=GameSaveStore(noBackupFilesDir,publisher)
                saveLease=saveStore.begin()
                saveSlot=friend?.optString("id")?.let{"friend.$it"}?:"resume"
                saveBoot=saveStore.load(saveSlot).put("slot",saveSlot).put("game",id).put("protocol",2)
                runOnUiThread {
                    if(isFinishing || isDestroyed) { archive?.close();archive=null;return@runOnUiThread }
                    requestedOrientation=when {
                        id==GamePackages.LAST_LIGHT_ID -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        orientation=="landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        orientation=="sensor" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                        else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    }
                    presentEntry()
                }
            } catch(e:Exception) { runOnUiThread { failed(e.message ?: "Game could not be opened") } }
        }
    }
    override fun onNewIntent(next: Intent) {
        super.onNewIntent(next)
        val requested=next.getStringExtra("game") ?: return
        // Every explicit Play resolves the requested title afresh. Never reuse a renderer
        // with a stale menu, session lease or consumed launch action.
        if((web==null && !active)||canLeaveStartup()){closing=true;next.flags=0;finish();startActivity(next);return}
        pendingLaunch=next
        requestReturn()
    }
    private fun presentEntry() {
        panel.setBackgroundColor(Color.TRANSPARENT)
        if(isFinishing || isDestroyed)return
        val record=saveBoot.optJSONObject("record")
        when {
            !saveBoot.optBoolean("ok",false) -> {
                entryDialog=AlertDialog.Builder(this).setTitle("Saved game needs attention")
                    .setMessage("Your saved progress could not be read and has been retained.")
                    .setNegativeButton("Cancel"){_,_->finish()}
                    .setPositiveButton("Open recovery"){_,_->beginGame("recover")}.create()
                entryDialog!!.setOnCancelListener{finish()};entryDialog!!.show()
            }
            saveBoot.optBoolean("exists",false) && record?.optBoolean("resumable",false)==true -> {
                val title=platformGames.firstOrNull { it.id == sessionId }?.title ?: sessionId
                entryDialog=AlertDialog.Builder(this).setTitle(title)
                    .setMessage("Would you like to resume the current game or start a new game?")
                    .setPositiveButton("Resume"){_,_->beginGame("resume")}
                    .setNeutralButton("New Game"){_,_->
                        // Friend New must explicitly leave the peer match; solo saves remain
                        // intact until the replacement successfully initializes and commits.
                        if(friend!=null){endFriend();friend=null;saveSlot="resume";saveBoot=JSONObject().put("protocol",2).put("game",sessionId).put("slot",saveSlot).put("ok",true).put("known",true).put("exists",false)}
                        presentStart()
                    }.setNegativeButton("Cancel"){_,_->finish()}.create()
                entryDialog!!.setOnCancelListener{finish()};entryDialog!!.show()
            }
            friend!=null -> beginGame("new")
            else -> presentStart()
        }
    }
    private fun presentStart() {
        val game=platformGames.firstOrNull{it.id==sessionId}?:PlatformGame(sessionId,
            archive?.getEntry("manifest.json")?.let{entry->archive!!.getInputStream(entry).use{JSONObject(it.readBytes().toString(Charsets.UTF_8)).optString("name",sessionId)}}?:sessionId,
            "Play this installed game.","pets")
        val friendAllowed=sessionId !in singlePlayerOnlyGames && (builtin || archive?.getEntry("manifest.json")?.let{entry->archive!!.getInputStream(entry).use{JSONObject(it.readBytes().toString(Charsets.UTF_8)).optJSONArray("play_modes")?.toString()?.contains("turn_sms")}}==true)
        val content=androidx.compose.ui.platform.ComposeView(this)
        entryView=content;panel.addView(content,LinearLayout.LayoutParams(-1,-1))
        content.setContent {
            app.onklav.shared.OnKlavTheme {
                GameStartDialog(game,friendAllowed,
                    onSolo={beginGame("new")},
                    onFriend={content.setContent{app.onklav.shared.OnKlavTheme{FriendInviteDialog(game,null){finish()}}}},
                    onCancel={finish()})
            }
        }
    }
    private fun beginGame(action:String) {
        if(active || web!=null || closing)return
        launchAction=action
        entryDialog?.dismiss();entryDialog=null
        entryView?.let{panel.removeView(it);it.disposeComposition()};entryView=null
        panel.setBackgroundColor(Color.rgb(7,14,24))
        try { startGame() }
        catch(e:Exception) {
            web?.let(::disposeRenderer)
            failed("Could not start the game renderer.", "Android WebView startup failed (${e.javaClass.simpleName}).", retry=true)
        }
    }
    @Suppress("DEPRECATION", "SetJavaScriptEnabled")
    private fun startGame() {
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            failed("Update Android System WebView to run shared games safely.");return
        }
        val view=WebView(this);web=view
        if (intent.getBooleanExtra(GameAudioSettings.EXTRA_MUTED, false) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)
        ) {
            WebViewCompat.setAudioMuted(view, true)
        }
        gameReady=false
        val graphicsMonitor=if(isIncludedGame()) """
            window.addEventListener('webglcontextlost', function(event) {
                event.preventDefault();
                event.stopImmediatePropagation();
                console.log('ONKLAV_GAME_GRAPHICS_LOST');
            }, true);
        """.trimIndent() else ""
        WebViewCompat.addDocumentStartJavaScript(view,graphicsMonitor+"\n"+GameWebPolicy.BOOTSTRAP+"window.onklavSaveBoot="+saveBoot.toString()+";window.onklavSaveReplies={};window.onklavHosted=true;window.onklavEntryProtocol=1;window.onklavLaunchAction="+JSONObject.quote(launchAction)+";window.onklavFreshStart="+(launchAction=="new")+";window.onklavFriend="+(friend?.toString()?:"null")+";",setOf(origin))
        with(view.settings) {
            javaScriptEnabled=true;domStorageEnabled=true;databaseEnabled=false
            allowFileAccess=false;allowContentAccess=false;allowFileAccessFromFileURLs=false;allowUniversalAccessFromFileURLs=false
            blockNetworkLoads=true;mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically=false;setSupportMultipleWindows(false)
            setGeolocationEnabled(false);mediaPlaybackRequiresUserGesture=false
            cacheMode=WebSettings.LOAD_NO_CACHE
        }
        CookieManager.getInstance().setAcceptCookie(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view,false)
        ServiceWorkerController.getInstance().apply {
            serviceWorkerWebSettings.allowFileAccess=false;serviceWorkerWebSettings.allowContentAccess=false;serviceWorkerWebSettings.blockNetworkLoads=true
            setServiceWorkerClient(object:ServiceWorkerClient(){override fun shouldInterceptRequest(request:WebResourceRequest)=denied()})
        }
        view.webViewClient=gameClient()
        view.webChromeClient=object:WebChromeClient() {
            override fun onConsoleMessage(message:ConsoleMessage):Boolean {
                if(web!==view || closing)return false
                if(message.message().startsWith("ONKLAV_SAVE|")){saveCommand(view,message.message().removePrefix("ONKLAV_SAVE|"));return true}
                if(message.message().startsWith("ONKLAV_TURN|")) { turnCommand(message.message().removePrefix("ONKLAV_TURN|"));return true }
                when(message.message()) {
                    "ONKLAV_SESSION_READY" -> { gameReady=true;return true }
                    "ONKLAV_GAME_GRAPHICS_LOST" -> {
                        if(!isIncludedGame())return false
                        reportRendererFailure(view,"The game lost its graphics connection.","WebGL context lost. Reopen the last saved checkpoint.")
                        return true
                    }
                    "ONKLAV_GAME_EXIT" -> {
                        closing=true
                        endFriend()
                        GameSessionStatus.open(this@WebGameActivity).release(sessionId)
                        finish();return true
                    }
                    "ONKLAV_GAME_START_MENU" -> {
                        closing=true
                        endFriend()
                        GameSessionStatus.open(this@WebGameActivity).release(sessionId)
                        // Older SDKs may request a menu after loading. Return to the list;
                        // never persist a menu request on MainActivity's retained intent.
                        startActivity(Intent(this@WebGameActivity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                        finish();return true
                    }
                    "ONKLAV_GAME_RESUME" -> {
                        GameSessionStatus.open(this@WebGameActivity).save(sessionId)
                        return true
                    }
                    "ONKLAV_GAME_RETURN" -> { returnToOnKlav();return true }
                    "ONKLAV_GAME_SCREEN_ON" -> { window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);return true }
                    "ONKLAV_GAME_SCREEN_OFF" -> { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);return true }
                }
                return false
            }
            override fun onPermissionRequest(r:PermissionRequest){r.deny()}
            override fun onGeolocationPermissionsShowPrompt(o:String,c:GeolocationPermissions.Callback){c.invoke(o,false,false)}
            override fun onShowFileChooser(v:WebView,c:ValueCallback<Array<Uri>>,p:FileChooserParams):Boolean{c.onReceiveValue(null);return true}
            override fun onJsAlert(v:WebView,u:String,m:String,r:JsResult):Boolean{r.cancel();return true}
            override fun onJsConfirm(v:WebView,u:String,m:String,r:JsResult):Boolean{r.cancel();return true}
        }
        view.setWebViewRenderProcessClient(object:WebViewRenderProcessClient(){
            override fun onRenderProcessResponsive(v:WebView,p:WebViewRenderProcess?){
                if(web===v)rendererWarning?.dismiss()
            }
            override fun onRenderProcessUnresponsive(v:WebView,p:WebViewRenderProcess?){
                if(web!==v || closing || isFinishing || isDestroyed || rendererWarning?.isShowing==true)return
                rendererWarning=AlertDialog.Builder(this@WebGameActivity)
                    .setTitle("Game is not responding")
                    .setMessage("Close this game and return to OnKlav?")
                    .setNegativeButton("Wait",null)
                    .setPositiveButton("Close game"){_,_->closing=true;finish()}
                    .show()
            }
        })
        view.setDownloadListener { _,_,_,_,_-> }
        panel.addView(view,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        val fresh=launchAction=="new"
        intent.removeExtra("start_menu")
        val query=mutableListOf<String>()
        if(builtin)query.add("game=$builtinId")
        if(fresh)query.add("start=new")
        view.loadUrl(origin+"/web/index.html"+(if(query.isEmpty())""else"?"+query.joinToString("&")))
    }
    private fun gameClient()=object:WebViewClient() {
        override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest):WebResourceResponse=resource(r.url,r.method)
        override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest)=true
        override fun onPageFinished(v:WebView,url:String){
            if(web===v && !closing && GameWebPolicy.path(origin,Uri.parse(url),"GET")=="web/index.html") {
                active=true;sendGameAction("host_state");pollTurns()
            }
        }
        override fun onReceivedError(v:WebView,request:WebResourceRequest,error:WebResourceError){
            if(request.isForMainFrame)reportLoadFailure(v,"WebView could not load the game page (error ${error.errorCode}).")
        }
        override fun onReceivedHttpError(v:WebView,request:WebResourceRequest,response:WebResourceResponse){
            if(request.isForMainFrame)reportLoadFailure(v,"The local game page returned HTTP ${response.statusCode}.")
        }
        override fun onReceivedSslError(v:WebView,h:SslErrorHandler,e:android.net.http.SslError){h.cancel()}
        override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (web === v) {
                disposeRenderer(v)
                if (!closing && !isFinishing && !isDestroyed) {
                    val message = "The game stopped. Your OnKlav apps are still available."
                    val reason = if (detail.didCrash()) "WebView renderer crashed" else "WebView renderer was terminated"
                    val phase=if(gameReady) "in game" else "during startup"
                    failed(message, "$message $reason $phase (priority ${detail.rendererPriorityAtExit()}).", retry = true)
                }
            }
            return true
        }
    }
    private fun disposeRenderer(view:WebView) {
        if(web!==view)return
        web=null
        rendererWarning?.dismiss();rendererWarning=null
        panel.removeView(view)
        view.destroy()
    }
    private fun reportLoadFailure(view:WebView,detail:String) =
        reportRendererFailure(view,"The game page could not load.",detail)
    private fun reportRendererFailure(view:WebView,message:String,detail:String) {
        // Dispose outside the WebView callback, and ignore events from a retired renderer.
        panel.post {
            if(web!==view || closing || isFinishing || isDestroyed)return@post
            disposeRenderer(view)
            failed(message,detail,retry=true)
        }
    }
    private fun saveCommand(view:WebView,raw:String) {
        if(raw.length>GameSaveStore.LIMIT+16384 || !::saveStore.isInitialized)return
        val command=runCatching{JSONObject(raw)}.getOrNull()?:return
        val sequence=command.optLong("request",-1)
        if(sequence<=saveSequence)return
        saveSequence=sequence
        val requested=command.optString("slot",saveSlot)
        // Reserved session slots cannot be chosen by game content; extra slots are private data.*.
        val slot=if(requested=="session")saveSlot else requested.takeIf{it.matches(Regex("data\\.[a-z][a-z0-9_.-]{0,80}"))}?:return
        worker.execute {
            val response=try {
                when(command.optString("op")) {
                    "save"->{check(slot!=saveSlot || !sessionDiscarded){"This session was discarded. Start a new game before saving."};saveStore.commit(saveLease,slot,command.getJSONObject("state"),command.optInt("version",1),command.optBoolean("resumable",true),command.optBoolean("replace",false))}
                    "load"->saveStore.load(slot)
                    "clear"->saveStore.clear(saveLease,slot).also{if(slot==saveSlot)sessionDiscarded=true}
                    else->JSONObject().put("ok",false).put("error","Unsupported save operation")
                }
            }catch(e:Exception){JSONObject().put("ok",false).put("error",e.message?:"Game save failed")}
            runOnUiThread { if(web===view && !closing)view.evaluateJavascript("window.onklavSaveReplies["+sequence+"]="+response.toString()+";",null) }
        }
    }
    private fun endFriend(){friend?.optString("id")?.let{id->FriendGames.work{runCatching{FriendGames.quit(this,id)}}}}
    private fun turnCommand(raw:String){
        val match=friend?.optString("id")?:return
        if(raw.length>128 || !raw.matches(Regex("[A-Za-z0-9_.,:-]+")))return
        FriendGames.work{try{if(raw=="retry")FriendGames.retry(this,match)else FriendGames.move(this,match,intent.getStringExtra("game")?:return@work,raw)}catch(e:Exception){runOnUiThread{web?.evaluateJavascript("window.onklavTurnError="+JSONObject.quote(e.message?:"Turn update could not be sent"),null)}}}
    }
    private fun pollTurns(){
        if(turnPolling || friend==null || !resumed || closing)return
        turnPolling=true
        val match=friend!!.getString("id");val game=intent.getStringExtra("game")?:return
        FriendGames.work{val result=runCatching{FriendGames.state(this,match,game)};val state=result.getOrNull();runOnUiThread{
            result.exceptionOrNull()?.let{web?.evaluateJavascript("window.onklavTurnError="+JSONObject.quote("Saved turn state could not be read. Return and try again."),null)}
            if(!isDestroyed && resumed && !closing){state?.let{web?.evaluateJavascript("window.onklavTurnSnapshot="+it.toString()+";if(typeof window.onklavTurnState==='function')window.onklavTurnState(window.onklavTurnSnapshot);",null)};panel.postDelayed({turnPolling=false;pollTurns()},1000)}else turnPolling=false
        }}
    }
    private fun local(uri:Uri)=GameWebPolicy.local(origin,uri)
    private fun resource(uri:Uri,method:String):WebResourceResponse {
        val path=GameWebPolicy.path(origin,uri,method) ?: return denied()
        return try {
            val filename=path.removePrefix("web/")
            val engineAsset = gameEngine.asset(filename)
            val stream=if(engineAsset != null)assets.open(engineAsset)
                else if(builtin)assets.open("godot-builtin/$filename")
                else { val zip=archive ?: return denied();val entry=zip.getEntry(path) ?: return denied();zip.getInputStream(entry) }
            val mime=when(filename.substringAfterLast('.')){"html"->"text/html";"js"->"application/javascript";"wasm"->"application/wasm";"png"->"image/png";"svg"->"image/svg+xml";"css"->"text/css";"json"->"application/json";else->"application/octet-stream"}
            WebResourceResponse(mime,if(mime.startsWith("text/")||mime=="application/javascript")"UTF-8" else null,200,"OK",mapOf(
                "Content-Security-Policy" to GameWebPolicy.CSP,
                "X-Content-Type-Options" to "nosniff","Cache-Control" to "no-store","Permissions-Policy" to "camera=(), microphone=(), geolocation=(), usb=(), bluetooth=()"
            ),stream)
        } catch(_:Exception){denied()}
    }
    private fun denied()=WebResourceResponse("text/plain","UTF-8",403,"Blocked",mapOf("Cache-Control" to "no-store"),ByteArrayInputStream(ByteArray(0)))
    private fun showsPortraitBars(): Boolean =
        sessionId==GamePackages.LAST_LIGHT_ID &&
            resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        immersive()
        ViewCompat.requestApplyInsets(panel)
    }

    private fun immersive() {
        WindowCompat.getInsetsController(window,window.decorView).apply {
            systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars=false;isAppearanceLightNavigationBars=false
            if(showsPortraitBars()) show(WindowInsetsCompat.Type.systemBars())
            else hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    private fun sendGameAction(action:String,complete:((Boolean)->Unit)?=null) {
        val view=web
        if(view==null){complete?.invoke(false);return}
        val script="(function(){window.onklavHostSuspended="+(!resumed)+";if(typeof window.onklavGameAction==='function'){window.onklavGameAction("+JSONObject.quote(action)+");return true;}return false;})()"
        view.evaluateJavascript(script){complete?.invoke(it=="true")}
    }
    // Included games cannot save before their session-ready handshake. A loader
    // that has installed the JS callback must not trap Back or a different Play.
    private fun isIncludedGame() = sessionId in BUILTINS || sessionId==GamePackages.SHROOM_ID || sessionId==GamePackages.LAST_LIGHT_ID
    private fun canLeaveStartup() = !gameReady && isIncludedGame()
    private fun requestReturn() {
        // Back from a native entry/cancel/recovery screen has no running game to save.
        if((web==null && !active)||canLeaveStartup()){closing=true;finish();return}
        if(returning)return
        returning=true
        sendGameAction("return") { supported ->
            if(!supported && !isFinishing && !isDestroyed) {
                returning=false
                AlertDialog.Builder(this).setTitle("Return to OnKlav?")
                    .setMessage("The game did not respond to the save request. Your last completed save stays available.")
                    .setNegativeButton("Stay here"){_,_->pendingLaunch=null}
                    .setOnCancelListener{pendingLaunch=null}
                    .setPositiveButton("Leave without saving"){_,_->returnToOnKlav()}.show()
            }
        }
        // A failed save must not trap the user or leave the return control latched.
        panel.postDelayed({returning=false},1500)
    }
    private fun returnToOnKlav() {
        returning=false
        closing=true
        GameSessionStatus.open(this).release(sessionId)
        val next=pendingLaunch
        pendingLaunch=null
        if(next!=null){next.flags=0;finish();startActivity(next)}
        else startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        finish()
    }
    @Deprecated("Handled through the platform back dispatcher on Android33+")
    override fun onBackPressed(){requestReturn()}
    private fun rememberPausedGame() {
        if(active && !closing)GameSessionStatus.open(this@WebGameActivity).save(sessionId)
    }
    override fun onPause(){
        rememberPausedGame()
        resumed=false
        // Keep JS timers available for checkpoint completion and the game's own pause UI.
        // A delayed callback cannot pause a WebView that has already resumed.
        val view=web
        if(view!=null)sendGameAction("host_pause"){if(!resumed && web===view)view.onPause()}
        super.onPause()
    }
    override fun onResume(){
        super.onResume();resumed=true;returning=false
        web?.let { it.onResume();it.resumeTimers() }
        immersive()
        if(active)sendGameAction("host_resume")
        pollTurns()
    }
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus)immersive()}
    override fun onDestroy(){rendererWarning?.dismiss();entryDialog?.dismiss();entryView?.disposeComposition();worker.shutdownNow();web?.let { panel.removeView(it);it.stopLoading();it.destroy() };web=null;archive?.close();archive=null;super.onDestroy()}
    private fun failed(message: String, report: String = message, retry: Boolean = false) {
        GameSessionStatus.open(this).release(sessionId)
        active=false
        gameReady=false
        panel.setBackgroundColor(Color.rgb(7,14,24))
        panel.removeAllViews()
        panel.addView(TextView(this).apply{text=message;setTextColor(Color.WHITE);setPadding(24,24,24,24)})
        if (retry) panel.addView(Button(this).apply {
            text = "Try again"
            setOnClickListener {
                if (closing) return@setOnClickListener
                closing = true
                val next = Intent(intent).setFlags(0)
                finish()
                startActivity(next)
            }
        })
        panel.addView(Button(this).apply {
            text = "Copy report"
            setOnClickListener { GameFailureReport("Launch game", report, sessionId).copyToClipboard(this@WebGameActivity) }
        })
        panel.addView(Button(this).apply{text="Return to OnKlav";setOnClickListener{finish()}})
    }
    private fun dp(value:Int)=(resources.displayMetrics.density*value).toInt()
    companion object {
        private var profileReady=false
        val BUILTINS=mapOf("dominos" to "Neon Dominos","panda" to "Crash Test Panda","hockey" to "Air Hockey","naval" to "Naval Battle","four" to "Cats vs Dogs","blackjack" to "Blackjack")
        fun launch(context:android.content.Context,id:String,fresh:Boolean=false,friend:String?=null){
            try {
            check(!hidden(context, true, id)) { "Restore this game in Manage games first." }
            val match=if(id in singlePlayerOnlyGames)null else friend ?: if(!fresh)FriendGames.resume(context,id)else null
            FriendGames.markCurrent(context,match)
            // The launcher owns this preference. Pass its current value into the games process.
            context.startActivity(Intent(context,WebGameActivity::class.java)
                .putExtra("game",id).putExtra("friend_match",match).putExtra("start_menu",fresh)
                .putExtra(GameAudioSettings.EXTRA_MUTED, !GameAudioSettings.enabled(context))
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            } catch(e:Exception) { android.widget.Toast.makeText(context,e.message?:"Could not open the saved game",android.widget.Toast.LENGTH_LONG).show() }
        }
    }
}
