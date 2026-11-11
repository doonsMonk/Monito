package com.monito.expense.work

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.monito.expense.MainActivity
import com.monito.expense.R
import com.monito.expense.data.AmountFormatter
import com.monito.expense.data.BackupManager
import com.monito.expense.data.Prefs
import com.monito.expense.data.Repository
import com.monito.expense.drive.DriveSync
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object Notifications {
    const val CHANNEL_REMINDER = "reminder"
    const val CHANNEL_BACKUP = "backup"
    const val CHANNEL_RECURRING = "recurring"
    const val ID_REMINDER = 1001
    const val ID_BACKUP = 1002
    const val ID_RECURRING = 1003

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.daily_reminder), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.daily_reminder_notif_channel_description)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BACKUP, context.getString(R.string.backup), NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RECURRING, context.getString(R.string.recurrings), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.recurring_notif_channel_description)
            },
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context, requestCode: Int, action: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            if (action != null) this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun showReminder(context: Context) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.its_time_to_add_today_expenses))
            .setContentIntent(openAppIntent(context, 1, MainActivity.ACTION_ADD_TRANSACTION))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_REMINDER, n)
    }

    /** Tells the user which recurring transactions were just added automatically. */
    fun showRecurringAdded(context: Context, added: List<Repository.AddedRecurrence>) {
        if (added.isEmpty() || !canPost(context)) return
        val currency = Prefs.get(context).current.currency
        val lines = added.map { "${it.note.ifBlank { it.catName }}: ${AmountFormatter.format(it.amount, currency)}" }
        val title = context.resources.getQuantityString(R.plurals.recurring_added_title, added.size, added.size)
        val style = NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach { s.addLine(it) } }
        val n = NotificationCompat.Builder(context, CHANNEL_RECURRING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.joinToString(", "))
            .setStyle(style)
            .setContentIntent(openAppIntent(context, 2))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_RECURRING, n)
    }
}

/** Adds due recurring transactions (with a notification) and purges old trash. */
class RecurrenceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { RecurrenceRunner.run(applicationContext) }
        return Result.success()
    }
}

object RecurrenceRunner {
    /** Shared by the worker and app start-up. */
    fun run(context: Context) {
        val repo = Repository.get(context)
        val added = repo.processDueRecurrencesDetailed()
        if (added.isNotEmpty()) Notifications.showRecurringAdded(context, added)
        if (Prefs.get(context).current.autoDeleteTrash) runCatching { repo.deleteOldTrash() }
    }
}

/** Daily local backup (the original app did this every night at 23:50), then an encrypted upload to Drive if enabled. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = Prefs.get(applicationContext).current
        if (!settings.autoBackup) return Result.success()
        val repo = Repository.get(applicationContext)
        val manager = BackupManager(applicationContext, repo)
        val local = runCatching {
            manager.createBackup(auto = true)
            manager.pruneAutoBackups()
        }
        if (local.isFailure) return Result.retry()
        if (settings.driveAutoUpload) {
            runCatching { DriveSync.uploadSnapshot(applicationContext, interactive = false) }
        }
        return Result.success()
    }
}

/** Shows the daily reminder, then schedules itself again for the next day. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs.get(applicationContext).current
        if (prefs.dailyReminder) {
            Notifications.showReminder(applicationContext)
            Scheduler.scheduleReminder(applicationContext, prefs.reminderTime, fromNow = true)
        }
        return Result.success()
    }
}

object Scheduler {
    private const val WORK_RECURRENCE = "monito_recurrence"
    private const val WORK_BACKUP = "monito_auto_backup"
    private const val WORK_REMINDER = "monito_reminder"

    fun scheduleAll(context: Context) {
        val settings = Prefs.get(context).current
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            WORK_RECURRENCE,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecurrenceWorker>(6, TimeUnit.HOURS).build(),
        )
        scheduleAutoBackup(context, settings.autoBackup)
        if (settings.dailyReminder) scheduleReminder(context, settings.reminderTime) else cancelReminder(context)
    }

    fun runRecurrencesNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "monito_recurrence_now",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RecurrenceWorker>().build(),
        )
    }

    fun scheduleAutoBackup(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_BACKUP)
            return
        }
        val delay = delayUntil(LocalTime.of(23, 50))
        wm.enqueueUniquePeriodicWork(
            WORK_BACKUP,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    fun scheduleReminder(context: Context, time: String, fromNow: Boolean = false) {
        val target = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(22, 0))
        var delay = delayUntil(target)
        if (fromNow && delay.toMinutes() < 1) delay = delay.plusDays(1)
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_REMINDER,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    fun cancelReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_REMINDER)
    }

    private fun delayUntil(time: LocalTime): Duration {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
