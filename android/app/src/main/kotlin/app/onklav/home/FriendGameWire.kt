package app.onklav.home

/** Single-part ASCII carrier frames. No arbitrary phone numbers or SMS bodies from games. */
internal data class FriendGameWire(val type:String,val id:String,val sequence:Int,val data:String) {
    fun encode()="$PREFIX$type|$id|$sequence|$data"
    companion object {
        const val QUESTION="? Do you have OnKlav App ?"
        const val PREFIX="!OKG1|"
        const val APP="!OKA1|"
        fun parse(text:String):FriendGameWire? {
            if(text.length>140 || !text.startsWith(PREFIX))return null
            val p=text.split('|');if(p.size!=5 || !p[1].matches(Regex("[IADNQKX]")) || !p[2].matches(Regex("[0-9a-f]{32}")))return null
            val seq=p[3].takeIf{it.matches(Regex("[0-9]{1,4}"))}?.toIntOrNull() ?: return null
            if(seq>9999)return null
            val type=p[1];val data=p[4]
            val valid=when(type){
                "I","A","D","N"->seq==0 && data.matches(Regex("pt1|pt2|pt3|nb1|fc1|fd1|bj3|p[0-9a-f]{64}"))
                "Q"->seq==9999 && data=="quit"
                "K"->seq>0 && data=="ack"
                "X"->seq>0 && data.length in 1..64 && data.matches(Regex("[A-Za-z0-9_.,:-]+"))
                else->false
            }
            return if(valid)FriendGameWire(type,p[2],seq,data)else null
        }
    }
}
