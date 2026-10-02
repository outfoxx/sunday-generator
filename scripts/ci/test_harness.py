"""Regressions for the cross-runner JVM handoff."""
import json
import tempfile
import unittest
from pathlib import Path

import harness


class HarnessTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        self.artifact = self.root / "artifact"
        for name in ("classes/kotlin/main/Main.class", "classes/kotlin/test/Test.class",
                     "classes/kotlin/testFixtures/Fixture.class", "resources/main/data",
                     "resources/test/data", "compiler-fixtures/Stub.class", "libs/generator.jar"):
            path = self.source / "generator/build" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(name)
        harness.package(self.source, self.artifact, "commit")

    def test_restore_relocates_outputs_and_removes_stale_files(self):
        target = self.root / "different-checkout"
        stale = target / "generator/build/classes/stale.class"
        stale.parent.mkdir(parents=True)
        stale.write_text("stale")
        harness.restore(self.artifact, target, "commit")
        self.assertFalse(stale.exists())
        self.assertEqual(harness.files(self.source), harness.files(target))

    def test_wrong_commit_is_rejected_before_replacement(self):
        with self.assertRaisesRegex(ValueError, "commit"):
            harness.restore(self.artifact, self.root / "target", "other")
        self.assertFalse((self.root / "target").exists())

    def test_changed_and_missing_outputs_are_rejected(self):
        path = self.artifact / "generator/build/classes/kotlin/main/Main.class"
        path.write_text("changed")
        with self.assertRaisesRegex(ValueError, "corrupt"):
            harness.verify(self.artifact, "commit")
        path.unlink()
        with self.assertRaisesRegex(ValueError, "Missing"):
            harness.verify(self.artifact, "commit")

    def test_missing_required_directory_rejected_even_with_updated_checksums(self):
        path = self.artifact / "generator/build/compiler-fixtures/Stub.class"
        path.unlink()
        manifest_path = self.artifact / "harness-manifest.json"
        manifest = json.loads(manifest_path.read_text())
        manifest["files"] = harness.files(self.artifact)
        manifest_path.write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, "Missing harness output"):
            harness.verify(self.artifact, "commit")

    def test_native_tools_and_test_results_are_not_transferred(self):
        for name in ("validation/native-tool", "test-results/test/result.xml", "kover/bin-reports/test.ic"):
            path = self.source / "generator/build" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("excluded")
        harness.package(self.source, self.artifact, "commit")
        self.assertFalse((self.artifact / "generator/build/validation").exists())
        self.assertFalse((self.artifact / "generator/build/test-results").exists())
        self.assertFalse((self.artifact / "generator/build/kover").exists())
