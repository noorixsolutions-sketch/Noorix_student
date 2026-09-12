#!/usr/bin/env python3
"""Noorix course-package generator & signer (PUBLISHER SIDE, offline).

The course-package DATA lives here (and only here). This tool:
  1. serializes each package deterministically (CANONICAL FORMAT v1, see below),
  2. computes contentHash = SHA-256(canonical),
  3. signs the canonical payload with the Noorix curriculum publisher key,
  4. writes app/src/main/assets/curriculum/<pkg>.json with hash + signature.

The Android app ships ONLY the publisher PUBLIC key (see PackageVerifier.kt),
so tampered packages can never be re-signed on device.

Usage:
  python3 tools/sign_course_package.py            # regenerate assets
  python3 tools/sign_course_package.py --verify   # CI: verify committed assets
"""
import base64
import hashlib
import json
import os
import sys

import ed25519

PUBLISHER_SEED = b"NoorixCurriculumPublisherKey2025"
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "curriculum")


def esc(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace("|", "\\|")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
    )


def props_str(properties: dict) -> str:
    if not properties:
        return ""
    return ";".join(f"{esc(k)}={esc(properties[k])}" for k in sorted(properties))


def canonicalize(pkg: dict) -> str:
    m = pkg["manifest"]
    lines = [
        "PACKAGE|" + "|".join(
            esc(x)
            for x in [
                m["packageId"], m["courseId"], m["subjectName"], m["version"],
                m["schemaVersion"], m["createdAt"],
                m.get("parentVersion") or "-",
            ]
        )
    ]
    for n in sorted(pkg["nodes"], key=lambda n: n["id"]):
        lines.append("NODE|" + "|".join(
            [esc(n["id"]), n["type"], esc(n["name"]), props_str(n.get("properties", {}))]
        ))
    for e in sorted(pkg["edges"], key=lambda e: e["id"]):
        lines.append("EDGE|" + "|".join(
            [esc(e["id"]), e["type"], esc(e["source"]), esc(e["target"]), props_str(e.get("properties", {}))]
        ))
    for q in sorted(pkg["questions"], key=lambda q: q["id"]):
        lines.append("QUESTION|" + "|".join(
            [
                esc(q["id"]),
                esc(q["courseVersionId"]),
                esc(q["targetConceptId"]),
                esc(q["questionType"]),
                esc(q["prompt"]),
                esc(";".join(q.get("options", []))),
                str(q["correctIndex"]),
                repr(float(q["difficulty"])),
                str(q.get("maxMarks", 4)),
                esc(q.get("bloomLevel", "APPLICATION")),
                esc(q.get("targetLaTeX") or "-"),
                esc(q.get("rubrics") or "-"),
                esc(q.get("prerequisiteConceptId") or "-"),
                esc(",".join(q.get("potentialMisconceptions", [])) or "-"),
            ]
        ))
    for r in sorted(pkg["resources"], key=lambda r: r["id"]):
        content_digest = hashlib.sha256(r.get("content", "").encode("utf-8")).hexdigest()
        lines.append("RESOURCE|" + "|".join(
            [
                esc(r["id"]),
                esc(r["conceptId"]),
                esc(r["resourceType"]),
                esc(r["title"]),
                content_digest,
                esc(r.get("contentHash", "")),
            ]
        ))
    return "\n".join(lines)


def sign_package(pkg: dict) -> None:
    canonical = canonicalize(pkg)
    pkg["manifest"]["contentHash"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    sig = ed25519.sign(PUBLISHER_SEED, canonical.encode("utf-8"))
    pkg["manifest"]["signature"] = base64.b64encode(sig).decode("ascii")


# ---------------------------------------------------------------------------
# Full Grade 6 to O-Level Cambridge Mathematics (4024) Package
# ---------------------------------------------------------------------------
MATH_4024 = {
    "manifest": {
        "packageId": "pkg-4024-2025-2027",
        "courseId": "course:mathematics-4024",
        "subjectName": "Mathematics (Cambridge 4024 & Grade 6-O Level)",
        "version": "2025-2027",
        "schemaVersion": "1.0",
        "createdAt": "2026-09-08T00:00:00Z",
        "parentVersion": None,
        "contentHash": "",
        "signature": "",
    },
    "nodes": [
        {"id": "qualification:cambridge-o-level", "type": "QUALIFICATION", "name": "Cambridge O Level & Lower Secondary",
         "properties": {"provider": "Cambridge Assessment International Education"}},
        {"id": "course:mathematics-4024", "type": "COURSE", "name": "Mathematics",
         "properties": {"code": "4024", "qualification": "qualification:cambridge-o-level"}},
        
        # Topics
        {"id": "topic:4024:number-foundations", "type": "TOPIC", "name": "Number & Arithmetic Foundations (Grade 6-7)",
         "properties": {"course": "course:mathematics-4024"}},
        {"id": "topic:4024:algebra", "type": "TOPIC", "name": "Algebraic Manipulation & Equations (Grade 8-O Level)",
         "properties": {"course": "course:mathematics-4024"}},
        {"id": "topic:4024:geometry-trig", "type": "TOPIC", "name": "Geometry & Trigonometry (Grade 7-O Level)",
         "properties": {"course": "course:mathematics-4024"}},

        # Concepts (Progressive from Grade 6 to Grade 11 / O-Level)
        {"id": "concept:4024:fractions-decimals", "type": "CONCEPT", "name": "Fractions, Decimals & Percentages (Grade 6)",
         "properties": {"difficulty": "0.25", "topic": "topic:4024:number-foundations", "level": "Grade 6"}},
        {"id": "concept:4024:negative-numbers", "type": "CONCEPT", "name": "Directed Numbers & Order of Operations (Grade 6-7)",
         "properties": {"difficulty": "0.30", "topic": "topic:4024:number-foundations", "level": "Grade 6-7"}},
        {"id": "concept:4024:algebraic-expressions", "type": "CONCEPT", "name": "Algebraic Expressions & Like Terms (Grade 7-8)",
         "properties": {"difficulty": "0.38", "topic": "topic:4024:algebra", "level": "Grade 7-8"}},
        {"id": "concept:4024:linear-equations", "type": "CONCEPT", "name": "Linear Equations & Transposition (Grade 8-9)",
         "properties": {"difficulty": "0.45", "topic": "topic:4024:algebra", "level": "Grade 8-9"}},
        {"id": "concept:4024:common-factors", "type": "CONCEPT", "name": "Extracting Common Factors & HCF (Grade 8-9)",
         "properties": {"difficulty": "0.48", "topic": "topic:4024:algebra", "level": "Grade 8-9"}},
        {"id": "concept:4024:factorisation", "type": "CONCEPT", "name": "Quadratic & Binomial Factorisation (Grade 9-10)",
         "properties": {"difficulty": "0.62", "topic": "topic:4024:algebra", "level": "O-Level Year 1"}},
        {"id": "concept:4024:quadratic-equations", "type": "CONCEPT", "name": "Solving Quadratic Equations (Grade 10-11)",
         "properties": {"difficulty": "0.74", "topic": "topic:4024:algebra", "level": "O-Level Year 2"}},
        {"id": "concept:4024:simultaneous-equations", "type": "CONCEPT", "name": "Simultaneous Linear & Non-Linear Equations",
         "properties": {"difficulty": "0.78", "topic": "topic:4024:algebra", "level": "O-Level Year 2"}},
        {"id": "concept:4024:trigonometry", "type": "CONCEPT", "name": "Right-Angled Trigonometry (SOH CAH TOA)",
         "properties": {"difficulty": "0.68", "topic": "topic:4024:geometry-trig", "level": "O-Level Year 1"}},

        # Misconceptions
        {"id": "misconception:factorisation:sign-error", "type": "MISCONCEPTION", "name": "Sign Inversion Error",
         "properties": {"severity": "HIGH", "description": "Failure to invert internal signs when expanding/factoring negatives: -(a-b) -> -a-b"}},
        {"id": "misconception:algebra:like-terms", "type": "MISCONCEPTION", "name": "Illegal Term Combination",
         "properties": {"severity": "MEDIUM", "description": "Combining unlike powers, e.g. x + x^2 = x^3"}},

        # Learning Objectives
        {"id": "lo:4024:fractions:01", "type": "LEARNING_OBJECTIVE", "name": "Perform four operations on fractions and mixed numbers", "properties": {"masteryThreshold": "0.85"}},
        {"id": "lo:4024:factorisation:01", "type": "LEARNING_OBJECTIVE", "name": "Factorise quadratic expressions completely into linear factors", "properties": {"masteryThreshold": "0.80"}},
        {"id": "lo:4024:quadratics:01", "type": "LEARNING_OBJECTIVE", "name": "Solve quadratic equations using factorisation and formula", "properties": {"masteryThreshold": "0.80"}},
        {"id": "lo:4024:trig:01", "type": "LEARNING_OBJECTIVE", "name": "Apply sine, cosine, and tangent ratios in 2D problems", "properties": {"masteryThreshold": "0.80"}},
    ],
    "edges": [
        {"id": "e1", "type": "CONTAINS", "source": "course:mathematics-4024", "target": "topic:4024:number-foundations"},
        {"id": "e2", "type": "CONTAINS", "source": "course:mathematics-4024", "target": "topic:4024:algebra"},
        {"id": "e3", "type": "CONTAINS", "source": "course:mathematics-4024", "target": "topic:4024:geometry-trig"},
        
        {"id": "e4", "type": "CONTAINS", "source": "topic:4024:number-foundations", "target": "concept:4024:fractions-decimals"},
        {"id": "e5", "type": "CONTAINS", "source": "topic:4024:number-foundations", "target": "concept:4024:negative-numbers"},
        {"id": "e6", "type": "CONTAINS", "source": "topic:4024:algebra", "target": "concept:4024:algebraic-expressions"},
        {"id": "e7", "type": "CONTAINS", "source": "topic:4024:algebra", "target": "concept:4024:linear-equations"},
        {"id": "e8", "type": "CONTAINS", "source": "topic:4024:algebra", "target": "concept:4024:common-factors"},
        {"id": "e9", "type": "CONTAINS", "source": "topic:4024:algebra", "target": "concept:4024:factorisation"},
        {"id": "e10", "type": "CONTAINS", "source": "topic:4024:algebra", "target": "concept:4024:quadratic-equations"},
        {"id": "e11", "type": "CONTAINS", "source": "topic:4024:algebra", "target": "concept:4024:simultaneous-equations"},
        {"id": "e12", "type": "CONTAINS", "source": "topic:4024:geometry-trig", "target": "concept:4024:trigonometry"},

        # Prerequisite Chain (Grade 6 -> O-Level)
        {"id": "pr1", "type": "PREREQUISITE_OF", "source": "concept:4024:fractions-decimals", "target": "concept:4024:negative-numbers"},
        {"id": "pr2", "type": "PREREQUISITE_OF", "source": "concept:4024:negative-numbers", "target": "concept:4024:algebraic-expressions"},
        {"id": "pr3", "type": "PREREQUISITE_OF", "source": "concept:4024:algebraic-expressions", "target": "concept:4024:linear-equations"},
        {"id": "pr4", "type": "PREREQUISITE_OF", "source": "concept:4024:algebraic-expressions", "target": "concept:4024:common-factors"},
        {"id": "pr5", "type": "PREREQUISITE_OF", "source": "concept:4024:common-factors", "target": "concept:4024:factorisation"},
        {"id": "pr6", "type": "PREREQUISITE_OF", "source": "concept:4024:factorisation", "target": "concept:4024:quadratic-equations"},
        {"id": "pr7", "type": "PREREQUISITE_OF", "source": "concept:4024:linear-equations", "target": "concept:4024:simultaneous-equations"},
        {"id": "pr8", "type": "PREREQUISITE_OF", "source": "concept:4024:linear-equations", "target": "concept:4024:trigonometry"},
        
        {"id": "em1", "type": "HAS_MISCONCEPTION", "source": "concept:4024:factorisation", "target": "misconception:factorisation:sign-error"},
        {"id": "em2", "type": "HAS_MISCONCEPTION", "source": "concept:4024:algebraic-expressions", "target": "misconception:algebra:like-terms"},
    ],
    "questions": [
        # Grade 6-7 Foundations
        {"id": "question:4024:001", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:fractions-decimals",
         "questionType": "STRUCTURED", "prompt": "Evaluate 2/3 + 3/5 in its simplest fraction form.",
         "options": ["19/15", "5/8", "13/15", "6/15"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.28, "maxMarks": 2, "bloomLevel": "KNOWLEDGE", "rubrics": "",
         "prerequisiteConceptId": None, "potentialMisconceptions": []},
        
        {"id": "question:4024:002", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:negative-numbers",
         "questionType": "STRUCTURED", "prompt": "Calculate: -8 - (-15) + (-4)",
         "options": ["3", "-27", "-19", "11"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.32, "maxMarks": 2, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:4024:fractions-decimals", "potentialMisconceptions": ["misconception:factorisation:sign-error"]},

        # Grade 7-8 Algebra Foundations
        {"id": "question:4024:003", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:algebraic-expressions",
         "questionType": "STRUCTURED", "prompt": "Simplify completely: 3(2x - 4) - 2(3x - 5)",
         "options": ["-2", "-2x - 2", "12x - 22", "2"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.42, "maxMarks": 3, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:4024:negative-numbers", "potentialMisconceptions": ["misconception:factorisation:sign-error"]},

        {"id": "question:4024:004", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:common-factors",
         "questionType": "STRUCTURED", "prompt": "Factorise completely: 8a^2b - 12ab^2",
         "options": ["4ab(2a - 3b)", "2ab(4a - 6b)", "4a(2ab - 3b^2)", "ab(8a - 12b)"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.46, "maxMarks": 2, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:4024:algebraic-expressions", "potentialMisconceptions": []},

        {"id": "question:4024:005", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:linear-equations",
         "questionType": "STRUCTURED", "prompt": "Solve for y: (3y - 5)/4 = 7",
         "options": ["y = 11", "y = 8", "y = 6", "y = 13"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.48, "maxMarks": 3, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:4024:algebraic-expressions", "potentialMisconceptions": []},

        # O-Level Core Algebra (Grade 9-11)
        {"id": "question:4024:006", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:factorisation",
         "questionType": "STRUCTURED", "prompt": "Factorise completely: 2x^2 - 7x + 3",
         "options": ["(2x - 1)(x - 3)", "(2x + 1)(x - 3)", "(2x - 3)(x - 1)", "(2x + 3)(x + 1)"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.64, "maxMarks": 3, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:4024:common-factors", "potentialMisconceptions": ["misconception:factorisation:sign-error"]},

        {"id": "question:4024:007", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:quadratic-equations",
         "questionType": "STRUCTURED", "prompt": "Solve 2x^2 + 5x - 3 = 0 for x.",
         "options": ["x = 1/2 or x = -3", "x = -1/2 or x = 3", "x = 1 or x = -3/2", "x = -1 or x = 3"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.75, "maxMarks": 4, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:4024:factorisation", "potentialMisconceptions": []},

        {"id": "question:4024:008", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:simultaneous-equations",
         "questionType": "STRUCTURED", "prompt": "Solve the simultaneous equations: 2x + 3y = 13 and 3x - y = 3",
         "options": ["x = 2, y = 3", "x = 3, y = 2", "x = 1, y = 4", "x = 4, y = 1"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.78, "maxMarks": 4, "bloomLevel": "ANALYSIS", "rubrics": "",
         "prerequisiteConceptId": "concept:4024:linear-equations", "potentialMisconceptions": []},

        {"id": "question:4024:009", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:trigonometry",
         "questionType": "STRUCTURED", "prompt": "In a right-angled triangle, hypotenuse = 10 cm and opposite side = 6 cm. Find sin(θ).",
         "options": ["0.60", "0.80", "0.75", "1.33"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.66, "maxMarks": 3, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:4024:linear-equations", "potentialMisconceptions": []},
    ],
    "resources": [
        {"id": "resource:factorisation-guide", "conceptId": "concept:4024:factorisation",
         "resourceType": "TEXT", "title": "Cambridge 4024: Comprehensive Factorisation Framework",
         "content": "Step 1: Check for highest common factors across all terms. Step 2: For trinomials ax^2+bx+c, find factors of ac that sum to b. Step 3: For difference of two squares a^2-b^2 = (a-b)(a+b).",
         "contentHash": "hash-math-res-001"},
        {"id": "resource:number-foundations", "conceptId": "concept:4024:fractions-decimals",
         "resourceType": "TEXT", "title": "Grade 6-7 Arithmetic Foundations",
         "content": "To add or subtract fractions with unlike denominators, find the lowest common multiple (LCM). To multiply, multiply numerators and denominators directly.",
         "contentHash": "hash-math-res-002"}
    ],
}

# ---------------------------------------------------------------------------
# Full Grade 6 to O-Level Cambridge Physics (5054) Package
# ---------------------------------------------------------------------------
PHYSICS_5054 = {
    "manifest": {
        "packageId": "pkg-5054-2025-2027",
        "courseId": "course:physics-5054",
        "subjectName": "Physics (Cambridge 5054 & Grade 6-O Level)",
        "version": "2025-2027",
        "schemaVersion": "1.0",
        "createdAt": "2026-09-08T00:00:00Z",
        "parentVersion": None,
        "contentHash": "",
        "signature": "",
    },
    "nodes": [
        {"id": "course:physics-5054", "type": "COURSE", "name": "Physics 5054 & Lower Secondary Science", "properties": {}},
        {"id": "topic:5054:mechanics", "type": "TOPIC", "name": "Forces & Motion (Grade 6-O Level)", "properties": {}},
        {"id": "topic:5054:energy-thermal", "type": "TOPIC", "name": "Energy, Work & Thermal Physics", "properties": {}},
        {"id": "topic:5054:electricity-waves", "type": "TOPIC", "name": "Electricity, Magnetism & Waves", "properties": {}},

        # Concepts
        {"id": "concept:5054:measurement-units", "type": "CONCEPT", "name": "Measurement, Units & Density (Grade 6-7)", "properties": {"difficulty": "0.30"}},
        {"id": "concept:5054:speed-motion", "type": "CONCEPT", "name": "Speed & Distance-Time Graphs (Grade 7-8)", "properties": {"difficulty": "0.38"}},
        {"id": "concept:5054:kinematics", "type": "CONCEPT", "name": "Velocity, Acceleration & v-t Graphs (Grade 9-10)", "properties": {"difficulty": "0.55"}},
        {"id": "concept:5054:forces", "type": "CONCEPT", "name": "Newton's Laws & Resultant Forces (Grade 9-10)", "properties": {"difficulty": "0.62"}},
        {"id": "concept:5054:energy", "type": "CONCEPT", "name": "Work, Kinetic Energy & Conservation (Grade 9-11)", "properties": {"difficulty": "0.68"}},
        {"id": "concept:5054:electricity", "type": "CONCEPT", "name": "Current, Voltage & Ohm's Law (Grade 8-O Level)", "properties": {"difficulty": "0.72"}},
    ],
    "edges": [
        {"id": "pe1", "type": "CONTAINS", "source": "course:physics-5054", "target": "topic:5054:mechanics"},
        {"id": "pe2", "type": "CONTAINS", "source": "course:physics-5054", "target": "topic:5054:energy-thermal"},
        {"id": "pe3", "type": "CONTAINS", "source": "course:physics-5054", "target": "topic:5054:electricity-waves"},

        {"id": "pe4", "type": "CONTAINS", "source": "topic:5054:mechanics", "target": "concept:5054:measurement-units"},
        {"id": "pe5", "type": "CONTAINS", "source": "topic:5054:mechanics", "target": "concept:5054:speed-motion"},
        {"id": "pe6", "type": "CONTAINS", "source": "topic:5054:mechanics", "target": "concept:5054:kinematics"},
        {"id": "pe7", "type": "CONTAINS", "source": "topic:5054:mechanics", "target": "concept:5054:forces"},
        {"id": "pe8", "type": "CONTAINS", "source": "topic:5054:energy-thermal", "target": "concept:5054:energy"},
        {"id": "pe9", "type": "CONTAINS", "source": "topic:5054:electricity-waves", "target": "concept:5054:electricity"},

        # Prerequisite links
        {"id": "ppr1", "type": "PREREQUISITE_OF", "source": "concept:5054:measurement-units", "target": "concept:5054:speed-motion"},
        {"id": "ppr2", "type": "PREREQUISITE_OF", "source": "concept:5054:speed-motion", "target": "concept:5054:kinematics"},
        {"id": "ppr3", "type": "PREREQUISITE_OF", "source": "concept:5054:kinematics", "target": "concept:5054:forces"},
        {"id": "ppr4", "type": "PREREQUISITE_OF", "source": "concept:5054:forces", "target": "concept:5054:energy"},
    ],
    "questions": [
        {"id": "question:5054:001", "courseVersionId": "2025-2027", "targetConceptId": "concept:5054:measurement-units",
         "questionType": "STRUCTURED", "prompt": "A block of wood has mass 240 g and volume 300 cm³. What is its density?",
         "options": ["0.80 g/cm³", "1.25 g/cm³", "72.0 g/cm³", "60 g/cm³"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.30, "maxMarks": 2, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": None, "potentialMisconceptions": []},

        {"id": "question:5054:002", "courseVersionId": "2025-2027", "targetConceptId": "concept:5054:speed-motion",
         "questionType": "STRUCTURED", "prompt": "A car travels 150 meters in 6.0 seconds at constant speed. What is its speed?",
         "options": ["25 m/s", "900 m/s", "0.04 m/s", "156 m/s"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.35, "maxMarks": 2, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:5054:measurement-units", "potentialMisconceptions": []},

        {"id": "question:5054:003", "courseVersionId": "2025-2027", "targetConceptId": "concept:5054:kinematics",
         "questionType": "STRUCTURED", "prompt": "A cyclist accelerates uniformly from 4.0 m/s to 12.0 m/s in 4.0 s. What is the acceleration?",
         "options": ["2.0 m/s²", "4.0 m/s²", "3.0 m/s²", "8.0 m/s²"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.52, "maxMarks": 3, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:5054:speed-motion", "potentialMisconceptions": []},

        {"id": "question:5054:004", "courseVersionId": "2025-2027", "targetConceptId": "concept:5054:forces",
         "questionType": "STRUCTURED", "prompt": "A resultant force of 18 N acts on a mass of 3.0 kg. What is the acceleration produced?",
         "options": ["6.0 m/s²", "54 m/s²", "0.17 m/s²", "15 m/s²"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.58, "maxMarks": 3, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:5054:kinematics", "potentialMisconceptions": []},

        {"id": "question:5054:005", "courseVersionId": "2025-2027", "targetConceptId": "concept:5054:energy",
         "questionType": "STRUCTURED", "prompt": "Calculate the kinetic energy of a 4.0 kg ball moving at 5.0 m/s (KE = 1/2 m v²).",
         "options": ["50 J", "100 J", "20 J", "10 J"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.65, "maxMarks": 3, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:5054:forces", "potentialMisconceptions": []},

        {"id": "question:5054:006", "courseVersionId": "2025-2027", "targetConceptId": "concept:5054:electricity",
         "questionType": "STRUCTURED", "prompt": "A 12 V battery is connected across a 4.0 Ω resistor. What is the current flowing?",
         "options": ["3.0 A", "48 A", "0.33 A", "8.0 A"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.70, "maxMarks": 3, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": None, "potentialMisconceptions": []},
    ],
    "resources": [
        {"id": "resource:newton-laws", "conceptId": "concept:5054:forces",
         "resourceType": "TEXT", "title": "Cambridge 5054: Dynamics & Newton's Laws",
         "content": "Newton's First Law: Objects remain at rest or in uniform motion unless acted upon by a resultant force. Newton's Second Law: F = ma. Newton's Third Law: Action and reaction forces are equal and opposite.",
         "contentHash": "hash-phys-res-001"}
    ],
}

PACKAGES = {
    "math_4024.json": MATH_4024,
    "physics_5054.json": PHYSICS_5054,
}


def main() -> int:
    ed25519.self_test()
    print("publisher public key (base64):",
          base64.b64encode(ed25519.public_key_from_seed(PUBLISHER_SEED)).decode())

    if "--verify" in sys.argv:
        ok = True
        for filename, expected in PACKAGES.items():
            path = os.path.join(ASSET_DIR, filename)
            with open(path, "r", encoding="utf-8") as f:
                actual = json.load(f)
            expected["manifest"]["contentHash"] = actual["manifest"]["contentHash"]
            expected["manifest"]["signature"] = actual["manifest"]["signature"]
            canonical = canonicalize(actual)
            digest = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
            sig_ok = ed25519.verify(
                ed25519.public_key_from_seed(PUBLISHER_SEED),
                canonical.encode("utf-8"),
                base64.b64decode(actual["manifest"]["signature"]),
            )
            hash_ok = digest == actual["manifest"]["contentHash"]
            if hash_ok and sig_ok:
                print(f"OK    {filename}: hash + signature verified")
            else:
                ok = False
                print(f"FAIL  {filename}: hash_ok={hash_ok} sig_ok={sig_ok}")
        return 0 if ok else 1

    for filename, pkg in PACKAGES.items():
        sign_package(pkg)
        path = os.path.join(ASSET_DIR, filename)
        os.makedirs(ASSET_DIR, exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(pkg, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print(f"wrote {path}  contentHash={pkg['manifest']['contentHash']}")
    print("done.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
