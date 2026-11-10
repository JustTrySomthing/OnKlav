package app.onklav.home

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import app.onklav.shared.OnKlavTheme
import java.time.*

/** Other apps may propose an event; the user must review it and press Save. */
class CalendarEntryActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);FolderVaultLifecycle.install(this);setContent{OnKlavTheme{val model:CalendarModel=viewModel();var accepted by remember{mutableStateOf(false)};LaunchedEffect(model.ready,model.busy){if(model.ready&&!model.busy&&!accepted){accepted=true;model.new();model.draft?.let{draft->model.change(runCatching{calendarIntentEvent(intent,draft)}.getOrElse{draft.copy(notes="Event details could not be read. Enter the event information before saving.")})}}};CalendarWorkspace({finish()},model)}}}}
internal fun calendarIntentEvent(i:Intent,base:CalendarEvent):CalendarEvent{val all=i.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY,false);val zone=runCatching{ZoneId.of(i.getStringExtra(CalendarContract.Events.EVENT_TIMEZONE)?:ZoneId.systemDefault().id)}.getOrDefault(ZoneId.systemDefault());val begin=i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,base.start.atZone(zone).toInstant().toEpochMilli());val finish=i.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME,begin+3600000)
 fun date(ms:Long)=Instant.ofEpochMilli(ms).atZone(if(all)ZoneOffset.UTC else zone).toLocalDateTime()
 val start=if(all)date(begin).toLocalDate().atStartOfDay()else date(begin);val end=if(all)maxOf(start.plusDays(1),date(finish).toLocalDate().atStartOfDay())else maxOf(start.plusMinutes(1),date(finish))
 if(start.year !in 1900..2199||end.year !in 1900..2200||Duration.between(start,end)>Duration.ofDays(366))return base.copy(notes="The proposed date range could not be used. Enter the event information before saving.")
 return base.copy(title=i.getStringExtra(CalendarContract.Events.TITLE).orEmpty().take(200),notes=i.getStringExtra(CalendarContract.Events.DESCRIPTION).orEmpty().take(20000),location=i.getStringExtra(CalendarContract.Events.EVENT_LOCATION).orEmpty().take(1000),start=start,end=end,allDay=all,zone=zone.id)
}
