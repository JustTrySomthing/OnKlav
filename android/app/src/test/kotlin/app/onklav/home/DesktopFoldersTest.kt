package app.onklav.home

import app.onklav.shared.LauncherLayout
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class DesktopFoldersTest{
 @Test fun defaultsGroupOnlyRequestedAppsAndKeepSettingsAndGamesReachable(){val f=DesktopFolders.defaults();assertEquals(listOf("compress","docuview","authentic","formatswap")+DesktopFolders.utilityMoves,f.folders[0].apps);assertEquals(listOf("videoeditor","audioeditor","photo","voicerecorder","screenrecorder","codeedit"),f.folders[1].apps);val ids=f.desktop(platformApps.map{it.id});assertTrue("settings" in ids);assertTrue("games" in ids);assertFalse("compress" in ids);assertEquals(ids.size,ids.distinct().size)}
 @Test fun membershipHasNoNineAppLimitAndMovesWithoutDuplicates(){var f=DesktopFolders(emptyList()).add("Many").add("Other");val a=f.folders[0].id;val b=f.folders[1].id;repeat(101){f=f.put(a,"app$it")};assertEquals(101,f.folders[0].apps.size);f=f.put(b,"app50");assertEquals(100,f.folders[0].apps.size);assertEquals(listOf("app50"),f.folders[1].apps);f=f.put(b,"app50");assertEquals(1,f.folders[1].apps.size);assertEquals(f,DesktopFolders.read(f.encode()))}
 @Test fun deletingFolderReturnsAppsAndPreservesOtherDesktopPositions(){val f=DesktopFolders.defaults();val catalog=listOf("phone","settings","compress","docuview");val layout=LauncherLayout.read("",f.desktop(catalog)).move("phone",14);val next=f.delete("folder-utilities");val restored=LauncherLayout.read(layout.encode(),next.desktop(catalog));assertEquals("phone",restored.slots[14]);assertTrue("compress" in restored.slots);assertTrue("docuview" in restored.slots)}
 @Test fun savedEmptyLayoutDoesNotReseedAndMissingPackagesKeepMembership(){val f=DesktopFolders(emptyList());assertTrue(DesktopFolders.read(f.encode()).folders.isEmpty());val populated=f.add("Private").let{it.put(it.folders.single().id,"sdk-example")};assertTrue(populated.desktop(emptyList()).contains(populated.folders.single().id));assertEquals(listOf("sdk-example"),DesktopFolders.read(populated.encode()).folders.single().apps)}
 @Test fun namesValidateAndFirstLetterTracksRename(){val f=DesktopFolders.defaults().rename("folder-create","  Studio  ");assertEquals("Studio",f.folders[1].name);assertEquals("S",f.folders[1].letter);assertThrows(IllegalArgumentException::class.java){f.add("  ")};assertThrows(IllegalArgumentException::class.java){f.add("bad\nname")};assertThrows(Exception::class.java){DesktopFolders.read("not-json")}}
 @Test fun requestedDefaultsExactlyMatchBothScreenshotPages(){
  val folders=DesktopFolders.defaults().withRequestedFolders()
  val ids=folders.desktop(platformApps.filter{it.id !in setOf("phone","camera")}.map{it.id})
  val layout=DesktopArrangement.arrange(ids)
  assertEquals(DesktopArrangement.order,layout.slots.filter{it.isNotBlank()});assertEquals(2,layout.pages)
  assertEquals(listOf("folder-utilities","folder-create","settings","files","cloudfiles"),layout.slots.drop(15).take(5))
  assertTrue(layout.slots.drop(20).all{it.isBlank()})
 }
 @Test fun migrationMovesRequestedAppsAndKeepsCustomFolderData(){
  var old=DesktopFolders(listOf(DesktopFolder("folder-utilities","Utilities",listOf("compress")),DesktopFolder("folder-create","Create",listOf("photo")),DesktopFolder("folder-custom","Private",listOf("voicerecorder","vault","browser","sdk-example"))))
  val migrated=old.withRequestedFolders();assertEquals(listOf("browser","sdk-example"),migrated.folders.last().apps)
  assertTrue("voicerecorder" in migrated.folders.first{it.id=="folder-create"}.apps)
  assertTrue(migrated.folders.first{it.id=="folder-utilities"}.apps.containsAll(DesktopFolders.utilityMoves))
  assertEquals(migrated,migrated.withRequestedFolders());assertEquals(migrated,DesktopFolders.read(migrated.encode()))
 }

}
