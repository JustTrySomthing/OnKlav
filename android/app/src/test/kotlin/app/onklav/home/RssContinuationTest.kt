package app.onklav.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class RssContinuationTest {
    @get:org.junit.Rule val emptyKeyStore=EmptyKeyStoreRule()
    private suspend fun freshRss(context:Application) {
        // Reset through production removal/install: JNI sessions can outlive a Robolectric Application.
        AppSdk.bootstrap(context)
        AppSdk.sync(context,JSONObject().put("action","remove").put("id","app.onklav.rss").put("delete_data",true))
        context.assets.open("sdk/RSS.onklav-a").use { AppSdk.stage(context,it) }
        AppSdk.install(context,AppSdk.inspect(context),setOf("storage.private","network.feeds"))
        context.getSharedPreferences("rss",0).edit().clear().commit()
    }
    @Test fun ownSourcesAreAdoptedOnceWithoutChangingPreviousPreferences() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        freshRss(context)
        val legacy=context.getSharedPreferences("rss",0)
        val previous="[\"https://example.org/feed\",\"https://example.org/feed\"]"
        legacy.edit().putString("feeds",previous).commit()
        val model=SdkAppModel(context);model.open("app.onklav.rss")
        val feeds=model.frame.child("state").child("meta").getJSONArray("feeds")
        assertEquals(1,feeds.length());assertEquals("https://example.org/feed",feeds.getJSONObject(0).getString("url"))
        assertEquals("https://example.org/feed",model.pending!!.child("args").getString("url"))
        assertEquals(previous,legacy.getString("feeds",null));assertTrue(legacy.getBoolean("sdk_feeds_adopted",false))
        legacy.edit().putString("feeds","[\"https://example.org/other\"]").commit()
        val again=SdkAppModel(context);again.open("app.onklav.rss")
        assertEquals("https://example.org/feed",again.frame.child("state").child("meta").getJSONArray("feeds").getJSONObject(0).getString("url"))
    }
    @Test fun anExistingSdkListWinsOverPreviousPreferences() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        freshRss(context)
        AppSdk.bootstrap(context)
        val publisher=AppSdk.list(context).first { it.child("manifest").optString("id")=="app.onklav.rss" }.child("manifest").getString("publisher")
        AppSdk.call(context,JSONObject().put("action","adopt_own_feeds").put("publisher",publisher).put("feeds","[]"))
        val legacy=context.getSharedPreferences("rss",0);legacy.edit().putString("feeds","[\"https://example.org/old\"]").commit()
        val model=SdkAppModel(context);model.open("app.onklav.rss")
        assertEquals(0,model.frame.child("state").child("meta").getJSONArray("feeds").length())
        assertNull(model.pending);assertTrue(legacy.getBoolean("sdk_feeds_adopted",false))
    }
    @Test fun malformedPreferencesRemainIntactAndEmptyListCanBeRetried() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        freshRss(context)
        val legacy=context.getSharedPreferences("rss",0);legacy.edit().putString("feeds","[false]").commit()
        val model=SdkAppModel(context)
        model.open("app.onklav.rss")
        assertTrue(model.error.orEmpty().contains("retained"));assertEquals(0,model.frame.length())
        assertEquals("[false]",legacy.getString("feeds",null));assertFalse(legacy.getBoolean("sdk_feeds_adopted",false))
        legacy.edit().putString("feeds","[]").commit();model.error=null;model.open("app.onklav.rss")
        assertNull(model.pending);assertEquals(0,model.frame.child("state").child("meta").getJSONArray("feeds").length())
    }
    @Test fun scrollPositionsAreSeparateBoundedAndRefreshRecentlyUsedSlots() {
        val memory=SdkScrollMemory();memory.save("articles",400);memory.save("article:one",17)
        assertEquals(400,memory.position("articles"));assertEquals(17,memory.position("article:one"))
        for(i in 0..29)memory.save("query:$i",i)
        memory.save("articles",450);memory.save("next",1)
        assertEquals(32,memory.size);assertEquals(450,memory.position("articles"));assertEquals(0,memory.position("article:one"))
        memory.save("negative",-20);assertEquals(0,memory.position("negative"))
    }
}
