package app.onklav.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],qualifiers="w432dp-h936dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsServicesTest {
    @Test fun temporaryCleanupKeepsRecentExportsSymlinksAndAllOtherPrivateData() {
        val c=ApplicationProvider.getApplicationContext<Context>()
        val dir=File(c.cacheDir,"exports").apply{mkdirs()}
        val old=File(dir,"old.txt").apply{writeText("old");setLastModified(System.currentTimeMillis()-3*86400000L)}
        val recent=File(dir,"recent.txt").apply{writeText("new")}
        val private=File(c.filesDir,"keep.txt").apply{writeText("private")}
        val voice=File(c.cacheDir,"sdk-voice-protected.amr").apply{writeText("voice");setLastModified(old.lastModified())}
        val link=File(dir,"link.txt");Files.createSymbolicLink(link.toPath(),private.toPath())
        try {
            assertTrue(SettingsStorage.clear(c)>=3);assertFalse(old.exists());assertTrue(recent.exists());assertTrue(private.exists());assertTrue(voice.exists());assertTrue(link.exists())
        }finally{link.delete();recent.delete();private.delete();voice.delete()}
    }
    @Test fun temporaryCleanupWorksThroughAnAndroidStyleCacheDirectoryAlias() {
        val c=ApplicationProvider.getApplicationContext<Context>()
        val target=File(c.cacheDir,"cache-actual").apply{mkdirs()}
        val alias=File(c.cacheDir,"cache-alias")
        Files.createSymbolicLink(alias.toPath(),target.toPath())
        val wrapped=object:android.content.ContextWrapper(c){override fun getCacheDir()=alias}
        val exports=File(target,"exports").apply{mkdirs()}
        val old=File(exports,"old-copy.txt").apply{writeText("old");setLastModified(System.currentTimeMillis()-3*86400000L)}
        try {assertEquals(3L,SettingsStorage.clear(wrapped));assertFalse(old.exists())}
        finally{alias.delete();target.deleteRecursively()}
    }
    @Test fun cleanupNeverFollowsARedirectedExportsDirectory() {
        val c=ApplicationProvider.getApplicationContext<Context>()
        val private=File(c.filesDir,"retained-private").apply{mkdirs()}
        val saved=File(private,"keep.txt").apply{writeText("keep");setLastModified(System.currentTimeMillis()-3*86400000L)}
        val exports=File(c.cacheDir,"exports");if(exports.exists())assertTrue(exports.deleteRecursively())
        Files.createSymbolicLink(exports.toPath(),private.toPath())
        try{assertTrue(SettingsStorage.eligible(c).isEmpty());assertEquals(0L,SettingsStorage.clear(c));assertEquals("keep",saved.readText())}
        finally{exports.delete();private.deleteRecursively()}
    }
    @Test fun metadataBackupRoundTripIsValidatedAndNeverRestoresCapabilitiesOrSends() {
        val c=ApplicationProvider.getApplicationContext<Context>();val chat=ChatStore(c)
        chat.requestSync(JSONObject().put("action","blocked").put("address","5550101234").put("blocked",true))
        chat.requestSync(JSONObject().put("action","message").put("key","sms:81").put("reminder_text","private excerpt").put("remind_at",999999))
        val backup=SettingsBackup.export(c)
        assertFalse(backup.toString().contains("private excerpt"))
        assertTrue(SettingsBackup.inspect(c,backup).getBoolean("same_device"))
        assertFalse(backup.getJSONObject("metadata").has("outgoing"))
        chat.requestSync(JSONObject().put("action","blocked").put("address","5550101234").put("blocked",false))
        SettingsBackup.restore(c,backup)
        assertTrue(chat.requestSync(JSONObject().put("action","snapshot")).getJSONArray("blocked").toString().contains("5550101234"))
        backup.put("version",999)
        assertThrows(Exception::class.java){SettingsBackup.restore(c,backup)}
    }
    @Test fun spamWarningNeedsALinkAndRiskWordingAndHonorsDisabledPreference() {
        val c=ApplicationProvider.getApplicationContext<Context>()
        assertTrue(SpamPolicy.suspicious("Urgent: verify your account https://example.org"))
        assertFalse(SpamPolicy.suspicious("Urgent: call me"));assertFalse(SpamPolicy.suspicious("Our picnic photos https://example.org"))
        c.getSharedPreferences("spam_screen",0).edit().putBoolean("enabled",false).commit()
        assertFalse(SpamPolicy.assess(c,"5550109999","Urgent: verify your account https://example.org"))
        c.getSharedPreferences("spam_screen",0).edit().clear().commit()
    }
}
