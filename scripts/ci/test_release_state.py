"""Prevent release resumes from overwriting published destinations or assets."""
import json
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import Mock, patch

import release_state as release

VERSION = '2.0.0-beta.52'


class ReleaseStateTest(unittest.TestCase):
    def test_absent_destinations_need_publication(self):
        self.assertEqual(release.inventory(VERSION, lambda _: None),
                         dict(maven=False, plugin=False, docker=False, docs=False))

    def test_partial_maven_visibility_prevents_duplicate_upload(self):
        pom = release.artifact_urls(VERSION)[0]
        state = release.inventory(VERSION, lambda url: b'published' if url == pom else None)
        self.assertTrue(state['maven'])

    def test_existing_destinations_are_skipped(self):
        seen = []

        def fetch(url):
            seen.append(url)
            return b'published'

        self.assertTrue(all(release.inventory(VERSION, fetch).values()))
        self.assertIn(f'{release.API}/contents/{VERSION}/index.html?ref=gh-pages', seen)

    def test_partial_plugin_publication_stops_instead_of_republishing(self):
        for visible in ('gradle.plugin-', 'gradle-plugin-'):
            with self.subTest(visible=visible), self.assertRaisesRegex(RuntimeError, 'partially visible'):
                release.inventory(VERSION, lambda url: b'existing' if visible in url else None)

    def test_only_404_means_absent(self):
        for status in (401, 403, 429, 500):
            with self.subTest(status=status), patch.object(release.urllib.request, 'urlopen',
                    side_effect=urllib.error.HTTPError('https://example.com', status, 'failure', {}, None)):
                with self.assertRaises(urllib.error.HTTPError):
                    release.get('https://example.com')
        with patch.object(release.urllib.request, 'urlopen',
                side_effect=urllib.error.HTTPError('https://example.com', 404, 'absent', {}, None)):
            self.assertIsNone(release.get('https://example.com'))

    def assets(self, root, version=VERSION):
        files = [root / module / 'build/libs' / f'{module}-{version}{suffix}.jar'
                 for module in ('generator', 'cli', 'gradle-plugin')
                 for suffix in ('', '-sources', '-javadoc')]
        files.append(root / 'build/scripts/sunday')
        for file in files:
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(b'asset')
        return files

    def test_resume_uploads_only_missing_assets_without_clobber(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = self.assets(root)
            state = json.dumps({'draft': False, 'assets': [{'name': f.name} for f in files[:-1]]}).encode()
            run = Mock()
            release.publish_github(VERSION, root, lambda _: state, run)
            run.assert_called_once_with(['gh', 'release', 'upload', VERSION, '--repo', f'github.com/{release.REPO}',
                                         str(files[-1])], check=True)

    def test_complete_release_is_a_noop(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = self.assets(root)
            state = json.dumps({'draft': False, 'assets': [{'name': f.name} for f in files]}).encode()
            run = Mock()
            release.publish_github(VERSION, root, lambda _: state, run)
            run.assert_not_called()

    def test_create_release_requires_existing_tag_and_correct_prerelease_flag(self):
        for version in (VERSION, '2.0.0'):
            with self.subTest(version=version), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.assets(root, version)
                run = Mock()
                release.publish_github(version, root, lambda _: None, run)
                command = run.call_args_list[0].args[0]
                self.assertIn('--verify-tag', command)
                self.assertEqual('--prerelease' in command, '-' in version)
                self.assertEqual(len(run.call_args_list), 2)
                self.assertNotIn('--clobber', run.call_args_list[1].args[0])

    def test_missing_javadoc_stops_before_creating_release(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = self.assets(root)
            files[2].unlink()
            run = Mock()
            with self.assertRaisesRegex(RuntimeError, 'javadoc.jar'):
                release.publish_github(VERSION, root, lambda _: None, run)
            run.assert_not_called()

    def test_existing_draft_requires_inspection(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assets(root)
            run = Mock()
            with self.assertRaisesRegex(RuntimeError, 'draft'):
                release.publish_github(VERSION, root, lambda _: b'{"draft": true}', run)
            run.assert_not_called()
