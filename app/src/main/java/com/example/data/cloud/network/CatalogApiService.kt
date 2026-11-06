package com.example.data.cloud.network

import com.example.data.cloud.ArtifactAvailabilityState
import com.example.data.cloud.CloudArtifactRecord
import com.example.data.cloud.CloudClassRecord
import com.example.data.cloud.CloudSubjectRecord
import com.example.data.cloud.CloudTopicRecord
import com.example.data.cloud.NoorixArtifactType
import com.example.data.cloud.PublishedContentCatalog
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

/**
 * Data Transfer Object for the root catalog.json matching Section 5 published content index contract.
 */
@JsonClass(generateAdapter = true)
data class CatalogSchemaDto(
    @Json(name = "schemaVersion") val schemaVersion: String = "1.0",
    @Json(name = "catalogVersion") val catalogVersion: String? = null,
    @Json(name = "curriculumId") val curriculumId: String? = null,
    @Json(name = "publishedAt") val publishedAt: String? = null,
    @Json(name = "classes") val classes: List<ClassSchemaDto>? = null,
    @Json(name = "subjects") val subjects: List<SubjectSchemaDto>? = null
)

@JsonClass(generateAdapter = true)
data class ClassSchemaDto(
    @Json(name = "classId") val classId: String,
    @Json(name = "className") val className: String? = null,
    @Json(name = "subjects") val subjects: List<SubjectSchemaDto>? = null
)

@JsonClass(generateAdapter = true)
data class SubjectSchemaDto(
    @Json(name = "subjectId") val subjectId: String,
    @Json(name = "subjectName") val subjectName: String? = null,
    @Json(name = "gradeId") val gradeId: String? = null,
    @Json(name = "className") val className: String? = null,
    @Json(name = "topics") val topics: List<TopicSchemaDto>? = null
)

@JsonClass(generateAdapter = true)
data class TopicSchemaDto(
    @Json(name = "topicId") val topicId: String,
    @Json(name = "topicName") val topicName: String? = null,
    @Json(name = "gradeId") val gradeId: String? = null,
    @Json(name = "className") val className: String? = null,
    @Json(name = "artifacts") val artifacts: Map<String, ArtifactSchemaDto>? = null
)

@JsonClass(generateAdapter = true)
data class ArtifactSchemaDto(
    @Json(name = "status") val status: String? = null,
    @Json(name = "artifactId") val artifactId: String? = null,
    @Json(name = "version") val version: String? = null,
    @Json(name = "format") val format: String? = null,
    @Json(name = "downloadUrl") val downloadUrl: String? = null,
    @Json(name = "sha256") val sha256: String? = null,
    @Json(name = "sizeBytes") val sizeBytes: Long? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "videoId") val videoId: String? = null,
    @Json(name = "channelId") val channelId: String? = null,
    @Json(name = "durationSeconds") val durationSeconds: Int? = null,
    @Json(name = "embedAllowed") val embedAllowed: Boolean? = null
)

/**
 * Retrofit service definition for fetching catalog.json from remote URLs with ETag caching support.
 */
interface CatalogApiService {

    /**
     * Fetches typed catalog schema with conditional GET using ETag headers.
     * When the remote catalog is unchanged, the server returns HTTP 304 (Not Modified).
     */
    @GET
    suspend fun getCatalog(
        @Url url: String,
        @Header("If-None-Match") ifNoneMatch: String? = null,
        @Header("User-Agent") userAgent: String = "NoorixStudent-Android/1.0"
    ): Response<CatalogSchemaDto>

    /**
     * Fetches the raw catalog.json response body with conditional GET using ETag headers.
     * Supports verbatim raw JSON caching into Room.
     */
    @GET
    suspend fun getCatalogRaw(
        @Url url: String,
        @Header("If-None-Match") ifNoneMatch: String? = null,
        @Header("User-Agent") userAgent: String = "NoorixStudent-Android/1.0"
    ): Response<ResponseBody>
}

/**
 * Maps Retrofit [CatalogSchemaDto] to domain [PublishedContentCatalog].
 */
fun CatalogSchemaDto.toDomain(etag: String? = null): PublishedContentCatalog {
    val domainClasses = mutableListOf<CloudClassRecord>()
    val domainSubjects = mutableListOf<CloudSubjectRecord>()

    fun mapArtifactType(key: String): NoorixArtifactType? = when (key.lowercase()) {
        "studypack", "study_pack" -> NoorixArtifactType.STUDY_PACK
        "notes", "note" -> NoorixArtifactType.NOTES
        "workedexamples", "worked_examples" -> NoorixArtifactType.WORKED_EXAMPLES
        "quiz", "quizzes" -> NoorixArtifactType.QUIZ
        "worksheet", "worksheets" -> NoorixArtifactType.WORKSHEET
        "flashcard", "flashcards" -> NoorixArtifactType.FLASHCARDS
        "revisionsheet", "revision_sheet" -> NoorixArtifactType.REVISION_SHEET
        "podcast", "podcasts", "audio" -> NoorixArtifactType.PODCAST
        "video", "videos" -> NoorixArtifactType.VIDEO
        else -> null
    }

    fun mapStatus(status: String?): ArtifactAvailabilityState = when (status?.lowercase()) {
        "available" -> ArtifactAvailabilityState.AVAILABLE
        "coming_soon", "comingsoon" -> ArtifactAvailabilityState.COMING_SOON
        "temporarily_unavailable" -> ArtifactAvailabilityState.TEMPORARILY_UNAVAILABLE
        "access_denied" -> ArtifactAvailabilityState.ACCESS_DENIED
        "updating" -> ArtifactAvailabilityState.UPDATING
        "unsupported" -> ArtifactAvailabilityState.UNSUPPORTED
        else -> ArtifactAvailabilityState.COMING_SOON
    }

    fun mapTopic(dto: TopicSchemaDto, gradeId: String, className: String): CloudTopicRecord {
        val artifactMap = mutableMapOf<NoorixArtifactType, CloudArtifactRecord>()
        dto.artifacts?.forEach { (key, artifactDto) ->
            val type = mapArtifactType(key) ?: return@forEach
            val status = mapStatus(artifactDto.status)
            artifactMap[type] = CloudArtifactRecord(
                artifactType = type,
                status = status,
                artifactId = artifactDto.artifactId ?: "",
                version = artifactDto.version ?: "",
                format = artifactDto.format ?: "",
                downloadUrl = artifactDto.downloadUrl ?: "",
                sha256 = artifactDto.sha256 ?: "",
                sizeBytes = artifactDto.sizeBytes ?: 0L,
                title = artifactDto.title ?: "",
                description = artifactDto.description ?: "",
                videoId = artifactDto.videoId ?: "",
                channelId = artifactDto.channelId ?: "",
                durationSeconds = artifactDto.durationSeconds ?: 0,
                embedAllowed = artifactDto.embedAllowed ?: true
            )
        }
        return CloudTopicRecord(
            topicId = dto.topicId,
            topicName = dto.topicName ?: "",
            gradeId = gradeId.ifBlank { dto.gradeId ?: "" },
            className = className.ifBlank { dto.className ?: "" },
            artifacts = artifactMap
        )
    }

    fun mapSubject(dto: SubjectSchemaDto, gradeId: String, className: String): CloudSubjectRecord {
        val effectiveGradeId = gradeId.ifBlank { dto.gradeId ?: "" }
        val effectiveClassName = className.ifBlank { dto.className ?: "" }
        val topics = dto.topics?.map { mapTopic(it, effectiveGradeId, effectiveClassName) } ?: emptyList()
        return CloudSubjectRecord(
            subjectId = dto.subjectId,
            subjectName = dto.subjectName ?: "",
            gradeId = effectiveGradeId,
            className = effectiveClassName,
            topics = topics
        )
    }

    // Process classes if provided
    classes?.forEach { classDto ->
        val subjects = classDto.subjects?.map { mapSubject(it, classDto.classId, classDto.className ?: "") } ?: emptyList()
        domainClasses.add(
            CloudClassRecord(
                classId = classDto.classId,
                className = classDto.className ?: classDto.classId,
                subjects = subjects
            )
        )
        // Also accumulate into flat subjects list for backwards compatibility
        domainSubjects.addAll(subjects)
    }

    // Process top-level subjects if provided
    subjects?.forEach { subjectDto ->
        val mapped = mapSubject(subjectDto, subjectDto.gradeId ?: "", subjectDto.className ?: "")
        domainSubjects.add(mapped)
    }

    return PublishedContentCatalog(
        schemaVersion = schemaVersion,
        catalogVersion = catalogVersion ?: "unversioned",
        curriculumId = curriculumId ?: "cambridge",
        publishedAt = publishedAt ?: "",
        etag = etag,
        classes = domainClasses,
        subjects = domainSubjects
    )
}
