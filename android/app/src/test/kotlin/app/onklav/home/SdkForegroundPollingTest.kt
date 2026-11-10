package app.onklav.home

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@LooperMode(LooperMode.Mode.PAUSED)
class SdkForegroundPollingTest {
    @Test fun pollingAndWatchersStopWhileHiddenAndRestartOnce(){
        val owner=object:LifecycleOwner {
            val registry=LifecycleRegistry.createUnsafe(this)
            override val lifecycle:Lifecycle get()=registry
        }
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        var watches=0;var stops=0;var refreshes=0
        fun idle(ms:Long=0)=shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        try {
            owner.registry.currentState=Lifecycle.State.CREATED
            scope.launch{sdkForegroundPolling(owner.lifecycle,watch={watches++;{stops++;Unit}},refresh={refreshes++})}
            idle(800);assertEquals(0,watches);assertEquals(0,refreshes)
            owner.registry.currentState=Lifecycle.State.STARTED
            idle(800);assertEquals(1,watches);assertEquals(1,refreshes)
            owner.registry.currentState=Lifecycle.State.CREATED
            idle(1600);assertEquals(1,stops);assertEquals(1,refreshes)
            owner.registry.currentState=Lifecycle.State.STARTED
            idle(800);assertEquals(2,watches);assertEquals(2,refreshes)
            owner.registry.currentState=Lifecycle.State.DESTROYED
            idle(1600);assertEquals(2,stops);assertEquals(2,refreshes)
        } finally {scope.cancel();idle()}
    }
}
