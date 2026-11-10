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
class SdkHttpBridgeTest {
    @Test fun signedOriginsReachJniAuthorizationAndHostPacketTamperingCannotWidenThem()=runBlocking{
        val c=ApplicationProvider.getApplicationContext<Application>()
        File(System.getProperty("onklav.project.root"),"docs/fixtures/http-wip.onklav-a").inputStream().use{AppSdk.stage(c,it)}
        val review=AppSdk.inspect(c);assertEquals("https://api.example.com",review.child("manifest").getJSONObject("network").getJSONArray("origins").getString(0))
        AppSdk.install(c,review,setOf("network.http"))
        val model=SdkAppModel(c);model.open("org.example.httpfixture");assertNull(model.error)
        val pending=requireNotNull(model.pending);pending.getJSONObject("args").put("url","https://evil.example.com/")
        val native=model.authorize(pending)
        assertEquals("https://api.example.com/items",native.getJSONObject("args").getString("url"))
        assertEquals("https://api.example.com",native.getJSONArray("origins").getString(0))
        AppSdk.call(c,JSONObject().put("action","permissions").put("id","org.example.httpfixture").put("grants",org.json.JSONArray()))
        try{model.authorize(pending);fail("Revoked HTTP token remained valid")}catch(expected:IllegalStateException){}
    }
    @Test fun declaringHttpAccessDoesNotGrantIt()=runBlocking{
        val c=ApplicationProvider.getApplicationContext<Application>()
        File(System.getProperty("onklav.project.root"),"docs/fixtures/http-wip.onklav-a").inputStream().use{AppSdk.stage(c,it)}
        AppSdk.install(c,AppSdk.inspect(c),emptySet())
        val model=SdkAppModel(c);model.open("org.example.httpfixture")
        assertNull(model.error);assertNull(model.pending)
    }
}
