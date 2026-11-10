package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.onklav.shared.*

@Composable internal fun ReaderTabs(items:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit){
 val spec=LocalAppearanceSpec.current
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  items.forEach{(value,title)->val active=value==selected
   Box(Modifier.weight(1f).height(48.dp).clickable(role=Role.Tab){onSelect(value)}.semantics{this.selected=active}.testTag("reader-tab-$value").padding(horizontal=4.dp,vertical=6.dp).then(if(active)Modifier.background(sentBrush(spec),RoundedCornerShape(8.dp)).border(1.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(8.dp))else Modifier.background(MaterialTheme.colorScheme.surface,RoundedCornerShape(8.dp))),contentAlignment=Alignment.Center){Text(title,fontSize=17.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(active&&spec.dark&&spec.palette in listOf(2,4,6))sentInk(spec)else MaterialTheme.colorScheme.primary)}
  }
 }
}
