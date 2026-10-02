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

`compilerTestForks` defaults to the minimum of eight, half the available processors, and one worker per four GiB after reserving four GiB for the build. Explicit values must fit that host limit. Each test JVM retains a 2 GiB heap; the remaining allowance covers metaspace, native compiler processes, Gradle, and integration tests. This gives eight workers on the 20-core/64-GiB development Mac, four on the dedicated 8-core/32-GiB Ubuntu runner, and one on CI Macs.

`swiftCompilerJobs` defaults to at most two jobs per compiler invocation. CI Macs explicitly use three with one test JVM. These are separate limits: increasing both can oversubscribe the machine.

External compiler commands have a five-minute timeout; dependency installation/resolution has ten minutes. Override with `-PcompilerTimeoutSeconds=...` and `-PdependencyTimeoutSeconds=...`. The shared process runner captures bounded diagnostic tails, avoids blocked output pipes, and terminates descendants on timeout or interruption.

Kotlin compilation uses an explicit dependency configuration plus compiled fixture stubs, rather than inheriting the test worker's entire classpath. Compiler/parser third-party packages are excluded from coverage instrumentation only; production report filters and coverage thresholds are unchanged. A regression test prevents those exclusions from matching generator production classes.

## CI partitions and coverage

The dedicated `sunday-generator-ubuntu-8-cores` runner belongs to the repository-restricted `Sunday Generator CI` group and permits at most two concurrent instances. Existing runner groups are unchanged.

| Partition | JUnit expression | Work |
|---|---|---|
| Ubuntu | `!swift` | Four generator JVMs, lint, CLI/plugin tests, baseline integration checks |
| Swift validation | `swift & validation` | Native macOS, one JVM, three Swift compiler jobs |
| Swift remainder | `swift & !validation` | Native macOS, one JVM, three Swift compiler jobs |

Untagged tests run on Ubuntu. The existing Quarkus compatibility matrix remains separate. Superseded PR runs are canceled.

Each partition uploads coverage binaries, original production classes where applicable, JUnit XML, compiler timings, process samples, and effective JUnit inventories. A manifest records the commit, module list, tag expression, and checksums. `scripts/ci/coverage.py` rejects missing or mismatched artifacts, failed test results, incomplete inventories, and overlapping selections.

The dedicated `:code-coverage:aggregateCiCoverage` task uses the pinned Kover CLI to generate combined XML/HTML reports without test task dependencies. Supplying `ciArtifacts` switches Sonar to that aggregation path:

```sh
./gradlew sonar -PciArtifacts=build/ci-artifacts -PciCommit="$COMMIT"
```

The final `build-test` gate requires all partitions and the Quarkus matrix before aggregation and analysis. Normal local `check` and Sonar retain their normal complete-test coverage behavior.

## Measuring changes

Run checks from inexpensive to expensive: workflow/static lint and artifact-protocol unit tests; tag/instrumentation/process-runner tests; representative compiler tests; full checks; all CI partitions, Quarkus compatibility, coverage aggregation, and Sonar.

Use `python3 scripts/ci/measure.py ./gradlew check --profile` to record elapsed time, CPU/RSS samples, and Gradle task profiles. Compiler timings are written per JVM under `generator/build/diagnostics/compilers`; the Gradle daemon PID is recorded so its descendants are included even when the daemon predates the measurement process.

Compare three forced test executions with warm dependencies and consistent toolchain versions. Add `-I scripts/ci/force-tests.gradle.kts` to the measurement command to force test tasks while leaving compilation incremental. Measure cold dependency caches separately; do not erase developers' shared caches. Keep test counts, skips, production coverage class inventory, and line/branch denominators alongside timings. Changes in class names from splitting tests must not remove cases or assertions.

The initial goals are six minutes locally and twenty minutes in CI, subject to measurement. Python environment pooling, persistent/incremental compiler services, cross-test output caches, and 16-core runners remain deferred.
