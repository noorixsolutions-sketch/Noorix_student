package com.example.domain.repository

import android.content.Context
import android.net.Uri
import com.example.core.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.ZipInputStream

object ZipPackageReader {
    fun parseFromZip(context: Context, uri: Uri): CoursePackage? {
        val manifestJson = StringBuilder()
        val nodesJson = StringBuilder()
        val edgesJson = StringBuilder()
        val questionsJson = StringBuilder()
        val resourcesJson = StringBuilder()

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val content = zis.bufferedReader().readText()
                        when {
                            name.endsWith("manifest.json") -> manifestJson.append(content)
                            name.endsWith("nodes.json") -> nodesJson.append(content)
                            name.endsWith("edges.json") -> edgesJson.append(content)
                            name.endsWith("questions.json") -> questionsJson.append(content)
                            name.endsWith("resources.json") -> resourcesJson.append(content)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            
            if (manifestJson.isEmpty() || nodesJson.isEmpty()) return null

            val mJson = JSONObject(manifestJson.toString())
            val manifest = CoursePackageManifest(
                packageId = mJson.optString("packageId"),
                courseId = mJson.optString("courseId"),
                subjectName = mJson.optString("subjectName"),
                version = mJson.optString("version"),
                schemaVersion = mJson.optString("schemaVersion"),
                createdAt = mJson.optString("createdAt"),
                parentVersion = mJson.optString("parentVersion", null).takeIf { it.isNotEmpty() },
                signature = mJson.optString("signature"),
                contentHash = mJson.optString("contentHash")
            )

            // Let's manually parse nodes to avoid provider dependencies if complex,
            // or just use manual parsing. I'll just write simple manual parsing.
            val nJson = JSONArray(nodesJson.toString())
            val nodes = mutableListOf<KGNode>()
            for (i in 0 until nJson.length()) {
                val n = nJson.getJSONObject(i)
                nodes.add(
                    KGNode(
                        id = n.getString("id"),
                        type = NodeType.valueOf(n.getString("type")),
                        name = n.getString("name"),
                        properties = n.optJSONObject("properties")?.let { props ->
                            val map = mutableMapOf<String, String>()
                            props.keys().forEach { k -> map[k] = props.getString(k) }
                            map
                        } ?: emptyMap()
                    )
                )
            }

            val eJson = JSONArray(edgesJson.toString())
            val edges = mutableListOf<KGEdge>()
            for (i in 0 until eJson.length()) {
                val e = eJson.getJSONObject(i)
                edges.add(
                    KGEdge(
                        id = e.getString("id"),
                        type = EdgeType.valueOf(e.getString("type")),
                        source = e.getString("source"),
                        target = e.getString("target"),
                        properties = e.optJSONObject("properties")?.let { props ->
                            val map = mutableMapOf<String, String>()
                            props.keys().forEach { k -> map[k] = props.getString(k) }
                            map
                        } ?: emptyMap()
                    )
                )
            }
            
            val qJson = JSONArray(questionsJson.toString())
            val questions = mutableListOf<QuestionItem>()
            for (i in 0 until qJson.length()) {
                val q = qJson.getJSONObject(i)
                val optsJson = q.getJSONArray("options")
                val opts = List(optsJson.length()) { optsJson.getString(it) }
                val misconceptionsJson = q.optJSONArray("potentialMisconceptions")
                val misconceptions = if (misconceptionsJson != null) List(misconceptionsJson.length()) { misconceptionsJson.getString(it) } else emptyList()
                questions.add(
                    QuestionItem(
                        id = q.getString("id"),
                        courseVersionId = q.getString("courseVersionId"),
                        targetConceptId = q.getString("targetConceptId"),
                        questionType = q.getString("questionType"),
                        prompt = q.getString("prompt"),
                        options = opts,
                        correctIndex = q.getInt("correctIndex"),
                        difficulty = q.getDouble("difficulty"),
                        maxMarks = q.getInt("maxMarks"),
                        bloomLevel = q.getString("bloomLevel"),
                        targetLaTeX = q.optString("targetLaTeX", ""),
                        rubrics = q.optString("rubrics", ""),
                        prerequisiteConceptId = q.optString("prerequisiteConceptId", null).takeIf { it != "null" && it.isNotEmpty() },
                        potentialMisconceptions = misconceptions
                    )
                )
            }

            val rJson = JSONArray(resourcesJson.toString())
            val resources = mutableListOf<ResourceItem>()
            for (i in 0 until rJson.length()) {
                val r = rJson.getJSONObject(i)
                resources.add(
                    ResourceItem(
                        id = r.getString("id"),
                        conceptId = r.getString("conceptId"),
                        resourceType = r.getString("resourceType"),
                        title = r.getString("title"),
                        content = r.getString("content"),
                        contentHash = r.getString("contentHash")
                    )
                )
            }

            return CoursePackage(manifest, nodes, edges, questions, resources)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
