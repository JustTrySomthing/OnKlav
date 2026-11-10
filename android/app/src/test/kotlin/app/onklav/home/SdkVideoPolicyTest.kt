package app.onklav.home
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.time.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class SdkVideoPolicyTest {
 @Test fun providerNavigationAndAdDomains() {
  assertEquals("AbC_def-123",SdkVideoPolicy.video("https://youtu.be/AbC_def-123"))
  assertEquals("AbC_def-123",SdkVideoPolicy.video("https://m.youtube.com/shorts/AbC_def-123"))
  for(u in listOf("http://youtube.com/","https://youtube.com.evil.test/","https://a@youtube.com/","https://youtube.com:8443/")) assertFalse(u,SdkVideoPolicy.page(u))
  assertTrue(SdkVideoPolicy.account("https://accounts.google.com/signin"));assertTrue(SdkVideoPolicy.account("https://m.youtube.com/account_settings"))
  assertTrue(SdkVideoPolicy.blocked("https://ads.doubleclick.net/x"));assertFalse(SdkVideoPolicy.blocked("https://doubleclick.net.evil.test/x"))
  assertTrue(SdkVideoPolicy.sameDocument("https://youtu.be/AbC_def-123","https://www.youtube.com/watch?v=AbC_def-123"))
  assertFalse(SdkVideoPolicy.sameDocument("https://youtu.be/AbC_def-123","https://youtu.be/AbC_def-124"))
 }
 @Test fun thumbnailInputIsBoundedAndChecksPermissionWhileReading() {
  assertEquals("https://i.ytimg.com/vi/AbC_def-123/mqdefault.jpg",SdkThumbnails.url("AbC_def-123"))
  assertTrue(runCatching { SdkThumbnails.url("../../other") }.isFailure)
  var calls=0;assertEquals(8192,SdkThumbnails.read(ByteArrayInputStream(ByteArray(8192))) { calls++ }.size);assertTrue(calls>=3)
  assertTrue(runCatching { SdkThumbnails.read(ByteArrayInputStream(ByteArray(98305))) {} }.isFailure)
  assertTrue(runCatching { SdkThumbnails.read(ByteArrayInputStream(ByteArray(10))) { error("revoked") } }.isFailure)
  assertNull(SdkThumbnails.decode(ByteArray(98305)))
 }
 @Test fun historySectionsUseLocalCalendarDays() {
  val zone=ZoneId.of("America/New_York");val today=LocalDate.of(2026,10,22)
  fun timestamp(day:LocalDate)=day.atStartOfDay(zone).toInstant().toEpochMilli()
  assertEquals("Today",videoSection(timestamp(today),today,zone));assertEquals("Yesterday",videoSection(timestamp(today.minusDays(1)),today,zone))
  assertEquals("Last 7 Days",videoSection(timestamp(today.minusDays(6)),today,zone));assertEquals("Earlier This Month",videoSection(timestamp(today.minusDays(8)),today,zone))
 }
}
