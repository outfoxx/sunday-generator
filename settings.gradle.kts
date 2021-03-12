
pluginManagement {

  val kotlinPluginVersion: String by settings
  val jibPluginVersion: String by settings
  val shadowPluginVersion: String by settings

  plugins {
    kotlin("jvm") version kotlinPluginVersion
    id("com.google.cloud.tools.jib") version jibPluginVersion
    id("com.github.johnrengelman.shadow") version shadowPluginVersion
  }

}

rootProject.name = "sunday-generator"

include(
  "generator",
  "cli",
  "gradle-plugin"
)
