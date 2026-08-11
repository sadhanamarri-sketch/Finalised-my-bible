package com.example.mybible.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import java.util.concurrent.TimeUnit

/**
 * Daily background counterpart to [MainViewModel.backupToDrive] — same
 * download-merge-upload flow (see that function's doc for why the merge
 * happens before uploading), but silent: it can't launch UI, so if Drive
 * needs the user to (re-)consent to the appdata scope, this run just does
 * nothing and the next scheduled run tries again. The manual "Back up" /
 * "Restore" buttons in Settings remain the way to resolve a stuck consent
 * prompt immediately instead of waiting a day.
 *
 * Requires the user to already be signed in to Drive (checked via
 * [DriveBackupManager.getLastSignedInAccount]) — this worker never shows
 * the sign-in picker itself.
 */
class DriveSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = BibleRepository(applicationContext)
        val driveBackupManager = DriveBackupManager(applicationContext)

        // Respect the toggle even if a stale periodic request somehow
        // survives being cancelled (e.g. an OS-level scheduling quirk) —
        // belt-and-suspenders against a silent background upload the user
        // turned off.
        if (!repository.getAutoBackupEnabled()) return Result.success()

        val account = driveBackupManager.getLastSignedInAccount()
            ?: return Result.success() // Not signed in — nothing to do, not an error.

        val downloadResult = driveBackupManager.downloadBackup(account)
        when (downloadResult) {
            is DriveBackupManager.RestoreResult.Success -> {
                try {
                    repository.importFromBackup(downloadResult.json)
                } catch (e: Exception) {
                    // Remote copy unreadable — proceed with a local-only
                    // backup rather than failing the whole sync on it.
                }
            }
            is DriveBackupManager.RestoreResult.NeedsConsent -> {
                // Can't show the consent dialog from the background.
                // Retry on the next scheduled run.
                return Result.success()
            }
            is DriveBackupManager.RestoreResult.NotFound -> {
                // First backup ever for this account — no merge needed.
            }
            is DriveBackupManager.RestoreResult.Failure -> {
                // Couldn't reach Drive for the pre-upload merge check.
                // Let WorkManager retry with backoff rather than silently
                // uploading over a remote copy we never actually checked.
                return Result.retry()
            }
        }

        val json = repository.exportBackupJson()
        return when (val result = driveBackupManager.uploadBackup(account, json)) {
            is DriveBackupManager.BackupResult.Success -> {
                repository.setLastDriveBackupAt(System.currentTimeMillis())
                Result.success()
            }
            is DriveBackupManager.BackupResult.NeedsConsent -> Result.success() // retry next scheduled run
            is DriveBackupManager.BackupResult.Failure -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "drive_auto_backup"

        /** Schedules (or reschedules) the daily sync. Safe to call
         * repeatedly — [ExistingPeriodicWorkPolicy.UPDATE] replaces any
         * existing request with these constraints rather than stacking
         * duplicates.
         *
         * No Wi-Fi-only or battery-not-low constraint: the backup payload
         * is plain JSON, typically just a few KB even with a lot of notes,
         * so metered data / low battery aren't meaningfully impacted.
         * Still requires *some* network connection — an upload obviously
         * can't happen fully offline. */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Cancels the daily sync — called when the user turns off
         * Settings > Auto backup, or signs out of Drive. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
