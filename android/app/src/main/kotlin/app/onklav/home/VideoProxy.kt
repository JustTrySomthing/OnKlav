package app.onklav.home

import java.io.File
import java.security.MessageDigest

/** Cache only. Export and portable project archives always resolve original media. */
internal fun videoProxyPath(folder:File,c:VideoClip):File{
    val source=File(folder,c.file);val key="proxy-v1:${c.file}:${source.length()}:${source.lastModified()}:${c.sourceMs}"
    val name=MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString(""){"%02x".format(it)}
    return File(File(folder,".proxies"),"$name.mp4")
}
internal fun videoMediaFile(folder:File,c:VideoClip,preview:Boolean):File{
    if(preview&&!c.image){val proxy=videoProxyPath(folder,c);val ready=File(proxy.path+".ready");if(proxy.isFile&&proxy.length()>0&&ready.isFile&&runCatching{ready.readText()==c.sourceMs.toString()}.getOrDefault(false))return proxy}
    return File(folder,c.file)
}
