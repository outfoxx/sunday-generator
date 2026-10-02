"""Package and verify partition results, then report coverage without executing tests."""
import argparse
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PARTITIONS = json.loads((Path(__file__).parent / "partitions.json").read_text())
MODULES = ("generator", "cli", "gradle-plugin", "integration-tests/quarkus")
TEST_TASKS = {
    "generator": ("test",),
    "cli": ("test",),
    "gradle-plugin": ("test",),
    "integration-tests/quarkus": ("test", "configurationTest"),
    "integration-tests/jaxrs": ("test", "defaultModelTest"),
}
TEST_MODULES = tuple(TEST_TASKS)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def package(root, destination, partition, commit):
    """Copy only verification outputs, preserving module identity and checksums."""
    target = destination / partition
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)
    modules = TEST_MODULES if partition == "infrastructure" else ("generator",)
    for module in modules:
        source = root / module
        for pattern in ("build/kover/bin-reports/*.ic", "build/kover/bin-reports/*.log", "build/test-results/**/*.xml",
                        "build/diagnostics/**/*", "build/reports/profile/*"):
            for path in source.glob(pattern):
                if path.is_file():
                    output = target / path.relative_to(root)
                    output.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(path, output)
        if partition == "infrastructure":
            for language in ("kotlin", "java"):
                for source_set in ("main", "testFixtures"):
                    classes = source / "build/classes" / language / source_set
                    if classes.exists():
                        shutil.copytree(classes, target / classes.relative_to(root))
    for path in (root / "build/diagnostics").glob("*"):
        if path.is_file():
            output = target / "build/diagnostics" / path.name
            output.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, output)
    for path in (root / "build/reports/profile").glob("*"):
        if path.is_file():
            output = target / "build/reports/profile" / path.name
            output.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, output)
    files = {str(p.relative_to(target)): digest(p) for p in sorted(target.rglob("*")) if p.is_file()}
    manifest = dict(commit=commit, partition=partition, testTags=PARTITIONS[partition],
                    modules=list(modules), files=files)
    (target / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


def verify(artifacts, commit):
    """Fail closed on missing, mixed-revision, corrupt, incomplete or overlapping results."""
    reports = []
    for partition, expression in PARTITIONS.items():
        directory = artifacts / partition
        manifest = json.loads((directory / "manifest.json").read_text())
        expected_modules = list(TEST_MODULES if partition == "infrastructure" else ("generator",))
        if (manifest["commit"], manifest["partition"], manifest["testTags"], manifest["modules"]) != (
                commit, partition, expression, expected_modules):
            raise ValueError(f"Mismatched manifest: {partition}")
        actual = {str(p.relative_to(directory)) for p in directory.rglob("*")
                  if p.is_file() and p.name != "manifest.json"}
        if actual != set(manifest["files"]):
            raise ValueError(f"Missing or extra files: {partition}")
        for name, checksum in manifest["files"].items():
            path = directory / name
            if not path.resolve().is_relative_to(directory.resolve()) or digest(path) != checksum:
                raise ValueError(f"Invalid artifact: {partition}/{name}")
        for module in expected_modules:
            binaries = list((directory / module / "build/kover/bin-reports").glob("*.ic"))
            results = list((directory / module / "build/test-results").glob("**/*.xml"))
            expected_binaries = {f"{task}.ic" for task in TEST_TASKS[module]}
            result_tasks = {path.parent.name for path in results}
            if {path.name for path in binaries} != expected_binaries or result_tasks != set(TEST_TASKS[module]):
                raise ValueError(f"Missing coverage or JUnit results: {partition}/{module}")
            for result in results:
                suite = ET.parse(result).getroot()
                if int(suite.get("failures", "0")) or int(suite.get("errors", "0")):
                    raise ValueError(f"Failed test results: {partition}/{module}")
            if module in MODULES:
                reports.extend(binaries)
    expected = json.loads((artifacts / "infrastructure/generator/build/diagnostics/test-partitions.json").read_text())
    seen = set()
    for partition, expression in PARTITIONS.items():
        inventory_files = list((artifacts / partition / "generator/build/diagnostics/inventory").glob("*.json"))
        if not inventory_files:
            raise ValueError(f"Missing inventory: {partition}")
        actual = set().union(*(set(json.loads(path.read_text())) for path in inventory_files))
        if actual != set(expected[expression]) or actual & seen:
            raise ValueError(f"Incomplete or overlapping inventory: {partition}")
        seen.update(actual)
    return reports


def aggregate(artifacts, commit, cli, destination):
    """Use Kover's pinned CLI with original production classes from the infrastructure build."""
    reports = verify(artifacts, commit)
    classes = []
    for module in TEST_MODULES:
        roots = [p for p in (artifacts / "infrastructure" / module / "build/classes").glob("*/*") if p.is_dir() and p.name in ("main", "testFixtures")]
        if not roots:
            raise ValueError(f"Missing production classes: {module}")
        if module in MODULES:
            classes.extend(roots)
        # Sonar's module analysis expects the compiled production outputs in their normal locations.
        for path in roots:
            restored = ROOT / module / "build/classes" / path.parent.name / path.name
            if restored.exists():
                shutil.rmtree(restored)
            shutil.copytree(path, restored)
    destination.mkdir(parents=True, exist_ok=True)
    command = ["java", "-jar", str(cli), "report", *map(str, reports)]
    for path in classes:
        command.extend(("--classfiles", str(path)))
    for module in MODULES:
        for source_set in ("main", "testFixtures"):
            for path in (ROOT / module / "src" / source_set).glob("*"):
                if path.is_dir():
                    command.extend(("--src", str(path)))
    command.extend(("--xml", str(destination / "report.xml"), "--html", str(destination / "html")))
    subprocess.run(command, check=True, timeout=300)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    pack = commands.add_parser("package")
    pack.add_argument("--partition", choices=PARTITIONS, required=True)
    pack.add_argument("--commit", required=True)
    pack.add_argument("--destination", type=Path, default=ROOT / "build/ci-artifacts")
    report = commands.add_parser("aggregate")
    report.add_argument("--artifacts", type=Path, required=True)
    report.add_argument("--commit", required=True)
    report.add_argument("--cli", type=Path, required=True)
    report.add_argument("--destination", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "package":
        package(ROOT, args.destination, args.partition, args.commit)
    else:
        aggregate(args.artifacts, args.commit, args.cli, args.destination)


if __name__ == "__main__":
    main()
