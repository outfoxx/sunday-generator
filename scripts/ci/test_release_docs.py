"""Exercise the workflow's documentation staging against stable-release output."""
import os
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

WORKFLOW = Path(__file__).resolve().parents[2] / '.github/workflows/publish-release.yml'


class ReleaseDocsTest(unittest.TestCase):
    def test_recovery_preserves_current_docs_and_launcher(self):
        for event, attempt in (('workflow_dispatch', '1'), ('push', '2')):
            with self.subTest(event=event, attempt=attempt):
                self.check_staging(event, attempt, recovery=True)

    def test_first_tag_attempt_still_publishes_current_docs(self):
        self.check_staging('push', '1', recovery=False)

    def check_staging(self, event, attempt, recovery):
        workflow = WORKFLOW.read_text()
        step = workflow.split('    - name: Stage Documentation\n', 1)[1].split('\n    - name:', 1)[0]
        script = textwrap.dedent(step.split('      run: |\n', 1)[1])
        self.assertIn('folder: build/release-docs', workflow)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = ('2.0.0/index.html', '2.0.0/sunday', '2.0.0/sunday.raml',
                     'current/index.html', 'current/sunday', 'sunday.raml')
            for name in files:
                file = root / 'build/dokka' / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text('old stable release')
            subprocess.run(['bash', '-e', '-c', script], cwd=root, check=True, env={
                **os.environ, 'RELEASE_VERSION': '2.0.0',
                'GITHUB_EVENT_NAME': event, 'GITHUB_RUN_ATTEMPT': attempt,
            })
            staged = root / 'build/release-docs'
            actual = {str(file.relative_to(staged)) for file in staged.rglob('*') if file.is_file()}
            expected = {name for name in files if not recovery or name.startswith('2.0.0/')}
            self.assertEqual(actual, expected)
            for file in staged.rglob('*'):
                if file.is_file():
                    self.assertEqual(file.read_text(), 'old stable release')
