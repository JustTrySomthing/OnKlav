package app.onklav.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import app.onklav.shared.*
import java.time.ZoneId
import java.util.Calendar
import java.util.Date

@Composable
fun ReplyReminderChooser(existing: Long, close: () -> Unit, save: (Long) -> Unit) {
    val context = LocalContext.current
    val spec=LocalAppearanceSpec.current
    val pickerContext=remember(context,spec){appearanceDialogContext(context,spec)}
    var error by remember { mutableStateOf<String?>(null) }
    val format = remember {
        { time: Long -> android.text.format.DateFormat.getMediumDateFormat(context).format(Date(time)) +
            " · " + android.text.format.DateFormat.getTimeFormat(context).format(Date(time)) }
    }
    fun choose(time: Long) {
        if (time != 0L && time <= System.currentTimeMillis()) error = "Choose a time in the future."
        else save(time)
    }
    ReferenceDialog("Remind Me to Reply", close, footer = { DialogAction("Cancel", onClick = close) }) {
        reminderPresets(System.currentTimeMillis(), ZoneId.systemDefault()).forEach { (label, time) ->
            SettingsChoice(label, format(time)) { choose(time) }
        }
        SettingsChoice("Choose Date & Time…") {
            val selected = Calendar.getInstance()
            DatePickerDialog(pickerContext, { _, year, month, day ->
                selected.set(Calendar.YEAR, year)
                selected.set(Calendar.MONTH, month)
                selected.set(Calendar.DAY_OF_MONTH, day)
                TimePickerDialog(pickerContext, { _, hour, minute ->
                    selected.set(Calendar.HOUR_OF_DAY, hour)
                    selected.set(Calendar.MINUTE, minute)
                    selected.set(Calendar.SECOND, 0)
                    selected.set(Calendar.MILLISECOND, 0)
                    choose(selected.timeInMillis)
                }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(context)).apply {
                    setTitle("Reminder Time")
                }.show()
            }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).apply {
                setTitle("Reminder Date")
                datePicker.minDate = System.currentTimeMillis()
            }.show()
        }
        if (existing > 0) SettingsChoice("Remove Reminder", format(existing)) { choose(0) }
        error?.let { Text(it) }
    }
}
