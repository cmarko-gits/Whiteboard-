package dev.whiteboard.sync

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.whiteboard.core.cloud.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class AndroidSyncScheduler @Inject constructor(@ApplicationContext private val context: Context, @CloudOrigin private val origin: String) :
    SyncScheduler {
    private val manager get() = WorkManager.getInstance(context)
    override fun enqueue(accountId: String) {
        // Append rather than dropping a wake-up that arrives while a preceding pass is finishing.
        manager.enqueueUniqueWork(name(accountId), ExistingWorkPolicy.APPEND_OR_REPLACE, request(accountId, origin))
    }
    override fun cancel(accountId: String) {
        manager.cancelUniqueWork(name(accountId))
    }
    override fun observe(accountId: String) = manager.getWorkInfosForUniqueWorkFlow(name(accountId)).map { infos ->
        when {
            infos.any { it.state == WorkInfo.State.RUNNING } -> SyncWorkStatus(SyncWorkState.RUNNING)
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } -> SyncWorkStatus(SyncWorkState.WAITING)
            else -> {
                val latest = infos.maxByOrNull { info -> info.tags.mapNotNull { it.removePrefix(ORDER).toLongOrNull() }.maxOrNull() ?: 0L }
                when (latest?.state) {
                    WorkInfo.State.FAILED -> SyncWorkStatus(
                        SyncWorkState.NEEDS_ATTENTION,
                        latest.outputData.getString(REASON)?.let { value -> SyncStopReason.entries.firstOrNull { it.name == value } }
                    )
                    WorkInfo.State.SUCCEEDED -> SyncWorkStatus(SyncWorkState.COMPLETED)
                    else -> SyncWorkStatus(SyncWorkState.IDLE)
                }
            }
        }
    }
    companion object {
        internal const val ACCOUNT = "account"
        internal const val ORIGIN = "origin"
        internal const val REASON = "reason"
        private const val ORDER = "sync-enqueued:"
        private fun name(id: String) = "whiteboard-cloud-sync:$id"
        private fun request(id: String, origin: String) = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setInputData(workDataOf(ACCOUNT to id, ORIGIN to origin)).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(ORDER + System.currentTimeMillis()).build()
        internal fun append(context: Context, id: String, origin: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(name(id), ExistingWorkPolicy.APPEND_OR_REPLACE, request(id, origin))
        }
    }
}
