package app.onklav.home

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.ui.text.input.KeyboardType
import app.onklav.shared.AppearanceSpec
import app.onklav.shared.appearanceColors
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SdkFieldInputsTest {
    @Test fun supportedInputsChooseTheirNativeKeyboard(){
        assertEquals(KeyboardType.Number,SdkFieldInputs.keyboard("integer"));assertEquals(KeyboardType.Decimal,SdkFieldInputs.keyboard("decimal"))
        assertEquals(KeyboardType.Email,SdkFieldInputs.keyboard("email"));assertEquals(KeyboardType.Phone,SdkFieldInputs.keyboard("phone"));assertEquals(KeyboardType.Text,SdkFieldInputs.keyboard("unknown"))
    }
    @Test fun datePickerReturnsIsoAndCancellationPreservesTheDraft(){
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get();val spec=AppearanceSpec(dark=true,palette=4,font=4);var value="keep draft"
        val cancelled=SdkFieldInputs.date(activity,spec,"invalid date"){value=it};cancelled.show();cancelled.cancel();assertEquals("keep draft",value)
        val picker=SdkFieldInputs.date(activity,spec,"2024-02-29"){value=it};picker.show();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();picker.datePicker.updateDate(2025,3,9);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(picker.getButton(DatePickerDialog.BUTTON_POSITIVE).isEnabled);picker.getButton(DatePickerDialog.BUTTON_POSITIVE).performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals("2025-04-09",value)
        val accent=android.util.TypedValue();assertTrue(picker.context.theme.resolveAttribute(android.R.attr.colorAccent,accent,true));assertEquals(appearanceColors(spec).primary.toArgb(),accent.data);activity.finish()
    }
    @Test fun timePickerFollowsUserClockWhileReturningIsoText(){
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val previous=android.provider.Settings.System.getString(activity.contentResolver,android.provider.Settings.System.TIME_12_24)
        try {for(format in listOf("12","24")){
            android.provider.Settings.System.putString(activity.contentResolver,android.provider.Settings.System.TIME_12_24,format)
            val picker=SdkFieldInputs.time(activity,AppearanceSpec(),"23:59"){}
            picker.show()
            val time=picker.findViewById<android.widget.TimePicker>(activity.resources.getIdentifier("timePicker","id","android"))
            assertEquals(format=="24",time.is24HourView)
            picker.dismiss()
        }}finally{android.provider.Settings.System.putString(activity.contentResolver,android.provider.Settings.System.TIME_12_24,previous)}
        activity.finish()
    }
    @Test fun timePickerReturnsStableTwentyFourHourText(){
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get();var value=""
        val picker=SdkFieldInputs.time(activity,AppearanceSpec(dark=false,palette=9),"23:59"){value=it};picker.show();picker.updateTime(5,7);picker.getButton(TimePickerDialog.BUTTON_POSITIVE).performClick();assertEquals("05:07",value);activity.finish()
    }
}
