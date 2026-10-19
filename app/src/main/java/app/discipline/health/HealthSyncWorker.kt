package app.discipline.health

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.discipline.DisciplineApp
import java.util.concurrent.TimeUnit

/** Drains the Health Connect outbox and refreshes the read cache. */
class HealthSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as DisciplineApp).container
        val hc = container.health
        if (hc.availability() != HealthAvailability.AVAILABLE) return Result.success()
        val (_, failed) = hc.flushOutbox()
        val readDays = inputData.getInt(KEY_READ_DAYS, 0)
        if (readDays > 0) hc.readRecent(readDays)
        return if (failed > 0 && runAttemptCount < 5) Result.retry() else Result.success()
    }

    companion object {
        private const val KEY_READ_DAYS = "read_days"
        private const val ONE_SHOT = "hc-sync-now"
        private const val PERIODIC = "hc-sync-periodic"

        /** Push pending writes soon (debounced by WorkManager's unique-work replacement). */
        fun enqueueWrite(context: Context) = enqueue(context, readDays = 0, delaySeconds = 3)

        /** Push writes and re-read the recent window. */
        fun enqueueFull(context: Context, readDays: Int = 7) = enqueue(context, readDays, 0)

        private fun enqueue(context: Context, readDays: Int, delaySeconds: Long) {
            val req = OneTimeWorkRequestBuilder<HealthSyncWorker>()
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_READ_DAYS to readDays))
                .build()
            val policy = if (readDays > 0) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniqueWork(if (readDays > 0) "$ONE_SHOT-full" else ONE_SHOT, policy, req)
        }

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<HealthSyncWorker>(1, TimeUnit.HOURS)
                .setInputData(workDataOf(KEY_READ_DAYS to 3))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
