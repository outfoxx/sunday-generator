# Build and verification performance

`./gradlew check` remains the complete verification entry point. TurnPost consumer builds are separate and are never prerequisites of this build or CI.

## Selecting generator tests

Use JUnit tags through one Gradle property. Omitting the property runs every generator test; other modules are unaffected.

```sh
./gradlew :generator:test -PtestTags='swift'
./gradlew :generator:test -PtestTags='validation'
./gradlew :generator:test -PtestTags='swift & validation'
./gradlew :generator:test -PtestTags='(swift | kotlin) & models'
```

Languages are `kotlin`, `swift`, `typescript`, and `python`. Features are `models`, `validation`, `requests`, `responses`, `security`, and `events`. A test can have several features. Composed annotations, inherited tags, class tags, and method tags have their normal JUnit semantics. Infrastructure and parser tests can remain untagged. `--tests` still selects individual tests. There are no group, numeric shard, or hash-sharding options.

Tag compiler-backed tests whenever they require a language, including tests that exercise multiple languages. Keep related cases in manageable classes so Gradle can distribute classes between worker JVMs. `TestPartitionTest` checks JUnit's actual discovery results and verifies the CI partition union and disjointness. Generated sources must still compile before inspection or snapshot comparison; `CompiledGeneratedSources` and `GeneratedCodeSnapshotInvariantTest` remain mandatory.

## Concurrency and resource limits

Generator tests run sequentially inside isolated JVMs. Concurrent first use of AMF's Scala model initialization can deadlock; JUnit thread parallelism is deliberately disabled.

`compilerTestForks` defaults to the minimum of eight, half the available processors, and one worker per four GiB after reserving four GiB for the build. Explicit values must fit that host limit. Each test JVM retains a 2 GiB heap; the remaining allowance covers metaspace, native compiler processes, Gradle, and integration tests. This gives eight workers on the 20-core/64-GiB development Mac, two on the standard 4-core/16-GiB public Ubuntu runner, and one on CI Macs.

`swiftCompilerJobs` defaults to at most two jobs per compiler invocation. CI Macs explicitly use three with one test JVM. These are separate limits: increasing both can oversubscribe the machine.

External compiler commands have a five-minute timeout; dependency installation/resolution has ten minutes. Override with `-PcompilerTimeoutSeconds=...` and `-PdependencyTimeoutSeconds=...`. The shared process runner captures bounded diagnostic tails, avoids blocked output pipes, and terminates descendants on timeout or interruption.

Kotlin compilation uses an explicit dependency configuration plus compiled fixture stubs, rather than inheriting the test worker's entire classpath. Compiler/parser third-party packages are excluded from coverage instrumentation only; production report filters and coverage thresholds are unchanged. A regression test prevents those exclusions from matching generator production classes.

## CI partitions and coverage

All CI jobs use standard GitHub-hosted `ubuntu-latest` or `macos-26` runners. Public pull requests do not select organization-specific runner groups or larger runners.

The preparation job builds the generator and JVM test harness once, then publishes portable classes, resources, JARs, and compiler fixture stubs as a commit-specific artifact. All six test jobs restore that artifact and verify its checksums before skipping the corresponding generator compilation/resource/JAR tasks. Dependency resolution and build-logic setup remain local to each runner. Native compiler installations, dependency environments, test results, and coverage data are never transferred in this artifact.

| Partition | JUnit expression | Work |
|---|---|---|
| Infrastructure | `!swift & !kotlin & !typescript & !python` | Two JVMs, untagged tests, lint, CLI/plugin and baseline integration checks |
| Kotlin | `!swift & kotlin` | Two JVMs, Kotlin compiler tests |
| TypeScript | `!swift & !kotlin & typescript` | Two JVMs, TypeScript compiler tests |
| Python | `!swift & !kotlin & !typescript & python` | Two JVMs, Python compiler tests |
| Swift validation | `swift & validation` | Native macOS, one JVM, three Swift compiler jobs |
| Swift remainder | `swift & !validation` | Native macOS, one JVM, three Swift compiler jobs |

The priority order Swift → Kotlin → TypeScript → Python gives mixed-language tests exactly one owner. The Linux partitions run concurrently on four standard GitHub-hosted Ubuntu instances. Each test still compiles its generated output before any assertion or snapshot; sharing the harness does not bypass generated-code compilation. `scripts/ci/partitions.json` defines the selection expressions and the discovery test verifies their union and disjointness.

Untagged tests run in the infrastructure partition. The existing Quarkus compatibility matrix remains separate. Superseded PR runs are canceled.

Each partition uploads coverage binaries, original production classes where applicable, JUnit XML, compiler timings, process samples, and effective JUnit inventories. A manifest records the commit, module list, tag expression, and checksums. `scripts/ci/coverage.py` rejects missing or mismatched artifacts, failed test results, incomplete inventories, and overlapping selections.

The dedicated `:code-coverage:aggregateCiCoverage` task uses the pinned Kover CLI to generate combined XML/HTML reports without test task dependencies. Supplying `ciArtifacts` switches Sonar to that aggregation path:

```sh
./gradlew sonar -PciArtifacts=build/ci-artifacts -PciCommit="$COMMIT"
```

The final `build-test` gate requires preparation, all partitions, and the Quarkus matrix before aggregation and analysis. Normal local `check` and Sonar retain their normal complete-test coverage behavior.

## Measuring changes

Run checks from inexpensive to expensive: workflow/static lint and artifact-protocol unit tests; tag/instrumentation/process-runner tests; representative compiler tests; full checks; all CI partitions, Quarkus compatibility, coverage aggregation, and Sonar.

Use `python3 scripts/ci/measure.py ./gradlew check --profile` to record elapsed time, CPU/RSS samples, and Gradle task profiles. Compiler timings are written per JVM under `generator/build/diagnostics/compilers`; the Gradle daemon PID is recorded so its descendants are included even when the daemon predates the measurement process.

Compare three forced test executions with warm dependencies and consistent toolchain versions. Add `-I scripts/ci/force-tests.gradle.kts` to the measurement command to force test tasks while leaving compilation incremental. Measure cold dependency caches separately; do not erase developers' shared caches. Keep test counts, skips, production coverage class inventory, and line/branch denominators alongside timings. Changes in class names from splitting tests must not remove cases or assertions.

The initial goals are six minutes locally and twenty minutes in CI, subject to measurement. Python environment pooling, persistent/incremental compiler services, cross-test output caches, and 16-core runners remain deferred.

## Swift dependency build reuse

Swift CI partitions prepare a stable workspace before starting JUnit. The cache key includes the partition, OS/architecture, exact Xcode/Swift/SDK fingerprint, package manifests, and preparation/compiler recipe. Preparation additionally rejects incompatible or relocated workspaces. A cache miss may resolve dependencies online; an exact prepared hit skips resolution and builds its dependency target with networking denied.

Only dependency products are retained. Preparation and post-test cleanup remove generated source files, test sources, generated modules, object directories, and XCTest products. Generated fixtures still pass through the Swift compiler on every test. `SwiftCompilerCacheTest` proves that deleting a previously compiled declaration makes a later reference fail.

CI supplies `SUNDAY_SWIFT_PREPARED_WORKSPACE` and requires one Gradle test JVM for that workspace. The compiler takes a file lock to prevent concurrent reuse. Every prepared Swift build/test invocation runs under a macOS sandbox that denies network access, including child processes; SwiftPM's nested sandbox is disabled because nested sandbox application is not supported. Missing dependencies fail instead of silently fetching during test execution. Local runtime overrides continue using the normal temporary-workspace path.

For a local comparison, run `python3 scripts/ci/swift_cache.py prepare --workspace /absolute/cache/workspace --jobs 3`, then set `SUNDAY_SWIFT_PREPARED_WORKSPACE` for the existing Gradle test command with `-PcompilerTestForks=1 -PswiftCompilerJobs=3`. Include preparation and cache transfer in end-to-end measurements. Compare identical tag expressions and toolchains, and distinguish first population from restored-cache runs.

For a forced warm-dependency measurement of the validation partition:

```sh
python3 scripts/ci/swift_cache.py prepare --workspace /tmp/sunday-swift-validation --jobs 3
SUNDAY_SWIFT_PREPARED_WORKSPACE=/tmp/sunday-swift-validation \
  ./gradlew :generator:test -PtestTags='swift & validation' \
  -PcompilerTestForks=1 -PswiftCompilerJobs=3 \
  -I scripts/ci/force-tests.gradle.kts --profile
```

Measure the combined duration of both commands. Alternate this with the same Gradle command on the baseline revision without the prepared-workspace environment variable. Keep dependency download caches warm for both. Hosted comparisons need a second run after the first has saved the dependency build cache; include restoration, preparation, test execution, and cache upload in the job comparison. Timing reports and cache-hit diagnostics are retained with each partition's artifacts/logs.
