package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun mediaContrast(a:Color,b:Color):Float{val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)}
internal fun messageActionColors(bubble:Color,scheme:ColorScheme):Pair<Color,Color>{
 val pair=listOf(scheme.primary to scheme.onPrimary,scheme.primaryContainer to scheme.onPrimaryContainer,scheme.surface to scheme.onSurface,scheme.inverseSurface to scheme.inverseOnSurface).maxBy{mediaContrast(it.first,bubble)}
 val ink=if(mediaContrast(pair.first,pair.second)>=4.5f)pair.second else listOf(Color.Black,Color.White).maxBy{mediaContrast(it,pair.first)}
 return pair.first to ink
}
@Composable internal fun MessageMediaAction(label:String,bubble:Color,click:()->Unit){
 val (background,ink)=messageActionColors(bubble,MaterialTheme.colorScheme)
 Surface(onClick=click,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).padding(top=4.dp).testTag("message-action-${label.lowercase().replace(' ','-')}"),shape=RoundedCornerShape(12.dp),color=background,contentColor=ink){
  Row(Modifier.padding(horizontal=16.dp,vertical=10.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
   Icon(painterResource(app.onklav.shared.R.drawable.ref_radio_play),null,Modifier.size(22.dp));Spacer(Modifier.width(8.dp));Text(label,fontSize=16.sp)
  }
 }
}
