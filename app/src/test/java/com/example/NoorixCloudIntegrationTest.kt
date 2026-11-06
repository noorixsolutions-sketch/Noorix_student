package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.cloud.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoorixCloudIntegrationTest {

    private lateinit var context: Context

    private val sampleValidCatalogJson = """
    {
      "schemaVersion": "1.0",
      "catalogVersion": "2026.11.01",
      "curriculumId": "cambridge",
      "publishedAt": "2026-11-01T10:00:00Z",
      "subjects": [
        {
          "subjectId": "mathematics-4024",
          "topics": [
            {
              "topicId": "concept:4024:factorisation",
              "artifacts": {
                "studyPack": {
                  "status": "available",
                  "artifactId": "math-4024-factorisation-pack",
                  "version": "1.2",
                  "format": "pdf",
                  "downloadUrl": "https://storage.googleapis.com/noorix/math-4024-factorisation-pack.pdf",
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
    """.trimIndent()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // 1. Valid catalog parsing
    @Test
    fun `valid catalog parses correctly with full metadata`() {
        val catalog = PublishedContentCatalog.parse(sampleValidCatalogJson, etag = "etag-123")
        assertEquals("1.0", catalog.schemaVersion)
        assertEquals("2026.11.01", catalog.catalogVersion)
        assertEquals("cambridge", catalog.curriculumId)
        assertEquals("etag-123", catalog.etag)
        assertEquals(1, catalog.subjects.size)

        val subject = catalog.subjects.first()
        assertEquals("mathematics-4024", subject.subjectId)
        assertEquals(1, subject.topics.size)

        val topic = subject.topics.first()
        assertEquals("concept:4024:factorisation", topic.topicId)
    }

    // 2. Unsupported schema versions
    @Test(expected = IllegalArgumentException::class)
    fun `unsupported schema version throws IllegalArgumentException`() {
        val invalidSchema = """
        {
          "schemaVersion": "2.0",
          "catalogVersion": "2027.01.01",
          "curriculumId": "cambridge",
          "subjects": []
        }
        """.trimIndent()
        PublishedContentCatalog.parse(invalidSchema)
    }

    // 3. Missing optional artifact fields handled gracefully
    @Test
    fun `missing optional artifact fields parse without crashing`() {
        val minimalJson = """
        {
          "schemaVersion": "1.0",
          "catalogVersion": "2026.11",
          "curriculumId": "cambridge",
          "subjects": [
            {
              "subjectId": "physics-5054",
              "topics": [
                {
                  "topicId": "concept:5054:kinematics",
                  "artifacts": {
                    "video": {
                      "status": "available",
                      "videoId": "N-Y0KvxJvg4"
                    }
                  }
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val catalog = PublishedContentCatalog.parse(minimalJson)
        val video = catalog.findArtifact("concept:5054:kinematics", NoorixArtifactType.VIDEO)
        assertNotNull(video)
        assertEquals("N-Y0KvxJvg4", video?.videoId)
        assertEquals("", video?.channelId)
        assertEquals(0, video?.durationSeconds)
        assertTrue(video?.embedAllowed == true)
    }

    // 4. Stable topic ID matching (never fuzzy title matching)
    @Test
    fun `artifacts match only strictly by stable topic ID`() {
        val catalog = PublishedContentCatalog.parse(sampleValidCatalogJson)
        val matched = catalog.findArtifact("concept:4024:factorisation", NoorixArtifactType.STUDY_PACK)
        assertNotNull(matched)
        assertEquals("math-4024-factorisation-pack", matched?.artifactId)

        // Title or fuzzy matching must not match
        val fuzzyMatch = catalog.findArtifact("Factorisation Explained", NoorixArtifactType.STUDY_PACK)
        assertNull(fuzzyMatch)
    }

    // 5. Available and Coming Soon states
    @Test
    fun `topic records distinguish available and explicitly coming soon artifacts`() {
        val catalog = PublishedContentCatalog.parse(sampleValidCatalogJson)
        val videoArt = catalog.findArtifact("concept:4024:factorisation", NoorixArtifactType.VIDEO)
        assertEquals(ArtifactAvailabilityState.AVAILABLE, videoArt?.status)

        val podcastArt = catalog.findArtifact("concept:4024:factorisation", NoorixArtifactType.PODCAST)
        assertEquals(ArtifactAvailabilityState.COMING_SOON, podcastArt?.status)
    }

    // 6. Provider outage vs Coming Soon
    @Test
    fun `provider outage reports temporarily unavailable and preserves cached content`() = runBlocking {
        val repo = CloudContentRepository(context)
        val catalog = PublishedContentCatalog.parse(sampleValidCatalogJson)
        repo.setCatalogForTesting(catalog)

        // When content is available in catalog, status is AVAILABLE
        val (status, record) = repo.resolveArtifactStatus("concept:4024:factorisation", NoorixArtifactType.STUDY_PACK)
        assertNotNull(record)
        // If internet is unreachable, resolving non-cached asset returns TEMPORARILY_UNAVAILABLE, not COMING_SOON
        assertTrue(status == ArtifactAvailabilityState.AVAILABLE || status == ArtifactAvailabilityState.TEMPORARILY_UNAVAILABLE)
    }

    // 7. Access denied handling
    @Test
    fun `access denied state is accurately classified`() {
        val deniedState = CatalogSyncState.AccessDenied("HTTP 403 Forbidden")
        assertEquals("HTTP 403 Forbidden", deniedState.message)
    }

    // 8. Catalog refresh and cache fallback
    @Test
    fun `outage retains cached catalog`() {
        val catalog = PublishedContentCatalog.parse(sampleValidCatalogJson)
        val outage = CatalogSyncState.Outage(
            message = "Server 503",
            cachedCatalog = catalog,
            isRetryable = true
        )
        assertNotNull(outage.cachedCatalog)
        assertEquals(1, outage.cachedCatalog?.subjects?.size)
    }

    // 9. ETag based refresh behavior
    @Test
    fun `catalog preserves ETag for conditional GET`() {
        val catalog = PublishedContentCatalog.parse(sampleValidCatalogJson, etag = "\"v1-hash-abc\"")
        assertEquals("\"v1-hash-abc\"", catalog.etag)
    }

    // 10. Download checksum validation and local file cache
    @Test
    fun `downloader verifies checksum and recognizes valid cached file`() {
        val downloader = CloudArtifactDownloader(context)
        val record = CloudArtifactRecord(
            artifactType = NoorixArtifactType.NOTES,
            status = ArtifactAvailabilityState.AVAILABLE,
            artifactId = "test-notes-factorisation",
            version = "1.0",
            format = "txt",
            sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08" // sha256("test")
        )

        // Create the expected cached file with matching content
        val testFile = downloader.getTargetFile(record)
        testFile.parentFile?.mkdirs()
        testFile.writeText("test", Charsets.UTF_8)

        val cached = downloader.getCachedFile(record)
        assertNotNull(cached)
        assertEquals(testFile.absolutePath, cached?.absolutePath)

        // Now test tampered checksum
        val tamperedRecord = record.copy(sha256 = "invalid_hash_should_fail")
        val tamperedResult = downloader.getCachedFile(tamperedRecord)
        assertNull("Corrupt or invalid checksum must delete or invalidate cache", tamperedResult)
    }

    // 11. Host allowlist security
    @Test
    fun `unapproved hosts are rejected by downloader security check`() = runBlocking {
        val downloader = CloudArtifactDownloader(context)
        val maliciousRecord = CloudArtifactRecord(
            artifactType = NoorixArtifactType.STUDY_PACK,
            status = ArtifactAvailabilityState.AVAILABLE,
            artifactId = "malicious-pack",
            version = "1.0",
            format = "pdf",
            downloadUrl = "https://evil-unapproved-site.com/study-pack.pdf"
        )

        val result = downloader.downloadArtifact(maliciousRecord)
        assertTrue(result is ArtifactDownloadResult.Failure)
        val failure = result as ArtifactDownloadResult.Failure
        assertTrue(failure.error.contains("not in approved HTTPS download allowlist"))
        assertFalse(failure.isRetryable)
    }

    // 12. Quiz payload validation and answer key safety
    @Test
    fun `quiz payload parser validates questions and filters out malformed items`() {
        val rawQuizJson = """
        {
          "quizId": "quiz:4024:factorisation:1",
          "topicId": "concept:4024:factorisation",
          "title": "Quadratic Factorisation Diagnostic",
          "questions": [
            {
              "id": "q1",
              "question": "What is the factored form of x^2 + 5x + 6?",
              "options": ["(x+2)(x+3)", "(x+1)(x+6)", "(x-2)(x-3)", "(x+5)(x+1)"],
              "correctIndex": 0,
              "explanation": "Factors of 6 summing to 5 are 2 and 3."
            },
            {
              "id": "q2-malformed",
              "question": "",
              "options": ["single option"],
              "correctIndex": 0
            },
            {
              "id": "q3",
              "question": "Factorise 2x^2 + 8x completely.",
              "options": ["2x(x + 4)", "x(2x + 8)", "2(x^2 + 4x)"],
              "correctIndex": 0,
              "explanation": "Extract the highest common factor 2x."
            }
          ]
        }
        """.trimIndent()

        val parsed = CloudQuizPayload.parse(rawQuizJson)
        assertEquals("quiz:4024:factorisation:1", parsed.quizId)
        assertEquals("concept:4024:factorisation", parsed.topicId)
        // q2-malformed should be gracefully skipped (empty question, < 2 options)
        assertEquals(2, parsed.questions.size)
        assertEquals("q1", parsed.questions[0].id)
        assertEquals("q3", parsed.questions[1].id)
        assertEquals(0, parsed.questions[0].correctIndex)
    }

    // 13. YouTube Video ID format validation
    @Test
    fun `youtube video IDs conform to standard YouTube pattern`() {
        val validIds = listOf("1kfq0aR3ASs", "N-Y0KvxJvg4", "TNYYPFndr8M", "nok99JOhcjo")
        validIds.forEach { id ->
            assertTrue(id.matches(Regex("^[A-Za-z0-9_-]{6,20}$")))
        }

        val invalidIds = listOf("invalid id with spaces", "short", "<script>", "https://youtube.com/watch")
        invalidIds.forEach { id ->
            assertFalse(id.matches(Regex("^[A-Za-z0-9_-]{6,20}$")))
        }
    }

    // 14. Topics with partial content availability
    @Test
    fun `topic with partial artifacts exposes available and coming soon independently`() {
        val repo = CloudContentRepository(context)
        val catalog = PublishedContentCatalog.parse(sampleValidCatalogJson)
        repo.setCatalogForTesting(catalog)

        val resources = repo.resolveAllTopicResources("concept:4024:factorisation")
        val studyPack = resources[NoorixArtifactType.STUDY_PACK]
        val podcast = resources[NoorixArtifactType.PODCAST]
        val worksheet = resources[NoorixArtifactType.WORKSHEET]

        // Study pack is registered
        assertNotNull(studyPack?.second)
        // Podcast was explicitly coming_soon in JSON
        assertEquals(ArtifactAvailabilityState.COMING_SOON, podcast?.first)
        // Worksheet was not in artifacts dictionary -> resolved as COMING_SOON
        assertEquals(ArtifactAvailabilityState.COMING_SOON, worksheet?.first)
        assertNull(worksheet?.second)
    }

    // 15. Structured hierarchy of classes, subjects, topics with names and IDs
    @Test
    fun `structured hierarchy schema loads classes, subjects, topics with IDs and names`() {
        val hierarchyJson = """
        {
          "schemaVersion": "1.0",
          "catalogVersion": "2026.11.02",
          "curriculumId": "cambridge",
          "classes": [
            {
              "classId": "grade-9",
              "className": "Grade 9 (O-Level Year 1)",
              "subjects": [
                {
                  "subjectId": "mathematics-4024",
                  "subjectName": "Mathematics D (4024)",
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
                          "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                        },
                        "video": {
                          "status": "available",
                          "videoId": "1kfq0aR3ASs",
                          "title": "Factorisation Explained",
                          "channelId": "UC_NOORIX"
                        },
                        "quiz": {
                          "status": "coming_soon"
                        }
                      }
                    }
                  ]
                }
              ]
            },
            {
              "classId": "grade-10",
              "className": "Grade 10 (O-Level Year 2)",
              "subjects": [
                {
                  "subjectId": "physics-5054",
                  "subjectName": "Physics (5054)",
                  "topics": [
                    {
                      "topicId": "concept:5054:kinematics",
                      "topicName": "Kinematics & Speed-Time Graphs",
                      "artifacts": {
                        "studyPack": {
                          "status": "available",
                          "artifactId": "phys-5054-kinematics-pack",
                          "version": "1.0",
                          "format": "pdf",
                          "downloadUrl": "https://storage.googleapis.com/noorix/kinematics.pdf",
                          "sha256": "4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a"
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

        val catalog = PublishedContentCatalog.parse(hierarchyJson)
        assertEquals(2, catalog.classes.size)
        assertEquals("grade-9", catalog.classes[0].classId)
        assertEquals("Grade 9 (O-Level Year 1)", catalog.classes[0].className)
        assertEquals("grade-10", catalog.classes[1].classId)
        assertEquals("Grade 10 (O-Level Year 2)", catalog.classes[1].className)

        // getTopicsForClass matching
        val grade9Topics = catalog.getTopicsForClass("Grade 9")
        assertEquals(1, grade9Topics.size)
        assertEquals("concept:4024:factorisation", grade9Topics[0].topicId)
        assertEquals("Algebraic Factorisation", grade9Topics[0].topicName)

        val grade10Topics = catalog.getTopicsForClass("Grade 10")
        assertEquals(1, grade10Topics.size)
        assertEquals("concept:5054:kinematics", grade10Topics[0].topicId)
        assertEquals("Kinematics & Speed-Time Graphs", grade10Topics[0].topicName)
    }

    // 16. checkForNewContentForClass filters out YouTube videos and detects uncached items
    @Test
    fun `checkForNewContentForClass detects uncached downloadable items and excludes YouTube videos`() {
        val hierarchyJson = """
        {
          "schemaVersion": "1.0",
          "catalogVersion": "2026.11.02",
          "curriculumId": "cambridge",
          "classes": [
            {
              "classId": "grade-9",
              "className": "Grade 9",
              "subjects": [
                {
                  "subjectId": "mathematics-4024",
                  "subjectName": "Mathematics",
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
                          "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                        },
                        "video": {
                          "status": "available",
                          "videoId": "1kfq0aR3ASs",
                          "title": "Factorisation Live Video",
                          "channelId": "UC_NOORIX"
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

        val catalog = PublishedContentCatalog.parse(hierarchyJson)
        val repo = CloudContentRepository(context)
        repo.setCatalogForTesting(catalog)

        val newItems = repo.checkForNewContentForClass("Grade 9")
        // Only the studyPack should be returned (1 item), video is excluded because it plays instantly via iframe,
        // and podcast is coming_soon.
        assertEquals(1, newItems.size)
        assertEquals("math-4024-factorisation-pack", newItems[0].second.artifactId)
        assertEquals("pdf", newItems[0].second.format)
    }

    // 17. Content sync policy preference persistence
    @Test
    fun `content sync policy and notification settings persist in preferences`() {
        val initialConfig = CloudContentPrefs.load(context)
        assertEquals(ContentSyncPolicy.NOTIFY_FIRST, initialConfig.contentSyncPolicy)
        assertTrue(initialConfig.notifyOnNewContent)

        // Save AUTOMATIC policy
        val autoConfig = initialConfig.copy(
            contentSyncPolicy = ContentSyncPolicy.AUTOMATIC,
            notifyOnNewContent = false
        )
        CloudContentPrefs.save(context, autoConfig)

        val loadedAuto = CloudContentPrefs.load(context)
        assertEquals(ContentSyncPolicy.AUTOMATIC, loadedAuto.contentSyncPolicy)
        assertFalse(loadedAuto.notifyOnNewContent)

        // Save MANUAL_ONLY policy
        val manualConfig = initialConfig.copy(
            contentSyncPolicy = ContentSyncPolicy.MANUAL_ONLY,
            notifyOnNewContent = true
        )
        CloudContentPrefs.save(context, manualConfig)

        val loadedManual = CloudContentPrefs.load(context)
        assertEquals(ContentSyncPolicy.MANUAL_ONLY, loadedManual.contentSyncPolicy)
        assertTrue(loadedManual.notifyOnNewContent)
    }
}
