package app.stocklist.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Background sync: right after each change (when online), every 15 minutes, and on app start. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: SyncRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (sync.syncNow()) Result.success() else Result.retry()
}

@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun requestNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun startPeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        // Daily expiry check; it does nothing unless the user allowed notifications.
        val expiry = PeriodicWorkRequestBuilder<ExpiryAlertWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(EXPIRY, ExistingPeriodicWorkPolicy.KEEP, expiry)
    }

    fun stop() {
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(context).cancelUniqueWork(EXPIRY)
    }

    private companion object {
        const val NOW = "sync-now"
        const val PERIODIC = "sync-periodic"
        const val EXPIRY = "expiry-daily"
    }
}
