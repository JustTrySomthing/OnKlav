package app.onklav.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private fun Context.mediaActivity():Activity?=when(this){is Activity->this;is ContextWrapper->baseContext.mediaActivity();else->null}

/** Own the actual Activity/Dialog window and restore the previous bar state on exit. */
@Composable internal fun ImmersiveMediaMode(enabled:Boolean){
 val view=LocalView.current
 DisposableEffect(view,enabled){
  val window=(view.parent as? DialogWindowProvider)?.window?:view.context.mediaActivity()?.window
  val controller=window?.let{WindowInsetsControllerCompat(it,view)}
  val bars=WindowInsetsCompat.Type.systemBars()
  val insets=view.rootWindowInsets?.let{WindowInsetsCompat.toWindowInsetsCompat(it,view)}
  val statusVisible=insets?.isVisible(WindowInsetsCompat.Type.statusBars())?:true
  val navVisible=insets?.isVisible(WindowInsetsCompat.Type.navigationBars())?:true
  val priorBehavior=controller?.systemBarsBehavior
  val keptOn=((window?.attributes?.flags?:0) and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0
  if(enabled){
   controller?.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
   controller?.hide(bars)
   window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }
  onDispose{if(enabled){
   if(statusVisible)controller?.show(WindowInsetsCompat.Type.statusBars())
   if(navVisible)controller?.show(WindowInsetsCompat.Type.navigationBars())
   priorBehavior?.let{controller?.systemBarsBehavior=it}
   if(!keptOn)window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }}
 }
}

/** Video screens own sensor rotation only while open, then restore the user's app orientation. */
@Composable internal fun MediaAutoRotation(video:Boolean){
 val activity=androidx.compose.ui.platform.LocalContext.current.mediaActivity()
 DisposableEffect(activity,video){
  val previous=activity?.requestedOrientation
  if(video)activity?.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
  onDispose{if(video&&previous!=null)activity?.requestedOrientation=previous}
 }
}
