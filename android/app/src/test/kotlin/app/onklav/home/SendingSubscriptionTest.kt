package app.onklav.home

import org.junit.Assert.*
import org.junit.Test

class SendingSubscriptionTest {
    @Test fun followsCurrentSystemDefault() {
        assertEquals(7, SendingSubscription.resolve(null, null, 7, listOf(3, 7)))
    }
    @Test fun respectsActiveSavedChoice() {
        assertEquals(3, SendingSubscription.resolve(null, 3, 7, listOf(3, 7)))
    }
    @Test fun recoversStaleChoiceOnSingleSimPhone() {
        assertEquals(7, SendingSubscription.resolve(null, 3, 7, listOf(7)))
    }
    @Test fun resolvesSoleSimWhenAndroidDefaultIsUnset() {
        assertEquals(7, SendingSubscription.resolve(null, null, -1, listOf(7)))
    }
    @Test fun doesNotGuessBetweenTwoSims() {
        assertThrows(IllegalStateException::class.java) { SendingSubscription.resolve(null, null, -1, listOf(3, 7)) }
        assertThrows(IllegalStateException::class.java) { SendingSubscription.resolve(null, 9, 7, listOf(3, 7)) }
    }
    @Test fun explicitTransferCannotSwitchSims() {
        assertThrows(IllegalStateException::class.java) { SendingSubscription.resolve(3, null, 7, listOf(7)) }
        assertEquals(3, SendingSubscription.resolve(3, 7, 7, listOf(3, 7)))
    }
    @Test fun missingPhoneStatePermissionStillUsesValidDefault() {
        assertEquals(7, SendingSubscription.resolve(null, null, 7, null))
    }
    @Test fun noSimFailsBeforeStaging() {
        assertThrows(IllegalStateException::class.java) { SendingSubscription.resolve(null, 3, 3, emptyList()) }
    }
}
