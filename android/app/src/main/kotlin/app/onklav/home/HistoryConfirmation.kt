package app.onklav.home

import android.content.Context
import android.net.Uri
import java.util.concurrent.atomic.AtomicBoolean

/** Ephemeral host review, never portable authorization or a guest capability. */
internal class HistoryConfirmation private constructor(
    val uri:Uri,val review:HistoryImport.Review,val identities:List<String>,private val root:String,val resume:HistoryResume.Binding?
) {
    private val finished=AtomicBoolean(false)
    fun cancel(){finished.set(true)}
    internal fun checkIdentities(c:Context){
        check(c.filesDir.absolutePath==root&&HistoryRecovery.configuration(c)==identities){"Local phone identities changed. Review the restore again."}
    }
    internal fun consume(c:Context){
        check(!finished.get()){"This restore review has ended. Compare and review again."}
        checkIdentities(c)
        check(finished.compareAndSet(false,true)){"This restore review has already been used."}
    }
    companion object {
        fun prepare(c:Context,uri:Uri,review:HistoryImport.Review,resume:HistoryResume.Binding?=null):HistoryConfirmation=HistoryRestore.exclusive {
            require(uri.scheme=="content"){"Choose a backup using Files"}
            val comparison=review.comparison ?: error("Compare the checked backup with phone history first")
            require(review.sha256.matches(Regex("[a-f0-9]{64}"))&&comparison.planSha.matches(Regex("[a-f0-9]{64}"))){"Compare the backup again"}
            require(comparison.fresh in 0..1000&&listOf(comparison.matched,comparison.needsReview,comparison.skipped,comparison.flagsMapped,comparison.flagsDeferred,comparison.threadsMapped,comparison.threadsDeferred).all{it>=0}){"Restore exceeds the reviewed batch limits"}
            val identities=HistoryRecovery.configuration(c)
            require(review.mms==0||identities.isNotEmpty()){"Save your local phone identities in Restore settings and recovery before reviewing an MMS restore."}
            resume?.let{check(it==HistoryResume.binding(c,it.id)&&it.archiveSha==review.sha256&&review.resumePending==it.pendingSources){"Saved restore record changed. Reopen it again."}}
            HistoryConfirmation(uri,review,java.util.Collections.unmodifiableList(identities.toList()),c.filesDir.absolutePath,resume)
        }
    }
}
