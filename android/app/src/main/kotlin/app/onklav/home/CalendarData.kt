package app.onklav.home

import android.content.Context
import android.util.AtomicFile
import org.json.*
import java.io.File
import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

internal data class CalendarReminder(val minutes:Int=15,val sound:Boolean=false){
 fun json()=JSONObject().put("minutes",minutes).put("sound",sound)
}
internal data class CalendarBook(val id:String="personal",val name:String="Personal",val color:Int=0,val visible:Boolean=true){
 fun json()=JSONObject().put("id",id).put("name",name).put("color",color).put("visible",visible)
}
internal data class CalendarEvent(
 val id:String=UUID.randomUUID().toString(),val title:String="",val start:LocalDateTime=LocalDate.now().atTime(9,0),val end:LocalDateTime=start.plusHours(1),
 val zone:String=ZoneId.systemDefault().id,val allDay:Boolean=false,val book:String="personal",val location:String="",val notes:String="",
 val repeat:String="none",val interval:Int=1,val weekdays:Int=1 shl(start.dayOfWeek.value-1),val until:LocalDate?=null,
 val excluded:Set<LocalDate> = emptySet(),val reminders:List<CalendarReminder> = listOf(CalendarReminder()),val updated:Long=System.currentTimeMillis(),val uid:String=id
){
 fun validate(){require(id.matches(Regex("[A-Za-z0-9_-]{1,100}")));require(title.isNotBlank()&&title.length<=200){"Add an event title"};require(notes.length<=20000&&location.length<=1000&&uid.length<=500);ZoneId.of(zone)
  require(start.year in 1900..2199&&end.year in 1900..2200&&end>start&&Duration.between(start,end)<=Duration.ofDays(366)){"End must be after start, within one year"}
  require(!allDay||(start.toLocalTime()==LocalTime.MIDNIGHT&&end.toLocalTime()==LocalTime.MIDNIGHT));require(repeat in listOf("none","daily","weekly","monthly","yearly")&&interval in 1..99&&weekdays in 1..127)
  require(until==null||(until>=start.toLocalDate()&&until.year<=2199)){ "Repeat end must follow the first event" };require(excluded.size<=10000&&reminders.size<=5&&reminders.all{it.minutes in 0..525600}&&reminders.distinctBy{it.minutes}.size==reminders.size){"Use up to five different reminders"}
 }
 fun json()=JSONObject().put("id",id).put("uid",uid).put("title",title).put("start",start.toString()).put("end",end.toString()).put("zone",zone).put("allDay",allDay).put("book",book).put("location",location).put("notes",notes).put("repeat",repeat).put("interval",interval).put("weekdays",weekdays).put("until",until?.toString()).put("excluded",JSONArray(excluded.sorted().map{it.toString()})).put("reminders",JSONArray(reminders.map{it.json()})).put("updated",updated)
 companion object{fun parse(j:JSONObject)=CalendarEvent(id=j.getString("id"),uid=j.optString("uid",j.getString("id")),title=j.getString("title"),start=LocalDateTime.parse(j.getString("start")),end=LocalDateTime.parse(j.getString("end")),zone=j.getString("zone"),allDay=j.optBoolean("allDay"),book=j.optString("book","personal"),location=j.optString("location"),notes=j.optString("notes"),repeat=j.optString("repeat","none"),interval=j.optInt("interval",1),weekdays=j.optInt("weekdays",1),until=j.optString("until").takeIf{it.isNotBlank()&&it!="null"}?.let(LocalDate::parse),excluded=j.optJSONArray("excluded")?.let{a->require(a.length()<=10000);(0 until a.length()).map{LocalDate.parse(a.getString(it))}.toSet()}?:emptySet(),reminders=j.optJSONArray("reminders")?.let{a->require(a.length()<=5);(0 until a.length()).map{a.getJSONObject(it).let{r->CalendarReminder(r.getInt("minutes"),r.optBoolean("sound"))}}}?:emptyList(),updated=j.optLong("updated")).also{it.validate()}}
}
internal data class CalendarOccurrence(val event:CalendarEvent,val date:LocalDate,val start:ZonedDateTime,val end:ZonedDateTime){val key get()="${event.id}:$date"}
internal data class CalendarLane(val occurrence:CalendarOccurrence,val column:Int,val columns:Int)
internal object CalendarRules{
 fun layout(rows:List<CalendarOccurrence>):List<CalendarLane>{val result=mutableListOf<CalendarLane>();var group=mutableListOf<CalendarOccurrence>();var end=Instant.MIN
  fun flush(){val lanes=mutableListOf<Instant>();val positions=group.map{o->val column=lanes.indexOfFirst{it<=o.start.toInstant()}.let{if(it<0)lanes.size else it};if(column==lanes.size)lanes+=o.end.toInstant()else lanes[column]=o.end.toInstant();o to column};positions.forEach{result+=CalendarLane(it.first,it.second,lanes.size)};group=mutableListOf()}
  rows.sortedBy{it.start.toInstant()}.forEach{o->if(group.isNotEmpty()&&o.start.toInstant()>=end)flush();group+=o;end=if(group.size==1)o.end.toInstant()else maxOf(end,o.end.toInstant())};if(group.isNotEmpty())flush();return result
 }
 /** Jump to the relevant period; never scan from a recurring event's original year. */
 fun occurrences(e:CalendarEvent,from:Instant,to:Instant,limit:Int=2000,overlap:Boolean=true,displayZone:ZoneId=ZoneId.systemDefault()):List<CalendarOccurrence>{
  if(to<=from||limit<=0)return emptyList();val zone=if(e.allDay)displayZone else ZoneId.of(e.zone);val duration=Duration.between(e.start,e.end)
  val earliest=from.atZone(zone).toLocalDate().minusDays(if(overlap)duration.toDays()+1 else 1);val last=to.atZone(zone).toLocalDate().plusDays(1);val first=e.start.toLocalDate();val out=mutableListOf<CalendarOccurrence>()
  fun add(date:LocalDate){if(date<first||date in e.excluded||e.until?.let{date>it}==true)return;val start=date.atTime(e.start.toLocalTime()).atZone(zone);val wallEnd=date.atTime(e.start.toLocalTime()).plus(duration).atZone(zone);val end=if(wallEnd.toInstant()<=start.toInstant())start.plus(duration)else wallEnd
   if(start.toInstant()<to&&end.toInstant()>from&&(overlap||start.toInstant()>=from))out+=CalendarOccurrence(e,date,start,end)}
  if(e.repeat=="none"){add(first);return out}
  val stop=minOf(last,e.until?:last);if(stop<earliest)return emptyList()
  when(e.repeat){
   "daily"->{var d=first.plusDays((ChronoUnit.DAYS.between(first,earliest).coerceAtLeast(0)/e.interval)*e.interval);while(d<=stop&&out.size<limit){add(d);d=d.plusDays(e.interval.toLong())}}
   "weekly"->{val anchor=first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));var week=anchor.plusWeeks((ChronoUnit.WEEKS.between(anchor,earliest).coerceAtLeast(0)/e.interval)*e.interval);while(week<=stop&&out.size<limit){for(i in 0..6)if(e.weekdays and(1 shl i)!=0&&out.size<limit)add(week.plusDays(i.toLong()));week=week.plusWeeks(e.interval.toLong())}}
   "monthly"->{val base=YearMonth.from(first);var month=base.plusMonths((ChronoUnit.MONTHS.between(base,YearMonth.from(earliest)).coerceAtLeast(0)/e.interval)*e.interval);while(month.atDay(1)<=stop&&out.size<limit){if(first.dayOfMonth<=month.lengthOfMonth())add(month.atDay(first.dayOfMonth));month=month.plusMonths(e.interval.toLong())}}
   "yearly"->{var year=first.year+((earliest.year-first.year).coerceAtLeast(0)/e.interval)*e.interval;while(year<=stop.year&&out.size<limit){val md=MonthDay.from(first);if(md.isValidYear(year))add(md.atYear(year));year+=e.interval}}
  };return out.sortedBy{it.start.toInstant()}.take(limit)
 }
 fun between(events:List<CalendarEvent>,from:LocalDate,to:LocalDate,books:List<CalendarBook>,zone:ZoneId=ZoneId.systemDefault(),limit:Int=3000):List<CalendarOccurrence>{val visible=books.filter{it.visible}.map{it.id}.toSet();val a=from.atStartOfDay(zone).toInstant();val b=to.atStartOfDay(zone).toInstant();return events.filter{it.book in visible}.flatMap{occurrences(it,a,b,limit,displayZone=zone)}.sortedWith(compareBy<CalendarOccurrence>{!it.event.allDay}.thenBy{it.start.toInstant()}).take(limit)}
 fun reminderLabel(minutes:Int)=when(minutes){0->"At event time";5->"5 minutes before";15->"15 minutes before";60->"1 hour before";1440->"1 day before";4320->"3 days before";else->if(minutes%1440==0)"${minutes/1440} days before"else if(minutes%60==0)"${minutes/60} hours before"else"$minutes minutes before"}
 fun repeatLabel(e:CalendarEvent)=when(e.repeat){"none"->"Does not repeat";"daily"->if(e.interval==1)"Every day"else"Every ${e.interval} days";"weekly"->if(e.interval==1)"Every week"else"Every ${e.interval} weeks";"monthly"->if(e.interval==1)"Every month"else"Every ${e.interval} months";else->if(e.interval==1)"Every year"else"Every ${e.interval} years"}
}
internal data class CalendarState(val events:List<CalendarEvent> = emptyList(),val books:List<CalendarBook> = listOf(CalendarBook(),CalendarBook("work","Work",1),CalendarBook("birthdays","Birthdays",3)),val monday:Boolean=true,val defaultMinutes:Int=60,val revision:Long=0){
 fun json()=JSONObject().put("version",1).put("events",JSONArray(events.map{it.json()})).put("books",JSONArray(books.map{it.json()})).put("monday",monday).put("defaultMinutes",defaultMinutes).put("revision",revision)
 companion object{fun parse(j:JSONObject):CalendarState{require(j.getInt("version")==1);val a=j.getJSONArray("events");val b=j.getJSONArray("books");require(a.length()<=10000&&b.length() in 1..32);val books=(0 until b.length()).map{b.getJSONObject(it).let{v->CalendarBook(v.getString("id"),v.getString("name"),v.optInt("color"),v.optBoolean("visible",true))}};require(books.map{it.name.trim().lowercase()}.distinct().size==books.size&&books.distinctBy{it.id}.size==books.size&&books.all{it.id.matches(Regex("[A-Za-z0-9_-]{1,100}"))&&it.name.isNotBlank()&&it.name.length<=60&&it.color in 0..5});val events=(0 until a.length()).map{CalendarEvent.parse(a.getJSONObject(it))};require(events.distinctBy{it.id}.size==events.size&&events.all{e->books.any{it.id==e.book}});return CalendarState(events,books,j.optBoolean("monday",true),j.optInt("defaultMinutes",60).coerceIn(5,1440),j.optLong("revision"))}}
}
internal class CalendarDisk(c:Context){
 private val file=AtomicFile(File(c.filesDir,"calendar/events.json"))
 fun read():CalendarState {if(!file.baseFile.exists()&&!File(file.baseFile.path+".bak").exists())return CalendarState();return file.openRead().use{require(file.baseFile.length()<=8_000_000);CalendarState.parse(JSONObject(it.readBytes().toString(Charsets.UTF_8)))}}
 fun write(state:CalendarState){CalendarState.parse(state.json());val data=state.json().toString().toByteArray();require(data.size<=8_000_000){"Calendar storage is full. Export a backup before removing older events."};file.baseFile.parentFile!!.mkdirs();val out=file.startWrite();try{out.write(data);file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}}
 companion object{val lock=Any()}
}
