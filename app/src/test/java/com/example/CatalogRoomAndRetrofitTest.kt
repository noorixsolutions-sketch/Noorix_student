package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.cloud.ArtifactAvailabilityState
import com.example.data.cloud.CatalogRoomRepository
import com.example.data.cloud.CloudArtifactRecord
import com.example.data.cloud.NoorixArtifactType
import com.example.data.cloud.network.ArtifactSchemaDto
import com.example.data.cloud.network.CatalogApiService
import com.example.data.cloud.network.CatalogRetrofitClient
import com.example.data.cloud.network.CatalogSchemaDto
import com.example.data.cloud.network.ClassSchemaDto
import com.example.data.cloud.network.SubjectSchemaDto
import com.example.data.cloud.network.TopicSchemaDto
import com.example.data.cloud.network.toDomain
import com.example.data.local.CatalogCacheDatabase
import com.example.data.local.DownloadedArtifactMetadataEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogRoomAndRetrofitTest {

    private lateinit var context: Context
    private lateinit var db: CatalogCacheDatabase
    private lateinit var repository: CatalogRoomRepository

    private val sampleCatalogJson = """
    {
      "schemaVersion": "1.0",
      "catalogVersion": "2026.11.01",
      "curriculumId": "cambridge",
      "publishedAt": "2026-11-01T10:00:00Z",
      "classes": [
        {
          "classId": "grade-9",
          "className": "Grade 9 (O-Level Year 1)",
          "subjects": [
            {
              "subjectId": "mathematics-4024",
              "subjectName": "Mathematics D",
              "topics": [
                {
                  "topicId": "concept:4024:factorisation",
                  "topicName": "Algebraic Factorisation",
                  "artifacts": {
                    "studyPack": {
                      "status": "available",
                      "artifactId": "math-4024-factorisation-pack",
                      "version": "1.2",
                      "format": "pdf",
                      "downloadUrl": "https://storage.googleapis.com/noorix/study-pack.pdf",
                      "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                      "sizeBytes": 1200000
                    },
                    "quiz": {
                      "status": "available",
                      "artifactId": "math-4024-factorisation-quiz",
                      "version": "1.0",
                      "format": "json",
                      "downloadUrl": "https://storage.googleapis.com/noorix/quiz.json",
                      "sha256": "4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a"
                    },
                    "video": {
                      "status": "available",
                      "videoId": "1kfq0aR3ASs",
                      "channelId": "UC_NOORIX_OFFICIAL_CHANNEL",
                      "title": "Factorisation Explained",
                      "durationSeconds": 420,
                      "embedAllowed": true
                    },
                    "podcast": {
                      "status": "coming_soon"
                    }
                  }
                }
              ]
            }
          ]
        }
      ]
    }
    """.trimIndent()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CatalogCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CatalogRoomRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // 1. Room caching of catalog JSON
    @Test
    fun `saveCatalog caches catalog JSON in Room and exposes via Flow and direct getters`() = runBlocking {
        assertNull(repository.getCachedCatalog())

        val savedCatalog = repository.saveCatalog(
            catalogJson = sampleCatalogJson,
            etag = "etag-v1",
            sourceUrl = "https://example.com/catalog.json"
        )

        assertEquals("2026.11.01", savedCatalog.catalogVersion)
        assertEquals("cambridge", savedCatalog.curriculumId)

        // Verify direct getter from Room
        val fromDb = repository.getCachedCatalog()
        assertNotNull(fromDb)
        assertEquals("2026.11.01", fromDb?.catalogVersion)
        assertEquals("etag-v1", repository.getCachedEtag())
        assertEquals(sampleCatalogJson, repository.getCachedCatalogJson())

        // Verify Flow emits cached catalog
        val flowValue = repository.cachedCatalogFlow.first()
        assertNotNull(flowValue)
        assertEquals("cambridge", flowValue?.curriculumId)
    }

    // 2. Offline curriculum topic structure queryable in Room
    @Test
    fun `saveCatalog extracts and persists curriculum topics for fast offline access`() = runBlocking {
        repository.saveCatalog(
            catalogJson = sampleCatalogJson,
            etag = "etag-v1",
            sourceUrl = "https://example.com/catalog.json"
        )

        val topicsForGrade9 = repository.getOfflineCurriculumTopics("Grade 9")
        assertEquals(1, topicsForGrade9.size)
        val topic = topicsForGrade9.first()
        assertEquals("concept:4024:factorisation", topic.topicId)
        assertEquals("Algebraic Factorisation", topic.topicName)
        assertEquals("mathematics-4024", topic.subjectId)
        assertEquals("Mathematics D", topic.subjectName)
        assertTrue(topic.availableArtifactTypes.contains("STUDY_PACK"))
        assertTrue(topic.availableArtifactTypes.contains("QUIZ"))
        assertTrue(topic.availableArtifactTypes.contains("VIDEO"))
        assertTrue(topic.comingSoonArtifactTypes.contains("PODCAST"))
    }

    // 3. Storing and querying downloaded artifact metadata in Room
    @Test
    fun `recordDownloadedArtifact stores metadata and tracks storage bytes and counts`() = runBlocking {
        val tempFile = File(context.cacheDir, "test-pack.pdf").apply {
            writeText("dummy pdf payload content")
        }

        val record = CloudArtifactRecord(
            artifactType = NoorixArtifactType.STUDY_PACK,
            status = ArtifactAvailabilityState.AVAILABLE,
            artifactId = "math-4024-factorisation-pack",
            version = "1.2",
            format = "pdf",
            downloadUrl = "https://example.com/study-pack.pdf",
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sizeBytes = tempFile.length(),
            title = "Factorisation Study Pack"
        )

        // Initially not downloaded
        assertFalse(repository.isArtifactDownloaded(record))

        val metadata = repository.recordDownloadedArtifact(
            record = record,
            file = tempFile,
            topicId = "concept:4024:factorisation",
            subjectId = "mathematics-4024",
            curriculumId = "cambridge"
        )

        assertEquals("math-4024-factorisation-pack", metadata.artifactId)
        assertEquals("STUDY_PACK", metadata.artifactType)
        assertEquals(tempFile.absolutePath, metadata.localFilePath)

        // Now marked as downloaded
        assertTrue(repository.isArtifactDownloaded(record))

        // Query by artifactId
        val retrieved = repository.getDownloadedArtifact("math-4024-factorisation-pack")
        assertNotNull(retrieved)
        assertEquals(tempFile.length(), retrieved?.fileSizeBytes)

        // Query by topicId
        val byTopic = repository.getDownloadedArtifactsForTopic("concept:4024:factorisation")
        assertEquals(1, byTopic.size)

        // Observe count and storage
        val count = repository.downloadedArtifactsCountFlow.first()
        assertEquals(1, count)
        val storage = repository.totalStorageBytesFlow.first()
        assertEquals(tempFile.length(), storage)

        // Delete artifact metadata
        repository.removeDownloadedArtifact("math-4024-factorisation-pack")
        assertNull(repository.getDownloadedArtifact("math-4024-factorisation-pack"))
        assertFalse(repository.isArtifactDownloaded(record))
    }

    // 4. Retrofit Data Model serialization / deserialization mapping
    @Test
    fun `CatalogSchemaDto parses from JSON and maps cleanly to domain PublishedContentCatalog`() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(CatalogSchemaDto::class.java)

        val dto = adapter.fromJson(sampleCatalogJson)
        assertNotNull(dto)
        assertEquals("1.0", dto?.schemaVersion)
        assertEquals("2026.11.01", dto?.catalogVersion)
        assertEquals("cambridge", dto?.curriculumId)
        assertEquals(1, dto?.classes?.size)

        val domain = dto!!.toDomain("etag-test-999")
        assertEquals("2026.11.01", domain.catalogVersion)
        assertEquals("etag-test-999", domain.etag)
        assertEquals(1, domain.classes.size)

        val topic = domain.classes[0].subjects[0].topics[0]
        assertEquals("concept:4024:factorisation", topic.topicId)
        assertEquals("Algebraic Factorisation", topic.topicName)

        val studyPack = topic.artifacts[NoorixArtifactType.STUDY_PACK]
        assertNotNull(studyPack)
        assertEquals(ArtifactAvailabilityState.AVAILABLE, studyPack?.status)
        assertEquals("math-4024-factorisation-pack", studyPack?.artifactId)

        val video = topic.artifacts[NoorixArtifactType.VIDEO]
        assertNotNull(video)
        assertEquals("1kfq0aR3ASs", video?.videoId)
        assertEquals(420, video?.durationSeconds)

        val podcast = topic.artifacts[NoorixArtifactType.PODCAST]
        assertNotNull(podcast)
        assertEquals(ArtifactAvailabilityState.COMING_SOON, podcast?.status)
    }

    // 5. Retrofit service definition with ETag header conditional caching test
    @Test
    fun `CatalogApiService definition supports conditional GET with If-None-Match ETag`() = runBlocking {
        // Mock implementation of CatalogApiService verifying ETag contract
        val mockApi = object : CatalogApiService {
            private fun build304(url: String): okhttp3.Response {
                return okhttp3.Response.Builder()
                    .code(304)
                    .message("Not Modified")
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .request(okhttp3.Request.Builder().url(url).build())
                    .build()
            }

            override suspend fun getCatalog(
                url: String,
                ifNoneMatch: String?,
                userAgent: String
            ): Response<CatalogSchemaDto> {
                return if (ifNoneMatch == "W/etag-current") {
                    Response.error("".toResponseBody("application/json".toMediaType()), build304(url))
                } else {
                    val dto = CatalogSchemaDto(
                        schemaVersion = "1.0",
                        catalogVersion = "2026.11.05",
                        curriculumId = "cambridge"
                    )
                    Response.success(200, dto)
                }
            }

            override suspend fun getCatalogRaw(
                url: String,
                ifNoneMatch: String?,
                userAgent: String
            ): Response<ResponseBody> {
                return if (ifNoneMatch == "W/etag-current") {
                    Response.error("".toResponseBody("application/json".toMediaType()), build304(url))
                } else {
                    val body = sampleCatalogJson.toResponseBody("application/json".toMediaType())
                    Response.success(200, body)
                }
            }
        }

        val client = CatalogRetrofitClient(apiServiceOverride = mockApi)

        // 1. Initial request without ETag -> returns HTTP 200 with content
        val initialResponse = client.apiService.getCatalog("https://example.com/catalog.json")
        assertEquals(200, initialResponse.code())
        assertTrue(initialResponse.isSuccessful)
        assertEquals("2026.11.05", initialResponse.body()?.catalogVersion)

        // 2. Conditional request with matching ETag -> returns HTTP 304 Not Modified
        val conditionalResponse = client.apiService.getCatalog(
            url = "https://example.com/catalog.json",
            ifNoneMatch = "W/etag-current"
        )
        assertEquals(304, conditionalResponse.code())
        assertFalse(conditionalResponse.isSuccessful)

        // 3. Raw response conditional check
        val raw304 = client.apiService.getCatalogRaw(
            url = "https://example.com/catalog.json",
            ifNoneMatch = "W/etag-current"
        )
        assertEquals(304, raw304.code())

        val raw200 = client.apiService.getCatalogRaw(
            url = "https://example.com/catalog.json",
            ifNoneMatch = "W/old-etag"
        )
        assertEquals(200, raw200.code())
        assertNotNull(raw200.body())
    }
}
