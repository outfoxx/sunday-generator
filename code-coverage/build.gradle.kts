plugins {
  base
  alias(libs.plugins.kover)
}

dependencies {
  kover(project(":generator"))
  kover(project(":cli"))
  kover(project(":gradle-plugin"))
  kover(project(":integration-tests:quarkus"))
  kover(project(":integration-tests:quarkus-security"))
}

tasks {
  check {
    finalizedBy(named("koverXmlReport"), named("koverHtmlReport"))
  }
}

val coverageCli by configurations.creating

dependencies {
  coverageCli("org.jetbrains.kotlinx:kover-cli:${libs.versions.kover.get()}")
}

// This path consumes completed partition artifacts and deliberately has no dependency on Test tasks.
tasks.register<Exec>("aggregateCiCoverage") {
  val artifacts = providers.gradleProperty("ciArtifacts").getOrElse("build/ci-artifacts")
  val commit = providers.gradleProperty("ciCommit").getOrElse("")
  val reportDirectory = layout.buildDirectory.dir("reports/kover").get().asFile
  inputs.dir(rootProject.file(artifacts))
  inputs.files(rootProject.files("scripts/ci/coverage.py", "scripts/ci/partitions.json"))
  inputs.property("commit", commit)
  inputs.files(coverageCli)
  outputs.dir(reportDirectory)
  workingDir(rootDir)
  commandLine(
    "python3", "scripts/ci/coverage.py", "aggregate",
    "--artifacts", artifacts, "--commit", commit,
    "--cli", coverageCli.singleFile.absolutePath,
    "--destination", reportDirectory.absolutePath,
  )
}
