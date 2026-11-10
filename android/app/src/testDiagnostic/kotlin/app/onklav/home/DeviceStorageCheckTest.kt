package app.onklav.home

import android.app.NotificationManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Lifecycle coroutines need a fresh Robolectric worker after its main looper is reset.
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class DeviceStorageCheckTest{
 @get:Rule val compose=createAndroidComposeRule<DeviceChecksActivity>()
 private val checks get()=ViewModelProvider(compose.activity)[DeviceChecks::class.java]
 @Test fun unavailableKeystoreIsReportedAsFailureWithoutLeakingTestValue(){
  compose.onNodeWithText("Start checks").performClick()
  try{compose.waitUntil(15000){checks.ready&&!checks.busy}}catch(e:Throwable){throw AssertionError(checks.report(),e)}
  compose.runOnIdle{checks.secureStorage()}
  compose.waitUntil(10000){!checks.busy}
  assertTrue(checks.results.getValue("Secure storage").startsWith("Failed"))
  assertFalse(checks.report().contains("private fixture"))
  assertFalse(compose.activity.noBackupFilesDir.walk().filter{it.isFile}.any{String(it.readBytes(),Charsets.ISO_8859_1).contains("private fixture")})
 }
}
