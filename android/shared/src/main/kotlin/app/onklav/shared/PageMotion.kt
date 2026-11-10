package app.onklav.shared

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/** Keep the outgoing screen alive until it has travelled fully off the right edge. */
@Composable
fun <T:Any> RightSlideRoute(route:T?, opaque:Boolean=true, popup:Boolean=false, content:@Composable (T)->Unit) {
    var retained by remember { mutableStateOf(route) }
    var retainedOpaque by remember { mutableStateOf(opaque) }
    var retainedPopup by remember { mutableStateOf(popup) }
    SideEffect { if(route!=null) {retained=route;retainedOpaque=opaque;retainedPopup=popup} }
    val centered=if(route!=null)popup else retainedPopup
    AnimatedVisibility(
        visible=route!=null,
        enter=if(centered) fadeIn(tween(180))+scaleIn(tween(230,easing=FastOutSlowInEasing),initialScale=.96f) else slideInHorizontally(tween(420,easing=FastOutSlowInEasing)){it},
        exit=if(centered) fadeOut(tween(140))+scaleOut(tween(160),targetScale=.96f) else slideOutHorizontally(tween(360,easing=FastOutSlowInEasing)){it},
    ) {
        val current=route?:retained
        if(current!=null) {
            if(if(route!=null)opaque else retainedOpaque) Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()){OnKlavBackground(Modifier.matchParentSize());Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background));Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(MaterialTheme.colorScheme.background));content(current)} }
            else Box(Modifier.fillMaxSize()){content(current)}
        }
    }
}
