"""Wait for public Maven artifacts and smoke-test the downloaded release CLI."""
import argparse
import hashlib
import http.client
import re
import subprocess
import tempfile
import time
import urllib.request
from pathlib import Path

CENTRAL = "https://repo.maven.apache.org/maven2/io/outfoxx/sunday"


def artifact_urls(version):
    """Return the Maven Central publications, including the CLI's all JAR."""
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", version):
        raise ValueError(f"Invalid release version: {version}")
    return [f"{CENTRAL}/{module}/{version}/{module}-{version}.{extension}"
            for module in ("generator", "cli") for extension in ("pom", "jar")] + [
                f"{CENTRAL}/cli/{version}/cli-{version}-all.jar"]


def download(url):
    """Read public artifact bytes with a bounded network timeout."""
    request = urllib.request.Request(url, headers={"Cache-Control": "no-cache"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.read()
    except (OSError, http.client.HTTPException) as error:
        raise OSError(f"{url}: {error}") from error


def smoke_cli(jar, version):
    """Run only the downloaded fat JAR and require its exact release version."""
    with tempfile.TemporaryDirectory(prefix="sunday-release-") as directory:
        path = Path(directory) / "sunday.jar"
        path.write_bytes(jar)
        try:
            result = subprocess.run(["java", "-jar", str(path), "--version"],
                                    capture_output=True, text=True, check=True, timeout=60)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            details = [str(error)]
            for name, output in (("stdout", error.stdout), ("stderr", error.stderr)):
                if output:
                    # TimeoutExpired may carry bytes even when text=True.
                    text = output.decode("utf-8", errors="replace") if isinstance(output, bytes) else output
                    details.append(f"{name}:\n{text}")
            raise ValueError("\n".join(details)) from error
        if not re.search(rf"\bver\.\s+{re.escape(version)}(?=\s|$)", result.stdout):
            raise ValueError(f"CLI did not report {version}: {result.stdout.strip()}")
        print(result.stdout.strip(), flush=True)


def wait_for_release(version, *, fetch=download, smoke=smoke_cli, pause=time.sleep,
                     log=print, attempts=240, interval=15):
    """Retry propagation and checksum failures; never report a partial release as ready."""
    urls = artifact_urls(version)
    if attempts < 1:
        raise ValueError("attempts must be positive")
    last_error = None
    for attempt in range(1, attempts + 1):
        try:
            for url in urls:
                data = fetch(url)
                checksum = fetch(url + ".sha256").decode("ascii").strip().lower()
                if not re.fullmatch(r"[0-9a-f]{64}", checksum):
                    raise ValueError(f"Invalid SHA-256 checksum for {url}")
                if hashlib.sha256(data).hexdigest() != checksum:
                    raise ValueError(f"SHA-256 mismatch for {url}")
            # The last URL is the CLI fat JAR; avoid resolving a local build or cached launcher.
            smoke(data, version)
            log(f"Verified Maven release {version}: all artifacts, SHA-256 checksums, and CLI.")
            return
        except (OSError, ValueError, http.client.HTTPException, subprocess.SubprocessError) as error:
            last_error = error
            log(f"Waiting for Maven Central ({attempt}/{attempts}): {error}")
            if attempt < attempts:
                pause(interval)
    raise RuntimeError(f"Maven release {version} did not become available: {last_error}") from last_error


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version", help="Exact releaseVersion used by the publishing build")
    args = parser.parse_args()
    wait_for_release(args.version, log=lambda message: print(message, flush=True))
