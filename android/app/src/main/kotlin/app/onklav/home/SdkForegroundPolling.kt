package app.onklav.home

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** App UI observation stops while hidden; carrier delivery stays host-owned. */
internal suspend fun sdkForegroundPolling(lifecycle:Lifecycle,watch:()->(()->Unit),refresh:suspend ()->Unit) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        val stop=watch()
        try { while(currentCoroutineContext().isActive){delay(750);refresh()} }
        finally { stop() }
    }
}
