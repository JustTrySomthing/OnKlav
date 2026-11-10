package app.onklav.home

/** One current audio owner inside OnKlav, including playback, speech and voice. */
internal object HostSpeechOwner {
    private var owner:Any?=null
    private var interrupt:(()->Unit)?=null
    fun claim(next:Any,stop:()->Unit){
        val previous=synchronized(this){
            if(owner===next)return
            val old=interrupt;owner=next;interrupt=stop;old
        }
        // A former decoder must not hold the ownership monitor while shutting
        // down, or prevent the new owner starting if its own cleanup fails.
        runCatching{previous?.invoke()}.onFailure{android.util.Log.w("OnKlavAudio","Previous audio cleanup failed",it)}
    }
    @Synchronized fun release(current:Any){if(owner===current){owner=null;interrupt=null}}
}
