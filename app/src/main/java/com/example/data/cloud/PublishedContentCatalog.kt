package com.example.data.cloud

import org.json.JSONArray
import org.json.JSONObject

/**
 * Detailed availability state conforming strictly to Section 6 of the spec.
 */
enum class ArtifactAvailabilityState {
    /** Approved content is registered and accessible. */
    AVAILABLE,

    /** No approved content has been published for this topic and artifact type. */
    COMING_SOON,

    /** Content is registered in catalog, but the provider cannot currently be reached. */
    TEMPORARILY_UNAVAILABLE,

    /** The source rejected access (HTTP 401/403 or permission issue). */
    ACCESS_DENIED,

    /** A new version is being published/downloaded; old version preserved if available. */
    UPDATING,

    /** The artifact format cannot be consumed by this app version. */
    UNSUPPORTED
}

/**
 * Standard artifact types that can be registered in the published Noorix index.
 */
enum class NoorixArtifactType {
    STUDY_PACK,
    NOTES,
    WORKED_EXAMPLES,
    QUIZ,
    WORKSHEET,
    FLASHCARDS,
    REVISION_SHEET,
    PODCAST,
    VIDEO
}

/**
 * Metadata record for a file/downloadable asset artifact in the catalog.
 */
data class CloudArtifactRecord(
    val artifactType: NoorixArtifactType,
    val status: ArtifactAvailabilityState,
    val artifactId: String = "",
    val version: String = "",
    val format: String = "",
    val downloadUrl: String = "",
    val sha256: String = "",
    val sizeBytes: Long = 0L,
    val title: String = "",
    val description: String = "",
    // Video-specific fields
    val videoId: String = "",
    val channelId: String = "",
    val durationSeconds: Int = 0,
    val embedAllowed: Boolean = true,
    // Custom/extra metadata
    val extraMetadata: Map<String, String> = emptyMap()
)

/**
 * Published topic entry associating stable topic IDs and names with multiple artifacts.
 */
data class CloudTopicRecord(
    val topicId: String,
    val topicName: String = "",
    val gradeId: String = "",
    val className: String = "",
    val artifacts: Map<NoorixArtifactType, CloudArtifactRecord> = emptyMap()
)

/**
 * Published subject entry grouping topics under a stable subject ID and name.
 */
data class CloudSubjectRecord(
    val subjectId: String,
    val subjectName: String = "",
    val gradeId: String = "",
    val className: String = "",
    val topics: List<CloudTopicRecord> = emptyList()
)

/**
 * Published grade/class entry grouping subjects and topics under a class/grade.
 */
data class CloudClassRecord(
    val classId: String,
    val className: String,
    val subjects: List<CloudSubjectRecord> = emptyList()
)

/**
 * Versioned content catalog root matching the schema contract in Section 5.
 */
data class PublishedContentCatalog(
    val schemaVersion: String,
    val catalogVersion: String,
    val curriculumId: String,
    val publishedAt: String,
    val subjects: List<CloudSubjectRecord>,
    val classes: List<CloudClassRecord> = emptyList(),
    val etag: String? = null,
    val rawJson: String = ""
) {
    /**
     * Finds the artifact record for the given stable topic ID and artifact type.
     */
    fun findArtifact(topicId: String, type: NoorixArtifactType): CloudArtifactRecord? {
        for (subj in subjects) {
            for (topic in subj.topics) {
                if (topic.topicId == topicId) {
                    return topic.artifacts[type]
                }
            }
        }
        for (cls in classes) {
            for (subj in cls.subjects) {
                for (topic in subj.topics) {
                    if (topic.topicId == topicId) {
                        return topic.artifacts[type]
                    }
                }
            }
        }
        return null
    }

    /**
     * Finds all artifacts registered for a topic ID.
     */
    fun findTopic(topicId: String): CloudTopicRecord? {
        for (subj in subjects) {
            for (topic in subj.topics) {
                if (topic.topicId == topicId) return topic
            }
        }
        for (cls in classes) {
            for (subj in cls.subjects) {
                for (topic in subj.topics) {
                    if (topic.topicId == topicId) return topic
                }
            }
        }
        return null
    }

    /**
     * Returns all topics that belong to a specific student class (e.g. "Grade 9", "Grade 10", "class:9", "O-Level").
     */
    fun getTopicsForClass(studentClass: String): List<CloudTopicRecord> {
        val cleanClass = studentClass.trim().lowercase()
        val allTopics = mutableListOf<CloudTopicRecord>()

        // 1. Check direct classes array if defined
        for (cls in classes) {
            val classMatches = cls.className.lowercase().contains(cleanClass) ||
                cleanClass.contains(cls.className.lowercase()) ||
                cls.classId.lowercase().contains(cleanClass) ||
                cleanClass.contains(cls.classId.lowercase())

            if (classMatches || cleanClass.isBlank()) {
                for (s in cls.subjects) {
                    allTopics.addAll(s.topics)
                }
            }
        }

        // 2. Also check flat subjects array
        for (subj in subjects) {
            val subjMatches = subj.className.lowercase().contains(cleanClass) ||
                cleanClass.contains(subj.className.lowercase()) ||
                subj.gradeId.lowercase().contains(cleanClass) ||
                cleanClass.contains(subj.gradeId.lowercase())

            for (topic in subj.topics) {
                val topicMatches = subjMatches ||
                    topic.className.lowercase().contains(cleanClass) ||
                    cleanClass.contains(topic.className.lowercase()) ||
                    topic.gradeId.lowercase().contains(cleanClass) ||
                    cleanClass.contains(topic.gradeId.lowercase()) ||
                    topic.topicId.lowercase().contains(cleanClass) ||
                    cleanClass.isBlank()

                if (topicMatches && allTopics.none { it.topicId == topic.topicId }) {
                    allTopics.add(topic)
                }
            }
        }
        return allTopics
    }

    companion object {
        const val SUPPORTED_SCHEMA_MAJOR = "1."

        /**
         * Parses and validates a JSON string against the Noorix published index schema.
         * Throws [IllegalArgumentException] on schema incompatibility or malformed root.
         */
        fun parse(jsonString: String, etag: String? = null): PublishedContentCatalog {
            val root = JSONObject(jsonString)
            val schemaVersion = root.optString("schemaVersion", "")
            if (!schemaVersion.startsWith(SUPPORTED_SCHEMA_MAJOR) && schemaVersion != "1.0") {
                throw IllegalArgumentException("Unsupported schemaVersion: '$schemaVersion'. Supported major: 1.x")
            }

            val catalogVersion = root.optString("catalogVersion", "unversioned")
            val curriculumId = root.optString("curriculumId", "cambridge")
            val publishedAt = root.optString("publishedAt", "")

            val subjectsArray = root.optJSONArray("subjects") ?: JSONArray()
            val parsedSubjects = mutableListOf<CloudSubjectRecord>()

            for (i in 0 until subjectsArray.length()) {
                val subjObj = subjectsArray.optJSONObject(i) ?: continue
                val subjectId = subjObj.optString("subjectId", "")
                if (subjectId.isBlank()) continue

                val topicsArray = subjObj.optJSONArray("topics") ?: JSONArray()
                val parsedTopics = mutableListOf<CloudTopicRecord>()

                for (j in 0 until topicsArray.length()) {
                    val topicObj = topicsArray.optJSONObject(j) ?: continue
                    val topicId = topicObj.optString("topicId", "")
                    if (topicId.isBlank()) continue

                    val artifactsObj = topicObj.optJSONObject("artifacts") ?: JSONObject()
                    val parsedArtifacts = mutableMapOf<NoorixArtifactType, CloudArtifactRecord>()

                    // Parse each known artifact type
                    for (type in NoorixArtifactType.values()) {
                        val key = typeToKey(type)
                        val artObj = artifactsObj.optJSONObject(key) ?: continue

                        val statusStr = artObj.optString("status", "available").lowercase()
                        val status = when (statusStr) {
                            "available" -> ArtifactAvailabilityState.AVAILABLE
                            "coming_soon" -> ArtifactAvailabilityState.COMING_SOON
                            "temporarily_unavailable" -> ArtifactAvailabilityState.TEMPORARILY_UNAVAILABLE
                            "access_denied" -> ArtifactAvailabilityState.ACCESS_DENIED
                            "updating" -> ArtifactAvailabilityState.UPDATING
                            "unsupported" -> ArtifactAvailabilityState.UNSUPPORTED
                            else -> ArtifactAvailabilityState.AVAILABLE
                        }

                        val format = artObj.optString("format", "")
                        // Check if app actually supports format
                        val effectiveStatus = if (status == ArtifactAvailabilityState.AVAILABLE && isUnsupportedFormat(type, format)) {
                            ArtifactAvailabilityState.UNSUPPORTED
                        } else {
                            status
                        }

                        val record = CloudArtifactRecord(
                            artifactType = type,
                            status = effectiveStatus,
                            artifactId = artObj.optString("artifactId", ""),
                            version = artObj.optString("version", ""),
                            format = format,
                            downloadUrl = artObj.optString("downloadUrl", ""),
                            sha256 = artObj.optString("sha256", ""),
                            sizeBytes = artObj.optLong("sizeBytes", 0L),
                            title = artObj.optString("title", ""),
                            description = artObj.optString("description", ""),
                            videoId = artObj.optString("videoId", ""),
                            channelId = artObj.optString("channelId", ""),
                            durationSeconds = artObj.optInt("durationSeconds", 0),
                            embedAllowed = artObj.optBoolean("embedAllowed", true)
                        )
                        parsedArtifacts[type] = record
                    }

                    val topicName = topicObj.optString("topicName", topicObj.optString("name", topicObj.optString("title", "")))
                    val topicGrade = topicObj.optString("gradeId", topicObj.optString("grade", subjObj.optString("gradeId", "")))
                    val topicClass = topicObj.optString("className", topicObj.optString("class", subjObj.optString("className", "")))

                    parsedTopics.add(
                        CloudTopicRecord(
                            topicId = topicId,
                            topicName = topicName,
                            gradeId = topicGrade,
                            className = topicClass,
                            artifacts = parsedArtifacts
                        )
                    )
                }
                val subjectName = subjObj.optString("subjectName", subjObj.optString("name", ""))
                val subjectGrade = subjObj.optString("gradeId", subjObj.optString("grade", ""))
                val subjectClass = subjObj.optString("className", subjObj.optString("class", ""))

                parsedSubjects.add(
                    CloudSubjectRecord(
                        subjectId = subjectId,
                        subjectName = subjectName,
                        gradeId = subjectGrade,
                        className = subjectClass,
                        topics = parsedTopics
                    )
                )
            }

            // Parse classes array if provided (e.g. classes: [ { classId, className, subjects: [...] } ])
            val classesArray = root.optJSONArray("classes")
            val parsedClasses = mutableListOf<CloudClassRecord>()
            if (classesArray != null) {
                for (c in 0 until classesArray.length()) {
                    val classObj = classesArray.optJSONObject(c) ?: continue
                    val classId = classObj.optString("classId", classObj.optString("id", ""))
                    val className = classObj.optString("className", classObj.optString("name", "Class $c"))

                    val classSubjArray = classObj.optJSONArray("subjects") ?: JSONArray()
                    val classSubjects = mutableListOf<CloudSubjectRecord>()

                    for (s in 0 until classSubjArray.length()) {
                        val cSubjObj = classSubjArray.optJSONObject(s) ?: continue
                        val cSubjId = cSubjObj.optString("subjectId", "")
                        val cSubjName = cSubjObj.optString("subjectName", cSubjObj.optString("name", ""))
                        val cTopicsArray = cSubjObj.optJSONArray("topics") ?: JSONArray()
                        val cTopics = mutableListOf<CloudTopicRecord>()

                        for (t in 0 until cTopicsArray.length()) {
                            val cTopObj = cTopicsArray.optJSONObject(t) ?: continue
                            val cTopId = cTopObj.optString("topicId", "")
                            if (cTopId.isBlank()) continue
                            val cTopName = cTopObj.optString("topicName", cTopObj.optString("name", cTopObj.optString("title", "")))

                            val cArtObj = cTopObj.optJSONObject("artifacts") ?: JSONObject()
                            val cArtifacts = mutableMapOf<NoorixArtifactType, CloudArtifactRecord>()

                            for (type in NoorixArtifactType.values()) {
                                val key = typeToKey(type)
                                val artObj = cArtObj.optJSONObject(key) ?: continue

                                val statusStr = artObj.optString("status", "available").lowercase()
                                val status = when (statusStr) {
                                    "available" -> ArtifactAvailabilityState.AVAILABLE
                                    "coming_soon" -> ArtifactAvailabilityState.COMING_SOON
                                    "temporarily_unavailable" -> ArtifactAvailabilityState.TEMPORARILY_UNAVAILABLE
                                    "access_denied" -> ArtifactAvailabilityState.ACCESS_DENIED
                                    "updating" -> ArtifactAvailabilityState.UPDATING
                                    "unsupported" -> ArtifactAvailabilityState.UNSUPPORTED
                                    else -> ArtifactAvailabilityState.AVAILABLE
                                }
                                val format = artObj.optString("format", "")
                                val effectiveStatus = if (status == ArtifactAvailabilityState.AVAILABLE && isUnsupportedFormat(type, format)) {
                                    ArtifactAvailabilityState.UNSUPPORTED
                                } else {
                                    status
                                }
                                cArtifacts[type] = CloudArtifactRecord(
                                    artifactType = type,
                                    status = effectiveStatus,
                                    artifactId = artObj.optString("artifactId", ""),
                                    version = artObj.optString("version", ""),
                                    format = format,
                                    downloadUrl = artObj.optString("downloadUrl", ""),
                                    sha256 = artObj.optString("sha256", ""),
                                    sizeBytes = artObj.optLong("sizeBytes", 0L),
                                    title = artObj.optString("title", ""),
                                    description = artObj.optString("description", ""),
                                    videoId = artObj.optString("videoId", ""),
                                    channelId = artObj.optString("channelId", ""),
                                    durationSeconds = artObj.optInt("durationSeconds", 0),
                                    embedAllowed = artObj.optBoolean("embedAllowed", true)
                                )
                            }

                            cTopics.add(
                                CloudTopicRecord(
                                    topicId = cTopId,
                                    topicName = cTopName,
                                    gradeId = classId,
                                    className = className,
                                    artifacts = cArtifacts
                                )
                            )
                        }

                        classSubjects.add(
                            CloudSubjectRecord(
                                subjectId = cSubjId,
                                subjectName = cSubjName,
                                gradeId = classId,
                                className = className,
                                topics = cTopics
                            )
                        )
                    }

                    parsedClasses.add(
                        CloudClassRecord(
                            classId = classId,
                            className = className,
                            subjects = classSubjects
                        )
                    )
                }
            }

            return PublishedContentCatalog(
                schemaVersion = schemaVersion,
                catalogVersion = catalogVersion,
                curriculumId = curriculumId,
                publishedAt = publishedAt,
                subjects = parsedSubjects,
                classes = parsedClasses,
                etag = etag,
                rawJson = jsonString
            )
        }

        private fun typeToKey(type: NoorixArtifactType): String = when (type) {
            NoorixArtifactType.STUDY_PACK -> "studyPack"
            NoorixArtifactType.NOTES -> "notes"
            NoorixArtifactType.WORKED_EXAMPLES -> "workedExamples"
            NoorixArtifactType.QUIZ -> "quiz"
            NoorixArtifactType.WORKSHEET -> "worksheet"
            NoorixArtifactType.FLASHCARDS -> "flashcards"
            NoorixArtifactType.REVISION_SHEET -> "revisionSheet"
            NoorixArtifactType.PODCAST -> "podcast"
            NoorixArtifactType.VIDEO -> "video"
        }

        private fun isUnsupportedFormat(type: NoorixArtifactType, format: String): Boolean {
            if (type == NoorixArtifactType.VIDEO) return false // Embedded YouTube player
            if (format.isBlank()) return false
            val f = format.lowercase()
            return when (type) {
                NoorixArtifactType.STUDY_PACK,
                NoorixArtifactType.NOTES,
                NoorixArtifactType.WORKSHEET,
                NoorixArtifactType.REVISION_SHEET -> f !in setOf("pdf", "json", "md", "txt")
                NoorixArtifactType.QUIZ,
                NoorixArtifactType.FLASHCARDS,
                NoorixArtifactType.WORKED_EXAMPLES -> f !in setOf("json", "pdf")
                NoorixArtifactType.PODCAST -> f !in setOf("mp3", "m4a", "wav", "json")
                NoorixArtifactType.VIDEO -> false
            }
        }
    }
}
