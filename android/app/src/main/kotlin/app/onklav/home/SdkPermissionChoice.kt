package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Whole-row target and one checkbox announcement for native SDK permission settings. */
@Composable internal fun SdkPermissionChoice(label:String,checked:Boolean,enabled:Boolean=true,change:(Boolean)->Unit){
 Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=checked,enabled=enabled,role=Role.Checkbox,onValueChange=change),verticalAlignment=Alignment.CenterVertically){
  Checkbox(checked,onCheckedChange=null,enabled=enabled)
  Text(label,Modifier.weight(1f).padding(vertical=8.dp),fontSize=15.sp,color=if(enabled)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
 }
}
