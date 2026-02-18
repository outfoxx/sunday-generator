import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  `java-library`
  kotlin("jvm")
  id("org.jetbrains.dokka")
  id("org.jetbrains.dokka-javadoc")
  id("org.jlleitschuh.gradle.ktlint")
  id("org.jetbrains.kotlinx.kover")
}

val javaVersion: String = providers.gradleProperty("javaVersion").get()
val releaseVersion: String by project

val catalog: VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val kotlinVersion: String = catalog.findVersion("kotlinLanguage").get().requiredVersion

group = "io.outfoxx.sunday"
version = releaseVersion

java {
  sourceCompatibility = JavaVersion.toVersion(javaVersion)
  targetCompatibility = JavaVersion.toVersion(javaVersion)
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(javaVersion))
  }
  withSourcesJar()
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(javaVersion))
  }
  compilerOptions {
    jvmTarget.set(JvmTarget.valueOf("JVM_$javaVersion"))
    javaParameters.set(true)
  }
}

tasks {
  test {
    useJUnitPlatform()
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.default", "concurrent")
    systemProperty("junit.jupiter.execution.parallel.config.strategy", "dynamic")
    systemProperty("junit.jupiter.execution.parallel.config.dynamic.factor", "3")

    testLogging {
      events("passed", "skipped", "failed")
    }
  }

  // Ensure ktlint runs as part of the standard verification workflow.
  named("check") {
    dependsOn("ktlintCheck")
  }

}
