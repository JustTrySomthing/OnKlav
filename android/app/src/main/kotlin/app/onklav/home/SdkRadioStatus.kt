package app.onklav.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun SdkRadioStatus(station:String){
 val rows by SdkRadioHealth.notices.collectAsState();val notice=rows[station]
 if(notice!=null&&notice.until>System.currentTimeMillis())Text(notice.label+" · tap to retry",Modifier.padding(start=4.dp,bottom=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
