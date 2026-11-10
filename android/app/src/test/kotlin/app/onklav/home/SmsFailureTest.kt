package app.onklav.home

import android.telephony.SmsManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsFailureTest {
    @Test fun distinguishesRadioAndServiceFailures() {
        assertTrue(MessagingTransport.smsFailure(SmsManager.RESULT_ERROR_RADIO_OFF).contains("airplane mode"))
        assertTrue(MessagingTransport.smsFailure(SmsManager.RESULT_ERROR_NO_SERVICE).contains("No cellular SMS service"))
    }
    @Test fun retainsModemDetailsAndPartialSendCaution() {
        val text = MessagingTransport.smsFailure(SmsManager.RESULT_ERROR_GENERIC_FAILURE, radioError = 42, partCount = 2)
        assertTrue(text.contains("SMS 1, modem 42"))
        assertTrue(text.contains("other parts may have been sent"))
    }
    @Test fun missingDefaultSimHasSpecificRecovery() {
        assertTrue(MessagingTransport.smsFailure(SmsManager.RESULT_ERROR_GENERIC_FAILURE, noDefault = true).startsWith("Choose a sending SIM"))
    }
    @Test fun unknownCodesRemainVisible() {
        assertTrue(MessagingTransport.smsFailure(999).contains("SMS 999"))
    }
    @Test fun shortNetworkFailureDoesNotClaimOtherPartsOrALengthProblem() {
        val text=MessagingTransport.smsFailure(SmsManager.RESULT_NETWORK_ERROR,radioError=2,partCount=1)
        assertTrue(text.startsWith("The mobile network"));assertTrue(text.contains("SMS 17, modem 2"))
        assertFalse(text.contains("other parts"));assertFalse(text.contains("too long"));assertTrue(text.contains("recipient"))
        assertFalse(MessagingTransport.smsFailure(SmsManager.RESULT_NETWORK_ERROR).contains("other parts"))
    }
    @Test fun modemAndRadioNetworkErrorsAreNamedAccurately() {
        assertTrue(MessagingTransport.smsFailure(SmsManager.RESULT_MODEM_ERROR).startsWith("The phone modem"))
        assertTrue(MessagingTransport.smsFailure(SmsManager.RESULT_RIL_NETWORK_ERR).startsWith("The mobile network"))
    }
    @Test fun oldSinglePartFailureIsCorrectedForDisplayWithoutChangingItsReceipt() {
        val old="The phone or carrier could not send this text. (SMS 17, modem 2). Check before retrying; other parts may have been sent."
        val send=org.json.JSONObject().put("kind","sms").put("parts",1).put("error",old)
        assertTrue(MessagingTransport.smsDisplayError(send).startsWith("The mobile network"))
        assertFalse(MessagingTransport.smsDisplayError(send).contains("other parts"));assertEquals(old,send.getString("error"))
        send.put("parts",2);assertTrue(MessagingTransport.smsDisplayError(send).contains("other parts"))
        send.put("kind","mms");assertEquals(old,MessagingTransport.smsDisplayError(send))
        send.put("kind","sms").put("error","Retained custom failure");assertEquals("Retained custom failure",MessagingTransport.smsDisplayError(send))
    }

}
