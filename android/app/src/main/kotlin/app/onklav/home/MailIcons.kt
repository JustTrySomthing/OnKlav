package app.onklav.home

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

/** Compact outline icons share the 24dp grid and inherit the active OnKlav tint. */
private val mailIcons=mutableMapOf<String,ImageVector>()
private fun mailIcon(name:String,path:String)=mailIcons.getOrPut(name){ImageVector.Builder(name,24.dp,24.dp,24f,24f).addPath(PathParser().parsePathString(path).toNodes(),fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round).build()}
internal val Icons.Outlined.ExpandMore get()=mailIcon("Expand more","M7,10l5,5 5,-5")
internal val Icons.Outlined.Archive get()=mailIcon("Archive","M4,8v12h16V8 M3,4h18v4H3z M9,12h6")
internal val Icons.Outlined.AttachFile get()=mailIcon("Attach file","M8,12l7,-7a4,4 0,0 1,6 6L10,22a6,6 0,0 1,-8 -8L13,3 M6,16L17,5a1.5,1.5 0,0 1,2 2L8,18a1.5,1.5 0,0 1,-2 -2")
internal val Icons.Outlined.Download get()=mailIcon("Download","M12,3v12 M7,10l5,5 5,-5 M4,16v4h16v-4")
internal val Icons.Outlined.Reply get()=mailIcon("Reply","M10,5L3,11l7,6 M3,11h10q7,0 8,9q0,-9 -8,-9")
internal val Icons.Outlined.Forward get()=mailIcon("Forward","M14,5l7,6 -7,6 M21,11H11q-7,0 -8,9q0,-9 8,-9")
internal val Icons.Outlined.Drafts get()=mailIcon("Drafts","M3,9l9,-6 9,6v12H3z M3,9l9,7 9,-7 M3,21l6,-7 M21,21l-6,-7")
internal val Icons.Outlined.MarkEmailRead get()=mailIcon("Mark read","M21,12V5H3v14h10 M3,5l9,7 9,-7 M15,18l3,3 5,-6")
internal val Icons.Outlined.StarOutline get()=mailIcon("Star","M12,3l2.8,5.7 6.2,.9 -4.5,4.4 1.1,6.2 -5.6,-3 -5.6,3 1.1,-6.2 -4.5,-4.4 6.2,-.9z")
internal val Icons.Outlined.AllInbox get()=mailIcon("All inboxes","M4,3h16v8h-5l-2,3h-2l-2,-3H4z M4,16v5h16v-5")
internal val Icons.Outlined.ManageAccounts get()=mailIcon("Manage accounts","M8,3a4,4 0,1 1,0 8a4,4 0,1 1,0 -8 M2,20v-2q0,-4 6,-4h2 M17,12l2,2 3,0 0,3 -2,2 0,3 -3,0 -2,-2 -3,0 0,-3 2,-2 0,-3z M17,15a2,2 0,1 1,0 4a2,2 0,1 1,0 -4")
internal val Icons.Outlined.FilterList get()=mailIcon("Filters","M3,5h18 M6,11h12 M9,17h6")
internal val Icons.Outlined.Visibility get()=mailIcon("Show password","M2,12q10,-15 20,0q-10,15 -20,0 M12,8a4,4 0,1 1,0 8a4,4 0,1 1,0 -8")
internal val Icons.Outlined.VisibilityOff get()=mailIcon("Hide password","M2,3l20,18 M2,12q4,-6 8,-7 M14,5q5,1 8,7q-3,5 -7,6 M11,19q-5,0 -9,-7")
internal val Icons.Outlined.CreateNewFolder get()=mailIcon("New folder","M3,5h7l2,3h9v13H3z M12,11v7 M8.5,14.5h7")
internal val Icons.Outlined.FormatListBulleted get()=mailIcon("Bullets","M8,6h13 M8,12h13 M8,18h13 M3,6h1 M3,12h1 M3,18h1")
internal val Icons.Outlined.FormatListNumbered get()=mailIcon("Numbered list","M9,6h12 M9,12h12 M9,18h12 M3,3h1v5 M2,8h4 M2,12q4,-3 4,0l-4,4h4 M2,19h3l-2,2q4,-1 3,2H2")
internal val Icons.Outlined.FormatClear get()=mailIcon("Remove list","M3,5h12 M9,5v15 M5,20h8 M3,2l19,20")
internal val Icons.Outlined.FormatColorText get()=mailIcon("Text color","M6,17l6,-14 6,14 M8,12h8 M4,21h16")
internal val Icons.Outlined.FormatColorFill get()=mailIcon("Highlight","M4,14l8,-9 8,8 -8,8z M6,3l9,9 M3,22h18 M21,16q-3,4 0,4q3,0 0,-4")
internal val Icons.Outlined.Link get()=mailIcon("Link","M10,15l5,-5 M9,16L7,18a4,4 0,0 1,-6 -6l5,-5a4,4 0,0 1,6 0 M15,8l2,-2a4,4 0,0 1,6 6l-5,5a4,4 0,0 1,-6 0")
internal val Icons.Outlined.FormatAlignLeft get()=mailIcon("Align left","M3,4h18 M3,9h12 M3,14h18 M3,19h12")
internal val Icons.Outlined.FormatAlignCenter get()=mailIcon("Align center","M3,4h18 M6,9h12 M3,14h18 M6,19h12")
internal val Icons.Outlined.FormatAlignRight get()=mailIcon("Align right","M3,4h18 M9,9h12 M3,14h18 M9,19h12")
