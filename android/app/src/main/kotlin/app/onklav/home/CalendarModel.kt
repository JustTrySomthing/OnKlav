package app.onklav.home

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.*
import kotlinx.coroutines.*
import java.time.*
import java.util.UUID

internal class CalendarModel(app:Application):AndroidViewModel(app){
 var state by mutableStateOf(CalendarState());private set
 var busy by mutableStateOf(false);private set
 var ready by mutableStateOf(false);private set
 var message by mutableStateOf("");private set
 var failed by mutableStateOf(false);private set
 var reminderError by mutableStateOf("");private set
 var selected by mutableStateOf(LocalDate.now())
 var mode by mutableStateOf("Month")
 var query by mutableStateOf("")
 var searching by mutableStateOf(false)
 var draft by mutableStateOf<CalendarEvent?>(null);private set
 var original by mutableStateOf<CalendarEvent?>(null);private set
 var occurrenceParent:CalendarOccurrence?=null;private set
 var detail by mutableStateOf<CalendarOccurrence?>(null)
 var imported by mutableStateOf<CalendarImport?>(null);private set
 private var editingExisting=false
 private var undoState:CalendarState?=null;private var undoRevision=0L
 val canUndo get()=undoState!=null
 init{load()}
 private fun task(block:suspend()->Unit){if(busy)return;busy=true;message="";failed=false;viewModelScope.launch{try{block();if(reminderError.isNotBlank())message=(message.ifBlank{"Calendar loaded"})+" · Reminders not scheduled. Retry reminders."}catch(e:Exception){if(e is CancellationException)throw e;message=e.message?.take(240)?:"Calendar could not save. Try again.";failed=true}finally{busy=false}}}
 fun load(){task{state=withContext(Dispatchers.IO){synchronized(CalendarDisk.lock){CalendarDisk(getApplication()).read()}};ready=true;refreshReminders()}}
 private suspend fun refreshReminders(){reminderError=withContext(Dispatchers.IO){runCatching{CalendarReminders.recover(getApplication())}.exceptionOrNull()?.let{"Reminders could not be scheduled. Your events are saved."}.orEmpty()}}
 fun retryReminders(){task{refreshReminders();if(reminderError.isEmpty())message="Reminders updated"}}
 private suspend fun mutate(action:(CalendarState)->CalendarState):CalendarState=withContext(Dispatchers.IO){synchronized(CalendarDisk.lock){val disk=CalendarDisk(getApplication());val old=disk.read();val next=action(old).copy(revision=old.revision+1);disk.write(next);reminderError=runCatching{CalendarReminders.cleanup(getApplication(),old.events,next.events)}.exceptionOrNull()?.let{"Reminders could not be scheduled. Your events are saved."}.orEmpty();next}}
 fun new(date:LocalDate=selected,hour:Int?=null){if(!ready||busy)return;val now=LocalDateTime.now();val start=when{hour!=null->date.atTime(hour,0);date==now.toLocalDate()->now.plusMinutes(30).withSecond(0).withNano(0).let{it.withMinute(it.minute/30*30)};else->date.atTime(9,0)};val book=state.books.firstOrNull{it.visible}?:state.books.first();draft=CalendarEvent(start=start,end=start.plusMinutes(state.defaultMinutes.toLong()),book=book.id);original=draft;editingExisting=false;occurrenceParent=null;detail=null}
 fun edit(o:CalendarOccurrence,one:Boolean){val e=o.event;occurrenceParent=if(one&&e.repeat!="none")o else null
  draft=if(occurrenceParent!=null)e.copy(id=UUID.randomUUID().toString(),uid=UUID.randomUUID().toString(),start=o.date.atTime(e.start.toLocalTime()),end=o.date.atTime(e.start.toLocalTime()).plus(Duration.between(e.start,e.end)),repeat="none",excluded=emptySet(),until=null)else e
  original=draft;editingExisting=occurrenceParent==null;detail=null
 }
 fun change(e:CalendarEvent){if(!busy)draft=e}
 fun closeDraft(){draft=null;original=null;occurrenceParent=null}
 fun save(){val input=draft?:return;task{input.validate();val parent=occurrenceParent;val base=original
  state=mutate{s->val existing=s.events.firstOrNull{it.id==input.id};if(editingExisting)check(existing!=null&&existing.updated==base?.updated){"This event changed. Reopen it before saving."};var events=s.events
   if(parent!=null){val p=events.firstOrNull{it.id==parent.event.id};check(p?.updated==parent.event.updated){"This series changed. Reopen it before saving."};events=events.map{if(it.id==p!!.id)it.copy(excluded=it.excluded+parent.date,updated=maxOf(System.currentTimeMillis(),it.updated+1))else it}}
   s.copy(events=events.filter{it.id!=input.id}+input.copy(updated=maxOf(System.currentTimeMillis(),input.updated+1)))}
  selected=if(input.allDay)input.start.toLocalDate()else input.start.atZone(ZoneId.of(input.zone)).withZoneSameInstant(ZoneId.systemDefault()).toLocalDate();closeDraft();message=if(input.reminders.isNotEmpty()&&!CalendarReminders.enabled(getApplication()))"Saved · enable notifications for reminders"else"Event saved"
 }}
 fun delete(o:CalendarOccurrence,one:Boolean){task{var before:CalendarState?=null;state=mutate{s->before=s;val existing=s.events.firstOrNull{it.id==o.event.id};check(existing?.updated==o.event.updated){"This event changed. Reopen it before deleting."};s.copy(events=if(one&&o.event.repeat!="none")s.events.map{if(it.id==o.event.id)it.copy(excluded=it.excluded+o.date,updated=maxOf(System.currentTimeMillis(),it.updated+1))else it}else s.events.filter{it.id!=o.event.id})};undoState=checkNotNull(before);undoRevision=state.revision;detail=null;message="Event deleted"}}
 fun undo(){val before=undoState?:return;task{state=mutate{s->check(s.revision==undoRevision){"Calendar changed after deletion. Undo is no longer available."};before};undoState=null;message="Change undone"}}
 fun books(books:List<CalendarBook>){task{state=mutate{it.copy(books=books)};message="Calendars updated"}}
 fun settings(monday:Boolean,minutes:Int){task{state=mutate{it.copy(monday=monday,defaultMinutes=minutes)};message="Settings saved"}}
 fun export(uri:Uri){task{val events=withContext(Dispatchers.IO){synchronized(CalendarDisk.lock){CalendarDisk(getApplication()).read().events}};val text=withContext(Dispatchers.Default){CalendarIcs.export(events)};writeFiledExport(getApplication(),uri){it.write(text.toByteArray())};message="Calendar exported"}}
 fun import(uri:Uri){task{imported=withContext(Dispatchers.IO){val bytes=getApplication<Application>().contentResolver.openInputStream(uri)!!.use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=CalendarIcs.MAX_BYTES){"Calendar file exceeds 24 MB"};out.write(buffer,0,n)};out.toByteArray()};require(bytes.size<=CalendarIcs.MAX_BYTES){"Calendar file exceeds 24 MB"};CalendarIcs.parse(bytes.toString(Charsets.UTF_8),state.books.first().id)}}}
 fun dismissImport(){imported=null}
 fun acceptImport(book:String){val pending=imported?:return;task{var count=0;var before:CalendarState?=null;state=mutate{s->before=s;check(s.books.any{it.id==book}){"Choose an available calendar"};val known=s.events.map{it.uid}.toSet();val added=pending.events.filter{it.uid !in known}.map{it.copy(book=book)};count=added.size;s.copy(events=s.events+added)};undoState=checkNotNull(before);undoRevision=state.revision;imported=null;message="$count events imported · ${pending.skipped} unsupported events skipped"}}
 fun navigate(amount:Long){selected=when(mode){"Month"->selected.plusMonths(amount);"Week"->selected.plusWeeks(amount);else->selected.plusDays(amount)}}
 fun notification(id:String,date:String?=null){val e=state.events.firstOrNull{it.id==id}?:return;val requested=runCatching{date?.let(LocalDate::parse)}.getOrNull();if(requested!=null){val zone=if(e.allDay)ZoneId.systemDefault()else ZoneId.of(e.zone);val found=CalendarRules.occurrences(e,requested.atStartOfDay(zone).toInstant(),requested.plusDays(1).atStartOfDay(zone).toInstant()).firstOrNull{it.date==requested};if(found!=null){selectNotice(found)}else message="This event has changed. Search Calendar for the current event.";return};val now=Instant.now();val occurrence=CalendarRules.occurrences(e,now.minusSeconds(86400),now.atZone(ZoneId.systemDefault()).plusYears(100).toInstant(),2).firstOrNull()?:CalendarOccurrence(e,e.start.toLocalDate(),e.start.atZone(ZoneId.of(e.zone)),e.end.atZone(ZoneId.of(e.zone)));selectNotice(occurrence)}
 private fun selectNotice(o:CalendarOccurrence){selected=if(o.event.allDay)o.date else o.start.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate();detail=o}

}
