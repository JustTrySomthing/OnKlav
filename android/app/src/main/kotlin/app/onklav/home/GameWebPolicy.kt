package app.onklav.home

import android.net.Uri

internal object GameWebPolicy {
    const val CSP="default-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval' 'wasm-unsafe-eval'; connect-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; style-src 'self' 'unsafe-inline'; font-src 'self'; worker-src 'self' blob:; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'"
    // Run before any game JS, with non-configurable properties. Games have local resources,
    // not a peer-network/device API. No host objects or native methods are exposed.
    const val BOOTSTRAP="""(() => { for (const key of ['RTCPeerConnection','webkitRTCPeerConnection','RTCDataChannel','WebSocket','WebTransport']) { Object.defineProperty(globalThis,key,{value:undefined,writable:false,configurable:false}); } })();"""
    fun exitMessage(message:String)=message=="ONKLAV_GAME_EXIT"
    fun local(origin:String,uri:Uri)=uri.scheme=="https" && uri.encodedAuthority==Uri.parse(origin).encodedAuthority && uri.port==-1 && uri.userInfo==null
    fun path(origin:String,uri:Uri,method:String):String? {
        if(method!="GET" || !local(origin,uri))return null
        val name=uri.encodedPath.orEmpty().removePrefix("/")
        return name.takeIf { it.startsWith("web/") && GamePackages.safe(it) }
    }
}
