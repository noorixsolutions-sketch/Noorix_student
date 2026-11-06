package com.example.data.curriculum

import android.content.Context
import com.example.core.model.AcademicState
import com.example.data.local.BandWithGradesHierarchyRelation
import com.example.data.local.CurriculumBandEntity
import com.example.data.local.CurriculumDao
import com.example.data.local.CurriculumDatabase
import com.example.data.local.CurriculumGradeEntity
import com.example.data.local.CurriculumIndexMetaEntity
import com.example.data.local.CurriculumInteractionEventEntity
import com.example.data.local.CurriculumSubjectEntity
import com.example.data.local.CurriculumTopicEntity
import com.example.data.local.CurriculumTopicMasteryEntity
import com.example.data.local.OLevelGradeSubjectMasteryRow
import com.example.data.local.OLevelSubjectMasteryAggregate
import com.example.domain.notebooklm.K12Band
import com.example.domain.notebooklm.K12Grade
import com.example.domain.notebooklm.K12Subject
import com.example.domain.notebooklm.K12Topic
import com.example.domain.notebooklm.NotebookLmStudioEngine
import com.example.domain.notebooklm.NotebookLmTopicBundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Filter criteria for browsing and searching the Room-indexed K-12 curriculum.
 */
data class CurriculumFilter(
    val bandId: String? = null,
    val gradeId: String? = null,
    val subjectId: String? = null,
    val query: String = ""
)

/**
 * Summary statistics of the indexed K-12 curriculum stored in Room.
 */
data class CurriculumIndexStats(
    val curriculumTitle: String,
    val version: String,
    val notebookLmEnabled: Boolean,
    val contentChecksum: String,
    val totalBands: Int,
    val totalGrades: Int,
    val totalSubjects: Int,
    val totalTopics: Int,
    val indexedAtMs: Long,
    val wasReusedFromCache: Boolean
)

/**
 * Dedicated service that indexes the K-12 curriculum JSON asset files into
 * [CurriculumDatabase], provides fast relational browsing, filtering, and
 * full-text search across bands, grades, subjects, and topics, and exposes
 * Room-backed mastery aggregation queries across O-Level subjects.
 */
class CurriculumManager(
    private val context: Context,
    private val database: CurriculumDatabase = CurriculumDatabase.getInstance(context),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val eventLogger: EventLogger = EventLogger(
        context = context,
        curriculumDatabase = database,
        ioDispatcher = ioDispatcher
    ),
    val eventLogSystem: ImmutableEventLogSystem = ImmutableEventLogSystem(
        context = context,
        curriculumDatabase = database,
        eventLogger = eventLogger,
        ioDispatcher = ioDispatcher
    )
) {
    private val dao: CurriculumDao = database.curriculumDao()
    private val indexMutex = Mutex()

    val indexMetaFlow: Flow<CurriculumIndexMetaEntity?> = dao.getIndexMetaFlow()

    val bandsFlow: Flow<List<CurriculumBandEntity>> = dao.getAllBandsFlow()

    val bandsHierarchyFlow: Flow<List<K12Band>> = dao.getAllBandsHierarchyFlow().map { relations ->
        relations.sortedBy { it.band.sortOrder }.map { it.toDomainBand() }
    }

    val totalTopicCountFlow: Flow<Int> = dao.getTopicCountFlow()

    val distinctSubjectNamesFlow: Flow<List<String>> = dao.getDistinctSubjectNamesFlow()

    // --- Room-Backed O-Level Mastery & Diagnostic Flows ---

    val oLevelSubjectMasteryFlow: Flow<List<OLevelSubjectMasteryAggregate>> =
        dao.getOLevelSubjectMasteryFlow(bandId = OLEVEL_BAND_ID)

    val oLevelGradeSubjectMasteryFlow: Flow<List<OLevelGradeSubjectMasteryRow>> =
        dao.getOLevelGradeSubjectMasteryFlow(bandId = OLEVEL_BAND_ID)

    val oLevelDiagnosticPriorityTopicsFlow: Flow<List<CurriculumTopicEntity>> =
        dao.getDiagnosticPriorityTopicsFlow(bandId = OLEVEL_BAND_ID)

    val topicMasteryMapFlow: Flow<Map<String, CurriculumTopicMasteryEntity>> =
        dao.getAllTopicMasteryFlow().map { list -> list.associateBy { it.topicId } }

    /**
     * Ensures the K-12 curriculum JSON files are indexed in the Room database.
     * Uses a SHA-256 content checksum so subsequent calls skip re-parsing unless
     * the underlying JSON assets changed or [forceReindex] is true.
     * Preserves any existing student topic mastery scores across re-indexes.
     */
    suspend fun ensureIndexed(forceReindex: Boolean = false): CurriculumIndexStats = withContext(ioDispatcher) {
        indexMutex.withLock {
            val indexSpec = readIndexManifest()
            val rawBandPayloads = indexSpec.bands.mapNotNull { entry ->
                runCatching {
                    val json = context.assets.open(entry.assetFile).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    entry to json
                }.getOrNull()
            }

            val checksum = computeSha256(
                buildString {
                    append(indexSpec.rawJson)
                    rawBandPayloads.forEach { (entry, json) ->
                        append("|").append(entry.bandId).append(":").append(json)
                    }
                }
            )

            if (!forceReindex) {
                val existingMeta = dao.getIndexMeta()
                val existingTopics = dao.getTopicCount()
                if (existingMeta != null && existingMeta.contentChecksum == checksum && existingTopics > 0) {
                    return@withLock CurriculumIndexStats(
                        curriculumTitle = existingMeta.curriculumTitle,
                        version = existingMeta.version,
                        notebookLmEnabled = existingMeta.notebookLmEnabled,
                        contentChecksum = existingMeta.contentChecksum,
                        totalBands = existingMeta.totalBands,
                        totalGrades = existingMeta.totalGrades,
                        totalSubjects = existingMeta.totalSubjects,
                        totalTopics = existingMeta.totalTopics,
                        indexedAtMs = existingMeta.indexedAtMs,
                        wasReusedFromCache = true
                    )
                }
            }

            // Preserve any persisted mastery state across re-indexing
            val persistedMasteryByTopicId = dao.getAllTopicMastery().associateBy { it.topicId }

            val nowMs = System.currentTimeMillis()
            val bandEntities = mutableListOf<CurriculumBandEntity>()
            val gradeEntities = mutableListOf<CurriculumGradeEntity>()
            val subjectEntities = mutableListOf<CurriculumSubjectEntity>()
            val topicEntities = mutableListOf<CurriculumTopicEntity>()

            var globalGradeOrder = 0
            var globalSubjectOrder = 0
            var globalTopicOrder = 0

            rawBandPayloads.forEachIndexed { bandOrder, (manifestBand, bandJson) ->
                val parsedBand = NotebookLmStudioEngine.parseBandJson(bandJson)
                val resolvedBandId = parsedBand.bandId.ifBlank { manifestBand.bandId }
                val resolvedBandName = parsedBand.bandName.ifBlank { manifestBand.bandName }

                bandEntities.add(
                    CurriculumBandEntity(
                        bandId = resolvedBandId,
                        bandName = resolvedBandName,
                        assetFile = manifestBand.assetFile,
                        gradeRangeJson = JSONArray(manifestBand.gradeRange).toString(),
                        sortOrder = bandOrder,
                        version = indexSpec.version,
                        indexedAtMs = nowMs
                    )
                )

                parsedBand.grades.forEach { grade ->
                    gradeEntities.add(
                        CurriculumGradeEntity(
                            gradeId = grade.gradeId,
                            bandId = resolvedBandId,
                            gradeName = grade.gradeName,
                            ageRange = grade.ageRange,
                            sortOrder = globalGradeOrder++
                        )
                    )

                    grade.subjects.forEach { subject ->
                        subjectEntities.add(
                            CurriculumSubjectEntity(
                                subjectId = subject.subjectId,
                                gradeId = grade.gradeId,
                                bandId = resolvedBandId,
                                subjectName = subject.subjectName,
                                iconName = subject.iconName,
                                topicCount = subject.topics.size,
                                sortOrder = globalSubjectOrder++
                            )
                        )

                        subject.topics.forEach { topic ->
                            val formulasJson = JSONArray(topic.keyFormulas).toString()
                            val notesJson = JSONArray(topic.sourceNotes).toString()
                            val tokens = buildSearchTokens(
                                bandName = resolvedBandName,
                                gradeName = grade.gradeName,
                                ageRange = grade.ageRange,
                                subjectName = subject.subjectName,
                                topic = topic
                            )
                            val savedMastery = persistedMasteryByTopicId[topic.topicId]
                            topicEntities.add(
                                CurriculumTopicEntity(
                                    topicId = topic.topicId,
                                    subjectId = subject.subjectId,
                                    gradeId = grade.gradeId,
                                    bandId = resolvedBandId,
                                    bandName = resolvedBandName,
                                    gradeName = grade.gradeName,
                                    ageRange = grade.ageRange,
                                    subjectName = subject.subjectName,
                                    iconName = subject.iconName,
                                    title = topic.title,
                                    summary = topic.summary,
                                    keyFormulasJson = formulasJson,
                                    sourceNotesJson = notesJson,
                                    workedExample = topic.workedExample,
                                    commonMisconception = topic.commonMisconception,
                                    searchTokens = tokens,
                                    sortOrder = globalTopicOrder++,
                                    masteryScore = savedMastery?.masteryScore ?: 0.0,
                                    masteryStatus = savedMastery?.masteryStatus ?: "UNASSESSED",
                                    evidenceCount = savedMastery?.evidenceCount ?: 0,
                                    lastStudiedAtMs = savedMastery?.lastStudiedAtMs ?: 0L
                                )
                            )
                        }
                    }
                }
            }

            val metaEntity = CurriculumIndexMetaEntity(
                metaKey = CurriculumIndexMetaEntity.META_KEY_K12,
                curriculumTitle = indexSpec.curriculumTitle,
                version = indexSpec.version,
                notebookLmEnabled = indexSpec.notebookLmEnabled,
                contentChecksum = checksum,
                totalBands = bandEntities.size,
                totalGrades = gradeEntities.size,
                totalSubjects = subjectEntities.size,
                totalTopics = topicEntities.size,
                indexedAtMs = nowMs
            )

            dao.replaceFullCurriculumIndex(
                meta = metaEntity,
                bands = bandEntities,
                grades = gradeEntities,
                subjects = subjectEntities,
                topics = topicEntities
            )

            CurriculumIndexStats(
                curriculumTitle = metaEntity.curriculumTitle,
                version = metaEntity.version,
                notebookLmEnabled = metaEntity.notebookLmEnabled,
                contentChecksum = metaEntity.contentChecksum,
                totalBands = metaEntity.totalBands,
                totalGrades = metaEntity.totalGrades,
                totalSubjects = metaEntity.totalSubjects,
                totalTopics = metaEntity.totalTopics,
                indexedAtMs = metaEntity.indexedAtMs,
                wasReusedFromCache = false
            )
        }
    }

    /**
     * Returns the full K-12 band -> grade -> subject -> topic hierarchy from Room,
     * automatically indexing the JSON assets first if the database is empty.
     */
    suspend fun getBandsHierarchy(): List<K12Band> = withContext(ioDispatcher) {
        ensureIndexed(forceReindex = false)
        dao.getAllBandsHierarchy()
            .sortedBy { it.band.sortOrder }
            .map { it.toDomainBand() }
    }

    suspend fun getAllBands(): List<CurriculumBandEntity> = withContext(ioDispatcher) {
        ensureIndexed(forceReindex = false)
        dao.getAllBands()
    }

    suspend fun getGradesForBand(bandId: String): List<CurriculumGradeEntity> = withContext(ioDispatcher) {
        ensureIndexed(forceReindex = false)
        dao.getGradesForBand(bandId)
    }

    fun getGradesForBandFlow(bandId: String): Flow<List<CurriculumGradeEntity>> =
        dao.getGradesForBandFlow(bandId)

    suspend fun getSubjectsForGrade(gradeId: String): List<CurriculumSubjectEntity> = withContext(ioDispatcher) {
        ensureIndexed(forceReindex = false)
        dao.getSubjectsForGrade(gradeId)
    }

    fun getSubjectsForGradeFlow(gradeId: String): Flow<List<CurriculumSubjectEntity>> =
        dao.getSubjectsForGradeFlow(gradeId)

    suspend fun getTopicsForSubject(subjectId: String): List<K12Topic> = withContext(ioDispatcher) {
        ensureIndexed(forceReindex = false)
        dao.getTopicsForSubject(subjectId).map { it.toDomainTopic() }
    }

    fun getTopicsForSubjectFlow(subjectId: String): Flow<List<K12Topic>> =
        dao.getTopicsForSubjectFlow(subjectId).map { list -> list.map { it.toDomainTopic() } }

    suspend fun getTopicsForGrade(gradeId: String): List<K12Topic> = withContext(ioDispatcher) {
        ensureIndexed(forceReindex = false)
        dao.getTopicsForGrade(gradeId).map { it.toDomainTopic() }
    }

    suspend fun getTopicsForBand(bandId: String): List<K12Topic> = withContext(ioDispatcher) {
        ensureIndexed(forceReindex = false)
        dao.getTopicsForBand(bandId).map { it.toDomainTopic() }
    }

    suspend fun getTopicEntitiesForBand(bandId: String = OLEVEL_BAND_ID): List<CurriculumTopicEntity> = withContext(ioDispatcher) {
        ensureIndexed(forceReindex = false)
        dao.getTopicsForBand(bandId)
    }

    fun getTopicEntitiesForBandFlow(bandId: String = OLEVEL_BAND_ID): Flow<List<CurriculumTopicEntity>> =
        dao.getTopicsForBandFlow(bandId)

    suspend fun getTopicById(topicId: String): K12Topic? = withContext(ioDispatcher) {
        ensureIndexed(forceReindex = false)
        dao.getTopicById(topicId)?.toDomainTopic()
    }

    suspend fun getTopicEntityById(topicId: String): CurriculumTopicEntity? = withContext(ioDispatcher) {
        ensureIndexed(forceReindex = false)
        dao.getTopicById(topicId)
    }

    fun observeTopicById(topicId: String): Flow<K12Topic?> =
        dao.getTopicByIdFlow(topicId).map { it?.toDomainTopic() }

    /**
     * Browses and searches topics in the Room index using optional band, grade,
     * subject filters and a full-text search query (supporting multi-word tokens).
     */
    suspend fun browseTopics(filter: CurriculumFilter = CurriculumFilter()): List<K12Topic> = withContext(ioDispatcher) {
        ensureIndexed(forceReindex = false)
        val trimmedQuery = filter.query.trim()
        val words = trimmedQuery.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val primaryQuery = words.firstOrNull().orEmpty()

        val candidates = dao.browseTopics(
            bandId = filter.bandId?.takeIf { it.isNotBlank() },
            gradeId = filter.gradeId?.takeIf { it.isNotBlank() },
            subjectId = filter.subjectId?.takeIf { it.isNotBlank() },
            query = primaryQuery
        )

        val matched = if (words.size <= 1) {
            candidates
        } else {
            candidates.filter { entity ->
                words.all { token -> entity.searchTokens.contains(token) }
            }
        }
        matched.map { it.toDomainTopic() }
    }

    /**
     * Reactive Flow version of [browseTopics] that emits updated topic lists whenever
     * the underlying Room tables or filter parameters change.
     */
    fun browseTopicsFlow(filter: CurriculumFilter = CurriculumFilter()): Flow<List<K12Topic>> {
        val trimmedQuery = filter.query.trim()
        val words = trimmedQuery.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val primaryQuery = words.firstOrNull().orEmpty()

        return dao.browseTopicsFlow(
            bandId = filter.bandId?.takeIf { it.isNotBlank() },
            gradeId = filter.gradeId?.takeIf { it.isNotBlank() },
            subjectId = filter.subjectId?.takeIf { it.isNotBlank() },
            query = primaryQuery
        ).map { candidates ->
            val matched = if (words.size <= 1) {
                candidates
            } else {
                candidates.filter { entity ->
                    words.all { token -> entity.searchTokens.contains(token) }
                }
            }
            matched.map { it.toDomainTopic() }
        }
    }

    suspend fun searchTopics(
        query: String,
        bandId: String? = null,
        gradeId: String? = null,
        subjectId: String? = null
    ): List<K12Topic> = browseTopics(
        CurriculumFilter(
            bandId = bandId,
            gradeId = gradeId,
            subjectId = subjectId,
            query = query
        )
    )

    suspend fun getDistinctSubjectNames(): List<String> = withContext(ioDispatcher) {
        ensureIndexed(forceReindex = false)
        dao.getDistinctSubjectNames()
    }

    /**
     * Queries Room for aggregated student progress across O-Level subjects (`band:9-10` by default)
     * based on mastery scores stored in the CurriculumManager.
     */
    suspend fun getOLevelSubjectMastery(bandId: String = OLEVEL_BAND_ID): List<OLevelSubjectMasteryAggregate> =
        withContext(ioDispatcher) {
            ensureIndexed(forceReindex = false)
            dao.getOLevelSubjectMastery(bandId)
        }

    /**
     * Queries Room for grade-specific subject mastery progress (e.g., Grade 9 vs Grade 10 O-Level subjects).
     */
    suspend fun getOLevelGradeSubjectMastery(bandId: String = OLEVEL_BAND_ID): List<OLevelGradeSubjectMasteryRow> =
        withContext(ioDispatcher) {
            ensureIndexed(forceReindex = false)
            dao.getOLevelGradeSubjectMastery(bandId)
        }

    /**
     * Queries Room for O-Level topics ordered by lowest mastery score first so the diagnostic
     * dashboard can surface highest-priority syllabus gaps.
     */
    suspend fun getDiagnosticPriorityTopics(bandId: String = OLEVEL_BAND_ID): List<CurriculumTopicEntity> =
        withContext(ioDispatcher) {
            ensureIndexed(forceReindex = false)
            dao.getDiagnosticPriorityTopics(bandId)
        }

    /**
     * Updates a topic's mastery score in the Room index AND logs an immutable interaction
     * event via [EventLogger] in a single atomic operation.
     */
    suspend fun updateTopicMastery(
        topicId: String,
        masteryScore: Double,
        studentId: String = EventLogger.DEFAULT_STUDENT_ID,
        interactionType: CurriculumInteractionType = CurriculumInteractionType.MASTERY_CALIBRATED,
        durationSeconds: Int = 45,
        note: String = "Mastery score calibrated in CurriculumManager"
    ): CurriculumInteractionEventEntity = withContext(ioDispatcher) {
        ensureIndexed(forceReindex = false)
        eventLogger.logInteraction(
            topicId = topicId,
            interactionType = interactionType,
            observedScore = masteryScore,
            explicitMasteryScore = masteryScore,
            studentId = studentId,
            durationSeconds = durationSeconds,
            metadata = mapOf("note" to note)
        )
    }

    /**
     * Synchronizes mastery states from the projected [StudentModel.academicStates] into
     * the Room curriculum index so diagnostic check-in and math verification updates
     * are reflected in [getOLevelSubjectMastery].
     */
    suspend fun syncMasteryFromAcademicStates(
        academicStates: Map<String, AcademicState>,
        studentId: String = EventLogger.DEFAULT_STUDENT_ID
    ) = withContext(ioDispatcher) {
        if (academicStates.isEmpty()) return@withContext
        ensureIndexed(forceReindex = false)
        for ((conceptId, state) in academicStates) {
            val topic = dao.getTopicById(conceptId) ?: continue
            val existing = dao.getTopicMastery(conceptId)
            val effective = state.effectiveMastery.coerceIn(0.0, 1.0)
            // Only sync if new evidence arrived or topic was previously unassessed
            if (existing == null || state.evidenceCount > existing.evidenceCount) {
                eventLogger.logInteraction(
                    topicId = topic.topicId,
                    interactionType = CurriculumInteractionType.DIAGNOSTIC_ASSESSED,
                    observedScore = effective,
                    explicitMasteryScore = effective,
                    studentId = studentId,
                    durationSeconds = 30,
                    metadata = mapOf(
                        "source" to "STUDENT_MODEL_PROJECTION",
                        "evidenceCount" to state.evidenceCount
                    )
                )
            }
        }
    }

    /**
     * Loads a topic by ID from the Room index and synthesizes its grounded
     * NotebookLM Topic Bundle (Sources, 2-Host Audio Podcast, 5-Scene Video Explainer, and Quiz).
     */
    suspend fun getNotebookLmBundleForTopic(
        topicId: String,
        customUserNotes: List<String> = emptyList()
    ): NotebookLmTopicBundle? = withContext(ioDispatcher) {
        val topic = getTopicById(topicId) ?: return@withContext null
        NotebookLmStudioEngine.generateBundleForTopic(topic, customUserNotes)
    }

    private data class ManifestBandEntry(
        val bandId: String,
        val bandName: String,
        val assetFile: String,
        val gradeRange: List<String>
    )

    private data class IndexManifestSpec(
        val curriculumTitle: String,
        val version: String,
        val notebookLmEnabled: Boolean,
        val bands: List<ManifestBandEntry>,
        val rawJson: String
    )

    private fun readIndexManifest(): IndexManifestSpec {
        val defaultBands = listOf(
            ManifestBandEntry(
                bandId = "band:k-5",
                bandName = "Elementary School (K – Grade 5)",
                assetFile = "curriculum/k12_elementary_k_5.json",
                gradeRange = listOf("Kindergarten", "Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5")
            ),
            ManifestBandEntry(
                bandId = "band:6-8",
                bandName = "Middle School (Grades 6 – 8)",
                assetFile = "curriculum/k12_middle_6_8.json",
                gradeRange = listOf("Grade 6", "Grade 7", "Grade 8")
            ),
            ManifestBandEntry(
                bandId = "band:9-10",
                bandName = "High School / O-Level (Grades 9 – 10)",
                assetFile = "curriculum/k12_high_9_10_olevel.json",
                gradeRange = listOf("Grade 9", "Grade 10")
            ),
            ManifestBandEntry(
                bandId = "band:11-12",
                bandName = "Senior High / A-Level (Grades 11 – 12)",
                assetFile = "curriculum/k12_senior_11_12_alevel.json",
                gradeRange = listOf("Grade 9", "Grade 10", "Grade 11", "Grade 12")
            )
        )

        return runCatching {
            val raw = context.assets.open("curriculum/k12_curriculum_index.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            val title = root.optString("curriculumTitle", "Noorix Complete K-12 Global & Cambridge Curriculum")
            val version = root.optString("version", "2026.1")
            val notebookEnabled = root.optBoolean("notebookLmEnabled", true)
            val bandsArr = root.optJSONArray("bands")
            val parsedBands = if (bandsArr != null && bandsArr.length() > 0) {
                buildList {
                    for (i in 0 until bandsArr.length()) {
                        val bObj = bandsArr.getJSONObject(i)
                        val rangeArr = bObj.optJSONArray("gradeRange")
                        val rangeList = if (rangeArr != null) {
                            List(rangeArr.length()) { idx -> rangeArr.optString(idx, "") }.filter { it.isNotBlank() }
                        } else {
                            emptyList()
                        }
                        add(
                            ManifestBandEntry(
                                bandId = bObj.getString("bandId"),
                                bandName = bObj.getString("bandName"),
                                assetFile = bObj.getString("assetFile"),
                                gradeRange = rangeList
                            )
                        )
                    }
                }
            } else {
                defaultBands
            }
            IndexManifestSpec(
                curriculumTitle = title,
                version = version,
                notebookLmEnabled = notebookEnabled,
                bands = parsedBands,
                rawJson = raw
            )
        }.getOrElse {
            IndexManifestSpec(
                curriculumTitle = "Noorix Complete K-12 Global & Cambridge Curriculum",
                version = "2026.1",
                notebookLmEnabled = true,
                bands = defaultBands,
                rawJson = "default"
            )
        }
    }

    companion object {
        const val OLEVEL_BAND_ID = "band:9-10"

        internal fun buildSearchTokens(
            bandName: String,
            gradeName: String,
            ageRange: String,
            subjectName: String,
            topic: K12Topic
        ): String {
            return buildString {
                append(topic.topicId).append(' ')
                append(topic.title).append(' ')
                append(topic.summary).append(' ')
                append(subjectName).append(' ')
                append(gradeName).append(' ')
                append(bandName).append(' ')
                append(ageRange).append(' ')
                topic.keyFormulas.forEach { append(it).append(' ') }
                topic.sourceNotes.forEach { append(it).append(' ') }
                append(topic.workedExample).append(' ')
                append(topic.commonMisconception)
            }.lowercase()
        }

        internal fun computeSha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * Maps a [CurriculumTopicEntity] stored in Room back to the domain [K12Topic] model.
 */
fun CurriculumTopicEntity.toDomainTopic(): K12Topic {
    return K12Topic(
        topicId = topicId,
        title = title,
        summary = summary,
        keyFormulas = parseJsonStringList(keyFormulasJson),
        sourceNotes = parseJsonStringList(sourceNotesJson),
        workedExample = workedExample,
        commonMisconception = commonMisconception,
        gradeName = gradeName,
        subjectName = subjectName
    )
}

/**
 * Maps a relational [BandWithGradesHierarchyRelation] from Room into a sorted domain [K12Band].
 */
fun BandWithGradesHierarchyRelation.toDomainBand(): K12Band {
    val sortedGrades = grades.sortedBy { it.grade.sortOrder }.map { gradeRel ->
        val sortedSubjects = gradeRel.subjects.sortedBy { it.subject.sortOrder }.map { subjRel ->
            val sortedTopics = subjRel.topics.sortedBy { it.sortOrder }.map { it.toDomainTopic() }
            K12Subject(
                subjectId = subjRel.subject.subjectId,
                subjectName = subjRel.subject.subjectName,
                iconName = subjRel.subject.iconName,
                topics = sortedTopics
            )
        }
        K12Grade(
            gradeId = gradeRel.grade.gradeId,
            gradeName = gradeRel.grade.gradeName,
            ageRange = gradeRel.grade.ageRange,
            subjects = sortedSubjects
        )
    }
    return K12Band(
        bandId = band.bandId,
        bandName = band.bandName,
        grades = sortedGrades
    )
}

private fun parseJsonStringList(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        List(arr.length()) { idx -> arr.optString(idx, "") }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}
