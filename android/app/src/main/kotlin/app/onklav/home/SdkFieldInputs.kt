package app.onklav.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.ui.text.input.KeyboardType
import app.onklav.shared.AppearanceSpec
import app.onklav.shared.appearanceDialogContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Shared native input affordances. Data remains plain ISO text in the guest protocol. */
internal object SdkFieldInputs {
    fun keyboard(kind:String):KeyboardType=when(kind){
        "integer"->KeyboardType.Number;"decimal"->KeyboardType.Decimal
        "email"->KeyboardType.Email;"phone"->KeyboardType.Phone;else->KeyboardType.Text
    }
    fun date(context:Context,spec:AppearanceSpec,value:String,selected:(String)->Unit):DatePickerDialog{
        val initial=runCatching{LocalDate.parse(value)}.getOrElse{LocalDate.now()}
        return DatePickerDialog(appearanceDialogContext(context,spec),{_,year,month,day->
            selected(String.format(Locale.ROOT,"%04d-%02d-%02d",year,month+1,day))
        },initial.year,initial.monthValue-1,initial.dayOfMonth)
    }
    fun time(context:Context,spec:AppearanceSpec,value:String,selected:(String)->Unit):TimePickerDialog{
        val initial=runCatching{LocalTime.parse(value)}.getOrElse{LocalTime.now()}
        return TimePickerDialog(appearanceDialogContext(context,spec),{_,hour,minute->
            selected(String.format(Locale.ROOT,"%02d:%02d",hour,minute))
        },initial.hour,initial.minute,android.text.format.DateFormat.is24HourFormat(context))
    }
}
