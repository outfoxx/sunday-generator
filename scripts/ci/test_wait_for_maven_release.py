"""Exercise release propagation and consumer verification without network or sleeps."""
import hashlib
import http.client
import subprocess
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import Mock, patch

import wait_for_maven_release as release

VERSION = "2.0.0-beta.51"


class MavenReleaseTest(unittest.TestCase):
    def setUp(self):
        self.urls = release.artifact_urls(VERSION)
        self.contents = {url: url.encode() for url in self.urls}
        self.responses = dict(self.contents)
        self.responses.update({url + ".sha256": hashlib.sha256(data).hexdigest().encode()
                               for url, data in self.contents.items()})
        self.pause = Mock()
        self.smoke = Mock()

    def verify(self, fetch=None, attempts=1):
        release.wait_for_release(VERSION, fetch=fetch or self.responses.__getitem__,
                                 smoke=self.smoke, pause=self.pause, log=lambda _: None,
                                 attempts=attempts)

    def test_downloads_every_publication_and_smokes_exact_fat_jar(self):
        fetch = Mock(side_effect=self.responses.__getitem__)
        self.verify(fetch)
        self.assertEqual(fetch.call_count, 10)
        self.assertTrue(self.urls[-1].endswith("cli-2.0.0-beta.51-all.jar"))
        self.smoke.assert_called_once_with(self.contents[self.urls[-1]], VERSION)
        self.pause.assert_not_called()

    def test_waits_for_each_artifact_and_checksum_to_propagate(self):
        for pending in self.responses:
            with self.subTest(pending=pending):
                failed = False

                def fetch(url):
                    nonlocal failed
                    if url == pending and not failed:
                        failed = True
                        raise urllib.error.HTTPError(url, 404, "Not found", {}, None)
                    return self.responses[url]

                self.pause.reset_mock()
                self.smoke.reset_mock()
                self.verify(fetch, attempts=2)
                self.pause.assert_called_once_with(15)
                self.smoke.assert_called_once()

    def test_retries_transient_network_failure(self):
        for failure in (TimeoutError("timed out"), urllib.error.URLError("offline"),
                        http.client.IncompleteRead(b"partial", 100)):
            with self.subTest(failure=failure):
                fetch = Mock(side_effect=[failure] + [
                    self.responses[key] for url in self.urls for key in (url, url + ".sha256")])
                self.pause.reset_mock()
                self.verify(fetch, attempts=2)
                self.pause.assert_called_once_with(15)

    def test_exhausted_retries_report_last_unmet_condition_without_final_sleep(self):
        fetch = Mock(side_effect=urllib.error.URLError("offline"))
        with self.assertRaisesRegex(RuntimeError, "did not become available:.*offline"):
            self.verify(fetch, attempts=2)
        self.assertEqual(fetch.call_count, 2)
        self.pause.assert_called_once_with(15)
        self.smoke.assert_not_called()

    def test_corrupt_or_malformed_checksums_cannot_pass(self):
        checksum_url = self.urls[-1] + ".sha256"
        for checksum in (b"0" * 64, b"not a checksum"):
            with self.subTest(checksum=checksum):
                self.responses[checksum_url] = checksum
                with self.assertRaisesRegex(RuntimeError, "SHA-256"):
                    self.verify()
                self.smoke.assert_not_called()

    def test_network_diagnostics_identify_unavailable_artifact(self):
        url = self.urls[-1]
        with patch.object(release.urllib.request, "urlopen", side_effect=TimeoutError("timed out")) as open_url:
            with self.assertRaisesRegex(OSError, "cli-2.0.0-beta.51-all.jar: timed out"):
                release.download(url)
            self.assertEqual(open_url.call_args.kwargs["timeout"], 30)

    def test_cli_failure_cannot_complete_release(self):
        self.smoke.side_effect = subprocess.CalledProcessError(1, "java")
        with self.assertRaisesRegex(RuntimeError, "non-zero exit status"):
            self.verify()

    def test_cli_failure_output_reaches_retry_log_and_final_error(self):
        failures = (
            subprocess.CalledProcessError(1, "java", output="startup output", stderr="missing class"),
            subprocess.TimeoutExpired("java", 60, output=b"partial output", stderr=b"stalled\xff"),
            subprocess.TimeoutExpired("java", 60),
        )
        for failure in failures:
            with self.subTest(failure=failure), patch.object(release.subprocess, "run", side_effect=failure):
                logs = []
                pause = Mock()
                with self.assertRaises(RuntimeError) as raised:
                    release.wait_for_release(VERSION, fetch=self.responses.__getitem__,
                                             smoke=release.smoke_cli, pause=pause, log=logs.append, attempts=2)
                self.assertEqual(len(logs), 2)
                pause.assert_called_once_with(15)
                details = [str(failure)]
                for name, value in (("stdout", failure.stdout), ("stderr", failure.stderr)):
                    if value:
                        text = value.decode("utf-8", errors="replace") if isinstance(value, bytes) else value
                        details.append(f"{name}:\n{text}")
                for detail in details:
                    self.assertIn(detail, str(raised.exception))
                    for log in logs:
                        self.assertIn(detail, log)

    def test_cli_executes_download_and_checks_exact_version(self):
        def run(command, **kwargs):
            self.assertEqual(command[:2], ["java", "-jar"])
            self.assertEqual(command[-1], "--version")
            self.assertEqual(Path(command[2]).read_bytes(), b"downloaded CLI")
            self.assertEqual(kwargs["timeout"], 60)
            return subprocess.CompletedProcess(command, 0, f"Sunday - Generator ver. {VERSION} (build: abc)")

        with patch.object(release.subprocess, "run", side_effect=run):
            release.smoke_cli(b"downloaded CLI", VERSION)
        for reported in ("2.0.0-beta.50", VERSION + "0"):
            with patch.object(release.subprocess, "run", return_value=
                              subprocess.CompletedProcess([], 0, f"ver. {reported}")):
                with self.assertRaisesRegex(ValueError, "did not report"):
                    release.smoke_cli(b"downloaded CLI", VERSION)

    def test_default_budget_survives_more_than_fifteen_minutes_of_propagation(self):
        attempts = 0

        def fetch(url):
            nonlocal attempts
            if url == self.urls[0]:
                attempts += 1
                if attempts <= 80:
                    raise urllib.error.URLError("propagating")
            return self.responses[url]

        release.wait_for_release(VERSION, fetch=fetch, smoke=self.smoke,
                                 pause=self.pause, log=lambda _: None)
        self.assertEqual(attempts, 81)
        self.assertEqual(self.pause.call_count, 80)
        self.smoke.assert_called_once()

    def test_versions_follow_exact_publisher_coordinates(self):
        for version in ("2.0.0", VERSION, "2.0.0-rc.1"):
            self.assertIn(f"/{version}/cli-{version}-all.jar", release.artifact_urls(version)[-1])
        for version in ("v2.0.0", "../../other", "2.0.0\n"):
            with self.assertRaises(ValueError):
                release.artifact_urls(version)
