package app.onklav.home

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.onklav.shared.R

internal class PhotoMarkupState{
 var tool by mutableStateOf("pen")
 var color by mutableIntStateOf(0xFFFF5252.toInt())
 var width by mutableFloatStateOf(3f)
 var selected by mutableStateOf<String?>(null)
 var text by mutableStateOf("")
 var textSize by mutableFloatStateOf(.055f)
 var anchor by mutableStateOf(androidx.compose.ui.geometry.Offset(.15f,.35f))
 var editing by mutableStateOf<String?>(null)
 var error by mutableStateOf<String?>(null)
 fun choose(value:String){tool=value;error=null;if(value=="highlight"&&color==0xFFFF5252.toInt())color=0xFFFFDF4B.toInt()}
 fun edit(m:PhotoMark){tool="text";selected=m.id;editing=m.id;text=m.text;color=m.color;textSize=m.textSize;anchor=androidx.compose.ui.geometry.Offset(m.x,m.y);error=null}
 fun note(w:Int,h:Int)=PhotoMarkup.placeText(PhotoMark(id=editing?:java.util.UUID.randomUUID().toString(),tool="text",color=color,x=anchor.x,y=anchor.y,text=text.trim(),textSize=textSize),w,h)
 fun finish(){text="";editing=null;error=null}
}
internal val photoMarkColors=listOf(0xFFFF5252,0xFFFFDF4B,0xFF48D999,0xFF4999FF,0xFFB08AFF,0xFFFFFFFF,0xFF171A22).map{it.toInt()}
@Composable internal fun PhotoMarkupPanel(state:PhotoMarkupState,edit:PhotoEdit,enabled:Boolean,w:Int,h:Int,beforeToolChange:()->Boolean={true},onRetouch:(()->Unit)?=null,retouchActive:Boolean=false,onMarks:(List<PhotoMark>)->Unit){
 val current=edit.marks.firstOrNull{it.id==state.selected}
 val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
 val textIme=!retouchActive&&state.tool=="text"&&WindowInsets.ime.getBottom(LocalDensity.current)>0
 fun applyText(){runCatching{check(state.editing==null||edit.marks.any{it.id==state.editing&&it.tool=="text"}){"This text changed. Select it again."};val mark=state.note(w,h);mark.validate();val next=if(state.editing==null)edit.marks+mark else edit.marks.map{if(it.id==state.editing)mark else it};PhotoMarkup.validate(next);onMarks(next);state.selected=mark.id;state.finish();state.tool="move";focus.clearFocus();keyboard?.hide()}.onFailure{state.error=it.message}}
 @Composable fun toolRow(modifier:Modifier=Modifier){Row(modifier,verticalAlignment=Alignment.CenterVertically){
   listOf(Triple("pen","Pen",R.drawable.photo_tool_pen),Triple("highlight","Highlighter",R.drawable.photo_tool_highlight),Triple("text","Text",R.drawable.photo_tool_text),Triple("move","Move marks",R.drawable.photo_tool_hand),Triple("erase","Erase marks",R.drawable.photo_tool_eraser)).forEach{(key,label,icon)->
    Box(Modifier.weight(1f),contentAlignment=Alignment.Center){IconToggleButton(checked=!retouchActive&&state.tool==key,onCheckedChange={
     if((retouchActive||key!=state.tool)&&!beforeToolChange())return@IconToggleButton
     focus.clearFocus();keyboard?.hide()
     if(key=="text"&&state.text.isBlank()&&state.editing==null){val crop=photoCropBounds(w,h,edit);state.anchor=androidx.compose.ui.geometry.Offset((crop.exactCenterX()/w-.15f).coerceIn(0f,1f),(crop.exactCenterY()/h-.04f).coerceIn(0f,1f))}
     state.choose(key)},enabled=enabled,modifier=Modifier.size(44.dp).semantics{contentDescription=label;stateDescription=if(!retouchActive&&state.tool==key)"Selected"else""}){Icon(painterResource(icon),null,Modifier.size(23.dp),tint=if(!retouchActive&&state.tool==key)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}}
   }
   if(onRetouch!=null)Box(Modifier.weight(1f),contentAlignment=Alignment.Center){IconButton(onClick=onRetouch,enabled=enabled,modifier=Modifier.size(48.dp)){Icon(painterResource(R.drawable.photo_tool_retouch),"Retouch",Modifier.size(23.dp))}}
 }}
 @Composable fun colorRow(modifier:Modifier=Modifier){Row(modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly){
    photoMarkColors.forEachIndexed{i,color->IconButton(enabled=enabled,onClick={state.color=color},modifier=Modifier.size(48.dp).semantics{contentDescription="Mark color ${listOf("red","yellow","green","blue","violet","white","black")[i]}";selected=state.color==color}){
     Canvas(Modifier.size(21.dp)){drawCircle(Color(color));drawCircle(if(state.color==color)Color(0xFFB8A2FF)else Color.Gray,radius=size.minDimension/2+if(state.color==color)3.dp.toPx()else 0f,style=Stroke(if(state.color==color)2.dp.toPx()else 1.dp.toPx()))}
    }}
 }}
 @Composable fun sizeControl(){
  if(state.tool=="text")PhotoDefaultSlider("Text size",state.textSize,.055f,.015f.. .18f,enabled,{state.textSize=it},{},format={"${(it*1000).toInt()}"})
  else PhotoDefaultSlider("Stroke width",state.width,3f,1f..12f,enabled,{state.width=it},{},format={"%.1f".format(it)})
 }
 BoxWithConstraints(Modifier.fillMaxWidth()){
 val wide=maxWidth>600.dp
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)){
  if(!textIme){
   if(wide)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
    toolRow(Modifier.width(240.dp))
    if(!retouchActive&&state.tool in setOf("pen","highlight","text")){colorRow(Modifier.weight(1f));Box(Modifier.width(190.dp)){sizeControl()}}
   }else{
    toolRow(Modifier.fillMaxWidth())
    if(!retouchActive&&state.tool in setOf("pen","highlight","text")){colorRow(Modifier.fillMaxWidth());sizeControl()}
   }
  }
  if(!retouchActive&&state.tool=="text"){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
    OutlinedTextField(state.text,{state.text=it.take(400).let{s->if(s.lastOrNull()?.isHighSurrogate()==true)s.dropLast(1)else s};state.error=null},enabled=enabled,modifier=Modifier.weight(1f),placeholder={Text("Text · tap image to place")},minLines=1,maxLines=2,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={if(enabled&&state.text.isNotBlank())applyText()}))
    if(textIme)IconButton(onClick={focus.clearFocus();keyboard?.hide()}){Canvas(Modifier.size(21.dp).semantics{contentDescription="Text appearance"}){drawCircle(Color(state.color))}}
    IconButton(onClick={applyText()},enabled=enabled&&state.text.isNotBlank()){Icon(painterResource(R.drawable.photo_tool_check),if(state.editing==null)"Add text"else"Apply text")}
   }
  }
  if(!retouchActive&&(state.tool=="move"||state.tool=="erase"))Row(Modifier.fillMaxWidth().heightIn(min=36.dp),verticalAlignment=Alignment.CenterVertically){
   Text(if(state.tool=="erase")"Tap or drag over a mark to erase"else if(current!=null)"Drag the selected mark"else"Drag a mark · pinch to zoom",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   if(current?.tool=="text"&&state.tool=="move")IconButton(enabled=enabled,onClick={state.edit(current)}){Icon(painterResource(R.drawable.photo_tool_pen),"Edit selected text")}
   if(current!=null)IconButton(enabled=enabled,onClick={onMarks(edit.marks.filter{it.id!=current.id});state.selected=null;if(state.editing==current.id)state.finish()}){Icon(painterResource(R.drawable.video_edit_delete),"Delete selected mark")}
  }
  state.error?.takeIf{!retouchActive}?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)}
 }
}
}

/** Colorize tint is edited locally; Cancel leaves the photo and undo history untouched. */
@Composable internal fun PhotoColorizeDialog(edit:PhotoEdit,enabled:Boolean,onDismiss:()->Unit,onColor:(Float,Float,Float)->Unit){
 var hue by remember{mutableFloatStateOf(edit["colorizeHue"])}
 var saturation by remember{mutableFloatStateOf(edit["colorizeSaturation"])}
 var value by remember{mutableFloatStateOf(edit["colorizeValue"])}
 val color=android.graphics.Color.HSVToColor(floatArrayOf(hue,saturation,value))
 AlertDialog(onDismissRequest=onDismiss,title={Text("Colorize color")},text={
  Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Surface(Modifier.fillMaxWidth().height(48.dp).semantics{contentDescription="Selected tint #"+Integer.toHexString(color).takeLast(6)},color=Color(color),shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)){}
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())){
    listOf("Red" to 0f,"Orange" to 30f,"Yellow" to 60f,"Green" to 120f,"Cyan" to 180f,"Blue" to 240f,"Violet" to 270f,"Magenta" to 300f).forEach{(name,angle)->
     IconButton(enabled=enabled,onClick={hue=angle;saturation=1f;value=1f},modifier=Modifier.size(48.dp).semantics{contentDescription="Colorize $name";selected=hue==angle&&saturation==1f&&value==1f}){
      Canvas(Modifier.size(24.dp)){drawCircle(Color(android.graphics.Color.HSVToColor(floatArrayOf(angle,1f,1f))));drawCircle(Color.Gray,style=Stroke(1.dp.toPx()))}
     }
    }
   }
   Text("Hue",style=MaterialTheme.typography.labelMedium)
   PhotoDefaultSlider("Tint hue",hue,30f,0f..360f,enabled,{hue=it},{},format={"${it.toInt()}°"})
   Text("Color saturation",style=MaterialTheme.typography.labelMedium)
   PhotoDefaultSlider("Tint saturation",saturation,1f,0f..1f,enabled,{saturation=it},{},format={"${(it*100).toInt()}%"})
   Text("Color brightness",style=MaterialTheme.typography.labelMedium)
   PhotoDefaultSlider("Tint brightness",value,1f,0f..1f,enabled,{value=it},{},format={"${(it*100).toInt()}%"})
   Text("The chosen color tints the photo’s light and dark detail. Adjust Look strength to blend with the original colors.",style=MaterialTheme.typography.bodySmall)
  }
 },confirmButton={TextButton(enabled=enabled,onClick={onColor(hue,saturation,value)}){Text("Use color")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
