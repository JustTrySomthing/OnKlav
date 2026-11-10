package app.onklav.home

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class GameSessionStatusTest {
    @Test fun catalogReaderObservesEveryWriterChangeWithoutPreferenceReload() {
        val dir=Files.createTempDirectory("game-session").toFile()
        try {
            val catalog=GameSessionStatus(dir)
            val game=GameSessionStatus(dir)
            assertNull(catalog.current()) // Prime the catalog before the game returns.
            assertTrue(game.save("hockey"));assertEquals("hockey",catalog.current())
            assertTrue(game.save("four"));assertEquals("four",catalog.current())
            assertTrue(game.save(null));assertNull(catalog.current())
            assertTrue(game.save("hockey"));assertEquals("hockey",GameSessionStatus(dir).current())
        } finally {dir.deleteRecursively()}
    }
    @Test fun quitOrNewCannotResurrectTheOldPreferenceMarker() {
        val dir=Files.createTempDirectory("game-session-legacy").toFile()
        try {
            val status=GameSessionStatus(dir){"hockey"}
            assertEquals("hockey",status.current())
            status.save(null)
            assertNull(GameSessionStatus(dir){"hockey"}.current())
        } finally {dir.deleteRecursively()}
    }
    @Test fun oldOrOversizedHintsCannotBeUsedAsAGameIdentity() {
        val dir=Files.createTempDirectory("game-session-invalid").toFile()
        try {
            java.io.File(dir,"game-session").writeText("x".repeat(161))
            assertNull(GameSessionStatus(dir).current())
            GameSessionStatus(dir).save("app.example.game")
            assertEquals("app.example.game",GameSessionStatus(dir).current())
        } finally {dir.deleteRecursively()}
    }

    @org.junit.Test fun sessionClaimsNeverRedirectOrReleaseAnotherGame() {
        val dir=java.nio.file.Files.createTempDirectory("game-owner").toFile()
        try {
            val first=GameSessionStatus(dir);val second=GameSessionStatus(dir)
            org.junit.Assert.assertTrue(first.claim("panda"))
            org.junit.Assert.assertTrue(second.claim("hockey"))
            org.junit.Assert.assertEquals("hockey",second.current())
            org.junit.Assert.assertTrue(second.release("panda"))
            org.junit.Assert.assertEquals("hockey",first.current())
            org.junit.Assert.assertTrue(first.release("panda"))
            org.junit.Assert.assertTrue(second.claim("four"))
            org.junit.Assert.assertEquals("four",first.current())
        } finally {dir.deleteRecursively()}
    }
}
