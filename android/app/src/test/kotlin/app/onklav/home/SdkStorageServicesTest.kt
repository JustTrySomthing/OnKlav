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
class SdkStorageServicesTest {
    @Test fun hostQuotaControlsDoNotGrantGuestPermissions()=runBlocking{
        val c=ApplicationProvider.getApplicationContext<Application>();AppSdk.bootstrap(c)
        val id="app.onklav.social"
        val before=AppSdk.list(c).single{it.child("manifest").getString("id")==id}.getJSONArray("grants").toString()
        val result=AppSdk.call(c,JSONObject().put("action","storage_admin").put("id",id).put("args",JSONObject().put("operation","limits").put("documents",64L*1048576).put("files",256L*1048576).put("cache",0)))as JSONObject
        assertEquals(256L*1048576,result.getJSONObject("limits").getLong("files"))
        assertEquals(0,result.getLong("files_used"))
        assertEquals(before,AppSdk.list(c).single{it.child("manifest").getString("id")==id}.getJSONArray("grants").toString())
        val model=SdkAppModel(c);model.open(id);assertNull(model.error)
    }
    @Test fun cacheClearAndInvalidQuotaKeepExistingCollectionData()=runBlocking{
        val c=ApplicationProvider.getApplicationContext<Application>()
        java.io.File(System.getProperty("onklav.project.root"),"docs/fixtures/collections-wip.onklav-a").inputStream().use{AppSdk.stage(c,it)}
        AppSdk.install(c,AppSdk.inspect(c),setOf("storage.private"))
        val id="org.example.collectionsfixture";val model=SdkAppModel(c);model.open(id);assertNull(model.error)
        val file=java.io.File(c.filesDir,"app-sdk/data/$id/collection-items.json");val before=file.readBytes()
        AppSdk.call(c,JSONObject().put("action","storage_admin").put("id",id).put("args",JSONObject().put("operation","clear_cache")))
        assertArrayEquals(before,file.readBytes())
        try{
            AppSdk.call(c,JSONObject().put("action","storage_admin").put("id",id).put("args",JSONObject().put("operation","limits").put("documents",1).put("files",1).put("cache",0)))
            fail("Invalid quota was accepted")
        }catch(expected:IllegalStateException){}
        assertArrayEquals(before,file.readBytes())
    }
}
