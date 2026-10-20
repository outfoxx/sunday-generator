# Publishing and resuming releases

Pushing a version tag starts **Publish Release**. Maven Central verification retries
240 times at 15-second intervals (about an hour, plus request time), with a
75-minute step timeout. It requires all generator and CLI POMs/JARs, matching
SHA-256 checksums, and a successful version check using the downloaded CLI fat JAR.
The overall job timeout is 120 minutes.

## Resume an existing release

Run **Publish Release** manually from `main` and enter the existing version tag,
for example `2.0.0-beta.52`:

```sh
gh workflow run publish-release.yml --repo github.com/outfoxx/sunday-generator \
  --ref main -f version=2.0.0-beta.52
```

The workflow keeps recovery scripts from the selected workflow ref, then checks
out and builds the exact tag. Manual runs and reruns **never upload to Maven
Central**, even when an accepted deployment is not publicly visible yet. They
wait for the existing deployment to propagate. Check the Sonatype deployment
status if verification continues to fail; an absent public artifact alone does
not prove an upload failed. If the original run never submitted a deployment,
resolve that separately before resuming. Do not move the tag or blindly rerun an
upload.

After Maven verification, the workflow refreshes the destination inventory:

- Existing Gradle Plugin Portal marker and implementation artifacts skip plugin
  publication. Partial visibility stops the run so a retry cannot overwrite it.
- An existing Docker Hub version tag skips image publication.
- Committed versioned documentation on `gh-pages` skips documentation deployment.
- An existing GitHub release keeps its metadata and assets. Only missing assets
  are uploaded, without clobbering; an existing draft requires manual inspection.

Authentication errors, rate limits, and server errors stop inventory checks;
only HTTP 404 means absent. Runs for the same version are serialized. A first
attempt triggered by a tag uploads to Maven only if no checked Maven artifact is
visible; any partial publication suppresses that upload and proceeds to verification.
