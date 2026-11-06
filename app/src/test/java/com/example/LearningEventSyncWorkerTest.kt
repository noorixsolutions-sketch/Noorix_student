package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.core.network.NoorixSyncApiService
import com.example.core.network.NoorixSyncClient
import com.example.core.network.SyncBatchRequestDto
import com.example.core.network.SyncBatchResponseDto
import com.example.data.local.AppDatabase
import com.example.data.local.EventEntity
import com.example.data.local.OfflineSyncQueueEntity
import com.example.domain.sync.LearningEventSyncScheduler
import com.example.domain.sync.LearningEventSyncWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LearningEventSyncWorkerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getInstance(context)
        NoorixSyncClient.testApiServiceOverride = null
    }

    @After
    fun tearDown() {
        NoorixSyncClient.testApiServiceOverride = null
    }

    @Test
    fun `worker pushes pending learning events via Retrofit and marks them SYNCED in Room`() = runBlocking {
        val syncQueueDao = db.syncQueueDao()
        val eventDao = db.eventDao()

        // Clear any prior pending items
        val initialPending = syncQueueDao.getPendingItems()
        if (initialPending.isNotEmpty()) {
            syncQueueDao.markSynced(initialPending.map { it.eventId })
            syncQueueDao.clearSynced()
        }

        val testEventId = "evt-test-sync-001"
        eventDao.insertEvent(
            EventEntity(
                eventId = testEventId,
                eventType = "CONCEPT_ASSESSED",
                studentId = "student-test-42",
                courseVersionId = "2025-2027",
                hlcTimestamp = "1700000000000:0001:nodeA",
                physicalMs = 1700000000000L,
                payloadJson = """{"conceptId":"concept:4024:algebra","score":0.9}""",
                provenanceHash = "prov-nodeA"
            )
        )
        syncQueueDao.enqueue(
            OfflineSyncQueueEntity(
                eventId = testEventId,
                eventType = "CONCEPT_ASSESSED",
                hlcTimestamp = "1700000000000:0001:nodeA",
                physicalMs = 1700000000000L,
                payloadJson = """{"conceptId":"concept:4024:algebra","score":0.9}""",
                status = "PENDING"
            )
        )

        var capturedRequest: SyncBatchRequestDto? = null
        var capturedSignature: String? = null

        NoorixSyncClient.testApiServiceOverride = object : NoorixSyncApiService {
            override suspend fun uploadLearningEventsBatch(
                request: SyncBatchRequestDto,
                signature: String,
                authorization: String?
            ): SyncBatchResponseDto {
                capturedRequest = request
                capturedSignature = signature
                return SyncBatchResponseDto(
                    acceptedEventIds = request.events.map { it.eventId },
                    serverHlc = "1700000000500:0001:server"
                )
            }
        }

        val worker = TestListenableWorkerBuilder<LearningEventSyncWorker>(context).build()
        val result = worker.doWork()

        assertTrue("Expected Result.Success but got $result", result is ListenableWorker.Result.Success)
        assertEquals(1, capturedRequest?.events?.size)
        assertEquals(testEventId, capturedRequest?.events?.first()?.eventId)
        assertTrue(!capturedSignature.isNullOrBlank())

        // Verify event is marked SYNCED (no longer pending)
        val remainingPending = syncQueueDao.getPendingItems()
        assertTrue(remainingPending.none { it.eventId == testEventId })
    }

    @Test
    fun `worker returns retry and increments retryCount when Retrofit service fails`() = runBlocking {
        val syncQueueDao = db.syncQueueDao()

        val initialPending = syncQueueDao.getPendingItems()
        if (initialPending.isNotEmpty()) {
            syncQueueDao.markSynced(initialPending.map { it.eventId })
            syncQueueDao.clearSynced()
        }

        val failEventId = "evt-test-retry-002"
        syncQueueDao.enqueue(
            OfflineSyncQueueEntity(
                eventId = failEventId,
                eventType = "MASTERY_UPDATED",
                hlcTimestamp = "1700000001000:0001:nodeA",
                physicalMs = 1700000001000L,
                payloadJson = """{"conceptId":"concept:4024:numbers","score":0.75}""",
                status = "PENDING",
                retryCount = 0
            )
        )

        NoorixSyncClient.testApiServiceOverride = object : NoorixSyncApiService {
            override suspend fun uploadLearningEventsBatch(
                request: SyncBatchRequestDto,
                signature: String,
                authorization: String?
            ): SyncBatchResponseDto {
                throw IOException("Simulated server unreachable")
            }
        }

        val worker = TestListenableWorkerBuilder<LearningEventSyncWorker>(context)
            .setRunAttemptCount(1)
            .build()
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)

        val pendingAfter = syncQueueDao.getPendingItems().first { it.eventId == failEventId }
        assertEquals("PENDING", pendingAfter.status)
        assertEquals(1, pendingAfter.retryCount)

        // Cleanup
        syncQueueDao.markSynced(listOf(failEventId))
        syncQueueDao.clearSynced()
    }

    @Test
    fun `periodic work request requires connected network constraint`() {
        val request = LearningEventSyncScheduler.buildPeriodicWorkRequest(15L)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(request.tags.contains(LearningEventSyncScheduler.SYNC_WORK_TAG))
    }
}
