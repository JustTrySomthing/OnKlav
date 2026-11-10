package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.onklav.shared.R
import app.onklav.shared.contrastInk

internal data class ChatLatestColors(val fill:Color,val ink:Color)
internal fun chatLatestColors(primary:Color):ChatLatestColors {
 val fill=lerp(primary,Color.White,.25f)
 return ChatLatestColors(fill,contrastInk(fill))
}
/** Shared by regular and group feeds; keep the Activities action when already at the latest item. */
@Composable internal fun ChatFeedAction(atLatest:Boolean,modifier:Modifier=Modifier,action:()->Unit){
 val latest=chatLatestColors(MaterialTheme.colorScheme.primary)
 Surface(modifier.size(48.dp),shape=CircleShape,color=if(atLatest)MaterialTheme.colorScheme.primary else latest.fill,shadowElevation=4.dp){
  IconButton(onClick=action,modifier=Modifier.fillMaxSize().testTag("chat-activities")){
   if(atLatest)Icon(painterResource(R.drawable.ref_message_wand),"Conversation Activities",Modifier.size(34.dp),tint=Color.Unspecified)
   else Icon(painterResource(R.drawable.ref_action_jump_latest),"Jump to latest message",Modifier.size(32.dp).testTag("chat-latest-arrow"),tint=latest.ink)
  }
 }
}
