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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkCollectionsTest {
    private fun fixture()=File(System.getProperty("onklav.project.root"),"docs/fixtures/collections-wip.onklav-a")
    @Test fun signedGuestCommitsThroughTheAndroidJniBroker()=runBlocking{
        val c=ApplicationProvider.getApplicationContext<Application>()
        fixture().inputStream().use{AppSdk.stage(c,it)}
        AppSdk.install(c,AppSdk.inspect(c),setOf("storage.private"))
        val model=SdkAppModel(c);model.open("org.example.collectionsfixture")
        assertNull(model.error);assertNull(model.pending)
        val snapshot=JSONObject(File(c.filesDir,"app-sdk/data/org.example.collectionsfixture/collection-items.json").readText())
        assertEquals(1,snapshot.getLong("revision"));assertEquals("Test item",snapshot.getJSONObject("rows").getJSONObject("one").getString("title"))
    }
    @Test fun ungrantedStorageDoesNotCreateCollectionFiles()=runBlocking{
        val c=ApplicationProvider.getApplicationContext<Application>()
        fixture().inputStream().use{AppSdk.stage(c,it)}
        AppSdk.install(c,AppSdk.inspect(c),emptySet())
        val model=SdkAppModel(c);model.open("org.example.collectionsfixture")
        assertNull(model.error);assertFalse(File(c.filesDir,"app-sdk/data/org.example.collectionsfixture/collection-items.json").exists())
    }
}
