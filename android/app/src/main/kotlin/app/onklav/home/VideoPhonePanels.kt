@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Staged text editing stays below the video; Apply and Cancel have their ordinary meanings. */
@Composable internal fun VideoTitlePanel(initial:VideoTitle,duration:Long,save:(VideoTitle)->Unit,delete:()->Unit,close:()->Unit,existing:Boolean=true){
 var draft by remember(initial.id){mutableStateOf(initial)}
 var page by remember(initial.id){mutableStateOf("Text")}
 Column(Modifier.fillMaxSize().padding(horizontal=12.dp)){
  Row(Modifier.fillMaxWidth().height(52.dp),verticalAlignment=Alignment.CenterVertically){
   TextButton(onClick=close,modifier=Modifier.heightIn(min=48.dp)){Text("Cancel")}
   Column(Modifier.weight(1f)){Text("Edit text",style=MaterialTheme.typography.titleSmall);Text("Draft · Apply to save",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   TextButton(onClick={save(draft)},enabled=draft.text.isNotBlank()&&draft.end>draft.start,modifier=Modifier.heightIn(min=48.dp)){Text("Apply")}
  }
  VideoInspectorTabs(listOf("Text","Style","Position","Timing","Motion"),page){page=it}
  key(page){VideoScrollableSettings(Modifier.fillMaxWidth().weight(1f)){
   when(page){
    "Text"->{OutlinedTextField(draft.text,{draft=draft.copy(text=it.take(1000))},label={Text("Title or caption")},minLines=1,maxLines=3,modifier=Modifier.fillMaxWidth());if(existing)VideoDangerButton("Delete text"){delete()}}
    "Style"->{
     Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
      listOf(-1,0xFF000000.toInt(),0xFFFFE55C.toInt(),0xFFFF8585.toInt(),0xFF8EDFFF.toInt(),0xFF9AE6B4.toInt()).forEach{color->Box(Modifier.size(48.dp).background(Color(color),RoundedCornerShape(24.dp)).border(if(draft.color==color)3.dp else 1.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(24.dp)).clickable{draft=draft.copy(color=color)}.semantics{contentDescription="Text color ${color.toUInt().toString(16)}";selected=draft.color==color})}
     }
     VideoToggle("Bold",draft.bold,true){draft=draft.copy(bold=it)}
     VideoToggle("Text background",draft.background,true){draft=draft.copy(background=it)}
     VideoControl("Text size",draft.size,.3f..3f,"${(draft.size*100).roundToInt()}%",true,{draft=draft.copy(size=it)},{})
    }
    "Position"->{
     VideoControl("Text horizontal position",draft.x,-.9f.. .9f,"${(draft.x*100).roundToInt()}%",true,{draft=draft.copy(x=it)},{})
     VideoControl("Text vertical position",draft.y,-.9f.. .9f,"${(draft.y*100).roundToInt()}%",true,{draft=draft.copy(y=it)},{})
        }

    "Timing"->{
     Text("${videoPreciseTime(draft.start)} – ${videoPreciseTime(draft.end)}",style=MaterialTheme.typography.labelLarge)
     RangeSlider(value=draft.start.toFloat()..draft.end.toFloat(),onValueChange={r->val a=r.start.toLong().coerceAtMost(draft.end-1);draft=draft.copy(start=a,end=r.endInclusive.toLong().coerceAtLeast(a+1),animationOffset=0,animationDuration=0)},valueRange=0f..duration.toFloat().coerceAtLeast(1f),modifier=Modifier.semantics{contentDescription="Text timing"})
    }
    else->{VideoChoice("Text entrance",draft.entrance,videoTitleAnimations.map{it to it}){draft=draft.copy(entrance=it)};VideoChoice("Text exit",draft.exit,videoTitleAnimations.map{it to it}){draft=draft.copy(exit=it)}}
   }
  }}
 }
}

/** Canvas edits are staged in the dock; export remains a separate final task. */
@Composable internal fun VideoCanvasPanel(project:VideoProject,apply:(VideoProject)->Unit,close:()->Unit){
 var draft by remember(project.id){mutableStateOf(project)}
 Column(Modifier.fillMaxSize().padding(horizontal=12.dp)){
  Row(Modifier.fillMaxWidth().height(52.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=close){Text("Cancel")};Column(Modifier.weight(1f)){Text("Canvas",style=MaterialTheme.typography.titleSmall);Text("Draft · Apply to save",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};TextButton(onClick={apply(draft)}){Text("Apply")}}
  VideoScrollableSettings(Modifier.fillMaxWidth().weight(1f)){
   Text("Aspect ratio", style = MaterialTheme.typography.labelLarge)
   FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    VideoProject.ratios.forEach { ratio ->
     FilterChip(
      selected = draft.ratio == ratio,
      onClick = { draft = draft.copy(ratio = ratio) },
      label = { Text(ratio) },
      modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$ratio canvas" },
     )
    }
   }
   VideoToggle("Fill frame (crop edges)",draft.fill,true){draft=draft.copy(fill=it)}
   Text(if(draft.fill)"Edges outside the canvas are cropped."else"The whole image fits inside the canvas.",style=MaterialTheme.typography.bodySmall)
  }
 }
}
