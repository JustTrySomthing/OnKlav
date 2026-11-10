package app.onklav.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import app.onklav.shared.*
import app.onklav.shared.R

/** The original individual-peer activity chooser. Merely opening it transmits nothing. */
@Composable internal fun ChatActivities(peer:String,name:String,close:()->Unit,voice:()->Unit,game:()->Unit){
    val display=name.ifBlank{displayPhone(peer)}
    ReferenceDialog("",close,centerTitle=true,footer={DialogAction("Cancel",onClick=close)}){
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            listOf(Triple("Voice chat",R.drawable.ref_app_walkie,voice),Triple("Play game",R.drawable.ref_nav_games,game)).forEach{(label,icon,action)->
                Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.background,RoundedCornerShape(18.dp)).clickable(role=Role.Button,onClick=action).padding(vertical=20.dp).testTag("activity-${if(label=="Voice chat")"voice"else"game"}"),horizontalAlignment=Alignment.CenterHorizontally){
                    Icon(painterResource(icon),null,Modifier.size(64.dp),tint=MaterialTheme.colorScheme.primary)
                    Text(label,Modifier.padding(top=10.dp),fontSize=17.sp,textAlign=TextAlign.Center)
                }
            }
        }
        Text("With $display",Modifier.fillMaxWidth().padding(horizontal=20.dp).heightIn(min=72.dp).wrapContentHeight(Alignment.CenterVertically),fontSize=18.sp,textAlign=TextAlign.Center)
    }
}
