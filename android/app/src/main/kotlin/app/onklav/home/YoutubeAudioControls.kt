package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable internal fun YoutubeAudioChoices(original:String,mp3:Boolean,enabled:Boolean,choose:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
        listOf(false,true).forEach{converted->OutlinedCard(onClick={choose(converted)},enabled=enabled,modifier=Modifier.weight(1f).semantics{selected=mp3==converted}){
            Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(selected=mp3==converted,onClick=null,enabled=enabled);Spacer(Modifier.width(6.dp));Column{
                Text(if(converted)"MP3"else original,style=MaterialTheme.typography.titleSmall)
                Text(if(converted)"Slower"else"Faster",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }}
        }}}
    Text(if(mp3)"Downloads first, then converts on this phone. Keep this window open."else"Keeps the original audio format without MP3 conversion.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun YoutubeAudioProgress(download:Float,conversion:Float,converting:Boolean){
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        listOf(Triple("Download",download,true),Triple("Convert to MP3",conversion,converting)).forEach{(label,value,active)->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,style=MaterialTheme.typography.labelMedium);Text(if(!active)"Waiting"else if(value<0)"Working…"else"${(value.coerceIn(0f,1f)*100).toInt()}%",style=MaterialTheme.typography.labelMedium)}
            val modifier=Modifier.fillMaxWidth().semantics{contentDescription=label}
            if(active&&value<0)LinearProgressIndicator(modifier)else LinearProgressIndicator(progress={value.coerceIn(0f,1f)},modifier=modifier)
        }
    }
}
