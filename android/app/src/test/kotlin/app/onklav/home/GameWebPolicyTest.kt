package app.onklav.home

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class GameWebPolicyTest {
    private val origin="https://g-one.onklav.invalid"
    @Test fun onlyExactExitMarkerClosesTheCurrentGame(){
        assertTrue(GameWebPolicy.exitMessage("ONKLAV_GAME_EXIT"))
        for(value in listOf("", " ONKLAV_GAME_EXIT", "ONKLAV_GAME_EXIT ", "ONKLAV_GAME_EXIT:other", "onklav_game_exit"))assertFalse(GameWebPolicy.exitMessage(value))
    }
    @Test fun onlyCurrentOriginGetResourcesAreServed(){
        assertEquals("web/index.pck",GameWebPolicy.path(origin,Uri.parse("$origin/web/index.pck"),"GET"))
        for(url in listOf("https://g-two.onklav.invalid/web/index.pck","https://example.com/web/index.pck","http://g-one.onklav.invalid/web/index.pck","file:///data/data/private","content://host/private","https://g-one.onklav.invalid:443/web/index.pck","https://user@g-one.onklav.invalid/web/index.pck"))assertNull(url,GameWebPolicy.path(origin,Uri.parse(url),"GET"))
        assertNull(GameWebPolicy.path(origin,Uri.parse("$origin/web/index.pck"),"POST"))
    }
    @Test fun encodedTraversalAndNonGamePathsNeverReachHostStorage(){
        for(path in listOf("/web/../secret","/web/%2e%2e/secret","/web/%2Fsecret","/godot-web/engine.json","/web/a//b","/web/a\\b","/web/./index.html"))assertNull(path,GameWebPolicy.path(origin,Uri.parse(origin+path),"GET"))
    }
    @Test fun samePublisherGameOriginSurvivesUpdatesAndOtherGamesAreSeparate(){
        fun game(id:String,version:Int,publisher:String)=GamePackages.Game(org.json.JSONObject().put("id",id).put("version_code",version),publisher,"1".repeat(64))
        assertEquals(game("app.example.a",1,"2".repeat(64)).origin,game("app.example.a",2,"2".repeat(64)).origin)
        assertNotEquals(game("app.example.a",1,"2".repeat(64)).origin,game("app.example.b",1,"2".repeat(64)).origin)
        assertNotEquals(game("app.example.a",1,"2".repeat(64)).origin,game("app.example.a",1,"3".repeat(64)).origin)
    }
}
