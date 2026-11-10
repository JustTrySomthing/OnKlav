package app.onklav.home

import app.onklav.shared.LauncherLayout

/** Screenshot-defined row order, read left to right. Extra/custom apps remain available. */
internal object DesktopArrangement {
 const val VERSION=2
 val order=listOf(
  "browser","email","youtube",
  "radio","podcasts","audiobooks",
  "ebooks","rss","games",
  "calendar","clock","weather",
  "write","social","maps",
  "folder-utilities","folder-create","settings",
  "files","cloudfiles"
 )
 fun arrange(catalog:List<String>):LauncherLayout {
  val ordered=order.filter{it in catalog}+catalog.filterNot{it in order}
  return LauncherLayout.read("",ordered)
 }
}
