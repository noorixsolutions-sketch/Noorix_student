package com.example.data.curriculum

import com.example.core.crypto.CoursePackageSigner
import com.example.core.crypto.PackageVerifier
import com.example.core.model.CoursePackage
import com.example.core.model.CoursePackageManifest
import com.example.core.model.EdgeType
import com.example.core.model.KGEdge
import com.example.core.model.KGNode
import com.example.core.model.NodeType
import com.example.core.model.Provenance
import com.example.core.model.QuestionItem
import com.example.core.model.ResourceItem
import org.json.JSONArray
import org.json.JSONObject

object CoursePackageProvider {

    /**
     * Builds the official Mathematics 4024 Course Package (Version 2025-2027).
     */
    fun createMath4024Package(): CoursePackage {
        val mathHash = "05bc022f52109f283911abb29022dcde97f00cc94fdbd72544453f188fb40d78"
        val manifest = CoursePackageManifest(
            packageId = "pkg-4024-2025-2027",
            courseId = "course:mathematics-4024",
            subjectName = "Mathematics (Cambridge 4024)",
            version = "2025-2027",
            schemaVersion = "1.0",
            createdAt = "2026-09-08T00:00:00Z",
            contentHash = mathHash,
            parentVersion = null,
            signature = CoursePackageSigner.signHash(mathHash)
        )

        val nodes = listOf(
            KGNode(
                id = "qualification:cambridge-o-level",
                type = NodeType.QUALIFICATION,
                name = "Cambridge O Level",
                properties = mapOf("provider" to "Cambridge Assessment International Education")
            ),
            KGNode(
                id = "course:mathematics-4024",
                type = NodeType.COURSE,
                name = "Mathematics",
                properties = mapOf("code" to "4024", "qualification" to "qualification:cambridge-o-level")
            ),
            KGNode(
                id = "topic:4024:algebra",
                type = NodeType.TOPIC,
                name = "Algebra & Algebraic Manipulation",
                properties = mapOf("course" to "course:mathematics-4024")
            ),
            KGNode(
                id = "concept:4024:algebraic-expressions",
                type = NodeType.CONCEPT,
                name = "Algebraic Expressions & Like Terms",
                properties = mapOf("difficulty" to "0.35", "topic" to "topic:4024:algebra")
            ),
            KGNode(
                id = "concept:4024:common-factors",
                type = NodeType.CONCEPT,
                name = "Extracting Common Factors",
                properties = mapOf("difficulty" to "0.45", "topic" to "topic:4024:algebra")
            ),
            KGNode(
                id = "concept:4024:factorisation",
                type = NodeType.CONCEPT,
                name = "Quadratic & Binomial Factorisation",
                properties = mapOf("difficulty" to "0.62", "topic" to "topic:4024:algebra")
            ),
            KGNode(
                id = "concept:4024:quadratic-equations",
                type = NodeType.CONCEPT,
                name = "Solving Quadratic Equations",
                properties = mapOf("difficulty" to "0.75", "topic" to "topic:4024:algebra")
            ),
            KGNode(
                id = "misconception:factorisation:sign-error",
                type = NodeType.MISCONCEPTION,
                name = "Sign Distribution Misconception",
                properties = mapOf("severity" to "HIGH", "description" to "Distributing negative sign without inverting internal terms e.g. -(x-3) -> -x-3")
            ),
            KGNode(
                id = "lo:4024:factorisation:01",
                type = NodeType.LEARNING_OBJECTIVE,
                name = "Factorise algebraic expressions completely",
                properties = mapOf("masteryThreshold" to "0.80")
            ),
            KGNode(
                id = "skill:algebraic-manipulation",
                type = NodeType.SKILL,
                name = "Symbolic Algebraic Manipulation",
                properties = mapOf("domain" to "Mathematics")
            )
        )

        val edges = listOf(
            KGEdge("e1", EdgeType.CONTAINS, "course:mathematics-4024", "topic:4024:algebra"),
            KGEdge("e2", EdgeType.CONTAINS, "topic:4024:algebra", "concept:4024:algebraic-expressions"),
            KGEdge("e3", EdgeType.CONTAINS, "topic:4024:algebra", "concept:4024:common-factors"),
            KGEdge("e4", EdgeType.CONTAINS, "topic:4024:algebra", "concept:4024:factorisation"),
            KGEdge("e5", EdgeType.CONTAINS, "topic:4024:algebra", "concept:4024:quadratic-equations"),
            KGEdge("e6", EdgeType.PREREQUISITE_OF, "concept:4024:algebraic-expressions", "concept:4024:common-factors"),
            KGEdge("e7", EdgeType.PREREQUISITE_OF, "concept:4024:common-factors", "concept:4024:factorisation"),
            KGEdge("e8", EdgeType.PREREQUISITE_OF, "concept:4024:factorisation", "concept:4024:quadratic-equations"),
            KGEdge("e9", EdgeType.HAS_MISCONCEPTION, "concept:4024:factorisation", "misconception:factorisation:sign-error")
        )

        val questions = listOf(
            QuestionItem(
                id = "question:4024:001",
                courseVersionId = manifest.version,
                targetConceptId = "concept:4024:factorisation",
                questionType = "STRUCTURED",
                prompt = "Factorise completely: 6x^2 + 15x",
                options = listOf("3x(2x + 5)", "3(2x^2 + 5x)", "x(6x + 15)", "3x(2x - 5)"),
                correctIndex = 0,
                difficulty = 0.48,
                maxMarks = 2,
                bloomLevel = "APPLICATION",
                prerequisiteConceptId = "concept:4024:common-factors"
            ),
            QuestionItem(
                id = "question:4024:002",
                courseVersionId = manifest.version,
                targetConceptId = "concept:4024:factorisation",
                questionType = "STRUCTURED",
                prompt = "Factorise: x^2 - 5x + 6",
                options = listOf("(x - 2)(x - 3)", "(x - 1)(x - 6)", "(x + 2)(x + 3)", "(x + 1)(x - 6)"),
                correctIndex = 0,
                difficulty = 0.62,
                maxMarks = 3,
                bloomLevel = "APPLICATION",
                prerequisiteConceptId = "concept:4024:algebraic-expressions",
                potentialMisconceptions = listOf("misconception:factorisation:sign-error")
            ),
            QuestionItem(
                id = "question:4024:003",
                courseVersionId = manifest.version,
                targetConceptId = "concept:4024:common-factors",
                questionType = "STRUCTURED",
                prompt = "What is the highest common factor (HCF) of 12a^2b and 18ab^2?",
                options = listOf("6ab", "3ab", "6a^2b^2", "12ab"),
                correctIndex = 0,
                difficulty = 0.42,
                maxMarks = 2,
                bloomLevel = "APPLICATION"
            ),
            QuestionItem(
                id = "question:4024:004",
                courseVersionId = manifest.version,
                targetConceptId = "concept:4024:algebraic-expressions",
                questionType = "STRUCTURED",
                prompt = "Simplify: 4(x - 2) - 3(2x - 5)",
                options = listOf("-2x + 7", "-2x - 23", "10x - 7", "-2x - 7"),
                correctIndex = 0,
                difficulty = 0.52,
                maxMarks = 3,
                bloomLevel = "APPLICATION",
                potentialMisconceptions = listOf("misconception:factorisation:sign-error")
            ),
            QuestionItem(
                id = "question:4024:005",
                courseVersionId = manifest.version,
                targetConceptId = "concept:4024:quadratic-equations",
                questionType = "STRUCTURED",
                prompt = "Solve for x: x^2 - 7x + 12 = 0",
                options = listOf("x = 3 or x = 4", "x = -3 or x = -4", "x = 2 or x = 6", "x = 1 or x = 12"),
                correctIndex = 0,
                difficulty = 0.72,
                maxMarks = 4,
                bloomLevel = "APPLICATION",
                prerequisiteConceptId = "concept:4024:factorisation"
            )
        )

        val resources = listOf(
            ResourceItem(
                id = "resource:factorisation-explanation-001",
                conceptId = "concept:4024:factorisation",
                resourceType = "TEXT",
                title = "Cambridge O-Level: Guide to Factorising Quadratics",
                content = "To factorise a quadratic trinomial ax^2 + bx + c, identify two integers whose product is ac and whose sum is b. Take care when handling negative coefficients to avoid sign inversion errors.",
                contentHash = "hash-math-res-001"
            )
        )

        return CoursePackage(manifest, nodes, edges, questions, resources)
    }

    /**
     * Builds Cambridge Physics 5054 Course Package to demonstrate runtime agnosticism:
     * APK requires ZERO code changes to load or learn physics!
     */
    fun createPhysics5054Package(): CoursePackage {
        val physHash = "aefdf60e7db7e88f97ae022422c487ad1496c4e86ff9a5c4b865768cfe8de397"
        val manifest = CoursePackageManifest(
            packageId = "pkg-5054-2025-2027",
            courseId = "course:physics-5054",
            subjectName = "Physics (Cambridge 5054)",
            version = "2025-2027",
            schemaVersion = "1.0",
            createdAt = "2026-09-08T00:00:00Z",
            contentHash = physHash,
            signature = CoursePackageSigner.signHash(physHash)
        )

        val nodes = listOf(
            KGNode("course:physics-5054", NodeType.COURSE, "Physics 5054"),
            KGNode("topic:5054:mechanics", NodeType.TOPIC, "Newtonian Mechanics"),
            KGNode("concept:5054:kinematics", NodeType.CONCEPT, "Speed, Velocity & Acceleration"),
            KGNode("concept:5054:forces", NodeType.CONCEPT, "Newton's Laws of Motion"),
            KGNode("concept:5054:energy", NodeType.CONCEPT, "Work, Energy & Power")
        )

        val edges = listOf(
            KGEdge("pe1", EdgeType.CONTAINS, "course:physics-5054", "topic:5054:mechanics"),
            KGEdge("pe2", EdgeType.CONTAINS, "topic:5054:mechanics", "concept:5054:kinematics"),
            KGEdge("pe3", EdgeType.CONTAINS, "topic:5054:mechanics", "concept:5054:forces"),
            KGEdge("pe4", EdgeType.PREREQUISITE_OF, "concept:5054:kinematics", "concept:5054:forces"),
            KGEdge("pe5", EdgeType.PREREQUISITE_OF, "concept:5054:forces", "concept:5054:energy")
        )

        val questions = listOf(
            QuestionItem(
                id = "question:5054:001",
                courseVersionId = manifest.version,
                targetConceptId = "concept:5054:forces",
                questionType = "STRUCTURED",
                prompt = "A constant resultant force of 15 N acts on a mass of 3.0 kg. What is the acceleration?",
                options = listOf("5.0 m/s²", "45 m/s²", "0.20 m/s²", "12 m/s²"),
                correctIndex = 0,
                difficulty = 0.45,
                bloomLevel = "APPLICATION",
                prerequisiteConceptId = "concept:5054:kinematics"
            )
        )

        return CoursePackage(manifest, nodes, edges, questions, emptyList())
    }

    /**
     * Serializes package to JSON string for integrity hash calculation.
     */
    fun serializePackageToJson(pkg: CoursePackage): String {
        val root = JSONObject()
        val m = JSONObject()
        m.put("packageId", pkg.manifest.packageId)
        m.put("courseId", pkg.manifest.courseId)
        m.put("subjectName", pkg.manifest.subjectName)
        m.put("version", pkg.manifest.version)
        m.put("schemaVersion", pkg.manifest.schemaVersion)
        m.put("createdAt", pkg.manifest.createdAt)
        m.put("signature", pkg.manifest.signature)
        root.put("manifest", m)

        val nodesArr = JSONArray()
        for (n in pkg.nodes) {
            val no = JSONObject()
            no.put("id", n.id)
            no.put("name", n.name)
            no.put("type", n.type.name)
            nodesArr.put(no)
        }
        root.put("nodes", nodesArr)

        val edgesArr = JSONArray()
        for (e in pkg.edges) {
            val eo = JSONObject()
            eo.put("id", e.id)
            eo.put("type", e.type.name)
            eo.put("source", e.source)
            eo.put("target", e.target)
            edgesArr.put(eo)
        }
        root.put("edges", edgesArr)

        return root.toString()
    }
}
