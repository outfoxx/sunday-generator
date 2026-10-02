"""Prepare a stable Swift workspace; retain dependency builds, never generated fixtures."""
import argparse
import hashlib
import json
import platform
import shutil
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEMPLATE = ROOT / "generator/src/test/resources/swift/compile/local"


def clean_generated(workspace):
    """Remove fixture sources and products without discarding compiled dependencies."""
    for name in ("src", "tests"):
        if (workspace / name).exists():
            shutil.rmtree(workspace / name)
    # SwiftPM also produces discovered-test modules and an XCTest executable with this prefix.
    for path in sorted((workspace / "build").rglob("SundayGenTest*"), key=lambda p: len(p.parts), reverse=True):
        if path.is_dir() and not path.is_symlink():
            shutil.rmtree(path)
        else:
            path.unlink(missing_ok=True)


def identity(workspace):
    """Invalidate incompatible or relocated build products instead of trusting a restored cache."""
    return dict(version=1, workspace=str(workspace.resolve()), architecture=platform.machine(),
                swift=subprocess.check_output(["swift", "--version"], text=True).strip(),
                sdk=subprocess.check_output(["xcrun", "--show-sdk-path"], text=True).strip(),
                sdkVersion=subprocess.check_output(["xcrun", "--show-sdk-version"], text=True).strip(),
                xcode=subprocess.check_output(["xcodebuild", "-version"], text=True).strip(),
                recipe=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                manifests={name: hashlib.sha256((TEMPLATE / name).read_bytes()).hexdigest()
                           for name in ("Package.swift", "Package.resolved")})


def prepare(workspace, cache, jobs):
    """Populate missing dependencies online, then build only the Sunday dependency target."""
    started = time.monotonic()
    expected = identity(workspace)
    marker = workspace / "prepared.json"
    previous = json.loads(marker.read_text()) if marker.exists() else None
    if previous != expected and workspace.exists():
        shutil.rmtree(workspace)
    workspace.mkdir(parents=True, exist_ok=True)
    marker.unlink(missing_ok=True)
    clean_generated(workspace)
    cache.mkdir(parents=True, exist_ok=True)
    for name in ("Package.swift", "Package.resolved"):
        target = workspace / name
        if not target.exists() or target.read_bytes() != (TEMPLATE / name).read_bytes():
            shutil.copy2(TEMPLATE / name, target)
    # SwiftPM requires the root target to contain a source even when building a dependency target.
    (workspace / "src").mkdir()
    (workspace / "src/Preparation.swift").write_text("import Sunday\n")
    (workspace / "tests").mkdir()
    common = ["--package-path", str(workspace), "--scratch-path", str(workspace / "build"),
              "--cache-path", str(cache), "--manifest-cache", "local",
              "--only-use-versions-from-resolved-file"]
    try:
        if previous != expected:
            subprocess.run(["swift", "package", *common, "resolve"], check=True, timeout=600)
        prefix = ["/usr/bin/sandbox-exec", "-p", "(version 1)(allow default)(deny network*)"] if previous == expected else []
        subprocess.run([*prefix, "swift", "build", *common, "--jobs", str(jobs), "--disable-index-store", "--disable-sandbox",
                        "--target", "Sunday"], check=True, timeout=600)
        marker.write_text(json.dumps(expected, indent=2) + "\n")
    finally:
        clean_generated(workspace)
        diagnostics = ROOT / "build/diagnostics"
        diagnostics.mkdir(parents=True, exist_ok=True)
        (diagnostics / "swift-preparation.json").write_text(json.dumps(
            dict(seconds=time.monotonic() - started, reused=previous == expected, success=marker.exists())) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("prepare", "clean"))
    parser.add_argument("--workspace", type=Path, required=True)
    parser.add_argument("--package-cache", type=Path, default=ROOT / "generator/build/validation/swift/package-cache")
    parser.add_argument("--jobs", type=int, default=3)
    args = parser.parse_args()
    if args.command == "prepare":
        prepare(args.workspace.resolve(), args.package_cache.resolve(), args.jobs)
    else:
        clean_generated(args.workspace.resolve())


if __name__ == "__main__":
    main()
