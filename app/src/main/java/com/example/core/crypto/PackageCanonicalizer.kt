package com.example.core.crypto

import com.example.core.model.CoursePackage
import com.example.core.model.KGEdge
import com.example.core.model.KGNode
import com.example.core.model.QuestionItem
import com.example.core.model.ResourceItem
import java.security.MessageDigest

/**
 * Deterministic canonical serialization of a [CoursePackage].
 *
 * THE canonical byte layout (CANONICAL FORMAT v1) is:
 *   - UTF-8 text, lines joined with '\n', no trailing newline
 *   - PACKAGE|packageId|courseId|subjectName|version|schemaVersion|createdAt|parentVersion-or-dash
 *   - NODE|id|type|name|props            (sorted by id; props k=v;k=v sorted by key)
 *   - EDGE|id|type|source|target|props   (sorted by id)
 *   - QUESTION|id|courseVersionId|targetConceptId|questionType|prompt|opt;opt;...|correctIndex|
 *     difficulty|maxMarks|bloomLevel|targetLaTeX-or-dash|rubrics-or-dash|prereq-or-dash|misc1,misc2-or-dash
 *     (sorted by id)
 *   - RESOURCE|id|conceptId|resourceType|title|sha256hex(content)|contentHash  (sorted by id)
 *
 * Every free-text field passes through [esc].
 *
 * THIS MUST AGREE BYTE-FOR-BYTE with tools/generate_package_signatures.py (Python,
 * publisher side). The unit test PackageCanonicalizerTest pins the math/physics
 * packages' content hashes, and CI re-runs the Python tool in --verify mode —
 * any drift on either side fails the build.
 */
object PackageCanonicalizer {

    fun esc(value: String): String = value
        .replace("\\", "\\\\")
        .replace("|", "\\|")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun propsStr(properties: Map<String, String>): String {
        if (properties.isEmpty()) return ""
        return properties.toSortedMap().entries.joinToString(";") { (k, v) -> "${esc(k)}=${esc(v)}" }
    }

    fun sha256Hex(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun nodeLine(n: KGNode): String =
        "NODE|" + esc(n.id) + "|" + n.type.name + "|" + esc(n.name) + "|" + propsStr(n.properties)

    private fun edgeLine(e: KGEdge): String =
        "EDGE|" + esc(e.id) + "|" + e.type.name + "|" + esc(e.source) + "|" + esc(e.target) + "|" + propsStr(e.properties)

    private fun questionLine(q: QuestionItem): String =
        "QUESTION|" +
            esc(q.id) + "|" +
            esc(q.courseVersionId) + "|" +
            esc(q.targetConceptId) + "|" +
            esc(q.questionType) + "|" +
            esc(q.prompt) + "|" +
            esc(q.options.joinToString(";")) + "|" +
            q.correctIndex + "|" +
            q.difficulty.toString() + "|" +
            q.maxMarks + "|" +
            esc(q.bloomLevel) + "|" +
            esc(q.targetLaTeX.ifEmpty { "-" }) + "|" +
            esc(q.rubrics.ifEmpty { "-" }) + "|" +
            esc(q.prerequisiteConceptId ?: "-") + "|" +
            esc(q.potentialMisconceptions.joinToString(",").ifEmpty { "-" })

    private fun resourceLine(r: ResourceItem): String =
        "RESOURCE|" +
            esc(r.id) + "|" +
            esc(r.conceptId) + "|" +
            esc(r.resourceType) + "|" +
            esc(r.title) + "|" +
            sha256Hex(r.content) + "|" +
            esc(r.contentHash)

    fun canonicalize(pkg: CoursePackage): String {
        val m = pkg.manifest
        val sb = StringBuilder()
        fun line(s: String) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(s)
        }

        line(
            "PACKAGE|" +
                esc(m.packageId) + "|" +
                esc(m.courseId) + "|" +
                esc(m.subjectName) + "|" +
                esc(m.version) + "|" +
                esc(m.schemaVersion) + "|" +
                esc(m.createdAt) + "|" +
                esc(m.parentVersion ?: "-")
        )
        pkg.nodes.sortedBy { it.id }.forEach { line(nodeLine(it)) }
        pkg.edges.sortedBy { it.id }.forEach { line(edgeLine(it)) }
        pkg.questions.sortedBy { it.id }.forEach { line(questionLine(it)) }
        pkg.resources.sortedBy { it.id }.forEach { line(resourceLine(it)) }
        return sb.toString()
    }

    /** SHA-256 hex of the canonical payload (what the manifest.contentHash must equal). */
    fun contentHashOf(pkg: CoursePackage): String = sha256Hex(canonicalize(pkg))
}
