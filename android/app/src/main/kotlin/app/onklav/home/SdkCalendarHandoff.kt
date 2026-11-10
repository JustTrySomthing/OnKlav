package app.onklav.home

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Calendar app owns calendar selection, review and saving. No provider-wide access. */
internal object SdkCalendarHandoff {
 private val format=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)
 fun resolve(a:JSONObject):JSONObject {
  val zone=ZoneId.of(a.getString("timezone"));val overlap=a.optString("overlap","earlier");val gap=a.optString("gap","reject")
  require(overlap in setOf("earlier","later")&&gap in setOf("reject","forward"))
  fun instant(key:String):Long{
   val local=LocalDateTime.parse(a.getString(key),format);require(local.year in 1970..9999)
   val offsets=zone.rules.getValidOffsets(local)
   require(offsets.isNotEmpty()||gap=="forward"){"This local time does not exist. Choose another time or move forward through the clock change."}
   return (if(offsets.isEmpty())local.atZone(zone)else java.time.ZonedDateTime.ofStrict(local,if(overlap=="later")offsets.last()else offsets.first(),zone)).toInstant().toEpochMilli()
  }
  val start=instant("start");val end=instant("end");require(end>start)
  val utc=DateTimeFormatter.ofPattern("uuuuMMdd\'T\'HHmmss\'Z\'").withZone(java.time.ZoneOffset.UTC)
  return JSONObject().put("start_ms",start).put("end_ms",end).put("timezone",zone.id).put("start",utc.format(java.time.Instant.ofEpochMilli(start))).put("end",utc.format(java.time.Instant.ofEpochMilli(end)))
 }
 fun localize(a:JSONObject):JSONObject{
  val zone=ZoneId.of(a.getString("timezone"));val utc=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC)
  val local=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(zone)
  fun convert(k:String)=local.format(java.time.Instant.from(utc.parse(a.getString(k))))
  return JSONObject().put("start",convert("start")).put("end",convert("end")).put("timezone",zone.id)
 }
 fun intent(a:JSONObject):Intent{
  val start=a.getLong("start_ms");val end=a.getLong("end_ms");require(start>=0&&end>start&&end<=253402300799999L)
  val title=a.getString("title");val details=a.optString("description");require(title.isNotBlank()&&title.length<=1024&&details.length<=8192)
  val zone=ZoneId.of(a.getString("timezone"));val allDay=a.optBoolean("all_day")
  if(allDay){require(start%86400000L==0L&&end%86400000L==0L&&zone.id=="UTC"){"All-day events use UTC midnight and an exclusive end date."}}
  return Intent(Intent.ACTION_INSERT,CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,start).putExtra(CalendarContract.EXTRA_EVENT_END_TIME,end).putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY,allDay).putExtra(CalendarContract.Events.TITLE,title).putExtra(CalendarContract.Events.DESCRIPTION,details).putExtra(CalendarContract.Events.EVENT_TIMEZONE,zone.id)
 }
 fun insert(c:Context,a:JSONObject,authorize:()->Unit):JSONObject{
  val i=intent(a);check(i.resolveActivity(c.packageManager)!=null){"No Android calendar app is available. Export an ICS file instead."};authorize();c.startActivity(i)
  return JSONObject().put("handed_off",true).put("saved",false)
 }
}
