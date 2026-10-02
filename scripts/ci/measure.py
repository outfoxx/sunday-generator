"""Run a build while recording elapsed time and its process tree's CPU/RSS samples."""
import json
import subprocess
import sys
import time
from pathlib import Path


def main():
    output = Path("build/diagnostics")
    output.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    process = subprocess.Popen(sys.argv[1:])
    with (output / "resources.jsonl").open("w") as samples:
        while process.poll() is None:
            rows = subprocess.check_output(["ps", "-axo", "pid=,ppid=,rss=,pcpu="], text=True)
            entries = [line.split() for line in rows.splitlines() if len(line.split()) == 4]
            descendants = {process.pid}
            daemon = output / "gradle.pid"
            if daemon.exists():
                value = daemon.read_text().strip()
                if value.isdigit():
                    descendants.add(int(value))
            for _ in range(len(entries)):
                children = {int(pid) for pid, ppid, _, _ in entries if int(ppid) in descendants}
                if children <= descendants:
                    break
                descendants |= children
            selected = [entry for entry in entries if int(entry[0]) in descendants]
            samples.write(json.dumps(dict(seconds=time.monotonic() - started,
                                          rssKiB=sum(int(e[2]) for e in selected),
                                          cpuPercent=sum(float(e[3]) for e in selected))) + "\n")
            samples.flush()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                pass
    (output / "elapsed.json").write_text(json.dumps(dict(seconds=time.monotonic() - started,
                                                       exitCode=process.returncode)) + "\n")
    return process.returncode


if __name__ == "__main__":
    sys.exit(main())
