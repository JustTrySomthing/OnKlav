package app.onklav.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import app.onklav.shared.OnKlavAlertDialog

/** A focused native presentation of the signed SDK converter's existing fields/events.
 * Unit definitions, arithmetic, rounding and persistence remain in its Rust controller. */
@Composable internal fun ConvertWorkspace(model:SdkAppModel,render:@Composable (JSONObject,Modifier)->Unit){
 val nodes=model.frame.nodes("body");if(nodes.isEmpty()){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};return}
 fun node(id:String)=nodes.firstOrNull{it.optString("id")==id}
 val category=node("convert.category")?:return;val colors=MaterialTheme.colorScheme
 var choosing by remember{mutableStateOf(false)}
 val enabled=!model.busy&&model.pending==null
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Surface(onClick={choosing=true},enabled=enabled,shape=RoundedCornerShape(24.dp),color=colors.primaryContainer,modifier=Modifier.fillMaxWidth().testTag("sdk-convert.category")){
   Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
    Icon(painterResource(app.onklav.shared.R.drawable.ref_app_convert),null,Modifier.size(34.dp),tint=colors.onPrimaryContainer)
    Column(Modifier.weight(1f)){Text("MEASUREMENT",style=MaterialTheme.typography.labelSmall,color=colors.onPrimaryContainer.copy(alpha=.7f));Text(category.nodes("options").firstOrNull{it.optString("value")==category.optString("value")}?.optString("text")?:"Length",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=colors.onPrimaryContainer)}
    Text("⌄",style=MaterialTheme.typography.titleLarge,color=colors.onPrimaryContainer)
   }
  }
  fun sideNodes(side:String)=node("convert.$side")
  for((label,side,field)in listOf(Triple("FROM","from","left"),Triple("TO","to","right"))){
   if(field=="right")Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){
    HorizontalDivider(color=colors.outlineVariant)
    FilledTonalIconButton(onClick={model.action("convert.swap")},enabled=enabled,modifier=Modifier.size(48.dp).testTag("sdk-convert.swap")){Icon(painterResource(app.onklav.shared.R.drawable.convert_swap),"Swap units",Modifier.size(24.dp))}
   }
   Surface(shape=RoundedCornerShape(24.dp),color=colors.surfaceContainerLow,modifier=Modifier.fillMaxWidth()){
    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=colors.primary);if(field=="right")IconButton(onClick={model.action("convert.copy")},enabled=enabled&&node("convert.result")?.optString("text").orEmpty().isNotEmpty()){Icon(painterResource(app.onklav.shared.R.drawable.convert_copy),"Copy conversion")}}
     sideNodes(side)?.let{ConvertUnitChoice(it,enabled){value->model.action(it.getString("id"),value)}}
     node("convert.$field")?.let{render(JSONObject(it.toString()).put("font",26).put("label",if(field=="left")"Value to convert"else"Converted value"),Modifier.fillMaxWidth())}
    }
   }
  }
  node("convert.error")?.optString("text")?.takeIf{it.isNotBlank()}?.let{Text(it,color=colors.error,style=MaterialTheme.typography.bodyMedium)}
  node("convert.denominator")?.let{ConvertUnitChoice(it,enabled){value->model.action(it.getString("id"),value)}}
  Text("Edit either value to convert back. Fractions such as 2 ½ can be entered as 2 1/2.",Modifier.padding(horizontal=8.dp),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
 }
 if(choosing)OnKlavAlertDialog(onDismissRequest={choosing=false},title={Text("Measurement")},text={Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
  category.nodes("options").forEach{item->FilledTonalButton(onClick={choosing=false;model.action("convert.category",item.getString("value"))},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(14.dp)){Text(item.getString("text"))}}
 }},confirmButton={TextButton(onClick={choosing=false}){Text("Done")}})
}
@Composable private fun ConvertUnitChoice(node:JSONObject,enabled:Boolean,choose:(String)->Unit){
 var open by remember(node.optString("id")){mutableStateOf(false)}
 Box{Surface(onClick={open=true},enabled=enabled,color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().testTag("sdk-"+node.getString("id")).semantics{contentDescription=node.optString("text")}){
  Row(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Text(node.nodes("options").firstOrNull{it.optString("value")==node.optString("value")}?.optString("text")?:"Choose unit",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Text("⌄")}
 };DropdownMenu(expanded=open&&enabled,onDismissRequest={open=false},modifier=Modifier.heightIn(max=360.dp),shape=RoundedCornerShape(20.dp)){node.nodes("options").forEach{item->DropdownMenuItem(text={Text(item.getString("text"))},onClick={open=false;choose(item.getString("value"))})}}}

}
