package com.example.data.curriculum

import android.content.Context
import com.example.core.model.CoursePackage
import com.example.core.model.CoursePackageManifest
import com.example.core.model.EdgeType
import com.example.core.model.KGEdge
import com.example.core.model.KGNode
import com.example.core.model.NodeType
import com.example.core.model.Provenance
import com.example.core.model.QuestionItem
import com.example.core.model.ResourceItem
import com.example.data.local.CoursePackageEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads official, publisher-SIGNED course packages from APK assets.
 *
 * The package JSON files under app/src/main/assets/curriculum are generated
 * offline by tools/sign_course_package.py, which computes the canonical
 * content hash and the Ed25519 signature with the publisher key. The app only
 * ever verifies (see PackageVerifier) — it cannot sign.
 */
class CoursePackageProvider(private val context: Context) {

    fun createMath4024Package(): CoursePackage = loadPackageFromAssets("curriculum/math_4024.json")

    fun createPhysics5054Package(): CoursePackage = loadPackageFromAssets("curriculum/physics_5054.json")

    /** Loads and parses a signed course package asset. */
    fun loadPackageFromAssets(assetPath: String): CoursePackage {
        val json = context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parsePackageFromJson(json)
    }

    /** Parses package JSON into a [CoursePackage]. Exposed for tests. */
    fun parsePackageFromJson(json: String): CoursePackage {
        val root = JSONObject(json)

        val m = root.getJSONObject("manifest")
        val manifest = CoursePackageManifest(
            packageId = m.getString("packageId"),
            courseId = m.getString("courseId"),
            subjectName = m.getString("subjectName"),
            version = m.getString("version"),
            schemaVersion = m.optString("schemaVersion", "1.0"),
            createdAt = m.getString("createdAt"),
            contentHash = m.getString("contentHash"),
            parentVersion = optNullableString(m, "parentVersion"),
            deltaBase = optNullableString(m, "deltaBase"),
            signature = m.getString("signature")
        )

        val nodes = ArrayList<KGNode>()
        val nodesArr = root.getJSONArray("nodes")
        for (i in 0 until nodesArr.length()) {
            val n = nodesArr.getJSONObject(i)
            val props = HashMap<String, String>()
            val propObj = n.optJSONObject("properties")
            if (propObj != null) {
                val keys = propObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    props[k] = propObj.optString(k, "")
                }
            }
            nodes.add(
                KGNode(
                    id = n.getString("id"),
                    type = NodeType.valueOf(n.getString("type")),
                    name = n.getString("name"),
                    properties = props,
                    provenance = Provenance()
                )
            )
        }

        val edges = ArrayList<KGEdge>()
        val edgesArr = root.getJSONArray("edges")
        for (i in 0 until edgesArr.length()) {
            val e = edgesArr.getJSONObject(i)
            val props = HashMap<String, String>()
            val propObj = e.optJSONObject("properties")
            if (propObj != null) {
                val keys = propObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    props[k] = propObj.optString(k, "")
                }
            }
            edges.add(
                KGEdge(
                    id = e.getString("id"),
                    type = EdgeType.valueOf(e.getString("type")),
                    source = e.getString("source"),
                    target = e.getString("target"),
                    properties = props
                )
            )
        }

        val questions = ArrayList<QuestionItem>()
        val qArr = root.getJSONArray("questions")
        for (i in 0 until qArr.length()) {
            val q = qArr.getJSONObject(i)
            val options = ArrayList<String>()
            val optArr = q.optJSONArray("options")
            if (optArr != null) for (j in 0 until optArr.length()) options.add(optArr.getString(j))
            val misc = ArrayList<String>()
            val miscArr = q.optJSONArray("potentialMisconceptions")
            if (miscArr != null) for (j in 0 until miscArr.length()) misc.add(miscArr.getString(j))
            questions.add(
                QuestionItem(
                    id = q.getString("id"),
                    courseVersionId = q.getString("courseVersionId"),
                    targetConceptId = q.getString("targetConceptId"),
                    questionType = q.optString("questionType", "STRUCTURED"),
                    prompt = q.getString("prompt"),
                    options = options,
                    correctIndex = q.optInt("correctIndex", -1),
                    targetLaTeX = q.optString("targetLaTeX", ""),
                    difficulty = q.optDouble("difficulty", 0.5),
                    maxMarks = q.optInt("maxMarks", 4),
                    bloomLevel = q.optString("bloomLevel", "APPLICATION"),
                    rubrics = q.optString("rubrics", ""),
                    prerequisiteConceptId = optNullableString(q, "prerequisiteConceptId"),
                    potentialMisconceptions = misc
                )
            )
        }

        val resources = ArrayList<ResourceItem>()
        val rArr = root.getJSONArray("resources")
        for (i in 0 until rArr.length()) {
            val r = rArr.getJSONObject(i)
            resources.add(
                ResourceItem(
                    id = r.getString("id"),
                    conceptId = r.getString("conceptId"),
                    resourceType = r.optString("resourceType", "TEXT"),
                    title = r.getString("title"),
                    content = r.getString("content"),
                    contentHash = r.optString("contentHash", "")
                )
            )
        }

        return CoursePackage(manifest, nodes, edges, questions, resources)
    }

    // ==========================================
    // DB storage codecs (serialize* + deserializePackage round-trip;
    // pinned by CoursePackageRoundTripTest)
    // ==========================================

    /**
     * Reads a nullable string field. Unlike [JSONObject.optString], JSON null
     * (and missing keys) become real null instead of the literal "null" string.
     */
    private fun optNullableString(obj: JSONObject, key: String): String? {
        if (obj.isNull(key)) return null
        return obj.optString(key).takeIf { it.isNotEmpty() }
    }

    /**
     * Rebuilds a [CoursePackage] from its DB row. Inverse of the serialize*
     * functions below; must round-trip identically w.r.t. canonicalization.
     */
    fun deserializePackage(entity: CoursePackageEntity): CoursePackage {
        val m = JSONObject(entity.manifestJson)
        val manifest = CoursePackageManifest(
            packageId = m.optString("packageId", entity.packageId),
            courseId = m.optString("courseId", entity.courseId),
            subjectName = m.optString("subjectName", entity.subjectName),
            version = m.optString("version", entity.version),
            schemaVersion = m.optString("schemaVersion", "1.0"),
            createdAt = m.optString("createdAt", "2026-09-08T00:00:00Z"),
            contentHash = m.optString("contentHash", entity.contentHash),
            parentVersion = optNullableString(m, "parentVersion"),
            deltaBase = optNullableString(m, "deltaBase"),
            signature = m.optString("signature", "")
        )

        val nodes = ArrayList<KGNode>()
        val nodesArr = JSONArray(entity.nodesJson)
        for (i in 0 until nodesArr.length()) {
            val n = nodesArr.getJSONObject(i)
            val props = HashMap<String, String>()
            val propObj = n.optJSONObject("properties")
            if (propObj != null) {
                val keys = propObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    props[k] = propObj.optString(k, "")
                }
            }
            nodes.add(
                KGNode(
                    id = n.getString("id"),
                    type = NodeType.valueOf(n.getString("type")),
                    name = n.getString("name"),
                    properties = props
                )
            )
        }

        val edges = ArrayList<KGEdge>()
        val edgesArr = JSONArray(entity.edgesJson)
        for (i in 0 until edgesArr.length()) {
            val e = edgesArr.getJSONObject(i)
            val props = HashMap<String, String>()
            val propObj = e.optJSONObject("properties")
            if (propObj != null) {
                val keys = propObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    props[k] = propObj.optString(k, "")
                }
            }
            edges.add(
                KGEdge(
                    id = e.getString("id"),
                    type = EdgeType.valueOf(e.getString("type")),
                    source = e.getString("source"),
                    target = e.getString("target"),
                    properties = props
                )
            )
        }

        val questions = ArrayList<QuestionItem>()
        val qArr = JSONArray(entity.questionsJson)
        for (i in 0 until qArr.length()) {
            val q = qArr.getJSONObject(i)
            val options = ArrayList<String>()
            val optArr = q.optJSONArray("options")
            if (optArr != null) for (j in 0 until optArr.length()) options.add(optArr.getString(j))
            val misc = ArrayList<String>()
            val miscArr = q.optJSONArray("potentialMisconceptions")
            if (miscArr != null) for (j in 0 until miscArr.length()) misc.add(miscArr.getString(j))
            questions.add(
                QuestionItem(
                    id = q.getString("id"),
                    courseVersionId = q.getString("courseVersionId"),
                    targetConceptId = q.getString("targetConceptId"),
                    questionType = q.optString("questionType", "STRUCTURED"),
                    prompt = q.getString("prompt"),
                    options = options,
                    correctIndex = q.optInt("correctIndex", -1),
                    targetLaTeX = q.optString("targetLaTeX", ""),
                    difficulty = q.optDouble("difficulty", 0.5),
                    maxMarks = q.optInt("maxMarks", 4),
                    bloomLevel = q.optString("bloomLevel", "APPLICATION"),
                    rubrics = q.optString("rubrics", ""),
                    prerequisiteConceptId = optNullableString(q, "prerequisiteConceptId"),
                    potentialMisconceptions = misc
                )
            )
        }

        val resources = ArrayList<ResourceItem>()
        val rArr = JSONArray(entity.resourcesJson)
        for (i in 0 until rArr.length()) {
            val r = rArr.getJSONObject(i)
            resources.add(
                ResourceItem(
                    id = r.getString("id"),
                    conceptId = r.getString("conceptId"),
                    resourceType = r.optString("resourceType", "TEXT"),
                    title = r.getString("title"),
                    content = r.getString("content"),
                    contentHash = r.optString("contentHash", "")
                )
            )
        }

        return CoursePackage(manifest, nodes, edges, questions, resources)
    }


    fun serializeManifest(pkg: CoursePackage): String = JSONObject().apply {
        put("packageId", pkg.manifest.packageId)
        put("courseId", pkg.manifest.courseId)
        put("subjectName", pkg.manifest.subjectName)
        put("version", pkg.manifest.version)
        put("schemaVersion", pkg.manifest.schemaVersion)
        put("createdAt", pkg.manifest.createdAt)
        put("contentHash", pkg.manifest.contentHash)
        put("parentVersion", pkg.manifest.parentVersion ?: JSONObject.NULL)
        put("deltaBase", pkg.manifest.deltaBase ?: JSONObject.NULL)
        put("signature", pkg.manifest.signature)
    }.toString()

    fun serializeNodes(nodes: List<KGNode>): String = JSONArray().apply {
        nodes.forEach { n ->
            put(JSONObject().apply {
                put("id", n.id)
                put("type", n.type.name)
                put("name", n.name)
                put("properties", JSONObject(n.properties))
            })
        }
    }.toString()

    fun serializeEdges(edges: List<KGEdge>): String = JSONArray().apply {
        edges.forEach { e ->
            put(JSONObject().apply {
                put("id", e.id)
                put("type", e.type.name)
                put("source", e.source)
                put("target", e.target)
                put("properties", JSONObject(e.properties))
            })
        }
    }.toString()

    fun serializeQuestions(questions: List<QuestionItem>): String = JSONArray().apply {
        questions.forEach { q ->
            put(JSONObject().apply {
                put("id", q.id)
                put("courseVersionId", q.courseVersionId)
                put("targetConceptId", q.targetConceptId)
                put("questionType", q.questionType)
                put("prompt", q.prompt)
                put("options", JSONArray(q.options))
                put("correctIndex", q.correctIndex)
                put("targetLaTeX", q.targetLaTeX)
                put("difficulty", q.difficulty)
                put("maxMarks", q.maxMarks)
                put("bloomLevel", q.bloomLevel)
                put("rubrics", q.rubrics)
                put("prerequisiteConceptId", q.prerequisiteConceptId ?: JSONObject.NULL)
                put("potentialMisconceptions", JSONArray(q.potentialMisconceptions))
            })
        }
    }.toString()

    fun serializeResources(resources: List<ResourceItem>): String = JSONArray().apply {
        resources.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id)
                put("conceptId", r.conceptId)
                put("resourceType", r.resourceType)
                put("title", r.title)
                put("content", r.content)
                put("contentHash", r.contentHash)
            })
        }
    }.toString()
}
