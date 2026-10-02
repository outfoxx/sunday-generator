"""Transfer only portable generator JVM outputs between jobs of the same commit."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path

OUTPUTS = ("classes", "resources", "libs", "compiler-fixtures")
TASKS = (
    "compileKotlin", "compileJava", "processResources", "jar",
    "compileTestFixturesKotlin", "compileTestFixturesJava", "processTestFixturesResources", "testFixturesJar",
    "compileTestKotlin", "compileTestJava", "processTestResources", "compilerFixtures",
)


def checksum(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def files(root):
    return {str(path.relative_to(root)): checksum(path) for output in OUTPUTS
            for path in sorted((root / "generator/build" / output).rglob("*")) if path.is_file()}


def verify(root, commit):
    """Reject wrong revisions, incomplete outputs and changed files before skipping compilation."""
    manifest = json.loads((root / "harness-manifest.json").read_text())
    if manifest.get("commit") != commit or manifest.get("tasks") != list(TASKS):
        raise ValueError("Mismatched harness commit or task contract")
    actual = files(root)
    if actual != manifest.get("files"):
        raise ValueError("Missing, extra or corrupt harness output")
    for required in ("classes/kotlin/main", "classes/kotlin/test", "classes/kotlin/testFixtures",
                     "resources/main", "resources/test", "compiler-fixtures", "libs"):
        if not any(name.startswith(f"generator/build/{required}/") for name in actual):
            raise ValueError(f"Missing harness output: {required}")
    return manifest


def package(root, destination, commit):
    """Publish a fresh immutable handoff, excluding native tools and test execution results."""
    if destination.exists():
        shutil.rmtree(destination)
    for output in OUTPUTS:
        source = root / "generator/build" / output
        if source.exists():
            shutil.copytree(source, destination / "generator/build" / output)
    manifest = dict(commit=commit, tasks=list(TASKS), files=files(destination))
    (destination / "harness-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    verify(destination, commit)


def restore(source, root, commit):
    """Verify before replacing outputs; dependency resolution remains local to each runner."""
    manifest = verify(source, commit)
    for output in OUTPUTS:
        target = root / "generator/build" / output
        if target.exists():
            shutil.rmtree(target)
        origin = source / "generator/build" / output
        if origin.exists():
            shutil.copytree(origin, target)
    (root / "harness-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    verify(root, commit)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("package", "restore", "verify"))
    parser.add_argument("--commit", required=True)
    parser.add_argument("--artifact", type=Path, default=Path("build/ci-harness"))
    args = parser.parse_args()
    root = Path.cwd()
    if args.command == "package":
        package(root, args.artifact, args.commit)
    elif args.command == "restore":
        restore(args.artifact, root, args.commit)
    else:
        verify(root, args.commit)


if __name__ == "__main__":
    main()
