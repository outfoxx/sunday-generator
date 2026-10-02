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
            modules = coverage.TEST_MODULES if partition == "ubuntu" else ("generator",)
            for module in modules:
                for name, value in (("kover/bin-reports/test.ic", "binary"),
                                    ("test-results/test/TEST-example.xml", '<testsuite tests="1" failures="0" errors="0"/>')):
                    path = source / module / "build" / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text(value)
                if module == "integration-tests/quarkus":
                    (source / module / "build/kover/bin-reports/configurationTest.ic").write_text("binary")
            inventory = source / "generator/build/diagnostics/inventory/worker-1.json"
            inventory.parent.mkdir(parents=True)
            inventory.write_text(json.dumps([partition]))
            if partition == "ubuntu":
                (inventory.parent.parent / "test-partitions.json").write_text(json.dumps(self.expected))
            coverage.package(source, self.artifacts, partition, "commit-a")

    def test_complete_partitions_are_accepted_without_executing_tests(self):
        self.assertEqual(7, len(coverage.verify(self.artifacts, "commit-a")))

    def test_other_commit_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Mismatched manifest"):
            coverage.verify(self.artifacts, "commit-b")

    def test_missing_artifact_is_rejected(self):
        (self.artifacts / "swift-remainder/manifest.json").unlink()
        with self.assertRaises(FileNotFoundError):
            coverage.verify(self.artifacts, "commit-a")

    def test_corrupt_binary_is_rejected(self):
        (self.artifacts / "ubuntu/generator/build/kover/bin-reports/test.ic").write_text("corrupt")
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
