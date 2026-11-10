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
class SdkBootstrapTest {
    @get:org.junit.Rule val emptyKeyStore=EmptyKeyStoreRule()
    @Test fun bundledAudioAppsAreReadyWithoutSettingsAndLaterRevocationsRemainEffective()=runBlocking {
        val c=ApplicationProvider.getApplicationContext<Application>();AppSdk.bootstrap(c)
        for(id in listOf("app.onklav.radio","app.onklav.podcasts")){
            val row=AppSdk.list(c).single{it.child("manifest").optString("id")==id}
            fun values(a:org.json.JSONArray)=(0 until a.length()).map{a.getString(it)}.toSet()
            assertEquals(values(row.child("manifest").getJSONArray("capabilities")),values(row.getJSONArray("grants")))
        }
        AppSdk.call(c,JSONObject().put("action","permissions").put("id","app.onklav.radio").put("grants",org.json.JSONArray().put("storage.private")))
        AppSdk.bootstrap(c)
        assertEquals(1,AppSdk.list(c).single{it.child("manifest").optString("id")=="app.onklav.radio"}.getJSONArray("grants").length())
    }
    @Test fun warmSetupPreservesRemovalAndLivePermissionRevocation() = runBlocking {
        val c=ApplicationProvider.getApplicationContext<Application>()
        AppSdk.bootstrap(c)
        AppSdk.bootstrap(c)
        AppSdk.call(c,JSONObject().put("action","remove").put("id","app.onklav.social").put("delete_data",true))
        AppSdk.bootstrap(c)
        assertFalse(AppSdk.list(c).single{it.child("manifest").getString("id")=="app.onklav.social"}.getBoolean("installed"))
        AppSdk.call(c,JSONObject().put("action","permissions").put("id","app.onklav.notes").put("grants",org.json.JSONArray()))
        AppSdk.bootstrap(c)
        val model=SdkAppModel(c)
        model.open("app.onklav.notes")
        assertNull(model.error)
        assertTrue("Cached setup must never bypass current storage permission",
            model.frame.child("state").optString("status").startsWith("Private document access is off"))
    }

    @Test fun setupRecoversWhenRegistryIsMissingWithinSameProcess() = runBlocking {
        val c=ApplicationProvider.getApplicationContext<Application>()
        AppSdk.bootstrap(c)
        assertTrue(File(c.filesDir,"app-sdk/registry.json").delete())
        AppSdk.bootstrap(c)
        assertEquals(11,AppSdk.list(c).count{it.getBoolean("installed")})
        assertTrue(AppSdk.list(c).any{it.child("manifest").optString("id")=="app.onklav.files"&&it.getBoolean("installed")})
        assertTrue(File(c.filesDir,"app-sdk/registry.json").isFile)
    }
    @Test fun apkUpgradeUpdatesExistingBundledPackageAndKeepsPermissionsAndData()=runBlocking {
        val c=ApplicationProvider.getApplicationContext<Application>()
        val old=File(System.getProperty("onklav.project.root"),"docs/fixtures/walkie-v8.onklav-a")
        old.inputStream().use{AppSdk.stage(c,it)}
        val review=AppSdk.inspect(c);AppSdk.install(c,review,setOf("storage.private"))
        val before=AppSdk.list(c).single{it.child("manifest").optString("id")=="app.onklav.walkie"}
        assertEquals(8,before.child("manifest").getInt("version"))
        val prefs=AppSdk.call(c,JSONObject().put("action","walkie_preferences").put("args",JSONObject().put("auto_play",false).put("auto_send",false).put("beeps",false)))
        AppSdk.bootstrap(c)
        val after=AppSdk.list(c).single{it.child("manifest").optString("id")=="app.onklav.walkie"}
        assertEquals(AppSdk.includedVersion(c,"app.onklav.walkie"),after.child("manifest").getLong("version"))
        assertEquals(after.child("manifest").getJSONArray("capabilities").toString(),after.getJSONArray("grants").toString())
        assertEquals(before.child("manifest").getString("publisher"),after.child("manifest").getString("publisher"))
        assertEquals(prefs.toString(),AppSdk.call(c,JSONObject().put("action","walkie_preferences")).toString())
    }

}
