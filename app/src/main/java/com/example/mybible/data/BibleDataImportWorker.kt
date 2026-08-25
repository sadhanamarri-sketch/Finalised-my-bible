package com.example.mybible.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.mybible.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull

/**
 * Runs [BibleDataInitializer.ensureImported] as WorkManager foreground
 * work instead of a plain viewModelScope coroutine (how this used to run,
 * directly inside MainViewModel's init) — a bare coroutine tied to the
 * ViewModel offered no protection against the OS reclaiming a background
 * process under memory pressure, or a user swiping the app away from
 * Recents, either of which could silently cut the one-time KJV/Greek/
 * Hebrew/cross-reference download short partway through.
 *
 * BibleDataInitializer is a process-wide singleton (see its getInstance),
 * so this worker's progress/errorMessage updates are the exact same
 * StateFlow instances MainViewModel already exposes to the UI as
 * importProgress/importError — the existing progress banner needs no
 * changes to keep working whether the import is currently driven by this
 * worker or was already finished before it ran.
 */
class BibleDataImportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())
        val repository = BibleRepository(applicationContext)
        when {
            inputData.getBoolean(KEY_FORCE_GREEK_HEBREW_REIMPORT, false) ->
                repository.dataInitializer.forceReimportGreekAndHebrew()
            inputData.getBoolean(KEY_FORCE_RETRY, false) -> repository.dataInitializer.retry()
            else -> repository.dataInitializer.ensureImported()
        }
        return Result.success()
    }

    // IMPORTANCE_LOW: a shade entry, not a heads-up interruption — this is
    // routine one-time setup, not something that needs to grab attention
    // the way a reading reminder does.
    private fun createForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager?.getNotificationChannel(CHANNEL_ID) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Bible data setup", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Setting up My Bible")
            .setContentText("Preparing Bible text and study data…")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val WORK_NAME = "bible_data_import"
        private const val CHANNEL_ID = "bible_data_import"
        private const val NOTIFICATION_ID = 4821
        private const val KEY_FORCE_RETRY = "force_retry"
        private const val KEY_FORCE_GREEK_HEBREW_REIMPORT = "force_greek_hebrew_reimport"

        /** Safe to call on every app launch — KEEP leaves a completed or
         *  already-in-progress import alone rather than restarting it. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<BibleDataImportWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        /** Used by the import error banner's "Retry" action — always starts
         *  a fresh run (REPLACE), forcing BibleDataInitializer.retry()
         *  instead of ensureImported() so a step already marked "attempted
         *  this session" actually re-attempts instead of no-op'ing. */
        fun enqueueRetry(context: Context) {
            val request = OneTimeWorkRequestBuilder<BibleDataImportWorker>()
                .setInputData(workDataOf(KEY_FORCE_RETRY to true))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /** Settings' "Re-check Greek/Hebrew data" action — see
         *  BibleDataInitializer.forceReimportGreekAndHebrew's doc for why
         *  this needs to be distinct from [enqueueRetry]: retry() only
         *  re-attempts a step that never finished, while this wipes both
         *  tables first so a step that finished against a now-outdated
         *  upstream snapshot gets a genuine from-scratch resync. */
        fun enqueueGreekHebrewReimport(context: Context) {
            val request = OneTimeWorkRequestBuilder<BibleDataImportWorker>()
                .setInputData(workDataOf(KEY_FORCE_GREEK_HEBREW_REIMPORT to true))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /** Suspends until the most recently enqueued run reaches a terminal
         *  state — used by MainViewModel to know when to refresh the
         *  chapter on screen with freshly-imported Room data, the same
         *  "wait until done" shape the old direct ensureImported() call
         *  had. Resolves immediately if that run already finished before
         *  this was called (e.g. on a later app launch). */
        suspend fun awaitCompletion(context: Context) {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(WORK_NAME)
                .mapNotNull { it.firstOrNull() }
                .first { it.state.isFinished }
        }
    }
}
