"""Inventory immutable release destinations and fill missing GitHub release assets."""
import argparse
import json
import os
import subprocess
import urllib.error
import urllib.request
from pathlib import Path

from wait_for_maven_release import artifact_urls

REPO = 'outfoxx/sunday-generator'
API = f'https://api.github.com/repos/{REPO}'


def get(url):
    """Return bytes or absence; authentication and network failures must stop publication."""
    headers = {}
    if url.startswith(API + '/'):
        headers['Authorization'] = 'Bearer ' + os.environ['GH_TOKEN']
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise


def inventory(version, fetch=get):
    """Never infer that an upload is safe from a partially visible publication."""
    maven = [fetch(url) is not None for url in artifact_urls(version)]
    plugin_base = f'https://plugins.gradle.org/m2/io/outfoxx/sunday/gradle-plugin/{version}/gradle-plugin-{version}'
    marker = (f'https://plugins.gradle.org/m2/io/outfoxx/sunday-generator/'
              f'io.outfoxx.sunday-generator.gradle.plugin/{version}/'
              f'io.outfoxx.sunday-generator.gradle.plugin-{version}.pom')
    plugin = [fetch(url) is not None for url in (marker, plugin_base + '.pom', plugin_base + '.jar')]
    if any(plugin) and not all(plugin):
        raise RuntimeError('Plugin publication is partially visible; wait for propagation before resuming')
    return {
        'maven': any(maven),
        'plugin': all(plugin),
        'docker': fetch(f'https://hub.docker.com/v2/repositories/{REPO}/tags/{version}') is not None,
        # Check the committed docs, not a CDN which may cache a 404.
        'docs': fetch(f'{API}/contents/{version}/index.html?ref=gh-pages') is not None,
    }


def publish_github(version, root=Path('.'), fetch=get, run=subprocess.run):
    """Create a missing release and upload only absent assets, never clobbering existing files."""
    artifact_urls(version)
    files = [root / module / 'build/libs' / f'{module}-{version}{suffix}.jar'
             for module in ('generator', 'cli', 'gradle-plugin')
             for suffix in ('', '-sources', '-javadoc')]
    files.append(root / 'build/scripts/sunday')
    for file in files:
        if not file.is_file():
            raise RuntimeError(f'Missing release asset: {file}')
    data = fetch(f'{API}/releases/tags/{version}')
    if data is None:
        command = ['gh', 'release', 'create', version, '--repo', f'github.com/{REPO}', '--verify-tag',
                   '--title', f'🚀 v{version}', '--generate-notes']
        if '-' in version:
            command.append('--prerelease')
        run(command, check=True)
        existing = set()
    else:
        release = json.loads(data)
        if release['draft']:
            raise RuntimeError('Existing release is a draft; inspect it before resuming')
        existing = {asset['name'] for asset in release['assets']}
    missing = [str(file) for file in files if file.name not in existing]
    if missing:
        run(['gh', 'release', 'upload', version, '--repo', f'github.com/{REPO}', *missing], check=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['inventory', 'github'])
    parser.add_argument('version')
    args = parser.parse_args()
    if args.command == 'github':
        publish_github(args.version)
    else:
        state = inventory(args.version)
        print(json.dumps(state))
        with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
            for name, exists in state.items():
                output.write(f'{name}={str(exists).lower()}\n')
