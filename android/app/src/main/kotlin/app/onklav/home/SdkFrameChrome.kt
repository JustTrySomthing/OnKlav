package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import app.onklav.home.R
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onklav.shared.*
import org.json.JSONObject

private fun JSONObject.chromeNodes(key:String)=optJSONArray(key)?.let{a->List(a.length()){a.getJSONObject(it)}}?:emptyList()

/** App-owned content uses the same host chrome in production and native previews. */
@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun SdkFrameHeader(frame:JSONObject,blocked:Boolean,progress:Int=100,onBack:()->Unit,onAction:(String)->Unit,showBack:Boolean=true,titleContent:(@Composable (Modifier)->Unit)?=null,afterAction:@Composable (String)->Unit={},controls:@Composable ()->Unit){
 PageHeader(frame.optString("title","Opening App…"),leading=if(showBack){{ClosePageButton(onClick=onBack)}}else null,titleContent=titleContent){
  if(blocked || (frame.optBoolean("header_loading")&&progress<100)) {
   val loading=stringResource(R.string.sdk_loading)
   CircularProgressIndicator(Modifier.size(20.dp).testTag("sdk-app-loading").semantics{contentDescription=loading},strokeWidth=2.dp)
  }
  if(frame.has("counter"))Text(frame.optString("counter"),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  frame.chromeNodes("actions").take(frame.optInt("header_actions",if(frame.optBoolean("full_bleed")&&frame.chromeNodes("body").any{it.optString("provider")=="youtube"})4 else 2).coerceIn(0,4)).forEach{node->
   val enabled=!blocked&&node.optBoolean("enabled",true);val icon=sdkIconResource(node.optString("icon"))
   if(node.has("long_action"))Box(Modifier.size(48.dp).combinedClickable(enabled=enabled,onClick={onAction(node.getString("id"))},onLongClickLabel=node.optString("long_label"),onLongClick={onAction(node.getString("long_action"))}).semantics{contentDescription=node.optString("text");role=Role.Button},contentAlignment=Alignment.Center){Icon(painterResource(icon),null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)}
   else if(!node.has("icon")) TextButton(onClick={onAction(node.getString("id"))},enabled=enabled,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics{contentDescription=node.optString("text")}){Text(node.optString("text"),fontSize=17.sp)}
   else OriginalAction(icon,node.optString("text"),enabled=enabled){onAction(node.getString("id"))}
   afterAction(node.getString("id"))
  }
  controls()
 }
}

@Composable internal fun SdkDrawerContent(frame:JSONObject,scroll:ScrollState,modifier:Modifier=Modifier,onClose:()->Unit,render:@Composable (JSONObject)->Unit){
 Column(modifier.fillMaxHeight().widthIn(max=frame.optInt("drawer_max_width",360).coerceIn(280,420).dp).fillMaxWidth(frame.optDouble("drawer_width",.9).toFloat().coerceIn(.5f,.95f)).background(MaterialTheme.colorScheme.surface).padding(12.dp).verticalScroll(scroll).testTag("sdk-app-drawer")){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   Text(frame.optString("drawer_title","Saved Items"),Modifier.weight(1f),fontSize=19.sp)
   CompactAction(Icons.Outlined.Close,"Close ${frame.optString("drawer_title","Saved Items")}",onClick=onClose)
  }
  frame.chromeNodes("drawer").filterNot{it.optString("id")==frame.optString("drawer_close")}.forEach{render(it)}
 }
}
