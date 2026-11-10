package app.onklav.home

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.provider.BlockedNumberContract
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowSubscriptionManager

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29])
class WalkieCarrierConnectionTest {
    @Test fun countryPrefixedCarrierRepliesConnectToTenDigitSelectedRecipient() {
        val c=ApplicationProvider.getApplicationContext<Application>()
        runBlocking { AppSdk.bootstrap(c) }
        AppSdk.sync(c,JSONObject().put("action","permissions").put("id","app.onklav.walkie")
            .put("grants",JSONArray(listOf("storage.private","contacts.pick","communications.walkie"))))
        Shadows.shadowOf(c).grantPermissions(Manifest.permission.SEND_SMS,Manifest.permission.RECEIVE_SMS)
        Shadows.shadowOf(c.getSystemService(RoleManager::class.java)).addHeldRole(RoleManager.ROLE_SMS)
        ShadowSubscriptionManager.setDefaultSmsSubscriptionId(1)
        ShadowContentResolver.registerProviderInternal(BlockedNumberContract.AUTHORITY,FriendGamesTest.BlockPolicy())
        val model=SdkAppModel(c)
        runBlocking { model.open("app.onklav.walkie");model.chooseWalkieRecipient("5550101234") }
        fun command(op:String,peer:String="5550101234",sub:Int=1,wire:String?=null):JSONObject =
            SdkWalkieTransport.command(c,JSONObject().put("operation",op).put("peer",peer)
                .put("subscription",sub).apply { if(wire!=null)put("wire",wire) })
        val initial=command("begin")
        val id=initial.getString("id")
        command("next") // probe claimed; a reply may precede the platform SENT callback
        val proof="!OKW1|C|$id|0|wt1"
        assertThrows(Exception::class.java) { command("receive","+15550109999",wire=proof) }
        assertThrows(Exception::class.java) { command("receive","+15550101234",sub=2,wire=proof) }
        assertEquals("invite_queued",command("receive","+15550101234",wire=proof).getString("phase"))
        command("next")
        val accept="!OKW1|A|$id|0|wt1"
        assertEquals("connected",command("receive","+1 (555) 010-1234",wire=accept).getString("phase"))
        assertEquals("5550101234",SdkWalkieTransport.state(c).getString("peer"))
    }
}
