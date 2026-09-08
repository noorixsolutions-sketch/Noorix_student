package com.example.core.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Curriculum Knowledge Graph — generated from the `master` branch's
 * Cambridge_Curriculum_Library (9 stages, 165 subjects, 5 content areas each)
 * by tools/generate_kg.py and shipped as an APK asset:
 * app/src/main/assets/curriculum/knowledge_graph.json
 */
data class GraphSource(
    val library: String,
    val branch: String,
    val commit: String,
    val generatedAt: String
)

data class GraphNode(
    val id: String,
    val type: String, // STAGE, SUBJECT, RESOURCE
    val name: String,
    val properties: Map<String, String> = emptyMap()
)

data class GraphEdge(
    val id: String,
    val type: String,
    val source: String,
    val target: String
)

data class GraphSubject(
    val id: String,
    val name: String,
    val syllabusCode: String?,
    val areas: List<String>,
    val hasSyllabusPdf: Boolean,
    val fileCount: Int
)

data class GraphStage(
    val id: String,
    val order: Int,
    val name: String,
    val subjectCount: Int,
    val subjects: List<GraphSubject>
)

data class CurriculumGraph(
    val source: GraphSource,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val stages: List<GraphStage>
) {
    val stageCount: Int get() = stages.size
    val subjectCount: Int get() = stages.sumOf { it.subjectCount }
    val hasConsistentEdges: Boolean
        get() {
            val ids = nodes.map { it.id }.toSet()
            return edges.all { ids.contains(it.source) && ids.contains(it.target) }
        }
}

object CurriculumGraphParser {

    fun parse(json: String): CurriculumGraph {
        val root = JSONObject(json)

        val s = root.getJSONObject("source")
        val source = GraphSource(
            library = s.optString("library", "Cambridge_Curriculum_Library"),
            branch = s.optString("branch", "master"),
            commit = s.optString("commit", "unknown"),
            generatedAt = s.optString("generatedAt", "")
        )

        val nodes = ArrayList<GraphNode>()
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
                GraphNode(
                    id = n.getString("id"),
                    type = n.getString("type"),
                    name = n.getString("name"),
                    properties = props
                )
            )
        }

        val edges = ArrayList<GraphEdge>()
        val edgesArr = root.getJSONArray("edges")
        for (i in 0 until edgesArr.length()) {
            val e = edgesArr.getJSONObject(i)
            edges.add(
                GraphEdge(
                    id = e.getString("id"),
                    type = e.getString("type"),
                    source = e.getString("source"),
                    target = e.getString("target")
                )
            )
        }

        val stages = ArrayList<GraphStage>()
        val stagesArr = root.getJSONArray("stages")
        for (i in 0 until stagesArr.length()) {
            val st = stagesArr.getJSONObject(i)
            val subjects = ArrayList<GraphSubject>()
            val subjArr = st.optJSONArray("subjects")
            if (subjArr != null) {
                for (j in 0 until subjArr.length()) {
                    val sbj = subjArr.getJSONObject(j)
                    val areas = ArrayList<String>()
                    val areasArr = sbj.optJSONArray("areas")
                    if (areasArr != null) for (k in 0 until areasArr.length()) areas.add(areasArr.getString(k))
                    subjects.add(
                        GraphSubject(
                            id = sbj.getString("id"),
                            name = sbj.getString("name"),
                            syllabusCode = sbj.optString("syllabusCode", "").takeIf { it.isNotEmpty() },
                            areas = areas,
                            hasSyllabusPdf = sbj.optBoolean("hasSyllabusPdf", false),
                            fileCount = sbj.optInt("fileCount", 0)
                        )
                    )
                }
            }
            stages.add(
                GraphStage(
                    id = st.getString("id"),
                    order = st.optInt("order", i + 1),
                    name = st.getString("name"),
                    subjectCount = st.optInt("subjectCount", subjects.size),
                    subjects = subjects
                )
            )
        }

        return CurriculumGraph(source, nodes, edges, stages)
    }
}
