package app.onklav.home

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.view.View
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewCompat
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlinx.coroutines.*
import app.onklav.shared.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object SdkWebPolicy {
    fun external(raw:String):String {
        // The browser never follows content/file/javascript/intent URLs or embedded credentials.
        require(raw.length<=4096 && raw.none { it.isISOControl() || it.isWhitespace() || it=='\\' }) { "Enter an HTTPS Web Address" }
        val parsed=requireNotNull(raw.toHttpUrlOrNull()) { "Enter an HTTPS Web Address" }
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) { "Website Credentials Must Not Be Embedded in Addresses" }
        val safe=parsed.newBuilder().scheme("https").port(if(parsed.scheme=="http" && parsed.port==80) 443 else parsed.port).build().toString()
        return safe
    }
    fun isGoogleSignIn(url: String): Boolean = Uri.parse(url).host?.trimEnd('.') == "accounts.google.com"
    fun address(raw:String):String { val safe=external(raw);require(!isGoogleSignIn(safe)) { "Continue This Sign-In with Open in Browser" };return safe }
    fun profile(app:String):String = "onklav_"+java.security.MessageDigest.getInstance("SHA-256").digest(app.toByteArray()).joinToString("") { "%02x".format(it) }
    fun bounded(bundle:Bundle):Boolean { val p=Parcel.obtain();return try { p.writeBundle(bundle);p.dataSize()<=256*1024 } catch(_:Exception) { false } finally { p.recycle() } }
}
/** Request a browser-backed tab. Browsers without Custom Tabs handle the same HTTPS intent normally. */
internal fun openSecureBrowser(context: android.content.Context, raw: String): Boolean = runCatching {
    val url = Uri.parse(SdkWebPolicy.external(raw))
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url)
        .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        .putExtras(Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
    context.startActivity(intent)
}.isSuccess

/** Small host memory cache. Rust owns the authoritative eight-tab metadata. */
internal class SdkWebPool {
    data class Entry(val state:Bundle,val seq:Long)
    var progress by mutableIntStateOf(100)
    var findStatus by mutableStateOf<String?>(null)
    var downloadRequest by mutableLongStateOf(0L)
    var downloadHandled=0L
    val videoStreams=YoutubeStreams()
    var autoplayVideo:String?=null
    var onBack:(()->Boolean)?=null
    fun back():Boolean=onBack?.invoke() ?: false
    val entries=linkedMapOf<String,Entry>()
    fun save(tab:String,state:Bundle,seq:Long) { entries.remove(tab);if(SdkWebPolicy.bounded(state)) entries[tab]=Entry(state,seq);while(entries.size>8) entries.remove(entries.keys.first()) }
}

/** One native surface; late events from a replaced renderer cannot change its UI. */
internal class SdkWebPage {
    var view by mutableStateOf<WebView?>(null);private set
    var failure by mutableStateOf<String?>(null)
    var progress by mutableIntStateOf(100)
    var rendererLost by mutableStateOf(false);private set
    var navigation=0L;private set
    fun attach(next:WebView) { navigation++; view=next;rendererLost=false;failure=null;progress=100;clearAfterLoad=-1 }
    fun current(candidate:WebView)=view===candidate
    fun started(candidate:WebView) { if(current(candidate)) { navigation++;failure=null;progress=0 } }
    fun failed(candidate:WebView,message:String) { if(current(candidate)) failure=message }
    fun progressed(candidate:WebView,value:Int) { if(current(candidate)) progress=value }
    fun detach(candidate:WebView,lost:Boolean=false):Boolean {
        if(!current(candidate)) return false
        navigation++;view=null;progress=100;rendererLost=lost;clearAfterLoad=-1
        previewRule=null;previewEnabled=null
        return true
    }
    private var previewRule: androidx.webkit.ScriptHandler? = null
    private var previewEnabled: Boolean? = null
    fun configurePreviews(view: WebView, enabled: Boolean, applyNow: Boolean) {
        if (previewEnabled == enabled) return
        val script = SdkMediaScripts.feedPreviews(enabled)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            previewRule?.remove()
            previewRule = WebViewCompat.addDocumentStartJavaScript(view, script,
                SdkVideoPolicy.hosts.map { "https://$it" }.toSet())
        }
        previewEnabled = enabled
        if (applyNow && SdkVideoPolicy.page(view.url.orEmpty())) view.evaluateJavascript(script, null)
    }
    private var clearAfterLoad=-1L
    fun cleared(candidate:WebView,seq:Long,currentSeq:Long,expectedNavigation:Long,authorize:()->Unit,load:()->Unit) {
        if(!current(candidate) || seq!=currentSeq || navigation!=expectedNavigation) return
        try { authorize();clearAfterLoad=seq;load() }
        catch(e:Exception) { failure=e.message ?: "Website Access Is Off" }
    }
    fun finished(candidate:WebView,seq:Long) {
        if(current(candidate) && clearAfterLoad==seq) { candidate.clearHistory();clearAfterLoad=-1 }
    }
    fun back(authorize:()->Unit):Boolean {
        val active=view ?: return false
        if(!active.canGoBack()) return false
        try { authorize();navigation++;active.goBack() } catch(e:Exception) { failure=e.message ?: "Website Access Is Off" }
        return true
    }
}

@Composable internal fun SdkWebComponent(node:JSONObject,model:SdkAppModel,pool:SdkWebPool,modifier:Modifier) {
    val owner=LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) { val observer=LifecycleEventObserver { _,_ -> foreground=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) };owner.lifecycle.addObserver(observer);onDispose { owner.lifecycle.removeObserver(observer) } }
    val epoch by AppSdk.epoch.collectAsState()
    var permission by remember(node.optString("id"),epoch) { mutableStateOf<String?>(null) }
    LaunchedEffect(epoch,node.optString("id")) { permission=runCatching { model.webComponent(node.getString("id"));null }.getOrElse { it.message ?: "Website Access Is Off" } }
    Box(modifier.fillMaxWidth().testTag("sdk-web-surface")) {
        if(permission!=null) Text(permission.orEmpty(),Modifier.padding(12.dp),fontSize=14.sp)
        else if(foreground) key(node.getString("tab"),epoch) { SdkActiveWeb(node,model,pool) }
    }
}

// Keep older WebView file-access switches disabled on supported Android versions.
@Suppress("DEPRECATION", "SetJavaScriptEnabled")
@Composable private fun SdkActiveWeb(node:JSONObject,model:SdkAppModel,pool:SdkWebPool) {
    val context=LocalContext.current
    val id=node.getString("id");val tab=node.getString("tab")
    val latest by rememberUpdatedState(node)
    val downloadScope=rememberCoroutineScope()
    var download by remember{mutableStateOf<FileDownload?>(null)}
    var downloading by remember{mutableStateOf(false)}
    var downloadStatus by remember{mutableStateOf<String?>(null)}
    var downloadSaved by remember{mutableStateOf(false)}
    var downloadJob by remember{mutableStateOf<Job?>(null)}
    download?.let{request->ReferenceDialog("Save Download?",{downloadJob?.cancel();download=null},footer={
        DialogAction("Cancel"){downloadJob?.cancel();download=null}
        DialogAction(if(downloading)"Saving…"else"Save",enabled=!downloading){downloading=true;downloadJob=downloadScope.launch{
            try{if (model.webComponent(id).optString("app") == "app.onklav.browser") {
                model.browserDownloads.enqueue(id, request)
                download=null
            } else {
                FilesDownloads.save(context,model,id,request)
                download=null;downloadSaved=true
            }}
            catch(e:CancellationException){throw e}catch(e:Exception){downloadStatus=e.message?:"Download could not be saved"}
            finally{downloading=false}
        }}
    }){Text(request.name+"\n\nSave this file in OnKlav Downloads?",Modifier.padding(20.dp))}}
    ErrorDialog(downloadStatus){downloadStatus=null}
    if(downloadSaved)ReferenceDialog("Download Saved",{downloadSaved=false},footer={DialogAction("Close"){downloadSaved=false}}){Text("Find it in OnKlav Files.",Modifier.padding(20.dp))}
    val page=remember { SdkWebPage() }
    var recreation by remember { mutableIntStateOf(0) }
    var recoveryCommand by remember { mutableStateOf(false) }
    var applied by remember { mutableLongStateOf(-1) }
    var fullscreen by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var navigating by remember { mutableStateOf(false) }
    val provider=node.optString("provider")=="youtube"
    val previewControl=provider && model.managesYoutubePreviews
    var youtubeDownloadOpen by remember{mutableStateOf(false)}
    LaunchedEffect(pool.downloadRequest){if(provider&&pool.downloadRequest>pool.downloadHandled){pool.downloadHandled=pool.downloadRequest;youtubeDownloadOpen=true}}
    if(provider)YoutubeDownloadDialog(model,id,pool,page.view,youtubeDownloadOpen){youtubeDownloadOpen=false}
    fun authorize():JSONObject = model.webComponent(id).also { require(it.child("node").optString("tab")==tab) { "Tab Is Closed" } }
    fun hideFullscreen() { val callback=fullscreenCallback;fullscreen=null;fullscreenCallback=null;callback?.onCustomViewHidden() }
    var secureSignIn by remember(tab) { mutableStateOf<String?>(null) }
    var secureBrowserUnavailable by remember(tab) { mutableStateOf(false) }
    fun offerSecureSignIn(raw: String, source: String? = null): Boolean {
        if (provider) return false
        val url = SdkWebPolicy.external(raw)
        if (!SdkWebPolicy.isGoogleSignIn(url)) return false
        // Restart at the referring page so the browser can create its own provider session.
        secureSignIn = source?.let { runCatching { SdkWebPolicy.address(it) }.getOrNull() } ?: url
        secureBrowserUnavailable = false
        return true
    }
    fun safe(raw:String):String { val url=SdkWebPolicy.address(raw);if(provider) require(SdkVideoPolicy.page(url) && !SdkVideoPolicy.account(url)) { "Use a Signed-Out YouTube Page" };return url }
    fun load(view: WebView, raw: String) { if (!offerSecureSignIn(raw)) view.loadUrl(safe(raw)) }
    val orientation=androidx.compose.ui.platform.LocalConfiguration.current.orientation
    val wide by rememberUpdatedState(orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    fun updateViewport(view:WebView){
        if(provider&&page.current(view)&&runCatching{authorize();safe(view.url.orEmpty())}.isSuccess)
            view.evaluateJavascript(if(wide)SdkMediaScripts.landscape else SdkMediaScripts.portrait,null)
    }
    LaunchedEffect(provider,orientation) {
        if(provider){if(!wide)hideFullscreen();page.view?.let(::updateViewport)}
    }
    fun notice(kind:String,url:String) { if(latest.has("on_notice")) model.webUpdate(id,tab,JSONObject().put("kind",kind).put("url",url),latest.optLong("epoch")) }
    var lastMetadata by remember{mutableStateOf("")}
    LaunchedEffect(node.optLong("epoch")){lastMetadata=""}
    fun metadata(view:WebView) {
        if(provider&&page.current(view)){val expected=view.url.orEmpty();view.evaluateJavascript(YoutubeDownloadScripts.manifest){raw->runCatching{if(!page.current(view)||!SdkVideoPolicy.sameDocument(expected,view.url.orEmpty()))return@runCatching;val text=org.json.JSONTokener(raw).nextValue() as? String?:return@runCatching;pool.videoStreams.bind(SdkVideoPolicy.video(expected));pool.videoStreams.manifest(JSONObject(text))}}}
        val expected=view.url ?: return;val generation=latest.optLong("epoch")
        view.postDelayed({ if(page.view===view && generation==latest.optLong("epoch") && runCatching { authorize();safe(expected) }.isSuccess) {
            view.evaluateJavascript(SdkMediaScripts.metadata) { raw ->
                runCatching {
                    require(page.view===view && generation==latest.optLong("epoch"));authorize()
                    val decoded=org.json.JSONTokener(raw).nextValue() as? String ?: return@runCatching
                    val value=JSONObject(decoded);val current=view.url ?: return@runCatching
                    require(SdkVideoPolicy.sameDocument(expected,current) && SdkVideoPolicy.sameDocument(current,value.getString("url")))
                    value.put("back",view.canGoBack()).put("forward",view.canGoForward())
                    if(value.toString()!=lastMetadata){lastMetadata=value.toString();model.webUpdate(id,tab,value,generation)}
                }
            }
        } },250)
    }
    fun emit(view:WebView,url:String) {
        if(page.view===view && (!provider || (!navigating && view.url==url)) && runCatching { authorize();safe(url) }.isSuccess) {
            model.webUpdate(id,tab,JSONObject().put("url",url).put("title",view.title.orEmpty()).put("back",view.canGoBack()).put("forward",view.canGoForward()),latest.optLong("epoch"))
            if(provider) metadata(view)
        }
    }
    LaunchedEffect(provider,page.view){
        if(provider)while(true){page.view?.takeIf{page.current(it)&&it.progress>=100}?.let(::metadata);delay(1500)}
    }
    DisposableEffect(tab) {
        val recovery=DefaultNetworkRecovery(context){if(page.failure!=null)runCatching{authorize();page.view?.let{view->safe(view.url.orEmpty());page.failure=null;view.reload()}}}

        val back:()->Boolean={page.back{authorize()}}
        pool.onBack=back
        onDispose {
        recovery.close()
        if(pool.onBack===back) pool.onBack=null
        hideFullscreen();pool.progress=100;pool.findStatus=null
        page.view?.let { view ->
            if(provider) runCatching { view.evaluateJavascript(SdkMediaScripts.pause,null) }
            runCatching { val state=Bundle();view.saveState(state);pool.save(tab,state,applied) }
            page.detach(view)
            view.stopLoading();view.onPause();view.removeAllViews();view.destroy()
        }
    } }
    Column(Modifier.fillMaxSize()) {
        if(node.optBoolean("progress_inline",true) && page.progress in 0..99) LinearProgressIndicator(progress={page.progress/100f},modifier=Modifier.fillMaxWidth().height(3.dp))
        val signInUrl = secureSignIn
        if (signInUrl != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(if (secureBrowserUnavailable) "No browser could open this sign-in." else "Google sign-in opens in a secure browser.", fontSize = 13.sp)
                TextButton(onClick = {
                    val access = runCatching { authorize() }
                    if (access.isFailure) {
                        secureSignIn = null
                        page.failure = access.exceptionOrNull()?.message
                    } else if (openSecureBrowser(context, signInUrl)) {
                        secureSignIn = null
                        page.failure = null
                    } else secureBrowserUnavailable = true
                }) { Text("Continue in secure browser") }
                TextButton(onClick = { secureSignIn = null; page.failure = null }) { Text("Cancel") }
            }
        } else page.failure?.let { Text(it,Modifier.padding(8.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.error) }
        key(recreation) { AndroidView(modifier=Modifier.weight(1f).fillMaxWidth(),factory={ ctx ->
            android.widget.FrameLayout(ctx).also { container ->
                try {
                    val auth=authorize()
                    require(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) { "Update Android System WebView to Use Isolated SDK Websites" }
                    val view=SdkGatedWebView(ctx,model::clipboardAllowed)
                    try {
                        // Profile is selected before any website/settings/state access.
                        WebViewCompat.setProfile(view,SdkNativeData.profile(ctx,auth.getString("app")))
                        val profile=WebViewCompat.getProfile(view)
                        profile.serviceWorkerController.serviceWorkerWebSettings.apply { blockNetworkLoads=true;allowFileAccess=false;allowContentAccess=false }
                        view.settings.apply {
                            javaScriptEnabled=auth.child("node").optBoolean("javascript")
                            domStorageEnabled=true;allowFileAccess=false;allowContentAccess=false
                            allowFileAccessFromFileURLs=false
                            allowUniversalAccessFromFileURLs=false
                            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            mediaPlaybackRequiresUserGesture=!provider;setGeolocationEnabled(false)
                            setSupportMultipleWindows(false);builtInZoomControls=true;displayZoomControls=false;safeBrowsingEnabled=true
                        }
                        if (previewControl) page.configurePreviews(view, model.youtubePreviews, applyNow = false)
                        profile.cookieManager.setAcceptThirdPartyCookies(view,false)
                        view.webChromeClient=object:WebChromeClient() {
                            override fun onProgressChanged(v:WebView,value:Int) { if(page.current(v)) { page.progressed(v,value);pool.progress=value } }
                            override fun onReceivedTitle(v:WebView,title:String?) { if(provider && page.current(v)) metadata(v) }
                            override fun onShowCustomView(custom:View,callback:CustomViewCallback) { if(!page.current(view) || fullscreen!=null) callback.onCustomViewHidden() else { fullscreen=custom;fullscreenCallback=callback } }
                            override fun onHideCustomView() { if(page.current(view)) hideFullscreen() }
                            override fun onPermissionRequest(request:PermissionRequest) { request.deny() }
                            override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback) { callback.invoke(origin,false,false) }
                            override fun onShowFileChooser(v:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean { callback.onReceiveValue(null);return true }
                        }
                        view.webViewClient=object:WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                                if (!page.current(v)) return true
                                return runCatching {
                                    authorize()
                                    val raw = request.url.toString()
                                    if (request.isForMainFrame) {
                                        if (provider) {
                                            if (SdkVideoPolicy.account(raw)) {
                                                notice("account", raw)
                                                return true
                                            }
                                            if (!SdkVideoPolicy.page(raw)) {
                                                if (raw.startsWith("https://")) notice("external", raw)
                                                return true
                                            }
                                        } else if (offerSecureSignIn(raw, v.url)) return true
                                    }
                                    val target = safe(raw)
                                    if (target == raw) return false
                                    if (request.isForMainFrame) v.loadUrl(target)
                                    true
                                }.getOrElse { page.failure = it.message; true }
                            }
                            override fun shouldInterceptRequest(v:WebView,request:WebResourceRequest):WebResourceResponse? = if(runCatching { check(page.current(v));authorize();val raw=request.url.toString();SdkWebPolicy.address(raw);if(provider){pool.videoStreams.observe(raw)};if(provider) require(!SdkVideoPolicy.blocked(raw) && !SdkVideoPolicy.account(raw) && (!request.isForMainFrame || SdkVideoPolicy.page(raw))) }.isSuccess) null else WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))
                            override fun onPageStarted(v:WebView,url:String,icon:Bitmap?) { if(!page.current(v)) return;page.started(v);if(provider)pool.videoStreams.bind(SdkVideoPolicy.video(url));pool.findStatus=null;v.setFindListener(null);navigating=false;if(runCatching { authorize();if(offerSecureSignIn(url)) v.stopLoading() else { secureSignIn=null; safe(url) } }.isFailure) { v.stopLoading();page.failure="Website Access Is Off or the Address Is Not Allowed" } }
                            override fun onPageCommitVisible(v: WebView, url: String) {
                                if (previewControl && page.current(v) && SdkVideoPolicy.page(url))
                                    v.evaluateJavascript(SdkMediaScripts.feedPreviews(model.youtubePreviews), null)
                            }
                            override fun onPageFinished(v:WebView,url:String) { if(!page.current(v)) return;page.finished(v,latest.optLong("seq"));if(provider && runCatching { authorize();safe(url) }.isSuccess){
                                val autoplay=pool.autoplayVideo?.takeIf{it==SdkVideoPolicy.video(url)}
                                v.evaluateJavascript((if(previewControl)SdkMediaScripts.feedPreviews(model.youtubePreviews)else"")+(autoplay?.let{SdkMediaScripts.autoplay(it)}?:"")+SdkMediaScripts.youtube,null)
                                if(autoplay!=null)pool.autoplayVideo=null
                                updateViewport(v)
                            };emit(v,url) }
                            override fun doUpdateVisitedHistory(v:WebView,url:String,reload:Boolean) { emit(v,url) }
                            override fun onReceivedError(v:WebView,request:WebResourceRequest,error:WebResourceError) { if(page.current(v) && request.isForMainFrame) { page.failure="Could Not Load This Page. Check Your Connection and Tap Reload.";notice("error",latest.getString("url")) } }
                            override fun onReceivedSslError(v:WebView,handler:SslErrorHandler,error:android.net.http.SslError) { handler.cancel();page.failed(v,"The Website’s Secure Connection Could Not Be Verified") }
                            override fun onRenderProcessGone(v:WebView,detail:RenderProcessGoneDetail):Boolean { if(page.detach(v,true)) { hideFullscreen();pool.progress=100;pool.findStatus=null;pool.entries.remove(tab);page.failure="The Website Closed. Tap Reload to Continue." };container.removeView(v);v.destroy();return true }
                        }
                        view.setDownloadListener { url,_,disposition,mime,length ->
                            try{if(!page.current(view))return@setDownloadListener;authorize();require(!latest.has("provider")){"This provider does not offer file downloads"};val safe=SdkWebPolicy.external(url);require(length<=FilesBroker.MAX_FILE){"Choose a download smaller than 256 MB"};download=FileDownload(safe,FilesBroker.cleanName(URLUtil.guessFileName(safe,disposition,mime)),mime?:"application/octet-stream",length)}catch(e:Exception){page.failure=e.message}
                        }
                        container.addView(view,android.widget.FrameLayout.LayoutParams(-1,-1));page.attach(view)
                        val old=pool.entries.remove(tab)
                        val current=latest
                        if(!recoveryCommand && old!=null && old.seq==current.optLong("seq") && view.restoreState(old.state)!=null) applied=old.seq
                        else if(recoveryCommand && current.getString("command") in setOf("reset","clear")) { applied=current.getLong("seq")-1 }
                        else { load(view, current.getString("url"));applied=current.getLong("seq") }
                        recoveryCommand=false
                    } catch(e:Exception) { page.detach(view);view.destroy();throw e }
                } catch(e:Exception) { page.failure=e.message ?: "Website Component Could Not Start" }
            }
        },update={ _ ->
            val n=latest;val seq=n.getLong("seq");val view=page.view
            if(view==null && page.rendererLost && seq!=applied) {
                pool.entries.remove(tab);recoveryCommand=true;applied=seq;recreation++
            }
            if (view != null && previewControl) page.configurePreviews(view, model.youtubePreviews, applyNow = true)
            if(view!=null && seq!=applied) {
                try {
                    authorize();page.failure=null;secureSignIn=null
                    if(provider && n.getString("command") in setOf("load","back","forward","reload","reset")) navigating=true
                    when(n.getString("command")) {
                        "load"->load(view, n.getString("url"))
                        "back"->if(view.canGoBack()) view.goBack()
                        "forward"->if(view.canGoForward()) view.goForward()
                        "reload"->view.reload()
                        "find","find.next","find.previous"->{
                            val navigation=page.navigation
                            view.setFindListener { at,total,finished ->
                                if(finished && page.current(view) && navigation==page.navigation && latest.optLong("seq")==seq && runCatching{authorize()}.isSuccess)
                                    pool.findStatus=if(total==0)"No Matches"else "${at+1} of $total"
                            }
                            if(n.getString("command")=="find") {
                                pool.findStatus=if(n.optString("find").isBlank())null else "Searching…"
                                view.findAllAsync(n.optString("find").take(512))
                            } else view.findNext(n.getString("command")=="find.next")
                        }
                        "find.close"->{view.setFindListener(null);view.clearMatches();pool.findStatus=null;if(n.optBoolean("clear_history")){view.clearHistory();pool.entries.clear()}}
                        "reset","clear"->{
                            check(WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
                                "Update Android System WebView to Clear This App’s Browsing Data"
                            }
                            hideFullscreen();view.stopLoading();if(provider) view.evaluateJavascript(SdkMediaScripts.pause,null)
                            val profile=WebViewCompat.getProfile(view);val navigation=page.navigation;pool.entries.clear();view.clearHistory();pool.findStatus=null
                            // This instance belongs to the authenticated app profile. Never
                            // clear the default/global WebView cache or another app's data.
                            WebStorageCompat.deleteBrowsingData(profile.webStorage) {
                                page.cleared(view,seq,latest.optLong("seq"),navigation,{authorize()}) {
                                    load(view, n.getString("url"))
                                }
                            }
                        }
                    };applied=seq
                } catch(e:Exception) { view.stopLoading();page.failure=e.message }
            }
        }) }
    }
    fullscreen?.let { custom ->
        Dialog(onDismissRequest={hideFullscreen()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
            ImmersiveMediaMode(true)
            AndroidView(modifier=Modifier.fillMaxSize(),factory={ctx -> android.widget.FrameLayout(ctx).apply { (custom.parent as? android.view.ViewGroup)?.removeView(custom);addView(custom,android.widget.FrameLayout.LayoutParams(-1,-1)) } })
        }
    }
}
