#!/usr/bin/env python3
"""Build the Noorix Curriculum Knowledge Graph from the `master` branch's
Cambridge_Curriculum_Library (9 stages, 165 subjects, 5 content areas each).

Output: app/src/main/assets/curriculum/knowledge_graph.json
  - source:     provenance (branch, commit, generator, generatedAt)
  - nodes:      STAGE -> SUBJECT -> RESOURCE(areas) graph nodes
  - edges:      STAGE CONTAINS SUBJECT, SUBJECT CONTAINS RESOURCE
  - stages:     UI-friendly tree (ordered stages with subject details)

Usage:
  # 1. extract the library from the master branch:
  git fetch origin master
  git archive origin/master Cambridge_Curriculum_Library | tar -x -C /tmp/kg
  # 2. generate:
  python3 tools/generate_kg.py /tmp/kg/Cambridge_Curriculum_Library [master_commit]
"""
import json
import os
import sys
from datetime import datetime, timezone

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_PATH = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "curriculum", "knowledge_graph.json")

AREAS = ["Syllabus", "Past_Papers", "Mark_Schemes", "Notes", "Resources"]
AREA_TYPE = {
    "Syllabus": "SYLLABUS",
    "Past_Papers": "PAST_PAPERS",
    "Mark_Schemes": "MARK_SCHEMES",
    "Notes": "NOTES",
    "Resources": "RESOURCES",
}


def slug(s: str) -> str:
    return s.strip().lower().replace(" ", "_")


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    lib = os.path.abspath(sys.argv[1])
    commit = sys.argv[2] if len(sys.argv) > 2 else "unknown"
    manifest_path = os.path.join(lib, "manifest.json")
    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    nodes = []
    edges = []
    stages_out = []
    total_subjects = 0
    total_pdfs = 0

    for stage in sorted(manifest.get("stages", {}), key=lambda k: int(k.split("_", 1)[0])):
        stage_info = manifest["stages"][stage]
        stage_dir = os.path.join(lib, stage)
        stage_id = f"stage:{slug(stage)}"
        stage_name = stage_info.get("display_name", stage)
        nodes.append({
            "id": stage_id,
            "type": "STAGE",
            "name": stage_name,
            "properties": {"order": str(int(stage.split("_", 1)[0])), "source": "master:Cambridge_Curriculum_Library"},
        })

        subjects_out = []
        for subject in stage_info.get("subjects", []):
            folder = subject["folder"]
            subject_dir = os.path.join(stage_dir, folder)
            if not os.path.isdir(subject_dir):
                continue
            total_subjects += 1
            subject_id = f"subject:{slug(stage)}:{slug(folder)}"
            subject_name = subject.get("display_name", folder)
            syllabus_code = subject.get("syllabus_code")

            # scan content areas on disk
            areas_found = []
            pdf_count = 0
            file_count = 0
            for area in AREAS:
                area_dir = os.path.join(subject_dir, area)
                if not os.path.isdir(area_dir):
                    continue
                area_files = []
                for root, _dirs, files in os.walk(area_dir):
                    for fn in sorted(files):
                        fp = os.path.join(root, fn)
                        file_count += 1
                        if fn.lower().endswith(".pdf"):
                            pdf_count += 1
                        area_files.append(os.path.relpath(fp, subject_dir))
                areas_found.append({
                    "name": area,
                    "type": AREA_TYPE[area],
                    "hasPdf": any(f.lower().endswith(".pdf") for f in area_files),
                })
                area_id = f"resource:{slug(stage)}:{slug(folder)}:{slug(area)}"
                nodes.append({
                    "id": area_id,
                    "type": "RESOURCE",
                    "name": f"{subject_name} — {area}",
                    "properties": {
                        "subject": subject_id,
                        "area": area,
                        "fileCount": str(len(area_files)),
                        "hasPdf": "true" if any(f.lower().endswith(".pdf") for f in area_files) else "false",
                    },
                })
                edges.append({
                    "id": f"e:{subject_id}>>{area_id}",
                    "type": "CONTAINS",
                    "source": subject_id,
                    "target": area_id,
                })

            total_pdfs += pdf_count
            has_syllabus_pdf = any(a["name"] == "Syllabus" and a["hasPdf"] for a in areas_found)
            nodes.append({
                "id": subject_id,
                "type": "SUBJECT",
                "name": subject_name,
                "properties": {
                    "stage": stage_id,
                    "syllabusCode": syllabus_code or "",
                    "hasSyllabusPdf": "true" if has_syllabus_pdf else "false",
                    "fileCount": str(file_count),
                },
            })
            edges.append({
                "id": f"e:{stage_id}>>{subject_id}",
                "type": "CONTAINS",
                "source": stage_id,
                "target": subject_id,
            })
            subjects_out.append({
                "id": subject_id,
                "name": subject_name,
                "syllabusCode": syllabus_code,
                "areas": [a["name"] for a in areas_found],
                "hasSyllabusPdf": has_syllabus_pdf,
                "fileCount": file_count,
            })

        subjects_out.sort(key=lambda s: s["name"])
        stages_out.append({
            "id": stage_id,
            "order": int(stage.split("_", 1)[0]),
            "name": stage_name,
            "subjectCount": len(subjects_out),
            "subjects": subjects_out,
        })

    kg = {
        "source": {
            "library": "Cambridge_Curriculum_Library",
            "branch": "master",
            "commit": commit,
            "generator": "tools/generate_kg.py",
            "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        },
        "nodes": nodes,
        "edges": edges,
        "stages": stages_out,
        "stats": {
            "stageCount": len(stages_out),
            "subjectCount": total_subjects,
            "pdfCount": total_pdfs,
            "nodeCount": len(nodes),
            "edgeCount": len(edges),
        },
    }

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(kg, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"wrote {OUT_PATH}")
    print(json.dumps(kg["stats"], indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
