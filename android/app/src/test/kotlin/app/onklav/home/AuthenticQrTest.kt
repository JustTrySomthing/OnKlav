package app.onklav.home
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import android.content.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[29]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuthenticQrTest{
 @Test fun standardSetupQrRoundTripsOffline(){val a=AuthenticAccount(issuer="Example service",account="hello@example.com",secret=AuthenticOtp.encode(ByteArray(64){it.toByte()}),algorithm="SHA512",digits=8,period=60);val value=AuthenticOtp.uri(a);val b=AuthenticQr.bitmap(value);try{assertEquals(value,AuthenticQr.image(b));val parsed=AuthenticOtp.parse(AuthenticQr.image(b));assertEquals(a.copy(id=parsed.id),parsed)}finally{b.recycle()}}
 @Test fun clipboardExpiresWithoutClearingSomeoneElsesCopiedText(){val c=ApplicationProvider.getApplicationContext<Application>();val manager=c.getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager;AuthenticClipboard.copy(c,"123456",2);assertEquals("123456",manager.primaryClip!!.getItemAt(0).text.toString());shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3));assertFalse(manager.hasPrimaryClip());AuthenticClipboard.copy(c,"654321",2);manager.setPrimaryClip(ClipData.newPlainText("Other","Keep me"));shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3));assertEquals("Keep me",manager.primaryClip!!.getItemAt(0).text.toString())}
}
