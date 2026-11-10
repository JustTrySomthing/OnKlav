package app.onklav.home

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Pure bounded date arithmetic using Android's timezone database. No calendar permissions. */
internal object SdkRecurrence {
 private val format=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)
 fun expand(a:JSONObject):JSONObject {
  val allowed=setOf("start","timezone","frequency","interval","count","duration_seconds","duration_days","window_start_ms","window_end_ms","limit","gap","overlap")
  require(a.keys().asSequence().all{it in allowed}){"Unsupported recurrence argument"}
  fun whole(key:String,min:Long,max:Long):Long{val v=a.get(key);require(v is Number&&v.toString().matches(Regex("[0-9]+")));return v.toString().toLong().also{require(it in min..max){"Recurrence $key is out of range"}}}
  val start=LocalDateTime.parse(a.getString("start"),format);require(start.year in 1970..9999)
  val zone=ZoneId.of(a.getString("timezone"));val frequency=a.getString("frequency");require(frequency in setOf("daily","weekly","monthly","yearly"))
  val interval=whole("interval",1,365);val count=whole("count",1,1000).toInt();val limit=whole("limit",1,200).toInt()
  val from=whole("window_start_ms",0,253402300799999);val until=whole("window_end_ms",1,253402300799999);require(until>from)
  val days=if(a.has("duration_days")){require(!a.has("duration_seconds")&&start.toLocalTime()==java.time.LocalTime.MIDNIGHT);whole("duration_days",1,366)}else null
  val seconds=if(days==null)whole("duration_seconds",1,604800)else 0
  val gap=a.getString("gap");val overlap=a.getString("overlap");require(gap in setOf("skip","forward")&&overlap in setOf("earlier","later"))
  fun resolve(local:LocalDateTime):ZonedDateTime?{
   val offsets=zone.rules.getValidOffsets(local)
   return when(offsets.size){0->if(gap=="skip")null else local.atZone(zone);1->ZonedDateTime.ofStrict(local,offsets[0],zone);else->ZonedDateTime.ofStrict(local,if(overlap=="earlier")offsets.first()else offsets.last(),zone)}
  }
  val rows=JSONArray();var emitted=0;var truncated=false;var previous:Long?=null;var finished=false
  for(n in 0 until 10000){
   if(emitted>=count){finished=true;break}
   val step=n.toLong()*interval
   val local=when(frequency){
    "daily"->start.plusDays(step);"weekly"->start.plusWeeks(step)
    else->{val month=YearMonth.from(start).plusMonths(step*if(frequency=="yearly")12 else 1);if(month.year>9999){truncated=true;finished=true;break};if(start.dayOfMonth>month.lengthOfMonth())continue;month.atDay(start.dayOfMonth).atTime(start.toLocalTime())}
   }
   if(local.year>9999){truncated=true;finished=true;break}
   val begin=if(days==null)resolve(local)?:continue else local.toLocalDate().atStartOfDay(zone).takeIf{it.toLocalDate()==local.toLocalDate()}?:continue;val ms=begin.toInstant().toEpochMilli()
   // A whole civil date can disappear after a dateline change. Never emit a duplicate instant.
   if(previous?.let{ms<=it}==true)continue
   val end=if(days==null)begin.plusSeconds(seconds)else local.toLocalDate().plusDays(days).atStartOfDay(zone)
   val endMs=end.toInstant().toEpochMilli();if(endMs<=ms)continue
   previous=ms;emitted++
   if(ms>=until){finished=true;break}
   if(ms<from)continue
   if(end.year>9999||endMs>253402300799999){truncated=true;finished=true;break}
   if(rows.length()>=limit){truncated=true;finished=true;break}
   rows.put(JSONObject().put("start_ms",ms).put("end_ms",endMs).put("local_start",begin.toLocalDateTime().format(format)).put("local_end",end.toLocalDateTime().format(format)).put("offset_seconds",begin.offset.totalSeconds).put("end_offset_seconds",end.offset.totalSeconds))
  }
  if(!finished&&emitted<count)truncated=true
  return JSONObject().put("timezone",a.getString("timezone")).put("occurrences",rows).put("truncated",truncated)
 }
}
