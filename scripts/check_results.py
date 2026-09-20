"""Fail CI unless the original score AND the underlying reports all pass."""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from write_score import JACOCO_LINE_COVERAGE_MIN, PIT_MUTATION_SCORE_MIN, STYLE_VIOLATIONS_THRESHOLD

ROOT = Path(__file__).resolve().parent.parent
EXPECTED_GATES = {
    "lab1_style", "lab2_coverage", "lab2_schema", "lab3_migration",
    "lab4_nplusone", "lab4_architecture", "lab5_mutation", "lab5_reference",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def count_elements(root, name):
    # PMD uses a versioned XML namespace; do not silently count it as zero.
    return sum(element.tag.rsplit("}", 1)[-1] == name for element in root.iter())


def check_results(root=ROOT):
    target = root / "target"
    score = json.loads((root / "score.json").read_text(encoding="utf-8"))
    require(set(score["gates"]) == EXPECTED_GATES, "Missing or unexpected scoring gates")
    require(score["score"] == score["max_score"] == 100, "Expected score 100/100")
    require(all(gate["passed"] is True for gate in score["gates"].values()), "A scoring gate failed")

    test_sources = root / "src/test/java"
    required_reports = {
        "TEST-" + ".".join(source.relative_to(test_sources).with_suffix("").parts) + ".xml"
        for source in test_sources.rglob("*Test.java")
        if re.search(r"@(Test|ParameterizedTest)\b", source.read_text(encoding="utf-8"))
    }
    reports = list((target / "surefire-reports").glob("TEST-*.xml"))
    require(required_reports, "No test sources found")
    missing = required_reports - {report.name for report in reports}
    require(not missing, "Missing JUnit reports: " + ", ".join(sorted(missing)))
    test_count = 0
    for report in reports:
        suite = ET.parse(report).getroot()
        tests = int(suite.attrib["tests"])
        require(tests > 0, f"Empty test suite: {report.name}")
        for status in ("failures", "errors", "skipped"):
            require(int(suite.attrib[status]) == 0, f"JUnit {status}: {report.name}")
        test_count += tests

    checkstyle = count_elements(ET.parse(target / "checkstyle-result.xml").getroot(), "error")
    pmd_report = ET.parse(target / "pmd.xml").getroot()
    require(count_elements(pmd_report, "error") == 0, "PMD could not analyze a source file")
    pmd = count_elements(pmd_report, "violation")
    require(checkstyle + pmd <= STYLE_VIOLATIONS_THRESHOLD,
            f"Style violations above threshold: Checkstyle={checkstyle}, PMD={pmd}")

    jacoco = ET.parse(target / "site/jacoco/jacoco.xml").getroot()
    lines = next(counter for counter in jacoco.findall("counter") if counter.get("type") == "LINE")
    covered, missed = int(lines.attrib["covered"]), int(lines.attrib["missed"])
    require(covered + missed > 0, "Empty JaCoCo report")
    coverage = covered / (covered + missed)
    require(coverage >= JACOCO_LINE_COVERAGE_MIN, f"Insufficient coverage: {coverage}")

    mutation_reports = list((target / "pit-reports").rglob("mutations.xml"))
    require(len(mutation_reports) == 1, "Expected exactly one fresh PIT report; run mvn clean verify first")
    mutations = ET.parse(mutation_reports[0]).getroot().findall("mutation")
    require(mutations, "No mutations tested")
    require(not any(m.get("status") in {"RUN_ERROR", "MEMORY_ERROR"} for m in mutations),
            "PIT execution errors")
    killed = sum(m.get("status") == "KILLED" for m in mutations)
    mutation_score = 100 * killed / len(mutations)
    require(mutation_score >= PIT_MUTATION_SCORE_MIN, f"Insufficient mutation score: {mutation_score}")
    print(f"Verified {test_count} tests; Checkstyle={checkstyle}, PMD={pmd}; "
          f"coverage={coverage:.2%}; PIT={killed}/{len(mutations)} ({mutation_score:.2f}%); score=100/100")


if __name__ == "__main__":
    try:
        check_results()
    except (OSError, ValueError, KeyError, TypeError, StopIteration, ET.ParseError) as error:
        print(f"Verification failed: {error}", file=sys.stderr)
        sys.exit(1)
