package com.example.domain.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.BuildConfig
import com.example.core.network.NoorixSyncClient
import com.example.data.local.AppDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.local.StudentRegistryDatabase
import com.example.domain.repository.LearningRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * WorkManager [CoroutineWorker] that periodically queries pending immutable learning
 * events from the Room database (`offline_sync_queue` / `learning_events`) and pushes
 * them to the remote server using the Retrofit-backed [NoorixSyncClient] configured
 * with `BuildConfig.NOORIX_SYNC_BASE_URL`.
 */
class LearningEventSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val syncQueueDao = db.syncQueueDao()
        val eventDao = db.eventDao()
        val auditDao = db.auditDao()

        val pendingItems = syncQueueDao.getPendingItems()
        val nowMs = System.currentTimeMillis()

        if (pendingItems.isEmpty()) {
            LearningEventSyncScheduler.recordWorkerExecution(
                context = applicationContext,
                status = "IDLE_QUEUE_EMPTY",
                message = "Periodic check complete: 0 pending learning events in Room.",
                timestampMs = nowMs
            )
            return@withContext Result.success(
                workDataOf(
                    KEY_SYNCED_COUNT to 0,
                    KEY_PENDING_COUNT to 0,
                    KEY_SUMMARY to "Queue empty — all learning events are synced.",
                    KEY_TIMESTAMP_MS to nowMs
                )
            )
        }

        // Resolve the student ID from the latest queued/stored learning event or active registered student
        val studentId = runCatching {
            eventDao.getAllEvents().lastOrNull()?.studentId
                ?: StudentRegistryDatabase.getInstance(applicationContext)
                    .studentRegistryDao()
                    .getActiveStudent()?.id
        }.getOrNull() ?: "student-001"

        val syncClient = NoorixSyncClient(BuildConfig.NOORIX_SYNC_BASE_URL)
        val uploadResult = syncClient.uploadBatchSuspend(
            studentId = studentId,
            items = pendingItems
        )

        uploadResult.fold(
            onSuccess = { upload ->
                if (upload.acceptedEventIds.isNotEmpty()) {
                    syncQueueDao.markSynced(upload.acceptedEventIds)
                }
                val remaining = (pendingItems.size - upload.acceptedEventIds.size).coerceAtLeast(0)
                val summary = "WorkManager synced ${upload.acceptedEventIds.size} learning event(s) via Retrofit."

                appendAuditLog(
                    auditDao = auditDao,
                    action = "WORKMANAGER_SYNC_UPLOADED",
                    details = summary
                )

                LearningEventSyncScheduler.recordWorkerExecution(
                    context = applicationContext,
                    status = "SUCCEEDED",
                    message = summary,
                    timestampMs = nowMs
                )

                Result.success(
                    workDataOf(
                        KEY_SYNCED_COUNT to upload.acceptedEventIds.size,
                        KEY_PENDING_COUNT to remaining,
                        KEY_SERVER_HLC to (upload.serverHlc ?: ""),
                        KEY_SUMMARY to summary,
                        KEY_TIMESTAMP_MS to nowMs
                    )
                )
            },
            onFailure = { error ->
                runCatching {
                    syncQueueDao.incrementRetryCount(pendingItems.map { it.eventId })
                }
                val errMsg = error.message ?: "Sync endpoint unreachable"
                val details = "WorkManager sync attempt #${runAttemptCount + 1} deferred (${pendingItems.size} pending): $errMsg"

                appendAuditLog(
                    auditDao = auditDao,
                    action = "WORKMANAGER_SYNC_DEFERRED",
                    details = details
                )

                LearningEventSyncScheduler.recordWorkerExecution(
                    context = applicationContext,
                    status = if (runAttemptCount < MAX_RUN_ATTEMPTS) "RETRY_SCHEDULED" else "FAILED",
                    message = details,
                    timestampMs = nowMs
                )

                if (runAttemptCount < MAX_RUN_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure(
                        workDataOf(
                            KEY_ERROR to errMsg,
                            KEY_PENDING_COUNT to pendingItems.size,
                            KEY_SUMMARY to details,
                            KEY_TIMESTAMP_MS to nowMs
                        )
                    )
                }
            }
        )
    }

    private suspend fun appendAuditLog(
        auditDao: com.example.data.local.AuditDao,
        action: String,
        details: String
    ) {
        runCatching {
            val prevHash = auditDao.getAllAudits().firstOrNull()?.verificationHash
                ?: LearningRepository.AUDIT_GENESIS_HASH
            val timestampMs = System.currentTimeMillis()
            val hash = LearningRepository.auditChainHash(action, details, timestampMs, prevHash)
            auditDao.insertAudit(
                AuditLogEntity(
                    auditId = "audit-" + UUID.randomUUID().toString(),
                    action = action,
                    timestampMs = timestampMs,
                    details = details,
                    verificationHash = hash,
                    prevHash = prevHash
                )
            )
        }
    }

    companion object {
        const val KEY_SYNCED_COUNT = "synced_count"
        const val KEY_PENDING_COUNT = "pending_count"
        const val KEY_SERVER_HLC = "server_hlc"
        const val KEY_SUMMARY = "summary"
        const val KEY_ERROR = "error"
        const val KEY_TIMESTAMP_MS = "timestamp_ms"
        const val MAX_RUN_ATTEMPTS = 5
    }
}

data class PeriodicSyncStatusSnapshot(
    val isPeriodicEnabled: Boolean = true,
    val intervalMinutes: Long = LearningEventSyncScheduler.DEFAULT_REPEAT_INTERVAL_MINUTES,
    val lastStatus: String = "SCHEDULED",
    val lastMessage: String = "Periodic WorkManager task active (every 15 min when connected).",
    val lastAttemptTimestampMs: Long = 0L
)

/**
 * Schedules and manages the periodic and on-demand WorkManager tasks that push pending
 * learning events from Room to `NOORIX_SYNC_BASE_URL` via Retrofit.
 */
object LearningEventSyncScheduler {

    const val UNIQUE_PERIODIC_WORK_NAME = "noorix_periodic_learning_event_sync"
    const val UNIQUE_IMMEDIATE_WORK_NAME = "noorix_immediate_learning_event_sync"
    const val SYNC_WORK_TAG = "noorix_learning_event_sync_tag"
    const val DEFAULT_REPEAT_INTERVAL_MINUTES = 15L

    private const val PREFS_NAME = "noorix_workmanager_sync_prefs"
    private const val KEY_ENABLED = "periodic_sync_enabled"
    private const val KEY_INTERVAL_MIN = "periodic_sync_interval_min"
    private const val KEY_LAST_STATUS = "last_worker_status"
    private const val KEY_LAST_MESSAGE = "last_worker_message"
    private const val KEY_LAST_TIMESTAMP = "last_worker_timestamp_ms"

    /**
     * Builds the [PeriodicWorkRequest] with a `NetworkType.CONNECTED` constraint and
     * exponential backoff policy.
     */
    fun buildPeriodicWorkRequest(
        repeatIntervalMinutes: Long = DEFAULT_REPEAT_INTERVAL_MINUTES
    ): PeriodicWorkRequest {
        val safeInterval = repeatIntervalMinutes.coerceAtLeast(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS / 60_000L)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        return PeriodicWorkRequestBuilder<LearningEventSyncWorker>(
            safeInterval,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag(SYNC_WORK_TAG)
            .build()
    }

    /**
     * Enqueues the periodic WorkManager sync task. Safe to call on every app startup
     * (`ExistingPeriodicWorkPolicy.KEEP` preserves the existing schedule unless
     * [replaceExisting] is true).
     */
    fun schedulePeriodicSync(
        context: Context,
        repeatIntervalMinutes: Long = DEFAULT_REPEAT_INTERVAL_MINUTES,
        replaceExisting: Boolean = false
    ) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putLong(KEY_INTERVAL_MIN, repeatIntervalMinutes.coerceAtLeast(15L))
            .apply()

        runCatching {
            val request = buildPeriodicWorkRequest(repeatIntervalMinutes)
            val policy = if (replaceExisting) {
                ExistingPeriodicWorkPolicy.UPDATE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            }
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                policy,
                request
            )
        }
    }

    /**
     * Cancels the periodic background sync schedule.
     */
    fun cancelPeriodicSync(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .putString(KEY_LAST_STATUS, "PAUSED")
            .putString(KEY_LAST_MESSAGE, "Periodic background sync paused by user.")
            .apply()

        runCatching {
            WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
        }
    }

    /**
     * Triggers an immediate one-shot WorkManager execution of [LearningEventSyncWorker].
     */
    fun enqueueImmediateSync(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<LearningEventSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(SYNC_WORK_TAG)
                .build()

            WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }
    }

    /**
     * Observes the [WorkInfo] list for the periodic sync task.
     */
    fun observePeriodicWorkInfos(context: Context): Flow<List<WorkInfo>> {
        return runCatching {
            WorkManager.getInstance(context.applicationContext)
                .getWorkInfosForUniqueWorkFlow(UNIQUE_PERIODIC_WORK_NAME)
        }.getOrElse { emptyFlow() }
    }

    /**
     * Observes all [WorkInfo] instances tagged with [SYNC_WORK_TAG].
     */
    fun observeAllSyncWorkInfos(context: Context): Flow<List<WorkInfo>> {
        return runCatching {
            WorkManager.getInstance(context.applicationContext)
                .getWorkInfosByTagFlow(SYNC_WORK_TAG)
        }.getOrElse { emptyFlow() }
    }

    fun recordWorkerExecution(
        context: Context,
        status: String,
        message: String,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_STATUS, status)
            .putString(KEY_LAST_MESSAGE, message)
            .putLong(KEY_LAST_TIMESTAMP, timestampMs)
            .apply()
    }

    fun loadStatusSnapshot(context: Context): PeriodicSyncStatusSnapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return PeriodicSyncStatusSnapshot(
            isPeriodicEnabled = prefs.getBoolean(KEY_ENABLED, true),
            intervalMinutes = prefs.getLong(KEY_INTERVAL_MIN, DEFAULT_REPEAT_INTERVAL_MINUTES),
            lastStatus = prefs.getString(KEY_LAST_STATUS, "SCHEDULED") ?: "SCHEDULED",
            lastMessage = prefs.getString(
                KEY_LAST_MESSAGE,
                "Periodic WorkManager task active (every 15 min when connected)."
            ) ?: "Periodic WorkManager task active (every 15 min when connected).",
            lastAttemptTimestampMs = prefs.getLong(KEY_LAST_TIMESTAMP, 0L)
        )
    }
}
