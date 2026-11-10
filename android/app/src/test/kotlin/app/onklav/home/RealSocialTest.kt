package app.onklav.home

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
class RealSocialTest {
    @Test fun packetRoundTripsUnicodeAndRejectsInvalidIdentifiers() {
        val packet=JSONObject().put("kind","post").put("token","a".repeat(32))
            .put("id","b".repeat(32)).put("text","Hello 🌱")
        assertEquals("Hello 🌱",RealSocial.decode(RealSocial.wire(packet))?.getString("text"))
        assertNull(RealSocial.decode(RealSocial.wire(packet.put("id","../escape"))))
        assertNull(RealSocial.decode("A regular text"))
        assertFalse(RealSocial.safeLink("https://user@host.example/"))
        assertFalse(RealSocial.safeLink("file:///private"))
    }

    @Test fun convertedCarrierDelimiterPreservesFriendRequestsAndPosts() {
        val c=carrierContext();val peer="+15550100003";val token="a".repeat(32)
        fun packet(kind:String,id:String)=JSONObject().put("kind",kind).put("token",token)
            .put("id",id.repeat(32)).put("at",System.currentTimeMillis())
        val invitation=RealSocial.wire(packet("invite","b")).replace("!OKS1|","!OKS1@")
        assertTrue(RealSocial.receive(c,peer,1,invitation))
        val key=phoneKey(peer)
        assertEquals("request",RealSocial.snapshot(c).getJSONObject("friends").getJSONObject(key).getString("status"))
        RealSocial.accept(c,key)
        val post=packet("post","c").put("text","A carrier-delivered post")
        assertTrue(RealSocial.receive(c,peer,1,RealSocial.wire(post).replace("!OKS1|","!OKS1:")))
        assertEquals(1,RealSocial.snapshot(c).getJSONObject("posts").length())
        val forged=packet("post","d").put("token","e".repeat(32)).put("text","Wrong token")
        RealSocial.receive(c,peer,1,RealSocial.wire(forged).replace("!OKS1|","!OKS1@"))
        assertEquals(1,RealSocial.snapshot(c).getJSONObject("posts").length())
    }

    @Test fun friendsGetOnlyThreeRecentPostsAndRemovedTokensCannotReopenRequests() {
        val c=carrierContext()
        repeat(5) { RealSocial.post(c,"Charles","Post $it","",null,"") }
        val peer="+15550100003"
        val number=phoneKey(peer)
        val token="a".repeat(32)
        val now=System.currentTimeMillis()
        fun update(kind:String,id:String)=JSONObject().put("kind",kind).put("id",id).put("token",token).put("at",now)
        val invitation=RealSocial.wire(update("invite","b".repeat(32)))
        assertTrue(RealSocial.receive(c,peer,1,invitation))
        assertEquals("request",RealSocial.snapshot(c).getJSONObject("friends").getJSONObject(number).getString("status"))
        RealSocial.accept(c,number)
        val outbox=RealSocial.snapshot(c).getJSONObject("jobs")
        assertEquals(4,outbox.length())
        assertEquals(3,outbox.keys().asSequence().count { RealSocial.decode(outbox.getJSONObject(it).getString("wire"))?.getString("kind")=="post" })
        assertTrue(number in FriendGames.verifiedPeers(c))
        val post=update("post","c".repeat(32)).put("text","Friend post")
        assertTrue(RealSocial.receive(c,peer,1,RealSocial.wire(post)))
        assertTrue(RealSocial.receive(c,peer,1,RealSocial.wire(post)))
        assertEquals(6,RealSocial.snapshot(c).getJSONObject("posts").length())
        RealSocial.remove(c,number,false)
        assertTrue(RealSocial.receive(c,peer,1,invitation))
        assertEquals(0,RealSocial.snapshot(c).getJSONObject("friends").length())
        assertEquals(5,RealSocial.snapshot(c).getJSONObject("posts").length())
        RealSocial.invite(c,peer,"Verified friend")
        fun invite()=RealSocial.snapshot(c).getJSONObject("friends").getJSONObject(number)
        assertEquals("fast",invite().getString("status"))
        assertEquals(0L,invite().getLong("at"))
        RealSocial.recover(c) // Earlier removal is ahead of the new invitation.
        assertEquals(0L,invite().getLong("at"))
        RealSocial.recover(c)
        assertTrue(invite().getLong("at")>0L)
    }

    @Test fun fullOutboxCanDrainWhenVerificationFallbackIsDue() {
        val c=carrierContext()
        val peer="+15550100003"
        FriendGames.recordVerified(c,peer)
        RealSocial.invite(c,peer,"Friend")
        val root=RealSocial.snapshot(c)
        val friends=root.getJSONObject("friends")
        friends.getJSONObject(phoneKey(peer)).put("at",System.currentTimeMillis()-70_000)
        val jobs=root.getJSONObject("jobs")
        repeat(383) { n ->
            val id=(n+1).toString(16).padStart(32,'0')
            jobs.put(id,JSONObject().put("id",id).put("peer",peer).put("sub",1)
                .put("wire",FriendGameWire.QUESTION).put("at",System.currentTimeMillis()-1_000))
        }
        java.io.File(c.noBackupFilesDir,"real-social/state.json").writeText(root.toString())
        assertNotNull(RealSocial.recover(c))
        val updated=RealSocial.snapshot(c)
        assertEquals("checking",updated.getJSONObject("friends").getJSONObject(phoneKey(peer)).getString("status"))
        assertEquals(384,updated.getJSONObject("jobs").length())
    }

    @Test fun verificationProofSurvivesFullOutboxAndCrossedInvitesCancelSupersededJobs() {
        val c=carrierContext()
        val peer="+15550100003"
        val number=phoneKey(peer)
        RealSocial.invite(c,peer,"Friend")
        val root=RealSocial.snapshot(c)
        val friend=root.getJSONObject("friends").getJSONObject(number)
        val superseded="f".repeat(32)
        friend.put("token",superseded).put("at",System.currentTimeMillis())
        val jobs=root.getJSONObject("jobs")
        repeat(383) { n ->
            val id=(n+1).toString(16).padStart(32,'0')
            jobs.put(id,JSONObject().put("id",id).put("peer",peer).put("sub",1)
                .put("wire",FriendGameWire.QUESTION).put("at",System.currentTimeMillis()))
        }
        java.io.File(c.noBackupFilesDir,"real-social/state.json").writeText(root.toString())
        RealSocial.observeVerification(c,peer,"!OKA1|${"b".repeat(32)}|t1")
        fun snapshot()=RealSocial.snapshot(c)
        fun status()=snapshot().getJSONObject("friends").getJSONObject(number).getString("status")
        assertEquals("verified_waiting",status())
        assertTrue(number in FriendGames.verifiedPeers(c))
        RealSocial.recover(c)
        assertEquals("invited",status())
        val incoming=RealSocial.wire(JSONObject().put("kind","invite").put("id","c".repeat(32))
            .put("token","a".repeat(32)).put("at",System.currentTimeMillis()))
        assertTrue(RealSocial.receive(c,peer,1,incoming))
        assertEquals("request",status())
        assertEquals(0,snapshot().getJSONObject("jobs").length())
        assertTrue(snapshot().getJSONObject("retired").has(superseded))
        RealSocial.post(c,"Me","Earlier post","",null,"")
        val busy = snapshot()
        val waiting = busy.getJSONObject("jobs")
        repeat(384) { n ->
            val id=(n+1).toString(16).padStart(32,'0')
            waiting.put(id,JSONObject().put("id",id).put("peer",peer).put("sub",1)
                .put("wire",FriendGameWire.QUESTION).put("at",System.currentTimeMillis()-1000))
        }
        java.io.File(c.noBackupFilesDir,"real-social/state.json").writeText(busy.toString())
        RealSocial.accept(c,number)
        assertEquals("friend",status())
        assertTrue(snapshot().getJSONObject("friends").getJSONObject(number).getBoolean("history_waiting"))
        RealSocial.recover(c)
        assertTrue(snapshot().getJSONObject("friends").getJSONObject(number).getBoolean("history_waiting"))
        RealSocial.recover(c)
        assertFalse(snapshot().getJSONObject("friends").getJSONObject(number).optBoolean("history_waiting"))
        val drained=snapshot().getJSONObject("jobs")
        assertTrue(drained.keys().asSequence().any {
            RealSocial.decode(drained.getJSONObject(it).getString("wire"))?.optString("kind")=="post"
        })
    }

    @Test fun oversizedLocalPostIsRejectedAndLegacyPostCannotBlockAcceptance() {
        val c=carrierContext()
        val text="\\".repeat(650)
        val link="https://example.org/"+"x".repeat(325)
        assertThrows(IllegalArgumentException::class.java) {
            RealSocial.post(c,"Me",text,link,null,"")
        }
        val root=RealSocial.snapshot(c)
        val posts=root.getJSONObject("posts")
        assertEquals(0,posts.length())
        val legacy="d".repeat(32)
        posts.put(legacy,JSONObject().put("id",legacy).put("at",System.currentTimeMillis())
            .put("name","Me").put("text",text).put("link",link))
        java.io.File(c.noBackupFilesDir,"real-social/state.json").writeText(root.toString())
        val peer="+15550100003"
        val invitation=RealSocial.wire(JSONObject().put("kind","invite").put("id","a".repeat(32))
            .put("token","b".repeat(32)).put("at",System.currentTimeMillis()))
        assertTrue(RealSocial.receive(c,peer,1,invitation))
        RealSocial.accept(c,phoneKey(peer))
        val accepted=RealSocial.snapshot(c)
        assertEquals("friend",accepted.getJSONObject("friends").getJSONObject(phoneKey(peer)).getString("status"))
        assertEquals(1,accepted.getJSONObject("jobs").length())
        assertTrue(accepted.getJSONObject("posts").has(legacy))
    }

    private fun carrierContext(): android.app.Application {
        val c=ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking { AppSdk.bootstrap(c) }
        org.robolectric.Shadows.shadowOf(c).grantPermissions(android.Manifest.permission.SEND_SMS,android.Manifest.permission.RECEIVE_SMS)
        org.robolectric.Shadows.shadowOf(c.getSystemService(android.app.role.RoleManager::class.java)).addHeldRole(android.app.role.RoleManager.ROLE_SMS)
        org.robolectric.shadows.ShadowSubscriptionManager.setDefaultSmsSubscriptionId(1)
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(
            android.provider.BlockedNumberContract.AUTHORITY, FriendGamesTest.BlockPolicy())
        return c
    }

    @Test fun ownPostsPersistWithoutRecipientsAndMuteCanToggle() {
        val c=ApplicationProvider.getApplicationContext<android.app.Application>()
        runBlocking { AppSdk.bootstrap(c) }
        assertTrue(RealSocial.installed(c))
        RealSocial.post(c,"Charles","A post","https://example.org",null,"")
        RealSocial.mute(c)
        val root=RealSocial.snapshot(c)
        val posts=root.getJSONObject("posts")
        assertEquals(1,posts.length())
        assertEquals("A post",posts.getJSONObject(posts.keys().next()).getString("text"))
        assertEquals(0,root.optJSONObject("jobs")?.length() ?: 0)
        assertTrue(root.getBoolean("muted"))
        RealSocial.mute(c)
        assertFalse(RealSocial.snapshot(c).getBoolean("muted"))
        RealSocial.clear(c)
        assertEquals(0,RealSocial.snapshot(c).getJSONObject("posts").length())
    }
}
