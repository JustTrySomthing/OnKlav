package app.onklav.home

import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.onklav.shared.*
import kotlinx.coroutines.*

/** A visible, host-owned publisher page, with no guest JS/DOM/native bridge. */
@Composable internal fun SdkPublisherPage(url:String,appId:String,clipboard:(String)->Boolean,authorize:suspend ()->Unit,close:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var address by remember { mutableStateOf(url) }
    var failure by remember { mutableStateOf<String?>(null) }
    val web=remember { SdkGatedWebView(context,clipboard).apply {
        // The read-only publisher surface has an app-specific browser profile too.
        if(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            WebViewCompat.setProfile(this,SdkNativeData.profile(context,appId))
            WebViewCompat.getProfile(this).serviceWorkerController.serviceWorkerWebSettings.apply { blockNetworkLoads=true;allowFileAccess=false;allowContentAccess=false }
        }

        settings.javaScriptEnabled=false
        settings.allowFileAccess=false;settings.allowContentAccess=false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs=false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs=false
        settings.domStorageEnabled=false;settings.setGeolocationEnabled(false)
        settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        if(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) WebViewCompat.getProfile(this).cookieManager.setAcceptThirdPartyCookies(this,false)
        webChromeClient=object:WebChromeClient() {
            override fun onPermissionRequest(request:PermissionRequest) { request.deny() }
            override fun onShowFileChooser(webView:WebView,callback:android.webkit.ValueCallback<Array<android.net.Uri>>,params:FileChooserParams):Boolean { callback.onReceiveValue(null);return true }
        }
        webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean = runCatching { SdkFeedService.address(request.url.toString());false }.getOrDefault(true)
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? = if(runCatching { SdkFeedService.address(request.url.toString()) }.isSuccess) null else WebResourceResponse("text/plain","UTF-8",java.io.ByteArrayInputStream(ByteArray(0)))
            override fun onPageStarted(view:WebView,u:String,icon:android.graphics.Bitmap?) { if(u=="about:blank") return;address=u;scope.launch { try { authorize() } catch(e:Exception) { view.stopLoading();view.loadUrl("about:blank");failure="Web access was revoked" } } }
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:android.net.http.SslError) { handler.cancel();failure="Secure connection could not be verified" }
        }
        setDownloadListener { _,_,_,_,_ -> failure="Use OnKlav's file export service to save files" }
    } }
    DisposableEffect(web) { onDispose { web.stopLoading();web.removeAllViews();web.destroy() } }
    val permissionsEpoch by AppSdk.epoch.collectAsState()
    LaunchedEffect(permissionsEpoch) { try { authorize() } catch(e:Exception) { web.stopLoading();web.loadUrl("about:blank");failure="Web access was revoked" } }
    LaunchedEffect(url) { try { require(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) { "Update Android System WebView to Use Isolated SDK Websites" };authorize();web.loadUrl(SdkFeedService.address(url)) } catch(e:Exception) { failure=e.message } }
    Dialog(close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BackHandler { if(web.canGoBack()) web.goBack() else close() }
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) { Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            PageHeader("Publisher",leading={app.onklav.shared.ClosePageButton(description="Close publisher",onClick=close)}) { CompactAction(Icons.Outlined.Refresh,"Reload Publisher") { scope.launch { try { authorize();web.reload() } catch(e:Exception) { failure=e.message } } } }
            Text(address,Modifier.fillMaxWidth().padding(12.dp),maxLines=2,fontSize=12.sp)
            AndroidView(factory={web},modifier=Modifier.weight(1f).fillMaxWidth())
        } }
        ErrorDialog(failure) { failure=null }
    }
}
