package app.onklav.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.PathFillType

internal object PhoneIcons {
 private fun icon(name:String,draw:PathBuilder.()->Unit)=ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply{path(fill=null,stroke=androidx.compose.ui.graphics.SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineCap=androidx.compose.ui.graphics.StrokeCap.Round,strokeLineJoin=androidx.compose.ui.graphics.StrokeJoin.Round,pathBuilder=draw)}.build()
 val Call=icon("Call"){moveTo(5f,3f);lineTo(9f,3f);lineTo(11f,8f);lineTo(8f,10f);curveTo(9.5f,13f,11f,14.5f,14f,16f);lineTo(16f,13f);lineTo(21f,15f);lineTo(21f,19f);curveTo(21f,23f,10f,20f,5f,13f);curveTo(2f,9f,1f,3f,5f,3f);close()}
 val Voicemail=icon("Voicemail"){moveTo(8f,12f);curveTo(8f,16f,2f,16f,2f,12f);curveTo(2f,8f,8f,8f,8f,12f);lineTo(8f,15f);lineTo(19f,15f);curveTo(23f,15f,23f,9f,19f,9f);curveTo(15f,9f,15f,15f,19f,15f)}
 val End=icon("End call"){moveTo(3f,15f);lineTo(3f,11f);curveTo(7f,7f,17f,7f,21f,11f);lineTo(21f,15f);lineTo(16f,15f);lineTo(16f,12f);curveTo(13f,11f,11f,11f,8f,12f);lineTo(8f,15f);close()}
 val Pad=icon("Keypad"){for(y in listOf(4f,10f,16f))for(x in listOf(6f,12f,18f)){moveTo(x,y);lineTo(x,y+.2f)};moveTo(12f,22f);lineTo(12f,22.1f)}
 val Recent=icon("Recent calls"){moveTo(3f,8f);lineTo(3f,3f);moveTo(3f,8f);lineTo(8f,8f);moveTo(3f,8f);curveTo(7f,-1f,22f,3f,21f,13f);curveTo(20f,22f,7f,25f,3f,16f);moveTo(12f,7f);lineTo(12f,12f);lineTo(16f,14f)}
 val Hold=icon("Hold"){moveTo(8f,5f);lineTo(8f,19f);moveTo(16f,5f);lineTo(16f,19f)}
 val Merge=icon("Merge calls"){moveTo(5f,20f);lineTo(5f,15f);lineTo(12f,9f);lineTo(12f,3f);moveTo(19f,20f);lineTo(19f,15f);lineTo(12f,9f);moveTo(8f,7f);lineTo(12f,3f);lineTo(16f,7f)}
 val Swap=icon("Swap calls"){moveTo(4f,7f);lineTo(20f,7f);lineTo(16f,3f);moveTo(4f,17f);lineTo(20f,17f);moveTo(4f,17f);lineTo(8f,13f);moveTo(4f,17f);lineTo(8f,21f)}
 val Paste=icon("Paste"){moveTo(8f,5f);lineTo(5f,5f);lineTo(5f,21f);lineTo(19f,21f);lineTo(19f,5f);lineTo(16f,5f);moveTo(8f,3f);lineTo(16f,3f);lineTo(16f,7f);lineTo(8f,7f);close()}
 val Backspace=icon("Delete digit"){moveTo(9f,5f);lineTo(21f,5f);lineTo(21f,19f);lineTo(9f,19f);lineTo(2f,12f);close();moveTo(12f,9f);lineTo(18f,15f);moveTo(18f,9f);lineTo(12f,15f)}
 val Up=icon("Outgoing"){moveTo(5f,19f);lineTo(19f,5f);moveTo(9f,5f);lineTo(19f,5f);lineTo(19f,15f)}
 val Down=icon("Incoming"){moveTo(19f,5f);lineTo(5f,19f);moveTo(5f,9f);lineTo(5f,19f);lineTo(15f,19f)}

}
