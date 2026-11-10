"""Regression tests for fail-closed CI artifact aggregation."""
import json
import tempfile
import unittest
from pathlib import Path

import coverage


class CoverageArtifactsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.artifacts = self.root / "artifacts"
        self.expected = {expression: [partition] for partition, expression in coverage.PARTITIONS.items()}
        for partition in coverage.PARTITIONS:
            source = self.root / partition
            modules = coverage.TEST_MODULES if partition == "infrastructure" else ("generator",)
            for module in modules:
                for task in coverage.TEST_TASKS[module]:
                    for name, value in ((f"kover/bin-reports/{task}.ic", "binary"),
                                        (f"test-results/{task}/TEST-example.xml", '<testsuite tests="1" failures="0" errors="0"/>')):
                        path = source / module / "build" / name
                        path.parent.mkdir(parents=True, exist_ok=True)
                        path.write_text(value)
            inventory = source / "generator/build/diagnostics/inventory/worker-1.json"
            inventory.parent.mkdir(parents=True)
            inventory.write_text(json.dumps([partition]))
            if partition == "infrastructure":
                (inventory.parent.parent / "test-partitions.json").write_text(json.dumps(self.expected))
            coverage.package(source, self.artifacts, partition, "commit-a")

    def test_complete_partitions_are_accepted_without_executing_tests(self):
        self.assertEqual(11, len(coverage.verify(self.artifacts, "commit-a")))

    def test_configuration_failure_reports_are_preserved_without_test_results(self):
        source = self.root / "failed-python"
        reports = (
            "build/reports/dependency-verification/at-123/dependency-verification-report.html",
            "build/reports/dependency-verification/at-123/css/style.css",
            "build/reports/configuration-cache/task-key/entry-key/configuration-cache-report.html",
            "generator/build/reports/dependency-verification/at-456/dependency-verification-report.html",
            "generator/build/reports/configuration-cache/task-key/entry-key/configuration-cache-report.html",
        )
        for name in reports:
            path = source / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(name)
        coverage.package(source, self.artifacts, "python", "commit-a")
        directory = self.artifacts / "python"
        manifest = json.loads((directory / "manifest.json").read_text())
        self.assertEqual(set(reports), set(manifest["files"]))
        for name in reports:
            self.assertEqual(name, (directory / name).read_text())
            self.assertEqual(coverage.digest(source / name), manifest["files"][name])
        with self.assertRaisesRegex(ValueError, "Missing coverage or JUnit results: python/generator"):
            coverage.verify(self.artifacts, "commit-a")

    def test_diagnostic_reports_do_not_prevent_successful_aggregation(self):
        source = self.root / "python"
        report = source / "build/reports/configuration-cache/task-key/entry-key/configuration-cache-report.html"
        report.parent.mkdir(parents=True)
        report.write_text("configuration cache diagnostics")
        coverage.package(source, self.artifacts, "python", "commit-a")
        self.assertEqual(11, len(coverage.verify(self.artifacts, "commit-a")))

    def test_missing_secondary_test_task_is_rejected(self):
        directory = self.artifacts / "infrastructure"
        name = "integration-tests/jaxrs/build/kover/bin-reports/defaultModelTest.ic"
        (directory / name).unlink()
        manifest = json.loads((directory / "manifest.json").read_text())
        del manifest["files"][name]
        (directory / "manifest.json").write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, "Missing coverage or JUnit results"):
            coverage.verify(self.artifacts, "commit-a")

    def test_other_commit_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Mismatched manifest"):
            coverage.verify(self.artifacts, "commit-b")

    def test_missing_artifact_is_rejected(self):
        (self.artifacts / "swift-remainder/manifest.json").unlink()
        with self.assertRaises(FileNotFoundError):
            coverage.verify(self.artifacts, "commit-a")

    def test_corrupt_binary_is_rejected(self):
        (self.artifacts / "infrastructure/generator/build/kover/bin-reports/test.ic").write_text("corrupt")
        with self.assertRaisesRegex(ValueError, "Invalid artifact"):
            coverage.verify(self.artifacts, "commit-a")

    def test_incomplete_inventory_is_rejected_even_with_valid_checksum(self):
        directory = self.artifacts / "swift-remainder"
        name = "generator/build/diagnostics/inventory/worker-1.json"
        (directory / name).write_text("[]")
        manifest = json.loads((directory / "manifest.json").read_text())
        manifest["files"][name] = coverage.digest(directory / name)
        (directory / "manifest.json").write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, "Incomplete or overlapping"):
            coverage.verify(self.artifacts, "commit-a")


if __name__ == "__main__":
    unittest.main()
