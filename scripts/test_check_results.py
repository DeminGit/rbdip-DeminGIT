"""Regression tests: a nominal 100/100 must not hide incomplete/failed reports."""
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from check_results import EXPECTED_GATES, check_results


class ResultVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="rbdip-results-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.write("score.json", json.dumps({
            "score": 100, "max_score": 100,
            "gates": {gate: {"passed": True} for gate in EXPECTED_GATES},
        }))
        self.write("src/test/java/example/SampleTest.java", "@Test void verifiesBehavior() {}")
        self.suite()
        self.write("target/checkstyle-result.xml", "<checkstyle/>")
        self.write("target/pmd.xml", '<pmd xmlns="http://pmd.sourceforge.net/report/2.0.0"/>')
        self.write("target/site/jacoco/jacoco.xml",
                   '<report><counter type="LINE" covered="60" missed="40"/></report>')
        self.mutations(6)

    def write(self, path, content):
        destination = self.root / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(content, encoding="utf-8")

    def suite(self, tests=1, failures=0, errors=0, skipped=0):
        self.write("target/surefire-reports/TEST-example.SampleTest.xml",
                   f'<testsuite tests="{tests}" failures="{failures}" errors="{errors}" skipped="{skipped}"/>')

    def mutations(self, killed):
        self.write("target/pit-reports/mutations.xml", "<mutations>"
                   + '<mutation status="KILLED"/>' * killed
                   + '<mutation status="SURVIVED"/>' * (10 - killed) + "</mutations>")

    def test_accepts_complete_reports_at_course_thresholds(self):
        with contextlib.redirect_stdout(io.StringIO()):
            check_results(self.root)

    def test_rejects_failed_skipped_and_empty_tests_despite_full_score(self):
        for counts in ({"failures": 1}, {"errors": 1}, {"skipped": 1}, {"tests": 0}):
            with self.subTest(counts=counts):
                self.suite(**counts)
                with self.assertRaises(ValueError):
                    check_results(self.root)

    def test_rejects_missing_test_report(self):
        self.write("src/test/java/example/AnotherTest.java", "@Test void verifiesAnotherBehavior() {}")
        with self.assertRaisesRegex(ValueError, "Missing JUnit"):
            check_results(self.root)

    def test_counts_namespaced_pmd_violations(self):
        self.write("target/pmd.xml", '<pmd xmlns="http://pmd.sourceforge.net/report/2.0.0"><file>'
                   + "<violation/>" * 16 + "</file></pmd>")
        with self.assertRaisesRegex(ValueError, "Style violations"):
            check_results(self.root)

    def test_rejects_insufficient_mutations_despite_full_score(self):
        self.mutations(5)
        with self.assertRaisesRegex(ValueError, "mutation score"):
            check_results(self.root)


if __name__ == "__main__":
    unittest.main()
