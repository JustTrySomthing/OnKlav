package app.onklav.home

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class GroupProtocolHostTest {
    @Test fun productionJniProjectsPostAndReaction() = runBlocking {
        val protocol = GroupProtocol(ApplicationProvider.getApplicationContext())
        val post = protocol.post("A real group post 🐈")
        val p = Message(1, post, 1, false, sender = "+15555550101")
        val first = protocol.feed(listOf(p))
        assertEquals("A real group post 🐈", first.child("texts").getString(p.key))
        val target = first.child("keys").getString(p.key)
        val reaction = protocol.reaction(target, "❤️")
        val r = Message(2, reaction, 2, false, sender = "+15555550102")
        val feed = protocol.feed(listOf(p, r))
        assertEquals(1, feed.getJSONArray("visible").length())
        assertEquals("❤️", feed.child("reactions").child(target).getString("+15555550102"))
        val orphan = protocol.feed(listOf(r))
        assertEquals(1, orphan.getJSONArray("visible").length())
        assertTrue(orphan.child("texts").getString(r.key).contains("reacted to a group post"))
    }
    @Test fun activityPickerOffersOneOtherPeerForCanonicalDuplicatesAndExcludesSelf(){
        assertEquals(listOf("+15555550101"),activityRecipients(listOf("+15555550101","(555) 555-0101","+15555550102",""),"+1 (555) 555-0102"))
    }
    @Test fun initialsMatchReference() {
        assertEquals("FF", groupInitials("Family Friends"))
        assertEquals("•", groupInitials("🌱"))
        assertEquals("AB", groupInitials("..Alice !Bob Carol"))
    }
}
