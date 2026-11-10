package app.onklav.home
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp
private val essentialsIcons=mutableMapOf<String,ImageVector>()
private fun essentialIcon(name:String,path:String)=essentialsIcons.getOrPut(name){ImageVector.Builder(name,24.dp,24.dp,24f,24f).addPath(PathParser().parsePathString(path).toNodes(),fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round).build()}
internal val Icons.Outlined.Alarm get()=essentialIcon("Alarm","M12,6a7,7 0,1 1,0 14a7,7 0,1 1,0 -14 M12,9v4l3,2 M3,5l3,-3 M18,2l3,3 M7,19l-2,3 M17,19l2,3")
internal val Icons.Outlined.Timer get()=essentialIcon("Timer","M12,7a7,7 0,1 1,0 14a7,7 0,1 1,0 -14 M12,10v5 M9,3h6 M12,3v4 M18,7l2,-2")
internal val Icons.Outlined.RestartAlt get()=essentialIcon("Restart","M5,8a8,8 0,1 1,-1 7 M5,3v6h6")
internal val Icons.Outlined.Pause get()=essentialIcon("Pause","M8,5v14 M16,5v14")
internal val Icons.Outlined.Flag get()=essentialIcon("Lap","M5,22V3q5,-4 9,0q3,3 7,0v11q-4,3 -7,0q-4,-4 -9,0")
internal val Icons.Outlined.MusicNote get()=essentialIcon("Sound","M10,17V5l10,-2v12 M10,8l10,-2 M7,16a3,3 0,1 1,0 6a3,3 0,1 1,0 -6 M17,14a3,3 0,1 1,0 6a3,3 0,1 1,0 -6")
internal val Icons.Outlined.PowerSettingsNew get()=essentialIcon("Power","M12,2v10 M6,5a9,9 0,1 0,12 0")
internal val Icons.Outlined.BrightnessLow get()=essentialIcon("Brightness","M12,8a4,4 0,1 1,0 8a4,4 0,1 1,0 -8 M12,2v2 M12,20v2 M2,12h2 M20,12h2 M5,5l1,1 M18,18l1,1 M5,19l1,-1 M18,6l1,-1")
internal val Icons.Outlined.LightMode get()=Icons.Outlined.BrightnessLow
internal val Icons.Outlined.Cameraswitch get()=essentialIcon("Switch camera","M4,7h4l2,-3h4l2,3h4v12h-7 M4,7v5 M12,10a3,3 0,1 1,0 6 M2,18h7 M6,15l3,3 -3,3")
internal val Icons.Outlined.FlashOff get()=essentialIcon("Flash off","M3,3l18,18 M8,8L5,14h6l-1,8 5,-7 M10,5l2,-4h6l-4,9h4l-1,2")
internal val Icons.Outlined.FlashOn get()=essentialIcon("Flash on","M12,2L5,14h6l-1,8 9,-13h-6l4,-7z")
internal val Icons.Outlined.FlashAuto get()=essentialIcon("Flash auto","M8,2L2,13h5l-1,9 8,-13H9l4,-7z M15,9l3,-7 3,7 M16,6h4")
internal val Icons.Outlined.CameraAlt get()=essentialIcon("Camera","M3,6h5l2,-3h4l2,3h5v15H3z M12,9a4,4 0,1 1,0 8a4,4 0,1 1,0 -8")
internal val Icons.Outlined.GridOn get()=essentialIcon("Grid","M3,3h18v18H3z M3,9h18 M3,15h18 M9,3v18 M15,3v18")
internal val Icons.Outlined.Mic get()=essentialIcon("Microphone","M9,5a3,3 0,0 1,6 0v7a3,3 0,0 1,-6 0z M6,10v2a6,6 0,0 0,12 0v-2 M12,18v4 M8,22h8")
internal val Icons.Outlined.MicOff get()=essentialIcon("Microphone off","M3,3l18,18 M9,5a3,3 0,0 1,6 0v5 M9,11v1a3,3 0,0 0,3 3 M6,10v2a6,6 0,0 0,10 4 M18,10v2 M12,18v4 M8,22h8")
internal val Icons.Outlined.PhotoLibrary get()=essentialIcon("Captures","M6,3h15v15H6z M3,6v15h15 M6,16l5,-5 4,4 2,-2 4,4 M16,6h1")
internal val Icons.Outlined.Stop get()=essentialIcon("Stop","M5,5h14v14H5z")
internal val Icons.Outlined.Videocam get()=essentialIcon("Video","M3,5h12v14H3z M15,10l7,-4v12l-7,-4")

internal val Icons.Outlined.ChevronRight get()=essentialIcon("Next","M9,5l7,7 -7,7")
