package app.onklav.shared
object NativeCore {
    init {
        System.loadLibrary("onklav_core")
    }

    @JvmStatic external fun dispatch(root: String, request: String): String
}
