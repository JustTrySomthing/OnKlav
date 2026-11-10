package app.onklav.home

import android.content.Context
import org.json.JSONObject

/** Logical filing time is allocated once, before insertion, and persisted in the
 * receive journal. Provider dates remain real wall time. Recovery reuses the journal
 * value, so it cannot invent a newer arrival after a later Archive/Trash action. */
internal object IncomingReceiptOrder {
 @Synchronized fun next(c:Context,state:JSONObject,now:Long=System.currentTimeMillis()):Long {
  val prefs=c.getSharedPreferences("incoming-receipt-order",0)
  val threads=state.optJSONObject("threads")
  var previous=prefs.getLong("last",0)
  threads?.keys()?.forEach{k->val t=threads.optJSONObject(k);previous=maxOf(previous,t?.optLong("last_incoming_at")?:0,t?.optLong("read_at")?:0)}
  check(previous<Long.MAX_VALUE){"Receive ordering needs attention; the message was kept for recovery."}
  val next=maxOf(1,now,previous+1)
  check(prefs.edit().putLong("last",next).commit()){"Receive order could not be saved; retry receiving."}
  return next
 }
 fun readTime(c:Context,wall:Long)=maxOf(wall,c.getSharedPreferences("incoming-receipt-order",0).getLong("last",0))
 @Synchronized fun persisted(c:Context,prefs:android.content.SharedPreferences,key:String,state:JSONObject):Long {
  if(prefs.contains(key))return prefs.getLong(key,0).also{check(it>0){"Invalid receive order"}}
  val order=next(c,state)
  check(prefs.edit().putLong(key,order).commit()){"Receive order could not be saved"}
  return order
 }
}
