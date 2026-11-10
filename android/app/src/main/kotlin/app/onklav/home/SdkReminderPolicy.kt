package app.onklav.home

import org.json.JSONObject

/** Repeats follow the original absolute cadence; recovery never floods old occurrences. */
internal object SdkReminderPolicy {
 fun validate(a:JSONObject) {
  require(a.optString("missed","latest") in setOf("latest","skip"))
  for((key,range) in listOf("repeat_seconds" to 60L..2592000L,"repeat_count" to 1L..100L,"snooze_seconds" to 60L..86400L)) {
   if(a.has(key)){val n=a.getLong(key);require(n in range && a.get(key).toString()==n.toString())}
  }
  require(a.has("repeat_seconds")==a.has("repeat_count"))
 }
 // Android jobs are inexact. A reminder is missed after a five-minute grace period.
 fun skip(a:JSONObject,due:Long,now:Long)=a.optString("missed","latest")=="skip" && now-due>300000L
 fun next(a:JSONObject,due:Long,now:Long):Pair<Long,Int>? {
  val remaining=a.optInt("repeat_count",1);if(remaining<=1)return null
  val interval=Math.multiplyExact(a.getLong("repeat_seconds"),1000L)
  val steps=((now-due).coerceAtLeast(0)/interval)+1
  if(steps>=remaining)return null
  return Math.addExact(due,Math.multiplyExact(steps,interval)) to (remaining-steps.toInt())
 }
}
