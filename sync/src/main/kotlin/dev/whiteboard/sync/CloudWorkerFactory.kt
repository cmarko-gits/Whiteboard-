package dev.whiteboard.sync

import android.content.Context
import androidx.work.*
import dev.whiteboard.core.cloud.*
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.*

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CloudOrigin

class CloudWorkerFactory @Inject constructor(
    private val accounts: AccountGateway,
    private val runner: BackgroundSyncRunner,
    @CloudOrigin private val origin: String
) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName ==
            CloudSyncWorker::class.java.name
        ) {
            CloudSyncWorker(appContext, workerParameters, accounts, runner, origin)
        } else {
            null
        }
}

class CloudSyncWorker(
    context: Context,
    parameters: WorkerParameters,
    private val accounts: AccountGateway,
    private val runner: BackgroundSyncRunner,
    private val origin: String
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(AndroidSyncScheduler.ACCOUNT) ?: return stopped(SyncStopReason.ACCOUNT)
        if (origin.isBlank() || inputData.getString(AndroidSyncScheduler.ORIGIN) != origin) return stopped(SyncStopReason.UNAVAILABLE)
        return try {
            val pass = withTimeout(180_000) {
                if (accounts.account.value == null) accounts.restore()
                if (accounts.account.value?.id != id) throw CloudFailure(CloudError.CREDENTIALS)
                runner.run(id)
            }
            if (pass.moreWork && accounts.account.value?.id == id) AndroidSyncScheduler.append(applicationContext, id, origin)
            if (pass.needsAttention) stopped(pass.reason ?: SyncStopReason.CONFLICT) else Result.success()
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            retryOrStop(SyncStopReason.SERVER)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: CloudFailure) {
            when {
                error.error == CloudError.CREDENTIALS -> stopped(SyncStopReason.ACCOUNT)
                error.error == CloudError.UNAVAILABLE -> stopped(SyncStopReason.UNAVAILABLE)
                error.code == "tls_validation_failed" -> stopped(SyncStopReason.SERVER)
                error.error == CloudError.OFFLINE || error.error == CloudError.LIMIT ||
                    error.error == CloudError.SERVER && (error.status ?: 500) >= 500 ->
                    retryOrStop(if (error.error == CloudError.LIMIT) SyncStopReason.LIMIT else SyncStopReason.SERVER)
                else -> stopped(SyncStopReason.ACCESS)
            }
        } catch (_: Exception) {
            retryOrStop(SyncStopReason.SERVER)
        }
    }
    private fun retryOrStop(reason: SyncStopReason): Result = if (runAttemptCount < 5) Result.retry() else stopped(reason)
    private fun stopped(reason: SyncStopReason) = Result.failure(workDataOf(AndroidSyncScheduler.REASON to reason.name))
}
