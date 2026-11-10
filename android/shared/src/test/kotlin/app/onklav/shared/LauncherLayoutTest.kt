package app.onklav.shared

import org.junit.Assert.*
import org.junit.Test

class LauncherLayoutTest {
    private val ids = listOf("notes", "browser", "reader", "rss")

    @Test
    fun sparseBottomRowAndNewPageSurviveReload() {
        val layout =
            LauncherLayout.read("", ids).move("notes", 14).ensurePage(1).move("browser", 19)
        val restored = LauncherLayout.read(layout.encode(), ids)
        assertEquals(layout, restored)
        assertEquals("notes", restored.slots[14])
        assertEquals("browser", restored.slots[19])
        assertEquals("", restored.slots[0])
        assertEquals(2, restored.pages)
    }

    @Test
    fun occupiedDropReordersWithoutLosingHolesOrIds() {
        val start = LauncherLayout.read("", ids).move("rss", 12)
        val moved = start.move("notes", 2)
        assertEquals(listOf("browser", "reader", "notes", ""), moved.slots.take(4))
        assertEquals("rss", moved.slots[12])
        assertEquals(ids.sorted(), moved.slots.filter { it.isNotEmpty() }.sorted())
        assertEquals(start, start.move("missing", 0))
    }

    @Test
    fun cancelSnapshotAndEmptyTrailingPageAreSafe() {
        val snapshot = LauncherLayout.read("", ids)
        val preview = snapshot.ensurePage(2).move("notes", 44)
        assertEquals("notes", snapshot.slots[0])
        assertEquals(3, preview.pages)
        assertEquals(1, preview.move("notes", 0).trimmed().pages)
    }

    @Test
    fun catalogReconciliationRejectsUnknownAndDuplicateIds() {
        val result = LauncherLayout.read("g1|notes,notes,evil,,,rss", ids)
        assertEquals(ids.sorted(), result.slots.filter { it.isNotEmpty() }.sorted())
        assertEquals("notes", result.slots[0])
        assertEquals("rss", result.slots[5])
        assertEquals(64, result.ensurePage(63).pages)
        assertEquals(result, result.ensurePage(64))
    }

    @Test
    fun hitTestingIncludesLastRowAndScrollOffset() {
        assertEquals(29, LauncherLayout.destination(1, 299f, 499f, 300f, 100f, 0f))
        assertEquals(12, LauncherLayout.destination(0, -10f, 200f, 300f, 100f, 250f))
    }
    @Test fun occupiedCrossPageDropUsesGapBeforeTargetBeforeSpilling() {
        val catalog=(0..19).map { "app$it" }
        val start=LauncherLayout.read("g1|"+(listOf("app0","", "app1","app2","app3","app4","app5","app6","app7","app8","app9","app10","app11","app12","app13")+listOf("app14","app15","app16","app17","app18","app19")).joinToString(","),catalog)
        val second=start.slots.drop(15)
        val moved=start.move("app19",14)
        assertEquals(2,moved.pages);assertEquals("app19",moved.slots[14]);assertEquals("app14",moved.slots[15])
        for(i in 15..29) if(start.slots[i]!="app19") assertEquals(start.slots[i],moved.slots[i])
        assertEquals(catalog.sorted(),moved.slots.filter{it.isNotEmpty()}.sorted())
    }
    @Test fun fullPageSpillsOnlyUntilTheFirstFollowingGap() {
        val ids=(0..30).map{"app$it"};val start=LauncherLayout.read("",ids)
        val moved=start.move("app30",14)
        assertEquals("app30",moved.slots[14]);assertEquals("app14",moved.slots[15]);assertEquals("app29",moved.slots[30])
        assertEquals(ids.sorted(),moved.slots.filter{it.isNotEmpty()}.sorted())
        val sparse=start.ensurePage(2).move("app17",35).move("app30",16)
        assertEquals("app30",sparse.slots[16]);assertEquals("app29",sparse.slots[29]);assertEquals("",sparse.slots[30])
    }
    @Test fun occupiedSamePageDropNeverShiftsTheNextPageAndEmptyDropLeavesOthersStill() {
        val ids=(0..18).map{"app$it"};val start=LauncherLayout.read("",ids)
        assertEquals(start.slots.drop(15),start.move("app0",14).slots.drop(15))
        val sparse=start.move("app1",22);val moved=sparse.move("app5",1)
        assertEquals("app5",moved.slots[1]);assertEquals("",moved.slots[5]);assertEquals("app2",moved.slots[2])
    }

}
