package app.onklav.home

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

/** Walk through the signed Files controller, never the content index or arbitrary paths. */
internal suspend fun compressFolder(c:Context,uri:Uri,staging:File):CompressSource=withContext(Dispatchers.IO){
    val row=FileSelectionStore.read(c,uri);require(row.optString("kind")=="folder"){"Choose a folder in OnKlav Files."}
    val path=FileSelectionStore.paths(row);require(path.isNotEmpty()){"Choose a folder inside Files."}
    require(path.none{AppSdk.folderVault(c,"status",it).optBoolean("encrypted")}){"Choose an unencrypted folder for a ZIP archive."}
    val entries=mutableListOf<CompressEntry>();var bytes=0L;var visited=0
    val rootName=CompressEngine.cleanName(row.optString("name","Folder"))
    FileSelectionSession(c).use{session->session.start()
        suspend fun walk(at:List<String>,prefix:String,depth:Int){
            currentCoroutineContext().ensureActive();require(depth<=32&&++visited<=CompressEngine.MAX_ENTRIES){"Folder is too large. Choose a smaller folder."}
            at.forEach{AppSdk.folderVault(c,"access",it)};session.navigate(at);session.event("query")
            val rows=mutableListOf<JSONObject>();val ids=mutableSetOf<String>()
            while(true){currentCoroutineContext().ensureActive();val state=session.state
                for(item in state.nodes("items")){require(ids.add(item.getString("id"))){"Folder listing changed. Choose it again."};rows+=JSONObject(item.toString());require(rows.size+entries.size<=CompressEngine.MAX_ENTRIES){"Choose a folder with fewer than 10,000 entries."}}
                if(state.optInt("offset")+40>=state.optInt("total"))break
                session.event("next")
            }
            entries+=CompressEntry(prefix)
            val names=mutableSetOf<String>()
            for(item in rows){currentCoroutineContext().ensureActive();val id=item.getString("id");AppSdk.folderVault(c,"access",id)
                require(!item.optBoolean("encrypted")){"Move encrypted content to a separate folder before making a ZIP."}
                val name=prefix+"/"+CompressEngine.uniqueName(item.optString("name","File"),names)
                if(item.optBoolean("folder"))walk(at+id,name,depth+1)
                else{
                    require(++visited<=CompressEngine.MAX_ENTRIES){"Choose a folder with fewer than 10,000 entries."}
                    val descriptor=session.chooseAt(at,item);val selected=FileSelectionStore.selected(c,descriptor,at)
                    val file=File(staging,"file-${entries.size}");val copied=CompressEngine.copy(c,selected,file,CompressEngine.MAX_BYTES-bytes);bytes+=copied.second;entries+=CompressEntry(name,file)
                }
            }
        }
        walk(path,rootName,0)
    }
    CompressSource(rootName,CompressKind.FILES,entries,bytes,note="Includes subfolders and empty folders. ZIP keeps file contents unchanged.")
}
