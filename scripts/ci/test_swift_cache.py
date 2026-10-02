"""Protect dependency-only Swift caches from stale generated fixtures."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import swift_cache


class SwiftCacheTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_cleanup_removes_sources_tests_and_generated_products_only(self):
        fixtures = ("src/Old.swift", "tests/OldTests.swift", "build/debug/SundayGenTest.build/Old.o",
                    "build/debug/Modules/SundayGenTest.swiftmodule", "build/debug/SundayGenTestPackageTests.xctest/executable")
        dependencies = ("build/debug/Sunday.build/Value.o", "build/debug/Modules/Sunday.swiftmodule",
                        "build/build.db", "build/checkouts/sunday-swift/Package.swift")
        for name in (*fixtures, *dependencies):
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(name)
        swift_cache.clean_generated(self.root)
        for name in fixtures:
            self.assertFalse((self.root / name).exists(), name)
        for name in dependencies:
            self.assertTrue((self.root / name).exists(), name)
        swift_cache.clean_generated(self.root)

    def test_failed_preparation_cannot_leave_a_success_marker(self):
        with patch.object(swift_cache, "identity", return_value={"version": 1}), \
                patch.object(swift_cache.subprocess, "run", side_effect=RuntimeError("compile failed")), \
                patch.object(swift_cache, "ROOT", self.root):
            with self.assertRaisesRegex(RuntimeError, "compile failed"):
                swift_cache.prepare(self.root / "workspace", self.root / "cache", 1)
        self.assertFalse((self.root / "workspace/prepared.json").exists())
        self.assertFalse((self.root / "workspace/src").exists())

    def test_changed_identity_discards_old_dependency_builds(self):
        workspace = self.root / "workspace"
        old = workspace / "build/old.o"
        old.parent.mkdir(parents=True)
        old.write_text("old")
        (workspace / "prepared.json").write_text('{"version": 0}')
        with patch.object(swift_cache, "identity", return_value={"version": 1}), \
                patch.object(swift_cache.subprocess, "run"), patch.object(swift_cache, "ROOT", self.root):
            swift_cache.prepare(workspace, self.root / "cache", 1)
        self.assertFalse(old.exists())
        self.assertTrue((workspace / "prepared.json").exists())
