package app.onklav.home

import app.onklav.shared.LauncherLayout

/** Clean-install row order, read left to right. Extra apps follow the defaults. */
internal object DesktopArrangement {
 const val VERSION=2
 val order=listOf(
  "youtube","browser","radio",
  "podcasts","audiobooks","ebooks",
  "rss","games","write",
  "email","calendar","maps",
  "clock","weather","social",
  "files","cloudfiles","settings",
  "folder-utilities","folder-create"
 )
 fun arrange(catalog:List<String>):LauncherLayout {
  val ordered=order.filter{it in catalog}+catalog.filterNot{it in order}
  return LauncherLayout.read("",ordered)
 }
}
