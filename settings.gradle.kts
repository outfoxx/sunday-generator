
pluginManagement {

  val kotlinPluginVersion: String by settings
  val jibPluginVersion: String by settings

  plugins {
    kotlin("jvm") version kotlinPluginVersion
    id("com.google.cloud.tools.jib") version jibPluginVersion
  }

}

rootProject.name = "sunday-generator"

include(
  "generator",
  "cli"
)
