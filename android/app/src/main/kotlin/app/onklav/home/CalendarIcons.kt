package app.onklav.home
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp
private val calendarIcons=mutableMapOf<String,ImageVector>()
private fun calendarIcon(name:String,path:String)=calendarIcons.getOrPut(name){ImageVector.Builder(name,24.dp,24.dp,24f,24f).addPath(PathParser().parsePathString(path).toNodes(),fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round).build()}
internal val Icons.Outlined.Schedule get()=calendarIcon("Time","M12,3a9,9 0,1 1,0 18a9,9 0,1 1,0 -18 M12,7v5l4,2")
internal val Icons.Outlined.Public get()=calendarIcon("Time zone","M12,3a9,9 0,1 1,0 18a9,9 0,1 1,0 -18 M3,12h18 M12,3q-8,9 0,18q8,-9 0,-18")
internal val Icons.Outlined.NotificationsNone get()=calendarIcon("Reminder","M5,17h14l-2,-3V9a5,5 0,0 0,-10 0v5z M10,21h4 M12,2v2")
internal val Icons.Outlined.VolumeOff get()=calendarIcon("Silent","M3,9h4l5,-4v14l-5,-4H3z M16,9l5,6 M21,9l-5,6")
internal val Icons.Outlined.Repeat get()=calendarIcon("Repeat","M4,10V6h15 M16,3l3,3 -3,3 M20,14v4H5 M8,15l-3,3 3,3")
internal val Icons.Outlined.EventAvailable get()=calendarIcon("Calendar","M5,5h14v16H5z M5,9h14 M8,3v4 M16,3v4 M8,15l3,3 5,-6")
