package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.*

/** Native presentation over the signed calculator's existing arithmetic and history. */
@Composable internal fun CalcWorkspace(model:SdkAppModel){
 val state=model.frame.child("state");val busy=model.busy||model.pending!=null
 BoxWithConstraints(Modifier.fillMaxSize()){
 val scale=androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
 val minimumDisplay=144.dp*scale
 val compact=maxHeight<480.dp||maxHeight<minimumDisplay+396.dp
 val displayHeight=if(compact)minimumDisplay else (maxHeight*.22f).coerceIn(minimumDisplay,180.dp*scale)
 Column(Modifier.fillMaxSize().then(if(compact)Modifier.verticalScroll(rememberScrollState())else Modifier).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Surface(Modifier.height(displayHeight).fillMaxWidth().semantics{contentDescription="Calculator display"},shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainerLow){
   Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.Bottom,horizontalAlignment=Alignment.End){
    Text(state.optString("expr").ifBlank{"0"},Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),textAlign=TextAlign.End,fontSize=22.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
    Spacer(Modifier.height(12.dp));Text(state.optString("result","0"),Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).combinedClickable(onClick={},onLongClick={model.action("calc.copy")}).semantics{contentDescription="Calculation result"},textAlign=TextAlign.End,fontSize=44.sp,fontWeight=FontWeight.Medium,maxLines=1)
    if(state.optString("error").isNotBlank())Text(state.optString("error"),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
   }
  }
  val rows=listOf(listOf("(" to "calc.key.(",")" to "calc.key.)","%" to "calc.key.%","AC" to "calc.clear"),listOf("7" to "calc.key.7","8" to "calc.key.8","9" to "calc.key.9","÷" to "calc.key.÷"),listOf("4" to "calc.key.4","5" to "calc.key.5","6" to "calc.key.6","×" to "calc.key.×"),listOf("1" to "calc.key.1","2" to "calc.key.2","3" to "calc.key.3","−" to "calc.key.−"),listOf("+/−" to "calc.sign","0" to "calc.key.0","." to "calc.key..","+" to "calc.key.+"))
  rows.forEach{row->Row(Modifier.fillMaxWidth().then(if(compact)Modifier else Modifier.weight(1f)),horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{(label,id)->val op=id=="calc.clear"||label in listOf("+","−","×","÷");FilledTonalButton(enabled=!busy,onClick={model.action(id)},modifier=Modifier.weight(1f).then(if(compact)Modifier.heightIn(min=52.dp)else Modifier.fillMaxHeight()),shape=RoundedCornerShape(18.dp),contentPadding=PaddingValues(4.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=if(op)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)){Text(label,fontSize=23.sp)}}}}
  Row(Modifier.fillMaxWidth().then(if(compact)Modifier else Modifier.weight(1f)),horizontalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedButton(enabled=!busy,onClick={model.action("calc.backspace")},modifier=Modifier.weight(1f).then(if(compact)Modifier.heightIn(min=52.dp)else Modifier.fillMaxHeight()),shape=RoundedCornerShape(18.dp)){Text("⌫",Modifier.semantics{contentDescription="Delete last digit"},fontSize=25.sp)}
   Button(enabled=!busy,onClick={model.action("calc.equals")},modifier=Modifier.weight(3f).then(if(compact)Modifier.heightIn(min=52.dp)else Modifier.fillMaxHeight()),shape=RoundedCornerShape(18.dp)){Text("=",Modifier.semantics{contentDescription="Equals"},fontSize=30.sp)}
  }
 }
}
}
