package app.onklav.home

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class CalendarImport(val events:List<CalendarEvent>,val skipped:Int)
/** Bounded interoperable VEVENT subset. Unsupported rules are reported, never flattened silently. */
internal object CalendarIcs{
 const val MAX_BYTES=24_000_000
 const val MAX_EVENTS=10000
 private val date=DateTimeFormatter.BASIC_ISO_DATE;private val time=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
 private fun escape(s:String)=s.replace("\\","\\\\").replace("\r","").replace("\n","\\n").replace(";","\\;").replace(",","\\,")
 private fun unescape(s:String):String{val out=StringBuilder();var i=0;while(i<s.length){if(s[i]=='\\'&&i+1<s.length){i++;out.append(if(s[i]=='n'||s[i]=='N')'\n'else s[i])}else out.append(s[i]);i++};return out.toString()}
 private fun fold(s:String):String{val out=StringBuilder();var length=0;s.codePoints().forEach{code->val text=String(Character.toChars(code));val count=text.toByteArray(Charsets.UTF_8).size;if(length+count>73){out.append("\r\n ");length=1};out.append(text);length+=count};return out.toString()}
 fun export(events:List<CalendarEvent>):String{val lines=mutableListOf("BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//OnKlav//Offline Calendar//EN","CALSCALE:GREGORIAN")
  for(e in events){e.validate();lines+="BEGIN:VEVENT";lines+="UID:${escape(e.uid)}";lines+="DTSTAMP:${Instant.ofEpochMilli(e.updated).atZone(ZoneOffset.UTC).format(time)}Z";lines+="SUMMARY:${escape(e.title)}"
   if(e.allDay){lines+="DTSTART;VALUE=DATE:${e.start.toLocalDate().format(date)}";lines+="DTEND;VALUE=DATE:${e.end.toLocalDate().format(date)}"}
   else{lines+="DTSTART;TZID=${e.zone}:${e.start.format(time)}";lines+="DTEND;TZID=${e.zone}:${e.end.format(time)}";lines+="X-ONKLAV-ZONE:${e.zone}"}
   if(e.location.isNotEmpty())lines+="LOCATION:${escape(e.location)}";if(e.notes.isNotEmpty())lines+="DESCRIPTION:${escape(e.notes)}"
   if(e.repeat!="none"){val days=listOf("MO","TU","WE","TH","FR","SA","SU");var rule="FREQ=${e.repeat.uppercase()};INTERVAL=${e.interval}";if(e.repeat=="weekly")rule+=";BYDAY="+days.filterIndexed{i,_->e.weekdays and(1 shl i)!=0}.joinToString(",");e.until?.let{rule+=";UNTIL="+it.format(date)};lines+="RRULE:$rule"}
   e.excluded.sorted().forEach{d->lines+=if(e.allDay)"EXDATE;VALUE=DATE:${d.format(date)}"else"EXDATE;TZID=${e.zone}:${d.atTime(e.start.toLocalTime()).format(time)}"}
   e.reminders.forEach{r->lines+="BEGIN:VALARM";lines+="ACTION:${if(r.sound)"AUDIO"else"DISPLAY"}";lines+="TRIGGER:-PT${r.minutes}M";lines+="DESCRIPTION:${escape(e.title)}";lines+="END:VALARM"};lines+="END:VEVENT"
  };lines+="END:VCALENDAR";return lines.joinToString("\r\n"){fold(it)}+"\r\n"
 }
 fun parse(text:String,book:String="personal"):CalendarImport{
  require(text.toByteArray().size<=MAX_BYTES){"Calendar file exceeds 24 MB"};require(text.contains("BEGIN:VCALENDAR")){"Choose an iCalendar (.ics) file"}
  val lines=text.replace("\r\n","\n").replace("\r","\n").replace(Regex("\n[ \t]"),"").split('\n');require(lines.size<=1000000)
  val blocks=mutableListOf<List<String>>();var current:MutableList<String>?=null
  lines.forEach{line->when(line){"BEGIN:VEVENT"->{require(current==null);current=mutableListOf()};"END:VEVENT"->{blocks+=checkNotNull(current);current=null;require(blocks.size<=MAX_EVENTS)};else->current?.add(line)}};require(current==null){"Calendar file is incomplete"}
  val exceptionUids=blocks.filter{b->b.any{it.startsWith("RECURRENCE-ID")}}.mapNotNull{b->b.firstOrNull{it.startsWith("UID:")}?.substringAfter(':')}.toSet()
  require(blocks.sumOf{b->b.firstOrNull{it.startsWith("RRULE:")}?.substringAfter("COUNT=","")?.substringBefore(';')?.toIntOrNull()?.coerceAtLeast(0)?:0}<=100000){"This import has too many counted repeats. Import smaller calendar files."}
  var skipped=0;val events=blocks.mapNotNull{block->if(block.firstOrNull{it.startsWith("UID:")}?.substringAfter(':') in exceptionUids){skipped++;null}else runCatching{parseEvent(block,book)}.getOrElse{skipped++;null}};return CalendarImport(events.distinctBy{it.uid},skipped)
 }
 private fun parseEvent(lines:List<String>,book:String):CalendarEvent{
  val props=mutableListOf<Pair<String,String>>();val reminders=mutableListOf<CalendarReminder>();var alarm=false;var sound=false;var minutes:Int?=null
  lines.forEach{line->when(line){"BEGIN:VALARM"->{alarm=true;sound=false;minutes=null};"END:VALARM"->{if(minutes!=null)reminders+=CalendarReminder(minutes!!,sound);alarm=false};else->{val n=line.indexOf(':');if(n>0){val key=line.substring(0,n);val value=line.substring(n+1);if(alarm){if(key=="ACTION"){require(value in listOf("AUDIO","DISPLAY"));sound=value=="AUDIO"};if(key.startsWith("TRIGGER")){require(key=="TRIGGER");require(value.startsWith('-')||value=="PT0M");val duration=Duration.parse(value.removePrefix("-"));require(duration.seconds%60==0L&&duration.toMinutes() in 0..525600);minutes=duration.toMinutes().toInt()}}else props+=key to value}}}}
  fun field(name:String)=props.firstOrNull{it.first.substringBefore(';')==name}
  require(field("RECURRENCE-ID")==null&&field("DURATION")==null){"Exception/duration format requires conversion"}
  fun stamp(p:Pair<String,String>):Triple<LocalDateTime,Boolean,String>{val (key,value)=p;val all=key.contains("VALUE=DATE")||value.length==8;if(all)return Triple(LocalDate.parse(value,date).atStartOfDay(),true,ZoneId.systemDefault().id)
   val zone=if(value.endsWith('Z'))"UTC"else key.substringAfter("TZID=",ZoneId.systemDefault().id).substringBefore(';').trim('"');ZoneId.of(zone)
   val plain=value.removeSuffix("Z");val parsed=LocalDateTime.parse(if(plain.length==13)plain+"00"else plain,time);return Triple(parsed,false,zone)}
  val begin=stamp(checkNotNull(field("DTSTART")));val finish=field("DTEND")?.let(::stamp)?:Triple(if(begin.second)begin.first.plusDays(1)else begin.first.plusHours(1),begin.second,begin.third);require(begin.second==finish.second)
  val zone=field("X-ONKLAV-ZONE")?.second?:begin.third;val start=begin.first.atZone(ZoneId.of(begin.third)).withZoneSameInstant(ZoneId.of(zone)).toLocalDateTime();val end=finish.first.atZone(ZoneId.of(finish.third)).withZoneSameInstant(ZoneId.of(zone)).toLocalDateTime()
  require(reminders.size<=5);var e=CalendarEvent(title=unescape(field("SUMMARY")?.second?:"Untitled event"),start=start,end=end,zone=zone,allDay=begin.second,book=book,notes=unescape(field("DESCRIPTION")?.second.orEmpty()),location=unescape(field("LOCATION")?.second.orEmpty()),uid=unescape(field("UID")?.second?:UUID.randomUUID().toString()),reminders=reminders.distinctBy{it.minutes}.take(5))
  field("RRULE")?.let{p->val rule=p.second.split(';').associate{v->v.substringBefore('=') to v.substringAfter('=')};require(rule.keys.all{it in listOf("FREQ","INTERVAL","BYDAY","UNTIL","WKST","COUNT")}){"Unsupported recurrence rule"};val freq=rule["FREQ"]!!.lowercase();require(freq in listOf("daily","weekly","monthly","yearly"));require(!rule.containsKey("BYDAY")||freq=="weekly");val days=listOf("MO","TU","WE","TH","FR","SA","SU");val mask=rule["BYDAY"]?.split(',')?.fold(0){a,s->val i=days.indexOf(s);require(i>=0);a or(1 shl i)}?:e.weekdays;val until=rule["UNTIL"]?.let{v->if(v.length==8)LocalDate.parse(v,date)else stamp("DTSTART" to v).let{t->require(!e.allDay){"Timed repeat cutoff on an all-day event is unsupported"};val cutoff=t.first.atZone(ZoneId.of(t.third)).toInstant();val local=cutoff.atZone(ZoneId.of(zone)).toLocalDate();if(local.atTime(e.start.toLocalTime()).atZone(ZoneId.of(zone)).toInstant()>cutoff)local.minusDays(1)else local}};e=e.copy(repeat=freq,interval=rule["INTERVAL"]?.toInt()?:1,weekdays=mask,until=until);require(rule["WKST"] in listOf(null,"MO")||freq!="weekly"||e.interval==1){"Unsupported interval week start"};rule["COUNT"]?.let{raw->require(until==null);val count=raw.toInt();require(count in 1..10000);e.validate();val rows=CalendarRules.occurrences(e,e.start.atZone(ZoneId.of(e.zone)).toInstant().minusSeconds(1),e.start.atZone(ZoneId.of(e.zone)).plusYears(count*e.interval*4L+8).toInstant(),count,false);require(rows.size==count);val last=rows.last().date;require(last.year<=2199);e=e.copy(until=last)}}
  val excluded=props.filter{it.first.substringBefore(';')=="EXDATE"}.flatMap{p->p.second.split(',').map{value->stamp(p.first to value).let{t->if(t.second)t.first.toLocalDate()else t.first.atZone(ZoneId.of(t.third)).withZoneSameInstant(ZoneId.of(zone)).toLocalDate()}}}.toSet()
  return e.copy(excluded=excluded).also{it.validate()}
 }
}
