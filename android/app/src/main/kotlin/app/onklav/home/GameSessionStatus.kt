package app.onklav.home

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.IOException
import java.io.File
import java.io.FileOutputStream

/** Legacy last-played hint only; never a save owner or a launch gate. No process-local preference cache.
 * Serialize AtomicFile readers and writers across processes as well as threads.
 */
internal class GameSessionStatus(private val directory:File,private val legacy:()->String?={null}) {
    private fun <T> locked(block:(AtomicFile)->T):T = synchronized(monitor) {
        if(!directory.isDirectory && !directory.mkdirs())throw IOException("Game session directory unavailable")
        FileOutputStream(File(directory,"game-session.lock"),true).channel.use { channel ->
            channel.lock().use { block(AtomicFile(File(directory,"game-session"))) }
        }
    }
    private fun read(file:AtomicFile):String? {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) {
            val previous=legacy()?.takeIf(::valid)
            write(file,previous)
            return previous
        }
        return file.openRead().use { input ->
            val bytes=ByteArray(161)
            var count=0
            while(count<bytes.size) {
                val amount=input.read(bytes,count,bytes.size-count)
                if(amount<0)break
                count+=amount
            }
            if(count>160)null else String(bytes,0,count,Charsets.UTF_8).takeIf(::valid)
        }
    }
    private fun write(file:AtomicFile,id:String?) {
        val stream=file.startWrite()
        try {stream.write(id.orEmpty().toByteArray(Charsets.UTF_8));file.finishWrite(stream)}
        catch(error:Exception){file.failWrite(stream);throw error}
    }
    fun current():String? = try { locked { read(it) } }
        catch(error:IOException){Log.w("OnKlavGames","Could not read game session status",error);null}
    fun save(id:String?):Boolean {
        require(id==null || valid(id))
        return try {locked {write(it,id)};true}
            catch(error:IOException){Log.w("OnKlavGames","Could not update game session status",error);false}
    }
    /** Compatibility hint update. Multiple games can retain independent saves. */
    fun claim(id:String):Boolean {
        require(valid(id))
        return try {locked {file-> val current=read(file);run {write(file,id);true}}}
            catch(error:IOException){Log.w("OnKlavGames","Could not claim game session",error);false}
    }
    fun release(id:String):Boolean = try {locked {file->if(read(file)==id)write(file,null);true}}
        catch(error:IOException){Log.w("OnKlavGames","Could not release game session",error);false}
    companion object {
        private val monitor=Any()
        private fun valid(id:String)=id.length in 1..160 && id.matches(Regex("[a-z][a-z0-9_.-]*"))
        fun open(context:Context)=GameSessionStatus(context.noBackupFilesDir) {
            context.getSharedPreferences("game_session",Context.MODE_PRIVATE).getString("paused_game",null)
        }
    }
}
