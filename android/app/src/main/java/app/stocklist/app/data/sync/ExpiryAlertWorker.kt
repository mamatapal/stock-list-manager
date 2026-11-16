package app.stocklist.app.data.sync

import app.stocklist.app.core.i18n.tr

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.stocklist.app.MainActivity
import app.stocklist.app.R
import app.stocklist.app.core.session.SessionState
import app.stocklist.app.core.session.SessionStore
import app.stocklist.app.data.repository.BatchRepository
import app.stocklist.app.domain.catalogue.ExpiryCalculator
import app.stocklist.app.domain.permission.Permission
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Once a day: if anything has expired or expires within 7 days, shows one notification with the counts. Runs from
 * the phone's own copy of the data, so it also works offline. Only for users with EXPIRY_VIEW who allowed
 * notifications; the notification holds counts only, never item or supplier names.
 */
@HiltWorker
class ExpiryAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val batches: BatchRepository,
    private val session: SessionStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val state = session.state.first { it !is SessionState.Loading }
        val user = (state as? SessionState.SignedIn)?.user ?: return Result.success()
        if (!user.isOwner && Permission.EXPIRY_VIEW !in Permission.fromCodes(user.permissions)) return Result.success()
        if (!canNotify(applicationContext)) return Result.success()
        val today = LocalDate.now()
        val expired = batches.count(ExpiryCalculator.Filter.EXPIRED, today)
        val soon = batches.count(ExpiryCalculator.Filter.WITHIN_7, today)
        if (expired + soon == 0) return Result.success()
        show(applicationContext, expired, soon)
        return Result.success()
    }

    companion object {
        const val CHANNEL = "expiry"
        private const val NOTIFICATION_ID = 7

        fun canNotify(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

        fun text(expired: Int, soon: Int): String = listOfNotNull(
            expired.takeIf { it > 0 }?.let { tr("%1\$s batch(es) expired", it) },
            soon.takeIf { it > 0 }?.let { tr("%1\$s expire within 7 days", it) },
        ).joinToString(" · ")

        @android.annotation.SuppressLint("MissingPermission") // checked in canNotify
        fun show(context: Context, expired: Int, soon: Int) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Expiry reminders"), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(tr("Expiry alert"))
                .setContentText(text(expired, soon))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        }
    }
}
