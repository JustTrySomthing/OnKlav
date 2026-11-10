package app.onklav.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs

internal data class PhotoWheelOption(val key:String,val label:String,val choose:()->Unit)
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable internal fun PhotoOptionsWheel(title:String,options:List<PhotoWheelOption>,selectedKey:String,onDismiss:()->Unit,onChoose:(PhotoWheelOption)->Unit){
 val initial=options.indexOfFirst{it.key==selectedKey}.coerceAtLeast(0)
 val state=rememberLazyListState(initialFirstVisibleItemIndex=initial)
 val fling=rememberSnapFlingBehavior(state)
 var centered by remember{mutableIntStateOf(initial)}
 LaunchedEffect(state){snapshotFlow{state.layoutInfo}.collect{info->val mid=(info.viewportStartOffset+info.viewportEndOffset)/2;info.visibleItemsInfo.minByOrNull{abs(it.offset+it.size/2-mid)}?.let{centered=it.index}}}
 val rowHeight=52.dp*androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f,2f)
 val surface=MaterialTheme.colorScheme.surfaceContainerHigh;val ink=MaterialTheme.colorScheme.primary
 Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.widthIn(max=480.dp).fillMaxWidth(.94f),shape=RoundedCornerShape(28.dp),color=surface,tonalElevation=6.dp){
   Column(Modifier.padding(16.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,"Close photo options")}}
    Text("Tap a choice or Use to apply it. Scrolling only browses.",style=MaterialTheme.typography.bodySmall)
    Box(Modifier.fillMaxWidth().height(260.dp),contentAlignment=Alignment.Center){
     Box(Modifier.fillMaxWidth().height(rowHeight).clip(RoundedCornerShape(16.dp)).background(ink.copy(alpha=.13f)))
     LazyColumn(state=state,flingBehavior=fling,contentPadding=PaddingValues(vertical=(260.dp-rowHeight)/2),modifier=Modifier.fillMaxSize().testTag("photo-options-wheel")){
      items(options.size,key={options[it].key}){i->
       TextButton(onClick={onChoose(options[i])},modifier=Modifier.fillMaxWidth().height(rowHeight).semantics{selected=options[i].key==selectedKey;stateDescription=if(options[i].key==selectedKey)"Current choice"else"Tap to choose"},colors=ButtonDefaults.textButtonColors(contentColor=if(centered==i)ink else MaterialTheme.colorScheme.onSurfaceVariant)){
        Text(options[i].label,style=if(centered==i&&options[i].label.length<17)MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
       }
      }
     }
     Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(38.dp).background(Brush.verticalGradient(listOf(surface,androidx.compose.ui.graphics.Color.Transparent))))
     Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(38.dp).background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent,surface))))
    }
    Button(onClick={onChoose(options[centered.coerceIn(options.indices)])},modifier=Modifier.fillMaxWidth().testTag("photo-options-use")){Text("Use ${options[centered.coerceIn(options.indices)].label}",maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}
   }
  }
 }
}
