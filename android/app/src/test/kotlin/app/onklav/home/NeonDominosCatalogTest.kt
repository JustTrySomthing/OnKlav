package app.onklav.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.onklav.shared.OnKlavTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NeonDominosCatalogTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun includedGameUsesSoloEntryArtworkAndTheSharedHost() {
        val context=compose.activity
        val game=platformGames.single{it.id=="dominos"}
        assertEquals("Neon Dominos",game.title)
        assertTrue(game.id in singlePlayerOnlyGames)
        assertTrue("hockey" in singlePlayerOnlyGames)
        assertFalse("panda" in singlePlayerOnlyGames)
        assertEquals(game.title,WebGameActivity.BUILTINS[game.id])
        context.assets.open("catalog/${game.artwork}.webp").use{assertNotNull(android.graphics.BitmapFactory.decodeStream(it))}
        assertThrows(IllegalStateException::class.java){FriendGames.token(context,game.id)}
        GameSessionStatus.open(context).save(null)
        compose.activity.setContent{OnKlavTheme{GameStartDialog(platformGames.first{it.id=="dominos"},false,{WebGameActivity.launch(context,"dominos",fresh=true)},{},{})}}
        compose.waitUntil(10000){runCatching{compose.onNodeWithText("Single Player Only").assertExists()}.isSuccess}
        compose.onNodeWithText("Start").assertExists()
        compose.onNodeWithText("Play a Friend").assertDoesNotExist()
        compose.onNodeWithText("Start").performSemanticsAction(SemanticsActions.OnClick){it()}
        compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(context).peekNextStartedActivity()!=null}
        val launched=org.robolectric.Shadows.shadowOf(context).nextStartedActivity
        assertEquals("dominos",launched.getStringExtra("game"))
        assertTrue(launched.getBooleanExtra("start_menu",false))
        assertNull(GameSessionStatus.open(context).current())
    }
    @Test fun savedPandaDoesNotRedirectOrBlockDominos() {
        val context=compose.activity
        GameSessionStatus.open(context).save("panda")
        WebGameActivity.launch(context,"dominos",fresh=true)
        assertEquals("dominos",org.robolectric.Shadows.shadowOf(context).nextStartedActivity.getStringExtra("game"))
        assertEquals("panda",GameSessionStatus.open(context).current())
    }
    @Test fun reopeningGamesAlwaysShowsListWithoutAnyEntryDialog() {
        val context=compose.activity
        GameSessionStatus.open(context).save("panda")
        repeat(2) {
            compose.activity.setContent{OnKlavTheme{GamesCatalog {}}}
            compose.onNodeWithTag("games-catalog").assertExists()
            compose.onNodeWithText("Start").assertDoesNotExist()
            compose.onNodeWithText("Resume").assertDoesNotExist()
            compose.onNodeWithText("Single Player Only").assertDoesNotExist()
        }
    }
}
