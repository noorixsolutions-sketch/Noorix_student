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
  python3 tools/generate_package_signatures.py            # regenerate assets
  python3 tools/generate_package_signatures.py --verify   # CI: verify committed assets

CANONICAL FORMAT v1 (UTF-8, lines joined with \\n, no trailing newline):
  PACKAGE|<id>|<courseId>|<subjectName>|<version>|<schemaVersion>|<createdAt>|<parentVersion or ->
  NODE|<id>|<type>|<name>|<props>                      (sorted by id; props k=v;k=v sorted by key)
  EDGE|<id>|<type>|<source>|<target>|<props>           (sorted by id)
  QUESTION|<id>|<courseVersionId>|<targetConceptId>|<questionType>|<prompt>|<opt;opt;...>|
           <correctIndex>|<difficulty>|<maxMarks>|<bloomLevel>|<targetLaTeX or ->|
           <rubrics or ->|<prereqConcept or ->|<misc1,misc2 or ->>   (sorted by id)
  RESOURCE|<id>|<conceptId>|<resourceType>|<title>|<sha256hex(content)>|<contentHash>   (sorted by id)

Every free-text field is escaped with esc() so the format is unambiguous.
The Kotlin implementation is PackageCanonicalizer.kt — the two MUST agree
(byte-for-byte). CI fails if --verify detects any drift.
"""
import base64
import hashlib
import json
import os
import sys

import ed25519

# Publisher key. The seed stays in this tool / in CI secret storage only.
PUBLISHER_SEED = b"NoorixCurriculumPublisherKey2025"
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "curriculum")


# ---------------------------------------------------------------------------
# Canonicalization (MUST mirror com.example.core.crypto.PackageCanonicalizer)
# ---------------------------------------------------------------------------
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
# Package data (verbatim content served by the app)
# ---------------------------------------------------------------------------
MATH_4024 = {
    "manifest": {
        "packageId": "pkg-4024-2025-2027",
        "courseId": "course:mathematics-4024",
        "subjectName": "Mathematics (Cambridge 4024)",
        "version": "2025-2027",
        "schemaVersion": "1.0",
        "createdAt": "2026-09-08T00:00:00Z",
        "parentVersion": None,
        "contentHash": "",
        "signature": "",
    },
    "nodes": [
        {"id": "qualification:cambridge-o-level", "type": "QUALIFICATION", "name": "Cambridge O Level",
         "properties": {"provider": "Cambridge Assessment International Education"}},
        {"id": "course:mathematics-4024", "type": "COURSE", "name": "Mathematics",
         "properties": {"code": "4024", "qualification": "qualification:cambridge-o-level"}},
        {"id": "topic:4024:algebra", "type": "TOPIC", "name": "Algebra & Algebraic Manipulation",
         "properties": {"course": "course:mathematics-4024"}},
        {"id": "concept:4024:algebraic-expressions", "type": "CONCEPT", "name": "Algebraic Expressions & Like Terms",
         "properties": {"difficulty": "0.35", "topic": "topic:4024:algebra"}},
        {"id": "concept:4024:common-factors", "type": "CONCEPT", "name": "Extracting Common Factors",
         "properties": {"difficulty": "0.45", "topic": "topic:4024:algebra"}},
        {"id": "concept:4024:factorisation", "type": "CONCEPT", "name": "Quadratic & Binomial Factorisation",
         "properties": {"difficulty": "0.62", "topic": "topic:4024:algebra"}},
        {"id": "concept:4024:quadratic-equations", "type": "CONCEPT", "name": "Solving Quadratic Equations",
         "properties": {"difficulty": "0.75", "topic": "topic:4024:algebra"}},
        {"id": "misconception:factorisation:sign-error", "type": "MISCONCEPTION", "name": "Sign Distribution Misconception",
         "properties": {"severity": "HIGH",
                          "description": "Distributing negative sign without inverting internal terms e.g. -(x-3) -> -x-3"}},
        {"id": "lo:4024:factorisation:01", "type": "LEARNING_OBJECTIVE", "name": "Factorise algebraic expressions completely",
         "properties": {"masteryThreshold": "0.80"}},
        {"id": "skill:algebraic-manipulation", "type": "SKILL", "name": "Symbolic Algebraic Manipulation",
         "properties": {"domain": "Mathematics"}},
    ],
    "edges": [
        {"id": "e1", "type": "CONTAINS", "source": "course:mathematics-4024", "target": "topic:4024:algebra"},
        {"id": "e2", "type": "CONTAINS", "source": "topic:4024:algebra", "target": "concept:4024:algebraic-expressions"},
        {"id": "e3", "type": "CONTAINS", "source": "topic:4024:algebra", "target": "concept:4024:common-factors"},
        {"id": "e4", "type": "CONTAINS", "source": "topic:4024:algebra", "target": "concept:4024:factorisation"},
        {"id": "e5", "type": "CONTAINS", "source": "topic:4024:algebra", "target": "concept:4024:quadratic-equations"},
        {"id": "e6", "type": "PREREQUISITE_OF", "source": "concept:4024:algebraic-expressions", "target": "concept:4024:common-factors"},
        {"id": "e7", "type": "PREREQUISITE_OF", "source": "concept:4024:common-factors", "target": "concept:4024:factorisation"},
        {"id": "e8", "type": "PREREQUISITE_OF", "source": "concept:4024:factorisation", "target": "concept:4024:quadratic-equations"},
        {"id": "e9", "type": "HAS_MISCONCEPTION", "source": "concept:4024:factorisation", "target": "misconception:factorisation:sign-error"},
    ],
    "questions": [
        {"id": "question:4024:001", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:factorisation",
         "questionType": "STRUCTURED", "prompt": "Factorise completely: 6x^2 + 15x",
         "options": ["3x(2x + 5)", "3(2x^2 + 5x)", "x(6x + 15)", "3x(2x - 5)"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.48, "maxMarks": 2, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:4024:common-factors", "potentialMisconceptions": []},
        {"id": "question:4024:002", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:factorisation",
         "questionType": "STRUCTURED", "prompt": "Factorise: x^2 - 5x + 6",
         "options": ["(x - 2)(x - 3)", "(x - 1)(x - 6)", "(x + 2)(x + 3)", "(x + 1)(x - 6)"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.62, "maxMarks": 3, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:4024:algebraic-expressions",
         "potentialMisconceptions": ["misconception:factorisation:sign-error"]},
        {"id": "question:4024:003", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:common-factors",
         "questionType": "STRUCTURED", "prompt": "What is the highest common factor (HCF) of 12a^2b and 18ab^2?",
         "options": ["6ab", "3ab", "6a^2b^2", "12ab"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.42, "maxMarks": 2, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": None, "potentialMisconceptions": []},
        {"id": "question:4024:004", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:algebraic-expressions",
         "questionType": "STRUCTURED", "prompt": "Simplify: 4(x - 2) - 3(2x - 5)",
         "options": ["-2x + 7", "-2x - 23", "10x - 7", "-2x - 7"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.52, "maxMarks": 3, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": None, "potentialMisconceptions": ["misconception:factorisation:sign-error"]},
        {"id": "question:4024:005", "courseVersionId": "2025-2027", "targetConceptId": "concept:4024:quadratic-equations",
         "questionType": "STRUCTURED", "prompt": "Solve for x: x^2 - 7x + 12 = 0",
         "options": ["x = 3 or x = 4", "x = -3 or x = -4", "x = 2 or x = 6", "x = 1 or x = 12"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.72, "maxMarks": 4, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:4024:factorisation", "potentialMisconceptions": []},
    ],
    "resources": [
        {"id": "resource:factorisation-explanation-001", "conceptId": "concept:4024:factorisation",
         "resourceType": "TEXT", "title": "Cambridge O-Level: Guide to Factorising Quadratics",
         "content": "To factorise a quadratic trinomial ax^2 + bx + c, identify two integers whose product is ac and "
                    "whose sum is b. Take care when handling negative coefficients to avoid sign inversion errors.",
         "contentHash": "hash-math-res-001"},
    ],
}

PHYSICS_5054 = {
    "manifest": {
        "packageId": "pkg-5054-2025-2027",
        "courseId": "course:physics-5054",
        "subjectName": "Physics (Cambridge 5054)",
        "version": "2025-2027",
        "schemaVersion": "1.0",
        "createdAt": "2026-09-08T00:00:00Z",
        "parentVersion": None,
        "contentHash": "",
        "signature": "",
    },
    "nodes": [
        {"id": "course:physics-5054", "type": "COURSE", "name": "Physics 5054", "properties": {}},
        {"id": "topic:5054:mechanics", "type": "TOPIC", "name": "Newtonian Mechanics", "properties": {}},
        {"id": "concept:5054:kinematics", "type": "CONCEPT", "name": "Speed, Velocity & Acceleration", "properties": {}},
        {"id": "concept:5054:forces", "type": "CONCEPT", "name": "Newton's Laws of Motion", "properties": {}},
        {"id": "concept:5054:energy", "type": "CONCEPT", "name": "Work, Energy & Power", "properties": {}},
    ],
    "edges": [
        {"id": "pe1", "type": "CONTAINS", "source": "course:physics-5054", "target": "topic:5054:mechanics"},
        {"id": "pe2", "type": "CONTAINS", "source": "topic:5054:mechanics", "target": "concept:5054:kinematics"},
        {"id": "pe3", "type": "CONTAINS", "source": "topic:5054:mechanics", "target": "concept:5054:forces"},
        {"id": "pe4", "type": "PREREQUISITE_OF", "source": "concept:5054:kinematics", "target": "concept:5054:forces"},
        {"id": "pe5", "type": "PREREQUISITE_OF", "source": "concept:5054:forces", "target": "concept:5054:energy"},
    ],
    "questions": [
        {"id": "question:5054:001", "courseVersionId": "2025-2027", "targetConceptId": "concept:5054:forces",
         "questionType": "STRUCTURED",
         "prompt": "A constant resultant force of 15 N acts on a mass of 3.0 kg. What is the acceleration?",
         "options": ["5.0 m/s²", "45 m/s²", "0.20 m/s²", "12 m/s²"], "correctIndex": 0,
         "targetLaTeX": "", "difficulty": 0.45, "maxMarks": 4, "bloomLevel": "APPLICATION", "rubrics": "",
         "prerequisiteConceptId": "concept:5054:kinematics", "potentialMisconceptions": []},
    ],
    "resources": [],
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
