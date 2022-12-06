
pluginManagement {

  val kotlinPluginVersion: String by settings
  val jibPluginVersion: String by settings
  val shadowPluginVersion: String by settings
  val dokkaPluginVersion: String by settings
  val licenserPluginVersion: String by settings
  val kotlinterPluginVersion: String by settings
  val detektPluginVersion: String by settings
  val pluginPublishPluginVersion: String by settings
  val githubReleasePluginVersion: String by settings
  val sonarqubeVersion: String by settings
  val nexusPublishPluginVersion: String by settings

  plugins {
    kotlin("jvm") version kotlinPluginVersion
    id("com.google.cloud.tools.jib") version jibPluginVersion
    id("com.github.johnrengelman.shadow") version shadowPluginVersion
    id("org.jetbrains.dokka") version dokkaPluginVersion
    id("org.cadixdev.licenser") version licenserPluginVersion
    id("org.jmailen.kotlinter") version kotlinterPluginVersion
    id("io.gitlab.arturbosch.detekt") version detektPluginVersion
    id("com.gradle.plugin-publish") version pluginPublishPluginVersion
    id("com.github.breadmoirai.github-release") version githubReleasePluginVersion
    id("org.sonarqube") version sonarqubeVersion
    id("io.github.gradle-nexus.publish-plugin") version nexusPublishPluginVersion
  }
}

rootProject.name = "sunday-generator"

include(
  "generator",
  "cli",
  "gradle-plugin",
  "code-coverage",
)
